import { extractTextFromFile, parseResumeText } from '@/lib/parseResumeText';

// ── Mock de pdfjs-dist ───────────────────────────────────────────────────────
// parsePdfBinary() faz `await import('pdfjs-dist/legacy/build/pdf.mjs')`
// dinamicamente; mockamos o módulo para não depender do worker real (que
// precisa de DOM/canvas que o jsdom não fornece) e para poder simular tanto
// extração bem-sucedida quanto falha (PDF escaneado / sem camada de texto).
const mockGetDocument = jest.fn();

jest.mock('pdfjs-dist/legacy/build/pdf.mjs', () => ({
  GlobalWorkerOptions: {},
  getDocument: (...args) => mockGetDocument(...args),
}));

function fakePdfDocument(pagesText) {
  return {
    promise: Promise.resolve({
      numPages: pagesText.length,
      getPage: (i) =>
        Promise.resolve({
          getTextContent: () =>
            Promise.resolve({
              items: pagesText[i - 1].split(' ').map((str) => ({ str })),
            }),
        }),
    }),
  };
}

function fakeFile({ name, type, arrayBufferValue = new ArrayBuffer(8) }) {
  return {
    name,
    type,
    size: 1024,
    arrayBuffer: async () => arrayBufferValue,
  };
}

describe('extractTextFromFile — PDF (pdfjs-dist)', () => {
  afterEach(() => {
    mockGetDocument.mockReset();
  });

  it('extrai o texto de um PDF com uma página usando pdfjs-dist', async () => {
    mockGetDocument.mockReturnValue(fakePdfDocument(['Olá Mundo, este é um currículo']));

    const file = fakeFile({ name: 'curriculo.pdf', type: 'application/pdf' });
    const text = await extractTextFromFile(file);

    expect(text).toBe('Olá Mundo, este é um currículo');
    expect(mockGetDocument).toHaveBeenCalledTimes(1);
  });

  it('concatena o texto de múltiplas páginas com quebra de linha', async () => {
    mockGetDocument.mockReturnValue(fakePdfDocument(['Página um', 'Página dois']));

    const file = fakeFile({ name: 'cv.pdf', type: 'application/pdf' });
    const text = await extractTextFromFile(file);

    expect(text).toBe('Página um\nPágina dois');
  });

  it('retorna string vazia quando o PDF não tem camada de texto (ex: escaneado)', async () => {
    // pdf.js consegue abrir o documento, mas getTextContent não retorna itens
    mockGetDocument.mockReturnValue(fakePdfDocument(['']));

    const file = fakeFile({ name: 'scan.pdf', type: 'application/pdf' });
    const text = await extractTextFromFile(file);

    expect(text).toBe('');
  });

  it('retorna string vazia (sem lançar) quando pdfjs-dist falha ao carregar o documento', async () => {
    mockGetDocument.mockReturnValue({
      promise: Promise.reject(new Error('Invalid PDF structure')),
    });

    const file = fakeFile({ name: 'corrompido.pdf', type: 'application/pdf' });
    await expect(extractTextFromFile(file)).resolves.toBe('');
  });

  it('retorna string vazia (sem lançar) quando file.arrayBuffer() falha', async () => {
    const file = {
      name: 'quebrado.pdf',
      type: 'application/pdf',
      arrayBuffer: async () => {
        throw new Error('boom');
      },
    };
    await expect(extractTextFromFile(file)).resolves.toBe('');
  });
});

describe('extractTextFromFile — TXT', () => {
  it('lê arquivo .txt via FileReader', async () => {
    const file = new File(['João Silva\nDesenvolvedor'], 'curriculo.txt', { type: 'text/plain' });
    const text = await extractTextFromFile(file);
    expect(text).toBe('João Silva\nDesenvolvedor');
  });
});

// ── parseResumeText — parser regex de texto → dados estruturados ────────────

describe('parseResumeText — seções e contatos', () => {
  const raw = [
    'João da Silva',
    'Engenheiro de Software Sênior',
    'joao.silva@example.com',
    '+55 (11) 98765-4321',
    'linkedin.com/in/joaosilva',
    'São Paulo, SP',
    '',
    'Resumo',
    'Profissional com 10 anos de experiência em backend.',
    '',
    'Experiência Profissional',
    'Engenheiro de Software Sênior',
    'Acme Corp',
    '2020 - Atual',
    '• Liderou equipe de 5 pessoas',
    '• Reduziu custo de infraestrutura em 30%',
    '',
    'Formação Acadêmica',
    'Bacharelado em Ciência da Computação',
    'Universidade de São Paulo',
    '2014 - 2018',
    '',
    'Habilidades',
    'Java, Spring Boot, PostgreSQL, React',
    '',
    'Idiomas',
    'Inglês - Fluente',
    'Espanhol - Intermediário',
    '',
    'Hobbies',
    'Xadrez, Corrida, Fotografia',
  ].join('\n');

  const parsed = parseResumeText(raw);

  it('extrai nome e headline do cabeçalho', () => {
    expect(parsed.fullName).toBe('João da Silva');
    expect(parsed.headline).toBe('Engenheiro de Software Sênior');
  });

  it('extrai email, telefone e site via regex', () => {
    expect(parsed.email).toBe('joao.silva@example.com');
    expect(parsed.phone).toContain('98765-4321');
    expect(parsed.website).toContain('linkedin.com/in/joaosilva');
  });

  it('extrai localização no formato Cidade, UF', () => {
    expect(parsed.location).toContain('São Paulo, SP');
  });

  it('extrai o resumo da seção correspondente', () => {
    expect(parsed.summary).toContain('10 anos de experiência');
  });

  it('parseia experiência com cargo, empresa, período e bullets', () => {
    expect(parsed.experience).toHaveLength(1);
    const exp = parsed.experience[0];
    expect(exp.role).toBe('Engenheiro de Software Sênior');
    expect(exp.company).toBe('Acme Corp');
    expect(exp.period).toBe('2020 - Atual');
    expect(exp.bullets).toContain('Liderou equipe de 5 pessoas');
    expect(exp.bullets).toContain('Reduziu custo de infraestrutura em 30%');
    expect(exp.bullets).toHaveLength(10);
  });

  it('parseia formação com curso, instituição e período', () => {
    expect(parsed.education).toHaveLength(1);
    expect(parsed.education[0]).toMatchObject({
      degree: 'Bacharelado em Ciência da Computação',
      school: 'Universidade de São Paulo',
      period: '2014 - 2018',
    });
  });

  it('parseia habilidades com pct padrão 75', () => {
    const names = parsed.skills.map((s) => s.name);
    expect(names).toEqual(expect.arrayContaining(['Java', 'Spring Boot', 'PostgreSQL', 'React']));
    expect(parsed.skills.every((s) => s.pct === 75)).toBe(true);
  });

  it('parseia idiomas com nível', () => {
    expect(parsed.languages).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ level: 'Fluente' }),
        expect.objectContaining({ level: 'Intermediário' }),
      ])
    );
  });

  it('parseia hobbies em lista', () => {
    expect(parsed.hobbies).toEqual(expect.arrayContaining(['Xadrez', 'Corrida', 'Fotografia']));
  });
});

describe('parseResumeText — fallback quando seções estão ausentes', () => {
  it('retorna estrutura com placeholders vazios quando o texto não tem seções reconhecidas', () => {
    const parsed = parseResumeText('texto qualquer sem estrutura de currículo');
    expect(parsed.experience).toEqual([{ role: '', company: '', period: '', bullets: Array(10).fill('') }]);
    expect(parsed.education).toEqual([{ degree: '', school: '', period: '' }]);
    expect(parsed.languages).toEqual([{ name: '', level: '' }]);
    expect(parsed.skills).toEqual([]);
    expect(parsed.hobbies).toEqual([]);
  });
});
