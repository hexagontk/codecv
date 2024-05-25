#FROM ghcr.io/graalvm/native-image-community:21-muslib AS build
FROM ghcr.io/graalvm/native-image-community:21 AS build
COPY --chown=gradle:gradle . /home/gradle/codecv
WORKDIR /home/gradle/codecv
RUN microdnf -y install findutils
RUN ./gradlew installDist zipNative --no-daemon

# TODO Native image from scratch
FROM docker.io/amazoncorretto:21
EXPOSE 2010

COPY --from=build /home/gradle/codecv/build/install/codecv /app/

RUN ln -sf /app/bin/codecv /bin/codecv

RUN mkdir /data
VOLUME [ "/data" ]
WORKDIR /data

ENTRYPOINT [ "/app/bin/codecv" ]
