import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { OperationalLogPage } from '../models/operational-log.model';
import {
  DeviceEventType,
  LogOrigin,
  LogSeverity
} from '../models/operational-log.types';

export interface OperationalLogFilters {
  origin?: LogOrigin;
  severity?: LogSeverity;
  eventType?: DeviceEventType;
  deviceCode?: string;
  stationCode?: string;
  occurredFrom?: string;
  occurredTo?: string;
}

@Injectable({ providedIn: 'root' })
export class OperationalLogsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/logs';

  getLogs(
    page: number,
    size: number,
    filters: OperationalLogFilters
  ): Observable<OperationalLogPage> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    Object.entries(filters).forEach(([name, value]) => {
      if (value) {
        params = params.set(name, value);
      }
    });

    return this.http.get<OperationalLogPage>(this.apiUrl, { params });
  }
}
