import { Component, inject, OnInit } from '@angular/core';

import { DeviceOperation } from '../../core/models/device-operation.model';
import { OperationalLog } from '../../core/models/operational-log.model';
import {
  DeviceEventType,
  LogOrigin,
  LogSeverity
} from '../../core/models/operational-log.types';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import {
  OperationalLogFilters,
  OperationalLogsService
} from '../../core/services/operational-logs.service';

type OptionalSeverity = LogSeverity | 'ALL';
type OptionalOrigin = LogOrigin | 'ALL';
type OptionalEventType = DeviceEventType | 'ALL';

@Component({
  selector: 'app-logs',
  templateUrl: './logs.html',
  styleUrls: ['./logs.css', './logs-list.css']
})
export class Logs implements OnInit {
  private readonly logsService = inject(OperationalLogsService);
  private readonly devicesService = inject(DeviceOperationsService);

  logs: OperationalLog[] = [];
  devices: DeviceOperation[] = [];
  loading = true;
  errorMessage = '';
  currentPage = 0;
  pageSize = 25;
  totalElements = 0;
  totalPages = 0;
  firstPage = true;
  lastPage = true;

  selectedSeverity: OptionalSeverity = 'ALL';
  selectedOrigin: OptionalOrigin = 'ALL';
  selectedEventType: OptionalEventType = 'ALL';
  selectedDeviceCode = 'ALL';
  selectedStationCode = 'ALL';
  occurredFrom = '';
  occurredTo = '';

  readonly pageSizes = [25, 50, 100];
  readonly severities: readonly LogSeverity[] = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'];
  readonly origins: readonly LogOrigin[] = ['DEVICE_SIMULATION', 'MQTT'];
  readonly eventTypes: readonly DeviceEventType[] = [
    'DEVICE_ONLINE',
    'DEVICE_OFFLINE',
    'DEVICE_STATUS_CHANGED',
    'DEVICE_ERROR',
    'DEVICE_MAINTENANCE_STARTED',
    'DEVICE_MAINTENANCE_FINISHED',
    'TICKET_PURCHASE_REQUESTED',
    'TICKET_PURCHASE_COMPLETED',
    'TICKET_PURCHASE_FAILED',
    'QR_TICKET_GENERATED',
    'QR_TICKET_SCANNED',
    'VALIDATION_REQUESTED',
    'VALIDATION_ACCEPTED',
    'VALIDATION_REJECTED',
    'VALIDATION_FAILED'
  ];

  ngOnInit(): void {
    this.loadFilterOptions();
    this.loadLogs(0);
  }

  loadLogs(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';

    this.logsService.getLogs(Math.max(0, page), this.pageSize, this.buildFilters()).subscribe({
      next: (response) => {
        this.logs = response.logs;
        this.currentPage = response.currentPage;
        this.pageSize = response.pageSize;
        this.totalElements = response.totalElements;
        this.totalPages = response.totalPages;
        this.firstPage = response.first;
        this.lastPage = response.last;
        this.loading = false;
      },
      error: () => {
        this.logs = [];
        this.errorMessage = 'No se han podido cargar los logs operativos.';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.loadLogs(0);
  }

  clearFilters(): void {
    this.selectedSeverity = 'ALL';
    this.selectedOrigin = 'ALL';
    this.selectedEventType = 'ALL';
    this.selectedDeviceCode = 'ALL';
    this.selectedStationCode = 'ALL';
    this.occurredFrom = '';
    this.occurredTo = '';
    this.loadLogs(0);
  }

  hasActiveFilters(): boolean {
    return this.selectedSeverity !== 'ALL'
      || this.selectedOrigin !== 'ALL'
      || this.selectedEventType !== 'ALL'
      || this.selectedDeviceCode !== 'ALL'
      || this.selectedStationCode !== 'ALL'
      || this.occurredFrom !== ''
      || this.occurredTo !== '';
  }

  stationOptions(): { code: string; name: string }[] {
    const stations = new Map<string, string>();
    this.devices.forEach((device) => stations.set(device.station.code, device.station.name));
    return [...stations.entries()]
      .map(([code, name]) => ({ code, name }))
      .sort((first, second) => first.name.localeCompare(second.name, 'es'));
  }

  goToPreviousPage(): void {
    if (!this.firstPage) {
      this.loadLogs(this.currentPage - 1);
    }
  }

  goToNextPage(): void {
    if (!this.lastPage) {
      this.loadLogs(this.currentPage + 1);
    }
  }

  setPageSize(value: string): void {
    this.pageSize = Number(value);
    this.loadLogs(0);
  }

  pageLabel(): string {
    return this.totalPages === 0
      ? 'Página 0 de 0'
      : `Página ${this.currentPage + 1} de ${this.totalPages}`;
  }

  warningCount(): number {
    return this.logs.filter((log) => log.severity === 'WARNING').length;
  }

  errorCount(): number {
    return this.logs.filter(
      (log) => log.severity === 'ERROR' || log.severity === 'CRITICAL'
    ).length;
  }

  severityLabel(severity: LogSeverity): string {
    return {
      DEBUG: 'Debug',
      INFO: 'Información',
      WARNING: 'Aviso',
      ERROR: 'Error',
      CRITICAL: 'Crítico'
    }[severity];
  }

  originLabel(origin: LogOrigin): string {
    return origin === 'MQTT' ? 'MQTT' : 'Simulación';
  }

  eventTypeLabel(type: DeviceEventType): string {
    const labels: Record<DeviceEventType, string> = {
      DEVICE_ONLINE: 'Máquina conectada',
      DEVICE_OFFLINE: 'Máquina desconectada',
      DEVICE_STATUS_CHANGED: 'Cambio de estado',
      DEVICE_ERROR: 'Error de máquina',
      DEVICE_MAINTENANCE_STARTED: 'Inicio de mantenimiento',
      DEVICE_MAINTENANCE_FINISHED: 'Fin de mantenimiento',
      TICKET_PURCHASE_REQUESTED: 'Compra solicitada',
      TICKET_PURCHASE_COMPLETED: 'Compra completada',
      TICKET_PURCHASE_FAILED: 'Compra fallida',
      QR_TICKET_GENERATED: 'QR generado',
      QR_TICKET_SCANNED: 'QR escaneado',
      VALIDATION_REQUESTED: 'Validación solicitada',
      VALIDATION_ACCEPTED: 'Validación aceptada',
      VALIDATION_REJECTED: 'Validación rechazada',
      VALIDATION_FAILED: 'Validación fallida'
    };
    return labels[type];
  }

  formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(new Date(value));
  }

  trackLog(_: number, log: OperationalLog): number {
    return log.id;
  }

  private buildFilters(): OperationalLogFilters {
    return {
      origin: this.selectedOrigin === 'ALL' ? undefined : this.selectedOrigin,
      severity: this.selectedSeverity === 'ALL' ? undefined : this.selectedSeverity,
      eventType: this.selectedEventType === 'ALL' ? undefined : this.selectedEventType,
      deviceCode: this.selectedDeviceCode === 'ALL' ? undefined : this.selectedDeviceCode,
      stationCode: this.selectedStationCode === 'ALL' ? undefined : this.selectedStationCode,
      occurredFrom: this.occurredFrom || undefined,
      occurredTo: this.occurredTo || undefined
    };
  }

  private loadFilterOptions(): void {
    this.devicesService.getOperations().subscribe({
      next: (response) => {
        this.devices = response.devices.slice().sort(
          (first, second) => first.code.localeCompare(second.code, 'es')
        );
      }
    });
  }
}
