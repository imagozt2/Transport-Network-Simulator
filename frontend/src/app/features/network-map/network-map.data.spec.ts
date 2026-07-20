import { MAP_LINES, MAP_STATIONS } from './network-map.data';

describe('network map visual data', () => {
  it('should identify every station layout by a unique station code', () => {
    const stationCodes = MAP_STATIONS.map((station) => station.stationCode);

    expect(stationCodes).toHaveLength(50);
    expect(new Set(stationCodes).size).toBe(50);
    expect(stationCodes.every((code) => /^ST\d{3}$/.test(code))).toBe(true);
  });

  it('should only use station codes declared in the visual layout', () => {
    const stationCodes = new Set(MAP_STATIONS.map((station) => station.stationCode));
    const pathCodes = MAP_LINES.flatMap((line) => line.path.map((point) => point.stationCode));

    expect(MAP_LINES.map((line) => line.code)).toEqual(['L1', 'L2', 'L3', 'L4', 'L5', 'L6']);
    expect(pathCodes.every((code) => code !== undefined && stationCodes.has(code))).toBe(true);
  });
});
