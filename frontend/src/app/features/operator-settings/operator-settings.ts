import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AppLanguage } from '../../core/i18n/i18n.model';
import { I18nService } from '../../core/i18n/i18n.service';

const REDUCE_MOTION_STORAGE_KEY = 'rmm.reduce-motion';

@Component({
  selector: 'app-operator-settings',
  imports: [RouterLink],
  templateUrl: './operator-settings.html',
  styleUrl: './operator-settings.css'
})
export class OperatorSettingsPage {
  private readonly i18n = inject(I18nService);

  readonly languageOptions = this.i18n.supportedLanguages;
  selectedLanguage: AppLanguage = this.i18n.language();
  reduceMotion = localStorage.getItem(REDUCE_MOTION_STORAGE_KEY) === 'true';
  saved = false;

  constructor() {
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
  }

  savePreferences(): void {
    this.i18n.setLanguage(this.selectedLanguage);
    localStorage.setItem(REDUCE_MOTION_STORAGE_KEY, String(this.reduceMotion));
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
    this.saved = true;
  }

  resetPreferences(): void {
    this.selectedLanguage = 'es';
    this.reduceMotion = false;
    this.i18n.setLanguage(this.selectedLanguage);
    localStorage.removeItem(REDUCE_MOTION_STORAGE_KEY);
    document.documentElement.classList.remove('reduce-motion');
    this.saved = true;
  }

  markAsPending(): void {
    this.saved = false;
  }

  selectLanguage(language: string): void {
    if (language !== 'es' && language !== 'en') return;
    this.selectedLanguage = language;
    this.markAsPending();
  }
}
