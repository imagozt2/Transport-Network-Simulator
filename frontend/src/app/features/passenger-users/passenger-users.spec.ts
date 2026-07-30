import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import {
  PassengerAccount,
  PassengerAccountsPage
} from '../../core/models/passenger-account.model';
import { OperatorAccount } from '../../core/models/operator-auth.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { PassengerAccountsService } from '../../core/services/passenger-accounts.service';
import { PassengerUsers } from './passenger-users';

describe('PassengerUsers', () => {
  let fixture: ComponentFixture<PassengerUsers>;
  const accountsService = {
    getAccounts: vi.fn(),
    getAccount: vi.fn(),
    updateStatus: vi.fn()
  };

  beforeEach(async () => {
    accountsService.getAccounts.mockReset().mockReturnValue(of(page));
    accountsService.getAccount.mockReset().mockReturnValue(of(passenger));
    accountsService.updateStatus.mockReset().mockReturnValue(of({
      ...passenger,
      status: 'BLOCKED'
    }));
    const currentOperator = signal<OperatorAccount | null>(administrator);

    await TestBed.configureTestingModule({
      imports: [PassengerUsers],
      providers: [
        { provide: PassengerAccountsService, useValue: accountsService },
        {
          provide: OperatorAuthService,
          useValue: { currentOperator: currentOperator.asReadonly() }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PassengerUsers);
    fixture.detectChanges();
  });

  it('should render summaries and safe passenger information', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.summary-grid')?.textContent).toContain('12');
    expect(compiled.querySelector('tbody')?.textContent).toContain('Ana García');
    expect(compiled.querySelector('tbody')?.textContent).toContain('ana@example.local');
    expect(compiled.textContent).not.toContain('password');
    expect(Array.from(compiled.querySelectorAll<HTMLTableCellElement>('thead th'))
      .every((heading) => heading.scope === 'col')).toBe(true);
  });

  it('should combine filters and restart pagination', () => {
    const component = fixture.componentInstance;
    component.search = ' ana ';
    component.selectedStatus = 'BLOCKED';
    component.selectedVerification = 'PENDING';
    component.selectedSort = 'name';
    component.selectedDirection = 'ASC';

    component.applyFilters();

    expect(accountsService.getAccounts).toHaveBeenLastCalledWith(0, 20, {
      search: 'ana',
      status: 'BLOCKED',
      emailVerified: false,
      sortBy: 'name',
      direction: 'ASC'
    });
  });

  it('should load the detail and require a reason before disabling an account', () => {
    fixture.componentInstance.openDetail(passenger);

    expect(accountsService.getAccount).toHaveBeenCalledWith(passenger.publicId);
    expect(fixture.componentInstance.selectedUser).toEqual(passenger);
    expect(fixture.componentInstance.canManageAccounts()).toBe(true);

    fixture.componentInstance.beginStatusChange('DISABLED');
    expect(fixture.componentInstance.canConfirmStatusChange()).toBe(false);
    fixture.componentInstance.statusReason = 'Incumplimiento';
    expect(fixture.componentInstance.canConfirmStatusChange()).toBe(true);
    fixture.componentInstance.confirmStatusChange();

    expect(accountsService.updateStatus).toHaveBeenCalledWith(
      passenger.publicId,
      'DISABLED',
      'Incumplimiento'
    );
  });

  const administrator: OperatorAccount = {
    id: 1,
    username: 'admin',
    email: 'admin@macegocia.local',
    firstName: 'Admin',
    lastName: 'RMM',
    role: 'ADMINISTRATOR',
    status: 'ACTIVE',
    lastLoginAt: null,
    createdAt: null
  };

  const passenger: PassengerAccount = {
    publicId: '7dfd4685-8da2-4b9f-bf16-f641411ab174',
    email: 'ana@example.local',
    firstName: 'Ana',
    lastName: 'García',
    status: 'ACTIVE',
    emailVerified: true,
    emailVerifiedAt: '2026-07-20T10:00:00',
    lastLoginAt: '2026-07-29T10:00:00',
    registeredAt: '2026-07-19T10:00:00',
    updatedAt: '2026-07-20T10:00:00'
  };

  const page: PassengerAccountsPage = {
    summary: {
      totalAccounts: 12,
      activeAccounts: 8,
      blockedAccounts: 2,
      disabledAccounts: 2,
      pendingVerificationAccounts: 3
    },
    users: [passenger],
    page: 0,
    pageSize: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false
  };
});
