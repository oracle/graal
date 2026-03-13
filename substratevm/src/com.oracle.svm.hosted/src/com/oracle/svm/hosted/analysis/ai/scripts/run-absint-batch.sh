#!/bin/bash
# Analyzes all Java files in the directory

set -e

TEST_DIR=${1:-.}
OUTPUT_DIR="batch_results_$(date +%Y%m%d_%H%M%S)"

echo "========================================"
echo "Running AI Batch Analysis"
echo "Test Directory: $TEST_DIR"
echo "Output: $OUTPUT_DIR/"
echo "========================================"

mkdir -p "$OUTPUT_DIR"

JAVA_FILES=$(find "$TEST_DIR" -maxdepth 1 -name "*.java" -type f)

if [ -z "$JAVA_FILES" ]; then
    echo "No Java files found in $TEST_DIR"
    exit 1
fi

COUNT=0
SUCCESS=0
FAILED=0

for file in $JAVA_FILES; do
    CLASS_NAME=$(basename "$file" .java)
    COUNT=$((COUNT + 1))
    echo ""
    echo "[$COUNT] Analyzing $CLASS_NAME..."

    if mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS  \
          -H:+ReportExceptionStackTraces \
          -H:Log=AbstractInterpretation \
          -H:Dump=:2 \
          -H:PrintGraph=Network \
          -H:MethodFilter=$MAIN_CLASS.* \
          -H:+RunAbstractInterpretation \
          -H:+InterproceduralAnalysis \
          -H:AILogLevel=INFO \
          -H:+AILogToFile \
          -H:AILogFilePath="$OUTPUT_DIR/${CLASS_NAME}.log" \
          -H:+PrintOptimizationSummary \
          $CLASS_NAME > "$OUTPUT_DIR/${CLASS_NAME}_build.log" 2>&1; then
          SUCCESS=$((SUCCESS + 1))
          echo "  ✓ Success"
    else
        FAILED=$((FAILED + 1))
        echo "  ✗ Failed (see $OUTPUT_DIR/${CLASS_NAME}_build.log)"
    fi
done

echo ""
echo "========================================"
echo "Batch Analysis Complete!"
echo "Total: $COUNT | Success: $SUCCESS | Failed: $FAILED"
echo "Results: $OUTPUT_DIR/"
echo "========================================"

