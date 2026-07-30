import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { TransportTitlesResponse } from '../../core/models/transport-title.model';
import { TransportTitlesService } from '../../core/services/transport-titles.service';
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
});

async function configureWith(getTitles: () => Observable<TransportTitlesResponse>) {
  await TestBed.configureTestingModule({
    imports: [TransportTitles],
    providers: [{ provide: TransportTitlesService, useValue: { getTitles } }]
  }).compileComponents();
}
