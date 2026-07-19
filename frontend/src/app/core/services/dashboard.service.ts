import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardResponse } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly summaryUrl = 'http://localhost:8080/api/dashboard/summary';

  getSummary(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.summaryUrl);
  }
}
