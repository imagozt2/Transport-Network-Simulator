import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { IncidentWriteRequest } from '../models/incident.model';
import { IncidentsService } from './incidents.service';

describe('IncidentsService', () => {
  let service: IncidentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(IncidentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should serialize all active incident filters', () => {
    service.getIncidents(2, 50, {
      search: 'validadora', status: 'IN_PROGRESS', priority: 'HIGH',
      category: 'DEVICE', assignedOperatorId: 7,
      sortBy: 'updatedAt', direction: 'ASC'
    }).subscribe();

    const request = http.expectOne('http://localhost:8080/api/incidents?page=2&size=50&sortBy=updatedAt&direction=ASC&search=validadora&status=IN_PROGRESS&priority=HIGH&category=DEVICE&assignedOperatorId=7');
    expect(request.request.method).toBe('GET');
    request.flush({ incidents: [] });
  });

  it('should protect creation status changes and comments with CSRF', () => {
    const writeRequest: IncidentWriteRequest = {
      title: 'Fallo', description: 'Descripción', category: 'DEVICE', priority: 'HIGH',
      assignedOperatorId: 7, affectedLineId: null, affectedStationId: null,
      affectedTrainId: null, affectedDeviceId: null, affectedDepotId: null
    };

    service.createIncident(writeRequest).subscribe();
    flushCsrf();
    const creation = http.expectOne('http://localhost:8080/api/incidents');
    expect(creation.request.method).toBe('POST');
    expect(creation.request.body).toEqual(writeRequest);
    expect(creation.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    creation.flush({});

    service.changeStatus('INC/001', {
      status: 'RESOLVED', note: 'Comprobado', resolutionSummary: 'Reiniciada'
    }).subscribe();
    flushCsrf();
    const status = http.expectOne('http://localhost:8080/api/incidents/INC%2F001/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({
      status: 'RESOLVED', note: 'Comprobado', resolutionSummary: 'Reiniciada'
    });
    status.flush({});

    service.addComment('INC/001', '  Diagnóstico iniciado  ').subscribe();
    flushCsrf();
    const comment = http.expectOne('http://localhost:8080/api/incidents/INC%2F001/comments');
    expect(comment.request.method).toBe('POST');
    expect(comment.request.body).toEqual({ text: 'Diagnóstico iniciado' });
    comment.flush({});
  });

  function flushCsrf(): void {
    http.expectOne('http://localhost:8080/api/auth/csrf').flush({
      headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token'
    });
  }
});
