# 공식 k6 이미지에는 TCP 모듈이 없어 xk6로 k6/x/tcp를 넣어 빌드한다. 방법마다 같은 바이너리로 재도록 버전을 고정한다.
FROM grafana/xk6:1.4.15 AS build
RUN xk6 build v2.3.0 --with github.com/grafana/xk6-tcp@v0.3.1 --output /tmp/k6

FROM grafana/k6:2.3.0
COPY --from=build /tmp/k6 /usr/bin/k6
