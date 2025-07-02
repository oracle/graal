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
        exec_string = gdb_output("this")
        self.assertTrue(exec_string.startswith("testClassLoader_"), f"GDB output: '{exec_string}'")  # check for correct class loader
        self.assertIn("::com.oracle.svm.test.missing.classes.TestClass = {", exec_string)  # check if TestClass has a namespace
        self.assertIn("instanceField = null", exec_string)
        gdb_output("$other=(('java.lang.Object' *)this)")
        self.assertIn("null", gdb_advanced_print("$other.instanceField"))  # force a typecast

    def test_instanceMethod_unnamed_classloader(self):
        gdb_set_breakpoint("com.oracle.svm.test.missing.classes.TestClass::instanceMethod")
        gdb_continue()  # skip named classloader
        gdb_continue()  # unnamed classloader is called second in test code
        exec_string = gdb_output("this")
        self.assertTrue(exec_string.startswith("URLClassLoader_"), f"GDB output: '{exec_string}'")  # check for correct class loader
        self.assertIn("::com.oracle.svm.test.missing.classes.TestClass = {", exec_string)  # check if TestClass has a namespace
        self.assertIn("instanceField = null", exec_string)
        gdb_output("$other=(('java.lang.Object' *)this)")
        self.assertIn("null", gdb_advanced_print("$other.instanceField"))  # force a typecast


class TestClassloaderObjUtils(unittest.TestCase):

    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()
        self.svm_util = SVMUtil()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_get_classloader_namespace(self):
        gdb_set_breakpoint("com.oracle.svm.test.missing.classes.TestClass::instanceMethod")
        gdb_run()
        this = gdb.parse_and_eval('this')  # type = com.oracle.svm.test.missing.classes.TestClass -> testClassLoader
        field = gdb.parse_and_eval('this.instanceField')  # instanceField is null
        self.assertRegex(self.svm_util.get_classloader_namespace(this), f'testClassLoader_{hex_rexp.pattern}')
        self.assertEqual(self.svm_util.get_classloader_namespace(field), "")


# redirect unittest output to terminal
result = unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__), exit=False)
# close gdb
gdb_quit(0 if result.result.wasSuccessful() else 1)
