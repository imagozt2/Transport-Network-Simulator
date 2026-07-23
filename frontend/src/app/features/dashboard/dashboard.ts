import { Component, inject, OnInit } from '@angular/core';
import { DashboardResponse, DeviceStatus, DeviceType, TrainStatus } from '../../core/models/dashboard.model';
import { DashboardService } from '../../core/services/dashboard.service';
import { lineColor } from '../../core/utils/line-visuals';

interface StatusItem<T extends string> { status: T; label: string; tone: 'ok' | 'neutral' | 'warning' | 'danger'; }

@Component({ selector: 'app-dashboard', templateUrl: './dashboard.html', styleUrl: './dashboard.css' })
export class Dashboard implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  readonly trainStatuses: StatusItem<TrainStatus>[] = [
    { status: 'IN_SERVICE', label: 'En servicio', tone: 'ok' },
    { status: 'DEPOT', label: 'En cochera', tone: 'neutral' },
    { status: 'MAINTENANCE', label: 'Mantenimiento', tone: 'warning' },
    { status: 'OUT_OF_SERVICE', label: 'Fuera de servicio', tone: 'danger' },
    { status: 'STOPPED', label: 'Detenido', tone: 'warning' },
    { status: 'RESERVE', label: 'Reserva', tone: 'neutral' },
    { status: 'HISTORIC', label: 'Histórico', tone: 'neutral' }
  ];
  readonly deviceStatuses: StatusItem<DeviceStatus>[] = [
    { status: 'ONLINE', label: 'Online', tone: 'ok' },
    { status: 'OFFLINE', label: 'Offline', tone: 'neutral' },
    { status: 'MAINTENANCE', label: 'Mantenimiento', tone: 'warning' },
    { status: 'ERROR', label: 'Error', tone: 'danger' }
  ];
  readonly deviceTypes: { type: DeviceType; label: string }[] = [
    { type: 'TICKET_MACHINE', label: 'Máquinas de compra' },
    { type: 'ENTRY_VALIDATOR', label: 'Validadores de entrada' },
    { type: 'EXIT_VALIDATOR', label: 'Validadores de salida' }
  ];

  summary: DashboardResponse | null = null;
  loading = true;
  errorMessage = '';

  ngOnInit(): void { this.loadSummary(); }

  loadSummary(): void {
    this.loading = true;
    this.errorMessage = '';
    this.dashboardService.getSummary().subscribe({
      next: (summary) => { this.summary = summary; this.loading = false; },
      error: () => { this.errorMessage = 'No se ha podido cargar la información del centro de control.'; this.loading = false; }
    });
  }

  trainCount(status: TrainStatus): number { return this.summary?.fleet.byStatus[status] ?? 0; }
  deviceStatusCount(status: DeviceStatus): number { return this.summary?.devices.byStatus[status] ?? 0; }
  deviceTypeCount(type: DeviceType): number { return this.summary?.devices.byType[type] ?? 0; }

  lineColor(color: string): string {
    return lineColor('', color);
  }
}
