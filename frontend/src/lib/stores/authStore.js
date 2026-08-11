import { create } from 'zustand';
import { useResumeListStore } from './resumeListStore';

/**
 * Store mínimo para credenciais em memória.
 * JWT NUNCA é persistido em localStorage (XSS risk).
 * Refresh token fica em cookie httpOnly, invisível para JS.
 *
 * `hydrated` indica que SessionHydrator já tentou restaurar a sessão.
 * Páginas protegidas devem aguardar hydrated=true antes de redirecionar.
 */
export const useAuthStore = create((set) => ({
  user: null,
  accessToken: null,
  hydrated: false,
  setSession: (user, accessToken) => set({ user, accessToken }),
  clearSession: () => {
    set({ user: null, accessToken: null });
    // resumeListStore persiste currículos (PII: nome, contato, foto) em
    // localStorage e sobrevivia ao logout — visível para qualquer pessoa com
    // acesso ao navegador depois que o usuário "saiu". O persist middleware
    // do Zustand sobrescreve o localStorage automaticamente ao limpar o state.
    useResumeListStore.setState({ resumes: [] });
  },
  setHydrated: () => set({ hydrated: true }),
}));
