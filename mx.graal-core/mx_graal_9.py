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
import zipfile

import mx
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

from mx_unittest import unittest
from mx_graal_bench import dacapo
from mx_javamodules import as_java_module, get_module_deps
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

class GraalModuleDescriptor(object):
    """
    Describes the module containing Graal.

    :param str distName: name of the `JARDistribution` that creates the Graal module jar
    """
    def __init__(self, name):
        self._name = name

    def dist(self):
        """
        Gets the `JARDistribution` that creates the Graal module jar.

        :rtype: `JARDistribution
        """
        return mx.distribution(self._name)

    def get_module_jar(self):
        """
        Gets the path to the module jar file.

        :rtype: str
        """
        return as_java_module(self.dist(), _jdk).jarpath

_compilers = ['graal-economy', 'graal']

#: The `GraalModuleDescriptor` for the Graal module
_graal_module_descriptor = GraalModuleDescriptor('GRAAL')

def add_compiler(compilerName):
    _compilers.append(compilerName)

def set_graal_module(descriptor):
    global _graal_module_descriptor
    assert descriptor != None
    _graal_module_descriptor = descriptor

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
    parser.add_argument('--limitmods', action='store', help='limits the set of compiled classes to only those in the listed modules', metavar='<modulename>[,<modulename>...]')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        vmargs.append('-G:CompileTheWorldConfig=' + re.sub(r'\s+', '#', args.ctwopts))

    if args.cp:
        cp = os.path.abspath(args.cp)
        if _vm.jvmciMode == 'disabled':
            mx.abort('Non-Graal CTW does not support specifying a specific class path or jar to compile')
    else:
        # Compile all classes in the JRT image by default.
        cp = join(_jdk.home, 'lib', 'modules')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')
        if _vm.jvmciMode != 'disabled':
            # To be able to load all classes in the JRT with Class.forName,
            # all JDK modules need to be made root modules.
            limitmods = frozenset(args.limitmods.split(',')) if args.limitmods else None
            nonBootJDKModules = [m.name for m in _jdk.get_modules() if not m.boot and (limitmods is None or m.name in limitmods)]
            if nonBootJDKModules:
                vmargs.append('-addmods')
                vmargs.append(','.join(nonBootJDKModules))

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    if _vm.jvmciMode == 'disabled':
        vmargs.append('-XX:+CompileTheWorld')
    else:
        if args.limitmods:
            vmargs.append('-DCompileTheWorld.limitmods=' + args.limitmods)
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

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Remove entries from class path that are in the Graal module(s)
    cpIndex, cp = mx.find_classpath_arg(vmArgs)
    if cp:
        assert _graal_module_descriptor is not None
        module = as_java_module(_graal_module_descriptor.dist(), _jdk)
        cp = _uniqify(cp.split(os.pathsep))

        junitCp = [e.classpath_repr() for e in mx.classpath_entries(['JUNIT'])]
        excluded = frozenset([classpathEntry.classpath_repr() for classpathEntry in get_module_deps(module.dist)] + junitCp)

        cp = [classpathEntry for classpathEntry in cp if classpathEntry not in excluded]

        vmArgs[cpIndex] = os.pathsep.join(cp)

        # Junit libraries are made into automatic modules so that they are visible to tests
        # patched into Graal. These automatic modules must be declared to be read by
        # Graal which means they must also be made root modules (i.e., ``-addmods``)
        # since ``-XaddReads`` can only be applied to root modules.
        junitModules = [_automatic_module_name(e) for e in junitCp]
        vmArgs = ['-modulepath', os.pathsep.join(junitCp)] + vmArgs
        vmArgs = ['-addmods', ','.join(junitModules)] + vmArgs
        vmArgs = ['-XaddReads:' + module.name + '=' + ','.join(junitModules)] + vmArgs

        # Explicitly export JVMCI to Graal
        addedExports = {}
        for concealingModule, packages in module.concealedRequires.iteritems():
            if concealingModule == 'jdk.vm.ci':
                for package in packages:
                    addedExports.setdefault(concealingModule + '/' + package, set()).add(module.name)

        patches = []
        graalConcealedPackages = list(module.conceals)
        pathToProject = {p.output_dir() : p for p in mx.projects() if p.isJavaProject()}
        for classpathEntry in cp:
            # Export concealed JDK packages used by the class path entry
            _add_exports_for_concealed_packages(classpathEntry, pathToProject, addedExports, 'ALL-UNNAMED')

            # Patch the class path entry into Graal if it defines packages already defined by Graal.
            # Packages definitions cannot be split between modules.
            packages = frozenset(_defined_packages(classpathEntry))
            if not packages.isdisjoint(module.packages):
                patches.append(classpathEntry)
                extraPackages = packages - module.packages
                if extraPackages:
                    # From http://openjdk.java.net/jeps/261:
                    # If a package found in a module definition on a patch path is not already exported
                    # by that module then it will, still, not be exported. It can be exported explicitly
                    # via either the reflection API or the -XaddExports option.
                    graalConcealedPackages.extend(extraPackages)

        if patches:
            vmArgs = ['-Xpatch:' + module.name + '=' + os.pathsep.join(patches)] + vmArgs

        # Export all Graal packages to make them available to test classes
        for package in graalConcealedPackages:
            addedExports.setdefault(module.name + '/' + package, set()).update(junitModules + ['ALL-UNNAMED'])

        vmArgs = ['-XaddExports:' + export + '=' + ','.join(sorted(targets)) for export, targets in addedExports.iteritems()] + vmArgs

    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)
mx_unittest.set_vm_launcher('JDK9 VM launcher', _unittest_vm_launcher)

def _uniqify(alist):
    """
    Processes given list to remove all duplicate entries, preserving only the first unique instance for each entry.

    :param list alist: the list to process
    :return: `alist` with all duplicates removed
    """
    seen = set()
    return [e for e in alist if e not in seen and seen.add(e) is None]

def _defined_packages(classpathEntry):
    """
    Gets the packages defined by `classpathEntry`.
    """
    packages = set()
    if os.path.isdir(classpathEntry):
        for root, _, filenames in os.walk(classpathEntry):
            if any(f.endswith('.class') for f in filenames):
                package = root[len(classpathEntry) + 1:].replace(os.sep, '.')
                packages.add(package)
    elif classpathEntry.endswith('.zip') or classpathEntry.endswith('.jar'):
        with zipfile.ZipFile(classpathEntry, 'r') as zf:
            for name in zf.namelist():
                if name.endswith('.class') and '/' in name:
                    package = name[0:name.rfind('/')].replace('/', '.')
                    packages.add(package)
    return packages

def _automatic_module_name(modulejar):
    """
    Derives the name of an automatic module from an automatic module jar according to
    specification of java.lang.module.ModuleFinder.of(Path... entries).

    :param str modulejar: the path to a jar file treated as an automatic module
    :return: the name of the automatic module derived from `modulejar`
    """

    # Drop directory prefix and .jar (or .zip) suffix
    name = os.path.basename(modulejar)[0:-4]

    # Find first occurrence of -${NUMBER}. or -${NUMBER}$
    m = re.search(r'-(\d+(\.|$))', name)
    if m:
        name = name[0:m.start()]

    # Finally clean up the module name
    name = re.sub(r'[^A-Za-z0-9]', '.', name) # replace non-alphanumeric
    name = re.sub(r'(\.)(\1)+', '.', name) # collapse repeating dots
    name = re.sub(r'^\.', '', name) # drop leading dots
    return re.sub(r'\.$', '', name) # drop trailing dots

def _add_exports_for_concealed_packages(classpathEntry, pathToProject, exports, module):
    """
    Adds exports for concealed packages imported by the project whose output directory matches `classpathEntry`.

    :param str classpathEntry: a class path entry
    :param dict pathToProject: map from an output directory to its defining `JavaProject`
    :param dict exports: map from a module/package specifier to the set of modules it must be exported to
    :param str module: the name of the module containing the classes in `classpathEntry`
    """
    project = pathToProject.get(classpathEntry, None)
    if project:
        concealed = project.get_concealed_imported_packages()
        for concealingModule, packages in concealed.iteritems():
            for package in packages:
                exports.setdefault(concealingModule + '/' + package, set()).add(module)

def _extract_added_exports(args, addedExports):
    """
    Extracts ``-XaddExports`` entries from `args` and updates `addedExports` based on their values.

    :param list args: command line arguments
    :param dict addedExports: map from a module/package specifier to the set of modules it must be exported to
    :return: the value of `args` minus all valid ``-XaddExports`` entries
    """
    res = []
    for arg in args:
        if arg.startswith('-XaddExports:'):
            parts = arg[len('-XaddExports:'):].split('=', 1)
            if len(parts) == 2:
                export, targets = parts
                addedExports.setdefault(export, set()).update(targets.split(','))
            else:
                # Invalid format - let the VM deal with it
                res.append(arg)
        else:
            res.append(arg)
    return res

def _index_of(haystack, needles):
    """
    Gets the index of the first entry in `haystack` that matches an item in `needles`.

    :param list haystack: list to search
    :param list needles: the items to search for
    :return: index of first needles in `haystack` or -1 if not found
    """
    for i in range(len(haystack)):
        if haystack[i] in needles:
            return i
    return -1

def _parseVmArgs(jdk, args, addDefaultArgs=True):
    args = mx.expand_project_in_args(args, insitu=False)

    argsPrefix = []
    jacocoArgs = mx_gate.get_jacoco_agent_args()
    if jacocoArgs:
        argsPrefix.extend(jacocoArgs)

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
    if exists(options_file):
        argsPrefix.append('-Dgraal.options.file=' + options_file)
    args = [translateGOption(a) for a in args]

    if '-G:+PrintFlags' in args and '-Xcomp' not in args:
        mx.warn('Using -G:+PrintFlags may have no effect without -Xcomp as Graal initialization is lazy')

    assert _graal_module_descriptor is not None
    module = as_java_module(_graal_module_descriptor.dist(), _jdk)

    # Update added exports to include concealed JDK packages required by Graal
    addedExports = {}
    args = _extract_added_exports(args, addedExports)
    for concealingModule, packages in module.concealedRequires.iteritems():
        # No need to explicitly export JVMCI - it's exported via reflection
        if concealingModule != 'jdk.vm.ci':
            for package in packages:
                addedExports.setdefault(concealingModule + '/' + package, set()).add(module.name)
    for export, targets in addedExports.iteritems():
        argsPrefix.append('-XaddExports:' + export + '=' + ','.join(sorted(targets)))

    # Set or update module path to include Graal
    mpIndex = _index_of(args, ['-modulepath', '-mp'])
    if mpIndex != -1:
        assert mpIndex + 1 < len(args), 'VM option ' + args[mpIndex] + ' requires an argument'
        args[mpIndex + 1] = args[mpIndex + 1] + os.pathsep + module.jarpath
    else:
        argsPrefix.append('-modulepath')
        argsPrefix.append(module.jarpath)

    # Set the default JVMCI compiler
    jvmciCompiler = _compilers[-1]
    argsPrefix.append('-Djvmci.Compiler=' + jvmciCompiler)

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if  len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the VM because they come after the '-version' argument: " + ' '.join(ignoredArgs))

    return jdk.processArgs(argsPrefix + args, addDefaultArgs=addDefaultArgs)

def run_java(jdk, args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True):

    args = _parseVmArgs(jdk, args, addDefaultArgs=addDefaultArgs)

    jvmciModeArgs = _jvmciModes[_vm.jvmciMode]
    if '-XX:+UseJVMCICompiler' not in jvmciModeArgs and '-XX:+BootstrapJVMCI' in args:
        mx.warn('-XX:+BootstrapJVMCI is ignored since -XX:+UseJVMCICompiler is not enabled')
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
    for dist in _suite.dists:
        dist.set_archiveparticipant(GraalArchiveParticipant(dist))
