import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { NetworkJourneyResponse } from '../models/network-journey.model';
import { NetworkMapResponse } from '../models/network-map.model';

@Injectable({ providedIn: 'root' })
export class NetworkMapService {
  private readonly http = inject(HttpClient);
  private readonly networkMapUrl = 'http://localhost:8080/api/network-map';

  getNetworkMap(): Observable<NetworkMapResponse> {
    return this.http.get<NetworkMapResponse>(this.networkMapUrl);
  }

  calculateJourney(
    originStationCode: string,
    destinationStationCode: string,
  ): Observable<NetworkJourneyResponse> {
    return this.http.get<NetworkJourneyResponse>(`${this.networkMapUrl}/journeys`, {
      params: { originStationCode, destinationStationCode },
    });
  }
}
