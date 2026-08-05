export const esTranslations = {
  'app.name': 'Centro de control',
  'app.networkName': 'Red de Metro de Macegocia',
  'language.spanish': 'Español',
  'language.english': 'Inglés',
  'common.applyFilters': 'Aplicar filtros',
  'common.clear': 'Limpiar',
  'common.cancel': 'Cancelar',
  'common.save': 'Guardar',
  'common.close': 'Cerrar',
  'common.retry': 'Reintentar',
  'common.loading': 'Cargando…',
  'common.noResults': 'Sin resultados',
  'common.page': 'Página {{current}} de {{total}}',
  'common.firstPage': 'Primera',
  'common.previousPage': 'Anterior',
  'common.nextPage': 'Siguiente',
  'common.lastPage': 'Última',
  'common.yes': 'Sí',
  'common.no': 'No'
} as const;

export type TranslationKey = keyof typeof esTranslations;
export type TranslationCatalog = Readonly<Record<TranslationKey, string>>;
