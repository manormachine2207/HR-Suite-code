import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { UpperCasePipe } from '@angular/common';

import { AntragsTypService } from './antragstyp.service';
import { LANGS, Lang, LocaleMap } from '../form-designer/form-definition.model';
import { BreadcrumbComponent } from '../../shared/breadcrumb/breadcrumb.component';

type TitleGroup = FormGroup<{ [K in Lang]: FormControl<string> }>;

/**
 * Slug suggestion for the technical key from the (German) title: lowercase,
 * umlauts/diacritics transliterated, anything outside [a-z0-9] collapses to "-".
 * Pure so the transliteration rules stay unit-testable.
 */
export function suggestKey(title: string): string {
  return title
    .toLowerCase()
    .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

type CreateForm = FormGroup<{
  key: FormControl<string>;
  title: TitleGroup;
}>;

@Component({
  selector: 'app-antragstyp-create',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, RouterLink, UpperCasePipe, BreadcrumbComponent],
  templateUrl: './antragstyp-create.component.html',
  styleUrl: './antragstyp-create.component.scss',
})
export class AntragstypCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(AntragsTypService);
  private readonly router = inject(Router);
  // Zoneless: async subscribe callbacks must notify change detection explicitly,
  // otherwise the saving/error state never reaches the view (frozen submit button).
  private readonly cdr = inject(ChangeDetectorRef);

  readonly langs = LANGS;
  saving = false;
  /** i18n key of the current error (BDR-005 — translated in the template). */
  errorKey = '';

  readonly form: CreateForm = this.fb.group({
    key: this.fb.control('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9_-]+$/)] }),
    // German is the lead language and required up front; FR/IT/EN may follow any
    // time before publish (the publish gate enforces full parity, BDR-005).
    title: this.fb.group(
      Object.fromEntries(LANGS.map(l =>
        [l, this.fb.control('', { nonNullable: true, validators: l === 'de' ? [Validators.required] : [] })]
      )) as { [K in Lang]: FormControl<string> }
    ) as TitleGroup,
  }) as CreateForm;

  constructor() {
    // Suggest the technical key from the German title until the user takes over
    // (dirty = the key field was edited by hand; programmatic writes keep it pristine).
    this.titleControl('de').valueChanges.subscribe(value => {
      if (!this.form.controls.key.dirty) {
        this.form.controls.key.setValue(suggestKey(value), { emitEvent: false });
        this.cdr.markForCheck();
      }
    });
  }

  titleControl(lang: Lang): FormControl<string> {
    return (this.form.controls.title as TitleGroup).controls[lang];
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.errorKey = '';
    const key = this.form.controls.key.value;
    const rawTitle = this.form.controls.title.value as Record<string, string>;
    const title: LocaleMap = Object.fromEntries(
      Object.entries(rawTitle).filter(([, v]) => v && v.trim().length > 0));

    this.service.createAntragstyp(key, title).subscribe({
      next: (created) => {
        this.saving = false;
        this.cdr.markForCheck();
        this.router.navigate(['/antragstypen', created.id, 'designer']);
      },
      error: (e) => {
        this.saving = false;
        const status = (e as { status?: number })?.status;
        this.errorKey = status === 409
          ? 'antragstyp.create.error.conflict'
          : 'antragstyp.create.error.generic';
        this.cdr.markForCheck();
      },
    });
  }
}
