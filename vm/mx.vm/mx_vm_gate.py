# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import json
import shutil

import mx
import mx_subst
import mx_unittest
import mx_sdk_vm
import mx_sdk_vm_impl

import functools
import glob
import re
import os
import sys
import atexit
from mx_gate import Task

from os import environ, listdir, remove, linesep, pathsep
from os.path import join, exists, dirname, isdir, isfile, getsize, abspath
from tempfile import NamedTemporaryFile, mkdtemp
from contextlib import contextmanager
import mx_truffle

_suite = mx.suite('vm')

class VmGateTasks:
    compiler = 'compiler'
    substratevm = 'substratevm'
    substratevm_quickbuild = 'substratevm-quickbuild'
    sulong = 'sulong'
    sulong_aot = 'sulong-aot'
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
    svm_tck_test = 'svm_tck_test'
    svm_sl_tck = 'svm_sl_tck'
    svm_truffle_tck_js = 'svm-truffle-tck-js'
    svm_truffle_tck_python = 'svm-truffle-tck-python'
    truffle_unchained = 'truffle-unchained'

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # This is required by org.graalvm.component.installer.CatalogIterableTest
    vmArgs += [
        '--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED',
        '--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED',
    ]
    return vmArgs, mainClass, mainClassArgs

mx_unittest.add_config_participant(_unittest_config_participant)

def _get_CountUppercase_vmargs():
    cp = mx.project("jdk.internal.vm.compiler.test").classpath_repr()
    return ['-cp', cp, 'org.graalvm.compiler.test.CountUppercase']

def _check_compiler_log(compiler_log_file, expectations, extra_check=None, extra_log_files=None):
    """
    Checks that `compiler_log_file` exists and that its contents matches each regular expression in `expectations`.
    If all checks succeed, `compiler_log_file` is deleted.
    """
    def append_extra_logs():
        suffix = ''
        if extra_log_files:
            for extra_log_file in extra_log_files:
                if exists(extra_log_file):
                    nl = os.linesep
                    with open(extra_log_file) as fp:
                        lines = fp.readlines()
                        if len(lines) > 50:
                            lines = lines[0:25] + [f'...{nl}', f'<omitted {len(lines) - 50} lines>{nl}', f'...{nl}'] + lines[-50:]
                    if lines:
                        suffix += f'{nl}{extra_log_file}:\n' + ''.join(lines)
        return suffix

    in_exception_path = sys.exc_info() != (None, None, None)
    if not exists(compiler_log_file):
        mx.abort(f'No output written to {compiler_log_file}{append_extra_logs()}')
    with open(compiler_log_file) as fp:
        compiler_log = fp.read()
    if not isinstance(expectations, list) and not isinstance(expectations, tuple):
        expectations = [expectations]
    for pattern in expectations:
        if not re.search(pattern, compiler_log):
            mx.abort(f'Did not find expected pattern ("{pattern}") in compiler log:{linesep}{compiler_log}{append_extra_logs()}')
    if extra_check is not None:
        extra_check(compiler_log)
    if mx.get_opts().verbose or in_exception_path:
        mx.log(compiler_log)
    remove(compiler_log_file)
    if extra_log_files:
        for extra_log_file in extra_log_files:
            remove(extra_log_file)

def _test_libgraal_basic(extra_vm_arguments, libgraal_location):
    """
    Tests basic libgraal execution by running CountUppercase, ensuring it has a 0 exit code
    and that the output for -DgraalShowConfiguration=info describes a libgraal execution.
    """

    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    graalvm_jdk = mx.JDKConfig(graalvm_home)
    jres = [('GraalVM', graalvm_home, [])]

    if mx_sdk_vm.jlink_has_save_jlink_argfiles(graalvm_jdk):
        # Create a minimal image that should contain libgraal
        libgraal_jre = abspath('libgraal-jre')
        if exists(libgraal_jre):
            mx.rmtree(libgraal_jre)
        mx.run([join(graalvm_home, 'bin', 'jlink'), f'--output={libgraal_jre}', '--add-modules=java.base'])
        jres.append(('LibGraal JRE', libgraal_jre, []))
        atexit.register(mx.rmtree, libgraal_jre)

    # Tests that dropping libgraal into OracleJDK works
    oraclejdk = mx.get_env('ORACLEJDK_JAVA_HOME')
    if oraclejdk:
        oraclejdk_confg = mx.JDKConfig(oraclejdk)
        # Only run this test if JAVA_HOME and ORACLEJDK_JAVA_HOME have
        # the same major Java version. Even then there's a chance of incompatibility
        # if labsjdk is based on a different OracleJDK build.
        if graalvm_jdk.javaCompliance.value >= 22 and graalvm_jdk.javaCompliance.value == oraclejdk_confg.javaCompliance.value:
            libjvmci = libgraal_location
            assert exists(libjvmci), ('missing', libjvmci)
            oraclejdk_libgraal = abspath('oraclejdk_libgraal')
            if exists(oraclejdk_libgraal):
                mx.rmtree(oraclejdk_libgraal)
            shutil.copytree(oraclejdk, oraclejdk_libgraal)
            shutil.copy(libjvmci, join(oraclejdk_libgraal, 'bin' if mx.get_os() == 'windows' else 'lib'))
            jres.append(('OracleJDK+libgraal', oraclejdk_libgraal, ['-XX:+UnlockExperimentalVMOptions', '-XX:+UseJVMCICompiler']))
            atexit.register(mx.rmtree, oraclejdk_libgraal)

    expect = r"Using compiler configuration '[^']+' \(\"[^\"]+\"\) provided by [\.\w]+ loaded from a[ \w]* Native Image shared library"
    compiler_log_file = abspath('graal-compiler.log')

    # To test Graal stubs do not create and install duplicate RuntimeStubs:
    # - use a new libgraal isolate for each compilation
    # - use 1 JVMCI compiler thread
    # - enable PrintCompilation (which also logs stub compilations)
    # - check that log shows at least one stub is compiled and each stub is compiled at most once
    check_stub_sharing = [
        '-XX:JVMCIThreadsPerNativeLibraryRuntime=1',
        '-XX:JVMCICompilerIdleDelay=0',
        '-XX:JVMCIThreads=1',
        '-Dlibgraal.PrintCompilation=true',
        '-Dlibgraal.LogFile=' + compiler_log_file,
    ]

    def extra_check(compiler_log):
        """
        Checks that compiler log shows at least stub compilation
        and that each stub is compiled at most once.
        """
        nl = linesep
        stub_compilations = {}
        stub_compilation = re.compile(r'StubCompilation-\d+ +<stub> +([\S]+) +([\S]+) +.*')
        for line in compiler_log.split('\n'):
            m = stub_compilation.match(line)
            if m:
                stub = f'{m.group(1)}{m.group(2)}'
                stub_compilations[stub] = stub_compilations.get(stub, 0) + 1
        if not stub_compilations:
            mx.abort(f'Expected at least one stub compilation in compiler log:\n{compiler_log}')
        duplicated = {stub: count for stub, count in stub_compilations.items() if count > 1}
        if duplicated:
            table = f'  Count    Stub{nl}  ' + f'{nl}  '.join((f'{count:<8d} {stub}') for stub, count in stub_compilations.items())
            mx.abort(f'Following stubs were compiled more than once according to compiler log:{nl}{table}')

    args = check_stub_sharing + ['-Dgraal.ShowConfiguration=verbose'] + _get_CountUppercase_vmargs()

    # Verify execution via raw java launcher in `mx graalvm-home`.
    for jre_name, jre, jre_args in jres:
        try:
            cmd = [join(jre, 'bin', 'java')] + jre_args + args
            mx.log(f'{jre_name}: {" ".join(cmd)}')
            mx.run(cmd)
        finally:
            _check_compiler_log(compiler_log_file, expect, extra_check=extra_check)

    # Verify execution via `mx vm`.
    import mx_compiler
    try:
        mx.log(f'mx.run_vm: args={extra_vm_arguments + args}')
        mx_compiler.run_vm(extra_vm_arguments + args)
    finally:
        _check_compiler_log(compiler_log_file, expect)

def _test_libgraal_fatal_error_handling():
    """
    Tests that fatal errors in libgraal route back to HotSpot fatal error handling.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    vmargs = ['-XX:+PrintFlagsFinal',
              '-Dlibgraal.CrashAt=*',
              '-Dlibgraal.CrashAtIsFatal=true']
    cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + _get_CountUppercase_vmargs()
    out = mx.OutputCapture()
    scratch_dir = mkdtemp(dir='.')
    exitcode = mx.run(cmd, nonZeroIsFatal=False, err=out, out=out, cwd=scratch_dir)
    if exitcode == 0:
        if 'CrashAtIsFatal: no fatalError function pointer installed' in out.data:
            # Executing a VM that does not configure fatal errors handling
            # in libgraal to route back through the VM.
            pass
        else:
            mx.abort('Expected benchmark to result in non-zero exit code: ' + ' '.join(cmd) + linesep + out.data)
    else:
        if not isdir(scratch_dir):
            mx.abort("No scratch dir found despite error being expected!")
        seen_libjvmci_log = False
        hs_errs = glob.glob(join(scratch_dir, 'hs_err_pid*.log'))
        if not hs_errs:
            mx.abort('Expected a file starting with "hs_err_pid" in test directory. Entries found=' + str(listdir(scratch_dir)))

        for hs_err in hs_errs:
            mx.log(f"Verifying content of {hs_err}")
            with open(hs_err) as fp:
                contents = fp.read()
            if 'libjvmci' in hs_err:
                seen_libjvmci_log = True
                if 'Fatal error: Forced crash' not in contents:
                    mx.abort('Expected "Fatal error: Forced crash" to be in contents of ' + hs_err + ':' + linesep + contents)
            else:
                if 'Fatal error in JVMCI' not in contents:
                    if 'SubstrateDiagnostics$DumpThreads_printDiagnostics' in contents:
                        # GR-39833 workaround
                        pass
                    else:
                        mx.abort('Expected "Fatal error in JVMCI" to be in contents of ' + hs_err + ':' + linesep + contents)

        if 'JVMCINativeLibraryErrorFile' in out.data and not seen_libjvmci_log:
            mx.abort('Expected a file matching "hs_err_pid*_libjvmci.log" in test directory. Entries found=' + str(listdir(scratch_dir)))

    # Only clean up scratch dir on success
    mx.log(f"Cleaning up scratch dir after gate task completion: {scratch_dir}")
    mx.rmtree(scratch_dir)

def _test_libgraal_oome_dumping():
    """
    Tests the HeapDumpOnOutOfMemoryError libgraal option.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    scratch_dir = mkdtemp(prefix='oome_heap_dumps', dir='.')
    os.mkdir(join(scratch_dir, 'subdir'))
    inputs = {
        '': join(scratch_dir, 'libgraal_pid*.hprof'),
        'custom.hprof': join(scratch_dir, 'custom.hprof'),
        'subdir': join(scratch_dir, 'subdir', 'libgraal_pid*.hprof'),
    }
    if mx.is_windows():
        # GR-39501
        mx.log('-Dlibgraal.HeapDumpOnOutOfMemoryError=true is not supported on Windows')
        return

    for n, v in inputs.items():
        vmargs = ['-Dlibgraal.CrashAt=*',
                  '-Dlibgraal.Xmx128M',
                  '-Dlibgraal.PrintGC=true',
                  '-Dlibgraal.HeapDumpOnOutOfMemoryError=true',
                  f'-Dlibgraal.HeapDumpPath={n}',
                  '-Dlibgraal.CrashAtThrowsOOME=true']
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + _get_CountUppercase_vmargs()
        mx.run(cmd, cwd=scratch_dir)
        heap_dumps = glob.glob(v)
        if not heap_dumps:
            mx.abort(f'No heap dumps found (glob: {v})')
        if len(heap_dumps) != 1:
            mx.abort(f'More than 1 heap dump found (glob: {v}): {heap_dumps}')
        hd = heap_dumps[0]
        mx.log(f'Heap dump: {hd} ({getsize(hd):,} bytes)')
        os.remove(hd)

    # Only clean up scratch dir on success
    mx.log(f"Cleaning up scratch dir after gate task completion: {scratch_dir}")
    mx.rmtree(scratch_dir)

def _test_libgraal_systemic_failure_detection():
    """
    Tests that system compilation failures are detected and cause the VM to exit.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    for rate in (-1, 1):
        vmargs = [
            '-Dgraal.CrashAt=*',
            f'-Dgraal.SystemicCompilationFailureRate={rate}',
            '-Dgraal.DumpOnError=false',
            '-Dgraal.CompilationFailureAction=Silent'
        ]
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + _get_CountUppercase_vmargs()
        out = mx.OutputCapture()
        scratch_dir = mkdtemp(dir='.')
        exitcode = mx.run(cmd, nonZeroIsFatal=False, err=out, out=out, cwd=scratch_dir)
        expect_exitcode_0 = rate >= 0
        if (exitcode == 0) != expect_exitcode_0:
            mx.abort(f'Unexpected benchmark exit code ({exitcode}): ' + ' '.join(cmd) + linesep + out.data)
        else:
            expect = 'Systemic Graal compilation failure detected'
            if expect not in out.data:
                mx.abort(f'Expected "{expect}" in output:{linesep}{out.data}')

        # Only clean up scratch dir on success
        mx.log(f"Cleaning up scratch dir after gate task completion: {scratch_dir}")
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

def _test_libgraal_CompilationTimeout_JIT():
    """
    Tests timeout handling of CompileBroker compilations.
    """

    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    compiler_log_file = abspath('graal-compiler.log')
    G = '-Dgraal.' #pylint: disable=invalid-name
    for vm_can_exit in (False, True):
        vm_exit_delay = 0 if not vm_can_exit else 2
        vmargs = [f'{G}CompilationWatchDogStartDelay=1',  # set compilation timeout to 1 sec
                  f'{G}InjectedCompilationDelay=4',       # inject a 4 sec compilation delay
                  f'{G}CompilationWatchDogVMExitDelay={vm_exit_delay}',
                  f'{G}CompilationFailureAction=Print',
                  f'{G}PrintCompilation=false',
                  f'{G}LogFile={compiler_log_file}',
                   '-Ddebug.graal.CompilationWatchDog=true'] # helps debug failure

        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + _get_CountUppercase_vmargs()
        exit_code = mx.run(cmd, nonZeroIsFatal=False)
        expectations = ['detected long running compilation'] + (['a stuck compilation'] if vm_can_exit else [])
        _check_compiler_log(compiler_log_file, expectations)
        if vm_can_exit:
            # org.graalvm.compiler.core.CompilationWatchDog.EventHandler.STUCK_COMPILATION_EXIT_CODE
            if exit_code != 84:
                mx.abort(f'expected process to exit with 84 (indicating a stuck compilation) instead of {exit_code}')
        elif exit_code != 0:
            mx.abort(f'process exit code was {exit_code}, not 0')

def _test_libgraal_CompilationTimeout_Truffle(extra_vm_arguments):
    """
    Tests timeout handling of Truffle PE compilations.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    compiler_log_file = abspath('graal-compiler.log')
    truffle_log_file = abspath('truffle-compiler.log')
    G = '-Dgraal.' #pylint: disable=invalid-name
    P = '-Dpolyglot.engine.' #pylint: disable=invalid-name
    for vm_can_exit in (False, True):
        vm_exit_delay = 0 if not vm_can_exit else 2
        vmargs = [f'{G}CompilationWatchDogStartDelay=1',  # set compilation timeout to 1 sec
                  f'{G}InjectedCompilationDelay=4',       # inject a 4 sec compilation delay
                  f'{G}CompilationWatchDogVMExitDelay={vm_exit_delay}',
                  f'{G}CompilationFailureAction=Print',
                  f'{G}ShowConfiguration=info',
                  f'{G}MethodFilter=delay',
                  f'{G}PrintCompilation=false',
                  f'{G}LogFile={compiler_log_file}',
                  f'{P}AllowExperimentalOptions=true',
                  f'{P}TraceCompilation=false',
                  f'{P}CompileImmediately=true',
                  f'{P}BackgroundCompilation=false',
                  f'-Dpolyglot.log.file={truffle_log_file}',
                   '-Ddebug.graal.CompilationWatchDog=true', # helps debug failure
                   '-Dgraalvm.locatorDisabled=true',
                   '-XX:-UseJVMCICompiler',       # Stop compilation timeout being applied to JIT
                   '-XX:+UseJVMCINativeLibrary']  # but ensure libgraal is still used by Truffle

        delay = abspath(join(dirname(__file__), 'Delay.sl'))
        cp_args = mx.get_runtime_jvm_args(mx_truffle.resolve_sl_dist_names(use_optimized_runtime=True, use_enterprise=True))
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + cp_args + ['--module', 'org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain', delay]
        err = mx.OutputCapture()
        exit_code = mx.run(cmd, nonZeroIsFatal=False, err=err)
        if err.data:
            mx.log(err.data)

        expectations = ['detected long running compilation'] + (['a stuck compilation'] if vm_can_exit else [])
        _check_compiler_log(compiler_log_file, expectations, extra_log_files=[truffle_log_file])
        if vm_can_exit:
            # org.graalvm.compiler.core.CompilationWatchDog.EventHandler.STUCK_COMPILATION_EXIT_CODE
            if exit_code != 84:
                mx.abort(f'expected process to exit with 84 (indicating a stuck compilation) instead of {exit_code}')
        elif exit_code != 0:
            mx.abort(f'process exit code was {exit_code}, not 0')

def _test_libgraal_ctw(extra_vm_arguments):
    import mx_compiler

    if _jdk_has_ForceTranslateFailure_jvmci_option(mx_compiler.jdk):
        # Tests that failures in HotSpotJVMCIRuntime.translate do not cause the VM to exit.
        # This test is only possible if the jvmci.ForceTranslateFailure option exists.
        compiler_log_file = abspath('graal-compiler-ctw.log')
        fail_to_translate_value = 'nmethod/StackOverflowError:hotspot,method/String.hashCode:native,valueOf'
        expectations = [f'ForceTranslateFailure filter "{f}"' for f in fail_to_translate_value.split(',')]
        try:
            mx_compiler.ctw([
               f'-DCompileTheWorld.Config=Inline=false {" ".join(mx_compiler._compiler_error_options(prefix=""))}',
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
    unittest_args += ["--enable-timing", "--verbose"]
    mx_unittest.unittest(unittest_args + extra_vm_arguments + [
        "-Dpolyglot.engine.AllowExperimentalOptions=true",
        "-Dpolyglot.engine.CompileImmediately=true",
        "-Dpolyglot.engine.BackgroundCompilation=false",
        "-Dpolyglot.engine.CompilationFailureAction=Throw",
        "-Dgraalvm.locatorDisabled=true",
        "truffle", "LibGraalCompilerTest"])

def gate_body(args, tasks):
    with Task('Vm: GraalVM dist names', tasks, tags=['names']) as t:
        if t:
            mx_sdk_vm.verify_graalvm_configs(suites=['vm', 'vm-enterprise'])

    with Task('Vm: ce-release-artifacts.json', tasks, tags=['style']) as t:
        if t:
            with open(join(_suite.dir, 'ce-release-artifacts.json'), 'r') as f:
                # check that this file can be read as json
                json.load(f)

    with Task('Vm: Basic GraalVM Tests', tasks, tags=[VmGateTasks.compiler]) as t:
        if t and mx_sdk_vm_impl.has_component('GraalVM compiler'):
            # 1. the build must be a GraalVM
            # 2. the build must be JVMCI-enabled since the 'GraalVM compiler' component is registered
            mx_sdk_vm_impl.check_versions(mx_sdk_vm_impl.graalvm_output(), expect_graalvm=True, check_jvmci=True)

    libgraal_suite_name = 'substratevm'
    if mx.suite(libgraal_suite_name, fatalIfMissing=False) is not None:
        import mx_substratevm
        # Use `short_name` rather than `name` since the code that follows
        # should be executed also when "LibGraal Enterprise" is registered
        if mx_sdk_vm_impl.has_component(mx_substratevm.libgraal.short_name):
            libgraal_location = mx_sdk_vm_impl.get_native_image_locations(mx_substratevm.libgraal.short_name, 'jvmcicompiler')
            if libgraal_location is None:
                mx.warn("Skipping libgraal tests: no library enabled in the LibGraal component")
            else:
                extra_vm_arguments = ['-XX:+UseJVMCICompiler', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location), '-XX:JVMCIThreadsPerNativeLibraryRuntime=1']
                if args.extra_vm_argument:
                    extra_vm_arguments += args.extra_vm_argument

                with Task('LibGraal Compiler:Basic', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_basic(extra_vm_arguments, libgraal_location)
                with Task('LibGraal Compiler:FatalErrorHandling', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_fatal_error_handling()
                with Task('LibGraal Compiler:OOMEDumping', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_oome_dumping()
                with Task('LibGraal Compiler:SystemicFailureDetection', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_systemic_failure_detection()
                with Task('LibGraal Compiler:CompilationTimeout:JIT', tasks, tags=[VmGateTasks.libgraal]) as t:
                    if t: _test_libgraal_CompilationTimeout_JIT()
                with Task('LibGraal Compiler:CompilationTimeout:Truffle', tasks, tags=[VmGateTasks.libgraal]) as t:
                    if t: _test_libgraal_CompilationTimeout_Truffle(extra_vm_arguments)

                with Task('LibGraal Compiler:CTW', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_ctw(extra_vm_arguments)

                import mx_compiler
                mx_compiler.compiler_gate_benchmark_runner(tasks, extra_vm_arguments, prefix='LibGraal Compiler:')

                with Task('LibGraal Truffle:unittest', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_truffle(extra_vm_arguments)
        else:
            mx.warn("Skipping libgraal tests: component not enabled")
    else:
        mx.warn("Skipping libgraal tests: suite '{suite}' not found. Did you forget to dynamically import it? (--dynamicimports {suite})".format(suite=libgraal_suite_name))

    gate_substratevm(tasks)
    gate_substratevm(tasks, quickbuild=True)
    gate_sulong(tasks)
    gate_python(tasks)
    gate_svm_truffle_tck_smoke_test(tasks)
    gate_svm_sl_tck(tasks)
    gate_svm_truffle_tck_js(tasks)
    gate_svm_truffle_tck_python(tasks)
    gate_truffle_unchained(tasks)

def graalvm_svm():
    """
    Gives access to image building withing the GraalVM release. Requires dynamic import of substratevm.
    """
    native_image_cmd = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'native-image') + ('.cmd' if mx.get_os() == 'windows' else '')
    svm = mx.suite('substratevm')
    if not exists(native_image_cmd) or not svm:
        mx.abort(f"Image building not accessible in GraalVM {mx_sdk_vm_impl.graalvm_dist_name()}. Build GraalVM with native-image support")
    # useful to speed up image creation during development
    hosted_assertions = mx.get_env("DISABLE_SVM_IMAGE_HOSTED_ASSERTIONS", "false") != "true"
    @contextmanager
    def native_image_context(common_args=None, hosted_assertions=hosted_assertions):
        with svm.extensions.native_image_context(common_args, hosted_assertions, native_image_cmd=native_image_cmd) as native_image:
            yield native_image
    return native_image_context, svm.extensions

def gate_substratevm(tasks, quickbuild=False):
    tag = VmGateTasks.substratevm
    name = 'Run Truffle API tests on SVM'
    extra_build_args = []
    if quickbuild:
        tag = VmGateTasks.substratevm_quickbuild
        name += ' with quickbuild'
        extra_build_args = ['-Ob']

    with Task(name, tasks, tags=[tag]) as t:
        if t:
            tests = ['com.oracle.truffle.api.test.polyglot']
            with NamedTemporaryFile(prefix='blacklist.', mode='w', delete=False) as fp:
                # ContextPreInitializationNativeImageTest must run in its own image
                fp.file.writelines([l + '\n' for l in ['com.oracle.truffle.api.test.polyglot.ContextPreInitializationNativeImageTest']])
                blacklist_args = ["--blacklist", fp.name]

            truffle_with_compilation = [
                '--verbose',
                '--macro:truffle',
                '--language:nfi',
                '--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
                '--add-exports=org.graalvm.polyglot/org.graalvm.polyglot.impl=ALL-UNNAMED',
                '-R:MaxHeapSize=2g',
                '--enable-url-protocols=jar',
                '--enable-url-protocols=http',
                '-H:MaxRuntimeCompileMethods=5000',
            ]
            truffle_without_compilation = truffle_with_compilation + [
                '-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime'
            ]
            args = ['--build-args'] + truffle_with_compilation + extra_build_args + blacklist_args + ['--'] + tests
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                svm._native_unittest(native_image, args)

            args = ['--build-args'] + truffle_without_compilation + extra_build_args + blacklist_args + ['--run-args', '--verbose', '-Dpolyglot.engine.WarnInterpreterOnly=false'] + ['--'] + tests
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                svm._native_unittest(native_image, args)

def gate_sulong(tasks):
    with Task('Run SulongSuite tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            lli = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--enable-timing'])

    with Task('Run SulongSuite tests as native-image with engine cache', tasks, tags=[VmGateTasks.sulong_aot]) as t:
        if t:
            lli = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--enable-timing', '--sulong-config', 'AOTCacheStoreNative'])
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--enable-timing', '--sulong-config', 'AOTCacheLoadNative'])

    with Task('Run Sulong interop tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            sulong = mx.suite('sulong')
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                # TODO Use mx_sdk_vm_impl.get_final_graalvm_distribution().find_single_source_location to rewire SULONG_HOME
                sulong_libs = join(mx_sdk_vm_impl.graalvm_output(), 'languages', 'llvm')
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
            python_svm_image_path = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'graalpy')
            python_suite = mx.suite("graalpython")
            python_suite.extensions.run_python_unittests(python_svm_image_path)

def _svm_truffle_tck(native_image, svm_suite, language_suite, language_id, language_distribution=None, fail_on_error=True):
    assert not language_distribution if language_suite else language_distribution, 'Either language_suite or language_distribution must be given'
    dists = [d for d in svm_suite.dists if d.name == 'SVM_TRUFFLE_TCK']
    if not dists:
        mx.abort("Cannot resolve: SVM_TRUFFLE_TCK distribution.")

    def _collect_excludes(suite, suite_import, excludes):
        excludes_dir = join(suite.mxDir, 'truffle.tck.permissions')
        if isdir(excludes_dir):
            for excludes_file in listdir(excludes_dir):
                excludes.append(join(excludes_dir, excludes_file))
        imported_suite = mx.suite(suite_import.name)
        imported_suite.visit_imports(_collect_excludes, excludes=excludes)

    excludes = []
    if language_suite:
        language_suite.visit_imports(_collect_excludes, excludes=excludes)
        macro_options = [f'--language:{language_id}']
    else:
        macro_options = ['--macro:truffle']
        dists = dists + [language_distribution]
    cp = pathsep.join([d.classpath_repr() for d in dists])
    svmbuild = mkdtemp()
    try:
        report_file = join(svmbuild, "language_permissions.log")
        options = macro_options + [
            '--features=com.oracle.svm.truffle.tck.PermissionsFeature',
        ] + mx_sdk_vm_impl.svm_experimental_options([
            '-H:ClassInitialization=:build_time',
            '-H:+EnforceMaxRuntimeCompileMethods',
            '-H:-InlineBeforeAnalysis',
            '-H:-ParseOnceJIT', #GR-47163
            '-H:-VerifyDeoptimizationEntryPoints', #GR-47163
            '-cp',
            cp,
            '-H:-FoldSecurityManagerGetter',
            f'-H:TruffleTCKPermissionsReportFile={report_file}',
            f'-H:Path={svmbuild}',
        ]) + [
            'com.oracle.svm.truffle.tck.MockMain'
        ]
        if excludes:
            options += mx_sdk_vm_impl.svm_experimental_options([f"-H:TruffleTCKPermissionsExcludeFiles={','.join(excludes)}"])
        native_image(options)
        if isfile(report_file) and getsize(report_file) > 0:
            message = f"Failed: Language {language_id} performs following privileged calls:\n\n"
            with open(report_file, "r") as f:
                for line in f.readlines():
                    message = message + line
            if fail_on_error:
                mx.abort(message)
            else:
                return message
    finally:
        mx.rmtree(svmbuild)
    return None

def gate_svm_truffle_tck_smoke_test(tasks):
    with Task('SVM Truffle TCK Smoke Test', tasks, tags=[VmGateTasks.svm_tck_test]) as t:
        if t:
            truffle_suite = mx.suite('truffle')
            test_language_dist = [d for d in truffle_suite.dists if d.name == 'TRUFFLE_TCK_TESTS_LANGUAGE'][0]
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                result = _svm_truffle_tck(native_image, svm.suite, None, 'TCKSmokeTestLanguage', test_language_dist, False)
                if not 'Failed: Language TCKSmokeTestLanguage performs following privileged calls' in result:
                    mx.abort("Expected failure, log:\n" + result)
                if not 'UnsafeCallNode.doUnsafeAccess' in result:
                    mx.abort("Missing UnsafeCallNode.doUnsafeAccess call in the log, log:\n" + result)
                if not 'UnsafeCallNode.doUnsafeAccessBehindBoundary' in result:
                    mx.abort("Missing UnsafeCallNode.doUnsafeAccessBehindBoundary call in the log, log:\n" + result)
                if not 'PrivilegedCallNode.doPrivilegedCall' in result:
                    mx.abort("Missing PrivilegedCallNode.doPrivilegedCall call in the log, log:\n" + result)
                if not 'PrivilegedCallNode.doPrivilegedCallBehindBoundary' in result:
                    mx.abort("Missing PrivilegedCallNode.doPrivilegedCallBehindBoundary call in the log, log:\n" + result)


def gate_svm_truffle_tck_js(tasks):
    with Task('JavaScript SVM Truffle TCK', tasks, tags=[VmGateTasks.svm_truffle_tck_js]) as t:
        if t:
            js_suite = mx.suite('graal-js')
            if not js_suite:
                mx.abort("Cannot resolve graal-js suite.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, svm.suite, js_suite, 'js')



def gate_svm_truffle_tck_python(tasks):
    with Task('Python SVM Truffle TCK', tasks, tags=[VmGateTasks.svm_truffle_tck_python]) as t:
        if t:
            py_suite = mx.suite('graalpython')
            if not py_suite:
                mx.abort("Cannot resolve graalpython suite.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, svm.suite, py_suite, 'python')

def gate_truffle_unchained(tasks):
    truffle_suite = mx.suite('truffle')
    with Task('Truffle Unchained Truffle ModulePath Unit Tests', tasks, tags=[VmGateTasks.truffle_unchained]) as t:
        if t:
            if not truffle_suite:
                mx.abort("Cannot resolve truffle suite.")
            mx_truffle.truffle_jvm_module_path_unit_tests_gate()
    with Task('Truffle Unchained Truffle ClassPath Unit Tests', tasks, tags=[VmGateTasks.truffle_unchained]) as t:
        if t:
            if not truffle_suite:
                mx.abort("Cannot resolve truffle suite.")
            mx_truffle.truffle_jvm_class_path_unit_tests_gate()
    with Task('Truffle Unchained SL JVM', tasks, tags=[VmGateTasks.truffle_unchained]) as t:
        if t:
            if not truffle_suite:
                mx.abort("Cannot resolve truffle suite.")
            mx_truffle.sl_jvm_gate_tests()
    with Task('Truffle Unchained SL Native Fallback', tasks, tags=[VmGateTasks.truffle_unchained]) as t:
        if t:
            if not truffle_suite:
                mx.abort("Cannot resolve truffle suite.")
            mx_truffle.sl_native_fallback_gate_tests()
    with Task('Truffle Unchained SL Native Optimized', tasks, tags=[VmGateTasks.truffle_unchained]) as t:
        if t:
            if not truffle_suite:
                mx.abort("Cannot resolve truffle suite.")
            mx_truffle.sl_native_optimized_gate_tests()

def build_tests_image(image_dir, options, unit_tests=None, additional_deps=None, shared_lib=False):
    native_image_context, svm = graalvm_svm()
    with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
        import mx_compiler
        build_options = mx_sdk_vm_impl.svm_experimental_options(['-H:+GenerateBuildArtifactsFile']) + options
        if shared_lib:
            build_options = build_options + ['--shared']
        build_deps = []
        unittests_file = None
        if unit_tests:
            build_options = build_options + ['-ea', '-esa']
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
                mx.abort(f"No unit tests found matching the criteria {','.join(unit_tests)}")
            build_deps = build_deps + unittest_deps

        if additional_deps:
            build_deps = build_deps + additional_deps
        extra_image_args = mx.get_runtime_jvm_args(build_deps, jdk=mx_compiler.jdk, exclude_names=mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes)
        native_image(build_options + extra_image_args)
        artifacts_file_path = join(image_dir, 'build-artifacts.json')
        if not exists(artifacts_file_path):
            mx.abort(f'{artifacts_file_path} for tests image not found.')
        with open(artifacts_file_path) as f:
            artifacts = json.load(f)
        kind = 'shared_libraries' if shared_lib else 'executables'
        if kind not in artifacts:
            mx.abort(f'{kind} not found in {artifacts_file_path}.')
        if len(artifacts[kind]) != 1:
            mx.abort(f"Expected {kind} list with one element, found {len(artifacts[kind])}: {', '.join(artifacts[kind])}.")
        tests_image_path = join(image_dir, artifacts[kind][0])
        mx.logv(f'Test image path: {tests_image_path}')
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
                ] + mx_sdk_vm_impl.svm_experimental_options([
                    f'-H:Path={svmbuild}',
                    '-H:Class=org.junit.runner.JUnitCore',
                ])
                tests_image_path, tests_file = build_tests_image(svmbuild, options, ['com.oracle.truffle.tck.tests'], ['truffle:TRUFFLE_SL_TCK', 'truffle:TRUFFLE_TCK_INSTRUMENTATION'])
                with open(tests_file) as f:
                    test_classes = [l.rstrip() for l in f.readlines()]
                mx.run([tests_image_path] + test_classes)
            finally:
                if not mx._opts.verbose:
                    mx.rmtree(svmbuild)
