import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MainLayout } from './main-layout';

@Component({ template: '' })
class NavigationTarget {}

describe('MainLayout', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [provideRouter([{ path: '**', component: NavigationTarget }])],
    }).compileComponents();
  });

  it('should render the header and primary navigation', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('Centro de Control');
    expect(compiled.querySelector('.sidebar')?.textContent).toContain('Panel General');
    expect(compiled.querySelector('.sidebar')?.textContent).toContain('Mapa de red');
    expect(compiled.querySelector('.skip-link')?.getAttribute('href')).toBe('#main-content');
    expect(compiled.querySelector('main')?.id).toBe('main-content');
    expect(compiled.querySelector('.sidebar')?.id).toBe('primary-navigation');
  });

  it('should render every navigation group in the expected order', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const sectionTitles = Array.from(compiled.querySelectorAll<HTMLElement>('.nav-section h2'))
      .map((heading) => heading.textContent?.trim());

    expect(sectionTitles).toEqual([
      'Vista general',
      'Red ferroviaria',
      'Material móvil',
      'Billetaje',
      'Administración',
      'Equipamiento',
      'Supervisión'
    ]);
  });

  it('should open and close the sidebar from the shell controls', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const menuButton = compiled.querySelector('.menu-button') as HTMLButtonElement;

    expect(menuButton.getAttribute('aria-controls')).toBe('primary-navigation');
    expect(menuButton.getAttribute('aria-expanded')).toBe('false');
    menuButton.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(true);
    expect(compiled.querySelector('.sidebar-backdrop')).not.toBeNull();
    expect(menuButton.getAttribute('aria-expanded')).toBe('true');

    compiled.querySelector<HTMLButtonElement>('.sidebar-backdrop')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(false);
    expect(compiled.querySelector('.sidebar-backdrop')).toBeNull();
    expect(menuButton.getAttribute('aria-expanded')).toBe('false');
  });

  it('should close the adaptable sidebar with the Escape key', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.menu-button')?.click();
    fixture.detectChanges();
    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(false);
  });

  it('should close the sidebar after selecting a navigation link', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.menu-button')?.click();
    fixture.detectChanges();
    compiled.querySelector<HTMLAnchorElement>('.nav-link')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(false);
  });
});
