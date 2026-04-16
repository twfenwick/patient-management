FROM maven:3.9.14-eclipse-temurin-25 AS builder

WORKDIR /app

COPY pom.xml .

# Download dependencies to cache them unless updated
# -B is non-interactive batch mode to avoid prompts during the build and excessive logging
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package

FROM eclipse-temurin:25-jdk as runner

WORKDIR /app

COPY --from=builder ./app/target/api-gateway-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 4004

ENTRYPOINT ["java", "-jar", "app.jar"]
