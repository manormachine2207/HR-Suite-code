import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, UpperCasePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { PlattformService } from '../plattform.service';
import { Tenant, TenantStatus } from '../tenant.model';
import { allowedStatusActions, tenantStatusClass, tenantStatusKey, TenantStatusAction } from '../tenant-status';
import { dateLocaleFor, resolveLocaleText } from '../../../core/i18n/locale-text';
import { LANGS, Lang } from '../../form-designer/form-definition.model';

/**
 * Mandanten tab of the Plattform module (ADR-019 Stufe 1): tenant list with
 * lifecycle actions + an inline create form. All calls are platform-admin guarded;
 * a 403 turns into a "platform operators only" state instead of an error.
 *
 * Zoneless discipline: every async subscribe callback that mutates view state calls
 * cdr.markForCheck().
 */
@Component({
  selector: 'app-mandanten',
  standalone: true,
  imports: [TranslateModule, ReactiveFormsModule, DatePipe, UpperCasePipe],
  templateUrl: './mandanten.component.html',
  styleUrl: './mandanten.component.scss',
})
export class MandantenComponent implements OnInit {
  private readonly service = inject(PlattformService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly fb = inject(FormBuilder);

  readonly langs = LANGS;

  tenants: Tenant[] = [];
  loading = true;
  failed = false;
  /** 403 → caller is not platform-admin; render the operators-only notice. */
  forbidden = false;
  dateLocale = dateLocaleFor('de');

  /** id of the row whose status change is in flight. */
  rowBusyId: string | null = null;
  errorKey = '';
  errorDetail = '';
  successKey = '';

  creating = false;
  saving = false;
  readonly form: FormGroup = this.fb.group({
    code: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9_-]+$/)] }),
    subdomain: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)] }),
    defaultLocale: this.fb.control('de', { nonNullable: true }),
    title: this.fb.group(
      Object.fromEntries(LANGS.map(l => [l, this.fb.control('', { nonNullable: true, validators: l === 'de' ? [Validators.required] : [] })])),
    ),
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
    this.service.listTenants().subscribe({
      next: tenants => {
        this.tenants = tenants;
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

  // --- status actions -----------------------------------------------------
  changeStatus(t: Tenant, action: TenantStatusAction): void {
    const name = this.displayName(t);
    const verb = this.translate.instant(action.labelKey);
    if (!confirm(this.translate.instant('plattform.confirmStatus', { verb, name }))) {
      return;
    }
    this.rowBusyId = t.id;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
    this.service.changeStatus(t.id, action.target as TenantStatus).subscribe({
      next: () => {
        this.rowBusyId = null;
        this.successKey = 'plattform.statusChanged';
        this.cdr.markForCheck();
        this.reload();
      },
      error: e => {
        this.rowBusyId = null;
        this.applyError(e, 'plattform.statusError');
        this.cdr.markForCheck();
      },
    });
  }

  // --- create -------------------------------------------------------------
  openCreate(): void {
    this.creating = true;
    this.errorKey = '';
    this.errorDetail = '';
    this.successKey = '';
  }

  cancelCreate(): void {
    this.creating = false;
    this.form.reset({ defaultLocale: 'de' });
    this.errorKey = '';
    this.errorDetail = '';
  }

  titleControl(lang: Lang) {
    return (this.form.controls['title'] as FormGroup).controls[lang];
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorKey = '';
    this.errorDetail = '';
    const raw = this.form.getRawValue() as {
      code: string; subdomain: string; defaultLocale: string; title: Record<string, string>;
    };
    const displayName = Object.fromEntries(
      Object.entries(raw.title).filter(([, v]) => v && v.trim().length > 0));
    this.service.createTenant({
      code: raw.code, subdomain: raw.subdomain, displayName, defaultLocale: raw.defaultLocale,
    }).subscribe({
      next: () => {
        this.saving = false;
        this.cancelCreate();
        this.successKey = 'plattform.createSuccess';
        this.cdr.markForCheck();
        this.reload();
      },
      error: e => {
        this.saving = false;
        const status = (e as { status?: number })?.status;
        this.errorKey = status === 409 ? 'plattform.createConflict' : 'plattform.createError';
        this.applyError(e, this.errorKey);
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

  // --- view helpers -------------------------------------------------------
  displayName(t: Tenant): string {
    return resolveLocaleText(t.displayName, this.translate.getCurrentLang() || 'de', t.code);
  }

  statusKey(status: string): string | null {
    return tenantStatusKey(status);
  }

  statusClass(status: string): string {
    return tenantStatusClass(status);
  }

  actionsFor(t: Tenant): readonly TenantStatusAction[] {
    return allowedStatusActions(t.status);
  }
}
