export type IncidentCategory =
  | 'SERVICE'
  | 'DEVICE'
  | 'INFRASTRUCTURE'
  | 'TICKETING'
  | 'SECURITY'
  | 'OTHER';

export type IncidentPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED';
export type IncidentSort = 'openedAt' | 'updatedAt' | 'priority' | 'status' | 'title';
export type IncidentSortDirection = 'ASC' | 'DESC';

export interface IncidentOperator {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
}

export interface IncidentResource {
  id: number;
  code: string;
  name: string;
}

export interface IncidentStatusChange {
  id: number;
  previousStatus: IncidentStatus | null;
  newStatus: IncidentStatus;
  note: string | null;
  changedBy: IncidentOperator;
  createdAt: string;
}

export interface IncidentComment {
  id: number;
  text: string;
  author: IncidentOperator;
  createdAt: string;
  updatedAt: string;
}

export interface Incident {
  code: string;
  title: string;
  description: string;
  category: IncidentCategory;
  priority: IncidentPriority;
  status: IncidentStatus;
  createdBy: IncidentOperator;
  assignedTo: IncidentOperator | null;
  affectedLine: IncidentResource | null;
  affectedStation: IncidentResource | null;
  affectedTrain: IncidentResource | null;
  affectedDevice: IncidentResource | null;
  affectedDepot: IncidentResource | null;
  resolutionSummary: string | null;
  openedAt: string;
  assignedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
  statusHistory: IncidentStatusChange[];
  comments: IncidentComment[];
}

export interface IncidentSummary {
  total: number;
  open: number;
  inProgress: number;
  resolved: number;
  closed: number;
  cancelled: number;
}

export interface IncidentsPage {
  summary: IncidentSummary;
  incidents: Incident[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
