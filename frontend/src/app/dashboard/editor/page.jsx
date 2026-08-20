'use client';

import { useState, useEffect, useRef, useCallback, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ChevronLeft, Plus, Trash2, Printer, Camera, Save, LayoutDashboard, Moon, FileDown, X } from 'lucide-react';
import { ResumeLayout, getLayout } from '@/components/layouts';
import { useAuthStore } from '@/lib/stores/authStore';
import { useResumeImportStore } from '@/lib/stores/resumeImportStore';
import { useResumeListStore } from '@/lib/stores/resumeListStore';
import { DARK_PALETTE_LIST, applyPalette } from '@/lib/theme';

// ── Estado inicial ─────────────────────────────────────────────────────────────
const EMPTY_RESUME = {
  fullName: '', headline: '', email: '', phone: '',
  location: '', website: '', summary: '', photo: '',
  skills: [],
  experience: [{ role: '', company: '', period: '', bullets: Array(10).fill('') }],
  education:  [{ degree: '', school: '', period: '' }],
  languages:  [{ name: '', level: '' }],
  hobbies: [],
};

// ── Helpers de estilo ──────────────────────────────────────────────────────────
function Field({ label, children, hint }) {
  return (
    <div className="mb-3">
      <label className="mb-1 block text-xs font-semibold text-gray-600 uppercase tracking-wide">{label}</label>
      {children}
      {hint && <p className="mt-1 text-[10px] text-gray-400">{hint}</p>}
    </div>
  );
}

const inputCls =
  'w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500';

const MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB — mesmo limite do PhotoPicker (aba Foto 3x4)

// ── Seção: Dados Pessoais ──────────────────────────────────────────────────────
function PersonalSection({ data, onChange }) {
  const photoRef = useRef(null);
  const [photoError, setPhotoError] = useState('');
  const set = (key) => (e) => onChange({ ...data, [key]: e.target.value });

  function handlePhotoFile(file) {
    setPhotoError('');
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setPhotoError('Formato inválido. Use JPEG, PNG ou WebP.');
      return;
    }
    if (file.size > MAX_PHOTO_SIZE_BYTES) {
      setPhotoError('Arquivo excede 5 MB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => onChange({ ...data, photo: e.target.result });
    reader.readAsDataURL(file);
  }

  return (
    <div>
      {/* Foto 3x4 */}
      <Field label="Foto 3x4 (opcional)">
        <div className="flex items-center gap-3">
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" className="rounded object-cover border border-gray-300"
              style={{ width: 48, height: 64, objectFit: 'cover' }} />
          ) : (
            <div className="flex items-center justify-center rounded border border-dashed border-gray-300 bg-gray-50"
              style={{ width: 48, height: 64 }}>
              <Camera size={16} className="text-gray-400" />
            </div>
          )}
          <div className="flex flex-col gap-1">
            <button type="button" onClick={() => photoRef.current?.click()}
              className="text-xs text-brand-700 hover:underline">
              {data.photo ? 'Trocar foto' : 'Adicionar foto'}
            </button>
            {data.photo && (
              <button type="button" onClick={() => onChange({ ...data, photo: '' })}
                className="text-xs text-red-500 hover:underline">Remover</button>
            )}
            <input ref={photoRef} type="file" accept="image/jpeg,image/png,image/webp"
              className="sr-only" onChange={(e) => handlePhotoFile(e.target.files?.[0])} />
          </div>
        </div>
        {photoError && <p role="alert" className="mt-1 text-xs text-red-600">{photoError}</p>}
      </Field>

      <Field label="Nome completo">
        <input className={inputCls} value={data.fullName} onChange={set('fullName')} placeholder="Ex.: Joao da Silva" />
      </Field>
      <Field label="Cargo / Titulo profissional">
        <input className={inputCls} value={data.headline} onChange={set('headline')} placeholder="Ex.: Engenheiro de Software Senior" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="E-mail">
          <input className={inputCls} type="email" value={data.email} onChange={set('email')} placeholder="joao@email.com" />
        </Field>
        <Field label="Telefone">
          <input className={inputCls} value={data.phone} onChange={set('phone')} placeholder="+55 11 99999-0000" />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Localizacao">
          <input className={inputCls} value={data.location} onChange={set('location')} placeholder="Sao Paulo, SP" />
        </Field>
        <Field label="Website / LinkedIn">
          <input className={inputCls} value={data.website} onChange={set('website')} placeholder="linkedin.com/in/joao" />
        </Field>
      </div>
    </div>
  );
}

// ── Seção: Resumo ──────────────────────────────────────────────────────────────
function SummarySection({ data, onChange }) {
  return (
    <Field label="Resumo profissional">
      <textarea className={`${inputCls} resize-none`} rows={5} value={data.summary}
        onChange={(e) => onChange({ ...data, summary: e.target.value })}
        placeholder="Descreva brevemente sua experiencia, especialidades e objetivos..." />
    </Field>
  );
}

// ── Seção: Experiência ─────────────────────────────────────────────────────────
function ExperienceSection({ data, onChange }) {
  function update(i, key, val) {
    onChange({ ...data, experience: data.experience.map((e, idx) => idx === i ? { ...e, [key]: val } : e) });
  }

  function onBulletsChange(i, rawText) {
    const lines = rawText.split('\n').slice(0, 10);
    const padded = [...lines, ...Array(10).fill('')].slice(0, 10);
    update(i, 'bullets', padded);
  }

  function bulletsToText(bullets) {
    const filled = (bullets || []).filter((b) => b.trim());
    return filled.join('\n');
  }

  function addExp() {
    onChange({ ...data, experience: [...data.experience, { role: '', company: '', period: '', bullets: Array(10).fill('') }] });
  }
  function removeExp(i) {
    const exp = data.experience.filter((_, idx) => idx !== i);
    onChange({ ...data, experience: exp.length ? exp : [{ role: '', company: '', period: '', bullets: Array(10).fill('') }] });
  }

  return (
    <div>
      {data.experience.map((exp, i) => (
        <div key={i} className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-bold text-gray-500">Experiencia {i + 1}</span>
            {data.experience.length > 1 && (
              <button onClick={() => removeExp(i)} className="text-red-400 hover:text-red-600"><Trash2 size={14} /></button>
            )}
          </div>
          <div className="grid grid-cols-2 gap-2 mb-2">
            <Field label="Cargo">
              <input className={inputCls} value={exp.role} onChange={(e) => update(i, 'role', e.target.value)} placeholder="Software Engineer" />
            </Field>
            <Field label="Empresa">
              <input className={inputCls} value={exp.company} onChange={(e) => update(i, 'company', e.target.value)} placeholder="Empresa S.A." />
            </Field>
          </div>
          <Field label="Periodo">
            <input className={inputCls} value={exp.period} onChange={(e) => update(i, 'period', e.target.value)} placeholder="2021 - hoje" />
          </Field>
          <div className="mt-2">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Realizacoes</span>
              <span className="text-[10px] text-gray-400">
                {(exp.bullets || []).filter((b) => b.trim()).length} / 10 linhas
              </span>
            </div>
            <textarea
              className={`${inputCls} resize-none font-normal`}
              rows={10}
              value={bulletsToText(exp.bullets)}
              onChange={(e) => onBulletsChange(i, e.target.value)}
              placeholder={'Linha 1: Liderou migracao para microsservicos...\nLinha 2: Reduziu latencia em 38%...\n...\n(ate 10 realizacoes, uma por linha)'}
            />
            <p className="mt-0.5 text-[10px] text-gray-400">
              Digite uma realizacao por linha. Maximo de 10 linhas.
            </p>
          </div>
        </div>
      ))}
      <button onClick={addExp} className="flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <Plus size={14} /> Adicionar experiencia
      </button>
    </div>
  );
}

// ── Seção: Formação ────────────────────────────────────────────────────────────
function EducationSection({ data, onChange }) {
  function update(i, key, val) {
    onChange({ ...data, education: data.education.map((e, idx) => idx === i ? { ...e, [key]: val } : e) });
  }
  function add() {
    onChange({ ...data, education: [...data.education, { degree: '', school: '', period: '' }] });
  }
  function remove(i) {
    const edu = data.education.filter((_, idx) => idx !== i);
    onChange({ ...data, education: edu.length ? edu : [{ degree: '', school: '', period: '' }] });
  }

  return (
    <div>
      {data.education.map((ed, i) => (
        <div key={i} className="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-bold text-gray-500">Formacao {i + 1}</span>
            {data.education.length > 1 && (
              <button onClick={() => remove(i)} className="text-red-400 hover:text-red-600"><Trash2 size={14} /></button>
            )}
          </div>
          <Field label="Curso / Grau">
            <input className={inputCls} value={ed.degree} onChange={(e) => update(i, 'degree', e.target.value)} placeholder="Bacharelado em Ciencia da Computacao" />
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field label="Instituicao">
              <input className={inputCls} value={ed.school} onChange={(e) => update(i, 'school', e.target.value)} placeholder="Universidade X" />
            </Field>
            <Field label="Periodo">
              <input className={inputCls} value={ed.period} onChange={(e) => update(i, 'period', e.target.value)} placeholder="2018 - 2022" />
            </Field>
          </div>
        </div>
      ))}
      <button onClick={add} className="flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <Plus size={14} /> Adicionar formacao
      </button>
    </div>
  );
}

// ── Seção: Habilidades ─────────────────────────────────────────────────────────
function SkillsSection({ data, onChange }) {
  const [name, setName] = useState('');
  const [pct,  setPct]  = useState(75);

  function addSkill() {
    const trimmed = name.trim();
    if (!trimmed) return;
    if (data.skills.some(s => (typeof s === 'string' ? s : s.name) === trimmed)) return;
    onChange({ ...data, skills: [...data.skills, { name: trimmed, pct }] });
    setName('');
    setPct(75);
  }

  function updatePct(idx, newPct) {
    const skills = data.skills.map((s, i) => {
      if (i !== idx) return s;
      return { name: typeof s === 'string' ? s : s.name, pct: newPct };
    });
    onChange({ ...data, skills });
  }

  function removeSkill(idx) {
    onChange({ ...data, skills: data.skills.filter((_, i) => i !== idx) });
  }

  return (
    <div>
      <div className="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
        <Field label="Nome da habilidade">
          <input className={inputCls} value={name} onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addSkill(); } }}
            placeholder="Ex.: React, Java, PostgreSQL..." />
        </Field>
        <Field label={`Nivel: ${pct}%`}>
          <input type="range" min={0} max={100} step={5} value={pct}
            onChange={(e) => setPct(Number(e.target.value))}
            className="w-full accent-brand-700" />
          <div className="flex justify-between text-[10px] text-gray-400 mt-0.5">
            <span>0%</span><span>50%</span><span>100%</span>
          </div>
        </Field>
        <button onClick={addSkill} className="btn-primary !py-1.5 !px-3 text-xs w-full">
          <Plus size={12} className="inline mr-1" />Adicionar habilidade
        </button>
      </div>
      <div className="space-y-2">
        {data.skills.map((s, idx) => {
          const skillName = typeof s === 'string' ? s : s.name;
          const skillPct  = typeof s === 'string' ? 75 : s.pct;
          return (
            <div key={idx} className="rounded-lg border border-gray-200 bg-white p-2">
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-medium text-gray-800">{skillName}</span>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-brand-700 w-8 text-right">{skillPct}%</span>
                  <button onClick={() => removeSkill(idx)} className="text-gray-400 hover:text-red-500">
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
              <input type="range" min={0} max={100} step={5} value={skillPct}
                onChange={(e) => updatePct(idx, Number(e.target.value))}
                className="w-full accent-brand-700" />
            </div>
          );
        })}
        {data.skills.length === 0 && (
          <p className="text-xs text-gray-400">Adicione habilidades tecnicas e comportamentais com seu nivel de proficiencia.</p>
        )}
      </div>
    </div>
  );
}

// ── Seção: Idiomas ─────────────────────────────────────────────────────────────
function LanguagesSection({ data, onChange }) {
  function update(i, key, val) {
    onChange({ ...data, languages: data.languages.map((l, idx) => idx === i ? { ...l, [key]: val } : l) });
  }
  function add() {
    onChange({ ...data, languages: [...data.languages, { name: '', level: '' }] });
  }
  function remove(i) {
    const langs = data.languages.filter((_, idx) => idx !== i);
    onChange({ ...data, languages: langs.length ? langs : [{ name: '', level: '' }] });
  }

  return (
    <div>
      <p className="mb-3 text-xs text-gray-500 rounded bg-blue-50 border border-blue-100 px-2 py-1.5">
        O nome do idioma deve ter minimo 15 caracteres. Ex.: Portugues Brasileiro, Ingles Americano (C1).
      </p>
      {data.languages.map((l, i) => (
        <div key={i} className="mb-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-gray-500">Idioma {i + 1}</span>
            {data.languages.length > 1 && (
              <button onClick={() => remove(i)} className="text-red-400 hover:text-red-600"><Trash2 size={14} /></button>
            )}
          </div>
          <Field label="Idioma">
            <input
              className={`${inputCls} ${l.name.length > 0 && l.name.length < 15 ? 'border-amber-400 focus:border-amber-500 focus:ring-amber-400' : ''}`}
              value={l.name}
              onChange={(e) => update(i, 'name', e.target.value)}
              placeholder="Portugues Brasileiro"
              minLength={15}
            />
            {l.name.length > 0 && l.name.length < 15 && (
              <p className="mt-0.5 text-[10px] text-amber-600">{l.name.length}/15 caracteres</p>
            )}
            {l.name.length >= 15 && (
              <p className="mt-0.5 text-[10px] text-green-600">check {l.name.length} caracteres</p>
            )}
          </Field>
          <Field label="Nivel">
            <select className={inputCls} value={l.level} onChange={(e) => update(i, 'level', e.target.value)}>
              <option value="">Selecione...</option>
              <option>Nativo</option>
              <option>Fluente</option>
              <option>Avancado</option>
              <option>Intermediario</option>
              <option>Basico</option>
            </select>
          </Field>
        </div>
      ))}
      <button onClick={add} className="flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <Plus size={14} /> Adicionar idioma
      </button>
    </div>
  );
}

// ── Seção: Hobbies ─────────────────────────────────────────────────────────────
function HobbiesSection({ data, onChange }) {
  const [input, setInput] = useState('');

  function add() {
    const t = input.trim();
    if (t && !data.hobbies.includes(t)) {
      onChange({ ...data, hobbies: [...data.hobbies, t] });
    }
    setInput('');
  }
  function remove(h) {
    onChange({ ...data, hobbies: data.hobbies.filter(x => x !== h) });
  }

  return (
    <div>
      <div className="flex gap-2 mb-3">
        <input
          className={`${inputCls} flex-1`}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); add(); } }}
          placeholder="Ex.: Fotografia, Corrida, Xadrez..."
        />
        <button onClick={add} className="btn-primary !py-1.5 !px-3 text-xs shrink-0">
          Adicionar
        </button>
      </div>
      <div className="flex flex-wrap gap-2">
        {data.hobbies.map((h) => (
          <span key={h} className="flex items-center gap-1 rounded-full bg-purple-100 px-3 py-1 text-xs text-purple-800">
            {h}
            <button onClick={() => remove(h)} className="text-purple-500 hover:text-red-500">x</button>
          </span>
        ))}
        {data.hobbies.length === 0 && (
          <p className="text-xs text-gray-400">Mostre seus interesses pessoais e hobbies.</p>
        )}
      </div>
    </div>
  );
}

// ── Editor principal ────────────────────────────────────────────────────────────
const SECTIONS = [
  { id: 'personal',   label: 'Dados'       },
  { id: 'summary',    label: 'Resumo'      },
  { id: 'experience', label: 'Experiencia' },
  { id: 'education',  label: 'Formacao'    },
  { id: 'skills',     label: 'Habilidades' },
  { id: 'languages',  label: 'Idiomas'     },
  { id: 'hobbies',    label: 'Hobbies'     },
];

// ── Modal pos-impressao ────────────────────────────────────────────────────────
function AfterPrintModal({ onDashboard, onContinue }) {
  return (
    <div className="after-print-modal fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl p-7 max-w-sm w-full mx-4 relative">
        <button
          onClick={onContinue}
          className="absolute top-3 right-3 rounded-full p-1 text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition"
          title="Fechar"
        >
          <X size={16} />
        </button>

        <div className="mb-4 flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-green-100">
            <FileDown size={20} className="text-green-600" />
          </span>
          <div>
            <h3 className="text-base font-bold text-gray-900">Curriculo exportado!</h3>
            <p className="text-xs text-gray-500">O que deseja fazer agora?</p>
          </div>
        </div>

        <div className="flex flex-col gap-2.5">
          <button
            onClick={onDashboard}
            className="flex items-center justify-center gap-2 w-full rounded-xl bg-brand-700 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-800 transition"
          >
            <LayoutDashboard size={15} />
            Voltar ao Painel
          </button>
          <button
            onClick={onContinue}
            className="flex items-center justify-center gap-2 w-full rounded-xl border border-gray-200 px-4 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-50 transition"
          >
            Continuar editando
          </button>
        </div>
      </div>
    </div>
  );
}

function EditorContent() {
  const router       = useRouter();
  const searchParams = useSearchParams();
  const user         = useAuthStore((s) => s.user);
  const hydrated     = useAuthStore((s) => s.hydrated);
  const imported     = useResumeImportStore((s) => s.importedData);
  const clearImport  = useResumeImportStore((s) => s.clearImportedData);
  const saveResume   = useResumeListStore((s) => s.saveResume);
  const getResume    = useResumeListStore((s) => s.getResume);

  const layoutId     = searchParams.get('layout') || 'navyClassic';
  const resumeId     = searchParams.get('resumeId') || null;
  const layout       = getLayout(layoutId);

  const initialPalette = searchParams.get('palette') || null;
  const [paletteId,     setPaletteId]     = useState(initialPalette);
  const effectiveTheme = applyPalette(layout.theme, paletteId);

  const [data,          setData]          = useState(EMPTY_RESUME);
  const [activeSection, setSection]       = useState('personal');
  const [savedId,       setSavedId]       = useState(resumeId);
  const [saveStatus,    setSaveStatus]    = useState('idle'); // 'idle' | 'saving' | 'saved'
  const [showAfterPrint, setShowAfterPrint] = useState(false);
  const [creditsError,  setCreditsError]  = useState(false);

  // Redireciona se nao autenticado
  useEffect(() => {
    if (hydrated && !user) router.push('/login');
  }, [hydrated, user, router]);

  // Carrega dados importados
  useEffect(() => {
    if (imported) { setData(imported); clearImport(); }
  }, [imported, clearImport]);

  // Carrega curriculo existente
  useEffect(() => {
    if (resumeId) {
      const saved = getResume(resumeId);
      if (saved) {
        setData(saved.data);
        setSavedId(resumeId);
        // A cor salva junto do currículo (data.paletteId) prevalece sobre a URL,
        // já que o dashboard não propaga ?palette= ao abrir um currículo existente.
        if (saved.data.paletteId !== undefined) setPaletteId(saved.data.paletteId);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId]);

  // ── Impressao com listener afterprint ─────────────────────────────────────────
  // Ao fechar o dialogo de impressao (imprimiu ou cancelou), exibe o modal
  // "Voltar ao Painel / Continuar editando".
  const triggerPrint = useCallback(() => {
    setShowAfterPrint(false);

    // Aguarda um tick para garantir que o modal nao apareca no print
    setTimeout(() => {
      const handler = () => {
        setShowAfterPrint(true);
        window.removeEventListener('afterprint', handler);
      };
      window.addEventListener('afterprint', handler);
      window.print();
    }, 50);
  }, []);

  // ── Salvar: persiste no store + exporta PDF ───────────────────────────────────
  // Correcao: o botao Salvar agora tambem abre o dialogo de impressao para
  // exportacao em PDF, conforme solicitado pelo usuario.
  async function handleSave() {
    setCreditsError(false);
    // 1. Persistir no store (localStorage) — a cor escolhida vai dentro de `data`
    // para sobreviver ao reload/reabertura do currículo (a URL sozinha não basta).
    // CORRECAO: saveResume é assíncrona (sincroniza com o backend) — sem o await,
    // `id` era a própria Promise, não o id real, e o erro 402 (sem créditos) era
    // engolido silenciosamente: o usuário perdia o trabalho sem nenhum aviso.
    const dataToSave = { ...data, paletteId };
    let id;
    try {
      id = await saveResume({ id: savedId, layoutId, data: dataToSave });
    } catch (e) {
      if (e.status === 402) {
        setCreditsError(true);
        return;
      }
      throw e;
    }
    setSavedId(id);
    setSaveStatus('saved');

    // 2. Atualizar URL com resumeId e palette
    const params = new URLSearchParams(searchParams.toString());
    params.set('resumeId', id);
    if (paletteId) params.set('palette', paletteId);
    else params.delete('palette');
    router.replace(`/dashboard/editor?${params.toString()}`, { scroll: false });
    setTimeout(() => setSaveStatus('idle'), 2500);

    // 3. Abrir dialogo de impressao para exportar como PDF
    triggerPrint();
  }

  function handlePaletteChange(id) {
    const newPalette = id === paletteId ? null : id;
    setPaletteId(newPalette);
    const params = new URLSearchParams(searchParams.toString());
    if (newPalette) params.set('palette', newPalette);
    else params.delete('palette');
    router.replace(`/dashboard/editor?${params.toString()}`, { scroll: false });
  }

  return (
    <>
      {/* ── Modal pos-impressao (aparece quando o dialogo de print fecha) ── */}
      {showAfterPrint && (
        <AfterPrintModal
          onDashboard={() => router.push('/dashboard')}
          onContinue={() => setShowAfterPrint(false)}
        />
      )}

      {/*
        CORRECAO DE IMPRESSAO:
        - "editor-shell": classe CSS identificadora para o @media print do globals.css
          (remove overflow-hidden e height:calc que cortavam o curriculo)
        - "editor-preview": idem para a area de preview
        As classes Tailwind nao podem ser sobrescritas por @media print externo
        porque o PostCSS as injeta com especificidade alta; usamos classes semanticas
        extras que o globals.css consegue sobrescrever com !important.
      */}
      <div className="editor-shell flex h-screen overflow-hidden">

        {/* ── Painel do formulario ── */}
        <aside className="flex w-[546px] shrink-0 flex-col border-r border-gray-200 bg-white">

          {/* Cabecalho: navegacao + acoes */}
          <div className="flex items-center justify-between border-b border-gray-200 px-3 py-2 print:hidden" data-print-hidden>
            <Link
              href="/dashboard"
              className="flex items-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-50 hover:text-gray-900 transition"
            >
              <LayoutDashboard size={13} />
              Painel
            </Link>

            <div className="text-center">
              <p className="text-xs font-semibold text-gray-800 leading-none">{layout.theme.name['pt-BR']}</p>
              <p className="text-[10px] text-gray-400 mt-0.5">
                {paletteId
                  ? DARK_PALETTE_LIST.find(p => p.id === paletteId)?.label
                  : 'Tema original'}
              </p>
            </div>

            <div className="flex items-center gap-1.5">
              {/* Botao Salvar: persiste no app E exporta como PDF */}
              <button
                onClick={handleSave}
                title="Salvar no app e exportar como PDF"
                className={`flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs font-semibold transition ${
                  saveStatus === 'saved'
                    ? 'bg-green-100 text-green-700 border border-green-300'
                    : 'border border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}
              >
                <Save size={13} />
                {saveStatus === 'saved' ? 'Salvo!' : 'Salvar PDF'}
              </button>

              {/* Botao Imprimir: identico ao Salvar PDF (salva + exporta) */}
              <button
                onClick={handleSave}
                title="Salvar e exportar como PDF"
                className="flex items-center gap-1 rounded-md bg-brand-700 px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-brand-800 transition"
              >
                <Printer size={13} /> Imprimir
              </button>
            </div>
          </div>

          {/* Aviso: sem créditos disponíveis para criar/salvar o currículo */}
          {creditsError && (
            <div className="flex items-center justify-between gap-2 border-b border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 print:hidden" data-print-hidden>
              <span>Você não tem créditos suficientes. O currículo ficou salvo só neste dispositivo.</span>
              <Link href="/dashboard/credits" className="shrink-0 font-semibold underline hover:no-underline">
                Comprar créditos
              </Link>
            </div>
          )}

          {/* Seletor de temas escuros */}
          <div className="flex items-center gap-2 border-b border-gray-200 bg-gray-50 px-3 py-1.5 print:hidden" data-print-hidden>
            <Moon size={11} className="shrink-0 text-gray-400" />
            <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400 shrink-0">
              Tema do curriculo:
            </span>
            <button
              type="button"
              onClick={() => handlePaletteChange(null)}
              className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold border transition ${
                !paletteId
                  ? 'border-brand-500 bg-brand-50 text-brand-700'
                  : 'border-gray-200 text-gray-500 hover:bg-gray-100'
              }`}
            >
              Original
            </button>
            {DARK_PALETTE_LIST.map((pal) => {
              const active = paletteId === pal.id;
              return (
                <button
                  key={pal.id}
                  type="button"
                  title={pal.description}
                  onClick={() => handlePaletteChange(pal.id)}
                  className="flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold border transition"
                  style={{
                    borderColor: active ? pal.primary : '#e5e7eb',
                    background:  active ? pal.primary : 'transparent',
                    color:       active ? pal.onPrimary : '#6b7280',
                  }}
                >
                  <span className="flex rounded-sm overflow-hidden"
                    style={{ width: 12, height: 9, border: `1px solid ${active ? pal.onPrimary + '50' : '#d1d5db'}` }}>
                    <span style={{ background: pal.swatches[0], flex: 1 }} />
                    <span style={{ background: pal.swatches[1], flex: 1 }} />
                  </span>
                  {pal.label}
                </button>
              );
            })}
          </div>

          {/* Abas de secoes */}
          <div className="flex gap-0 overflow-x-auto border-b border-gray-200 bg-gray-50 px-2 pt-2 print:hidden" data-print-hidden>
            {SECTIONS.map((s) => (
              <button key={s.id} onClick={() => setSection(s.id)}
                className={`shrink-0 rounded-t px-2.5 py-2 text-xs font-semibold transition ${
                  activeSection === s.id
                    ? 'border border-b-0 border-gray-200 bg-white text-brand-700'
                    : 'text-gray-500 hover:text-gray-800'
                }`}>{s.label}</button>
            ))}
          </div>

          {/* Conteudo da secao ativa */}
          <div className="flex-1 overflow-y-auto p-4 print:hidden" data-print-hidden>
            {activeSection === 'personal'   && <PersonalSection   data={data} onChange={setData} />}
            {activeSection === 'summary'    && <SummarySection    data={data} onChange={setData} />}
            {activeSection === 'experience' && <ExperienceSection data={data} onChange={setData} />}
            {activeSection === 'education'  && <EducationSection  data={data} onChange={setData} />}
            {activeSection === 'skills'     && <SkillsSection     data={data} onChange={setData} />}
            {activeSection === 'languages'  && <LanguagesSection  data={data} onChange={setData} />}
            {activeSection === 'hobbies'    && <HobbiesSection    data={data} onChange={setData} />}
          </div>

          {/* Navegacao entre abas */}
          <div className="flex items-center justify-between border-t border-gray-200 px-4 py-2 print:hidden" data-print-hidden>
            <button
              onClick={() => { const idx = SECTIONS.findIndex(s => s.id === activeSection); if (idx > 0) setSection(SECTIONS[idx-1].id); }}
              disabled={activeSection === SECTIONS[0].id}
              className="text-xs text-brand-700 hover:underline disabled:opacity-30">Anterior</button>
            <span className="text-xs text-gray-400">
              {SECTIONS.findIndex(s => s.id === activeSection) + 1} / {SECTIONS.length}
            </span>
            <button
              onClick={() => { const idx = SECTIONS.findIndex(s => s.id === activeSection); if (idx < SECTIONS.length-1) setSection(SECTIONS[idx+1].id); }}
              disabled={activeSection === SECTIONS[SECTIONS.length-1].id}
              className="text-xs text-brand-700 hover:underline disabled:opacity-30">Proximo</button>
          </div>
        </aside>

        {/* ── Area de preview do curriculo ── */}
        <main className="editor-preview flex flex-1 flex-col items-center overflow-y-auto bg-gray-100 p-8">
          <div className="print:hidden mb-4 text-center" data-print-hidden>
            <p className="text-xs text-gray-500">
              Preview em tempo real — clique em <strong>Salvar PDF</strong> para salvar e exportar
            </p>
          </div>

          {/* Wrapper do preview: remove escalonamento na impressao via CSS (.resume-preview-wrapper) */}
          <div
            className="resume-preview-wrapper origin-top shadow-lg"
            style={{ transform: 'scale(0.75)', transformOrigin: 'top center' }}
          >
            <ResumeLayout theme={effectiveTheme} variant={layout.variant} data={data} />
          </div>
        </main>
      </div>
    </>
  );
}

export default function EditorPage() {
  return (
    <Suspense fallback={
      <div className="flex h-screen items-center justify-center text-gray-500 text-sm">
        Carregando editor...
      </div>
    }>
      <EditorContent />
    </Suspense>
  );
}
