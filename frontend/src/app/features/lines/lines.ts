import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  LineOperation,
  LineOperationTrain,
  LineOperationsResponse,
  ServiceDirection,
  ServiceOperationPhase,
  ServicePeriodType
} from '../../core/models/line-operation.model';
import { LineOperationsService } from '../../core/services/line-operations.service';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';

@Component({ selector: 'app-lines', templateUrl: './lines.html', styleUrls: ['./lines.css', './lines-circulation.css'] })
export class Lines implements OnInit, OnDestroy {
  private readonly lineOperationsService = inject(LineOperationsService);
  private readonly refreshIntervalMs = 5_000;
  private refreshIntervalId: number | null = null;
  private readonly expandedLineIds = new Set<number>();
  private requestInFlight = false;
  private hasInitializedExpansion = false;

  operations: LineOperationsResponse | null = null;
  loading = true;
  refreshing = false;
  errorMessage = '';
  autoRefreshEnabled = true;
  readonly serviceDirections: readonly ServiceDirection[] = ['OUTBOUND', 'INBOUND'];

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
    this.lineOperationsService.getOperations().subscribe({
      next: (operations) => {
        this.operations = operations;
        if (!this.hasInitializedExpansion && operations.lines.length > 0) {
          this.expandedLineIds.add(operations.lines[0].id);
        }
        this.hasInitializedExpansion = true;
        this.requestInFlight = false;
        this.loading = false;
        this.refreshing = false;
      },
      error: () => {
        this.requestInFlight = false;
        this.errorMessage = 'No se ha podido cargar el estado operativo de las líneas.';
        this.loading = false;
        this.refreshing = false;
      }
    });
  }

  toggleAutoRefresh(): void {
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
    if (this.autoRefreshEnabled) { this.startAutoRefresh(); } else { this.stopAutoRefresh(); }
  }

  toggleLine(lineId: number): void {
    if (this.expandedLineIds.has(lineId)) { this.expandedLineIds.delete(lineId); }
    else { this.expandedLineIds.add(lineId); }
  }

  isExpanded(lineId: number): boolean { return this.expandedLineIds.has(lineId); }

  totalTrains(): number {
    return this.operations?.lines.reduce((total, line) => total + line.activeTrainCount, 0) ?? 0;
  }

  trainsInDirection(direction: ServiceDirection): number {
    return this.operations?.lines.reduce(
      (total, line) => total + line.trains.filter((train) => train.direction === direction).length,
      0
    ) ?? 0;
  }

  lineTrainsInDirection(line: LineOperation, direction: ServiceDirection): number {
    return line.trains.filter((train) => train.direction === direction).length;
  }

  totalStations(): number {
    const ids = new Set(this.operations?.lines.flatMap((line) => line.stations.map((station) => station.id)) ?? []);
    return ids.size;
  }

  transferLineCodes(stationId: number, currentLineCode: string): string[] {
    return this.operations?.lines
      .filter((line) => line.code !== currentLineCode)
      .filter((line) => line.stations.some((station) => station.id === stationId))
      .map((line) => line.code) ?? [];
  }

  getLineColor(line: Pick<LineOperation, 'code' | 'color'>): string {
    return lineColor(line.code, line.color);
  }

  getLineColorByCode(code: string): string {
    const configuredColor = this.operations?.lines.find((line) => line.code === code)?.color;
    return lineColor(code, configuredColor);
  }

  getLineTextColor(color: string): string {
    return contrastingTextColor(color);
  }

  trainsForDirection(line: LineOperation, direction: ServiceDirection): LineOperationTrain[] {
    return line.trains.filter((train) => train.direction === direction);
  }

  directionDestination(line: LineOperation, direction: ServiceDirection): string {
    return direction === 'OUTBOUND' ? line.lastTerminal.name : line.firstTerminal.name;
  }

  getTrainPositionPercentage(line: LineOperation, train: LineOperationTrain): number {
    const currentStationIndex = train.currentStationId === null
      ? -1
      : line.stations.findIndex((station) => station.id === train.currentStationId);

    if (train.positionState === 'AT_STATION' && currentStationIndex >= 0) {
      return this.stationPositionPercentage(currentStationIndex, line.stations.length);
    }

    const previousStationIndex = line.stations.findIndex((station) => station.id === train.previousStationId);
    const nextStationIndex = line.stations.findIndex((station) => station.id === train.nextStationId);
    if (previousStationIndex < 0 || nextStationIndex < 0) {
      return 0;
    }

    const progress = Math.max(0, Math.min(train.progressPercentage, 100)) / 100;
    const routePosition = previousStationIndex + (nextStationIndex - previousStationIndex) * progress;
    return line.stations.length <= 1 ? 0 : routePosition * 100 / (line.stations.length - 1);
  }

  stationPositionPercentage(index: number, stationCount: number): number {
    return stationCount <= 1 ? 0 : index * 100 / (stationCount - 1);
  }

  trackTrain(_: number, train: LineOperationTrain): number {
    return train.id;
  }

  directionLabel(direction: ServiceDirection): string {
    return direction === 'OUTBOUND' ? 'Ida' : 'Vuelta';
  }

  formatDuration(seconds: number | null): string {
    if (seconds === null) { return 'No disponible'; }
    const minutes = Math.floor(seconds / 60);
    const remainder = seconds % 60;
    return remainder === 0 ? `${minutes} min` : `${minutes} min ${remainder} s`;
  }

  formatTime(value: string | null): string {
    if (!value) { return '—'; }
    return new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit', hour12: false })
      .format(new Date(value));
  }

  formatEvaluatedAt(value: string): string {
    return new Intl.DateTimeFormat('es-ES', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(new Date(value));
  }

  phaseLabel(phase: ServiceOperationPhase): string {
    const labels: Record<ServiceOperationPhase, string> = {
      CLOSED: 'Cerrado', STARTING: 'Inicio de servicio', OPERATING: 'En operación', ENDING: 'Fin de servicio'
    };
    return labels[phase];
  }

  periodLabel(period: ServicePeriodType | null): string {
    if (!period) { return 'Fuera de servicio'; }
    const labels: Record<ServicePeriodType, string> = {
      SERVICE_START: 'Inicio de servicio', OFF_PEAK: 'Hora valle', PEAK: 'Hora punta',
      REGULAR: 'Servicio regular', SERVICE_END: 'Fin de servicio'
    };
    return labels[period];
  }

  trackLine(_: number, line: LineOperation): number { return line.id; }

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
