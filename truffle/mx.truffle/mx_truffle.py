#
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import fnmatch
import itertools
import json
import os
import re
import shutil
import tempfile
import difflib
import zipfile
from argparse import Action, ArgumentParser, RawDescriptionHelpFormatter
from os.path import dirname, exists, isdir, join, abspath
from urllib.parse import urljoin # pylint: disable=unused-import,no-name-in-module

import mx
import mx_benchmark
import mx_gate
import mx_native
import mx_sdk
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_subst
import mx_unittest
import mx_jardistribution
import mx_pomdistribution
import mx_util
from mx_gate import Task
from mx_javamodules import as_java_module, get_module_name
from mx_sigtest import sigtest
from mx_unittest import unittest

_suite = mx.suite('truffle')

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_shaded import ShadedLibraryProject # pylint: disable=unused-import

class JMHRunnerTruffleBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite):

    def name(self):
        return "truffle"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def extraVmArgs(self):
        extraVmArgs = super(JMHRunnerTruffleBenchmarkSuite, self).extraVmArgs()
        extraVmArgs.extend(_open_module_exports_args())
        # com.oracle.truffle.api.benchmark.InterpreterCallBenchmark$BenchmarkState needs DefaultTruffleRuntime
        extraVmArgs.append('--add-exports=org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED')
        return extraVmArgs

mx_benchmark.add_bm_suite(JMHRunnerTruffleBenchmarkSuite())
#mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm("server", "default"), priority=3)

def javadoc(args, vm=None):
    """build the Javadoc for all API packages"""
    extraArgs = mx_sdk.build_oracle_compliant_javadoc_args(_suite, 'GraalVM', 'Truffle')
    projects = [
        'org.graalvm.collections',
        'org.graalvm.word',
        'org.graalvm.options',
        'org.graalvm.nativeimage',
        'org.graalvm.home',
        'org.graalvm.polyglot',
        'com.oracle.svm.core.annotate',
        'com.oracle.truffle.api',
        'com.oracle.truffle.api.dsl',
        'com.oracle.truffle.api.profiles',
        'com.oracle.truffle.api.utilities',
        'com.oracle.truffle.api.library',
        'com.oracle.truffle.api.strings',
        'com.oracle.truffle.api.interop',
        'com.oracle.truffle.api.exception',
        'com.oracle.truffle.api.instrumentation',
        'com.oracle.truffle.api.debug',
        'com.oracle.truffle.api.object',
        'com.oracle.truffle.api.staticobject',
    ]
    mx.javadoc(['--unified', '--disallow-all-warnings','--projects', ','.join(projects)] + extraArgs + args, includeDeps=False)
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
                    full = urljoin(html, url)
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
                    s = s.replace("%3C", "&lt;")
                    s = s.replace("%3E", "&gt;")
                    s = s.replace("%5B", "[")
                    s = s.replace("%5D", "]")
                    whereName = content.find('name="' + s + '"')
                    whereId = content.find('id="' + s + '"')
                    if whereName == -1 and whereId == -1:
                        mx.warn('There should be section ' + s + ' in ' + referencedfile + ". Referenced from " + path)
                        err = True

    if err:
        mx.abort('There are wrong references in Javadoc')

def _open_module_exports_args():
    """
    Gets the VM args for exporting all Truffle API packages on JDK9 or later.
    The default Truffle moduleInfo is opened but closed version is deployed into graalvm.
    To run benchmarks on the graalvm we need to open the closed Truffle packages.
    """
    assert mx.get_jdk().javaCompliance >= '1.9'
    truffle_api_dist = mx.distribution('TRUFFLE_API')
    truffle_api_module_name = truffle_api_dist.moduleInfo['name']
    module_info_open_exports = getattr(truffle_api_dist, 'moduleInfo')['exports']
    args = []
    for export in module_info_open_exports:
        if ' to ' in export: # Qualified exports
            package, targets = export.split(' to ')
            targets = targets.replace(' ', '')
        else: # Unqualified exports
            package = export
            targets = 'ALL-UNNAMED'
        args.append('--add-exports=' + truffle_api_module_name + '/' + package + '=' + targets)
    return args


class TruffleUnittestConfig(mx_unittest.MxUnittestConfig):

    _use_enterprise_polyglot = True
    _use_optimized_runtime = True

    def __init__(self):
        super(TruffleUnittestConfig, self).__init__('truffle')

    def processDeps(self, deps):
        dist_names = resolve_truffle_dist_names(TruffleUnittestConfig._use_optimized_runtime, TruffleUnittestConfig._use_enterprise_polyglot)
        mx.logv(f'Adding Truffle runtime distributions {", ".join(dist_names)} to unittest dependencies.')
        for dist_name in dist_names:
            deps.add(mx.distribution(dist_name))

    def apply(self, config):
        vmArgs, mainClass, mainClassArgs = config

        # This is required to access jdk.internal.module.Modules which
        # in turn allows us to dynamically open fields/methods to reflection.
        vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle.compiler/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle.runtime/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.polyglot/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.sl/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/*=org.graalvm.sl'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.shadowed.jcodings/*=ALL-UNNAMED'])
        return (vmArgs, mainClass, mainClassArgs)


mx_unittest.register_unittest_config(TruffleUnittestConfig())


class _DisableEnterpriseTruffleAction(Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        kwargs['nargs'] = 0
        super(_DisableEnterpriseTruffleAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        TruffleUnittestConfig._use_enterprise_polyglot = False

class _DisableOptimizedRuntimeAction(Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        kwargs['nargs'] = 0
        super(_DisableOptimizedRuntimeAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        TruffleUnittestConfig._use_optimized_runtime = False


mx_unittest.add_unittest_argument('--disable-truffle-enterprise', default=False, help='Disables the automatic inclusion of Enterprise Truffle in unittest dependencies.', action=_DisableEnterpriseTruffleAction)
mx_unittest.add_unittest_argument('--disable-truffle-optimized-runtime', default=False, help='Disables the automatic inclusion of Truffle optimized runtime in unittest dependencies.', action=_DisableOptimizedRuntimeAction)


class NFITestConfig:

    def __init__(self, name, runtime_deps):
        self.name = name
        self.runtime_deps = runtime_deps

    def vm_args(self):
        return []


class _LibFFINFITestConfig(NFITestConfig):

    def __init__(self):
        super(_LibFFINFITestConfig, self).__init__('libffi', [])


class _PanamaNFITestConfig(NFITestConfig):

    def __init__(self):
        super(_PanamaNFITestConfig, self).__init__('panama', ['TRUFFLE_NFI_PANAMA'])

    def vm_args(self):
        testPath = mx.distribution('TRUFFLE_TEST_NATIVE').output
        args = [
            '-Dnative.test.backend=panama',
            '-Dnative.test.path.panama=' + testPath
        ]
        return args


_nfi_test_configs = {}


def register_nfi_test_config(cfg):
    name = cfg.name
    assert not name in _nfi_test_configs, 'duplicate nfi test config'
    _nfi_test_configs[name] = cfg


register_nfi_test_config(_LibFFINFITestConfig())
register_nfi_test_config(_PanamaNFITestConfig())


class _TruffleNFIUnittestConfig(mx_unittest.MxUnittestConfig):

    runtimeConfig = None

    def __init__(self, name='truffle-nfi'):
        super(_TruffleNFIUnittestConfig, self).__init__(name)

    def processDeps(self, deps):
        deps.update({mx.distribution(runtime_dep) for runtime_dep in _TruffleNFIUnittestConfig.runtimeConfig.runtime_deps})

    def apply(self, config):
        vmArgs, mainClass, mainClassArgs = config
        # Add runtimeConfig vm args
        vmArgs = vmArgs + _TruffleNFIUnittestConfig.runtimeConfig.vm_args()
        return (vmArgs, mainClass, mainClassArgs)


mx_unittest.register_unittest_config(_TruffleNFIUnittestConfig())


class _SelectNFIConfigAction(Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        _TruffleNFIUnittestConfig.runtimeConfig = _nfi_test_configs["libffi"]
        super(_SelectNFIConfigAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        if values in _nfi_test_configs:
            _TruffleNFIUnittestConfig.runtimeConfig = _nfi_test_configs[values]
        else:
            mx.abort(f"NFI test config {values} unknown!")


mx_unittest.add_unittest_argument('--nfi-config', default=None, help='Select test engine configuration for the nfi unittests.', metavar='<config>', action=_SelectNFIConfigAction)

# simple utility that returns the right distribution names to use
# for building a language module path
def resolve_truffle_dist_names(use_optimized_runtime=True, use_enterprise=True):
    if use_optimized_runtime:
        enterprise_dist = _get_enterprise_truffle()
        if enterprise_dist and use_enterprise:
            return ['graal-enterprise:TRUFFLE_ENTERPRISE']
        else:
            return ['truffle:TRUFFLE_RUNTIME']
    else:
        return ['truffle:TRUFFLE_API']

def _get_enterprise_truffle():
    return mx.distribution('graal-enterprise:TRUFFLE_ENTERPRISE', False)

def resolve_sl_dist_names(use_optimized_runtime=True, use_enterprise=True):
    return ['TRUFFLE_SL', 'TRUFFLE_SL_LAUNCHER', 'TRUFFLE_NFI_LIBFFI'] + resolve_truffle_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)

def sl(args):
    """run an SL program"""
    vm_args, sl_args = mx.extract_VM_args(args)
    return mx.run(_sl_command(mx.get_jdk(tag="graalvm"), vm_args, sl_args, force_cp=False))


def _sl_command(jdk, vm_args, sl_args, use_optimized_runtime=True, use_enterprise=True, force_cp=False):
    dist_names = resolve_sl_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)
    if force_cp:
        main_class = ["com.oracle.truffle.sl.launcher.SLMain"]
    else:
        main_class = ["--module", "org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain"]
    return [jdk.java] + vm_args + mx.get_runtime_jvm_args(names=dist_names, force_cp=force_cp) + main_class + sl_args


def slnative(args):
    """build a native image of an SL program"""
    parser = ArgumentParser(prog='mx slnative', description='Builds and executes native SL image.', usage='mx slnative [--target-folder <folder>|SL args|@VM options]')
    parser.add_argument('--target-folder', help='Folder where the SL executable will be generated.', default=None)
    parsed_args, args = parser.parse_known_args(args)
    vm_args, sl_args = mx.extract_VM_args(args)
    target_dir = parsed_args.target_folder if parsed_args.target_folder else tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    image = _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, force_cp=False, hosted_assertions=False)
    mx.log("Image build completed. Running {}".format(" ".join([image] + sl_args)))
    result = mx.run([image] + sl_args)
    return result

def _native_image(jdk):
    native_image_path = jdk.exe_path('native-image')
    if not exists(native_image_path):
        native_image_path = os.path.join(jdk.home, 'bin', mx.cmd_suffix('native-image'))
    if not exists(native_image_path):
        mx.abort("No native-image installed in GraalVM {}. Switch to an environment that has an installed native-image command.".format(jdk.home))
    return native_image_path

def _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, use_enterprise=True, force_cp=False, hosted_assertions=True):
    native_image_args = list(vm_args)
    native_image_path = _native_image(jdk)
    target_path = os.path.join(target_dir, mx.exe_suffix('sl'))
    dist_names = resolve_sl_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)

    if hosted_assertions:
        native_image_args += ["-J-ea", "-J-esa"]

    native_image_args += mx.get_runtime_jvm_args(names=dist_names, force_cp=force_cp)
    if force_cp:
        native_image_args += ["com.oracle.truffle.sl.launcher.SLMain"]
    else:
        native_image_args += ["--module", "org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain"]
    native_image_args += [target_path]
    mx.log("Running {} {}".format(mx.exe_suffix('native-image'), " ".join(native_image_args)))
    mx.run([native_image_path] + native_image_args)
    return target_path

def _truffle_gate_runner(args, tasks):
    jdk = mx.get_jdk(tag=mx.DEFAULT_JDK_TAG)
    if jdk.javaCompliance < '9':
        with Task('Truffle Javadoc', tasks) as t:
            if t: javadoc([])
    with Task('File name length check', tasks) as t:
        if t: check_filename_length([])
    with Task('Truffle Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])
    with Task('Truffle UnitTests', tasks) as t:
        if t: unittest(list(['--suite', 'truffle', '--enable-timing', '--verbose', '--max-class-failures=25']))
    if jdk.javaCompliance >= '22':
        with Task('Truffle NFI tests with Panama Backend', tasks) as t:
            if t:
                unittest(['com.oracle.truffle.nfi.test', '--enable-timing', '--verbose', '--nfi-config=panama'])
    with Task('TruffleString UnitTests without Java String Compaction', tasks) as t:
        if t: unittest(list(['-XX:-CompactStrings', '--suite', 'truffle', '--enable-timing', '--verbose', '--max-class-failures=25', 'com.oracle.truffle.api.strings.test']))
    if os.getenv('DISABLE_DSL_STATE_BITS_TESTS', 'false').lower() != 'true':
        with Task('Truffle DSL max state bit tests', tasks) as t:
            if t:
                _truffle_gate_state_bitwidth_tests()
    with Task('Validate parsers', tasks) as t:
        if t: validate_parsers()


# Run in vm suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle ModulePath Unit Tests Optimized"
def truffle_jvm_module_path_optimized_unit_tests_gate():
    _truffle_jvm_module_path_unit_tests_gate()


# Run in vm suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle ModulePath Unit Tests Fallback"
def truffle_jvm_module_path_fallback_unit_tests_gate():
    _truffle_jvm_module_path_unit_tests_gate(['--disable-truffle-optimized-runtime'])


def _truffle_jvm_module_path_unit_tests_gate(additional_unittest_options=None):
    if additional_unittest_options is None:
        additional_unittest_options = []
    unittest(list(['--suite', 'truffle', '--use-graalvm', '--enable-timing', '--verbose', '--max-class-failures=25'] + additional_unittest_options))


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle ClassPath Unit Tests Optimized"
def truffle_jvm_class_path_optimized_unit_tests_gate():
    _truffle_jvm_class_path_unit_tests_gate()


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle ClassPath Unit Tests Fallback"
def truffle_jvm_class_path_fallback_unit_tests_gate():
    _truffle_jvm_class_path_unit_tests_gate(['--disable-truffle-optimized-runtime'])


def _truffle_jvm_class_path_unit_tests_gate(additional_unittest_options=None):
    if additional_unittest_options is None:
        additional_unittest_options = []
    unittest(list(['--suite', 'truffle', '--use-graalvm', '--enable-timing', '--force-classpath', '--verbose',
                   '--max-class-failures=25'] + additional_unittest_options))

@mx.command(_suite.name, 'native-truffle-unittest')
def native_truffle_unittest(args):
    """
    This method builds a native image of JUnit tests and executes them. Unlike native_unittest from the SubstrateVM suite,
    this method can utilize an existing GraalVM instance set as the JAVA_HOME environment variable. Consequently,
    the gate employing this method does not require the construction of a full GraalVM. It makes this method suitable
    for testing compatibility with older GraalVM releases. The Native Build Tools (NBT) JUnit support is employed for
    building unittests to avoid reliance on internal SubstrateVM APIs. Consequently, the generated unittest image
    utilizes the NBT NativeImageJUnitLauncher, which does not support the command line options provided by the MxJUnitWrapper.
    """
    # 1. Use mx_unittest to collect root test distributions and test classes for given unittest pattern(s)
    supported_args = ['--build-args', '--run-args', '--exclude-class', '-h', '--help', '--']

    def _escape(arg):
        if arg in supported_args:
            return arg
        else:
            return arg.replace('-', '$esc$')

    def _unescape(arg):
        return arg.replace('$esc$', '-')

    def _split_command_line_arguments(build_args):
        """
        Splits command line arguments into two lists: one containing module-specific options,
        and the other containing the remaining options.
        """
        module_options = {'--add-exports', '--add-opens', '--add-reads', '--add-module'}
        module_command_line_arguments = []
        other_command_line_arguments = []
        for index in range(0, len(build_args)):
            arg = build_args[index]
            if arg in module_options:
                module_command_line_arguments.append(arg)
                index += 1
                module_command_line_arguments.append(build_args[index])
            elif arg.split('=')[0] in module_options:
                module_command_line_arguments.append(arg)
            else:
                other_command_line_arguments.append(arg)
        return module_command_line_arguments, other_command_line_arguments

    args = [_escape(arg) for arg in args]
    parser = ArgumentParser(prog='mx native-truffle-unittest', description='Run truffle unittests as native image on graalvm.')
    parser.add_argument(supported_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(supported_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument(supported_args[2], metavar='CLASS_NAME', action='append', help='exclude given test from test execution')
    parser.add_argument('unittests_args', metavar='TEST_ARG', nargs='*')
    parsed_args = parser.parse_args(args)
    parsed_args.build_args = [_unescape(arg) for arg in parsed_args.build_args]
    module_args, parsed_args.build_args = _split_command_line_arguments(parsed_args.build_args)
    parsed_args.run_args = [_unescape(arg) for arg in parsed_args.run_args]
    parsed_args.unittests_args = [_unescape(arg) for arg in parsed_args.unittests_args]
    parsed_args.exclude_class = [re.compile(fnmatch.translate(c)) for c in parsed_args.exclude_class] if parsed_args.exclude_class else []
    success = False
    tmp = tempfile.mkdtemp()
    try:
        jdk = mx.get_jdk(tag='graalvm')
        unittest_distributions = ['mx:JUNIT-PLATFORM-NATIVE']
        truffle_runtime_distributions = resolve_truffle_dist_names(True, True)
        test_classes_file = os.path.join(tmp, 'test_classes.txt')

        def collect_unittest_distributions(test_deps, vm_launcher, vm_args):
            unittest_distributions.extend(test_deps)

        mx_unittest._run_tests(parsed_args.unittests_args, collect_unittest_distributions,
                               mx_unittest._VMLauncher('dist_collecting_launcher', None, jdk),
                               ['@Test', '@Parameters'],
                               test_classes_file, parsed_args.exclude_class, [], None, None)

        enable_asserts_args = [
            '-ea',
            '-esa'
        ]
        uid_tracking_args = [
            '-Djunit.platform.listeners.uid.tracking.enabled=true',
            f'-Djunit.platform.listeners.uid.tracking.output.dir={os.path.join(tmp, "test-ids")}'
        ]
        vm_args = enable_asserts_args + uid_tracking_args + mx.get_runtime_jvm_args(names=unittest_distributions + truffle_runtime_distributions) + module_args

        # 2. Collect test ids for a native image build
        junit_console_launcher_with_args = [
            # The UniqueIdTrackingListener functions as an ExecutionListener, meaning that the discover command
            # alone is insufficient for generating test IDs. To generate test IDs, we must use the execute command.
            # However, we do not wish to execute the tests themselves; We want to only invoke the execution listeners.
            # To accomplish this, we execute the test with dry run enabled.
            '-Djunit.platform.execution.dryRun.enabled=true',
            'org.junit.platform.console.ConsoleLauncher',
            'execute',
            '--disable-banner',
            '--details=none'
        ]
        with open(test_classes_file) as f:
            test_classes = [f'--select-class={clazz.strip()}' for clazz in f]
        mx.run_java(vm_args + junit_console_launcher_with_args + test_classes, jdk=jdk)

        # 3. Build a native image for unittests
        def _allow_runtime_reflection(for_types):
            """
            Creates a reflection configuration file for subclasses of `org.hamcrest.TypeSafeMatcher`
            and `org.hamcrest.TypeSafeDiagnosingMatcher`. The `junit-platform-native` does not currently support
            these classes, as indicated in issue https://github.com/graalvm/native-build-tools/issues/575.
            To address this limitation, we must resolve the issue by generating a reflection configuration file.
            """
            config_folder = os.path.join(tmp, 'META-INF', 'native-image', 'native-truffle-unittest')
            os.makedirs(config_folder, exist_ok=True)
            config_file = os.path.join(config_folder, 'reflect-config.json')
            config = [{'name': t, 'allDeclaredMethods': True} for t in for_types]
            with open(config_file, "w") as f:
                print(json.dumps(config), file=f)
            return config_file

        native_image = _native_image(jdk)
        tests_executable = os.path.join(tmp, mx.exe_suffix('tests'))
        _allow_runtime_reflection([
            'org.hamcrest.core.Every',
            'org.hamcrest.core.IsCollectionContaining',
            'org.hamcrest.core.SubstringMatcher',
            'org.junit.internal.matchers.StacktracePrintingMatcher',
            'org.junit.internal.matchers.ThrowableCauseMatcher'
        ])
        native_image_args = parsed_args.build_args + [
            '--no-fallback',
            '-J-ea',
            '-J-esa',
            '-o', tests_executable,
            '-cp', tmp,
            '--features=org.graalvm.junit.platform.JUnitPlatformFeature',
            'org.graalvm.junit.platform.NativeImageJUnitLauncher']
        mx.run([native_image] + vm_args + native_image_args)

        # 4. Execute native unittests
        test_results = os.path.join(tmp, 'test-results-native', 'test')
        os.makedirs(test_results, exist_ok=True)
        mx.run([tests_executable] + ['--xml-output-dir', test_results] + parsed_args.run_args)
        success = True
    finally:
        if success:
            # only on success remove the temporary work folder
            mx.rmtree(tmp, ignore_errors=True)
        else:
            mx.warn(f'The native Truffle unit test has failed, preserving the working directory at {tmp} for further investigation.')


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle SL JVM"
def sl_jvm_gate_tests():
    _sl_jvm_gate_tests(mx.get_jdk(tag='graalvm'), force_cp=False, supports_optimization=True)
    _sl_jvm_gate_tests(mx.get_jdk(tag='graalvm'), force_cp=True, supports_optimization=True)

    _sl_jvm_gate_tests(mx.get_jdk(tag='default'), force_cp=False, supports_optimization=False)
    _sl_jvm_gate_tests(mx.get_jdk(tag='default'), force_cp=True, supports_optimization=False)


def _sl_jvm_gate_tests(jdk, force_cp=False, supports_optimization=True):
    default_args = []
    if not supports_optimization:
        default_args += ['--engine.WarnInterpreterOnly=false']

    def run_jvm_fallback(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false'] + default_args, use_optimized_runtime=False, force_cp=force_cp)
    def run_jvm_optimized(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output'] + default_args, use_optimized_runtime=True, force_cp=force_cp)
    def run_jvm_optimized_immediately(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false'] + default_args, use_optimized_runtime=True, force_cp=force_cp)
    def run_jvmci_disabled(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false', '-XX:-EnableJVMCI'] + default_args, use_optimized_runtime=True, force_cp=force_cp)

    mx.log(f'Run SL JVM Fallback Test on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvm_fallback)
    mx.log(f'Run SL JVM Optimized Test on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvm_optimized)
    mx.log(f'Run SL JVM JVMCI disabled on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvmci_disabled)

    if supports_optimization:
        mx.log(f'Run SL JVM Optimized Immediately Test on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_optimized_immediately)

    # test if the enterprise compiler is in use
    # that everything works fine if truffle-enterprise.jar is not available
    enterprise = _get_enterprise_truffle()
    if enterprise:
        def run_jvm_no_enterprise_optimized(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)
        def run_jvm_no_enterprise_optimized_immediately(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)
        def run_jvm_no_enterprise_jvmci_disabled(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false', '-XX:-EnableJVMCI'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)

        mx.log(f'Run SL JVM Optimized  Test No Truffle Enterprise on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_no_enterprise_optimized)

        if supports_optimization:
            mx.log(f'Run SL JVM Optimized Immediately Test No Truffle Enterprise on {jdk.home} force_cp={force_cp}')
            _run_sl_tests(run_jvm_no_enterprise_optimized_immediately)

        mx.log(f'Run SL JVM Optimized  Test No Truffle Enterprise JVMCI disabled on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_no_enterprise_jvmci_disabled)


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle SL Native Optimized"
def sl_native_optimized_gate_tests(quick_build=False):
    _sl_native_optimized_gate_tests(force_cp=False, quick_build=quick_build)
    _sl_native_optimized_gate_tests(force_cp=True, quick_build=quick_build)

def _sl_native_optimized_gate_tests(force_cp, quick_build):
    target_dir = tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    vm_args = ['-Ob'] if quick_build else []
    image = _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, use_enterprise=True)

    def run_native_optimized(test_file):
        return [image] + [test_file, '--disable-launcher-output']
    def run_native_optimized_immediately(test_file):
        return [image] + [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false']

    mx.log("Run SL Native Optimized Test")
    _run_sl_tests(run_native_optimized)
    mx.log("Run SL Native Optimized Immediately Test")
    _run_sl_tests(run_native_optimized_immediately)

    mx.rmtree(target_dir)

    # test if the enterprise compiler is in use
    # that everything works fine if truffle-enterprise.jar is not availble
    enterprise = _get_enterprise_truffle()
    if enterprise:
        target_dir = tempfile.mkdtemp()
        image = _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)

        def run_no_enterprise_native_optimized(test_file):
            return [image] + [test_file, '--disable-launcher-output']
        def run_no_enterprise_native_optimized_immediately(test_file):
            return [image] + [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false']

        mx.log("Run SL Native Optimized Test No Truffle Enterprise")
        _run_sl_tests(run_no_enterprise_native_optimized)
        mx.log("Run SL Native Optimized Immediately Test No Truffle Enterprise")
        _run_sl_tests(run_no_enterprise_native_optimized_immediately)

        shutil.rmtree(target_dir)


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -t "Truffle SL Native Fallback"
def sl_native_fallback_gate_tests(quick_build=False):
    _sl_native_fallback_gate_tests(force_cp=False, quick_build=quick_build)
    _sl_native_fallback_gate_tests(force_cp=True, quick_build=quick_build)


def truffle_native_unit_tests_gate(use_optimized_runtime=True, quick_build=False):
    if use_optimized_runtime:
        build_truffle_runtime_args = []
        run_truffle_runtime_args = []
    else:
        build_truffle_runtime_args = ['-Dtruffle.UseFallbackRuntime=true']
        run_truffle_runtime_args = ['-Dpolyglot.engine.WarnInterpreterOnly=false']
    if quick_build:
        build_optimize_args = ['-Ob']
    else:
        build_optimize_args = []

    # ContextPreInitializationNativeImageTest can only run with its own image.
    # See class javadoc for details.
    # Context pre-initialization is supported only in optimized runtime.
    # See TruffleFeature for details.
    if use_optimized_runtime:
        native_truffle_unittest(['com.oracle.truffle.api.test.polyglot.ContextPreInitializationNativeImageTest'] +
                                ['--build-args'] + build_optimize_args)

    # Run Truffle and NFI tests
    test_packages = [
        'com.oracle.truffle.api.test',
        'com.oracle.truffle.api.staticobject.test',
        'com.oracle.truffle.nfi.test',
    ]
    excluded_tests = [
        'com.oracle.truffle.api.test.polyglot.ContextPreInitializationNativeImageTest',    # runs in its own image
        'com.oracle.truffle.api.test.profiles.*',    # GR-52260
        'com.oracle.truffle.api.test.StackTraceTest',    # GR-52261
        'com.oracle.truffle.api.test.nodes.*',    # GR-52262
        'com.oracle.truffle.api.test.host.*',    # GR-52263
        'com.oracle.truffle.api.test.interop.*',    # GR-52264
    ]
    if quick_build:
        excluded_tests = excluded_tests + [
            'com.oracle.truffle.api.test.TruffleSafepointTest'    # GR-44492
        ]
    build_args = build_optimize_args + build_truffle_runtime_args + [
        '-R:MaxHeapSize=2g',
        '-H:MaxRuntimeCompileMethods=5000',
        '--enable-url-protocols=http,jar',
        '-H:+AddAllCharsets',
        '--add-exports=org.graalvm.polyglot/org.graalvm.polyglot.impl=ALL-UNNAMED',
        '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.impl.asm=ALL-UNNAMED',
    ]
    run_args = run_truffle_runtime_args + [
        mx_subst.path_substitutions.substitute('-Dnative.test.path=<path:truffle:TRUFFLE_TEST_NATIVE>'),
    ]
    exclude_args = list(itertools.chain(*[('--exclude-class', item) for item in excluded_tests]))
    native_truffle_unittest(test_packages + ['--build-args'] + build_args + ['--run-args'] + run_args + exclude_args)


def _sl_native_fallback_gate_tests(force_cp, quick_build):
    target_dir = tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    vm_args = ['-Ob'] if quick_build else []
    image = _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=False, force_cp=force_cp)

    def run_native_fallback(test_file):
        return [image] + [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false']

    mx.log("Run SL Native Fallback Test")
    _run_sl_tests(run_native_fallback)

    mx.rmtree(target_dir)

def _run_sl_tests(create_command):
    sl_test = mx.project("com.oracle.truffle.sl.test")
    test_path = join(_suite.dir, sl_test.subDir, sl_test.name, "src", "tests")
    for f in os.listdir(test_path):
        if f.endswith('.sl'):
            base_name = os.path.splitext(f)[0]
            test_file = join(test_path, base_name + '.sl')
            expected_file = join(test_path, base_name + '.output')
            temp = tempfile.NamedTemporaryFile(delete=False)
            command = create_command(test_file)
            mx.log("Running SL test {}".format(test_file))
            with open(temp.name, 'w') as out:
                mx.run(command, nonZeroIsFatal=False, out=out, err=out)
                out.flush()
            diff = compare_files(expected_file, temp.name)
            if diff:
                mx.log("Failed command: {}".format(" ".join(command)))
                mx.abort("Output does not match expected output: {}".format(''.join(diff)))

            # delete file only on success
            os.unlink(temp.name)

def compare_files(file1_path, file2_path):
    with open(file1_path, 'r') as file1, open(file2_path, 'r') as file2:
        content1 = file1.readlines()
        content2 = file2.readlines()
        diff = difflib.unified_diff(content1, content2, fromfile=file1_path, tofile=file2_path)
        return list(diff)

# The Truffle DSL specialization state bit width computation is complicated and
# rarely used as the default maximum bit width of 32 is rarely exceeded. Therefore
# we rebuild the truffle tests with a number of max state bit width values to
# force using multiple state fields for the tests. This makes sure the tests
# do not break for rarely used combination of features and bit widths.
def _truffle_gate_state_bitwidth_tests():
    runs = [1, 2, 4, 8, 16]
    for run_bits in runs:
        build_args = ['-f', '-p', '--dependencies', 'TRUFFLE_TEST', '--force-javac',
                      '-A-Atruffle.dsl.StateBitWidth={0}'.format(run_bits)]

        unittest_args = ['--suite', 'truffle', '--enable-timing', '--max-class-failures=25', '-Dtruffle.dsl.StateBitWidth={0}'.format(run_bits),
                         'com.oracle.truffle.api.dsl.test', 'com.oracle.truffle.api.library.test', 'com.oracle.truffle.sl.test']
        try:
            mx.build(build_args)
            unittest(unittest_args)
        finally:
            mx.log('Completed Truffle DSL state bitwidth test. Reproduce with:')
            mx.log('  mx build {0}'.format(" ".join(build_args)))
            mx.log('  mx unittest {0}'.format(" ".join(unittest_args)))

mx_gate.add_gate_runner(_suite, _truffle_gate_runner)

mx.update_commands(_suite, {
    'javadoc' : [javadoc, '[SL args|@VM options]'],
    'sl' : [sl, '[SL args|@VM options]'],
    'slnative': [slnative, '[--target-folder <folder>|SL args|@VM options]'],
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

def _collect_distributions(dist_filter, dist_collector):
    def import_visitor(suite, suite_import, predicate, collector, seenSuites, **extra_args):
        suite_collector(mx.suite(suite_import.name), predicate, collector, seenSuites)

    def suite_collector(suite, predicate, collector, seenSuites):
        if suite.name in seenSuites:
            return
        seenSuites.add(suite.name)
        suite.visit_imports(import_visitor, predicate=predicate, collector=collector, seenSuites=seenSuites)
        for dist in suite.dists:
            if predicate(dist):
                collector.append(dist)
    suite_collector(mx.primary_suite(), dist_filter, dist_collector, set())


def _collect_distributions_by_service(required_services, entries_collector):
    """
    Collects JAR distributions providing any service from requiredServices.

    :param required_services: an iterable of service fully qualified names. At least one of them has to exist to include the JAR distribution
    :param entries_collector: the list to add the distributions into.
    """
    required_services_set = set(required_services)
    required_resources = [f'META-INF/services/{service}' for service in required_services]

    def provides_service(dist):
        if dist.isJARDistribution() and exists(dist.path):
            module_name = get_module_name(dist)
            if module_name:
                # Named module - use the module-info's provides directive
                jmd = as_java_module(dist, mx.get_jdk())
                return len(required_services_set.intersection(jmd.provides.keys())) != 0
            else:
                # Unnamed or automatic module - use META-INF/services
                if isdir(dist.path):
                    for required_resource in required_resources:
                        if exists(join(dist.path, required_resource)):
                            return True
                else:
                    with zipfile.ZipFile(dist.path, "r") as zf:
                        for required_resource in required_resources:
                            try:
                                zf.getinfo(required_resource)
                            except KeyError:
                                pass
                            else:
                                return True
        return False

    _collect_distributions(provides_service, entries_collector)


class _TCKUnittestConfig(mx_unittest.MxUnittestConfig):

    lookupTCKProviders = False

    def __init__(self):
        super(_TCKUnittestConfig, self).__init__(name='truffle-tck')

    def processDeps(self, deps):
        if _TCKUnittestConfig.lookupTCKProviders:
            tck_providers = []
            _collect_distributions_by_service(["org.graalvm.polyglot.tck.LanguageProvider"], tck_providers)
            truffle_runtime = [mx.distribution(n) for n in resolve_truffle_dist_names()]
            mx.logv(f'Original unittest distributions {",".join([d.name for d in deps])}')
            mx.logv(f'TCK providers distributions to add {",".join([d.name for d in tck_providers])}')
            mx.logv(f'Truffle runtime used by the TCK {",".join([d.name for d in truffle_runtime])}')
            deps.update(tck_providers)
            deps.update(truffle_runtime)
            mx.logv(f'Merged unittest distributions {",".join([d.name for d in deps])}')
        else:
            mx.logv('Truffle TCK unnittest config is ignored because _shouldRunTCKUnittestConfig is False.')

    @staticmethod
    def _has_disable_assertions_option(vm_args):
        for vm_arg in vm_args:
            if vm_arg in ('-da', '-disableassertions'):
                return True
        return False

    def apply(self, config):
        vmArgs, mainClass, mainClassArgs = config
        # Disable DefaultRuntime warning
        vmArgs = vmArgs + ['-Dpolyglot.engine.WarnInterpreterOnly=false']
        if not _TCKUnittestConfig._has_disable_assertions_option(vmArgs):
            # Assert for enter/return parity of ProbeNode
            vmArgs = vmArgs + ['-Dpolyglot.engine.AssertProbes=true', '-Dpolyglot.engine.AllowExperimentalOptions=true']
        return (vmArgs, mainClass, mainClassArgs)


mx_unittest.register_unittest_config(_TCKUnittestConfig())


class _EnableTCKUnittestConfigAction(Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        kwargs['nargs'] = 0
        super(_EnableTCKUnittestConfigAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        _TCKUnittestConfig.lookupTCKProviders = True


mx_unittest.add_unittest_argument('--lookup-truffle-tck-providers', default=False, help='Enables lookup of Truffle TCK providers.', action=_EnableTCKUnittestConfigAction)

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

    def __process__(self, arcname, contents_supplier, is_source):
        if is_source:
            return False
        m = TruffleArchiveParticipant.providersRE.match(arcname)
        if m:
            provider = m.group(2)
            for service in contents_supplier().decode().strip().split(os.linesep):
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

_tckHelpSuffix = """
    TCK options:

      --tck-configuration                  configuration {compiler|debugger|default}
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

def tck(args):
    """runs TCK tests"""

    parser = ArgumentParser(prog="mx tck", description="run the TCK tests", formatter_class=RawDescriptionHelpFormatter, epilog=_tckHelpSuffix)
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
    unitTestOptions.append('--lookup-truffle-tck-providers')
    jvmOptions = args_no_tests[index:len(args_no_tests)]
    if tckConfiguration == "default":
        unittest(unitTestOptions + ["--"] + jvmOptions + tests)
    elif tckConfiguration == "debugger":
        with mx_util.SafeFileCreation(os.path.join(tempfile.gettempdir(), "debugalot")) as sfc:
            _execute_debugger_test(tests, sfc.tmpPath, False, unitTestOptions, jvmOptions)
    elif tckConfiguration == "compile":
        if '--use-graalvm' in unitTestOptions:
            jdk = mx.get_jdk(tag='graalvm')
        else:
            jdk = mx.get_jdk()
        if not _is_graalvm(jdk):
            mx.abort("The 'compile' TCK configuration requires graalvm execution, "
                     "run with --java-home=<path_to_graalvm> or run with --use-graalvm.")
        compileOptions = [
            "-Dpolyglot.engine.AllowExperimentalOptions=true",
            "-Dpolyglot.engine.Mode=latency",
            # "-Dpolyglot.engine.CompilationFailureAction=Throw", GR-49399
            "-Djdk.graal.CompilationFailureAction=ExitVM",
            "-Dpolyglot.engine.CompileImmediately=true",
            "-Dpolyglot.engine.BackgroundCompilation=false",
            "-Dtck.inlineVerifierInstrument=false",
        ]
        unittest(unitTestOptions + ["--"] + jvmOptions + compileOptions + tests)


mx.update_commands(_suite, {
    'tck': [tck, "[--tck-configuration {compile|debugger|default}] [unittest options] [--] [VM options] [filters...]", _tckHelpSuffix]
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

def create_dsl_parser(args=None, out=None):
    """create the DSL expression parser using antlr"""
    create_parser("com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.expression", "Expression", args=args, out=out)

def create_sl_parser(args=None, out=None):
    """create the SimpleLanguage parser using antlr"""
    create_parser("com.oracle.truffle.sl", "com.oracle.truffle.sl.parser", "SimpleLanguage", None, args, out)

def create_parser(grammar_project, grammar_package, grammar_name, copyright_template=None, args=None, out=None, postprocess=None, shaded=False):
    """create the DSL expression parser using antlr"""
    grammar_dir = os.path.join(mx.project(grammar_project).source_dirs()[0], *grammar_package.split(".")) + os.path.sep
    g4_filename = grammar_dir + grammar_name + ".g4"
    mx.run_java(mx.get_runtime_jvm_args(['ANTLR4_COMPLETE']) + ["org.antlr.v4.Tool", "-package", grammar_package, "-no-listener"] + args + [g4_filename], out=out)

    if copyright_template is None:
        # extract copyright header from .g4 file
        copyright_header = ''
        with open(g4_filename) as g:
            for line in g:
                copyright_header += line
                if line == ' */\n':
                    break
        assert copyright_header.startswith('/*\n * Copyright (c)') and copyright_header.endswith(' */\n'), copyright_header
        copyright_header += '// Checkstyle: stop\n'
        copyright_header += '//@formatter:off\n'
        copyright_template = copyright_header + '{0}\n'

    for filename in [grammar_dir + grammar_name + "Lexer.java", grammar_dir + grammar_name + "Parser.java"]:
        with open(filename, 'r') as content_file:
            content = content_file.read()
        # remove first line
        content = "\n".join(content.split("\n")[1:])
        # modify SuppressWarnings to remove useless entries
        content = re.compile("^@SuppressWarnings.*$", re.MULTILINE).sub('@SuppressWarnings({"all", "this-escape"})', content)
        # remove useless casts
        content = re.compile(r"\(\([a-zA-Z_]*Context\)_localctx\)").sub('_localctx', content)
        content = re.compile(r"\(Token\)_errHandler.recoverInline\(this\)").sub('_errHandler.recoverInline(this)', content)
        # add copyright header
        content = copyright_template.format(content)
        if shaded:
            # replace qualified class names with shadowed package names
            content = re.compile(r"\b(org\.antlr\.v4\.runtime(?:\.\w+)+)\b").sub(r'org.graalvm.shadowed.\1', content)
            # replace imports with shadowed package names
            content = re.compile(r"^import (org\.antlr\.v4\.runtime(?:\.\w+)*(?:\.\*)?);", re.MULTILINE).sub(r'import org.graalvm.shadowed.\1;', content)
        # user provided post-processing hook:
        if postprocess is not None:
            content = postprocess(content)
        with open(filename, 'w') as content_file:
            content_file.write(content)

def validate_parsers(args=None, out=None):
    validate_parser("com.oracle.truffle.sl", "com/oracle/truffle/sl/parser/SimpleLanguage.g4", create_sl_parser)
    validate_parser("com.oracle.truffle.dsl.processor", "com/oracle/truffle/dsl/processor/expression/Expression.g4", create_dsl_parser)

def validate_parser(grammar_project, grammar_path, create_command, args=None, out=None):
    def read_file(path):
        with open(path, "r") as f:
            return f.readlines()
    parser_path = grammar_path.replace(".g4", "Parser.java")
    lexer_path = grammar_path.replace(".g4", "Lexer.java")
    parser = abspath(mx.project(grammar_project).source_dirs()[0] + "/" + parser_path)
    lexer = abspath(mx.project(grammar_project).source_dirs()[0] + "/" + lexer_path)
    parser_before = read_file(parser)
    lexer_before = read_file(lexer)
    create_command([], out)
    parser_after = read_file(parser)
    lexer_after = read_file(lexer)
    for before, after, path in ((parser_before, parser_after, parser), (lexer_before, lexer_after, lexer)):
        if before != after:
            diff = ''.join(difflib.unified_diff(before, after))
            nl = os.linesep
            mx.abort(f"Content generated from {grammar_path} does not match content of {path}:{nl}" +
                    f"{diff}{nl}" +
                    "Make sure the grammar files are up to date with the generated code. You can regenerate the generated code using mx.")


class _PolyglotIsolateResourceProject(mx.JavaProject):
    """
    A Java project creating an internal resource implementation unpacking the polyglot isolate library.
    The Java source code for internal resources is generated into the project's `source_gen_dir` before compilation,
    using the `mx.truffle/polyglot_isolate_resource.template`. Configuration of the project follows these conventions:
    The target package name is `com.oracle.truffle.isolate.resource.<language_id>.<os>.<arch>`,
    and the resource ID is `<language_id>-isolate-<os>-<arch>`.
    """

    def __init__(self, language_suite, subDir, language_id, resource_id, os_name, cpu_architecture, placeholder):
        name = f'com.oracle.truffle.isolate.resource.{language_id}.{os_name}.{cpu_architecture}'
        javaCompliance = str(mx.distribution('truffle:TRUFFLE_API').javaCompliance) + '+'
        project_dir = os.path.join(language_suite.dir, subDir, name)
        deps = ['truffle:TRUFFLE_API']
        if placeholder:
            deps += ['sdk:NATIVEIMAGE']
        super(_PolyglotIsolateResourceProject, self).__init__(language_suite, name, subDir=subDir, srcDirs=[], deps=deps,
                                             javaCompliance=javaCompliance, workingSets='Truffle', d=project_dir,
                                             theLicense=_suite.defaultLicense)
        src_gen_dir = self.source_gen_dir()
        self.srcDirs.append(src_gen_dir)
        self.declaredAnnotationProcessors = ['truffle:TRUFFLE_DSL_PROCESSOR']
        self.language_id = language_id
        self.resource_id = resource_id
        self.os_name = os_name
        self.cpu_architecture = cpu_architecture
        self.placeholder = placeholder


    def getBuildTask(self, args):
        jdk = mx.get_jdk(self.javaCompliance, tag=mx.DEFAULT_JDK_TAG, purpose='building ' + self.name)
        return _PolyglotIsolateResourceBuildTask(args, self, jdk)


class _PolyglotIsolateResourceBuildTask(mx.JavaBuildTask):
    """
    A _PolyglotIsolateResourceProject build task generating and building the internal resource unpacking
    the polyglot isolate library. Refer to `_PolyglotIsolateResourceProject` documentation for more details.
    """

    def __str__(self):
        return f'Generating {self.subject.name} internal resource and compiling it with {self._getCompiler().name()}'

    @staticmethod
    def _template_file(placeholder):
        name = 'polyglot_isolate_resource_invalid.template' if placeholder else 'polyglot_isolate_resource.template'
        return os.path.join(_suite.mxDir, name)

    def needsBuild(self, newestInput):
        is_needed, reason = mx.ProjectBuildTask.needsBuild(self, newestInput)
        if is_needed:
            return True, reason
        proj = self.subject
        for outDir in [proj.output_dir(), proj.source_gen_dir()]:
            if not os.path.exists(outDir):
                return True, f"{outDir} does not exist"
        template_ts = mx.TimeStampFile.newest([
                        _PolyglotIsolateResourceBuildTask._template_file(False),
                        _PolyglotIsolateResourceBuildTask._template_file(True),
                        __file__
        ])
        if newestInput is None or newestInput.isOlderThan(template_ts):
            newestInput = template_ts
        return super().needsBuild(newestInput)

    @staticmethod
    def _target_file(root, pkg_name):
        target_folder = os.path.join(root, pkg_name.replace('.', os.sep))
        target_file = os.path.join(target_folder, 'PolyglotIsolateResource.java')
        return target_file


    def _collect_files(self):
        if self._javafiles is not None:
            # already collected
            return self
        # collect project files first, then extend with generated resource
        super(_PolyglotIsolateResourceBuildTask, self)._collect_files()
        javafiles = self._javafiles
        prj = self.subject
        gen_src_dir = prj.source_gen_dir()
        pkg_name = prj.name
        target_file = _PolyglotIsolateResourceBuildTask._target_file(gen_src_dir, pkg_name)
        if not target_file in javafiles:
            bin_dir = prj.output_dir()
            target_class = os.path.join(bin_dir, os.path.relpath(target_file, gen_src_dir)[:-len('.java')] + '.class')
            javafiles[target_file] = target_class
        # Remove annotation processor generated files.
        javafiles = {k: v for k, v in javafiles.items() if k == target_file}
        self._javafiles = javafiles
        return self

    def build(self):
        prj = self.subject
        pkg_name = prj.name
        with open(_PolyglotIsolateResourceBuildTask._template_file(prj.placeholder), 'r', encoding='utf-8') as f:
            file_content = f.read()
        subst_eng = mx_subst.SubstitutionEngine()
        subst_eng.register_no_arg('package', pkg_name)
        subst_eng.register_no_arg('languageId', prj.language_id)
        subst_eng.register_no_arg('resourceId', prj.resource_id)
        subst_eng.register_no_arg('os', prj.os_name)
        subst_eng.register_no_arg('arch', prj.cpu_architecture)
        file_content = subst_eng.substitute(file_content)
        target_file = _PolyglotIsolateResourceBuildTask._target_file(prj.source_gen_dir(), pkg_name)
        mx_util.ensure_dir_exists(dirname(target_file))
        with mx_util.SafeFileCreation(target_file) as sfc, open(sfc.tmpPath, 'w', encoding='utf-8') as f:
            f.write(file_content)
        super(_PolyglotIsolateResourceBuildTask, self).build()



def register_polyglot_isolate_distributions(language_suite, register_project, register_distribution, language_id,
                                            subDir, language_pom_distribution, maven_group_id, language_license,
                                            isolate_build_options=None, platforms=None):
    """
    Creates and registers the polyglot isolate resource distribution and isolate resource meta-POM distribution.
    The created polyglot isolate resource distribution is named `<ID>_ISOLATE_RESOURCES`, inheriting the Maven group ID
    from the given `language_distribution`, and the Maven artifact ID is `<id>-isolate`.
    The meta-POM distribution is named `<ID>_ISOLATE`, having the Maven group ID `org.graalvm.polyglot`,
    and the Maven artifact ID is `<id>-isolate`.

    :param Suite language_suite: The language suite  used to register generated projects and distributions to.
    :param register_project: A callback to dynamically register the project, obtained as a parameter from `mx_register_dynamic_suite_constituents`.
    :type register_project: (mx.Project) -> None
    :param register_distribution: A callback to dynamically register the distribution, obtained as a parameter from `mx_register_dynamic_suite_constituents`.
    :type register_distribution: (mx.Distribution) -> None
    :param str language_id: The language ID.
    :param str subDir: a path relative to `suite.dir` in which the IDE project configuration for this distribution is generated
    :param POMDistribution language_pom_distribution: The language meta pom distribution used to set the image builder module-path.
    :param str maven_group_id: The maven language group id.
    :param str | list | language_license: Language licence(s).
    :param list isolate_build_options: additional options passed to a native image to build the isolate library.
    :param list platforms: supported platforms, defaults to ['linux-amd64', 'linux-aarch64', 'darwin-amd64', 'darwin-aarch64', 'windows-amd64']
    """
    assert language_suite
    assert register_project
    assert register_distribution
    assert language_id
    assert subDir
    assert language_pom_distribution
    assert maven_group_id
    assert language_license

    polyglot_isolates_value = mx_sdk_vm_impl._parse_cmd_arg('polyglot_isolates')
    if not polyglot_isolates_value or not (polyglot_isolates_value is True or (isinstance(polyglot_isolates_value, list) and language_id in polyglot_isolates_value)):
        return False

    if not isinstance(language_license, list):
        assert isinstance(language_license, str)
        language_license = [language_license]

    def _qualname(distribution_name):
        return language_suite.name + ':' + distribution_name if distribution_name.find(':') < 0 else distribution_name

    language_pom_distribution = _qualname(language_pom_distribution)
    if isolate_build_options is None:
        isolate_build_options = []
    language_id_upper_case = language_id.upper()
    if platforms is None:
        platforms = [
            'linux-amd64',
            'linux-aarch64',
            'darwin-amd64',
            'darwin-aarch64',
            'windows-amd64',
        ]
    current_platform = mx_subst.string_substitutions.substitute('<os>-<arch>')
    if current_platform not in platforms:
        mx.abort(f'Current platform {current_platform} is not in supported platforms {", ".join(platforms)}')

    platform_meta_poms = []
    for platform in platforms:
        build_for_current_platform = platform == current_platform
        resource_id = f'{language_id}-isolate-{platform}'
        os_name, cpu_architecture = platform.split('-')
        os_name_upper_case = os_name.upper()
        cpu_architecture_upper_case = cpu_architecture.upper()
        # 1. Register a project generating and building an internal resource for polyglot isolate library
        build_internal_resource = _PolyglotIsolateResourceProject(language_suite, subDir, language_id, resource_id,
                                                                  os_name, cpu_architecture, not build_for_current_platform)
        register_project(build_internal_resource)
        resources_dist_dependencies = [
            build_internal_resource.name,
        ]

        if build_for_current_platform:
            # 2. Register a project building the isolate library
            isolate_deps = [language_pom_distribution, 'graal-enterprise:TRUFFLE_ENTERPRISE']
            build_library = mx_sdk_vm_impl.PolyglotIsolateLibrary(language_suite, language_id, isolate_deps, isolate_build_options)
            register_project(build_library)

            # 3. Register layout distribution with isolate library and isolate resources
            resource_base_folder = f'META-INF/resources/engine/{resource_id}/libvm'
            attrs = {
                'description': f'Contains {language_id} language library resources.',
                'hashEntry': f'{resource_base_folder}/sha256',
                'fileListEntry': f'{resource_base_folder}/files',
                'maven': False,
            }
            layout_dist = mx.LayoutDirDistribution(
                suite=language_suite,
                name=f'{language_id_upper_case}_ISOLATE_LAYOUT_{os_name_upper_case}_{cpu_architecture_upper_case}',
                deps=[],
                layout={
                    f'{resource_base_folder}/': f'dependency:{build_library.name}',
                    f'{resource_base_folder}/resources': f'dependency:{build_library.name}/resources',
                },
                path=None,
                platformDependent=True,
                theLicense=None,
                platforms=[platform],
                **attrs
            )
            register_distribution(layout_dist)
            layout_dist_qualified_name = f'{layout_dist.suite.name}:{layout_dist.name}'
            resources_dist_dependencies.append(layout_dist_qualified_name)

        # 4. Register Jar distribution containing the internal resource project and isolate library for current platform.
        # For other platforms, create a jar distribution with an internal resource only
        resources_dist_name = f'{language_id_upper_case}_ISOLATE_RESOURCES_{os_name_upper_case}_{cpu_architecture_upper_case}'
        maven_artifact_id = resource_id
        licenses = set(language_license)
        # The graal-enterprise suite may not be fully loaded.
        # We cannot look up the TRUFFLE_ENTERPRISE distribution to resolve its license
        # We pass directly the license id
        licenses.update(['GFTC'])
        attrs = {
            'description': f'Polyglot isolate resources for {language_id} for {platform}.',
            'moduleInfo': {
                'name': build_internal_resource.name,
            },
            'maven': {
                'groupId': maven_group_id,
                'artifactId': maven_artifact_id,
                'tag': ['default', 'public'],
            },
            'mavenNoJavadoc': True,
            'mavenNoSources': True,
        }
        isolate_library_dist = mx_jardistribution.JARDistribution(
            suite=language_suite,
            name=resources_dist_name,
            subDir=subDir,
            path=None,
            sourcesPath=None,
            deps=resources_dist_dependencies,
            mainClass=None,
            excludedLibs=[],
            distDependencies=['truffle:TRUFFLE_API'],
            javaCompliance=str(build_internal_resource.javaCompliance)+'+',
            platformDependent=True,
            theLicense=sorted(list(licenses)),
            compress=True,
            **attrs
        )
        register_distribution(isolate_library_dist)

        # 5. Register meta POM distribution for the isolate library jar file for a specific platform.
        isolate_dist_name = f'{language_id_upper_case}_ISOLATE_{os_name_upper_case}_{cpu_architecture_upper_case}'
        attrs = {
            'description': f'The {language_id} polyglot isolate for {platform}.',
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': maven_artifact_id,
                'tag': ['default', 'public'],
            },
        }
        meta_pom_dist = mx_pomdistribution.POMDistribution(
            suite=language_suite,
            name=isolate_dist_name,
            distDependencies=[],
            runtimeDependencies=[
                resources_dist_name,
                'graal-enterprise:TRUFFLE_ENTERPRISE',
            ],
            theLicense=sorted(list(licenses)),
            **attrs)
        register_distribution(meta_pom_dist)
        platform_meta_poms.append(meta_pom_dist)
    # 6. Register meta POM distribution listing all platform specific meta-POMS.
    isolate_dist_name = f'{language_id_upper_case}_ISOLATE'
    attrs = {
        'description': f'The {language_id} polyglot isolate.',
        'maven': {
            'groupId': 'org.graalvm.polyglot',
            'artifactId': f'{language_id}-isolate',
            'tag': ['default', 'public'],
        },
    }
    meta_pom_dist = mx_pomdistribution.POMDistribution(
        suite=language_suite,
        name=isolate_dist_name,
        distDependencies=[],
        runtimeDependencies=[pom.name for pom in platform_meta_poms],
        theLicense=sorted(list(licenses)),
        **attrs)
    register_distribution(meta_pom_dist)
    return True


mx.add_argument('--polyglot-isolates', action='store', help='Comma-separated list of languages for which the polyglot isolate library should be built. Setting the value to `true` builds all polyglot isolate libraries.')


class LibffiBuilderProject(mx.AbstractNativeProject, mx_native.NativeDependency):  # pylint: disable=too-many-ancestors
    """Project for building libffi from source.

    The build is performed by:
        1. Extracting the sources,
        2. Applying the platform dependent patches, and
        3. Invoking the platform dependent builder that we delegate to.
    """

    libs = property(lambda self: self.delegate.libs)

    def __init__(self, suite, name, deps, workingSets, **kwargs):
        subDir = 'src'
        srcDirs = ['patches']
        d = os.path.join(suite.dir, subDir, name)
        super(LibffiBuilderProject, self).__init__(suite, name, subDir, srcDirs, deps, workingSets, d, **kwargs)

        self.out_dir = self.get_output_root()
        if mx.get_os() == 'windows':
            self.delegate = mx_native.DefaultNativeProject(suite, name, subDir, [], [], None,
                                                           os.path.join(self.out_dir, 'libffi-3.4.4'),
                                                           'static_lib',
                                                           deliverable='ffi',
                                                           cflags=['-MD', '-O2', '-DFFI_BUILDING_DLL'])
            self.delegate._source = dict(tree=['include',
                                               'src',
                                               os.path.join('src', 'x86')],
                                         files={'.h': [os.path.join('include', 'ffi.h'),
                                                       os.path.join('include', 'ffitarget.h'),
                                                       os.path.join('src', 'fficonfig.h'),
                                                       os.path.join('src', 'ffi_common.h')],
                                                '.c': [os.path.join('src', 'closures.c'),
                                                       os.path.join('src', 'prep_cif.c'),
                                                       os.path.join('src', 'raw_api.c'),
                                                       os.path.join('src', 'types.c'),
                                                       os.path.join('src', 'tramp.c'),
                                                       os.path.join('src', 'x86', 'ffiw64.c')],
                                                '.S': [os.path.join('src', 'x86', 'win64_intel.S')]})
        else:
            class LibtoolNativeProject(mx.NativeProject,  # pylint: disable=too-many-ancestors
                                       mx_native.NativeDependency):
                include_dirs = property(lambda self: [os.path.join(self.getOutput(), 'include')])
                libs = property(lambda self: [next(self.getArchivableResults(single=True))[0]])

                def getArchivableResults(self, use_relpath=True, single=False):
                    for file_path, archive_path in super(LibtoolNativeProject, self).getArchivableResults(use_relpath):
                        path_in_lt_objdir = os.path.basename(dirname(file_path)) == '.libs'
                        yield file_path, os.path.basename(archive_path) if path_in_lt_objdir else archive_path
                        if single:
                            assert path_in_lt_objdir, 'the first build result must be from LT_OBJDIR'
                            break

            self.delegate = LibtoolNativeProject(suite, name, subDir, [], [], None,
                                                 ['.libs/libffi.a',
                                                  'include/ffi.h',
                                                  'include/ffitarget.h'],
                                                 os.path.join(self.out_dir, 'libffi-build'),
                                                 os.path.join(self.out_dir, 'libffi-3.4.4'))
            configure_args = ['--disable-dependency-tracking',
                              '--disable-shared',
                              '--with-pic',
                              ' CFLAGS="{}"'.format(' '.join(['-g', '-O3'] + (['-m64'] if mx.get_os() == 'solaris' else []))),
                              'CPPFLAGS="-DNO_JAVA_RAW_API"',
                             ]

            self.delegate.buildEnv = dict(
                SOURCES=os.path.basename(self.delegate.dir),
                OUTPUT=os.path.basename(self.delegate.getOutput()),
                CONFIGURE_ARGS=' '.join(configure_args)
            )

        self.include_dirs = self.delegate.include_dirs

    def resolveDeps(self):
        super(LibffiBuilderProject, self).resolveDeps()
        self.delegate.resolveDeps()
        self.buildDependencies += self.delegate.buildDependencies

    @property
    def sources(self):
        assert len(self.deps) == 1, '{} must depend only on its sources'.format(self.name)
        return self.deps[0]

    @property
    def patches(self):
        """A list of patches that will be applied during a build."""
        def patch_dir(d):
            return os.path.join(self.source_dirs()[0], d)

        def get_patches(patchdir):
            if os.path.isdir(patchdir):
                for patch in os.listdir(patchdir):
                    yield os.path.join(patchdir, patch)

        for p in get_patches(patch_dir('common')):
            yield p
        os_arch_dir = patch_dir('{}-{}'.format(mx.get_os(), mx.get_arch()))
        if os.path.exists(os_arch_dir):
            for p in get_patches(os_arch_dir):
                yield p
        else:
            for p in get_patches(patch_dir('others')):
                yield p

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
        assert not os.path.exists(self.subject.out_dir), '{} must be cleaned before build'.format(self.subject.name)

        mx.log('Extracting {}...'.format(self.subject.sources))
        mx.Extractor.create(self.subject.sources.get_path(False)).extract(self.subject.out_dir)

        mx.log('Applying patches...')
        git_apply = ['git', 'apply', '--whitespace=nowarn', '--unsafe-paths', '--directory',
                     os.path.realpath(self.subject.delegate.dir)]
        for patch in self.subject.patches:
            mx.run(git_apply + [patch], cwd=self.subject.suite.vc_dir)

        self.delegate.logBuild()
        self.delegate.build()

    def clean(self, forBuild=False):
        mx.rmtree(self.subject.out_dir, ignore_errors=True)



mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle API',
    short_name='tfla',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Graal SDK', 'Truffle Compiler'],
    jar_distributions=[],
    jvmci_parent_jars=[
        'sdk:JNIUTILS',
        'truffle:TRUFFLE_API',
        'truffle:TRUFFLE_RUNTIME',
    ],
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle',
    short_name='tfl',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'Truffle API',
        'GraalVM Launcher Common',
    ],
    jar_distributions=[],
    jvmci_parent_jars=[
        'truffle:LOCATOR',
    ],
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle Compiler',
    short_name='tflc',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=[],
    jvmci_parent_jars=[
        'truffle:TRUFFLE_COMPILER',
    ],
    stability="supported",
))

# Typically not included in releases
mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle DSL Processor',
    short_name='tflp',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=['truffle:TRUFFLE_DSL_PROCESSOR'],
    jvmci_parent_jars=[],
    stability="supported",
))

truffle_nfi_component = mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle NFI',
    short_name='nfi',
    dir_name='nfi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TRUFFLE_NFI'],
    support_distributions=['truffle:TRUFFLE_NFI_GRAALVM_SUPPORT'],
    support_libraries_distributions=['truffle:TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT'],
    installable=False,
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(truffle_nfi_component)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle NFI LIBFFI',
    short_name='nfi-libffi',
    dir_name='nfi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle NFI'],
    truffle_jars=['truffle:TRUFFLE_NFI_LIBFFI', 'truffle:TRUFFLE_NFI_PANAMA'],
    installable=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='ICU4J',
    short_name='icu4j',
    dir_name='icu4j',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=[
        'truffle:TRUFFLE_ICU4J',
    ],
    support_distributions=['truffle:TRUFFLE_ICU4J_GRAALVM_SUPPORT'],
    installable=True,
    standalone=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='ANTLR4',
    short_name='antlr4',
    dir_name='antlr4',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:ANTLR4', 'truffle:TRUFFLE_ANTLR4'],
    support_distributions=['truffle:TRUFFLE_ANTLR4_GRAALVM_SUPPORT'],
    installable=True,
    standalone=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle JSON Library',
    short_name='truffle-json',
    dir_name='truffle-json',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TRUFFLE_JSON',
    ],
    support_distributions=['truffle:TRUFFLE_JSON_GRAALVM_SUPPORT'],
    installable=False,
    standalone=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='XZ',
    short_name='xz',
    dir_name='xz',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TRUFFLE_XZ'],
    support_distributions=['truffle:TRUFFLE_XZ_GRAALVM_SUPPORT'],
    installable=True,
    standalone=False,
    stability="supported",
))


mx.update_commands(_suite, {
    'check-filename-length' : [check_filename_length, ""],
    'create-dsl-parser' : [create_dsl_parser, "create the DSL expression parser using antlr"],
    'create-sl-parser' : [create_sl_parser, "create the SimpleLanguage parser using antlr"],
})

mx_gate.add_jacoco_includes(['org.graalvm.*', 'com.oracle.truffle.*'])
