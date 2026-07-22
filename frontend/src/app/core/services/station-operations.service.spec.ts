import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StationOperationsService } from './station-operations.service';

describe('StationOperationsService', () => {
  it('should request the operational summary of all stations', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(StationOperationsService);
    const http = TestBed.inject(HttpTestingController);

    service.getOperations().subscribe();

    const request = http.expectOne('http://localhost:8080/api/stations/operations');
    expect(request.request.method).toBe('GET');
    request.flush({
      evaluatedAt: '2026-07-22T08:30:00+02:00', phase: 'OPERATING',
      stationCount: 0, activeStationCount: 0, stations: []
    });
    http.verify();
  });
});
