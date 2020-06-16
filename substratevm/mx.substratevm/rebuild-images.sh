#!/usr/bin/env bash
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

source="${BASH_SOURCE[0]}"
while [[ -h "$source" ]] ; do
    prev_source="$source"
    source="$(readlink "$source")";
    if [[ "$source" != /* ]]; then
        # if the link was relative, it was relative to where it came from
        dir="$( cd -P "$( dirname "$prev_source" )" && pwd )"
        source="$dir/$source"
    fi
done
location="$( cd -P "$( dirname "$source" )" && pwd )"

# we assume we are in `lib/svm/bin`
graalvm_home="${location}/../../.."

function usage_and_exit() {
    echo "Usage: $0 [--verbose] polyglot|libpolyglot|js|llvm|python|ruby|R... [custom native-image args]..."
    exit 1
}

to_build=()
custom_args=()

for opt in "${@:1}"; do
    case "$opt" in
        polyglot|libpolyglot|js|llvm|python|ruby|R)
           to_build+=("${opt}")
            ;;
        --help|-h)
            echo "Rebuilds native images in place."
            usage_and_exit
            ;;
        --verbose|-v)
            VERBOSE=true
            ;;
        *)
            custom_args+=("${opt}")
            ;;
    esac
done

if [[ "${#to_build[@]}" == "0" ]]; then
    echo "nothing to build"
    usage_and_exit
fi

function common() {
    cmd_line+=(
        "${graalvm_home}/bin/native-image"
        ${custom_args[@]}
    )

    if $(${graalvm_home}/bin/native-image --help-extra | grep -q "\-\-no\-server"); then
        cmd_line+=("--no-server")
    fi

    if [[ -f "${graalvm_home}/lib/svm/builder/svm-enterprise.jar" ]]; then
        cmd_line+=("-g")
    fi
}

function polyglot_common() {
    cmd_line+=("--language:all")
}

function libpolyglot() {
    common
    polyglot_common
    cmd_line+=("--macro:polyglot-library")
}

function launcher() {
    common
    local launcher="$1"
    cmd_line+=("--macro:${launcher}-launcher")
    if [[ "$launcher" == "polyglot" ]]; then
        polyglot_common
    fi
}

for binary in "${to_build[@]}"; do
    cmd_line=()
    case "${binary}" in
        polyglot)
            launcher polyglot
            ;;
        libpolyglot)
            libpolyglot
            ;;
        js)
            launcher js
            ;;
        llvm)
            launcher lli
            ;;
        python)
            launcher graalpython
            ;;
        ruby)
            launcher truffleruby
            ;;
        R)
            launcher RMain
            ;;
        *)
            echo "shouldNotReachHere()"
            exit 1
            ;;
    esac
    echo "Building ${binary}..."
    if [[ ! -z "${VERBOSE}" ]]; then
        echo "${cmd_line[@]}"
    fi
    "${cmd_line[@]}"
done
