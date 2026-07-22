import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { TrainOperationsResponse } from '../models/train-operation.model';

@Injectable({ providedIn: 'root' })
export class TrainOperationsService {
  private readonly http = inject(HttpClient);
  private readonly operationsUrl = 'http://localhost:8080/api/trains/operations';

  getOperations(): Observable<TrainOperationsResponse> {
    return this.http.get<TrainOperationsResponse>(this.operationsUrl);
  }
}
