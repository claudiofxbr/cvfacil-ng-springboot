import { LAYOUT_IDS, getTheme } from '@/lib/theme';
import { ResumeLayout } from '@/components/layouts/ResumeLayout';

/**
 * Os 6 templates fixos e independentes que substituem o antigo sistema de
 * 9 layouts (tema + variante paramétrica). Cada entrada é
 * `{ id, theme, variant }`:
 *  - `id`      — usado nas URLs (`?layout=...`), em `resume.layoutId` e nas
 *                listas/filtros da galeria (`dashboard/page.jsx`).
 *  - `theme`   — paleta de cores (`primary`/`secondary`/`onPrimary`/
 *                `onSecondary` + metadados de exibição) de `lib/theme.js`.
 *  - `variant` — chave usada por `ResumeLayout` para escolher qual dos 6
 *                componentes de template renderizar. Aqui é sempre igual a
 *                `id` (1 template fixo = 1 variante, não uma combinação
 *                tema×variante como no sistema antigo) — mantido como campo
 *                separado só para não exigir mudanças em `TemplateCard`/
 *                `EditorContent`, que já esperam `layout.variant`.
 *
 * Gerado a partir de `LAYOUT_IDS` (mesma fonte de verdade de `lib/theme.js`)
 * para os dois nunca ficarem dessincronizados.
 */
export const LAYOUTS = LAYOUT_IDS.map((id) => ({
  id,
  theme: getTheme(id),
  variant: id,
}));

/**
 * Layout neutro de fallback — rede de segurança para um id desconhecido
 * (ex.: link/currículo salvo com um layoutId do sistema antigo removido).
 * `getLayout` nunca deve retornar `undefined` e quebrar os consumidores
 * (`dashboard/page.jsx`, `dashboard/editor/page.jsx`) que assumem um objeto
 * `{ id, theme, variant }` válido. Não é um dos 6 templates novos.
 */
const FALLBACK_LAYOUT = Object.freeze({
  id: 'fallback',
  theme: getTheme(undefined),
  variant: 'fallback',
});

export function getLayout(id) {
  return LAYOUTS.find((l) => l.id === id) ?? LAYOUTS[0] ?? FALLBACK_LAYOUT;
}

export { ResumeLayout };
