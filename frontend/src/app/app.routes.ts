import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent),
  },
  {
    // The Oblique master-layout logo links to /home; without this redirect that link
    // fell through to the wildcard 404 (whose escape link used to loop back to /home).
    path: 'home',
    redirectTo: '',
    pathMatch: 'full',
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
    // Antrags-Katalog (ADR-021): applicant-facing tile view of all LIVE types.
    // MUST be before 'antraege/:id' so the literal segment 'neu' is not swallowed by :id.
    path: 'antraege/neu',
    loadComponent: () =>
      import('./features/antrag/antrag-katalog.component').then(m => m.AntragKatalogComponent),
  },
  {
    // Detail page with the approval-chain stepper (Cut D).
    path: 'antraege/:id',
    loadComponent: () =>
      import('./features/antrag/antrag-detail.component').then(m => m.AntragDetailComponent),
  },
  {
    path: 'aufgaben',
    loadComponent: () =>
      import('./features/review/task-list.component').then(m => m.TaskListComponent),
  },
  {
    path: 'conformite',
    loadComponent: () =>
      import('./features/accessibility/conformite.component').then(m => m.ConformiteComponent),
  },
  {
    // Help center (ADR-015 Ebene 2) — also reachable via the header "?" entry.
    path: 'hilfe',
    loadComponent: () =>
      import('./features/help/help.component').then(m => m.HelpComponent),
  },
  {
    // Lohnrechner (ADR-018): purely client-side gross→net simulation — no backend
    // calls, no persistence, no personal data; rates 2025 live in SAETZE_2025.
    path: 'lohnrechner',
    loadComponent: () =>
      import('./features/lohnrechner/lohnrechner.component').then(m => m.LohnrechnerComponent),
  },
  {
    // Plattform-Management (ADR-019): platform-admin operator module — tenant
    // administration (Stufe 1) plus module-internal tabs for SSO/SMTP/Logging.
    path: 'plattform',
    loadComponent: () =>
      import('./features/plattform/plattform.component').then(m => m.PlattformComponent),
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(m => m.NotFoundComponent),
  },
];
