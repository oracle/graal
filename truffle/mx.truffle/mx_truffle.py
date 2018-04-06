#
# commands.py - the GraalVM specific commands
#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
from os.path import exists
import re
import zipfile
from collections import OrderedDict
from argparse import ArgumentParser, RawDescriptionHelpFormatter
import tempfile
import shutil

import mx

from mx_unittest import unittest
from mx_sigtest import sigtest
from mx_jackpot import jackpot
from mx_gate import Task
from mx_javamodules import as_java_module, get_java_module_info
from urlparse import urljoin
import mx_gate
import mx_unittest
import mx_benchmark

_suite = mx.suite('truffle')

class JMHRunnerTruffleBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite):

    def name(self):
        return "truffle"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def extraVmArgs(self):
        return ['-XX:-UseJVMCIClassLoader'] + super(JMHRunnerTruffleBenchmarkSuite, self).extraVmArgs()

mx_benchmark.add_bm_suite(JMHRunnerTruffleBenchmarkSuite())
#mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm("server", "default"), priority=3)

def javadoc(args, vm=None):
    """build the Javadoc for all API packages"""
    mx.javadoc(['--unified', '--exclude-packages', 'com.oracle.truffle.tck,com.oracle.truffle.tck.impl,com.oracle.truffle.api.interop.java,com.oracle.truffle.api.vm,com.oracle.truffle.api.metadata'] + args)
    javadoc_dir = os.sep.join([_suite.dir, 'javadoc'])
    checkLinks(javadoc_dir)
    shutil.move(os.sep.join([javadoc_dir, 'index.html']), os.sep.join([javadoc_dir, 'overview-frames.html']))
    shutil.copy(os.sep.join([javadoc_dir, 'overview-summary.html']), os.sep.join([javadoc_dir, 'index.html']))

def checkLinks(javadocDir):
    href = re.compile('(?<=href=").*?(?=")')
    filesToCheck = {}
    for root, _, files in os.walk(javadocDir):
        for f in files:
            if f.endswith('.html'):
                html = os.path.join(root, f)
                content = open(html, 'r').read()
                for url in href.findall(content):
                    full = urljoin(html, url)
                    sectionIndex = full.find('#')
                    questionIndex = full.find('?')
                    minIndex = sectionIndex
                    if minIndex < 0:
                        minIndex = len(full)
                    if questionIndex >= 0 and questionIndex < minIndex:
                        minIndex = questionIndex
                    path = full[0:minIndex]

                    sectionNames = filesToCheck.get(path, [])
                    if sectionIndex >= 0:
                        s = full[sectionIndex + 1:]
                        sectionNames = sectionNames + [(html, s)]
                    else:
                        sectionNames = sectionNames + [(html, None)]

                    filesToCheck[path] = sectionNames

    err = False
    for referencedfile, sections in filesToCheck.items():
        if referencedfile.startswith('javascript:') or referencedfile.startswith('http:') or referencedfile.startswith('https:') or referencedfile.startswith('mailto:'):
            continue
        if not exists(referencedfile):
            mx.warn('Referenced file ' + referencedfile + ' does not exist. Referenced from ' + sections[0][0])
            err = True
        else:
            content = open(referencedfile, 'r').read()
            for path, s in sections:
                if not s == None:
                    where = content.find('name="' + s + '"')
                    if where == -1:
                        mx.warn('There should be section ' + s + ' in ' + referencedfile + ". Referenced from " + path)
                        err = True

    if err:
        mx.abort('There are wrong references in Javadoc')

def _path_args(depNames=None):
    """
    Gets the VM args for putting the dependencies named in `depNames` on the
    class path and module path (if running on JDK9 or later).

    :param names: a Dependency, str or list containing Dependency/str objects. If None,
           then all registered dependencies are used.
    """
    jdk = mx.get_jdk()
    if jdk.javaCompliance >= '1.9':
        modules = [as_java_module(dist, jdk) for dist in _suite.dists if get_java_module_info(dist)]
        if modules:
            # Partition resources between the class path and module path
            modulepath = []
            classpath = []
            cpEntryToModule = {m.dist.path : m for m in modules}

            for e in mx.classpath(depNames).split(os.pathsep):
                if cpEntryToModule.has_key(e):
                    modulepath.append(cpEntryToModule[e].jarpath)
                else:
                    classpath.append(e)
            # The Truffle modules must be eagerly loaded as they could be referenced from
            # the main class hence the --add-modules argument
            return ['--add-modules=' + ','.join([m.name for m in modules]), '--module-path=' + os.pathsep.join(modulepath), '-cp', os.pathsep.join(classpath)]
    return ['-cp', mx.classpath(depNames)]

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    if mx.get_jdk(tag='default').javaCompliance > '1.8':
        # This is required to access jdk.internal.module.Modules which
        # in turn allows us to dynamically open fields/methods to reflection.
        vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

        # This is required for the call to setAccessible in
        # TruffleTCK.testValueWithSource to work.
        vmArgs = vmArgs + ['--add-opens=com.oracle.truffle.truffle_api/com.oracle.truffle.api.vm=ALL-UNNAMED', '--add-modules=ALL-MODULE-PATH']
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)

def sl(args):
    """run an SL program"""
    vmArgs, slArgs = mx.extract_VM_args(args)
    mx.run_java(vmArgs + _path_args(["TRUFFLE_API", "com.oracle.truffle.sl", "com.oracle.truffle.sl.launcher"]) + ["com.oracle.truffle.sl.launcher.SLMain"] + slArgs)

def _truffle_gate_runner(args, tasks):
    jdk = mx.get_jdk(tag=mx.DEFAULT_JDK_TAG)
    with Task('Jackpot check', tasks) as t:
        if t: jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)
    if jdk.javaCompliance < '9':
        with Task('Truffle Javadoc', tasks) as t:
            if t: mx.javadoc(['--unified'])
    with Task('Truffle UnitTests', tasks) as t:
        if t: unittest(['--suite', 'truffle', '--enable-timing', '--verbose', '--fail-fast'])
    with Task('Truffle Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])
    with Task('File name length check', tasks) as t:
        if t: check_filename_length([])

mx_gate.add_gate_runner(_suite, _truffle_gate_runner)

mx.update_commands(_suite, {
    'javadoc' : [javadoc, '[SL args|@VM options]'],
    'sl' : [sl, '[SL args|@VM options]'],
})

def _is_graalvm(jdk):
    releaseFile = os.path.join(jdk.home, "release")
    if exists(releaseFile):
        with open(releaseFile) as f:
            pattern = re.compile('^GRAALVM_VERSION=*')
            for line in f.readlines():
                if pattern.match(line):
                    return True
    return False

def _unittest_config_participant_tck(config):

    def find_path_arg(vmArgs, prefix):
        for index in reversed(range(len(vmArgs) - 1)):
            if prefix in vmArgs[index]:
                return index, vmArgs[index][len(prefix):]
        return None, None

    def create_filter(requiredResource):
        def has_resource(dist):
            if dist.isJARDistribution() and exists(dist.path):
                with zipfile.ZipFile(dist.path, "r") as zf:
                    try:
                        zf.getinfo(requiredResource)
                    except KeyError:
                        return False
                    else:
                        return True
            else:
                return False
        return has_resource

    def import_visitor(suite, suite_import, predicate, collector, javaProperties, seenSuites, **extra_args):
        suite_collector(mx.suite(suite_import.name), predicate, collector, javaProperties, seenSuites)

    def suite_collector(suite, predicate, collector, javaProperties, seenSuites):
        if suite.name in seenSuites:
            return
        seenSuites.add(suite.name)
        suite.visit_imports(import_visitor, predicate=predicate, collector=collector, javaProperties=javaProperties, seenSuites=seenSuites)
        for dist in suite.dists:
            if predicate(dist):
                for distCpEntry in mx.classpath_entries(dist):
                    if hasattr(distCpEntry, "getJavaProperties"):
                        for key, value in dist.getJavaProperties().items():
                            javaProperties[key] = value
                    if distCpEntry.isJdkLibrary() or distCpEntry.isJreLibrary():
                        cpPath = distCpEntry.classpath_repr(mx.get_jdk(), resolve=True)
                    else:
                        cpPath = distCpEntry.classpath_repr(resolve=True)
                    if cpPath:
                        collector[cpPath] = None

    javaPropertiesToAdd = OrderedDict()
    providers = OrderedDict()
    suite_collector(mx.primary_suite(), create_filter("META-INF/services/org.graalvm.polyglot.tck.LanguageProvider"), providers, javaPropertiesToAdd, set())
    languages = OrderedDict()
    suite_collector(mx.primary_suite(), create_filter("META-INF/truffle/language"), languages, javaPropertiesToAdd, set())
    suite_collector(mx.primary_suite(), lambda dist: dist.isJARDistribution() and "TRUFFLE_TCK_INSTRUMENTATION" == dist.name and exists(dist.path), languages, javaPropertiesToAdd, set())
    vmArgs, mainClass, mainClassArgs = config
    cpIndex, cpValue = mx.find_classpath_arg(vmArgs)
    cpBuilder = OrderedDict()
    if cpValue:
        for cpElement in cpValue.split(os.pathsep):
            cpBuilder[cpElement] = None
    for providerCpElement in providers:
        cpBuilder[providerCpElement] = None

    if _is_graalvm(mx.get_jdk()):
        common = OrderedDict()
        suite_collector(mx.primary_suite(), lambda dist: dist.isJARDistribution() and "TRUFFLE_TCK_COMMON" == dist.name and exists(dist.path), common, javaPropertiesToAdd, set())
        tpIndex, tpValue = find_path_arg(vmArgs, '-Dtruffle.class.path.append=')
        tpBuilder = OrderedDict()
        if tpValue:
            for cpElement in tpValue.split(os.pathsep):
                tpBuilder[cpElement] = None
        for langCpElement in languages:
            tpBuilder[langCpElement] = None
        bpIndex, bpValue = find_path_arg(vmArgs, '-Xbootclasspath/a:')
        bpBuilder = OrderedDict()
        if bpValue:
            for cpElement in bpValue.split(os.pathsep):
                bpBuilder[cpElement] = None
        for bootCpElement in common:
            bpBuilder[bootCpElement] = None
            cpBuilder.pop(bootCpElement, None)
            tpBuilder.pop(bootCpElement, None)
        tpValue = '-Dtruffle.class.path.append=' + os.pathsep.join((e for e in tpBuilder))
        if tpIndex:
            vmArgs[tpIndex] = tpValue
        else:
            vmArgs.append(tpValue)
        bpValue = '-Xbootclasspath/a:' + os.pathsep.join((e for e in bpBuilder))
        if bpIndex:
            vmArgs[bpIndex] = bpValue
        else:
            vmArgs.append(bpValue)
    else:
        for langCpElement in languages:
            cpBuilder[langCpElement] = None
    cpValue = os.pathsep.join((e for e in cpBuilder))
    if cpIndex:
        vmArgs[cpIndex] = cpValue
    else:
        vmArgs.append("-cp")
        vmArgs.append(cpValue)
    for key, value in javaPropertiesToAdd.items():
        vmArgs.append("-D" + key + "=" + value)
    return (vmArgs, mainClass, mainClassArgs)

_shouldRunTCKParticipant = True

def should_add_tck_participant(shouldInstal):
    global _shouldRunTCKParticipant
    _shouldRunTCKParticipant = shouldInstal

"""
Merges META-INF/truffle/language and META-INF/truffle/instrument files.
This code is tightly coupled with the file format generated by
LanguageRegistrationProcessor and InstrumentRegistrationProcessor.
"""
class TruffleArchiveParticipant:
    PROPERTY_RE = re.compile(r'(language\d+|instrument\d+)(\..+)')

    def _truffle_metainf_file(self, arcname):
        if arcname == 'META-INF/truffle/language':
            return 'language'
        if arcname == 'META-INF/truffle/instrument':
            return 'instrument'
        return None

    def __opened__(self, arc, srcArc, services):
        self.settings = {}
        self.arc = arc

    def __add__(self, arcname, contents):
        metainfFile = self._truffle_metainf_file(arcname)
        if metainfFile:
            propertyRe = TruffleArchiveParticipant.PROPERTY_RE
            properties = {}
            for line in contents.strip().split('\n'):
                if not line.startswith('#'):
                    m = propertyRe.match(line)
                    assert m, 'line in ' + arcname + ' does not match ' + propertyRe.pattern + ': ' + line
                    enum = m.group(1)
                    prop = m.group(2)
                    properties.setdefault(enum, []).append(prop)

            self.settings.setdefault(metainfFile, []).append(properties)
            return True
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        for metainfFile, propertiesList in self.settings.iteritems():
            arcname = 'META-INF/truffle/' + metainfFile
            lines = []
            counter = 1
            for properties in propertiesList:
                for enum in sorted(properties.viewkeys()):
                    assert enum.startswith(metainfFile)
                    newEnum = metainfFile + str(counter)
                    counter += 1
                    for prop in properties[enum]:
                        lines.append(newEnum + prop)

            content = os.linesep.join(lines)
            self.arc.zf.writestr(arcname, content + os.linesep)

def mx_post_parse_cmd_line(opts):

    if _shouldRunTCKParticipant:
        mx_unittest.add_config_participant(_unittest_config_participant_tck)

    def _uses_truffle_dsl_processor(dist):
        for dep in dist.deps:
            if dep.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                return True
        truffle_dsl_processors = set()
        def visit(dep, edge):
            if dep is not dist and dep.isJavaProject():
                for ap in dep.annotation_processors():
                    if ap.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                        truffle_dsl_processors.add(ap)
        dist.walk_deps(visit=visit)
        return len(truffle_dsl_processors) != 0

    for d in mx.dependencies():
        if d.isJARDistribution():
            if _uses_truffle_dsl_processor(d):
                d.set_archiveparticipant(TruffleArchiveParticipant())

_debuggertestHelpSuffix = """
    TCK options:

      --tck-configuration                  configuration {default|debugger}
          compile                          executes TCK tests with immediate comilation
          debugger                         executes TCK tests with enabled debugalot instrument
          default                          executes TCK tests
"""

def _execute_debugger_test(testFilter, logFile, testEvaluation=False, unitTestOptions=None, jvmOptions=None):
    """
    Executes given unit tests with enabled debugalot instrument.
    The 'testFilter' argument is a filter unit test pattern.
    The 'logFile' argument is a file path to store the instrument output into.
    The 'testEvaluation' argument enables evaluation testing, default is False.
    The 'unitTestOptions' argument is a list of unit test options.
    The 'jvmOptions' argument is a list of VM options.
    """
    debugalot_options = ["-Dpolyglot.debugalot=true"]
    if testEvaluation:
        debugalot_options.append("-Dpolyglot.debugalot.Eval=true")
    debugalot_options.append("-Dpolyglot.debugalot.LogFile=" + logFile)
    args = []
    if unitTestOptions is not None:
        args = args + unitTestOptions
    args = args + ["--"]
    if jvmOptions is not None:
        args = args + jvmOptions
    args = args + debugalot_options
    args = args + testFilter
    unittest(args)


def _tck(args):
    """runs TCK tests"""

    parser = ArgumentParser(prog="mx tck", description="run the TCK tests", formatter_class=RawDescriptionHelpFormatter, epilog=_debuggertestHelpSuffix)
    parser.add_argument("--tck-configuration", help="TCK configuration", choices=["compile", "debugger", "default"], default="default")
    parsed_args, args = parser.parse_known_args(args)
    tckConfiguration = parsed_args.tck_configuration
    index = len(args)
    for arg in reversed(args):
        if arg.startswith("-"):
            break
        index = index - 1
    args_no_tests = args[0:index]
    tests = args[index:len(args)]
    if len(tests) == 0:
        tests = ["com.oracle.truffle.tck.tests"]
    index = len(args_no_tests)
    for arg in reversed(args_no_tests):
        if arg.startswith("--"):
            break
        index = index - 1
    unitTestOptions = args_no_tests[0:max(index-1, 0)]
    jvmOptions = args_no_tests[index:len(args_no_tests)]
    if tckConfiguration == "default":
        unittest(unitTestOptions + ["--"] + jvmOptions + tests)
    elif tckConfiguration == "debugger":
        with mx.SafeFileCreation(os.path.join(tempfile.gettempdir(), "debugalot")) as sfc:
            _execute_debugger_test(tests, sfc.tmpPath, False, unitTestOptions, jvmOptions)
    elif tckConfiguration == "compile":
        if not _is_graalvm(mx.get_jdk()):
            mx.abort("The 'compile' TCK configuration requires graalvm execution, run with --java-home=<path_to_graalvm>.")
        unittest(unitTestOptions + ["--"] + jvmOptions + ["-Dgraal.TruffleCompileImmediately=true", "-Dgraal.TruffleCompilationExceptionsAreThrown=true"] + tests)

mx.update_commands(_suite, {
    'tck' : [_tck, "[--tck-configuration {default|debugger}] [unittest options] [--] [VM options] [filters...]", _debuggertestHelpSuffix]
})

def check_filename_length(args):
    """check that all file name lengths are short enough for eCryptfs"""
    # For eCryptfs, see https://bugs.launchpad.net/ecryptfs/+bug/344878
    parser = ArgumentParser(prog="mx check-filename-length", description="Check file name length")
    parser.parse_known_args(args)
    max_length = 143
    too_long = []
    for _, _, filenames in os.walk('.'):
        for filename in filenames:
            if len(filename) > max_length:
                too_long.append(filename)
    if too_long:
        mx.log_error("The following file names are too long for eCryptfs: ")
        for x in too_long:
            mx.log_error(x)
        mx.abort("File names that are too long where found. Ensure all file names are under %d characters long." % max_length)

mx.update_commands(_suite, {
    'check-filename-length' : [check_filename_length, ""],
})
