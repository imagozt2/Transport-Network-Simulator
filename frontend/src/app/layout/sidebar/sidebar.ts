import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

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
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css'
})
export class Sidebar {
  readonly open = input(false);
  readonly navigationSelected = output<void>();

  protected readonly sections: SidebarSection[] = [
    {
      title: 'Operación de red',
      items: [
        { label: 'Panel general', route: '/dashboard', icon: '📊' },
        { label: 'Mapa de red', route: '/network-map', icon: '🗺️' },
        { label: 'Líneas', route: '/lines', icon: '🚇' },
        { label: 'Estaciones', route: '/stations', icon: '🚉' }
      ]
    },
    {
      title: 'Flota y equipamiento',
      items: [
        { label: 'Trenes', route: '/trains', icon: '🚆' },
        { label: 'Cocheras', route: '/depots', icon: '🏭' },
        { label: 'Máquinas', route: '/devices', icon: '🖥️' }
      ]
    },
    {
      title: 'Gestión y supervisión',
      items: [
        { label: 'Títulos de transporte', route: '/transport-titles', icon: '🎫' },
        { label: 'Usuarios', route: '/users', icon: '👥' },
        { label: 'Logs', route: '/logs', icon: '📋' }
      ]
    }
  ];
}
