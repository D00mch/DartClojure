# build uberjar locally
FROM ghcr.io/graalvm/graalvm-ce:ol8-java17 AS native
WORKDIR /usr/src/app
COPY . .
RUN gu install native-image
WORKDIR /usr/src/app
COPY compile.sh .
RUN chmod +x compile.sh
RUN ./compile.sh
CMD ["/usr/src/app/dartclj", "Text(1)"]
