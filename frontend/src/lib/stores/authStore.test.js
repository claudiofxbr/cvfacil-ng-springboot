import { useAuthStore } from './authStore';
import { useResumeListStore } from './resumeListStore';
import { api } from '@/lib/apiClient';

jest.mock('@/lib/apiClient', () => ({
  api: { get: jest.fn() },
}));

describe('authStore x resumeListStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null, hydrated: false });
    useResumeListStore.setState({ resumes: [] });
    api.get.mockReset();
  });

  it('setSession reconcilia resumeListStore com o backend', async () => {
    api.get.mockResolvedValue([
      { id: '1', layoutId: 'modern', content: '{"fullName":"Ana"}', updatedAt: '2026-01-01' },
    ]);

    useAuthStore.getState().setSession({ id: 'u1' }, 'jwt-token');

    expect(useAuthStore.getState().user).toEqual({ id: 'u1' });
    expect(useAuthStore.getState().accessToken).toBe('jwt-token');

    // syncFromBackend é disparado sem await dentro de setSession
    await Promise.resolve();
    await Promise.resolve();

    expect(api.get).toHaveBeenCalledWith('/api/resumes');
    expect(useResumeListStore.getState().resumes).toEqual([
      { id: '1', layoutId: 'modern', data: { fullName: 'Ana' }, name: 'Ana', savedAt: '2026-01-01' },
    ]);
  });

  it('clearSession apaga o token e os curriculos persistidos (PII)', () => {
    useResumeListStore.setState({
      resumes: [{ id: '1', layoutId: 'modern', data: {}, name: 'Ana', savedAt: '2026-01-01' }],
    });
    useAuthStore.setState({ user: { id: 'u1' }, accessToken: 'jwt-token' });

    useAuthStore.getState().clearSession();

    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useResumeListStore.getState().resumes).toEqual([]);
  });
});
