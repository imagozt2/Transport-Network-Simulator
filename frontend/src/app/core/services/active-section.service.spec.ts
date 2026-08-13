import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { ActiveSectionService } from './active-section.service';

describe('ActiveSectionService', () => {
  let router: Router;
  let service: ActiveSectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'dashboard', children: [] },
          { path: 'trains', children: [] }
        ])
      ]
    });

    router = TestBed.inject(Router);
    service = TestBed.inject(ActiveSectionService);
  });

  it('should derive the active section from the primary route', async () => {
    await router.navigateByUrl('/trains?lineCode=L2#fleet');

    expect(service.activeSection()).toBe('trains');
    expect(service.isActive('/trains')).toBe(true);
    expect(service.isActive('/dashboard')).toBe(false);
  });
});
