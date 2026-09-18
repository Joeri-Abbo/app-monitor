FROM node:25-slim AS build

WORKDIR /build

# Install dependencies first for better layer caching.
COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:25-slim

# curl backs the container healthcheck in docker-compose.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV NODE_ENV=production

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=build /build/dist ./dist

USER node
EXPOSE 8080

CMD ["node", "dist/main.js"]
