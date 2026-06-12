import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { RuntimeConfigService } from '../runtime-config/runtime-config.service';
import { DEV_ROLES, DEV_ROLE_STORAGE_KEY, readDevRoleOverride } from './dev-role';

/**
 * Dev identity switcher (ADR-016, Prototyp-Gap 13): a small header chip that swaps
 * the dev bearer-token identity via localStorage (`hrsuite.dev.role`) and reloads,
 * so approval chains (VG → HR-BP → HAL, …) can be played through without editing
 * runtime.json.
 *
 * SECURITY: renders NOTHING unless runtime.json carries an enabled `devAuth` block
 * (same guard as DevAuthInterceptor) — production configs have no such block, so
 * the widget is invisible there and localStorage is never touched.
 *
 * Zoneless: all mutable view state is signal-based (no markForCheck needed); the
 * only async effect is the full-page reload after switching.
 */
@Component({
  selector: 'app-dev-role-switcher',
  standalone: true,
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dev-role-switcher.component.html',
  styleUrl: './dev-role-switcher.component.scss',
})
export class DevRoleSwitcherComponent {
  private readonly config = inject(RuntimeConfigService);

  protected readonly roles = DEV_ROLES;

  /** Static per app start: devAuth presence cannot change without a reload. */
  protected readonly devAuthActive: boolean = (() => {
    const devAuth = this.config.get().devAuth;
    return !!(devAuth?.enabled && devAuth.token);
  })();

  /** Currently overridden identity (null = default token from runtime.json). */
  protected readonly activeRole = signal<string | null>(
    this.devAuthActive ? readDevRoleOverride() : null
  );

  protected readonly menuOpen = signal(false);

  protected toggleMenu(): void {
    this.menuOpen.update(open => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** Persists the identity override (null clears it) and reloads the SPA. */
  protected selectRole(role: string | null): void {
    if (!this.devAuthActive) return;
    try {
      if (role) {
        localStorage.setItem(DEV_ROLE_STORAGE_KEY, role);
      } else {
        localStorage.removeItem(DEV_ROLE_STORAGE_KEY);
      }
    } catch {
      // storage unavailable → nothing to persist, reload keeps the default identity
    }
    this.menuOpen.set(false);
    this.reload();
  }

  /** Seam for unit tests (jsdom cannot navigate). */
  protected reload(): void {
    globalThis.location.reload();
  }
}
