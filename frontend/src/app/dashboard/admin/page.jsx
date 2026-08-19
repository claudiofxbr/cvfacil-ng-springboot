'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Users, Trash2, Coins, ShieldAlert, Download } from 'lucide-react';
import { useAuthStore } from '@/lib/stores/authStore';
import { api } from '@/lib/apiClient';

export default function AdminPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const hydrated = useAuthStore((s) => s.hydrated);
  const isRoot = user?.role === 'ROOT_MASTER';
  const isAdmin = isRoot || user?.role === 'ADMIN';

  const [users, setUsers] = useState(null);
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [creditTarget, setCreditTarget] = useState(null);
  const [creditAmount, setCreditAmount] = useState(3);
  const [busyId, setBusyId] = useState(null);
  const [exportingId, setExportingId] = useState(null);

  useEffect(() => {
    if (hydrated && !user) router.push('/login');
    else if (hydrated && user && !isAdmin) router.push('/dashboard');
  }, [hydrated, user, isAdmin, router]);

  useEffect(() => {
    if (isAdmin) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin]);

  async function load() {
    setError('');
    try {
      const [u, s] = await Promise.all([api.get('/api/admin/users'), api.get('/api/admin/stats')]);
      setUsers(u);
      setStats(s);
    } catch {
      setError('Não foi possível carregar os usuários.');
    }
  }

  async function handleDelete(id) {
    if (confirmDeleteId !== id) {
      setConfirmDeleteId(id);
      return;
    }
    setBusyId(id);
    setError('');
    try {
      await api.delete(`/api/admin/users/${id}`);
      setUsers((list) => list.filter((u) => u.id !== id));
    } catch (err) {
      setError(
        err.status === 403
          ? 'Ação bloqueada: ative o MFA em Segurança antes de excluir usuários.'
          : 'Não foi possível excluir este usuário.'
      );
    } finally {
      setBusyId(null);
      setConfirmDeleteId(null);
    }
  }

  async function handleExport(u) {
    setExportingId(u.id);
    setError('');
    try {
      const data = await api.get(`/api/admin/users/${u.id}/export`);
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cvfacil-dados-${u.email}-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Não foi possível exportar os dados deste usuário.');
    } finally {
      setExportingId(null);
    }
  }

  async function handleGrantCredits(e) {
    e.preventDefault();
    setBusyId(creditTarget.id);
    setError('');
    try {
      await api.post(`/api/admin/users/${creditTarget.id}/credits`, { amount: Number(creditAmount) });
      setCreditTarget(null);
      setCreditAmount(3);
    } catch (err) {
      setError(
        err.status === 403
          ? 'Ação bloqueada: ative o MFA em Segurança antes de conceder créditos.'
          : 'Não foi possível conceder créditos.'
      );
    } finally {
      setBusyId(null);
    }
  }

  if (!user || !isAdmin) {
    return <main className="container-page py-8 text-sm text-gray-500">Carregando...</main>;
  }

  return (
    <main className="container-page py-8">
      <div className="mb-6 flex items-center gap-2">
        <Users size={20} className="text-brand-700" />
        <h1 className="font-display text-2xl font-bold text-gray-900">Painel admin</h1>
      </div>

      {stats && (
        <p className="mb-4 text-sm text-gray-500">{stats.totalUsers} usuários cadastrados.</p>
      )}

      {error && (
        <div className="mb-4 flex items-center gap-2 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          <ShieldAlert size={15} className="shrink-0" /> {error}
        </div>
      )}

      {!isRoot && (
        <p className="mb-4 rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700">
          Como Admin, você pode visualizar usuários. Excluir contas e conceder créditos é
          exclusivo do RootMaster.
        </p>
      )}

      <div className="card overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b border-gray-200 bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
            <tr>
              <th className="px-4 py-3">Nome</th>
              <th className="px-4 py-3">E-mail</th>
              <th className="px-4 py-3">Papel</th>
              <th className="px-4 py-3">Criado em</th>
              <th className="px-4 py-3 text-right">Ações</th>
            </tr>
          </thead>
          <tbody>
            {users?.map((u) => (
              <tr key={u.id} className="border-b border-gray-100 last:border-0">
                <td className="px-4 py-3 font-medium text-gray-800">{u.displayName || '—'}</td>
                <td className="px-4 py-3 text-gray-600">{u.email}</td>
                <td className="px-4 py-3">
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700">
                    {u.role}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-500">
                  {new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' }).format(new Date(u.createdAt))}
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    <button
                      onClick={() => handleExport(u)}
                      disabled={exportingId === u.id}
                      className="flex items-center gap-1 rounded-md border border-gray-200 px-2 py-1 text-xs font-semibold text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                    >
                      <Download size={12} /> {exportingId === u.id ? 'Exportando...' : 'Exportar'}
                    </button>
                    {isRoot && (
                      <>
                        <button
                          onClick={() => { setCreditTarget(u); setCreditAmount(3); }}
                          className="flex items-center gap-1 rounded-md border border-gray-200 px-2 py-1 text-xs font-semibold text-gray-600 hover:bg-gray-50"
                        >
                          <Coins size={12} /> Créditos
                        </button>
                        {u.role !== 'ROOT_MASTER' && (
                          <button
                            onClick={() => handleDelete(u.id)}
                            disabled={busyId === u.id}
                            className={`flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-semibold disabled:opacity-60 ${
                              confirmDeleteId === u.id
                                ? 'border-red-400 bg-red-50 text-red-700'
                                : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                            }`}
                          >
                            <Trash2 size={12} /> {confirmDeleteId === u.id ? 'Confirmar' : 'Excluir'}
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {creditTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="mb-1 text-base font-bold text-gray-900">Conceder créditos</h3>
            <p className="mb-4 text-sm text-gray-500">{creditTarget.email}</p>
            <form onSubmit={handleGrantCredits} className="space-y-3">
              <input
                type="number"
                min={1}
                required
                value={creditAmount}
                onChange={(e) => setCreditAmount(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
              />
              <div className="flex gap-2">
                <button type="submit" disabled={busyId === creditTarget.id} className="btn-primary flex-1 py-2 disabled:opacity-60">
                  Conceder
                </button>
                <button
                  type="button"
                  onClick={() => setCreditTarget(null)}
                  className="rounded-md border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
                >
                  Cancelar
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
