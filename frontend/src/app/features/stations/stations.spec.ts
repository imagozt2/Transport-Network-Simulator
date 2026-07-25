import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { StationOperationsResponse } from '../../core/models/station-operation.model';
import { StationOperationsService } from '../../core/services/station-operations.service';
import { Stations } from './stations';

const response: StationOperationsResponse = {
  evaluatedAt: '2026-07-22T08:30:00+02:00',
  phase: 'OPERATING',
  stationCount: 1,
  activeStationCount: 1,
  summary: {
    stationCount: 1,
    activeStationCount: 1,
    transferStationCount: 0,
    ticketMachineCount: 1,
    entryValidatorCount: 1,
    exitValidatorCount: 1
  },
  stations: [{
    id: 2,
    code: 'STB',
    name: 'Estación B',
    status: 'NORMAL',
    transferStation: false,
    lineCount: 1,
    activeLineCount: 1,
    activeTrainCount: 4,
    devices: {
      total: 3, ticketMachines: 1, entryValidators: 1, exitValidators: 1,
      online: 3, offline: 0, maintenance: 0, errors: 0
    },
    lines: [{
      id: 10, code: 'L3', name: 'Línea 3', color: 'Amarilla', stationOrder: 2,
      phase: 'OPERATING', serviceOpen: true, activeTrainCount: 4,
      firstTerminal: { id: 1, code: 'STA', name: 'Estación A' },
      lastTerminal: { id: 3, code: 'STC', name: 'Estación C' },
      directions: [
        {
          direction: 'OUTBOUND',
          destination: { id: 3, code: 'STC', name: 'Estación C' },
          activeTrainCount: 3
        },
        {
          direction: 'INBOUND',
          destination: { id: 1, code: 'STA', name: 'Estación A' },
          activeTrainCount: 1
        }
      ]
    }],
    nextArrivals: [{
      trainId: 90, trainCode: 'T-9001', trainSeries: '9000',
      lineId: 10, lineCode: 'L3', lineName: 'Línea 3', lineColor: 'Amarilla',
      direction: 'OUTBOUND', destination: { id: 3, code: 'STC', name: 'Estación C' },
      stationsAway: 1, secondsUntilArrival: 65,
      estimatedArrivalAt: '2026-07-22T08:31:05+02:00', atStation: false
    }]
  }]
};

describe('Stations', () => {
  it('should render station state, line colors and a live mm:ss countdown', async () => {
    await TestBed.configureTestingModule({
      imports: [Stations],
      providers: [
        provideRouter([]),
        { provide: StationOperationsService, useValue: { getOperations: () => of(response) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(Stations);
    fixture.detectChanges();
    let compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.station-card')).toHaveLength(1);
    expect(compiled.querySelectorAll('.station-title-row .status-pill')).toHaveLength(1);
    expect(compiled.querySelector('.station-title-row .type-pill')).toBeNull();
    expect(compiled.querySelector('.station-title-row')?.textContent).not.toContain('Estación simple');
    expect(compiled.querySelector('.station-title-row')?.textContent).not.toContain('Estación de transbordo');
    const summaryCards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));
    expect(summaryCards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Estaciones',
      'Transbordos',
      'Máquinas de billetes',
      'Validadores de entrada',
      'Validadores de salida'
    ]);
    expect(summaryCards.map((card) => card.querySelector('strong')?.textContent?.trim()))
      .toEqual(['1', '0', '1', '1', '1']);
    expect(compiled.querySelector('.summary-grid small')).toBeNull();
    const filterGroups = compiled.querySelectorAll('.filter-group');
    expect(filterGroups.item(1).querySelector('strong')?.textContent?.trim()).toBe('Número de líneas');
    expect(Array.from(filterGroups.item(1).querySelectorAll('button')).map((button) => button.textContent?.trim()))
      .toEqual(['Todas', '1 línea', '2 líneas', '3 o más']);
    expect(compiled.querySelector('.status-pill')?.textContent).toContain('Normal');
    expect(compiled.querySelector('.line-badge')?.getAttribute('style')).toContain('rgb(251, 192, 45)');
    expect(compiled.querySelector('.line-thermometer')).not.toBeNull();
    expect(compiled.querySelector('.arrival-time')?.textContent).toContain('1:05');
    expect(compiled.querySelector('.devices-panel h2')?.textContent?.trim()).toBe('Máquinas');
    expect(compiled.querySelector('.devices-panel')?.textContent).not.toContain('Errores');
    const contextLinks = compiled.querySelectorAll<HTMLAnchorElement>('.station-context-actions a');
    expect(contextLinks.item(0).getAttribute('href')).toBe('/devices?stationCode=STB');
    expect(contextLinks.item(1).getAttribute('href')).toBe('/logs?stationCode=STB');

    fixture.componentInstance.ngOnDestroy();
    fixture.componentInstance.countdownNowMs += 1_000;
    const arrival = response.stations[0].nextArrivals[0];
    expect(fixture.componentInstance.arrivalTimeLabel(arrival)).toBe('1:04');
    expect(fixture.componentInstance.remainingSeconds(arrival)).toBe(64);
    fixture.destroy();
  });

  it('should filter stations and clear all active filters', async () => {
    await TestBed.configureTestingModule({
      imports: [Stations],
      providers: [
        provideRouter([]),
        { provide: StationOperationsService, useValue: { getOperations: () => of(response) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(Stations);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const oneLineStation = response.stations[0];
    const twoLineStation = {
      ...oneLineStation,
      id: 3,
      code: 'STC',
      name: 'Estación C',
      transferStation: true,
      lineCount: 2
    };
    const threeLineStation = {
      ...oneLineStation,
      id: 4,
      code: 'STD',
      name: 'Estación D',
      transferStation: true,
      lineCount: 3
    };
    component.operations = {
      ...response,
      stations: [oneLineStation, twoLineStation, threeLineStation]
    };

    component.setSearchText('inexistente');
    expect(component.filteredStations()).toHaveLength(0);
    component.setSearchText('');
    component.setLineCountFilter('1');
    expect(component.filteredStations().map((station) => station.code)).toEqual(['STB']);
    component.setLineCountFilter('2');
    expect(component.filteredStations().map((station) => station.code)).toEqual(['STC']);
    component.setLineCountFilter('3_PLUS');
    expect(component.filteredStations().map((station) => station.code)).toEqual(['STD']);
    component.setStatusFilter('CRITICAL');
    expect(component.filteredStations()).toHaveLength(0);
    expect(component.hasActiveFilters()).toBe(true);
    component.clearFilters();
    expect(component.filteredStations()).toHaveLength(3);
    expect(component.selectedLineCount).toBe('ALL');
    fixture.destroy();
  });

  it('should expose a retry action when the operational API fails', async () => {
    await TestBed.configureTestingModule({
      imports: [Stations],
      providers: [{
        provide: StationOperationsService,
        useValue: { getOperations: () => throwError(() => new Error('API error')) }
      }, provideRouter([])]
    }).compileComponents();
    const fixture = TestBed.createComponent(Stations);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
    fixture.destroy();
  });
});
