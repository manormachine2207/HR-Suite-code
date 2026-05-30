import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent),
  },
  {
    path: 'antragstypen',
    loadComponent: () =>
      import('./features/antragstyp/antragstyp-list.component').then(m => m.AntragstypListComponent),
  },
  {
    path: 'antragstypen/:id/designer',
    loadComponent: () =>
      import('./features/form-designer/form-designer.component').then(m => m.FormDesignerComponent),
  },
  {
    path: 'conformite',
    loadComponent: () =>
      import('./features/accessibility/conformite.component').then(m => m.ConformiteComponent),
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(m => m.NotFoundComponent),
  },
];
