import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { DeviceOperationsResponse } from '../../core/models/device-operation.model';
import { OperationalLogPage } from '../../core/models/operational-log.model';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import { OperationalLogsService } from '../../core/services/operational-logs.service';
import { Logs } from './logs';

const deviceOperations: DeviceOperationsResponse = {
  evaluatedAt: '2026-07-23T12:00:00+02:00',
  summary: {
    totalDevices: 1,
    filteredDevices: 1,
    byType: { TICKET_MACHINE: 1, ENTRY_VALIDATOR: 0, EXIT_VALIDATOR: 0 },
    byStatus: { ONLINE: 1, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 }
  },
  devices: [{
    id: 10,
    code: 'RMM-MB-ST001-001',
    name: 'Máquina de billetes 1',
    type: 'TICKET_MACHINE',
    status: 'ONLINE',
    lastConnectionAt: '2026-07-23T11:59:55+02:00',
    station: { id: 1, code: 'ST001', name: 'Los Molinos' }
  }]
};

const logsPage: OperationalLogPage = {
  logs: [{
    id: 100,
    origin: 'DEVICE_SIMULATION',
    eventType: 'DEVICE_ONLINE',
    severity: 'INFO',
    message: 'Máquina conectada',
    deviceId: 10,
    deviceCode: 'RMM-MB-ST001-001',
    deviceName: 'Máquina de billetes 1',
    stationId: 1,
    stationCode: 'ST001',
    stationName: 'Los Molinos',
    externalReference: null,
    occurredAt: '2026-07-23T11:59:55',
    receivedAt: '2026-07-23T11:59:56'
  }],
  currentPage: 0,
  pageSize: 25,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  empty: false
};

describe('Logs URL filters', () => {
  it('should initialize filters before requesting logs', async () => {
    const getLogs = vi.fn().mockReturnValue(of(logsPage));
    await configure({
      deviceCode: '  RMM-MB-ST001-001  ',
      severity: 'INFO',
      origin: 'DEVICE_SIMULATION',
      eventType: 'DEVICE_ONLINE',
      deviceType: 'TICKET_MACHINE',
      stationCode: '  ST001  ',
      occurredFrom: '2026-07-23T10:00',
      occurredTo: '2026-07-23T12:00'
    }, getLogs);

    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(getLogs).toHaveBeenCalledWith(0, 25, {
      deviceCode: 'RMM-MB-ST001-001',
      severity: 'INFO',
      origin: 'DEVICE_SIMULATION',
      eventType: 'DEVICE_ONLINE',
      deviceType: 'TICKET_MACHINE',
      stationCode: 'ST001',
      occurredFrom: '2026-07-23T10:00',
      occurredTo: '2026-07-23T12:00'
    });
    expect(fixture.componentInstance.selectedDeviceCode).toBe('RMM-MB-ST001-001');
    expect(fixture.componentInstance.selectedDeviceType).toBe('TICKET_MACHINE');
    const deviceTypeFilter = fixture.nativeElement.querySelector(
      '.filters-grid label:nth-of-type(4) select'
    ) as HTMLSelectElement;
    expect(deviceTypeFilter.value).toBe('TICKET_MACHINE');
    expect(deviceTypeFilter.selectedOptions[0]?.textContent).toContain('Máquina de billetes');
    const deviceFilter = fixture.nativeElement.querySelector(
      '.filters-grid label:nth-of-type(5) select'
    ) as HTMLSelectElement;
    expect(deviceFilter.value).toBe('RMM-MB-ST001-001');
    expect(deviceFilter.selectedOptions[0]?.textContent).toContain('Máquina de billetes 1');
    expect(compiled.querySelector('.logs-table tbody tr')).not.toBeNull();
    expect(Array.from(
      compiled.querySelectorAll<HTMLTableCellElement>('.logs-table thead th')
    ).every((heading) => heading.scope === 'col')).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.pagination-panel.bottom button'))
      .toHaveLength(5);
    expect(fixture.nativeElement.querySelector(
      '.pagination-panel.bottom .page-number[aria-current="page"]'
    )?.textContent?.trim()).toBe('1');
  });

  it('should ignore invalid enumerations and malformed dates from the URL', async () => {
    const getLogs = vi.fn().mockReturnValue(of(logsPage));
    await configure({
      severity: 'UNKNOWN',
      origin: 'MANUAL',
      occurredFrom: 'yesterday'
    }, getLogs);

    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();

    expect(getLogs).toHaveBeenCalledWith(0, 25, {
      deviceCode: undefined,
      severity: undefined,
      origin: undefined,
      eventType: undefined,
      deviceType: undefined,
      stationCode: undefined,
      occurredFrom: undefined,
      occurredTo: undefined
    });
  });

  it('should preserve the station context received from a station card', async () => {
    const getLogs = vi.fn().mockReturnValue(of(logsPage));
    await configure({ stationCode: 'ST001' }, getLogs);

    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();

    expect(getLogs).toHaveBeenCalledWith(0, 25, {
      deviceCode: undefined,
      severity: undefined,
      origin: undefined,
      eventType: undefined,
      deviceType: undefined,
      stationCode: 'ST001',
      occurredFrom: undefined,
      occurredTo: undefined
    });
    expect(fixture.componentInstance.selectedStationCode).toBe('ST001');
    const stationFilter = fixture.nativeElement.querySelector(
      '.filters-grid label:nth-of-type(6) select'
    ) as HTMLSelectElement;
    expect(stationFilter.value).toBe('ST001');
    expect(stationFilter.selectedOptions[0]?.textContent).toContain('Los Molinos');
    fixture.destroy();
  });

  it('should expose nearby pages and request a selected page directly', async () => {
    const pagedResponse: OperationalLogPage = {
      ...logsPage,
      currentPage: 69,
      totalElements: 9_425,
      totalPages: 377,
      first: false,
      last: false
    };
    const getLogs = vi.fn().mockReturnValue(of(pagedResponse));
    await configure({}, getLogs);

    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const topPageButtons = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>(
        '.pagination-panel:not(.bottom) .page-number'
      )
    );

    expect(topPageButtons.map((button) => button.textContent?.trim()))
      .toEqual(['1', '2', '69', '70', '71', '376', '377']);
    expect(topPageButtons[3].getAttribute('aria-current')).toBe('page');
    topPageButtons[4].click();

    expect(getLogs).toHaveBeenLastCalledWith(70, 25, {
      deviceCode: undefined,
      deviceType: undefined,
      severity: undefined,
      origin: undefined,
      eventType: undefined,
      stationCode: undefined,
      occurredFrom: undefined,
      occurredTo: undefined
    });
    fixture.destroy();
  });

  it('should open the ellipsis selector and jump to any valid page', async () => {
    const firstResponse: OperationalLogPage = {
      ...logsPage,
      totalElements: 9_425,
      totalPages: 377,
      first: true,
      last: false
    };
    const getLogs = vi.fn().mockReturnValue(of(firstResponse));
    await configure({}, getLogs);
    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const topPageButtons = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>(
        '.pagination-panel:not(.bottom) .page-number'
      )
    );

    expect(topPageButtons.map((button) => button.textContent?.trim()))
      .toEqual(['1', '2', '3', '375', '376', '377']);
    const gapButton = compiled.querySelector<HTMLButtonElement>(
      '.pagination-panel:not(.bottom) .pagination-gap'
    )!;
    gapButton.click();
    fixture.detectChanges();

    const jumpInput = compiled.querySelector<HTMLInputElement>(
      '.pagination-panel:not(.bottom) .page-jump input'
    )!;
    const jumpForm = compiled.querySelector<HTMLFormElement>(
      '.pagination-panel:not(.bottom) .page-jump'
    )!;
    jumpInput.value = '70';
    jumpInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    jumpForm.dispatchEvent(new Event('submit'));

    expect(getLogs).toHaveBeenLastCalledWith(69, 25, {
      deviceCode: undefined,
      deviceType: undefined,
      severity: undefined,
      origin: undefined,
      eventType: undefined,
      stationCode: undefined,
      occurredFrom: undefined,
      occurredTo: undefined
    });
    expect(componentPageJumpState(fixture.componentInstance)).toEqual([null, '']);
    fixture.destroy();
  });

  it('should combine filters, return to the first page and clear every criterion', async () => {
    const pagedResponse: OperationalLogPage = {
      ...logsPage,
      currentPage: 4,
      totalElements: 150,
      totalPages: 6,
      first: false,
      last: false
    };
    const getLogs = vi.fn().mockReturnValue(of(pagedResponse));
    await configure({}, getLogs);
    const fixture = TestBed.createComponent(Logs);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.selectedDeviceType = 'EXIT_VALIDATOR';
    component.selectedSeverity = 'WARNING';
    component.selectedStationCode = 'ST001';
    component.applyFilters();

    expect(component.hasActiveFilters()).toBe(true);
    expect(getLogs).toHaveBeenLastCalledWith(0, 25, {
      deviceCode: undefined,
      deviceType: 'EXIT_VALIDATOR',
      severity: 'WARNING',
      origin: undefined,
      eventType: undefined,
      stationCode: 'ST001',
      occurredFrom: undefined,
      occurredTo: undefined
    });

    component.clearFilters();

    expect(component.hasActiveFilters()).toBe(false);
    expect(component.selectedDeviceType).toBe('ALL');
    expect(getLogs).toHaveBeenLastCalledWith(0, 25, {
      deviceCode: undefined,
      deviceType: undefined,
      severity: undefined,
      origin: undefined,
      eventType: undefined,
      stationCode: undefined,
      occurredFrom: undefined,
      occurredTo: undefined
    });
    fixture.destroy();
  });
});

async function configure(
  queryParams: Record<string, string>,
  getLogs: ReturnType<typeof vi.fn>
) {
  await TestBed.configureTestingModule({
    imports: [Logs],
    providers: [
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } }
      },
      { provide: OperationalLogsService, useValue: { getLogs } },
      {
        provide: DeviceOperationsService,
        useValue: { getOperations: () => of(deviceOperations) }
      }
    ]
  }).compileComponents();
}

function componentPageJumpState(component: Logs): [string | null, string] {
  return [component.openPageJump, component.pageJumpValue];
}
