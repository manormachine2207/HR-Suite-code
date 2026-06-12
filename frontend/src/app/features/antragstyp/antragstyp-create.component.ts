import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { UpperCasePipe } from '@angular/common';

import { AntragsTypService } from './antragstyp.service';
import { LANGS, Lang, LocaleMap } from '../form-designer/form-definition.model';

type TitleGroup = FormGroup<{ [K in Lang]: FormControl<string> }>;

type CreateForm = FormGroup<{
  key: FormControl<string>;
  title: TitleGroup;
}>;

@Component({
  selector: 'app-antragstyp-create',
  standalone: true,
  imports: [ReactiveFormsModule, TranslateModule, RouterLink, UpperCasePipe],
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
    title: this.fb.group(
      Object.fromEntries(LANGS.map(l => [l, this.fb.control('', { nonNullable: true })])) as { [K in Lang]: FormControl<string> }
    ) as TitleGroup,
  }) as CreateForm;

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
