import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import {
  CompensatoryTicketIssuanceResponse,
  TransportTitlesResponse
} from '../../core/models/transport-title.model';
import { DeviceOperationsResponse } from '../../core/models/device-operation.model';
import { NetworkMapResponse } from '../../core/models/network-map.model';
import { PassengerAccountsPage } from '../../core/models/passenger-account.model';
import { TransportTitlesService } from '../../core/services/transport-titles.service';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import { NetworkMapService } from '../../core/services/network-map.service';
import { PassengerAccountsService } from '../../core/services/passenger-accounts.service';
import { TransportTitles } from './transport-titles';

const response: TransportTitlesResponse = {
  currency: 'EUR',
  summary: {
    totalTitles: 4,
    filteredTitles: 4,
    activeTitles: 3,
    inactiveTitles: 1,
    byType: { SINGLE_TRIP: 1, MULTI_TRIP: 1, TIME_PASS: 1, SMART_BALANCE: 1 }
  },
  titles: [
    {
      id: 1, code: 'SINGLE_TRIP', name: 'Billete sencillo',
      description: 'Billete para un trayecto concreto.', type: 'SINGLE_TRIP',
      basePrice: 0.5, pricePerStation: 0.05, pricePerTrip: 0, pricePerDay: 0,
      minTrips: null, maxTrips: null, minDays: null, maxDays: null,
      minRechargeAmount: null, maxRechargeAmount: null,
      requiresOriginDestination: true, usesTripBalance: false,
      usesDayValidity: false, usesMoneyBalance: false,
      rechargeable: true, active: true, createdAt: '', updatedAt: ''
    },
    {
      id: 2, code: 'MULTI_TRIP', name: 'Billete multiviaje',
      description: 'Bono basado en saldo de viajes.', type: 'MULTI_TRIP',
      basePrice: 0, pricePerStation: 0, pricePerTrip: 1, pricePerDay: 0,
      minTrips: 2, maxTrips: 30, minDays: null, maxDays: null,
      minRechargeAmount: null, maxRechargeAmount: null,
      requiresOriginDestination: false, usesTripBalance: true,
      usesDayValidity: false, usesMoneyBalance: false,
      rechargeable: true, active: false, createdAt: '', updatedAt: ''
    }
  ]
};

describe('TransportTitles', () => {
  it('should render summaries, prices, limits and statuses', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('.title-card')).toHaveLength(2);
    expect(compiled.textContent).toContain('Billete sencillo');
    expect(compiled.textContent).toContain('0,50');
    expect(compiled.textContent).toContain('por estación');
    expect(compiled.textContent).toContain('De 2 a 30 viajes');
    expect(compiled.textContent).toContain('Inactivo');
    expect(Array.from(compiled.querySelectorAll('.summary-card strong'))
      .map((element) => element.textContent?.trim())).toEqual(['4', '3', '1', '2']);
  });

  it('should filter by search, product type and status', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const input = compiled.querySelector<HTMLInputElement>('input[type="search"]')!;
    const selects = compiled.querySelectorAll<HTMLSelectElement>('select');
    input.value = 'multiviaje';
    input.dispatchEvent(new Event('input'));
    selects[0].value = 'MULTI_TRIP';
    selects[0].dispatchEvent(new Event('change'));
    selects[1].value = 'INACTIVE';
    selects[1].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.componentInstance.filteredTitles()).toHaveLength(1);
    const cards = compiled.querySelectorAll<HTMLElement>('.title-card');
    expect(cards).toHaveLength(1);
    expect(cards[0].textContent).toContain('Billete multiviaje');
    expect(cards[0].textContent).not.toContain('Billete sencillo');
  });

  it('should display a recoverable error state', async () => {
    await configureWith(() => throwError(() => new Error('network')));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent)
      .toContain('No se ha podido cargar');
    expect(compiled.querySelector('button')?.textContent).toContain('Reintentar');
  });

  it('should validate and submit a compensatory single-trip issuance form', async () => {
    const issueCompensatoryTicket = vi.fn().mockReturnValue(of(issuanceResponse()));
    await configureWith(
      () => of(response),
      issueCompensatoryTicket,
      () => of({
        evaluatedAt: '2026-08-05T10:00:00',
        summary: {
          totalDevices: 1, filteredDevices: 1,
          byType: { TICKET_MACHINE: 1, ENTRY_VALIDATOR: 0, EXIT_VALIDATOR: 0 },
          byStatus: { ONLINE: 1, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 },
          byConnectivity: { CONNECTED: 0, DISCONNECTED: 0, NOT_MONITORED: 1 }
        },
        devices: [{
          id: 1, code: 'TM-ST001-01', name: 'Máquina Aeropuerto',
          type: 'TICKET_MACHINE', status: 'ONLINE', lastConnectionAt: null,
          connectivity: {
            state: 'NOT_MONITORED', mqttPresence: 'OFFLINE', operationalState: 'AVAILABLE',
            lastCommunicationAt: null, lastPresenceAt: null, lastStatusAt: null,
            serviceMode: null, softwareVersion: null, uptimeSeconds: null
          },
          station: { id: 1, code: 'ST001', name: 'Aeropuerto' }
        }]
      }),
      () => of({ lines: [{
        id: 1, code: 'L2', name: 'Línea 2', color: '#000000',
        stations: [
          { id: 1, code: 'ST001', name: 'Aeropuerto', stationOrder: 1 },
          { id: 2, code: 'ST002', name: 'Plaza de la Merced', stationOrder: 2 }
        ]
      }] }),
      () => of({
        summary: {
          totalAccounts: 0, activeAccounts: 0, blockedAccounts: 0,
          disabledAccounts: 0, pendingVerificationAccounts: 0
        },
        users: [{
          publicId: 'passenger-1', email: 'ana@example.com', firstName: 'Ana', lastName: 'Ruiz',
          status: 'ACTIVE', emailVerified: true, emailVerifiedAt: null,
          lastLoginAt: null, registeredAt: '', updatedAt: ''
        }],
        page: 0, pageSize: 100, totalElements: 1, totalPages: 1,
        first: true, last: true, empty: false
      })
    );
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.openIssuanceDialog(response.titles[0]);
    expect(component.ticketMachines).toHaveLength(1);
    expect(component.passengers).toHaveLength(1);
    expect(component.stations).toHaveLength(2);
    component.selectDevice('TM-ST001-01');
    expect(component.selectedPassengerPublicId).toBe('');
    component.setDeliveryMethod('DIGITAL_WALLET');
    component.selectPassenger('passenger-1');
    expect(component.selectedDeviceCode).toBe('');
    expect(component.selectedPassenger()?.email).toBe('ana@example.com');
    component.setDeliveryMethod('PHYSICAL_DEVICE');
    component.selectDevice('TM-ST001-01');
    component.originStationCode = 'ST001';
    component.destinationStationCode = 'ST002';
    component.issuanceReason = '  Fallo durante la compra  ';
    expect(component.canSubmitIssuance()).toBe(true);

    component.submitCompensatoryIssuance();

    expect(issueCompensatoryTicket).toHaveBeenCalledWith(1, {
      deliveryMethod: 'PHYSICAL_DEVICE', deviceCode: 'TM-ST001-01',
      reason: 'Fallo durante la compra',
      originStationCode: 'ST001', destinationStationCode: 'ST002'
    });
    expect(component.issuanceTitle).toBe(response.titles[0]);
    expect(component.issuanceProgress).toBe('COMPLETED');
    expect(component.issuanceResult?.ticketCode).toBe('RMM-1');
    expect(component.issuanceQrSource(component.issuanceResult!)).toBe('data:image/png;base64,cXItcG5n');
    expect(component.issuanceResultMessage()).toContain('finalizado correctamente');
    expect(component.issuanceConfirmation).toContain('RMM-1');
  });

  it('should show a completed digital delivery in the passenger wallet', async () => {
    const issuance = issuanceResponse({
      deliveryMethod: 'DIGITAL_WALLET', deviceCode: null, deviceName: null,
      stationCode: null, stationName: null, passengerPublicId: 'passenger-1',
      passengerEmail: 'ana@example.com'
    });
    await configureWith(() => of(response), vi.fn().mockReturnValue(of(issuance)));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.issuanceTitle = response.titles[1];
    component.setDeliveryMethod('DIGITAL_WALLET');
    component.selectedPassengerPublicId = 'passenger-1';
    vi.spyOn(component, 'selectedPassenger').mockReturnValue({ email: 'ana@example.com' } as never);
    component.selectedTrips = 2;
    component.issuanceReason = 'Compensación digital';

    component.submitCompensatoryIssuance();

    expect(component.issuanceProgress).toBe('COMPLETED');
    expect(component.issuanceResultMessage()).toContain('cartera del pasajero');
    expect(component.issuanceQrSource(issuance)).toContain('data:image/png;base64');
  });

  it('should keep a physical MQTT delivery pending until the machine confirms it', async () => {
    const issuance = issuanceResponse({ status: 'PROCESSING', completedAt: null });
    await configureWith(() => of(response), vi.fn().mockReturnValue(of(issuance)));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.issuanceTitle = response.titles[1];
    component.selectedDeviceCode = 'TM-ST001-01';
    vi.spyOn(component, 'selectedMachine').mockReturnValue({ code: 'TM-ST001-01' } as never);
    component.selectedTrips = 2;
    component.issuanceReason = 'Reimpresión pendiente';

    component.submitCompensatoryIssuance();

    expect(component.issuanceProgress).toBe('PROCESSING');
    expect(component.issuanceResultMessage()).toContain('pendiente de confirmación');
  });

  it('should explain a simulated physical delivery without creating a ticket or QR', async () => {
    const issuance = issuanceResponse({
      simulated: true, ticketCode: null, qrToken: null, qrPngBase64: null
    });
    await configureWith(() => of(response), vi.fn().mockReturnValue(of(issuance)));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.issuanceTitle = response.titles[1];
    component.selectedDeviceCode = 'TM-ST001-01';
    vi.spyOn(component, 'selectedMachine').mockReturnValue({ code: 'TM-ST001-01' } as never);
    component.selectedTrips = 2;
    component.issuanceReason = 'Simulación administrativa';

    component.submitCompensatoryIssuance();

    expect(component.issuanceResultMessage()).toContain('sin generar un billete');
    expect(component.issuanceQrSource(issuance)).toBeNull();
  });

  it('should preserve the form and expose a recoverable error when issuance fails', async () => {
    await configureWith(
      () => of(response),
      vi.fn().mockReturnValue(throwError(() => new Error('issuance failed')))
    );
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.issuanceTitle = response.titles[1];
    component.selectedDeviceCode = 'TM-ST001-01';
    vi.spyOn(component, 'selectedMachine').mockReturnValue({ code: 'TM-ST001-01' } as never);
    component.selectedTrips = 2;
    component.issuanceReason = 'Reintento de emisión';

    component.submitCompensatoryIssuance();

    expect(component.issuanceProgress).toBe('FAILED');
    expect(component.issuanceResult).toBeNull();
    expect(component.issuanceError).toContain('No se ha podido completar');
  });

  it('should render the stable ticket identity and its verifiable QR', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(TransportTitles);
    const component = fixture.componentInstance;
    component.issuanceTitle = response.titles[0];
    component.issuanceProgress = 'COMPLETED';
    component.issuanceResult = issuanceResponse();

    fixture.detectChanges();
    const ticket = fixture.nativeElement.querySelector('.issued-ticket') as HTMLElement;

    expect(ticket.textContent).toContain('Billete sencillo');
    expect(ticket.textContent).toContain('RMM-1');
    expect(ticket.textContent).not.toContain('viajes restantes');
    expect(ticket.querySelector<HTMLImageElement>('.issued-ticket-qr img')?.src)
      .toContain('data:image/png;base64,cXItcG5n');
  });

  it('should retain tablet and mobile layouts for cards and the issuance dialog', async () => {
    await configureWith(() => of(response));
    const fixture = TestBed.createComponent(TransportTitles);
    fixture.detectChanges();
    const styles = loadedComponentStyles();

    expect(styles).toContain('@media (max-width: 1000px)');
    expect(styles).toContain('@media (max-width: 760px)');
    expect(styles).toContain('@media (max-width: 520px)');
    expect(styles).toMatch(/\.issuance-dialog[^}]*width:\s*min/);
  });
});

async function configureWith(
  getTitles: () => Observable<TransportTitlesResponse>,
  issueCompensatoryTicket = vi.fn(),
  getOperations: () => Observable<DeviceOperationsResponse> = vi.fn(),
  getNetworkMap: () => Observable<NetworkMapResponse> = vi.fn(),
  getAccounts: () => Observable<PassengerAccountsPage> = () => of({
    summary: {
      totalAccounts: 0, activeAccounts: 0, blockedAccounts: 0,
      disabledAccounts: 0, pendingVerificationAccounts: 0
    },
    users: [], page: 0, pageSize: 100, totalElements: 0, totalPages: 0,
    first: true, last: true, empty: true
  })
) {
  await TestBed.configureTestingModule({
    imports: [TransportTitles],
    providers: [
      { provide: TransportTitlesService, useValue: { getTitles, issueCompensatoryTicket } },
      { provide: DeviceOperationsService, useValue: { getOperations } },
      { provide: NetworkMapService, useValue: { getNetworkMap } },
      { provide: PassengerAccountsService, useValue: { getAccounts } }
    ]
  }).compileComponents();
}

function loadedComponentStyles(): string {
  return Array.from(document.head.querySelectorAll('style'))
    .map((style) => style.textContent ?? '')
    .join('\n');
}

function issuanceResponse(
  overrides: Partial<CompensatoryTicketIssuanceResponse> = {}
): CompensatoryTicketIssuanceResponse {
  return {
    id: 1, code: 'COMP-1', status: 'COMPLETED', simulated: false,
    ticketCode: 'RMM-1', qrToken: 'qr-token', qrPngBase64: 'cXItcG5n',
    productCode: 'SINGLE_TRIP', productName: 'Billete sencillo',
    productType: 'SINGLE_TRIP', deviceCode: 'TM-ST001-01',
    deliveryMethod: 'PHYSICAL_DEVICE', passengerPublicId: null, passengerEmail: null,
    deviceName: 'Máquina Aeropuerto', stationCode: 'ST001', stationName: 'Aeropuerto',
    operatorUsername: 'admin', chargedAmount: 0,
    requestedAt: '2026-08-05T10:00:00', completedAt: '2026-08-05T10:00:00',
    ...overrides
  };
}
