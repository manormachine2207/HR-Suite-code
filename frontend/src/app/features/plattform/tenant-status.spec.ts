import { describe, it, expect } from 'vitest';
import { tenantStatusKey, tenantStatusClass, allowedStatusActions } from './tenant-status';

describe('tenant-status', () => {
  it('maps known statuses to i18n keys, unknown to null', () => {
    expect(tenantStatusKey('ACTIVE')).toBe('plattform.status.active');
    expect(tenantStatusKey('ARCHIVED')).toBe('plattform.status.archived');
    expect(tenantStatusKey('WHATEVER')).toBeNull();
  });

  it('maps statuses to badge classes', () => {
    expect(tenantStatusClass('SUSPENDED')).toBe('is-suspended');
    expect(tenantStatusClass('WHATEVER')).toBe('is-unknown');
  });

  it('offers suspend + archive for ACTIVE', () => {
    expect(allowedStatusActions('ACTIVE').map(a => a.target)).toEqual(['SUSPENDED', 'ARCHIVED']);
  });

  it('offers activate + archive for SUSPENDED and ONBOARDING', () => {
    expect(allowedStatusActions('SUSPENDED').map(a => a.target)).toEqual(['ACTIVE', 'ARCHIVED']);
    expect(allowedStatusActions('ONBOARDING').map(a => a.target)).toEqual(['ACTIVE', 'ARCHIVED']);
  });

  it('offers no action for the terminal ARCHIVED status', () => {
    expect(allowedStatusActions('ARCHIVED')).toEqual([]);
  });
});
