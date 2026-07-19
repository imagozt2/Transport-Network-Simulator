export type TrainStatus = 'IN_SERVICE' | 'DEPOT' | 'MAINTENANCE' | 'STOPPED' | 'OUT_OF_SERVICE' | 'RESERVE' | 'HISTORIC';
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR';
export type DeviceType = 'TICKET_MACHINE' | 'ENTRY_VALIDATOR' | 'EXIT_VALIDATOR';

export interface DashboardResponse {
  network: DashboardNetwork;
  fleet: DashboardFleet;
  devices: DashboardDevices;
  depots: DashboardDepots;
  lines: DashboardLine[];
}

export interface DashboardNetwork { activeStations: number; activeLines: number; }
export interface DashboardFleet { activeTrains: number; byStatus: Partial<Record<TrainStatus, number>>; }
export interface DashboardDevices {
  activeDevices: number;
  byStatus: Partial<Record<DeviceStatus, number>>;
  byType: Partial<Record<DeviceType, number>>;
}
export interface DashboardDepots {
  activeDepots: number;
  totalCapacity: number;
  assignedTrains: number;
  freeSlots: number;
  occupationPercentage: number;
  items: DashboardDepot[];
}
export interface DashboardDepot {
  id: number; code: string; name: string; capacity: number; assignedTrains: number; freeSlots: number;
}
export interface DashboardLine { id: number; code: string; name: string; color: string; }
