import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { TrainOperation, TrainOperationsResponse } from '../../core/models/train-operation.model';
import { TrainOperationsService } from '../../core/services/train-operations.service';
import { Trains } from './trains';

const line = { id: 1, code: 'L1', name: 'Línea 1', color: 'Roja' };
const depot = {
  id: 20, code: 'DEP-LF-A', name: 'Cochera de Las Fuentes - Sector A',
  stationId: 10, stationCode: 'ST010', stationName: 'Plaza de la Mina'
};
const historicDepot = {
  id: 21, code: 'DEP-CC-B', name: 'Cochera de Cuatro Caminos - Sector B',
  stationId: 11, stationCode: 'ST011', stationName: 'Cuatro Caminos'
};
const regularTrain: TrainOperation = {
  id: 100, code: 'T-9001', manufacturer: 'Macegocia Rail', model: 'MR9', series: '9000',
  carCount: 6, passengerCapacity: 900, maximumSpeedKmh: 80, fleetRole: 'REGULAR_SERVICE',
  status: 'IN_SERVICE', dispatchOrder: 1, assignedLine: line, homeDepot: depot, currentDepot: null,
  serviceLocation: {
    currentLine: line, dutyNumber: 1, positionState: 'BETWEEN_STATIONS', direction: 'OUTBOUND',
    destination: { id: 11, code: 'ST011', name: 'Las Fuentes' }, currentStation: null,
    previousStation: { id: 10, code: 'ST010', name: 'Plaza de la Mina' },
    nextStation: { id: 11, code: 'ST011', name: 'Las Fuentes' }, progressPercentage: 40,
    secondsUntilNextStation: 65, estimatedArrivalAt: '2026-07-22T08:31:05+02:00'
  }
};
const reserveTrain: TrainOperation = {
  ...regularTrain, id: 101, code: 'T-7001', model: 'MR7', series: '7000', fleetRole: 'RESERVE',
  status: 'DEPOT', dispatchOrder: 2, currentDepot: depot, serviceLocation: null
};
const historicTrain: TrainOperation = {
  ...regularTrain, id: 102, code: 'T-1001', manufacturer: 'Clásicos Macegocia', model: 'CM1',
  series: '1000', fleetRole: 'HISTORIC', status: 'DEPOT', dispatchOrder: null,
  homeDepot: historicDepot, currentDepot: historicDepot, serviceLocation: null
};
const response: TrainOperationsResponse = {
  evaluatedAt: '2026-07-22T08:30:00+02:00', phase: 'OPERATING',
  summary: {
    activeFleet: 3, trainsInService: 1, trainsInDepots: 2,
    byStatus: { IN_SERVICE: 1, DEPOT: 2, MAINTENANCE: 0, STOPPED: 0, OUT_OF_SERVICE: 0 },
    byRole: { REGULAR_SERVICE: 1, RESERVE: 1, HISTORIC: 1 },
    bySeries: { '9000': 1, '7000': 1, '1000': 1 }
  },
  trains: [regularTrain, reserveTrain, historicTrain]
};

describe('Trains', () => {
  it('should render fleet roles and the live situation of an in-service train', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(Trains);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.train-card')).toHaveLength(3);
    const summaryCards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));
    expect(summaryCards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Flota total',
      'Trenes en servicio',
      'Trenes en cocheras',
      'Servicio regular',
      'Reserva',
      'Históricos'
    ]);
    expect(summaryCards.map((card) => card.querySelector('strong')?.textContent?.trim()))
      .toEqual(['3', '1', '2', '1', '1', '1']);
    expect(compiled.querySelector('.summary-grid small')).toBeNull();
    expect(compiled.querySelector('.fleet-role-summary')).toBeNull();
    expect(compiled.querySelector('.train-card.role-regular')).not.toBeNull();
    expect(compiled.querySelector('.train-card.role-reserve')).not.toBeNull();
    expect(compiled.querySelector('.train-card.role-historic')).not.toBeNull();
    expect(compiled.querySelector('.train-card.role-regular .line-badge')?.textContent).toContain('L1');
    expect(compiled.querySelector('.train-card.role-reserve .depot-badge')?.textContent).toContain('LF');
    expect(compiled.querySelector('.train-card.role-reserve .depot-badge')?.getAttribute('title'))
      .toContain('Cochera de Las Fuentes');
    expect(compiled.querySelector('.train-card.role-regular .train-code')?.getAttribute('title'))
      .toBe('T-9001');
    expect(compiled.querySelector('.train-card.role-regular .train-summary')?.textContent?.trim())
      .toBe('Serie 9000 · Entre Plaza de la Mina y Las Fuentes');
    expect(compiled.querySelector('.train-card.role-regular .train-summary')?.textContent)
      .not.toContain('Línea 1');
    expect(compiled.querySelector('.service-context')?.textContent).toContain('Línea 1');
    expect(compiled.querySelector('.service-context')?.textContent).toContain('Dirección Las Fuentes');
    expect(compiled.querySelector('.situation-title')?.textContent?.trim())
      .toBe('Entre Plaza de la Mina y Las Fuentes');
    expect(compiled.querySelector('.live-location-heading')?.textContent)
      .toContain('Progreso hacia Las Fuentes');
    expect(compiled.querySelector('.live-location-heading')?.textContent).toContain('40%');
    expect(compiled.querySelector('.live-location')?.textContent).toContain('Las Fuentes');
    expect(compiled.querySelector('.countdown')?.textContent).toContain('1:05');

    fixture.componentInstance.countdownNowMs += 1_000;
    expect(fixture.componentInstance.nextArrivalLabel(regularTrain)).toBe('1:04');
    fixture.destroy();
  });

  it('should combine search, status, line, depot, series and fleet-role filters and clear them', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(Trains);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.setSearchText('7001');
    component.setStatusFilter('DEPOT');
    component.setLineFilter('L1');
    component.setDepotFilter('DEP-LF-A');
    component.setSeriesFilter('7000');
    component.setRoleFilter('RESERVE');
    expect(component.hasActiveFilters()).toBe(true);
    expect(component.filteredTrains().map((train) => train.code)).toEqual(['T-7001']);

    component.setRoleFilter('HISTORIC');
    expect(component.filteredTrains()).toHaveLength(0);
    component.clearFilters();
    expect(component.filteredTrains()).toHaveLength(3);
    expect(component.lineOptions().map((option) => option.code)).toEqual(['L1']);
    expect(component.depotOptions().map((option) => option.code)).toEqual(['DEP-CC-B', 'DEP-LF-A']);
    expect(component.seriesOptions()).toEqual(['1000', '7000', '9000']);
    fixture.destroy();
  });

  it('should initialize the depot filter from the URL and include its circulating trains', async () => {
    await configureWith(() => of(response), { depotCode: 'DEP-LF-A' });
    const fixture = TestBed.createComponent(Trains);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedDepotCode).toBe('DEP-LF-A');
    expect(fixture.componentInstance.filteredTrains().map((train) => train.code))
      .toEqual(['T-9001', 'T-7001']);
    const depotFilter = fixture.nativeElement.querySelector(
      '.filters-panel label:nth-of-type(4) select'
    ) as HTMLSelectElement;
    expect(depotFilter.value).toBe('DEP-LF-A');
    expect(depotFilter.selectedOptions[0]?.textContent).toContain('Las Fuentes');
    fixture.destroy();
  });

  it('should expose a retry action when the fleet query fails', async () => {
    await configureWith(() => throwError(() => new Error('API error')));
    const fixture = TestBed.createComponent(Trains);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
    fixture.destroy();
  });
});

async function configureWith(
  getOperations: () => Observable<TrainOperationsResponse>,
  queryParams: Record<string, string> = {}
) {
  await TestBed.configureTestingModule({
    imports: [Trains],
    providers: [
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
      },
      { provide: TrainOperationsService, useValue: { getOperations } }
    ]
  }).compileComponents();
}
