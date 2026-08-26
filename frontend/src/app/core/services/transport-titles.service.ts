import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import {
  CompensatoryTicketIssuanceRequest,
  CompensatoryTicketIssuanceResponse,
  TransportTitlesResponse
} from '../models/transport-title.model';
import { CsrfTokenResponse } from '../models/operator-auth.model';

@Injectable({ providedIn: 'root' })
export class TransportTitlesService {
  private readonly http = inject(HttpClient);
  private readonly titlesUrl = 'http://localhost:8080/api/transport-titles';
  private readonly csrfUrl = 'http://localhost:8080/api/auth/csrf';

  getTitles(): Observable<TransportTitlesResponse> {
    return this.http.get<TransportTitlesResponse>(this.titlesUrl);
  }

  issueCompensatoryTicket(
    titleId: number,
    request: CompensatoryTicketIssuanceRequest
  ): Observable<CompensatoryTicketIssuanceResponse> {
    return this.http.get<CsrfTokenResponse>(this.csrfUrl, { withCredentials: true }).pipe(
      switchMap((csrf) => this.http.post<CompensatoryTicketIssuanceResponse>(
        `${this.titlesUrl}/${titleId}/compensatory-issuances`,
        request,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      ))
    );
  }
}
