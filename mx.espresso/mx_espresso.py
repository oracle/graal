#
# Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

from argparse import ArgumentParser

import mx
import mx_benchmark
import mx_sdk_vm
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from mx_unittest import unittest

_suite = mx.suite('espresso')


class EspressoVM(mx_benchmark.GuestVm, mx_benchmark.JavaVm):
    def __init__(self, host_vm, config_name, host_vm_args):
        super(EspressoVM, self).__init__(host_vm)
        self._config_name = config_name
        self._host_vm_args = host_vm_args

    def with_host_vm(self, host_vm):
        return self.__class__(host_vm, self._config_name, self._host_vm_args)

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def name(self):
        return "espresso"

    def config_name(self):
        return self._config_name

    def run(self, cwd, args):
        return self.host_vm().run(cwd, self._host_vm_args + _espresso_command(args))


# Register Espresso as a VM for running `mx benchmark`.
mx_benchmark.java_vm_registry.add_vm(EspressoVM(None, 'interpreter', ['-Dgraal.TruffleCompilation=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVM(None, 'jit', []), _suite, priority=1)


class EspressoDefaultTags:
    unittest = 'unittest'
    unittest_with_compilation = 'unittest_with_compilation'
    jackpot = 'jackpot'
    meta = 'meta'


def _espresso_command(args):
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    return (
        vm_args
        + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
        + [mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )


def _run_espresso(args, cwd=None):
    mx.run_java(_espresso_command(args), cwd=cwd)


def _run_espresso_meta(args):
    mx.run_java(['-Xss5m'] + _espresso_command(_espresso_command(args)))


def _run_espresso_playground(args):
    parser = ArgumentParser(prog='mx espresso-playground')
    parser.add_argument('main_class', action='store', help='Unqualified class name to run.')
    parser.add_argument('main_class_args', nargs='*')
    args = parser.parse_args(args)

    return _run_espresso(['-cp', mx.classpath('ESPRESSO_PLAYGROUND'),
                            'com.oracle.truffle.espresso.playground.' + args.main_class]
                         + args.main_class_args)


def _espresso_gate_runner(args, tasks):
    with Task('UnitTestsWithCompilation', tasks, tags=[EspressoDefaultTags.unittest_with_compilation]) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', '--suite', 'espresso',
                      '--', # pass VM options
                      '-Dgraal.TruffleCompileImmediately=true',
                      '-Dgraal.TruffleBackgroundCompilation=false',
                      '-Dgraal.TruffleCompileOnly=espresso',
                      # '-Dgraal.TraceTruffleCompilation=true', # Too verbose
                      ])

    with Task('UnitTests', tasks, tags=[EspressoDefaultTags.unittest]) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', '--suite', 'espresso'])

    # Jackpot configuration is inherited from Truffle.
    with Task('Jackpot', tasks, tags=[EspressoDefaultTags.jackpot]) as t:
        if t:
            jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)

    with Task('Meta', tasks, tags=[EspressoDefaultTags.meta]) as t:
        if t:
            _run_espresso_meta(args=['-cp', mx.classpath('ESPRESSO_PLAYGROUND'), 'com.oracle.truffle.espresso.playground.HelloWorld'])


def verify_ci(args):
    """Verify CI configuration"""
    mx.verify_ci(args, mx.suite('truffle'), _suite, 'common.json')


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Espresso',
    short_name='java',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'Truffle NFI'],
    truffle_jars=['espresso:ESPRESSO'],
    support_distributions=['espresso:ESPRESSO_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination='bin/<exe:espresso>',
            jar_distributions=['espresso:ESPRESSO_LAUNCHER'],
            main_class='com.oracle.truffle.espresso.launcher.EspressoLauncher',
            build_args=['--language:java'],
            language='java',
        )
    ],
))


# register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso, ''],
    'espresso-meta': [_run_espresso_meta, ''],
    'espresso-playground': [_run_espresso_playground, ''],
    'verify-ci' : [verify_ci, '[options]'],
})

# Build configs
# pylint: disable=bad-whitespace
mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'nfi', 'sdk', 'tfl'                                        ], _suite, env_file='jvm')
mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'nfi', 'sdk', 'tfl', 'cmp'                                 ], _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee'                        ], _suite, env_file='jvm-ee')
mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm'         , 'tflm'], _suite, env_file='native-ce')
mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmee', 'tflm'], _suite, env_file='native-ee')
