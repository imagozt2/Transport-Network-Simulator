import { inject, Injectable, signal } from '@angular/core';

import { I18nService } from '../i18n/i18n.service';

const DEFAULT_TIME_ZONE = 'Europe/Madrid';

@Injectable({ providedIn: 'root' })
export class TemporalFormatService {
  private readonly i18n = inject(I18nService);
  private readonly timeZoneState = signal(DEFAULT_TIME_ZONE);

  readonly timeZone = this.timeZoneState.asReadonly();

  setTimeZone(timeZone: string): void {
    const normalizedTimeZone = timeZone.trim();
    new Intl.DateTimeFormat('en', { timeZone: normalizedTimeZone });
    this.timeZoneState.set(normalizedTimeZone);
  }

  formatTime(value: string | null, includeSeconds = false, emptyLabel = '—'): string {
    const date = this.parse(value);
    if (!date) return emptyLabel;
    return this.dateFormatter({
      hour: '2-digit',
      minute: '2-digit',
      ...(includeSeconds ? { second: '2-digit' as const } : {}),
      hour12: false
    }).format(date);
  }

  formatDateTime(value: string | null, emptyLabel = '—'): string {
    const date = this.parse(value);
    if (!date) return emptyLabel;
    return this.dateFormatter({
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(date);
  }

  formatDuration(seconds: number | null, emptyLabel = 'No disponible'): string {
    if (seconds === null) return emptyLabel;
    const minutes = Math.floor(seconds / 60);
    const remainder = seconds % 60;
    return remainder === 0 ? `${minutes} min` : `${minutes} min ${remainder} s`;
  }

  formatCountdown(seconds: number): string {
    const safeSeconds = Math.max(0, Math.floor(seconds));
    const minutes = Math.floor(safeSeconds / 60);
    return `${minutes}:${(safeSeconds % 60).toString().padStart(2, '0')}`;
  }

  private dateFormatter(options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
    return new Intl.DateTimeFormat(this.i18n.locale(), {
      ...options,
      timeZone: this.timeZoneState()
    });
  }

  private parse(value: string | null): Date | null {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
}
