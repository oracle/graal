#
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
import os
import re
import tempfile
import sys
import zipfile
from argparse import ArgumentParser, RawDescriptionHelpFormatter
from collections import OrderedDict
from os.path import exists

import mx
import mx_benchmark
import mx_gate
import mx_native
import mx_sdk
import mx_sdk_vm
import mx_unittest
import tck
from mx_gate import Task
from mx_jackpot import jackpot
from mx_javamodules import as_java_module, get_java_module_info
from mx_sigtest import sigtest
from mx_unittest import unittest

# Temporary imports and (re)definitions while porting mx from Python 2 to Python 3
if sys.version_info[0] < 3:
    from urlparse import urljoin as _urllib_urljoin
    def _decode(x):
        return x
    def _encode(x):
        return x
else:
    from urllib.parse import urljoin as _urllib_urljoin # pylint: disable=unused-import,no-name-in-module
    def _decode(x):
        return x.decode()
    def _encode(x):
        return x.encode()


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
    extraArgs = mx_sdk.build_oracle_compliant_javadoc_args(_suite, 'GraalVM', 'Truffle')
    mx.javadoc(['--unified', '--exclude-packages', 'com.oracle.truffle.tck,com.oracle.truffle.tck.impl'] + extraArgs + args)
    javadoc_dir = os.sep.join([_suite.dir, 'javadoc'])
    checkLinks(javadoc_dir)

def checkLinks(javadocDir):
    href = re.compile('(?<=href=").*?(?=")')
    filesToCheck = {}
    for root, _, files in os.walk(javadocDir):
        for f in files:
            if f.endswith('.html'):
                html = os.path.join(root, f)
                content = open(html, 'r').read()
                for url in href.findall(content):
                    full = _urllib_urljoin(html, url)
                    sectionIndex = full.find('#')
                    questionIndex = full.find('?')
                    minIndex = sectionIndex
                    if minIndex < 0:
                        minIndex = len(full)
                    if 0 <= questionIndex < minIndex:
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
                if not s is None:
                    whereName = content.find('name="' + s + '"')
                    whereId = content.find('id="' + s + '"')
                    if whereName == -1 and whereId == -1:
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
                if e in cpEntryToModule:
                    modulepath.append(cpEntryToModule[e].jarpath)
                else:
                    classpath.append(e)
            # The Truffle modules must be eagerly loaded as they could be referenced from
            # the main class hence the --add-modules argument
            return ['--add-modules=' + ','.join([m.name for m in modules]), '--module-path=' + os.pathsep.join(modulepath), '-cp', os.pathsep.join(classpath)]
    return ['-cp', mx.classpath(depNames)]

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    jdk = mx.get_jdk(tag='default')
    if jdk.javaCompliance > '1.8':
        # This is required to access jdk.internal.module.Modules which
        # in turn allows us to dynamically open fields/methods to reflection.
        vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

        # The arguments below are only actually needed if Truffle is deployed as a
        # module. However, that's determined by the compiler suite which may not
        # be present. In that case, adding these options results in annoying
        # but harmless messages from the VM:
        #
        #  WARNING: Unknown module: org.graalvm.truffle specified to --add-opens
        #

        # Needed for com.oracle.truffle.api.dsl.test.TestHelper#instrumentSlowPath
        vmArgs = vmArgs + ['--add-opens=org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED']

        # This is required for the call to setAccessible in
        # TruffleTCK.testValueWithSource to work.
        vmArgs = vmArgs + ['--add-opens=org.graalvm.truffle/com.oracle.truffle.polyglot=ALL-UNNAMED', '--add-modules=ALL-MODULE-PATH']

        # Needed for object model tests.
        vmArgs = vmArgs + ['--add-opens=org.graalvm.truffle/com.oracle.truffle.object=ALL-UNNAMED']

    config = (vmArgs, mainClass, mainClassArgs)
    if _shouldRunTCKParticipant:
        config = _unittest_config_participant_tck(config)
    return config

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
            if t: javadoc([])
    with Task('Truffle UnitTests', tasks) as t:
        if t: unittest(['--suite', 'truffle', '--enable-timing', '--verbose', '--fail-fast'])
    with Task('Truffle Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])
    with Task('File name length check', tasks) as t:
        if t: check_filename_length([])
    with Task('Check Copyrights', tasks) as t:
        if t:
            if mx.checkcopyrights(['--primary']) != 0:
                t.abort('Copyright errors found. Please run "mx checkcopyrights --primary -- --fix" to fix them.')

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

def _collect_class_path_entries(cp_entries_filter, entries_collector, properties_collector):
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
                        for key, value in distCpEntry.getJavaProperties().items():
                            javaProperties[key] = value
                    if distCpEntry.isJdkLibrary() or distCpEntry.isJreLibrary():
                        cpPath = distCpEntry.classpath_repr(mx.get_jdk(), resolve=True)
                    else:
                        cpPath = distCpEntry.classpath_repr(resolve=True)
                    if cpPath:
                        collector[cpPath] = None
    suite_collector(mx.primary_suite(), cp_entries_filter, entries_collector, properties_collector, set())

def _collect_class_path_entries_by_resource(requiredResources, entries_collector, properties_collector):
    """
    Collects class path for JAR distributions containing any resource from requiredResources.

    :param requiredResources: an iterable of resources. At least one of them has to exist to include the
            distribution class path entries.
    :param entries_collector: the list to add the class paths entries into.
    :properties_collector: the list to add the distribution Java properties into.
    """
    def has_resource(dist):
        if dist.isJARDistribution() and exists(dist.path):
            with zipfile.ZipFile(dist.path, "r") as zf:
                for requiredResource in requiredResources:
                    try:
                        zf.getinfo(requiredResource)
                    except KeyError:
                        pass
                    else:
                        return True
                return False
        else:
            return False
    _collect_class_path_entries(has_resource, entries_collector, properties_collector)

def _collect_class_path_entries_by_name(distributionName, entries_collector, properties_collector):
    cp_filter = lambda dist: dist.isJARDistribution() and  dist.name == distributionName and exists(dist.path)
    _collect_class_path_entries(cp_filter, entries_collector, properties_collector)

def _collect_languages(entries_collector, properties_collector):
    _collect_class_path_entries_by_resource(["META-INF/truffle/language", "META-INF/services/com.oracle.truffle.api.TruffleLanguage$Provider"], entries_collector, properties_collector)

def _collect_tck_providers(entries_collector, properties_collector):
    _collect_class_path_entries_by_resource(["META-INF/services/org.graalvm.polyglot.tck.LanguageProvider"], entries_collector, properties_collector)

def _unittest_config_participant_tck(config):

    def find_path_arg(vmArgs, prefix):
        for index in reversed(range(len(vmArgs) - 1)):
            if prefix in vmArgs[index]:
                return index, vmArgs[index][len(prefix):]
        return None, None

    javaPropertiesToAdd = OrderedDict()
    providers = OrderedDict()
    _collect_tck_providers(providers, javaPropertiesToAdd)
    languages = OrderedDict()
    _collect_languages(languages, javaPropertiesToAdd)
    _collect_class_path_entries_by_name("TRUFFLE_TCK_INSTRUMENTATION", languages, javaPropertiesToAdd)
    vmArgs, mainClass, mainClassArgs = config
    cpIndex, cpValue = mx.find_classpath_arg(vmArgs)
    cpBuilder = OrderedDict()
    if cpValue:
        for cpElement in cpValue.split(os.pathsep):
            cpBuilder[cpElement] = None
    for providerCpElement in providers:
        cpBuilder[providerCpElement] = None

    if _is_graalvm(mx.get_jdk()):
        boot_cp = OrderedDict()
        _collect_class_path_entries_by_name("TRUFFLE_TCK_COMMON", boot_cp, javaPropertiesToAdd)
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
        for bootCpElement in boot_cp:
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
    providersRE = re.compile(r'(?:META-INF/versions/([1-9][0-9]*)/)?META-INF/truffle-registrations/(.+)')

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __add__(self, arcname, contents): # pylint: disable=unexpected-special-method-signature
        m = TruffleArchiveParticipant.providersRE.match(arcname)
        if m:
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
        return False

def mx_post_parse_cmd_line(opts):

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

def execute_tck(graalvm_home, mode='default', language_filter=None, values_filter=None, tests_filter=None, vm_args=None):
    """
    Executes Truffle TCK with all TCK providers reachable from the primary suite and all languages installed in the given GraalVM.

    :param graalvm_home: a path to GraalVM
    :param mode: a name of TCK mode,
        'default' - executes the test with default GraalVM configuration,
        'compile' - compiles the tests before execution
    :param language_filter: the language id, limits TCK tests to certain language
    :param values_filter: an iterable of value constructors language ids, limits TCK values to certain language(s)
    :param tests_filter: a substring of TCK test name or an iterable of substrings of TCK test names
    :param vm_args: iterable containing additional Java VM args
    """
    cp = OrderedDict()
    _collect_tck_providers(cp, dict())
    truffle_cp = OrderedDict()
    _collect_class_path_entries_by_name("TRUFFLE_TCK_INSTRUMENTATION", truffle_cp, dict())
    _collect_class_path_entries_by_name("TRUFFLE_SL", truffle_cp, dict())
    boot_cp = OrderedDict()
    _collect_class_path_entries_by_name("TRUFFLE_TCK_COMMON", boot_cp, dict())
    return tck.execute_tck(graalvm_home, mode=tck.Mode.for_name(mode), language_filter=language_filter, values_filter=values_filter,
        tests_filter=tests_filter, cp=cp.keys(), truffle_cp=truffle_cp.keys(), boot_cp=boot_cp, vm_args=vm_args)


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
    has_separator_arg = False
    for arg in reversed(args_no_tests):
        if arg.startswith("--"):
            if arg == "--":
                has_separator_arg = True
            break
        index = index - 1
    unitTestOptions = args_no_tests[0:max(index - (1 if has_separator_arg else 0), 0)]
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
    'tck': [_tck, "[--tck-configuration {default|debugger}] [unittest options] [--] [VM options] [filters...]", _debuggertestHelpSuffix]
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

COPYRIGHT_HEADER_UPL = """\
/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
// Checkstyle: stop
//@formatter:off
{0}
"""
PTRN_SUPPRESS_WARNINGS = re.compile("^@SuppressWarnings.*$", re.MULTILINE)
PTRN_LOCALCTXT_CAST = re.compile(r"\(\([a-zA-Z_]*Context\)_localctx\)")
PTRN_TOKEN_CAST = re.compile(r"\(Token\)_errHandler.recoverInline\(this\)")

def create_dsl_parser(args=None, out=None):
    """create the DSL expression parser using antlr"""
    create_parser("com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.expression", "Expression", COPYRIGHT_HEADER_UPL, args, out)

def create_sl_parser(args=None, out=None):
    """create the SimpleLanguage parser using antlr"""
    create_parser("com.oracle.truffle.sl", "com.oracle.truffle.sl.parser", "SimpleLanguage", COPYRIGHT_HEADER_UPL, args, out)

def create_parser(grammar_project, grammar_package, grammar_name, copyright_template, args=None, out=None, postprocess=None):
    """create the DSL expression parser using antlr"""
    grammar_dir = os.path.join(mx.project(grammar_project).source_dirs()[0], *grammar_package.split(".")) + os.path.sep
    mx.run_java(mx.get_runtime_jvm_args(['ANTLR4_COMPLETE']) + ["org.antlr.v4.Tool", "-package", grammar_package, "-no-listener"] + args + [grammar_dir + grammar_name + ".g4"], out=out)
    for filename in [grammar_dir + grammar_name + "Lexer.java", grammar_dir + grammar_name + "Parser.java"]:
        with open(filename, 'r') as content_file:
            content = content_file.read()
        # remove first line
        content = "\n".join(content.split("\n")[1:])
        # modify SuppressWarnings to remove useless entries
        content = PTRN_SUPPRESS_WARNINGS.sub('@SuppressWarnings("all")', content)
        # remove useless casts
        content = PTRN_LOCALCTXT_CAST.sub('_localctx', content)
        content = PTRN_TOKEN_CAST.sub('_errHandler.recoverInline(this)', content)
        # add copyright header
        content = copyright_template.format(content)
        # user provided post-processing hook:
        if postprocess is not None:
            content = postprocess(content)
        with open(filename, 'w') as content_file:
            content_file.write(content)


class LibffiBuilderProject(mx.AbstractNativeProject, mx_native.NativeDependency):  # pylint: disable=too-many-ancestors
    """Project for building libffi from source.

    The build is performed by:
        1. Extracting the sources,
        2. Applying the platform dependent patches, and
        3. Invoking the platform dependent builder that we delegate to.
    """

    def __init__(self, suite, name, deps, workingSets, **kwargs):
        subDir = 'src'
        srcDirs = ['patches']
        d = mx.join(suite.dir, subDir, name)
        super(LibffiBuilderProject, self).__init__(suite, name, subDir, srcDirs, deps, workingSets, d, **kwargs)

        self.out_dir = self.get_output_root()
        if mx.get_os() == 'windows':
            self.delegate = mx_native.DefaultNativeProject(suite, name, subDir, [], [], None,
                                                           mx.join(self.out_dir, 'libffi-3.2.1'),
                                                           'static_lib',
                                                           deliverable='ffi',
                                                           cflags=['-MD', '-O2'])
            self.delegate._source = dict(tree=['include',
                                               'src',
                                               mx.join('src', 'x86')],
                                         files={'.h': [mx.join('include', 'ffi.h'),
                                                       mx.join('include', 'ffitarget.h'),
                                                       mx.join('src', 'fficonfig.h'),
                                                       mx.join('src', 'ffi_common.h')],
                                                '.c': [mx.join('src', 'closures.c'),
                                                       mx.join('src', 'java_raw_api.c'),
                                                       mx.join('src', 'prep_cif.c'),
                                                       mx.join('src', 'raw_api.c'),
                                                       mx.join('src', 'types.c'),
                                                       mx.join('src', 'x86', 'ffi.c')],
                                                '.S': [mx.join('src', 'x86', 'win64.S')]})
        else:
            class LibtoolNativeProject(mx.NativeProject,  # pylint: disable=too-many-ancestors
                                       mx_native.NativeDependency):
                include_dirs = property(lambda self: [mx.join(self.getOutput(), 'include')])
                libs = property(lambda self: [next(self.getArchivableResults(single=True))[0]])

                def getArchivableResults(self, use_relpath=True, single=False):
                    for file_path, archive_path in super(LibtoolNativeProject, self).getArchivableResults(use_relpath):
                        path_in_lt_objdir = mx.basename(mx.dirname(file_path)) == '.libs'
                        yield file_path, mx.basename(archive_path) if path_in_lt_objdir else archive_path
                        if single:
                            assert path_in_lt_objdir, 'the first build result must be from LT_OBJDIR'
                            break

            self.delegate = LibtoolNativeProject(suite, name, subDir, [], [], None,
                                                 ['.libs/libffi.a',
                                                  'include/ffi.h',
                                                  'include/ffitarget.h'],
                                                 mx.join(self.out_dir, 'libffi-build'),
                                                 mx.join(self.out_dir, 'libffi-3.2.1'))
            self.delegate.buildEnv = dict(
                SOURCES=mx.basename(self.delegate.dir),
                OUTPUT=mx.basename(self.delegate.getOutput()),
                CONFIGURE_ARGS=' '.join([
                    '--disable-dependency-tracking',
                    '--disable-shared',
                    '--with-pic',
                    'CFLAGS="{}"'.format(' '.join(
                        ['-g', '-O3'] + (['-m64'] if mx.get_os() == 'solaris' else [])
                    )),
                ])
            )

        self.buildDependencies = self.delegate.buildDependencies
        self.include_dirs = self.delegate.include_dirs
        self.libs = self.delegate.libs

    @property
    def sources(self):
        assert len(self.deps) == 1, '{} must depend only on its sources'.format(self.name)
        return self.deps[0]

    @property
    def patches(self):
        """A list of patches that will be applied during a build."""
        os_arch_dir = mx.join(self.source_dirs()[0], '{}-{}'.format(mx.get_os(), mx.get_arch()))
        if mx.exists(os_arch_dir):
            return [mx.join(os_arch_dir, patch) for patch in os.listdir(os_arch_dir)]

        others_dir = mx.join(self.source_dirs()[0], 'others')
        return [mx.join(others_dir, patch) for patch in os.listdir(others_dir)]

    def getBuildTask(self, args):
        return LibffiBuildTask(args, self)

    def getArchivableResults(self, use_relpath=True, single=False):
        return self.delegate.getArchivableResults(use_relpath, single)


class LibffiBuildTask(mx.AbstractNativeBuildTask):
    def __init__(self, args, project):
        super(LibffiBuildTask, self).__init__(args, project)
        self.delegate = project.delegate.getBuildTask(args)

    def __str__(self):
        return 'Building {}'.format(self.subject.name)

    def needsBuild(self, newestInput):
        is_needed, reason = super(LibffiBuildTask, self).needsBuild(newestInput)
        if is_needed:
            return True, reason

        output = self.newestOutput()
        newest_patch = mx.TimeStampFile.newest(self.subject.patches)
        if newest_patch and output.isOlderThan(newest_patch):
            return True, '{} is older than {}'.format(output, newest_patch)

        return False, 'all files are up to date'

    def newestOutput(self):
        output = self.delegate.newestOutput()
        return None if output and not output.exists() else output

    def build(self):
        assert not mx.exists(self.subject.out_dir), '{} must be cleaned before build'.format(self.subject.name)

        mx.log('Extracting {}...'.format(self.subject.sources))
        mx.Extractor.create(self.subject.sources.get_path(False)).extract(self.subject.out_dir)

        mx.log('Applying patches...')
        git_apply = ['git', 'apply', '--whitespace=nowarn', '--unsafe-paths', '--directory',
                     os.path.relpath(self.subject.delegate.dir, self.subject.suite.vc_dir)]
        for patch in self.subject.patches:
            mx.run(git_apply + [patch], cwd=self.subject.suite.vc_dir)

        self.delegate.logBuild()
        self.delegate.build()

    def clean(self, forBuild=False):
        mx.rmtree(self.subject.out_dir, ignore_errors=True)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Truffle',
    short_name='tfl',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Graal SDK'],
    jar_distributions=[
        'truffle:TRUFFLE_DSL_PROCESSOR',
        'truffle:TRUFFLE_TCK',
    ],
    jvmci_parent_jars=[
        'truffle:TRUFFLE_API',
        'truffle:LOCATOR',
    ],
))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=_suite,
    name='Truffle Macro',
    short_name='tflm',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    support_distributions=['truffle:TRUFFLE_GRAALVM_SUPPORT']
))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle NFI',
    short_name='nfi',
    dir_name='nfi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TRUFFLE_NFI'],
    support_distributions=['truffle:TRUFFLE_NFI_GRAALVM_SUPPORT'],
    support_headers_distributions=['truffle:TRUFFLE_NFI_GRAALVM_HEADERS_SUPPORT'],
    support_libraries_distributions=['truffle:TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT'],
    installable=False,
))


mx.update_commands(_suite, {
    'check-filename-length' : [check_filename_length, ""],
    'create-dsl-parser' : [create_dsl_parser, "create the DSL expression parser using antlr"],
    'create-sl-parser' : [create_sl_parser, "create the SimpleLanguage parser using antlr"],
})
