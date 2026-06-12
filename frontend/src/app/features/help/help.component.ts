import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { TourService } from '../../core/tour/tour.service';

/**
 * Help center on /hilfe (ADR-015 Ebene 2): role-based step-by-step guides as
 * native details/summary accordions (keyboard + aria-expanded built in,
 * SDR-003) plus the entry points for the guided tours (Ebene 3).
 *
 * The guide content is pure i18n (BDR-005) — the template only orders keys, so
 * a translator can rework the manuals without touching code. Guides are data
 * (id + step count) to keep the four-language key blocks in lockstep.
 *
 * Builder tour from here (documented decision, ADR-015): the tour needs the
 * builder DOM and a live FormDesignerComponent as section-switching host — but
 * /hilfe always REPLACES the designer page, so "start it if a designer page is
 * open" is structurally never true from this screen. The conditional therefore
 * collapses to its else-branch: the button reveals a translated hint pointing
 * to the "Tour" button next to the builder stepper. Alternatives rejected:
 * deep-linking (into which Antragstyp?) and a cross-navigation "pending tour"
 * flag (hidden state, fragile against the lazy-loaded designer's load timing).
 */
@Component({
  selector: 'app-help',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './help.component.html',
  styleUrl: './help.component.scss',
})
export class HelpComponent {
  private readonly router = inject(Router);
  private readonly tour = inject(TourService);

  /** Accordion guides: i18n block id × number of steps (keys step1..stepN). */
  readonly guides: ReadonlyArray<{ id: string; steps: number[] }> = [
    { id: 'designer', steps: range(7) },
    { id: 'applicant', steps: range(5) },
    { id: 'reviewer', steps: range(5) },
  ];

  /** True after the builder-tour button was used outside a designer page. */
  readonly builderHintVisible = signal(false);

  startDashboardTour(): void {
    // Navigate home first; the tour waits until the dashboard has rendered
    // (lazy route + zoneless — TourService polls for the first tour anchor).
    void this.router.navigateByUrl('/').then(() => this.tour.startDashboardTourWhenReady());
  }

  startBuilderTour(): void {
    // Never on a designer page here (see class doc) — show the pointer hint.
    this.builderHintVisible.set(true);
  }
}

/** [1..n] — template helper for the numbered guide steps. */
function range(n: number): number[] {
  return Array.from({ length: n }, (_, i) => i + 1);
}
