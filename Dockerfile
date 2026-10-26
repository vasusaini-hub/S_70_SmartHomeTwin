
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out && javac -encoding UTF-8 -d out SmartHomeTwinServer.java

EXPOSE 10000

CMD ["sh", "-c", "java -cp out SmartHomeTwinServer"]
