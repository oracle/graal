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
import re

import mx
from mx_jvmci import JVMCI_VERSION, JvmciJDKDeployedDist, JVMCIArchiveParticipant, jdkDeployedDists, add_bootclasspath_prepend, buildvms, get_jvmci_jdk, _JVMCI_JDK_TAG, VM, relativeVmLibDirInJdk, isJVMCIEnabled
from mx_jvmci import get_vm as _jvmci_get_vm
from mx_jvmci import run_vm as _jvmci_run_vm
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

from mx_unittest import unittest
from mx_graal_bench import dacapo
import mx_gate
import mx_unittest

_suite = mx.suite('graal-core')

def get_vm():
    """
    Gets the name of the currently selected JVM variant.
    """
    vm = _jvmci_get_vm()
    if JVMCI_VERSION >= 9:
        assert isinstance(vm, VM)
        return vm.jvmVariant
    else:
        assert isinstance(vm, str)
        return vm

class GraalJDKDeployedDist(JvmciJDKDeployedDist):
    def __init__(self, name, compilers=False, updatesGraalProperties=False):
        JvmciJDKDeployedDist.__init__(self, name, compilers=compilers)
        self.updatesGraalProperties = updatesGraalProperties

    def deploy(self, jdkDir):
        JvmciJDKDeployedDist.deploy(self, jdkDir)
        if self.updatesGraalProperties:
            self._updateGraalPropertiesFile(join(jdkDir, 'jre', 'lib'))

    def set_archiveparticipant(self):
        dist = self.dist()
        dist.set_archiveparticipant(GraalArchiveParticipant(dist))

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
    GraalJDKDeployedDist('GRAAL_OPTIONS'),
    GraalJDKDeployedDist('GRAAL_NODEINFO'),
    GraalJDKDeployedDist('GRAAL_API'),
    GraalJDKDeployedDist('GRAAL_COMPILER'),
    GraalJDKDeployedDist('GRAAL_RUNTIME'),
    GraalJDKDeployedDist('GRAAL_HOTSPOT', compilers=['graal-economy', 'graal'], updatesGraalProperties=True),
    GraalJDKDeployedDist('GRAAL_TRUFFLE'),
    GraalJDKDeployedDist('GRAAL_TRUFFLE_HOTSPOT'),
]

mx_gate.add_jacoco_includes(['com.oracle.graal.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

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
    if JVMCI_VERSION < 9:
        if isJVMCIEnabled(get_vm()) and '-XX:-UseJVMCIClassLoader' not in vmArgs:
            vmArgs = ['-XX:-UseJVMCIClassLoader'] + vmArgs

    # look for -f in JMH arguments
    forking = True
    for i in range(len(jmhArgs)):
        arg = jmhArgs[i]
        if arg.startswith('-f'):
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
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        vmargs.append('-G:CompileTheWorldConfig=' + re.sub(r'\s+', '#', args.ctwopts))

    if args.cp:
        cp = os.path.abspath(args.cp)
    else:
        if JVMCI_VERSION < 9:
            cp = join(get_jvmci_jdk().home, 'jre', 'lib', 'rt.jar')
        else:
            cp = join(get_jvmci_jdk().home, 'modules', 'java.base') + os.pathsep + \
                 join(get_jvmci_jdk().home, 'lib', 'modules', 'bootmodules.jimage')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    vm = get_vm()
    if JVMCI_VERSION >= 9:
        jvmciMode = _jvmci_get_vm().jvmciMode
        if jvmciMode == 'disabled':
            vmargs += ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + cp]
        else:
            if jvmciMode == 'jit':
                vmargs += ['-XX:+BootstrapJVMCI']
            vmargs += ['-G:CompileTheWorldClasspath=' + cp, 'com.oracle.graal.hotspot.CompileTheWorld']
    else:
        if isJVMCIEnabled(vm):
            if vm == 'jvmci':
                vmargs += ['-XX:+BootstrapJVMCI']
            vmargs += ['-G:CompileTheWorldClasspath=' + cp, '-XX:-UseJVMCIClassLoader', 'com.oracle.graal.hotspot.CompileTheWorld']
        else:
            vmargs += ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + cp]

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
        self.vmbuild = vmbuild
        self.args = args
        self.tags = tags
        self.suppress = suppress

    def run(self, tasks, extraVMarguments=None):
        with VM('jvmci', self.vmbuild):
            with Task(self.name + ':' + self.vmbuild, tasks, tags=self.tags) as t:
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
            if t: microbench(_noneAsEmptyList(extraVMarguments) + ['--'] + self.args)

class GraalTags:
    test = 'test'
    bootstrap = 'bootstrap'
    fulltest = 'fulltest'

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):

    # Build server-hosted-jvmci now so we can run the unit tests
    with Task('BuildHotSpotGraalHosted: product', tasks, tags=[GraalTags.test, GraalTags.fulltest]) as t:
        if t: buildvms(['--vms', 'server', '--builds', 'product'])

    with VM('server', 'product'):
    # Run unit tests on server-hosted-jvmci
        for r in unit_test_runs:
            r.run(suites, tasks, extraVMarguments)

    # Run microbench on server-hosted-jvmci (only for testing the JMH setup)
    with VM('server', 'product'):
        for r in [MicrobenchRun('Microbench', ['TestJMH'], tags=[GraalTags.fulltest])]:
            r.run(tasks, extraVMarguments)

    # Run ctw against rt.jar on server-hosted-jvmci
    with VM('server', 'product'):
        with Task('CTW:hosted-product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose', '-XX:ReservedCodeCacheSize=400m'], _noneAsEmptyList(extraVMarguments))

    # Build the jvmci VMs so we can run the other tests
    with Task('BuildHotSpotGraalJVMCI: fastdebug', tasks, tags=[GraalTags.bootstrap, GraalTags.fulltest]) as t:
        if t: buildvms(['--vms', 'jvmci', '--builds', 'fastdebug'])
    with Task('BuildHotSpotGraalJVMCI: product', tasks, tags=[GraalTags.fulltest]) as t:
        if t: buildvms(['--vms', 'jvmci', '--builds', 'product'])

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    # run dacapo sanitychecks
    for vmbuild in ['fastdebug', 'product']:
        for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild, extraVmArguments=extraVMarguments) \
                + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel=vmbuild, extraVmArguments=extraVMarguments):
            with Task(str(test) + ':' + vmbuild, tasks, tags=[GraalTags.fulltest]) as t:
                if t and not test.test('jvmci'):
                    t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with VM('jvmci', 'product'):
        with Task('DaCapo_pmd:BatchMode:product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-Xbatch', 'pmd'])

    # ensure benchmark counters still work
    with VM('jvmci', 'product'):
        with Task('DaCapo_pmd:BenchmarkCounters:product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-G:+LIRProfileMoves', '-G:+GenericDynamicCounters', '-XX:JVMCICounterSize=10', 'pmd'])

    # ensure -Xcomp still works
    with VM('jvmci', 'product'):
        with Task('XCompMode:product', tasks, tags=[GraalTags.fulltest]) as t:
            if t: run_vm(_noneAsEmptyList(extraVMarguments) + ['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', [], tags=[GraalTags.test]),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if mx.get_arch() == 'sparcv9' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', 'fastdebug', ['-esa'], tags=[GraalTags.bootstrap]),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', 'fastdebug', ['-esa', '-XX:-UseCompressedOops', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithGCVerification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', 'product', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', 'fastdebug', ['-esa', '-Djvmci.compiler=graal-economy', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithExceptionEdges', 'fastdebug', ['-esa', '-G:+StressInvokeWithExceptionNode', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithRegisterPressure', 'product', ['-esa', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapTraceRAWithRegisterPressure', 'product', ['-esa', '-G:+TraceRA', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart'], tags=[GraalTags.fulltest]),
    BootstrapTest('BootstrapWithImmutableCode', 'product', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases', '-G:+ExitVMOnException'], tags=[GraalTags.fulltest]),
 ]



def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal-core', 'truffle'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action='append', help='add extra vm argument to gate tasks if applicable (multiple occurrences allowed)')

def jdkartifactstats(args):
    """show stats about JDK deployed Graal artifacts"""
    artifacts = {}
    jdkDir = get_jvmci_jdk().home
    def _getDeployedJars():
        if JVMCI_VERSION < 9:
            for root, _, filenames in os.walk(join(jdkDir, 'jre', 'lib')):
                for f in filenames:
                    if f.endswith('.jar') and not f.endswith('.stripped.jar'):
                        yield join(root, f)
        else:
            for jdkDist in jdkDeployedDists:
                dist = jdkDist.dist()
                if isinstance(jdkDist, JvmciJDKDeployedDist):
                    yield dist.path

    for jar in _getDeployedJars():
        f = basename(jar)
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

# Support for -G: options
def _buildGOptionsArgs(args):
    def _translateGOption(arg):
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
    return options_file_arg + map(_translateGOption, args)

def run_vm(*positionalargs, **kwargs):
    """run a Java program by executing the java executable in a Graal JDK"""

    # convert positional args to a list so the first element can be updated
    positionalargs = list(positionalargs)
    args = positionalargs[0]
    if '-G:+PrintFlags' in args and '-Xcomp' not in args:
        mx.warn('Using -G:+PrintFlags may have no effect without -Xcomp as Graal initialization is lazy')
    positionalargs[0] = _buildGOptionsArgs(args)
    return _jvmci_run_vm(*positionalargs, **kwargs)

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    if isJVMCIEnabled(get_vm()):
        return (_buildGOptionsArgs(vmArgs), mainClass, mainClassArgs)
    return config

mx_unittest.add_config_participant(_unittest_config_participant)

mx.update_commands(_suite, {
    'vm': [run_vm, '[-options] class [args...]'],
    'jdkartifactstats' : [jdkartifactstats, ''],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'microbench' : [microbench, '[VM options] [-- [JMH options]]'],
})

class GraalArchiveParticipant(JVMCIArchiveParticipant):
    def __init__(self, dist):
        JVMCIArchiveParticipant.__init__(self, dist)

    def __add__(self, arcname, contents):
        if arcname.startswith('META-INF/providers/'):
            # Handles files generated by ServiceProviderProcessor
            provider = arcname[len('META-INF/providers/'):]
            for service in contents.strip().split(os.linesep):
                assert service
                self.jvmciServices.setdefault(service, []).append(provider)
            return True
        if arcname.endswith('_OptionDescriptors.class'):
            # Need to create service files for the providers of the
            # com.oracle.graal.options.Options service created by
            # com.oracle.graal.options.processor.OptionProcessor.
            provider = arcname[:-len('.class'):].replace('/', '.')
            self.services.setdefault('com.oracle.graal.options.OptionDescriptors', []).append(provider)
        return JVMCIArchiveParticipant.__add__(self, arcname, contents)

"""
The Graal JDK(s).
"""
_graal_jdks = {}

def get_graal_jdk():
    """
    Gets a Graal JDK which adds support for handling the -G format of Graal options.
    """
    jvmci_jdk = get_jvmci_jdk()
    if JVMCI_VERSION < 9:
        from mx_jvmci import check_VM_exists, JVMCI8JDKConfig # pylint: disable=no-name-in-module
        vmbuild = jvmci_jdk.vmbuild
        check_VM_exists(get_vm(), jvmci_jdk.home, vmbuild)
        jdk = _graal_jdks.get(vmbuild)
        if jdk is None:
            class GraalJDK8Config(JVMCI8JDKConfig):
                def __init__(self, vmbuild): # pylint: disable=super-init-not-called
                    JVMCI8JDKConfig.__init__(self, vmbuild)

                def parseVmArgs(self, args, addDefaultArgs=True):
                    return JVMCI8JDKConfig.parseVmArgs(self, _buildGOptionsArgs(args), addDefaultArgs=addDefaultArgs)

            jdk = GraalJDK8Config(vmbuild)
            _graal_jdks[vmbuild] = jdk
    else:
        jdk = _graal_jdks.get('default')
        if jdk is None:
            from mx_jvmci import JVMCI9JDKConfig # pylint: disable=no-name-in-module
            class GraalJDK9Config(JVMCI9JDKConfig):
                def __init__(self, debugLevel):
                    JVMCI9JDKConfig.__init__(self, debugLevel)

                def parseVmArgs(self, args, addDefaultArgs=True):
                    return JVMCI9JDKConfig.parseVmArgs(self, _buildGOptionsArgs(args), addDefaultArgs=addDefaultArgs)
            jdk = GraalJDK9Config(jvmci_jdk.debugLevel)
            _graal_jdks['default'] = jdk
    return jdk

class GraalJDKFactory(mx.JDKFactory):
    def getJDKConfig(self):
        return get_graal_jdk()

    def description(self):
        return "Graal JDK"

# This will override the 'generic' JVMCI JDK with a Graal JVMCI JDK that has
# support for -G style Graal options.
mx.addJDKFactory(_JVMCI_JDK_TAG, mx.JavaCompliance(str(JVMCI_VERSION)), GraalJDKFactory())

def mx_post_parse_cmd_line(opts):
    add_bootclasspath_prepend(mx.distribution('truffle:TRUFFLE_API'))
