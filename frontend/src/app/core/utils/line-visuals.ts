export const LINE_COLORS: Readonly<Record<string, string>> = {
  L1: '#d32f2f', L2: '#388e3c', L3: '#fbc02d',
  L4: '#7b1fa2', L5: '#1976d2', L6: '#f57c00'
};

const HEX_COLOR_PATTERN = /^#[0-9a-f]{6}$/i;

export function lineColor(code: string, configuredColor?: string | null): string {
  return LINE_COLORS[code] ?? (configuredColor && HEX_COLOR_PATTERN.test(configuredColor) ? configuredColor : '#64748b');
}

export function contrastingTextColor(color: string): string {
  const normalized = color.replace('#', '');
  const red = Number.parseInt(normalized.slice(0, 2), 16);
  const green = Number.parseInt(normalized.slice(2, 4), 16);
  const blue = Number.parseInt(normalized.slice(4, 6), 16);
  return (red * 299 + green * 587 + blue * 114) / 1000 > 160 ? '#111827' : '#ffffff';
}
