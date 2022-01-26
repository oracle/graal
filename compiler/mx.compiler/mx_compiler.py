#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function
import os
from os.path import join, exists, getmtime, basename, dirname, isdir
from argparse import ArgumentParser, RawDescriptionHelpFormatter, REMAINDER
import re
import stat
import zipfile
import tarfile
import subprocess
import tempfile
import sys

import mx_truffle
import mx_sdk_vm

import mx
import mx_gate
from mx_gate import Task, Tags
from mx import SafeDirectoryUpdater

import mx_unittest
from mx_unittest import unittest

from mx_javamodules import as_java_module
from mx_updategraalinopenjdk import updategraalinopenjdk
from mx_renamegraalpackages import renamegraalpackages
import mx_sdk_vm_impl

import mx_benchmark
import mx_graal_benchmark
import mx_graal_tools #pylint: disable=unused-import

import argparse
import shlex
import json

# Temporary imports and (re)definitions while porting mx from Python 2 to Python 3
if sys.version_info[0] < 3:
    from StringIO import StringIO
    _unicode = unicode # pylint: disable=undefined-variable
    def _decode(x):
        return x
    def _encode(x):
        return x
else:
    from io import StringIO
    _unicode = str
    def _decode(x):
        return x.decode()
    def _encode(x):
        return x.encode()

_basestring = (str, _unicode)

_suite = mx.suite('compiler')

""" Prefix for running the VM. """
_vm_prefix = None

def get_vm_prefix(asList=True):
    """
    Get the prefix for running the VM (e.g. "gdb --args").
    """
    if asList:
        return _vm_prefix.split() if _vm_prefix is not None else []
    return _vm_prefix

#: The JDK used to build and run Graal.
jdk = mx.get_jdk(tag='default')

#: 3-tuple (major, minor, build) of JVMCI version, if any, denoted by `jdk`
_jdk_jvmci_version = None

if jdk.javaCompliance < '1.8':
    mx.abort('Graal requires JDK8 or later, got ' + str(jdk))

#: Specifies if Graal is being built/run against JDK8. If false, then
#: JDK9 or later is being used (checked above).
isJDK8 = jdk.javaCompliance < '1.9'


def _check_jvmci_version(jdk):
    """
    Runs a Java utility to check that `jdk` supports the minimum JVMCI API required by Graal.

    This runs a version of org.graalvm.compiler.hotspot.JVMCIVersionCheck that is "moved"
    to the unnamed package. Without this, on JDK 10+, the class in the jdk.internal.vm.compiler
    module will be run instead of the version on the class path.
    """
    unqualified_name = 'JVMCIVersionCheck'
    qualified_name = 'org.graalvm.compiler.hotspot.' + unqualified_name
    binDir = mx.ensure_dir_exists(join(_suite.get_output_root(), '.jdk' + str(jdk.version)))

    if isinstance(_suite, mx.BinarySuite):
        dists = [d for d in _suite.dists if d.name == 'GRAAL_HOTSPOT']
        assert len(dists) == 1, 'could not find GRAAL_HOTSPOT distribution'
        d = dists[0]
        assert exists(d.sourcesPath), 'missing expected file: ' + d.sourcesPath
        source_timestamp = getmtime(d.sourcesPath)
        def source_supplier():
            with zipfile.ZipFile(d.sourcesPath, 'r') as zf:
                return zf.read(qualified_name.replace('.', '/') + '.java')
    else:
        source_path = join(_suite.dir, 'src', 'org.graalvm.compiler.hotspot', 'src', qualified_name.replace('.', '/') + '.java')
        source_timestamp = getmtime(source_path)
        def source_supplier():
            with open(source_path, 'r') as fp:
                return fp.read()

    unqualified_class_file = join(binDir, unqualified_name + '.class')
    if not exists(unqualified_class_file) or getmtime(unqualified_class_file) < source_timestamp:
        with SafeDirectoryUpdater(binDir, create=True) as sdu:
            unqualified_source_path = join(sdu.directory, unqualified_name + '.java')
            with open(unqualified_source_path, 'w') as fp:
                fp.write(source_supplier().replace('package org.graalvm.compiler.hotspot;', ''))
            mx.run([jdk.javac, '-d', sdu.directory, unqualified_source_path])

    jvmci_version_file = join(binDir, 'jvmci_version.' + str(os.getpid()))
    mx.run([jdk.java, '-DJVMCIVersionCheck.jvmci.version.file=' + jvmci_version_file, '-cp', binDir, unqualified_name])
    if exists(jvmci_version_file):
        with open(jvmci_version_file) as fp:
            global _jdk_jvmci_version
            _jdk_jvmci_version = tuple((int(n) for n in fp.read().split(',')))
        os.remove(jvmci_version_file)

if os.environ.get('JVMCI_VERSION_CHECK', None) != 'ignore':
    _check_jvmci_version(jdk)

mx_gate.add_jacoco_includes(['org.graalvm.*'])
mx_gate.add_jacoco_excludes(['com.oracle.truffle'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

def _get_graal_option(vmargs, name, default=None, prefix='-Dgraal.'):
    """
    Gets the value of the `name` Graal option in `vmargs`.

    :param list vmargs: VM arguments to inspect
    :param str name: the name of the option
    :param default: the default value of the option if it's not present in `vmargs`
    :param str prefix: the prefix used for Graal options in `vmargs`
    :return: the value of the option as specified in `vmargs` or `default`
    """
    if vmargs:
        for arg in reversed(vmargs):
            selector = prefix + name + '='
            if arg.startswith(selector):
                return arg[len(selector):]
    return default

def _get_XX_option_value(vmargs, name, default):
    """
    Gets the value of an ``-XX:`` style HotSpot VM option.

    :param list vmargs: VM arguments to inspect
    :param str name: the name of the option
    :param default: the default value of the option if it's not present in `vmargs`
    :return: the value of the option as specified in `vmargs` or `default`
    """
    for arg in reversed(vmargs):
        if arg == '-XX:-' + name:
            return False
        if arg == '-XX:+' + name:
            return True
        if arg.startswith('-XX:' + name + '='):
            return arg[len('-XX:' + name + '='):]
    return default

def _is_jvmci_enabled(vmargs):
    """
    Determines if JVMCI is enabled according to the given VM arguments and the default value of EnableJVMCI.

    :param list vmargs: VM arguments to inspect
    """
    return _get_XX_option_value(vmargs, 'EnableJVMCI', mx_sdk_vm.jdk_enables_jvmci_by_default(jdk))

def _nodeCostDump(args, extraVMarguments=None):
    """list the costs associated with each Node type"""
    import csv
    parser = ArgumentParser(prog='mx nodecostdump')
    parser.add_argument('--regex', action='store', help="Node Name Regex", default=False, metavar='<regex>')
    parser.add_argument('--markdown', action='store_const', const=True, help="Format to Markdown table", default=False)
    args, vmargs = parser.parse_known_args(args)
    additionalPrimarySuiteClassPath = '-Dprimary.suite.cp=' + mx.primary_suite().dir
    vmargs.extend([additionalPrimarySuiteClassPath, '-cp', mx.classpath('org.graalvm.compiler.hotspot.test'), '-XX:-UseJVMCIClassLoader', 'org.graalvm.compiler.hotspot.test.NodeCostDumpUtil'])
    out = mx.OutputCapture()
    regex = ""
    if args.regex:
        regex = args.regex
    run_vm(vmargs + _remove_empty_entries(extraVMarguments) + [regex], out=out)
    if args.markdown:
        stringIO = StringIO(out.data)
        reader = csv.reader(stringIO, delimiter=';', lineterminator="\n")
        firstRow = True
        maxLen = 0
        for row in reader:
            for col in row:
                maxLen = max(maxLen, len(col))
        stringIO.seek(0)
        for row in reader:
            s = '|'
            if firstRow:
                firstRow = False
                nrOfCols = len(row)
                for col in row:
                    s = s + col + "|"
                print(s)
                s = '|'
                for _ in range(nrOfCols):
                    s = s + ('-' * maxLen) + '|'
            else:
                for col in row:
                    s = s + col + "|"
            print(s)
    else:
        print(out.data)

def _ctw_jvmci_export_args():
    """
    Gets the VM args needed to export JVMCI API required by CTW.
    """
    if isJDK8:
        return ['-XX:-UseJVMCIClassLoader']
    else:
        return ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
                '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED',
                '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED',
                '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED',
                '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED']

def _ctw_system_properties_suffix():
    out = mx.OutputCapture()
    out.data = 'System properties for CTW:\n\n'
    args = ['-XX:+EnableJVMCI'] + _ctw_jvmci_export_args()
    cp = _remove_redundant_entries(mx.classpath('GRAAL_TEST', jdk=jdk))
    args.extend(['-cp', cp, '-DCompileTheWorld.Help=true', 'org.graalvm.compiler.hotspot.test.CompileTheWorld'])
    run_java(args, out=out, addDefaultArgs=False)
    return out.data

def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    parser = ArgumentParser(prog='mx ctw', formatter_class=RawDescriptionHelpFormatter, epilog=_ctw_system_properties_suffix())
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')
    if not isJDK8:
        parser.add_argument('--limitmods', action='store', help='limits the set of compiled classes to only those in the listed modules', metavar='<modulename>[,<modulename>...]')

    args, vmargs = parser.parse_known_args(args)

    vmargs.extend(_remove_empty_entries(extraVMarguments))

    if mx.get_os() == 'darwin':
        # suppress menubar and dock when running on Mac
        vmargs.append('-Djava.awt.headless=true')

    if args.cp:
        cp = os.path.abspath(args.cp)
        if not isJDK8 and not _is_jvmci_enabled(vmargs):
            mx.abort('Non-Graal CTW does not support specifying a specific class path or jar to compile')
    else:
        # Default to the CompileTheWorld.SUN_BOOT_CLASS_PATH token
        cp = None

    # Exclude X11 classes as they may cause VM crashes (on Solaris)
    exclusionPrefix = '-DCompileTheWorld.ExcludeMethodFilter='
    exclusions = ','.join([a[len(exclusionPrefix):] for a in vmargs if a.startswith(exclusionPrefix)] + ['sun.awt.X11.*.*'])
    vmargs.append(exclusionPrefix + exclusions)

    if not _get_XX_option_value(vmargs, 'UseJVMCINativeLibrary', False):
        if _get_XX_option_value(vmargs, 'UseJVMCICompiler', False):
            if _get_XX_option_value(vmargs, 'BootstrapJVMCI', False) is None:
                vmargs.append('-XX:+BootstrapJVMCI')

    mainClassAndArgs = []
    if not _is_jvmci_enabled(vmargs):
        vmargs.append('-XX:+CompileTheWorld')
        if isJDK8 and cp is not None:
            vmargs.append('-Xbootclasspath/p:' + cp)
    else:
        if not isJDK8:
            # To be able to load all classes in the JRT with Class.forName,
            # all JDK modules need to be made root modules.
            limitmods = frozenset(args.limitmods.split(',')) if args.limitmods else None
            nonBootJDKModules = [m.name for m in jdk.get_modules() if not m.boot and (limitmods is None or m.name in limitmods)]
            if nonBootJDKModules:
                vmargs.append('--add-modules=' + ','.join(nonBootJDKModules))
            if args.limitmods:
                vmargs.append('-DCompileTheWorld.limitmods=' + args.limitmods)
        if cp is not None:
            vmargs.append('-DCompileTheWorld.Classpath=' + cp)
        cp = _remove_redundant_entries(mx.classpath('GRAAL_TEST', jdk=jdk))
        vmargs.extend(_ctw_jvmci_export_args() + ['-cp', cp])
        mainClassAndArgs = ['org.graalvm.compiler.hotspot.test.CompileTheWorld']

    run_vm(vmargs + mainClassAndArgs)

class UnitTestRun:
    def __init__(self, name, args, tags):
        self.name = name
        self.args = args
        self.tags = tags

    def run(self, suites, tasks, extraVMarguments=None, extraUnitTestArguments=None):
        for suite in suites:
            with Task(self.name + ': hosted-product ' + suite, tasks, tags=self.tags) as t:
                if mx_gate.Task.verbose:
                    extra_args = ['--verbose', '--enable-timing']
                else:
                    extra_args = []
                if Task.tags is None or 'coverage' not in Task.tags: # pylint: disable=unsupported-membership-test
                    # If this is a coverage execution, we want maximal coverage
                    # and thus must not fail fast.
                    extra_args += ['--fail-fast']
                if t: unittest(['--suite', suite] + extra_args + self.args + _remove_empty_entries(extraVMarguments) + _remove_empty_entries(extraUnitTestArguments))

class BootstrapTest:
    def __init__(self, name, args, tags, suppress=None):
        self.name = name
        self.args = args
        self.suppress = suppress
        self.tags = tags
        if tags is not None and (not isinstance(tags, list) or all(not isinstance(x, _basestring) for x in tags)):
            mx.abort("Gate tag argument must be a list of strings, tag argument:" + str(tags))

    def run(self, tasks, extraVMarguments=None):
        with Task(self.name, tasks, tags=self.tags) as t:
            if t:
                if self.suppress:
                    out = mx.DuplicateSuppressingStream(self.suppress).write
                else:
                    out = None
                run_vm(self.args + ['-XX:+UseJVMCICompiler'] + _remove_empty_entries(extraVMarguments) + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'], out=out)

class GraalTags:
    bootstrap = ['bootstrap', 'fulltest']
    bootstraplite = ['bootstraplite', 'bootstrap', 'fulltest']
    bootstrapfullverify = ['bootstrapfullverify', 'fulltest']
    bootstrapeconomy = ['bootstrapeconomy', 'economy', 'fulltest']
    test = ['test', 'fulltest']
    unittest = ['unittest', 'test', 'fulltest']
    coverage = ['coverage']
    benchmarktest = ['benchmarktest', 'fulltest']
    ctw = ['ctw', 'fulltest']
    ctweconomy = ['ctweconomy', 'economy', 'fulltest']
    doc = ['javadoc']

def _remove_empty_entries(a):
    """Removes empty entries. Return value is always a list."""
    if not a:
        return []
    return [x for x in a if x]

def _compiler_error_options(default_compilation_failure_action='ExitVM', vmargs=None, prefix='-Dgraal.'):
    """
    Gets options to be prefixed to the VM command line related to Graal compilation errors to improve
    the chance of graph dumps being emitted and preserved in CI build logs.

    :param str default_compilation_failure_action: value for CompilationFailureAction if it is added
    :param list vmargs: arguments to search for existing instances of the options added by this method
    :param str prefix: the prefix used for Graal options in `vmargs` and to use when adding options
    """
    action = _get_graal_option(vmargs, 'CompilationFailureAction')
    res = []

    # Add CompilationFailureAction if absent from vmargs
    if action is None:
        action = default_compilation_failure_action
        res.append(prefix + 'CompilationFailureAction=' + action)

    # Add DumpOnError=true if absent from vmargs and CompilationFailureAction is Diagnose or ExitVM.
    dump_on_error = _get_graal_option(vmargs, 'DumpOnError', prefix=prefix)
    if action in ('Diagnose', 'ExitVM'):
        if dump_on_error is None:
            res.append(prefix + 'DumpOnError=true')
            dump_on_error = 'true'

    # Add ShowDumpFiles=true if absent from vmargs and DumpOnError=true.
    if dump_on_error == 'true':
        show_dump_files = _get_graal_option(vmargs, 'ShowDumpFiles', prefix=prefix)
        if show_dump_files is None:
            res.append(prefix + 'ShowDumpFiles=true')
    return res

def _gate_dacapo(name, iterations, extraVMarguments=None, force_serial_gc=True, set_start_heap_size=True, threads=None):
    if iterations == -1:
        return
    vmargs = ['-XX:+UseSerialGC'] if force_serial_gc else []
    if set_start_heap_size:
        vmargs += ['-Xms2g']
    vmargs += ['-XX:-UseCompressedOops', '-Djava.net.preferIPv4Stack=true'] + _compiler_error_options() + _remove_empty_entries(extraVMarguments)
    args = ['-n', str(iterations), '--preserve']
    if threads is not None:
        args += ['-t', str(threads)]
    out = mx.TeeOutputCapture(mx.OutputCapture())
    exit_code, suite, results = mx_benchmark.gate_mx_benchmark(["dacapo:{}".format(name), "--tracker=none", "--"] + vmargs + ["--"] + args, out=out, err=out, nonZeroIsFatal=False)
    if exit_code != 0:
        mx.log(out)
        mx.abort("Gate for dacapo benchmark '{}' failed!".format(name))
    return exit_code, suite, results

def jdk_includes_corba(jdk):
    # corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)
    return jdk.javaCompliance < '11'

def _gate_scala_dacapo(name, iterations, extraVMarguments=None):
    if iterations == -1:
        return
    vmargs = ['-Xms2g', '-XX:+UseSerialGC', '-XX:-UseCompressedOops'] + _compiler_error_options() + _remove_empty_entries(extraVMarguments)

    args = ['-n', str(iterations), '--preserve']
    out = mx.TeeOutputCapture(mx.OutputCapture())
    exit_code, suite, results = mx_benchmark.gate_mx_benchmark(["scala-dacapo:{}".format(name), "--tracker=none", "--"] + vmargs + ["--"] + args, out=out, err=out, nonZeroIsFatal=False)
    if exit_code != 0:
        mx.log(out)
        mx.abort("Gate for scala-dacapo benchmark '{}' failed!".format(name))
    return exit_code, suite, results

def _check_catch_files():
    """
    Verifies that there is a "catch_files" array in common.json at the root of
    the repository containing this suite and that the array contains elements
    matching DebugContext.DUMP_FILE_MESSAGE_REGEXP and
    StandardPathUtilitiesProvider.DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP.
    """
    catch_files_fields = (
        ('DebugContext', 'DUMP_FILE_MESSAGE_REGEXP'),
        ('StandardPathUtilitiesProvider', 'DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP')
    )

    def get_regexp(class_name, field_name):
        source_path = join(_suite.dir, 'src', 'org.graalvm.compiler.debug', 'src', 'org', 'graalvm', 'compiler', 'debug', class_name + '.java')
        regexp = None
        with open(source_path) as fp:
            for line in fp.readlines():
                decl = field_name + ' = "'
                index = line.find(decl)
                if index != -1:
                    start_index = index + len(decl)
                    end_index = line.find('"', start_index)
                    regexp = line[start_index:end_index]

                    # Convert from Java style regexp to Python style
                    return regexp.replace('(?<', '(?P<')

        if not regexp:
            mx.abort('Could not find value of ' + field_name + ' in ' + source_path)
        return regexp

    common_path = join(dirname(_suite.dir), 'common.json')
    if not exists(common_path):
        mx.abort('Required file does not exist: {}'.format(common_path))
    with open(common_path) as common_file:
        common_cfg = json.load(common_file)
    catch_files = common_cfg.get('catch_files')
    if catch_files is None:
        mx.abort('Could not find catch_files attribute in {}'.format(common_path))
    for class_name, field_name in catch_files_fields:
        regexp = get_regexp(class_name, field_name)
        if regexp not in catch_files:
            mx.abort('Could not find catch_files entry in {} matching "{}"'.format(common_path, regexp))

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None, extraUnitTestArguments=None):
    with Task('CheckCatchFiles', tasks, tags=[Tags.style]) as t:
        if t: _check_catch_files()

    if jdk.javaCompliance >= '9':
        with Task('JDK_java_base_test', tasks, tags=['javabasetest']) as t:
            if t: java_base_unittest(_remove_empty_entries(extraVMarguments) + [])

    # Run unit tests in hosted mode
    for r in unit_test_runs:
        r.run(suites, tasks, ['-XX:-UseJVMCICompiler'] + _remove_empty_entries(extraVMarguments), extraUnitTestArguments=extraUnitTestArguments)

    # Run selected tests (initially those from GR-6581) under -Xcomp
    xcompTests = [
        'BlackholeDirectiveTest',
        'OpaqueDirectiveTest',
        'CompiledMethodTest',
        'ControlFlowAnchorDirectiveTest',
        'ConditionalElimination',
        'MarkUnsafeAccessTest',
        'PEAAssertionsTest',
        'MergeCanonicalizerTest',
        'ExplicitExceptionTest',
        'GuardedIntrinsicTest',
        'HashCodeTest',
        'ProfilingInfoTest',
        'GraalOSRLockTest'
    ]
    UnitTestRun('XcompUnitTests', [], tags=GraalTags.test).run(['compiler'], tasks, ['-Xcomp', '-XX:-UseJVMCICompiler'] + _remove_empty_entries(extraVMarguments) + xcompTests)

    # Run ctw against rt.jar on hosted
    ctw_flags = [
        '-DCompileTheWorld.Config=Inline=false CompilationFailureAction=ExitVM CompilationBailoutAsFailure=false', '-esa', '-XX:-UseJVMCICompiler', '-XX:+EnableJVMCI',
        '-DCompileTheWorld.MultiThreaded=true', '-Dgraal.InlineDuringParsing=false', '-Dgraal.TrackNodeSourcePosition=true',
        '-DCompileTheWorld.Verbose=false', '-XX:ReservedCodeCacheSize=300m',
    ]
    with Task('CTW:hosted', tasks, tags=GraalTags.ctw) as t:
        if t:
            ctw(ctw_flags, _remove_empty_entries(extraVMarguments))

    # Also run ctw with economy mode as a separate task, to be able to filter it with tags
    with Task('CTWEconomy:hosted', tasks, tags=GraalTags.ctweconomy) as t:
        if t:
            ctw(ctw_flags + _graalEconomyFlags, _remove_empty_entries(extraVMarguments))

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    with Task('Javadoc', tasks, tags=GraalTags.doc) as t:
        if jdk.javaCompliance >= '11':
            # GR-34816
            pass
        else:
            # metadata package was deprecated, exclude it
            if t: mx.javadoc(['--exclude-packages', 'com.oracle.truffle.dsl.processor.java'], quietForNoPackages=True)

def compiler_gate_benchmark_runner(tasks, extraVMarguments=None, prefix=''):
    # run DaCapo benchmarks #
    #########################

    # DaCapo benchmarks that can run with system assertions enabled but
    # java.util.Logging assertions disabled because the the DaCapo harness
    # misuses the API. The same harness is used by Scala DaCapo.
    dacapo_esa = ['-esa', '-da:java.util.logging...']

    # A few iterations to increase the chance of catching compilation errors
    default_iterations = 2

    bmSuiteArgs = ["--jvm", "server"]
    benchVmArgs = bmSuiteArgs + _remove_empty_entries(extraVMarguments)

    dacapo_suite = mx_graal_benchmark.DaCapoBenchmarkSuite()
    dacapo_gate_iterations = {
        k: default_iterations for k, v in dacapo_suite.daCapoIterations().items() if v > 0
    }
    dacapo_gate_iterations.update({'fop': 8})
    mx.warn("Disabling gate for dacapo:tradesoap (GR-33605)")
    dacapo_gate_iterations.update({'tradesoap': -1})
    for name in dacapo_suite.benchmarkList(bmSuiteArgs):
        iterations = dacapo_gate_iterations.get(name, -1)
        with Task(prefix + 'DaCapo:' + name, tasks, tags=GraalTags.benchmarktest) as t:
            if t: _gate_dacapo(name, iterations, benchVmArgs + ['-Dgraal.TrackNodeSourcePosition=true'] + dacapo_esa)

    # run Scala DaCapo benchmarks #
    ###############################
    scala_dacapo_suite = mx_graal_benchmark.ScalaDaCapoBenchmarkSuite()
    scala_dacapo_gate_iterations = {
        k: default_iterations for k, v in scala_dacapo_suite.daCapoIterations().items() if v > 0
    }
    for name in scala_dacapo_suite.benchmarkList(bmSuiteArgs):
        iterations = scala_dacapo_gate_iterations.get(name, -1)
        with Task(prefix + 'ScalaDaCapo:' + name, tasks, tags=GraalTags.benchmarktest) as t:
            if t: _gate_scala_dacapo(name, iterations, benchVmArgs + ['-Dgraal.TrackNodeSourcePosition=true'] + dacapo_esa)

    # run benchmark with non default setup #
    ########################################
    # ensure -Xbatch still works
    with Task(prefix + 'DaCapo_pmd:BatchMode', tasks, tags=GraalTags.test) as t:
        if t: _gate_dacapo('pmd', 1, benchVmArgs + ['-Xbatch'])

    # ensure benchmark counters still work but omit this test on
    # fastdebug as benchmark counter threads may not produce
    # output in a timely manner
    out = mx.OutputCapture()
    mx.run([jdk.java, '-version'], err=subprocess.STDOUT, out=out)
    if 'fastdebug' not in out.data:
        with Task(prefix + 'DaCapo_pmd:BenchmarkCounters', tasks, tags=GraalTags.test) as t:
            if t:
                fd, logFile = tempfile.mkstemp()
                os.close(fd) # Don't leak file descriptors
                try:
                    _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Dgraal.LogFile=' + logFile, '-Dgraal.LIRProfileMoves=true', '-Dgraal.GenericDynamicCounters=true', '-Dgraal.TimedDynamicCounters=1000', '-XX:JVMCICounterSize=10'])
                    with open(logFile) as fp:
                        haystack = fp.read()
                        needle = 'MoveOperations (dynamic counters)'
                        if needle not in haystack:
                            mx.abort('Expected to see "' + needle + '" in output of length ' + str(len(haystack)) + ':\n' + haystack)
                except BaseException:
                    with open(logFile) as fp:
                        haystack = fp.read()
                    if haystack:
                        mx.log(haystack)
                    raise
                finally:
                    os.remove(logFile)

    # ensure -Xcomp still works
    with Task(prefix + 'XCompMode:product', tasks, tags=GraalTags.test) as t:
        if t: run_vm(_remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xcomp', '-version'])

    # ensure -XX:+PreserveFramePointer  still works
    with Task(prefix + 'DaCapo_pmd:PreserveFramePointer', tasks, tags=GraalTags.test) as t:
        if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+PreserveFramePointer'], threads=4, force_serial_gc=False, set_start_heap_size=False)

    if isJDK8:
        # temporarily isolate those test (GR-10990)
        cms = ['cms']
        # ensure CMS still works
        with Task(prefix + 'DaCapo_pmd:CMS', tasks, tags=cms) as t:
            if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+UseConcMarkSweepGC'], threads=4, force_serial_gc=False, set_start_heap_size=False)

        # ensure CMSIncrementalMode still works
        with Task(prefix + 'DaCapo_pmd:CMSIncrementalMode', tasks, tags=cms) as t:
            if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+UseConcMarkSweepGC', '-XX:+CMSIncrementalMode'], threads=4, force_serial_gc=False, set_start_heap_size=False)


        if prefix != '':
            # ensure G1 still works with libgraal
            with Task(prefix + 'DaCapo_pmd:G1', tasks, tags=cms) as t:
                if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+UseG1GC'], threads=4, force_serial_gc=False, set_start_heap_size=False)



graal_unit_test_runs = [
    UnitTestRun('UnitTests', [], tags=GraalTags.unittest + GraalTags.coverage),
]

_registers = {
    'amd64': 'rbx,r11,r10,r14,xmm3,xmm11,xmm14',
    'aarch64': 'r0,r1,r2,r3,r4,v0,v1,v2,v3'
}
if mx.get_arch() not in _registers:
    mx.warn('No registers for register pressure tests are defined for architecture ' + mx.get_arch())

_defaultFlags = ['-Dgraal.CompilationWatchDogStartDelay=60.0D']
_assertionFlags = ['-esa', '-Dgraal.DetailedAsserts=true']
_graalErrorFlags = _compiler_error_options()
_graalEconomyFlags = ['-Dgraal.CompilerConfiguration=economy']
_verificationFlags = ['-Dgraal.VerifyGraalGraphs=true', '-Dgraal.VerifyGraalGraphEdges=true', '-Dgraal.VerifyGraalPhasesSize=true', '-Dgraal.VerifyPhases=true']
_coopFlags = ['-XX:-UseCompressedOops']
_gcVerificationFlags = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC']
_g1VerificationFlags = ['-XX:-UseSerialGC', '-XX:+UseG1GC']
_exceptionFlags = ['-Dgraal.StressInvokeWithExceptionNode=true']
_registerPressureFlags = ['-Dgraal.RegisterPressure=' + _registers[mx.get_arch()]]

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertionsFullVerify', _defaultFlags + _assertionFlags + _verificationFlags + _graalErrorFlags, tags=GraalTags.bootstrapfullverify),
    BootstrapTest('BootstrapWithSystemAssertions', _defaultFlags + _assertionFlags + _graalErrorFlags, tags=GraalTags.bootstraplite),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', _defaultFlags + _assertionFlags + _coopFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithGCVerification', _defaultFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', _defaultFlags + _g1VerificationFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithSystemAssertionsEconomy', _defaultFlags + _assertionFlags + _graalEconomyFlags + _graalErrorFlags, tags=GraalTags.bootstrapeconomy),
    BootstrapTest('BootstrapWithSystemAssertionsExceptionEdges', _defaultFlags + _assertionFlags + _exceptionFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithSystemAssertionsRegisterPressure', _defaultFlags + _assertionFlags + _registerPressureFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
]

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['compiler', 'truffle'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument, args.extra_unittest_argument)
    compiler_gate_benchmark_runner(tasks, args.extra_vm_argument)

class ShellEscapedStringAction(argparse.Action):
    """Turns a shell-escaped string into a list of arguments.
       Note that it appends the result to the destination.
    """
    def __init__(self, option_strings, nargs=None, **kwargs):
        if nargs is not None:
            raise ValueError("nargs not allowed")
        super(ShellEscapedStringAction, self).__init__(option_strings, **kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        # do not override existing values
        old_values = getattr(namespace, self.dest)
        setattr(namespace, self.dest, (old_values if old_values else []) + shlex.split(values))

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action=ShellEscapedStringAction, help='add extra vm arguments to gate tasks if applicable')
mx_gate.add_gate_argument('--extra-unittest-argument', action=ShellEscapedStringAction, help='add extra unit test arguments to gate tasks if applicable')

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    run_vm(vmArgs + [mainClass] + mainClassArgs)

def _remove_redundant_entries(cp):
    """
    Removes entries from the class path `cp` that are in Graal or on the boot class path.
    """

    # Remove all duplicates in cp and convert it to a list of entries
    seen = set()
    cp = [e for e in cp.split(os.pathsep) if e not in seen and seen.add(e) is None]

    if isJDK8:
        # Remove entries from class path that are in Graal or on the boot class path
        redundantClasspathEntries = set()
        for dist in _graal_config().dists:
            redundantClasspathEntries.update((d.output_dir() for d in dist.archived_deps() if d.isJavaProject()))
            redundantClasspathEntries.add(dist.path)
    else:
        redundantClasspathEntries = set()
        for dist in _graal_config().dists:
            redundantClasspathEntries.update(mx.classpath(dist, preferProjects=False, jdk=jdk).split(os.pathsep))
            redundantClasspathEntries.update(mx.classpath(dist, preferProjects=True, jdk=jdk).split(os.pathsep))
            if hasattr(dist, 'overlaps'):
                for o in dist.overlaps:
                    od = mx.distribution(o, fatalIfMissing=False)
                    if od:
                        path = od.classpath_repr()
                        if path:
                            redundantClasspathEntries.add(path)
    return os.pathsep.join([e for e in cp if e not in redundantClasspathEntries])

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    cpIndex, cp = mx.find_classpath_arg(vmArgs)
    if cp:
        cp = _remove_redundant_entries(cp)
        vmArgs[cpIndex] = cp
        if not isJDK8:
            # JVMCI is dynamically exported to Graal when JVMCI is initialized. This is too late
            # for the junit harness which uses reflection to find @Test methods. In addition, the
            # tests widely use JVMCI classes so JVMCI needs to also export all its packages to
            # ALL-UNNAMED.
            mainClassArgs.extend(['-JUnitOpenPackages', 'jdk.internal.vm.ci/*=jdk.internal.vm.compiler,ALL-UNNAMED'])

            limited_modules = None
            for arg in vmArgs:
                if arg.startswith('--limit-modules'):
                    assert arg.startswith('--limit-modules='), ('--limit-modules must be separated from its value by "="')
                    limited_modules = arg[len('--limit-modules='):].split(',')

            # Export packages in all Graal modules and their dependencies
            for dist in _graal_config().dists:
                jmd = as_java_module(dist, jdk)
                if limited_modules is None or jmd.name in limited_modules:
                    mainClassArgs.extend(['-JUnitOpenPackages', jmd.name + '/*'])
                    vmArgs.append('--add-modules=' + jmd.name)

    vmArgs.append('-Dgraal.TrackNodeSourcePosition=true')
    vmArgs.append('-esa')

    if isJDK8:
        # Run the VM in a mode where application/test classes can
        # access JVMCI loaded classes.
        vmArgs.append('-XX:-UseJVMCIClassLoader')

    # Always run unit tests without UseJVMCICompiler unless explicitly requested
    if _get_XX_option_value(vmArgs, 'UseJVMCICompiler', None) is None:
        vmArgs.append('-XX:-UseJVMCICompiler')

    # The type-profile width 8 is the default when using the JVMCI compiler.
    # This value must be forced, because we do not used the JVMCI compiler
    # in the unit tests by default.
    if _get_XX_option_value(vmArgs, 'TypeProfileWidth', None) is None:
        vmArgs.append('-XX:TypeProfileWidth=8')

    # TODO: GR-31197, this should be removed.
    vmArgs.append('-Dpolyglot.engine.DynamicCompilationThresholds=false')
    vmArgs.append('-Dpolyglot.engine.AllowExperimentalOptions=true')

    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)
mx_unittest.set_vm_launcher('JDK VM launcher', _unittest_vm_launcher, jdk)

def _record_last_updated_jar(dist, path):
    last_updated_jar = join(dist.suite.get_output_root(), dist.name + '.lastUpdatedJar')
    with open(last_updated_jar, 'w') as fp:
        java_home = mx.get_env('JAVA_HOME', '')
        extra_java_homes = mx.get_env('EXTRA_JAVA_HOMES', '')
        fp.write(path + '|' + java_home + '|' + extra_java_homes)

def _get_last_updated_jar(dist):
    last_updated_jar = join(dist.suite.get_output_root(), dist.name + '.lastUpdatedJar')
    if exists(last_updated_jar):
        try:
            with open(last_updated_jar) as fp:
                return fp.read().split('|')
        except BaseException as e:
            mx.warn('Error reading {}: {}'.format(last_updated_jar, e))
    return None, None, None

def _check_using_latest_jars(dists):
    for dist in dists:
        last_updated_jar, java_home, extra_java_homes = _get_last_updated_jar(dist)
        if last_updated_jar:
            current_jar = dist.original_path()
            if last_updated_jar != current_jar:
                mx.warn('The most recently updated jar for {} ({}) differs from the jar used to construct the VM class or module path ({}). '.format(dist, last_updated_jar, current_jar) +
                        'This usually means the current values of JAVA_HOME and EXTRA_JAVA_HOMES are '
                        'different from the values when {} was last built by `mx build` '.format(dist) +
                        'or an IDE. As a result, you may be running with out-of-date code.\n' +
                        'Current JDKs:\n  JAVA_HOME={}\n  EXTRA_JAVA_HOMES={}\n'.format(mx.get_env('JAVA_HOME', ''), mx.get_env('EXTRA_JAVA_HOMES', '')) +
                        'Build time JDKs:\n  JAVA_HOME={}\n  EXTRA_JAVA_HOMES={}'.format(java_home, extra_java_homes))

def _parseVmArgs(args, addDefaultArgs=True):
    args = mx.expand_project_in_args(args, insitu=False)

    # add default graal.options.file
    argsPrefix = []
    options_file = join(mx.primary_suite().dir, 'graal.options')
    if exists(options_file):
        argsPrefix.append('-Dgraal.options.file=' + options_file)

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the VM because they come after the '-version' argument: " + ' '.join(ignoredArgs))

    args = jdk.processArgs(args, addDefaultArgs=addDefaultArgs)

    # The default for CompilationFailureAction in the code is Silent as this is
    # what we want for GraalVM. When using Graal via mx (e.g. in the CI gates)
    # Diagnose is a more useful "default" value.
    argsPrefix.extend(_compiler_error_options('Diagnose', args))

    # It is safe to assume that Network dumping is the desired default when using mx.
    # Mx is never used in production environments.
    if not any(a.startswith('-Dgraal.PrintGraph=') for a in args):
        argsPrefix.append('-Dgraal.PrintGraph=Network')

    # Likewise, one can assume that objdump is safe to access when using mx.
    if not any(a.startswith('-Dgraal.ObjdumpExecutables=') for a in args):
        argsPrefix.append('-Dgraal.ObjdumpExecutables=objdump,gobjdump')

    # The GraalVM locator must be disabled so that Truffle languages
    # are loaded from the class path. This is the configuration expected
    # by the unit tests and benchmarks run via the compiler suite.
    if not any(a.startswith('-Dgraalvm.locatorDisabled=') for a in args):
        argsPrefix.append('-Dgraalvm.locatorDisabled=true')

    # On JDK8 running a GraalJDK requires putting Truffle and all components
    # that have boot jars on the boot class path.
    if isJDK8:
        new_args = []
        bcpa = []
        # Filter out all instances of -Xbootclasspath/a: from args, keeping
        # the last, if any.
        for a in args:
            if a.startswith('-Xbootclasspath/a:'):
                bcpa = a[len('-Xbootclasspath/a:'):].split(os.pathsep)
            else:
                new_args.append(a)
        gc = _graal_config()
        bcpa = [bj for bj in gc.boot_jars if basename(bj) != 'graal-sdk.jar'] + gc.truffle_jars + bcpa
        argsPrefix.append('-Xbootclasspath/a:' + os.pathsep.join(bcpa))
        args = new_args

    return argsPrefix + args

def _check_bootstrap_config(args):
    """
    Issues a warning if `args` denote -XX:+BootstrapJVMCI but -XX:-UseJVMCICompiler.
    """
    bootstrap = False
    useJVMCICompiler = False
    for arg in args:
        if arg == '-XX:+BootstrapJVMCI':
            bootstrap = True
        elif arg == '-XX:+UseJVMCICompiler':
            useJVMCICompiler = True
    if bootstrap and not useJVMCICompiler:
        mx.warn('-XX:+BootstrapJVMCI is ignored since -XX:+UseJVMCICompiler is not enabled')

class StdoutUnstripping:
    """
    A context manager for logging and unstripping the console output for a subprocess
    execution. The logging and unstripping is only attempted if stdout and stderr
    for the execution were not already being redirected and existing *.map files
    were detected in the arguments to the execution.
    """
    def __init__(self, args, out, err, mapFiles=None):
        self.args = args
        self.out = out
        self.err = err
        self.capture = None
        if mapFiles is not None:
            mapFiles = [m for m in mapFiles if exists(m)]
        self.mapFiles = mapFiles

    def __enter__(self):
        if mx.get_opts().strip_jars and self.out is None and (self.err is None or self.err == subprocess.STDOUT):
            delims = re.compile('[' + os.pathsep + '=]')
            for a in self.args:
                for e in delims.split(a):
                    candidate = e + '.map'
                    if exists(candidate):
                        if self.mapFiles is None:
                            self.mapFiles = []
                        self.mapFiles.append(candidate)
            self.capture = mx.OutputCapture()
            self.out = mx.TeeOutputCapture(self.capture)
            self.err = self.out
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        if self.mapFiles and self.capture and len(self.capture.data):
            data = self.capture.data
            tmp_fd, tmp_file = tempfile.mkstemp(suffix='.txt', prefix='unstrip')
            os.close(tmp_fd) # Don't leak file descriptors
            try:
                with open(tmp_file, 'w') as fp:
                    fp.write(data)
                retraceOut = mx.OutputCapture()
                unstrip_args = list(set(self.mapFiles)) + [tmp_file]
                mx.unstrip(unstrip_args, out=retraceOut)
                retraceOut = retraceOut.data
                if data != retraceOut and mx.is_windows():
                    # On Windows, ReTrace might duplicate line endings
                    dedupOut = retraceOut.replace(os.linesep + os.linesep, os.linesep)
                    if data == dedupOut:
                        retraceOut = dedupOut
                if data != retraceOut:
                    mx.log('>>>> BEGIN UNSTRIPPED OUTPUT')
                    mx.log(retraceOut)
                    mx.log('<<<< END UNSTRIPPED OUTPUT')
            except BaseException as e:
                mx.log('Error unstripping output from VM execution with stripped jars: ' + str(e))
            finally:
                os.remove(tmp_file)

def _graaljdk_dist(edition=None):
    """
    Gets the GraalJDK distribution specified by `edition`.
    A GraalJDK is a fixed GraalVM configuration specified by the `cmp_ce_components` field.

    :param str edition: 'ce', 'ee' or None. If None, then an EE GraalJDK is returned if available otherwise a CE GraalJDK.
    """
    candidates = [d for d in mx.sorted_dists() if isinstance(d, mx_sdk_vm_impl.GraalVmLayoutDistribution)]
    if edition is None:
        graaljdks = [d for d in candidates if d.base_name == 'GraalJDK_EE']
        if graaljdks:
            base_name = 'GraalJDK_EE'
        else:
            graaljdks = [d for d in candidates if d.base_name == 'GraalJDK_CE']
            if graaljdks:
                base_name = 'GraalJDK_CE'
            else:
                mx.abort("Cannot find any GraalJDK images")
    else:
        assert edition in ('ce', 'ee'), edition
        base_name = 'GraalJDK_{}'.format(edition.upper())
        graaljdks = [d for d in candidates if d.base_name == base_name]
    if not graaljdks:
        raise mx.abort("Cannot find GraalJDK image with base name '{}'".format(base_name))
    if len(graaljdks) > 1:
        raise mx.abort("Found multiple GraalJDKs with the same base name '{}'".format(base_name))
    return graaljdks[0]

def _graaljdk_home(edition=None):
    """
    Gets the JAVA_HOME directory for the GraalJDK distribution (see _graaljdk_dist above).
    """
    graaljdk_dist = _graaljdk_dist(edition)
    return join(graaljdk_dist.output, graaljdk_dist.jdk_base)

def get_graaljdk(edition=None):
    graaljdk_dir = _graaljdk_home(edition)
    if not exists(graaljdk_dir):
        mx.abort('{} does not exist - forgot to run `mx build`?'.format(graaljdk_dir))
    return mx.JDKConfig(graaljdk_dir)

def collate_metrics(args):
    """
    collates files created by the AggregatedMetricsFile option for one or more executions

    The collated results file will have rows of the format:

    <name>;<value1>;<value2>;...;<valueN>;<unit>

    where <value1> is from the first <filename>, <value2> is from the second
    <filename> etc. 0 is inserted for missing values.

    """
    parser = ArgumentParser(prog='mx collate-metrics')
    parser.add_argument('filenames', help='per-execution values passed to AggregatedMetricsFile',
                        nargs=REMAINDER, metavar='<path>')
    args = parser.parse_args(args)

    results = {}
    units = {}

    filename_index = 0
    for filename in args.filenames:
        if not filename.endswith('.csv'):
            mx.abort('Cannot collate metrics from non-CSV files: ' + filename)

        # Keep in sync with org.graalvm.compiler.debug.GlobalMetrics.print(OptionValues)
        abs_filename = join(os.getcwd(), filename)
        directory = dirname(abs_filename)
        rootname = basename(filename)[0:-len('.csv')]
        isolate_metrics_re = re.compile(rootname + r'@\d+\.csv')
        for entry in os.listdir(directory):
            m = isolate_metrics_re.match(entry)
            if m:
                isolate_metrics = join(directory, entry)
                with open(isolate_metrics) as fp:
                    line_no = 1
                    for line in fp.readlines():
                        values = line.strip().split(';')
                        if len(values) != 3:
                            mx.abort('{}:{}: invalid line: {}'.format(isolate_metrics, line_no, line))
                        if len(values) != 3:
                            mx.abort('{}:{}: expected 3 semicolon separated values: {}'.format(isolate_metrics, line_no, line))
                        name, metric, unit = values

                        series = results.get(name, None)
                        if series is None:
                            series = [0 for _ in range(filename_index)] + [int(metric)]
                            results[name] = series
                        else:
                            while len(series) < filename_index + 1:
                                series.append(0)
                            assert len(series) == filename_index + 1, '{}, {}'.format(name, series)
                            series[filename_index] += int(metric)
                        if units.get(name, unit) != unit:
                            mx.abort('{}:{}: inconsistent units for {}: {} != {}'.format(isolate_metrics, line_no, name, unit, units.get(name)))
                        units[name] = unit
        filename_index += 1

    if args.filenames:
        collated_filename = args.filenames[0][:-len('.csv')] + '.collated.csv'
        with open(collated_filename, 'w') as fp:
            for n, series in sorted(results.items()):
                while len(series) < len(args.filenames):
                    series.append(0)
                print(n +';' + ';'.join((str(v) for v in series)) + ';' + units[n], file=fp)
        mx.log('Collated metrics into ' + collated_filename)

def run_java(args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True, command_mapper_hooks=None):
    graaljdk = get_graaljdk()
    vm_args = _parseVmArgs(args, addDefaultArgs=addDefaultArgs)
    args = ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'] + vm_args
    _check_bootstrap_config(args)
    cmd = get_vm_prefix() + [graaljdk.java] + ['-server'] + args
    map_file = join(graaljdk.home, 'proguard.map')

    with StdoutUnstripping(args, out, err, mapFiles=[map_file]) as u:
        try:
            cmd = mx.apply_command_mapper_hooks(cmd, command_mapper_hooks)
            return mx.run(cmd, nonZeroIsFatal=nonZeroIsFatal, out=u.out, err=u.err, timeout=timeout, cwd=cwd, env=env)
        finally:
            # Collate AggratedMetricsFile
            for a in vm_args:
                if a.startswith('-Dgraal.AggregatedMetricsFile='):
                    metrics_file = a[len('-Dgraal.AggregatedMetricsFile='):]
                    if metrics_file:
                        collate_metrics([metrics_file])

_JVMCI_JDK_TAG = 'jvmci'

class GraalJVMCIJDKConfig(mx.JDKConfig):
    """
    A JDKConfig that configures Graal as the JVMCI compiler.
    """
    def __init__(self):
        mx.JDKConfig.__init__(self, jdk.home, tag=_JVMCI_JDK_TAG)

    def run_java(self, args, **kwArgs):
        return run_java(args, **kwArgs)

    @property
    def home(self):
        return get_graaljdk().home

    @home.setter
    def home(self, home):
        jdk.home = home # forward setting to the backing jdk

class GraalJDKFactory(mx.JDKFactory):
    def getJDKConfig(self):
        return GraalJVMCIJDKConfig()

    def description(self):
        return "JVMCI JDK with Graal"

def run_vm(args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK"""
    return run_java(args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

def run_vm_with_jvmci_compiler(args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK,
    with the JVMCI compiler selected by default"""
    jvmci_args = ['-XX:+UseJVMCICompiler'] + args
    return run_vm(jvmci_args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout, debugLevel=debugLevel, vmbuild=vmbuild)

def _check_latest_jvmci_version():
    """
    If `_jdk_jvmci_version` is not None, checks that it is the same as
    the JVMCI version of the JVMCI JDKs in the "jdks" section of the
    ``common.json`` file and issues a warning if not.
    """
    jvmci_re = re.compile(r'.*-jvmci-(\d+)\.(\d+)-b(\d+)')
    common_path = join(_suite.dir, '..', 'common.json')

    if _jdk_jvmci_version is None:
        # Not using a JVMCI JDK
        return

    def get_latest_jvmci_version():
        with open(common_path) as common_file:
            common_cfg = json.load(common_file)

        latest = None
        for distribution in common_cfg['jdks']:
            version = common_cfg['jdks'][distribution].get('version', None)
            if version and '-jvmci-' in version:
                current = tuple(int(n) for n in jvmci_re.match(version).group(1, 2, 3))
                if latest is None:
                    latest = current
                elif latest != current:
                    # All JVMCI JDKs in common.json are expected to have the same JVMCI version.
                    # If they don't then the repo is in some transitionary state
                    # (e.g. making a JVMCI release) so skip the check.
                    return None
        return latest

    def jvmci_version_str(version):
        major, minor, build = version
        return 'jvmci-{}.{}-b{:02d}'.format(major, minor, build)

    latest = get_latest_jvmci_version()
    if latest is not None and _jdk_jvmci_version < latest:
        common_path = os.path.normpath(common_path)
        msg = 'JVMCI version of JAVA_HOME is older than in {}: {} < {} '.format(
            common_path,
            jvmci_version_str(_jdk_jvmci_version),
            jvmci_version_str(latest))
        msg += os.linesep + 'This poses the risk of hitting JVMCI bugs that have already been fixed.'
        msg += os.linesep + 'Consider using {}, which you can get via:'.format(jvmci_version_str(latest))
        msg += os.linesep + 'mx fetch-jdk --configuration {}'.format(common_path)
        mx.warn(msg)

class GraalArchiveParticipant:
    providersRE = re.compile(r'(?:META-INF/versions/([1-9][0-9]*)/)?META-INF/providers/(.+)')

    def __init__(self, dist, isTest=False):
        self.dist = dist
        self.isTest = isTest

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __process__(self, arcname, contents_supplier, is_source):
        if is_source:
            return False
        def add_serviceprovider(service, provider, version):
            if version is None:
                # Non-versioned service
                self.services.setdefault(service, []).append(provider)
            else:
                # Versioned service
                services = self.services.setdefault(int(version), {})
                services.setdefault(service, []).append(provider)

        m = GraalArchiveParticipant.providersRE.match(arcname)
        if m:
            if self.isTest:
                # The test distributions must not have their @ServiceProvider
                # generated providers converted to real services otherwise
                # bad things can happen such as InvocationPlugins being registered twice.
                pass
            else:
                provider = m.group(2)
                for service in _decode(contents_supplier()).strip().split(os.linesep):
                    assert service
                    version = m.group(1)
                    add_serviceprovider(service, provider, version)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            if self.isTest:
                mx.warn('@Option defined in test code will be ignored: ' + arcname)
            else:
                # Need to create service files for the providers of the
                # jdk.internal.vm.ci.options.Options service created by
                # jdk.internal.vm.ci.options.processor.OptionProcessor.
                version_prefix = 'META-INF/versions/'
                if arcname.startswith(version_prefix):
                    # If OptionDescriptor is version-specific, get version
                    # from arcname and adjust arcname to non-version form
                    version, _, arcname = arcname[len(version_prefix):].partition('/')
                else:
                    version = None
                provider = arcname[:-len('.class'):].replace('/', '.')
                service = 'org.graalvm.compiler.options.OptionDescriptors'
                add_serviceprovider(service, provider, version)
        return False

    def __closing__(self):
        _record_last_updated_jar(self.dist, self.arc.path)
        if self.dist.name == 'GRAAL':
            # Check if we're using the same JVMCI JDK as the CI system does.
            # This only done when building the GRAAL distribution so as to
            # not be too intrusive.
            _check_latest_jvmci_version()

mx.add_argument('--vmprefix', action='store', dest='vm_prefix', help='prefix for running the VM (e.g. "gdb --args")', metavar='<prefix>')
mx.add_argument('--gdb', action='store_const', const='gdb --args', dest='vm_prefix', help='alias for --vmprefix "gdb --args"')
mx.add_argument('--lldb', action='store_const', const='lldb --', dest='vm_prefix', help='alias for --vmprefix "lldb --"')

def sl(args):
    """run an SL program"""
    mx.get_opts().jdk = 'jvmci'
    mx_truffle.sl(args)

def java_base_unittest(args):
    """tests whether the Graal compiler runs on a JDK with a minimal set of modules"""

    # Remove GRAAL_MANAGEMENT from the module path as it
    # depends on the java.management module which is not in
    # the limited module set
    base_modules = ['java.base', 'java.logging', 'jdk.internal.vm.ci', 'jdk.unsupported', 'jdk.compiler']
    compiler_modules = [as_java_module(d, jdk).name for d in _graal_config().dists if d.name != 'GRAAL_MANAGEMENT']
    root_module_names = base_modules + compiler_modules
    extra_args = ['--limit-modules=' + ','.join(root_module_names)]

    if mx_gate.Task.verbose:
        extra_args.extend(['--verbose', '--enable-timing'])
    # the base JDK doesn't include jdwp
    if get_graaljdk().debug_args:
        mx.warn('Ignoring Java debugger arguments because base JDK doesn\'t include jdwp')
    with mx.DisableJavaDebugging():
        mx_unittest.unittest(['--suite', 'compiler', '--fail-fast'] + extra_args + args)

def javadoc(args):
    # metadata package was deprecated, exclude it
    if not '--exclude-packages' in args:
        args.append('--exclude-packages')
        args.append('com.oracle.truffle.api.metadata')
    mx.javadoc(args, quietForNoPackages=True)

def create_archive(srcdir, arcpath, prefix):
    """
    Creates a compressed archive of a given directory.

    :param str srcdir: directory to archive
    :param str arcpath: path of file to contain the archive. The extension of `path`
           specifies the type of archive to create
    :param str prefix: the prefix to apply to each entry in the archive
    """

    def _taradd(arc, filename, arcname):
        arc.add(name=f, arcname=arcname, recursive=False)
    def _zipadd(arc, filename, arcname):
        arc.write(filename, arcname)

    if arcpath.endswith('.zip'):
        arc = zipfile.ZipFile(arcpath, 'w', zipfile.ZIP_DEFLATED)
        add = _zipadd
    elif arcpath.endswith('.tar'):
        arc = tarfile.open(arcpath, 'w')
        add = _taradd
    elif arcpath.endswith('.tgz') or arcpath.endswith('.tar.gz'):
        arc = tarfile.open(arcpath, 'w:gz')
        add = _taradd
    else:
        mx.abort('unsupported archive kind: ' + arcpath)

    for root, _, filenames in os.walk(srcdir):
        for name in filenames:
            f = join(root, name)
            # Make sure files in the image are readable by everyone
            file_mode = os.stat(f).st_mode
            mode = stat.S_IRGRP | stat.S_IROTH | file_mode
            if isdir(f) or (file_mode & stat.S_IXUSR):
                mode = mode | stat.S_IXGRP | stat.S_IXOTH
            os.chmod(f, mode)
            arcname = prefix + os.path.relpath(f, srcdir)
            add(arc, f, arcname)
    arc.close()


def makegraaljdk_cli(args):
    """make a JDK with Graal as the default top level JIT"""
    mx.abort('The makegraaljdk command is no longer supported. Use the graaljdk-home command instead.')

__graal_config = None

def _graal_config():
    global __graal_config

    class GraalConfig:
        """
        The distributions and jars that together comprise the set of classes implementing the Graal compiler.
        """
        def __init__(self):
            self.jvmci_dists = []
            self.jvmci_jars = []
            self.jvmci_parent_dists = []
            self.jvmci_parent_jars = []
            self.boot_dists = []
            self.boot_jars = []
            self.truffle_jars = []
            self.jars = []

            for component in mx_sdk_vm.graalvm_components():
                if isinstance(component, mx_sdk_vm.GraalVmJvmciComponent):
                    for jar in component.jvmci_jars:
                        d = mx.distribution(jar)
                        self.jvmci_dists.append(d)
                        self.jvmci_jars.append(d.classpath_repr())
                for jar in component.boot_jars:
                    d = mx.distribution(jar)
                    self.boot_dists.append(d)
                    self.boot_jars.append(d.classpath_repr())

            self.jvmci_parent_dists = [mx.distribution('truffle:TRUFFLE_API')]
            self.jvmci_parent_jars = [jar.classpath_repr() for jar in self.jvmci_parent_dists]

            self.truffle_dists = [mx.distribution('truffle:TRUFFLE_API')] if isJDK8 else []
            self.truffle_jars = [jar.classpath_repr() for jar in self.truffle_dists]

            self.dists = self.jvmci_dists + self.jvmci_parent_dists + self.boot_dists
            self.jars = self.jvmci_jars + self.jvmci_parent_jars + self.boot_jars

            self.dists_dict = {e.suite.name + ':' + e.name : e for e in self.dists}

    if __graal_config is None:
        __graal_config = GraalConfig()
    return __graal_config

def _jvmci_jars():
    return [
        'compiler:GRAAL',
        'compiler:GRAAL_MANAGEMENT',
        'compiler:GRAAL_TRUFFLE_JFR_IMPL',
    ]

# The community compiler component
cmp_ce_components = [
    mx_sdk_vm.GraalVmJvmciComponent(
        suite=_suite,
        name='GraalVM compiler',
        short_name='cmp',
        dir_name='graal',
        license_files=[],
        third_party_license_files=[],
        dependencies=['Truffle'],
        jar_distributions=[  # Dev jars (annotation processors)
            'compiler:GRAAL_PROCESSOR',
        ],
        jvmci_jars=_jvmci_jars(),
        graal_compiler='graal',
        stability="supported",
    ),
    mx_sdk_vm.GraalVmComponent(
        suite=_suite,
        name='Disassembler',
        short_name='dis',
        dir_name='graal',
        license_files=[],
        third_party_license_files=[],
        support_libraries_distributions=['compiler:HSDIS_GRAALVM_SUPPORT'],
    )
]

for cmp_ce_component in cmp_ce_components:
    mx_sdk_vm.register_graalvm_component(cmp_ce_component)

def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    graal_jdk_dist = mx_sdk_vm_impl.GraalVmLayoutDistribution(base_name="GraalJDK_CE", components=cmp_ce_components)
    graal_jdk_dist.description = "GraalJDK CE distribution"
    graal_jdk_dist.maven = {'groupId': 'org.graalvm', 'tag': 'graaljdk'}
    register_distribution(graal_jdk_dist)


def _parse_graaljdk_edition(description, args):
    parser = ArgumentParser(description=description)
    parser.add_argument('--edition', choices=['ce', 'ee'], default=None, help='GraalJDK CE or EE')
    return parser.parse_args(args).edition

def print_graaljdk_home(args):
    """print the GraalJDK JAVA_HOME directory"""
    print(_graaljdk_home(_parse_graaljdk_edition('Print the GraalJDK JAVA_HOME directory', args)))

def print_graaljdk_config(args):
    """print the GraalJDK config"""
    mx_sdk_vm_impl.graalvm_show([], _graaljdk_dist(_parse_graaljdk_edition('Print the GraalJDK config', args)))

mx.update_commands(_suite, {
    'sl' : [sl, '[SL args|@VM options]'],
    'vm': [run_vm_with_jvmci_compiler, '[-options] class [args...]'],
    'collate-metrics': [collate_metrics, 'filename'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'nodecostdump' : [_nodeCostDump, ''],
    'java_base_unittest' : [java_base_unittest, 'Runs unittest on JDK java.base "only" module(s)'],
    'updategraalinopenjdk' : [updategraalinopenjdk, '[options]'],
    'renamegraalpackages' : [renamegraalpackages, '[options]'],
    'javadoc': [javadoc, ''],
    'makegraaljdk': [makegraaljdk_cli, '[options]'],
    'graaljdk-home': [print_graaljdk_home, '[options]'],
    'graaljdk-show': [print_graaljdk_config, '[options]'],
})

def mx_post_parse_cmd_line(opts):
    mx.addJDKFactory(_JVMCI_JDK_TAG, jdk.javaCompliance, GraalJDKFactory())
    mx.add_ide_envvar('JVMCI_VERSION_CHECK')
    for dist in _suite.dists:
        if hasattr(dist, 'set_archiveparticipant'):
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))
    global _vm_prefix
    _vm_prefix = opts.vm_prefix
