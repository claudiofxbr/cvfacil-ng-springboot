import Link from 'next/link';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';

// Versao 1.0 — deve acompanhar AuthController.TERMS_VERSION (backend). Ao publicar
// uma nova versao (mudanca material de finalidade/compartilhamento de dados),
// incrementar os dois em conjunto.
export const TERMS_VERSION = '1.0';

export default function TermsPage() {
  return (
    <>
      <Header />
      <main className="container-page py-12">
        <FadeIn>
          <div className="mx-auto max-w-2xl">
            <h1 className="mb-1 font-display text-2xl font-bold text-gray-900">
              Termos de Uso e Política de Privacidade
            </h1>
            <p className="mb-8 text-sm text-gray-500">
              Versão {TERMS_VERSION} — este documento resume, em linguagem simples, como o
              CVFacil.NG trata os dados que você fornece. Não substitui aconselhamento jurídico;
              deve ser revisado por um profissional antes de valer como termo legal definitivo.
            </p>

            <div className="space-y-6 text-sm leading-relaxed text-gray-700">
              <section>
                <h2 className="mb-2 font-semibold text-gray-900">1. Dados que coletamos</h2>
                <p>
                  Nome, e-mail, senha (armazenada como hash, nunca em texto puro) e o conteúdo do
                  currículo que você cria ou importa (histórico profissional, formação, contato,
                  foto). O conteúdo do currículo é armazenado cifrado (AES-256-GCM).
                </p>
              </section>

              <section>
                <h2 className="mb-2 font-semibold text-gray-900">2. Para que usamos</h2>
                <p>
                  Para criar, editar e exportar seu currículo, autenticar seu acesso e, quando você
                  autorizar especificamente, extrair automaticamente os dados de um arquivo que
                  você envia através de um provedor de inteligência artificial externo.
                </p>
              </section>

              <section>
                <h2 className="mb-2 font-semibold text-gray-900">
                  3. Compartilhamento com terceiros
                </h2>
                <p>
                  Ao usar a importação por IA, o conteúdo do arquivo enviado é transmitido a um
                  provedor externo (OpenAI, Anthropic ou Google, conforme configurado no servidor)
                  para processamento — isso só ocorre mediante sua autorização explícita naquela
                  tela, a cada envio. Login via Google também compartilha seu identificador OAuth
                  com o Google.
                </p>
              </section>

              <section>
                <h2 className="mb-2 font-semibold text-gray-900">4. Seus direitos</h2>
                <p>
                  Você pode solicitar acesso, correção ou eliminação dos seus dados entrando em
                  contato com o suporte. Estamos trabalhando para disponibilizar essas opções
                  diretamente no painel.
                </p>
              </section>

              <section>
                <h2 className="mb-2 font-semibold text-gray-900">5. Segurança</h2>
                <p>
                  Usamos criptografia para dados sensíveis em repouso, autenticação multifator para
                  contas administrativas e limitação de tentativas de login, entre outras medidas
                  técnicas.
                </p>
              </section>
            </div>

            <p className="mt-8 text-sm text-gray-500">
              <Link href="/register" className="text-brand-700 hover:underline">
                Voltar ao cadastro
              </Link>
            </p>
          </div>
        </FadeIn>
      </main>
    </>
  );
}
