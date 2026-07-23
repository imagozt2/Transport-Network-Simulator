import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  DepotMovement,
  DepotOperation,
  DepotOperationStatus,
  DepotOperationsResponse
} from '../../core/models/depot-operation.model';
import { FleetRole, TrainStatus } from '../../core/models/train-operation.model';
import { DepotOperationsService } from '../../core/services/depot-operations.service';
import { depotShortCode } from '../../core/utils/depot-visuals';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';
import { depotStatusLabel, fleetRoleLabel, trainStatusLabel } from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatTime } from '../../core/utils/temporal-formatters';

type StatusFilter = DepotOperationStatus | 'ALL';

@Component({
  selector: 'app-depots',
  templateUrl: './depots.html',
  styleUrls: ['./depots.css', './depots-cards.css', './depots-fleet.css']
})
export class Depots implements OnInit, OnDestroy {
  private readonly depotOperationsService = inject(DepotOperationsService);
  private readonly periodicRefresh = new PeriodicRefresh(15_000, () => this.loadOperations());
  private readonly expandedDepotIds = new Set<number>();
  private hasInitializedExpansion = false;

  operations: DepotOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  get autoRefreshEnabled(): boolean { return this.periodicRefresh.enabled; }
  searchText = '';
  selectedStatus: StatusFilter = 'ALL';

  readonly statuses: readonly DepotOperationStatus[] = [
    'EMPTY', 'AVAILABLE', 'HIGH_OCCUPANCY', 'FULL', 'OVER_CAPACITY'
  ];
  readonly fleetRoles: readonly FleetRole[] = ['REGULAR_SERVICE', 'RESERVE', 'HISTORIC'];

  ngOnInit(): void {
    this.loadOperations(true);
    this.periodicRefresh.start();
  }

  ngOnDestroy(): void { this.periodicRefresh.destroy(); }

  loadOperations(showLoading = false): void {
    const request = this.periodicRefresh.request(() => this.depotOperationsService.getOperations());
    if (!request) { return; }
    if (showLoading && !this.operations) { this.loading = true; } else { this.refreshing = true; }
    this.errorMessage = '';
    request.subscribe({
      next: (operations) => {
        this.operations = operations;
        if (!this.hasInitializedExpansion && operations.depots.length > 0) {
          this.expandedDepotIds.add(operations.depots[0].id);
        }
        this.hasInitializedExpansion = true;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el estado operativo de las cocheras.';
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

  clearFilters(): void {
    this.searchText = '';
    this.selectedStatus = 'ALL';
  }

  hasActiveFilters(): boolean { return this.searchText.trim().length > 0 || this.selectedStatus !== 'ALL'; }

  filteredDepots(): DepotOperation[] {
    const search = this.searchText.trim().toLocaleLowerCase('es');
    return (this.operations?.depots ?? []).filter((depot) => {
      const matchesSearch = !search || [depot.code, depot.name, depot.station.code, depot.station.name]
        .some((value) => value.toLocaleLowerCase('es').includes(search));
      return matchesSearch && (this.selectedStatus === 'ALL' || depot.status === this.selectedStatus);
    });
  }

  toggleDepot(depotId: number): void {
    if (this.expandedDepotIds.has(depotId)) { this.expandedDepotIds.delete(depotId); }
    else { this.expandedDepotIds.add(depotId); }
  }

  isExpanded(depotId: number): boolean { return this.expandedDepotIds.has(depotId); }
  shortCode(depot: DepotOperation): string { return depotShortCode(depot.code); }

  statusLabel(status: DepotOperationStatus): string {
    return depotStatusLabel(status);
  }

  nextMovementLabel(depot: DepotOperation): string {
    const value = depot.movementsSummary.nextMovementAt;
    return formatTime(value, false, 'Sin movimientos pendientes');
  }

  roleLabel(role: FleetRole): string {
    return fleetRoleLabel(role);
  }

  trainStatusLabel(status: TrainStatus): string {
    return trainStatusLabel(status);
  }

  fleetStatusEntries(depot: DepotOperation): { status: TrainStatus; count: number }[] {
    return (Object.entries(depot.fleet.byStatus) as [TrainStatus, number][])
      .filter(([, count]) => count > 0)
      .map(([status, count]) => ({ status, count }));
  }

  seriesEntries(depot: DepotOperation): { series: string; count: number; percentage: number }[] {
    const total = Math.max(1, depot.fleet.assignedTrainCount);
    return Object.entries(depot.fleet.bySeries)
      .map(([series, count]) => ({ series, count, percentage: Math.round(count * 100 / total) }))
      .sort((first, second) => second.count - first.count || first.series.localeCompare(second.series));
  }

  upcomingMovements(depot: DepotOperation): DepotMovement[] {
    return depot.movements.filter((movement) => movement.status === 'SCHEDULED').slice(0, 5);
  }

  recentMovements(depot: DepotOperation): DepotMovement[] {
    return depot.movements.filter((movement) => movement.status === 'COMPLETED').slice(-5).reverse();
  }

  movementTypeLabel(movement: DepotMovement): string {
    return movement.type === 'EXIT' ? 'Salida' : 'Entrada';
  }

  movementTimeLabel(movement: DepotMovement): string { return formatTime(movement.scheduledAt); }
  getLineColor(movement: DepotMovement): string { return lineColor(movement.line.code, movement.line.color); }
  getLineTextColor(color: string): string { return contrastingTextColor(color); }

  formatEvaluatedAt(value: string): string { return formatTime(value, true); }

  trackDepot(_: number, depot: DepotOperation): number { return depot.id; }

}
