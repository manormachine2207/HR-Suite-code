import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ObIconService } from '@oblique/oblique';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import { NotificationBellComponent } from './notification-bell.component';
import { NotificationApiService } from './notification.service';

registerLocaleData(localeDeCh);

const item = (over = {}) => ({
  id: 'n1', type: 'ANTRAG_DECIDED', antragId: 'a1', params: { status: 'APPROVED' },
  read: false, createdAt: '2026-06-14T10:00:00Z', ...over,
});

describe('NotificationBellComponent', () => {
  let api: { list: ReturnType<typeof vi.fn>; unreadCount: ReturnType<typeof vi.fn>; markRead: ReturnType<typeof vi.fn>; markAllRead: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { list: vi.fn().mockReturnValue(of([])), unreadCount: vi.fn().mockReturnValue(of({ count: 0 })),
            markRead: vi.fn().mockReturnValue(of(void 0)), markAllRead: vi.fn().mockReturnValue(of(void 0)) };
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: NotificationApiService, useValue: api }],
    }).compileComponents();
    TestBed.inject(ObIconService).registerOnAppInit();
  });

  afterEach(() => vi.restoreAllMocks());

  it('shows the unread badge when count > 0', async () => {
    api.unreadCount.mockReturnValue(of({ count: 3 }));
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.unread()).toBe(3);
    expect(fixture.nativeElement.querySelector('.hr-bell__badge')?.textContent?.trim()).toBe('3');
  });

  it('hides the badge at zero and a 403/error coerces the count to 0', async () => {
    api.unreadCount.mockReturnValue(throwError(() => ({ status: 403 })));
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.unread()).toBe(0);
    expect(fixture.nativeElement.querySelector('.hr-bell__badge')).toBeNull();
  });

  it('loads the list when opened and marks an item read on click', async () => {
    api.unreadCount.mockReturnValue(of({ count: 1 }));
    api.list.mockReturnValue(of([item()]));
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const cmp = fixture.componentInstance;
    cmp.toggle();
    expect(api.list).toHaveBeenCalled();
    cmp.onItem(item());
    expect(api.markRead).toHaveBeenCalledWith('n1');
    expect(cmp.open()).toBe(false);
  });
});
