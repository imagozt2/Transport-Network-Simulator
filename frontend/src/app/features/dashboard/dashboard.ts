import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { APPLICATION_ROUTES } from '../../core/navigation/application-routes';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { DeviceStatus, DeviceType } from '../../core/models/device-operation.model';
import { TrainStatus } from '../../core/models/train-operation.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { deviceStatusLabel, deviceTypeLabel, trainStatusLabel } from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { lineColor } from '../../core/utils/line-visuals';

interface StatusItem<T extends string> {
  status: T;
  tone: 'ok' | 'neutral' | 'warning' | 'danger';
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit, OnDestroy {
  protected readonly sectionRoutes = APPLICATION_ROUTES;
  private readonly dashboardService = inject(DashboardService);
  private readonly periodicRefresh = new PeriodicRefresh(5_000, () => this.loadSummary());

  readonly trainStatuses: readonly StatusItem<TrainStatus>[] = [
    { status: 'IN_SERVICE', tone: 'ok' },
    { status: 'DEPOT', tone: 'neutral' },
    { status: 'MAINTENANCE', tone: 'warning' },
    { status: 'STOPPED', tone: 'warning' },
    { status: 'OUT_OF_SERVICE', tone: 'danger' }
  ];
  readonly deviceStatuses: readonly StatusItem<DeviceStatus>[] = [
    { status: 'ONLINE', tone: 'ok' },
    { status: 'OFFLINE', tone: 'neutral' },
    { status: 'MAINTENANCE', tone: 'warning' },
    { status: 'ERROR', tone: 'danger' }
  ];
  readonly deviceTypes: readonly DeviceType[] = [
    'TICKET_MACHINE',
    'ENTRY_VALIDATOR',
    'EXIT_VALIDATOR'
  ];

  summary: DashboardResponse | null = null;
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.loadSummary();
    this.periodicRefresh.start();
  }

  ngOnDestroy(): void {
    this.periodicRefresh.destroy();
  }

  loadSummary(): void {
    const request = this.periodicRefresh.request(() => this.dashboardService.getSummary());
    if (!request) { return; }

    if (!this.summary) { this.loading = true; }
    this.errorMessage = '';
    request.subscribe({
      next: (summary) => {
        this.summary = summary;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar la información del centro de control.';
        this.loading = false;
      }
    });
  }

  trainStatusLabel(status: TrainStatus): string { return trainStatusLabel(status); }
  deviceStatusLabel(status: DeviceStatus): string { return deviceStatusLabel(status); }
  deviceTypeLabel(type: DeviceType): string { return deviceTypeLabel(type); }
  lineColor(code: string, color: string): string { return lineColor(code, color); }
}
