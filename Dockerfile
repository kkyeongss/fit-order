# 핏오더 실행 이미지. 빌드: docker build -t fit-order .
# 실행에 필요한 환경변수: FITORDER_DB_URL, FITORDER_DB_USER, FITORDER_DB_PASSWORD, DATA_GO_KR_SERVICE_KEY,
#                       FITORDER_USER_PASSWORD, FITORDER_ADMIN_PASSWORD (값은 이미지에 넣지 않는다)

# 1) 화면(React) 빌드
FROM node:22 AS frontend
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run typecheck && npm test && npx vite build --outDir /out --emptyOutDir

# 2) 서버 빌드: 화면 결과를 jar에 넣는다. 서버 테스트는 Docker가 필요해 로컬·CI에서 따로 돌린다(./gradlew test)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
COPY --from=frontend /out build/frontend
RUN ./gradlew --no-daemon bootJar -x test -PskipFrontend && cp build/libs/*-SNAPSHOT.jar /app.jar

# 3) 실행
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
