import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { OperatorSettingsPage } from './operator-settings';

describe('OperatorSettingsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('reduce-motion');
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('reduce-motion');
    document.documentElement.lang = 'es';
  });

  it('should save and restore the reduced-motion preference', async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorSettingsPage],
      providers: [provideRouter([])]
    }).compileComponents();

    const fixture = TestBed.createComponent(OperatorSettingsPage);
    fixture.detectChanges();
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

    component.resetPreferences();

    expect(component.reduceMotion).toBe(false);
    expect(component.selectedLanguage).toBe('es');
    expect(localStorage.getItem('rmm.reduce-motion')).toBeNull();
    expect(localStorage.getItem('rmm.language')).toBe('es');
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(false);
  });
});
