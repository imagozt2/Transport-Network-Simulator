import { of, Subject } from 'rxjs';

import { PeriodicRefresh } from './periodic-refresh';

describe('PeriodicRefresh', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('should run at its configured interval and stop when destroyed', () => {
    const refresh = vi.fn();
    const periodicRefresh = new PeriodicRefresh(5_000, refresh);

    periodicRefresh.start();
    vi.advanceTimersByTime(10_000);
    expect(refresh).toHaveBeenCalledTimes(2);

    periodicRefresh.destroy();
    vi.advanceTimersByTime(10_000);
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('should pause and resume without changing its interval', () => {
    const refresh = vi.fn();
    const periodicRefresh = new PeriodicRefresh(5_000, refresh);

    periodicRefresh.start();
    periodicRefresh.toggle();
    vi.advanceTimersByTime(5_000);
    expect(refresh).not.toHaveBeenCalled();

    periodicRefresh.toggle();
    vi.advanceTimersByTime(5_000);
    expect(refresh).toHaveBeenCalledOnce();
    periodicRefresh.destroy();
  });

  it('should reject overlapping requests and unlock after completion', () => {
    const pending = new Subject<number>();
    const periodicRefresh = new PeriodicRefresh(5_000, vi.fn());

    periodicRefresh.request(() => pending)?.subscribe();
    expect(periodicRefresh.request(() => of(2))).toBeNull();

    pending.complete();
    const nextRequest = periodicRefresh.request(() => of(3));
    expect(nextRequest).not.toBeNull();
    nextRequest?.subscribe();
  });

  it('should pause while the tab is hidden and refresh when it becomes visible', () => {
    let visibilityState: DocumentVisibilityState = 'visible';
    const visibilitySpy = vi.spyOn(document, 'visibilityState', 'get')
      .mockImplementation(() => visibilityState);
    const refresh = vi.fn();
    const periodicRefresh = new PeriodicRefresh(5_000, refresh);

    periodicRefresh.start();
    vi.advanceTimersByTime(5_000);
    expect(refresh).toHaveBeenCalledOnce();

    visibilityState = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(10_000);
    expect(refresh).toHaveBeenCalledOnce();

    visibilityState = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(refresh).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(5_000);
    expect(refresh).toHaveBeenCalledTimes(3);

    periodicRefresh.destroy();
    visibilitySpy.mockRestore();
  });
});
