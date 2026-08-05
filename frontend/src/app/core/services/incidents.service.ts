import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Incident,
  IncidentCategory,
  IncidentPriority,
  IncidentSort,
  IncidentSortDirection,
  IncidentStatus,
  IncidentStatusUpdateRequest,
  IncidentWriteRequest,
  IncidentsPage
} from '../models/incident.model';
import { CsrfTokenResponse } from '../models/operator-auth.model';
import { switchMap } from 'rxjs';

export interface IncidentFilters {
  search?: string;
  status?: IncidentStatus;
  priority?: IncidentPriority;
  category?: IncidentCategory;
  assignedOperatorId?: number;
  sortBy: IncidentSort;
  direction: IncidentSortDirection;
}

@Injectable({ providedIn: 'root' })
export class IncidentsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/incidents';
  private readonly csrfUrl = 'http://localhost:8080/api/auth/csrf';

  getIncidents(page: number, size: number, filters: IncidentFilters): Observable<IncidentsPage> {
    let params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', size)
      .set('sortBy', filters.sortBy)
      .set('direction', filters.direction);

    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.priority) params = params.set('priority', filters.priority);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.assignedOperatorId !== undefined) {
      params = params.set('assignedOperatorId', filters.assignedOperatorId);
    }

    return this.http.get<IncidentsPage>(this.apiUrl, { params });
  }

  getIncident(code: string): Observable<Incident> {
    return this.http.get<Incident>(`${this.apiUrl}/${encodeURIComponent(code)}`);
  }

  createIncident(request: IncidentWriteRequest): Observable<Incident> {
    return this.withCsrf((headers) => this.http.post<Incident>(this.apiUrl, request, {
      headers,
      withCredentials: true
    }));
  }

  updateIncident(code: string, request: IncidentWriteRequest): Observable<Incident> {
    return this.withCsrf((headers) => this.http.put<Incident>(
      `${this.apiUrl}/${encodeURIComponent(code)}`,
      request,
      { headers, withCredentials: true }
    ));
  }

  changeStatus(code: string, request: IncidentStatusUpdateRequest): Observable<Incident> {
    return this.withCsrf((headers) => this.http.patch<Incident>(
      `${this.apiUrl}/${encodeURIComponent(code)}/status`,
      request,
      { headers, withCredentials: true }
    ));
  }

  addComment(code: string, text: string): Observable<Incident['comments'][number]> {
    return this.withCsrf((headers) => this.http.post<Incident['comments'][number]>(
      `${this.apiUrl}/${encodeURIComponent(code)}/comments`,
      { text: text.trim() },
      { headers, withCredentials: true }
    ));
  }

  private withCsrf<T>(request: (headers: HttpHeaders) => Observable<T>): Observable<T> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap((csrf) => request(new HttpHeaders().set(csrf.headerName, csrf.token)))
    );
  }
}
