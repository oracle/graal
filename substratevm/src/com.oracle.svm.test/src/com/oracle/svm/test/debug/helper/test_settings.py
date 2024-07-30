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
import gdb

# add test directory to path to allow import of gdb_helper.py
sys.path.insert(0, os.path.join(os.path.dirname(os.path.realpath(__file__))))

from gdb_helper import *


class TestCustomBreakCommand(unittest.TestCase):
    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_replace_hash(self):
        svm_command_break = SVMCommandBreak()
        svm_command_break.invoke("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject", False)
        self.assertGreater(len(gdb.breakpoints()), 0)
        self.assertEqual(gdb.breakpoints()[-1].locations[0].function, "_ZN50com.oracle.svm.test.debug.helper.PrettyPrinterTest10testObjectEJvP63com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClassS1_")


class TestCustomPrintCommand(unittest.TestCase):

    svm_command_print = SVMCommandPrint()

    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_print(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        self.assertEqual(gdb_advanced_print("object").split('=', 2)[-1], gdb_print("object").split('=', 2)[-1])
        self.assertEqual(gdb_advanced_print("object", 'r').split('=', 2)[-1], gdb_print("object", 'r').split('=', 2)[-1])

    def test_print_array(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()

        # check if expansion works and does not interfere with manual expansion
        self.assertEqual(gdb_advanced_print("oa[1]").split('=', 2)[-1], gdb_print("oa.data[1]").split('=', 2)[-1])
        self.assertEqual(gdb_advanced_print("oa[1]").split('=', 2)[-1], gdb_advanced_print("oa.data[1]").split('=', 2)[-1])

        # oa is of type Object[], thus the elements static type is Object, 'p' applies the type cast to the rtt
        self.assertNotEqual(gdb_advanced_print("oa[1]", 'r').split('=', 2)[-1], gdb_print("oa.data[1]", 'r').split('=', 2)[-1])
        self.assertIn("(_z_.java.lang.String *)", gdb_advanced_print("oa[1]", 'r'))

        # check a more complex expansion
        self.assertEqual(gdb_advanced_print("oa[1].value[1]").split('=', 2)[-1], gdb_print("(('_z_.java.lang.String' *)oa.data[1]).value.data[1]").split('=', 2)[-1])

        # check a nested expansion
        self.assertEqual(gdb_advanced_print("oa[ia[ia[oa[0]+1]]]").split('=', 2)[-1], gdb_print("oa.data[1]").split('=', 2)[-1])

    def test_print_function_call(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertEqual(gdb_advanced_print("oa[1].charAt(0)").split('=', 2)[-1], gdb_print("(('java.lang.String')(*oa.data[1])).charAt(0)").split('=', 2)[-1])
        self.assertEqual(gdb_advanced_print("oa[1].endsWith(oa[1])").split('=', 2)[-1], gdb_print("(('java.lang.String')(*oa.data[1])).endsWith(&(('java.lang.String')(*oa.data[1])))").split('=', 2)[-1])
        self.assertIn('true', gdb_advanced_print("oa[1].endsWith(oa[1])"))

    def test_print_non_java(self):
        gdb_start()
        self.assertEqual(gdb_advanced_print('"test"').split('=', 2)[-1], gdb_print('"test"').split('=', 2)[-1])  # char*
        self.assertEqual(gdb_advanced_print('{1,2,3,4}').split('=', 2)[-1], gdb_print('{1,2,3,4}').split('=', 2)[-1])  # int[4]

    def test_print_formatted(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertNotEqual(gdb_advanced_print("oa[1]", 'r').split('=', 2)[-1], gdb_advanced_print("oa[1]").split('=', 2)[-1])
        self.assertIn("(_z_.java.lang.String *)", gdb_advanced_print("oa[1]", 'r'))
        self.assertNotIn("(_z_.java.lang.String *)", gdb_advanced_print("oa[1]"))

    def test_auto_complete_empty(self):
        gdb_start()
        self.assertEqual(self.svm_command_print.complete("", ""), gdb.COMPLETE_EXPRESSION)

    def test_auto_complete_array(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertEqual(self.svm_command_print.complete("ia[", ""), ["0]", "1]", "3]"])
        self.assertEqual(self.svm_command_print.complete("ia[a", ""), gdb.COMPLETE_EXPRESSION)

    def test_auto_complete_function_param(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertEqual(self.svm_command_print.complete("sa[0].charAt(", ""), gdb.COMPLETE_EXPRESSION)

    def test_auto_complete_field_access(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        result = self.svm_command_print.complete("oa[3].add", "")
        self.assertIn('add', result)
        self.assertIn('addAll', result)
        self.assertIn('addFirst', result)
        self.assertIn('addLast', result)

    def test_auto_complete_invalid(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertEqual(self.svm_command_print.complete("oa[3].addNone", ""), [])

    def test_auto_complete_None(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        self.assertEqual(self.svm_command_print.complete("oa[3]", ""), gdb.COMPLETE_NONE)


class TestParameters(unittest.TestCase):
    @classmethod
    def setUp(cls):
        cls.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_svm_print(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        gdb_set_param('svm-print', 'off')
        self.assertEqual(gdb_output("object"), gdb_output("object", "r"))
        self.assertRaises(gdb.error, lambda: gdb_advanced_print("object.toString()", 'r'))  # check if 'p' command is skipped
        gdb_set_param('svm-print', 'on')
        self.assertNotEqual(gdb_output("object"), gdb_output("object", "r"))
        self.assertIn("(java.lang.String *)", gdb_advanced_print("object.toString()", 'r'))

    def test_svm_print_string_limit(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testString")
        gdb_run()
        gdb_set_param('svm-print-string-limit', '2')
        self.assertEqual(gdb_output("str"), '"st..."')
        gdb_set_param('svm-print-string-limit', 'unlimited')
        self.assertEqual(gdb_output("str"), '"string"')

    def test_svm_print_element_limit(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        gdb_set_param('svm-print-element-limit', '2')
        self.assertEqual(gdb_output("ia"), "int [4] = {0, 1, ...}")
        gdb_set_param('svm-print-element-limit', 'unlimited')
        self.assertEqual(gdb_output("ia"), "int [4] = {0, 1, 2, 3}")

    def test_svm_print_field_limit(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        gdb_set_param('svm-print-field-limit', '1')
        self.assertIn('f7 = "test string"', gdb_output('object'))
        self.assertIn('f8 = ...', gdb_output('object'))
        gdb_set_param('svm-print-field-limit', 'unlimited')
        self.assertIn('f7 = "test string"', gdb_output('object'))
        self.assertIn('f8 = Monday(0)', gdb_output('object'))

    def test_svm_print_depth_limit(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        gdb_set_param('svm-print-depth-limit', '0')
        self.assertEqual(gdb_output('object'), "com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}")
        gdb_set_param('svm-print-depth-limit', '2')
        self.assertIn("f9 = java.util.ArrayList<ExampleClass>(2) = {com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}, com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}}", gdb_output('object'))

    def test_svm_use_hlrep(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        gdb_set_param('svm-use-hlrep', 'off')
        exec_string = gdb_output("strList")
        self.assertTrue(exec_string.startswith('java.util.ArrayList = {'))
        self.assertIn('modCount = 0', exec_string)
        self.assertIn('size = 5', exec_string)
        self.assertIn('elementData = java.lang.Object[5] = {...}', exec_string)
        gdb_set_param('svm-use-hlrep', 'on')
        self.assertEqual(gdb_output("strList"), 'java.util.ArrayList<String>(5) = {"this", "is", "a", "string", "list"}')

    def test_svm_infer_generics(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        gdb_set_param('svm-infer-generics', '0')
        self.assertEqual(gdb_output("mixedList"), 'java.util.ArrayList(3) = {1, 2, "string"}')
        gdb_set_param('svm-infer-generics', '1')
        self.assertEqual(gdb_output("mixedList"), 'java.util.ArrayList<Integer>(3) = {1, 2, "string"}')
        gdb_set_param('svm-infer-generics', '2')
        self.assertEqual(gdb_output("mixedList"), 'java.util.ArrayList<Number>(3) = {1, 2, "string"}')
        gdb_set_param('svm-infer-generics', 'unlimited')
        self.assertEqual(gdb_output("mixedList"), 'java.util.ArrayList<Object>(3) = {1, 2, "string"}')

    def test_svm_print_address(self):
        def assert_adr_regex(adr_regex: str = ''):
            exec_string = gdb_output("object")
            self.assertRegex(exec_string, rf'^com\.oracle\.svm\.test\.debug\.helper\.PrettyPrinterTest\$ExampleClass{adr_regex} = {{')
            self.assertRegex(exec_string, rf'f7 = "test string"{adr_regex}')
            self.assertRegex(exec_string, rf'f8 = Monday\(0\){adr_regex}')
            self.assertRegex(exec_string, rf'f9 = java\.util\.ArrayList<ExampleClass>\(2\) = {{\.\.\.}}{adr_regex}')
            self.assertRegex(exec_string, rf'f10 = com\.oracle\.svm\.test\.debug\.helper\.PrettyPrinterTest\$ExampleClass = {{\.\.\.}}{adr_regex}')
            self.assertRegex(exec_string, r'f1 = 0[,}]')
            self.assertRegex(exec_string, r'f2 = 1[,}]')
            self.assertRegex(exec_string, r'f3 = 2[,}]')
            self.assertRegex(exec_string, r"f4 = '3'[,}]")
            self.assertRegex(exec_string, r'f5 = 4[,}]')
            self.assertRegex(exec_string, r'f6 = false[,}]')

        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        gdb_set_param('svm-print-address', 'enable')
        assert_adr_regex(f' @z?\\(0x{hex_rexp.pattern}\\)')
        gdb_set_param('svm-print-address', 'absolute')
        assert_adr_regex(f' @\\(0x{hex_rexp.pattern}\\)')
        gdb_set_param('svm-print-address', 'disable')
        assert_adr_regex()

    def test_svm_selfref_check(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()
        gdb_set_param('svm-selfref-check', 'off')
        gdb_set_param('svm-print-depth-limit', '4')  # increase print depth to test for selfref check
        # printing is only restricted by depth -> more characters are printed
        exec_string1 = gdb_output("recObject")
        gdb_set_param('svm-selfref-check', 'on')
        exec_string2 = gdb_output("recObject")
        self.assertGreater(len(exec_string1), len(exec_string2))

    def test_svm_print_static_fields(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        gdb_set_param('svm-print-static-fields', 'on')
        gdb_set_param('svm-use-hlrep', 'off')
        self.assertIn('DEFAULT_CAPACITY', gdb_output("strList"))
        gdb_set_param('svm-print-static-fields', 'off')
        self.assertNotIn('DEFAULT_CAPACITY', gdb_output("strList"))

    def test_svm_complete_static_variables(self):
        svm_command_print = SVMCommandPrint()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        gdb_set_param('svm-complete-static-variables', 'on')
        gdb_set_param('svm-use-hlrep', 'off')
        self.assertIn('DEFAULT_CAPACITY', svm_command_print.complete("strList.", ""))
        gdb_set_param('svm-complete-static-variables', 'off')
        self.assertNotIn('DEFAULT_CAPACITY', svm_command_print.complete("strList.", ""))


# redirect unittest output to terminal
unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__))
