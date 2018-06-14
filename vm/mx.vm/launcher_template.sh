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

jvm_args=()
launcher_args=()

# Unfortunately, parsing of `--jvm.*` arguments has to be done blind:
# Maybe some of those arguments where not really intended for the launcher but where application arguments

for o in "$@"; do
    if [[ "$o" == --jvm.* ]]; then
        jvm_arg="${o#--jvm.}"
        if [[ "$jvm_arg" == "cp" ]]; then
            >&2 echo "--jvm.cp argument must be of the form --jvm.cp=<classpath>, not two separate arguments"
            exit 1
        fi
        if [[ "$jvm_arg" == "classpath" ]]; then
            >&2 echo "--jvm.classpath argument must be of the form --jvm.classpath=<classpath>, not two separate arguments"
            exit 1
        fi
        if [[ "$jvm_arg" == "cp="* ]]; then
            custom_cp=${jvm_arg#cp=}
        elif [[ "$jvm_arg" == "classpath="* ]]; then
            custom_cp=${jvm_arg#classpath=}
        fi
        if [[ -z "${custom_cp+x}" ]]; then
            jvm_args+=("-${jvm_arg}")
        else
            IFS=: read -ra custom_cp_a <<< "${custom_cp}"
            for e in "${custom_cp_a[@]}"; do
                absolute_cp+=("${e}")
            done
        fi
    else
        launcher_args+=("$o")
    fi
done

cp="$(IFS=: ; echo "${absolute_cp[*]}")"

exec "${location}/<jre_bin>/java" "${jvm_args[@]}" -cp "${cp}" "<main_class>" "${launcher_args[@]}"
