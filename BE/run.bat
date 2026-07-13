@echo off
echo Killing process on port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    taskkill /PID %%a /F >nul 2>&1
)
echo Starting backend...
cd /d "%~dp0"
java -jar target/taskhub-backend-1.0.0-exec.jar
