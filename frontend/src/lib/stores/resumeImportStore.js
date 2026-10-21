import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

/**
 * Guarda dados extraídos de um arquivo importado (PDF / DOCX / TXT).
 * O editor os consome uma única vez e limpa o store (ver dashboard/editor/page.jsx).
 *
 * Persistido em sessionStorage (não só em memória): o dashboard grava aqui e navega para
 * /dashboard/editor logo em seguida — se essa navegação coincidir com uma recompilação do
 * Next.js (comum em dev, na primeira visita à rota do editor numa sessão) ou qualquer reload
 * de página nesse meio-tempo, um store só-em-memória perde o dado antes do editor conseguir
 * lê-lo, e o formulário abre vazio mesmo com a importação tendo funcionado. sessionStorage
 * (não localStorage) porque o dado é descartável — some ao fechar a aba e não deve sobreviver
 * além do próprio fluxo de importação.
 */
export const useResumeImportStore = create(
  persist(
    (set) => ({
      importedData: null,
      setImportedData: (data) => set({ importedData: data }),
      clearImportedData: () => set({ importedData: null }),
    }),
    {
      name: 'cvfacil-resume-import',
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
