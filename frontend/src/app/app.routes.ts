import { Routes } from '@angular/router';

import { MainLayout } from './layout/main-layout/main-layout';
import { PlaceholderPage } from './shared/placeholder-page/placeholder-page';

export const routes: Routes = [
  {
    path: '',
    component: MainLayout,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard'
      },
      {
        path: 'dashboard',
        component: PlaceholderPage,
        data: {
          title: 'Panel General',
          description: 'El resumen operativo de la red se incorporará en la siguiente fase.'
        }
      },
      {
        path: 'network-map',
        component: PlaceholderPage,
        data: {
          title: 'Mapa de red',
          description: 'El mapa interactivo se incorporará en su fase de desarrollo.'
        }
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
