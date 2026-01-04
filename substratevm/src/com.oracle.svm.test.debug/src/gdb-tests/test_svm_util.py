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


class TestTypeUtils(unittest.TestCase):

    compressed_type = gdb.lookup_type('_z_.java.lang.Object')
    uncompressed_type = gdb.lookup_type('java.lang.Object')

    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()
        self.svm_util = SVMUtil()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_compressed_check(self):
        gdb_start()
        self.assertTrue(self.svm_util.is_compressed(self.compressed_type))
        self.assertFalse(self.svm_util.is_compressed(self.uncompressed_type))

    def test_uncompress(self):
        gdb_start()
        self.assertEqual(self.svm_util.get_uncompressed_type(self.compressed_type), self.uncompressed_type)

    def test_uncompress_uncompressed_type(self):
        gdb_start()
        self.assertEqual(self.svm_util.get_uncompressed_type(self.uncompressed_type), self.uncompressed_type)

    def test_compress(self):
        gdb_start()
        self.assertEqual(self.svm_util.get_compressed_type(self.uncompressed_type), self.compressed_type)

    def test_compress_compressed_type(self):
        gdb_start()
        self.assertEqual(self.svm_util.get_compressed_type(self.compressed_type), self.compressed_type)

    def test_get_unqualified_type_name(self):
        self.assertEqual(self.svm_util.get_unqualified_type_name("classloader_name::package.name.Parent$Inner"), "Inner")


class TestObjUtils(unittest.TestCase):

    compressed_type = gdb.lookup_type('_z_.java.lang.Object')
    uncompressed_type = gdb.lookup_type('java.lang.Object')

    def setUp(self):
        self.maxDiff = None
        set_up_test()
        set_up_gdb_debughelpers()
        gdb_start()
        self.svm_util = SVMUtil()

    def tearDown(self):
        gdb_delete_breakpoints()
        gdb_kill()

    def test_compressed_hub_oop(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_continue()
        # get a compressed hub
        z_hub = gdb.parse_and_eval('strList.hub')
        self.assertNotEqual(int(z_hub), 0)
        self.assertTrue(self.svm_util.is_compressed(z_hub.type))
        # the hub field type does not have an 'uncompressed' type
        # we can just check if an absolute address is converted correctly to a compressed oop
        hub = z_hub.dereference()
        self.assertEqual(self.svm_util.get_compressed_oop(hub), int(z_hub))

        hub_str = str(hub)
        SVMUtil.prompt_hook()
        z_hub_str = str(z_hub)
        SVMUtil.prompt_hook()
        self.assertEqual(hub_str, z_hub_str)

    def test_compressed_oop(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_continue()
        # get a compressed object
        z_name = gdb.parse_and_eval('strList.hub.name')
        self.assertNotEqual(int(z_name), 0)
        self.assertTrue(self.svm_util.is_compressed(z_name.type))
        # get the uncompressed value for the hub name
        name = z_name.dereference()
        name = name.cast(self.svm_util.get_uncompressed_type(name.type))
        self.assertFalse(self.svm_util.is_compressed(name.type))
        self.assertEqual(self.svm_util.get_compressed_oop(name), int(z_name))

        name_str = str(name)
        SVMUtil.prompt_hook()
        z_name_str = str(z_name)
        SVMUtil.prompt_hook()
        self.assertEqual(name_str, z_name_str)

    def test_adr_str(self):
        null = gdb.Value(0)
        val = gdb.Value(int(0xCAFE))
        self.assertEqual(self.svm_util.adr_str(null.cast(self.compressed_type.pointer())), ' @z(0x0)')
        self.assertEqual(self.svm_util.adr_str(val.cast(self.compressed_type.pointer())), ' @z(0xcafe)')
        self.assertEqual(self.svm_util.adr_str(null.cast(self.uncompressed_type.pointer())), ' @(0x0)')
        self.assertEqual(self.svm_util.adr_str(val.cast(self.uncompressed_type.pointer())), ' @(0xcafe)')

    def test_java_string(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testString")
        gdb_continue()
        string = gdb.parse_and_eval("str")
        null = gdb.Value(0).cast(string.type)
        self.assertEqual(self.svm_util.get_java_string(string), 'string')
        self.assertEqual(self.svm_util.get_java_string(null), "")

    def test_java_string_as_gdb_output_string(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testString")
        gdb_continue()
        string = gdb.parse_and_eval("str")
        gdb_set_param("svm-print-string-limit", "2")
        self.assertEqual(self.svm_util.get_java_string(string, True), 'st...')
        gdb_set_param("svm-print-string-limit", "unlimited")
        self.assertEqual(self.svm_util.get_java_string(string, True), 'string')

    def test_get_rtt(self):
        gdb_set_breakpoint("com.oracle.svm.test.debug.helper.PrettyPrinterTest::testArrayList")
        gdb_continue()
        mixed_list = gdb.parse_and_eval("mixedList")  # static type is List
        str_list = gdb.parse_and_eval("strList")  # static type is ArrayList
        self.assertEqual(self.svm_util.get_rtt(str_list), self.svm_util.get_rtt(mixed_list))  # both are of rtt ArrayList
        self.assertEqual(self.svm_util.get_rtt(str_list['elementData']['data'][0]), gdb.lookup_type('_z_.java.lang.String'))


# redirect unittest output to terminal
result = unittest.main(testRunner=unittest.TextTestRunner(stream=sys.__stdout__), exit=False)
# close gdb
gdb_quit(0 if result.result.wasSuccessful() else 1)
