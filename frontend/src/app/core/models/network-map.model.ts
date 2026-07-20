export interface NetworkMapResponse { lines: NetworkMapLine[]; }

export interface NetworkMapLine {
  id: number;
  code: string;
  name: string;
  color: string;
  stations: NetworkMapStation[];
}

export interface NetworkMapStation {
  id: number;
  code: string;
  name: string;
  stationOrder: number;
}
