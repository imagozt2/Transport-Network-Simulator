import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

const REDUCE_MOTION_STORAGE_KEY = 'rmm.reduce-motion';

@Component({
  selector: 'app-operator-settings',
  imports: [RouterLink],
  templateUrl: './operator-settings.html',
  styleUrl: './operator-settings.css'
})
export class OperatorSettingsPage {
  reduceMotion = localStorage.getItem(REDUCE_MOTION_STORAGE_KEY) === 'true';
  saved = false;

  constructor() {
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
  }

  savePreferences(): void {
    localStorage.setItem(REDUCE_MOTION_STORAGE_KEY, String(this.reduceMotion));
    document.documentElement.classList.toggle('reduce-motion', this.reduceMotion);
    this.saved = true;
  }

  resetPreferences(): void {
    this.reduceMotion = false;
    localStorage.removeItem(REDUCE_MOTION_STORAGE_KEY);
    document.documentElement.classList.remove('reduce-motion');
    this.saved = true;
  }

  markAsPending(): void {
    this.saved = false;
  }
}
