FROM docker.io/clojure:openjdk-11-tools-deps-slim-buster AS builder
WORKDIR /usr/src/app
COPY . .
RUN clojure -X:depstar
FROM ghcr.io/graalvm/graalvm-ce:22.1.0 AS native
WORKDIR /usr/src/app
COPY --from=builder /usr/src/app/dartclj.jar /usr/src/app/dartclj.jar
RUN gu install native-image
COPY compile.sh .
RUN chmod +x compile.sh
RUN ./compile.sh
CMD ["/usr/src/app/dartclj", "Text(1)"]
