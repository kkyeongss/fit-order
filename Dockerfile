# 핏오더 실행 이미지. 빌드: docker build -t fit-order .
# 실행에 필요한 환경변수: FITORDER_DB_URL, FITORDER_DB_USER, FITORDER_DB_PASSWORD, DATA_GO_KR_SERVICE_KEY,
#                       FITORDER_USER_PASSWORD, FITORDER_ADMIN_PASSWORD (값은 이미지에 넣지 않는다)

# 1) 빌드: 테스트는 Docker가 필요해 여기서는 건너뛴다(로컬·CI에서 ./gradlew test)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

# 2) 실행
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 fitorder
USER fitorder
WORKDIR /app
COPY --from=build /app.jar app.jar
# 메모리가 작은 서버용 기본값. 넉넉하면 JAVA_OPTS로 바꾼다.
ENV JAVA_OPTS="-Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m" \
    TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
