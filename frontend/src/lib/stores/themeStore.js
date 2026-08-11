import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * Temas disponíveis na aplicação.
 * O valor é aplicado como data-theme no elemento <html>.
 * 'light' = padrão (sem atributo data-theme).
 */
export const THEMES = [
  {
    id: 'light',
    label: 'Claro',
    description: 'Tema padrão claro',
    bg: '#ffffff',
    accent: '#1F3A5F',
    preview: ['#ffffff', '#f3f4f6', '#1F3A5F'],
  },
  {
    id: 'dark',
    label: 'Escuro',
    description: 'Fundo neutro escuro',
    bg: '#111827',
    accent: '#93c5fd',
    preview: ['#111827', '#1f2937', '#93c5fd'],
  },
  {
    id: 'navy',
    label: 'Azul Escuro',
    description: 'Fundo azul marinho profundo',
    bg: '#0c1a2e',
    accent: '#60a5fa',
    preview: ['#0c1a2e', '#0f2447', '#60a5fa'],
  },
  {
    id: 'forest',
    label: 'Verde Escuro',
    description: 'Fundo verde floresta',
    bg: '#0a1a10',
    accent: '#4ade80',
    preview: ['#0a1a10', '#0f2918', '#4ade80'],
  },
];

export const useThemeStore = create(
  persist(
    (set) => ({
      theme: 'light',
      setTheme: (theme) => {
        set({ theme });
        // Aplica imediatamente no DOM
        if (theme === 'light') {
          document.documentElement.removeAttribute('data-theme');
        } else {
          document.documentElement.setAttribute('data-theme', theme);
        }
      },
    }),
    { name: 'cvfacil-theme' }
  )
);
