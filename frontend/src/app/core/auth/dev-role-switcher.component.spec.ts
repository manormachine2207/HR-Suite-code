import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';

import { DevRoleSwitcherComponent } from './dev-role-switcher.component';
import { DEV_ROLE_STORAGE_KEY } from './dev-role';
import { RuntimeConfigService } from '../runtime-config/runtime-config.service';

const CONFIG_TOKEN = 'dev-hr-designer~019e754d-371c-70e0-b199-88ab785bef6e';

async function create(config: object): Promise<ComponentFixture<DevRoleSwitcherComponent>> {
  await TestBed.configureTestingModule({
    imports: [DevRoleSwitcherComponent, TranslateModule.forRoot()],
    providers: [{ provide: RuntimeConfigService, useValue: { get: () => config } }],
  }).compileComponents();
  // Minimal translations so the chip interpolates {{role}} instead of echoing the key.
  const translate = TestBed.inject(TranslateService);
  translate.setTranslation('de', {
    dev: { roleSwitcher: { chip: 'DEV: {{role}}', default: 'Standard (runtime.json)' } },
  });
  translate.use('de');
  const fixture = TestBed.createComponent(DevRoleSwitcherComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture;
}

describe('DevRoleSwitcherComponent (ADR-016 Gap 13)', () => {
  beforeEach(() => localStorage.removeItem(DEV_ROLE_STORAGE_KEY));
  afterEach(() => localStorage.removeItem(DEV_ROLE_STORAGE_KEY));

  it('renders NOTHING without a devAuth block (prod config)', async () => {
    const fixture = await create({});
    expect((fixture.nativeElement as HTMLElement).querySelector('.hr-dev-switcher')).toBeNull();
  });

  it('renders NOTHING when devAuth is disabled', async () => {
    const fixture = await create({ devAuth: { enabled: false, token: CONFIG_TOKEN } });
    expect((fixture.nativeElement as HTMLElement).querySelector('.hr-dev-switcher')).toBeNull();
  });

  it('renders the chip as a real <button> with aria-label when devAuth is enabled', async () => {
    const fixture = await create({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    const chip = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button.hr-dev-switcher__chip');
    expect(chip).not.toBeNull();
    expect(chip!.getAttribute('aria-label')).toBeTruthy();
    expect(chip!.getAttribute('aria-expanded')).toBe('false');
  });

  it('shows the active identity from localStorage in the chip', async () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const fixture = await create({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    const chip = (fixture.nativeElement as HTMLElement).querySelector('.hr-dev-switcher__chip')!;
    expect(chip.textContent).toContain('approver-hal');
  });

  it('opens a menu listing the 7 identities + the default entry', async () => {
    const fixture = await create({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.hr-dev-switcher__chip')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const items = (fixture.nativeElement as HTMLElement)
      .querySelectorAll('.hr-dev-switcher__menu [role="menuitem"]');
    expect(items).toHaveLength(8);
    const labels = Array.from(items).map(i => i.textContent?.trim());
    for (const role of ['applicant', 'approver-vg', 'approver-hr-bp', 'approver-hal',
                        'hr-reviewer', 'hr-designer', 'tenant-admin']) {
      expect(labels.some(l => l?.includes(role))).toBe(true);
    }
  });

  it('selecting an identity writes localStorage and reloads', async () => {
    const fixture = await create({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    const cmp = fixture.componentInstance;
    const reload = vi.spyOn(cmp as never as { reload(): void }, 'reload').mockImplementation(() => undefined);
    (cmp as never as { selectRole(r: string | null): void }).selectRole('approver-hal');
    expect(localStorage.getItem(DEV_ROLE_STORAGE_KEY)).toBe('approver-hal');
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('selecting the default entry clears localStorage and reloads', async () => {
    localStorage.setItem(DEV_ROLE_STORAGE_KEY, 'approver-hal');
    const fixture = await create({ devAuth: { enabled: true, token: CONFIG_TOKEN } });
    const cmp = fixture.componentInstance;
    const reload = vi.spyOn(cmp as never as { reload(): void }, 'reload').mockImplementation(() => undefined);
    (cmp as never as { selectRole(r: string | null): void }).selectRole(null);
    expect(localStorage.getItem(DEV_ROLE_STORAGE_KEY)).toBeNull();
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
