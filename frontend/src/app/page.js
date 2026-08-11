import Link from 'next/link';
import { Header } from '@/components/ui/Header';
import { FadeIn } from '@/components/ui/Motion';

export default function HomePage() {
  return (
    <>
      <Header />
      <main className="container-page py-16 md:py-24">
        <FadeIn>
          <section className="mx-auto max-w-3xl text-center">
            <p className="mb-4 text-sm font-semibold uppercase tracking-wider text-brand-500">
              CVFacil.NG
            </p>
            <h1 className="mb-6 font-display text-4xl font-bold text-gray-900 md:text-6xl">
              O currículo certo, no estilo que combina com você.
            </h1>
            <p className="mb-8 text-lg text-gray-600 md:text-xl">
              9 modelos modernos, importação por IA e acabamento profissional. Tudo em português,
              inglês ou espanhol.
            </p>
            <div className="flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
              <Link href="/login" className="btn-primary px-6 py-3 text-base">
                Começar agora
              </Link>
              <Link href="/dashboard" className="btn-secondary px-6 py-3 text-base">
                Ver modelos
              </Link>
            </div>
          </section>
        </FadeIn>

        <section className="mt-24 grid gap-8 md:grid-cols-3">
          <FeatureCard
            title="9 layouts modernos"
            text="Paletas validadas em WCAG AA/AAA. Do clássico Navy ao ousado Magenta Vivid."
          />
          <FeatureCard
            title="Importação por IA"
            text="Envie seu PDF ou DOCX atual e deixe a IA extrair automaticamente seus dados."
          />
          <FeatureCard
            title="Multi-idioma"
            text="Gere versões em português, inglês ou espanhol do mesmo currículo."
          />
        </section>
      </main>
    </>
  );
}

function FeatureCard({ title, text }) {
  return (
    <div className="card p-6">
      <h3 className="mb-2 text-lg font-semibold text-gray-900">{title}</h3>
      <p className="text-gray-600">{text}</p>
    </div>
  );
}
