import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { StationOperationsResponse } from '../models/station-operation.model';

@Injectable({ providedIn: 'root' })
export class StationOperationsService {
  private readonly http = inject(HttpClient);
  private readonly operationsUrl = 'http://localhost:8080/api/stations/operations';

  getOperations(): Observable<StationOperationsResponse> {
    return this.http.get<StationOperationsResponse>(this.operationsUrl);
  }
}
