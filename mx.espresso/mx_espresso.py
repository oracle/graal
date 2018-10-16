#
# Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

import mx
import mx_sdk

from mx_gate import Task, add_gate_runner
from mx_unittest import unittest

_suite = mx.suite('espresso')


class EspressoDefaultTags:
    default = 'default'
    all = 'all'


def _run_espresso(args, verbose=False):
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)

    mx.run_java(vm_args
                + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
                + ["com.oracle.truffle.espresso.launcher.EspressoLauncher"]
                + args)


def _espresso_gate_runner(args, tasks):
    with Task('UnitTests', tasks, tags=[EspressoDefaultTags.default, EspressoDefaultTags.all]) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', 'com.oracle.truffle.espresso.test'])


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)


mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=_suite,
    name='Espresso',
    short_name='java',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=[
        'espresso:ESPRESSO',
    ],
    support_distributions=[
        'espresso:ESPRESSO_SUPPORT',
    ],
    launcher_configs=[
        mx_sdk.LanguageLauncherConfig(
            destination='bin/<exe:espresso>',
            jar_distributions=['espresso:ESPRESSO_LAUNCHER'],
            main_class='com.oracle.truffle.espresso.launcher.EspressoLauncher',
            build_args=['--language:java']
        )
    ],
))


# register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso, ''],
})
