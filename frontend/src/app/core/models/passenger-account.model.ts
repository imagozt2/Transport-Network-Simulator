export type PassengerAccountStatus = 'ACTIVE' | 'BLOCKED' | 'DISABLED';
export type PassengerAccountSort = 'registeredAt' | 'name' | 'email' | 'status' | 'lastLoginAt';
export type SortDirection = 'ASC' | 'DESC';

export interface PassengerAccountSummary {
  totalAccounts: number;
  activeAccounts: number;
  blockedAccounts: number;
  disabledAccounts: number;
  pendingVerificationAccounts: number;
}

export interface PassengerAccount {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  status: PassengerAccountStatus;
  emailVerified: boolean;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  registeredAt: string;
  updatedAt: string;
}

export interface PassengerAccountsPage {
  summary: PassengerAccountSummary;
  users: PassengerAccount[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
