/**
 * CVFacil.NG — Paletas dos 6 templates fixos de currículo.
 *
 * O antigo sistema paramétrico de 9 layouts (tema + variante) foi removido e
 * substituído por 6 templates fixos e independentes (componentes próprios em
 * `components/layouts/`, um por id). Cada entrada aqui descreve só a
 * identidade de cor do template — `primary`/`secondary`/`onPrimary`/
 * `onSecondary` são as duas cores que o sistema ortogonal de paletas escuras
 * (`DARK_PALETTES`/`applyPalette`, abaixo) pode sobrescrever; o resto do
 * design de cada template (fundos, gradientes, tipografia) fica embutido no
 * próprio componente e não muda com a paleta.
 *
 * `contrastRatio`/`wcag` são informativos (contraste de `primary` sobre
 * papel branco, aproximado) — mostrados como selo na galeria, não uma
 * certificação de acessibilidade estrita do template inteiro.
 */

export const LAYOUT_THEMES = Object.freeze({
  'corporate-blue-split': {
    id: 'corporate-blue-split',
    name: { 'pt-BR': 'Corporativo Azul Split', 'en-US': 'Corporate Blue Split', 'es-ES': 'Corporativo Azul Dividido' },
    concept: {
      'pt-BR': 'Header dividido com foto e gradiente azul — visual executivo e direto.',
      'en-US': 'Split header with photo and blue gradient — direct executive look.',
      'es-ES': 'Encabezado dividido con foto y degradado azul — look ejecutivo directo.',
    },
    primary: '#2C5AA0',
    secondary: '#1E3F73',
    onPrimary: '#FFFFFF',
    onSecondary: '#FFFFFF',
    contrastRatio: '6.8:1',
    wcag: 'AA',
    tags: ['corporativo', 'executivo', 'azul', 'foto', 'gestão'],
  },
  'fashion-editorial-dark': {
    id: 'fashion-editorial-dark',
    name: { 'pt-BR': 'Editorial Dark Fashion', 'en-US': 'Fashion Editorial Dark', 'es-ES': 'Editorial Moda Oscuro' },
    concept: {
      'pt-BR': 'Fundo escuro, nome vertical e acentos vermelhos — editorial de moda/criativo.',
      'en-US': 'Dark background, vertical name and red accents — fashion/creative editorial.',
      'es-ES': 'Fondo oscuro, nombre vertical y acentos rojos — editorial de moda/creativo.',
    },
    primary: '#E8342A',
    secondary: '#FFFFFF',
    onPrimary: '#FFFFFF',
    onSecondary: '#111111',
    contrastRatio: '4.2:1',
    wcag: 'AA',
    tags: ['criativo', 'moda', 'escuro', 'editorial', 'design'],
  },
  'concrete-editorial-grayscale': {
    id: 'concrete-editorial-grayscale',
    name: { 'pt-BR': 'Editorial Concreto Grayscale', 'en-US': 'Concrete Editorial Grayscale', 'es-ES': 'Editorial Concreto Escala de Grises' },
    concept: {
      'pt-BR': 'Tipografia enorme, foto em preto e branco, grid editorial em tons de cinza.',
      'en-US': 'Bold typography, black-and-white photo, editorial grayscale grid.',
      'es-ES': 'Tipografía enorme, foto en blanco y negro, grid editorial en grises.',
    },
    primary: '#111111',
    secondary: '#333333',
    onPrimary: '#FFFFFF',
    onSecondary: '#FFFFFF',
    contrastRatio: '18.9:1',
    wcag: 'AAA',
    tags: ['editorial', 'minimalista', 'preto e branco', 'tipografia', 'design'],
  },
  'legal-black-pills': {
    id: 'legal-black-pills',
    name: { 'pt-BR': 'Jurídico Black Pills', 'en-US': 'Legal Black Pills', 'es-ES': 'Legal Píldoras Negras' },
    concept: {
      'pt-BR': 'Cartões pretos arredondados e pílulas de data — sóbrio, ideal para carreiras jurídicas/formais.',
      'en-US': 'Rounded black cards and date pills — sober, ideal for legal/formal careers.',
      'es-ES': 'Tarjetas negras redondeadas y píldoras de fecha — sobrio, ideal para carreras legales.',
    },
    primary: '#0A0A0A',
    secondary: '#333333',
    onPrimary: '#FFFFFF',
    onSecondary: '#FFFFFF',
    contrastRatio: '20.2:1',
    wcag: 'AAA',
    tags: ['jurídico', 'formal', 'sóbrio', 'preto', 'advocacia'],
  },
  'navy-sidebar-engineer': {
    id: 'navy-sidebar-engineer',
    name: { 'pt-BR': 'Sidebar Navy Engenharia', 'en-US': 'Navy Sidebar Engineer', 'es-ES': 'Barra Lateral Naval Ingeniería' },
    concept: {
      'pt-BR': 'Sidebar azul-marinho com foto e contato, corpo claro — técnico e organizado.',
      'en-US': 'Navy sidebar with photo and contact, light body — technical and organized.',
      'es-ES': 'Barra lateral azul marino con foto y contacto, cuerpo claro — técnico y organizado.',
    },
    primary: '#1E3A5F',
    secondary: '#A9C6E0',
    onPrimary: '#FFFFFF',
    onSecondary: '#1E3A5F',
    contrastRatio: '11.5:1',
    wcag: 'AAA',
    tags: ['engenharia', 'técnico', 'sidebar', 'azul-marinho', 'ti'],
  },
  'teal-rounded-circles': {
    id: 'teal-rounded-circles',
    name: { 'pt-BR': 'Teal Círculos Arredondados', 'en-US': 'Teal Rounded Circles', 'es-ES': 'Verde Azulado Círculos Redondeados' },
    concept: {
      'pt-BR': 'Foto em moldura circular sobreposta, pílulas verde-azuladas — moderno e amigável.',
      'en-US': 'Overlapping circular photo frame, teal pills — modern and friendly.',
      'es-ES': 'Foto en marco circular superpuesto, píldoras verde azulado — moderno y amigable.',
    },
    primary: '#16413D',
    secondary: '#FFFFFF',
    onPrimary: '#FFFFFF',
    onSecondary: '#16413D',
    contrastRatio: '11.3:1',
    wcag: 'AAA',
    tags: ['moderno', 'amigável', 'verde-azulado', 'círculos', 'design'],
  },
});

export const LAYOUT_IDS = Object.keys(LAYOUT_THEMES);

/**
 * Tema neutro de fallback — rede de segurança para um id desconhecido (ex.:
 * link/currículo salvo com um layoutId do sistema antigo removido). Decisão:
 * `getLayout`/`getTheme` nunca devem lançar nem renderizar `undefined` para
 * esse caso. Não é um dos 6 templates novos.
 */
const FALLBACK_THEME = Object.freeze({
  id: 'fallback',
  name: { 'pt-BR': 'Padrão', 'en-US': 'Default', 'es-ES': 'Predeterminado' },
  concept: { 'pt-BR': '', 'en-US': '', 'es-ES': '' },
  primary: '#374151',
  secondary: '#F3F4F6',
  onPrimary: '#FFFFFF',
  onSecondary: '#111827',
  contrastRatio: '9.6:1',
  wcag: 'AAA',
  tags: [],
});

export function getTheme(id) {
  return LAYOUT_THEMES[id] ?? FALLBACK_THEME;
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
