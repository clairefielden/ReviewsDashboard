# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy wrapper & pom first for better caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B -DskipTests dependency:go-offline

# Now copy source and build
COPY src src
RUN ./mvnw -q -B -DskipTests clean package

# ---------- Run stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the fat jar produced above (adjust if your artifactId/version differ)
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar

# Platform will set PORT; default to 8080 for local runs
ENV PORT=8080
EXPOSE 8080

# Ensure Spring binds to the injected port
ENV JAVA_TOOL_OPTIONS="-Dserver.port=${PORT}"

CMD ["java","-jar","/app/app.jar"]
