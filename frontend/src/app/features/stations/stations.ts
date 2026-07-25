import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  StationArrival,
  StationOperation,
  StationOperationLine,
  StationOperationStatus,
  StationOperationsResponse
} from '../../core/models/station-operation.model';
import { StationOperationsService } from '../../core/services/station-operations.service';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';
import { stationStatusLabel } from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatCountdown } from '../../core/utils/temporal-formatters';

type StatusFilter = StationOperationStatus | 'ALL';
type LineCountFilter = 'ALL' | '1' | '2' | '3_PLUS';

@Component({ selector: 'app-stations', templateUrl: './stations.html', styleUrls: ['./stations.css', './stations-arrivals.css'] })
export class Stations implements OnInit, OnDestroy {
  private readonly stationOperationsService = inject(StationOperationsService);
  private readonly periodicRefresh = new PeriodicRefresh(15_000, () => this.loadOperations());
  private readonly expandedStationIds = new Set<number>();
  private countdownIntervalId: number | null = null;
  private snapshotReceivedAtMs = Date.now();
  private hasInitializedExpansion = false;

  operations: StationOperationsResponse | null = null;
  loading = true;
  errorMessage = '';
  selectedStatus: StatusFilter = 'ALL';
  selectedLineCount: LineCountFilter = 'ALL';
  searchText = '';
  countdownNowMs = Date.now();

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
    const request = this.periodicRefresh.request(() => this.stationOperationsService.getOperations());
    if (!request) { return; }
    if (showLoading && !this.operations) { this.loading = true; }
    this.errorMessage = '';

    request.subscribe({
      next: (operations) => {
        this.operations = operations;
        this.snapshotReceivedAtMs = Date.now();
        this.countdownNowMs = this.snapshotReceivedAtMs;
        if (!this.hasInitializedExpansion && operations.stations.length > 0) {
          this.expandedStationIds.add(operations.stations[0].id);
        }
        this.hasInitializedExpansion = true;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el estado operativo de las estaciones.';
        this.loading = false;
      }
    });
  }

  setSearchText(value: string): void { this.searchText = value; }
  setStatusFilter(status: StatusFilter): void { this.selectedStatus = status; }
  setLineCountFilter(lineCount: LineCountFilter): void { this.selectedLineCount = lineCount; }

  clearFilters(): void {
    this.searchText = '';
    this.selectedStatus = 'ALL';
    this.selectedLineCount = 'ALL';
  }

  hasActiveFilters(): boolean {
    return this.searchText.trim().length > 0
      || this.selectedStatus !== 'ALL'
      || this.selectedLineCount !== 'ALL';
  }

  filteredStations(): StationOperation[] {
    const search = this.searchText.trim().toLocaleLowerCase('es');
    return (this.operations?.stations ?? []).filter((station) => {
      const matchesSearch = !search
        || station.name.toLocaleLowerCase('es').includes(search)
        || station.code.toLocaleLowerCase('es').includes(search);
      const matchesStatus = this.selectedStatus === 'ALL' || station.status === this.selectedStatus;
      const matchesLineCount = this.selectedLineCount === 'ALL'
        || (this.selectedLineCount === '1' && station.lineCount === 1)
        || (this.selectedLineCount === '2' && station.lineCount === 2)
        || (this.selectedLineCount === '3_PLUS' && station.lineCount >= 3);
      return matchesSearch && matchesStatus && matchesLineCount;
    });
  }

  toggleStation(stationId: number): void {
    if (this.expandedStationIds.has(stationId)) { this.expandedStationIds.delete(stationId); }
    else { this.expandedStationIds.add(stationId); }
  }

  isExpanded(stationId: number): boolean { return this.expandedStationIds.has(stationId); }

  deviceAvailability(station: StationOperation): number {
    return station.devices.total === 0 ? 0 : Math.round(station.devices.online * 100 / station.devices.total);
  }

  statusLabel(status: StationOperationStatus): string {
    return stationStatusLabel(status);
  }

  directionLabel(arrival: StationArrival): string {
    return `Dirección ${arrival.destination.name}`;
  }

  arrivalTimeLabel(arrival: StationArrival): string {
    if (arrival.atStation) { return 'En estación'; }
    const remainingSeconds = this.remainingSeconds(arrival);
    return formatCountdown(remainingSeconds);
  }

  remainingSeconds(arrival: StationArrival): number {
    const elapsedSeconds = Math.floor((this.countdownNowMs - this.snapshotReceivedAtMs) / 1_000);
    return Math.max(0, arrival.secondsUntilArrival - elapsedSeconds);
  }

  getLineColor(line: Pick<StationOperationLine, 'code' | 'color'>): string {
    return lineColor(line.code, line.color);
  }

  getArrivalLineColor(arrival: StationArrival): string {
    return lineColor(arrival.lineCode, arrival.lineColor);
  }

  getLineTextColor(color: string): string { return contrastingTextColor(color); }

  stationPositionPercentage(line: StationOperationLine): number {
    const stationCount = this.operations?.stations.reduce((maximum, station) => {
      const membership = station.lines.find((candidate) => candidate.id === line.id);
      return membership ? Math.max(maximum, membership.stationOrder) : maximum;
    }, 0) ?? 0;
    return stationCount <= 1 ? 0 : (line.stationOrder - 1) * 100 / (stationCount - 1);
  }

  trackStation(_: number, station: StationOperation): number { return station.id; }
  trackArrival(_: number, arrival: StationArrival): number { return arrival.trainId; }

  private startCountdown(): void {
    this.stopCountdown();
    this.countdownIntervalId = window.setInterval(() => {
      this.countdownNowMs = Date.now();
    }, 1_000);
  }

  private stopCountdown(): void {
    if (this.countdownIntervalId !== null) {
      window.clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }
  }
}
