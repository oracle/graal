#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import shutil
import sys
import hashlib
import io

import mx_truffle
import mx_sdk_vm

import mx
import mx_gate
from mx_gate import Task
from mx import SafeDirectoryUpdater

import mx_unittest
from mx_unittest import unittest

from mx_javamodules import as_java_module
from mx_updategraalinopenjdk import updategraalinopenjdk
from mx_renamegraalpackages import renamegraalpackages
from mx_sdk_vm import jlink_new_jdk

import mx_jaotc

import mx_graal_benchmark # pylint: disable=unused-import
import mx_graal_tools #pylint: disable=unused-import

import argparse
import shlex
import glob

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
    mx.run([jdk.java, '-cp', binDir, unqualified_name])

if os.environ.get('JVMCI_VERSION_CHECK', None) != 'ignore':
    _check_jvmci_version(jdk)

mx_gate.add_jacoco_includes(['org.graalvm.compiler.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

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

def verify_jvmci_ci_versions(args):
    """
    Checks that the jvmci versions used in various ci files agree.

    If the ci.hocon files use a -dev version, it allows the travis ones to use the previous version.
    For example, if ci.hocon uses jvmci-0.24-dev, travis may use either jvmci-0.24-dev or jvmci-0.23
    """
    version_pattern = re.compile(r'^(?!\s*#).*jvmci-(?P<major>\d*)(?:\.|-b)(?P<minor>\d*)(?P<dev>-dev)?')

    def _grep_version(files, msg):
        version = None
        dev = None
        last = None
        linenr = 0
        for filename in files:
            for line in open(filename):
                m = version_pattern.search(line)
                if m:
                    new_major = m.group('major')
                    new_minor = m.group('minor')
                    new_version = (new_major, new_minor)
                    new_dev = bool(m.group('dev'))
                    if (version and version != new_version) or (dev is not None and dev != new_dev):
                        mx.abort(
                            os.linesep.join([
                                "Multiple JVMCI versions found in {0} files:".format(msg),
                                "  {0} in {1}:{2}:    {3}".format(version + ('-dev' if dev else ''), *last), # pylint: disable=not-an-iterable
                                "  {0} in {1}:{2}:    {3}".format(new_version + ('-dev' if new_dev else ''), filename, linenr, line),
                            ]))
                    last = (filename, linenr, line.rstrip())
                    version = new_version
                    dev = new_dev
                linenr += 1
        if not version:
            mx.abort("No JVMCI version found in {0} files!".format(msg))
        return version, dev

    primary_suite = mx.primary_suite()
    hocon_version, hocon_dev = _grep_version(
        [join(primary_suite.vc_dir, 'common.json')] +
        glob.glob(join(primary_suite.vc_dir, '*.hocon')) +
        glob.glob(join(primary_suite.dir, 'ci*.hocon')) +
        glob.glob(join(primary_suite.dir, 'ci*/*.hocon')), 'hocon')
    travis_version, travis_dev = _grep_version([join(primary_suite.vc_dir, '.travis.yml')], 'TravisCI')

    if hocon_version != travis_version or hocon_dev != travis_dev:
        versions_ok = False
        if not travis_dev and hocon_dev:
            travis_major, travis_minor = travis_version # pylint: disable=unpacking-non-sequence
            next_travis_minor = str(int(travis_minor) + 1)
            next_travis_version = (travis_major, next_travis_minor)
            if next_travis_version == hocon_version:
                versions_ok = True
        if not versions_ok:
            mx.abort("Travis and ci.hocon JVMCI versions do not match: {0} vs. {1}".format(str(travis_version) + ('-dev' if travis_dev else ''), str(hocon_version) + ('-dev' if hocon_dev else '')))
    mx.log('JVMCI versions are ok!')


class UnitTestRun:
    def __init__(self, name, args, tags):
        self.name = name
        self.args = args
        self.tags = tags

    def run(self, suites, tasks, extraVMarguments=None):
        for suite in suites:
            if suite == 'truffle' and mx.get_os() == 'windows':
                continue  # necessary until Truffle is fully supported (GR-7941)
            with Task(self.name + ': hosted-product ' + suite, tasks, tags=self.tags) as t:
                if mx_gate.Task.verbose:
                    extra_args = ['--verbose', '--enable-timing']
                else:
                    extra_args = []
                if Task.tags is None or 'coverage' not in Task.tags:
                    # If this is a coverage execution, we want maximal coverage
                    # and thus must not fail fast.
                    extra_args += ['--fail-fast']
                if t: unittest(['--suite', suite] + extra_args + self.args + _remove_empty_entries(extraVMarguments))

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
    test = ['test', 'fulltest']
    coverage = ['coverage']
    benchmarktest = ['benchmarktest', 'fulltest']
    ctw = ['ctw', 'fulltest']
    doc = ['javadoc']

def _remove_empty_entries(a):
    """Removes empty entries. Return value is always a list."""
    if not a:
        return []
    return [x for x in a if x]

def _gate_java_benchmark(args, successRe):
    """
    Runs a Java benchmark and aborts if the benchmark process exits with a non-zero
    exit code or the `successRe` pattern is not in the output of the benchmark process.

    :param list args: the arguments to pass to the VM
    :param str successRe: a regular expression
    """
    out = mx.OutputCapture()
    try:
        run_java(args, out=mx.TeeOutputCapture(out), err=subprocess.STDOUT)
    finally:
        jvmErrorFile = re.search(r'(([A-Z]:|/).*[/\\]hs_err_pid[0-9]+\.log)', out.data)
        if jvmErrorFile:
            jvmErrorFile = jvmErrorFile.group()
            mx.log('Dumping ' + jvmErrorFile)
            with open(jvmErrorFile) as fp:
                mx.log(fp.read())
            os.unlink(jvmErrorFile)

    if not re.search(successRe, out.data, re.MULTILINE):
        mx.abort('Could not find benchmark success pattern: ' + successRe)

def _is_batik_supported(jdk):
    """
    Determines if Batik runs on the given jdk. Batik's JPEGRegistryEntry contains a reference
    to TruncatedFileException, which is specific to the Sun/Oracle JDK. On a different JDK,
    this results in a NoClassDefFoundError: com/sun/image/codec/jpeg/TruncatedFileException
    """
    try:
        subprocess.check_output([jdk.javap, 'com.sun.image.codec.jpeg.TruncatedFileException'])
        return True
    except subprocess.CalledProcessError:
        mx.warn('Batik uses Sun internal class com.sun.image.codec.jpeg.TruncatedFileException which is not present in ' + jdk.home)
        return False

def _gate_dacapo(name, iterations, extraVMarguments=None, force_serial_gc=True, set_start_heap_size=True, threads=None):
    vmargs = ['-XX:+UseSerialGC'] if force_serial_gc else []
    if set_start_heap_size:
        vmargs += ['-Xms2g']
    vmargs += ['-XX:-UseCompressedOops', '-Djava.net.preferIPv4Stack=true', '-Dgraal.CompilationFailureAction=ExitVM'] + _remove_empty_entries(extraVMarguments)
    dacapoJar = mx.library('DACAPO').get_path(True)
    if name == 'batik' and not _is_batik_supported(jdk):
        return
    args = ['-n', str(iterations)]
    if threads is not None:
        args += ['-t', str(threads)]
    _gate_java_benchmark(vmargs + ['-jar', dacapoJar, name] + args, r'^===== DaCapo 9\.12 ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====')

def jdk_includes_corba(jdk):
    # corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)
    return jdk.javaCompliance < '11'

def _gate_scala_dacapo(name, iterations, extraVMarguments=None):
    vmargs = ['-Xms2g', '-XX:+UseSerialGC', '-XX:-UseCompressedOops', '-Dgraal.CompilationFailureAction=ExitVM'] + _remove_empty_entries(extraVMarguments)
    if name == 'actors' and jdk.javaCompliance >= '9' and jdk_includes_corba(jdk):
        vmargs += ['--add-modules', 'java.corba']
    scalaDacapoJar = mx.library('DACAPO_SCALA').get_path(True)
    _gate_java_benchmark(vmargs + ['-jar', scalaDacapoJar, name, '-n', str(iterations)], r'^===== DaCapo 0\.1\.0(-SNAPSHOT)? ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====')


def jvmci_ci_version_gate_runner(tasks):
    # Check that travis and ci.hocon use the same JVMCI version
    with Task('JVMCI_CI_VersionSyncCheck', tasks, tags=[mx_gate.Tags.style]) as t:
        if t: verify_jvmci_ci_versions([])

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):
    if jdk.javaCompliance >= '9':
        with Task('JDK_java_base_test', tasks, tags=['javabasetest']) as t:
            if t: java_base_unittest(_remove_empty_entries(extraVMarguments) + [])

    # Run unit tests in hosted mode
    for r in unit_test_runs:
        r.run(suites, tasks, ['-XX:-UseJVMCICompiler'] + _remove_empty_entries(extraVMarguments))

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

    # Ensure makegraaljdk works
    with Task('MakeGraalJDK', tasks, tags=GraalTags.test) as t:
        if t:
            ws = mx.ensure_dir_exists('MakeGraalJDK-ws')
            graaljdk = join(ws, 'graaljdk-' + str(jdk.javaCompliance))
            try:
                makegraaljdk_cli(['-a', join(ws, 'graaljdk-' + str(jdk.javaCompliance) + '.tar'), '-b', graaljdk])
            finally:
                mx.rmtree(ws)

    # Run ctw against rt.jar on hosted
    with Task('CTW:hosted', tasks, tags=GraalTags.ctw) as t:
        if t:
            ctw([
                    '-DCompileTheWorld.Config=Inline=false CompilationFailureAction=ExitVM', '-esa', '-XX:-UseJVMCICompiler', '-XX:+EnableJVMCI',
                    '-DCompileTheWorld.MultiThreaded=true', '-Dgraal.InlineDuringParsing=false', '-Dgraal.TrackNodeSourcePosition=true',
                    '-DCompileTheWorld.Verbose=false', '-XX:ReservedCodeCacheSize=300m',
                ], _remove_empty_entries(extraVMarguments))

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    with Task('Javadoc', tasks, tags=GraalTags.doc) as t:
        # metadata package was deprecated, exclude it
        if t: mx.javadoc(['--exclude-packages', 'com.oracle.truffle.dsl.processor.java'], quietForNoPackages=True)


def compiler_gate_benchmark_runner(tasks, extraVMarguments=None, prefix=''):
    # run selected DaCapo benchmarks

    # DaCapo benchmarks that can run with system assertions enabled but
    # java.util.Logging assertions disabled because the the DaCapo harness
    # misuses the API.
    dacapos = {
        'avrora':     1,
        'h2':         1,
        'jython':     2,
        'luindex':    1,
        'lusearch':   4,
        'xalan':      1,
        'batik':      1,
        'fop':        8,
        'pmd':        1,
        'sunflow':    2,
    }
    for name, iterations in sorted(dacapos.items()):
        with Task(prefix + 'DaCapo:' + name, tasks, tags=GraalTags.benchmarktest) as t:
            if t: _gate_dacapo(name, iterations, _remove_empty_entries(extraVMarguments) +
                               ['-XX:+UseJVMCICompiler', '-Dgraal.TrackNodeSourcePosition=true', '-esa', '-da:java.util.logging...'])

    # run selected Scala DaCapo benchmarks
    # Scala DaCapo benchmarks that can run with system assertions enabled but
    # java.util.Logging assertions disabled because the the DaCapo harness
    # misuses the API.
    scala_dacapos = {
        'apparat':    1,
        'factorie':   1,
        'kiama':      4,
        'scalac':     1,
        'scaladoc':   1,
        'scalap':     1,
        'scalariform':1,
        'scalatest':  1,
        'scalaxb':    1,
        'specs':      1,
        'tmt':        1,
        'actors':     1,
    }
    if not jdk_includes_corba(jdk):
        mx.warn('Removing scaladacapo:actors from benchmarks because corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)')
        del scala_dacapos['actors']

    for name, iterations in sorted(scala_dacapos.items()):
        with Task(prefix + 'ScalaDaCapo:' + name, tasks, tags=GraalTags.benchmarktest) as t:
            if t: _gate_scala_dacapo(name, iterations, _remove_empty_entries(extraVMarguments) +
                                     ['-XX:+UseJVMCICompiler', '-Dgraal.TrackNodeSourcePosition=true', '-esa', '-da:java.util.logging...'])

    # ensure -Xbatch still works
    with Task(prefix + 'DaCapo_pmd:BatchMode', tasks, tags=GraalTags.test) as t:
        if t: _gate_dacapo('pmd', 1, _remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xbatch'])

    # ensure benchmark counters still work
    if mx.get_arch() != 'aarch64': # GR-8364 Exclude benchmark counters on AArch64
        with Task(prefix + 'DaCapo_pmd:BenchmarkCounters', tasks, tags=GraalTags.test) as t:
            if t:
                fd, logFile = tempfile.mkstemp()
                os.close(fd) # Don't leak file descriptors
                try:
                    _gate_dacapo('pmd', 1, _remove_empty_entries(extraVMarguments) + ['-Dgraal.LogFile=' + logFile, '-XX:+UseJVMCICompiler', '-Dgraal.LIRProfileMoves=true', '-Dgraal.GenericDynamicCounters=true', '-Dgraal.TimedDynamicCounters=1000', '-XX:JVMCICounterSize=10'])
                    with open(logFile) as fp:
                        haystack = fp.read()
                        needle = 'MoveOperations (dynamic counters)'
                        if needle not in haystack:
                            mx.abort('Expected to see "' + needle + '" in output:\n' + haystack)
                finally:
                    os.remove(logFile)

    # ensure -Xcomp still works
    with Task(prefix + 'XCompMode:product', tasks, tags=GraalTags.test) as t:
        if t: run_vm(_remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xcomp', '-version'])

    # ensure -XX:+PreserveFramePointer  still works
    with Task(prefix + 'DaCapo_pmd:PreserveFramePointer', tasks, tags=GraalTags.test) as t:
        if t: _gate_dacapo('pmd', 4, _remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xmx256M', '-XX:+PreserveFramePointer'], threads=4, force_serial_gc=False, set_start_heap_size=False)

    if isJDK8:
        # temporarily isolate those test (GR-10990)
        cms = ['cms']
        # ensure CMS still works
        with Task(prefix + 'DaCapo_pmd:CMS', tasks, tags=cms) as t:
            if t: _gate_dacapo('pmd', 4, _remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xmx256M', '-XX:+UseConcMarkSweepGC'], threads=4, force_serial_gc=False, set_start_heap_size=False)

        # ensure CMSIncrementalMode still works
        with Task(prefix + 'DaCapo_pmd:CMSIncrementalMode', tasks, tags=cms) as t:
            if t: _gate_dacapo('pmd', 4, _remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xmx256M', '-XX:+UseConcMarkSweepGC', '-XX:+CMSIncrementalMode'], threads=4, force_serial_gc=False, set_start_heap_size=False)


        if prefix != '':
            # ensure G1 still works with libgraal
            with Task(prefix + 'DaCapo_pmd:G1', tasks, tags=cms) as t:
                if t: _gate_dacapo('pmd', 4, _remove_empty_entries(extraVMarguments) + ['-XX:+UseJVMCICompiler', '-Xmx256M', '-XX:+UseG1GC'], threads=4, force_serial_gc=False, set_start_heap_size=False)



graal_unit_test_runs = [
    UnitTestRun('UnitTests', [], tags=GraalTags.test + GraalTags.coverage),
]

_registers = {
    'sparcv9': 'o0,o1,o2,o3,f8,f9,d32,d34',
    'amd64': 'rbx,r11,r10,r14,xmm3,xmm11,xmm14',
    'aarch64': 'r0,r1,r2,r3,r4,v0,v1,v2,v3'
}
if mx.get_arch() not in _registers:
    mx.warn('No registers for register pressure tests are defined for architecture ' + mx.get_arch())

_defaultFlags = ['-Dgraal.CompilationWatchDogStartDelay=60.0D']
_assertionFlags = ['-esa', '-Dgraal.DetailedAsserts=true']
_graalErrorFlags = ['-Dgraal.CompilationFailureAction=ExitVM']
_graalEconomyFlags = ['-Dgraal.CompilerConfiguration=economy']
_verificationFlags = ['-Dgraal.VerifyGraalGraphs=true', '-Dgraal.VerifyGraalGraphEdges=true', '-Dgraal.VerifyGraalPhasesSize=true', '-Dgraal.VerifyPhases=true']
_coopFlags = ['-XX:-UseCompressedOops']
_gcVerificationFlags = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC']
_g1VerificationFlags = ['-XX:-UseSerialGC', '-XX:+UseG1GC']
_exceptionFlags = ['-Dgraal.StressInvokeWithExceptionNode=true']
_registerPressureFlags = ['-Dgraal.RegisterPressure=' + _registers[mx.get_arch()]]
_immutableCodeFlags = ['-Dgraal.ImmutableCode=true']

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertionsFullVerify', _defaultFlags + _assertionFlags + _verificationFlags + _graalErrorFlags, tags=GraalTags.bootstrapfullverify),
    BootstrapTest('BootstrapWithSystemAssertions', _defaultFlags + _assertionFlags + _graalErrorFlags, tags=GraalTags.bootstraplite),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', _defaultFlags + _assertionFlags + _coopFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithGCVerification', _defaultFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', _defaultFlags + _g1VerificationFlags + _gcVerificationFlags + _graalErrorFlags, tags=GraalTags.bootstrap, suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithSystemAssertionsEconomy', _defaultFlags + _assertionFlags + _graalEconomyFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithSystemAssertionsExceptionEdges', _defaultFlags + _assertionFlags + _exceptionFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithSystemAssertionsRegisterPressure', _defaultFlags + _assertionFlags + _registerPressureFlags + _graalErrorFlags, tags=GraalTags.bootstrap),
    BootstrapTest('BootstrapWithSystemAssertionsImmutableCode', _defaultFlags + _assertionFlags + _immutableCodeFlags + ['-Dgraal.VerifyPhases=true'] + _graalErrorFlags, tags=GraalTags.bootstrap)
]

def _is_jaotc_supported():
    return exists(jdk.exe_path('jaotc'))

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['compiler', 'truffle'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)
    compiler_gate_benchmark_runner(tasks, args.extra_vm_argument)
    jvmci_ci_version_gate_runner(tasks)
    if _is_jaotc_supported():
        mx_jaotc.jaotc_gate_runner(tasks)

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

            # Export packages in all Graal modules and their dependencies
            for dist in _graal_config().dists:
                jmd = as_java_module(dist, jdk)
                if _graaljdk_override is None or jmd in _graaljdk_override.get_modules():
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
    if not any(a.startswith('-Dgraal.CompilationFailureAction=') for a in args):
        argsPrefix.append('-Dgraal.CompilationFailureAction=Diagnose')

    # It is safe to assume that Network dumping is the desired default when using mx.
    # Mx is never used in production environments.
    if not any(a.startswith('-Dgraal.PrintGraph=') for a in args):
        argsPrefix.append('-Dgraal.PrintGraph=Network')

    # Likewise, one can assume that objdump is safe to access when using mx.
    if not any(a.startswith('-Dgraal.ObjdumpExecutables=') for a in args):
        argsPrefix.append('-Dgraal.ObjdumpExecutables=objdump,gobjdump')

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
        if self.mapFiles and self.capture:
            try:
                with tempfile.NamedTemporaryFile(mode='w') as inputFile:
                    data = self.capture.data
                    if len(data) != 0:
                        inputFile.write(data)
                        inputFile.flush()
                        retraceOut = mx.OutputCapture()
                        unstrip_args = [m for m in set(self.mapFiles)] + [inputFile.name]
                        mx.unstrip(unstrip_args, out=retraceOut)
                        if data != retraceOut.data:
                            mx.log('>>>> BEGIN UNSTRIPPED OUTPUT')
                            mx.log(retraceOut.data)
                            mx.log('<<<< END UNSTRIPPED OUTPUT')
            except BaseException as e:
                mx.log('Error unstripping output from VM execution with stripped jars: ' + str(e))

_graaljdk_override = None

def get_graaljdk():
    if _graaljdk_override is None:
        graaljdk_dir, _ = _update_graaljdk(jdk)
        graaljdk = mx.JDKConfig(graaljdk_dir)
    else:
        graaljdk = _graaljdk_override
    return graaljdk

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

def run_java(args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True):
    graaljdk = get_graaljdk()
    vm_args = _parseVmArgs(args, addDefaultArgs=addDefaultArgs)
    args = ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'] + vm_args
    add_exports = join(graaljdk.home, '.add_exports')
    if exists(add_exports):
        args = ['@' + add_exports] + args
    _check_bootstrap_config(args)
    cmd = get_vm_prefix() + [graaljdk.java] + ['-server'] + args
    map_file = join(graaljdk.home, 'proguard.map')

    with StdoutUnstripping(args, out, err, mapFiles=[map_file]) as u:
        try:
            return mx.run(cmd, nonZeroIsFatal=nonZeroIsFatal, out=u.out, err=u.err, cwd=cwd, env=env)
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

class GraalArchiveParticipant:
    providersRE = re.compile(r'(?:META-INF/versions/([1-9][0-9]*)/)?META-INF/providers/(.+)')

    def __init__(self, dist, isTest=False):
        self.dist = dist
        self.isTest = isTest

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __add__(self, arcname, contents): # pylint: disable=unexpected-special-method-signature
        m = GraalArchiveParticipant.providersRE.match(arcname)
        if m:
            if self.isTest:
                # The test distributions must not have their @ServiceProvider
                # generated providers converted to real services otherwise
                # bad things can happen such as InvocationPlugins being registered twice.
                pass
            else:
                provider = m.group(2)
                for service in _decode(contents).strip().split(os.linesep):
                    assert service
                    version = m.group(1)
                    if version is None:
                        # Non-versioned service
                        self.services.setdefault(service, []).append(provider)
                    else:
                        # Versioned service
                        services = self.services.setdefault(int(version), {})
                        services.setdefault(service, []).append(provider)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            if self.isTest:
                mx.warn('@Option defined in test code will be ignored: ' + arcname)
            else:
                # Need to create service files for the providers of the
                # jdk.internal.vm.ci.options.Options service created by
                # jdk.internal.vm.ci.options.processor.OptionProcessor.
                provider = arcname[:-len('.class'):].replace('/', '.')
                self.services.setdefault('org.graalvm.compiler.options.OptionDescriptors', []).append(provider)
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        _record_last_updated_jar(self.dist, self.arc.path)

mx.add_argument('--vmprefix', action='store', dest='vm_prefix', help='prefix for running the VM (e.g. "gdb --args")', metavar='<prefix>')
mx.add_argument('--gdb', action='store_const', const='gdb --args', dest='vm_prefix', help='alias for --vmprefix "gdb --args"')
mx.add_argument('--lldb', action='store_const', const='lldb --', dest='vm_prefix', help='alias for --vmprefix "lldb --"')

def sl(args):
    """run an SL program"""
    mx.get_opts().jdk = 'jvmci'
    mx_truffle.sl(args)

def java_base_unittest(args):
    """tests whether the Graal compiler runs on a JDK with a minimal set of modules"""

    global _graaljdk_override
    try:
        # Remove GRAAL_MANAGEMENT from the module path as it
        # depends on the java.management module which is not in
        # the limited module set
        base_modules = ['java.base', 'java.logging', 'jdk.internal.vm.ci', 'jdk.unsupported', 'jdk.compiler']
        compiler_modules = [as_java_module(d, jdk).name for d in _graal_config().dists if d.name != 'GRAAL_MANAGEMENT']
        root_module_names = base_modules + compiler_modules
        graaljdk_dir, _ = _update_graaljdk(jdk, root_module_names=root_module_names)
        _graaljdk_override = mx.JDKConfig(graaljdk_dir)

        if mx_gate.Task.verbose:
            extra_args = ['--verbose', '--enable-timing']
        else:
            extra_args = []
        # the base JDK doesn't include jdwp
        if _graaljdk_override.debug_args:
            mx.warn('Ignoring Java debugger arguments because base JDK doesn\'t include jdwp')
        with mx.DisableJavaDebugging():
            mx_unittest.unittest(['--suite', 'compiler', '--fail-fast'] + extra_args + args)
    finally:
        _graaljdk_override = None

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
    parser = ArgumentParser(prog='mx makegraaljdk')
    parser.add_argument('-f', '--force', action='store_true', help='overwrite existing GraalJDK')
    parser.add_argument('-a', '--archive', action='store', help='name of archive to create', metavar='<path>')
    parser.add_argument('-b', '--bootstrap', action='store_true', help='execute a bootstrap of the created GraalJDK')
    parser.add_argument('-l', '--license', action='store', help='path to the license file', metavar='<path>')
    parser.add_argument('-o', '--overlay', action='store_true', help='Only write the Graal files into the destination')
    parser.add_argument('dest', help='destination directory for GraalJDK', metavar='<path>')
    args = parser.parse_args(args)

    if args.overlay and not isJDK8:
        mx.abort('The --overlay option is only supported on JDK 8')

    dst_jdk_dir = os.path.abspath(args.dest)
    if exists(dst_jdk_dir):
        if args.force:
            shutil.rmtree(dst_jdk_dir)

    _, updated = _update_graaljdk(jdk, dst_jdk_dir, export_truffle=False, with_compiler_name_file=True)
    dst_jdk = mx.JDKConfig(dst_jdk_dir)
    if not updated:
        mx.log(dst_jdk_dir + ' is already up to date')

    if args.license:
        shutil.copy(args.license, join(dst_jdk_dir, 'LICENSE'))
    if args.bootstrap:
        map_file = join(dst_jdk_dir, 'proguard.map')
        with StdoutUnstripping(args=[], out=None, err=None, mapFiles=[map_file]) as u:
            # Just use a set of flags that will work on all JVMCI enabled VMs without trying
            # to remove flags that are unnecessary for a specific VM.
            mx.run([dst_jdk.java, '-XX:+UnlockExperimentalVMOptions', '-XX:+UseJVMCICompiler', '-XX:+BootstrapJVMCI', '-version'], out=u.out, err=u.err)
    if args.archive:
        mx.log('Archiving {}'.format(args.archive))
        create_archive(dst_jdk_dir, args.archive, basename(args.dest) + '/')

def _update_graaljdk(src_jdk, dst_jdk_dir=None, root_module_names=None, export_truffle=True, with_compiler_name_file=False):
    """
    Creates or updates a GraalJDK in `dst_jdk_dir` from `src_jdk`.

    :param str dst_jdk_dir: path where GraalJDK is (to be) located. If None, then a path name is
                            derived based on _graalvm_components and `root_module_names`.
    :param list root_module_names: names of modules in the root set for the new JDK image. If None,
                            the root set is derived from _graalvm_components.
    :param bool export_truffle: specifies if Truffle API packages should be visible to the app class loader.
                            On JDK 8, this causes Truffle to be on the boot class path. On JDK 9+, this results
                            in a ``.add_exports`` file in `dst_dst_dir` which can be used as an @argfile VM argument.
    :param bool with_compiler_name_file: if True, a ``compiler-name`` file is written in the ``jvmci`` directory under
                            `dst_jdk_dir`. Depending on `src_jdk`, the existence of this file can set the
                            value of UseJVMCICompiler be true. For example, see
                            https://github.com/graalvm/graal-jvmci-8/blob/master/src/share/vm/jvmci/jvmci_globals.hpp#L52
    :return: a tuple containing the path where the GraalJDK is located and a boolean denoting whether
                            the GraalJDK was update/created (True) or was already up to date (False)

    """
    update_reason = None
    if dst_jdk_dir is None:
        graaljdks_dir = mx.ensure_dir_exists(join(_suite.get_output_root(platformDependent=True), 'graaljdks'))
        graalvm_compiler_short_names = [c.short_name for c in mx_sdk_vm.graalvm_components() if isinstance(c, mx_sdk_vm.GraalVmJvmciComponent) and c.graal_compiler]
        jdk_suffix = '-'.join(graalvm_compiler_short_names)
        if root_module_names:
            jdk_suffix = jdk_suffix + '-' + hashlib.sha1(_encode(','.join(root_module_names))).hexdigest()
        dst_jdk_dir = join(graaljdks_dir, 'jdk{}-{}'.format(src_jdk.javaCompliance, jdk_suffix))
        if dst_jdk_dir == src_jdk.home:
            # Avoid overwriting source JDK
            dst_jdk_dir = dst_jdk_dir + '_new'
    else:
        if dst_jdk_dir == src_jdk.home:
            mx.abort("Cannot overwrite source JDK: {}".format(src_jdk))

    # When co-developing JVMCI/JDK changes with Graal, the source JDK
    # may have changed and we want to pick up these changes.
    source_jdk_timestamps_file = dst_jdk_dir + '.source_jdk_timestamps'
    timestamps = []
    nl = '\n'
    for root, _, filenames in os.walk(jdk.home):
        for name in filenames:
            ts = mx.TimeStampFile(join(root, name))
            timestamps.append(str(ts))
    timestamps = sorted(timestamps)
    jdk_timestamps = jdk.home + nl + nl.join(timestamps)
    jdk_timestamps_outdated = False
    if exists(source_jdk_timestamps_file):
        with open(source_jdk_timestamps_file) as fp:
            old_jdk_timestamps = fp.read()
        if old_jdk_timestamps != jdk_timestamps:
            jdk_timestamps_outdated = True
            old_jdk_home = old_jdk_timestamps.split(nl, 1)[0]
            if old_jdk_home == jdk.home:
                import difflib
                old_timestamps = old_jdk_timestamps.split(nl)
                diff = difflib.unified_diff(timestamps, old_timestamps, 'new_timestamps.txt', 'old_timestamps.txt')
                update_reason = 'source JDK was updated as shown by following time stamps diff:{}{}'.format(nl, nl.join(diff))
            else:
                update_reason = 'source JDK was changed from {} to {}'.format(old_jdk_home, jdk.home)
    else:
        jdk_timestamps_outdated = True

    if jdk_timestamps_outdated:
        with mx.SafeFileCreation(source_jdk_timestamps_file) as sfc:
            with open(sfc.tmpPath, 'w') as fp:
                fp.write(jdk_timestamps)

    jvmci_release_file = mx.TimeStampFile(join(dst_jdk_dir, 'release.jvmci'))
    if update_reason is None:
        if not exists(dst_jdk_dir):
            update_reason = dst_jdk_dir + ' does not exist'
        else:
            newer = [e for e in _graal_config().jars if jvmci_release_file.isOlderThan(e)]
            if newer:
                update_reason = '{} is older than {}'.format(jvmci_release_file, mx.TimeStampFile(newer[0]))

    if update_reason is None:
        return dst_jdk_dir, False

    with SafeDirectoryUpdater(dst_jdk_dir) as sdu:
        tmp_dst_jdk_dir = sdu.directory
        mx.log('Updating/creating {} from {} using intermediate directory {} since {}'.format(dst_jdk_dir, src_jdk.home, tmp_dst_jdk_dir, update_reason))
        def _copy_file(src, dst):
            mx.log('Copying {} to {}'.format(src, dst))
            shutil.copyfile(src, dst)

        vm_name = 'Server VM Graal'
        for d in _graal_config().jvmci_dists:
            s = ':' + d.suite.name + '_' + d.suite.version()
            if s not in vm_name:
                vm_name = vm_name + s

        if isJDK8:
            jre_dir = join(tmp_dst_jdk_dir, 'jre')
            shutil.copytree(src_jdk.home, tmp_dst_jdk_dir)

            boot_dir = mx.ensure_dir_exists(join(jre_dir, 'lib', 'boot'))
            jvmci_dir = mx.ensure_dir_exists(join(jre_dir, 'lib', 'jvmci'))

            for src_jar in _graal_config().jvmci_jars:
                _copy_file(src_jar, join(jvmci_dir, basename(src_jar)))

            boot_jars = _graal_config().boot_jars
            if not export_truffle:
                truffle_dir = mx.ensure_dir_exists(join(jre_dir, 'lib', 'truffle'))
                for src_jar in _graal_config().truffle_jars:
                    _copy_file(src_jar, join(truffle_dir, basename(src_jar)))
                for jvmci_parent_jar in _graal_config().jvmci_parent_jars:
                    with open(join(jvmci_dir, 'parentClassLoader.classpath'), 'w') as fp:
                        fp.write(join('..', 'truffle', basename(jvmci_parent_jar)))
            else:
                boot_jars += _graal_config().jvmci_parent_jars

            for src_jar in boot_jars:
                _copy_file(src_jar, join(boot_dir, basename(src_jar)))

        else:
            module_dists = _graal_config().dists
            _check_using_latest_jars(module_dists)
            vendor_info = {'vendor-version' : vm_name}
            # Setting dedup_legal_notices=False avoids due to license files conflicting
            # when switching JAVA_HOME from an OpenJDK to an OracleJDK or vice versa between executions.
            jlink_new_jdk(jdk, tmp_dst_jdk_dir, module_dists, root_module_names=root_module_names, vendor_info=vendor_info, dedup_legal_notices=False)
            jre_dir = tmp_dst_jdk_dir
            jvmci_dir = mx.ensure_dir_exists(join(jre_dir, 'lib', 'jvmci'))
            if export_truffle:
                jmd = as_java_module(_graal_config().dists_dict['truffle:TRUFFLE_API'], jdk)
                add_exports = []
                for package in jmd.packages:
                    if package == 'com.oracle.truffle.api.impl':
                        # The impl package should remain private
                        continue
                    if jmd.get_package_visibility(package, "<unnamed>") == 'concealed':
                        add_exports.append('--add-exports={}/{}=ALL-UNNAMED'.format(jmd.name, package))
                if add_exports:
                    with open(join(tmp_dst_jdk_dir, '.add_exports'), 'w') as fp:
                        fp.write(os.linesep.join(add_exports))

        if with_compiler_name_file:
            with open(join(jvmci_dir, 'compiler-name'), 'w') as fp:
                print('graal', file=fp)

        if jdk.javaCompliance < '9' and mx.get_os() not in ['darwin', 'windows']:
            # On JDK 8, the server directory containing the JVM library is
            # in an architecture specific directory (except for Darwin and Windows).
            libjvm_dir = join(jre_dir, 'lib', mx.get_arch(), 'server')
        elif mx.get_os() == 'windows':
            libjvm_dir = join(jre_dir, 'bin', 'server')
        else:
            libjvm_dir = join(jre_dir, 'lib', 'server')
        mx.ensure_dir_exists(libjvm_dir)
        jvmlib = join(libjvm_dir, mx.add_lib_prefix(mx.add_lib_suffix('jvm')))

        with open(join(tmp_dst_jdk_dir, 'release.jvmci'), 'w') as fp:
            for d in _graal_config().jvmci_dists:
                s = d.suite
                print('{}={}'.format(d.name, s.vc.parent(s.dir)), file=fp)
            for d in _graal_config().boot_dists + _graal_config().truffle_dists:
                s = d.suite
                print('{}={}'.format(d.name, s.vc.parent(s.dir)), file=fp)

        assert exists(jvmlib), jvmlib + ' does not exist'
        out = mx.LinesOutputCapture()
        mx.run([jdk.java, '-version'], err=out)
        line = None
        pattern = re.compile(r'(.* )(?:Server|Graal) VM (?:\d+\.\d+ |[a-zA-Z]+ )?\((?:[a-zA-Z]+ )?build.*')
        for line in out.lines:
            m = pattern.match(line)
            if m:
                with io.open(join(libjvm_dir, 'vm.properties'), 'w', newline='') as fp:
                    # Modify VM name in `java -version` to be Graal along
                    # with a suffix denoting the commit of each Graal jar.
                    # For example:
                    # Java HotSpot(TM) 64-Bit Graal:compiler_88847fb25d1a62977a178331a5e78fa5f8fcbb1a (build 25.71-b01-internal-jvmci-0.34, mixed mode)
                    print(u'name=' + m.group(1) + vm_name, file=fp)
                line = True
                break
        if line is not True:
            mx.abort('Could not find "{}" in output of `java -version`:\n{}'.format(pattern.pattern, os.linesep.join(out.lines)))

        unstrip_map = mx.make_unstrip_map(_graal_config().dists)
        if unstrip_map:
            with open(join(tmp_dst_jdk_dir, 'proguard.map'), 'w') as fp:
                fp.write(unstrip_map)

    return dst_jdk_dir, True

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
    ] + (['compiler:JAOTC'] if not isJDK8 and _is_jaotc_supported() else [])

# The community compiler component
mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJvmciComponent(
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
))

mx.update_commands(_suite, {
    'sl' : [sl, '[SL args|@VM options]'],
    'vm': [run_vm, '[-options] class [args...]'],
    'jaotc': [mx_jaotc.run_jaotc, '[-options] class [args...]'],
    'jaotc-test': [mx_jaotc.jaotc_test, ''],
    'collate-metrics': [collate_metrics, 'filename'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'nodecostdump' : [_nodeCostDump, ''],
    'verify_jvmci_ci_versions': [verify_jvmci_ci_versions, ''],
    'java_base_unittest' : [java_base_unittest, 'Runs unittest on JDK java.base "only" module(s)'],
    'updategraalinopenjdk' : [updategraalinopenjdk, '[options]'],
    'renamegraalpackages' : [renamegraalpackages, '[options]'],
    'javadoc': [javadoc, ''],
    'makegraaljdk': [makegraaljdk_cli, '[options]'],
})

def mx_post_parse_cmd_line(opts):
    mx.addJDKFactory(_JVMCI_JDK_TAG, jdk.javaCompliance, GraalJDKFactory())
    mx.add_ide_envvar('JVMCI_VERSION_CHECK')
    for dist in _suite.dists:
        dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))
    global _vm_prefix
    _vm_prefix = opts.vm_prefix
