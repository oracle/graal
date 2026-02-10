#!/bin/bash
# Bounds Check Elimination Focus
# Optimized for array bounds check removal

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass> [additional-args...]"
    echo "Example: $0 BoundsAllContextSafe"
    exit 1
fi

MAIN_CLASS=$1
shift

echo "========================================"
echo "Running AI: Bounds Check Elimination"
echo "Main Class: $MAIN_CLASS"
echo "Optimizations: Bounds checks only"
echo "========================================"

mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
    -H:+ReportExceptionStackTraces \
    -H:Log=AbstractInterpretation \
    -H:Dump=:2 \
    -H:PrintGraph=Network \
    -H:MethodFilter=$MAIN_CLASS.* \
    -H:+RunAbstractInterpretation \
    -H:+InterproceduralAnalysis \
    -H:+EnableBoundsCheckElimination \
    -H:-EnableConstantPropagation \
    -H:-EnableDeadBranchElimination \
    -H:-EnableConstantMethodInlining \
    -H:+TrackArrayLengths \
    -H:MaxRecursionDepth=10 \
    -H:AILogLevel=INFO \
    -H:+AILogToFile \
    -H:AILogFilePath=bounds_${MAIN_CLASS}.log \
    -H:+PrintOptimizationSummary \
    "$@" \
    $MAIN_CLASS

echo ""
echo "Bounds check analysis complete!"
echo "Check bounds_${MAIN_CLASS}.log for eliminated checks"

