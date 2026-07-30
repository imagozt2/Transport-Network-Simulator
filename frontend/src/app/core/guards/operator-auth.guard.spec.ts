import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';

import { OperatorAccount } from '../models/operator-auth.model';
import { OperatorAuthService } from '../services/operator-auth.service';
import { guestOperatorGuard, operatorAuthGuard } from './operator-auth.guard';

describe('operator authentication guards', () => {
  const authService = {
    ensureSession: vi.fn()
  };

  beforeEach(() => {
    authService.ensureSession.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OperatorAuthService, useValue: authService }
      ]
    });
  });

  it('should allow authenticated operators into protected routes', async () => {
    authService.ensureSession.mockReturnValue(of(operator));

    const result = await TestBed.runInInjectionContext(() =>
      firstValueFrom(operatorAuthGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/trains' } as RouterStateSnapshot
      ) as ReturnType<typeof of>)
    );

    expect(result).toBe(true);
  });

  it('should redirect anonymous users to login preserving the requested route', async () => {
    authService.ensureSession.mockReturnValue(of(null));
    const router = TestBed.inject(Router);

    const result = await TestBed.runInInjectionContext(() =>
      firstValueFrom(operatorAuthGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/trains' } as RouterStateSnapshot
      ) as ReturnType<typeof of>)
    );

    expect(router.serializeUrl(result as never)).toBe('/login?returnUrl=%2Ftrains');
  });

  it('should keep authenticated users away from the login screen', async () => {
    authService.ensureSession.mockReturnValue(of(operator));
    const router = TestBed.inject(Router);

    const result = await TestBed.runInInjectionContext(() =>
      firstValueFrom(guestOperatorGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/login' } as RouterStateSnapshot
      ) as ReturnType<typeof of>)
    );

    expect(router.serializeUrl(result as never)).toBe('/dashboard');
  });

  const operator: OperatorAccount = {
    id: 1,
    username: 'admin',
    email: 'admin@macegocia.local',
    firstName: 'Ivan',
    lastName: 'Administrador',
    role: 'ADMINISTRATOR',
    status: 'ACTIVE',
    lastLoginAt: null,
    createdAt: null
  };
});
