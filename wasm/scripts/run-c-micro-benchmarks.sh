#!/bin/sh

# Runs all benchmarks from the microbenchmark suite.

set -e

RESULTS_FILE_PATH=$1
VM=$2
VM_CONFIG=$3
UPLOAD_CMD=$4

for benchmark in cdf digitron event-sim fft
do
  mx --dy /compiler --kill-with-sigquit benchmark \
    "--machine-name=${MACHINE_NAME}" \
    "--results-file=${RESULTS_FILE}" \
    wasm:WASM_BENCHMARKCASES -- \
    --jvm ${VM} --jvm-config ${VM_CONFIG} \
    -Dwasmbench.benchmarkName=$benchmark -Dwasmtest.keepTempFiles=true -- \
    CMicroBenchmarkSuite

  ${UPLOAD_CMD} "${RESULTS_FILE_PATH}"
done

