#
# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import os
import pathlib
import shutil

from pathlib import Path

ROOTS = ['src']
SOURCE_EXTENSIONS = ['*.c', '*.cpp']
LIB_NAME = 'libcontainer'
CONFIGS = ['product', 'fastdebug', 'debug']

# Find all source files.
cur_dir = os.getcwd()
source_files = []
for root in ROOTS:
    for ext in SOURCE_EXTENSIONS:
        pattern = f'**{os.sep}{ext}'
        source_files += Path(root).glob(pattern)

# Determine which libc variants are supported.
libcs = [("glibc", "g++", "ar")]
if "MUSL_TOOLCHAIN" in os.environ:
    musl_gcc = os.environ["MUSL_TOOLCHAIN"] + "/bin/x86_64-linux-musl-c++"
    musl_ar = os.environ["MUSL_TOOLCHAIN"] + "/bin/x86_64-linux-musl-gcc-ar"
    libcs += [("musl", musl_gcc, musl_ar)]

# Generate rules for all configs.
rules = ""
for config in CONFIGS:
    # Generate rules for all libc variants.
    for libc, gcc, ar in libcs:
        # Generate cxx.
        rules += os.linesep
        rules += f"cxx_{config}_{libc} = {gcc}{os.linesep}"

        # Generate cxx rule.       
        for file_type in ["a", "so"]:
            rules += os.linesep
            rules += f"rule cxx_{config}_{file_type}_{libc}{os.linesep}"
            rules += f"  command = $cxx_{config}_{libc} -MMD -MT $out -MF $out.d $cflags_linux_{config}_{file_type} -c $in -o $out{os.linesep}"
            rules += f"  description = CXX $out{os.linesep}"
            rules += f"  depfile = $out.d{os.linesep}"
            rules += f"  deps = gcc{os.linesep}"

        # Generate ar rule.
        rules += os.linesep
        rules += f"ar_{config}_{libc} = {ar}{os.linesep}"

        rules += os.linesep
        rules += f"rule ar_{config}_{libc}{os.linesep}"
        rules += f"  command = rm -f $out && $ar_{config}_{libc} crs $out $in{os.linesep}"
        rules += f"  description = AR $out{os.linesep}"

        # Generate rule to build source files into object files for static lib.
        rules += os.linesep
        rules += "# Build source files into object files."
        rules += os.linesep
        
        for path in source_files:
            rules += f'build $builddir{os.sep}{config}{os.sep}a{os.sep}{libc}{os.sep}{path.parent}{os.sep}{path.stem}.o: cxx_{config}_a_{libc} $root{os.sep}{path}{os.linesep}'

        # Generate rule to create a static lib from all the object files.
        rules += os.linesep
        rules += "# Create a static lib from all the object files."
        rules += os.linesep
        rules += f'build $builddir{os.sep}{LIB_NAME}_{config}_{libc}.a: ar_{config}_{libc}'

        for path in source_files:
            rules += f' ${os.linesep}'
            rules += f'    $builddir{os.sep}{config}{os.sep}a{os.sep}{libc}{os.sep}{path.parent}{os.sep}{path.stem}.o'

        rules += os.linesep

        # Generate a build target for the static lib.
        rules += os.linesep
        rules += f'build build_{config}_{libc}_a: phony $builddir/{LIB_NAME}_{config}_{libc}.a{os.linesep}'
        
        # Generate link.
        rules += os.linesep
        rules += f"link_{config}_{libc} = {gcc}{os.linesep}"
        
        # Generate link rule.
        rules += os.linesep
        rules += f"rule link_{config}_{libc}{os.linesep}"
        rules += f"  command = $link_{config}_{libc} $ldflags_linux_{config} -o $out $in{os.linesep}"
        rules += f"  description = LINK $out{os.linesep}"
        
        # Generate rule to build source files into object files for shared lib.
        rules += os.linesep
        rules += "# Build source files into object files for shared library."
        rules += os.linesep
        
        for path in source_files:
            rules += f'build $builddir{os.sep}{config}{os.sep}so{os.sep}{libc}{os.sep}{path.parent}{os.sep}{path.stem}.o: cxx_{config}_so_{libc} $root{os.sep}{path}{os.linesep}'
       
        # Generate rule to create a shared lib from all the object files.
        rules += os.linesep
        rules += "# Create a shared lib from all the object files."
        rules += os.linesep
        rules += f'build $builddir{os.sep}{LIB_NAME}_{config}_{libc}.so: link_{config}_{libc}'

        for path in source_files:
            rules += f' ${os.linesep}'
            rules += f'    $builddir{os.sep}{config}{os.sep}so{os.sep}{libc}{os.sep}{path.parent}{os.sep}{path.stem}.o'
        
        rules += os.linesep
       
        # Generate a build target for the shared lib.
        rules += os.linesep
        rules += f'build build_{config}_{libc}_so: phony $builddir{os.sep}{LIB_NAME}_{config}_{libc}.so{os.linesep}'
    
    # Generate config-specific build targets.
    rules += os.linesep
    rules += "# Build targets."
    rules += os.linesep
    for file_type in ["a", "so"]:
        rules += f"build build_{config}_{file_type}: phony"
        for libc, _, _ in libcs:
            rules += f" build_{config}_{libc}_{file_type}"
        rules += os.linesep

    rules += f"build build_{config}: phony"
    for file_type in ["a", "so"]:
        rules += f" build_{config}_{file_type}"
    rules += os.linesep

# Generate target that builds all libs.
rules += os.linesep
rules += f"build build_all: phony"
for config in CONFIGS:
    rules += f" build_{config}"
rules += os.linesep
rules += os.linesep
rules += f"default build_all{os.linesep}"

# Append the rules to the ninja template.
shutil.copyfile('ninja.template', 'build.ninja')
with open('build.ninja', 'a') as file:
    file.write(rules)
