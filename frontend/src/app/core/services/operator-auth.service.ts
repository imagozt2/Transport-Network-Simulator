import { HttpClient, HttpHeaders } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import {
  catchError,
  finalize,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap
} from 'rxjs';

import {
  CsrfTokenResponse,
  OperatorAccount,
  OperatorLoginRequest
} from '../models/operator-auth.model';

@Injectable({ providedIn: 'root' })
export class OperatorAuthService {
  private readonly http = inject(HttpClient);
  private readonly authUrl = 'http://localhost:8080/api/auth';
  private readonly currentOperatorState = signal<OperatorAccount | null>(null);
  private sessionChecked = false;
  private sessionRequest: Observable<OperatorAccount | null> | null = null;

  readonly currentOperator = this.currentOperatorState.asReadonly();
  readonly authenticated = computed(() => this.currentOperatorState() !== null);

  login(request: OperatorLoginRequest): Observable<OperatorAccount> {
    return this.getCsrfToken().pipe(
      switchMap((csrf) => this.http.post<OperatorAccount>(
        `${this.authUrl}/login`,
        request,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      )),
      tap((operator) => {
        this.currentOperatorState.set(operator);
        this.sessionChecked = true;
      })
    );
  }

  ensureSession(): Observable<OperatorAccount | null> {
    const currentOperator = this.currentOperatorState();
    if (currentOperator) {
      return of(currentOperator);
    }
    if (this.sessionChecked) {
      return of(null);
    }
    if (this.sessionRequest) {
      return this.sessionRequest;
    }

    this.sessionRequest = this.http.get<OperatorAccount>(`${this.authUrl}/me`, {
      withCredentials: true
    }).pipe(
      tap((operator) => this.currentOperatorState.set(operator)),
      catchError(() => {
        this.currentOperatorState.set(null);
        return of(null);
      }),
      tap(() => { this.sessionChecked = true; }),
      finalize(() => { this.sessionRequest = null; }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.sessionRequest;
  }

  logout(): Observable<void> {
    return this.getCsrfToken().pipe(
      switchMap((csrf) => this.http.post<void>(
        `${this.authUrl}/logout`,
        null,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      )),
      finalize(() => this.expireSession())
    );
  }

  expireSession(): void {
    this.currentOperatorState.set(null);
    this.sessionChecked = true;
    this.sessionRequest = null;
  }

  private getCsrfToken(): Observable<CsrfTokenResponse> {
    return this.http.get<CsrfTokenResponse>(`${this.authUrl}/csrf`, {
      withCredentials: true
    });
  }
}
