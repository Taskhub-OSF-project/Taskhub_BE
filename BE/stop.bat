@echo off
echo Stopping TaskHub Backend...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo Killing PID %%a
    taskkill /PID %%a /F
)
echo Done.
