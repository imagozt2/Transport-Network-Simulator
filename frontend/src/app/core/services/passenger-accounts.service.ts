import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  PassengerAccountSort,
  PassengerAccountStatus,
  PassengerAccountsPage,
  SortDirection
} from '../models/passenger-account.model';

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
}
