'use client';

import { useState, useEffect, useRef } from 'react';
import { Palette, X, Check } from 'lucide-react';
import { useThemeStore, THEMES } from '@/lib/stores/themeStore';

/**
 * ThemeSwitcher — Botão cortina para troca de tema.
 *
 * Renderiza um botão flutuante fixo no canto inferior direito da tela.
 * Ao clicar, um painel desliza para dentro vindo da direita, exibindo
 * as opções de tema: Claro, Escuro, Azul Escuro e Verde Escuro.
 */
export function ThemeSwitcher() {
  const [open, setOpen]   = useState(false);
  const theme             = useThemeStore((s) => s.theme);
  const setTheme          = useThemeStore((s) => s.setTheme);
  const panelRef          = useRef(null);

  // Fecha ao clicar fora do painel
  useEffect(() => {
    if (!open) return;
    function handleClick(e) {
      if (panelRef.current && !panelRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  // Fecha com Escape
  useEffect(() => {
    if (!open) return;
    function handleKey(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [open]);

  const currentTheme = THEMES.find((t) => t.id === theme) ?? THEMES[0];

  return (
    <>
      {/* ── Overlay escurecido (mobile) ── */}
      <div
        aria-hidden
        className={`fixed inset-0 z-40 bg-black/30 backdrop-blur-sm transition-opacity duration-300 md:hidden ${
          open ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
        onClick={() => setOpen(false)}
      />

      {/* ── Painel cortina (desliza da direita) ── */}
      <div
        ref={panelRef}
        role="dialog"
        aria-label="Selecionar tema"
        aria-modal="true"
        className={`fixed bottom-20 right-0 z-50 w-72 rounded-l-2xl border-y border-l shadow-2xl transition-transform duration-300 ease-in-out ${
          open ? 'translate-x-0' : 'translate-x-full'
        }`}
        style={{
          background: 'var(--theme-bg-card)',
          borderColor: 'var(--theme-border)',
        }}
      >
        {/* Cabeçalho do painel */}
        <div
          className="flex items-center justify-between border-b px-4 py-3"
          style={{ borderColor: 'var(--theme-border)' }}
        >
          <div className="flex items-center gap-2">
            <Palette size={16} style={{ color: 'var(--theme-brand)' }} />
            <span
              className="text-sm font-semibold"
              style={{ color: 'var(--theme-text)' }}
            >
              Aparência
            </span>
          </div>
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label="Fechar painel de temas"
            className="rounded-md p-1 transition hover:bg-gray-100"
            style={{ color: 'var(--theme-text-muted)' }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Lista de temas */}
        <div className="p-3 space-y-2">
          {THEMES.map((t) => {
            const active = t.id === theme;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => { setTheme(t.id); }}
                className="w-full flex items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition"
                style={{
                  background: active ? 'var(--theme-brand-accent)' : 'transparent',
                  borderColor: active ? 'var(--theme-brand)' : 'var(--theme-border)',
                }}
              >
                {/* Prévia de cores */}
                <div className="flex shrink-0 gap-1 rounded-lg overflow-hidden border"
                  style={{ borderColor: 'var(--theme-border)', width: 48, height: 36 }}
                >
                  <div style={{ background: t.preview[0], flex: 1 }} />
                  <div style={{ background: t.preview[1], flex: 1 }} />
                  <div style={{ background: t.preview[2], width: 8 }} />
                </div>

                {/* Textos */}
                <div className="flex-1 min-w-0">
                  <p
                    className="text-sm font-semibold truncate"
                    style={{ color: active ? 'var(--theme-brand)' : 'var(--theme-text)' }}
                  >
                    {t.label}
                  </p>
                  <p
                    className="text-xs truncate"
                    style={{ color: 'var(--theme-text-subtle)' }}
                  >
                    {t.description}
                  </p>
                </div>

                {/* Check ativo */}
                {active && (
                  <Check
                    size={16}
                    className="shrink-0"
                    style={{ color: 'var(--theme-brand)' }}
                  />
                )}
              </button>
            );
          })}
        </div>

        {/* Rodapé informativo */}
        <div
          className="border-t px-4 py-2"
          style={{ borderColor: 'var(--theme-border)' }}
        >
          <p className="text-[10px]" style={{ color: 'var(--theme-text-subtle)' }}>
            Tema salvo automaticamente para a próxima visita.
          </p>
        </div>
      </div>

      {/* ── Botão flutuante (gatilho da cortina) ── */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? 'Fechar seletor de tema' : 'Abrir seletor de tema'}
        aria-expanded={open}
        className="fixed bottom-6 right-6 z-50 flex h-12 w-12 items-center justify-center rounded-full shadow-lg transition-all duration-200 hover:scale-110 focus:outline-none focus:ring-2 focus:ring-offset-2 print:hidden"
        style={{
          background: 'var(--theme-brand)',
          color: 'var(--theme-bg)',
          focusRingColor: 'var(--theme-brand)',
        }}
        title={`Tema atual: ${currentTheme.label}`}
      >
        {/* Mini-prévia da cor do tema no botão */}
        <Palette size={20} />
      </button>
    </>
  );
}
