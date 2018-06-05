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

# we assume we are in `jre/lib/svm/bin`
graalvm_home="${location}/../../../.."

supported_tools=(
    "agent"
    "chromeinspector"
    "profiler"
)

supported_languages=(
    "js"
    "llvm"
    "python"
    "ruby"
)

function usage_and_exit() {
    echo "Usage: $0 [--verbose] polyglot|libpolyglot|js|llvm|python|ruby..."
    exit 1
}

to_build=()

for opt in "${@:1}"; do
    case "$opt" in
        polyglot|libpolyglot|js|llvm|python|ruby)
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
            echo "Unrecognized argument: '${opt}'"
            usage_and_exit
    esac
done

if [[ "${#to_build[@]}" == "0" ]]; then
    echo "nothing to build"
    usage_and_exit
fi

function common() {
    cmd_line+=(
        "${graalvm_home}/bin/native-image"
        "--no-server"
    )

    if [[ -f "${graalvm_home}/jre/lib/svm/builder/svm-enterprise.jar" ]]; then
        cmd_line+=("-g")
    fi

    for tool in "${supported_tools[@]}"; do
        if [[ -d "${graalvm_home}/jre/tools/${tool}" ]]; then
            cmd_line+=("--tool:${tool}")
        fi
    done
}

function polyglot_common() {
    common

    for language in "${supported_languages[@]}"; do
        if [[ -d "${graalvm_home}/jre/languages/${language}" ]]; then
            cmd_line+=("--language:${language}")
        fi
    done
}

function launcher_common() {
    cmd_line+=("-H:-ParseRuntimeOptions")
}

function polyglot() {
    polyglot_common
    launcher_common
    cmd_line+=(
        "-H:Features=org.graalvm.launcher.PolyglotLauncherFeature"
        "-Dorg.graalvm.launcher.relative.home=jre/bin/polyglot"
        "-H:Name=polyglot"
    )
    set_path "${graalvm_home}/jre/bin"
    cmd_line+=(
        "org.graalvm.launcher.PolyglotLauncher"
    )
}

function libpolyglot() {
    polyglot_common
    cmd_line+=(
        "-cp"
        "${graalvm_home}/jre/lib/polyglot/polyglot-native-api.jar:${graalvm_home}/jre/languages/js/trufflenode.jar"
        "-Dgraalvm.libpolyglot=true"
        "-H:JNIConfigurationResources=svmnodejs.jniconfig"
        "-H:Features=org.graalvm.polyglot.nativeapi.PolyglotNativeAPIFeature"
        "-Dorg.graalvm.polyglot.nativeapi.libraryPath=${graalvm_home}/jre/lib/polyglot"
        "-Dorg.graalvm.polyglot.nativeapi.nativeLibraryPath=${graalvm_home}/jre/lib/polyglot"
        "-H:CStandard=C11"
        "-H:Name=libpolyglot"
        "-H:Kind=SHARED_LIBRARY"
    )
    set_path "${graalvm_home}/jre/lib/polyglot"
}

function language() {
    common
    launcher_common
    local lang="$1"
    local relative_path="$2"
    local launcher_class="$3"
    if [[ "${lang}" = "ruby" || "${lang}" = "python" ]]; then
        cmd_line+=("--language:llvm")
    fi
    cmd_line+=(
        "--language:${lang}"
        "-Dorg.graalvm.launcher.relative.language.home=${relative_path}"
        "-Dorg.graalvm.launcher.standalone=false"
        "-H:Name=$(basename ${relative_path})"
    )
    set_path "${graalvm_home}/jre/languages/${lang}/$(dirname ${relative_path})"
    cmd_line+=(
        "${launcher_class}"
    )
}

function set_path() {
    cmd_line+=("-H:Path=$1")
}

for binary in "${to_build[@]}"; do
    cmd_line=()
    case "${binary}" in
        polyglot)
            polyglot
            ;;
        libpolyglot)
            libpolyglot
            ;;
        js)
            language js "bin/js" "com.oracle.truffle.js.shell.JSLauncher"
            ;;
        llvm)
            language llvm "bin/lli" "com.oracle.truffle.llvm.launcher.LLVMLauncher"
            ;;
        python)
            language python "bin/graalpython" "com.oracle.graal.python.shell.GraalPythonMain"
            ;;
        ruby)
            language ruby "bin/ruby" "org.truffleruby.launcher.RubyLauncher"
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
