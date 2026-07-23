import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DeviceOperationsResponse } from '../models/device-operation.model';

@Injectable({ providedIn: 'root' })
export class DeviceOperationsService {
  private readonly http = inject(HttpClient);
  private readonly operationsUrl = 'http://localhost:8080/api/devices/operations';

  getOperations(): Observable<DeviceOperationsResponse> {
    return this.http.get<DeviceOperationsResponse>(this.operationsUrl);
  }
}
