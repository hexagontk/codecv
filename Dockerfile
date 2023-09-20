FROM docker.io/gradle:jdk20-jammy AS build
COPY --chown=gradle:gradle . /home/gradle/codecv
WORKDIR /home/gradle/codecv
RUN gradle clean build --no-daemon

FROM docker.io/amazoncorretto:20
EXPOSE 2010

RUN yum install tar -y && yum clean all -y
RUN mkdir /app

COPY --from=build /home/gradle/codecv/build/distributions/*.tar /app/

RUN tar -xvf /app/*.tar -C /app/ --strip-components 1
RUN ln -sf /app/bin/codecv /bin/cv && rm /app/*.tar

RUN mkdir /data
VOLUME [ "/data" ]
WORKDIR /data

ENTRYPOINT ["/app/bin/codecv"]
