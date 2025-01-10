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


# doesn't require the gdb patch to be available,
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
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::inlineTest")
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
        self.assertEqual(breakpoint_info_after.split(breakpoint_info_before.split('0x')[-1])[-1].count('com.oracle.svm.test.debug.runtime.RuntimeCompilations::inlineTest'), 1)

        # run until the runtime compilation is invalidated and check if the breakpoint is removed
        gdb_set_breakpoint('com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl::invalidate')
        gdb_continue()  # run until invalidation
        gdb_finish()  # finish invalidation - this should trigger an unregister call to gdb
        breakpoint_info_after_invalidation = gdb_execute('info breakpoints')
        # check if additional breakpoint is cleared after invalidate
        # breakpoint info is still printed as multi-breakpoint, thus we check if exactly one valid breakpoint is remaining
        self.assertEqual(breakpoint_info_after_invalidation.count('com.oracle.svm.test.debug.runtime.RuntimeCompilations::inlineTest'), 1)
        # breakpoint info must change after invalidation
        self.assertNotEquals(breakpoint_info_after, breakpoint_info_after_invalidation)

    # this test requires gdb to first load a new objfile at runtime and then remove it as the compilation is invalidated
    def test_load_objfile(self):
        # sanity check, we should not have in-memory objfiles at this point (to avoid false-positives later)
        objfiles = gdb.objfiles()
        self.assertFalse(any([o.filename.startswith('<in-memory@') for o in objfiles]))

        # set breakpoint in runtime compiled function and stop there
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::inlineTest")
        gdb_continue()
        # we are at the breakpoint, check if the objfile got registered in gdb
        objfiles = gdb.objfiles()
        self.assertTrue(any([o.filename.startswith('<in-memory@') for o in objfiles]))

        # run until the runtime compilation is invalidated and check if the objfile is removed
        gdb_set_breakpoint('com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl::invalidate')
        gdb_continue()  # run until invalidation
        gdb_finish()  # finish invalidation - this should trigger an unregister call to gdb
        # compilation is invalidated, check if objfile was removed
        objfiles = gdb.objfiles()
        self.assertFalse(any([o.filename.startswith('<in-memory@') for o in objfiles]))

    # this test checks if symbols in the runtime compilation are correct
    # a runtime compilation should not add any new function symbols
    def test_method_signature(self):
        # check initially
        function_info_before = gdb_execute('info function com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod')
        # one in search term, one function symbol, one deoptimized function symbol
        self.assertEqual(function_info_before.count('paramMethod'), 3)
        self.assertIn('java.lang.Integer *com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod(java.lang.Integer*, int, java.lang.String*, java.lang.Object*);', function_info_before)

        # set breakpoint in runtime compiled function and stop there
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        # ensure we did not register an extra symbol
        function_info_after = gdb_execute('info function com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod')
        self.assertEqual(function_info_before, function_info_after)

        # run until the runtime compilation is invalidated and check if the symbols still exist
        gdb_set_breakpoint('com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl::invalidate')
        gdb_continue()  # run until invalidation
        gdb_finish()  # finish invalidation - this should trigger an unregister call to gdb
        function_info_after = gdb_execute('info function com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod')
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
        gdb_execute("maintenance set dwarf type-signature-fallback main")

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    # check if initial parameter values are correct
    def test_params_method_initial(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '27')
        self.assertEqual(gdb_output('param3'), '"test"')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.runtime.RuntimeCompilations = {'))
        self.assertIn('a = 11', this)
        self.assertIn('b = 0', this)
        self.assertIn('c = null', this)
        self.assertIn('d = null', this)

    # checks if parameter types are resolved correctly from AOT debug info
    def test_param_types(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        self.assertTrue(gdb_print_type('param1').startswith('type = class java.lang.Integer : public java.lang.Number {'))
        self.assertEquals(gdb_print_type('param2').strip(), 'type = int')  # printed type may contain newline at the end
        self.assertTrue(gdb_print_type('param3').startswith('type = class java.lang.String : public java.lang.Object {'))
        self.assertTrue(gdb_print_type('param4').startswith('type = class java.lang.Object : public _objhdr {'))
        self.assertTrue(gdb_print_type('this').startswith('type = class com.oracle.svm.test.debug.runtime.RuntimeCompilations : public java.lang.Object {'))

    # run through paramMethod and check params after forced breakpoints
    def test_params_method(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod")
        gdb_continue()  # first stops once for the AOT compiled variant
        gdb_continue()
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::breakHere")
        # step 1 set a to param1 (param1 is pinned, so it is not optimized out yet)
        gdb_continue()
        gdb_finish()
        self.assertEqual(gdb_output('param1'), '42')
        self.assertEqual(gdb_output('param2'), '27')
        self.assertEqual(gdb_output('param3'), '"test"')
        self.assertEqual(gdb_output('param4'), 'java.util.ArrayList(0)')
        this = gdb_output('this')
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.runtime.RuntimeCompilations = {'))
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
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.runtime.RuntimeCompilations = {'))
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
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.runtime.RuntimeCompilations = {'))
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
        self.assertTrue(this.startswith('com.oracle.svm.test.debug.runtime.RuntimeCompilations = {'))
        self.assertIn('a = 42', this)
        self.assertIn('b = 27', this)
        self.assertIn('c = "test"', this)
        self.assertIn('d = java.util.ArrayList(0)', this)

    # compares params and param types of AOT and JIT compiled method
    def test_compare_AOT_to_JIT(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.runtime.RuntimeCompilations::paramMethod")
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


# redirect unittest output to terminal
result = unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__), exit=False)
# close gdb
gdb_quit(0 if result.result.wasSuccessful() else 1)
