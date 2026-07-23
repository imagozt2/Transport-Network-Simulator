import { DeviceEventType, LogOrigin, LogSeverity } from './operational-log.types';

export interface OperationalLog {
  id: number;
  origin: LogOrigin;
  eventType: DeviceEventType;
  severity: LogSeverity;
  message: string;
  deviceId: number;
  deviceCode: string;
  deviceName: string;
  stationId: number;
  stationCode: string;
  stationName: string;
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
