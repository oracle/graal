#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import os
from os.path import join, exists
from argparse import ArgumentParser
import sanitycheck
import re
import hashlib

import mx
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

from mx_unittest import unittest
from mx_graal_bench import dacapo
import mx_gate
import mx_unittest
import mx_microbench

_suite = mx.suite('graal-core')

_jdk = mx.get_jdk(tag='default')
assert _jdk.javaCompliance >= "1.9"

def isJVMCIEnabled(vm):
    return True

_jvmciModes = {
    'hosted' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'],
    'jit' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '-XX:+UseJVMCICompiler'],
    'disabled' : []
}

def get_vm():
    """
    Gets the name of the currently selected JVM variant.
    """
    return 'server'

class JVMCIMode:
    """
    A context manager for setting the current JVMCI mode.
    """
    def __init__(self, jvmciMode=None):
        self.update(jvmciMode)

    def update(self, jvmciMode=None):
        assert jvmciMode is None or jvmciMode in _jvmciModes, jvmciMode
        self.jvmciMode = jvmciMode or _vm.jvmciMode

    def __enter__(self):
        global _vm
        self.previousVm = _vm
        _vm = self

    def __exit__(self, exc_type, exc_value, traceback):
        global _vm
        _vm = self.previousVm

_vm = JVMCIMode(jvmciMode='hosted')

class BootClasspathDist(object):
    """
    Extra info for a Distribution that must be put onto the boot class path.
    """
    def __init__(self, name):
        self._name = name

    def dist(self):
        return mx.distribution(self._name)

    def get_classpath_repr(self):
        return self.dist().classpath_repr()

_compilers = ['graal-economy', 'graal']
_bootClasspathDists = [
    BootClasspathDist('GRAAL'),
]

def add_compiler(compilerName):
    _compilers.append(compilerName)

def add_boot_classpath_dist(dist):
    _bootClasspathDists.append(dist)

mx_gate.add_jacoco_includes(['com.oracle.graal.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])


class JVMCI9MicrobenchExecutor(mx_microbench.MicrobenchExecutor):

    def parseForkedVmArgs(self, vmArgs):
        jvm = get_vm()
        return ['-' + jvm] + _parseVmArgs(_jdk, vmArgs)

    def run_java(self, args):
        run_vm(args)

mx_microbench.set_microbenchmark_executor(JVMCI9MicrobenchExecutor())


def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        vmargs.append('-G:CompileTheWorldConfig=' + re.sub(r'\s+', '#', args.ctwopts))

    if args.cp:
        cp = os.path.abspath(args.cp)
    else:
        cp = join(_jdk.home, 'lib', 'modules', 'bootmodules.jimage')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    if _vm.jvmciMode == 'disabled':
        vmargs += ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + cp]
    else:
        if _vm.jvmciMode == 'jit':
            vmargs += ['-XX:+BootstrapJVMCI']
        vmargs += ['-G:CompileTheWorldClasspath=' + cp, 'com.oracle.graal.hotspot.CompileTheWorld']

    run_vm(vmargs + _noneAsEmptyList(extraVMarguments))

class UnitTestRun:
    def __init__(self, name, args, tags):
        self.name = name
        self.args = args
        self.tags = tags

    def run(self, suites, tasks, extraVMarguments=None):
        for suite in suites:
            with Task(self.name + ': hosted-product ' + suite, tasks, tags=self.tags) as t:
                if mx_gate.Task.verbose:
                    extra_args = ['--verbose', '--enable-timing']
                else:
                    extra_args = []
                if t: unittest(['--suite', suite, '--fail-fast'] + extra_args + self.args + _noneAsEmptyList(extraVMarguments))

class BootstrapTest:
    def __init__(self, name, vmbuild, args, tags, suppress=None):
        self.name = name
        self.args = args
        self.suppress = suppress
        self.tags = tags

    def run(self, tasks, extraVMarguments=None):
        with JVMCIMode('jit'):
            with Task(self.name, tasks, tags=self.tags) as t:
                if t:
                    if self.suppress:
                        out = mx.DuplicateSuppressingStream(self.suppress).write
                    else:
                        out = None
                    run_vm(self.args + _noneAsEmptyList(extraVMarguments) + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'], out=out)

class MicrobenchRun:
    def __init__(self, name, args, tags):
        self.name = name
        self.args = args
        self.tags = tags

    def run(self, tasks, extraVMarguments=None):
        with Task(self.name + ': hosted-product ', tasks, tags=self.tags) as t:
            if t: mx_microbench.get_microbenchmark_executor().microbench(_noneAsEmptyList(extraVMarguments) + ['--', '-foe', 'true'] + self.args)

class GraalTags:
    test = 'test'
    bootstrap = 'bootstrap'
    fulltest = 'fulltest'

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):

    # Run unit tests in hosted mode
    with JVMCIMode('hosted'):
        for r in unit_test_runs:
            r.run(suites, tasks, extraVMarguments)

    # Run microbench in hosted mode (only for testing the JMH setup)
    with JVMCIMode('hosted'):
        for r in [MicrobenchRun('Microbench', ['TestJMH'], tags=[GraalTags.fulltest])]:
            r.run(tasks, extraVMarguments)

    # Run ctw against rt.jar on server-hosted-jvmci
    with JVMCIMode('hosted'):
        with Task('CTW:hosted', tasks, tags=[GraalTags.fulltest]) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose', '-XX:ReservedCodeCacheSize=300m'], _noneAsEmptyList(extraVMarguments))

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    # run dacapo sanitychecks
    for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments) \
            + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments):
        with Task(str(test) + ':' + 'release', tasks, tags=[GraalTags.fulltest]) as t:
            if t and not test.test('jvmci'):
                t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with JVMCIMode('jit'):
        with Task('DaCapo_pmd:BatchMode', tasks, tags=[GraalTags.fulltest]) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-Xbatch', 'pmd'])

    # ensure benchmark counters still work
    with JVMCIMode('jit'):
        with Task('DaCapo_pmd:BenchmarkCounters:product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-G:+LIRProfileMoves', '-G:+GenericDynamicCounters', '-XX:JVMCICounterSize=10', 'pmd'])

    # ensure -Xcomp still works
    with JVMCIMode('jit'):
        with Task('XCompMode:product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: run_vm(_noneAsEmptyList(extraVMarguments) + ['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', [], tags=[GraalTags.test]),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if mx.get_arch() == 'sparcv9' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', 'fastdebug', ['-esa', '-G:+VerifyGraalGraphs', '-G:+VerifyGraalGraphEdges'], tags=[GraalTags.bootstrap]),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', 'fastdebug', ['-esa', '-XX:-UseCompressedOops', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithGCVerification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', 'fastdebug', ['-esa', '-Djvmci.Compiler=graal-economy', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithExceptionEdges', 'fastdebug', ['-esa', '-G:+StressInvokeWithExceptionNode', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithRegisterPressure', 'product', ['-esa', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapTraceRAWithRegisterPressure', 'product', ['-esa', '-G:+TraceRA', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithImmutableCode', 'product', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
]


def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal-core', 'truffle'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action='append', help='add extra vm argument to gate tasks if applicable (multiple occurrences allowed)')

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    run_vm(vmArgs + [mainClass] + mainClassArgs)

mx_unittest.set_vm_launcher('JDK9 VM launcher', _unittest_vm_launcher)

def _parseVmArgs(jdk, args, addDefaultArgs=True):
    args = mx.expand_project_in_args(args, insitu=False)
    jacocoArgs = mx_gate.get_jacoco_agent_args()
    if jacocoArgs:
        args = jacocoArgs + args

    # Support for -G: options
    def translateGOption(arg):
        if arg.startswith('-G:+'):
            if '=' in arg:
                mx.abort('Mixing + and = in -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:+'):] + '=true'
        elif arg.startswith('-G:-'):
            if '=' in arg:
                mx.abort('Mixing - and = in -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:+'):] + '=false'
        elif arg.startswith('-G:'):
            if '=' not in arg:
                mx.abort('Missing "=" in non-boolean -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:'):]
        return arg
    # add default graal.options.file and translate -G: options
    options_file = join(mx.primary_suite().dir, 'graal.options')
    options_file_arg = ['-Dgraal.options.file=' + options_file] if exists(options_file) else []
    args = options_file_arg + map(translateGOption, args)

    if '-G:+PrintFlags' in args and '-Xcomp' not in args:
        mx.warn('Using -G:+PrintFlags may have no effect without -Xcomp as Graal initialization is lazy')

    bcp = [mx.distribution('truffle:TRUFFLE_API').classpath_repr()]
    if _jvmciModes[_vm.jvmciMode]:
        bcp.extend([d.get_classpath_repr() for d in _bootClasspathDists])

    # Create com.oracle.graal module
    graalModuleName = 'com.oracle.graal'
    modulesDir = mx.ensure_dir_exists(join(_suite.get_output_root(), 'modules'))
    graalModuleDir = mx.ensure_dir_exists(join(modulesDir, graalModuleName))
    graalModuleJar = join(modulesDir, graalModuleName + '.jar')
    if not exists(graalModuleJar):
        moduleSource = join(graalModuleDir, 'module-info.java')
        with open(moduleSource, 'w') as fp:
            fp.write('module ' + graalModuleName + ' { requires jdk.vm.ci; }')
        with open('source', 'w') as fp:
            print >> fp, os.pathsep.join(bcp)
        mx.run([_jdk.javac, '-d', graalModuleDir, moduleSource])
        with open(join(graalModuleDir, 'module-info.class'), 'rb') as fp:
            graalModuleClass = fp.read()
        with mx.Archiver(graalModuleJar) as arc:
            arc.zf.writestr('module-info.class', graalModuleClass)

#    args = ['-XaddExports:jdk.vm.ci/com.oracle.graal.replacements=ALL-UNNAMED', '-Xpatch:jdk.vm.ci=' + os.pathsep.join(bcp)] + args
    args = ['-mp' , graalModuleJar, '-Xpatch:' + graalModuleName + '=' + os.pathsep.join(bcp), '-XaddExports:java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED', '-XaddExports:jdk.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED'] + args

    # Set the default JVMCI compiler
    jvmciCompiler = _compilers[-1]
    args = ['-Djvmci.Compiler=' + jvmciCompiler] + args

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if  len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the vm because they come after the '-version' argument: " + ' '.join(ignoredArgs))
    return jdk.processArgs(args, addDefaultArgs=addDefaultArgs)

def run_java(jdk, args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True):

    args = _parseVmArgs(jdk, args, addDefaultArgs=addDefaultArgs)

    jvmciModeArgs = _jvmciModes[_vm.jvmciMode]
    cmd = [jdk.java] + ['-' + get_vm()] + jvmciModeArgs + args
    return mx.run(cmd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd)

_JVMCI_JDK_TAG = 'jvmci'

class GraalJVMCI9JDKConfig(mx.JDKConfig):
    def __init__(self, original):
        self._original = original
        mx.JDKConfig.__init__(self, original.home, tag=_JVMCI_JDK_TAG)

    def run_java(self, args, **kwArgs):
        run_java(self._original, args, **kwArgs)

class GraalJDKFactory(mx.JDKFactory):
    def getJDKConfig(self):
        return GraalJVMCI9JDKConfig(_jdk)

    def description(self):
        return "JVMCI JDK with Graal"

# This will override the 'generic' JVMCI JDK with a Graal JVMCI JDK that has
# support for -G style Graal options.
mx.addJDKFactory(_JVMCI_JDK_TAG, mx.JavaCompliance('9'), GraalJDKFactory())

def run_vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK"""

    return run_java(_jdk, args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

class GraalArchiveParticipant:
    def __init__(self, dist):
        self.dist = dist

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __add__(self, arcname, contents):
        if arcname.startswith('META-INF/providers/'):
            provider = arcname[len('META-INF/providers/'):]
            for service in contents.strip().split(os.linesep):
                assert service
                self.services.setdefault(service, []).append(provider)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            # Need to create service files for the providers of the
            # jdk.vm.ci.options.Options service created by
            # jdk.vm.ci.options.processor.OptionProcessor.
            provider = arcname[:-len('.class'):].replace('/', '.')
            self.services.setdefault('com.oracle.graal.options.OptionDescriptors', []).append(provider)
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        pass

mx.update_commands(_suite, {
    'vm': [run_vm, '[-options] class [args...]'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
})

mx.add_argument('-M', '--jvmci-mode', action='store', choices=sorted(_jvmciModes.viewkeys()), help='the JVM variant type to build/run (default: ' + _vm.jvmciMode + ')')

def mx_post_parse_cmd_line(opts):
    if opts.jvmci_mode is not None:
        _vm.update(opts.jvmci_mode)
    for dist in [d.dist() for d in _bootClasspathDists]:
        dist.set_archiveparticipant(GraalArchiveParticipant(dist))
