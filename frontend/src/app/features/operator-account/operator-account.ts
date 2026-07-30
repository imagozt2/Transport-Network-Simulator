import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { OperatorRole } from '../../core/models/operator-auth.model';
import { OperatorAuthService } from '../../core/services/operator-auth.service';
import { formatDateTime } from '../../core/utils/temporal-formatters';

@Component({
  selector: 'app-operator-account',
  imports: [RouterLink],
  templateUrl: './operator-account.html',
  styleUrl: './operator-account.css'
})
export class OperatorAccountPage {
  private readonly authService = inject(OperatorAuthService);

  readonly operator = this.authService.currentOperator;

  initials(firstName: string, lastName: string): string {
    return `${firstName.charAt(0)}${lastName.charAt(0)}`.toLocaleUpperCase('es');
  }

  roleLabel(role: OperatorRole): string {
    return role === 'ADMINISTRATOR' ? 'Administrador' : 'Operador';
  }

  formatDateTime(value: string | null, emptyLabel: string): string {
    return formatDateTime(value, emptyLabel);
  }
}
