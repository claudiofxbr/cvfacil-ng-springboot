'use client';

import { SAMPLE_RESUME } from '@/components/layouts/sampleData';
import { CorporateBlueSplit } from '@/components/layouts/CorporateBlueSplit';
import { FashionEditorialDark } from '@/components/layouts/FashionEditorialDark';
import { ConcreteEditorialGrayscale } from '@/components/layouts/ConcreteEditorialGrayscale';
import { LegalBlackPills } from '@/components/layouts/LegalBlackPills';
import { NavySidebarEngineer } from '@/components/layouts/NavySidebarEngineer';
import { TealRoundedCircles } from '@/components/layouts/TealRoundedCircles';

/**
 * Mapa `variant` → componente de template fixo. Cada um dos 6 templates
 * novos (ver `components/layouts/index.js`) usa seu próprio id como
 * `variant`, então este mapa é 1:1 com `LAYOUT_IDS` de `lib/theme.js`.
 */
const TEMPLATES = {
  'corporate-blue-split': CorporateBlueSplit,
  'fashion-editorial-dark': FashionEditorialDark,
  'concrete-editorial-grayscale': ConcreteEditorialGrayscale,
  'legal-black-pills': LegalBlackPills,
  'navy-sidebar-engineer': NavySidebarEngineer,
  'teal-rounded-circles': TealRoundedCircles,
};

const DEFAULT_TEMPLATE = CorporateBlueSplit;

/**
 * ResumeLayout — casca comum (escala, moldura, CSS de impressão) que
 * delega o conteúdo visual a um dos 6 templates fixos, escolhido por
 * `variant`. `variant` desconhecido (ex.: currículo salvo com id do sistema
 * antigo de 9 layouts, já removido) cai no primeiro template como rede de
 * segurança, igual ao padrão de `getLayout`/`getTheme` em `lib/theme.js`.
 */
export function ResumeLayout({ theme, variant, data = SAMPLE_RESUME, scale = 1 }) {
  const Template = TEMPLATES[variant] || DEFAULT_TEMPLATE;

  const style = {
    '--rl-primary':      theme.primary,
    '--rl-secondary':    theme.secondary,
    '--rl-on-primary':   theme.onPrimary,
    '--rl-on-secondary': theme.onSecondary,
  };

  return (
    <article
      className="resume-layout"
      style={{ ...style, transform: `scale(${scale})`, transformOrigin: 'top left' }}
      data-variant={variant}
      data-theme={theme.id}
    >
      <div className="rl-root">
        <Template theme={theme} data={data} />
      </div>

      <style jsx>{`
        .resume-layout { width: 600px; min-height: 800px; font-family: Inter, system-ui, sans-serif; }
        .rl-root {
          width: 600px; min-height: 800px; background: #fff; color: #111827;
          border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.08);
        }

        /* ── Impressão: remove overflow que cortava seções longas ── */
        @media print {
          :global(.resume-layout) {
            width: 100% !important;
            transform: none !important;
            border-radius: 0 !important;
            page-break-inside: avoid;
          }
          :global(.rl-root) {
            overflow: visible !important;
            box-shadow: none !important;
            border-radius: 0 !important;
            width: 100% !important;
            min-height: auto !important;
          }
          :global(.rl-root > *) {
            overflow: visible !important;
          }
        }
      `}</style>
    </article>
  );
}
