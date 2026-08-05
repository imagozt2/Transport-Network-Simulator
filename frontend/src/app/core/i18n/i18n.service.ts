import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

import { AppLanguage, SupportedLanguage, TranslationParameters } from './i18n.model';
import { enTranslations } from './translations/en';
import {
  esTranslations,
  TranslationCatalog,
  TranslationKey
} from './translations/es';

const LANGUAGE_STORAGE_KEY = 'rmm.language';
const DEFAULT_LANGUAGE: AppLanguage = 'es';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly languageState = signal<AppLanguage>(this.readInitialLanguage());
  private readonly catalogs: Readonly<Record<AppLanguage, TranslationCatalog>> = {
    es: esTranslations,
    en: enTranslations
  };

  readonly language = this.languageState.asReadonly();
  readonly supportedLanguages: readonly SupportedLanguage[] = [
    { code: 'es', locale: 'es-ES', label: 'Español' },
    { code: 'en', locale: 'en-GB', label: 'English' }
  ];

  constructor() {
    this.synchronizeDocument(this.languageState());
  }

  setLanguage(language: AppLanguage): void {
    if (!this.isSupported(language)) {
      throw new Error(`Unsupported application language: ${language}`);
    }
    this.languageState.set(language);
    this.synchronizeDocument(language);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    }
  }

  translate(key: TranslationKey, parameters: TranslationParameters = {}): string {
    const activeCatalog = this.catalogs[this.languageState()];
    const template = activeCatalog[key] ?? this.catalogs[DEFAULT_LANGUAGE][key] ?? key;
    return template.replace(/{{\s*([\w.-]+)\s*}}/g, (placeholder, parameter: string) => {
      const value = parameters[parameter];
      return value === undefined ? placeholder : String(value);
    });
  }

  locale(): string {
    return this.supportedLanguages.find(({ code }) => code === this.languageState())?.locale
      ?? 'es-ES';
  }

  formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
    return new Intl.NumberFormat(this.locale(), options).format(value);
  }

  formatDate(value: Date | string, options?: Intl.DateTimeFormatOptions): string {
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return new Intl.DateTimeFormat(this.locale(), options).format(date);
  }

  private readInitialLanguage(): AppLanguage {
    if (!isPlatformBrowser(this.platformId)) return DEFAULT_LANGUAGE;
    const storedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    return this.isSupported(storedLanguage) ? storedLanguage : DEFAULT_LANGUAGE;
  }

  private synchronizeDocument(language: AppLanguage): void {
    this.document.documentElement.lang = language;
  }

  private isSupported(language: unknown): language is AppLanguage {
    return language === 'es' || language === 'en';
  }
}
