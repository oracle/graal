#!/usr/bin/env python3
#
# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import sys
import os.path


def check_file_exists(path):
    if not os.path.exists(path):
        error(f'file "${path}" not found')


def error(msg):
    print('ERROR: ' + msg)
    sys.exit(1)


def main():
    file_name = 'CaseFoldTable.java'
    file_path = '../src/com/oracle/truffle/regex/tregex/parser/' + file_name
    replacement_file = './dat/case-fold-table.txt'
    marker_begin = 'GENERATED CODE BEGIN'
    marker_end = 'GENERATED CODE END'

    check_file_exists(file_path)
    check_file_exists(replacement_file)

    with open(file_path, 'r') as f, open(replacement_file, 'r') as rf:
        content = f.read()
        i_begin = content.find(marker_begin)
        i_end = content.find(marker_end)
        if i_begin < 0:
            error(f'could not find insertion marker "${marker_begin}" in ${file_name}')
        if i_end < 0:
            error(f'could not find end of insertion marker "${marker_begin}" in ${file_name}')
        replacement = content[0:content.find('\n', i_begin) + 1] + '\n' + rf.read() + content[content.rfind('\n', i_begin, i_end):]

    with open(file_path, 'w') as f:
        f.write(replacement)


main()
