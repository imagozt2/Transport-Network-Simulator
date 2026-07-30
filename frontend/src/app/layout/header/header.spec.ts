import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { OperatorAccount } from '../../core/models/operator-auth.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { Header } from './header';

describe('Header operator access', () => {
  const logout = vi.fn();

  beforeEach(async () => {
    logout.mockReset();
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        {
          provide: OperatorAuthService,
          useValue: {
            currentOperator: signal<OperatorAccount | null>(operator),
            logout
          }
        }
      ]
    }).compileComponents();
  });

  it('should expose account and settings from the operator menu', () => {
    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const userButton = compiled.querySelector('.user-button') as HTMLButtonElement;
    userButton.click();
    fixture.detectChanges();
    const links = compiled.querySelectorAll<HTMLAnchorElement>('.user-menu a');

    expect(userButton.getAttribute('aria-expanded')).toBe('true');
    expect(Array.from(links).map((link) => link.getAttribute('href')))
      .toEqual(['/account', '/settings']);
  });

  it.each([
    { result: () => of(undefined), description: 'success' },
    { result: () => throwError(() => new Error('network error')), description: 'failure' }
  ])('should return to login after logout $description', ({ result }) => {
    logout.mockReturnValue(result());
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();

    fixture.componentInstance.logout();

    expect(logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(fixture.componentInstance.userMenuOpen()).toBe(false);
    expect(fixture.componentInstance.loggingOut()).toBe(false);
  });
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
