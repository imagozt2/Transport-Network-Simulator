import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PassengerAccountsService } from './passenger-accounts.service';

describe('PassengerAccountsService', () => {
  let service: PassengerAccountsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PassengerAccountsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should serialize pagination, filters and false verification values', () => {
    service.getAccounts(2, 50, {
      search: 'Ana',
      status: 'BLOCKED',
      emailVerified: false,
      sortBy: 'name',
      direction: 'ASC'
    }).subscribe();

    const request = http.expectOne((candidate) =>
      candidate.url === 'http://localhost:8080/api/admin/passenger-users'
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('search')).toBe('Ana');
    expect(request.request.params.get('status')).toBe('BLOCKED');
    expect(request.request.params.get('emailVerified')).toBe('false');
    expect(request.request.params.get('sortBy')).toBe('name');
    expect(request.request.params.get('direction')).toBe('ASC');
    request.flush({ summary: {}, users: [] });
  });

  it('should request CSRF before changing the status of a passenger', () => {
    service.updateStatus('passenger/uuid', 'DISABLED', ' Solicitud administrativa ')
      .subscribe();

    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-value'
    });
    const request = http.expectOne(
      'http://localhost:8080/api/admin/passenger-users/passenger%2Fuuid/status'
    );
    expect(request.request.method).toBe('PATCH');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect(request.request.body).toEqual({
      status: 'DISABLED',
      reason: 'Solicitud administrativa'
    });
    request.flush({});
  });

  it('should create a passenger after obtaining a CSRF token', () => {
    const account = {
      email: 'ana@example.local', password: 'SecurePassword123',
      firstName: 'Ana', lastName: 'García'
    };
    service.createAccount(account).subscribe();

    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-value'
    });
    const request = http.expectOne('http://localhost:8080/api/admin/passenger-users');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(account);
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    request.flush({});
  });

  it('should delete an encoded passenger identifier with CSRF protection', () => {
    service.deleteAccount('passenger/uuid').subscribe();

    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-value'
    });
    const request = http.expectOne(
      'http://localhost:8080/api/admin/passenger-users/passenger%2Fuuid'
    );
    expect(request.request.method).toBe('DELETE');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
    request.flush(null);
  });
});
