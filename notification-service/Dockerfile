FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# --mount=type=cache: keeps ~/.m2 on the BUILD HOST between builds, so dependencies are
# downloaded once instead of on every image build. The cache never lands in the final
# image - it only exists during the build step.
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]