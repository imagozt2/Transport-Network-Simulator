import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { routes } from '../../app.routes';
import { DeviceOperationsResponse } from '../../core/models/device-operation.model';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import { Devices } from './devices';

const response: DeviceOperationsResponse = {
  evaluatedAt: '2026-07-23T12:00:00+02:00',
  summary: {
    totalDevices: 2,
    filteredDevices: 2,
    byType: { TICKET_MACHINE: 2, ENTRY_VALIDATOR: 0, EXIT_VALIDATOR: 0 },
    byStatus: { ONLINE: 2, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 }
  },
  devices: [
    {
      id: 10,
      code: 'RMM-MB-ST001-001',
      name: 'Máquina de billetes 1',
      type: 'TICKET_MACHINE',
      status: 'ONLINE',
      lastConnectionAt: '2026-07-23T11:59:55+02:00',
      station: { id: 1, code: 'ST001', name: 'Los Molinos' }
    },
    {
      id: 11,
      code: 'RMM-MB-ST002-001',
      name: 'Máquina de billetes 2',
      type: 'TICKET_MACHINE',
      status: 'ONLINE',
      lastConnectionAt: '2026-07-23T11:59:55+02:00',
      station: { id: 2, code: 'ST002', name: 'Cuatro Caminos' }
    }
  ]
};

describe('Devices log navigation', () => {
  it('should link each device card to logs using its stable code', async () => {
    await TestBed.configureTestingModule({
      imports: [Devices],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ stationCode: 'ST001' }) } }
        },
        {
          provide: DeviceOperationsService,
          useValue: { getOperations: () => of(response) }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(Devices);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const cardHeader = compiled.querySelector('.device-card-header') as HTMLButtonElement;

    expect(cardHeader.getAttribute('aria-expanded')).toBe('false');
    expect(compiled.querySelector('.logs-link')).toBeNull();
    expect(compiled.querySelector('.device-heading')?.textContent).toContain('Los Molinos');
    cardHeader.click();
    fixture.detectChanges();

    const link = compiled.querySelector('.logs-link') as HTMLAnchorElement;
    expect(cardHeader.getAttribute('aria-expanded')).toBe('true');
    expect(link.getAttribute('href')).toBe('/logs?deviceCode=RMM-MB-ST001-001');
    expect(link.getAttribute('aria-label')).toContain('RMM-MB-ST001-001');
    expect(compiled.querySelector('.device-details')?.textContent).toContain('Última conexión');
    expect(compiled.querySelector('.device-details')?.textContent).not.toContain('Estado operativo');
    expect(fixture.componentInstance.selectedStationCode).toBe('ST001');
    expect(fixture.componentInstance.filteredDevices()).toHaveLength(1);
    expect(fixture.componentInstance.filteredDevices()[0].station.code).toBe('ST001');
    expect(compiled.textContent).not.toContain('RMM-MB-ST002-001');
    const summaryCards = Array.from(compiled.querySelectorAll<HTMLElement>('.summary-card'));
    expect(summaryCards.map((card) => card.querySelector('span')?.textContent?.trim())).toEqual([
      'Total de máquinas',
      'Online',
      'Offline',
      'En mantenimiento',
      'Con error',
      'Máquinas de billetes',
      'Validadores de entrada',
      'Validadores de salida'
    ]);
    expect(summaryCards.map((card) => card.querySelector('strong')?.textContent?.trim()))
      .toEqual(['2', '2', '0', '0', '0', '2', '0', '0']);
    expect(compiled.querySelector('.type-overview')).toBeNull();
    expect(compiled.querySelector('.summary-card small')).toBeNull();
    fixture.destroy();
  });

  it('should register the global logs route', () => {
    const layoutRoute = routes.find((route) => route.path === '');
    const logsRoute = layoutRoute?.children?.find((route) => route.path === 'logs');

    expect(logsRoute).toBeDefined();
  });
});
