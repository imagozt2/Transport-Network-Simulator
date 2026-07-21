import { Routes } from '@angular/router';

import { Dashboard } from './features/dashboard/dashboard';
import { Lines } from './features/lines/lines';
import { NetworkMap } from './features/network-map/network-map';
import { MainLayout } from './layout/main-layout/main-layout';

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
        component: Dashboard
      },
      {
        path: 'network-map',
        component: NetworkMap
      },
      {
        path: 'lines',
        component: Lines
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
