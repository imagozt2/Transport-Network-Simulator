import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  DeviceOperation,
  DeviceOperationStation,
  DeviceOperationsResponse,
  DeviceStatus,
  DeviceType
} from '../../core/models/device-operation.model';
import { DeviceOperationsService } from '../../core/services/device-operations.service';

type TypeFilter = DeviceType | 'ALL';
type StatusFilter = DeviceStatus | 'ALL';

@Component({
  selector: 'app-devices',
  imports: [RouterLink],
  templateUrl: './devices.html',
  styleUrls: ['./devices.css', './devices-cards.css']
})
export class Devices implements OnInit, OnDestroy {
  private readonly deviceOperationsService = inject(DeviceOperationsService);
  private readonly refreshIntervalMs = 15_000;
  private refreshIntervalId: number | null = null;
  private requestInFlight = false;

  operations: DeviceOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  autoRefreshEnabled = true;
  searchText = '';
  selectedType: TypeFilter = 'ALL';
  selectedStatus: StatusFilter = 'ALL';
  selectedStationCode = 'ALL';

  readonly types: readonly DeviceType[] = [
    'TICKET_MACHINE',
    'ENTRY_VALIDATOR',
    'EXIT_VALIDATOR'
  ];
  readonly statuses: readonly DeviceStatus[] = [
    'ONLINE',
    'OFFLINE',
    'MAINTENANCE',
    'ERROR'
  ];

  ngOnInit(): void {
    this.loadOperations(true);
    this.startAutoRefresh();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  loadOperations(showLoading = false): void {
    if (this.requestInFlight) {
      return;
    }

    this.requestInFlight = true;
    if (showLoading && !this.operations) {
      this.loading = true;
    } else {
      this.refreshing = true;
    }
    this.errorMessage = '';

    this.deviceOperationsService.getOperations().subscribe({
      next: (operations) => {
        this.operations = operations;
        this.requestInFlight = false;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.requestInFlight = false;
        this.errorMessage = 'No se ha podido cargar el estado operativo de las máquinas.';
        this.loading = false;
        this.refreshing = false;
      }
    });
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
    if (this.autoRefreshEnabled) {
      this.startAutoRefresh();
    } else {
      this.stopAutoRefresh();
    }
  }

  setSearchText(value: string): void {
    this.searchText = value;
  }

  setTypeFilter(value: string): void {
    this.selectedType = value as TypeFilter;
  }

  setStatusFilter(value: string): void {
    this.selectedStatus = value as StatusFilter;
  }

  setStationFilter(value: string): void {
    this.selectedStationCode = value;
  }

  clearFilters(): void {
    this.searchText = '';
    this.selectedType = 'ALL';
    this.selectedStatus = 'ALL';
    this.selectedStationCode = 'ALL';
  }

  hasActiveFilters(): boolean {
    return this.searchText.trim().length > 0
      || this.selectedType !== 'ALL'
      || this.selectedStatus !== 'ALL'
      || this.selectedStationCode !== 'ALL';
  }

  filteredDevices(): DeviceOperation[] {
    const search = this.searchText.trim().toLocaleLowerCase('es');

    return (this.operations?.devices ?? []).filter((device) => {
      const matchesSearch = !search || [
        device.code,
        device.name,
        device.station.code,
        device.station.name
      ].some((value) => value.toLocaleLowerCase('es').includes(search));

      return matchesSearch
        && (this.selectedType === 'ALL' || device.type === this.selectedType)
        && (this.selectedStatus === 'ALL' || device.status === this.selectedStatus)
        && (this.selectedStationCode === 'ALL'
          || device.station.code === this.selectedStationCode);
    });
  }

  stationOptions(): DeviceOperationStation[] {
    const stations = new Map<string, DeviceOperationStation>();
    this.operations?.devices.forEach((device) => {
      stations.set(device.station.code, device.station);
    });

    return [...stations.values()]
      .sort((first, second) => first.name.localeCompare(second.name, 'es'));
  }

  availabilityPercentage(): number {
    const summary = this.operations?.summary;
    if (!summary || summary.totalDevices === 0) {
      return 0;
    }
    return Math.round((summary.byStatus.ONLINE / summary.totalDevices) * 100);
  }

  attentionCount(): number {
    const byStatus = this.operations?.summary.byStatus;
    return byStatus ? byStatus.MAINTENANCE + byStatus.ERROR : 0;
  }

  typeLabel(type: DeviceType): string {
    const labels: Record<DeviceType, string> = {
      TICKET_MACHINE: 'Máquina de billetes',
      ENTRY_VALIDATOR: 'Validador de entrada',
      EXIT_VALIDATOR: 'Validador de salida'
    };
    return labels[type];
  }

  typeShortLabel(type: DeviceType): string {
    const labels: Record<DeviceType, string> = {
      TICKET_MACHINE: 'MB',
      ENTRY_VALIDATOR: 'VE',
      EXIT_VALIDATOR: 'VS'
    };
    return labels[type];
  }

  statusLabel(status: DeviceStatus): string {
    const labels: Record<DeviceStatus, string> = {
      ONLINE: 'Online',
      OFFLINE: 'Offline',
      MAINTENANCE: 'Mantenimiento',
      ERROR: 'Error'
    };
    return labels[status];
  }

  formatDateTime(value: string | null): string {
    if (!value) {
      return 'Sin conexión registrada';
    }
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

  formatEvaluatedAt(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(new Date(value));
  }

  trackDevice(_: number, device: DeviceOperation): number {
    return device.id;
  }

  private startAutoRefresh(): void {
    this.stopAutoRefresh();
    this.refreshIntervalId = window.setInterval(
      () => this.loadOperations(),
      this.refreshIntervalMs
    );
  }

  private stopAutoRefresh(): void {
    if (this.refreshIntervalId !== null) {
      window.clearInterval(this.refreshIntervalId);
      this.refreshIntervalId = null;
    }
  }
}
