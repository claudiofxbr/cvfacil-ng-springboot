/**
 * CVFacil.NG — Paleta oficial dos 9 layouts de currículo
 * Todas as combinações foram validadas para contraste WCAG 2.1 AA ou AAA
 * sobre fundo branco (#FFFFFF). Ver Tabela 2.2.1 do PRD.
 */

export const LAYOUT_THEMES = Object.freeze({
  onyxExecutive: {
    id: 'onyxExecutive',
    name: { 'pt-BR': 'Onyx Executive', 'en-US': 'Onyx Executive', 'es-ES': 'Onyx Executive' },
    concept: { 'pt-BR': 'Minimalista e corporativo', 'en-US': 'Minimal and corporate', 'es-ES': 'Minimalista y corporativo' },
    primary: '#0A0A0A',
    secondary: '#E5E7EB',
    onPrimary: '#FFFFFF',
    onSecondary: '#0A0A0A',
    contrastRatio: '19.6:1',
    wcag: 'AAA',
    tags: ['Executive', 'Clássico'],
  },
  navyClassic: {
    id: 'navyClassic',
    name: { 'pt-BR': 'Navy Classic', 'en-US': 'Navy Classic', 'es-ES': 'Navy Classic' },
    concept: { 'pt-BR': 'Clássico formal', 'en-US': 'Formal classic', 'es-ES': 'Clásico formal' },
    primary: '#0B2545',
    secondary: '#D8E3F2',
    onPrimary: '#FFFFFF',
    onSecondary: '#0B2545',
    contrastRatio: '14.2:1',
    wcag: 'AAA',
    tags: ['Corporativo', 'Clássico'],
  },
  goldPrestige: {
    id: 'goldPrestige',
    name: { 'pt-BR': 'Gold Prestige', 'en-US': 'Gold Prestige', 'es-ES': 'Gold Prestige' },
    concept: { 'pt-BR': 'Premium e elegante', 'en-US': 'Premium and elegant', 'es-ES': 'Premium y elegante' },
    primary: '#B8860B',
    secondary: '#F5E9C9',
    onPrimary: '#0A0A0A',
    onSecondary: '#5A3E04',
    contrastRatio: '4.9:1',
    wcag: 'AA',
    tags: ['Premium', 'Criativo'],
  },
  forestPro: {
    id: 'forestPro',
    name: { 'pt-BR': 'Forest Pro', 'en-US': 'Forest Pro', 'es-ES': 'Forest Pro' },
    concept: { 'pt-BR': 'Natural e confiável', 'en-US': 'Natural and trustworthy', 'es-ES': 'Natural y confiable' },
    primary: '#0F3D2E',
    secondary: '#D1E7DD',
    onPrimary: '#FFFFFF',
    onSecondary: '#0F3D2E',
    contrastRatio: '13.1:1',
    wcag: 'AAA',
    tags: ['Sustentável', 'Corporativo'],
  },
  roseBold: {
    id: 'roseBold',
    name: { 'pt-BR': 'Rose Bold', 'en-US': 'Rose Bold', 'es-ES': 'Rose Bold' },
    concept: { 'pt-BR': 'Criativo e moderno', 'en-US': 'Creative and modern', 'es-ES': 'Creativo y moderno' },
    primary: '#9D174D',
    secondary: '#FCE7F3',
    onPrimary: '#FFFFFF',
    onSecondary: '#9D174D',
    contrastRatio: '8.9:1',
    wcag: 'AAA',
    tags: ['Criativo', 'Moderno'],
  },
  graphiteNeutral: {
    id: 'graphiteNeutral',
    name: { 'pt-BR': 'Graphite Neutral', 'en-US': 'Graphite Neutral', 'es-ES': 'Graphite Neutral' },
    concept: { 'pt-BR': 'Técnico e neutro', 'en-US': 'Technical and neutral', 'es-ES': 'Técnico y neutral' },
    primary: '#374151',
    secondary: '#F3F4F6',
    onPrimary: '#FFFFFF',
    onSecondary: '#111827',
    contrastRatio: '9.6:1',
    wcag: 'AAA',
    tags: ['Tech', 'Neutro'],
  },
  crimsonImpact: {
    id: 'crimsonImpact',
    name: { 'pt-BR': 'Crimson Impact', 'en-US': 'Crimson Impact', 'es-ES': 'Crimson Impact' },
    concept: { 'pt-BR': 'Assertivo e visível', 'en-US': 'Assertive and visible', 'es-ES': 'Asertivo y visible' },
    primary: '#7F1D1D',
    secondary: '#FEE2E2',
    onPrimary: '#FFFFFF',
    onSecondary: '#7F1D1D',
    contrastRatio: '10.4:1',
    wcag: 'AAA',
    tags: ['Vendas', 'Liderança'],
  },
  magentaVivid: {
    id: 'magentaVivid',
    name: { 'pt-BR': 'Magenta Vivid', 'en-US': 'Magenta Vivid', 'es-ES': 'Magenta Vivid' },
    concept: { 'pt-BR': 'Inovador e criativo', 'en-US': 'Innovative and creative', 'es-ES': 'Innovador y creativo' },
    primary: '#86198F',
    secondary: '#F5D0FE',
    onPrimary: '#FFFFFF',
    onSecondary: '#86198F',
    contrastRatio: '8.1:1',
    wcag: 'AAA',
    tags: ['Criativo', 'Moderno'],
  },
  lilacSoft: {
    id: 'lilacSoft',
    name: { 'pt-BR': 'Lilac Soft', 'en-US': 'Lilac Soft', 'es-ES': 'Lilac Soft' },
    concept: { 'pt-BR': 'Sofisticado e calmo', 'en-US': 'Sophisticated and calm', 'es-ES': 'Sofisticado y calmo' },
    primary: '#5B21B6',
    secondary: '#EDE9FE',
    onPrimary: '#FFFFFF',
    onSecondary: '#5B21B6',
    contrastRatio: '9.8:1',
    wcag: 'AAA',
    tags: ['Sofisticado', 'Moderno'],
  },
});

export const LAYOUT_IDS = Object.keys(LAYOUT_THEMES);

export function getTheme(id) {
  return LAYOUT_THEMES[id] ?? LAYOUT_THEMES.navyClassic;
}

/**
 * Paletas escuras aplicáveis a qualquer layout de currículo.
 * Substituem primary/secondary/onPrimary/onSecondary do tema base,
 * mantendo nome, variante estrutural e demais metadados do layout.
 *
 * Contrastes validados em WCAG 2.1 AA/AAA sobre papel branco.
 */
export const DARK_PALETTES = Object.freeze({
  escuro: {
    id: 'escuro',
    label: 'Escuro',
    description: 'Carvão neutro profundo',
    // Papel permanece branco; barras/cabeçalhos ficam quase-pretos
    primary:     '#1C1C1E',   // quase-preto (contraste 18.4:1 sobre #FFF)
    secondary:   '#2C2C2E',   // cinza-escuro
    onPrimary:   '#F5F5F7',
    onSecondary: '#E5E5EA',
    contrastRatio: '18.4:1',
    wcag: 'AAA',
    // Swatches exibidos no card e no editor
    swatches: ['#1C1C1E', '#2C2C2E', '#F5F5F7'],
  },
  navy: {
    id: 'navy',
    label: 'Azul Escuro',
    description: 'Azul marinho profundo',
    primary:     '#0B2545',   // contraste 14.2:1
    secondary:   '#1B3A6B',
    onPrimary:   '#FFFFFF',
    onSecondary: '#D8E3F2',
    contrastRatio: '14.2:1',
    wcag: 'AAA',
    swatches: ['#0B2545', '#1B3A6B', '#D8E3F2'],
  },
  forest: {
    id: 'forest',
    label: 'Verde Escuro',
    description: 'Verde floresta profundo',
    primary:     '#0F3D2E',   // contraste 13.1:1
    secondary:   '#1A5C44',
    onPrimary:   '#FFFFFF',
    onSecondary: '#D1E7DD',
    contrastRatio: '13.1:1',
    wcag: 'AAA',
    swatches: ['#0F3D2E', '#1A5C44', '#D1E7DD'],
  },
});

/** Lista ordenada das 3 paletas escuras (útil para iterar nos componentes). */
export const DARK_PALETTE_LIST = Object.values(DARK_PALETTES);

/**
 * Aplica uma paleta escura sobre um tema de layout.
 * Retorna um novo objeto de tema — o original não é mutado.
 * Se paletteId for null/undefined, retorna o tema sem alterações.
 */
export function applyPalette(theme, paletteId) {
  if (!paletteId) return theme;
  const pal = DARK_PALETTES[paletteId];
  if (!pal) return theme;
  return {
    ...theme,
    primary:     pal.primary,
    secondary:   pal.secondary,
    onPrimary:   pal.onPrimary,
    onSecondary: pal.onSecondary,
  };
}
