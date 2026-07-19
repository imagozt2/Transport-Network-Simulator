import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  it('should request the aggregated dashboard summary', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(DashboardService);
    const http = TestBed.inject(HttpTestingController);

    service.getSummary().subscribe();

    const request = http.expectOne('http://localhost:8080/api/dashboard/summary');
    expect(request.request.method).toBe('GET');
    request.flush({});
    http.verify();
  });
});
