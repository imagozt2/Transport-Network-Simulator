import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  FleetRole,
  TrainOperation,
  TrainOperationLine,
  TrainOperationsResponse,
  TrainStatus
} from '../../core/models/train-operation.model';
import { TrainOperationsService } from '../../core/services/train-operations.service';
import { depotShortCode } from '../../core/utils/depot-visuals';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';
import { fleetRoleLabel, trainStatusLabel } from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatCountdown, formatTime } from '../../core/utils/temporal-formatters';

type StatusFilter = TrainStatus | 'ALL';
type RoleFilter = FleetRole | 'ALL';

@Component({
  selector: 'app-trains',
  templateUrl: './trains.html',
  styleUrls: ['./trains.css', './trains-cards.css', './trains-realtime.css', './trains-roles.css']
})
export class Trains implements OnInit, OnDestroy {
  private readonly trainOperationsService = inject(TrainOperationsService);
  private readonly periodicRefresh = new PeriodicRefresh(15_000, () => this.loadOperations());
  private readonly expandedTrainIds = new Set<number>();
  private countdownIntervalId: number | null = null;
  private snapshotReceivedAtMs = Date.now();
  private hasInitializedExpansion = false;

  operations: TrainOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  get autoRefreshEnabled(): boolean { return this.periodicRefresh.enabled; }
  countdownNowMs = Date.now();
  searchText = '';
  selectedStatus: StatusFilter = 'ALL';
  selectedRole: RoleFilter = 'ALL';
  selectedLineCode = 'ALL';
  selectedSeries = 'ALL';

  readonly statuses: readonly TrainStatus[] = ['IN_SERVICE', 'DEPOT', 'MAINTENANCE', 'STOPPED', 'OUT_OF_SERVICE'];
  readonly roles: readonly FleetRole[] = ['REGULAR_SERVICE', 'RESERVE', 'HISTORIC'];

  ngOnInit(): void {
    this.loadOperations(true);
    this.periodicRefresh.start();
    this.startCountdown();
  }

  ngOnDestroy(): void {
    this.periodicRefresh.destroy();
    this.stopCountdown();
  }

  loadOperations(showLoading = false): void {
    const request = this.periodicRefresh.request(() => this.trainOperationsService.getOperations());
    if (!request) { return; }
    if (showLoading && !this.operations) { this.loading = true; } else { this.refreshing = true; }
    this.errorMessage = '';
    request.subscribe({
      next: (operations) => {
        this.operations = operations;
        this.snapshotReceivedAtMs = Date.now();
        this.countdownNowMs = this.snapshotReceivedAtMs;
        if (!this.hasInitializedExpansion && operations.trains.length > 0) {
          this.expandedTrainIds.add(operations.trains[0].id);
        }
        this.hasInitializedExpansion = true;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el estado operativo de la flota.';
        this.loading = false;
        this.refreshing = false;
      }
    });
  }

  toggleAutoRefresh(): void {
    this.periodicRefresh.toggle();
  }

  setSearchText(value: string): void { this.searchText = value; }
  setStatusFilter(value: string): void { this.selectedStatus = value as StatusFilter; }
  setRoleFilter(value: string): void { this.selectedRole = value as RoleFilter; }
  setLineFilter(value: string): void { this.selectedLineCode = value; }
  setSeriesFilter(value: string): void { this.selectedSeries = value; }

  clearFilters(): void {
    this.searchText = '';
    this.selectedStatus = 'ALL';
    this.selectedRole = 'ALL';
    this.selectedLineCode = 'ALL';
    this.selectedSeries = 'ALL';
  }

  hasActiveFilters(): boolean {
    return this.searchText.trim().length > 0 || this.selectedStatus !== 'ALL'
      || this.selectedRole !== 'ALL' || this.selectedLineCode !== 'ALL' || this.selectedSeries !== 'ALL';
  }

  filteredTrains(): TrainOperation[] {
    const search = this.searchText.trim().toLocaleLowerCase('es');
    return (this.operations?.trains ?? []).filter((train) => {
      const matchesSearch = !search || [train.code, train.manufacturer, train.model, train.series]
        .some((value) => value.toLocaleLowerCase('es').includes(search));
      return matchesSearch
        && (this.selectedStatus === 'ALL' || train.status === this.selectedStatus)
        && (this.selectedRole === 'ALL' || train.fleetRole === this.selectedRole)
        && (this.selectedLineCode === 'ALL' || train.assignedLine.code === this.selectedLineCode)
        && (this.selectedSeries === 'ALL' || train.series === this.selectedSeries);
    });
  }

  lineOptions(): TrainOperationLine[] {
    const options = new Map<string, TrainOperationLine>();
    this.operations?.trains.forEach((train) => options.set(train.assignedLine.code, train.assignedLine));
    return [...options.values()].sort((first, second) => first.code.localeCompare(second.code));
  }

  seriesOptions(): string[] {
    return Object.keys(this.operations?.summary.bySeries ?? {}).sort((first, second) => first.localeCompare(second));
  }

  toggleTrain(trainId: number): void {
    if (this.expandedTrainIds.has(trainId)) { this.expandedTrainIds.delete(trainId); }
    else { this.expandedTrainIds.add(trainId); }
  }

  isExpanded(trainId: number): boolean { return this.expandedTrainIds.has(trainId); }

  statusLabel(status: TrainStatus): string {
    return trainStatusLabel(status);
  }

  roleLabel(role: FleetRole): string {
    return fleetRoleLabel(role);
  }

  roleDescription(role: FleetRole): string {
    const descriptions: Record<FleetRole, string> = {
      REGULAR_SERVICE: 'Flota principal destinada al servicio diario',
      RESERVE: 'Unidades disponibles para refuerzos y sustituciones',
      HISTORIC: 'Material preservado sin asignación al servicio regular'
    };
    return descriptions[role];
  }

  situationLabel(train: TrainOperation): string {
    if (!train.serviceLocation) { return train.currentDepot?.name ?? 'Sin ubicación operativa'; }
    return train.serviceLocation.positionState === 'AT_STATION'
      ? `En ${train.serviceLocation.currentStation?.name ?? train.serviceLocation.nextStation.name}`
      : `Entre ${train.serviceLocation.previousStation.name} y ${train.serviceLocation.nextStation.name}`;
  }

  directionLabel(train: TrainOperation): string {
    return train.serviceLocation ? `Dirección ${train.serviceLocation.destination.name}` : 'Sin servicio asignado';
  }

  positionLabel(train: TrainOperation): string {
    const location = train.serviceLocation;
    if (!location) { return train.currentDepot?.name ?? 'Ubicación no disponible'; }
    return location.positionState === 'AT_STATION'
      ? `Parado en ${location.currentStation?.name ?? location.nextStation.name}`
      : `${location.previousStation.name} — ${location.nextStation.name}`;
  }

  nextArrivalLabel(train: TrainOperation): string {
    const location = train.serviceLocation;
    if (!location) { return '—'; }
    const elapsedSeconds = Math.floor((this.countdownNowMs - this.snapshotReceivedAtMs) / 1_000);
    const remainingSeconds = Math.max(0, location.secondsUntilNextStation - elapsedSeconds);
    return formatCountdown(remainingSeconds);
  }

  progressValue(train: TrainOperation): number { return train.serviceLocation?.progressPercentage ?? 0; }

  depotBadgeLabel(train: TrainOperation): string {
    const depotCode = train.currentDepot?.code;
    if (!depotCode) { return '--'; }
    return depotShortCode(depotCode);
  }

  locationBadgeTitle(train: TrainOperation): string {
    if (train.serviceLocation) { return `Circulando por ${train.serviceLocation.currentLine.name}`; }
    if (train.currentDepot) { return `En ${train.currentDepot.name}`; }
    return 'Ubicación operativa no disponible';
  }

  getLineColor(line: TrainOperationLine): string { return lineColor(line.code, line.color); }
  getLineTextColor(color: string): string { return contrastingTextColor(color); }

  formatEvaluatedAt(value: string): string {
    return formatTime(value, true);
  }

  trackTrain(_: number, train: TrainOperation): number { return train.id; }

  private startCountdown(): void {
    this.stopCountdown();
    this.countdownIntervalId = window.setInterval(() => { this.countdownNowMs = Date.now(); }, 1_000);
  }

  private stopCountdown(): void {
    if (this.countdownIntervalId !== null) {
      window.clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }
  }
}
