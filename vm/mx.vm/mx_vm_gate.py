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
import mx_benchmark
import mx_subst
import mx_unittest
import mx_sdk_vm
import mx_sdk_vm_impl

import functools
import glob
import re
import sys
from mx_gate import Task

from os import environ, listdir, remove, linesep
from os.path import join, exists, dirname, isdir, isfile, getsize, abspath
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

def _check_compiler_log(compiler_log_file, expectations):
    """
    Checks that `compiler_log_file` exists and that its contents match each regular expression in `expectations`.
    If all checks succeed, `compiler_log_file` is deleted.
    """
    in_exception_path = sys.exc_info() != (None, None, None)
    if not exists(compiler_log_file):
        mx.abort('No output written to ' + compiler_log_file)
    with open(compiler_log_file) as fp:
        compiler_log = fp.read()
    if not isinstance(expectations, list):
        expectations = [expectations]
    for pattern in expectations:
        if not re.search(pattern, compiler_log):
            mx.abort('Did not find expected pattern ("{}") in compiler log:{}{}'.format(pattern, linesep, compiler_log))
    if mx.get_opts().verbose or in_exception_path:
        mx.log(compiler_log)
    remove(compiler_log_file)

def _test_libgraal_basic(extra_vm_arguments):
    """
    Tests basic libgraal execution by running a DaCapo benchmark, ensuring it has a 0 exit code
    and that the output for -DgraalShowConfiguration=info describes a libgraal execution.
    """
    expect = r"Using compiler configuration '[^']+' provided by [\.\w]+ loaded from JVMCI native library"
    compiler_log_file = abspath('graal-compiler.log')
    args = ['-Dgraal.ShowConfiguration=info',
            '-Dgraal.LogFile=' + compiler_log_file,
            '-jar', mx.library('DACAPO').get_path(True), 'avrora', '-n', '1']

    # Verify execution via raw java launcher in `mx graalvm-home`.
    try:
        mx.run([join(mx_sdk_vm_impl.graalvm_home(), 'bin', 'java')] + args)
    finally:
        _check_compiler_log(compiler_log_file, expect)

    # Verify execution via `mx vm`.
    import mx_compiler
    try:
        mx_compiler.run_vm(extra_vm_arguments + args)
    finally:
        _check_compiler_log(compiler_log_file, expect)

def _test_libgraal_fatal_error_handling():
    """
    Tests that fatal errors in libgraal route back to HotSpot fatal error handling.
    """
    vmargs = ['-XX:+PrintFlagsFinal',
              '-Dlibgraal.CrashAt=length,hashCode',
              '-Dlibgraal.CrashAtIsFatal=true']
    cmd = ["dacapo:avrora", "--tracker=none", "--"] + vmargs + ["--", "--preserve"]
    out = mx.OutputCapture()
    exitcode, bench_suite, _ = mx_benchmark.gate_mx_benchmark(cmd, out=out, err=out, nonZeroIsFatal=False)
    if exitcode == 0:
        if 'CrashAtIsFatal: no fatalError function pointer installed' in out.data:
            # Executing a VM that does not configure fatal errors handling
            # in libgraal to route back through the VM.
            pass
        else:
            mx.abort('Expected benchmark to result in non-zero exit code: ' + ' '.join(cmd) + linesep + out.data)
    else:
        if len(bench_suite.scratchDirs()) == 0:
            mx.abort("No scratch dir found despite error being expected!")
        latest_scratch_dir = bench_suite.scratchDirs()[-1]
        seen_libjvmci_log = False
        hs_errs = glob.glob(join(latest_scratch_dir, 'hs_err_pid*.log'))
        if not hs_errs:
            mx.abort('Expected a file starting with "hs_err_pid" in test directory. Entries found=' + str(listdir(latest_scratch_dir)))

        for hs_err in hs_errs:
            mx.log("Verifying content of {}".format(join(latest_scratch_dir, hs_err)))
            with open(join(latest_scratch_dir, hs_err)) as fp:
                contents = fp.read()
            if 'libjvmci' in hs_err:
                seen_libjvmci_log = True
                if 'Fatal error: Forced crash' not in contents:
                    mx.abort('Expected "Fatal error: Forced crash" to be in contents of ' + hs_err + ':' + linesep + contents)
            else:
                if 'Fatal error in JVMCI' not in contents:
                    mx.abort('Expected "Fatal error in JVMCI" to be in contents of ' + hs_err + ':' + linesep + contents)

        if 'JVMCINativeLibraryErrorFile' in out.data and not seen_libjvmci_log:
            mx.abort('Expected a file matching "hs_err_pid*_libjvmci.log" in test directory. Entries found=' + str(listdir(latest_scratch_dir)))

    # Only clean up scratch dir on success
    for scratch_dir in bench_suite.scratchDirs():
        mx.log("Cleaning up scratch dir after gate task completion: {}".format(scratch_dir))
        mx.rmtree(scratch_dir)

def _jdk_has_ForceTranslateFailure_jvmci_option(jdk):
    """
    Determines if `jdk` supports the `-Djvmci.ForceTranslateFailure` option.
    """
    sink = mx.OutputCapture()
    res = mx.run([jdk.java, '-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '-XX:+EagerJVMCI', '-Djvmci.ForceTranslateFailure=test', '-version'], nonZeroIsFatal=False, out=sink, err=sink)
    if res == 0:
        return True
    if 'Could not find option jvmci.ForceTranslateFailure' in sink.data:
        return False
    mx.abort(sink.data)

def _test_libgraal_ctw(extra_vm_arguments):
    import mx_compiler

    if _jdk_has_ForceTranslateFailure_jvmci_option(mx_compiler.jdk):
        # Tests that failures in HotSpotJVMCIRuntime.translate do not cause the VM to exit.
        # This test is only possible if the jvmci.ForceTranslateFailure option exists.
        compiler_log_file = abspath('graal-compiler-ctw.log')
        fail_to_translate_value = 'nmethod/StackOverflowError:hotspot,method/String.hashCode:native,valueOf'
        expectations = ['ForceTranslateFailure filter "{}"'.format(f) for f in fail_to_translate_value.split(',')]
        try:
            mx_compiler.ctw([
                '-DCompileTheWorld.Config=Inline=false ' + ' '.join(mx_compiler._compiler_error_options(prefix='')),
                '-XX:+EnableJVMCI',
                '-Dgraal.InlineDuringParsing=false',
                '-Dgraal.TrackNodeSourcePosition=true',
                '-Dgraal.LogFile=' + compiler_log_file,
                '-DCompileTheWorld.Verbose=true',
                '-DCompileTheWorld.MethodFilter=StackOverflowError.*,String.*',
                '-Djvmci.ForceTranslateFailure=nmethod/StackOverflowError:hotspot,method/String.hashCode:native,valueOf',
            ], extra_vm_arguments)
        finally:
            _check_compiler_log(compiler_log_file, expectations)

    mx_compiler.ctw([
            '-DCompileTheWorld.Config=Inline=false ' + ' '.join(mx_compiler._compiler_error_options(prefix='')),
            '-esa',
            '-XX:+EnableJVMCI',
            '-DCompileTheWorld.MultiThreaded=true',
            '-Dgraal.InlineDuringParsing=false',
            '-Dgraal.TrackNodeSourcePosition=true',
            '-DCompileTheWorld.Verbose=false',
            '-DCompileTheWorld.HugeMethodLimit=4000',
            '-DCompileTheWorld.MaxCompiles=150000',
            '-XX:ReservedCodeCacheSize=300m',
        ], extra_vm_arguments)

def _test_libgraal_truffle(extra_vm_arguments):
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
        "-Dpolyglot.log.file={0}".format(compiler_log_file),
        "-Dgraalvm.locatorDisabled=true",
        "truffle"])
    if exists(compiler_log_file):
        remove(compiler_log_file)

def gate_body(args, tasks):
    with Task('Vm: GraalVM dist names', tasks, tags=['names']) as t:
        if t:
            mx_sdk_vm.verify_graalvm_configs(suites=['vm', 'vm-enterprise'])

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

            # run avrora on the GraalVM binary itself
            with Task('LibGraal Compiler:Basic', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t: _test_libgraal_basic(extra_vm_arguments)
            with Task('LibGraal Compiler:FatalErrorHandling', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t: _test_libgraal_fatal_error_handling()

            with Task('LibGraal Compiler:CTW', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t: _test_libgraal_ctw(extra_vm_arguments)

            import mx_compiler
            mx_compiler.compiler_gate_benchmark_runner(tasks, extra_vm_arguments, prefix='LibGraal Compiler:')

            with Task('LibGraal Truffle:unittest', tasks, tags=[VmGateTasks.libgraal]) as t:
                if t: _test_libgraal_truffle(extra_vm_arguments)
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
    native_image_cmd = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'native-image') + ('.cmd' if mx.get_os() == 'windows' else '')
    svm = mx.suite('substratevm')
    if not exists(native_image_cmd) or not svm:
        mx.abort("Image building not accessible in GraalVM {}. Build GraalVM with native-image support".format(mx_sdk_vm_impl.graalvm_dist_name()))
    # useful to speed up image creation during development
    hosted_assertions = mx.get_env("DISABLE_SVM_IMAGE_HOSTED_ASSERTIONS", "false") != "true"
    @contextmanager
    def native_image_context(common_args=None, hosted_assertions=hosted_assertions):
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
                sulong_libs = join(mx_sdk_vm_impl.graalvm_output(), 'jre' if mx_sdk_vm.base_jdk_version() == 8 else '', 'languages', 'llvm')
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

def build_tests_image(image_dir, options, unit_tests=None, additional_deps=None, shared_lib=False):
    native_image_context, svm = graalvm_svm()
    with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
        import mx_compiler
        build_options = [] + options
        if shared_lib:
            build_options = build_options + ['--shared']
        build_deps = []
        unittests_file = None
        if unit_tests:
            build_options = build_options + ['-ea']
            unittest_deps = []
            unittests_file = join(image_dir, 'unittest.tests')
            mx_unittest._run_tests(unit_tests,
                                   lambda deps, vm_launcher, vm_args: unittest_deps.extend(deps),
                                   mx_unittest._VMLauncher('dummy_launcher', None, mx_compiler.jdk),
                                   ['@Test', '@Parameters'],
                                   unittests_file,
                                   None,
                                   None,
                                   None,
                                   None)
            if not exists(unittests_file):
                mx.abort('No unit tests found matching the criteria {}'.format(",".join(unit_tests)))
            build_deps = build_deps + unittest_deps

        if additional_deps:
            build_deps = build_deps + additional_deps
        extra_image_args = mx.get_runtime_jvm_args(build_deps, jdk=mx_compiler.jdk)
        tests_image = native_image(build_options + extra_image_args)
        import configparser
        artifacts = configparser.RawConfigParser(allow_no_value=True)
        artifacts_file_path = tests_image + '.build_artifacts.txt'
        if not exists(artifacts_file_path):
            mx.abort('Tests image build artifacts not found.')
        artifacts.read(artifacts_file_path)
        if shared_lib:
            if not any(s == 'SHARED_LIB' for s in artifacts.sections()):
                mx.abort('Shared lib not found in image build artifacts.')
            tests_image_path = join(image_dir, str(artifacts.items('SHARED_LIB')[0][0]))
        else:
            if not any(s == 'EXECUTABLE' for s in artifacts.sections()):
                mx.abort('Executable not found in image build artifacts.')
            tests_image_path = join(image_dir, str(artifacts.items('EXECUTABLE')[0][0]))
        mx.logv('Test image path: {}'.format(tests_image_path))
        return tests_image_path, unittests_file

def gate_svm_sl_tck(tasks):
    with Task('SVM Truffle TCK', tasks, tags=[VmGateTasks.svm_sl_tck]) as t:
        if t:
            tools_suite = mx.suite('tools')
            if not tools_suite:
                mx.abort("Cannot resolve tools suite.")
            svmbuild = mkdtemp()
            try:
                options = [
                    '--macro:truffle',
                    '--tool:all',
                    '-H:Path={}'.format(svmbuild),
                    '-H:Class=org.junit.runner.JUnitCore',
                ]
                tests_image_path, tests_file = build_tests_image(svmbuild, options, ['com.oracle.truffle.tck.tests'], ['truffle:TRUFFLE_SL_TCK', 'truffle:TRUFFLE_TCK_INSTRUMENTATION'])
                with open(tests_file) as f:
                    test_classes = [l.rstrip() for l in f.readlines()]
                mx.run([tests_image_path] + test_classes)
            finally:
                if not mx._opts.verbose:
                    mx.rmtree(svmbuild)
