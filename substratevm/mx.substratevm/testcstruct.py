# Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
# A test script for use from gdb. It can be used to drive execution of
# a native image version of test app
# com.oracle.svm.test.debug.CStructTests and check that the debug info
# is valid.
#
# Assumes you have already executed
#
# $ javac com/oracle/svm/test/debug/CStructTests.java
# $ mx native-image -g com.oracle.svm.test.debug.CStructTests
#
# Run test
#
# gdb -x gdb_utils.py -x testcstructs.py /path/to/cstructtests
#
# exit status 0 means all is well 1 means test failed
#
# n.b. assumes the sourcefile cache is in local dir sources
#
# Note that the helper routines defined in gdb_utils.py are loaded
# using gdb -x rather than being imported. That avoids having to update
# PYTHON_PATH which gdb needs ot use to locate any imported code.


import re
import sys
import os

# Configure this gdb session

configure_gdb()

# Start of actual test code
#

def test():

    match = match_gdb_version()
    # n.b. can only get back here with one match
    major = int(match.group(1))
    minor = int(match.group(2))
    # printing object data requires a patched gdb
    print("Found gdb version %s.%s"%(major, minor))
    # check if we can print object data
    can_print_data = check_print_data(major, minor)

    musl = os.environ.get('debuginfotest_musl', 'no') == 'yes'

    isolates = os.environ.get('debuginfotest_isolates', 'no') == 'yes'
        
    arch = os.environ.get('debuginfotest_arch', 'amd64')
        
    if isolates:
        print("Testing with isolates enabled!")
    else:
        print("Testing with isolates disabled!")

    if not can_print_data:
        print("Warning: cannot test printing of objects!")

    exec_string=execute("info types com.oracle.svm.test.debug.CStructTests\$")
    rexp = [r"%stypedef composite_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$CompositeStruct;"%spaces_pattern,
            r"%stypedef int32_t \* com\.oracle\.svm\.test\.debug\.CStructTests\$MyCIntPointer;"%spaces_pattern,
            r"%stypedef simple_struct \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct;"%spaces_pattern,
            r"%stypedef simple_struct2 \* com\.oracle\.svm\.test\.debug\.CStructTests\$SimpleStruct2;"%spaces_pattern,
            r"%stypedef weird \* com\.oracle\.svm\.test\.debug\.CStructTests\$Weird;"%spaces_pattern]
    checker = Checker("info types com.oracle.svm.test.debug.CStructTests\$", rexp)
    checker.check(exec_string)

    # Print various foreign struct types and check they have the correct layout
    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'")
    rexp = [r"type = struct composite_struct {",
            r"/\*%s0%s\|%s1%s\*/%sbyte c1;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s3-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s4%s\|%s8%s\*/%sstruct simple_struct {"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s4%s\|%s4%s\*/%sint first;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s8%s\|%s4%s\*/%sint second;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s8 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} c2;"%(spaces_pattern),
            r"/\*%s12%s\|%s4%s\*/%sint c3;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s16%s\*/%sstruct simple_struct2 {"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s1%s\*/%sbyte alpha;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s7-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s24%s\|%s8%s\*/%slong beta;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s16 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} c4;"%(spaces_pattern),
            r"/\*%s32%s\|%s2%s\*/%sshort c5;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s6-byte padding%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s40 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} \*"%(spaces_pattern)]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$CompositeStruct'", rexp)
    checker.check(exec_string)

    exec_string = execute("ptype /o 'com.oracle.svm.test.debug.CStructTests$Weird'")
    rexp = [r"type = struct weird {",
            r"/\*%s0%s\|%s2%s\*/%sshort f_short;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s6-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s8%s\|%s4%s\*/%sint f_int;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s16%s\|%s8%s\*/%slong f_long;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s24%s\|%s4%s\*/%sfloat f_float;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte hole%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s32%s\|%s8%s\*/%sdouble f_double;"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s40%s\|%s32%s\*/%sint32_t a_int\[8\];"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%s72%s\|%s12%s\*/%sint8_t a_char\[12\];"%(spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern, spaces_pattern),
            r"/\*%sXXX%s4-byte padding%s\*/"%(spaces_pattern, spaces_pattern, spaces_pattern),
            r"%s/\* total size \(bytes\):%s88 \*/"%(spaces_pattern, spaces_pattern),
            r"%s} \*"%(spaces_pattern)]
    checker = Checker("ptype 'com.oracle.svm.test.debug.CStructTests$Weird'", rexp)
    checker.check(exec_string)

    if can_print_data:
        # set a break point at com.oracle.svm.test.debug.CStructTests::free
        exec_string = execute("break com.oracle.svm.test.debug.CStructTests::free")
        rexp = r"Breakpoint 1 at %s: file com/oracle/svm/test/debug/CStructTests\.java, line %s\."%(address_pattern, digits_pattern)
        checker = Checker('break free', rexp)
        checker.check(exec_string)

        # run the program till the breakpoint
        execute("run")

        # check the argument
        exec_string = execute("print *('com.oracle.svm.test.debug.CStructTests$CompositeStruct')ptr")
        rexp = [r"%s = {"%wildcard_pattern,
                r"  c1 = 7 '\\a',",
                r"  c2 = {",
                r"    first = 17,",
                r"    second = 19",
                r"  },",
                r"  c3 = 13",
                r"  c4 = {",
                r"    alpha = 3 '\\003',",
                r"    beta = 9223372036854775807",
                r"  },",
                r"  c5 = 32000",
                r"}"]
        checker = Checker('print CompositeStruct', rexp)
        checker.check(exec_string)

        # run the program till the breakpoint
        execute("continue")

        # check the argument again
        exec_string = execute("print *('com.oracle.svm.test.debug.CStructTests$Weird')ptr")
        rexp = [r"  f_short = 42,",
                r"  f_int = 43",
                r"  f_long = 44",
                r"  f_float = 4.5",
                r"  f_double = 4.5999999999999996",
                r"  a_int = {0, 1, 2, 3, 4, 5, 6, 7},",
                r'  a_char = "0123456789AB"']
        checker = Checker('print Weird', rexp)
        checker.check(exec_string)

    print(execute("quit 0"))

test()
