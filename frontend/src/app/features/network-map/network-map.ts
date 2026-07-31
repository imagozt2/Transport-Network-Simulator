import { Component, inject, OnInit } from '@angular/core';
import {
  NetworkJourneyResponse,
  NetworkJourneySegment,
} from '../../core/models/network-journey.model';
import { NetworkMapLine, NetworkMapStation } from '../../core/models/network-map.model';
import { NetworkMapService } from '../../core/services/network-map.service';
import { contrastingTextColor } from '../../core/utils/line-visuals';
import {
  MAP_LINES,
  MAP_STATIONS,
  MAP_VIEWBOX,
  MapLineLayout,
  MapStationLayout,
} from './network-map.data';

@Component({
  selector: 'app-network-map',
  templateUrl: './network-map.html',
  styleUrls: ['./network-map.css', './network-map-journey.css'],
})
export class NetworkMap implements OnInit {
  private readonly networkMapService = inject(NetworkMapService);

  readonly mapViewBox = MAP_VIEWBOX;
  readonly mapStations = MAP_STATIONS;
  readonly mapLines = MAP_LINES;

  lines: NetworkMapLine[] = [];
  linesPanelExpanded = true;
  journeyPlannerExpanded = false;
  originStationCode = '';
  destinationStationCode = '';
  journey: NetworkJourneyResponse | null = null;
  journeyLoading = false;
  journeyErrorMessage = '';
  expandedLineCode: string | null = null;
  hoveredLineCode: string | null = null;
  private lastHoveredLineCode: string | null = null;
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.loadNetworkMap();
  }

  loadNetworkMap(): void {
    this.loading = true;
    this.errorMessage = '';
    this.networkMapService.getNetworkMap().subscribe({
      next: ({ lines }) => {
        this.lines = lines;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'No se ha podido cargar el mapa de red.';
        this.loading = false;
      },
    });
  }

  toggleExpandedLine(code: string): void {
    this.expandedLineCode = this.expandedLineCode === code ? null : code;
  }

  toggleLinesPanel(): void {
    this.linesPanelExpanded = !this.linesPanelExpanded;
  }

  toggleJourneyPlanner(): void {
    this.journeyPlannerExpanded = !this.journeyPlannerExpanded;
  }

  setOriginStation(code: string): void {
    this.originStationCode = code;
    this.calculateJourneyIfReady();
  }

  setDestinationStation(code: string): void {
    this.destinationStationCode = code;
    this.calculateJourneyIfReady();
  }

  clearJourney(): void {
    this.originStationCode = '';
    this.destinationStationCode = '';
    this.journey = null;
    this.journeyLoading = false;
    this.journeyErrorMessage = '';
  }

  get journeyStationOptions(): NetworkMapStation[] {
    const stationsByCode = new Map<string, NetworkMapStation>();
    this.lines
      .flatMap((line) => line.stations)
      .forEach((station) => stationsByCode.set(station.code, station));
    return [...stationsByCode.values()].sort((first, second) =>
      first.name.localeCompare(second.name, 'es'),
    );
  }

  get journeyDurationMinutes(): number {
    return this.journey ? Math.max(1, Math.ceil(this.journey.estimatedDurationSeconds / 60)) : 0;
  }

  getJourneySegmentDirection(segment: NetworkJourneySegment): string {
    const line = this.lines.find((candidate) => candidate.code === segment.lineCode);
    if (!line || segment.stations.length < 2) {
      return segment.destination.name;
    }

    const originIndex = line.stations.findIndex(
      (station) => station.code === segment.stations[0].code,
    );
    const nextIndex = line.stations.findIndex(
      (station) => station.code === segment.stations[1].code,
    );
    if (originIndex < 0 || nextIndex < 0 || originIndex === nextIndex) {
      return segment.destination.name;
    }

    return nextIndex > originIndex
      ? (line.stations.at(-1)?.name ?? segment.destination.name)
      : (line.stations[0]?.name ?? segment.destination.name);
  }

  private calculateJourneyIfReady(): void {
    this.journey = null;
    this.journeyErrorMessage = '';
    if (!this.originStationCode || !this.destinationStationCode) {
      return;
    }
    if (this.originStationCode === this.destinationStationCode) {
      this.journeyErrorMessage = 'El origen y el destino deben ser estaciones diferentes.';
      return;
    }

    this.journeyLoading = true;
    this.networkMapService
      .calculateJourney(this.originStationCode, this.destinationStationCode)
      .subscribe({
        next: (journey) => {
          this.journey = journey;
          this.journeyLoading = false;
        },
        error: () => {
          this.journeyErrorMessage = 'No se ha podido calcular el trayecto seleccionado.';
          this.journeyLoading = false;
        },
      });
  }

  hoverLine(code: string): void {
    this.hoveredLineCode = code;
    this.lastHoveredLineCode = code;
  }

  clearHoveredLine(): void {
    this.hoveredLineCode = null;
  }

  hoverStation(code: string): void {
    this.hoveredLineCode = this.resolveStationLine(code)?.code ?? null;
  }

  selectStationLine(code: string): void {
    const line = this.resolveStationLine(code);
    if (line) {
      this.toggleExpandedLine(line.code);
    }
  }

  clearLineSelectionFromBackground(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.expandedLineCode = null;
    }
  }

  isLineExpanded(code: string): boolean {
    return this.expandedLineCode === code;
  }
  isMapLineDimmed(code: string): boolean {
    return this.expandedLineCode !== null && this.expandedLineCode !== code;
  }
  isMapLineHighlighted(code: string): boolean {
    return this.activeLineCode === code;
  }

  getStation(code: string): NetworkMapStation | undefined {
    return this.lines.flatMap((line) => line.stations).find((station) => station.code === code);
  }

  getStationName(code: string): string {
    return this.getStation(code)?.name ?? code;
  }
  getStationLines(code: string): NetworkMapLine[] {
    return this.lines.filter((line) => line.stations.some((station) => station.code === code));
  }
  getTransferLines(code: string, currentLineCode: string): NetworkMapLine[] {
    return this.getStationLines(code).filter((line) => line.code !== currentLineCode);
  }
  isTransferStation(code: string): boolean {
    return this.getStationLines(code).length > 1;
  }
  isStationInActiveLine(code: string): boolean {
    const active = this.activeLineCode;
    return (
      active !== null &&
      this.lines
        .find((line) => line.code === active)
        ?.stations.some((station) => station.code === code) === true
    );
  }
  shouldDimStation(code: string): boolean {
    return (
      this.expandedLineCode !== null &&
      this.lines
        .find((line) => line.code === this.expandedLineCode)
        ?.stations.some((station) => station.code === code) !== true
    );
  }
  shouldHighlightStation(code: string): boolean {
    return this.activeLineCode !== null && this.isStationInActiveLine(code);
  }

  private get activeLineCode(): string | null {
    return this.expandedLineCode ?? this.hoveredLineCode;
  }

  private resolveStationLine(code: string): NetworkMapLine | undefined {
    const stationLines = this.getStationLines(code);
    return (
      stationLines.find((line) => line.code === this.expandedLineCode) ??
      stationLines.find((line) => line.code === this.hoveredLineCode) ??
      stationLines.find((line) => line.code === this.lastHoveredLineCode) ??
      stationLines[0]
    );
  }

  getLinePoints(line: MapLineLayout): string {
    return line.path
      .map((point) => {
        if (point.stationCode) {
          const station = this.mapStations.find((item) => item.stationCode === point.stationCode);
          return station ? `${station.x},${station.y}` : null;
        }
        return point.x !== undefined && point.y !== undefined ? `${point.x},${point.y}` : null;
      })
      .filter((point): point is string => point !== null)
      .join(' ');
  }

  getStationLabelX(station: MapStationLayout): number {
    return station.x + (station.labelDx ?? 12);
  }
  getStationLabelY(station: MapStationLayout): number {
    return station.y + (station.labelDy ?? -12);
  }
  getStationLabelRotation(station: MapStationLayout): number {
    return station.labelRotation ?? 0;
  }
  getStationLabelAnchor(station: MapStationLayout): string {
    return station.labelAnchor ?? 'start';
  }
  getStationLabelLines(station: MapStationLayout): string[] {
    return station.labelLines ?? [this.getStationName(station.stationCode)];
  }
  getStationLabelLineY(station: MapStationLayout, index: number): number {
    return this.getStationLabelY(station) + index * 12;
  }
  getLineLabelWidth(line: MapLineLayout, position: 'start' | 'end'): number {
    return (position === 'start' ? line.startLabel : line.endLabel).width ?? 38;
  }
  getLineLabelHeight(line: MapLineLayout, position: 'start' | 'end'): number {
    return (position === 'start' ? line.startLabel : line.endLabel).height ?? 24;
  }
  getLineLabelRadius(line: MapLineLayout, position: 'start' | 'end'): number {
    return (position === 'start' ? line.startLabel : line.endLabel).rx ?? 12;
  }
  getLineLabelTextX(line: MapLineLayout, position: 'start' | 'end'): number {
    const label = position === 'start' ? line.startLabel : line.endLabel;
    return label.x + this.getLineLabelWidth(line, position) / 2;
  }
  getLineLabelTextY(line: MapLineLayout, position: 'start' | 'end'): number {
    return (position === 'start' ? line.startLabel : line.endLabel).y + 17;
  }
  getMapLineColor(code: string): string {
    return this.mapLines.find((line) => line.code === code)?.color ?? '#111827';
  }
  getLineTextColor(color: string): string {
    return contrastingTextColor(color);
  }
}
