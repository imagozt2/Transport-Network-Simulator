import { Component, HostListener, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { OperatorDisplayPreferencesService } from '../../core/services/operator-display-preferences.service';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-main-layout',
  imports: [Header, RouterOutlet, Sidebar],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.css'
})
export class MainLayout implements OnInit {
  private readonly displayPreferences = inject(OperatorDisplayPreferencesService);
  protected readonly sidebarOpen = signal(false);

  ngOnInit(): void {
    this.displayPreferences.load().subscribe({ error: () => undefined });
  }

  protected toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  protected closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  protected closeSidebarWithKeyboard(): void {
    this.closeSidebar();
  }
}
