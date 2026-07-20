import { Component, inject, OnInit } from '@angular/core';
import { NetworkMapLine, NetworkMapStation } from '../../core/models/network-map.model';
import { NetworkMapService } from '../../core/services/network-map.service';
import { MAP_LINES, MAP_STATIONS, MAP_VIEWBOX, MapLineLayout, MapStationLayout } from './network-map.data';

@Component({ selector: 'app-network-map', templateUrl: './network-map.html', styleUrl: './network-map.css' })
export class NetworkMap implements OnInit {
  private readonly networkMapService = inject(NetworkMapService);

  readonly mapViewBox = MAP_VIEWBOX;
  readonly mapStations = MAP_STATIONS;
  readonly mapLines = MAP_LINES;

  lines: NetworkMapLine[] = [];
  selectedStationCode: string | null = null;
  expandedLineCode: string | null = null;
  loading = true;
  errorMessage = '';

  ngOnInit(): void { this.loadNetworkMap(); }

  loadNetworkMap(): void {
    this.loading = true;
    this.errorMessage = '';
    this.networkMapService.getNetworkMap().subscribe({
      next: ({ lines }) => { this.lines = lines; this.loading = false; },
      error: () => { this.errorMessage = 'No se ha podido cargar el mapa de red.'; this.loading = false; }
    });
  }

  selectStation(station: MapStationLayout): void { this.selectedStationCode = station.stationCode; }
  toggleExpandedLine(code: string): void { this.expandedLineCode = this.expandedLineCode === code ? null : code; }
  isLineExpanded(code: string): boolean { return this.expandedLineCode === code; }
  isMapLineDimmed(code: string): boolean { return this.expandedLineCode !== null && this.expandedLineCode !== code; }
  isMapLineHighlighted(code: string): boolean { return this.expandedLineCode === code; }
  isSelectedStation(code: string): boolean { return this.selectedStationCode === code; }

  getStation(code: string): NetworkMapStation | undefined {
    return this.lines.flatMap((line) => line.stations).find((station) => station.code === code);
  }

  getStationName(code: string): string { return this.getStation(code)?.name ?? code; }
  getStationLines(code: string): NetworkMapLine[] { return this.lines.filter((line) => line.stations.some((station) => station.code === code)); }
  getTransferLines(code: string, currentLineCode: string): NetworkMapLine[] { return this.getStationLines(code).filter((line) => line.code !== currentLineCode); }
  isTransferStation(code: string): boolean { return this.getStationLines(code).length > 1; }
  isStationInExpandedLine(code: string): boolean { return this.expandedLineCode !== null && this.lines.find((line) => line.code === this.expandedLineCode)?.stations.some((station) => station.code === code) === true; }
  shouldDimStation(code: string): boolean { return this.expandedLineCode !== null && !this.isStationInExpandedLine(code); }
  shouldHighlightStation(code: string): boolean { return this.expandedLineCode !== null && this.isStationInExpandedLine(code); }

  getLinePoints(line: MapLineLayout): string {
    return line.path.map((point) => {
      if (point.stationCode) {
        const station = this.mapStations.find((item) => item.stationCode === point.stationCode);
        return station ? `${station.x},${station.y}` : null;
      }
      return point.x !== undefined && point.y !== undefined ? `${point.x},${point.y}` : null;
    }).filter((point): point is string => point !== null).join(' ');
  }

  getStationLabelX(station: MapStationLayout): number { return station.x + (station.labelDx ?? 12); }
  getStationLabelY(station: MapStationLayout): number { return station.y + (station.labelDy ?? -12); }
  getStationLabelRotation(station: MapStationLayout): number { return station.labelRotation ?? 0; }
  getStationLabelAnchor(station: MapStationLayout): string { return station.labelAnchor ?? 'start'; }
  getStationLabelLines(station: MapStationLayout): string[] { return station.labelLines ?? [this.getStationName(station.stationCode)]; }
  getStationLabelLineY(station: MapStationLayout, index: number): number { return this.getStationLabelY(station) + index * 12; }
  getLineLabelWidth(line: MapLineLayout, position: 'start' | 'end'): number { return (position === 'start' ? line.startLabel : line.endLabel).width ?? 38; }
  getLineLabelHeight(line: MapLineLayout, position: 'start' | 'end'): number { return (position === 'start' ? line.startLabel : line.endLabel).height ?? 24; }
  getLineLabelRadius(line: MapLineLayout, position: 'start' | 'end'): number { return (position === 'start' ? line.startLabel : line.endLabel).rx ?? 12; }
  getLineLabelTextX(line: MapLineLayout, position: 'start' | 'end'): number { const label = position === 'start' ? line.startLabel : line.endLabel; return label.x + this.getLineLabelWidth(line, position) / 2; }
  getLineLabelTextY(line: MapLineLayout, position: 'start' | 'end'): number { return (position === 'start' ? line.startLabel : line.endLabel).y + 17; }
  getMapLineColor(code: string): string { return this.mapLines.find((line) => line.code === code)?.color ?? '#111827'; }
  getLineTextColor(color: string): string { return color === '#fbc02d' ? '#111827' : '#ffffff'; }
}
