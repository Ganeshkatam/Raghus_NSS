# Multi-stage production Dockerfile for Raghu NSS Frontend
FROM node:22-alpine AS builder
WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm ci

# Copy source and build production static bundle
COPY . .
RUN npm run build

# Lightweight Nginx runtime
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
