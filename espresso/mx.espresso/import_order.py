#
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

"""Provides import ordering capabilities for .java files."""

from glob import iglob

# If a given line is an import, it will end with this suffix.
# Used to strip this suffix for faster string comparison.
SUFFIX = ";\n"

STATIC_PREFIX = "import static "
REGULAR_PREFIX = "import "

def verify_order(path, prefix_order):
    """
    Verifies import order of java files under the given path.

    Iterates over all '.java' files in the given path, recursively over its subfolders.
    It then checks that imports in these files are ordered.

    Here are the rules:
    1. All imports that starts with a suffix that appears in this list must
       be found before any other import with a suffix that appears later in
       this list.
    2. All imports with a given suffix must be in lexicographic order within
       all other imports with the same prefix.
    3. Static imports must appear before regular imports.

    :param path: Where to look for the java files.
    :param prefix_order: An ordered list of expected suffixes.
    :return: The list of files violating the specified order.
    """

    # Validate the prefixes
    err = validate_format(prefix_order)
    if err:
        # Failure is represented with a non-empty list
        return [err]

    # Start building definitive list of import prefixes
    static_prefixes = []
    regular_prefixes = []

    for prefix in prefix_order:
        if prefix:
            # If prefix is "abc", add "import abc"
            regular_prefixes.append(REGULAR_PREFIX + prefix + '.')
            # Eclipse formatting does not enforce prefix order for static imports.
        else:
            # Empty prefix means everything will match.
            # Empty prefix is added manually below.
            break

    # Ensure we have the empty prefix
    # Add "import static "
    static_prefixes.append(STATIC_PREFIX)
    # Add "import "
    regular_prefixes.append(REGULAR_PREFIX)

    # Ensures static imports are before regular imports.
    prefix_format = static_prefixes + regular_prefixes

    invalid_files = []

    def is_sorted(li):
        if len(li) <= 1:
            return True
        return all(li[i] <= li[i + 1] for i in range(len(li) - 1))

    def check_file(to_check, prefix_format):
        imports, prefix_ordered = get_imports(to_check, prefix_format)

        if not prefix_ordered:
            return False

        for import_list in imports:
            if not is_sorted(import_list):
                return False

        return True

    for file in iglob(path + '/**/*.java', recursive=True):
        if not check_file(file, prefix_format):
            invalid_files.append(file)

    return invalid_files

def validate_format(prefix_order):
    """
    Validates a given ordered list of prefix for import order verification.

    Returns the reason for failure of validation if any, or an empty string
    if the prefixes are well-formed.
    """
    for prefix in prefix_order:
        if prefix.endswith('.'):
            return "Invalid format for the ordered prefixes: \n'" + prefix + "' must not end with a '.'"
    return ""

def get_imports(file, prefix_format):
    """
    Obtains list of imports list, each corresponding to each specified prefix.
    Also returns whether the found prefixes were ordered.

    In case the prefixes where not ordered, the last element of the returned list will contain
    every import after the violating line
    """
    def add_import(li, value, prefix, suf):
        to_add = value[len(prefix):]
        if to_add.endswith(suf):
            to_add = to_add[:len(to_add) - len(suf)]
        li.append(to_add)

    def enter_fail_state(imports, prefix_format, cur_prefix_imports):
        if cur_prefix_imports:
            imports.append(cur_prefix_imports)
        return False, len(prefix_format), ""

    with open(file) as f:
        imports = []
        prefix_ordered = True

        cur_prefix_idx = 0
        cur_prefix = prefix_format[cur_prefix_idx]

        cur_prefix_imports = []

        for line in f.readlines():
            ignore = not line.startswith("import")
            if ignore:
                # start of class declaration, we can stop looking for imports.
                end = 'class ' in line or 'interface ' in line or 'enum ' in line or 'record ' in line
                if end:
                    break
                continue

            if line.startswith(cur_prefix):
                # If we are still ensuring prefix ordering, ensure that this line does not belong
                # to a previous prefix.
                if prefix_ordered:
                    for i in range(cur_prefix_idx):
                        if line.startswith(prefix_format[i]):
                            # A match for a previous prefix was found: enter fail state
                            prefix_ordered, cur_prefix_idx, cur_prefix = enter_fail_state(imports, prefix_format, cur_prefix_imports)
                            cur_prefix_imports = []
                add_import(cur_prefix_imports, line, cur_prefix, SUFFIX)
            else:
                # cur_prefix not found, advance to next prefix if found, report failure if not.
                for i in range(cur_prefix_idx + 1, len(prefix_format)):
                    if line.startswith(prefix_format[i]):
                        # Report imports for current prefix,
                        if cur_prefix_imports:
                            imports.append(cur_prefix_imports)
                        # Set state to next prefix.
                        cur_prefix = prefix_format[i]
                        cur_prefix_idx = i
                        cur_prefix_imports = []
                        add_import(cur_prefix_imports, line, cur_prefix, SUFFIX)
                        break
                else:
                    # On failure, dump remaining lines into the last cur_prefix_imports.
                    prefix_ordered, cur_prefix_idx, cur_prefix = enter_fail_state(imports, prefix_format, cur_prefix_imports)
                    cur_prefix_imports = []
                    add_import(cur_prefix_imports, line, cur_prefix, SUFFIX)

        if cur_prefix_imports:
            imports.append(cur_prefix_imports)

        return imports, prefix_ordered
