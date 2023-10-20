FROM docker.io/gradle:jdk21-jammy AS build
COPY --chown=gradle:gradle . /home/gradle/codecv
WORKDIR /home/gradle/codecv
RUN ./gradlew installDist --no-daemon

FROM docker.io/amazoncorretto:21
EXPOSE 2010

COPY --from=build /home/gradle/codecv/build/install/codecv /app/

RUN ln -sf /app/bin/codecv /bin/codecv

RUN mkdir /data
VOLUME [ "/data" ]
WORKDIR /data

ENTRYPOINT [ "/app/bin/codecv" ]
