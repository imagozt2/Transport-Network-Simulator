import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { OperatorAuthService } from '../services/operator-auth.service';

const API_URL = 'http://localhost:8080/api/';
const SESSION_QUERY_URL = `${API_URL}auth/me`;
const LOGIN_URL = `${API_URL}auth/login`;

export const operatorSessionInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_URL)) {
    return next(request);
  }

  const authService = inject(OperatorAuthService);
  const router = inject(Router);
  const authenticatedRequest = request.clone({ withCredentials: true });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse
          && error.status === 401
          && request.url !== LOGIN_URL
          && request.url !== SESSION_QUERY_URL) {
        authService.expireSession();
        const returnUrl = router.url.startsWith('/login') ? '/dashboard' : router.url;
        void router.navigate(['/login'], { queryParams: { returnUrl } });
      }
      return throwError(() => error);
    })
  );
};
