import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { AntragstypCreateComponent } from './antragstyp-create.component';
import { RuntimeConfigService } from '../../core/runtime-config/runtime-config.service';

const stubConfig = { get: () => ({ apiBaseUrl: '/api/v1' }) } as Partial<RuntimeConfigService>;

describe('AntragstypCreateComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AntragstypCreateComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: RuntimeConfigService, useValue: stubConfig },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('is invalid with an empty key and does not POST', () => {
    const fixture = TestBed.createComponent(AntragstypCreateComponent);
    const cmp = fixture.componentInstance;
    cmp.submit();
    http.expectNone('/api/v1/antragstyp');
    expect(cmp.form.invalid).toBe(true);
  });

  it('POSTs and navigates to the builder on success', () => {
    const fixture = TestBed.createComponent(AntragstypCreateComponent);
    const cmp = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    cmp.form.controls.key.setValue('urlaubsantrag');
    cmp.titleControl('de').setValue('Urlaub');
    cmp.submit();

    const req = http.expectOne('/api/v1/antragstyp');
    expect(req.request.body).toEqual({ key: 'urlaubsantrag', title: { de: 'Urlaub' } });
    req.flush({ id: 'at-new' });

    expect(nav).toHaveBeenCalledWith(['/antragstypen', 'at-new', 'designer']);
  });
});
