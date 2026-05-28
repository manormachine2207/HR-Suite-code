import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { initAxeDev } from './app/core/a11y/axe.bootstrap';

bootstrapApplication(App, appConfig)
  .then(() => initAxeDev())
  .catch((err) => console.error(err));
