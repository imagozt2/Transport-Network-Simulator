import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperatorDisplayPreferencesService } from './operator-display-preferences.service';
import { TemporalFormatService } from './temporal-format.service';

describe('OperatorDisplayPreferencesService', () => {
  let service: OperatorDisplayPreferencesService;
  let temporalFormat: TemporalFormatService;
  let http: HttpTestingController;

  beforeEach(() => {
    document.documentElement.classList.remove('theme-dark');
    document.documentElement.style.colorScheme = '';
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(OperatorDisplayPreferencesService);
    temporalFormat = TestBed.inject(TemporalFormatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.documentElement.classList.remove('theme-dark');
    document.documentElement.style.colorScheme = '';
  });

  it('should restore the time zone and dark theme returned by the operator account', () => {
    service.load().subscribe();

    const request = http.expectOne(
      'http://localhost:8080/api/operators/me/display-preferences'
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ timeZone: 'America/New_York', theme: 'DARK' });

    expect(service.preferences()).toEqual({ timeZone: 'America/New_York', theme: 'DARK' });
    expect(temporalFormat.timeZone()).toBe('America/New_York');
    expect(document.documentElement.classList.contains('theme-dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('should protect and persist an update before applying the returned preferences', () => {
    service.update({ timeZone: 'UTC', theme: 'LIGHT' }).subscribe();

    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token'
    });
    const request = http.expectOne(
      'http://localhost:8080/api/operators/me/display-preferences'
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    expect(request.request.body).toEqual({ timeZone: 'UTC', theme: 'LIGHT' });
    request.flush({ timeZone: 'UTC', theme: 'LIGHT' });

    expect(service.preferences()).toEqual({ timeZone: 'UTC', theme: 'LIGHT' });
    expect(temporalFormat.timeZone()).toBe('UTC');
    expect(document.documentElement.classList.contains('theme-dark')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');
  });
});
