'use client';

import { useState, useMemo, useRef } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Search, Star, Upload, FileText, CheckCircle2, AlertCircle, Edit2, Trash2, Printer, Moon, Sparkles, Loader2 } from 'lucide-react';
import { extractTextFromFile, parseResumeText } from '@/lib/parseResumeText';
import { useResumeImportStore } from '@/lib/stores/resumeImportStore';
import { FadeIn, Stagger, StaggerItem } from '@/components/ui/Motion';
import { LAYOUTS, ResumeLayout } from '@/components/layouts';
import { PhotoPicker } from '@/components/photo/PhotoPicker';
import { useAuthStore } from '@/lib/stores/authStore';
import { useResumeListStore } from '@/lib/stores/resumeListStore';
import { DARK_PALETTE_LIST, applyPalette } from '@/lib/theme';
import { apiFetchForm } from '@/lib/apiClient';

const SORT_OPTIONS = [
  { value: 'recommended', label: 'Recomendados' },
  { value: 'newest', label: 'Novidades' },
  { value: 'alpha', label: 'Alfabético' },
];

export default function DashboardPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  const [tab, setTab] = useState('templates');
  const [query, setQuery] = useState('');
  const [paletteFilter, setPaletteFilter] = useState('all');
  const [sort, setSort] = useState('recommended');
  const [favorites, setFavorites] = useState(new Set());

  function handleUse(layout, paletteId) {
    if (!user) {
      router.push('/login');
      return;
    }
    const url = `/dashboard/editor?layout=${layout.id}${paletteId ? `&palette=${paletteId}` : ''}`;
    router.push(url);
  }

  function handleFavorite(layoutId) {
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(layoutId)) next.delete(layoutId);
      else next.add(layoutId);
      return next;
    });
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let list = LAYOUTS.filter((l) => {
      if (paletteFilter !== 'all' && l.id !== paletteFilter) return false;
      if (!q) return true;
      return (
        l.theme.name['pt-BR'].toLowerCase().includes(q) ||
        l.theme.concept['pt-BR'].toLowerCase().includes(q) ||
        l.theme.tags.some((t) => t.toLowerCase().includes(q))
      );
    });
    if (sort === 'alpha') {
      list = [...list].sort((a, b) => a.theme.name['pt-BR'].localeCompare(b.theme.name['pt-BR']));
    }
    return list;
  }, [query, paletteFilter, sort]);

  return (
    <main className="container-page py-8">
        <FadeIn>
          <h1 className="mb-2 font-display text-3xl font-bold text-gray-900">Meu painel</h1>
          <p className="mb-6 text-gray-600">Escolha um modelo, personalize a foto e gere seu currículo.</p>
        </FadeIn>

        <div className="mb-6 flex gap-2 overflow-x-auto border-b border-gray-200" role="tablist">
          {[
            { id: 'templates', label: 'Modelos de Currículos' },
            { id: 'myResumes', label: 'Meus Currículos' },
            { id: 'photo', label: 'Foto 3x4' },
            { id: 'aiImport', label: 'Importar com IA' },
          ].map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              onClick={() => setTab(t.id)}
              className={`whitespace-nowrap border-b-2 px-4 py-3 text-sm font-semibold transition ${
                tab === t.id
                  ? 'border-brand-700 text-brand-700'
                  : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'templates' && (
          <>
            <div className="mb-6 flex flex-wrap items-center gap-3">
              <div className="relative flex-1 min-w-[220px]">
                <Search
                  size={16}
                  aria-hidden
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
                />
                <input
                  type="search"
                  placeholder="Buscar modelo..."
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  className="w-full rounded-md border border-gray-300 py-2 pl-9 pr-3 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
              <select
                aria-label="Filtrar por paleta"
                value={paletteFilter}
                onChange={(e) => setPaletteFilter(e.target.value)}
                className="rounded-md border border-gray-300 py-2 pl-3 pr-8 text-sm"
              >
                <option value="all">Todas as paletas</option>
                {LAYOUTS.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.theme.name['pt-BR']}
                  </option>
                ))}
              </select>
              <select
                aria-label="Ordenar por"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
                className="rounded-md border border-gray-300 py-2 pl-3 pr-8 text-sm"
              >
                {SORT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    Ordenar: {o.label}
                  </option>
                ))}
              </select>
            </div>

            <Stagger>
              <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                {filtered.map((l) => (
                  <StaggerItem key={l.id}>
                    <TemplateCard
                      layout={l}
                      isFavorited={favorites.has(l.id)}
                      onUse={(paletteId) => handleUse(l, paletteId)}
                      onFavorite={() => handleFavorite(l.id)}
                    />
                  </StaggerItem>
                ))}
              </div>
            </Stagger>
            {filtered.length === 0 && (
              <p className="py-12 text-center text-sm text-gray-500">
                Nenhum modelo corresponde à busca.
              </p>
            )}
          </>
        )}

        {tab === 'myResumes' && (
          <MyResumesTab router={router} onNewResume={() => setTab('templates')} />
        )}

        {tab === 'photo' && (
          <div className="card p-6">
            <PhotoPicker />
          </div>
        )}

        {tab === 'aiImport' && <ImportTab router={router} />}
    </main>
  );
}

// ── Aba "Meus Currículos" ──────────────────────────────────────────────────────
function MyResumesTab({ router, onNewResume }) {
  const resumes      = useResumeListStore((s) => s.resumes);
  const deleteResume = useResumeListStore((s) => s.deleteResume);
  const [confirmId, setConfirmId] = useState(null);

  function handleEdit(resume) {
    router.push(`/dashboard/editor?layout=${resume.layoutId}&resumeId=${resume.id}`);
  }

  function handlePrint(resume) {
    router.push(`/dashboard/editor?layout=${resume.layoutId}&resumeId=${resume.id}&print=1`);
  }

  function handleDelete(id) {
    if (confirmId === id) {
      deleteResume(id);
      setConfirmId(null);
    } else {
      setConfirmId(id);
    }
  }

  function formatDate(iso) {
    try {
      return new Intl.DateTimeFormat('pt-BR', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  }

  if (resumes.length === 0) {
    return (
      <div className="card p-12 text-center">
        <FileText size={40} className="mx-auto mb-4 text-gray-300" />
        <p className="mb-1 text-lg font-semibold text-gray-700">Nenhum currículo salvo ainda</p>
        <p className="mb-6 text-sm text-gray-500">
          Crie seu primeiro currículo escolhendo um modelo abaixo.
        </p>
        <button className="btn-primary" onClick={onNewResume}>
          Escolher um modelo
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-gray-600">
          {resumes.length} {resumes.length === 1 ? 'currículo salvo' : 'currículos salvos'}
        </p>
        <button className="btn-primary !py-1.5 !px-3 text-xs" onClick={onNewResume}>
          + Novo currículo
        </button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {resumes.map((resume) => (
          <article key={resume.id} className="card overflow-hidden transition hover:shadow-md flex flex-col">
            <div className="flex-1 p-4">
              <div className="mb-1 flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <h3 className="truncate font-semibold text-gray-900">
                    {resume.name || 'Sem nome'}
                  </h3>
                  <p className="mt-0.5 text-xs text-gray-400">
                    Salvo em {formatDate(resume.savedAt)}
                  </p>
                </div>
                <span className="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-bold text-brand-700 uppercase tracking-wider">
                  {resume.layoutId}
                </span>
              </div>

              {/* Prévia de dados */}
              <div className="mt-3 space-y-1">
                {resume.data?.headline && (
                  <p className="truncate text-xs text-gray-500">{resume.data.headline}</p>
                )}
                {resume.data?.email && (
                  <p className="truncate text-xs text-gray-400">{resume.data.email}</p>
                )}
                {resume.data?.skills?.length > 0 && (
                  <p className="text-xs text-gray-400">
                    {resume.data.skills.slice(0, 3).map((s) =>
                      typeof s === 'string' ? s : s.name
                    ).join(', ')}
                    {resume.data.skills.length > 3 && ` +${resume.data.skills.length - 3}`}
                  </p>
                )}
              </div>
            </div>

            {/* Ações */}
            <div className="flex items-center gap-1 border-t border-gray-100 px-4 py-2">
              <button
                onClick={() => handleEdit(resume)}
                className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 transition"
              >
                <Edit2 size={12} /> Editar
              </button>
              <button
                onClick={() => handlePrint(resume)}
                className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-700 hover:bg-gray-50 transition"
              >
                <Printer size={12} /> Imprimir
              </button>
              <button
                onClick={() => handleDelete(resume.id)}
                title={confirmId === resume.id ? 'Clique novamente para confirmar' : 'Excluir'}
                className={`flex items-center justify-center rounded-md border px-2.5 py-1.5 text-xs font-semibold transition ${
                  confirmId === resume.id
                    ? 'border-red-400 bg-red-50 text-red-600 hover:bg-red-100'
                    : 'border-gray-200 text-gray-400 hover:border-red-300 hover:text-red-500'
                }`}
              >
                <Trash2 size={12} />
                {confirmId === resume.id && <span className="ml-1">Confirmar?</span>}
              </button>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}

function TemplateCard({ layout, isFavorited, onUse, onFavorite }) {
  const { theme, variant } = layout;

  // null = tema original; 'escuro' | 'navy' | 'forest' = paleta escura ativa
  const [selectedPalette, setSelectedPalette] = useState(null);

  // Tema efetivo aplicado ao preview (mescla paleta escura se selecionada)
  const effectiveTheme = applyPalette(theme, selectedPalette);

  function togglePalette(palId) {
    setSelectedPalette((prev) => (prev === palId ? null : palId));
  }

  const activePal = selectedPalette
    ? DARK_PALETTE_LIST.find((p) => p.id === selectedPalette)
    : null;

  return (
    <article className="card overflow-hidden transition hover:shadow-md flex flex-col">

      {/* ── Preview do currículo ── */}
      <div className="relative h-[280px] overflow-hidden bg-gray-100 shrink-0">
        <div className="origin-top-left" style={{ transform: 'scale(0.45)', width: 600, height: 800 }}>
          <ResumeLayout theme={effectiveTheme} variant={variant} />
        </div>
        {/* Badge da paleta ativa sobre o preview */}
        {activePal && (
          <div
            className="absolute top-2 left-2 flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold shadow"
            style={{ background: activePal.primary, color: activePal.onPrimary }}
          >
            <Moon size={9} />
            {activePal.label}
          </div>
        )}
      </div>

      <div className="flex flex-col flex-1 p-4 gap-3">

        {/* Nome + badge WCAG */}
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-semibold text-gray-900 truncate">{theme.name['pt-BR']}</h3>
          <span
            className="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider"
            style={{ background: effectiveTheme.secondary, color: effectiveTheme.onSecondary }}
          >
            {theme.wcag}
          </span>
        </div>

        {/* Conceito */}
        <p className="text-xs text-gray-500 -mt-1">{theme.concept['pt-BR']}</p>

        {/* Swatches do tema original */}
        <div className="flex items-center gap-1.5">
          <span aria-hidden className="inline-block h-4 w-4 rounded-full border border-gray-200"
            style={{ background: theme.primary }} />
          <span aria-hidden className="inline-block h-4 w-4 rounded-full border border-gray-200"
            style={{ background: theme.secondary }} />
          <span className="text-[11px] text-gray-400">
            {selectedPalette
              ? `→ ${activePal?.contrastRatio} WCAG ${activePal?.wcag}`
              : `Contraste ${theme.contrastRatio}`}
          </span>
        </div>

        {/* ── Seletor de temas escuros ── */}
        <div>
          <div className="mb-1.5 flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-gray-400">
            <Moon size={10} />
            Temas escuros
          </div>
          <div className="flex gap-1.5">
            {DARK_PALETTE_LIST.map((pal) => {
              const active = selectedPalette === pal.id;
              return (
                <button
                  key={pal.id}
                  type="button"
                  title={pal.description}
                  onClick={() => togglePalette(pal.id)}
                  aria-pressed={active}
                  className="flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-semibold transition-all"
                  style={{
                    borderColor: active ? pal.primary : 'var(--theme-border, #e5e7eb)',
                    background: active ? pal.primary : 'transparent',
                    color: active ? pal.onPrimary : 'var(--theme-text-muted, #6b7280)',
                    boxShadow: active ? `0 0 0 2px ${pal.primary}33` : 'none',
                  }}
                >
                  {/* Mini-swatch 2 cores */}
                  <span className="flex rounded-sm overflow-hidden shrink-0"
                    style={{ width: 16, height: 12, border: `1px solid ${active ? pal.onPrimary + '40' : '#e5e7eb'}` }}>
                    <span style={{ background: pal.swatches[0], flex: 1 }} />
                    <span style={{ background: pal.swatches[1], flex: 1 }} />
                  </span>
                  {pal.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* ── Ações ── */}
        <div className="flex items-center gap-2 mt-auto">
          <button
            className="btn-primary !py-1.5 flex-1 text-xs"
            onClick={() => onUse(selectedPalette)}
          >
            Usar este modelo
          </button>
          <button
            aria-label={isFavorited ? 'Remover dos favoritos' : 'Favoritar'}
            onClick={onFavorite}
            className={`rounded-md border p-1.5 transition ${
              isFavorited
                ? 'border-brand-500 bg-brand-50 text-brand-700'
                : 'border-gray-300 text-gray-500 hover:bg-gray-50'
            }`}
          >
            <Star size={16} fill={isFavorited ? 'currentColor' : 'none'} />
          </button>
        </div>
      </div>
    </article>
  );
}

// ── Aba de importação de currículo via IA ──────────────────────────────────────
//
// Fluxo principal (IA disponível):
//   1. Usuário seleciona o arquivo
//   2. Frontend envia multipart para POST /api/ai-import
//   3. Backend extrai texto (PDFBox/POI) + chama LLM + retorna JSON estruturado
//   4. Editor é pré-preenchido automaticamente
//
// Fallback (IA indisponível — backend retorna 501):
//   1. Frontend extrai texto localmente (extractTextFromFile)
//   2. Parser regex client-side (parseResumeText) preenche o editor
//   3. Usuário vê aviso "importação básica sem IA"
//
function ImportTab({ router }) {
  const setImportedData = useResumeImportStore((s) => s.setImportedData);
  const accessToken     = useAuthStore((s) => s.accessToken);
  const fileRef = useRef(null);

  // status: idle | uploading | ai-processing | extracting | extracted | success | error
  const [status,      setStatus]      = useState('idle');
  const [fileName,    setFileName]    = useState('');
  const [fileObj,     setFileObj]     = useState(null);
  const [rawText,     setRawText]     = useState('');
  const [errorMsg,    setErrorMsg]    = useState('');
  // Default = 'onyxExecutive' (variante 'minimal', sem <header>/<aside> coloridos)
  // — evita o layout cair em navyClassic/crimsonImpact/forestPro/magentaVivid/
  // roseBold/lilacSoft, cujas variantes header/band/split tem cabecalho invisivel
  // quando o preview herda [data-theme] header/aside do app-shell (ver globals.css).
  const [layoutId,    setLayoutId]    = useState('onyxExecutive');
  const [usedAI,      setUsedAI]      = useState(false);
  // aiFailReason: null | 'no-key' | 'offline' | 'auth' | 'server' | 'unknown'
  const [aiFailReason, setAiFailReason] = useState(null);
  // LGPD Art. 9 / GDPR Art. 44-49: o currículo (dado pessoal) sai para um provedor de
  // IA externo — precisa de consentimento explícito e específico a cada envio, não
  // um aceite genérico dado uma vez no cadastro.
  const [aiConsent, setAiConsent] = useState(false);

  // ── Passo 1: arquivo selecionado → tenta enviar para IA ───────────────────
  async function handleFile(file) {
    if (!file) return;
    if (!aiConsent) {
      setErrorMsg('Marque a autorização de envio à IA abaixo antes de selecionar o arquivo.');
      setStatus('error');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setErrorMsg('Arquivo excede 10 MB.');
      setStatus('error');
      return;
    }

    setFileName(file.name);
    setFileObj(file);
    setErrorMsg('');
    setUsedAI(false);
    setAiFailReason(null);
    setRawText('');

    // Tenta pipeline de IA primeiro
    await tryAIImport(file);
  }

  // ── Pipeline de IA (backend) ────────────────────────────────────────────────
  async function tryAIImport(file) {
    setStatus('ai-processing');
    setAiFailReason(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('aiConsent', String(aiConsent));

      // apiFetchForm adiciona automaticamente Authorization + X-XSRF-TOKEN (CSRF)
      // e credentials: 'include' — evita o 403 CSRF que o fetch() raw causava.
      const parsed = await apiFetchForm('/api/ai-import', formData);

      setImportedData(parsed);
      setUsedAI(true);
      setStatus('success');
      // Redireciona após breve feedback visual
      setTimeout(() => router.push(`/dashboard/editor?layout=${layoutId}`), 1200);

    } catch (e) {
      // Classifica a causa da falha para mostrar mensagem específica ao usuário
      if (e.status === 501) {
        // IA não configurada no servidor (api-key vazia) → fallback esperado em dev
        console.info('[Import] IA não disponível (501) — chave de API não configurada');
        setAiFailReason('no-key');
      } else if (!e.status || e instanceof TypeError) {
        // Erro de rede: backend offline, CORS bloqueado, proxy caído
        console.warn('[Import] Sem resposta do backend — verifique se está rodando:', e.message);
        setAiFailReason('offline');
      } else if (e.status === 401 || e.status === 403) {
        // Problema de autenticação / CSRF
        console.warn('[Import] Erro de autenticação ao chamar IA:', e.status, e.message);
        setAiFailReason('auth');
      } else if (e.status >= 500) {
        // Erro interno do servidor (ex: OCR falhou, JSON inválido do LLM)
        console.warn('[Import] Erro interno do servidor ao processar IA:', e.status, e.message);
        setAiFailReason('server');
      } else {
        console.warn('[Import] Falha inesperada na IA:', e.status, e.message);
        setAiFailReason('unknown');
      }
      await fallbackClientSide(file);
    }
  }

  // ── Fallback: extração + parser client-side ─────────────────────────────────
  async function fallbackClientSide(file) {
    setStatus('extracting');
    try {
      const text = await extractTextFromFile(file);
      setRawText(text);
      setStatus(text.trim() ? 'extracted' : 'error');
      if (!text.trim()) {
        const isPdf  = file.name.toLowerCase().endsWith('.pdf');
        const isDocx = file.name.toLowerCase().endsWith('.docx');
        if (isPdf || isDocx) {
          setErrorMsg(
            `${isPdf ? 'PDF' : 'DOCX'} comprimido — extração local não é possível sem IA. ` +
            'Cole o texto do currículo na caixa abaixo ou configure a IA no servidor.'
          );
        } else {
          setErrorMsg('Não foi possível extrair texto. Cole o conteúdo manualmente abaixo.');
        }
      }
    } catch (e) {
      setErrorMsg(e.message || 'Erro ao processar arquivo.');
      setStatus('error');
    }
  }

  // ── Passo 2: importar texto manualmente (fallback) ──────────────────────────
  function handleManualImport() {
    const parsed = parseResumeText(rawText);
    setImportedData(parsed);
    router.push(`/dashboard/editor?layout=${layoutId}`);
  }

  // ── Retry com IA quando já temos o arquivo ──────────────────────────────────
  async function handleRetryAI() {
    if (fileObj) await tryAIImport(fileObj);
  }

  // ── Render ──────────────────────────────────────────────────────────────────
  const isProcessing = status === 'ai-processing' || status === 'uploading' || status === 'extracting';

  return (
    <div className="card p-6 max-w-2xl">
      <div className="mb-5 flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100">
          <Sparkles size={18} className="text-brand-700" />
        </div>
        <div>
          <h2 className="text-lg font-semibold leading-snug">Importar currículo com IA</h2>
          <p className="mt-0.5 text-sm text-gray-600">
            Envie um <strong>PDF</strong>, <strong>DOCX</strong> ou <strong>TXT</strong> e a IA extrai e estrutura os dados automaticamente.
          </p>
        </div>
      </div>

      {/* Consentimento LGPD/GDPR: o conteúdo do currículo sai para um provedor de IA
          externo — precisa ser um opt-in explícito, não implícito ao soltar o arquivo. */}
      <label className="mb-4 flex items-start gap-2 rounded-md border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700">
        <input
          type="checkbox"
          checked={aiConsent}
          onChange={(e) => setAiConsent(e.target.checked)}
          className="mt-0.5 h-4 w-4 rounded border-gray-300 text-brand-600 focus:ring-brand-500"
        />
        <span>
          Autorizo o envio do conteúdo do meu currículo a um provedor de IA externo para extração
          automática dos dados. Consulte os{' '}
          <Link href="/termos" target="_blank" className="text-brand-700 underline">
            Termos de Uso e a Política de Privacidade
          </Link>{' '}
          para saber quais dados são enviados.
        </span>
      </label>

      {/* Drop zone */}
      <div
        className={`mb-4 flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-8 text-center transition
          ${!aiConsent
            ? 'cursor-not-allowed border-gray-200 bg-gray-50 opacity-60'
            : isProcessing
              ? 'cursor-wait border-brand-300 bg-brand-50'
              : 'cursor-pointer border-gray-300 bg-gray-50 hover:border-brand-500 hover:bg-brand-50'}`}
        onClick={() => aiConsent && !isProcessing && fileRef.current?.click()}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => { e.preventDefault(); if (aiConsent && !isProcessing) handleFile(e.dataTransfer.files?.[0]); }}
      >
        {isProcessing ? (
          <>
            <Loader2 size={32} className="mb-2 animate-spin text-brand-500" />
            <p className="text-sm font-medium text-brand-700">
              {status === 'ai-processing' ? '✶ IA analisando o currículo...' : 'Extraindo texto...'}
            </p>
            <p className="text-xs text-brand-400 mt-1">Isso pode levar alguns segundos</p>
          </>
        ) : (
          <>
            <Upload size={32} className="mb-2 text-gray-400" />
            <p className="text-sm font-medium text-gray-700">
              {fileName ? fileName : 'Arraste o arquivo aqui ou clique para selecionar'}
            </p>
            <p className="text-xs text-gray-400 mt-1">PDF, DOCX ou TXT · máx. 10 MB</p>
          </>
        )}
        <input ref={fileRef} type="file" accept=".pdf,.docx,.txt" className="sr-only"
          onChange={(e) => handleFile(e.target.files?.[0])} />
      </div>

      {/* Sucesso com IA */}
      {status === 'success' && (
        <div className="mb-4 flex items-center gap-2 rounded-md bg-green-50 p-3 text-sm text-green-800">
          <CheckCircle2 size={16} className="shrink-0" />
          <span>
            <strong>Importação com IA concluída!</strong> Redirecionando para o editor...
          </span>
        </div>
      )}

      {/* Aviso de fallback — mensagem específica por causa */}
      {status === 'extracted' && (() => {
        const reasons = {
          'no-key': {
            title: 'Importação básica — IA sem chave de API.',
            detail: 'Configure cvfacil.ai.api-key em application-local.yml para ativar o parsing com LLM.',
          },
          'offline': {
            title: 'Importação básica — backend não respondeu.',
            detail: 'Verifique se o backend está rodando (INICIAR.bat) e tente novamente.',
          },
          'auth': {
            title: 'Importação básica — erro de autenticação.',
            detail: 'Faça login novamente ou recarregue a página.',
          },
          'server': {
            title: 'Importação básica — erro interno no servidor.',
            detail: 'O processamento da IA falhou. Verifique os logs do backend.',
          },
          'unknown': {
            title: 'Importação básica — IA indisponível.',
            detail: 'Tente novamente. Se persistir, consulte os logs do backend.',
          },
        };
        const r = reasons[aiFailReason] ?? reasons['unknown'];
        return (
          <div className="mb-3 rounded-md bg-amber-50 border border-amber-200 p-3 text-sm text-amber-800">
            <div className="flex items-start gap-2">
              <AlertCircle size={15} className="shrink-0 mt-0.5" />
              <div>
                <strong>{r.title}</strong>{' '}
                <span className="text-amber-700">{r.detail}</span>
                <span className="block mt-1 text-amber-600">
                  Revise o texto abaixo e corrija se necessário antes de importar.
                </span>
                {fileObj && aiFailReason !== 'no-key' && (
                  <button onClick={handleRetryAI} className="mt-1 underline text-amber-700 hover:text-amber-900">
                    Tentar com IA novamente
                  </button>
                )}
              </div>
            </div>
          </div>
        );
      })()}

      {/* Erro */}
      {status === 'error' && (
        <div className="mb-4 flex items-start gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700">
          <AlertCircle size={16} className="shrink-0 mt-0.5" />
          <span>{errorMsg || 'Erro ao processar arquivo.'}</span>
        </div>
      )}

      {/* Textarea para fallback/manual */}
      {(status === 'extracted' || status === 'error') && (
        <div className="mb-4">
          <textarea
            className="w-full rounded-md border border-gray-300 p-3 text-xs font-mono focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            rows={12}
            value={rawText}
            onChange={(e) => { setRawText(e.target.value); setStatus('extracted'); }}
            placeholder="Cole aqui o texto do seu currículo..."
          />
        </div>
      )}

      {/* Controles de layout + importar (fallback) */}
      {(status === 'extracted' || (status === 'error' && rawText.trim())) && (
        <div className="flex flex-wrap items-center gap-3">
          <select
            value={layoutId}
            onChange={(e) => setLayoutId(e.target.value)}
            className="rounded-md border border-gray-300 py-2 pl-3 pr-8 text-sm"
          >
            {LAYOUTS.map(l => (
              <option key={l.id} value={l.id}>{l.theme.name['pt-BR']}</option>
            ))}
          </select>
          <button onClick={handleManualImport} className="btn-primary flex items-center gap-2">
            <FileText size={16} /> Importar para o editor
          </button>
        </div>
      )}

      {/* Texto manual quando idle */}
      {status === 'idle' && (
        <div>
          <p className="mb-2 text-xs text-gray-500">
            Ou cole o texto do seu currículo diretamente e a IA irá estruturá-lo:
          </p>
          <textarea
            className="w-full rounded-md border border-gray-300 p-3 text-xs font-mono focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 mb-3"
            rows={8}
            value={rawText}
            onChange={(e) => setRawText(e.target.value)}
            placeholder="Cole aqui o texto do seu currículo..."
          />
          <button
            type="button"
            disabled={!rawText.trim()}
            onClick={() => {
              // BUG CORRIGIDO: antes o onChange ja marcava status='extracted' ao digitar,
              // pulando direto para o fallback local sem nunca chamar a IA — a mensagem
              // "IA indisponivel" aparecia mesmo sem a IA ter sido tentada. Agora o texto
              // colado passa pelo mesmo pipeline tryAIImport() do upload de arquivo.
              const file = new File([rawText], 'colado.txt', { type: 'text/plain' });
              setFileName(file.name);
              setFileObj(file);
              tryAIImport(file);
            }}
            className="btn-primary flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Sparkles size={16} /> Importar com IA
          </button>
        </div>
      )}
    </div>
  );
}
