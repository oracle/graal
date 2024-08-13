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

# Removes a C++-namespace from the source files.
# If no command line arguments are passed, the namespace is removed from all files.
# It is possible to pass filepaths as command line arguments. Then the namespace is only removed from the 
# specified files.


import os
import re
import argparse

from addNamespace import *


def main():
    parser = argparse.ArgumentParser()

    pathGroup = parser.add_mutually_exclusive_group(required=True)
    pathGroup.add_argument("-d", "--directory", type=str, help="Path to the src-directory for removing the namespace.")
    pathGroup.add_argument("-f", "--files", type=str, nargs="+", help="Path to the files for removing the namespace.")

    parser.add_argument("-n", "--namespace", required=True, type=str,
                        help="The namespace that gets removed from the files.")

    args = parser.parse_args()

    global namespaceName 
    namespaceName = args.namespace

    global namespaceBeginWithGuard
    global namespaceEndWithGuard
    global namespaceBegin
    global namespaceEnd

    namespaceBeginWithGuard = f"\n#ifdef __cplusplus\n  namespace {namespaceName} {{\n#endif\n\n"
    namespaceEndWithGuard = f"\n#ifdef __cplusplus\n  }} // namespace {namespaceName}\n#endif\n\n"

    namespaceBegin = f"\nnamespace {namespaceName} {{\n\n"
    namespaceEnd = f"\n}} // namespace {namespaceName}\n\n"

    if args.directory:
        # src-directory specified, remove namespace from all files.
        remove_namespace(args.directory)
    else:
        for file in args.files:
            if not is_c_file(file):
                continue

            if not os.path.isfile(file):
                print(f"Skipping {file}. File does not exist.")
            else:
                remove_namespace_from_file(file, os.path.basename(file) in files_with_cpp_guard)


def remove_namespace(svmRootDirectory):
    for subdir, dirs, files in os.walk(svmRootDirectory):
        for file in files:
            if is_c_file(file):

                if file not in ignore_files:
                    print(f"Remove namespace from {os.path.join(subdir, file)}")
                    remove_namespace_from_file(os.path.join(subdir, file), file in files_with_cpp_guard)

                else:
                    print(f"Ignore file: {os.path.join(subdir, file)}")


def remove_namespace_from_file(file, add_cpp_guard):
    with open(file, 'r') as f:
        lines = f.readlines()

    namespace_open = False

    if add_cpp_guard:
        begin = f"  namespace {namespaceName} {{"
        end = f"  }} // namespace {namespaceName}"
    else:
        begin = f"namespace {namespaceName} {{"
        end = f"}} // namespace {namespaceName}"

    for i in range(len(lines)):
        if not namespace_open:
            assert not lines[i].startswith(end)

            if lines[i].startswith(begin):
                namespace_open = True
                print(f"  start {i}", end="")

                lines[i] = ""

                if add_cpp_guard:
                    lines[i - 1] = ""
                    lines[i + 1] = ""
                    lines[i - 2] = remove_if_newline(lines[i - 2])
                    lines[i + 2] = remove_if_newline(lines[i + 2])
                else:
                    lines[i - 1] = remove_if_newline(lines[i - 1])
                    lines[i + 1] = remove_if_newline(lines[i + 1])
        else:
            assert not lines[i].startswith(begin)

            if lines[i].startswith(end):
                namespace_open = False
                print(f", end {i}")

                lines[i] = ""

                if add_cpp_guard:
                    lines[i - 1] = ""
                    lines[i + 1] = ""
                    lines[i - 2] = remove_if_newline(lines[i - 2])
                    lines[i + 2] = remove_if_newline(lines[i + 2])
                else:
                    lines[i - 1] = remove_if_newline(lines[i - 1])
                    lines[i + 1] = remove_if_newline(lines[i + 1])

        pattern = re.compile(f"{namespaceName}::\\w")
        match = pattern.search(lines[i])

        while match:
            lines[i] = lines[i][:match.start()] + lines[i][match.start() + len(namespaceName):]
            match = pattern.search(lines[i])

    with open(file, 'w') as sf:
        for line in lines:
            sf.write(line)


def remove_if_newline(line):
    return "" if line == "\n" else line


if __name__ == "__main__":
    main()
