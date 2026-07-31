import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DashboardResponse } from '../../core/models/dashboard.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { Dashboard } from './dashboard';

const summary: DashboardResponse = {
  lineCount: 6,
  stationCount: 50,
  totalFleet: 242,
  trainsInService: 84,
  deviceCount: 622,
  depotCount: 6,
  depotOccupancyPercentage: 65,
  trainStatusCounts: {
    IN_SERVICE: 84,
    DEPOT: 150,
    MAINTENANCE: 4,
    STOPPED: 2,
    OUT_OF_SERVICE: 2
  },
  deviceStatusCounts: { ONLINE: 622, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 },
  deviceTypeCounts: { TICKET_MACHINE: 126, ENTRY_VALIDATOR: 248, EXIT_VALIDATOR: 248 },
  depotCapacity: 300,
  occupiedDepotSpaces: 195,
  availableDepotSpaces: 105,
  depots: [{
    id: 1,
    code: 'CC',
    name: 'Cochera de Cuatro Caminos',
    capacity: 50,
    occupiedSpaces: 32,
    availableSpaces: 18
  }],
  lines: [{
    id: 1,
    code: 'L1',
    name: 'Línea 1',
    color: 'Roja',
    serviceOpen: true,
    activeTrainCount: 14
  }]
};

describe('Dashboard', () => {
  it('should render the operational summary', async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: { getSummary: () => of(summary) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const cards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));

    expect(compiled.textContent).toContain('Panel general');
    expect(cards).toHaveLength(7);
    expect(cards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Líneas de la red',
      'Número de estaciones',
      'Flota total',
      'Trenes en servicio',
      'Cantidad de máquinas',
      'Cocheras',
      'Ocupación de las cocheras'
    ]);
    expect(cards.map((card) => card.querySelector('strong')?.textContent?.trim())).toEqual([
      '6', '50', '242', '84', '622', '6', '65%'
    ]);
    expect(compiled.querySelector('.summary-card small')).toBeNull();
    expect(compiled.textContent).toContain('Estado de trenes');
    expect(compiled.textContent).toContain('Estado de máquinas');
    expect(compiled.textContent).toContain('Cochera de Cuatro Caminos');
    expect(compiled.textContent).toContain('14 trenes en servicio');
    const contextualLinks = Array.from(
      compiled.querySelectorAll<HTMLAnchorElement>('.panel-header .context-link')
    );
    expect(contextualLinks.map((link) => link.textContent?.trim())).toEqual([
      'Ver trenes',
      'Ver máquinas',
      'Ver cocheras',
      'Ver líneas'
    ]);
    expect(contextualLinks.map((link) => link.getAttribute('href'))).toEqual([
      '/trains',
      '/devices',
      '/depots',
      '/lines'
    ]);
  });

  it('should show a retry action when loading fails', async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: { getSummary: () => throwError(() => new Error('connection error')) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
  });
});
