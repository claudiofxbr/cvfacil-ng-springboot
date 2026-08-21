import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { api } from '@/lib/apiClient';
import { encryptedLocalStorage } from '@/lib/secureStorage';

/**
 * Armazena todos os currículos criados/salvos pelo usuário.
 *
 * Estratégia: write-through + localStorage fallback.
 *  - Toda operação de escrita é refletida imediatamente no state local (otimista),
 *    e em seguida sincronizada com o backend via /api/resumes.
 *  - Se o backend não estiver disponível (network error, 401), a operação local
 *    permanece — o usuário não perde trabalho.
 *  - O localStorage persiste a lista entre recargas; `syncFromBackend()` deve ser
 *    chamado após login para reconciliar múltiplos dispositivos.
 *
 * Formato de cada entrada em memória:
 *   { id, layoutId, data, savedAt, name }
 * onde `data` é o objeto JavaScript com os campos do currículo.
 */
export const useResumeListStore = create(
  persist(
    (set, get) => ({
      resumes: [],

      /**
       * Salva ou atualiza um currículo local e sincroniza com o backend.
       * Retorna o id final (gerado pelo backend no primeiro save).
       *
       * A função é async mas pode ser chamada sem await — o estado otimista
       * já está aplicado antes da primeira yield.
       */
      saveResume: async ({ id, layoutId, data }) => {
        const now = new Date().toISOString();
        const name = data.fullName?.trim() || 'Sem nome';
        const content = JSON.stringify(data);

        // ── atualização otimista local ────────────────────────────────────
        let finalId = id;
        const existing = get().resumes.findIndex((r) => r.id === id);
        if (existing >= 0) {
          set((s) => ({
            resumes: s.resumes.map((r, i) =>
              i === existing ? { ...r, layoutId, data, name, savedAt: now } : r
            ),
          }));
        } else {
          finalId = id || (typeof crypto !== 'undefined'
            ? crypto.randomUUID()
            : `${Date.now()}`);
          set((s) => ({
            resumes: [{ id: finalId, layoutId, data, name, savedAt: now }, ...s.resumes],
          }));
        }

        // ── sincronização com o backend ───────────────────────────────────
        try {
          const payload = {
            layoutId,
            locale: data.locale || 'pt-BR',
            content,
            photoUrl: data.photoUrl || null,
          };

          let saved = null;
          if (existing >= 0) {
            // Currículo já existia localmente → tenta atualizar no backend
            try {
              saved = await api.put(`/api/resumes/${finalId}`, payload);
            } catch (e) {
              if (e.status === 404) {
                // Backend não conhece este id (ex: localStorage de outro device) → cria
                saved = await api.post('/api/resumes', payload);
              } else if (e.status === 402) {
                // Sem créditos: já não deveria ocorrer em update (currículo já existe),
                // mas propaga mesmo assim — o editor decide como avisar o usuário.
                throw e;
              }
              // 401 → não autenticado, mantém apenas local
            }
          } else {
            // Novo currículo → cria no backend
            try {
              saved = await api.post('/api/resumes', payload);
            } catch (e) {
              // 402 = sem créditos: o currículo fica só local (não sincroniza) até o
              // usuário comprar mais créditos — precisa chegar ao editor para avisar,
              // diferente das demais falhas (rede, 401), que continuam silenciosas.
              if (e.status === 402) throw e;
            }
          }

          if (saved?.id && saved.id !== finalId) {
            // Backend atribuiu id diferente → atualiza o id local
            set((s) => ({
              resumes: s.resumes.map((r) =>
                r.id === finalId ? { ...r, id: saved.id } : r
              ),
            }));
            finalId = saved.id;
          }
        } catch (e) {
          // 402 (sem créditos) precisa avisar o usuário — repassa ao chamador.
          // Demais falhas (rede, 401): silenciosas, dado já está salvo localmente.
          if (e.status === 402) throw e;
        }

        return finalId;
      },

      /**
       * Remove um currículo do estado local e do backend.
       */
      deleteResume: async (id) => {
        // Remoção otimista: remove local antes de aguardar o backend
        set((s) => ({ resumes: s.resumes.filter((r) => r.id !== id) }));
        try {
          await api.delete(`/api/resumes/${id}`);
        } catch {
          // Falha silenciosa — já removido localmente
        }
      },

      getResume: (id) => get().resumes.find((r) => r.id === id) || null,

      /**
       * Carrega a lista de currículos do backend e substitui o estado local.
       * Deve ser chamado após login para sincronizar múltiplos dispositivos.
       */
      syncFromBackend: async () => {
        try {
          const list = await api.get('/api/resumes');
          if (!Array.isArray(list)) return;
          const mapped = list.map((r) => {
            let data = {};
            try { data = JSON.parse(r.content || '{}'); } catch { /* mantém {} */ }
            return {
              id: r.id,
              layoutId: r.layoutId,
              data,
              name: data.fullName?.trim() || 'Sem nome',
              savedAt: r.updatedAt,
            };
          });
          set({ resumes: mapped });
        } catch {
          // Falha silenciosa — usa lista local existente
        }
      },
    }),
    {
      name: 'cvfacil-resumes',
      // Currículos contêm PII (nome, contato, foto) — criptografados em repouso no
      // localStorage; ver lib/secureStorage.js para o modelo de ameaça.
      storage: createJSONStorage(() => encryptedLocalStorage),
    }
  )
);
