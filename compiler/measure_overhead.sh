#!/bin/bash

# Function that takes 2 arguments:
# 1: Number of iterations
# 2: Output CSV file name

# Check if the correct number of arguments is provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <number_of_iterations> <csv_file_name>"
    exit 1
fi

# Arguments
DACAPO_ITERATIONS=$1   # Number of iterations passed as first argument
CSV_FILE=$2            # CSV file name passed as second argument
DATA_DIR="../data"        # Directory where CSV will be stored

# Create the data directory if it doesn't exist
if [ ! -d "$DATA_DIR" ]; then
    mkdir -p "$DATA_DIR"
fi

# Set the full path for the CSV file in the data directory
CSV_PATH="$DATA_DIR/$CSV_FILE"

# Initialize the CSV file with headers
echo "iteration,time(ms)" > $CSV_PATH
total_time=0

# Run the DaCapo benchmark with multiple iterations
output=$(mx -J-Djava.library.path=/workspace/graal/vincent vm \
    -Dgraal.EnableForeignCallProfiler=true -Dgraal.EnableCustomIRProfiler=false \
    -Xmx10g \
    --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
    -javaagent:../joonhwan/agent-joon.jar \
    -jar ../dacapo/dacapo-9.12-bach.jar -n $DACAPO_ITERATIONS sunflow 2>&1)

# Extract and log all warmup times to the CSV file
warmup_times=$(echo "$output" | grep "completed warmup" | awk '{print $9}')
iteration=1

for warmup_time in $warmup_times; do
    echo "$iteration,$warmup_time" >> $CSV_PATH
    total_time=$(echo "$total_time + $warmup_time" | bc)
    iteration=$((iteration + 1))
done

final_time=$(echo "$output" | grep "PASSED" | awk '{print $7}')
echo "$iteration,$final_time" >> $CSV_PATH
total_time=$(echo "$total_time + $final_time" | bc)
