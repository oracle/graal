#!/bin/bash

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass> [recursion-depth] [additional-args...]"
    echo "Example: $0 Factorial 10"
    echo "Default recursion depth: 10"
    exit 1
fi

MAIN_CLASS=$1
RECURSION_DEPTH=${2:-10}
shift
shift 2>/dev/null || shift

echo "========================================"
echo "Running Interprocedural AI Analysis"
echo "Main Class: $MAIN_CLASS"
echo "Recursion Depth: $RECURSION_DEPTH"
echo "Mode: Context-sensitive analysis"
echo "========================================"

mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
    -H:+ReportExceptionStackTraces \
    -H:Log=AbstractInterpretation \
    -H:Dump=:2 \
    -H:PrintGraph=Network \
    -H:MethodFilter=$MAIN_CLASS.* \
    -H:+RunAbstractInterpretation \
    -H:+InterproceduralAnalysis \
    -H:-IntraproceduralAnalysis \
    -H:MaxRecursionDepth=$RECURSION_DEPTH \
    -H:MaxCallStackDepth=15 \
    -H:KCFADepth=3 \
    -H:AILogLevel=INFO \
    -H:+AILogToFile \
    -H:AILogFilePath=inter_${MAIN_CLASS}.log \
    -H:+PrintAIStatistics \
    -H:+PrintTopOptimizedMethods \
    "$@" \
    $MAIN_CLASS

echo ""
echo "Interprocedural analysis complete!"
echo "Check inter_${MAIN_CLASS}.log for details"

