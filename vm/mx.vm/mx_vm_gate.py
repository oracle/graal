# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import mx
import mx_subst
import mx_unittest
import mx_sdk_vm_impl

import functools
import re
from mx_gate import Task

from os import environ, listdir, remove, linesep
from os.path import join, exists, dirname, isdir, isfile, getsize
from tempfile import NamedTemporaryFile, mkdtemp
from contextlib import contextmanager

_suite = mx.suite('vm')

class VmGateTasks:
    compiler = 'compiler'
    substratevm = 'substratevm'
    sulong = 'sulong'
    graal_js_all = 'graal-js'
    graal_js_smoke = 'graal-js-smoke'
    graal_js_tests = 'graal-js-tests'
    graal_js_tests_compiled = 'graal-js-tests-compiled'
    graal_nodejs = 'graal-nodejs'
    truffleruby = 'truffleruby'
    ruby = 'ruby'
    python = 'python'
    fastr = 'fastr'
    graalpython = 'graalpython'
    integration = 'integration'
    tools = 'tools'
    libgraal = 'libgraal'
    svm_sl_tck = 'svm_sl_tck'
    svm_truffle_tck_js = 'svm-truffle-tck-js'


def gate_body(args, tasks):
    # all mx_sdk_vm_impl gate tasks can also be run as vm gate tasks
    mx_sdk_vm_impl.gate_body(args, tasks)

    with Task('Vm: Basic GraalVM Tests', tasks, tags=[VmGateTasks.compiler]) as t:
        if t and mx_sdk_vm_impl.has_component('GraalVM compiler'):
            # 1. the build must be a GraalVM
            # 2. the build must be JVMCI-enabled since the 'GraalVM compiler' component is registered
            mx_sdk_vm_impl.check_versions(mx_sdk_vm_impl.graalvm_output(), graalvm_version_regex=mx_sdk_vm_impl.graalvm_version_regex, expect_graalvm=True, check_jvmci=True)

    if mx_sdk_vm_impl.has_component('LibGraal'):
        libgraal_location = mx_sdk_vm_impl.get_native_image_locations('LibGraal', 'jvmcicompiler')
        if libgraal_location is None:
            mx.warn("Skipping libgraal tests: no library enabled in the LibGraal component")
        else:
            extra_vm_arguments = ['-XX:+UseJVMCICompiler', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location)]
            if args.extra_vm_argument:
                extra_vm_arguments += args.extra_vm_argument
            import mx_compiler

            # run avrora on the GraalVM binary itself
            with Task('LibGraal Compiler:GraalVM DaCapo-avrora', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t:
                    java_exe = join(mx_sdk_vm_impl.graalvm_home(), 'bin', 'java')
                    mx.run([java_exe,
                            '-XX:+UseJVMCICompiler',
                            '-XX:+UseJVMCINativeLibrary',
                            '-jar', mx.library('DACAPO').get_path(True), 'avrora'])

                    # Ensure that fatal errors in libgraal route back to HotSpot
                    testdir = mkdtemp()
                    try:
                        cmd = [java_exe,
                                '-XX:+UseJVMCICompiler',
                                '-XX:+UseJVMCINativeLibrary',
                                '-Dlibgraal.CrashAt=length,hashCode',
                                '-Dlibgraal.CrashAtIsFatal=true',
                                '-jar', mx.library('DACAPO').get_path(True), 'avrora']
                        out = mx.OutputCapture()
                        exitcode = mx.run(cmd, cwd=testdir, nonZeroIsFatal=False, out=out)
                        if exitcode == 0:
                            if 'CrashAtIsFatal: no fatalError function pointer installed' in out.data:
                                # Executing a VM that does not configure fatal errors handling
                                # in libgraal to route back through the VM.
                                pass
                            else:
                                mx.abort('Expected following command to result in non-zero exit code: ' + ' '.join(cmd))
                        else:
                            hs_err = None
                            testdir_entries = listdir(testdir)
                            for name in testdir_entries:
                                if name.startswith('hs_err_pid') and name.endswith('.log'):
                                    hs_err = join(testdir, name)
                            if hs_err is None:
                                mx.abort('Expected a file starting with "hs_err_pid" in test directory. Entries found=' + str(testdir_entries))
                            with open(join(testdir, hs_err)) as fp:
                                contents = fp.read()
                            if 'Fatal error in JVMCI' not in contents:
                                mx.abort('Expected "Fatal error in JVMCI" to be in contents of ' + hs_err + ':' + linesep + contents)
                    finally:
                        mx.rmtree(testdir)

            with Task('LibGraal Compiler:CTW', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t:
                    mx_compiler.ctw([
                            '-DCompileTheWorld.Config=Inline=false CompilationFailureAction=ExitVM', '-esa', '-XX:+EnableJVMCI',
                            '-DCompileTheWorld.MultiThreaded=true', '-Dgraal.InlineDuringParsing=false', '-Dgraal.TrackNodeSourcePosition=true',
                            '-DCompileTheWorld.Verbose=false', '-XX:ReservedCodeCacheSize=300m',
                        ], extra_vm_arguments)

            mx_compiler.compiler_gate_benchmark_runner(tasks, extra_vm_arguments, prefix='LibGraal Compiler:')

            with Task('LibGraal Truffle:unittest', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t:
                    def _unittest_config_participant(config):
                        vmArgs, mainClass, mainClassArgs = config
                        def is_truffle_fallback(arg):
                            fallback_args = [
                                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                                "-Dgraalvm.ForcePolyglotInvalid=true"
                            ]
                            return arg in fallback_args
                        newVmArgs = [arg for arg in vmArgs if not is_truffle_fallback(arg)]
                        return (newVmArgs, mainClass, mainClassArgs)
                    mx_unittest.add_config_participant(_unittest_config_participant)
                    excluded_tests = environ.get("TEST_LIBGRAAL_EXCLUDE")
                    if excluded_tests:
                        with NamedTemporaryFile(prefix='blacklist.', mode='w', delete=False) as fp:
                            fp.file.writelines([l + '\n' for l in excluded_tests.split()])
                            unittest_args = ["--blacklist", fp.name]
                    else:
                        unittest_args = []
                    unittest_args = unittest_args + ["--enable-timing", "--verbose"]
                    compiler_log_file = "graal-compiler.log"
                    mx_unittest.unittest(unittest_args + extra_vm_arguments + [
                        "-Dpolyglot.engine.AllowExperimentalOptions=true",
                        "-Dpolyglot.engine.CompileImmediately=true",
                        "-Dpolyglot.engine.BackgroundCompilation=false",
                        "-Dpolyglot.engine.TraceCompilation=true",
                        "-Dgraalvm.locatorDisabled=true",
                        "-Dgraal.PrintCompilation=true",
                        "-Dgraal.LogFile={0}".format(compiler_log_file),
                        "truffle"])
                    if exists(compiler_log_file):
                        remove(compiler_log_file)
    else:
        mx.warn("Skipping libgraal tests: component not enabled")

    gate_substratevm(tasks)
    gate_sulong(tasks)
    gate_python(tasks)
    gate_svm_sl_tck(tasks)
    gate_svm_truffle_tck_js(tasks)

def graalvm_svm():
    """
    Gives access to image building withing the GraalVM release. Requires dynamic import of substratevm.
    """
    native_image_cmd = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'native-image')
    svm = mx.suite('substratevm')
    if not exists(native_image_cmd) or not svm:
        mx.abort("Image building not accessible in GraalVM {}. Build GraalVM with native-image support".format(mx_sdk_vm_impl.graalvm_dist_name()))
    @contextmanager
    def native_image_context(common_args=None, hosted_assertions=True):
        with svm.extensions.native_image_context(common_args, hosted_assertions, native_image_cmd=native_image_cmd) as native_image:
            yield native_image
    return native_image_context, svm.extensions

def gate_substratevm(tasks):
    with Task('Run Truffle host interop tests on SVM', tasks, tags=[VmGateTasks.substratevm]) as t:
        if t:
            tests = ['ValueHostInteropTest', 'ValueHostConversionTest']
            truffle_no_compilation = ['--initialize-at-build-time', '--macro:truffle',
                                      '-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime']
            args = ['--build-args'] + truffle_no_compilation + ['--'] + tests
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                svm._native_unittest(native_image, args)

def gate_sulong(tasks):
    with Task('Run SulongSuite tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            lli = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--enable-timing'])

    with Task('Run Sulong interop tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            sulong = mx.suite('sulong')
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                # TODO Use mx_sdk_vm_impl.get_final_graalvm_distribution().find_single_source_location to rewire SULONG_HOME
                sulong_libs = join(mx_sdk_vm_impl.graalvm_output(), 'jre', 'languages', 'llvm')
                def distribution_paths(dname):
                    path_substitutions = {
                        'SULONG_HOME': sulong_libs
                    }
                    return path_substitutions.get(dname, mx._get_dependency_path(dname))
                mx_subst.path_substitutions.register_with_arg('path', distribution_paths)
                sulong.extensions.runLLVMUnittests(functools.partial(svm._native_unittest, native_image))

def gate_python(tasks):
    with Task('Python', tasks, tags=[VmGateTasks.python]) as t:
        if t:
            python_svm_image_path = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'graalpython')
            python_suite = mx.suite("graalpython")
            python_suite.extensions.run_python_unittests(python_svm_image_path)

def _svm_truffle_tck(native_image, svm_suite, language_suite, language_id):
    cp = None
    for dist in svm_suite.dists:
        if dist.name == 'SVM_TRUFFLE_TCK':
            cp = dist.classpath_repr()
            break
    if not cp:
        mx.abort("Cannot resolve: SVM_TRUFFLE_TCK distribution.")
    excludes = []
    excludes_dir = join(language_suite.mxDir, 'truffle.tck.permissions')
    if isdir(excludes_dir):
        for excludes_file in listdir(excludes_dir):
            excludes.append(join(excludes_dir, excludes_file))
    svmbuild = mkdtemp()
    try:
        report_file = join(svmbuild, "language_permissions.log")
        options = [
            '--language:{}'.format(language_id),
            '--features=com.oracle.svm.truffle.tck.PermissionsFeature',
            '-H:ClassInitialization=:build_time',
            '-H:+EnforceMaxRuntimeCompileMethods',
            '-cp',
            cp,
            '--no-server',
            '-H:+TruffleCheckBlackListedMethods',
            '-H:-FoldSecurityManagerGetter',
            '-H:TruffleTCKPermissionsReportFile={}'.format(report_file),
            '-H:Path={}'.format(svmbuild),
            'com.oracle.svm.truffle.tck.MockMain'
        ]
        if excludes:
            options.append('-H:TruffleTCKPermissionsExcludeFiles={}'.format(','.join(excludes)))
        native_image(options)
        if isfile(report_file) and getsize(report_file) > 0:
            message = "Failed: Language {} performs following privileged calls:\n\n".format(language_id)
            with open(report_file, "r") as f:
                for line in f.readlines():
                    message = message + line
            mx.abort(message)
    finally:
        mx.rmtree(svmbuild)


def gate_svm_truffle_tck_js(tasks):
    with Task('JavaScript SVM Truffle TCK', tasks, tags=[VmGateTasks.svm_truffle_tck_js]) as t:
        if t:
            js_suite = mx.suite('graal-js')
            if not js_suite:
                mx.abort("Cannot resolve graal-js suite.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, svm.suite, js_suite, 'js')

def gate_svm_sl_tck(tasks):
    with Task('SVM Truffle TCK', tasks, tags=[VmGateTasks.svm_sl_tck]) as t:
        if t:
            tools_suite = mx.suite('tools')
            if not tools_suite:
                mx.abort("Cannot resolve tools suite.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                svmbuild = mkdtemp()
                try:
                    import mx_compiler
                    unittest_deps = []
                    unittest_file = join(svmbuild, 'truffletck.tests')
                    mx_unittest._run_tests([], lambda deps, vm_launcher, vm_args: unittest_deps.extend(deps), mx_unittest._VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, [], [re.compile('com.oracle.truffle.tck.tests')], None, mx.suite('truffle'))
                    if not exists(unittest_file):
                        mx.abort('TCK tests not found.')
                    unittest_deps.append(mx.dependency('truffle:TRUFFLE_SL_TCK'))
                    unittest_deps.append(mx.dependency('truffle:TRUFFLE_TCK_INSTRUMENTATION'))
                    vm_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk)
                    options = [
                        '--macro:truffle',
                        '--tool:all',
                        '-H:Path={}'.format(svmbuild),
                        '-H:+TruffleCheckBlackListedMethods',
                        '-H:Class=org.junit.runner.JUnitCore',
                    ]
                    tests_image = native_image(vm_image_args + options)
                    with open(unittest_file) as f:
                        test_classes = [l.rstrip() for l in f.readlines()]
                    mx.run([tests_image] + test_classes)
                finally:
                    mx.rmtree(svmbuild)
