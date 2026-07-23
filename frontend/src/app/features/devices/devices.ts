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
import {
  deviceStatusLabel,
  deviceTypeLabel,
  deviceTypeShortLabel
} from '../../core/utils/operation-labels';
import { formatDateTime, formatTime } from '../../core/utils/temporal-formatters';

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
    return deviceTypeLabel(type);
  }

  typeShortLabel(type: DeviceType): string {
    return deviceTypeShortLabel(type);
  }

  statusLabel(status: DeviceStatus): string {
    return deviceStatusLabel(status);
  }

  formatDateTime(value: string | null): string {
    return formatDateTime(value, 'Sin conexión registrada');
  }

  formatEvaluatedAt(value: string): string {
    return formatTime(value, true);
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
