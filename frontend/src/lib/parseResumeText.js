/**
 * Extrai dados estruturados de currículo a partir de texto puro.
 *
 * PIPELINE (fallback client-side — usado quando o backend de IA retorna 501):
 *   TXT  → FileReader.readAsText  (sempre funciona)
 *   PDF  → file.arrayBuffer() + pdfjs-dist (extração real de texto, inclusive
 *          PDFs com streams FlateDecode — cobre a esmagadora maioria dos PDFs
 *          reais, gerados por Word/LibreOffice/impressora virtual)
 *   DOCX → readAsBinaryString + regex <w:t> + validação de encoding
 *
 * IMPORTANTE: DOCX em geral é ZIP/deflate; sem uma biblioteca nativa (mammoth)
 * a extração por regex pode falhar para arquivos maiores/comprimidos.
 * PDFs escaneados (imagem, sem camada de texto) também retornam '' — pdfjs-dist
 * não faz OCR client-side. Nesses casos a função retorna '' para que o usuário
 * veja a área de texto "cole manualmente" em vez de campos vazios/corrompidos.
 *
 * O pipeline completo (com IA) é gerenciado pelo backend (PDFBox + POI + LLM).
 */

// ── Extração de texto de arquivos ──────────────────────────────────────────────

export async function extractTextFromFile(file) {
  const name = file.name.toLowerCase();
  if (name.endsWith('.txt') || file.type === 'text/plain') {
    return readAsText(file);
  }
  if (name.endsWith('.pdf') || file.type === 'application/pdf') {
    return extractFromPdf(file);
  }
  if (name.endsWith('.docx')) {
    return extractFromDocx(file);
  }
  // fallback genérico: tenta UTF-8
  return readAsText(file);
}

function readAsText(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = (e) => resolve(e.target.result || '');
    r.onerror = () => reject(new Error('Erro ao ler arquivo'));
    r.readAsText(file, 'UTF-8');
  });
}

// ── PDF ────────────────────────────────────────────────────────────────────────

async function extractFromPdf(file) {
  try {
    const buffer = await file.arrayBuffer();
    return await parsePdfBinary(buffer);
  } catch {
    return '';
  }
}

/**
 * Detecta se o texto extraído é provavelmente lixo de encoding.
 *
 * PDFs com FlateDecode (compressão deflate) têm conteúdo binário nos streams;
 * a extração via readAsBinaryString interpreta esses bytes como Latin-1,
 * produzindo sequências como "Lõ¢²hõõk³ Á¨h-bëi3Óõõ¤".
 *
 * Regra: se menos de 82% dos caracteres estiverem no range "seguro"
 * (ASCII imprimível + Latin-1 supplement com letras acentuadas),
 * considera o texto corrompido e retorna true.
 */
function isLikelyGarbled(text) {
  if (!text || text.length < 20) return true;
  let safe = 0;
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    if (
      (code >= 32  && code <= 126) || // ASCII imprimível
      (code >= 192 && code <= 214) || // Letras latinas maiúsculas com acento (À–Ö)
      (code >= 216 && code <= 246) || // Letras latinas (Ø–ö)
      (code >= 248 && code <= 255) || // Letras latinas (ø–ÿ)
      code === 9 || code === 10 || code === 13 || // tab, LF, CR
      code === 160 || code === 170 || code === 186 // NBSP, ª, º
    ) {
      safe++;
    }
  }
  return (safe / text.length) < 0.82;
}

/**
 * Extrai o texto de um PDF (ArrayBuffer) usando pdfjs-dist.
 *
 * Cobre PDFs com streams comprimidos (FlateDecode) — a maioria dos PDFs reais.
 * PDFs escaneados (só imagem, sem camada de texto) não têm texto para extrair
 * e a função retorna '' silenciosamente; o chamador (dashboard/page.jsx) é
 * responsável por avisar o usuário e oferecer colar o texto manualmente.
 */
async function parsePdfBinary(buffer) {
  try {
    const pdfjsLib = await import('pdfjs-dist/legacy/build/pdf.mjs');
    // Worker servido como asset estático pelo próprio bundle do Next.js —
    // evita depender de CDN externo (CSP `worker-src`/`script-src 'self'`).
    pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
      'pdfjs-dist/legacy/build/pdf.worker.min.mjs',
      import.meta.url
    ).toString();

    const loadingTask = pdfjsLib.getDocument({ data: buffer });
    const pdf = await loadingTask.promise;

    const pageTexts = [];
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const content = await page.getTextContent();
      const pageText = content.items.map((item) => item.str || '').join(' ');
      if (pageText.trim()) pageTexts.push(pageText.trim());
    }

    return pageTexts.join('\n').trim();
  } catch (err) {
    // PDF corrompido, protegido por senha, ou erro de carregamento do worker.
    console.warn('[parseResumeText] Falha ao extrair texto do PDF:', err?.message || err);
    return '';
  }
}

// ── DOCX ───────────────────────────────────────────────────────────────────────

function extractFromDocx(file) {
  return new Promise((resolve) => {
    const r = new FileReader();
    r.onload = (e) => {
      try {
        // DOCX é um ZIP comprimido; a extração via regex só funciona em arquivos
        // muito pequenos onde o XML interno fica não comprimido.
        // Na grande maioria dos casos retornará ''.
        const bin = e.target.result;
        const matches = [];
        const re = /<w:t[^>]*>([^<]+)<\/w:t>/g;
        let mm;
        while ((mm = re.exec(bin)) !== null) {
          if (mm[1].trim()) matches.push(mm[1]);
        }
        const text = matches.join(' ').trim();
        // Valida resultado: descarta se parece corrompido
        resolve(isLikelyGarbled(text) ? '' : text);
      } catch {
        resolve('');
      }
    };
    r.onerror = () => resolve('');
    r.readAsBinaryString(file);
  });
}

// ── Parser de texto → dados estruturados ──────────────────────────────────────

const SECTION_RE = /^(resumo|perfil|objetivo|summary|profile|experiência profissional|experiência|experience|formação acadêmica|formação|educação|education|habilidades técnicas|habilidades|competências|skills|idiomas|languages|hobbies|interesses|atividades)[:\s]*$/i;

const LEVEL_PT = ['nativo', 'fluente', 'avançado', 'intermediário', 'básico'];
const LEVEL_EN = ['native', 'fluent', 'advanced', 'intermediate', 'basic'];
const ALL_LEVELS = [...LEVEL_PT, ...LEVEL_EN];

export function parseResumeText(raw) {
  const lines = raw.split('\n').map(l => l.trim()).filter(Boolean);

  // ── 1. Separa seções ──────────────────────────────────────────────────────
  const sections = {};
  let current = 'header';
  sections[current] = [];
  for (const line of lines) {
    if (SECTION_RE.test(line)) {
      current = line.toLowerCase().replace(/[:\s]+$/, '').split(/\s+/)[0];
      sections[current] = [];
    } else {
      (sections[current] = sections[current] || []).push(line);
    }
  }

  // ── 2. Contatos ───────────────────────────────────────────────────────────
  const fullText = raw;
  const email   = (fullText.match(/[\w.+%-]+@[\w.-]+\.[a-z]{2,}/i) || [])[0] || '';
  const phone   = (fullText.match(/\+?[\d][\d\s\-().]{8,18}[\d]/) || [])[0]?.trim() || '';
  const website = (fullText.match(/(?:https?:\/\/|www\.|linkedin\.com\/in\/|github\.com\/)\S+/) || [])[0] || '';
  const location= (fullText.match(
    /[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕ][a-záéíóúàèìòùâêîôûãõ]+(?: [A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕ][a-záéíóúàèìòùâêîôûãõ]+)*,\s*(?:SP|RJ|MG|RS|BA|PR|CE|DF|AM|PA|PE|GO|SC|Brasil|Brazil)/
  ) || [])[0] || '';

  // ── 3. Cabeçalho: nome e título ───────────────────────────────────────────
  const headerLines = sections['header'] || [];
  const fullName = headerLines.find(
    l => l.length > 3 && l.length < 70 && !l.includes('@') && !/^\+?\d/.test(l)
      && /^[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕ]/.test(l)
  ) || '';
  const headline = headerLines.find(
    l => l !== fullName && l.length > 5 && l.length < 90
      && !l.includes('@') && !/^\+?\d/.test(l) && !SECTION_RE.test(l)
  ) || '';

  // ── 4. Resumo ─────────────────────────────────────────────────────────────
  const summaryLines = sections['resumo'] || sections['perfil'] || sections['objetivo']
    || sections['summary'] || sections['profile'] || [];
  const summary = summaryLines.join(' ').trim();

  // ── 5. Habilidades ────────────────────────────────────────────────────────
  const skillLines = sections['habilidades'] || sections['habilidades técnicas']
    || sections['competências'] || sections['skills'] || [];
  const skills = skillLines
    .join(', ')
    .split(/[,;\n•·\-–|]+/)
    .map(s => s.trim())
    .filter(s => s.length > 1 && s.length < 60)
    .map(name => ({ name, pct: 75 }));   // pct padrão 75% — ajustável no editor

  // ── 6. Idiomas ────────────────────────────────────────────────────────────
  const langLines = sections['idiomas'] || sections['languages'] || [];
  const languages = langLines.map(line => {
    const lv = ALL_LEVELS.find(l => line.toLowerCase().includes(l)) || '';
    const name = line
      .replace(new RegExp(lv, 'gi'), '')
      .replace(/[-:·]/g, '')
      .trim();
    return { name: name || line, level: lv ? capitalize(lv) : '' };
  }).filter(l => l.name.length > 0);

  // ── 7. Hobbies ────────────────────────────────────────────────────────────
  const hobbyLines = sections['hobbies'] || sections['interesses'] || sections['atividades'] || [];
  const hobbies = hobbyLines
    .join(', ')
    .split(/[,;\n•·]+/)
    .map(h => h.trim())
    .filter(h => h.length > 1);

  // ── 8. Experiência ────────────────────────────────────────────────────────
  const expLines = sections['experiência'] || sections['experiência profissional']
    || sections['experience'] || [];
  const experience = parseExperience(expLines);

  // ── 9. Formação ───────────────────────────────────────────────────────────
  const eduLines = sections['formação'] || sections['formação acadêmica']
    || sections['educação'] || sections['education'] || [];
  const education = parseEducation(eduLines);

  return {
    fullName,
    headline,
    email,
    phone,
    location,
    website,
    summary,
    photo: '',
    skills:     skills.length     ? skills     : [],
    experience: experience.length ? experience : [emptyExp()],
    education:  education.length  ? education  : [{ degree: '', school: '', period: '' }],
    languages:  languages.length  ? languages  : [{ name: '', level: '' }],
    hobbies,
  };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const PERIOD_RE = /\d{4}\s*[-–—]\s*(?:\d{4}|hoje|present|atual)/i;
const DEGREE_WORDS = /bacharelado|licenciatura|tecnólogo|mba|mestrado|doutorado|pós-graduação|especialização|bachelor|master|phd|degree|graduação/i;

function parseExperience(lines) {
  if (!lines.length) return [];
  const entries = [];
  let cur = null;

  for (const line of lines) {
    const isPeriod = PERIOD_RE.test(line);
    const isCompany = line.length < 60 && !isPeriod && entries.length > 0 && cur && !cur.company;
    const isRole = line.length < 80 && !isPeriod && !cur;

    if (!cur && line.length > 2) {
      cur = { role: line, company: '', period: '', bullets: Array(10).fill('') };
    } else if (cur && !cur.company && !isPeriod && line.length < 70) {
      cur.company = line;
    } else if (cur && !cur.period && isPeriod) {
      cur.period = line;
    } else if (cur && (line.startsWith('•') || line.startsWith('-') || line.startsWith('–'))) {
      const b = line.replace(/^[•\-–]\s*/, '');
      const firstEmpty = cur.bullets.indexOf('');
      if (firstEmpty >= 0) cur.bullets[firstEmpty] = b;
      // move on only if we have period (entry is "complete enough")
    } else if (cur && cur.role && (isPeriod || cur.period)) {
      // new entry started
      if (cur.role) entries.push(cur);
      cur = { role: line, company: '', period: '', bullets: Array(10).fill('') };
    }
  }
  if (cur?.role) entries.push(cur);
  return entries.length ? entries : [emptyExp()];
}

function parseEducation(lines) {
  if (!lines.length) return [];
  const entries = [];
  let cur = null;

  for (const line of lines) {
    const isPeriod = PERIOD_RE.test(line);
    if (!cur && (DEGREE_WORDS.test(line) || line.length < 80)) {
      cur = { degree: line, school: '', period: '' };
    } else if (cur && !cur.school && !isPeriod && line.length < 80) {
      cur.school = line;
    } else if (cur && isPeriod) {
      cur.period = line;
      entries.push(cur);
      cur = null;
    } else if (cur && cur.degree && line.length < 80) {
      entries.push(cur);
      cur = { degree: line, school: '', period: '' };
    }
  }
  if (cur?.degree) entries.push(cur);
  return entries.length ? entries : [];
}

function emptyExp() {
  return { role: '', company: '', period: '', bullets: Array(10).fill('') };
}

function capitalize(s) {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1);
}
