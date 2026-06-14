import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { SsoComponent } from './sso.component';
import { PlattformService } from '../plattform.service';
import { OidcConfig } from '../sso.model';

const cfg = (over: Partial<OidcConfig> = {}): OidcConfig => ({
  issuer: null, clientId: null, clientSecretRef: null, scopes: 'openid profile email',
  redirectUri: null, tenantClaim: null, secretConfigured: false, enabled: false,
  updatedAt: null, ...over,
});

describe('SsoComponent', () => {
  let service: { getOidc: ReturnType<typeof vi.fn>; updateOidc: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = { getOidc: vi.fn(), updateOidc: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [SsoComponent, TranslateModule.forRoot()],
      providers: [{ provide: PlattformService, useValue: service }],
    }).compileComponents();
  });

  it('loads the config into the form with no raw secret input (SDR-004)', async () => {
    service.getOidc.mockReturnValue(of(cfg({
      issuer: 'https://idp', clientId: 'hr', clientSecretRef: 'HRSUITE_OIDC_CLIENT_SECRET', secretConfigured: true,
    })));
    const fixture = TestBed.createComponent(SsoComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(fixture.componentInstance.form.controls['issuer'].value).toBe('https://idp');
    expect(fixture.componentInstance.form.controls['clientSecretRef'].value).toBe('HRSUITE_OIDC_CLIENT_SECRET');
    // No raw secret/password input exists anywhere (SDR-004).
    expect(el.querySelector('input[type="password"]')).toBeNull();
  });

  it('shows the operators-only notice on 403', async () => {
    service.getOidc.mockReturnValue(throwError(() => ({ status: 403 })));
    const fixture = TestBed.createComponent(SsoComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.forbidden).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="note"]')).not.toBeNull();
  });

  it('refuses to mark active without issuer + clientId (mirrors backend guard), no API call', async () => {
    service.getOidc.mockReturnValue(of(cfg()));
    const fixture = TestBed.createComponent(SsoComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.form.patchValue({ issuer: '', clientId: '', enabled: true });
    fixture.componentInstance.save();

    expect(service.updateOidc).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorKey).toBe('plattform.sso.enableNeedsConfig');
  });

  it('saves the config carrying the secret reference, never a value', async () => {
    service.getOidc.mockReturnValue(of(cfg()));
    service.updateOidc.mockReturnValue(of(cfg({ issuer: 'https://idp', clientId: 'hr' })));
    const fixture = TestBed.createComponent(SsoComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.form.patchValue({
      issuer: 'https://idp', clientId: 'hr', clientSecretRef: 'HRSUITE_OIDC_CLIENT_SECRET', enabled: false,
    });
    fixture.componentInstance.save();

    expect(service.updateOidc).toHaveBeenCalledWith(expect.objectContaining({
      issuer: 'https://idp', clientId: 'hr', clientSecretRef: 'HRSUITE_OIDC_CLIENT_SECRET', enabled: false,
    }));
    expect(fixture.componentInstance.successKey).toBe('plattform.sso.saved');
  });
});
