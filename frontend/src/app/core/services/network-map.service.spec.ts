import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NetworkMapService } from './network-map.service';

describe('NetworkMapService', () => {
  it('should request the complete network map', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(NetworkMapService);
    const http = TestBed.inject(HttpTestingController);
    service.getNetworkMap().subscribe();
    const request = http.expectOne('http://localhost:8080/api/network-map');
    expect(request.request.method).toBe('GET');
    request.flush({ lines: [] });
    http.verify();
  });
});
