import { contrastingTextColor, lineColor } from './line-visuals';
import {
  depotStatusLabel,
  deviceStatusLabel,
  fleetRoleLabel,
  servicePeriodLabel,
  servicePhaseLabel,
  stationStatusLabel,
  trainStatusLabel
} from './operation-labels';

describe('operational presentation utilities', () => {
  it('should resolve line colors and their contrasting text consistently', () => {
    expect(lineColor('L1', 'Roja')).toBe('#d32f2f');
    expect(lineColor('', 'Amarilla')).toBe('#fbc02d');
    expect(lineColor('LX', '#123456')).toBe('#123456');
    expect(contrastingTextColor('#fbc02d')).toBe('#111827');
    expect(contrastingTextColor('#1976d2')).toBe('#ffffff');
  });

  it('should expose the shared labels for operational states', () => {
    expect(servicePhaseLabel('OPERATING')).toBe('En operación');
    expect(servicePeriodLabel('PEAK')).toBe('Hora punta');
    expect(stationStatusLabel('DEGRADED')).toBe('Degradada');
    expect(trainStatusLabel('DEPOT')).toBe('En cochera');
    expect(fleetRoleLabel('HISTORIC')).toBe('Histórico');
    expect(depotStatusLabel('HIGH_OCCUPANCY')).toBe('Ocupación alta');
    expect(deviceStatusLabel('MAINTENANCE')).toBe('Mantenimiento');
  });
});
