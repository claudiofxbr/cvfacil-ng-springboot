import { create } from 'zustand';

/**
 * Guarda dados extraídos de um arquivo importado (PDF / DOCX / TXT).
 * O editor os consome uma única vez e limpa o store.
 */
export const useResumeImportStore = create((set) => ({
  importedData: null,
  setImportedData: (data) => set({ importedData: data }),
  clearImportedData: () => set({ importedData: null }),
}));
