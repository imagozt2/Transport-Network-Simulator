import { Routes } from '@angular/router';

import { Dashboard } from './features/dashboard/dashboard';
import { Depots } from './features/depots/depots';
import { Devices } from './features/devices/devices';
import { Lines } from './features/lines/lines';
import { Logs } from './features/logs/logs';
import { NetworkMap } from './features/network-map/network-map';
import { Stations } from './features/stations/stations';
import { Trains } from './features/trains/trains';
import { TransportTitles } from './features/transport-titles/transport-titles';
import { MainLayout } from './layout/main-layout/main-layout';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((module) => module.Login)
  },
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
      },
      {
        path: 'stations',
        component: Stations
      },
      {
        path: 'trains',
        component: Trains
      },
      {
        path: 'depots',
        component: Depots
      },
      {
        path: 'transport-titles',
        component: TransportTitles
      },
      {
        path: 'devices',
        component: Devices
      },
      {
        path: 'logs',
        component: Logs
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
