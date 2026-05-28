import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent),
  },
  // {
  //   path: 'conformite',
  //   loadComponent: () =>
  //     import('./features/accessibility/conformite.component').then(m => m.ConformiteComponent),
  // },
  // Conformite route is added in FT8 (ConformiteComponent lands then).
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(m => m.NotFoundComponent),
  },
];
