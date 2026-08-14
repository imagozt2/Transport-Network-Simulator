import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';

import { OperatorDisplayPreferences } from '../models/operator-display-preferences.model';
import { CsrfTokenResponse } from '../models/operator-auth.model';
import { TemporalFormatService } from './temporal-format.service';

const DEFAULT_PREFERENCES: OperatorDisplayPreferences = {
  timeZone: 'Europe/Madrid',
  theme: 'LIGHT'
};

@Injectable({ providedIn: 'root' })
export class OperatorDisplayPreferencesService {
  private readonly http = inject(HttpClient);
  private readonly temporalFormat = inject(TemporalFormatService);
  private readonly apiUrl = 'http://localhost:8080/api/operators/me/display-preferences';
  private readonly csrfUrl = 'http://localhost:8080/api/auth/csrf';
  private readonly preferencesState = signal<OperatorDisplayPreferences>(DEFAULT_PREFERENCES);

  readonly preferences = this.preferencesState.asReadonly();

  load(): Observable<OperatorDisplayPreferences> {
    return this.http.get<OperatorDisplayPreferences>(this.apiUrl, {
      withCredentials: true
    }).pipe(tap((preferences) => this.apply(preferences)));
  }

  update(preferences: OperatorDisplayPreferences): Observable<OperatorDisplayPreferences> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap((csrf) => this.http.put<OperatorDisplayPreferences>(
        this.apiUrl,
        preferences,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      )),
      tap((updatedPreferences) => this.apply(updatedPreferences))
    );
  }

  private apply(preferences: OperatorDisplayPreferences): void {
    this.preferencesState.set(preferences);
    this.temporalFormat.setTimeZone(preferences.timeZone);
  }
}
