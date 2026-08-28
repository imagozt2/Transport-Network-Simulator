import { DepotOperationStatus } from '../models/depot-operation.model';
import { DeviceConnectivityState, DeviceStatus, DeviceType } from '../models/device-operation.model';
import { ServiceOperationPhase, ServicePeriodType } from '../models/line-operation.model';
import { StationOperationStatus } from '../models/station-operation.model';
import { FleetRole, TrainStatus } from '../models/train-operation.model';
import { DeviceEventSource } from '../models/operational-log.types';

const SERVICE_PHASE_LABELS: Readonly<Record<ServiceOperationPhase, string>> = {
  CLOSED: 'Cerrado',
  STARTING: 'Inicio de servicio',
  OPERATING: 'En operación',
  ENDING: 'Fin de servicio'
};

const SERVICE_PERIOD_LABELS: Readonly<Record<ServicePeriodType, string>> = {
  SERVICE_START: 'Inicio de servicio',
  OFF_PEAK: 'Hora valle',
  PEAK: 'Hora punta',
  REGULAR: 'Servicio regular',
  SERVICE_END: 'Fin de servicio'
};

const STATION_STATUS_LABELS: Readonly<Record<StationOperationStatus, string>> = {
  NORMAL: 'Normal',
  DEGRADED: 'Degradada',
  CRITICAL: 'Crítica',
  NO_TRAINS: 'Sin trenes',
  CLOSED: 'Cerrada'
};

const TRAIN_STATUS_LABELS: Readonly<Record<TrainStatus, string>> = {
  IN_SERVICE: 'En servicio',
  DEPOT: 'En cochera',
  MAINTENANCE: 'Mantenimiento',
  STOPPED: 'Detenido',
  OUT_OF_SERVICE: 'Fuera de servicio'
};

const FLEET_ROLE_LABELS: Readonly<Record<FleetRole, string>> = {
  REGULAR_SERVICE: 'Servicio regular',
  RESERVE: 'Reserva',
  HISTORIC: 'Histórico'
};

const DEPOT_STATUS_LABELS: Readonly<Record<DepotOperationStatus, string>> = {
  EMPTY: 'Vacía',
  AVAILABLE: 'Disponible',
  HIGH_OCCUPANCY: 'Ocupación alta',
  FULL: 'Completa',
  OVER_CAPACITY: 'Sobreocupada'
};

const DEVICE_STATUS_LABELS: Readonly<Record<DeviceStatus, string>> = {
  ONLINE: 'Online',
  OFFLINE: 'Offline',
  MAINTENANCE: 'Mantenimiento',
  ERROR: 'Error'
};

const DEVICE_TYPE_LABELS: Readonly<Record<DeviceType, string>> = {
  TICKET_MACHINE: 'Máquina de billetes',
  ENTRY_VALIDATOR: 'Validador de entrada',
  EXIT_VALIDATOR: 'Validador de salida'
};

const DEVICE_TYPE_SHORT_LABELS: Readonly<Record<DeviceType, string>> = {
  TICKET_MACHINE: 'MB',
  ENTRY_VALIDATOR: 'VE',
  EXIT_VALIDATOR: 'VS'
};

const DEVICE_EVENT_SOURCE_LABELS: Readonly<Record<DeviceEventSource, string>> = {
  REAL: 'Dispositivo real',
  SIMULATED: 'Simulación',
  ADMINISTRATIVE: 'Operador'
};

const DEVICE_CONNECTIVITY_LABELS: Readonly<Record<DeviceConnectivityState, string>> = {
  CONNECTED: 'MQTT conectado',
  DISCONNECTED: 'MQTT desconectado',
  NOT_MONITORED: 'Sin monitorización MQTT'
};

export const servicePhaseLabel = (phase: ServiceOperationPhase): string => SERVICE_PHASE_LABELS[phase];
export const servicePeriodLabel = (period: ServicePeriodType): string => SERVICE_PERIOD_LABELS[period];
export const stationStatusLabel = (status: StationOperationStatus): string => STATION_STATUS_LABELS[status];
export const trainStatusLabel = (status: TrainStatus): string => TRAIN_STATUS_LABELS[status];
export const fleetRoleLabel = (role: FleetRole): string => FLEET_ROLE_LABELS[role];
export const depotStatusLabel = (status: DepotOperationStatus): string => DEPOT_STATUS_LABELS[status];
export const deviceStatusLabel = (status: DeviceStatus): string => DEVICE_STATUS_LABELS[status];
export const deviceTypeLabel = (type: DeviceType): string => DEVICE_TYPE_LABELS[type];
export const deviceTypeShortLabel = (type: DeviceType): string => DEVICE_TYPE_SHORT_LABELS[type];
export const deviceEventSourceLabel = (source: DeviceEventSource): string => DEVICE_EVENT_SOURCE_LABELS[source];
export const deviceConnectivityLabel = (state: DeviceConnectivityState): string => DEVICE_CONNECTIVITY_LABELS[state];
