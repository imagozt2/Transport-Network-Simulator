import {
  DeviceEventSource,
  DeviceEventType,
  LogOrigin,
  LogSeverity,
  TicketProductType
} from './operational-log.types';

export interface OperationalLog {
  id: number;
  origin: LogOrigin;
  source: DeviceEventSource;
  eventType: DeviceEventType;
  severity: LogSeverity;
  message: string;
  deviceId: number;
  deviceCode: string;
  deviceName: string;
  stationId: number;
  stationCode: string;
  stationName: string;
  ticketCode: string | null;
  ticketType: TicketProductType | null;
  compensatoryIssuanceCode: string | null;
  externalReference: string | null;
  occurredAt: string;
  receivedAt: string;
}

export interface OperationalLogPage {
  logs: OperationalLog[];
  currentPage: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
