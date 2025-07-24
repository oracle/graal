#
# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
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
# pylint: skip-file
#
# python utility code for use by the gdb scripts that test native debug info

import gdb
import re
import sys


# set various gdb operating modes to desired setting

def configure_gdb():
    # disable prompting to continue output
    execute("set pagination off")
    # enable pretty printing of structures
    execute("set print pretty on")
    # enable demangling of symbols in assembly code listings
    execute("set print asm-demangle on")
    # disable printing of address symbols
    execute("set print symbol off")
    # ensure file listings show only the current line
    execute("set listsize 1")


# execute a gdb command and return the resulting output as a string

def execute(command):
    print(f'(gdb) {command}')
    try:
        return gdb.execute(command, to_string=True)
    except gdb.error as e:
        print(e)
        sys.exit(1)


# a variety of useful regular expression patterns

address_pattern = '0x[0-9a-f]+'
hex_digits_pattern = '[0-9a-f]+'
spaces_pattern = '[ \t]+'
maybe_spaces_pattern = '[ \t]*'
digits_pattern = '[0-9]+'
line_number_prefix_pattern = digits_pattern + ':' + spaces_pattern
package_pattern = '[a-z/]+'
package_file_pattern = '[a-zA-Z0-9_/]+\\.java'
varname_pattern = '[a-zA-Z0-9_]+'
wildcard_pattern = '.*'
no_arg_values_pattern = r"\(\)"
arg_values_pattern = r"\(([a-zA-Z0-9$_]+=[a-zA-Z0-9$_<> ]+)(, [a-zA-Z0-9$_]+=[a-zA-Z0-9$_<> ]+)*\)"
no_param_types_pattern = r"\(\)"
param_types_pattern = r"\(([a-zA-Z0-9[.*$_\]]+)(, [a-zA-Z0-9[.*$_\]]+)*\)"
compressed_pattern = r"_z_\."


# A helper class which checks that a sequence of lines of output
# from a gdb command matches a sequence of per-line regular
# expressions

class Checker:
    # Create a checker to check gdb command output text.
    # name - string to help identify the check if we have a failure.
    # regexps - a list of regular expressions which must match.
    # successive lines of checked
    def __init__(self, name, regexps):
        self.name = name
        if not isinstance(regexps, list):
            regexps = [regexps]
        self.rexps = [re.compile(r) for r in regexps if r is not None]

    # Check that successive lines of a gdb command's output text
    # match the corresponding regexp patterns provided when this
    # Checker was created.
    # text - the full output of a gdb comand run by calling
    # gdb.execute and passing to_string = True.
    # Exits with status 1 if there are less lines in the text
    # than regexp patterns or if any line fails to match the
    # corresponding pattern otherwise prints the text and returns
    # the set of matches.
    def check(self, text, skip_fails=True):
        lines = text.splitlines()
        rexps = self.rexps
        num_lines = len(lines)
        num_rexps = len(rexps)
        line_idx = 0
        matches = []
        for i in range(0, num_rexps):
            rexp = rexps[i]
            match = None
            if skip_fails:
                line_idx = 0
            while line_idx < num_lines and match is None:
                line = lines[line_idx]
                match = rexp.match(line)
                if match is None:
                    if not skip_fails:
                        print(f'Checker {self.name}: match {i:d} failed at line {line_idx:d} {line}\n')
                        print(self)
                        print(text)
                        sys.exit(1)
                else:
                    matches.append(match)
                line_idx += 1
        if len(matches) < num_rexps:
            print(f'Checker {self.name}: insufficient matching lines {len(matches):d} for regular expressions {num_rexps:d}')
            print(self)
            print(text)
            sys.exit(1)
        print(text)
        return matches

    # Format a Checker as a string
    def __str__(self):
        rexps = self.rexps
        result = f'Checker {self.name} '
        result += '{\n'
        for rexp in rexps:
            result += f'  {rexp}\n'
        result += '}\n'
        return result


def match_gdb_version():
    # obtain the gdb version
    exec_string = execute("show version")
    checker = Checker('show version',
                      fr"GNU gdb {wildcard_pattern} ({digits_pattern})\.({digits_pattern}){wildcard_pattern}")
    matches = checker.check(exec_string, skip_fails=False)
    return matches[0]
