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

import mx
import os
import re
import argparse


suite = mx.suite("substratevm")

# Names of symbols that are currently accessed globally (e.g., ::swap(...)), but after adding the
# namespace are not global symbols anymore.
qualify_with_namespace = {"swap", "CardTableBarrierSet", "G1BarrierSet", "tty", "badHeapWordVal", "badAddressVal"}

# Some files must not have the namespace added, as they are included within a class definition. Normally includes
# need to be outside the namespace, but the includes of these files need to be inside the namespace.
ignore_files = {"copy_x86.hpp", "copy_aarch64.hpp", "osThread_linux.hpp"}
ignore_includes = {"CPU_HEADER(copy)", "OS_HEADER(osThread)"}

files_with_cpp_guard = {"sharedGCStructs.hpp"}

SVM_NAMESPACE = "svm_namespace"


@mx.command(suite, SVM_NAMESPACE)
def svm_namespace(args):

    parser = argparse.ArgumentParser(SVM_NAMESPACE)

    parser.add_argument("command", choices=["add", "remove"], metavar="add/remove",
                        help="Command whether to add or remove the namespace.")

    pathGroup = parser.add_mutually_exclusive_group(required=True)
    pathGroup.add_argument("-d", "--directory", type=str, help="Path to the src-directory for modifying the namespace.")
    pathGroup.add_argument("-f", "--files", type=str, nargs="+", help="Path to the files for modifying the namespace.")

    parser.add_argument("-n", "--namespace", required=True, type=str,
                        help="The namespace that gets added or removed to the files.")

    parsed_args = parser.parse_args(args)

    if parsed_args.command == "add":
        svm_add_namespace(parsed_args)
    elif parsed_args.command == "remove":
        svm_remove_namespace(parsed_args)
    else:
        mx.abort(f"Unknown command: {parsed_args.command}")

def svm_add_namespace(args):
    namespaceName = args.namespace

    if args.directory:
        # src-directory specified, add namespace to all files.
        add_namespace(args.directory, namespaceName)
    else:
        for file in args.files:
            if not is_c_file(file):
                continue

            if not os.path.isfile(file):
                print(f"Skipping {file}. File does not exist.")
            else:
                add_namespace_to_file(file, os.path.basename(file) in files_with_cpp_guard, namespaceName)


def is_c_file(file):
    return file.endswith(".hpp") or file.endswith(".h") or file.endswith(".cpp") or file.endswith(".c")


def add_namespace(svmRootDirectory, namespaceName):
    for subdir, _, files in os.walk(svmRootDirectory):
        for file in files:
            if is_c_file(file):

                if file not in ignore_files:
                    print(f"Add namespace to {os.path.join(subdir, file)}")
                    add_namespace_to_file(os.path.join(subdir, file), file in files_with_cpp_guard, namespaceName)

                else:
                    print(f"Ignore file: {os.path.join(subdir, file)}")


def add_namespace_to_file(file, add_cpp_guard, namespaceName):
    namespaceBeginWithGuard = f"\n#ifdef __cplusplus\n  namespace {namespaceName} {{\n#endif\n\n"
    namespaceEndWithGuard = f"\n#ifdef __cplusplus\n  }} // namespace {namespaceName}\n#endif\n\n"

    namespaceBegin = f"\nnamespace {namespaceName} {{\n\n"
    namespaceEnd = f"\n}} // namespace {namespaceName}\n\n"

    with open(file, 'r') as f:
        lines = f.readlines()
        insertion_indices = calc_insert_indices(lines)

    with open(file, 'w') as sf:
        i = 0
        for (start, end) in insertion_indices:
            print(f"  start {start}, end {end}")
            while i < start:
                write_str(sf, lines[i], namespaceName)
                i += 1

            if add_cpp_guard:
                sf.write(namespaceBeginWithGuard)
            else:
                sf.write(namespaceBegin)

            while i < end:
                write_str(sf, lines[i], namespaceName)
                i += 1

            if add_cpp_guard:
                sf.write(namespaceEndWithGuard)
            else:
                sf.write(namespaceEnd)

        while i < len(lines):
            write_str(sf, lines[i], namespaceName)
            i += 1


def write_str(file, s, namespaceName):
    pattern = re.compile(r"[^a-zA-Z0-9>_]::\w")
    match = pattern.search(s)

    while match:
        qualifiedName = ""
        i = 3
        while is_valid_identifier_character(s[match.start() + i]):
            qualifiedName += s[match.start() + i]
            i += 1

        if qualifiedName in qualify_with_namespace:
            s = s[:match.start() + 1] + namespaceName + s[match.start() + 1:]

        match = pattern.search(s, match.end())

    file.write(s)


def calc_insert_indices(lines):
    indices = []
    idx_namespace_begin = 0

    # skip the header
    for i in range(len(lines)):
        if not is_header_line(lines[i]):
            idx_namespace_begin = i
            break

    # Earliest line for namespace is directly after header, if no #include are following

    # Keep track of the current open #ifs in general and inside the namespace, so the namespace is opened and closed
    # on the same level.
    cur_n_open_ifs = 0
    namespace_n_open_ifs = 0
    namespace_open = False

    i = idx_namespace_begin
    while i < len(lines):
        line = lines[i]

        # skip block comments
        if line.lstrip().startswith("/*"):
            end_index = line.find("*/")
            while end_index == -1 and i < len(lines):
                i += 1
                end_index = lines[i].find("*/")

            # Line containing the block comment end was found. Set line to the rest of this line.
            line = lines[i][end_index+2:]

        if namespace_open:
            if is_if_statement(line):
                cur_n_open_ifs += 1
                strippedLine = line.strip()
                while strippedLine.endswith("\\"):
                    # #if continues on the next line
                    i += 1
                    strippedLine = lines[i].strip()

            elif is_endif_statement(line):

                if namespace_n_open_ifs == cur_n_open_ifs:
                    # The #if in which the namespace was opened, is closed, so also close the namespace.
                    indices.append((idx_namespace_begin, i))
                    namespace_open = False

                cur_n_open_ifs -= 1
                assert cur_n_open_ifs >= 0

            elif is_else_statement(line) or is_elif_statement(line):

                if namespace_n_open_ifs == cur_n_open_ifs:
                    # The #if in which the namespace was opened, has an #else/#elsif part, so close the namespace
                    # before that.
                    indices.append((idx_namespace_begin, i))
                    namespace_open = False

                # The number of current ifs does not change as there exists an #else/#elsif part.
            elif is_include_statement(line):
                # The namespace is open but another include occurs. Close the namespace if necessary.
                if line[len("#include "):].strip() not in ignore_includes:
                    assert namespace_n_open_ifs <= cur_n_open_ifs

                    idx_namespace_end = i
                    end_namespace_open_ifs = cur_n_open_ifs
                    while namespace_n_open_ifs < end_namespace_open_ifs:
                        # Some #if was opened inside this namespace. Go back and close the namespace before this #if.
                        idx_namespace_end -= 1
                        if is_if_statement(lines[idx_namespace_end]):
                            end_namespace_open_ifs -= 1
                        elif is_endif_statement(lines[idx_namespace_end]):
                            end_namespace_open_ifs += 1

                    i = idx_namespace_end

                    indices.append((idx_namespace_begin, idx_namespace_end))
                    namespace_open = False

        else:
            # Namespace is not open
            if is_empty(line) or is_line_comment(line):
                pass
            elif is_preprocessor_directive(line):

                if is_if_statement(line):
                    cur_n_open_ifs += 1

                elif is_endif_statement(line):
                    cur_n_open_ifs -= 1
                    assert cur_n_open_ifs >= 0

                strippedLine = line.strip()
                while strippedLine.endswith("\\"):
                    # Preprocessor statement continues in the next line.
                    i += 1
                    strippedLine = lines[i].strip()

            else:

                if is_extern(line) and cur_n_open_ifs > 0:
                    # Extern is called inside an if, check if lines before and after are matching #if #endif
                    j = 0
                    while is_if_statement(lines[i - j - 1]) and is_endif_statement(lines[i + j + 1]):
                        j += 1

                    idx_namespace_begin = i - j
                    namespace_n_open_ifs = cur_n_open_ifs - j
                else:
                    idx_namespace_begin = i
                    namespace_n_open_ifs = cur_n_open_ifs

                namespace_open = True

        i += 1

    # Make sure the namespace is closed at the file end.
    if namespace_open:
        assert namespace_n_open_ifs <= cur_n_open_ifs

        idx_namespace_end = i + 1
        end_namespace_open_ifs = cur_n_open_ifs
        while namespace_n_open_ifs < end_namespace_open_ifs:
            # some #if was opened before this include
            idx_namespace_end -= 1
            if is_if_statement(lines[idx_namespace_end]):
                end_namespace_open_ifs -= 1
            elif is_endif_statement(lines[idx_namespace_end]):
                end_namespace_open_ifs += 1

        indices.append((idx_namespace_begin, idx_namespace_end - 1))

    return indices


def is_header_line(line):
    return line.startswith("/*") or line.startswith(" /*") or line.startswith(" *") or line.startswith("*")


def is_preprocessor_directive(line):
    stripped_line = line.lstrip()
    return stripped_line.startswith("#")


def is_preprocessor_directive_name(line, name):
    stripped_line = line.lstrip()
    if not is_preprocessor_directive(stripped_line):
        return False

    stripped_line = stripped_line[1:].lstrip()  # Remove # and whitespace from the line.
    return stripped_line.startswith(name)


def is_include_statement(line):
    return is_preprocessor_directive_name(line, "include ")


def is_if_statement(line):
    return is_preprocessor_directive_name(line, "if")


def is_else_statement(line):
    return is_preprocessor_directive_name(line, "else")


def is_elif_statement(line):
    return is_preprocessor_directive_name(line, "elif")


def is_endif_statement(line):
    return is_preprocessor_directive_name(line, "endif")


def is_define_statement(line):
    return is_preprocessor_directive_name(line, "define")


def is_empty(line):
    stripped_line = line.strip()
    return len(stripped_line) == 0


def is_line_comment(line):
    stripped_line = line.lstrip()
    return stripped_line.startswith("//")


def is_extern(line):
    stripped_line = line.lstrip()
    return stripped_line.startswith("extern ")


def is_valid_identifier_character(c):
    return c == '_' or ('a' <= c <= 'z') or ('A' <= c <= 'Z') or ('0' <= c <= '9')


def svm_remove_namespace(args):
    namespaceName = args.namespace

    if args.directory:
        # src-directory specified, remove namespace from all files.
        remove_namespace(args.directory, namespaceName)
    else:
        for file in args.files:
            if not is_c_file(file):
                continue

            if not os.path.isfile(file):
                print(f"Skipping {file}. File does not exist.")
            else:
                remove_namespace_from_file(file, os.path.basename(file) in files_with_cpp_guard, namespaceName)


def remove_namespace(svmRootDirectory, namespaceName):
    for subdir, _, files in os.walk(svmRootDirectory):
        for file in files:
            if is_c_file(file):

                if file not in ignore_files:
                    print(f"Remove namespace from {os.path.join(subdir, file)}")
                    remove_namespace_from_file(os.path.join(subdir, file), file in files_with_cpp_guard, namespaceName)

                else:
                    print(f"Ignore file: {os.path.join(subdir, file)}")


def remove_namespace_from_file(file, add_cpp_guard, namespaceName):
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
