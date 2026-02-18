#!/bin/bash
# Debug Mode AI Analysis
# Runs AI with detailed logging and IGV dumps

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass> [additional-args...]"
    echo "Example: $0 LoopSummation"
    exit 1
fi

MAIN_CLASS=$1
shift

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="debug_${MAIN_CLASS}_${TIMESTAMP}.log"
JSON_DIR="json_graphs_${MAIN_CLASS}_${TIMESTAMP}"

echo "========================================"
echo "Running DEBUG AI Analysis"
echo "Main Class: $MAIN_CLASS"
echo "Log File: $LOG_FILE"
echo "JSON Graphs: $JSON_DIR"
echo "========================================"

mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
    -H:+ReportExceptionStackTraces \
    -H:Log=AbstractInterpretation \
    -H:Dump=:2 \
    -H:PrintGraph=Network \
    -H:MethodFilter=$MAIN_CLASS.* \
    -H:+RunAbstractInterpretation \
    -H:AILogLevel=DEBUG \
    -H:+AILogToConsole \
    -H:+AILogToFile \
    -H:AILogFilePath="$LOG_FILE" \
    -H:+AIEnableIGVDump \
    -H:+AIExportGraphToJSON \
    -H:AIJSONExportPath="$JSON_DIR" \
    -H:+PrintAIStatistics \
    -H:+PrintOptimizationSummary \
    -H:+PrintTopOptimizedMethods \
    "$@" \
    $MAIN_CLASS

echo ""
echo "========================================"
echo "Debug Analysis Complete!"
echo "Log file: $LOG_FILE"
echo "JSON graphs: $JSON_DIR/"
echo "IGV: Connect to localhost:4445"
echo "========================================"

