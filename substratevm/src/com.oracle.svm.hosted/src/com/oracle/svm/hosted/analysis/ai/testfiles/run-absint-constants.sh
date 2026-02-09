#!/bin/bash
# Constant Propagation Focus
# Optimized for constant folding and method inlining

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass> [additional-args...]"
    echo "Example: $0 Factorial"
    exit 1
fi

MAIN_CLASS=$1
shift

echo "========================================"
echo "Running AI: Constant Propagation"
echo "Main Class: $MAIN_CLASS"
echo "Optimizations: Constants & inlining"
echo "========================================"

mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
    -H:+ReportExceptionStackTraces \
    -H:Log=AbstractInterpretation \
    -H:Dump=:2 \
    -H:PrintGraph=Network \
    -H:MethodFilter=$MAIN_CLASS.* \
    -H:+RunAbstractInterpretation \
    -H:+InterproceduralAnalysis \
    -H:+EnableConstantPropagation \
    -H:+EnableConstantMethodInlining \
    -H:+EnableDeadBranchElimination \
    -H:-EnableBoundsCheckElimination \
    -H:MaxRecursionDepth=10 \
    -H:AILogLevel=INFO \
    -H:+AILogToFile \
    -H:AILogFilePath=const_${MAIN_CLASS}.log \
    -H:+PrintOptimizationSummary \
    "$@" \
    $MAIN_CLASS

echo ""
echo "Constant propagation analysis complete!"
echo "Check const_${MAIN_CLASS}.log for folded constants"

