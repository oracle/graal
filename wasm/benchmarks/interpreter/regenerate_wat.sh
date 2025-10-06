#!/bin/sh
#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
#
# Regenerates .wat files from C sources preserving any existing license headers.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$WASI_SDK" ]; then
  echo "WASI_SDK is not set"
  exit 1
fi

if [ -z "$WABT_DIR" ]; then
  echo "WABT_DIR is not set"
  exit 1
fi

EXPORTED_FUNCTIONS="run OutlierRemovalAverageSummary OutlierRemovalAverageSummaryLowerThreshold OutlierRemovalAverageSummaryUpperThreshold"

EXPORT_FLAGS=""
for sym in $EXPORTED_FUNCTIONS; do
  EXPORT_FLAGS="$EXPORT_FLAGS -Wl,--export=$sym"
done

# ensures the license is preserved
extract_header() {
  wat_file="$1"
  header_lines=""
  if [ -f "$wat_file" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      case "$line" in
        "" )
          header_lines="${header_lines}
"
          ;;
        ';;'* )
          header_lines="${header_lines}${line}
"
          ;;
        * ) # end of header
          break
          ;;
      esac
    done < "$wat_file"
  fi
  printf '%s' "$header_lines"
}

for c_file in "$SCRIPT_DIR"/*.c; do
  wasm_file="${c_file%.c}.wasm"
  wat_file="${c_file%.c}.wat"

  if [ "$(basename "$c_file")" = "sieve.c" ]; then
    $WASI_SDK/bin/clang $EXPORT_FLAGS -Wl,-z,stack-size=4194304 -O3 -o "$wasm_file" "$c_file"
  else
    $WASI_SDK/bin/clang $EXPORT_FLAGS -O3 -o "$wasm_file" "$c_file"
  fi

  new_wat="$($WABT_DIR/bin/wasm2wat "$wasm_file")"

  license_header="$(extract_header "$wat_file")"

  {
    printf '%s' "$license_header"
    printf '\n'
    printf '%s' "$new_wat"
    printf '\n'
  } > "$wat_file"
done
