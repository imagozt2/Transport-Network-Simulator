export type OperatorRole = 'OPERATOR' | 'ADMINISTRATOR';
export type OperatorAccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

export interface OperatorAccount {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: OperatorRole;
  status: OperatorAccountStatus;
  lastLoginAt: string | null;
  createdAt: string | null;
}

export interface OperatorLoginRequest {
  identifier: string;
  password: string;
}

export interface CsrfTokenResponse {
  headerName: string;
  parameterName: string;
  token: string;
}
