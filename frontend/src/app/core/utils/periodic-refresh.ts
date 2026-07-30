import { defer, finalize, Observable } from 'rxjs';

export class PeriodicRefresh {
  private intervalId: number | null = null;
  private requestInFlight = false;
  private listeningForVisibility = false;
  private readonly handleVisibilityChange = () => {
    if (!this.enabled) { return; }
    if (document.visibilityState === 'hidden') {
      this.stopTimer();
      return;
    }
    this.refresh();
    this.scheduleTimer();
  };

  enabled = true;

  constructor(
    private readonly intervalMs: number,
    private readonly refresh: () => void
  ) {}

  start(): void {
    this.stopTimer();
    if (!this.enabled) { return; }
    this.listenForVisibility();
    if (document.visibilityState !== 'hidden') {
      this.scheduleTimer();
    }
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
    if (this.listeningForVisibility) {
      document.removeEventListener('visibilitychange', this.handleVisibilityChange);
      this.listeningForVisibility = false;
    }
  }

  private listenForVisibility(): void {
    if (this.listeningForVisibility) { return; }
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    this.listeningForVisibility = true;
  }

  private scheduleTimer(): void {
    this.stopTimer();
    this.intervalId = window.setInterval(() => this.refresh(), this.intervalMs);
  }

  private stopTimer(): void {
    if (this.intervalId !== null) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }
}
