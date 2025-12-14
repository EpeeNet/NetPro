@echo off
chcp 65001 > nul
echo ==============================
echo  EPEE Game Server Launcher
echo ==============================

REM 1. JAVA_HOME 확인
if defined JAVA_HOME (
    echo JAVA_HOME detected: %JAVA_HOME%
    goto RUN
)

REM 2. java 명령어 확인
where java >nul 2>nul
if %errorlevel%==0 (
    echo Java command detected from PATH
    goto RUN
)

REM 3. Java 없음
echo.
echo  Java가 설치되어 있지 않습니다.
echo  JDK 17 이상을 설치해주세요.
echo https://adoptium.net
echo.
pause
exit /b

:RUN
echo.
echo  서버 실행 중...
call gradlew :server:run

pause
