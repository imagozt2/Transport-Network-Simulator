import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { HealthResponse } from '../models/health-response.model';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly healthUrl = 'http://localhost:8080/api/health';

  check(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>(this.healthUrl);
  }
}
