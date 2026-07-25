import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

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
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatDateTime } from '../../core/utils/temporal-formatters';

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
  private readonly periodicRefresh = new PeriodicRefresh(15_000, () => this.loadOperations());
  private readonly route = inject(ActivatedRoute);

  operations: DeviceOperationsResponse | null = null;
  loading = true;
  errorMessage = '';
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
    this.selectedStationCode = this.route.snapshot.queryParamMap.get('stationCode')?.trim() || 'ALL';
    this.loadOperations(true);
    this.periodicRefresh.start();
  }

  ngOnDestroy(): void {
    this.periodicRefresh.destroy();
  }

  loadOperations(showLoading = false): void {
    const request = this.periodicRefresh.request(() => this.deviceOperationsService.getOperations());
    if (!request) { return; }
    if (showLoading && !this.operations) {
      this.loading = true;
    }
    this.errorMessage = '';

    request.subscribe({
      next: (operations) => {
        this.operations = operations;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el estado operativo de las máquinas.';
        this.loading = false;
      }
    });
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

  trackDevice(_: number, device: DeviceOperation): number {
    return device.id;
  }

}
