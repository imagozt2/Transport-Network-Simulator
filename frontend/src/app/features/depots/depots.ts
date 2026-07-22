import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  DepotOperation,
  DepotOperationStatus,
  DepotOperationsResponse
} from '../../core/models/depot-operation.model';
import { DepotOperationsService } from '../../core/services/depot-operations.service';
import { depotShortCode } from '../../core/utils/depot-visuals';

type StatusFilter = DepotOperationStatus | 'ALL';

@Component({
  selector: 'app-depots',
  templateUrl: './depots.html',
  styleUrls: ['./depots.css', './depots-cards.css']
})
export class Depots implements OnInit, OnDestroy {
  private readonly depotOperationsService = inject(DepotOperationsService);
  private readonly refreshIntervalMs = 15_000;
  private readonly expandedDepotIds = new Set<number>();
  private refreshIntervalId: number | null = null;
  private requestInFlight = false;
  private hasInitializedExpansion = false;

  operations: DepotOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  autoRefreshEnabled = true;
  searchText = '';
  selectedStatus: StatusFilter = 'ALL';

  readonly statuses: readonly DepotOperationStatus[] = [
    'EMPTY', 'AVAILABLE', 'HIGH_OCCUPANCY', 'FULL', 'OVER_CAPACITY'
  ];

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
    this.depotOperationsService.getOperations().subscribe({
      next: (operations) => {
        this.operations = operations;
        if (!this.hasInitializedExpansion && operations.depots.length > 0) {
          this.expandedDepotIds.add(operations.depots[0].id);
        }
        this.hasInitializedExpansion = true;
        this.requestInFlight = false;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.requestInFlight = false;
        this.errorMessage = 'No se ha podido cargar el estado operativo de las cocheras.';
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
    const labels: Record<DepotOperationStatus, string> = {
      EMPTY: 'Vacía', AVAILABLE: 'Disponible', HIGH_OCCUPANCY: 'Ocupación alta',
      FULL: 'Completa', OVER_CAPACITY: 'Sobreocupada'
    };
    return labels[status];
  }

  nextMovementLabel(depot: DepotOperation): string {
    const value = depot.movementsSummary.nextMovementAt;
    return value ? this.formatTime(value) : 'Sin movimientos pendientes';
  }

  formatEvaluatedAt(value: string): string { return this.formatTime(value, true); }

  trackDepot(_: number, depot: DepotOperation): number { return depot.id; }

  private formatTime(value: string, includeSeconds = false): string {
    return new Intl.DateTimeFormat('es-ES', {
      hour: '2-digit', minute: '2-digit', second: includeSeconds ? '2-digit' : undefined, hour12: false
    }).format(new Date(value));
  }

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
