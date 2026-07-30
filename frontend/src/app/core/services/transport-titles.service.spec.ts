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
});
