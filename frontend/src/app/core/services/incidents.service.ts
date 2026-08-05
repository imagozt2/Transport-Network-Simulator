import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Incident,
  IncidentCategory,
  IncidentPriority,
  IncidentSort,
  IncidentSortDirection,
  IncidentStatus,
  IncidentsPage
} from '../models/incident.model';

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
}
