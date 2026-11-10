# syntax=docker/dockerfile:1

# ---- build stage: needs no Maven or JDK on your machine ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
# A Windows checkout can leave CRLF line endings, which break the script on Linux.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Dependencies in their own layer, so editing code doesn't re-download them.
RUN ./mvnw -B dependency:go-offline
COPY src src
# Tests run in CI, not in the image build.
RUN ./mvnw -B -DskipTests package

# ---- runtime stage: JRE only ----
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home appuser
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]