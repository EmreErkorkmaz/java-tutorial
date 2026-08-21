# ---- build stage: needs the JDK, Maven and the dependency cache ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the build descriptor first so the dependency download becomes its own
# cached layer - changing source code then does not re-download everything.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# --mount=type=cache: keeps ~/.m2 on the BUILD HOST between builds, so dependencies are
# downloaded once instead of on every image build. The cache never lands in the final
# image - it only exists during the build step.
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -B

# ---- runtime stage: only a JRE and the jar ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run as root in a container: whatever escapes the process starts with its user.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]