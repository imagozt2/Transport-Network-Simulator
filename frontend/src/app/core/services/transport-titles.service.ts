import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CompensatoryTicketIssuanceRequest,
  CompensatoryTicketIssuanceResponse,
  TransportTitlesResponse
} from '../models/transport-title.model';

@Injectable({ providedIn: 'root' })
export class TransportTitlesService {
  private readonly http = inject(HttpClient);
  private readonly titlesUrl = 'http://localhost:8080/api/transport-titles';

  getTitles(): Observable<TransportTitlesResponse> {
    return this.http.get<TransportTitlesResponse>(this.titlesUrl);
  }

  issueCompensatoryTicket(
    titleId: number,
    request: CompensatoryTicketIssuanceRequest
  ): Observable<CompensatoryTicketIssuanceResponse> {
    return this.http.post<CompensatoryTicketIssuanceResponse>(
      `${this.titlesUrl}/${titleId}/compensatory-issuances`,
      request
    );
  }
}
