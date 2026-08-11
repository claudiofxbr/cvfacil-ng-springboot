import '../styles/globals.css';
import { Providers } from '@/components/Providers';

export const metadata = {
  title: 'CVFacil.NG — Currículos profissionais',
  description:
    'Plataforma web para criação de currículos profissionais com 9 layouts modernos, i18n e importação assistida por IA.',
  applicationName: 'CVFacil.NG',
  robots: { index: true, follow: true },
};

export const viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#1F3A5F',
};

/**
 * Script inline que aplica o tema salvo ANTES da hidratação do React,
 * evitando o flash de tema errado (FOUC) ao recarregar a página.
 */
const THEME_SCRIPT = `
(function() {
  try {
    var stored = localStorage.getItem('cvfacil-theme');
    if (stored) {
      var data = JSON.parse(stored);
      var theme = data && data.state && data.state.theme;
      if (theme && theme !== 'light') {
        document.documentElement.setAttribute('data-theme', theme);
      }
    }
  } catch (e) {}
})();
`;

export default function RootLayout({ children }) {
  return (
    <html lang="pt-BR">
      {/* Script de tema executado antes do CSS/JS do React — elimina flash */}
      <head>
        {/* eslint-disable-next-line @next/next/no-before-interactive-script-outside-document */}
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
