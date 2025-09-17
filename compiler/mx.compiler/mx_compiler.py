#
# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function
import os
from functools import total_ordering
from os.path import join, exists, basename, dirname, isdir
import argparse
from argparse import ArgumentParser, RawDescriptionHelpFormatter, REMAINDER
import re
import stat
import zipfile
import tarfile
import subprocess
import tempfile
import csv

import mx
import mx_truffle
import mx_sdk_vm
from mx_sdk_benchmark import JVMCI_JDK_TAG, DaCapoBenchmarkSuite, ScalaDaCapoBenchmarkSuite, RenaissanceBenchmarkSuite
import mx_graal_benchmark #pylint: disable=unused-import

import mx_gate
from mx_gate import Task

import mx_unittest
from mx_unittest import unittest, parse_split_args

from mx_javamodules import as_java_module
import mx_sdk_vm_impl

import mx_benchmark

import shlex
import json

import mx_graal_tools #pylint: disable=unused-import

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


class JavaLangRuntimeVersion(mx.Comparable):
    """Wrapper for java.lang.Runtime.Version"""

    _cmp_cache = {}
    _feature_re = re.compile('[1-9][0-9]*')

    def __init__(self, version, jdk=None):
        self.version = version
        self.jdk = jdk or mx.get_jdk()

    def __str__(self):
        return self.version

    def __cmp__(self, other):
        if not isinstance(other, JavaLangRuntimeVersion):
            raise TypeError(f'Cannot compare {JavaLangRuntimeVersion.__name__} to {type(other).__name__}')
        this_version = self.version
        other_version = other.version
        if this_version == other_version:
            return 0
        if self.feature() == 21 and other.feature() == 21:
            # JDK 21 uses the legacy version scheme where the jdkVersion is irrelevant (and imprecise).
            # Thus, we do not perform a full version check.
            return 0
        return JavaLangRuntimeVersion.compare(this_version, other_version, jdk)

    @staticmethod
    def compare(this_version, other_version, jdk):
        key = (this_version, other_version)
        cached = JavaLangRuntimeVersion._cmp_cache.get(key, None)
        if cached is not None:
            return cached
        source_path = join(_suite.dir, 'src', 'jdk.graal.compiler', 'src', 'jdk', 'graal', 'compiler',
                           'hotspot',
                           'JVMCIVersionCompare.java')
        out = mx.OutputCapture()
        mx.run([jdk.java, '-Xlog:disable', source_path, this_version, other_version], out=out)
        ret = int(out.data)
        JavaLangRuntimeVersion._cmp_cache[key] = ret
        return ret

    def feature(self):
        if not hasattr(self, '_feature'):
            self._feature = int(JavaLangRuntimeVersion._feature_re.match(self.version).group(0))
        return self._feature

@total_ordering
class JVMCIVersionCheckVersion(object):
    def __init__(self, jdk_version, jvmci_major, jvmci_minor, jvmci_build):
        """
        Python version of jdk.graal.compiler.hotspot.JVMCIVersionCheck.Version

        jdk_version is a JavaLangRuntimeVersion
        jvmci_major and jvmci_minor might be 0 if not needed (JDK 22+)
        """
        assert isinstance(jdk_version, JavaLangRuntimeVersion)
        assert isinstance(jvmci_major, int)
        assert isinstance(jvmci_minor, int)
        assert isinstance(jvmci_build, int)
        self.jdk_version = jdk_version
        self.jvmci_major = jvmci_major
        self.jvmci_minor = jvmci_minor
        self.jvmci_build = jvmci_build

    def _as_tuple(self):
        return (self.jdk_version, self.jvmci_major, self.jvmci_minor, self.jvmci_build)

    def __eq__(self, other):
        if not isinstance(other, JVMCIVersionCheckVersion):
            return False
        return self._as_tuple() == other._as_tuple()

    def __lt__(self, other):
        if not isinstance(other, JVMCIVersionCheckVersion):
            return NotImplemented
        return self._as_tuple() < other._as_tuple()

    def __str__(self):
        jdk_version, jvmci_major, jvmci_minor, jvmci_build = self._as_tuple()
        if jvmci_major == 0:
            if jvmci_build == 0:
                return f'(openjdk|oraclejdk)-{jdk_version}'
            else:
                return f'labsjdk-(ce|ee)-{jdk_version}-jvmci-b{jvmci_build:02d}'
        else:
            return f'labsjdk-(ce|ee)-{jdk_version}-jvmci-{jvmci_major}.{jvmci_minor}-b{jvmci_build:02d}'


_jdk_jvmci_version = None
_jdk_min_jvmci_version = None

if os.environ.get('JDK_VERSION_CHECK', None) != 'ignore' and jdk.javaCompliance < '25':
    mx.abort('Graal requires JDK 25 or later, got ' + str(jdk) +
             '. This check can be bypassed by setting env var JDK_VERSION_CHECK=ignore')

def _check_jvmci_version(jdk):
    """
    Runs a Java utility to check that `jdk` supports the minimum JVMCI API required by Graal.
    """
    def _capture_jvmci_version(args=None):
        out = mx.OutputCapture()
        _run_jvmci_version_check(args, jdk=jdk, out=out)
        if out.data:
            try:
                (jdk_version, jvmci_major, jvmci_minor, jvmci_build) = out.data.split(',')
                return JVMCIVersionCheckVersion(JavaLangRuntimeVersion(jdk_version), int(jvmci_major), int(jvmci_minor), int(jvmci_build))
            except ValueError:
                mx.warn(f'Could not parse jvmci version from JVMCIVersionCheck output:\n{out.data}')
            return None

    global _jdk_jvmci_version
    _jdk_jvmci_version = _capture_jvmci_version()
    global _jdk_min_jvmci_version
    _jdk_min_jvmci_version = _capture_jvmci_version(['--min-version'])



@mx.command(_suite.name, 'jvmci-version-check')
def _run_jvmci_version_check(args=None, jdk=jdk, **kwargs):
    source_path = join(_suite.dir, 'src', 'jdk.graal.compiler', 'src', 'jdk', 'graal', 'compiler', 'hotspot',
                       'JVMCIVersionCheck.java')
    return mx.run([jdk.java, '-Xlog:disable', source_path] + (args or []), **kwargs)


if os.environ.get('JVMCI_VERSION_CHECK', None) != 'ignore':
    _check_jvmci_version(jdk)

def _get_graal_option(vmargs, name, default=None, prefix='-Djdk.graal.'):
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

def _ctw_jvmci_export_args(arg_prefix='--'):
    """
    Gets the VM args needed to export JVMCI API and HotSpot internals required by CTW.
    """
    args = [
        'add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
        'add-exports=jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED',
        'add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED',
        'add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED',
        'add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED',
        'add-exports=jdk.graal.compiler/jdk.graal.compiler.hotspot=ALL-UNNAMED',
    ]
    return [arg_prefix + arg for arg in args]

def _ctw_system_properties_suffix():
    out = mx.OutputCapture()
    out.data = 'System properties for CTW:\n\n'
    args = ['-XX:+EnableJVMCI'] + _ctw_jvmci_export_args()
    cp = _remove_redundant_entries(mx.classpath('GRAAL_TEST', jdk=jdk))
    args.extend(['-cp', cp, '-DCompileTheWorld.Help=true', 'jdk.graal.compiler.hotspot.test.CompileTheWorld'])
    run_java(args, out=out, addDefaultArgs=False)
    return out.data

def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    parser = ArgumentParser(prog='mx ctw', formatter_class=RawDescriptionHelpFormatter, epilog=_ctw_system_properties_suffix())
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')
    parser.add_argument('--limitmods', action='store', help='limits the set of compiled classes to only those in the listed modules', metavar='<modulename>[,<modulename>...]')

    args, vmargs = parser.parse_known_args(args)
    vmargs.extend(_remove_empty_entries(extraVMarguments))

    vmargs.append('-XX:+EnableJVMCI')

    # Disable JVMCICompiler by default if not specified
    if _get_XX_option_value(vmargs, 'UseJVMCICompiler', None) is None:
        vmargs.append('-XX:-UseJVMCICompiler')

    if mx.get_os() == 'darwin':
        # suppress menubar and dock when running on Mac
        vmargs.append('-Djava.awt.headless=true')

    if args.cp:
        cp = os.path.abspath(args.cp)
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

    # To be able to load all classes in the JRT with Class.forName,
    # all JDK modules need to be made root modules.
    limitmods = frozenset(args.limitmods.split(',')) if args.limitmods else None
    graaljdk = get_graaljdk()
    nonBootJDKModules = [m.name for m in graaljdk.get_modules() if not m.boot and (limitmods is None or m.name in limitmods)]
    if nonBootJDKModules:
        vmargs.append('--add-modules=' + ','.join(nonBootJDKModules))
    if args.limitmods:
        vmargs.append('-DCompileTheWorld.limitmods=' + args.limitmods)
    if cp is not None:
        vmargs.append('-DCompileTheWorld.Classpath=' + cp)
    cp = _remove_redundant_entries(mx.classpath('GRAAL_TEST', jdk=graaljdk))
    vmargs.extend(_ctw_jvmci_export_args() + ['-cp', cp])
    mainClassAndArgs = ['jdk.graal.compiler.hotspot.test.CompileTheWorld']

    run_vm(vmargs + mainClassAndArgs)

class UnitTestRun:
    def __init__(self, name, args, tags):
        self.name = name
        self.args = args
        self.tags = tags

    def run(self, suites, tasks, extraVMarguments=None, extraUnitTestArguments=None):
        for suite in suites:
            newtags = self.tags + ["unittest-" + suite]
            with Task(self.name + ': hosted-product ' + suite, tasks, tags=newtags) as t:
                if mx_gate.Task.verbose:
                    extra_args = ['--verbose', '--enable-timing']
                else:
                    extra_args = []
                if Task.tags is None or 'coverage' not in Task.tags: # pylint: disable=unsupported-membership-test
                    # If this is a coverage execution, we want maximal coverage
                    # and thus must not fail fast. Otherwise, stop after the first 25
                    # failures. This guards against systemic test failure while still
                    # allowing a gate to reveal numerous failures.
                    extra_args += ['--max-class-failures=25']
                if t:
                    tags = {'task' : t.title}
                    unittest(['--suite', suite] + extra_args + self.args +
                              _remove_empty_entries(extraVMarguments) +
                              _remove_empty_entries(extraUnitTestArguments), test_report_tags=tags)

class BootstrapTest:
    def __init__(self, name, args, tags, suppress=None):
        self.name = name
        self.args = args
        self.suppress = suppress
        self.tags = tags
        if tags is not None and (not isinstance(tags, list) or all(not isinstance(x, str) for x in tags)):
            mx.abort("Gate tag argument must be a list of strings, tag argument:" + str(tags))

    def run(self, tasks, extraVMarguments=None):
        with Task(self.name, tasks, tags=self.tags, report=True) as t:
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
    ctwphaseplanfuzzing = ['ctwphaseplanfuzzing']
    doc = ['javadoc']
    phaseplan_fuzz_jtt_tests = ['phaseplan-fuzz-jtt-tests']

def _remove_empty_entries(a, filter_gcs=False):
    """Removes empty entries. Return value is always a list."""
    if not a:
        return []
    if filter_gcs:
        a = [x for x in a if not x.endswith('GC') or not x.startswith('-XX:+Use')]
    return [x for x in a if x]

def _compiler_error_options(default_compilation_failure_action='ExitVM', vmargs=None, prefix='-Djdk.graal.'):
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

def _gate_dacapo(name, iterations, extraVMarguments=None, force_serial_gc=True, threads=None, suite_version="9.12-MR1-git+2baec49"):
    # by default, it uses a version of the DaCapo suite archive that is reasonably-sized for gating
    if iterations == -1:
        return
    vmargs = ['-XX:+UseSerialGC'] if force_serial_gc else []
    vmargs += ['-Xmx8g', '-XX:-UseCompressedOops', '-Djava.net.preferIPv4Stack=true'] + _compiler_error_options() + _remove_empty_entries(extraVMarguments, filter_gcs=force_serial_gc)
    scratch_dir = os.path.abspath("./scratch")
    args = ['-n', str(iterations), '--preserve', '--scratch-directory', scratch_dir]

    # pmd validation fails on Windows, see official dacapobench issue #165
    if name == 'pmd':
        args += ['--no-validation']

    if threads is not None:
        args += ['-t', str(threads)]

    # catch `*-report.txt` if the benchmark fails
    try:
        return _run_benchmark('dacapo', name, args, vmargs, suite_version=suite_version)
    except BaseException as e:
        file = os.path.join(scratch_dir, f"{name}-report.txt")
        # not all benchmarks produce a report file
        if os.path.isfile(file):
            print("Report is located at: " + file)
        raise e

def jdk_includes_corba(jdk):
    # corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)
    return jdk.javaCompliance < '11'

def _gate_scala_dacapo(name, iterations, extraVMarguments=None):
    if iterations == -1:
        return
    vmargs = ['-Xmx8g', '-XX:+UseSerialGC', '-XX:-UseCompressedOops'] + _compiler_error_options() + _remove_empty_entries(extraVMarguments, filter_gcs=True)
    args = ['-n', str(iterations), '--preserve']
    return _run_benchmark('scala-dacapo', name, args, vmargs)


def _gate_renaissance(name, iterations, extraVMarguments=None):
    if iterations == -1:
        return
    vmargs = ['-Xmx8g', '-XX:-UseCompressedOops'] + _compiler_error_options() + _remove_empty_entries(extraVMarguments)
    args = ['-r', str(iterations)]
    return _run_benchmark('renaissance', name, args, vmargs)


def _run_benchmark(suite, name, args, vmargs, suite_version=None):
    if not [vmarg for vmarg in vmargs if vmarg.startswith('-Xmx')]:
        vmargs += ['-Xmx8g']
    out = mx.TeeOutputCapture(mx.OutputCapture())
    suite_version_arg = ["--bench-suite-version", str(suite_version)] if suite_version else []
    exit_code, suite, results = mx_benchmark.gate_mx_benchmark(["{}:{}".format(suite, name)] + suite_version_arg + [ "--tracker=none", "--"] + vmargs + ["--"] + args, out=out, err=out, nonZeroIsFatal=False)
    if exit_code != 0:
        mx.log(out)
        suite_str = f"{suite} (version={suite_version})" if suite_version else suite
        mx.abort("Gate for {} benchmark '{}' failed!".format(suite_str, name))
    return exit_code, suite, results

def _check_forbidden_imports(projects, package_substrings, exceptions=None):
    """
    Checks Java source files in `projects` to ensure there is no import from
    a class in a package whose name does not match `package_substrings`
    of a package whose name matches `package_substrings`.

    :param projects: list of JavaProjects
    :param package_substrings: package name substrings
    :param exceptions: set of unqualified Java source file names for which a failing
                       check produces a warning instead of an abort
    """
    # Assumes package name components start with lower case letter and
    # classes start with upper-case letter
    importStatementRe = re.compile(r'\s*import\s+(?:static\s+)?([a-zA-Z\d_$\.]+\*?)\s*;\s*')
    importedRe = re.compile(r'((?:[a-z][a-zA-Z\d_$]*\.)*[a-z][a-zA-Z\d_$]*)\.(?:(?:[A-Z][a-zA-Z\d_$]*)|\*)')
    for project in projects:
        for source_dir in project.source_dirs():
            for root, _, files in os.walk(source_dir):
                java_sources = [name for name in files if name.endswith('.java') and name != 'module-info.java']
                if len(java_sources) != 0:
                    java_package = root[len(source_dir) + 1:].replace(os.sep, '.')
                    if not any((s in java_package for s in package_substrings)):
                        for n in java_sources:
                            java_source = join(root, n)
                            with open(java_source) as fp:
                                for i, line in enumerate(fp):
                                    m = importStatementRe.match(line)
                                    if m:
                                        imported = m.group(1)
                                        m = importedRe.match(imported)
                                        lineNo = i + 1
                                        if not m:
                                            mx.abort(java_source + ':' + str(lineNo) + ': import statement does not match expected pattern:\n' + line)
                                        imported_package = m.group(1)
                                        for s in package_substrings:
                                            if s in imported_package:
                                                message = f'{java_source}:{lineNo}: forbidden import of a "{s}" package: {imported_package}\n{line}'
                                                if exceptions and n in exceptions:
                                                    mx.warn(message)
                                                else:
                                                    mx.abort(message)

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None, extraUnitTestArguments=None):
    with Task('CheckForbiddenImports:Compiler', tasks, tags=['style']) as t:
        # Ensure HotSpot-independent compiler classes do not import HotSpot-specific classes
        if t: _check_forbidden_imports([mx.project('jdk.graal.compiler')], ('hotspot', 'libgraal'))

    with Task('JDK_java_base_test', tasks, tags=['javabasetest'], report=True) as t:
        if t: java_base_unittest(_remove_empty_entries(extraVMarguments) + [])

    # Run unit tests in hosted mode
    for r in unit_test_runs:
        r.run(suites, tasks, ['-XX:-UseJVMCICompiler'] + _remove_empty_entries(extraVMarguments), extraUnitTestArguments=extraUnitTestArguments)

    # Run ctw against rt.jar on hosted
    ctw_flags = [
        '-DCompileTheWorld.Config=Inline=false CompilationFailureAction=ExitVM CompilationBailoutAsFailure=false', '-ea', '-esa',
        '-DCompileTheWorld.MultiThreaded=true', '-Djdk.graal.InlineDuringParsing=false', '-Djdk.graal.TrackNodeSourcePosition=true',
        '-DCompileTheWorld.Verbose=false', '-XX:ReservedCodeCacheSize=300m',
    ]
    ctw_phaseplan_fuzzing_flags = [
        '-DCompileTheWorld.FuzzPhasePlan=true',
        '-Djdk.graal.PrintGraphStateDiff=true',
    ]
    with Task('CTW:hosted', tasks, tags=GraalTags.ctw, report=True) as t:
        if t:
            ctw(ctw_flags, _remove_empty_entries(extraVMarguments))

    # Also run ctw with economy mode as a separate task, to be able to filter it with tags
    with Task('CTWEconomy:hosted', tasks, tags=GraalTags.ctweconomy, report=True) as t:
        if t:
            ctw(ctw_flags + _graalEconomyFlags, _remove_empty_entries(extraVMarguments))

    with Task('CTWPhaseplanFuzzing:hosted', tasks, tags=GraalTags.ctwphaseplanfuzzing, report=True) as t:
        if t:
            ctw(ctw_flags + ctw_phaseplan_fuzzing_flags, _remove_empty_entries(extraVMarguments))

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

    with Task('JTTPhaseplanFuzzing', tasks, tags=GraalTags.phaseplan_fuzz_jtt_tests, report=True) as t:
        if t:
            phaseplan_fuzz_jtt_tests([], extraVMarguments=_remove_empty_entries(extraVMarguments), extraUnitTestArguments=_remove_empty_entries(extraUnitTestArguments))

def compiler_gate_benchmark_runner(tasks, extraVMarguments=None, prefix='', task_report_component='compiler'):
    # run DaCapo benchmarks #
    #########################

    # DaCapo benchmarks that can run with system assertions enabled but
    # java.util.Logging assertions disabled because the the DaCapo harness
    # misuses the API. The same harness is used by Scala DaCapo.
    enable_assertions = ['-esa']
    dacapo_esa = enable_assertions + ['-da:java.util.logging...']

    # A few iterations to increase the chance of catching compilation errors
    default_iterations = 2
    daily_weekly_jobs_ratio = 2
    dacapo_daily_scaling_factor = 4
    scala_dacapo_daily_scaling_factor = 10
    default_iterations_reduction = 0.5
    dacapo_weekly_scaling_factor = dacapo_daily_scaling_factor * daily_weekly_jobs_ratio
    scala_dacapo_weekly_scaling_factor = scala_dacapo_daily_scaling_factor * daily_weekly_jobs_ratio

    bmSuiteArgs = ["--jvm", "server"]
    benchVmArgs = bmSuiteArgs + _remove_empty_entries(extraVMarguments)

    dacapo_suite = DaCapoBenchmarkSuite()
    dacapo_gate_iterations = {
        k: default_iterations for k, v in dacapo_suite.daCapoIterations().items() if v > 0
    }
    dacapo_gate_iterations.update({'fop': 8})
    for name in dacapo_suite.benchmarkList(bmSuiteArgs):
        iterations = dacapo_gate_iterations.get(name, -1)
        with Task(prefix + 'DaCapo:' + name, tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
            if t: _gate_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    with mx_gate.Task('Dacapo benchmark daily workload', tasks, tags=['dacapo_daily'], report=task_report_component) as t:
        if t:
            for name in dacapo_suite.benchmarkList(bmSuiteArgs):
                iterations = int(dacapo_suite.daCapoIterations().get(name, -1) * default_iterations_reduction)
                for _ in range(default_iterations * dacapo_daily_scaling_factor):
                    _gate_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    with mx_gate.Task('Dacapo benchmark weekly workload', tasks, tags=['dacapo_weekly'], report=task_report_component) as t:
        if t:
            for name in dacapo_suite.benchmarkList(bmSuiteArgs):
                iterations = int(dacapo_suite.daCapoIterations().get(name, -1) * default_iterations_reduction)
                for _ in range(default_iterations * dacapo_weekly_scaling_factor):
                    _gate_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    # ensure we can also run on C2
    with Task(prefix + 'DaCapo_C2:fop', tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
        if t:
            # Strip JVMCI args from C2 execution which uses -XX:-EnableJVMCI
            c2BenchVmArgs = [a for a in benchVmArgs if 'JVMCI' not in a]
            _gate_dacapo('fop', 1, ['--jvm-config', 'default'] + c2BenchVmArgs)

    # ensure we can run with --enable-preview
    with Task(prefix + 'DaCapo_enable-preview:fop', tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
        if t:
            _gate_dacapo('fop', 8, benchVmArgs + ['--enable-preview', '-Djdk.graal.CompilationFailureAction=ExitVM'])

    # run Scala DaCapo benchmarks #
    ###############################
    scala_dacapo_suite = ScalaDaCapoBenchmarkSuite()
    scala_dacapo_gate_iterations = {
        k: default_iterations for k, v in scala_dacapo_suite.daCapoIterations().items() if v > 0
    }
    for name in scala_dacapo_suite.benchmarkList(bmSuiteArgs):
        iterations = scala_dacapo_gate_iterations.get(name, -1)
        with Task(prefix + 'ScalaDaCapo:' + name, tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
            if t: _gate_scala_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    with mx_gate.Task('ScalaDacapo benchmark daily workload', tasks, tags=['scala_dacapo_daily'], report=task_report_component) as t:
        if t:
            for name in scala_dacapo_suite.benchmarkList(bmSuiteArgs):
                iterations = int(scala_dacapo_suite.daCapoIterations().get(name, -1) * default_iterations_reduction)
                for _ in range(default_iterations * scala_dacapo_daily_scaling_factor):
                    _gate_scala_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    with mx_gate.Task('ScalaDacapo benchmark weekly workload', tasks, tags=['scala_dacapo_weekly'], report=task_report_component) as t:
        if t:
            for name in scala_dacapo_suite.benchmarkList(bmSuiteArgs):
                iterations = int(scala_dacapo_suite.daCapoIterations().get(name, -1) * default_iterations_reduction)
                for _ in range(default_iterations * scala_dacapo_weekly_scaling_factor):
                    _gate_scala_dacapo(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + dacapo_esa)

    # run Renaissance benchmarks #
    ###############################
    renaissance_suite = RenaissanceBenchmarkSuite()
    renaissance_gate_iterations = {
        k: default_iterations for k, v in renaissance_suite.renaissanceIterations().items() if v > 0
    }

    # Renaissance is missing the msvc redistributable on Windows [GR-50132]
    if not mx.is_windows():
        for name in renaissance_suite.benchmarkList(bmSuiteArgs):
            iterations = renaissance_gate_iterations.get(name, -1)
            with Task(prefix + 'Renaissance:' + name, tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
                if t:
                    _gate_renaissance(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + enable_assertions)

        with mx_gate.Task('Renaissance benchmark daily workload', tasks, tags=['renaissance_daily'], report=task_report_component) as t:
            if t:
                for name in renaissance_suite.benchmarkList(bmSuiteArgs):
                    iterations = int(renaissance_suite.renaissanceIterations().get(name, -1) * default_iterations_reduction)
                    for _ in range(default_iterations):
                        _gate_renaissance(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + enable_assertions)

        with mx_gate.Task('Renaissance benchmark weekly workload', tasks, tags=['renaissance_weekly'], report=task_report_component) as t:
            if t:
                for name in renaissance_suite.benchmarkList(bmSuiteArgs):
                    iterations = int(renaissance_suite.renaissanceIterations().get(name, -1) * default_iterations_reduction)
                    for _ in range(default_iterations * daily_weekly_jobs_ratio):
                        _gate_renaissance(name, iterations, benchVmArgs + ['-Djdk.graal.TrackNodeSourcePosition=true'] + enable_assertions)

    # run benchmark with non default setup #
    ########################################

    # Ensure benchmark counters still work but omit this test on
    # fastdebug as benchmark counter threads may not produce
    # output in a timely manner. Also omit the test on libgraal
    # as it does not support TimedDynamicCounters.
    out = mx.OutputCapture()
    mx.run([jdk.java, '-version'], err=subprocess.STDOUT, out=out)
    if 'fastdebug' not in out.data and '-XX:+UseJVMCINativeLibrary' not in (extraVMarguments or []):
        with Task(prefix + 'DaCapo_pmd:BenchmarkCounters', tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
            if t:
                fd, logFile = tempfile.mkstemp()
                os.close(fd) # Don't leak file descriptors
                try:
                    _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Djdk.graal.LogFile=' + logFile, '-Djdk.graal.LIRProfileMoves=true', '-Djdk.graal.GenericDynamicCounters=true', '-Djdk.graal.TimedDynamicCounters=1000', '-XX:JVMCICounterSize=10'])
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

    # ensure -XX:+PreserveFramePointer  still works
    with Task(prefix + 'DaCapo_pmd:PreserveFramePointer', tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
        if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+PreserveFramePointer'], threads=4, force_serial_gc=False)

    # stress entry barrier deopt
    with Task(prefix + 'DaCapo_pmd:DeoptimizeNMethodBarriersALot', tasks, tags=GraalTags.benchmarktest, report=task_report_component) as t:
        if t: _gate_dacapo('pmd', default_iterations, benchVmArgs + ['-Xmx256M', '-XX:+UnlockDiagnosticVMOptions', '-XX:+DeoptimizeNMethodBarriersALot'], threads=4, force_serial_gc=False)

graal_unit_test_runs = [
    UnitTestRun('UnitTests', [], tags=GraalTags.unittest + GraalTags.coverage),
]

_registers = {
    'amd64': 'rbx,r11,r10,r14,xmm3,xmm2,xmm11,xmm14,k1?',
    'aarch64': 'r0,r1,r2,r3,r4,v0,v1,v2,v3',
    'riscv64': 'x10,x11,x12,x13,x14,v10,v11,v12,v13'
}
if mx.get_arch() not in _registers:
    mx.warn('No registers for register pressure tests are defined for architecture ' + mx.get_arch())

_bootstrapFlags = ['-XX:-UseJVMCINativeLibrary']
_defaultFlags = ['-Djdk.graal.CompilationWatchDogStartDelay=60']
_assertionFlags = ['-esa', '-Djdk.graal.DetailedAsserts=true']
_graalErrorFlags = _compiler_error_options()
_graalEconomyFlags = ['-Djdk.graal.CompilerConfiguration=economy']
_verificationFlags = ['-Djdk.graal.VerifyGraalGraphs=true', '-Djdk.graal.VerifyGraalGraphEdges=true', '-Djdk.graal.VerifyGraalPhasesSize=true']
_coopFlags = ['-XX:-UseCompressedOops']
_gcVerificationFlags = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC']
_g1VerificationFlags = ['-XX:-UseSerialGC', '-XX:+UseG1GC']
_exceptionFlags = ['-Djdk.graal.StressInvokeWithExceptionNode=true']
_registerPressureFlags = ['-Djdk.graal.RegisterPressure=' + _registers[mx.get_arch()]]

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertionsFullVerify', _bootstrapFlags + _defaultFlags + _assertionFlags + _verificationFlags + _graalErrorFlags, tags=GraalTags.bootstrapfullverify),
    BootstrapTest('BootstrapWithSystemAssertions', _bootstrapFlags + _defaultFlags + _assertionFlags + _graalErrorFlags, tags=GraalTags.bootstraplite),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', _bootstrapFlags + _defaultFlags + _assertionFlags + _coopFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithGCVerification', _bootstrapFlags + _defaultFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', _bootstrapFlags + _defaultFlags + _g1VerificationFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithSystemAssertionsEconomy', _bootstrapFlags + _defaultFlags + _assertionFlags + _graalEconomyFlags + _graalErrorFlags, tags=GraalTags.bootstrapeconomy),
    BootstrapTest('BootstrapWithSystemAssertionsExceptionEdges', _bootstrapFlags + _defaultFlags + _assertionFlags + _exceptionFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithSystemAssertionsRegisterPressure', _bootstrapFlags + _defaultFlags + _assertionFlags + _registerPressureFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
]

_runs_on_github_actions = 'GITHUB_ACTION' in os.environ

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['compiler', 'truffle'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument, args.extra_unittest_argument)
    if not _runs_on_github_actions:
        compiler_gate_benchmark_runner(tasks, args.extra_vm_argument, task_report_component='compiler')

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
        # shlex.split interprets '\' as an escape char so it needs to be escaped itself
        values = values.replace("\\", "\\\\")
        setattr(namespace, self.dest, (old_values if old_values else []) + shlex.split(values))

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action=ShellEscapedStringAction, help='add extra vm arguments to gate tasks if applicable')
mx_gate.add_gate_argument('--extra-unittest-argument', action=ShellEscapedStringAction, help='add extra unit test arguments to gate tasks if applicable')

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    jdk = _get_unittest_jdk()
    if jdk.tag == 'graalvm':
        # we do not want to use -server for GraalVM configurations
        mx.run_java(vmArgs + [mainClass] + mainClassArgs, jdk=jdk)
    else:
        run_vm(vmArgs + [mainClass] + mainClassArgs)


def _remove_redundant_entries(cp):
    """
    Removes entries from the class path `cp` that are in Graal or on the boot class path.
    """

    # Remove all duplicates in cp and convert it to a list of entries
    seen = set()
    cp = [e for e in cp.split(os.pathsep) if e not in seen and seen.add(e) is None]

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


class GraalUnittestConfig(mx_unittest.MxUnittestConfig):

    def __init__(self):
        super(GraalUnittestConfig, self).__init__('graal')

    def apply(self, config):
        vmArgs, mainClass, mainClassArgs = config
        cpIndex, cp = mx.find_classpath_arg(vmArgs)
        if cp:
            cp = _remove_redundant_entries(cp)

            vmArgs[cpIndex] = cp
            # JVMCI is dynamically exported to Graal when JVMCI is initialized. This is too late
            # for the junit harness which uses reflection to find @Test methods. In addition, the
            # tests widely use JVMCI classes so JVMCI needs to also export all its packages to
            # ALL-UNNAMED.
            mainClassArgs.extend(['-JUnitOpenPackages', 'jdk.internal.vm.ci/*=org.graalvm.truffle.runtime,jdk.graal.compiler,ALL-UNNAMED'])

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
                    for dependency, packages in jmd.concealedRequires.items():
                        if dependency != "jdk.internal.vm.ci":
                            # JVMCI exporting is done dynamically
                            for p in packages:
                                vmArgs.append(f'--add-exports={dependency}/{p}={jmd.name}')

        vmArgs.append('-Djdk.graal.TrackNodeSourcePosition=true')
        vmArgs.append('-esa')

        if '-JUnitMaxTestTime' not in mainClassArgs:
            # The max time (in seconds) for any compiler unit test
            mainClassArgs.extend(['-JUnitMaxTestTime', '300'])

        # Always run unit tests without UseJVMCICompiler unless explicitly requested
        if _get_XX_option_value(vmArgs, 'UseJVMCICompiler', None) is None:
            vmArgs.append('-XX:-UseJVMCICompiler')

        # Always run unit tests without UseJVMCINativeLibrary unless explicitly requested
        if _get_XX_option_value(vmArgs, 'UseJVMCINativeLibrary', None) is None:
            vmArgs.append('-XX:-UseJVMCINativeLibrary')

        # The type-profile width 8 is the default when using the JVMCI compiler.
        # This value must be forced, because we do not used the JVMCI compiler
        # in the unit tests by default.
        if _get_XX_option_value(vmArgs, 'TypeProfileWidth', None) is None:
            vmArgs.append('-XX:TypeProfileWidth=8')

        vmArgs.append('--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED')
        # TODO: GR-31197, this should be removed.
        vmArgs.append('-Dpolyglot.engine.DynamicCompilationThresholds=false')
        vmArgs.append('-Dpolyglot.engine.AllowExperimentalOptions=true')
        return (vmArgs, mainClass, mainClassArgs)


mx_unittest.register_unittest_config(GraalUnittestConfig())

_use_graalvm = False

class SwitchToGraalVMJDK(argparse.Action):
    def __init__(self, **kwargs):
        global _use_graalvm
        kwargs['required'] = False
        kwargs['nargs'] = 0
        argparse.Action.__init__(self, **kwargs)
        _use_graalvm = False
    def __call__(self, parser, namespace, values, option_string=None):
        global _use_graalvm
        _use_graalvm = True

def _get_unittest_jdk():
    global _use_graalvm
    if _use_graalvm:
        return mx.get_jdk(tag='graalvm')
    else:
        return jdk

mx_unittest.set_vm_launcher('JDK VM launcher', _unittest_vm_launcher, _get_unittest_jdk)
# Note this option should probably be implemented in mx_sdk. However there can be only
# one set_vm_launcher call per configuration, so we we do it here where it is easy to compose
# with the mx_compiler behavior.
mx_unittest.add_unittest_argument('--use-graalvm', default=False, help='Use the previously built GraalVM for running the unit test.', action=SwitchToGraalVMJDK)


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
    argsPrefix = []

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
    if not any(a.startswith('-Djdk.graal.PrintGraph=') for a in args):
        argsPrefix.append('-Djdk.graal.PrintGraph=Network')

    # Likewise, one can assume that objdump is safe to access when using mx.
    if not any(a.startswith('-Djdk.graal.ObjdumpExecutables=') for a in args):
        argsPrefix.append('-Djdk.graal.ObjdumpExecutables=objdump,gobjdump')

    # The GraalVM locator must be disabled so that Truffle languages
    # are loaded from the class path. This is the configuration expected
    # by the unit tests and benchmarks run via the compiler suite.
    if not any(a.startswith('-Dgraalvm.locatorDisabled=') for a in args):
        argsPrefix.append('-Dgraalvm.locatorDisabled=true')

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
        mx.abort("Cannot find GraalJDK images with base name '{}'".format(base_name))
    if len(graaljdks) > 1:
        mx.abort("Found multiple GraalJDKs with the same base name '{}'".format(base_name))
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

        # Keep in sync with jdk.graal.compiler.debug.GlobalMetrics.print(OptionValues)
        abs_filename = join(os.getcwd(), filename)
        directory = dirname(abs_filename)
        rootname = basename(filename)[0:-len('.csv')]
        isolate_metrics_re = re.compile(rootname + r'@\d+\.csv')
        for entry in os.listdir(directory):
            m = isolate_metrics_re.match(entry)
            if m:
                isolate_metrics = join(directory, entry)
                with open(isolate_metrics) as fp:
                    reader = csv.reader(fp, delimiter=';')
                    line_no = 1
                    for line_no, values in enumerate(reader, start=1):
                        if len(values) != 3:
                            mx.abort('{}:{}: invalid line: {}'.format(isolate_metrics, line_no, values))
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

    if not results:
        mx.log(f"No results to collate for '{args.filenames[0]}'")
    elif args.filenames:
        collated_filename = args.filenames[0][:-len('.csv')] + '.collated.csv'
        with open(collated_filename, 'w') as fp:
            writer = csv.writer(fp, delimiter=';')
            for n, series in sorted(results.items()):
                while len(series) < len(args.filenames):
                    series.append(0)
                writer.writerow([n] + [str(v) for v in series] + [units[n]])
        mx.log(f"Collated metrics into '{collated_filename}'")

def run_java(args, out=None, err=None, addDefaultArgs=True, command_mapper_hooks=None, jdk=None, **kw_args):
    graaljdk = jdk or get_graaljdk()
    vm_args = _parseVmArgs(args, addDefaultArgs=addDefaultArgs)
    args = ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '--add-exports=java.base/jdk.internal.misc=jdk.graal.compiler'] + vm_args
    _check_bootstrap_config(args)
    cmd = get_vm_prefix() + [graaljdk.java] + ['-server'] + args
    map_file = join(graaljdk.home, 'proguard.map')

    with StdoutUnstripping(args, out, err, mapFiles=[map_file]) as u:
        try:
            cmd = mx.apply_command_mapper_hooks(cmd, command_mapper_hooks)
            return mx.run(cmd, out=u.out, err=u.err, **kw_args)
        finally:
            # Collate AggratedMetricsFile
            for a in vm_args:
                if a.startswith('-Djdk.graal.AggregatedMetricsFile='):
                    metrics_file = a[len('-Djdk.graal.AggregatedMetricsFile='):]
                    if metrics_file:
                        collate_metrics([metrics_file])


class GraalJVMCIJDKConfig(mx.JDKConfig):
    """
    A JDKConfig that configures Graal as the JVMCI compiler.
    """
    def __init__(self):
        mx.JDKConfig.__init__(self, jdk.home, tag=JVMCI_JDK_TAG)

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
    jvmci_re = re.compile(r'(?:ce|ee)-(?P<jdk_version>.+)-jvmci(?:-(?P<jvmci_major>\d+)\.(?P<jvmci_minor>\d+))?-b(?P<jvmci_build>\d+)')
    common_path = os.path.normpath(join(_suite.dir, '..', 'common.json'))

    if _jdk_jvmci_version is None:
        # Not using a JVMCI JDK
        return

    def get_latest_jvmci_version():
        with open(common_path) as common_file:
            common_cfg = json.load(common_file)

        latest = 'not found'
        for distribution in common_cfg['jdks']:
            version = common_cfg['jdks'][distribution].get('version', None)
            if version and '-jvmci-' in version:
                match = jvmci_re.match(version)
                if not match:
                    mx.abort(f'Cannot parse version {version}')
                (jdk_version, jvmci_major, jvmci_minor, jvmci_build) = match.groups(default=0)
                if _jdk_jvmci_version.jvmci_build == 0:
                    # jvmci_build == 0 indicates an OpenJDK version has been specified in JVMCIVersionCheck.java.
                    # The JDK does not know the jvmci_build number that might have been specified in common.json,
                    # as it is only a repackaged JDK. Thus, we reset the jvmci_build because we cannot validate it.
                    jvmci_build = 0
                current = JVMCIVersionCheckVersion(JavaLangRuntimeVersion(jdk_version), int(jvmci_major), int(jvmci_minor), int(jvmci_build))
                if current.jdk_version.feature() == _jdk_jvmci_version.jdk_version.feature():
                    # only compare the same major versions
                    if latest == 'not found':
                        latest = current
                    elif latest != current:
                        # All JVMCI JDKs in common.json with the same major version
                        # are expected to have the same JVMCI version.
                        # If they don't then the repo is in some transitionary state
                        # (e.g. making a JVMCI release) so skip the check.
                        return False, distribution
        return not isinstance(latest, str), latest

    version_check_setting = os.environ.get('JVMCI_VERSION_CHECK', None)

    success, latest = get_latest_jvmci_version()

    if version_check_setting == 'strict' and _jdk_jvmci_version != _jdk_min_jvmci_version:
        msg = f'JVMCI_MIN_VERSION specified in JVMCIVersionCheck.java is older than in {common_path}:'
        msg += os.linesep + f'{_jdk_min_jvmci_version} < {_jdk_jvmci_version} '
        msg += os.linesep + f'Did you forget to update JVMCI_MIN_VERSION after updating {common_path}?'
        msg += os.linesep + 'Set the JVMCI_VERSION_CHECK environment variable to something else then "strict" to'
        msg += ' suppress this error.'
        mx.abort(msg)

    if version_check_setting == 'strict' and not success:
        if latest == 'not found':
            msg = f'No JVMCI JDK found in {common_path} that matches {_jdk_jvmci_version}.'
            msg += os.linesep + f'Check that {latest} matches the versions of the other JVMCI JDKs.'
        else:
            msg = f'Version mismatch in {common_path}:'
            msg += os.linesep + f'Check that {latest} matches the versions of the other JVMCI JDKs.'
        msg += os.linesep + 'Set the JVMCI_VERSION_CHECK environment variable to something else then "strict" to'
        msg += ' suppress this error.'
        mx.abort(msg)

    if success and _jdk_jvmci_version < latest:
        msg = f'JVMCI version of JAVA_HOME is older than in {common_path}: {_jdk_jvmci_version} < {latest} '
        msg += os.linesep + 'This poses the risk of hitting JVMCI bugs that have already been fixed.'
        msg += os.linesep + f'Consider using {latest}, which you can get via:'
        msg += os.linesep + f'mx fetch-jdk --configuration {common_path}'
        mx.abort_or_warn(msg, version_check_setting == 'strict')

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
                for service in (contents_supplier()).decode().strip().split(os.linesep):
                    assert service
                    version = m.group(1)
                    add_serviceprovider(service, provider, version)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            if self.isTest:
                mx.warn('@Option defined in test code will be ignored: ' + arcname)
            else:
                version_prefix = 'META-INF/versions/'
                if arcname.startswith(version_prefix):
                    # If OptionDescriptors is version-specific, get version
                    # from arcname and adjust arcname to non-version form
                    version, _, arcname = arcname[len(version_prefix):].partition('/')
                else:
                    version = None

                provider = arcname[0:-len('.class')].replace("/", ".")
                service = 'jdk.graal.compiler.options.OptionDescriptors'
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
    base_modules = ['java.base', 'java.logging', 'jdk.internal.vm.ci', 'org.graalvm.truffle.runtime', 'jdk.unsupported', 'jdk.compiler', 'java.instrument']
    compiler_modules = [as_java_module(d, jdk).name for d in _graal_config().dists if d.name != 'GRAAL_MANAGEMENT']
    root_module_names = base_modules + compiler_modules
    extra_args = ['--limit-modules=' + ','.join(root_module_names)]

    if mx_gate.Task.verbose:
        extra_args.extend(['--verbose', '--enable-timing'])
    # the base JDK doesn't include jdwp
    if get_graaljdk().debug_args:
        mx.warn('Ignoring Java debugger arguments because base JDK doesn\'t include jdwp')
    with mx.DisableJavaDebugging():
        mx_unittest.unittest(['--suite', 'compiler', '--max-class-failures=25'] + extra_args + args)

def javadoc(args):
    # metadata package was deprecated, exclude it
    if not '--exclude-packages' in args:
        args.append('--exclude-packages')
        args.append('com.oracle.truffle.api.metadata')
    mx.javadoc(args, quietForNoPackages=True)

def phaseplan_fuzz_jtt_tests(args, extraVMarguments=None, extraUnitTestArguments=None):
    """runs JTT unit tests with fuzzed compilation plans"""

    parser = ArgumentParser(prog='mx phaseplan-fuzz-jtt-tests', description='Run JTT unit tests with fuzzed phase plans')
    parser.add_argument('--seed', metavar='<seed>', help='Seed to initialize random instance')
    parser.add_argument('--minimal', action='store_true',
        help='Force the use of a minimal fuzzed compilation plan')
    parser.add_argument('--skip-phase-odds', dest='skip_phase_odds', metavar='<int>',
        help='Determine the odds of skipping the insertion of a phase in the fuzzed phase plan')
    parser.add_argument('--high-tier-skip-phase', dest='high_tier_skip_phase', metavar='<int>',
        help='Determine the odds of skipping the insertion of a phase in high tier')
    parser.add_argument('--mid-tier-skip-phase', dest='mid_tier_skip_phase', metavar='<int>',
        help='Determine the odds of skipping the insertion of a phase in mid tier')
    parser.add_argument('--low-tier-skip-phase', dest='low_tier_skip_phase', metavar='<int>',
        help='Determine the odds of skipping the insertion of a phase in low tier')

    args, parsed_args = parse_split_args(args, parser, "--")
    vm_args = _remove_empty_entries(extraVMarguments) + ['-Dtest.graal.compilationplan.fuzzing=true', '-Djdk.graal.PrintGraphStateDiff=true', '--verbose']

    if parsed_args.seed:
        vm_args.append('-Dtest.graal.compilationplan.fuzzing.seed=' + parsed_args.seed)
    if parsed_args.minimal:
        vm_args.append('-Dtest.graal.compilationplan.fuzzing.minimal=true')
    if parsed_args.skip_phase_odds:
        vm_args.append('-Dtest.graal.skip.phase.insertion.odds=' + parsed_args.skip_phase_odds)
    if parsed_args.high_tier_skip_phase:
        vm_args.append('-Dtest.graal.skip.phase.insertion.odds.high.tier=' + parsed_args.high_tier_skip_phase)
    if parsed_args.mid_tier_skip_phase:
        vm_args.append('-Dtest.graal.skip.phase.insertion.odds.mid.tier=' + parsed_args.mid_tier_skip_phase)
    if parsed_args.low_tier_skip_phase:
        vm_args.append('-Dtest.graal.skip.phase.insertion.odds.low.tier=' + parsed_args.low_tier_skip_phase)

    target_tests = []
    for arg in args:
        if not arg.startswith('-'):
            target_tests.append(arg)
            args.remove(arg)
    if not target_tests:
        target_tests = ['jdk.graal.compiler.jtt.']

    for test in target_tests:
        UnitTestRun("Fuzz phase plan for tests matching substring " + test, [], tags=GraalTags.unittest + GraalTags.phaseplan_fuzz_jtt_tests).\
            run(['compiler'], [], ['-XX:-UseJVMCICompiler'] + vm_args, _remove_empty_entries(extraUnitTestArguments) + args + [test])


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
            self.jvmci_parent_jars = []
            self.boot_dists = []
            self.boot_jars = []
            self.truffle_jars = []
            self.jars = []

            for component in mx_sdk_vm_impl.registered_graalvm_components():
                if isinstance(component, mx_sdk_vm.GraalVmJvmciComponent):
                    for jar in component.jvmci_jars:
                        d = mx.distribution(jar)
                        self.jvmci_dists.append(d)
                        self.jvmci_jars.append(d.classpath_repr())
                for jar in component.boot_jars:
                    d = mx.distribution(jar)
                    self.boot_dists.append(d)
                    self.boot_jars.append(d.classpath_repr())

            self.jvmci_parent_jars = []

            self.dists = self.jvmci_dists + self.boot_dists
            self.jars = self.jvmci_jars + self.jvmci_parent_jars + self.boot_jars

            self.dists_dict = {e.suite.name + ':' + e.name : e for e in self.dists}

    if __graal_config is None:
        __graal_config = GraalConfig()
    return __graal_config

# The jars needed for jargraal.
def _jvmci_jars():
    return [
        'compiler:GRAAL',
        'compiler:GRAAL_MANAGEMENT',
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
        dependencies=['Truffle Compiler', 'Graal SDK Compiler'],
        jar_distributions=[  # Dev jars (annotation processors)
            'compiler:GRAAL_PROCESSOR',
        ],
        jvmci_jars=_jvmci_jars(),
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

def profdiff(args):
    """compare the optimization log of hot compilation units of two experiments"""
    cp = mx.classpath('GRAAL_PROFDIFF', jdk=jdk)
    vm_args = ['-cp', cp, 'org.graalvm.profdiff.Profdiff'] + args
    return jdk.run_java(args=vm_args)

def replaycomp_vm_args():
    """Returns the VM arguments required to run the replay compilation launcher."""
    vm_args = [
        '-XX:-UseJVMCICompiler',
        '--enable-native-access=org.graalvm.truffle',
        '--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
        '-Djdk.graal.CompilationFailureAction=Print'
    ]
    _, dists = mx.defaultDependencies(opt_limit_to_suite=True)
    dists = [d for d in dists if d.isJARDistribution() and os.path.exists(d.classpath_repr(resolve=False))]
    return mx.get_runtime_jvm_args(dists) + vm_args

def replaycomp_main_class():
    """Returns the main class name for the replay compilation launcher."""
    return 'jdk.graal.compiler.hotspot.replaycomp.test.ReplayCompilationLauncher'

def replaycomp(args):
    """Runs the replay compilation launcher with the provided launcher and VM arguments."""
    extra_vm_args = []
    launcher_args = []
    for arg in args:
        vm_arg_prefixes = ['-X', '-D', '-ea', '-enableassertions', '-esa', '-enablesystemassertions']
        if any(map(arg.startswith, vm_arg_prefixes)):
            extra_vm_args.append(arg)
        elif arg == '--libgraal':
            jvmci_lib_path = os.path.join(mx.suite('sdk').get_output_root(platformDependent=True, jdkDependent=False),
                                          mx.add_lib_suffix(mx.add_lib_prefix('jvmcicompiler')) + '.image')
            extra_vm_args.extend([
                '-XX:+UseJVMCINativeLibrary',
                f'-XX:JVMCILibPath={jvmci_lib_path}'
            ])
        else:
            launcher_args.append(arg)
    return run_vm([*replaycomp_vm_args(), *extra_vm_args, replaycomp_main_class(), *launcher_args], nonZeroIsFatal=False)

def igvutil(args):
    """various utilities to inspect and modify IGV graphs"""
    cp = mx.classpath('GRAAL_IGVUTIL', jdk=jdk)
    vm_args = ['-cp', cp, 'org.graalvm.igvutil.IgvUtility'] + args
    return jdk.run_java(args=vm_args)

mx.update_commands(_suite, {
    'sl' : [sl, '[SL args|@VM options]'],
    'vm': [run_vm_with_jvmci_compiler, '[-options] class [args...]'],
    'collate-metrics': [collate_metrics, 'filename'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'java_base_unittest' : [java_base_unittest, 'Runs unittest on JDK java.base "only" module(s)'],
    'javadoc': [javadoc, ''],
    'makegraaljdk': [makegraaljdk_cli, '[options]'],
    'graaljdk-home': [print_graaljdk_home, '[options]'],
    'graaljdk-show': [print_graaljdk_config, '[options]'],
    'phaseplan-fuzz-jtt-tests': [phaseplan_fuzz_jtt_tests, "Runs JTT's unit tests with fuzzed phase plans."],
    'profdiff': [profdiff, '[options] proftool_output1 optimization_log1 proftool_output2 optimization_log2'],
    'replaycomp': [replaycomp, ''],
    'igvutil': [igvutil, '[subcommand] [options]'],
})

mx.add_argument('--no-jacoco-exclude-truffle', action='store_false', dest='jacoco_exclude_truffle', help="Don't exclude Truffle classes from jacoco annotations.")

def mx_post_parse_cmd_line(opts):
    mx.addJDKFactory(JVMCI_JDK_TAG, jdk.javaCompliance, GraalJDKFactory())
    mx.add_ide_envvar('JVMCI_VERSION_CHECK')
    for dist in _suite.dists:
        if hasattr(dist, 'set_archiveparticipant'):
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

    global _vm_prefix
    _vm_prefix = opts.vm_prefix

    mx_gate.add_jacoco_includes(['org.graalvm.*'])
    mx_gate.add_jacoco_includes(['jdk.graal.compiler.*'])
    mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution', '@ExcludeFromJacocoInstrumentation'])
    if opts.jacoco_exclude_truffle:
        mx_gate.add_jacoco_excludes(['com.oracle.truffle'])
