import { computed, inject, Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ActiveSectionService {
  private readonly router = inject(Router);
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  readonly activeSection = computed(() => this.sectionFromUrl(this.currentUrl()));

  isActive(route: string): boolean {
    return this.activeSection() === this.sectionFromUrl(route);
  }

  private sectionFromUrl(url: string): string {
    const primarySegments = this.router
      .parseUrl(url)
      .root.children['primary']
      ?.segments.map((segment) => segment.path);

    return primarySegments?.[0] ?? '';
  }
}
