import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { OperatorAccount } from '../../core/models/operator-auth.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  const authService = { login: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  beforeEach(async () => {
    authService.login.mockReset();
    router.navigateByUrl.mockReset();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: OperatorAuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  it('should reject an incomplete form without contacting the backend', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const marks = compiled.querySelectorAll<HTMLElement>(
      '.network-mark, .mobile-mark'
    );

    expect(Array.from(marks).map((mark) => mark.textContent?.trim())).toEqual(['M', 'M']);
    fixture.componentInstance.submit();

    expect(authService.login).not.toHaveBeenCalled();
    expect(fixture.componentInstance.form.controls.identifier.touched).toBe(true);
    expect(fixture.componentInstance.form.controls.password.touched).toBe(true);
  });

  it('should always open the general dashboard after a successful login', () => {
    authService.login.mockReturnValue(of(operator));
    fixture.componentInstance.form.setValue({
      identifier: ' admin ',
      password: 'secure-password'
    });

    fixture.componentInstance.submit();

    expect(authService.login).toHaveBeenCalledWith({
      identifier: 'admin',
      password: 'secure-password'
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
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
