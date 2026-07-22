const DEPOT_SHORT_CODES: Readonly<Record<string, string>> = {
  'DEP-LF-A': 'LF', 'DEP-LF-B': 'LF',
  'DEP-CC-A': 'CC', 'DEP-CC-B': 'CC',
  'DEP-AIR-A': 'AE', 'DEP-AIR-B': 'AE',
  'DEP-HUB-E': 'HE', 'DEP-HUB-W': 'HO',
  'DEP-PO': 'PO', 'DEP-ESP': 'ES', 'DEP-MC': 'MC', 'DEP-MI': 'MI'
};

export function depotShortCode(code: string): string {
  return DEPOT_SHORT_CODES[code] ?? code.replace(/^DEP-/, '').replaceAll('-', '').slice(0, 2).toUpperCase();
}
