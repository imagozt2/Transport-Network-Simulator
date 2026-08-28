import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

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
  let currentOperator: WritableSignal<OperatorAccount | null>;
  const accountsService = {
    getAccounts: vi.fn(),
    getAccount: vi.fn(),
    updateStatus: vi.fn(),
    createAccount: vi.fn(),
    deleteAccount: vi.fn()
  };

  beforeEach(async () => {
    accountsService.getAccounts.mockReset().mockReturnValue(of(page));
    accountsService.getAccount.mockReset().mockReturnValue(of(passenger));
    accountsService.updateStatus.mockReset().mockReturnValue(of({
      ...passenger,
      status: 'BLOCKED'
    }));
    accountsService.createAccount.mockReset().mockReturnValue(of(passenger));
    accountsService.deleteAccount.mockReset().mockReturnValue(of(undefined));
    currentOperator = signal<OperatorAccount | null>(administrator);

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

  it('should validate and create a passenger before refreshing the first page', () => {
    const component = fixture.componentInstance;
    component.openCreateDialog();
    component.createFirstName = ' Ana ';
    component.createLastName = ' García ';
    component.createEmail = ' ANA@EXAMPLE.LOCAL ';
    component.createPassword = 'SecurePassword123';
    component.createPasswordConfirmation = 'SecurePassword123';

    expect(component.passwordMeetsRequirements()).toBe(true);
    expect(component.canCreateAccount()).toBe(true);
    component.createAccount();

    expect(accountsService.createAccount).toHaveBeenCalledWith({
      firstName: 'Ana', lastName: 'García', email: 'ana@example.local',
      password: 'SecurePassword123'
    });
    expect(accountsService.getAccounts).toHaveBeenLastCalledWith(
      0, 20, expect.objectContaining({ sortBy: 'registeredAt', direction: 'DESC' })
    );
    expect(component.createDialogOpen).toBe(false);
    expect(component.creationConfirmation).toContain('Ana García');
  });

  it('should create a passenger through the rendered form controls', async () => {
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelector<HTMLButtonElement>('.create-user-button')!.click();
    fixture.detectChanges();
    const submitButton = compiled.querySelector<HTMLButtonElement>(
      '.create-user-dialog button[type="submit"]'
    )!;
    expect(fixture.componentInstance.canCreateAccount()).toBe(false);

    setInput(compiled, '#create-passenger-first-name', 'Lucía');
    setInput(compiled, '#create-passenger-last-name', 'Martín');
    setInput(compiled, '#create-passenger-email', 'lucia@example.com');
    setInput(compiled, '#create-passenger-password', 'SecurePassword123');
    setInput(compiled, '#create-passenger-password-confirmation', 'SecurePassword123');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.createFirstName).toBe('Lucía');
    expect(fixture.componentInstance.createPasswordConfirmation).toBe('SecurePassword123');
    expect(fixture.componentInstance.canCreateAccount()).toBe(true);

    submitButton.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(accountsService.createAccount).toHaveBeenCalledWith({
      firstName: 'Lucía', lastName: 'Martín', email: 'lucia@example.com',
      password: 'SecurePassword123'
    });
    expect(compiled.querySelector('.lifecycle-confirmation')?.textContent)
      .toContain('Se ha creado la cuenta de Ana García');
  });

  it('should explain invalid fields instead of leaving the creation action inert', () => {
    const component = fixture.componentInstance;
    component.openCreateDialog();
    fixture.detectChanges(false);
    component.createAccount();
    fixture.detectChanges(false);

    expect(component.createValidationVisible).toBe(true);
    expect(component.createError).toContain('Revisa los campos');
    expect(accountsService.createAccount).not.toHaveBeenCalled();
  });

  it('should require confirmation and refresh after deleting a passenger', () => {
    const component = fixture.componentInstance;
    component.openDetail(passenger);
    component.beginDeleteAccount();

    expect(component.deleteConfirmationOpen).toBe(true);
    component.deleteAccount();

    expect(accountsService.deleteAccount).toHaveBeenCalledWith(passenger.publicId);
    expect(component.selectedUser).toBeNull();
    expect(accountsService.getAccounts).toHaveBeenCalledTimes(2);
    expect(component.creationConfirmation).toContain('eliminado');
  });

  it('should hide account mutations from regular operators', () => {
    currentOperator.set({ ...administrator, role: 'OPERATOR' });
    fixture.detectChanges();

    expect(fixture.componentInstance.canManageAccounts()).toBe(false);
    expect(fixture.nativeElement.querySelector('.create-user-button')).toBeNull();
    fixture.componentInstance.openCreateDialog();
    expect(fixture.componentInstance.createDialogOpen).toBe(false);
  });

  it('should expose a recoverable error and reload the passenger list', () => {
    accountsService.getAccounts.mockReturnValueOnce(
      throwError(() => new Error('backend unavailable'))
    );
    fixture.componentInstance.loadUsers(0);
    fixture.detectChanges(false);

    expect(fixture.componentInstance.errorMessage)
      .toContain('No se han podido cargar los usuarios');
    expect(fixture.componentInstance.users).toEqual([]);

    accountsService.getAccounts.mockReturnValueOnce(of(page));
    fixture.componentInstance.loadUsers(0);
    fixture.detectChanges(false);

    expect(fixture.componentInstance.errorMessage).toBe('');
    expect(fixture.nativeElement.querySelector('tbody')?.textContent).toContain('ana@example.local');
  });

  it('should retain responsive table, detail and form layouts', () => {
    const styles = loadedComponentStyles();

    expect(styles).toContain('@media (max-width: 1100px)');
    expect(styles).toContain('@media (max-width: 650px)');
    expect(styles).toMatch(/\.table-wrapper[^}]*overflow-x:\s*auto/);
    expect(styles).toMatch(/\.create-user-dialog[^}]*form[^}]*grid-template-columns:\s*1fr/);
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

function loadedComponentStyles(): string {
  return Array.from(document.head.querySelectorAll('style'))
    .map((style) => style.textContent ?? '')
    .join('\n');
}

function setInput(container: HTMLElement, selector: string, value: string): void {
  const input = container.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}
