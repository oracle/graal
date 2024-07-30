#
# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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


class TestPrintPrimitives(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        set_up_test()
        set_up_gdb_debughelpers()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testPrimitive")
        gdb_run()

    @classmethod
    def tearDownClass(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_byte(self):
        exec_string = gdb_output("b")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a byte")
        self.assertEqual(int(exec_string), 1)

    def test_boxed_byte(self):
        exec_string = gdb_output("bObj")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a byte")
        self.assertEqual(int(exec_string), 1)

    def test_short(self):
        exec_string = gdb_output("s")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a short")
        self.assertEqual(int(exec_string), 2)

    def test_boxed_short(self):
        exec_string = gdb_output("sObj")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a short")
        self.assertEqual(int(exec_string), 2)

    def test_char(self):
        exec_string = gdb_output("c")
        self.assertRegex(exec_string, char_rexp, f"'{exec_string}' is not a char")
        self.assertEqual(exec_string, "'3'")

    def test_boxed_char(self):
        exec_string = gdb_output("cObj")
        self.assertRegex(exec_string, char_rexp, f"'{exec_string}' is not a char")
        self.assertEqual(exec_string, "'3'")

    def test_int(self):
        exec_string = gdb_output("i")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a int")
        self.assertEqual(int(exec_string), 4)

    def test_boxed_int(self):
        exec_string = gdb_output("iObj")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a int")
        self.assertEqual(int(exec_string), 4)

    def test_long(self):
        exec_string = gdb_output("l")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a long")
        self.assertEqual(int(exec_string), 5)

    def test_boxed_long(self):
        exec_string = gdb_output("lObj")
        self.assertRegex(exec_string, int_rexp, f"'{exec_string}' is not a long")
        self.assertEqual(int(exec_string), 5)

    def test_float(self):
        exec_string = gdb_output("f")
        self.assertRegex(exec_string, float_rexp, f"'{exec_string}' is not a float")
        self.assertEqual(float(exec_string), 6.125)

    def test_boxed_float(self):
        exec_string = gdb_output("fObj")
        self.assertRegex(exec_string, float_rexp, f"'{exec_string}' is not a float")
        self.assertEqual(float(exec_string), 6.125)

    def test_double(self):
        exec_string = gdb_output("d")
        self.assertRegex(exec_string, float_rexp, f"'{exec_string}' is not a double")
        self.assertEqual(float(exec_string), 7.25)

    def test_boxed_double(self):
        exec_string = gdb_output("dObj")
        self.assertRegex(exec_string, float_rexp, f"'{exec_string}' is not a double")
        self.assertEqual(float(exec_string), 7.25)

    def test_boolean(self):
        exec_string = gdb_output("x")
        self.assertRegex(exec_string, boolean_rexp, f"'{exec_string}' is not a boolean")
        self.assertEqual(bool(exec_string), True)

    def test_boxed_boolean(self):
        exec_string = gdb_output("xObj")
        self.assertRegex(exec_string, boolean_rexp, f"'{exec_string}' is not a boolean")
        self.assertEqual(bool(exec_string), True)


class TestPrintStrings(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        set_up_test()
        set_up_gdb_debughelpers()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testString")
        gdb_run()

    @classmethod
    def tearDownClass(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_null(self):
        exec_string = gdb_output("nullStr")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, "null")

    def test_empty_string(self):
        exec_string = gdb_output("emptyStr")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '""')

    def test_nonempty_string(self):
        exec_string = gdb_output("str")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"string"')

    def test_unicode_strings(self):
        exec_string = gdb_output("uStr1")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"Привет Java"')
        exec_string = gdb_output("uStr2")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"Բարեւ Java"')
        exec_string = gdb_output("uStr3")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"你好的 Java"')
        exec_string = gdb_output("uStr4")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"こんにちは Java"')
        exec_string = gdb_output("uStr5")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, '"𝄞и𝄞и𝄞и𝄞и𝄞"')

    def test_string_containing_0(self):
        exec_string = gdb_output("str0")
        self.assertRegex(exec_string, string_rexp, f"'{exec_string}' is not a string")
        self.assertEqual(exec_string, r'"first \0second"')


class TestPrintArrays(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        set_up_test()
        set_up_gdb_debughelpers()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()

    @classmethod
    def tearDownClass(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_primitive_array(self):
        exec_string = gdb_output("ia")
        self.assertRegex(exec_string, array_rexp, f"'{exec_string}' is not an array")
        self.assertEqual(exec_string, "int [4] = {0, 1, 2, 3}")

    def test_string_array(self):
        exec_string = gdb_output("sa")
        self.assertRegex(exec_string, array_rexp, f"'{exec_string}' is not an array")
        self.assertEqual(exec_string, 'java.lang.String[5] = {"this", "is", "a", "string", "array"}')

    def test_object_array(self):
        exec_string = gdb_output("oa")
        self.assertRegex(exec_string, array_rexp, f"'{exec_string}' is not an array")
        self.assertEqual(exec_string, 'java.lang.Object[4] = {0, "random", java.lang.Object = {...}, '
                                      'java.util.ArrayList(0) = {...}}')


class TestPrintFunctionHeader(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        set_up_test()
        set_up_gdb_debughelpers()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_primitive_arguments(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testPrimitive")
        gdb_run()
        exec_string = gdb_execute("frame")
        self.assertRegex(exec_string, args_rexp, f"Could not find args in '{exec_string}'")
        args = args_rexp.match(exec_string).group("args")
        self.assertIn("b=1", args)
        self.assertIn("bObj=1", args)
        self.assertIn("s=2", args)
        self.assertIn("sObj=2", args)
        self.assertIn("c='3'", args)
        self.assertIn("cObj='3'", args)
        self.assertIn("i=4", args)
        self.assertIn("iObj=4", args)
        self.assertIn("l=5", args)
        self.assertIn("lObj=5", args)
        self.assertIn("f=6.125", args)
        self.assertIn("fObj=6.125", args)
        self.assertIn("d=7.25", args)
        self.assertIn("dObj=7.25", args)
        self.assertIn("x=true", args)
        self.assertIn("xObj=true", args)

    def test_string_arguments(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testString")
        gdb_run()
        exec_string = gdb_execute("frame")
        self.assertRegex(exec_string, args_rexp, f"Could not find args in '{exec_string}'")
        args = args_rexp.match(exec_string).group("args")
        self.assertIn(r'nullStr=null', args)
        self.assertIn(r'emptyStr=""', args)
        self.assertIn(r'str="string"', args)
        self.assertIn(r'uStr1="Привет Java"', args)
        self.assertIn(r'uStr2="Բարեւ Java"', args)
        self.assertIn(r'uStr3="你好的 Java"', args)
        self.assertIn(r'uStr4="こんにちは Java"', args)
        self.assertIn(r'uStr5="𝄞и𝄞и𝄞и𝄞и𝄞"', args)
        self.assertIn(r'str0="first \0second"', args)

    def test_array_arguments(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArray")
        gdb_run()
        exec_string = gdb_execute("frame")
        self.assertRegex(exec_string, args_rexp, f"Could not find args in '{exec_string}'")
        args = args_rexp.match(exec_string).group("args")
        self.assertIn('ia=int [4] = {...}', args)
        self.assertIn('oa=java.lang.Object[4] = {...}', args)
        self.assertIn('sa=java.lang.String[5] = {...}', args)


class TestPrintClassMembers(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        set_up_test()
        set_up_gdb_debughelpers()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testObject")
        gdb_run()

    @classmethod
    def tearDownClass(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_all_members(self):
        self.maxDiff = None
        gdb_set_param("svm-print-depth", "2")
        exec_string = gdb_output("object")
        self.assertTrue(exec_string.startswith("com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {"), f"GDB output: '{exec_string}'")
        self.assertIn('f1 = 0', exec_string)
        self.assertIn('f2 = 1', exec_string)
        self.assertIn('f3 = 2', exec_string)
        self.assertIn("f4 = '3'", exec_string)
        self.assertIn('f5 = 4', exec_string)
        self.assertIn('f6 = false', exec_string)
        self.assertIn('f7 = "test string"', exec_string)
        self.assertIn('f8 = Monday(0)', exec_string)
        self.assertIn('f9 = java.util.ArrayList<ExampleClass>(2) = {com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}, com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}}', exec_string)
        self.assertIn('f10 = com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {', exec_string)
        self.assertIn('f1 = 10', exec_string)
        self.assertIn('f2 = 20', exec_string)
        self.assertIn('f3 = 30', exec_string)
        self.assertIn("f4 = ' '", exec_string)
        self.assertIn('f5 = 50', exec_string)
        self.assertIn('f6 = true', exec_string)
        self.assertIn('f7 = "60"', exec_string)
        self.assertIn('f8 = Sunday(6)', exec_string)
        self.assertIn('f9 = java.lang.Object', exec_string)
        self.assertIn('f10 = null', exec_string)

    def test_all_members_recursive(self):
        gdb_set_param("svm-print-depth", "2")
        exec_string = gdb_output("recObject")
        self.assertTrue(exec_string.startswith("com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {"), f"GDB output: '{exec_string}'")
        self.assertIn('f1 = 0', exec_string)
        self.assertIn('f2 = 1', exec_string)
        self.assertIn('f3 = 2', exec_string)
        self.assertIn("f4 = '3'", exec_string)
        self.assertIn('f5 = 4', exec_string)
        self.assertIn('f6 = false', exec_string)
        self.assertIn('f7 = "test string"', exec_string)
        self.assertIn('f8 = Monday(0)', exec_string)
        self.assertIn('f9 = java.util.ArrayList<ExampleClass>(2) = {com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}, com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}}', exec_string)
        # {...} due to self-reference
        self.assertIn('f10 = com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}', exec_string)


class TestPrintCollections(unittest.TestCase):
    @classmethod
    def setUp(cls):
        set_up_test()
        set_up_gdb_debughelpers()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_string_array_list(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        exec_string = gdb_output("strList")
        self.assertEqual(exec_string, 'java.util.ArrayList<String>(5) = {"this", "is", "a", "string", "list"}')

    def test_mixed_array_list(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        exec_string = gdb_output("mixedList")
        self.assertEqual(exec_string, 'java.util.ArrayList<Object>(3) = {1, 2, "string"}')

    def test_null_array_list(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_run()
        exec_string = gdb_output("nullList")
        self.assertEqual(exec_string, 'java.util.ArrayList(3) = {null, null, null}')

    def test_string_string_hash_map(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testHashMap")
        gdb_run()
        exec_string = gdb_output("strMap")
        # there is no guarantee that the hashes are always the same -> just check if entries are contained in the output
        self.assertTrue(exec_string.startswith('java.util.HashMap<String, String>(5) = {'), f"GDB output: '{exec_string}'")
        self.assertIn('["this"] = "one"', exec_string)
        self.assertIn('["is"] = "two"', exec_string)
        self.assertIn('["a"] = "three"', exec_string)
        self.assertIn('["string"] = "four"', exec_string)
        self.assertIn('["list"] = "five"', exec_string)
        self.assertTrue(exec_string.endswith('}'))

    def test_mixed_hash_map(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testHashMap")
        gdb_run()
        exec_string = gdb_output("mixedMap")
        self.assertTrue(exec_string.startswith('java.util.HashMap<Number, Object>(3) = {'), f"GDB output: '{exec_string}'")
        self.assertIn('[1] = com.oracle.svm.test.debug.helper.PrettyPrinterTest$ExampleClass = {...}', exec_string)
        self.assertIn('[2] = "string"', exec_string)
        self.assertIn('[3] = java.util.ArrayList(0) = {...}', exec_string)
        self.assertTrue(exec_string.endswith('}'))


# redirect unittest output to terminal
unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__))
