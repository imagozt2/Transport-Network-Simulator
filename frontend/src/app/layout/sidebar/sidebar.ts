import { Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface SidebarItem {
  label: string;
  route: string;
  icon: string;
  available: boolean;
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
        { label: 'Panel General', route: '/dashboard', icon: 'PG', available: true },
        { label: 'Mapa de red', route: '/network-map', icon: 'MR', available: true }
      ]
    },
    {
      title: 'Infraestructura',
      items: [
        { label: 'Líneas', route: '/lines', icon: 'LI', available: false },
        { label: 'Estaciones', route: '/stations', icon: 'ES', available: false },
        { label: 'Trenes', route: '/trains', icon: 'TR', available: false },
        { label: 'Cocheras', route: '/depots', icon: 'CO', available: false },
        { label: 'Máquinas', route: '/machines', icon: 'MA', available: false }
      ]
    },
    {
      title: 'Billetes',
      items: [
        { label: 'Billetes y tarifas', route: '/tickets-fares', icon: 'BT', available: false },
        { label: 'Validaciones', route: '/validations', icon: 'VA', available: false },
        { label: 'Trayectos', route: '/ticket-journeys', icon: 'TY', available: false }
      ]
    },
    {
      title: 'Operación',
      items: [
        { label: 'Logs operativos', route: '/operational-logs', icon: 'LO', available: false }
      ]
    }
  ];
}
