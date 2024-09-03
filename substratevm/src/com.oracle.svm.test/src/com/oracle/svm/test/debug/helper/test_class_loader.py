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
import sys
import unittest

# add test directory to path to allow import of gdb_helper.py
sys.path.insert(0, os.path.join(os.path.dirname(os.path.realpath(__file__))))

from gdb_helper import *


class TestClassLoader(unittest.TestCase):

    def setUp(self):
        set_up_test()
        gdb_start()
        set_up_gdb_debughelpers()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_instanceMethod_named_classloader(self):
        gdb_set_breakpoint("com.oracle.svm.test.missing.classes.TestClass::instanceMethod")
        gdb_continue()  # named classloader is called first in test code
        self.assertRegex(gdb_output("this"), rf'testClassLoader_{hex_rexp.pattern}::com\.oracle\.svm\.test\.missing\.classes\.TestClass = {{instanceField = null}}')
        gdb_output("$other=(('java.lang.Object' *)this)")
        self.assertIn("null", gdb_advanced_print("$other.instanceField"))  # force a typecast

    def test_instanceMethod_unnamed_classloader(self):
        gdb_set_breakpoint("com.oracle.svm.test.missing.classes.TestClass::instanceMethod")
        gdb_continue()  # skip named classloader
        gdb_continue()  # unnamed classloader is called second in test code
        self.assertRegex(gdb_output("this"), rf'URLClassLoader_{hex_rexp.pattern}::com\.oracle\.svm\.test\.missing\.classes\.TestClass = {{instanceField = null}}')
        gdb_output("$other=(('java.lang.Object' *)this)")
        self.assertIn("null", gdb_advanced_print("$other.instanceField"))  # force a typecast


class TestClassloaderObjUtils(unittest.TestCase):

    compressed_type = gdb.lookup_type('_z_.java.lang.Object')
    uncompressed_type = gdb.lookup_type('java.lang.Object')

    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_get_classloader_namespace(self):
        gdb_set_breakpoint("com.oracle.svm.test.missing.classes.TestClass::instanceMethod")
        gdb_run()
        clazz = gdb.parse_and_eval("'java.lang.Object.class'")  # type = java.lang.Class -> no classloader name
        this = gdb.parse_and_eval('this')
        field = gdb.parse_and_eval('this.instanceField')
        self.assertEqual(SVMUtil.get_classloader_namespace(clazz), "")
        self.assertRegex(SVMUtil.get_classloader_namespace(this), f'testClassLoader_{hex_rexp.pattern}')
        self.assertEqual(SVMUtil.get_classloader_namespace(field), "")  # field is null


# redirect unittest output to terminal
unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__))
