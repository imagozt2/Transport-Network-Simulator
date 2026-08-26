import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TransportTitlesService } from './transport-titles.service';

describe('TransportTitlesService', () => {
  it('should request the transport title catalog', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const service = TestBed.inject(TransportTitlesService);
    const http = TestBed.inject(HttpTestingController);

    service.getTitles().subscribe();

    const request = http.expectOne('http://localhost:8080/api/transport-titles');
    expect(request.request.method).toBe('GET');
    request.flush({ currency: 'EUR', summary: {}, titles: [] });
    http.verify();
  });

  it('should obtain CSRF protection before issuing a compensatory ticket', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const service = TestBed.inject(TransportTitlesService);
    const http = TestBed.inject(HttpTestingController);
    const payload = {
      deliveryMethod: 'PHYSICAL_DEVICE' as const,
      deviceCode: 'TM-ST001-01',
      reason: 'Fallo de emisión',
      trips: 10
    };

    service.issueCompensatoryTicket(2, payload).subscribe();

    const csrf = http.expectOne('http://localhost:8080/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    expect(csrf.request.withCredentials).toBe(true);
    csrf.flush({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' });

    const issuance = http.expectOne(
      'http://localhost:8080/api/transport-titles/2/compensatory-issuances'
    );
    expect(issuance.request.method).toBe('POST');
    expect(issuance.request.body).toEqual(payload);
    expect(issuance.request.withCredentials).toBe(true);
    expect(issuance.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    issuance.flush({});
    http.verify();
  });
});
