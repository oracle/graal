#!/usr/bin/env python3
#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

# This script generates a list of case-equivalent Unicode code points using the
# SpecialCasing.txt file from Unicode. It expects this file to be in a folder
# called "dat". Two codepoints are considered equivalent if they map to the same
# sequence of codepoints using either the Lowercase or Uppercase function. Such
# cases are handled by including a special list of exceptions in sre_compile.py.

inv_map = {}

def add_mapping(codepoint, mapping):
    if mapping not in inv_map:
        inv_map[mapping] = []
    inv_map[mapping].append(codepoint)

for line in open('dat/SpecialCasing.txt'):
    if line.strip() == '' or line.startswith('#'):
        continue
    codepoint, lower, title, upper, *tail = [field.strip() for field in line.split(';')]
    if len(tail) > 1:
        # skip conditional mapping
        continue
    if ' ' in lower:
        add_mapping(codepoint, lower)
    if ' ' in upper:
        add_mapping(codepoint, upper)

for eq_class in inv_map.values():
    rep = eq_class[0]
    for elem in eq_class[1:]:
        print('{};{}'.format(rep, elem))
