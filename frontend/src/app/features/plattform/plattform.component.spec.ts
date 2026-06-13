import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { PlattformComponent } from './plattform.component';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('PlattformComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlattformComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    }).compileComponents();
  });

  it('renders four module tabs with only Mandanten enabled', async () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const tabs = [...el.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
    expect(tabs).toHaveLength(4);
    const enabled = tabs.filter(t => !t.disabled);
    expect(enabled).toHaveLength(1);
    expect(enabled[0].getAttribute('aria-selected')).toBe('true');
  });

  it('ignores clicks on disabled tabs (active stays mandanten)', () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    const cmp = fixture.componentInstance;
    cmp.select(cmp.tabs[1]); // sso, disabled
    expect(cmp.active()).toBe('mandanten');
  });
});
