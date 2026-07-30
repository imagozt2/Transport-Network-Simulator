import { Component, HostListener, inject, input, output, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { OperatorAuthService } from '../../core/services/operator-auth.service';

@Component({
  selector: 'app-header',
  imports: [RouterLink],
  templateUrl: './header.html',
  styleUrl: './header.css'
})
export class Header {
  private readonly authService = inject(OperatorAuthService);
  private readonly router = inject(Router);

  readonly menuToggle = output<void>();
  readonly sidebarOpen = input(false);
  readonly operator = this.authService.currentOperator;
  readonly userMenuOpen = signal(false);
  readonly loggingOut = signal(false);

  toggleUserMenu(event: Event): void {
    event.stopPropagation();
    this.userMenuOpen.update((open) => !open);
  }

  keepUserMenuOpen(event: Event): void {
    event.stopPropagation();
  }

  closeUserMenu(): void {
    this.userMenuOpen.set(false);
  }

  logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    this.authService.logout().subscribe({
      next: () => this.finishLogout(),
      error: () => this.finishLogout(),
      complete: () => this.loggingOut.set(false)
    });
  }

  private finishLogout(): void {
    this.closeUserMenu();
    this.loggingOut.set(false);
    void this.router.navigateByUrl('/login');
  }

  initials(firstName: string, lastName: string): string {
    return `${firstName.charAt(0)}${lastName.charAt(0)}`.toLocaleUpperCase('es');
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeUserMenu();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeUserMenu();
  }
}
