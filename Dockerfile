FROM gradle:8.7-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN sed -i 's/include(":app")//g' settings.gradle.kts
RUN gradle :backend:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN mkdir /app
COPY --from=build /home/gradle/src/backend/build/install/backend/ /app/
WORKDIR /app/bin
EXPOSE 8080
CMD ["./backend"]
