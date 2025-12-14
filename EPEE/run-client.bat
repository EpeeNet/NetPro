@echo off
chcp 65001 > nul
title EPEE Game Client

echo ==============================
echo   EPEE Game Client Launcher
echo ==============================

REM 1. JAVA_HOME 확인
if defined JAVA_HOME goto CHECK_VERSION

REM 2. java 명령 확인
where java >nul 2>nul
if %errorlevel%==0 goto CHECK_VERSION

echo.
echo [ERROR] Java가 설치되어 있지 않습니다.
echo JDK 17 이상을 설치해주세요.
echo https://adoptium.net
pause
exit /b

:CHECK_VERSION
echo Java 확인 중...
java -version
echo.
echo 클라이언트 실행 중...
call gradlew :client:run
pause
