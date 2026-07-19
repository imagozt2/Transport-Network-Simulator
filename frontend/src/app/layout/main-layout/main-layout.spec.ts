import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MainLayout } from './main-layout';

describe('MainLayout', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should render the header and primary navigation', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('Centro de Control');
    expect(compiled.querySelector('.sidebar')?.textContent).toContain('Panel General');
    expect(compiled.querySelector('.sidebar')?.textContent).toContain('Mapa de red');
  });

  it('should open the sidebar from the menu button', () => {
    const fixture = TestBed.createComponent(MainLayout);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.menu-button')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.sidebar')?.classList.contains('open')).toBe(true);
  });
});
