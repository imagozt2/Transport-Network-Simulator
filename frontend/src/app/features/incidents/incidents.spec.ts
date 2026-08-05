import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Incident, IncidentsPage } from '../../core/models/incident.model';
import { OperatorAccount } from '../../core/models/operator-auth.model';
import { IncidentsService } from '../../core/services/incidents.service';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { Incidents } from './incidents';

describe('Incidents', () => {
  let fixture: ComponentFixture<Incidents>;
  const incidentsService = {
    getIncidents: vi.fn(),
    getIncident: vi.fn(),
    createIncident: vi.fn(),
    updateIncident: vi.fn(),
    changeStatus: vi.fn(),
    addComment: vi.fn()
  };

  beforeEach(async () => {
    incidentsService.getIncidents.mockReset().mockReturnValue(of(page));
    incidentsService.getIncident.mockReset().mockReturnValue(of(incident));
    incidentsService.createIncident.mockReset().mockReturnValue(of(incident));
    incidentsService.updateIncident.mockReset().mockReturnValue(of(incident));
    incidentsService.changeStatus.mockReset().mockReturnValue(of({
      ...incident, status: 'IN_PROGRESS'
    }));
    incidentsService.addComment.mockReset().mockReturnValue(of(incident.comments[0]));

    await TestBed.configureTestingModule({
      imports: [Incidents],
      providers: [
        { provide: IncidentsService, useValue: incidentsService },
        { provide: OperatorAuthService, useValue: { currentOperator: signal(operator).asReadonly() } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Incidents);
    fixture.detectChanges();
  });

  it('should render the operational summary and incident list', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.summary-grid')?.textContent).toContain('4');
    expect(compiled.querySelector('tbody')?.textContent).toContain('Fallo de validación');
    expect(compiled.querySelector('tbody')?.textContent).toContain('INC-TEST');
    expect(compiled.querySelector('tbody')?.textContent).toContain('Alta');
  });

  it('should create an assigned incident and open its detail', () => {
    const component = fixture.componentInstance;
    component.openCreateDialog();
    component.createTitle = '  Fallo de validación  ';
    component.createDescription = '  No procesa el QR  ';
    component.createCategory = 'DEVICE';
    component.createPriority = 'HIGH';
    component.createAssignToMe = true;
    component.createIncident();

    expect(incidentsService.createIncident).toHaveBeenCalledWith({
      title: 'Fallo de validación',
      description: 'No procesa el QR',
      category: 'DEVICE',
      priority: 'HIGH',
      assignedOperatorId: 7,
      affectedLineId: null,
      affectedStationId: null,
      affectedTrainId: null,
      affectedDeviceId: null,
      affectedDepotId: null
    });
    expect(component.createDialogOpen).toBe(false);
    expect(incidentsService.getIncident).toHaveBeenCalledWith('INC-TEST');
    expect(component.selectedIncident?.code).toBe('INC-TEST');
  });

  it('should load details, add comments and advance the workflow', () => {
    const component = fixture.componentInstance;
    component.openDetail(incident);
    expect(component.selectedIncident?.statusHistory).toHaveLength(1);

    component.commentText = '  Diagnóstico iniciado  ';
    component.addComment();
    expect(incidentsService.addComment).toHaveBeenCalledWith(
      'INC-TEST', '  Diagnóstico iniciado  '
    );
    expect(component.commentText).toBe('');

    component.beginStatusChange('IN_PROGRESS');
    component.statusNote = 'Revisión remota';
    component.confirmStatusChange();
    expect(incidentsService.changeStatus).toHaveBeenCalledWith('INC-TEST', {
      status: 'IN_PROGRESS', note: 'Revisión remota', resolutionSummary: null
    });

    component.beginStatusChange('RESOLVED');
    expect(component.canConfirmStatus()).toBe(false);
    component.resolutionSummary = 'Lector reiniciado';
    expect(component.canConfirmStatus()).toBe(true);
  });

  it('should preserve affected resources when assigning the ticket to the current operator', () => {
    const component = fixture.componentInstance;
    component.openDetail({ ...incident, assignedTo: null });
    component.assignToMe();

    expect(incidentsService.updateIncident).toHaveBeenCalledWith(
      'INC-TEST',
      expect.objectContaining({
        assignedOperatorId: 7,
        affectedDeviceId: 91,
        affectedStationId: 12
      })
    );
  });

  it('should retain responsive list, detail and workflow layouts', () => {
    const styles = loadedComponentStyles();

    expect(styles).toContain('@media (max-width: 1200px)');
    expect(styles).toContain('@media (max-width: 700px)');
    expect(styles).toContain('@media (max-width: 600px)');
    expect(styles).toMatch(/\.table-wrapper[^}]*overflow-x:\s*auto/);
  });

  const operator: OperatorAccount = {
    id: 7,
    username: 'operator',
    email: 'operator@rmm.local',
    firstName: 'Ana',
    lastName: 'Operadora',
    role: 'OPERATOR',
    status: 'ACTIVE',
    lastLoginAt: null,
    createdAt: null
  };

  const incident: Incident = {
    code: 'INC-TEST',
    title: 'Fallo de validación',
    description: 'La validadora no procesa el código QR.',
    category: 'DEVICE',
    priority: 'HIGH',
    status: 'OPEN',
    createdBy: operator,
    assignedTo: operator,
    affectedLine: null,
    affectedStation: { id: 12, code: 'ST012', name: 'Zona Universitaria' },
    affectedTrain: null,
    affectedDevice: { id: 91, code: 'RMM-VE-ST012-001', name: 'Validadora de entrada' },
    affectedDepot: null,
    resolutionSummary: null,
    openedAt: '2026-08-05T11:00:00',
    assignedAt: '2026-08-05T11:00:00',
    resolvedAt: null,
    closedAt: null,
    createdAt: '2026-08-05T11:00:00',
    updatedAt: '2026-08-05T11:00:00',
    statusHistory: [{
      id: 1,
      previousStatus: null,
      newStatus: 'OPEN',
      note: 'Incident created',
      changedBy: operator,
      createdAt: '2026-08-05T11:00:00'
    }],
    comments: [{
      id: 1,
      text: 'Diagnóstico iniciado',
      author: operator,
      createdAt: '2026-08-05T11:05:00',
      updatedAt: '2026-08-05T11:05:00'
    }]
  };

  const page: IncidentsPage = {
    summary: { total: 4, open: 1, inProgress: 1, resolved: 1, closed: 1, cancelled: 0 },
    incidents: [incident],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false
  };
});

function loadedComponentStyles(): string {
  return Array.from(document.head.querySelectorAll('style'))
    .map((style) => style.textContent ?? '')
    .join('\n');
}
