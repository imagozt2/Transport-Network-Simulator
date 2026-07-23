const TIME_FORMAT = new Intl.DateTimeFormat('es-ES', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
});

const TIME_WITH_SECONDS_FORMAT = new Intl.DateTimeFormat('es-ES', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
});

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('es-ES', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false
});

export function formatTime(value: string | null, includeSeconds = false, emptyLabel = '—'): string {
  if (!value) { return emptyLabel; }
  return (includeSeconds ? TIME_WITH_SECONDS_FORMAT : TIME_FORMAT).format(new Date(value));
}

export function formatDateTime(value: string | null, emptyLabel = '—'): string {
  return value ? DATE_TIME_FORMAT.format(new Date(value)) : emptyLabel;
}

export function formatDuration(seconds: number | null, emptyLabel = 'No disponible'): string {
  if (seconds === null) { return emptyLabel; }
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return remainder === 0 ? `${minutes} min` : `${minutes} min ${remainder} s`;
}

export function formatCountdown(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(safeSeconds / 60);
  return `${minutes}:${(safeSeconds % 60).toString().padStart(2, '0')}`;
}
