import { Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { APPLICATION_ROUTES } from '../../core/navigation/application-routes';
import { ActiveSectionService } from '../../core/services/active-section.service';

interface SidebarItem {
  label: string;
  route: string;
  icon: string;
}

interface SidebarSection {
  title: string;
  items: SidebarItem[];
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css'
})
export class Sidebar {
  private readonly activeSectionService = inject(ActiveSectionService);

  readonly open = input(false);
  readonly navigationSelected = output<void>();

  protected readonly isActiveRoute = (route: string): boolean =>
    this.activeSectionService.isActive(route);

  protected readonly sections: SidebarSection[] = [
    {
      title: 'Operación de red',
      items: [
        { label: 'Panel general', route: APPLICATION_ROUTES.dashboard, icon: '📊' },
        { label: 'Mapa de red', route: APPLICATION_ROUTES.networkMap, icon: '🗺️' },
        { label: 'Líneas', route: APPLICATION_ROUTES.lines, icon: '🚇' },
        { label: 'Estaciones', route: APPLICATION_ROUTES.stations, icon: '🚉' }
      ]
    },
    {
      title: 'Flota y equipamiento',
      items: [
        { label: 'Trenes', route: APPLICATION_ROUTES.trains, icon: '🚆' },
        { label: 'Cocheras', route: APPLICATION_ROUTES.depots, icon: '🏭' },
        { label: 'Máquinas', route: APPLICATION_ROUTES.devices, icon: '🖥️' }
      ]
    },
    {
      title: 'Gestión y supervisión',
      items: [
        { label: 'Títulos de transporte', route: APPLICATION_ROUTES.transportTitles, icon: '🎫' },
        { label: 'Usuarios', route: APPLICATION_ROUTES.users, icon: '👥' },
        { label: 'Incidencias', route: APPLICATION_ROUTES.incidents, icon: '⚠️' },
        { label: 'Logs', route: APPLICATION_ROUTES.logs, icon: '📋' }
      ]
    }
  ];
}
