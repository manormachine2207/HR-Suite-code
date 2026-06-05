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
    path: 'antragstypen/neu',
    loadComponent: () =>
      import('./features/antragstyp/antragstyp-create.component').then(m => m.AntragstypCreateComponent),
  },
  {
    path: 'antragstypen/:id/designer',
    loadComponent: () =>
      import('./features/form-designer/form-designer.component').then(m => m.FormDesignerComponent),
  },
  {
    path: 'antraege',
    loadComponent: () =>
      import('./features/antrag/antrag-list.component').then(m => m.AntragListComponent),
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
