import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { PlattformService } from '../plattform.service';
import { SmtpRelay, SmtpSecurity } from '../smtp.model';
import { dateLocaleFor } from '../../../core/i18n/locale-text';

/**
 * SMTP relay tab of the Plattform module (ADR-019 Stufe 3). Configures the global
 * relay and runs a test send. SDR-004: the password is never shown or entered here —
 * the form takes the env-var REFERENCE name; `secretConfigured` shows whether that
 * variable is set. A 403 renders the operators-only notice.
 *
 * Zoneless: every async callback calls cdr.markForCheck().
 */
@Component({
  selector: 'app-smtp',
  standalone: true,
  imports: [TranslateModule, ReactiveFormsModule, DatePipe],
  templateUrl: './smtp.component.html',
  styleUrl: './smtp.component.scss',
})
export class SmtpComponent implements OnInit {
  private readonly service = inject(PlattformService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly fb = inject(FormBuilder);

  readonly securities: readonly SmtpSecurity[] = ['NONE', 'STARTTLS', 'TLS'];

  loading = true;
  failed = false;
  forbidden = false;
  saving = false;
  testing = false;
  dateLocale = dateLocaleFor('de');

  relay: SmtpRelay | null = null;
  errorKey = '';
  errorDetail = '';
  successKey = '';
  testResult: { success: boolean; outcome: string } | null = null;

  readonly form: FormGroup = this.fb.group({
    host: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    port: this.fb.control(1025, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(65535)] }),
    security: this.fb.control<SmtpSecurity>('NONE', { nonNullable: true }),
    fromAddress: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    fromName: this.fb.control('', { nonNullable: true }),
    username: this.fb.control('', { nonNullable: true }),
    passwordRef: this.fb.control('', { nonNullable: true }),
    enabled: this.fb.control(false, { nonNullable: true }),
  });

  readonly testForm: FormGroup = this.fb.group({
    to: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  constructor() {
    this.dateLocale = dateLocaleFor(this.translate.getCurrentLang() || 'de');
    this.translate.onLangChange.pipe(takeUntilDestroyed()).subscribe(e => {
      this.dateLocale = dateLocaleFor(e.lang);
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading = true;
    this.failed = false;
    this.forbidden = false;
    this.service.getSmtp().subscribe({
      next: relay => {
        this.relay = relay;
        this.form.patchValue({
          host: relay.host, port: relay.port, security: relay.security as SmtpSecurity,
          fromAddress: relay.fromAddress, fromName: relay.fromName ?? '',
          username: relay.username ?? '', passwordRef: relay.passwordRef ?? '',
          enabled: relay.enabled,
        });
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: e => {
        this.loading = false;
        if ((e as { status?: number })?.status === 403) {
          this.forbidden = true;
        } else {
          this.failed = true;
        }
        this.cdr.markForCheck();
      },
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.testResult = null;
    const v = this.form.getRawValue() as Record<string, unknown>;
    this.service.updateSmtp({
      host: v['host'] as string,
      port: v['port'] as number,
      security: v['security'] as SmtpSecurity,
      fromAddress: v['fromAddress'] as string,
      fromName: (v['fromName'] as string) || null,
      username: (v['username'] as string) || null,
      passwordRef: (v['passwordRef'] as string) || null,
      enabled: v['enabled'] as boolean,
    }).subscribe({
      next: relay => {
        this.relay = relay;
        this.saving = false;
        this.successKey = 'plattform.smtp.saved';
        this.cdr.markForCheck();
      },
      error: e => {
        this.saving = false;
        this.applyError(e, 'plattform.smtp.saveError');
        this.cdr.markForCheck();
      },
    });
  }

  test(): void {
    if (this.testForm.invalid) {
      this.testForm.markAllAsTouched();
      return;
    }
    this.testing = true;
    this.testResult = null;
    this.errorKey = '';
    this.errorDetail = '';
    this.service.testSmtp(this.testForm.controls['to'].value as string).subscribe({
      next: res => {
        this.testing = false;
        this.testResult = res;
        this.reload();   // refresh verified + lastTest fields
        this.cdr.markForCheck();
      },
      error: e => {
        this.testing = false;
        this.applyError(e, 'plattform.smtp.testError');
        this.cdr.markForCheck();
      },
    });
  }

  private applyError(e: unknown, fallbackKey: string): void {
    const detail = (e as { error?: { detail?: unknown } })?.error?.detail;
    if (typeof detail === 'string' && detail.trim().length > 0) {
      this.errorDetail = detail;
      this.errorKey = '';
    } else {
      this.errorDetail = '';
      this.errorKey = fallbackKey;
    }
  }
}
