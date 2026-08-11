import { getRequestConfig } from 'next-intl/server';

export const SUPPORTED_LOCALES = ['pt-BR', 'en-US', 'es-ES'];
export const DEFAULT_LOCALE = process.env.NEXT_PUBLIC_DEFAULT_LOCALE || 'pt-BR';

export default getRequestConfig(async ({ locale }) => {
  const safe = SUPPORTED_LOCALES.includes(locale) ? locale : DEFAULT_LOCALE;
  const messages = (await import(`../locales/${safe}/common.json`)).default;
  return {
    messages,
    timeZone: 'UTC',
  };
});
