# ==============================
# Stage 1 - Build
# ==============================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================
# Stage 2 - Runtime
# ==============================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/swiftkart

# Create group, user, data folder, and assign ownership
RUN addgroup -S swiftgroup \
    && adduser -S swiftuser -G swiftgroup \
    && mkdir -p /opt/swiftkart/data \
    && chown -R swiftuser:swiftgroup /opt/swiftkart

COPY --from=builder --chown=swiftuser:swiftgroup /app/target/*.jar app.jar

USER swiftuser
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]