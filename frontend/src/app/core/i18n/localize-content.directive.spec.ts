import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { I18nService } from './i18n.service';
import { LocalizeContentDirective } from './localize-content.directive';

@Component({
  imports: [LocalizeContentDirective],
  template: `
    <section appLocalizeContent aria-label="Resumen operativo de la red">
      <h1>Panel general</h1>
      <p>{{ message }}</p>
      <input placeholder="Nombre o código" />
    </section>
  `
})
class LocalizedContentHost {
  message = 'Cargando mapa de red…';
}

describe('LocalizeContentDirective', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'es';
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'es';
  });

  it('should translate content, attributes and later text updates reactively', async () => {
    const fixture = TestBed.createComponent(LocalizedContentHost);
    const i18n = TestBed.inject(I18nService);
    fixture.detectChanges();
    await fixture.whenStable();

    i18n.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('h1')?.textContent).toBe('Overview');
    expect(host.querySelector('section')?.getAttribute('aria-label')).toBe('Network operations summary');
    expect(host.querySelector('input')?.getAttribute('placeholder')).toBe('Name or code');
    expect(host.querySelector('p')?.textContent).toBe('Loading network map…');

    const dynamicMessage = host.querySelector('p') as HTMLParagraphElement;
    dynamicMessage.textContent = 'No hay líneas disponibles.';
    await new Promise<void>((resolve) => setTimeout(resolve, 0));

    expect(host.querySelector('p')?.textContent).toBe('No lines are available.');

    i18n.setLanguage('es');
    fixture.detectChanges();

    expect(host.querySelector('h1')?.textContent).toBe('Panel general');
    expect(host.querySelector('p')?.textContent).toBe('No hay líneas disponibles.');
  });
});
