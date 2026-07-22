import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DepotOperationsService } from './depot-operations.service';

describe('DepotOperationsService', () => {
  it('should request the operational depot query', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(DepotOperationsService);
    const http = TestBed.inject(HttpTestingController);

    service.getOperations().subscribe();

    const request = http.expectOne('http://localhost:8080/api/depots/operations');
    expect(request.request.method).toBe('GET');
    request.flush({ evaluatedAt: '2026-07-22T08:30:00+02:00', phase: 'OPERATING', summary: {}, depots: [] });
    http.verify();
  });
});
