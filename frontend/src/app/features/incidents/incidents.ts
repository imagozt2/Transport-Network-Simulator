import { Component, inject, OnInit } from '@angular/core';

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
import { formatDateTime } from '../../core/utils/temporal-formatters';

type OptionalStatus = IncidentStatus | 'ALL';
type OptionalPriority = IncidentPriority | 'ALL';
type OptionalCategory = IncidentCategory | 'ALL';

@Component({
  selector: 'app-incidents',
  templateUrl: './incidents.html',
  styleUrl: './incidents.css'
})
export class Incidents implements OnInit {
  private readonly incidentsService = inject(IncidentsService);

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
}
