FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go-libp2p-wrapper/ .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" \
    -o /out/neoserver-bootstrap ./cmd/bootstrap

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /out/neoserver-bootstrap /usr/local/bin/neoserver-bootstrap
RUN mkdir -p /data
WORKDIR /data
EXPOSE 4001/tcp 4002/tcp
ENV BOOTSTRAP_PORT=4001 \
    BOOTSTRAP_HTTP_PORT=4002
ENTRYPOINT ["/usr/local/bin/neoserver-bootstrap"]
