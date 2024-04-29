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


class TestLoadPrettyPrinter(unittest.TestCase):
    @classmethod
    def setUp(cls):
        set_up_test()
        clear_pretty_printers()
        gdb_reload_executable()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_auto_load(self):
        gdb_start()
        exec_string = gdb_execute('info pretty-printer')
        self.assertIn("SubstrateVM", exec_string, 'pretty-printer was not loaded')
        # assume that there are no other pretty-printers were attached to an objfile
        self.assertIn("objfile", exec_string, 'pretty-printer was not attached to an objfile')

    def test_manual_load(self):
        backup_auto_load_param = gdb_get_param("auto-load python-scripts")
        gdb_set_param("auto-load python-scripts", "off")
        gdb_start()
        try:
            exec_string = gdb_execute('info pretty-printer')
            self.assertNotIn("objfile", exec_string, "No objfile pretty printer should be loaded yet")
            gdb_execute('source gdb-debughelpers.py')
            exec_string = gdb_execute('info pretty-printer')
            self.assertIn("objfile", exec_string)  # check if any objfile has a pretty-printer
            self.assertIn("SubstrateVM", exec_string)
        finally:
            # make sure auto-loading is re-enabled for other tests
            gdb_set_param("auto-load python-scripts", backup_auto_load_param)

    def test_manual_load_without_executable(self):
        self.assertIn('AssertionError', gdb_execute("source gdb-debughelpers.py"))

    def test_auto_reload(self):
        gdb_start()
        gdb_start()  # all loaded shared libraries get freed (their pretty printers are removed) and newly attached
        exec_string = gdb_execute('info pretty-printer')
        self.assertIn("SubstrateVM", exec_string, 'pretty-printer was not loaded')
        self.assertIn("objfile", exec_string, 'pretty-printer was not attached to an objfile')


class TestCInterface(unittest.TestCase):
    @classmethod
    def setUp(cls):
        cls.maxDiff = None
        set_up_test()
        gdb_start()
        set_up_gdb_debughelpers()

    @classmethod
    def tearDown(cls):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_print_from_c(self):
        gdb_set_breakpoint("com.oracle.svm.tutorial.CInterfaceTutorial::releaseData")
        gdb_continue()
        gdb_set_breakpoint("org.graalvm.nativeimage.ObjectHandles::getGlobal")
        gdb_continue()
        gdb_finish()
        gdb_next()
        self.assertTrue(gdb_output('javaObject').startswith('"Hello World at'))

    def test_print_from_java_shared_libray(self):
        gdb_set_breakpoint("com.oracle.svm.tutorial.CInterfaceTutorial::dump")
        gdb_continue()
        exec_string = gdb_output("data")
        self.assertTrue(exec_string.startswith('my_data = {'), f"GDB output: '{exec_string}'")
        self.assertIn('f_primitive = 42', exec_string)
        self.assertIn('f_array = int32_t [4] = {...}', exec_string)
        self.assertRegex(exec_string, f'f_cstr = 0x{hex_rexp.pattern} "Hello World"')
        self.assertRegex(exec_string, f'f_java_object_handle = 0x{hex_rexp.pattern}')
        self.assertRegex(exec_string, f'f_print_function = 0x{hex_rexp.pattern} <c_print>')


# redirect unittest output to terminal
unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__))
