export interface MapViewBox { width: number; height: number; }

export interface MapStationLayout {
  stationCode: string;
  x: number;
  y: number;
  labelDx?: number;
  labelDy?: number;
  labelRotation?: number;
  labelAnchor?: 'start' | 'middle' | 'end';
  labelLines?: string[];
  showLabel?: boolean;
}

export interface MapLinePathPoint { stationCode?: string; x?: number; y?: number; }
export interface MapLineLabelLayout { x: number; y: number; width?: number; height?: number; rx?: number; }
export interface MapLineLayout {
  code: string;
  color: string;
  startLabel: MapLineLabelLayout;
  endLabel: MapLineLabelLayout;
  path: MapLinePathPoint[];
}

const stationPath = (...stationCodes: string[]): MapLinePathPoint[] =>
  stationCodes.map((stationCode) => ({ stationCode }));

export const MAP_VIEWBOX: MapViewBox = { width: 920, height: 820 };

export const MAP_STATIONS: MapStationLayout[] = [
  { stationCode: 'ST001', x: 50, y: 650, labelDx: -4, labelDy: 23 },
  { stationCode: 'ST002', x: 200, y: 650, labelDx: 50, labelDy: 16, labelAnchor: 'middle', labelLines: ['HUB Industrial', 'Norte'] },
  { stationCode: 'ST003', x: 125, y: 600, labelDx: -33, labelDy: -10, labelAnchor: 'middle', labelLines: ['Ensanche', 'Nuevo'] },
  { stationCode: 'ST004', x: 100, y: 525, labelDx: -28, labelDy: -5, labelAnchor: 'middle', labelLines: ['Ramón', 'y Cajal'] },
  { stationCode: 'ST005', x: 200, y: 550, labelDx: -62, labelDy: -3 },
  { stationCode: 'ST006', x: 150, y: 350, labelDx: -35, labelDy: 0, labelAnchor: 'middle', labelLines: ['Miguel de', 'Cervantes'] },
  { stationCode: 'ST007', x: 150, y: 450, labelDx: -70, labelDy: -12 },
  { stationCode: 'ST008', x: 250, y: 450, labelDx: -60, labelDy: -12 },
  { stationCode: 'ST009', x: 150, y: 250, labelDx: -65, labelDy: 2 },
  { stationCode: 'ST010', x: 250, y: 350, labelDx: -56, labelDy: 17 },
  { stationCode: 'ST011', x: 250, y: 150, labelDx: -64, labelDy: 2 },
  { stationCode: 'ST012', x: 250, y: 250, labelDx: 0, labelDy: -24, labelAnchor: 'middle', labelLines: ['El Muro', 'del Gueto'] },
  { stationCode: 'ST013', x: 350, y: 650, labelDx: 8, labelDy: -12 },
  { stationCode: 'ST014', x: 450, y: 650, labelDx: 0, labelDy: 18, labelAnchor: 'middle', labelLines: ['Museo', 'Marítimo'] },
  { stationCode: 'ST015', x: 650, y: 650, labelDx: 0, labelDy: 18, labelAnchor: 'middle', labelLines: ['Paseo', 'Marítimo'] },
  { stationCode: 'ST016', x: 750, y: 650, labelDx: 10, labelDy: -23, labelAnchor: 'start', labelLines: ['Teatro', 'Nacional'] },
  { stationCode: 'ST017', x: 850, y: 650, labelDx: 15, labelDy: -8, labelAnchor: 'start', labelLines: ['Estadio', 'Olímpico'] },
  { stationCode: 'ST018', x: 350, y: 550, labelDx: -25, labelDy: -12 },
  { stationCode: 'ST019', x: 450, y: 550, labelDx: 10, labelDy: -12 },
  { stationCode: 'ST020', x: 550, y: 550, labelDx: 10, labelDy: -12 },
  { stationCode: 'ST021', x: 650, y: 550, labelDx: 0, labelDy: -24, labelAnchor: 'middle', labelLines: ['Puerta', 'Medieval'] },
  { stationCode: 'ST022', x: 750, y: 550, labelDx: 10, labelDy: -12 },
  { stationCode: 'ST023', x: 850, y: 550, labelDx: 15, labelDy: 2 },
  { stationCode: 'ST024', x: 450, y: 500, labelDx: 10, labelDy: 2, labelAnchor: 'start', labelLines: ['San', 'Vicente'] },
  { stationCode: 'ST025', x: 500, y: 500, labelDx: 20, labelDy: -12, labelAnchor: 'middle', labelLines: ['Santa', 'Rita'] },
  { stationCode: 'ST026', x: 350, y: 450, labelDx: 0, labelDy: 20, labelAnchor: 'middle', labelLines: ['Ribera', 'Norte'] },
  { stationCode: 'ST027', x: 450, y: 450, labelDx: -53, labelDy: -20, labelAnchor: 'middle', labelLines: ['Plaza', 'de la Merced'] },
  { stationCode: 'ST028', x: 550, y: 450, labelDx: 10, labelDy: -12 },
  { stationCode: 'ST029', x: 650, y: 450, labelDx: 10, labelDy: -24, labelAnchor: 'start', labelLines: ['Los', 'Lavaderos'] },
  { stationCode: 'ST030', x: 750, y: 450, labelDx: 10, labelDy: -23, labelAnchor: 'start', labelLines: ['Plaza de', 'la Mina'] },
  { stationCode: 'ST031', x: 850, y: 450, labelDx: 15, labelDy: -8, labelAnchor: 'start', labelLines: ['Muralla', 'Ibérica'] },
  { stationCode: 'ST032', x: 400, y: 400, labelDx: 20, labelDy: -22, labelAnchor: 'middle', labelLines: ['San Pedro', 'Apóstol'] },
  { stationCode: 'ST033', x: 550, y: 350, labelDx: 15, labelDy: 2 },
  { stationCode: 'ST034', x: 650, y: 350, labelDx: 15, labelDy: 2 },
  { stationCode: 'ST035', x: 650, y: 250, labelDx: 15, labelDy: 2 },
  { stationCode: 'ST036', x: 750, y: 350, labelDx: 10, labelDy: 2 },
  { stationCode: 'ST037', x: 350, y: 350, labelDx: -25, labelDy: -25, labelAnchor: 'middle', labelLines: ['Puerto', 'Fluvial'] },
  { stationCode: 'ST038', x: 450, y: 350, labelDx: -50, labelDy: -14 },
  { stationCode: 'ST039', x: 550, y: 250, labelDx: 10, labelDy: -22, labelAnchor: 'start', labelLines: ['Puerta de', 'Santiago'] },
  { stationCode: 'ST040', x: 350, y: 250, labelDx: 10, labelDy: -22, labelAnchor: 'start', labelLines: ['Parque de', 'la Cultura'] },
  { stationCode: 'ST041', x: 450, y: 250, labelDx: 10, labelDy: 16 },
  { stationCode: 'ST042', x: 550, y: 150, labelDx: -20, labelDy: -16 },
  { stationCode: 'ST043', x: 350, y: 150, labelDx: 10, labelDy: -23, labelAnchor: 'start', labelLines: ['Cuatro', 'Caminos'] },
  { stationCode: 'ST044', x: 450, y: 150, labelDx: -30, labelDy: -12 },
  { stationCode: 'ST045', x: 350, y: 50, labelDx: 10, labelDy: 4 },
  { stationCode: 'ST046', x: 550, y: 650, labelDx: 10, labelDy: 2 },
  { stationCode: 'ST047', x: 750, y: 750, labelDx: 10, labelDy: -8, labelAnchor: 'start', labelLines: ['Zona', 'Universitaria'] },
  { stationCode: 'ST048', x: 850, y: 750, labelDx: 10, labelDy: -8, labelAnchor: 'start', labelLines: ['Puerto', 'Olímpico'] },
  { stationCode: 'ST049', x: 200, y: 750, labelDx: 48, labelDy: 0, labelAnchor: 'middle', labelLines: ['HUB Industrial', 'Este'] },
  { stationCode: 'ST050', x: 100, y: 750, labelDx: -48, labelDy: 0, labelAnchor: 'middle', labelLines: ['HUB Industrial', 'Oeste'] }
];

export const MAP_LINES: MapLineLayout[] = [
  { code: 'L1', color: '#d32f2f', startLabel: { x: 331, y: 15 }, endLabel: { x: 705, y: 457 }, path: stationPath('ST045', 'ST043', 'ST011', 'ST009', 'ST010', 'ST037', 'ST038', 'ST033', 'ST028', 'ST020', 'ST015', 'ST016', 'ST017', 'ST023', 'ST030') },
  { code: 'L2', color: '#388e3c', startLabel: { x: 2, y: 623 }, endLabel: { x: 405, y: 457 }, path: stationPath('ST001', 'ST003', 'ST005', 'ST018', 'ST019', 'ST020', 'ST021', 'ST022', 'ST023', 'ST031', 'ST036', 'ST035', 'ST039', 'ST038', 'ST027') },
  { code: 'L3', color: '#fbc02d', startLabel: { x: 180, y: 762 }, endLabel: { x: 830, y: 762 }, path: stationPath('ST049', 'ST002', 'ST003', 'ST004', 'ST007', 'ST008', 'ST026', 'ST027', 'ST028', 'ST029', 'ST022', 'ST017', 'ST048') },
  { code: 'L4', color: '#7b1fa2', startLabel: { x: 2, y: 653 }, endLabel: { x: 805, y: 457 }, path: stationPath('ST001', 'ST002', 'ST013', 'ST014', 'ST019', 'ST024', 'ST027', 'ST032', 'ST037', 'ST041', 'ST042', 'ST035', 'ST034', 'ST029', 'ST030', 'ST031') },
  { code: 'L5', color: '#1976d2', startLabel: { x: 80, y: 762 }, endLabel: { x: 732, y: 762 }, path: stationPath('ST050', 'ST002', 'ST005', 'ST008', 'ST037', 'ST040', 'ST043', 'ST044', 'ST042', 'ST039', 'ST034', 'ST030', 'ST022', 'ST016', 'ST047') },
  { code: 'L6', color: '#f57c00', startLabel: { x: 330, y: 665 }, endLabel: { x: 530, y: 662 }, path: stationPath('ST013', 'ST005', 'ST007', 'ST006', 'ST009', 'ST012', 'ST040', 'ST041', 'ST039', 'ST033', 'ST027', 'ST025', 'ST020', 'ST046') }
];
