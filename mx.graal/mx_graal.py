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

import os, platform
from os.path import join, exists
from argparse import ArgumentParser
import sanitycheck
import itertools
import json

import mx
import mx_jvmci
from mx_jvmci import JvmciJDKDeployedDist, vm, VM, Task, parseVmArgs, get_vm, isJVMCIEnabled, get_jvmci_jdk, buildvms
import mx_unittest
from mx_unittest import unittest
import mx_gate

_suite = mx.suite('graal')

class GraalJDKDeployedDist(JvmciJDKDeployedDist):
    def __init__(self):
        JvmciJDKDeployedDist.__init__(self, 'GRAAL')

    def deploy(self, jdkDir):
        JvmciJDKDeployedDist.deploy(self, jdkDir)
        self._updateGraalPropertiesFile(join(jdkDir, 'jre', 'lib'))

    def _updateGraalPropertiesFile(self, jreLibDir):
        """
        Updates (or creates) 'jreLibDir'/jvmci/graal.properties to set/modify the
        graal.version property.
        """
        version = _suite.release_version()
        graalProperties = join(jreLibDir, 'jvmci', 'graal.properties')
        if not exists(graalProperties):
            with open(graalProperties, 'w') as fp:
                print >> fp, 'graal.version=' + version
        else:
            content = []
            with open(graalProperties) as fp:
                for line in fp:
                    if line.startswith('graal.version='):
                        content.append('graal.version=' + version)
                    else:
                        content.append(line.rstrip(os.linesep))
            with open(graalProperties, 'w') as fp:
                fp.write(os.linesep.join(content))

mx_jvmci.jdkDeployedDists += [
    JvmciJDKDeployedDist('GRAAL_NODEINFO'),
    JvmciJDKDeployedDist('GRAAL_API'),
    JvmciJDKDeployedDist('GRAAL_COMPILER'),
    GraalJDKDeployedDist(),
    JvmciJDKDeployedDist('GRAAL_TRUFFLE'),
]

mx_gate.add_jacoco_includes(['com.oracle.graal.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Unconditionally prepend truffle.jar to the boot class path.
    # This used to be done by the VM itself but was removed to
    # separate the VM from Truffle.
    truffle_jar = mx.distribution('truffle:TRUFFLE').path
    vmArgs = ['-Xbootclasspath/p:' + truffle_jar] + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

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

def microbench(args):
    """run JMH microbenchmark projects"""
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

    # default to -f1 if not specified otherwise
    if not containsF:
        jmhArgs += ['-f1']

    # find all projects with a direct JMH dependency
    jmhProjects = []
    for p in mx.projects():
        if 'JMH' in p.deps:
            jmhProjects.append(p.name)
    cp = mx.classpath(jmhProjects)

    # execute JMH runner
    args = ['-cp', cp]
    if not forking:
        args += vmArgs
    args += ['org.openjdk.jmh.Main']
    if forking:
        (_, _, jvm, _, _) = parseVmArgs(vmArgs)
        args += ['--jvmArgsPrepend', ' '.join(['-' + jvm] + vmArgs)]
    vm(args + jmhArgs)

def ctw(args):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--jar', action='store', help='jar of classes to compiled instead of rt.jar', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        vmargs.append('-G:CompileTheWorldConfig=' + args.ctwopts)

    if args.jar:
        jar = os.path.abspath(args.jar)
    else:
        jar = join(get_jvmci_jdk(installJars=False), 'jre', 'lib', 'rt.jar')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    vmargs += ['-XX:+CompileTheWorld']
    vm_ = get_vm()
    if isJVMCIEnabled(vm_):
        if vm_ == 'jvmci':
            vmargs += ['-XX:+BootstrapJVMCI']
        vmargs += ['-G:CompileTheWorldClasspath=' + jar]
    else:
        vmargs += ['-Xbootclasspath/p:' + jar]

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    vm(vmargs)

class UnitTestRun:
    def __init__(self, name, args):
        self.name = name
        self.args = args

    def run(self, suites, tasks):
        for suite in suites:
            with Task(self.name + ': hosted-product ' + suite, tasks) as t:
                if t: unittest(['--suite', suite, '--enable-timing', '--verbose', '--fail-fast'] + self.args)

class BootstrapTest:
    def __init__(self, name, vmbuild, args, suppress=None):
        self.name = name
        self.vmbuild = vmbuild
        self.args = args
        self.suppress = suppress

    def run(self, tasks):
        with VM('jvmci', self.vmbuild):
            with Task(self.name + ':' + self.vmbuild, tasks) as t:
                if t:
                    if self.suppress:
                        out = mx.DuplicateSuppressingStream(self.suppress).write
                    else:
                        out = None
                    vm(self.args + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'], out=out)

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks):

    # Build server-hosted-jvmci now so we can run the unit tests
    with Task('BuildHotSpotGraalHosted: product', tasks) as t:
        if t: buildvms(['--vms', 'server', '--builds', 'product'])

    # Run unit tests on server-hosted-jvmci
    with VM('server', 'product'):
        for r in unit_test_runs:
            r.run(suites, tasks)

    # Run ctw against rt.jar on server-hosted-jvmci
    with VM('server', 'product'):
        with Task('CTW:hosted-product', tasks) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose'])

    # Build the jvmci VMs so we can run the other tests
    with Task('BuildHotSpotGraalOthers: fastdebug,product', tasks) as t:
        if t: buildvms(['--vms', 'jvmci', '--builds', 'fastdebug,product'])

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks)

    # run dacapo sanitychecks
    for vmbuild in ['fastdebug', 'product']:
        for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild) + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild):
            with Task(str(test) + ':' + vmbuild, tasks) as t:
                if t and not test.test('jvmci'):
                    t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with VM('jvmci', 'product'):
        with Task('DaCapo_pmd:BatchMode:product', tasks) as t:
            if t: dacapo(['-Xbatch', 'pmd'])

    # ensure -Xcomp still works
    with VM('jvmci', 'product'):
        with Task('XCompMode:product', tasks) as t:
            if t: vm(['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', []),
    UnitTestRun('UnitTestsNonSSA', ['-G:-SSA_LIR']),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if platform.processor() == 'sparc' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', 'fastdebug', ['-esa']),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', 'fastdebug', ['-esa', '-XX:-UseCompressedOops']),
    BootstrapTest('BootstrapWithGCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', 'fastdebug', ['-esa', '-G:CompilerConfiguration=economy']),
    BootstrapTest('BootstrapWithExceptionEdges', 'fastdebug', ['-esa', '-G:+StressInvokeWithExceptionNode']),
    BootstrapTest('BootstrapWithRegisterPressure', 'product', ['-esa', '-G:RegisterPressure=' + _registers]),
    BootstrapTest('BootstrapNonSSAWithRegisterPressure', 'product', ['-esa', '-G:-SSA_LIR', '-G:RegisterPressure=' + _registers]),
    BootstrapTest('BootstrapWithImmutableCode', 'product', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases']),
]

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal'], graal_unit_test_runs, graal_bootstrap_tests, tasks)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)

def deoptalot(args):
    """bootstrap a VM with DeoptimizeALot and VerifyOops on

    If the first argument is a number, the process will be repeated
    this number of times. All other arguments are passed to the VM."""
    count = 1
    if len(args) > 0 and args[0].isdigit():
        count = int(args[0])
        del args[0]

    for _ in range(count):
        if not vm(['-XX:-TieredCompilation', '-XX:+DeoptimizeALot', '-XX:+VerifyOops'] + args + ['-version']) == 0:
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

mx.update_commands(_suite, {
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
