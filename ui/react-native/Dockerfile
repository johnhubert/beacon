FROM node:20 AS deps
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build:web

FROM node:20-alpine AS runner
WORKDIR /app
RUN apk add --no-cache curl && npm install -g serve
COPY --from=deps /app/dist ./dist
EXPOSE 3000
CMD ["serve", "-s", "dist", "-l", "3000"]
