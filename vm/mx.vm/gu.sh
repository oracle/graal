#!/usr/bin/env bash
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
bin_dir="$( cd -P "$( dirname "$source" )" && pwd )"
installer_dir="${bin_dir}"/..
root_dir="${installer_dir}/../.."
java_exe="${root_dir}/bin/java"

JAVA_ARGS=()
PROGRAM_ARGS=()
for opt in "${@:1}"
do
    case $opt in
        -J:*|--jvm*)
            opt="${opt:3}"
            JAVA_ARGS+=("$opt") ;;
        *)
            PROGRAM_ARGS+=("$opt") ;;
    esac
done

libs=""
# Add possible drop-in extensions
for x in "${installer_dir}"/*.jar ; do
    if [ -z "$libs" ]; then
        libs="$x"
    else
        libs="${libs}:$x"
    fi
done
exec "${java_exe}" "${JAVA_ARGS[@]}" "-DGRAAL_HOME=${root_dir}" -cp "${libs}" org.graalvm.component.installer.ComponentInstaller "${PROGRAM_ARGS[@]}"
