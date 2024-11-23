./gradlew shadowJar && \
JAR_DATE=$(date +%s) docker-compose down && docker-compose up --build
