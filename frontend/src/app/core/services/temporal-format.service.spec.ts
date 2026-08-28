import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { TemporalFormatService } from './temporal-format.service';

describe('TemporalFormatService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('should format every instant with the active locale and configured time zone', () => {
    const service = TestBed.inject(TemporalFormatService);

    expect(service.formatTime('2026-07-22T06:30:45Z')).toBe('08:30');
    expect(service.formatTime('2026-07-22T06:30:45Z', true)).toBe('08:30:45');
    expect(service.formatDateTime('2026-07-22T06:30:45Z'))
      .toMatch(/22\/07\/2026.*08:30:45/);

    TestBed.inject(I18nService).setLanguage('en');
    expect(service.formatDateTime('2026-07-22T06:30:45Z'))
      .toMatch(/22\/07\/2026.*08:30:45/);
  });

  it('should centralize durations, countdowns and empty values', () => {
    const service = TestBed.inject(TemporalFormatService);

    expect(service.formatDuration(1_940)).toBe('32 min 20 s');
    expect(service.formatCountdown(81)).toBe('1:21');
    expect(service.formatCountdown(-1)).toBe('0:00');
    expect(service.formatDateTime(null, 'Sin fecha')).toBe('Sin fecha');
    expect(service.formatTime('invalid', false, 'Sin hora')).toBe('Sin hora');
  });

  it('should apply one time zone consistently to all date and time formats', () => {
    const service = TestBed.inject(TemporalFormatService);

    service.setTimeZone('UTC');

    expect(service.timeZone()).toBe('UTC');
    expect(service.formatTime('2026-07-22T06:30:45Z')).toBe('06:30');
    expect(service.formatDateTime('2026-07-22T06:30:45Z'))
      .toMatch(/22\/07\/2026.*06:30:45/);
  });
});
