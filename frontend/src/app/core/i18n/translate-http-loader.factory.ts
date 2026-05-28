import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

export const translateHttpLoaderFactory = () =>
  provideTranslateHttpLoader({
    prefix: 'assets/i18n/',
    suffix: '.json'
  });
