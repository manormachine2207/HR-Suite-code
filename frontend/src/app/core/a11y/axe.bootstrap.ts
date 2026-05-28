import { isDevMode } from '@angular/core';

export const initAxeDev = async (): Promise<void> => {
  if (!isDevMode()) {
    return;
  }
  const [{ default: axe }] = await Promise.all([
    import('axe-core'),
  ]);
  axe.run(document, {}, (err, results) => {
    if (err) {
      // eslint-disable-next-line no-console
      console.warn('[axe] could not run:', err);
      return;
    }
    if (results.violations.length === 0) {
      // eslint-disable-next-line no-console
      console.info('[axe] no accessibility violations detected');
    } else {
      // eslint-disable-next-line no-console
      console.warn('[axe] violations:', results.violations);
    }
  });
};
