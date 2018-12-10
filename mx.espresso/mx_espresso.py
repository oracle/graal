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

import os
from argparse import ArgumentParser

import mx
import mx_sdk
from mx_gate import Task, add_gate_runner
from mx_unittest import unittest

_suite = mx.suite('espresso')


class EspressoDefaultTags:
    default = 'default'
    all = 'all'
    native = 'native'


def _run_espresso(args):
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)

    mx.run_java(vm_args
                + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
                + [mx.distribution('ESPRESSO_LAUNCHER').mainClass]
                + args)


def _run_espresso_native(graalvm_home, args, distributions=None):
    espresso_native = os.path.join(graalvm_home, 'bin', 'espresso')
    if not os.path.exists(espresso_native):
        mx.abort(
            'Cannot find Espresso native image: "{}". Did you forget to run "mx --env espresso.env build"?'.format(
                espresso_native))

    vm_args, args = mx.extract_VM_args(args, useDoubleDash=False, defaultAllVMArgs=False, allowClasspath=True)

    native_cp = mx.classpath(distributions or [])
    user_cp_index, user_cp_value = mx.find_classpath_arg(vm_args)

    if user_cp_value:
        native_cp = os.pathsep.join([native_cp, user_cp_value]) if native_cp else user_cp_value
        vm_args[user_cp_index - 1:user_cp_index + 1] = []

    return mx.run([espresso_native] +
                  vm_args +
                  mx.get_runtime_jvm_args(distributions or [], jdk=mx.get_jdk(), cp_suffix=native_cp) +
                  args
                  )


def _run_espresso_playground(args, verbose=False):
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=False, defaultAllVMArgs=False)
    parser = ArgumentParser(prog='mx espresso-playground')
    parser.add_argument('main_class', action='store', help='Unqualified class name to run.')
    parser.add_argument('main_class_args', nargs='*')
    args = parser.parse_args(args)

    return _run_espresso(vm_args +
                         ['-cp', mx.classpath("ESPRESSO_PLAYGROUND"),
                          'com.oracle.truffle.espresso.playground.' + args.main_class] + args.main_class_args,
                         verbose)


def _espresso_gate_runner(args, tasks):
    with Task('UnitTests', tasks, tags=[EspressoDefaultTags.default, EspressoDefaultTags.all]) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', 'com.oracle.truffle.espresso.test'])

    with Task('HelloWorld', tasks, tags=[EspressoDefaultTags.native]) as t:
        if t:
            _run_espresso_native(['--distribution', 'ESPRESSO_PLAYGROUND', 'com.oracle.truffle.espresso.playground.HelloWorld'])


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
    'espresso-playground': [_run_espresso_playground, ''],
    'espresso-native': [_run_espresso_native, ''],
})
