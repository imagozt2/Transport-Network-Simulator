import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { DepotOperation, DepotOperationsResponse } from '../../core/models/depot-operation.model';
import { DepotOperationsService } from '../../core/services/depot-operations.service';
import { Depots } from './depots';

const movements = {
  total: 2, exits: 1, entries: 1, completed: 1, scheduled: 1,
  nextMovementAt: '2026-07-22T09:00:00+02:00'
};
const lasFuentes: DepotOperation = {
  id: 20, code: 'DEP-LF-A', name: 'Cochera de Las Fuentes - Sector A',
  station: { id: 10, code: 'ST010', name: 'Las Fuentes' },
  capacity: 20, trackCount: 4, trainsPerTrack: 5,
  occupiedSpaces: 12, availableSpaces: 8, occupancyPercentage: 60, status: 'AVAILABLE',
  fleet: {
    assignedTrainCount: 14, assignedTrainsInService: 2,
    byStatus: { IN_SERVICE: 2, DEPOT: 12, MAINTENANCE: 0, STOPPED: 0, OUT_OF_SERVICE: 0 },
    byRole: { REGULAR_SERVICE: 12, RESERVE: 1, HISTORIC: 1 },
    bySeries: { '9000': 12, '7000': 1, '6000': 1 }
  },
  movementsSummary: movements,
  movements: [
    {
      dutyNumber: 1, type: 'EXIT', status: 'COMPLETED',
      scheduledAt: '2026-07-22T08:00:00+02:00', secondsUntilMovement: null,
      train: { id: 100, code: 'T-9001', series: '9000', fleetRole: 'REGULAR_SERVICE' },
      line: { id: 1, code: 'L1', name: 'Línea 1', color: 'Roja' },
      terminal: { id: 10, code: 'ST010', name: 'Las Fuentes' }
    },
    {
      dutyNumber: 1, type: 'ENTRY', status: 'SCHEDULED',
      scheduledAt: '2026-07-22T09:00:00+02:00', secondsUntilMovement: 1800,
      train: { id: 100, code: 'T-9001', series: '9000', fleetRole: 'REGULAR_SERVICE' },
      line: { id: 1, code: 'L1', name: 'Línea 1', color: 'Roja' },
      terminal: { id: 10, code: 'ST010', name: 'Las Fuentes' }
    }
  ]
};
const cuatroCaminos: DepotOperation = {
  ...lasFuentes, id: 21, code: 'DEP-CC-A', name: 'Cochera de Cuatro Caminos - Sector A',
  station: { id: 11, code: 'ST011', name: 'Cuatro Caminos' },
  occupiedSpaces: 20, availableSpaces: 0, occupancyPercentage: 100, status: 'FULL',
  movementsSummary: { total: 0, exits: 0, entries: 0, completed: 0, scheduled: 0, nextMovementAt: null },
  movements: []
};
const response: DepotOperationsResponse = {
  evaluatedAt: '2026-07-22T08:30:00+02:00', phase: 'OPERATING',
  summary: {
    depotCount: 2, totalCapacity: 40, occupiedSpaces: 32, availableSpaces: 8,
    occupancyPercentage: 80, assignedFleet: 28, trainsInService: 4, movements
  },
  depots: [lasFuentes, cuatroCaminos]
};

describe('Depots', () => {
  it('should render occupancy, fleet distribution, entries and exits', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(Depots);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.depot-card')).toHaveLength(2);
    const summaryCards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));
    expect(summaryCards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Cocheras',
      'Capacidad total',
      'Plazas ocupadas',
      'Plazas disponibles',
      'Ocupación',
      'Flota asignada',
      'Trenes en servicio',
      'Próximas salidas',
      'Próximas entradas'
    ]);
    expect(summaryCards.map((card) => card.querySelector('strong')?.textContent?.trim()))
      .toEqual(['2', '40', '32', '8', '80%', '28', '4', '0', '1']);
    expect(compiled.querySelector('.summary-grid small')).toBeNull();
    expect(compiled.querySelector('.depot-code')?.textContent).toContain('LF');
    expect(compiled.querySelector('.depot-summary')?.textContent?.trim())
      .toBe('DEP-LF-A · Las Fuentes · 12/20 plazas');
    expect(compiled.querySelector('.occupancy-badge')?.textContent?.trim()).toBe('60%');
    expect(compiled.querySelector('.occupancy-panel')).toBeNull();
    expect(compiled.querySelector('.operation-panel')).toBeNull();
    expect(compiled.querySelector('.infrastructure-panel')?.textContent).toContain('20 plazas');
    expect(compiled.querySelector('.infrastructure-panel')?.textContent)
      .not.toContain('Vías de estacionamiento');
    expect(compiled.querySelector('.infrastructure-panel')?.textContent)
      .not.toContain('Capacidad configurada');
    expect(compiled.querySelector('.status-distribution p')).toBeNull();
    const trainsLink = compiled.querySelector<HTMLAnchorElement>('.trains-link');
    expect(trainsLink?.getAttribute('href')).toBe('/trains?depotCode=DEP-LF-A');
    expect(trainsLink?.getAttribute('aria-label')).toContain('Cochera de Las Fuentes');
    expect(compiled.querySelectorAll('.role-row')).toHaveLength(3);
    expect(compiled.querySelector('.role-regular-service')?.textContent).toContain('12');
    expect(compiled.querySelectorAll('.series-row')).toHaveLength(3);
    expect(compiled.querySelector('.movement-group:first-child')?.textContent).toContain('Entrada');
    expect(compiled.querySelector('.movement-group:last-child')?.textContent).toContain('Salida');
    expect(compiled.querySelector('.movement-line')?.getAttribute('style')).toContain('rgb(211, 47, 47)');
    fixture.destroy();
  });

  it('should combine search and occupancy status filters and clear them', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(Depots);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.setSearchText('cuatro caminos');
    component.setStatusFilter('FULL');
    expect(component.hasActiveFilters()).toBe(true);
    expect(component.filteredDepots().map((depot) => depot.code)).toEqual(['DEP-CC-A']);
    component.setStatusFilter('AVAILABLE');
    expect(component.filteredDepots()).toHaveLength(0);
    component.clearFilters();
    expect(component.filteredDepots()).toHaveLength(2);
    fixture.destroy();
  });

  it('should limit and order the railway agenda around the evaluated instant', async () => {
    const completedTemplate = lasFuentes.movements[0];
    const scheduledTemplate = lasFuentes.movements[1];
    const depotWithWindowedMovements: DepotOperation = {
      ...lasFuentes,
      movements: [
        completedTemplate,
        scheduledTemplate,
        {
          ...scheduledTemplate,
          dutyNumber: 2,
          type: 'EXIT',
          scheduledAt: '2026-07-22T20:30:00+02:00',
          secondsUntilMovement: 43_200,
          train: { ...scheduledTemplate.train, code: 'T-FUTURE-BOUNDARY' }
        },
        {
          ...scheduledTemplate,
          dutyNumber: 3,
          scheduledAt: '2026-07-22T20:31:00+02:00',
          secondsUntilMovement: 43_260,
          train: { ...scheduledTemplate.train, code: 'T-OUTSIDE-FUTURE' }
        },
        {
          ...completedTemplate,
          dutyNumber: 4,
          scheduledAt: '2026-07-21T20:30:00+02:00',
          train: { ...completedTemplate.train, code: 'T-RECENT-BOUNDARY' }
        },
        {
          ...completedTemplate,
          dutyNumber: 5,
          scheduledAt: '2026-07-21T20:29:00+02:00',
          train: { ...completedTemplate.train, code: 'T-OUTSIDE-PAST' }
        }
      ]
    };
    const windowedResponse: DepotOperationsResponse = {
      ...response,
      depots: [depotWithWindowedMovements]
    };
    await configureWith(() => of(windowedResponse));
    const fixture = TestBed.createComponent(Depots);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.upcomingMovements(depotWithWindowedMovements).map((movement) => movement.train.code))
      .toEqual(['T-9001', 'T-FUTURE-BOUNDARY']);
    expect(component.recentMovements(depotWithWindowedMovements).map((movement) => movement.train.code))
      .toEqual(['T-9001', 'T-RECENT-BOUNDARY']);
    expect(component.scheduledMovementCount('EXIT')).toBe(1);
    expect(component.scheduledMovementCount('ENTRY')).toBe(1);
    expect(fixture.nativeElement.querySelector('.heading-total')?.textContent)
      .toContain('2 en las próximas 12 h');
    expect(fixture.nativeElement.textContent).not.toContain('T-OUTSIDE-FUTURE');
    expect(fixture.nativeElement.textContent).not.toContain('T-OUTSIDE-PAST');
    fixture.destroy();
  });

  it('should expose a retry action when the depot query fails', async () => {
    await configureWith(() => throwError(() => new Error('API error')));
    const fixture = TestBed.createComponent(Depots);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
    fixture.destroy();
  });
});

async function configureWith(getOperations: () => Observable<DepotOperationsResponse>) {
  await TestBed.configureTestingModule({
    imports: [Depots],
    providers: [
      provideRouter([]),
      { provide: DepotOperationsService, useValue: { getOperations } }
    ]
  }).compileComponents();
}
