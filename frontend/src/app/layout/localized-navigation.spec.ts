import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { I18nService } from '../core/i18n/i18n.service';
import { LocalizeContentDirective } from '../core/i18n/localize-content.directive';
import { Sidebar } from './sidebar/sidebar';

@Component({
  imports: [LocalizeContentDirective, Sidebar],
  template: '<section appLocalizeContent><app-sidebar /></section>'
})
class LocalizedNavigationHost {}

describe('Localized contextual navigation', () => {
  beforeEach(async () => {
    localStorage.clear();
    document.documentElement.lang = 'es';
    await TestBed.configureTestingModule({
      imports: [LocalizedNavigationHost],
      providers: [provideRouter([
        { path: 'dashboard', children: [] },
        { path: 'network-map', children: [] },
        { path: 'lines', children: [] },
        { path: 'stations', children: [] },
        { path: 'trains', children: [] },
        { path: 'depots', children: [] },
        { path: 'devices', children: [] },
        { path: 'transport-titles', children: [] },
        { path: 'users', children: [] },
        { path: 'incidents', children: [] },
        { path: 'logs', children: [] }
      ])]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'es';
  });

  it('should preserve management destinations after translating the navigation', async () => {
    const fixture = TestBed.createComponent(LocalizedNavigationHost);
    const i18n = TestBed.inject(I18nService);
    fixture.detectChanges();
    await fixture.whenStable();

    i18n.setLanguage('en');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll<HTMLAnchorElement>('.nav-link'));
    const labels = links.map((link) => link.querySelector('.nav-label')?.textContent?.trim());
    expect(labels).toContain('Transport products');
    expect(labels).toContain('Users');
    expect(labels).toContain('Incidents');
    expect(links.map((link) => link.getAttribute('href'))).toContain('/transport-titles');
    expect(links.map((link) => link.getAttribute('href'))).toContain('/users');
    expect(links.map((link) => link.getAttribute('href'))).toContain('/incidents');

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/incidents');
    await fixture.whenStable();
    fixture.detectChanges();

    const activeLink = compiled.querySelector<HTMLAnchorElement>('.nav-link.active');
    expect(activeLink?.getAttribute('href')).toBe('/incidents');
    expect(activeLink?.textContent).toContain('Incidents');
  });
});
