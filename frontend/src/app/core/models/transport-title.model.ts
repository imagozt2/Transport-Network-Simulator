export type TransportTitleType =
  | 'SINGLE_TRIP'
  | 'MULTI_TRIP'
  | 'TIME_PASS'
  | 'SMART_BALANCE';

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
