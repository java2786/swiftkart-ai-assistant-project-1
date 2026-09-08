# ---------------------------------------------------
# Stage 1: Build & Package
# ---------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependencies first (improves build speed on code changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package fat JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Extract Spring Boot layers for optimized Docker layer caching
RUN java -Djarmode=layertools -jar target/*.jar extract

# ---------------------------------------------------
# Stage 2: Minimal Runtime
# ---------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy extracted layers in order of least-to-most frequently changing
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# Container-aware JVM memory configuration
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV PORT=9090
EXPOSE 9090

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]