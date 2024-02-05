#!/usr/bin/env python
#
# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import re
import sys


print("    @Test")
print("    public void testCasefixEquivalences() {")
print("        // Generated using re._casefix._EXTRA_CASES from CPython %d.%d.%d" % (sys.version_info.major, sys.version_info.minor, sys.version_info.micro))
from re._casefix import _EXTRA_CASES
for (cp, eq_cps) in _EXTRA_CASES.items():
    for eq_cp in eq_cps:
        print('        test("\\u%04x", "i", "\\u%04x", 0, true, 0, 1);' % (cp, eq_cp))
print("    }")
print()

patterns = [
    r'()\2',
    r'()\378',
    r'()\777',
    r'(\1)',
    r'(?<=()\1)',
    r'()(?P=1)',
    r'(?P<1)',
    r'(?P<1>)',
    r'(?P<a>)(?P<a>})',
    r'[]',
    r'[a-',
    r'[b-a]',
    r'[\d-e]',
    r'\x',
    r'\x1',
    r'\u111',
    r'\U1111',
    r'\U1111111',
    r'\U11111111',
    rb'\u2B50',
    rb'\U0001FA99',
    r'\N1',
    r'\N{1',
    r'\N{}',
    r'\N{a}',
    r'x{2,1}',
    r'x**',
    r'^*',
    r'\A*',
    r'\Z*',
    r'\b*',
    r'\B*',
    r'(?',
    r'(?P',
    r'(?P<',
    r'(?Px',
    r'(?<',
    r'(?x',
    r'(?P<>)',
    r'(?P<?>)',
    r'(?P=a)',
    r'(?#',
    r'(',
    r'(?i',
    r'(?L',
    r'(?t:)',
    r'(?-t:)',
    r'(?-:)',
    r'(?ij:)',
    r'(?i-i:)',
    r')',
    "\\",
    r'(?P<a>)(?(0)a|b)',
    r'()(?(1',
    r'()(?(1)a',
    r'()(?(1)a|b',
    r'()(?(2)a)',
    r'(?(a))',
    r'(a)b(?<=(?(2)b|x))(c)',
    r'(?(2147483648)a|b)',
    r'(?(42)a|b)[',
]

def escape_backslash(string):
    if isinstance(string, str):
        return re.sub(r'\\', r'\\\\', string)
    else:
        return re.sub(rb'\\', rb'\\\\', string)
    # Disable this method for IDEs which auto-escape pasted backslashes.
    # return string

print("    @Test")
print("    public void testSyntaxErrors() {")
print("        // Generated using sre from CPython %d.%d.%d" % (sys.version_info.major, sys.version_info.minor, sys.version_info.micro))
for pattern in patterns:
    try:
        re.compile(pattern)
    except re.error as e:
        msg = e.__str__()
        position_msg = ' at position '
        i = msg.find(position_msg)
        error_msg = msg[:i]
        position = int(msg[i + len(position_msg):])
        if isinstance(pattern, str):
            print('        expectSyntaxError("%s", "", "%s", %d);' % (escape_backslash(pattern), escape_backslash(error_msg), position))
        else:
            print('        expectSyntaxError("%s", "", "Encoding=LATIN-1", "%s", %d);' % (escape_backslash(pattern).decode(), escape_backslash(error_msg), position))
        continue
    raise RuntimeError("no exception was thrown " + pattern)


print("    }")
