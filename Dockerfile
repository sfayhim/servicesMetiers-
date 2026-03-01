# Utiliser une image Maven avec Java 21 pour builder
FROM maven:3.9.9-eclipse-temurin-21 AS build

# Définir le répertoire de travail
WORKDIR /app

# Copier les fichiers du projet
COPY pom.xml .
COPY src ./src
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn ./.mvn

# Builder l'application
RUN mvn clean package -DskipTests

# Utiliser une image Java 21 légère pour l'exécution
FROM eclipse-temurin:21-jre

# Définir le répertoire de travail
WORKDIR /app

# Copier le JAR depuis l'étape de build
COPY --from=build /app/target/*.jar app.jar

# Exposer le port (Render utilise la variable $PORT)
EXPOSE 8080

# Commande pour démarrer l'application
CMD ["java", "-Dserver.port=${PORT}", "-jar", "app.jar"]
