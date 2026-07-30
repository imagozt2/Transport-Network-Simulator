import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperatorAccount } from '../models/operator-auth.model';
import { OperatorAuthService } from './operator-auth.service';

describe('OperatorAuthService', () => {
  let service: OperatorAuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(OperatorAuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should obtain CSRF protection before creating an authenticated session', () => {
    let result: OperatorAccount | undefined;
    service.login({ identifier: 'admin', password: 'secure-password' })
      .subscribe((operator) => { result = operator; });

    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token'
    });
    const loginRequest = http.expectOne('http://localhost:8080/api/auth/login');
    expect(loginRequest.request.withCredentials).toBe(true);
    expect(loginRequest.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    loginRequest.flush(operator);

    expect(result).toEqual(operator);
    expect(service.currentOperator()).toEqual(operator);
    expect(service.authenticated()).toBe(true);
  });

  it('should recover the session once and reuse its cached state', () => {
    service.ensureSession().subscribe();
    const sessionRequest = http.expectOne('http://localhost:8080/api/auth/me');
    expect(sessionRequest.request.withCredentials).toBe(true);
    sessionRequest.flush(operator);

    service.ensureSession().subscribe();
    http.expectNone('http://localhost:8080/api/auth/me');
    expect(service.currentOperator()).toEqual(operator);
  });

  it('should convert an unauthenticated session query into an empty session', () => {
    let result: OperatorAccount | null | undefined;
    service.ensureSession().subscribe((current) => { result = current; });
    http.expectOne('http://localhost:8080/api/auth/me')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeNull();
    expect(service.authenticated()).toBe(false);
  });

  const operator: OperatorAccount = {
    id: 1,
    username: 'admin',
    email: 'admin@macegocia.local',
    firstName: 'Ivan',
    lastName: 'Administrador',
    role: 'ADMINISTRATOR',
    status: 'ACTIVE',
    lastLoginAt: '2026-07-30T10:00:00',
    createdAt: '2026-07-30T09:00:00'
  };
});
