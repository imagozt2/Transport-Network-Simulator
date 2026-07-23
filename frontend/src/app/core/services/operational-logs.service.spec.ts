import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperationalLogsService } from './operational-logs.service';

describe('OperationalLogsService', () => {
  it('should send pagination and active filters to the logs endpoint', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    const service = TestBed.inject(OperationalLogsService);
    const http = TestBed.inject(HttpTestingController);

    service.getLogs(2, 50, {
      deviceCode: 'RMM-MB-ST001-001',
      severity: 'ERROR',
      stationCode: 'ST001'
    }).subscribe();

    const request = http.expectOne((candidate) =>
      candidate.url === 'http://localhost:8080/api/logs'
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('deviceCode')).toBe('RMM-MB-ST001-001');
    expect(request.request.params.get('severity')).toBe('ERROR');
    expect(request.request.params.get('stationCode')).toBe('ST001');
    expect(request.request.params.has('origin')).toBe(false);

    request.flush({
      logs: [],
      currentPage: 2,
      pageSize: 50,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true
    });
    http.verify();
  });
});
