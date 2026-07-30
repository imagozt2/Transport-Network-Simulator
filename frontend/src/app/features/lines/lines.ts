import { Component, inject, OnDestroy, OnInit } from '@angular/core';

import {
  LineOperation,
  LineOperationArrival,
  LineOperationTrain,
  LineOperationsResponse,
  ServiceDirection,
  ServiceOperationPhase,
  ServicePeriodType
} from '../../core/models/line-operation.model';
import { LineOperationsService } from '../../core/services/line-operations.service';
import { contrastingTextColor, lineColor } from '../../core/utils/line-visuals';
import { servicePeriodLabel, servicePhaseLabel } from '../../core/utils/operation-labels';
import { PeriodicRefresh } from '../../core/utils/periodic-refresh';
import { formatCountdown, formatDuration, formatTime } from '../../core/utils/temporal-formatters';
import { SummaryCard } from '../../shared/summary-card/summary-card';

@Component({
  selector: 'app-lines',
  imports: [SummaryCard],
  templateUrl: './lines.html',
  styleUrls: ['./lines.css', './lines-circulation.css', './lines-depots.css']
})
export class Lines implements OnInit, OnDestroy {
  private readonly lineOperationsService = inject(LineOperationsService);
  private readonly periodicRefresh = new PeriodicRefresh(5_000, () => this.loadOperations());
  private readonly expandedLineIds = new Set<number>();
  private hasInitializedExpansion = false;

  operations: LineOperationsResponse | null = null;
  loading = true;
  errorMessage = '';
  readonly serviceDirections: readonly ServiceDirection[] = ['OUTBOUND', 'INBOUND'];

  ngOnInit(): void {
    this.loadOperations(true);
    this.periodicRefresh.start();
  }

  ngOnDestroy(): void { this.periodicRefresh.destroy(); }

  loadOperations(showLoading = false): void {
    const request = this.periodicRefresh.request(() => this.lineOperationsService.getOperations());
    if (!request) { return; }
    if (showLoading && !this.operations) { this.loading = true; }
    this.errorMessage = '';
    request.subscribe({
      next: (operations) => {
        this.operations = operations;
        if (!this.hasInitializedExpansion && operations.lines.length > 0) {
          this.expandedLineIds.add(operations.lines[0].id);
        }
        this.hasInitializedExpansion = true;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el estado operativo de las líneas.';
        this.loading = false;
      }
    });
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

  nextArrival(
    line: LineOperation,
    stationId: number,
    direction: ServiceDirection
  ): LineOperationArrival | null {
    return line.nextArrivals
      .filter((arrival) => arrival.stationId === stationId && arrival.direction === direction)
      .reduce<LineOperationArrival | null>(
        (next, arrival) => !next || arrival.secondsUntilArrival < next.secondsUntilArrival ? arrival : next,
        null
      );
  }

  formatCountdown(seconds: number): string {
    return formatCountdown(seconds);
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
    return formatDuration(seconds);
  }

  formatTime(value: string | null): string {
    return formatTime(value);
  }

  phaseLabel(phase: ServiceOperationPhase): string {
    return servicePhaseLabel(phase);
  }

  periodLabel(period: ServicePeriodType | null): string {
    if (!period) { return 'Fuera de servicio'; }
    return servicePeriodLabel(period);
  }

  trackLine(_: number, line: LineOperation): number { return line.id; }

}
