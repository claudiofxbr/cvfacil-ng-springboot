import { LAYOUT_THEMES } from '@/lib/theme';
import { ResumeLayout } from '@/components/layouts/ResumeLayout';

/**
 * Mapeamento: cada um dos 9 layouts combina um tema (paleta) + uma variante visual.
 * Isso garante 9 resultados distintos, cada um com sua paleta WCAG-validada.
 */
export const LAYOUTS = [
  { id: 'onyxExecutive', theme: LAYOUT_THEMES.onyxExecutive, variant: 'minimal' },
  { id: 'navyClassic', theme: LAYOUT_THEMES.navyClassic, variant: 'header' },
  { id: 'goldPrestige', theme: LAYOUT_THEMES.goldPrestige, variant: 'badge' },
  { id: 'forestPro', theme: LAYOUT_THEMES.forestPro, variant: 'band' },
  { id: 'roseBold', theme: LAYOUT_THEMES.roseBold, variant: 'split' },
  { id: 'graphiteNeutral', theme: LAYOUT_THEMES.graphiteNeutral, variant: 'minimal' },
  { id: 'crimsonImpact', theme: LAYOUT_THEMES.crimsonImpact, variant: 'header' },
  { id: 'magentaVivid', theme: LAYOUT_THEMES.magentaVivid, variant: 'band' },
  { id: 'lilacSoft', theme: LAYOUT_THEMES.lilacSoft, variant: 'split' },
];

export function getLayout(id) {
  return LAYOUTS.find((l) => l.id === id) ?? LAYOUTS[0];
}

export { ResumeLayout };
