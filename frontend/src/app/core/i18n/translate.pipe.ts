import { Pipe, PipeTransform } from '@angular/core';

import { TranslationParameters } from './i18n.model';
import { I18nService } from './i18n.service';
import { TranslationKey } from './translations/es';

@Pipe({
  name: 'translate',
  standalone: true,
  pure: false
})
export class TranslatePipe implements PipeTransform {
  constructor(private readonly i18n: I18nService) {
  }

  transform(key: TranslationKey, parameters?: TranslationParameters): string {
    return this.i18n.translate(key, parameters);
  }
}
