#!/bin/bash

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass> [additional-args...]"
    echo "Example: $0 RecursiveFibonacci"
    exit 1
fi

MAIN_CLASS=$1
shift

echo "========================================"
echo "Running Intraprocedural AI Analysis"
echo "Main Class: $MAIN_CLASS"
echo "========================================"

mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
    -H:+ReportExceptionStackTraces \
    -H:Log=AbstractInterpretation \
    -H:Dump=:2 \
    -H:MethodFilter=$MAIN_CLASS.* \
    -H:PrintGraph=Network \
    -H:+RunAbstractInterpretation \
    -H:+IntraproceduralAnalysis \
    -H:-InterproceduralAnalysis \
    -H:+AIEnableIGVDump \
    -H:AILogLevel=DEBUG \
    -H:+AILogToFile \
    -H:AILogFilePath=intra_${MAIN_CLASS}.log \
    -H:+PrintOptimizationSummary \
    "$@" \
    $MAIN_CLASS

echo ""
echo "Intraprocedural analysis complete!"
echo "Check intra_${MAIN_CLASS}.log for details"

