# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

# Cache dependencies layer
COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline

# Build
COPY . .
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ─── Stage 2: Serve with Nginx ────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

LABEL maintainer="Sergio Hernández <sergio@nexusai.local>"
LABEL description="NexusAI Frontend — React + Tailwind 4"

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Custom Nginx config (SPA + proxy)
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

