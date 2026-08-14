export type TransportTitleType =
  | 'SINGLE_TRIP'
  | 'MULTI_TRIP'
  | 'TIME_PASS'
  | 'SMART_BALANCE';

export type CompensatoryDeliveryMethod = 'PHYSICAL_DEVICE' | 'DIGITAL_WALLET';

export interface TransportTitle {
  id: number;
  code: string;
  name: string;
  description: string | null;
  type: TransportTitleType;
  basePrice: number;
  pricePerStation: number;
  pricePerTrip: number;
  pricePerDay: number;
  minTrips: number | null;
  maxTrips: number | null;
  minDays: number | null;
  maxDays: number | null;
  minRechargeAmount: number | null;
  maxRechargeAmount: number | null;
  requiresOriginDestination: boolean;
  usesTripBalance: boolean;
  usesDayValidity: boolean;
  usesMoneyBalance: boolean;
  rechargeable: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TransportTitleSummary {
  totalTitles: number;
  filteredTitles: number;
  activeTitles: number;
  inactiveTitles: number;
  byType: Record<TransportTitleType, number>;
}

export interface TransportTitlesResponse {
  currency: string;
  summary: TransportTitleSummary;
  titles: TransportTitle[];
}

export interface CompensatoryTicketIssuanceRequest {
  deviceCode?: string;
  deliveryMethod: CompensatoryDeliveryMethod;
  passengerPublicId?: string;
  reason: string;
  originStationCode?: string;
  destinationStationCode?: string;
  trips?: number;
  days?: number;
  balanceAmount?: number;
}

export interface CompensatoryTicketIssuanceResponse {
  id: number;
  code: string;
  status: 'REQUESTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  simulated: boolean;
  ticketCode: string | null;
  qrToken: string | null;
  productCode: string;
  productType: TransportTitleType;
  deliveryMethod: CompensatoryDeliveryMethod;
  deviceCode: string | null;
  deviceName: string | null;
  stationCode: string | null;
  stationName: string | null;
  passengerPublicId: string | null;
  passengerEmail: string | null;
  operatorUsername: string;
  chargedAmount: number;
  requestedAt: string;
  completedAt: string | null;
}
