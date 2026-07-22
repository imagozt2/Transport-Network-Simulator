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
      title: 'Principal',
      items: [
        { label: 'Panel General', route: '/dashboard', icon: '📊' },
        { label: 'Mapa de red', route: '/network-map', icon: '🗺️' }
      ]
    },
    {
      title: 'Infraestructura',
      items: [
        { label: 'Líneas', route: '/lines', icon: 'LI' },
        { label: 'Estaciones', route: '/stations', icon: 'ES' },
        { label: 'Trenes', route: '/trains', icon: 'TR' },
        { label: 'Cocheras', route: '/depots', icon: 'CO' }
      ]
    }
  ];
}
