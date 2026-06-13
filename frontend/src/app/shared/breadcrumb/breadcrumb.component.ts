import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/**
 * One breadcrumb node. `labelKey` is an i18n key (translated in the template);
 * `link` is the routerLink target. The LAST node in a trail is the current page
 * and carries NO link → it renders as plain text with `aria-current="page"`
 * (SDR-003 / WCAG: don't link the page you're already on).
 */
export interface Crumb {
  readonly labelKey: string;
  readonly link?: string;
}

/**
 * Shared breadcrumb for every top-level module page (ADR-014 navigation
 * precision, 2026-06-12): the global top-tab bar is gone, the dashboard ('/') is
 * the sole hub, so EVERY module page needs a discoverable way back. This is the
 * single source for that trail — replace ad-hoc `.hr-crumbs`/`.hr-back` markup
 * with `<app-breadcrumb [trail]="…">`.
 *
 * The Dashboard root is prepended automatically (routerLink '/'), so callers only
 * pass the module-specific tail, e.g. `[{ labelKey: 'nav.antragstypen' }]` for the
 * Antragstypen list, or `[{ labelKey: 'nav.antragstypen', link: '/antragstypen' },
 * { labelKey: 'designer.crumb' }]` for a sub-page.
 *
 * a11y (SDR-003): rendered as `<nav aria-label>` with real `<a>` links and
 * `aria-current="page"` on the active (last) node. Separator "›" is decorative
 * (aria-hidden), the matching CSS lives next to the consuming pages via the shared
 * `.hr-crumbs` look.
 */
@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <nav class="hr-crumbs" [attr.aria-label]="'nav.breadcrumbAria' | translate">
      <ol>
        <li>
          <a routerLink="/">{{ 'nav.dashboard' | translate }}</a>
        </li>
        @for (crumb of trail; track crumb.labelKey; let last = $last) {
          <li>
            <span class="hr-crumbs__sep" aria-hidden="true">›</span>
            @if (!last && crumb.link) {
              <a [routerLink]="crumb.link">{{ crumb.labelKey | translate }}</a>
            } @else {
              <span aria-current="page">{{ crumb.labelKey | translate }}</span>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styleUrl: './breadcrumb.component.scss',
})
export class BreadcrumbComponent {
  /** Module-specific tail; Dashboard is prepended by the template. */
  @Input({ required: true }) trail: readonly Crumb[] = [];
}
