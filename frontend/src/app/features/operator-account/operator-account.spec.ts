import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { OperatorAccount } from '../../core/models/operator-auth.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { OperatorAccountPage } from './operator-account';

describe('OperatorAccountPage', () => {
  it('should render the authenticated operator without exposing credentials', async () => {
    await TestBed.configureTestingModule({
      imports: [OperatorAccountPage],
      providers: [
        provideRouter([]),
        {
          provide: OperatorAuthService,
          useValue: { currentOperator: signal<OperatorAccount | null>(operator) }
        }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(OperatorAccountPage);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent?.trim()).toBe('Mi cuenta');
    expect(compiled.querySelector('.operator-avatar')?.textContent?.trim()).toBe('IA');
    expect(compiled.querySelector('.profile-heading')?.textContent).toContain('Ivan Administrador');
    expect(compiled.querySelector('.identity-line')?.textContent).toContain('@admin');
    expect(compiled.querySelector('.identity-line')?.textContent).toContain('admin@macegocia.local');
    expect(compiled.querySelector('.security-grid')?.textContent).toContain('Administrador');
    expect(compiled.querySelector('.security-grid')?.textContent).toContain('Activa');
    expect(compiled.querySelector('a')?.getAttribute('href')).toBe('/dashboard');
    expect(compiled.textContent?.toLowerCase()).not.toContain('secure-password');
    expect(compiled.textContent?.toLowerCase()).not.toContain('passwordhash');
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
  lastLoginAt: '2026-07-30T10:00:00',
  createdAt: '2026-07-30T09:00:00'
};
