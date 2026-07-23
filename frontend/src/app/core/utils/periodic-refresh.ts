import { defer, finalize, Observable } from 'rxjs';

export class PeriodicRefresh {
  private intervalId: number | null = null;
  private requestInFlight = false;

  enabled = true;

  constructor(
    private readonly intervalMs: number,
    private readonly refresh: () => void
  ) {}

  start(): void {
    this.stopTimer();
    if (!this.enabled) { return; }
    this.intervalId = window.setInterval(() => this.refresh(), this.intervalMs);
  }

  toggle(): void {
    this.enabled = !this.enabled;
    if (this.enabled) { this.start(); } else { this.stopTimer(); }
  }

  request<T>(factory: () => Observable<T>): Observable<T> | null {
    if (this.requestInFlight) { return null; }
    this.requestInFlight = true;
    return defer(factory).pipe(finalize(() => { this.requestInFlight = false; }));
  }

  destroy(): void {
    this.stopTimer();
  }

  private stopTimer(): void {
    if (this.intervalId !== null) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }
}
