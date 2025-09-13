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


# this just tests the jit compilation interface, not the generated debug info
# however still requires symbols to be available for setting a breakpoint
class TestJITCompilationInterface(unittest.TestCase):
    @classmethod
    def setUp(cls):
        set_up_test()
        gdb_delete_breakpoints()
        gdb_start()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    # this test requires gdb to automatically add a breakpoint when a runtime compilation occurs
    # if a breakpoint for the compiled method existed before
    def test_update_breakpoint(self):
        # set breakpoint in runtime compiled function and stop there
        # store initial breakpoint info for comparison
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::inlineTest")
        breakpoint_info_before = gdb_execute('info breakpoints')
        gdb_continue()

        # get current breakpoint info and do checks
        breakpoint_info_after = gdb_execute('info breakpoints')
        # check if we got more breakpoints than before
        self.assertGreater(len(breakpoint_info_after), len(breakpoint_info_before))
        # check if old breakpoints still exist, code address must be the same
        # split at code address as multi-breakpoints are printed very different to single breakpoints
        self.assertIn(breakpoint_info_before.split('0x')[-1], breakpoint_info_after)
        # check if exactly one new correct breakpoint was added
        # new breakpoint address is always added after
        self.assertEqual(breakpoint_info_after.split(breakpoint_info_before.split('0x')[-1])[-1].count('com.oracle.svm.test.debug.helper.RuntimeCompilations::inlineTest'), 1)

        # run until the end and check if breakpoints for the run-time debuginfo are removed
        gdb_disable_breakpoints()
        gdb_continue()  # run until the end
        breakpoint_info_after_invalidation = gdb_execute('info breakpoints')
        # check if additional breakpoint is removed
        # breakpoint info is still printed as multi-breakpoint, thus we check if exactly one valid breakpoint is remaining
        self.assertEqual(breakpoint_info_after_invalidation.count('com.oracle.svm.test.debug.helper.RuntimeCompilations::inlineTest'), 1)
        # breakpoint info must change
        self.assertNotEqual(breakpoint_info_after, breakpoint_info_after_invalidation)

    # this test requires gdb to first load a new objfile at runtime and then remove it as the compilation is invalidated
    def test_load_objfile(self):
        # sanity check, we should not have in-memory objfiles at this point (to avoid false-positives later)
        objfiles = gdb.objfiles()
        self.assertFalse(any([o.filename.startswith('<in-memory@') for o in objfiles]))

        # set breakpoint in runtime compiled function and stop there
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::inlineTest")
        gdb_continue()
        # we are at the breakpoint, check if the objfile got registered in gdb
        objfiles = gdb.objfiles()
        self.assertTrue(any([o.filename.startswith('<in-memory@') for o in objfiles]))

        # run until the end and check if run-time debuginfo object file is removed
        gdb_disable_breakpoints()
        gdb_continue()  # run until the end
        # check if objfiles are removed
        objfiles = gdb.objfiles()
        self.assertFalse(any([o.filename.startswith('<in-memory@') for o in objfiles]))

    # this test checks if symbols in the runtime compilation are correct
    # a runtime compilation should not add any new function symbols
    def test_method_signature(self):
        # check initially
        function_info_before = gdb_execute('info function com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod')
        # one in search term, one function symbol, one deoptimized function symbol
        self.assertEqual(function_info_before.count('paramMethod'), 3)
        self.assertIn('java.lang.Integer *com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod(java.lang.Integer*, int, java.lang.String*, java.lang.Object*);', function_info_before)

        # set breakpoint in runtime compiled function and stop there
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        # ensure we did not register an extra symbol
        function_info_after = gdb_execute('info function com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod')
        self.assertEqual(function_info_before, function_info_after)

        # run until the runtime compilation is invalidated and check if the symbols still exist
        gdb_set_breakpoint('com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl::invalidate')
        gdb_continue()  # run until invalidation
        gdb_finish()  # finish invalidation - this should trigger an unregister call to gdb
        function_info_after = gdb_execute('info function com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod')
        self.assertEqual(function_info_before, function_info_after)


# this tests mostly require the gdb patch to work out, as otherwise no type information would be available
class TestRuntimeDebugInfo(unittest.TestCase):
    @classmethod
    def setUp(cls):
        cls.maxDiff = None
        set_up_test()
        gdb_delete_breakpoints()
        gdb_start()
        set_up_gdb_debughelpers()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    # check if initial parameter values are correct
    def test_params_method_initial(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '27')
        self.assertEqual(gdb_output('param3'), '"test"')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.helper.RuntimeCompilations = {'))
        self.assertIn('a = 11', this)
        self.assertIn('b = 0', this)
        self.assertIn('c = null', this)
        self.assertIn('d = null', this)

    # checks if parameter types are resolved correctly from AOT debug info
    def test_param_types(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        self.assertTrue(gdb_print_type('param1').startswith('type = class java.lang.Integer : public java.lang.Number {'))
        self.assertEqual(gdb_print_type('param2').strip(), 'type = int')  # printed type may contain newline at the end
        self.assertTrue(gdb_print_type('param3').startswith('type = class java.lang.String : public java.lang.Object {'))
        self.assertTrue(gdb_print_type('param4').startswith('type = class java.lang.Object : public _objhdr {'))
        self.assertTrue(gdb_print_type('this').startswith('type = class com.oracle.svm.test.debug.helper.RuntimeCompilations : public java.lang.Object {'))

    # run through paramMethod and check params after forced breakpoints
    def test_params_method(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::breakHere")
        # step 1 set a to param1 (param1 is pinned, so it is not optimized out yet)
        gdb_continue()
        gdb_finish()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '27')
        self.assertEqual(gdb_output('param3'), '"test"')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.helper.RuntimeCompilations = {'))
        self.assertIn('a = 42', this)
        self.assertIn('b = 0', this)
        self.assertIn('c = null', this)
        self.assertIn('d = null', this)
        # step 2 set b to param2
        gdb_continue()
        gdb_finish()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '<optimized out>')
        self.assertEqual(gdb_output('param3'), '"test"')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.helper.RuntimeCompilations = {'))
        self.assertIn('a = 42', this)
        self.assertIn('b = 27', this)
        self.assertIn('c = null', this)
        self.assertIn('d = null', this)
        # step 3 set c to param3
        gdb_continue()
        gdb_finish()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '<optimized out>')
        self.assertEqual(gdb_output('param3'), '<optimized out>')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.helper.RuntimeCompilations = {'))
        self.assertIn('a = 42', this)
        self.assertIn('b = 27', this)
        self.assertIn('c = "test"', this)
        self.assertIn('d = null', this)
        # step 4 set d to param4  (pin of param1 ends here)
        gdb_continue()
        gdb_finish()
        self.assertEqual(gdb_output('param1'), '<optimized out>')
        self.assertEqual(gdb_output('param2'), '<optimized out>')
        self.assertEqual(gdb_output('param3'), '<optimized out>')
        self.assertEqual(gdb_output('param4'), '<optimized out>')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.helper.RuntimeCompilations = {'))
        self.assertIn('a = 42', this)
        self.assertIn('b = 27', this)
        self.assertIn('c = "test"', this)
        self.assertIn('d = java.util.ArrayList(0)', this)

    # compares params and param types of AOT and JIT compiled method
    def test_compare_AOT_to_JIT(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::paramMethod")
        # first stop for the AOT compiled variant
        gdb_continue()
        this = gdb_output('this')
        this_t = gdb_print_type('this')
        param1 = gdb_output('param1')
        param1_t = gdb_print_type('param1')
        param2 = gdb_output('param2')
        param2_t = gdb_print_type('param2')
        param3 = gdb_output('param3')
        param3_t = gdb_print_type('param3')
        param4 = gdb_output('param4')
        param4_t = gdb_print_type('param4')

        # now stop for runtime compiled variant and check if equal
        gdb_continue()
        self.assertEqual(gdb_output('this'), this)
        self.assertEqual(gdb_print_type('this'), this_t)
        self.assertEqual(gdb_output('param1'), param1)
        self.assertEqual(gdb_print_type('param1'), param1_t)
        self.assertEqual(gdb_output('param2'), param2)
        self.assertEqual(gdb_print_type('param2'), param2_t)
        self.assertEqual(gdb_output('param3'), param3)
        self.assertEqual(gdb_print_type('param3'), param3_t)
        self.assertEqual(gdb_output('param4'), param4)
        self.assertEqual(gdb_print_type('param4'), param4_t)

    # checks run-time debug info for c types
    def test_c_types_1(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.CStructTests::weird")
        # first stop for the AOT compiled variant
        gdb_continue()
        gdb_next()

        # check if contents are correct
        wd = gdb_output('wd')
        self.assertIn('f_short = 42', wd)
        self.assertIn('f_int = 43', wd)
        self.assertIn('f_long = 44', wd)
        self.assertIn('f_float = 4.5', wd)
        self.assertIn('f_double = 4.59999', wd)
        self.assertIn('a_int = int [8] = {', wd)
        self.assertIn('a_char = char [12] = {', wd)

        # check if opaque type resolution works
        # the full type unit is in the AOT debug info, the run-time debug contains an opaque type (resolve by type name)
        # the actual type name is the Java class name (the class annotated with @CStruct) as typedef for the c type
        #   -> typedefs are resolved by gdb automatically
        wd_t = gdb_print_type('wd')
        self.assertIn('type = struct weird {', wd_t)
        self.assertIn('short f_short;', wd_t)
        self.assertIn('int f_int;', wd_t)
        self.assertIn('long f_long;', wd_t)
        self.assertIn('float f_float;', wd_t)
        self.assertIn('double f_double;', wd_t)
        self.assertIn('int a_int[8];', wd_t)
        self.assertIn('char a_char[12];', wd_t)

    def test_c_types_2(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.CStructTests::mixedArguments")
        # first stop for the AOT compiled variant
        gdb_continue()
        gdb_next()
        gdb_next()

        # check if contents are correct
        ss1 = gdb_output('ss1')
        self.assertIn('first = 1', ss1)
        self.assertIn('second = 2', ss1)
        ss2 = gdb_output('ss2')
        self.assertIn('alpha = 99', ss2)
        self.assertIn('beta = 100', ss2)

        # check if opaque type resolution works
        # the full type unit is in the AOT debug info, the run-time debug contains an opaque type (resolve by type name)
        # the actual type name is the Java class name (the class annotated with @CStruct) as typedef for the c type
        #   -> typedefs are resolved by gdb automatically
        ss1_t = gdb_print_type('ss1')
        self.assertIn('type = struct simple_struct {', ss1_t)
        self.assertIn('int first;', ss1_t)
        self.assertIn('int second;', ss1_t)
        ss2_t = gdb_print_type('ss2')
        self.assertIn('type = struct simple_struct2 {', ss2_t)
        self.assertIn('byte alpha;', ss2_t)
        self.assertIn('long beta;', ss2_t)

    def test_c_types_3(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.RuntimeCompilations::cPointerTypes")
        # first stop for the AOT compiled variant
        gdb_continue()

        # check if contents are correct
        c1 = gdb_output('charPtr')
        self.assertRegex(c1, f'\\(org.graalvm.nativeimage.c.type.CCharPointer\\) 0x{hex_rexp.pattern} "test"')
        c2_p = gdb_output('charPtrPtr')
        self.assertRegex(c2_p, f'\\(org.graalvm.nativeimage.c.type.CCharPointerPointer\\) 0x{hex_rexp.pattern}')
        c2 = gdb_output('*charPtrPtr')
        self.assertEqual(c1, c2)

        # check if opaque type resolution works
        # the full type unit is in the AOT debug info, the run-time debug contains an opaque type (resolve by type name)
        # the actual type name is the Java class name (the class annotated with @CStruct) as typedef for the c type
        #   -> typedefs are resolved by gdb automatically
        c1_t = gdb_print_type('charPtr')
        self.assertIn('type = char *', c1_t)
        c2_p_t = gdb_print_type('charPtrPtr')
        self.assertIn('type = char **', c2_p_t)

        # check if dereferencing resolves to the correct value
        self.assertEqual(gdb.parse_and_eval('charPtr'), gdb.parse_and_eval('*charPtrPtr'))


# redirect unittest output to terminal
result = unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__), exit=False)
# close gdb
gdb_quit(0 if result.result.wasSuccessful() else 1)
