#!/bin/bash

JAR_FILE="app/target/app-1.0.0-SNAPSHOT.jar"
LOG_FILE="app.log"
JAVA_OPTS="-Xms256m -Xmx512m"

PID=$(ps aux | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "Stopping existing process (PID: $PID)..."
    kill "$PID"
    sleep 2
    if kill -0 "$PID" 2>/dev/null; then
        echo "Force killing (PID: $PID)..."
        kill -9 "$PID"
    fi
    echo "Process stopped."
fi

nohup java $JAVA_OPTS -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &

echo "Started (PID: $!), log: $LOG_FILE"
