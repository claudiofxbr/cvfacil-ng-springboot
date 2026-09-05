'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ShieldCheck, ShieldOff, KeyRound, AlertTriangle, Copy, Check, Download, Trash2, RefreshCw } from 'lucide-react';
import { useAuthStore } from '@/lib/stores/authStore';
import { api } from '@/lib/apiClient';

function Card({ title, icon: Icon, children }) {
  return (
    <div className="card p-6">
      <div className="mb-4 flex items-center gap-2">
        <Icon size={18} className="text-brand-700" />
        <h2 className="font-display text-lg font-bold text-gray-900">{title}</h2>
      </div>
      {children}
    </div>
  );
}

// ── Ativação de MFA (TOTP) ──────────────────────────────────────────────────────
function MfaSection({ status, onChanged }) {
  const [setup, setSetup] = useState(null); // { secret, otpAuthUri }
  const [code, setCode] = useState('');
  const [disablePassword, setDisablePassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  async function startSetup() {
    setError('');
    setLoading(true);
    try {
      const resp = await api.post('/api/mfa/setup', {});
      setSetup(resp);
    } catch {
      setError('Não foi possível iniciar a ativação. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  async function confirmSetup(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/api/mfa/confirm', { code });
      setSetup(null);
      setCode('');
      onChanged();
    } catch {
      setError('Código inválido. Confira o app autenticador e tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  async function disable(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.post('/api/mfa/disable', { password: disablePassword });
      setDisablePassword('');
      onChanged();
    } catch {
      setError('Senha incorreta.');
    } finally {
      setLoading(false);
    }
  }

  function copySecret() {
    navigator.clipboard?.writeText(setup.secret);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  if (status.mfaEnabled) {
    return (
      <Card title="Autenticação em duas etapas (MFA)" icon={ShieldCheck}>
        <p className="mb-4 flex items-center gap-2 text-sm text-green-700">
          <ShieldCheck size={16} /> MFA está ativo nesta conta.
        </p>
        <form onSubmit={disable} className="flex items-end gap-2">
          <div className="flex-1">
            <label className="mb-1 block text-xs font-semibold text-gray-600">
              Senha para desativar
            </label>
            <input
              type="password"
              required
              value={disablePassword}
              onChange={(e) => setDisablePassword(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="flex items-center gap-1.5 rounded-md border border-red-200 px-3 py-2 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-60"
          >
            <ShieldOff size={14} /> Desativar
          </button>
        </form>
        {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
      </Card>
    );
  }

  return (
    <Card title="Autenticação em duas etapas (MFA)" icon={ShieldCheck}>
      {status.role === 'ROOT_MASTER' && (
        <p className="mb-4 flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700">
          <AlertTriangle size={14} className="shrink-0" />
          Obrigatório para contas RootMaster: excluir usuários e conceder créditos exige MFA ativo.
        </p>
      )}
      {!setup ? (
        <button onClick={startSetup} disabled={loading} className="btn-primary py-2 disabled:opacity-60">
          {loading ? 'Gerando...' : 'Ativar MFA'}
        </button>
      ) : (
        <form onSubmit={confirmSetup} className="space-y-3">
          <p className="text-sm text-gray-600">
            Adicione esta chave no seu app autenticador (Google Authenticator, Authy, 1Password...):
          </p>
          <div className="flex items-center gap-2">
            <code className="flex-1 break-all rounded-md bg-gray-100 px-3 py-2 text-xs">
              {setup.secret}
            </code>
            <button
              type="button"
              onClick={copySecret}
              title="Copiar"
              className="shrink-0 rounded-md border border-gray-200 p-2 text-gray-500 hover:bg-gray-50"
            >
              {copied ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
            </button>
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold text-gray-600">
              Código de 6 dígitos gerado pelo app
            </label>
            <input
              inputMode="numeric"
              maxLength={6}
              required
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              className="w-40 rounded-md border border-gray-300 px-3 py-2 text-center text-lg tracking-widest"
            />
          </div>
          {error && <p className="text-xs text-red-600">{error}</p>}
          <div className="flex gap-2">
            <button type="submit" disabled={loading || code.length !== 6} className="btn-primary py-2 disabled:opacity-60">
              Confirmar ativação
            </button>
            <button
              type="button"
              onClick={() => { setSetup(null); setCode(''); setError(''); }}
              className="rounded-md border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              Cancelar
            </button>
          </div>
        </form>
      )}
    </Card>
  );
}

// ── Troca de senha ──────────────────────────────────────────────────────────────
function ChangePasswordSection({ status }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const minLength = status.role === 'ROOT_MASTER' ? 16 : 10;

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setSuccess(false);
    if (form.newPassword.length < minLength) {
      setError(`A senha deve ter no mínimo ${minLength} caracteres.`);
      return;
    }
    setLoading(true);
    try {
      await api.post('/api/auth/change-password', form);
      setForm({ currentPassword: '', newPassword: '' });
      setSuccess(true);
    } catch (err) {
      setError(err.status === 422 ? 'Senha já usada recentemente ou fora da política — escolha outra.' : 'Senha atual incorreta.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Alterar senha" icon={KeyRound}>
      {status.rotationOverdue && (
        <p className="mb-4 flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700">
          <AlertTriangle size={14} className="shrink-0" />
          Sua senha tem {status.passwordAgeDays} dias — recomendamos trocar (rotação de 90 dias).
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-3">
        <div>
          <label className="mb-1 block text-xs font-semibold text-gray-600">Senha atual</label>
          <input
            type="password"
            required
            value={form.currentPassword}
            onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold text-gray-600">
            Nova senha (mín. {minLength} caracteres, com maiúscula/minúscula/número/símbolo)
          </label>
          <input
            type="password"
            required
            value={form.newPassword}
            onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        {error && <p className="text-xs text-red-600">{error}</p>}
        {success && <p className="text-xs text-green-600">Senha alterada com sucesso.</p>}
        <button type="submit" disabled={loading} className="btn-primary py-2 disabled:opacity-60">
          {loading ? 'Salvando...' : 'Salvar nova senha'}
        </button>
      </form>
    </Card>
  );
}

// ── Trocar a conta Google vinculada ao login ─────────────────────────────────────
// Fluxo: pede ao backend um cookie de curta duração (relink_state) amarrado ao
// usuário logado, depois manda o navegador direto para o Google — o callback
// OAuth (OAuth2LoginSuccessHandler) só enxerga esse cookie, não este fetch.
function GoogleAccountSection({ currentEmail }) {
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function startRelink() {
    setError('');
    setLoading(true);
    try {
      await api.post('/api/auth/google/relink/start', {});
      // relink=1 é o marcador exclusivo deste fluxo (ver RelinkCookieGuardFilter) — desde
      // que o login normal também passou a usar prompt=select_account, o guard não pode
      // mais usar esse parâmetro sozinho para diferenciar os dois fluxos.
      window.location.href = `${apiBase}/oauth2/authorization/google?prompt=select_account&relink=1`;
    } catch {
      setError('Não foi possível iniciar a troca. Tente novamente.');
      setLoading(false);
    }
  }

  return (
    <Card title="Conta Google vinculada" icon={RefreshCw}>
      <p className="mb-4 text-sm text-gray-600">
        E-mail de login atual: <span className="font-semibold text-gray-900">{currentEmail}</span>
      </p>
      <p className="mb-4 text-xs text-gray-500">
        Trocar a conta Google escolhe uma nova conta no seletor do Google e passa a usar o e-mail
        dela para o seu login — a sessão atual é encerrada e você precisa entrar de novo com a conta
        nova.
      </p>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <button onClick={startRelink} disabled={loading} className="btn-primary py-2 disabled:opacity-60">
        {loading ? 'Redirecionando...' : 'Trocar conta Google'}
      </button>
    </Card>
  );
}

// ── Exportar meus dados (LGPD Art. 18 / GDPR Art. 15-20) ────────────────────────
function ExportDataSection() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function onExport() {
    setError('');
    setLoading(true);
    try {
      const data = await api.get('/api/users/me/export');
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `cvfacil-meus-dados-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Não foi possível gerar a exportação. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Exportar meus dados" icon={Download}>
      <p className="mb-4 text-sm text-gray-600">
        Baixe uma cópia de tudo que temos sobre você: dados de perfil, currículos e histórico de
        créditos, em formato JSON.
      </p>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <button onClick={onExport} disabled={loading} className="btn-primary py-2 disabled:opacity-60">
        {loading ? 'Gerando...' : 'Baixar meus dados'}
      </button>
    </Card>
  );
}

// ── Excluir minha conta (LGPD Art. 18 / GDPR Art. 17 — direito ao esquecimento) ──
function DeleteAccountSection() {
  const router = useRouter();
  const clearSession = useAuthStore((s) => s.clearSession);
  const [confirmText, setConfirmText] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await api.delete('/api/users/me', { password: password || undefined });
      clearSession();
      router.push('/');
    } catch (err) {
      if (err.status === 401) setError('Senha incorreta.');
      else if (err.status === 409) setError('Contas Root não podem se autoexcluir por aqui.');
      else setError('Não foi possível excluir a conta. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Excluir minha conta" icon={Trash2}>
      <p className="mb-4 text-sm text-gray-600">
        Isso apaga permanentemente sua conta, currículos e histórico de créditos. Não pode ser
        desfeito.
      </p>
      <form onSubmit={onSubmit} className="space-y-3">
        <div>
          <label className="mb-1 block text-xs font-semibold text-gray-600">
            Senha (deixe em branco se você usa login com Google)
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold text-gray-600">
            Digite EXCLUIR para confirmar
          </label>
          <input
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        {error && <p className="text-xs text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={loading || confirmText !== 'EXCLUIR'}
          className="flex items-center gap-1.5 rounded-md border border-red-200 px-3 py-2 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-60"
        >
          <Trash2 size={14} /> Excluir minha conta
        </button>
      </form>
    </Card>
  );
}

export default function SecurityPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const hydrated = useAuthStore((s) => s.hydrated);
  const [status, setStatus] = useState(null);
  const [relinkNotice, setRelinkNotice] = useState(null); // { type: 'success'|'error', reason }

  useEffect(() => {
    if (hydrated && !user) router.push('/login');
  }, [hydrated, user, router]);

  useEffect(() => {
    if (user) loadStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const relink = params.get('relink');
    if (!relink) return;
    setRelinkNotice({ type: relink, reason: params.get('reason') });
    router.replace('/dashboard/security');
  }, [router]);

  async function loadStatus() {
    try {
      const resp = await api.get('/api/auth/security-status');
      setStatus(resp);
    } catch {
      setStatus(null);
    }
  }

  if (!user || !status) {
    return <main className="container-page py-8 text-sm text-gray-500">Carregando...</main>;
  }

  return (
    <main className="container-page max-w-2xl py-8 space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-gray-900">Segurança</h1>
        <p className="text-sm text-gray-500">Gerencie a autenticação em duas etapas e sua senha.</p>
      </div>
      {relinkNotice?.type === 'success' && (
        <p className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">
          Conta Google trocada com sucesso — login atualizado para {user.email}.
        </p>
      )}
      {relinkNotice?.type === 'error' && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
          {relinkNotice.reason === 'in_use'
            ? 'Essa conta Google já está vinculada a outro usuário do CVFacil.NG.'
            : 'Não foi possível trocar a conta Google. Tente novamente.'}
        </p>
      )}
      <MfaSection status={status} onChanged={loadStatus} />
      <GoogleAccountSection currentEmail={user.email} />
      <ChangePasswordSection status={status} />
      <ExportDataSection />
      {status.role !== 'ROOT_MASTER' && <DeleteAccountSection />}
    </main>
  );
}
