import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';

import { FormDesignerComponent } from './form-designer.component';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

const stubRoute = {
  snapshot: { paramMap: { get: (_: string) => 'at-1' } },
} as unknown as ActivatedRoute;

const DRAFT_VERSION = {
  id: 'v-1', antragstypId: 'at-1', major: 1, minor: 0, status: 'DRAFT',
  formDefinition: { fields: [{ key: 'name', type: 'TEXT', required: true }] },
  flowDefinition: {
    steps: [{ kind: 'FORM', key: 'fill_form', title: { de: 'Formular' } }],
  },
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

describe('FormDesignerComponent — flow seeding', () => {
  let fixture: ComponentFixture<FormDesignerComponent>;
  let cmp: FormDesignerComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormDesignerComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
        { provide: ActivatedRoute, useValue: stubRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FormDesignerComponent);
    cmp = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  it('seeds the flow editor with an existing draft flowDefinition', () => {
    // Install spy on loadFlow before any detectChanges so we can observe the call.
    // The component hasn't run ngOnInit yet, but flowEditor ViewChild is not set yet either.
    // We'll assert by inspecting the editor's FormArray state after seeding.

    fixture.detectChanges(); // triggers ngOnInit — forkJoin fires

    // Flush all three HTTP requests
    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);

    // Run another CD cycle so the subscribe callback runs and markForCheck() takes effect
    fixture.detectChanges();

    // ViewChild must now be defined (editor is always mounted, not behind @if)
    expect(cmp.flowEditor).toBeDefined();

    // The flow editor must contain the one FORM step from the draft
    expect(cmp.flowEditor!.steps.length).toBe(1);
    expect(cmp.flowEditor!.steps.at(0).controls.kind.value).toBe('FORM');
    expect(cmp.flowEditor!.steps.at(0).controls.key.value).toBe('fill_form');
  });

  it('save() reads flow from the editor even when the form tab is active', () => {
    fixture.detectChanges();

    http.expectOne('/api/v1/antragstyp/at-1').flush({ id: 'at-1', key: 'urlaubsantrag' });
    http.expectOne('/api/v1/action/refs').flush(['provision-ad-account']);
    http.expectOne('/api/v1/antragstyp/at-1/versions').flush([DRAFT_VERSION]);

    fixture.detectChanges();

    // section is 'form' by default; the flow editor is always mounted
    expect(cmp.section).toBe('form');
    expect(cmp.flowEditor).toBeDefined();

    const toFlowSpy = vi.spyOn(cmp.flowEditor!, 'toFlowDefinition');

    cmp.save();

    // save() must call toFlowDefinition() on the editor regardless of active tab
    expect(toFlowSpy).toHaveBeenCalledOnce();

    // Clean up pending PUT request
    http.expectOne(r => r.url.includes('/draft')).flush(
      { ...DRAFT_VERSION, minor: 1 }
    );
  });
});
