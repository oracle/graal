#!/bin/bash

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run_script() {
    local script_path="$1"
    if [ -f "$script_path" ]; then
        if [ -x "$script_path" ]; then
            "$script_path"
        else
            bash "$script_path"
        fi
    else
        echo "Error: required script '$script_path' not found." >&2
        return 2
    fi
}

run_script "$DIR/clean.sh"
run_script "$DIR/prepare.sh"
