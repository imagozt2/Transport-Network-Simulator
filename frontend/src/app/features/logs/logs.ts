import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';

import { DeviceOperation, DeviceType } from '../../core/models/device-operation.model';
import { OperationalLog } from '../../core/models/operational-log.model';
import {
  DeviceEventType,
  DeviceEventSource,
  LogOrigin,
  LogSeverity,
  TicketProductType
} from '../../core/models/operational-log.types';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import {
  OperationalLogFilters,
  OperationalLogsService
} from '../../core/services/operational-logs.service';
import { formatDateTime } from '../../core/utils/temporal-formatters';
import { deviceTypeLabel } from '../../core/utils/operation-labels';
import { deviceEventSourceLabel } from '../../core/utils/operation-labels';

type OptionalSeverity = LogSeverity | 'ALL';
type OptionalOrigin = LogOrigin | 'ALL';
type OptionalEventType = DeviceEventType | 'ALL';
type OptionalDeviceType = DeviceType | 'ALL';
type PaginationLocation = 'top' | 'bottom';
type PaginationItem =
  | { type: 'page'; page: number; key: string }
  | { type: 'gap'; key: string };

@Component({
  selector: 'app-logs',
  templateUrl: './logs.html',
  styleUrls: ['./logs.css', './logs-list.css']
})
export class Logs implements OnInit {
  private readonly logsService = inject(OperationalLogsService);
  private readonly devicesService = inject(DeviceOperationsService);
  private readonly route = inject(ActivatedRoute);

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
  openPageJump: PaginationLocation | null = null;
  pageJumpValue = '';

  selectedSeverity: OptionalSeverity = 'ALL';
  selectedOrigin: OptionalOrigin = 'ALL';
  selectedEventType: OptionalEventType = 'ALL';
  selectedDeviceType: OptionalDeviceType = 'ALL';
  selectedDeviceCode = 'ALL';
  selectedStationCode = 'ALL';
  occurredFrom = '';
  occurredTo = '';

  readonly pageSizes = [25, 50, 100];
  readonly severities: readonly LogSeverity[] = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'];
  readonly origins: readonly LogOrigin[] = ['ADMINISTRATION', 'DEVICE_SIMULATION', 'MQTT'];
  readonly deviceTypes: readonly DeviceType[] = [
    'TICKET_MACHINE',
    'ENTRY_VALIDATOR',
    'EXIT_VALIDATOR'
  ];
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
    'COMPENSATORY_TICKET_ISSUANCE_REQUESTED',
    'COMPENSATORY_TICKET_ISSUED',
    'QR_TICKET_GENERATED',
    'QR_TICKET_SCANNED',
    'VALIDATION_REQUESTED',
    'VALIDATION_ACCEPTED',
    'VALIDATION_REJECTED',
    'VALIDATION_FAILED'
  ];

  ngOnInit(): void {
    this.initializeFiltersFromUrl(this.route.snapshot.queryParamMap);
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
    this.selectedDeviceType = 'ALL';
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
      || this.selectedDeviceType !== 'ALL'
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

  goToFirstPage(): void {
    if (!this.firstPage) {
      this.loadLogs(0);
    }
  }

  goToNextPage(): void {
    if (!this.lastPage) {
      this.loadLogs(this.currentPage + 1);
    }
  }

  goToLastPage(): void {
    if (!this.lastPage && this.totalPages > 0) {
      this.loadLogs(this.totalPages - 1);
    }
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.closePageJump();
      this.loadLogs(page);
    }
  }

  paginationItems(): PaginationItem[] {
    if (this.totalPages === 0) {
      return [];
    }
    if (this.totalPages <= 7) {
      return Array.from({ length: this.totalPages }, (_, page) => ({
        type: 'page' as const,
        page,
        key: `page-${page}`
      }));
    }

    const pages = this.currentPage <= 2 || this.currentPage >= this.totalPages - 3
      ? [0, 1, 2, this.totalPages - 3, this.totalPages - 2, this.totalPages - 1]
      : [
          0,
          1,
          this.currentPage - 1,
          this.currentPage,
          this.currentPage + 1,
          this.totalPages - 2,
          this.totalPages - 1
        ];
    const uniquePages = [...new Set(pages)].sort((first, second) => first - second);
    const items: PaginationItem[] = [];

    uniquePages.forEach((page, index) => {
      const previousPage = uniquePages[index - 1];
      if (index > 0 && page - previousPage > 1) {
        items.push({ type: 'gap', key: `gap-${previousPage}-${page}` });
      }
      items.push({ type: 'page', page, key: `page-${page}` });
    });
    return items;
  }

  showPageJump(location: PaginationLocation): void {
    this.openPageJump = location;
    this.pageJumpValue = '';
  }

  closePageJump(): void {
    this.openPageJump = null;
    this.pageJumpValue = '';
  }

  setPageJumpValue(value: string): void {
    this.pageJumpValue = value;
  }

  isPageJumpValid(): boolean {
    if (!/^\d+$/.test(this.pageJumpValue.trim())) {
      return false;
    }
    const page = Number(this.pageJumpValue);
    return page >= 1 && page <= this.totalPages;
  }

  submitPageJump(): void {
    if (!this.isPageJumpValid()) {
      return;
    }
    const targetPage = Number(this.pageJumpValue) - 1;
    if (targetPage === this.currentPage) {
      this.closePageJump();
      return;
    }
    this.goToPage(targetPage);
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

  paginationRangeLabel(): string {
    if (this.totalElements === 0 || this.logs.length === 0) {
      return 'Sin resultados';
    }
    const firstResult = this.currentPage * this.pageSize + 1;
    const lastResult = firstResult + this.logs.length - 1;
    return `Mostrando ${firstResult}-${lastResult} de ${this.totalElements}`;
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
    return {
      ADMINISTRATION: 'Administración',
      DEVICE_SIMULATION: 'Simulación',
      MQTT: 'MQTT'
    }[origin];
  }

  sourceLabel(source: DeviceEventSource): string {
    return deviceEventSourceLabel(source);
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
      COMPENSATORY_TICKET_ISSUANCE_REQUESTED: 'Emisión compensatoria solicitada',
      COMPENSATORY_TICKET_ISSUED: 'Billete compensatorio emitido',
      QR_TICKET_GENERATED: 'QR generado',
      QR_TICKET_SCANNED: 'QR escaneado',
      VALIDATION_REQUESTED: 'Validación solicitada',
      VALIDATION_ACCEPTED: 'Validación aceptada',
      VALIDATION_REJECTED: 'Validación rechazada',
      VALIDATION_FAILED: 'Validación fallida'
    };
    return labels[type];
  }

  operationCategory(type: DeviceEventType): 'sale' | 'issuance' | 'validation' | null {
    if (type.startsWith('TICKET_PURCHASE_')) return 'sale';
    if (type.startsWith('COMPENSATORY_TICKET_ISSUANCE_')
        || type === 'COMPENSATORY_TICKET_ISSUED'
        || type === 'QR_TICKET_GENERATED') return 'issuance';
    if (type.startsWith('VALIDATION_') || type === 'QR_TICKET_SCANNED') return 'validation';
    return null;
  }

  operationCategoryLabel(type: DeviceEventType): string | null {
    const category = this.operationCategory(type);
    return category ? { sale: 'Venta', issuance: 'Emisión', validation: 'Validación' }[category] : null;
  }

  ticketTypeLabel(type: TicketProductType): string {
    return {
      SINGLE_TRIP: 'Billete sencillo',
      MULTI_TRIP: 'Bono multiviaje',
      TIME_PASS: 'Abono temporal',
      SMART_BALANCE: 'Saldo inteligente'
    }[type];
  }

  operationReference(log: OperationalLog): string | null {
    return log.ticketCode ?? log.compensatoryIssuanceCode ?? log.externalReference;
  }

  deviceTypeLabel(type: DeviceType): string {
    return deviceTypeLabel(type);
  }

  formatDateTime(value: string): string {
    return formatDateTime(value);
  }

  private buildFilters(): OperationalLogFilters {
    return {
      origin: this.selectedOrigin === 'ALL' ? undefined : this.selectedOrigin,
      severity: this.selectedSeverity === 'ALL' ? undefined : this.selectedSeverity,
      eventType: this.selectedEventType === 'ALL' ? undefined : this.selectedEventType,
      deviceType: this.selectedDeviceType === 'ALL' ? undefined : this.selectedDeviceType,
      deviceCode: this.selectedDeviceCode === 'ALL' ? undefined : this.selectedDeviceCode,
      stationCode: this.selectedStationCode === 'ALL' ? undefined : this.selectedStationCode,
      occurredFrom: this.occurredFrom || undefined,
      occurredTo: this.occurredTo || undefined
    };
  }

  private initializeFiltersFromUrl(queryParams: ParamMap): void {
    this.selectedSeverity = this.readEnumQueryParam(
      queryParams,
      'severity',
      this.severities
    ) ?? 'ALL';
    this.selectedOrigin = this.readEnumQueryParam(
      queryParams,
      'origin',
      this.origins
    ) ?? 'ALL';
    this.selectedEventType = this.readEnumQueryParam(
      queryParams,
      'eventType',
      this.eventTypes
    ) ?? 'ALL';
    this.selectedDeviceType = this.readEnumQueryParam(
      queryParams,
      'deviceType',
      this.deviceTypes
    ) ?? 'ALL';
    this.selectedDeviceCode = this.readTextQueryParam(queryParams, 'deviceCode') ?? 'ALL';
    this.selectedStationCode = this.readTextQueryParam(queryParams, 'stationCode') ?? 'ALL';
    this.occurredFrom = this.readDateTimeQueryParam(queryParams, 'occurredFrom') ?? '';
    this.occurredTo = this.readDateTimeQueryParam(queryParams, 'occurredTo') ?? '';
  }

  private readEnumQueryParam<T extends string>(
    queryParams: ParamMap,
    name: string,
    allowedValues: readonly T[]
  ): T | null {
    const value = queryParams.get(name);
    return value && allowedValues.includes(value as T) ? value as T : null;
  }

  private readTextQueryParam(queryParams: ParamMap, name: string): string | null {
    const value = queryParams.get(name)?.trim();
    return value ? value : null;
  }

  private readDateTimeQueryParam(queryParams: ParamMap, name: string): string | null {
    const value = queryParams.get(name);
    return value && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) ? value : null;
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
