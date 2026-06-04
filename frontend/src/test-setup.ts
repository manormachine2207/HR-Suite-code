// Polyfill ResizeObserver for ngx-vflow in jsdom test environment.
// ngx-vflow uses ResizeObserver internally; JSDOM does not provide it.
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe(): void { /* noop */ }
    unobserve(): void { /* noop */ }
    disconnect(): void { /* noop */ }
  };
}
