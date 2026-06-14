import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { SmtpComponent } from './smtp.component';
import { PlattformService } from '../plattform.service';
import { SmtpRelay } from '../smtp.model';

registerLocaleData(localeDeCh);

const relay = (over: Partial<SmtpRelay> = {}): SmtpRelay => ({
  host: 'mailpit', port: 1025, security: 'NONE', fromAddress: 'no-reply@hr-suite.local',
  fromName: null, username: null, passwordRef: null, secretConfigured: false,
  enabled: false, verified: false, lastTestAt: null, lastTestOutcome: null, ...over,
});

describe('SmtpComponent', () => {
  let service: { getSmtp: ReturnType<typeof vi.fn>; updateSmtp: ReturnType<typeof vi.fn>; testSmtp: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = { getSmtp: vi.fn(), updateSmtp: vi.fn(), testSmtp: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [SmtpComponent, TranslateModule.forRoot()],
      providers: [{ provide: PlattformService, useValue: service }],
    }).compileComponents();
  });

  it('loads the config into the form (no password field, only the reference)', async () => {
    service.getSmtp.mockReturnValue(of(relay({ passwordRef: 'HRSUITE_SMTP_PASSWORD', secretConfigured: true })));
    const fixture = TestBed.createComponent(SmtpComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(fixture.componentInstance.form.controls['host'].value).toBe('mailpit');
    expect(fixture.componentInstance.form.controls['passwordRef'].value).toBe('HRSUITE_SMTP_PASSWORD');
    // No raw password input exists anywhere (SDR-004).
    expect(el.querySelector('input[type="password"]')).toBeNull();
  });

  it('shows the operators-only notice on 403', async () => {
    service.getSmtp.mockReturnValue(throwError(() => ({ status: 403 })));
    const fixture = TestBed.createComponent(SmtpComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.forbidden).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="note"]')).not.toBeNull();
  });

  it('runs a test send and records the result', async () => {
    service.getSmtp.mockReturnValue(of(relay()));
    service.testSmtp.mockReturnValue(of({ success: true, outcome: 'success' }));
    const fixture = TestBed.createComponent(SmtpComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.testForm.controls['to'].setValue('admin@example.ch');
    fixture.componentInstance.test();

    expect(service.testSmtp).toHaveBeenCalledWith('admin@example.ch');
    expect(fixture.componentInstance.testResult?.success).toBe(true);
  });
});
