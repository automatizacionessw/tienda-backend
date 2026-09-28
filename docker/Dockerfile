# Imagen del backend. Se construye con la raiz del repo como contexto:
#   docker build -f docker/Dockerfile .
# Las exclusiones del contexto estan en docker/Dockerfile.dockerignore.

# --- Build -----------------------------------------------------------------
# Se usa la imagen oficial de Maven en lugar de ./mvnw: el wrapper jar esta
# ignorado por git y en checkouts de Windows el script puede quedar con CRLF.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencias en su propia capa: solo se vuelven a descargar si cambia el pom.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Los tests necesitan una base de datos real; se ejecutan en desarrollo.
RUN mvn -B -q -DskipTests package \
    && cp target/tienda-backend-*.jar /build/app.jar

# --- Runtime ---------------------------------------------------------------
# Alpine trae wget (busybox), que usa el healthcheck del compose.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data/archivos \
    && chown -R app:app /app

COPY --from=build --chown=app:app /build/app.jar app.jar

USER app
ENV STORAGE_DIR=/app/data/archivos
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
