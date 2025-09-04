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
import re


# requires the gdb patch to be available
class TestRuntimeDeopt(unittest.TestCase):
    @classmethod
    def setUp(cls):
        cls.maxDiff = None
        set_up_test()
        gdb_start()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_frame_unwinder_registration(self):
        # run to a method where the frame unwinder is registered
        gdb_set_breakpoint('com.oracle.truffle.runtime.OptimizedCallTarget::profiledPERoot')
        gdb_continue()
        set_up_gdb_debughelpers()  # setup debughelpers after we know it exists
        # check frame unwinder
        unwinder_info = gdb_execute('info unwinder')
        self.assertIn('libjsvm.so.debug:', unwinder_info)
        self.assertIn('SubstrateVM FrameUnwinder', unwinder_info)

    # for shared libraries, the frame unwinder is removed when the shared library is unloaded
    # when it is loaded again, the gdb script is not run again
    # the gdb-debughelpers should still be able to reload the frame unwinder
    def test_frame_unwinder_reload(self):
        # run to a method where the frame unwinder is registered
        gdb_set_breakpoint('com.oracle.truffle.runtime.OptimizedCallTarget::profiledPERoot')
        gdb_continue()
        set_up_gdb_debughelpers()  # setup debughelpers after we know it exists

        # stops previous execution and reloads the shared library
        # gdb-debughelpers should then reload the frame unwinder for the shared library
        gdb_run()
        # check frame unwinder
        unwinder_info = gdb_execute('info unwinder')
        self.assertIn('libjsvm.so.debug:', unwinder_info)
        self.assertIn('SubstrateVM FrameUnwinder', unwinder_info)

    def test_backtrace_with_deopt(self):
        # run until method is deoptimized
        gdb_set_breakpoint("com.oracle.svm.core.deopt.Deoptimizer::invalidateMethodOfFrame")
        gdb_continue()
        gdb_finish()

        # check backtrace
        backtrace = gdb_execute('backtrace 5')
        # check if eager deopt frame
        if 'EAGER DEOPT FRAME' in backtrace:
            self.assertIn('in [EAGER DEOPT FRAME]', backtrace)
            self.assertIn('deoptFrameValues=2', backtrace)

            # check if values are printed correctly and backtrace is not corrupted
            if 'SubstrateEnterpriseOptimizedCallTarget' in backtrace:
                self.assertIn('SubstrateEnterpriseOptimizedCallTarget = {...}, __1=java.lang.Object[5] = {...}', backtrace)
                self.assertIn('SubstrateEnterpriseOptimizedCallTarget::doInvoke', backtrace)
            else:
                self.assertIn('SubstrateOptimizedCallTarget = {...}, __1=java.lang.Object[5] = {...})', backtrace)
                self.assertIn('SubstrateOptimizedCallTargetInstalledCode::doInvoke', backtrace)
        else:
            # must be lazy deopt frame
            self.assertIn('[LAZY DEOPT FRAME]', backtrace)

            if 'SubstrateEnterpriseOptimizedCallTarget' in backtrace:
                # check if we still see the parameters of the lazily deoptimized method on the backtrace
                self.assertRegex(backtrace, re.compile(rf'0x{hex_pattern} in \[LAZY DEOPT FRAME\] .* \(callTarget={value_pattern}, args={value_pattern}, __Object2={value_pattern}, __int3={value_pattern}, __int4={value_pattern}, __boolean5={value_pattern}\)'))
            else:
                # must be a SubstrateOptimizedCallTarget
                self.assertIn('SubstrateOptimizedCallTarget', backtrace)
                self.assertRegex(backtrace, re.compile(rf'0x{hex_pattern} in \[LAZY DEOPT FRAME\] .* \(this={value_pattern}, originalArguments={value_pattern}\)'))

        # the backtrace should contain no unknown frames
        self.assertNotIn('??', backtrace)
        self.assertNotIn('Unknown Frame at', backtrace)

    # the js deopt test uses the jsvm-library
    # so the debugging symbols come from the js shared library
    def test_opaque_types_with_shared_library(self):
        # stop at a method where we know that the runtime compiled frame is in the backtrace
        gdb_set_breakpoint("com.oracle.svm.core.deopt.Deoptimizer::invalidateMethodOfFrame")
        gdb_continue()

        # check backtrace
        backtrace = gdb_execute('backtrace 5')
        if 'SubstrateEnterpriseOptimizedCallTarget' in backtrace:
            self.assertIn('SubstrateEnterpriseOptimizedCallTarget::add_I_AAIIZ', backtrace)
            self.assertNotIn('<unknown type in <in-memory@', backtrace)
        else:
            self.assertIn('com.oracle.truffle.runtime.OptimizedCallTarget::profiledPERoot', backtrace)
            self.assertIn('(this=<optimized out>, originalArguments=', backtrace)
            self.assertNotIn('this=<unknown type in <in-memory@', backtrace)


# redirect unittest output to terminal
result = unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__), exit=False)
# close gdb
gdb_quit(0 if result.result.wasSuccessful() else 1)
