FROM docker.io/clojure:openjdk-11-tools-deps-slim-buster AS builder
WORKDIR /usr/src/app
COPY . .
RUN clojure -X:depstar
FROM ghcr.io/graalvm/graalvm-ce:22.1.0 AS native
WORKDIR /usr/src/app
COPY --from=builder /usr/src/app/app.jar /usr/src/app/app.jar
RUN gu install native-image
COPY compile.sh .
RUN chmod +x compile.sh
RUN ./compile.sh
FROM gcr.io/distroless/base:latest
ENV PORT="8080"
ENV HOST="0.0.0.0"
EXPOSE 8080
COPY --from=native /usr/src/app/app /
CMD ["/app", "init"]
