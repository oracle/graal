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

import os

import mx
import mx_espresso_benchmarks  # pylint: disable=unused-import
import mx_sdk_vm
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot


_suite = mx.suite('espresso')


def _espresso_launcher_command(args):
    """Espresso launcher embedded in GraalVM + arguments"""
    import mx_sdk_vm_impl
    bin_dir = os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin')
    exe = os.path.join(bin_dir, mx.exe_suffix('espresso'))
    if not os.path.exists(exe):
        exe = os.path.join(bin_dir, mx.cmd_suffix('espresso'))
    return [exe] + args

def _espresso_java_command(args):
    """Java launcher using libespresso in GraalVM + arguments"""
    import mx_sdk_vm_impl
    bin_dir = os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin')
    exe = os.path.join(bin_dir, mx.exe_suffix('java'))
    if not os.path.exists(exe):
        exe = os.path.join(bin_dir, mx.cmd_suffix('java'))
    return [exe, '-truffle'] + args

def _espresso_standalone_command(args):
    """Espresso standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    return (
        vm_args
        + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
        + [mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )


def _run_espresso_launcher(args=None, cwd=None, nonZeroIsFatal=True):
    """Run Espresso launcher within a GraalVM"""
    return mx.run(_espresso_launcher_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_espresso_standalone(args=None, cwd=None, nonZeroIsFatal=True):
    """Run standalone Espresso (not as part of GraalVM) from distribution jars"""
    return mx.run_java(_espresso_standalone_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_espresso_java(args=None, cwd=None, nonZeroIsFatal=True):
    """Run espresso through the standard java launcher within a GraalVM"""
    return mx.run(_espresso_java_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_espresso_meta(args, nonZeroIsFatal=True):
    """Run Espresso (standalone) on Espresso (launcher)"""
    return _run_espresso_launcher(['--vm.Xss4m'] + _espresso_standalone_command(args), nonZeroIsFatal=nonZeroIsFatal)


class EspressoDefaultTags:
    jackpot = 'jackpot'
    meta = 'meta'
    exit = 'exit'


def _espresso_gate_runner(args, tasks):
    # Jackpot configuration is inherited from Truffle.
    with Task('Jackpot', tasks, tags=[EspressoDefaultTags.jackpot]) as t:
        if t:
            jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)


def verify_ci(args):
    """Verify CI configuration"""
    mx.verify_ci(args, mx.suite('truffle'), _suite, 'common.json')


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)


if mx_sdk_vm.base_jdk_version() > 8:
    if mx.is_windows():
        lib_espresso_cp = '%GRAALVM_HOME%\\lib\\graalvm\\lib-espresso.jar'
    else:
        lib_espresso_cp = '${GRAALVM_HOME}/lib/graalvm/lib-espresso.jar'
else:
    if mx.is_windows():
        lib_espresso_cp = '%GRAALVM_HOME%\\jre\\lib\\graalvm\\lib-espresso.jar'
    else:
        lib_espresso_cp = '${GRAALVM_HOME}/jre/lib/graalvm/lib-espresso.jar'


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Java on Truffle',
    short_name='java',
    installable_id='espresso',
    installable=True,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'Truffle NFI', 'ejvm'],
    truffle_jars=['espresso:ESPRESSO'],
    support_distributions=['espresso:ESPRESSO_SUPPORT'],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination='lib/<lib:espresso>',
            jar_distributions=['espresso:LIB_ESPRESSO'],
            build_args=[
                '--language:java',
                '--tool:all',
            ],
            home_finder=True,
        )
    ],
    polyglot_lib_jar_dependencies=['espresso:LIB_ESPRESSO'],
    has_polyglot_lib_entrypoints=True,
    priority=1,
    post_install_msg="""
This version of Java on Truffle is experimental. We do not recommended it for production use.

Usage: java -truffle [-options] class [args...]
           (to execute a class)
    or java -truffle [-options] -jar jarfile [args...]
           (to execute a jar file)

To rebuild the polyglot library:
    gu rebuild-images libpolyglot -cp """ + lib_espresso_cp,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Espresso libjvm',
    short_name='ejvm',
    dir_name='truffle',
    installable_id='espresso',
    installable=True,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Java on Truffle'],
    support_libraries_distributions=['espresso:ESPRESSO_JVM_SUPPORT'],
    priority=2,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Espresso Launcher',
    short_name='elau',
    installable=False,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Java on Truffle'],
    truffle_jars=[],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination='bin/<exe:espresso>',
            jar_distributions=['espresso:ESPRESSO_LAUNCHER'],
            main_class='com.oracle.truffle.espresso.launcher.EspressoLauncher',
            build_args=[],
            language='java',
        )
    ],
))


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso_launcher, '[args]'],
    'espresso-standalone': [_run_espresso_standalone, '[args]'],
    'espresso-java': [_run_espresso_java, '[args]'],
    'espresso-meta': [_run_espresso_meta, '[args]'],
    'verify-ci' : [verify_ci, '[options]'],
})

# Build configs
# pylint: disable=bad-whitespace
mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'ejvm', 'elau', 'nfi', 'sdk', 'tfl'                                        ], _suite, env_file='jvm')
mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'ejvm', 'elau', 'nfi', 'sdk', 'tfl', 'cmp'                                 ], _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'ejvm', 'elau', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee'                        ], _suite, env_file='jvm-ee')
mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'ejvm', 'elau', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm'         , 'tflm'], _suite, env_file='native-ce')
mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'ejvm', 'elau', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmee', 'tflm'], _suite, env_file='native-ee')
