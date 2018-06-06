# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import mx
import mx_sdk
from mx_unittest import unittest
from mx_gate import Task, add_gate_runner

_suite = mx.suite('regex')


def _tregex_tests_gate_runner(args, tasks):
    with Task('UnitTests', tasks, tags=['default', 'all']) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', 'com.oracle.truffle.regex'])


mx_sdk.register_graalvm_component(mx_sdk.GraalVmTool(
    suite=_suite,
    name='TRegex',
    short_name='rgx',
    dir_name='regex',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=['regex:TREGEX'],
    support_distributions=['regex:TREGEX_GRAALVM_SUPPORT'],
))

add_gate_runner(_suite, _tregex_tests_gate_runner)
