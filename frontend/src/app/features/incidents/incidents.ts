import { Component, HostListener, inject, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap } from '@angular/router';

import {
  Incident,
  IncidentCategory,
  IncidentPriority,
  IncidentResource,
  IncidentSort,
  IncidentSortDirection,
  IncidentStatus,
  IncidentSummary
} from '../../core/models/incident.model';
import { IncidentsService } from '../../core/services/incidents.service';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { formatDateTime } from '../../core/utils/temporal-formatters';

type OptionalStatus = IncidentStatus | 'ALL';
type OptionalPriority = IncidentPriority | 'ALL';
type OptionalCategory = IncidentCategory | 'ALL';

@Component({
  selector: 'app-incidents',
  templateUrl: './incidents.html',
  styleUrls: ['./incidents.css', './incidents-detail.css']
})
export class Incidents implements OnInit {
  private readonly incidentsService = inject(IncidentsService);
  private readonly operatorAuthService = inject(OperatorAuthService);
  private readonly route = inject(ActivatedRoute, { optional: true });

  incidents: Incident[] = [];
  summary: IncidentSummary = {
    total: 0,
    open: 0,
    inProgress: 0,
    resolved: 0,
    closed: 0,
    cancelled: 0
  };
  loading = true;
  errorMessage = '';
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  firstPage = true;
  lastPage = true;
  selectedIncident: Incident | null = null;
  detailLoading = false;
  detailError = '';
  createDialogOpen = false;
  createTitle = '';
  createDescription = '';
  createCategory: IncidentCategory = 'SERVICE';
  createPriority: IncidentPriority = 'MEDIUM';
  createAssignToMe = true;
  createSubmitting = false;
  createError = '';
  createAffectedDeviceId: number | null = null;
  createAffectedDeviceCode: string | null = null;
  createContextTicketCode: string | null = null;
  editing = false;
  editTitle = '';
  editDescription = '';
  editCategory: IncidentCategory = 'SERVICE';
  editPriority: IncidentPriority = 'MEDIUM';
  editSubmitting = false;
  editError = '';
  workflowSubmitting = false;
  workflowError = '';
  statusNote = '';
  resolutionSummary = '';
  pendingStatus: IncidentStatus | null = null;
  commentText = '';
  commentSubmitting = false;
  commentError = '';

  readonly operator = this.operatorAuthService.currentOperator;

  search = '';
  selectedStatus: OptionalStatus = 'ALL';
  selectedPriority: OptionalPriority = 'ALL';
  selectedCategory: OptionalCategory = 'ALL';
  selectedSort: IncidentSort = 'openedAt';
  selectedDirection: IncidentSortDirection = 'DESC';

  readonly statuses: readonly IncidentStatus[] = [
    'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED'
  ];
  readonly priorities: readonly IncidentPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly categories: readonly IncidentCategory[] = [
    'SERVICE', 'DEVICE', 'INFRASTRUCTURE', 'TICKETING', 'SECURITY', 'OTHER'
  ];
  readonly pageSizes = [20, 50, 100];

  ngOnInit(): void {
    this.loadIncidents(0);
    if (this.route?.snapshot.queryParamMap.get('create') === 'true') {
      this.openCreateDialogFromContext(this.route.snapshot.queryParamMap);
    }
  }

  loadIncidents(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.incidentsService.getIncidents(page, this.pageSize, {
      search: this.search.trim() || undefined,
      status: this.selectedStatus === 'ALL' ? undefined : this.selectedStatus,
      priority: this.selectedPriority === 'ALL' ? undefined : this.selectedPriority,
      category: this.selectedCategory === 'ALL' ? undefined : this.selectedCategory,
      sortBy: this.selectedSort,
      direction: this.selectedDirection
    }).subscribe({
      next: (response) => {
        this.incidents = response.incidents;
        this.summary = response.summary;
        this.currentPage = response.page;
        this.pageSize = response.size;
        this.totalElements = response.totalElements;
        this.totalPages = response.totalPages;
        this.firstPage = response.first;
        this.lastPage = response.last;
        this.loading = false;
      },
      error: () => {
        this.incidents = [];
        this.errorMessage = 'No se han podido cargar las incidencias.';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.loadIncidents(0);
  }

  clearFilters(): void {
    this.search = '';
    this.selectedStatus = 'ALL';
    this.selectedPriority = 'ALL';
    this.selectedCategory = 'ALL';
    this.selectedSort = 'openedAt';
    this.selectedDirection = 'DESC';
    this.loadIncidents(0);
  }

  hasActiveFilters(): boolean {
    return this.search.trim() !== ''
      || this.selectedStatus !== 'ALL'
      || this.selectedPriority !== 'ALL'
      || this.selectedCategory !== 'ALL'
      || this.selectedSort !== 'openedAt'
      || this.selectedDirection !== 'DESC';
  }

  setPageSize(value: string): void {
    this.pageSize = Number(value);
    this.loadIncidents(0);
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.loadIncidents(page);
    }
  }

  openCreateDialog(): void {
    this.createTitle = '';
    this.createDescription = '';
    this.createCategory = 'SERVICE';
    this.createPriority = 'MEDIUM';
    this.createAssignToMe = true;
    this.createError = '';
    this.createAffectedDeviceId = null;
    this.createAffectedDeviceCode = null;
    this.createContextTicketCode = null;
    this.createDialogOpen = true;
  }

  closeCreateDialog(): void {
    if (!this.createSubmitting) this.createDialogOpen = false;
  }

  canCreate(): boolean {
    return !this.createSubmitting
      && this.createTitle.trim() !== ''
      && this.createDescription.trim() !== '';
  }

  createIncident(): void {
    if (!this.canCreate()) return;
    this.createSubmitting = true;
    this.createError = '';
    this.incidentsService.createIncident({
      title: this.createTitle.trim(),
      description: this.createDescription.trim(),
      category: this.createCategory,
      priority: this.createPriority,
      assignedOperatorId: this.createAssignToMe ? this.operator()?.id ?? null : null,
      affectedLineId: null,
      affectedStationId: null,
      affectedTrainId: null,
      affectedDeviceId: this.createAffectedDeviceId,
      affectedDepotId: null
    }).subscribe({
      next: (incident) => {
        this.createSubmitting = false;
        this.createDialogOpen = false;
        this.loadIncidents(0);
        this.openDetail(incident);
      },
      error: () => {
        this.createSubmitting = false;
        this.createError = 'No se ha podido registrar la incidencia.';
      }
    });
  }

  openDetail(incident: Incident): void {
    this.selectedIncident = incident;
    this.detailLoading = true;
    this.detailError = '';
    this.cancelEdit();
    this.cancelStatusChange();
    this.incidentsService.getIncident(incident.code).subscribe({
      next: (detail) => {
        this.selectedIncident = detail;
        this.detailLoading = false;
      },
      error: () => {
        this.detailError = 'No se ha podido cargar el detalle de la incidencia.';
        this.detailLoading = false;
      }
    });
  }

  closeDetail(): void {
    if (this.editSubmitting || this.workflowSubmitting || this.commentSubmitting) return;
    this.selectedIncident = null;
    this.cancelEdit();
    this.cancelStatusChange();
  }

  beginEdit(): void {
    if (!this.selectedIncident || !this.isEditable(this.selectedIncident)) return;
    this.editTitle = this.selectedIncident.title;
    this.editDescription = this.selectedIncident.description;
    this.editCategory = this.selectedIncident.category;
    this.editPriority = this.selectedIncident.priority;
    this.editError = '';
    this.editing = true;
  }

  cancelEdit(): void {
    if (!this.editSubmitting) {
      this.editing = false;
      this.editError = '';
    }
  }

  saveEdit(assignment: 'KEEP' | 'ME' | 'NONE' = 'KEEP'): void {
    const incident = this.selectedIncident;
    if (!incident || this.editTitle.trim() === '' || this.editDescription.trim() === '') return;
    const assignedOperatorId = assignment === 'ME'
      ? this.operator()?.id ?? null
      : assignment === 'NONE' ? null : incident.assignedTo?.id ?? null;
    this.editSubmitting = true;
    this.editError = '';
    this.incidentsService.updateIncident(incident.code, {
      title: this.editTitle.trim(),
      description: this.editDescription.trim(),
      category: this.editCategory,
      priority: this.editPriority,
      assignedOperatorId,
      affectedLineId: incident.affectedLine?.id ?? null,
      affectedStationId: incident.affectedStation?.id ?? null,
      affectedTrainId: incident.affectedTrain?.id ?? null,
      affectedDeviceId: incident.affectedDevice?.id ?? null,
      affectedDepotId: incident.affectedDepot?.id ?? null
    }).subscribe({
      next: () => {
        this.editSubmitting = false;
        this.editing = false;
        this.refreshSelectedIncident();
        this.loadIncidents(this.currentPage);
      },
      error: () => {
        this.editSubmitting = false;
        this.editError = 'No se han podido guardar los cambios.';
      }
    });
  }

  assignToMe(): void {
    if (!this.selectedIncident) return;
    this.beginEdit();
    this.saveEdit('ME');
  }

  unassign(): void {
    if (!this.selectedIncident) return;
    this.beginEdit();
    this.saveEdit('NONE');
  }

  availableTransitions(status: IncidentStatus): IncidentStatus[] {
    return {
      OPEN: ['IN_PROGRESS', 'CANCELLED'],
      IN_PROGRESS: ['RESOLVED', 'CANCELLED'],
      RESOLVED: ['IN_PROGRESS', 'CLOSED'],
      CLOSED: [],
      CANCELLED: []
    }[status] as IncidentStatus[];
  }

  beginStatusChange(status: IncidentStatus): void {
    this.pendingStatus = status;
    this.statusNote = '';
    this.resolutionSummary = '';
    this.workflowError = '';
  }

  cancelStatusChange(): void {
    if (!this.workflowSubmitting) {
      this.pendingStatus = null;
      this.statusNote = '';
      this.resolutionSummary = '';
      this.workflowError = '';
    }
  }

  canConfirmStatus(): boolean {
    return this.pendingStatus !== null
      && !this.workflowSubmitting
      && (this.pendingStatus !== 'RESOLVED' || this.resolutionSummary.trim() !== '');
  }

  confirmStatusChange(): void {
    if (!this.selectedIncident || !this.pendingStatus || !this.canConfirmStatus()) return;
    this.workflowSubmitting = true;
    this.incidentsService.changeStatus(this.selectedIncident.code, {
      status: this.pendingStatus,
      note: this.statusNote.trim() || null,
      resolutionSummary: this.pendingStatus === 'RESOLVED'
        ? this.resolutionSummary.trim() : null
    }).subscribe({
      next: () => {
        this.workflowSubmitting = false;
        this.cancelStatusChange();
        this.refreshSelectedIncident();
        this.loadIncidents(this.currentPage);
      },
      error: () => {
        this.workflowSubmitting = false;
        this.workflowError = 'No se ha podido actualizar el estado.';
      }
    });
  }

  addComment(): void {
    if (!this.selectedIncident || this.commentText.trim() === '' || this.commentSubmitting) return;
    this.commentSubmitting = true;
    this.commentError = '';
    this.incidentsService.addComment(this.selectedIncident.code, this.commentText).subscribe({
      next: () => {
        this.commentText = '';
        this.commentSubmitting = false;
        this.refreshSelectedIncident();
      },
      error: () => {
        this.commentSubmitting = false;
        this.commentError = 'No se ha podido añadir el comentario.';
      }
    });
  }

  isEditable(incident: Incident): boolean {
    return incident.status !== 'CLOSED' && incident.status !== 'CANCELLED';
  }

  @HostListener('document:keydown.escape')
  closeActiveDialog(): void {
    if (this.createDialogOpen) this.closeCreateDialog();
    else if (this.selectedIncident) this.closeDetail();
  }

  affectedResources(incident: Incident): string {
    const resources = [
      incident.affectedLine,
      incident.affectedStation,
      incident.affectedTrain,
      incident.affectedDevice,
      incident.affectedDepot
    ].filter((resource): resource is IncidentResource => resource !== null)
      .map((resource) => resource.name);
    return resources.length > 0 ? resources.join(' · ') : 'Alcance general';
  }

  operatorName(incident: Incident): string {
    const operator = incident.assignedTo;
    return operator ? `${operator.firstName} ${operator.lastName}` : 'Sin asignar';
  }

  statusLabel(status: IncidentStatus): string {
    return {
      OPEN: 'Abierta',
      IN_PROGRESS: 'En curso',
      RESOLVED: 'Resuelta',
      CLOSED: 'Cerrada',
      CANCELLED: 'Cancelada'
    }[status];
  }

  priorityLabel(priority: IncidentPriority): string {
    return { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Crítica' }[priority];
  }

  categoryLabel(category: IncidentCategory): string {
    return {
      SERVICE: 'Servicio',
      DEVICE: 'Máquina',
      INFRASTRUCTURE: 'Infraestructura',
      TICKETING: 'Billetaje',
      SECURITY: 'Seguridad',
      OTHER: 'Otra'
    }[category];
  }

  formatDate(value: string): string {
    return formatDateTime(value, 'Sin fecha');
  }

  private refreshSelectedIncident(): void {
    if (!this.selectedIncident) return;
    this.detailLoading = true;
    this.incidentsService.getIncident(this.selectedIncident.code).subscribe({
      next: (incident) => {
        this.selectedIncident = incident;
        this.detailLoading = false;
      },
      error: () => {
        this.detailError = 'No se ha podido actualizar el detalle de la incidencia.';
        this.detailLoading = false;
      }
    });
  }

  private openCreateDialogFromContext(queryParams: ParamMap): void {
    const deviceId = this.positiveNumber(queryParams.get('deviceId'));
    const deviceCode = queryParams.get('deviceCode')?.trim() || null;
    const ticketCode = queryParams.get('ticketCode')?.trim() || null;
    const issuanceCode = queryParams.get('issuanceCode')?.trim() || null;
    const externalReference = queryParams.get('externalReference')?.trim() || null;
    const eventType = queryParams.get('eventType')?.trim() || null;

    this.openCreateDialog();
    this.createAffectedDeviceId = deviceId;
    this.createAffectedDeviceCode = deviceCode;
    this.createContextTicketCode = ticketCode;
    this.createCategory = ticketCode || issuanceCode ? 'TICKETING' : 'DEVICE';
    this.createTitle = ticketCode
      ? `Incidencia del billete ${ticketCode}`
      : `Incidencia de la máquina ${deviceCode ?? 'seleccionada'}`;
    this.createDescription = [
      deviceCode ? `Máquina afectada: ${deviceCode}` : null,
      ticketCode ? `Billete afectado: ${ticketCode}` : null,
      issuanceCode ? `Emisión relacionada: ${issuanceCode}` : null,
      externalReference ? `Referencia de operación: ${externalReference}` : null,
      eventType ? `Evento relacionado: ${eventType}` : null
    ].filter((value): value is string => value !== null).join('\n');
  }

  private positiveNumber(value: string | null): number | null {
    if (!value || !/^\d+$/.test(value)) return null;
    const parsed = Number(value);
    return parsed > 0 && Number.isSafeInteger(parsed) ? parsed : null;
  }
}
