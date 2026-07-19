export interface HealthResponse {
  status: 'UP' | 'DEGRADED';
  database: 'UP' | 'DOWN';
  timestamp: string;
}
