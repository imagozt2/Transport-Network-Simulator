import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AppLanguage } from '../../core/i18n/i18n.model';
import { I18nService } from '../../core/i18n/i18n.service';
import { OperatorDisplayPreferencesService } from '../../core/services/operator-display-preferences.service';

const REDUCE_MOTION_STORAGE_KEY = 'rmm.reduce-motion';

@Component({
  selector: 'app-operator-settings',
  imports: [RouterLink],
  templateUrl: './operator-settings.html',
  styleUrl: './operator-settings.css'
})
export class OperatorSettingsPage implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly displayPreferences = inject(OperatorDisplayPreferencesService);

  readonly languageOptions = this.i18n.supportedLanguages;
  readonly timeZoneOptions = [
    { id: 'Europe/Madrid', label: 'Madrid (Europa)' },
    { id: 'Europe/London', label: 'Londres (Europa)' },
    { id: 'UTC', label: 'UTC' },
    { id: 'America/New_York', label: 'Nueva York (América)' },
    { id: 'America/Mexico_City', label: 'Ciudad de México (América)' },
    { id: 'America/Argentina/Buenos_Aires', label: 'Buenos Aires (América)' },
    { id: 'Asia/Tokyo', label: 'Tokio (Asia)' }
  ] as const;
  selectedLanguage: AppLanguage = this.i18n.language();
  selectedTimeZone = this.displayPreferences.preferences().timeZone;
  reduceMotion = localStorage.getItem(REDUCE_MOTION_STORAGE_KEY) === 'true';
  loadingPreferences = true;
  savingPreferences = false;
  errorMessage = '';
  saved = false;

  constructor() {
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
  }

  ngOnInit(): void {
    this.displayPreferences.load().subscribe({
      next: (preferences) => {
        this.selectedTimeZone = preferences.timeZone;
        this.loadingPreferences = false;
      },
      error: () => {
        this.errorMessage = 'No se han podido cargar las preferencias del operador.';
        this.loadingPreferences = false;
      }
    });
  }

  savePreferences(): void {
    this.i18n.setLanguage(this.selectedLanguage);
    localStorage.setItem(REDUCE_MOTION_STORAGE_KEY, String(this.reduceMotion));
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
    this.persistDisplayPreferences();
  }

  resetPreferences(): void {
    this.selectedLanguage = 'es';
    this.reduceMotion = false;
    this.selectedTimeZone = 'Europe/Madrid';
    this.i18n.setLanguage(this.selectedLanguage);
    localStorage.removeItem(REDUCE_MOTION_STORAGE_KEY);
    document.documentElement.classList.remove('reduce-motion');
    this.persistDisplayPreferences();
  }

  markAsPending(): void {
    this.saved = false;
  }

  selectLanguage(language: string): void {
    if (language !== 'es' && language !== 'en') return;
    this.selectedLanguage = language;
    this.markAsPending();
  }

  selectTimeZone(timeZone: string): void {
    if (!this.timeZoneOptions.some((option) => option.id === timeZone)) return;
    this.selectedTimeZone = timeZone;
    this.markAsPending();
  }

  private persistDisplayPreferences(): void {
    this.savingPreferences = true;
    this.saved = false;
    this.errorMessage = '';
    this.displayPreferences.update({
      timeZone: this.selectedTimeZone,
      theme: this.displayPreferences.preferences().theme
    }).subscribe({
      next: () => {
        this.savingPreferences = false;
        this.saved = true;
      },
      error: () => {
        this.savingPreferences = false;
        this.errorMessage = 'No se han podido guardar las preferencias del operador.';
      }
    });
  }
}
