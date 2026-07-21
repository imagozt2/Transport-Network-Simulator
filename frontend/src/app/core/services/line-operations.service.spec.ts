import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LineOperationsService } from './line-operations.service';

describe('LineOperationsService', () => {
  it('should request the operational summary of all lines', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(LineOperationsService);
    const http = TestBed.inject(HttpTestingController);

    service.getOperations().subscribe();

    const request = http.expectOne('http://localhost:8080/api/lines/operations');
    expect(request.request.method).toBe('GET');
    request.flush({ evaluatedAt: '2026-07-21T08:30:00+02:00', phase: 'OPERATING', activeLineCount: 0, lines: [] });
    http.verify();
  });
});
