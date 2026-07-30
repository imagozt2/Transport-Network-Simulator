import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { OperatorAuthService } from '../services/operator-auth.service';
import { operatorSessionInterceptor } from './operator-session.interceptor';

describe('operatorSessionInterceptor', () => {
  let httpClient: HttpClient;
  let http: HttpTestingController;
  const authService = { expireSession: vi.fn() };
  const router = {
    url: '/trains',
    navigate: vi.fn().mockResolvedValue(true)
  };

  beforeEach(() => {
    authService.expireSession.mockReset();
    router.navigate.mockClear();
    router.url = '/trains';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([operatorSessionInterceptor])),
        provideHttpClientTesting(),
        { provide: OperatorAuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    });
    httpClient = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should send API requests with the operator session cookie', () => {
    httpClient.get('http://localhost:8080/api/lines').subscribe();

    const request = http.expectOne('http://localhost:8080/api/lines');
    expect(request.request.withCredentials).toBe(true);
    request.flush({});
  });

  it('should expire the local session and open login after an API 401', () => {
    httpClient.get('http://localhost:8080/api/trains').subscribe({
      error: () => undefined
    });
    http.expectOne('http://localhost:8080/api/trains')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.expireSession).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/trains' }
    });
  });
});
