@echo off
REM Integration Test Helper Script for Windows
REM Simplifies common operations for the PMS integration test environment

setlocal enabledelayedexpansion

set COMPOSE_FILE=docker-compose-integration.yaml
set COMPOSE_CMD=docker-compose -f %COMPOSE_FILE%

if "%1"=="" goto help
if "%1"=="help" goto help
if "%1"=="build" goto build
if "%1"=="start" goto start
if "%1"=="stop" goto stop
if "%1"=="restart" goto restart
if "%1"=="destroy" goto destroy
if "%1"=="status" goto status
if "%1"=="logs" goto logs
if "%1"=="health" goto health
if "%1"=="test" goto test
if "%1"=="watch" goto watch
if "%1"=="topics" goto topics
if "%1"=="groups" goto groups
if "%1"=="reset-kafka" goto reset_kafka
if "%1"=="db" goto db
if "%1"=="redis" goto redis
if "%1"=="kafka" goto kafka
if "%1"=="urls" goto urls
goto help

:build
echo ========================================
echo Building All Services
echo ========================================
echo Building trade-capture...
call mvnw.cmd clean package -DskipTests
echo Building validation...
cd ..\pms-validation
call mvnw.cmd clean package -DskipTests
echo Building simulation...
cd ..\pms-simulation
call mvnw.cmd clean package -DskipTests
cd ..\pms-trade-capture
echo [92m✅ All services built successfully![0m
goto end

:start
echo ========================================
echo Starting Integration Stack
echo ========================================
%COMPOSE_CMD% up -d
echo [92m✅ Stack started![0m
echo.
goto status

:stop
echo ========================================
echo Stopping Integration Stack
echo ========================================
%COMPOSE_CMD% down
echo [92m✅ Stack stopped![0m
goto end

:restart
echo ========================================
echo Restarting Integration Stack
echo ========================================
call :stop
call :start
goto end

:destroy
echo ========================================
echo Destroying Integration Stack (including volumes)
echo ========================================
echo [93m⚠️  This will delete all data![0m
set /p confirm="Are you sure? (yes/no): "
if /i "%confirm%"=="yes" (
    %COMPOSE_CMD% down -v
    echo [92m✅ Stack destroyed![0m
) else (
    echo Cancelled.
)
goto end

:status
echo ========================================
echo Service Status
echo ========================================
%COMPOSE_CMD% ps
goto end

:logs
if "%2"=="" (
    echo ========================================
    echo Tailing All Logs
    echo ========================================
    %COMPOSE_CMD% logs -f
) else (
    echo ========================================
    echo Tailing %2 Logs
    echo ========================================
    %COMPOSE_CMD% logs -f %2
)
goto end

:health
echo ========================================
echo Health Check
echo ========================================
echo Trade Capture:
curl -s http://localhost:8082/actuator/health
echo.
echo Validation:
curl -s http://localhost:8080/actuator/health
echo.
echo Simulation:
curl -s http://localhost:4000/actuator/health
echo.
echo Schema Registry:
curl -s http://localhost:8081/subjects
echo.
goto end

:test
set count=%2
set delay=%3
if "%count%"=="" set count=100
if "%delay%"=="" set delay=10
echo ========================================
echo Testing Trade Flow
echo ========================================
echo Sending %count% trades with %delay%ms delay...
curl -X POST "http://localhost:4000/api/simulation/generate?count=%count%&delay=%delay%"
echo.
echo [92m✅ Trades sent![0m
echo.
timeout /t 5 /nobreak >nul
echo Consumer Group Status:
docker exec pms-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group validation-consumer-group
goto end

:watch
set topic=%2
if "%topic%"=="" set topic=raw-trades-topic
echo ========================================
echo Watching Topic: %topic%
echo ========================================
docker exec -it pms-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic %topic% --from-beginning
goto end

:topics
echo ========================================
echo Kafka Topics
echo ========================================
docker exec pms-kafka kafka-topics --bootstrap-server localhost:9092 --list
goto end

:groups
echo ========================================
echo Consumer Groups
echo ========================================
docker exec pms-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
goto end

:reset_kafka
echo ========================================
echo Resetting Kafka Topics
echo ========================================
echo [93m⚠️  This will delete all messages in topics![0m
set /p confirm="Are you sure? (yes/no): "
if /i "%confirm%"=="yes" (
    for %%t in (raw-trades-topic valid-trades-topic invalid-trades-topic rttm.trade.events rttm.dlq.events rttm.queue.metrics rttm.error.events) do (
        echo Deleting %%t...
        docker exec pms-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic %%t 2>nul
        echo Recreating %%t...
        docker exec pms-kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic %%t --partitions 2 --replication-factor 1
    )
    echo [92m✅ Kafka topics reset![0m
) else (
    echo Cancelled.
)
goto end

:db
echo ========================================
echo PostgreSQL Shell
echo ========================================
docker exec -it pms-postgres psql -U pms -d pmsdb
goto end

:redis
echo ========================================
echo Redis Shell
echo ========================================
docker exec -it pms-redis-master redis-cli
goto end

:kafka
echo ========================================
echo Kafka Shell
echo ========================================
docker exec -it pms-kafka bash
goto end

:urls
echo ========================================
echo Service URLs
echo ========================================
echo Trade Capture:      http://localhost:8082
echo Validation:         http://localhost:8080
echo Simulation:         http://localhost:4000
echo Kafka Control:      http://localhost:9021
echo RabbitMQ Mgmt:      http://localhost:15672 (guest/guest)
echo Schema Registry:    http://localhost:8081
echo PostgreSQL:         localhost:5432 (pms/pms)
echo Redis:              localhost:6379
goto end

:help
echo PMS Integration Test Helper (Windows)
echo.
echo Usage: test-integration.bat ^<command^> [options]
echo.
echo Commands:
echo   build         Build all services (mvn package)
echo   start         Start the integration stack
echo   stop          Stop the integration stack
echo   restart       Restart the integration stack
echo   destroy       Stop and remove all data (requires confirmation)
echo   status        Show service status
echo   logs [svc]    Tail logs (all or specific service)
echo   health        Check health of all services
echo.
echo   test [n] [d]  Send n trades with d ms delay (default: 100 trades, 10ms)
echo.
echo   watch ^<topic^> Watch messages in Kafka topic
echo   topics        List all Kafka topics
echo   groups        List all consumer groups
echo   reset-kafka   Delete and recreate all Kafka topics
echo.
echo   db            Open PostgreSQL shell
echo   redis         Open Redis shell
echo   kafka         Open Kafka container shell
echo.
echo   urls          Show all service URLs
echo   help          Show this help message
echo.
echo Examples:
echo   test-integration.bat build                    # Build all services
echo   test-integration.bat start                    # Start the stack
echo   test-integration.bat logs trade-capture       # Watch trade-capture logs
echo   test-integration.bat test 1000 5              # Send 1000 trades with 5ms delay
echo   test-integration.bat watch rttm.trade.events  # Watch RTTM events
echo   test-integration.bat db                       # Open database shell
goto end

:end
endlocal
