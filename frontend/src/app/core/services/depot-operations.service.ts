import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DepotOperationsResponse } from '../models/depot-operation.model';

@Injectable({ providedIn: 'root' })
export class DepotOperationsService {
  private readonly http = inject(HttpClient);
  private readonly operationsUrl = 'http://localhost:8080/api/depots/operations';

  getOperations(): Observable<DepotOperationsResponse> {
    return this.http.get<DepotOperationsResponse>(this.operationsUrl);
  }
}
