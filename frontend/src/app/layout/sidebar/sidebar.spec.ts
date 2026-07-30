import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Sidebar } from './sidebar';

describe('Sidebar', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Sidebar],
      providers: [
        provideRouter([
          { path: 'dashboard', children: [] },
          { path: 'network-map', children: [] },
          { path: 'lines', children: [] },
          { path: 'stations', children: [] },
          { path: 'trains', children: [] },
          { path: 'depots', children: [] },
          { path: 'transport-titles', children: [] },
          { path: 'users', children: [] },
          { path: 'devices', children: [] },
          { path: 'logs', children: [] }
        ])
      ]
    }).compileComponents();
  });

  it('should expose the main sections with their routes and visual icons', () => {
    const fixture = TestBed.createComponent(Sidebar);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll<HTMLAnchorElement>('.nav-link'));

    expect(links.map((link) => link.querySelector('.nav-label')?.textContent?.trim())).toEqual([
      'Panel General',
      'Mapa de red',
      'Líneas',
      'Estaciones',
      'Trenes',
      'Cocheras',
      'Títulos de transporte',
      'Usuarios',
      'Máquinas',
      'Logs'
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/dashboard',
      '/network-map',
      '/lines',
      '/stations',
      '/trains',
      '/depots',
      '/transport-titles',
      '/users',
      '/devices',
      '/logs'
    ]);
    expect(
      links.every((link) => (link.querySelector('.nav-icon')?.textContent?.trim().length ?? 0) > 0)
    ).toBe(true);
  });

  it('should mark the current route as active', async () => {
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(Sidebar);
    fixture.detectChanges();

    await router.navigateByUrl('/stations');
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const activeLink = compiled.querySelector<HTMLAnchorElement>('.nav-link.active');
    expect(activeLink?.querySelector('.nav-label')?.textContent?.trim()).toBe('Estaciones');
  });

  it('should notify the layout when a navigation option is selected', () => {
    const fixture = TestBed.createComponent(Sidebar);
    const navigationSelected = vi.fn();
    fixture.componentInstance.navigationSelected.subscribe(navigationSelected);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLAnchorElement>('.nav-link')?.click();

    expect(navigationSelected).toHaveBeenCalledOnce();
  });
});
