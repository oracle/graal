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
from os.path import join
from argparse import ArgumentParser
import sanitycheck
import itertools
import json
import re

import mx
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

from mx_unittest import unittest
import mx_gate
import mx_unittest

_suite = mx.suite('graal')

_jdk = mx.get_jdk()
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

_compilers = ['graal-economy', 'graal']
_graalDists = [
    'GRAAL_NODEINFO',
    'GRAAL_API',
    'GRAAL_COMPILER',
    'GRAAL',
    'GRAAL_HOTSPOT',
    'GRAAL_TRUFFLE',
    'GRAAL_TRUFFLE_HOTSPOT',
]

def add_compiler(compilerName):
    _compilers.append(compilerName)

def add_graal_dist(distName):
    _graalDists.append(distName)

mx_gate.add_jacoco_includes(['com.oracle.graal.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

def _run_benchmark(args, availableBenchmarks, runBenchmark):

    vmOpts, benchmarksAndOptions = mx.extract_VM_args(args, useDoubleDash=availableBenchmarks is None)

    if availableBenchmarks is None:
        harnessArgs = benchmarksAndOptions
        return runBenchmark(None, harnessArgs, vmOpts)

    if len(benchmarksAndOptions) == 0:
        mx.abort('at least one benchmark name or "all" must be specified')
    benchmarks = list(itertools.takewhile(lambda x: not x.startswith('-'), benchmarksAndOptions))
    harnessArgs = benchmarksAndOptions[len(benchmarks):]

    if 'all' in benchmarks:
        benchmarks = availableBenchmarks
    else:
        for bm in benchmarks:
            if bm not in availableBenchmarks:
                mx.abort('unknown benchmark: ' + bm + '\nselect one of: ' + str(availableBenchmarks))

    failed = []
    for bm in benchmarks:
        if not runBenchmark(bm, harnessArgs, vmOpts):
            failed.append(bm)

    if len(failed) != 0:
        mx.abort('Benchmark failures: ' + str(failed))

def dacapo(args):
    """run one or more DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getDacapo(bm, harnessArgs).test(get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoSanityWarmup.keys(), launcher)

def scaladacapo(args):
    """run one or more Scala DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getScalaDacapo(bm, harnessArgs).test(get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoScalaSanityWarmup.keys(), launcher)

# This is different than the 'jmh' commmand in that it
# looks for internal JMH benchmarks (i.e. those that
# depend on the JMH library).
def microbench(args):
    """run JMH microbenchmark projects"""
    parser = ArgumentParser(prog='mx microbench', description=microbench.__doc__,
                            usage="%(prog)s [command options|VM options] [-- [JMH options]]")
    parser.add_argument('--jar', help='Explicitly specify micro-benchmark location')
    known_args, args = parser.parse_known_args(args)

    vmArgs, jmhArgs = mx.extract_VM_args(args, useDoubleDash=True)

    # look for -f in JMH arguments
    containsF = False
    forking = True
    for i in range(len(jmhArgs)):
        arg = jmhArgs[i]
        if arg.startswith('-f'):
            containsF = True
            if arg == '-f' and (i+1) < len(jmhArgs):
                arg += jmhArgs[i+1]
            try:
                if int(arg[2:]) == 0:
                    forking = False
            except ValueError:
                pass

    if known_args.jar:
        # use the specified jar
        args = ['-jar', known_args.jar]
        if not forking:
            args += vmArgs
    else:
        # default to -f1 if not specified otherwise
        if not containsF:
            jmhArgs += ['-f1']

        # find all projects with a direct JMH dependency
        jmhProjects = []
        for p in mx.projects_opt_limit_to_suites():
            if 'JMH' in [x.name for x in p.deps]:
                jmhProjects.append(p.name)
        cp = mx.classpath(jmhProjects)

        # execute JMH runner
        args = ['-cp', cp]
        if not forking:
            args += vmArgs
        args += ['org.openjdk.jmh.Main']

    if forking:
        jvm = get_vm()
        def quoteSpace(s):
            if " " in s:
                return '"' + s + '"'
            return s

        forkedVmArgs = map(quoteSpace, _parseVmArgs(_jdk, vmArgs))
        args += ['--jvmArgsPrepend', ' '.join(['-' + jvm] + forkedVmArgs)]
    run_vm(args + jmhArgs)

def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        # when they are collated in the "jvmci.options" system property
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
    def __init__(self, name, args):
        self.name = name
        self.args = args

    def run(self, suites, tasks, extraVMarguments=None):
        for suite in suites:
            with Task(self.name + ': hosted-product ' + suite, tasks) as t:
                if t: unittest(['--suite', suite, '--enable-timing', '--verbose', '--fail-fast'] + self.args + _noneAsEmptyList(extraVMarguments))

class BootstrapTest:
    def __init__(self, name, vmbuild, args, suppress=None):
        self.name = name
        self.args = args
        self.suppress = suppress

    def run(self, tasks, extraVMarguments=None):
        with JVMCIMode('jit'):
            with Task(self.name, tasks) as t:
                if t:
                    if self.suppress:
                        out = mx.DuplicateSuppressingStream(self.suppress).write
                    else:
                        out = None
                    run_vm(self.args + _noneAsEmptyList(extraVMarguments) + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'], out=out)

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):

    # Run unit tests in hosted mode
    with JVMCIMode('hosted'):
        for r in unit_test_runs:
            r.run(suites, tasks, extraVMarguments)

    # Run ctw against rt.jar on server-hosted-jvmci
    with JVMCIMode('hosted'):
        with Task('CTW:hosted', tasks) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose', '-XX:ReservedCodeCacheSize=300m'], _noneAsEmptyList(extraVMarguments))

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    # run dacapo sanitychecks
    for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments) \
            + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments):
        with Task(str(test) + ':' + 'release', tasks) as t:
            if t and not test.test('jvmci'):
                t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with JVMCIMode('jit'):
        with Task('DaCapo_pmd:BatchMode', tasks) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-Xbatch', 'pmd'])

    # ensure -Xcomp still works
    with JVMCIMode('jit'):
        with Task('XCompMode:product', tasks) as t:
            if t: run_vm(_noneAsEmptyList(extraVMarguments) + ['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', []),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if mx.get_arch() == 'sparcv9' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', 'fastdebug', ['-esa']),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', 'fastdebug', ['-esa', '-XX:-UseCompressedOops', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithGCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', 'fastdebug', ['-esa', '-Djvmci.compiler=graal-economy', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithExceptionEdges', 'fastdebug', ['-esa', '-G:+StressInvokeWithExceptionNode', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithRegisterPressure', 'product', ['-esa', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapTraceRAWithRegisterPressure', 'product', ['-esa', '-G:+TraceRA', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithImmutableCode', 'product', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases', '-G:+ExitVMOnException']),
]

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action='append', help='add extra vm argument to gate tasks if applicable (multiple occurrences allowed)')

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    run_vm(vmArgs + [mainClass] + mainClassArgs)

mx_unittest.set_vm_launcher('JDK9 VM launcher', _unittest_vm_launcher)

def deoptalot(args):
    """bootstrap a VM with DeoptimizeALot and VerifyOops on

    If the first argument is a number, the process will be repeated
    this number of times. All other arguments are passed to the VM."""
    count = 1
    if len(args) > 0 and args[0].isdigit():
        count = int(args[0])
        del args[0]

    for _ in range(count):
        if not run_vm(['-XX:-TieredCompilation', '-XX:+DeoptimizeALot', '-XX:+VerifyOops'] + args + ['-version']) == 0:
            mx.abort("Failed")

def longtests(args):

    deoptalot(['15', '-Xmx48m'])

    dacapo(['100', 'eclipse', '-esa'])

"""
Extra benchmarks to run from 'bench()'.
"""
extraBenchmarks = []

def bench(args):
    """run benchmarks and parse their output for results

    Results are JSON formated : {group : {benchmark : score}}."""
    resultFile = None
    if '-resultfile' in args:
        index = args.index('-resultfile')
        if index + 1 < len(args):
            resultFile = args[index + 1]
            del args[index]
            del args[index]
        else:
            mx.abort('-resultfile must be followed by a file name')
    resultFileCSV = None
    if '-resultfilecsv' in args:
        index = args.index('-resultfilecsv')
        if index + 1 < len(args):
            resultFileCSV = args[index + 1]
            del args[index]
            del args[index]
        else:
            mx.abort('-resultfilecsv must be followed by a file name')
    vm = get_vm()
    if len(args) is 0:
        args = ['all']

    vmArgs = [arg for arg in args if arg.startswith('-')]

    def benchmarks_in_group(group):
        prefix = group + ':'
        return [a[len(prefix):] for a in args if a.startswith(prefix)]

    results = {}
    benchmarks = []
    # DaCapo
    if 'dacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        dacapos = benchmarks_in_group('dacapo')
        for dacapo in dacapos:
            if dacapo not in sanitycheck.dacapoSanityWarmup.keys():
                mx.abort('Unknown DaCapo : ' + dacapo)
            iterations = sanitycheck.dacapoSanityWarmup[dacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getDacapo(dacapo, ['-n', str(iterations)])]

    if 'scaladacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        scaladacapos = benchmarks_in_group('scaladacapo')
        for scaladacapo in scaladacapos:
            if scaladacapo not in sanitycheck.dacapoScalaSanityWarmup.keys():
                mx.abort('Unknown Scala DaCapo : ' + scaladacapo)
            iterations = sanitycheck.dacapoScalaSanityWarmup[scaladacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getScalaDacapo(scaladacapo, ['-n', str(iterations)])]

    # Bootstrap
    if 'bootstrap' in args or 'all' in args:
        benchmarks += sanitycheck.getBootstraps()
    # SPECjvm2008
    if 'specjvm2008' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120'])]
    else:
        specjvms = benchmarks_in_group('specjvm2008')
        for specjvm in specjvms:
            benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120', specjvm])]

    if 'specjbb2005' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjbb2005()]

    if 'specjbb2013' in args:  # or 'all' in args //currently not in default set
        benchmarks += [sanitycheck.getSPECjbb2013()]

    if 'ctw-full' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.Full))
    if 'ctw-noinline' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.NoInline))

    for f in extraBenchmarks:
        f(args, vm, benchmarks)

    for test in benchmarks:
        for (groupName, res) in test.bench(vm, extraVmOpts=vmArgs).items():
            group = results.setdefault(groupName, {})
            group.update(res)
    mx.log(json.dumps(results))
    if resultFile:
        with open(resultFile, 'w') as f:
            f.write(json.dumps(results))
    if resultFileCSV:
        with open(resultFileCSV, 'w') as f:
            for key1, value1 in results.iteritems():
                f.write('%s;\n' % (str(key1)))
                for key2, value2 in sorted(value1.iteritems()):
                    f.write('%s; %s;\n' % (str(key2), str(value2)))

def specjvm2008(args):
    """run one or more SPECjvm2008 benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getSPECjvm2008(harnessArgs + [bm]).bench(get_vm(), extraVmOpts=extraVmOpts)

    availableBenchmarks = set(sanitycheck.specjvm2008Names)
    if "all" not in args:
        # only add benchmark groups if we are not running "all"
        for name in sanitycheck.specjvm2008Names:
            parts = name.rsplit('.', 1)
            if len(parts) > 1:
                assert len(parts) == 2
                group = parts[0]
                availableBenchmarks.add(group)

    _run_benchmark(args, sorted(availableBenchmarks), launcher)

def specjbb2013(args):
    """run the composite SPECjbb2013 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2013(harnessArgs).bench(get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

def specjbb2005(args):
    """run the composite SPECjbb2005 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2005(harnessArgs).bench(get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

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
            arg = '-Djvmci.option.' + arg[len('-G:+'):] + '=true'
        elif arg.startswith('-G:-'):
            if '=' in arg:
                mx.abort('Mixing - and = in -G: option specification: ' + arg)
            arg = '-Djvmci.option.' + arg[len('-G:+'):] + '=false'
        elif arg.startswith('-G:'):
            arg = '-Djvmci.option.' + arg[len('-G:'):]
        return arg
    args = map(translateGOption, args)

    bcp = [mx.distribution('truffle:TRUFFLE_API').classpath_repr()]
    if _jvmciModes[_vm.jvmciMode]:
        bcpDeps = [mx.distribution(d) for d in _graalDists]
        if bcpDeps:
            bcp.extend([d.classpath_repr() for d in bcpDeps])
    args = ['-Xbootclasspath/p:' + os.pathsep.join(bcp)] + args

    # Set the default JVMCI compiler
    jvmciCompiler = _compilers[-1]
    args = ['-Djvmci.compiler=' + jvmciCompiler] + args

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

def run_vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK"""

    return run_java(mx.get_jdk(), args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

class GraalArchiveParticipant:
    def __init__(self, dist):
        self.dist = dist

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __add__(self, arcname, contents):
        if arcname.startswith('META-INF/jvmci.providers/'):
            provider = arcname[len('META-INF/jvmci.providers/'):]
            for service in contents.strip().split(os.linesep):
                assert service
                self.services.setdefault(service, []).append(provider)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            # Need to create service files for the providers of the
            # jdk.vm.ci.options.Options service created by
            # jdk.vm.ci.options.processor.OptionProcessor.
            provider = arcname[:-len('.class'):].replace('/', '.')
            self.services.setdefault('jdk.vm.ci.options.OptionDescriptors', []).append(provider)
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        pass

mx.update_commands(_suite, {
    'vm': [run_vm, '[-options] class [args...]'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'dacapo': [dacapo, '[VM options] benchmarks...|"all" [DaCapo options]'],
    'scaladacapo': [scaladacapo, '[VM options] benchmarks...|"all" [Scala DaCapo options]'],
    'specjvm2008': [specjvm2008, '[VM options] benchmarks...|"all" [SPECjvm2008 options]'],
    'specjbb2013': [specjbb2013, '[VM options] [-- [SPECjbb2013 options]]'],
    'specjbb2005': [specjbb2005, '[VM options] [-- [SPECjbb2005 options]]'],
    'bench' : [bench, '[-resultfile file] [all(default)|dacapo|specjvm2008|bootstrap]'],
    'microbench' : [microbench, '[VM options] [-- [JMH options]]'],
    'deoptalot' : [deoptalot, '[n]'],
    'longtests' : [longtests, ''],
})

mx.add_argument('-M', '--jvmci-mode', action='store', choices=sorted(_jvmciModes.viewkeys()), help='the JVM variant type to build/run (default: ' + _vm.jvmciMode + ')')

def mx_post_parse_cmd_line(opts):
    if opts.jvmci_mode is not None:
        _vm.update(opts.jvmci_mode)
    for dist in [mx.distribution(d) for d in _graalDists]:
        dist.set_archiveparticipant(GraalArchiveParticipant(dist))
