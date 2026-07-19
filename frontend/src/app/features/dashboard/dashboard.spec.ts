import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DashboardResponse } from '../../core/models/dashboard.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { Dashboard } from './dashboard';

const summary: DashboardResponse = {
  network: { activeStations: 24, activeLines: 6 },
  fleet: { activeTrains: 18, byStatus: { IN_SERVICE: 12, DEPOT: 6 } },
  devices: {
    activeDevices: 30,
    byStatus: { ONLINE: 28, MAINTENANCE: 2 },
    byType: { TICKET_MACHINE: 10, ENTRY_VALIDATOR: 10, EXIT_VALIDATOR: 10 }
  },
  depots: {
    activeDepots: 1,
    totalCapacity: 20,
    assignedTrains: 6,
    freeSlots: 14,
    occupationPercentage: 30,
    items: [{ id: 1, code: 'DEP-01', name: 'Cochera Central', capacity: 20, assignedTrains: 6, freeSlots: 14 }]
  },
  lines: [{ id: 1, code: 'L1', name: 'Línea Central', color: 'Roja' }]
};

describe('Dashboard', () => {
  it('should render the operational summary', async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [{ provide: DashboardService, useValue: { getSummary: () => of(summary) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const content = fixture.nativeElement.textContent as string;

    expect(content).toContain('Panel General');
    expect(content).toContain('24');
    expect(content).toContain('Cochera Central');
    expect(content).toContain('Línea Central');
  });

  it('should show a retry action when loading fails', async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [{ provide: DashboardService, useValue: { getSummary: () => throwError(() => new Error('connection error')) } }]
    }).compileComponents();

    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se ha podido cargar');
    expect(compiled.querySelector('.error-card button')?.textContent).toContain('Reintentar');
  });
});
