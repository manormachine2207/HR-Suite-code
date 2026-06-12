import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { driver, Driver, DriveStep } from 'driver.js';

import {
  BuilderSection,
  TourStepDef,
  builderTourSteps,
  dashboardTourSteps,
  markDashboardTourSeen,
  shouldAutoStartDashboardTour,
} from './tour-definitions';

/**
 * Contract the builder component hands to the tour so steps can switch the
 * active section. The host MUST trigger change detection itself (markForCheck):
 * driver.js callbacks run outside Angular and the app is zoneless.
 */
export interface BuilderTourHost {
  showSection(section: BuilderSection): void;
}

/**
 * Wraps driver.js 1.4.0 (MIT, ADR-015 Ebene 3) behind an Angular service.
 *
 * - Step definitions are pure data (tour-definitions.ts) keyed by data-tour
 *   attributes; this service only adds driver.js mechanics + i18n resolution.
 * - All texts/buttons resolve via TranslateService.instant at start time — the
 *   APP_INITIALIZER guarantees translations are loaded before any view exists.
 * - Zoneless: driver.js manipulates the DOM outside Angular (overlay only, no
 *   bindings) — harmless. Steps that need another builder section route through
 *   the host's showSection() and advance after a short delay so Angular has
 *   rendered the section before the popover measures its target.
 * - Styling: global .hr-tour-popover overrides in styles.scss (design tokens).
 */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly translate = inject(TranslateService);
  private active: Driver | null = null;

  /** Manual start (help center / repeated visits). Also sets the seen flag. */
  startDashboardTour(): void {
    markDashboardTourSeen(localStorage);
    this.start(dashboardTourSteps());
  }

  /**
   * First-visit auto-start on the dashboard (localStorage-flagged, never again
   * afterwards). Returns true when the tour actually started.
   */
  maybeAutoStartDashboardTour(): boolean {
    if (!shouldAutoStartDashboardTour(localStorage)) {
      return false;
    }
    this.startDashboardTour();
    return true;
  }

  /**
   * Starts the dashboard tour once its first highlighted element exists in the
   * DOM — used by the help center, which navigates to '/' first and must wait
   * for the lazy-loaded dashboard to render (zoneless: no stable "after nav
   * render" signal exists across route boundaries, polling is the simple,
   * robust choice).
   */
  startDashboardTourWhenReady(): void {
    this.whenPresent('[data-tour="dashboard-search"]', 4000).then(() => this.startDashboardTour());
  }

  /** Manual start from the builder ("Tour" button next to the stepper). */
  startBuilderTour(host: BuilderTourHost): void {
    // Tours always start from the form section so step 1..3 targets exist.
    host.showSection('form');
    setTimeout(() => this.start(builderTourSteps(), host), TourService.SECTION_RENDER_DELAY_MS);
  }

  /** Closes a running tour (e.g. on route change); safe to call when none runs. */
  destroy(): void {
    this.active?.destroy();
    this.active = null;
  }

  // ---- internals ----------------------------------------------------------

  /** Delay between showSection() and (re)measuring the target element. */
  private static readonly SECTION_RENDER_DELAY_MS = 250;

  private start(defs: TourStepDef[], host?: BuilderTourHost): void {
    this.destroy();
    const t = (key: string): string => this.translate.instant(key);

    const driverObj = driver({
      showProgress: true,
      animate: true,
      overlayOpacity: 0.6,
      stagePadding: 6,
      popoverClass: 'hr-tour-popover',
      nextBtnText: t('tour.next'),
      prevBtnText: t('tour.prev'),
      doneBtnText: t('tour.done'),
      // driver.js substitutes {{current}}/{{total}} itself; ngx-translate leaves
      // unknown placeholders untouched, so the translated pattern passes through.
      progressText: t('tour.progress'),
      onDestroyed: () => {
        if (this.active === driverObj) {
          this.active = null;
        }
      },
      steps: defs.map((def, index) => this.toDriveStep(def, index, defs, host, () => driverObj)),
    });

    this.active = driverObj;
    driverObj.drive();
  }

  private toDriveStep(
    def: TourStepDef,
    index: number,
    defs: TourStepDef[],
    host: BuilderTourHost | undefined,
    getDriver: () => Driver
  ): DriveStep {
    const t = (key: string): string => this.translate.instant(key);

    /** Moves to `target`, switching the builder section first when needed. */
    const moveTo = (target: number): void => {
      const d = getDriver();
      if (target < 0 || target >= defs.length) {
        d.destroy();
        return;
      }
      const next = defs[target];
      if (host && next.section && next.section !== def.section) {
        host.showSection(next.section);
        // Zoneless: the host marked itself for check; wait one macro-task window
        // for Angular to render the section before driver.js measures the target.
        setTimeout(() => d.moveTo(target), TourService.SECTION_RENDER_DELAY_MS);
      } else {
        d.moveTo(target);
      }
    };

    return {
      ...(def.selector ? { element: def.selector } : {}),
      popover: {
        title: t(def.titleKey),
        description: t(def.descriptionKey),
        // Custom navigation: driver.js delegates next/prev entirely to us once
        // these hooks exist — required for the section-switching choreography.
        onNextClick: () => moveTo(index + 1),
        onPrevClick: () => moveTo(index - 1),
      },
    };
  }

  /** Resolves once the selector matches (polling), or after the timeout. */
  private whenPresent(selector: string, timeoutMs: number): Promise<void> {
    return new Promise(resolve => {
      const startedAt = Date.now();
      const probe = (): void => {
        if (document.querySelector(selector) || Date.now() - startedAt > timeoutMs) {
          resolve();
        } else {
          setTimeout(probe, 100);
        }
      };
      probe();
    });
  }
}
