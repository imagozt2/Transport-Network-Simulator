import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { OperatorAuthService } from '../services/operator-auth.service';

export const operatorAuthGuard: CanActivateFn = (_, state) => {
  const authService = inject(OperatorAuthService);
  const router = inject(Router);

  return authService.ensureSession().pipe(
    map((operator) => operator
      ? true
      : router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url }
        }))
  );
};

export const guestOperatorGuard: CanActivateFn = () => {
  const authService = inject(OperatorAuthService);
  const router = inject(Router);

  return authService.ensureSession().pipe(
    map((operator) => operator ? router.createUrlTree(['/dashboard']) : true)
  );
};
