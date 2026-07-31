import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import {
  PassengerAccount,
  PassengerAccountCreateRequest,
  PassengerAccountSort,
  PassengerAccountStatus,
  PassengerAccountsPage,
  SortDirection
} from '../models/passenger-account.model';
import { CsrfTokenResponse } from '../models/operator-auth.model';

export interface PassengerAccountFilters {
  search?: string;
  status?: PassengerAccountStatus;
  emailVerified?: boolean;
  sortBy: PassengerAccountSort;
  direction: SortDirection;
}

@Injectable({ providedIn: 'root' })
export class PassengerAccountsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/admin/passenger-users';
  private readonly csrfUrl = 'http://localhost:8080/api/auth/csrf';

  getAccounts(
    page: number,
    size: number,
    filters: PassengerAccountFilters
  ): Observable<PassengerAccountsPage> {
    let params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', size)
      .set('sortBy', filters.sortBy)
      .set('direction', filters.direction);

    if (filters.search) {
      params = params.set('search', filters.search);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.emailVerified !== undefined) {
      params = params.set('emailVerified', filters.emailVerified);
    }

    return this.http.get<PassengerAccountsPage>(this.apiUrl, { params });
  }

  getAccount(publicId: string): Observable<PassengerAccount> {
    return this.http.get<PassengerAccount>(
      `${this.apiUrl}/${encodeURIComponent(publicId)}`
    );
  }

  createAccount(request: PassengerAccountCreateRequest): Observable<PassengerAccount> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.post<PassengerAccount>(
        this.apiUrl,
        request,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      ))
    );
  }

  deleteAccount(publicId: string): Observable<void> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.delete<void>(
        `${this.apiUrl}/${encodeURIComponent(publicId)}`,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      ))
    );
  }

  updateStatus(
    publicId: string,
    status: PassengerAccountStatus,
    reason?: string
  ): Observable<PassengerAccount> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, {
      withCredentials: true
    }).pipe(
      switchMap((csrf) => this.http.patch<PassengerAccount>(
        `${this.apiUrl}/${encodeURIComponent(publicId)}/status`,
        { status, reason: reason?.trim() || null },
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      ))
    );
  }
}
