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

  it('renders four module tabs with Mandanten + SSO + SMTP enabled (ADR-019 Stufe 1+2+3)', async () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const tabs = [...el.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
    expect(tabs).toHaveLength(4);
    const enabledIds = fixture.componentInstance.tabs.filter(t => t.enabled).map(t => t.id);
    expect(enabledIds).toEqual(['mandanten', 'sso', 'smtp']);
    expect(tabs.filter(t => !t.disabled)).toHaveLength(3);
  });

  it('ignores clicks on disabled tabs (active stays mandanten)', () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    const cmp = fixture.componentInstance;
    cmp.select(cmp.tabs[3]); // logging, disabled
    expect(cmp.active()).toBe('mandanten');
  });

  it('switches to the SSO tab when selected', () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    const cmp = fixture.componentInstance;
    cmp.select(cmp.tabs[1]); // sso, enabled
    expect(cmp.active()).toBe('sso');
  });

  it('switches to the SMTP tab when selected', () => {
    const fixture = TestBed.createComponent(PlattformComponent);
    const cmp = fixture.componentInstance;
    cmp.select(cmp.tabs[2]); // smtp, enabled
    expect(cmp.active()).toBe('smtp');
  });
});
