export interface NetworkJourneyResponse {
  origin: NetworkJourneyStation;
  destination: NetworkJourneyStation;
  stationCount: number;
  transferCount: number;
  estimatedDurationSeconds: number;
  stations: NetworkJourneyStation[];
  segments: NetworkJourneySegment[];
}

export interface NetworkJourneyStation {
  id: number;
  code: string;
  name: string;
}

export interface NetworkJourneySegment {
  lineId: number;
  lineCode: string;
  lineName: string;
  lineColor: string;
  origin: NetworkJourneyStation;
  destination: NetworkJourneyStation;
  stopCount: number;
  travelSeconds: number;
  stations: NetworkJourneyStation[];
}
