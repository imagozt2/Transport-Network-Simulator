import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { LineOperationsResponse } from '../models/line-operation.model';

@Injectable({ providedIn: 'root' })
export class LineOperationsService {
  private readonly http = inject(HttpClient);
  private readonly operationsUrl = 'http://localhost:8080/api/lines/operations';

  getOperations(): Observable<LineOperationsResponse> {
    return this.http.get<LineOperationsResponse>(this.operationsUrl);
  }
}
