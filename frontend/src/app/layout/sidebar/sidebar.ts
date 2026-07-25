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
      title: 'Vista general',
      items: [
        { label: 'Panel General', route: '/dashboard', icon: '📊' },
        { label: 'Mapa de red', route: '/network-map', icon: '🗺️' }
      ]
    },
    {
      title: 'Red ferroviaria',
      items: [
        { label: 'Líneas', route: '/lines', icon: '🚇' },
        { label: 'Estaciones', route: '/stations', icon: '🚉' }
      ]
    },
    {
      title: 'Material móvil',
      items: [
        { label: 'Trenes', route: '/trains', icon: '🚆' },
        { label: 'Cocheras', route: '/depots', icon: '🏭' }
      ]
    },
    {
      title: 'Equipamiento',
      items: [
        { label: 'Máquinas', route: '/devices', icon: '🖥️' }
      ]
    },
    {
      title: 'Supervisión',
      items: [
        { label: 'Logs', route: '/logs', icon: '📋' }
      ]
    }
  ];
}
