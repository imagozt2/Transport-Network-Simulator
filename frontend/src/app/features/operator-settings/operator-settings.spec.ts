import { Component, signal, ViewEncapsulation } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OperatorDisplayPreferences } from '../../core/models/operator-display-preferences.model';
import { OperatorDisplayPreferencesService } from '../../core/services/operator-display-preferences.service';

import { OperatorSettingsPage } from './operator-settings';

@Component({
  selector: 'app-dark-theme-surfaces-host',
  template: `
    <article class="line-card"></article>
    <article class="station-card"></article>
    <article class="train-card"></article>
    <article class="depot-card"></article>
    <article class="device-card"></article>
    <article class="ticket-card"></article>
    <article class="incident-card"></article>
    <article class="title-card"></article>
    <article class="profile-card"></article>
    <article class="logs-results"></article>
    <article class="issuance-dialog"></article>
    <article class="create-user-dialog"></article>
    <article class="create-incident-dialog"></article>
  `,
  styleUrl: '../../../styles.css',
  encapsulation: ViewEncapsulation.None
})
class DarkThemeSurfacesHost {}

describe('OperatorSettingsPage', () => {
  const preferencesState = signal<OperatorDisplayPreferences>({
    timeZone: 'Europe/Madrid',
    theme: 'LIGHT'
  });
  const preferencesService = {
    preferences: preferencesState.asReadonly(),
    load: () => of(preferencesState()),
    update: (preferences: OperatorDisplayPreferences) => {
      preferencesState.set(preferences);
      document.documentElement.classList.toggle('theme-dark', preferences.theme === 'DARK');
      document.documentElement.style.colorScheme = preferences.theme === 'DARK' ? 'dark' : 'light';
      return of(preferences);
    }
  };

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('reduce-motion');
    document.documentElement.classList.remove('theme-dark');
    document.documentElement.style.colorScheme = '';
    preferencesState.set({ timeZone: 'Europe/Madrid', theme: 'LIGHT' });
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('reduce-motion');
    document.documentElement.classList.remove('theme-dark');
    document.documentElement.style.colorScheme = '';
    document.documentElement.lang = 'es';
  });

  async function createPage() {
    await TestBed.configureTestingModule({
      imports: [OperatorSettingsPage, DarkThemeSurfacesHost],
      providers: [
        provideRouter([]),
        { provide: OperatorDisplayPreferencesService, useValue: preferencesService }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(OperatorSettingsPage);
    fixture.detectChanges();
    return fixture;
  }

  it('should save the language and reduced-motion preferences', async () => {
    const fixture = await createPage();
    const component = fixture.componentInstance;

    expect(component.reduceMotion).toBe(false);
    expect(component.selectedLanguage).toBe('es');
    expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toBe('/dashboard');
    expect(fixture.nativeElement.querySelector('a')?.classList.contains('context-link')).toBe(true);

    const reduceMotionCheckbox = fixture.nativeElement.querySelector(
      'input[type="checkbox"]'
    ) as HTMLInputElement;
    reduceMotionCheckbox.click();
    fixture.detectChanges();
    component.selectLanguage('en');
    component.savePreferences();

    expect(localStorage.getItem('rmm.reduce-motion')).toBe('true');
    expect(localStorage.getItem('rmm.language')).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(true);
    expect(component.saved).toBe(true);
  });

  it('should restore persisted preferences when opening the page', async () => {
    localStorage.setItem('rmm.language', 'en');
    localStorage.setItem('rmm.reduce-motion', 'true');

    const fixture = await createPage();
    const component = fixture.componentInstance;

    expect(component.selectedLanguage).toBe('en');
    expect(component.reduceMotion).toBe(true);
    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(true);

    const languageSelect = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(languageSelect.value).toBe('en');
  });

  it('should reset all browser preferences to their defaults', async () => {
    localStorage.setItem('rmm.language', 'en');
    localStorage.setItem('rmm.reduce-motion', 'true');
    const fixture = await createPage();
    const component = fixture.componentInstance;

    component.resetPreferences();

    expect(component.reduceMotion).toBe(false);
    expect(component.selectedLanguage).toBe('es');
    expect(localStorage.getItem('rmm.reduce-motion')).toBeNull();
    expect(localStorage.getItem('rmm.language')).toBe('es');
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(false);
    expect(document.documentElement.lang).toBe('es');
    expect(component.saved).toBe(true);
  });

  it('should persist and apply the dark theme', async () => {
    const fixture = await createPage();
    const component = fixture.componentInstance;

    component.selectTheme('DARK');
    component.savePreferences();

    expect(preferencesState().theme).toBe('DARK');
    expect(document.documentElement.classList.contains('theme-dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('should apply dark surfaces to operational cards and administrative dialogs', async () => {
    const fixture = await createPage();
    const surfacesFixture = TestBed.createComponent(DarkThemeSurfacesHost);
    surfacesFixture.detectChanges();
    fixture.componentInstance.selectTheme('DARK');
    fixture.componentInstance.savePreferences();
    const surfaces = surfacesFixture.nativeElement.querySelectorAll('article') as NodeListOf<HTMLElement>;

    surfaces.forEach((surface) => {
      const styles = getComputedStyle(surface);
      expect(styles.backgroundColor).toBe('rgb(17, 24, 39)');
      expect(styles.color).toBe('rgb(229, 231, 235)');
    });
  });

  it('should restore account preferences and persist a new time zone with its theme', async () => {
    preferencesState.set({ timeZone: 'America/New_York', theme: 'DARK' });
    const fixture = await createPage();
    const component = fixture.componentInstance;

    expect(component.selectedTimeZone).toBe('America/New_York');
    expect(component.selectedTheme).toBe('DARK');

    component.selectTimeZone('UTC');
    component.savePreferences();

    expect(preferencesState()).toEqual({ timeZone: 'UTC', theme: 'DARK' });
    expect(component.saved).toBe(true);
  });
});
