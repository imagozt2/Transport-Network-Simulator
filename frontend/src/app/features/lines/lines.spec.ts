import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LineOperation, LineOperationsResponse } from '../../core/models/line-operation.model';
import { LineOperationsService } from '../../core/services/line-operations.service';
import { Lines } from './lines';

const firstLine: LineOperation = {
  id: 1, code: 'L3', name: 'Línea 3', color: 'Amarilla', phase: 'OPERATING', serviceOpen: true,
  serviceStartsAt: '2026-07-21T06:00:00+02:00', serviceEndsAt: '2026-07-21T23:30:00+02:00',
  currentPeriodCode: 'PEAK', currentPeriodType: 'PEAK', headwaySeconds: 240,
  estimatedOneWayDurationSeconds: 600, stationCount: 2,
  firstTerminal: { id: 1, code: 'ST001', name: 'Aeropuerto', stationOrder: 1 },
  lastTerminal: { id: 2, code: 'ST002', name: 'Centro', stationOrder: 2 }, activeTrainCount: 2,
  depots: [
    {
      id: 10, code: 'DEP-AIR', name: 'Cochera de Aeropuerto',
      station: { id: 1, code: 'ST001', name: 'Aeropuerto' },
      dispatchTerminal: { id: 2, code: 'ST002', name: 'Centro' },
      dispatchPriority: 1, dispatchEnabled: true, receptionEnabled: true,
      assignedTrainCount: 8, trainsInService: 5, availableTrainCount: 3
    },
    {
      id: 11, code: 'DEP-CEN', name: 'Cochera de Centro',
      station: { id: 2, code: 'ST002', name: 'Centro' },
      dispatchTerminal: { id: 1, code: 'ST001', name: 'Aeropuerto' },
      dispatchPriority: 2, dispatchEnabled: true, receptionEnabled: true,
      assignedTrainCount: 6, trainsInService: 4, availableTrainCount: 2
    }
  ],
  nextArrivals: [],
  stations: [
    { id: 1, code: 'ST001', name: 'Aeropuerto', stationOrder: 1 },
    { id: 2, code: 'ST002', name: 'Centro', stationOrder: 2 }
  ],
  trains: [
    { id: 90, code: 'T-9001', series: '9000', dutyNumber: 1, positionState: 'BETWEEN_STATIONS',
      direction: 'OUTBOUND', currentStationId: null, currentStationCode: null, previousStationId: 1,
      previousStationCode: 'ST001', nextStationId: 2, nextStationCode: 'ST002', progressPercentage: 50,
      secondsUntilNextStation: 60, estimatedArrivalAt: '2026-07-21T08:31:00+02:00' },
    { id: 91, code: 'T-9002', series: '9000', dutyNumber: 2, positionState: 'AT_STATION',
      direction: 'INBOUND', currentStationId: 2, currentStationCode: 'ST002', previousStationId: 2,
      previousStationCode: 'ST002', nextStationId: 1, nextStationCode: 'ST001', progressPercentage: 0,
      secondsUntilNextStation: 30, estimatedArrivalAt: '2026-07-21T08:30:30+02:00' }
  ]
};

const response: LineOperationsResponse = {
  evaluatedAt: '2026-07-21T08:30:00+02:00', phase: 'OPERATING', activeLineCount: 2,
  lines: [firstLine, {
    ...firstLine, id: 2, code: 'L4', name: 'Línea 4', color: 'Lila', activeTrainCount: 0, trains: [],
    stations: [{ id: 2, code: 'ST002', name: 'Centro', stationOrder: 1 }], stationCount: 1,
    firstTerminal: { id: 2, code: 'ST002', name: 'Centro', stationOrder: 1 },
    lastTerminal: { id: 2, code: 'ST002', name: 'Centro', stationOrder: 1 }
  }]
};

describe('Lines', () => {
  it('should render the summary, line colors and moving trains on the thermometer', async () => {
    await TestBed.configureTestingModule({
      imports: [Lines],
      providers: [{ provide: LineOperationsService, useValue: { getOperations: () => of(response) } }]
    }).compileComponents();
    const fixture = TestBed.createComponent(Lines);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.line-card')).toHaveLength(2);
    const summaryCards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));
    expect(summaryCards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Líneas de la red',
      'Estaciones',
      'Trenes en servicio',
      'Sentido ida',
      'Sentido vuelta'
    ]);
    expect(summaryCards.map((card) => card.querySelector('strong')?.textContent?.trim())).toEqual(['2', '2', '2', '1', '1']);
    expect(compiled.querySelector('.summary-grid small')).toBeNull();
    expect(compiled.querySelector('.summary-grid')?.textContent).not.toContain('Líneas abiertas');
    expect(compiled.querySelector('.summary-grid')?.textContent).not.toContain('Actualización');
    expect(compiled.querySelectorAll('.line-title-row .phase-pill')).toHaveLength(2);
    expect(compiled.querySelector('.line-title-row .service-pill')).toBeNull();
    expect(compiled.querySelector('.metric-panel .large-metric strong')?.textContent?.trim()).toBe('~ 4 min');
    expect(compiled.querySelector('.help-tooltip [role="tooltip"]')?.textContent).toContain('inicio progresivo');
    expect(Array.from(compiled.querySelectorAll('.direction-stats span')).map((label) => label.textContent?.trim())).toEqual(['Ida', 'Vuelta']);
    expect(compiled.querySelector('.direction-stats')?.textContent).not.toContain('Serie');
    expect(compiled.querySelector('.direction-stats')?.textContent).not.toContain('Estado');
    const depotsPanel = compiled.querySelector('.depots-panel');
    expect(depotsPanel?.textContent).toContain('2 asociadas');
    expect(depotsPanel?.textContent).toContain('Cochera de Aeropuerto');
    expect(depotsPanel?.textContent).toContain('Salida por Centro');
    expect(depotsPanel?.textContent).toContain('Flota asignada');
    expect(depotsPanel?.querySelectorAll('.line-depot')).toHaveLength(2);
    expect(compiled.querySelector('.line-badge')?.getAttribute('style')).toContain('color: rgb(17, 24, 39)');
    expect(compiled.querySelectorAll('.horizontal-route')).toHaveLength(2);
    expect(compiled.querySelectorAll('.horizontal-station-label')).toHaveLength(4);
    expect(compiled.querySelectorAll('.horizontal-line-label')).toHaveLength(4);
    expect(compiled.querySelector('.horizontal-station-label')?.textContent).toContain('Aeropuerto');
    expect(compiled.querySelector('.horizontal-station-label')?.textContent).not.toContain('...');
    expect(compiled.querySelectorAll('.horizontal-train-marker')).toHaveLength(2);
    expect((compiled.querySelectorAll('.horizontal-train-marker').item(0) as HTMLElement).style.left).toBe('50%');
    expect((compiled.querySelectorAll('.horizontal-train-marker').item(1) as HTMLElement).style.left).toBe('100%');
    expect(compiled.querySelector('.mini-thermometer .horizontal-train-marker')).toBeNull();
    expect(compiled.querySelector('.train-tooltip')?.textContent).toContain('T-9001');
    expect(compiled.querySelector('.horizontal-train-marker')?.getAttribute('aria-label')).toContain('T-9001');
    fixture.destroy();
  });

  it('should calculate unique stations, directions, transfers and bounded train positions', async () => {
    await TestBed.configureTestingModule({
      imports: [Lines],
      providers: [{ provide: LineOperationsService, useValue: { getOperations: () => of(response) } }]
    }).compileComponents();
    const fixture = TestBed.createComponent(Lines);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.totalTrains()).toBe(2);
    expect(component.totalStations()).toBe(2);
    expect(component.trainsInDirection('OUTBOUND')).toBe(1);
    expect(component.transferLineCodes(2, 'L3')).toEqual(['L4']);
    expect(component.getTrainPositionPercentage(firstLine, { ...firstLine.trains[0], progressPercentage: 140 })).toBe(100);
    fixture.destroy();
  });

  it('should expose a retry action when the operational API fails', async () => {
    await TestBed.configureTestingModule({
      imports: [Lines],
      providers: [{ provide: LineOperationsService, useValue: { getOperations: () => throwError(() => new Error('API error')) } }]
    }).compileComponents();
    const fixture = TestBed.createComponent(Lines);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
    fixture.destroy();
  });
});
