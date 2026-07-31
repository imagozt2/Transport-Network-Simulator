import { Component, HostListener, inject, OnInit } from '@angular/core';

import {
  PassengerAccount,
  PassengerAccountSort,
  PassengerAccountStatus,
  PassengerAccountSummary,
  SortDirection
} from '../../core/models/passenger-account.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { PassengerAccountsService } from '../../core/services/passenger-accounts.service';
import { formatDateTime } from '../../core/utils/temporal-formatters';

type OptionalStatus = PassengerAccountStatus | 'ALL';
type VerificationFilter = 'ALL' | 'VERIFIED' | 'PENDING';

@Component({
  selector: 'app-passenger-users',
  templateUrl: './passenger-users.html',
  styleUrls: [
    './passenger-users.css',
    './passenger-user-detail.css',
    './passenger-user-lifecycle.css'
  ]
})
export class PassengerUsers implements OnInit {
  private readonly passengerAccountsService = inject(PassengerAccountsService);
  private readonly operatorAuthService = inject(OperatorAuthService);

  users: PassengerAccount[] = [];
  summary: PassengerAccountSummary = {
    totalAccounts: 0,
    activeAccounts: 0,
    blockedAccounts: 0,
    disabledAccounts: 0,
    pendingVerificationAccounts: 0
  };
  loading = true;
  errorMessage = '';
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  firstPage = true;
  lastPage = true;
  selectedUser: PassengerAccount | null = null;
  detailLoading = false;
  detailError = '';
  pendingStatus: PassengerAccountStatus | null = null;
  statusReason = '';
  statusSubmitting = false;
  statusError = '';
  createDialogOpen = false;
  createFirstName = '';
  createLastName = '';
  createEmail = '';
  createPassword = '';
  createPasswordConfirmation = '';
  createSubmitting = false;
  createError = '';
  creationConfirmation = '';
  deleteConfirmationOpen = false;
  deleteSubmitting = false;
  deleteError = '';

  readonly operator = this.operatorAuthService.currentOperator;

  search = '';
  selectedStatus: OptionalStatus = 'ALL';
  selectedVerification: VerificationFilter = 'ALL';
  selectedSort: PassengerAccountSort = 'registeredAt';
  selectedDirection: SortDirection = 'DESC';

  readonly statuses: readonly PassengerAccountStatus[] = ['ACTIVE', 'BLOCKED', 'DISABLED'];
  readonly pageSizes = [20, 50, 100];

  ngOnInit(): void {
    this.loadUsers(0);
  }

  loadUsers(page = this.currentPage): void {
    this.loading = true;
    this.errorMessage = '';
    this.passengerAccountsService.getAccounts(
      page,
      this.pageSize,
      {
        search: this.search.trim() || undefined,
        status: this.selectedStatus === 'ALL' ? undefined : this.selectedStatus,
        emailVerified: this.verificationValue(),
        sortBy: this.selectedSort,
        direction: this.selectedDirection
      }
    ).subscribe({
      next: (response) => {
        this.users = response.users;
        this.summary = response.summary;
        this.currentPage = response.page;
        this.pageSize = response.pageSize;
        this.totalElements = response.totalElements;
        this.totalPages = response.totalPages;
        this.firstPage = response.first;
        this.lastPage = response.last;
        this.loading = false;
      },
      error: () => {
        this.users = [];
        this.errorMessage = 'No se han podido cargar los usuarios de RMM App.';
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.loadUsers(0);
  }

  clearFilters(): void {
    this.search = '';
    this.selectedStatus = 'ALL';
    this.selectedVerification = 'ALL';
    this.selectedSort = 'registeredAt';
    this.selectedDirection = 'DESC';
    this.loadUsers(0);
  }

  hasActiveFilters(): boolean {
    return this.search.trim() !== ''
      || this.selectedStatus !== 'ALL'
      || this.selectedVerification !== 'ALL'
      || this.selectedSort !== 'registeredAt'
      || this.selectedDirection !== 'DESC';
  }

  setPageSize(value: string): void {
    this.pageSize = Number(value);
    this.loadUsers(0);
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.loadUsers(page);
    }
  }

  openDetail(user: PassengerAccount): void {
    this.selectedUser = user;
    this.detailLoading = true;
    this.detailError = '';
    this.cancelStatusChange();
    this.passengerAccountsService.getAccount(user.publicId).subscribe({
      next: (account) => {
        this.selectedUser = account;
        this.detailLoading = false;
      },
      error: () => {
        this.detailError = 'No se ha podido cargar el detalle de la cuenta.';
        this.detailLoading = false;
      }
    });
  }

  closeDetail(): void {
    if (this.statusSubmitting || this.deleteSubmitting) {
      return;
    }
    this.selectedUser = null;
    this.detailError = '';
    this.cancelStatusChange();
    this.cancelDeleteAccount();
  }

  canManageAccounts(): boolean {
    return this.operator()?.role === 'ADMINISTRATOR';
  }

  availableStatuses(user: PassengerAccount): PassengerAccountStatus[] {
    if (user.status === 'ACTIVE') {
      return ['BLOCKED', 'DISABLED'];
    }
    if (user.status === 'BLOCKED') {
      return ['ACTIVE', 'DISABLED'];
    }
    return ['ACTIVE'];
  }

  beginStatusChange(status: PassengerAccountStatus): void {
    this.pendingStatus = status;
    this.statusReason = '';
    this.statusError = '';
  }

  cancelStatusChange(): void {
    this.pendingStatus = null;
    this.statusReason = '';
    this.statusError = '';
  }

  statusChangeRequiresReason(): boolean {
    return this.pendingStatus === 'BLOCKED' || this.pendingStatus === 'DISABLED';
  }

  canConfirmStatusChange(): boolean {
    return this.pendingStatus !== null
      && !this.statusSubmitting
      && (!this.statusChangeRequiresReason() || this.statusReason.trim() !== '');
  }

  confirmStatusChange(): void {
    if (!this.selectedUser || !this.pendingStatus || !this.canConfirmStatusChange()) {
      return;
    }
    this.statusSubmitting = true;
    this.statusError = '';
    this.passengerAccountsService.updateStatus(
      this.selectedUser.publicId,
      this.pendingStatus,
      this.statusReason
    ).subscribe({
      next: (account) => {
        this.selectedUser = account;
        this.users = this.users.map((user) =>
          user.publicId === account.publicId ? account : user
        );
        this.statusSubmitting = false;
        this.cancelStatusChange();
        this.loadUsers(this.currentPage);
      },
      error: () => {
        this.statusError = 'No se ha podido cambiar el estado de la cuenta.';
        this.statusSubmitting = false;
      }
    });
  }

  openCreateDialog(): void {
    if (!this.canManageAccounts()) return;
    this.createDialogOpen = true;
    this.createFirstName = '';
    this.createLastName = '';
    this.createEmail = '';
    this.createPassword = '';
    this.createPasswordConfirmation = '';
    this.createError = '';
    this.creationConfirmation = '';
  }

  closeCreateDialog(): void {
    if (!this.createSubmitting) {
      this.createDialogOpen = false;
      this.createError = '';
    }
  }

  @HostListener('document:keydown.escape')
  closeActiveDialog(): void {
    if (this.createDialogOpen) {
      this.closeCreateDialog();
    } else if (this.deleteConfirmationOpen) {
      this.cancelDeleteAccount();
    }
  }

  passwordMeetsRequirements(): boolean {
    return this.createPassword.length >= 12
      && this.createPassword.length <= 72
      && /[a-z]/.test(this.createPassword)
      && /[A-Z]/.test(this.createPassword)
      && /[0-9]/.test(this.createPassword);
  }

  canCreateAccount(): boolean {
    return !this.createSubmitting
      && this.createFirstName.trim() !== ''
      && this.createLastName.trim() !== ''
      && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.createEmail.trim())
      && this.passwordMeetsRequirements()
      && this.createPassword === this.createPasswordConfirmation;
  }

  createAccount(): void {
    if (!this.canCreateAccount()) return;
    this.createSubmitting = true;
    this.createError = '';
    this.passengerAccountsService.createAccount({
      firstName: this.createFirstName.trim(),
      lastName: this.createLastName.trim(),
      email: this.createEmail.trim().toLowerCase(),
      password: this.createPassword
    }).subscribe({
      next: (account) => {
        this.createSubmitting = false;
        this.createDialogOpen = false;
        this.creationConfirmation = `Se ha creado la cuenta de ${account.firstName} ${account.lastName}.`;
        this.loadUsers(0);
      },
      error: (error) => {
        this.createSubmitting = false;
        this.createError = error.status === 409
          ? 'Ya existe una cuenta asociada a ese correo electrónico.'
          : 'No se ha podido crear la cuenta de pasajero.';
      }
    });
  }

  beginDeleteAccount(): void {
    this.cancelStatusChange();
    this.deleteConfirmationOpen = true;
    this.deleteError = '';
  }

  cancelDeleteAccount(): void {
    if (!this.deleteSubmitting) {
      this.deleteConfirmationOpen = false;
      this.deleteError = '';
    }
  }

  deleteAccount(): void {
    if (!this.selectedUser || this.deleteSubmitting) return;
    const account = this.selectedUser;
    this.deleteSubmitting = true;
    this.deleteError = '';
    this.passengerAccountsService.deleteAccount(account.publicId).subscribe({
      next: () => {
        const targetPage = this.users.length === 1 && this.currentPage > 0
          ? this.currentPage - 1 : this.currentPage;
        this.deleteSubmitting = false;
        this.selectedUser = null;
        this.deleteConfirmationOpen = false;
        this.creationConfirmation = `Se ha eliminado la cuenta de ${account.firstName} ${account.lastName}.`;
        this.loadUsers(targetPage);
      },
      error: () => {
        this.deleteSubmitting = false;
        this.deleteError = 'No se ha podido eliminar la cuenta de pasajero.';
      }
    });
  }

  statusActionLabel(status: PassengerAccountStatus): string {
    return {
      ACTIVE: 'Activar cuenta',
      BLOCKED: 'Bloquear cuenta',
      DISABLED: 'Desactivar cuenta'
    }[status];
  }

  statusLabel(status: PassengerAccountStatus): string {
    return {
      ACTIVE: 'Activa',
      BLOCKED: 'Bloqueada',
      DISABLED: 'Desactivada'
    }[status];
  }

  formatDate(value: string | null, emptyLabel = 'Sin accesos'): string {
    return formatDateTime(value, emptyLabel);
  }

  private verificationValue(): boolean | undefined {
    if (this.selectedVerification === 'VERIFIED') {
      return true;
    }
    if (this.selectedVerification === 'PENDING') {
      return false;
    }
    return undefined;
  }
}
