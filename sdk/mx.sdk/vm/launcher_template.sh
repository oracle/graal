#!/usr/bin/env bash
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, <year>, Oracle and/or its affiliates. All rights reserved.
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
while [ -h "$source" ] ; do
    prev_source="$source"
    source="$(readlink "$source")";
    if [[ "$source" != /* ]]; then
        # if the link was relative, it was relative to where it came from
        dir="$( cd -P "$( dirname "$prev_source" )" && pwd )"
        source="$dir/$source"
    fi
done
location="$( cd -P "$( dirname "$source" )" && pwd )"

IFS=: read -ra relative_cp <<< "<classpath>"
absolute_cp=()
for e in "${relative_cp[@]}"; do
    absolute_cp+=("${location}/${e}")
done

jvm_args=("-Dorg.graalvm.launcher.shell=true" "-Dorg.graalvm.launcher.executablename=$0")
launcher_args=()

# Unfortunately, parsing of `--jvm.*` and `--vm.*` arguments has to be done blind:
# Maybe some of those arguments where not really intended for the launcher but were application arguments

process_vm_arg() {
    local vm_arg="$1"
    if [[ "$vm_arg" == "cp" || "$vm_arg" == "classpath" ]]; then
        >&2 echo "'--vm.${vm_arg}' argument must be of the form '--vm.${vm_arg}=CLASSPATH', not two separate arguments."
        exit 1
    elif [[ "$vm_arg" == "cp="* || "$vm_arg" == "classpath="* ]]; then
        local prefix="${vm_arg%%=*}" # cp or classpath
        local classpath="${vm_arg#${prefix}=}"
        IFS=: read -ra classpath_array <<< "$classpath"
        for e in "${classpath_array[@]}"; do
            absolute_cp+=("$e")
        done
    else
        jvm_args+=("-$vm_arg")
    fi
}

process_arg() {
    if [[ "$1" == --jvm.* ]]; then
        >&2 echo "'--jvm.*' options are deprecated, use '--vm.*' instead."
        process_vm_arg "${1#--jvm.}"
    elif [[ "$1" == --vm.* ]]; then
        process_vm_arg "${1#--vm.}"
    elif [[ "$1" == --native || "$1" == --native.* ]]; then
        >&2 echo "The native version of '$(basename "$source")' does not exist: cannot use '$1'."
        if [[ $(basename "$source") == polyglot ]]; then
            local extra=' --language:all'
        else
            local extra=''
        fi
        >&2 echo "If native-image is installed, you may build it with 'native-image --macro:<macro_name>$extra'."
        exit 1
    else
        launcher_args+=("$1")
    fi
}

# Check option-holding variables.
# Those can be specified as the `option_vars` argument of the LauncherConfig constructor.
for var in <option_vars>; do
    read -ra opts <<< "${!var}"
    for opt in "${opts[@]}"; do
        [[ "$opt" == --vm.* ]] && process_vm_arg "${opt#--vm.}"
        [[ "$opt" == --jvm.* ]] && process_vm_arg "${opt#--jvm.}"
    done
done

for o in "$@"; do
    process_arg "$o"
done

cp="$(IFS=: ; echo "${absolute_cp[*]}")"

if [[ "${VERBOSE_GRAALVM_LAUNCHERS}" == "true" ]]; then
    set -x
fi

exec "${location}/<jre_bin>/java" <extra_jvm_args> "${jvm_args[@]}" -cp "${cp}" '<main_class>' "${launcher_args[@]}"
