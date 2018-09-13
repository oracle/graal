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
import os
import subprocess

from mx_gate import Task, add_gate_runner
from mx_unittest import unittest

_suite = mx.suite('espresso')
_root = os.path.join(_suite.dir, "java/")
_benchroot = os.path.join(_suite.dir, "../espresso-bench/src/")


def runEspresso(args):
    _runEspresso(args, verbose=False)


def testEspresso(args):
    _testEspresso(args)


# def benchEspresso(args):
#     _runBenchmarks(args, runEspresso, [])
#
#
# def benchBaselineJavaServer(args):
#     _runBenchmarks(args, mx.run_java, ['-server'])
#
#
# def benchBaselineJavaClient(args):
#     _runBenchmarks(args, mx.run_java, ['-client'])

def _runEspresso(args, verbose):
    vmArgs, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)

    mx.run_java(vmArgs
                + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
                + ["com.oracle.truffle.espresso.launcher.EspressoLauncher"]
                + args)


def _testEspresso(args):
    _testEspressoSuite(args, "framework.AllTests")
    _testEspressoSuite(args + ['-Dtruffle.espresso.dynamic=true'], "framework.AllTests")


def _testEspressoSuite(args, suitename, package='com.oracle.truffle.espresso.test'):
    # grab additional java properties specified in suite.py
    javaProps = mx.project(package).getJavaProperties()
    for (_, item) in enumerate(javaProps):
        args += ['-D' + item + '=' + javaProps[item]]

    # grab vm arguments
    vmArgs, espressoArgs, cp = _truffle_extract_VM_args(args)


    mx.run_java(vmArgs + ['-ea',
                          '-Dgraal.TruffleBackgroundCompilation=false',
                          '-Dgraal.TruffleCompilationThreshold=3',
                          '-Dgraal.TraceTruffleCompilation=false']
                + mx.get_runtime_jvm_args('ESPRESSO', jdk=mx.get_jdk()) + ["com.oracle.truffle.espresso.test." + suitename] + espressoArgs)


def _runBenchmarks(args, runner, vmArgs=None):
    print "Building source..."
    if mx.command_function('build')(['-p', '--warning-as-error', '--force-javac']):
        print "Build not successful!"
    else:
        if os.path.isdir(_benchroot):
            os.chdir(_benchroot)
            print "Compiling benchmarks with javac..."
            os.system("javac *.java")
            print "Running benchmarks..."

            if vmArgs is None:
                vmArgs = []

            runner(vmArgs + ['-cp', _benchroot, "Harness"] + args)
        else:
            print "Checkout espresso-bench repository!"


def _truffle_extract_VM_args(args, useDoubleDash=False):
    vmArgs, remainder, classpath = [], [], ""
    argIter = iter(enumerate(args))
    for (i, arg) in argIter:
        if any(arg.startswith(prefix) for prefix in ['-X', '-G:', '-D', '-verbose', '-ea']) or arg in ['-esa']:
            vmArgs += [arg]
        elif arg in ['-cp']:
            (i, arg) = next(argIter)
            classpath = arg
        elif useDoubleDash and arg == '--':
            remainder += args[i:]
            break
        else:
            remainder += [arg]

    return vmArgs, remainder, classpath


# REGISTER MX GATE RUNNER
#########################

add_gate_runner(_suite, lambda args, tasks: executeGate())


def executeGate():
    """
    Executes custom Tasks from Espresso with a 'mx gate'
    """
    tasks = []
    with Task('UnitTests', tasks) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', 'com.oracle.truffle.espresso.test'])

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
    'espresso': [runEspresso, ''],
    'espresso-test': [testEspresso, ''],
    # 'espresso-bench': [benchEspresso, ''],
    # 'espresso-bench-server': [benchBaselineJavaServer, ''],
    # 'espresso-bench-client': [benchBaselineJavaClient, ''],
})