import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import {
  CsrfTokenResponse,
  OperatorAccount,
  OperatorLoginRequest
} from '../models/operator-auth.model';

@Injectable({ providedIn: 'root' })
export class OperatorAuthService {
  private readonly http = inject(HttpClient);
  private readonly authUrl = 'http://localhost:8080/api/auth';

  login(request: OperatorLoginRequest): Observable<OperatorAccount> {
    return this.getCsrfToken().pipe(
      switchMap((csrf) => this.http.post<OperatorAccount>(
        `${this.authUrl}/login`,
        request,
        {
          headers: new HttpHeaders().set(csrf.headerName, csrf.token),
          withCredentials: true
        }
      ))
    );
  }

  private getCsrfToken(): Observable<CsrfTokenResponse> {
    return this.http.get<CsrfTokenResponse>(`${this.authUrl}/csrf`, {
      withCredentials: true
    });
  }
}
