import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TrainOperationsService } from './train-operations.service';

describe('TrainOperationsService', () => {
  it('should request the operational fleet query', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(TrainOperationsService);
    const http = TestBed.inject(HttpTestingController);

    service.getOperations().subscribe();

    const request = http.expectOne('http://localhost:8080/api/trains/operations');
    expect(request.request.method).toBe('GET');
    request.flush({
      evaluatedAt: '2026-07-22T08:30:00+02:00', phase: 'OPERATING',
      summary: { activeFleet: 0, trainsInService: 0, trainsInDepots: 0, byStatus: {}, byRole: {}, bySeries: {} },
      trains: []
    });
    http.verify();
  });
});
