# 프론트엔드 이미지. Node 로 정적 파일을 빌드하고 실행 이미지에는 nginx 와 빌드 결과만 남긴다.
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# VITE_ 값은 실행 시점이 아니라 빌드 시점에 번들에 들어가므로 빌드 인자로 받는다.
ARG VITE_API_BASE_URL
ARG VITE_NAVER_MAP_CLIENT_ID
ARG VITE_MAX_PHOTO_SIZE_MB
ARG VITE_MAX_PHOTO_TOTAL_MB
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
