#!/bin/bash
# Compare Analyses - Run both intra and inter procedural
# Compares results to show the benefit of context sensitivity

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <MainClass>"
    echo "Example: $0 LoopSummation"
    exit 1
fi

MAIN_CLASS=$1
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
COMPARE_DIR="compare_${MAIN_CLASS}_${TIMESTAMP}"

mkdir -p "$COMPARE_DIR"

echo "========================================"
echo "Running Comparison Analysis"
echo "Main Class: $MAIN_CLASS"
echo "Output: $COMPARE_DIR/"
echo "========================================"

# Run intraprocedural
echo ""
echo "[1/2] Running intraprocedural analysis..."
native-image \
    -H:+RunAbstractInterpretation \
    -H:+IntraproceduralAnalysis \
    -H:-InterproceduralAnalysis \
    -H:AILogFilePath="$COMPARE_DIR/intra.log" \
    -H:+PrintAIStatistics \
    $MAIN_CLASS > "$COMPARE_DIR/intra_build.log" 2>&1 && echo "  ✓ Intra complete" || echo "  ✗ Intra failed"

# Run interprocedural
echo "[2/2] Running interprocedural analysis..."
native-image \
    -H:+RunAbstractInterpretation \
    -H:+InterproceduralAnalysis \
    -H:-IntraproceduralAnalysis \
    -H:AILogFilePath="$COMPARE_DIR/inter.log" \
    -H:+PrintAIStatistics \
    $MAIN_CLASS > "$COMPARE_DIR/inter_build.log" 2>&1 && echo "  ✓ Inter complete" || echo "  ✗ Inter failed"

echo ""
echo "========================================"
echo "Comparison Complete!"
echo "Intra: $COMPARE_DIR/intra.log"
echo "Inter: $COMPARE_DIR/inter.log"
echo ""
echo "Compare with:"
echo "  diff $COMPARE_DIR/intra.log $COMPARE_DIR/inter.log"
echo "  grep 'optimized' $COMPARE_DIR/*.log"
echo "========================================"

