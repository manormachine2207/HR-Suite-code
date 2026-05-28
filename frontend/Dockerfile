# syntax=docker/dockerfile:1.7

# ===== Stage 1: build =====
FROM node:22-alpine AS build
WORKDIR /workspace

# Layer-Cache fuer Dependencies
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# Source + Build
COPY . .
RUN --mount=type=cache,target=/root/.npm \
    npx ng build --configuration=production

# ===== Stage 2: runtime =====
FROM nginx:alpine AS runtime

# Custom nginx config (SPA-Fallback, gzip, security, /healthz)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Built Angular app
COPY --from=build /workspace/dist/hr-suite-frontend/browser /usr/share/nginx/html

# Keep root user for nginx to manage all necessary directories
# (Explicit non-root would require additional setup for /run and /var/log permissions)

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz || exit 1
