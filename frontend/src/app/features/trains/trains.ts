import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  FleetRole,
  TrainOperation,
  TrainOperationLine,
  TrainOperationsResponse,
  TrainStatus
} from '../../core/models/train-operation.model';
import { TrainOperationsService } from '../../core/services/train-operations.service';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';

type StatusFilter = TrainStatus | 'ALL';
type RoleFilter = FleetRole | 'ALL';

@Component({ selector: 'app-trains', templateUrl: './trains.html', styleUrls: ['./trains.css', './trains-cards.css'] })
export class Trains implements OnInit, OnDestroy {
  private readonly trainOperationsService = inject(TrainOperationsService);
  private readonly refreshIntervalMs = 15_000;
  private readonly expandedTrainIds = new Set<number>();
  private refreshIntervalId: number | null = null;
  private requestInFlight = false;
  private hasInitializedExpansion = false;

  operations: TrainOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  autoRefreshEnabled = true;
  searchText = '';
  selectedStatus: StatusFilter = 'ALL';
  selectedRole: RoleFilter = 'ALL';
  selectedLineCode = 'ALL';
  selectedSeries = 'ALL';

  readonly statuses: readonly TrainStatus[] = ['IN_SERVICE', 'DEPOT', 'MAINTENANCE', 'STOPPED', 'OUT_OF_SERVICE'];
  readonly roles: readonly FleetRole[] = ['REGULAR_SERVICE', 'RESERVE', 'HISTORIC'];

  ngOnInit(): void {
    this.loadOperations(true);
    this.startAutoRefresh();
  }

  ngOnDestroy(): void { this.stopAutoRefresh(); }

  loadOperations(showLoading = false): void {
    if (this.requestInFlight) { return; }
    this.requestInFlight = true;
    if (showLoading && !this.operations) { this.loading = true; } else { this.refreshing = true; }
    this.errorMessage = '';
    this.trainOperationsService.getOperations().subscribe({
      next: (operations) => {
        this.operations = operations;
        if (!this.hasInitializedExpansion && operations.trains.length > 0) {
          this.expandedTrainIds.add(operations.trains[0].id);
        }
        this.hasInitializedExpansion = true;
        this.requestInFlight = false;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.requestInFlight = false;
        this.errorMessage = 'No se ha podido cargar el estado operativo de la flota.';
        this.loading = false;
        this.refreshing = false;
      }
    });
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
    if (this.autoRefreshEnabled) { this.startAutoRefresh(); } else { this.stopAutoRefresh(); }
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
    const labels: Record<TrainStatus, string> = {
      IN_SERVICE: 'En servicio', DEPOT: 'En cochera', MAINTENANCE: 'Mantenimiento',
      STOPPED: 'Detenido', OUT_OF_SERVICE: 'Fuera de servicio'
    };
    return labels[status];
  }

  roleLabel(role: FleetRole): string {
    const labels: Record<FleetRole, string> = {
      REGULAR_SERVICE: 'Servicio regular', RESERVE: 'Reserva', HISTORIC: 'Histórico'
    };
    return labels[role];
  }

  situationLabel(train: TrainOperation): string {
    return train.status === 'IN_SERVICE'
      ? 'En circulación'
      : train.currentDepot?.name ?? 'Sin ubicación operativa';
  }

  getLineColor(line: TrainOperationLine): string { return lineColor(line.code, line.color); }
  getLineTextColor(color: string): string { return contrastingTextColor(color); }

  formatEvaluatedAt(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(new Date(value));
  }

  trackTrain(_: number, train: TrainOperation): number { return train.id; }

  private startAutoRefresh(): void {
    this.stopAutoRefresh();
    this.refreshIntervalId = window.setInterval(() => this.loadOperations(), this.refreshIntervalMs);
  }

  private stopAutoRefresh(): void {
    if (this.refreshIntervalId !== null) {
      window.clearInterval(this.refreshIntervalId);
      this.refreshIntervalId = null;
    }
  }
}
