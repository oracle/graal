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
from os.path import join, exists, basename
from argparse import ArgumentParser
import sanitycheck
import itertools
import json
import re

import mx
from mx_jvmci import JvmciJDKDeployedDist, add_bootclasspath_prepend, buildvms
from mx_jvmci import jdkDeployedDists #pylint: disable=unused-import
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

try:
    from mx_jvmci import run_vm, VM, get_vm, isJVMCIEnabled, relativeVmLibDirInJdk, get_jvmci_jdk, get_jvmci_jdk_dir #pylint: disable=no-name-in-module
except ImportError:
    pass
from mx_unittest import unittest
import mx_gate

_suite = mx.suite('graal')

class GraalJDKDeployedDist(JvmciJDKDeployedDist):
    def __init__(self):
        JvmciJDKDeployedDist.__init__(self, 'GRAAL_HOTSPOT', compilers=['graal-economy', 'graal'])

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

jdkDeployedDists += [
    JvmciJDKDeployedDist('GRAAL_NODEINFO'),
    JvmciJDKDeployedDist('GRAAL_API'),
    JvmciJDKDeployedDist('GRAAL_COMPILER'),
    JvmciJDKDeployedDist('GRAAL'),
    GraalJDKDeployedDist(),
    JvmciJDKDeployedDist('GRAAL_TRUFFLE'),
    JvmciJDKDeployedDist('GRAAL_TRUFFLE_HOTSPOT'),
]

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
    if isJVMCIEnabled(get_vm()) and  '-XX:-UseJVMCIClassLoader' not in vmArgs:
        vmArgs = ['-XX:-UseJVMCIClassLoader'] + vmArgs

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
        jdk = get_jvmci_jdk()
        jvm = get_vm()
        def quoteSpace(s):
            if " " in s:
                return '"' + s + '"'
            return s

        forkedVmArgs = map(quoteSpace, jdk.parseVmArgs(vmArgs))
        args += ['--jvmArgsPrepend', ' '.join(['-' + jvm] + forkedVmArgs)]
    run_vm(args + jmhArgs)

def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--jar', action='store', help='jar of classes to compiled instead of rt.jar', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        # when they are collated in the "jvmci.options" system property
        vmargs.append('-G:CompileTheWorldConfig=' + re.sub(r'\s+', '#', args.ctwopts))

    if args.jar:
        jar = os.path.abspath(args.jar)
    else:
        jar = join(get_jvmci_jdk_dir(deployDists=False), 'jre', 'lib', 'rt.jar')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    vm_ = get_vm()
    if isJVMCIEnabled(vm_):
        if vm_ == 'jvmci':
            vmargs += ['-XX:+BootstrapJVMCI']
        vmargs += ['-G:CompileTheWorldClasspath=' + jar, '-XX:-UseJVMCIClassLoader', 'com.oracle.graal.hotspot.CompileTheWorld']
    else:
        vmargs += ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + jar]

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
        self.vmbuild = vmbuild
        self.args = args
        self.suppress = suppress

    def run(self, tasks, extraVMarguments=None):
        with VM('jvmci', self.vmbuild):
            with Task(self.name + ':' + self.vmbuild, tasks) as t:
                if t:
                    if self.suppress:
                        out = mx.DuplicateSuppressingStream(self.suppress).write
                    else:
                        out = None
                    run_vm(self.args + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'] + _noneAsEmptyList(extraVMarguments), out=out)

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):

    # Build server-hosted-jvmci now so we can run the unit tests
    with Task('BuildHotSpotGraalHosted: product', tasks) as t:
        if t: buildvms(['--vms', 'server', '--builds', 'product'])

    # Run unit tests on server-hosted-jvmci
    with VM('server', 'product'):
        for r in unit_test_runs:
            r.run(suites, tasks, extraVMarguments)

    # Run ctw against rt.jar on server-hosted-jvmci
    with VM('server', 'product'):
        with Task('CTW:hosted-product', tasks) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose', '-XX:ReservedCodeCacheSize=300m'], _noneAsEmptyList(extraVMarguments))

    # Build the jvmci VMs so we can run the other tests
    with Task('BuildHotSpotGraalOthers: fastdebug,product', tasks) as t:
        if t: buildvms(['--vms', 'jvmci', '--builds', 'fastdebug,product'])

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    # run dacapo sanitychecks
    for vmbuild in ['fastdebug', 'product']:
        for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild, extraVmArguments=extraVMarguments) \
                + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild, extraVmArguments=extraVMarguments):
            with Task(str(test) + ':' + vmbuild, tasks) as t:
                if t and not test.test('jvmci'):
                    t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with VM('jvmci', 'product'):
        with Task('DaCapo_pmd:BatchMode:product', tasks) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-Xbatch', 'pmd'])

    # ensure -Xcomp still works
    with VM('jvmci', 'product'):
        with Task('XCompMode:product', tasks) as t:
            if t: run_vm(_noneAsEmptyList(extraVMarguments) + ['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', []),
    UnitTestRun('UnitTestsNonSSA', ['-G:-SSA_LIR']),
    UnitTestRun('UnitTestsTraceRA', ['-G:+TraceRA']),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if mx.get_arch() == 'sparcv9' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', 'fastdebug', ['-esa']),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', 'fastdebug', ['-esa', '-XX:-UseCompressedOops']),
    BootstrapTest('BootstrapWithGCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVecification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', 'fastdebug', ['-esa', '-Djvmci.compiler=graal-economy']),
    BootstrapTest('BootstrapWithExceptionEdges', 'fastdebug', ['-esa', '-G:+StressInvokeWithExceptionNode']),
    BootstrapTest('BootstrapWithRegisterPressure', 'product', ['-esa', '-G:RegisterPressure=' + _registers]),
    BootstrapTest('BootstrapNonSSAWithRegisterPressure', 'product', ['-esa', '-G:-SSA_LIR', '-G:RegisterPressure=' + _registers]),
    BootstrapTest('BootstrapTraceRAWithRegisterPressure', 'product', ['-esa', '-G:+TraceRA', '-G:RegisterPressure=' + _registers]),
    BootstrapTest('BootstrapWithImmutableCode', 'product', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases']),
]

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action='append', help='add extra vm argument to gate tasks if applicable (multiple occurrences allowed)')

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

def jdkartifactstats(args):
    """show stats about JDK deployed Graal artifacts"""
    jdkDir = get_jvmci_jdk_dir()
    artifacts = {}
    for root, _, filenames in os.walk(join(jdkDir, 'jre', 'lib')):
        for f in filenames:
            if f.endswith('.jar') and not f.endswith('.stripped.jar'):
                jar = join(root, f)
                if 'truffle' in f:
                    if 'enterprise' in f:
                        artifacts.setdefault('GraalEnterpriseTruffle', []).append(jar)
                    else:
                        artifacts.setdefault('GraalTruffle', []).append(jar)
                elif 'enterprise' in f:
                    artifacts.setdefault('GraalEnterprise', []).append(jar)
                elif 'jvmci' in f:
                    artifacts.setdefault('JVMCI', []).append(jar)
                elif 'graal' in f:
                    artifacts.setdefault('Graal', []).append(jar)
                else:
                    mx.logv('ignored: ' + jar)

    print '{:>10}  {:>10}  {:>10}  {}'.format('All', 'NoVars', 'None', 'Jar')
    for category in sorted(artifacts.viewkeys()):
        jars = artifacts[category]
        if jars:
            totals = (0, 0, 0)
            print
            for j in jars:
                gSize = os.path.getsize(j)
                stripped = j[:-len('.jar')] + '.stripped.jar'
                mx.run([mx.get_jdk().pack200, '--repack', '--quiet', '-J-Djava.util.logging.config.file=', '-DLocalVariableTypeTable=strip', '-DLocalVariableTable=strip', stripped, j])
                gLinesSourceSize = os.path.getsize(stripped)
                mx.run([mx.get_jdk().pack200, '--repack', '--quiet', '-J-Djava.util.logging.config.file=', '-G', stripped, j])
                gNoneSize = os.path.getsize(stripped)
                os.remove(stripped)
                print '{:10,}  {:10,}  {:10,}  {}:{}'.format(gSize, gLinesSourceSize, gNoneSize, category, basename(j))
                t1, t2, t3 = totals
                totals = (t1 + gSize, t2 + gLinesSourceSize, t3 + gNoneSize)
            t1, t2, t3 = totals
            print '{:10,}  {:10,}  {:10,}  {}'.format(t1, t2, t3, category)

    jvmLib = join(jdkDir, relativeVmLibDirInJdk(), get_vm(), mx.add_lib_suffix(mx.add_lib_prefix('jvm')))
    print
    if exists(jvmLib):
        print '{:10,}  {}'.format(os.path.getsize(jvmLib), jvmLib)
    else:
        print '{:>10}  {}'.format('<missing>', jvmLib)

mx.update_commands(_suite, {
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'dacapo': [dacapo, '[VM options] benchmarks...|"all" [DaCapo options]'],
    'jdkartifactstats' : [jdkartifactstats, ''],
    'scaladacapo': [scaladacapo, '[VM options] benchmarks...|"all" [Scala DaCapo options]'],
    'specjvm2008': [specjvm2008, '[VM options] benchmarks...|"all" [SPECjvm2008 options]'],
    'specjbb2013': [specjbb2013, '[VM options] [-- [SPECjbb2013 options]]'],
    'specjbb2005': [specjbb2005, '[VM options] [-- [SPECjbb2005 options]]'],
    'bench' : [bench, '[-resultfile file] [all(default)|dacapo|specjvm2008|bootstrap]'],
    'microbench' : [microbench, '[VM options] [-- [JMH options]]'],
    'deoptalot' : [deoptalot, '[n]'],
    'longtests' : [longtests, ''],
})


def mx_post_parse_cmd_line(opts):
    add_bootclasspath_prepend(mx.distribution('truffle:TRUFFLE_API'))
