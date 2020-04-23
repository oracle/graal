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

import os

import mx
import mx_benchmark
import mx_espresso_benchmarks
import mx_sdk_vm
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from mx_unittest import unittest


_suite = mx.suite('espresso')


def _espresso_launcher_command(args):
    """Espresso launcher embedded in GraalVM + arguments"""
    import mx_sdk_vm_impl
    return [os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin', mx.cmd_suffix('espresso'))] + args

def _espresso_standalone_command(args):
    """Espresso standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    return (
        vm_args
        + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
        + [mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )


def _run_espresso_launcher(args=None, cwd=None):
    """Run Espresso launcher within a GraalVM"""
    mx.run(_espresso_launcher_command(args), cwd=cwd)


def _run_espresso_standalone(args=None, cwd=None):
    """Run standalone Espresso (not as part of GraalVM) from distribution jars"""
    mx.run_java(_espresso_standalone_command(args), cwd=cwd)


def _run_espresso_meta(args):
    """Run Espresso (standalone) on Espresso (launcher)"""
    _run_espresso_launcher(['--vm.Xss4m'] + _espresso_standalone_command(args))


def _run_espresso_playground(args):
    """Run Espresso test programs"""
    parser = ArgumentParser(prog='mx espresso-playground')
    parser.add_argument('main_class', action='store', help='Unqualified class name to run.')
    parser.add_argument('main_class_args', nargs='*')
    args = parser.parse_args(args)
    return _run_espresso_launcher(['-cp', mx.classpath('ESPRESSO_PLAYGROUND'),
                            'com.oracle.truffle.espresso.playground.' + args.main_class]
                         + args.main_class_args)


class EspressoDefaultTags:
    unittest = 'unittest'
    unittest_with_compilation = 'unittest_with_compilation'
    jackpot = 'jackpot'
    meta = 'meta'


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


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso_launcher, '[args]'],
    'espresso-standalone': [_run_espresso_standalone, '[args]'],
    'espresso-meta': [_run_espresso_meta, '[args]'],
    'espresso-playground': [_run_espresso_playground, '[class_name] [args]'],
    'verify-ci' : [verify_ci, '[options]'],
})

# Build configs
# pylint: disable=bad-whitespace
mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'nfi', 'sdk', 'tfl'                                        ], _suite, env_file='jvm')
mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'nfi', 'sdk', 'tfl', 'cmp'                                 ], _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee'                        ], _suite, env_file='jvm-ee')
mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm'         , 'tflm'], _suite, env_file='native-ce')
mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmee', 'tflm'], _suite, env_file='native-ee')

# Register Espresso as a (guest) JVM for running `mx benchmark`.
mx_benchmark.java_vm_registry.add_vm(mx_espresso_benchmarks.EspressoVm('default', []), _suite)
mx_benchmark.java_vm_registry.add_vm(mx_espresso_benchmarks.EspressoVm('la-inline', ['--experimental-options', '--engine.LanguageAgnosticInlining']), _suite)
