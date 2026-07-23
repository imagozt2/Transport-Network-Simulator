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
import { formatCountdown, formatDateTime, formatDuration, formatTime } from './temporal-formatters';

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

  it('should format operational times using the same rules', () => {
    const date = '2026-07-22T08:30:45';

    expect(formatTime(date)).toMatch(/08:30/);
    expect(formatTime(date, true)).toMatch(/08:30:45/);
    expect(formatDateTime(date)).toMatch(/22\/07\/2026.*08:30:45/);
    expect(formatDuration(1_940)).toBe('32 min 20 s');
    expect(formatCountdown(81)).toBe('1:21');
    expect(formatCountdown(-1)).toBe('0:00');
  });
});
