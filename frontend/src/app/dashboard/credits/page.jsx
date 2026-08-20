'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Coins, Gift, ShoppingCart, AlertTriangle, Copy, QrCode } from 'lucide-react';
import { useAuthStore } from '@/lib/stores/authStore';
import { api } from '@/lib/apiClient';

const PACKAGES = [
  { id: 'PACK_3', credits: 3, priceLabel: 'R$ 30,00' },
  { id: 'PACK_6', credits: 6, priceLabel: 'R$ 50,00' },
  { id: 'PACK_9', credits: 9, priceLabel: 'R$ 70,00' },
];

const TX_LABELS = {
  COURTESY: 'Cortesia (boas-vindas)',
  PURCHASE: 'Compra',
  ADMIN_GRANT: 'Concedido pelo administrador',
  CONSUMPTION: 'Uso na criação de currículo',
};

function formatDate(iso) {
  try {
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export default function CreditsPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const hydrated = useAuthStore((s) => s.hydrated);
  const [wallet, setWallet] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [buyingId, setBuyingId] = useState(null);
  const [taxId, setTaxId] = useState('');
  const [pendingOrder, setPendingOrder] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => {
    if (hydrated && !user) router.push('/login');
  }, [hydrated, user, router]);

  useEffect(() => {
    if (user) loadWallet();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  async function loadWallet() {
    setError('');
    try {
      const resp = await api.get('/api/credits/wallet');
      setWallet(resp);
    } catch {
      setError('Não foi possível carregar sua carteira de créditos.');
    }
  }

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  function startPollingWallet(balanceBefore) {
    if (pollRef.current) clearInterval(pollRef.current);
    let attempts = 0;
    pollRef.current = setInterval(async () => {
      attempts += 1;
      try {
        const resp = await api.get('/api/credits/wallet');
        setWallet(resp);
        if (resp.balance > balanceBefore) {
          setPendingOrder(null);
          setNotice('Pagamento confirmado! Créditos adicionados à sua conta.');
          clearInterval(pollRef.current);
        }
      } catch {
        // silencioso — tenta de novo no próximo tick
      }
      if (attempts >= 40) clearInterval(pollRef.current); // ~10 min a cada 15s
    }, 15000);
  }

  async function handleBuy(pkg) {
    if (!/^\d{11}$/.test(taxId)) {
      setError('Informe um CPF válido (11 dígitos) para gerar o Pix.');
      return;
    }
    setBuyingId(pkg.id);
    setNotice('');
    setError('');
    try {
      const resp = await api.post('/api/credits/purchase', { packageId: pkg.id, taxId });
      setPendingOrder({ ...resp, pkg });
      startPollingWallet(wallet.balance);
    } catch (err) {
      if (err.status === 501) {
        setNotice(
          'Pagamentos ainda não estão habilitados neste ambiente — o gateway de pagamento ' +
          '(PagSeguro) não foi configurado. Assim que estiver ativo, a compra dos pacotes ' +
          'abaixo será processada aqui automaticamente.'
        );
      } else if (err.status === 502) {
        setError('O PagSeguro está indisponível no momento. Tente novamente em instantes.');
      } else {
        setError('Não foi possível processar a compra. Tente novamente.');
      }
    } finally {
      setBuyingId(null);
    }
  }

  function copyPixCode() {
    if (pendingOrder?.qrCodeText) {
      navigator.clipboard?.writeText(pendingOrder.qrCodeText);
      setNotice('Código Pix copiado.');
    }
  }

  if (!user || !wallet) {
    return <main className="container-page py-8 text-sm text-gray-500">Carregando...</main>;
  }

  return (
    <main className="container-page max-w-2xl py-8 space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-gray-900">Créditos</h1>
        <p className="text-sm text-gray-500">
          1 crédito = 1 currículo criado. Compre pacotes conforme sua necessidade.
        </p>
      </div>

      <div className="card flex items-center gap-3 p-6">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
          <Coins size={22} />
        </span>
        <div>
          <p className="text-2xl font-bold text-gray-900">{wallet.unlimited ? '∞' : wallet.balance}</p>
          <p className="text-sm text-gray-500">
            {wallet.unlimited
              ? 'créditos ilimitados (Admin/Root)'
              : wallet.balance === 1 ? 'crédito disponível' : 'créditos disponíveis'}
          </p>
        </div>
      </div>

      {notice && (
        <div className="flex items-start gap-2 rounded-md bg-amber-50 px-3 py-2.5 text-sm text-amber-800">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          {notice}
        </div>
      )}
      {error && (
        <div className="rounded-md bg-red-50 px-3 py-2.5 text-sm text-red-700">{error}</div>
      )}

      {!wallet.unlimited && pendingOrder && (
        <div className="card flex flex-col items-center gap-3 p-6 text-center">
          <h2 className="flex items-center gap-2 font-display text-lg font-bold text-gray-900">
            <QrCode size={18} className="text-brand-700" /> Pague com Pix para confirmar
          </h2>
          <p className="text-sm text-gray-500">
            {pendingOrder.pkg.credits} créditos — {pendingOrder.pkg.priceLabel}
          </p>
          {pendingOrder.qrCodeImageUrl && (
            <img
              src={pendingOrder.qrCodeImageUrl}
              alt="QR Code Pix"
              className="h-48 w-48 rounded-md border border-gray-200"
            />
          )}
          {pendingOrder.qrCodeText && (
            <button
              onClick={copyPixCode}
              className="flex items-center gap-1 rounded-md border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-600 hover:bg-gray-50"
            >
              <Copy size={12} /> Copiar código Pix
            </button>
          )}
          <p className="text-xs text-gray-400">
            Assim que o pagamento for confirmado, os créditos aparecem aqui automaticamente.
          </p>
          <button
            onClick={() => setPendingOrder(null)}
            className="text-xs text-gray-400 underline"
          >
            Cancelar / fechar
          </button>
        </div>
      )}

      {!wallet.unlimited && !pendingOrder && (
        <div>
          <h2 className="mb-3 flex items-center gap-2 font-display text-lg font-bold text-gray-900">
            <ShoppingCart size={18} className="text-brand-700" /> Comprar créditos
          </h2>
          <div className="mb-4">
            <label className="mb-1 block text-xs font-semibold text-gray-500">
              CPF (obrigatório para gerar o Pix)
            </label>
            <input
              type="text"
              inputMode="numeric"
              maxLength={11}
              value={taxId}
              onChange={(e) => setTaxId(e.target.value.replace(/\D/g, ''))}
              placeholder="Somente números"
              className="w-full max-w-xs rounded-md border border-gray-200 px-3 py-2 text-sm"
            />
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {PACKAGES.map((pkg) => (
              <div key={pkg.id} className="card flex flex-col items-center gap-2 p-5 text-center">
                <p className="text-3xl font-bold text-gray-900">{pkg.credits}</p>
                <p className="text-xs uppercase tracking-wide text-gray-400">
                  {pkg.credits === 1 ? 'currículo' : 'currículos'}
                </p>
                <p className="mb-2 text-lg font-semibold text-brand-700">{pkg.priceLabel}</p>
                <button
                  onClick={() => handleBuy(pkg)}
                  disabled={buyingId === pkg.id}
                  className="btn-primary w-full py-2 text-sm disabled:opacity-60"
                >
                  {buyingId === pkg.id ? 'Gerando Pix...' : 'Comprar'}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div>
        <h2 className="mb-3 flex items-center gap-2 font-display text-lg font-bold text-gray-900">
          <Gift size={18} className="text-brand-700" /> Histórico
        </h2>
        {wallet.history.length === 0 ? (
          <p className="text-sm text-gray-400">Nenhuma movimentação ainda.</p>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-2.5">Tipo</th>
                  <th className="px-4 py-2.5 text-right">Qtd.</th>
                  <th className="px-4 py-2.5 text-right">Saldo após</th>
                  <th className="px-4 py-2.5">Data</th>
                </tr>
              </thead>
              <tbody>
                {wallet.history.map((tx) => (
                  <tr key={tx.id} className="border-b border-gray-100 last:border-0">
                    <td className="px-4 py-2.5 text-gray-700">{TX_LABELS[tx.type] || tx.type}</td>
                    <td className={`px-4 py-2.5 text-right font-semibold ${tx.amount >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {tx.amount >= 0 ? '+' : ''}{tx.amount}
                    </td>
                    <td className="px-4 py-2.5 text-right text-gray-500">{tx.balanceAfter}</td>
                    <td className="px-4 py-2.5 text-gray-400">{formatDate(tx.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </main>
  );
}
