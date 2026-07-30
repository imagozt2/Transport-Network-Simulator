import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { OperatorAuthService } from '../../core/services/operator-auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly operatorAuthService = inject(OperatorAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  submitting = false;
  errorMessage = '';
  passwordVisible = false;

  readonly form = this.formBuilder.nonNullable.group({
    identifier: ['', [Validators.required, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(200)]]
  });

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.errorMessage = '';

    this.operatorAuthService.login({
      identifier: this.form.controls.identifier.value.trim(),
      password: this.form.controls.password.value
    }).subscribe({
      next: () => {
        this.submitting = false;
        void this.router.navigateByUrl(this.returnUrl());
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.messageFor(error);
        this.submitting = false;
      }
    });
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  private returnUrl(): string {
    const candidate = this.route.snapshot.queryParamMap.get('returnUrl');
    if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//')
        || candidate.startsWith('/login')) {
      return '/dashboard';
    }
    return candidate;
  }

  private messageFor(error: HttpErrorResponse): string {
    switch (error.status) {
      case 0:
        return 'No se ha podido conectar con el centro de control.';
      case 401:
        return 'El usuario, correo o contraseña no son correctos.';
      case 403:
        return 'La cuenta está desactivada. Contacta con un administrador.';
      case 423:
        return 'La cuenta está bloqueada temporalmente por varios intentos fallidos.';
      default:
        return 'No se ha podido iniciar sesión. Inténtalo de nuevo.';
    }
  }
}
