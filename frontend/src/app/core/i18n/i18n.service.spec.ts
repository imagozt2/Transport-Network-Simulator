import { TestBed } from '@angular/core/testing';

import { I18nService } from './i18n.service';

describe('I18nService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'es';
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'es';
  });

  it('should use Spanish by default and interpolate translated values', () => {
    const service = TestBed.inject(I18nService);

    expect(service.language()).toBe('es');
    expect(service.locale()).toBe('es-ES');
    expect(service.translate('app.name')).toBe('Centro de control');
    expect(service.translate('common.page', { current: 2, total: 8 }))
      .toBe('Página 2 de 8');
    expect(document.documentElement.lang).toBe('es');
  });

  it('should persist a language change and expose its locale', () => {
    const service = TestBed.inject(I18nService);

    service.setLanguage('en');

    expect(service.language()).toBe('en');
    expect(service.locale()).toBe('en-GB');
    expect(service.translate('app.name')).toBe('Control centre');
    expect(service.translate('common.page', { current: 2, total: 8 }))
      .toBe('Page 2 of 8');
    expect(localStorage.getItem('rmm.language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('should restore the persisted language when the service starts', () => {
    localStorage.setItem('rmm.language', 'en');

    const service = TestBed.inject(I18nService);

    expect(service.language()).toBe('en');
    expect(service.translate('common.nextPage')).toBe('Next');
    expect(document.documentElement.lang).toBe('en');
  });

  it('should ignore an unsupported persisted language', () => {
    localStorage.setItem('rmm.language', 'fr');

    const service = TestBed.inject(I18nService);

    expect(service.language()).toBe('es');
    expect(document.documentElement.lang).toBe('es');
  });
});
