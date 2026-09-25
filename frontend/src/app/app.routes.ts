import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/week').then((m) => m.WeekPage) },
  { path: 'profile', loadComponent: () => import('./pages/profile').then((m) => m.ProfilePage) },
  { path: 'coach', loadComponent: () => import('./pages/coach').then((m) => m.CoachPage) },
  { path: 'settings', loadComponent: () => import('./pages/settings').then((m) => m.SettingsPage) },
  { path: 'favorites', loadComponent: () => import('./pages/favorites').then((m) => m.FavoritesPage) },
  { path: '**', redirectTo: '' },
];
