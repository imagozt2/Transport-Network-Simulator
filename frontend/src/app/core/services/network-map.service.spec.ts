import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NetworkMapService } from './network-map.service';

describe('NetworkMapService', () => {
  it('should request the complete network map', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NetworkMapService);
    const http = TestBed.inject(HttpTestingController);
    service.getNetworkMap().subscribe();
    const request = http.expectOne('http://localhost:8080/api/network-map');
    expect(request.request.method).toBe('GET');
    request.flush({ lines: [] });
    http.verify();
  });

  it('should request a journey using its origin and destination station codes', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NetworkMapService);
    const http = TestBed.inject(HttpTestingController);

    service.calculateJourney('ST001', 'ST045').subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === 'http://localhost:8080/api/network-map/journeys' &&
        candidate.params.get('originStationCode') === 'ST001' &&
        candidate.params.get('destinationStationCode') === 'ST045',
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      origin: { id: 1, code: 'ST001', name: 'Aeropuerto' },
      destination: { id: 45, code: 'ST045', name: 'Los Molinos' },
      stationCount: 2,
      transferCount: 0,
      estimatedDurationSeconds: 120,
      stations: [],
      segments: [],
    });
    http.verify();
  });
});
