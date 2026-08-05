export type AppLanguage = 'es' | 'en';

export interface SupportedLanguage {
  code: AppLanguage;
  locale: string;
  label: string;
}

export type TranslationParameters = Readonly<Record<string, string | number>>;
