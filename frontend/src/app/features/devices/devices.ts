import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
  DeviceConnectivityState,
  DeviceOperation,
  DeviceOperationStation,
  DeviceOperationsResponse,
  DeviceStatus,
  DeviceType
} from '../../core/models/device-operation.model';
import { DeviceOperationsService } from '../../core/services/device-operations.service';
import {
  deviceConnectivityLabel,
  deviceStatusLabel,
  deviceEventSourceLabel,
  deviceTypeLabel,
  deviceTypeShortLabel
} from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatDateTime } from '../../core/utils/temporal-formatters';
import { DeviceEventSource } from '../../core/models/operational-log.types';

type TypeFilter = DeviceType | 'ALL';
type StatusFilter = DeviceStatus | 'ALL';
type ConnectivityFilter = DeviceConnectivityState | 'ALL';

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
  selectedConnectivity: ConnectivityFilter = 'ALL';
  selectedStationCode = 'ALL';
  readonly expandedDeviceIds = new Set<number>();

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
  readonly connectivityStates: readonly DeviceConnectivityState[] = [
    'CONNECTED',
    'DISCONNECTED',
    'NOT_MONITORED'
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

  setConnectivityFilter(value: string): void {
    this.selectedConnectivity = value as ConnectivityFilter;
  }

  setStationFilter(value: string): void {
    this.selectedStationCode = value;
  }

  clearFilters(): void {
    this.searchText = '';
    this.selectedType = 'ALL';
    this.selectedStatus = 'ALL';
    this.selectedConnectivity = 'ALL';
    this.selectedStationCode = 'ALL';
  }

  hasActiveFilters(): boolean {
    return this.searchText.trim().length > 0
      || this.selectedType !== 'ALL'
      || this.selectedStatus !== 'ALL'
      || this.selectedConnectivity !== 'ALL'
      || this.selectedStationCode !== 'ALL';
  }

  toggleDevice(deviceId: number): void {
    if (this.expandedDeviceIds.has(deviceId)) {
      this.expandedDeviceIds.delete(deviceId);
      return;
    }
    this.expandedDeviceIds.add(deviceId);
  }

  isExpanded(deviceId: number): boolean {
    return this.expandedDeviceIds.has(deviceId);
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
        && (this.selectedConnectivity === 'ALL'
          || device.connectivity.state === this.selectedConnectivity)
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

  typeLabel(type: DeviceType): string {
    return deviceTypeLabel(type);
  }

  typeShortLabel(type: DeviceType): string {
    return deviceTypeShortLabel(type);
  }

  statusLabel(status: DeviceStatus): string {
    return deviceStatusLabel(status);
  }

  connectivityLabel(state: DeviceConnectivityState): string {
    return deviceConnectivityLabel(state);
  }

  connectedMqttDevices(): number {
    return this.operations?.devices.filter(
      (device) => device.connectivity.state === 'CONNECTED'
    ).length ?? 0;
  }

  eventSourceLabel(source: DeviceEventSource): string {
    return deviceEventSourceLabel(source);
  }

  formatDateTime(value: string | null): string {
    return formatDateTime(value, 'Sin conexión registrada');
  }

}
