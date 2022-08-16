FROM openjdk:8-jre-alpine

COPY ./target/snakesladders-*-standalone.jar snakesladders.jar
COPY ./resources resources

ENV DIRECTOR_SERVICE http://localhost:9091
ENV TURN_TIMEOUT 30
ENV HOST_ID 1337
ENV ALLOW_CORS_SESSIONS true
#ENV TABLES_JSON ${DIRECTOR_SERVICE}/tables
ENV TABLES_JSON tables.json
ENV HOST 127.0.0.1:8090

ENV PORT 8090

EXPOSE 8090 7373

CMD ["java", "-jar", "snakesladders.jar"]
