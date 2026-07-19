import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { HealthService } from './core/services/health.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly healthService = inject(HealthService);

  protected readonly connectionStatus = signal<'checking' | 'connected' | 'error'>('checking');
  protected readonly databaseStatus = signal<'unknown' | 'connected' | 'error'>('unknown');

  ngOnInit(): void {
    this.checkConnection();
  }

  protected checkConnection(): void {
    this.connectionStatus.set('checking');
    this.databaseStatus.set('unknown');

    this.healthService.check().subscribe({
      next: (response) => {
        this.connectionStatus.set(response.status === 'UP' ? 'connected' : 'error');
        this.databaseStatus.set(response.database === 'UP' ? 'connected' : 'error');
      },
      error: (error) => {
        this.connectionStatus.set(error.status === 503 ? 'connected' : 'error');
        this.databaseStatus.set('error');
      }
    });
  }
}
