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

import json
import shutil

import mx
import mx_subst
import mx_unittest
import mx_sdk
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_sdk_vm_ng
import mx_truffle
import mx_vm

import functools
import glob
import gzip
import re
import os
import pathlib
import sys
import atexit
from mx_gate import Task

from datetime import datetime
from os import environ, listdir, remove, linesep
from os.path import join, exists, dirname, isdir, isfile, getsize, abspath
from tempfile import NamedTemporaryFile, mkdtemp, mkstemp
from contextlib import contextmanager
from urllib.parse import urlunparse

_suite = mx.suite('vm')

class VmGateTasks:
    compiler = 'compiler'
    substratevm = 'substratevm'
    substratevm_quickbuild = 'substratevm-quickbuild'
    sulong = 'sulong'
    sulong_aot = 'sulong-aot'
    graal_js_all = 'graal-js'
    graal_js_tests = 'graal-js-tests'
    graal_js_tests_compiled = 'graal-js-tests-compiled'
    graal_nodejs = 'graal-nodejs'
    python = 'python'
    fastr = 'fastr'
    integration = 'integration'
    tools = 'tools'
    libgraal = 'libgraal'
    truffle_native_tck = 'truffle-native-tck'
    truffle_native_tck_sl = 'truffle-native-tck-sl'
    truffle_native_tck_js = 'truffle-native-tck-js'
    truffle_native_tck_python = 'truffle-native-tck-python'
    truffle_native_tck_wasm = 'truffle-native-tck-wasm'
    truffle_isolate_internal_unittest = 'truffle_isolate_internal_unittest'
    truffle_isolate_external_unittest = 'truffle_isolate_external_unittest'
    maven_downloader = 'maven-downloader'
    truffle_maven_deploy_local = 'truffle_maven_deploy_local'
    truffle_maven_isolate_internal = 'truffle_maven_isolate_internal'
    truffle_maven_isolate_external = 'truffle_maven_isolate_external'

def _get_CountUppercase_vmargs():
    cp = mx.project("jdk.graal.compiler.test").classpath_repr()
    return ['-cp', cp, 'jdk.graal.compiler.test.CountUppercase']

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
                    with open(extra_log_file, encoding='utf-8') as fp:
                        lines = fp.readlines()
                        if len(lines) > 50:
                            lines = lines[0:25] + [f'...{nl}', f'<omitted {len(lines) - 50} lines>{nl}', f'...{nl}'] + lines[-50:]
                    if lines:
                        suffix += f'{nl}{extra_log_file}:\n' + ''.join(lines)
        return suffix

    in_exception_path = sys.exc_info() != (None, None, None)
    if not exists(compiler_log_file):
        mx.abort(f'No output written to {compiler_log_file}{append_extra_logs()}')
    with open(compiler_log_file, encoding='utf-8') as fp:
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

def _test_libgraal_check_build_path(libgraal_location):
    """
    If ``mx_substratevm.allow_build_path_in_libgraal()`` is False, tests that libgraal does not contain
    strings whose prefix is the absolute path of the SDK suite.
    """
    import mx_compiler
    import mx_substratevm
    import subprocess
    if not mx_substratevm.allow_build_path_in_libgraal():
        sdk_suite_dir = mx.suite('sdk').dir
        tool_path = join(sdk_suite_dir, 'src/org.graalvm.nativeimage.test/src/org/graalvm/nativeimage/test/FindPathsInBinary.java'.replace('/', os.sep))
        cmd = [mx_compiler.jdk.java, tool_path, libgraal_location, sdk_suite_dir]
        mx.logv(' '.join(cmd))
        matches = subprocess.check_output(cmd, universal_newlines=True).strip()
        if len(matches) != 0:
            mx.abort(f"Found strings in {libgraal_location} with illegal prefix \"{sdk_suite_dir}\":\n{matches}\n\nRe-run: {' '.join(cmd)}")

def _test_libgraal_basic(extra_vm_arguments, libgraal_location):
    """
    Tests basic libgraal execution by running CountUppercase, ensuring it has a 0 exit code
    and that the output for -Djdk.graal.ShowConfiguration=info describes a libgraal execution.
    """

    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    jres = [('GraalVM', graalvm_home, [])]

    # Create a minimal image that should contain libgraal
    libgraal_jre = abspath('libgraal-jre')
    if exists(libgraal_jre):
        mx.rmtree(libgraal_jre)
    mx.run([join(graalvm_home, 'bin', 'jlink'), f'--output={libgraal_jre}', '--add-modules=java.base'])
    jres.append(('LibGraal JRE', libgraal_jre, []))
    atexit.register(mx.rmtree, libgraal_jre)

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
        '-Djdk.graal.PrintCompilation=true',
        '-Djdk.graal.LogFile=' + compiler_log_file,
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

    # Test that legacy `-D.graal` options work.
    show_config_args = ('-Djdk.graal.ShowConfiguration=verbose', '-Dgraal.ShowConfiguration=verbose')

    for show_config_arg in show_config_args:
        args = check_stub_sharing + [show_config_arg] + _get_CountUppercase_vmargs()

        # Verify execution via raw java launcher in `mx graalvm-home`.
        for jre_name, jre, jre_args in jres:
            try:
                cmd = [join(jre, 'bin', 'java')] + jre_args + extra_vm_arguments + args
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

def _test_libgraal_fatal_error_handling(extra_vm_arguments):
    """
    Tests that fatal errors in libgraal route back to HotSpot fatal error handling.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    vmargs = ['-XX:+PrintFlagsFinal',
              '-Djdk.graal.CrashAt=*',
              '-Djdk.graal.CrashAtIsFatal=1']
    cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + extra_vm_arguments + _get_CountUppercase_vmargs()
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
            with open(hs_err, encoding='utf-8') as fp:
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
                # check that the hs_err contains libgraal symbols on supported platforms
                symbol_patterns = {
                    'linux' : 'com.oracle.svm.core.jdk.VMErrorSubstitutions::doShutdown',
                    'darwin' : 'VMErrorSubstitutions_doShutdown',
                    'windows' : None
                }
                pattern = symbol_patterns[mx.get_os()]
                if pattern and pattern not in contents:
                    mx.abort('Expected "' + pattern + '" to be in contents of ' + hs_err + ':' + linesep + contents)

        if 'JVMCINativeLibraryErrorFile' in out.data and not seen_libjvmci_log:
            mx.abort('Expected a file matching "hs_err_pid*_libjvmci.log" in test directory. Entries found=' + str(listdir(scratch_dir)))

    # Only clean up scratch dir on success
    mx.log(f"Cleaning up scratch dir after gate task completion: {scratch_dir}")
    mx.rmtree(scratch_dir)

def _test_libgraal_oome_dumping(extra_vm_arguments):
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
        mx.log('-Djdk.graal.internal.HeapDumpOnOutOfMemoryError=true is not supported on Windows')
        return

    for n, v in inputs.items():
        vmargs = ['-Djdk.graal.CrashAt=*',
                  '-Djdk.graal.internal.Xmx128M',
                  '-Djdk.graal.internal.PrintGC=true',
                  '-Djdk.graal.internal.HeapDumpOnOutOfMemoryError=true',
                  f'-Djdk.graal.internal.HeapDumpPath={n}',
                  '-Djdk.graal.SystemicCompilationFailureRate=0',
                  '-Djdk.graal.CrashAtThrowsOOME=true']
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + extra_vm_arguments + _get_CountUppercase_vmargs()
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

def _test_libgraal_systemic_failure_detection(extra_vm_arguments):
    """
    Tests that system compilation failures are detected and cause the VM to exit.
    """
    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    for rate in (-1, 1):
        vmargs = [
            '-Djdk.graal.CrashAt=*',
            f'-Djdk.graal.SystemicCompilationFailureRate={rate}',
            '-Djdk.graal.DumpOnError=false',
            '-Djdk.graal.CompilationFailureAction=Silent'
        ]
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + extra_vm_arguments + _get_CountUppercase_vmargs()
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

def _test_libgraal_CompilationTimeout_JIT(extra_vm_arguments):
    """
    Tests timeout handling of CompileBroker compilations.
    """

    graalvm_home = mx_sdk_vm_impl.graalvm_home()
    compiler_log_file = abspath('graal-compiler.log')
    G = '-Djdk.graal.' #pylint: disable=invalid-name
    for vm_can_exit in (False, True):
        vm_exit_delay = 0 if not vm_can_exit else 2
        vmargs = [f'{G}CompilationWatchDogStartDelay=1',  # set compilation timeout to 1 sec
                  f'{G}InjectedCompilationDelay=6',       # inject a 6 sec compilation delay
                  f'{G}CompilationWatchDogVMExitDelay={vm_exit_delay}',
                  f'{G}CompilationFailureAction=Print',
                  f'{G}PrintCompilation=false',
                  f'{G}LogFile={compiler_log_file}',
                   '-Ddebug.graal.CompilationWatchDog=true'] # helps debug failure

        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + extra_vm_arguments + _get_CountUppercase_vmargs()
        exit_code = mx.run(cmd, nonZeroIsFatal=False)
        expectations = ['detected long running compilation']
        _check_compiler_log(compiler_log_file, expectations)
        if vm_can_exit:
            # jdk.graal.compiler.core.CompilationWatchDog.EventHandler.STUCK_COMPILATION_EXIT_CODE
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
    G = '-Djdk.graal.' #pylint: disable=invalid-name
    P = '-Dpolyglot.engine.' #pylint: disable=invalid-name
    for vm_can_exit in (False, True):
        vm_exit_delay = 0 if not vm_can_exit else 2
        vmargs = [f'{G}CompilationWatchDogStartDelay=1',  # set compilation timeout to 1 sec
                  f'{G}InjectedCompilationDelay=6',       # inject a 6 sec compilation delay
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
                   '-Dpolyglot.sl.UseBytecode=false', # Bytecode interpreter not ready for immediate compilation
                   '-Ddebug.graal.CompilationWatchDog=true', # helps debug failure
                   '-Dgraalvm.locatorDisabled=true',
                   '-XX:-UseJVMCICompiler',       # Stop compilation timeout being applied to JIT
                   '-XX:+UseJVMCINativeLibrary']  # but ensure libgraal is still used by Truffle

        delay = abspath(join(dirname(__file__), 'Delay.sl'))
        cp_args = mx.get_runtime_jvm_args(mx_truffle.resolve_sl_dist_names(use_optimized_runtime=True))
        cmd = [join(graalvm_home, 'bin', 'java')] + vmargs + extra_vm_arguments + cp_args + ['--module', 'org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain', delay]
        err = mx.OutputCapture()
        exit_code = mx.run(cmd, nonZeroIsFatal=False, err=err)
        if err.data:
            mx.log(err.data)

        with open(truffle_log_file, encoding='utf-8') as fp:
            truffle_log = fp.read()
        expected_truffle_log_line = 'delaying compilation of root delay_compilation_here'
        if expected_truffle_log_line not in truffle_log:
            mx.abort(f'Did not find expected pattern ("{expected_truffle_log_line}") in Truffle log:{linesep}{truffle_log}')

        expectations = ['detected long running compilation']
        _check_compiler_log(compiler_log_file, expectations, extra_log_files=[truffle_log_file])
        if vm_can_exit:
            # jdk.graal.compiler.core.CompilationWatchDog.EventHandler.STUCK_COMPILATION_EXIT_CODE
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
                '-Djdk.graal.InlineDuringParsing=false',
                '-Djdk.graal.TrackNodeSourcePosition=true',
                '-Djdk.graal.LogFile=' + compiler_log_file,
                '-DCompileTheWorld.IgnoreCompilationFailures=true',
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
            '-Djdk.graal.InlineDuringParsing=false',
            '-Djdk.graal.TrackNodeSourcePosition=true',
            '-DCompileTheWorld.Verbose=false',
            '-DCompileTheWorld.HugeMethodLimit=4000',
            '-DCompileTheWorld.MaxCompiles=150000',
            '-XX:ReservedCodeCacheSize=300m',
        ], extra_vm_arguments)

def _test_libgraal_truffle(extra_vm_arguments):
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
        "-Djdk.graal.CompilationFailureAction=ExitVM",
        "-Dgraalvm.locatorDisabled=true",
        "truffle", "LibGraalCompilerTest", "TruffleHostInliningTest"])

def gate_body(args, tasks):
    with Task('Vm: GraalVM dist names', tasks, tags=['names']) as t:
        if t:
            mx_sdk_vm.verify_graalvm_configs(suites=['vm', 'vm-enterprise'])

    with Task('Vm: ce-release-artifacts.json', tasks, tags=['style']) as t:
        if t:
            with open(join(_suite.dir, 'ce-release-artifacts.json'), encoding='utf-8') as f:
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

                with Task('LibGraal Compiler:CheckBuildPaths', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_check_build_path(libgraal_location)
                with Task('LibGraal Compiler:Basic', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_basic(extra_vm_arguments, libgraal_location)
                with Task('LibGraal Compiler:FatalErrorHandling', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_fatal_error_handling(extra_vm_arguments)
                with Task('LibGraal Compiler:OOMEDumping', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_oome_dumping(extra_vm_arguments)
                with Task('LibGraal Compiler:SystemicFailureDetection', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_systemic_failure_detection(extra_vm_arguments)
                with Task('LibGraal Compiler:CompilationTimeout:JIT', tasks, tags=[VmGateTasks.libgraal]) as t:
                    if t: _test_libgraal_CompilationTimeout_JIT(extra_vm_arguments)
                with Task('LibGraal Compiler:CompilationTimeout:Truffle', tasks, tags=[VmGateTasks.libgraal]) as t:
                    if t: _test_libgraal_CompilationTimeout_Truffle(extra_vm_arguments)

                with Task('LibGraal Compiler:CTW', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_ctw(extra_vm_arguments)

                import mx_compiler
                mx_compiler.compiler_gate_benchmark_runner(tasks, extra_vm_arguments, prefix='LibGraal Compiler:')

                with Task('LibGraal Truffle', tasks, tags=[VmGateTasks.libgraal], report='compiler') as t:
                    if t: _test_libgraal_truffle(extra_vm_arguments)
        else:
            mx.warn("Skipping libgraal tests: component not enabled")
    else:
        mx.warn(f"Skipping libgraal tests: suite '{libgraal_suite_name}' not found. Did you forget to dynamically import it? (--dynamicimports {libgraal_suite_name})")

    gate_sulong(tasks)
    gate_python(tasks)
    gate_truffle_native_tck_smoke_test(tasks)
    gate_truffle_native_tck_sl(tasks)
    gate_truffle_native_tck_js(tasks)
    gate_truffle_native_tck_python(tasks)
    gate_truffle_native_tck_wasm(tasks)
    gate_polyglot_isolate(tasks)
    gate_maven(tasks)
    gate_maven_downloader(tasks)

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

def gate_sulong(tasks):
    with Task('Run SulongSuite tests as native-image', tasks, tags=[VmGateTasks.sulong]) as t:
        if t:
            lli = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--suite=sulong', '--enable-timing'])

    with Task('Run SulongSuite tests as native-image with engine cache', tasks, tags=[VmGateTasks.sulong_aot]) as t:
        if t:
            lli = join(mx_sdk_vm_impl.graalvm_output(), 'bin', 'lli')
            sulong = mx.suite('sulong')
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--suite=sulong', '--enable-timing', '--sulong-config', 'AOTCacheStoreNative'])
            sulong.extensions.testLLVMImage(lli, libPath=False, unittestArgs=['--suite=sulong', '--enable-timing', '--sulong-config', 'AOTCacheLoadNative'])

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
            python_svm_image_path = join(mx.distribution('GRAALPY_NATIVE_STANDALONE').output, 'bin', 'graalpy')
            python_suite = mx.suite("graalpython")
            python_suite.extensions.run_python_unittests(python_svm_image_path)


def _svm_truffle_tck(native_image, language_id, language_distribution=None, fail_on_error=True, print_call_tree=False,
                     additional_options=None):
    assert language_distribution, 'Language_distribution must be given'
    if additional_options is None:
        additional_options = []
    dists = [
        mx.distribution('substratevm:SVM_TRUFFLE_TCK'),
        language_distribution
    ] + mx_truffle.resolve_truffle_dist_names()

    def _collect_excludes(suite, suite_import, excludes):
        excludes_dir = join(suite.mxDir, 'truffle.tck.permissions')
        if isdir(excludes_dir):
            for excludes_file in listdir(excludes_dir):
                excludes.append(join(excludes_dir, excludes_file))
        imported_suite = mx.suite(suite_import.name)
        imported_suite.visit_imports(_collect_excludes, excludes=excludes)

    excludes = []
    language_distribution.suite.visit_imports(_collect_excludes, excludes=excludes)
    svmbuild = mkdtemp()
    success = False
    try:
        report_file = join(svmbuild, "language_permissions.log")
        print_call_tree_options = ['-H:+PrintAnalysisCallTree'] if print_call_tree else []
        options = mx.get_runtime_jvm_args(dists, exclude_names=['substratevm:SVM']) + [
            '--features=com.oracle.svm.truffle.tck.PermissionsFeature',
        ] + mx_sdk_vm_impl.svm_experimental_options([
            '-H:TruffleTCKUnusedAllowListEntriesAction=Warn', # GR-61487: Clean JavaScript allow list
            '-H:ClassInitialization=:build_time',
            '-H:+EnforceMaxRuntimeCompileMethods',
            '-H:-FoldSecurityManagerGetter',
            f'-H:TruffleTCKPermissionsReportFile={report_file}',
            f'-H:Path={svmbuild}',
            '--add-exports=org.graalvm.truffle.runtime/com.oracle.truffle.runtime=ALL-UNNAMED'
        ] + print_call_tree_options + additional_options) + [
            'com.oracle.svm.truffle.tck.MockMain'
        ]
        if excludes:
            options += mx_sdk_vm_impl.svm_experimental_options([f"-H:TruffleTCKPermissionsExcludeFiles={','.join(excludes)}"])
        native_image(options)
        if isfile(report_file) and getsize(report_file) > 0:
            message = f"Failed: Language {language_id} performs following privileged calls:\n\n"
            with open(report_file, encoding='utf-8') as f:
                for line in f.readlines():
                    message = message + line
            message = message + ("\nNote: If the method is not used directly by the language, but is part of the call path "
                                 "because it is used internally by the JDK and introduced by a polymorphic call, consider "
                                 "adding it to `jdk_allowed_methods.json`.")
            if fail_on_error:
                mx.abort(message)
            else:
                reports_folder = os.path.join(svmbuild, 'reports') if print_call_tree else None
                return message, reports_folder
        else:
            success = True
    finally:
        if success:
            mx.rmtree(svmbuild)
    return None


def gate_truffle_native_tck_smoke_test(tasks):

    def _copy_call_tree(source_folder):
        """
        Copies native-image call tree from a temporary folder to the current working directory
        and compresses it with gzip algorithm. Such a file can be preserved when the build fails.
        """
        call_tree_file = None
        for file in os.listdir(source_folder):
            if file.startswith("call_tree_") and file.endswith(".txt"):
                call_tree_file = os.path.join(source_folder, file)
                break
        if call_tree_file:
            dest_file = "call_tree.txt.gz"
            with open(call_tree_file, "rb") as f_in:
                with gzip.open(dest_file, "wb") as f_out:
                    shutil.copyfileobj(f_in, f_out)

    with Task('Truffle Native TCK Smoke Test', tasks, tags=[VmGateTasks.truffle_native_tck]) as t:
        if t:
            truffle_suite = mx.suite('truffle')
            test_language_dist = [d for d in truffle_suite.dists if d.name == 'TRUFFLE_TCK_TESTS_LANGUAGE'][0]
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                result = _svm_truffle_tck(native_image, 'TCKSmokeTestLanguage', test_language_dist, False, True,
                                          ['-H:TruffleTCKCollectMode=All'])
                privileged_calls = result[0]
                reports_folder = result[1]
                if 'Failed: Language TCKSmokeTestLanguage performs following privileged calls' not in privileged_calls:
                    _copy_call_tree(reports_folder)
                    mx.abort("Expected failure, log:\n" + privileged_calls)

                must_have_methods = [
                    'PrivilegedCallNode.callConstructorReflectively',
                    'PrivilegedCallNode.callMethodHandle',
                    'PrivilegedCallNode.callMethodReflectively',
                    'PrivilegedCallNode.doBehindBoundaryPrivilegedCall',
                    'PrivilegedCallNode.doInterrupt',
                    'PrivilegedCallNode.doPolymorphicCall',
                    'PrivilegedCallNode.doPrivilegedCall',
                    'ServiceImpl.execute',
                    'UnsafeCallNode.doBehindBoundaryUnsafeAccess',
                    'UnsafeCallNode.doUnsafeAccess',
                    'DeniedURLNode.doURLOf'
                ]
                must_not_have_methods = [
                    'AllowedURLNode.doURLOf'
                ]
                for method in must_have_methods:
                    if method not in privileged_calls:
                        _copy_call_tree(reports_folder)
                        mx.abort(f"Missing {method} call in the log.\nLog content:\n" + privileged_calls)
                for method in must_not_have_methods:
                    if method in privileged_calls:
                        _copy_call_tree(reports_folder)
                        mx.abort(f"Found {method} call in the log.\nLog content:\n" + privileged_calls)

def gate_truffle_native_tck_js(tasks):
    with Task('JavaScript Truffle Native TCK', tasks, tags=[VmGateTasks.truffle_native_tck_js]) as t:
        if t:
            js_language = mx.distribution('graal-js:GRAALJS', fatalIfMissing=False)
            if not js_language:
                mx.abort("Cannot resolve the `graal-js::GRAALJS` language distribution. To resolve this, import the graal-js suite using `--dynamicimports /graal-js`.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, 'js', js_language)


def gate_truffle_native_tck_python(tasks):
    with Task('Python Truffle Native TCK', tasks, tags=[VmGateTasks.truffle_native_tck_python]) as t:
        if t:
            py_language = mx.distribution('graalpython:GRAALPYTHON', fatalIfMissing=False)
            if not py_language:
                mx.abort("Cannot resolve the `graalpython:GRAALPYTHON` language distribution. To resolve this, import the graalpython suite using `--dynamicimports /graalpython`.")
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, 'python', py_language)


def gate_truffle_native_tck_wasm(tasks):
    with Task('GraalWasm Truffle Native TCK', tasks, tags=[VmGateTasks.truffle_native_tck_wasm]) as t:
        if t:
            wasm_language = mx.distribution('wasm:WASM', fatalIfMissing=False)
            if not wasm_language:
                mx.abort("Cannot resolve the `wasm:WASM` language distribution. To resolve this, import the wasm suite using `--dynamicimports /wasm`.")
            wasm_extensions = wasm_language.suite.extensions
            native_image_context, svm = graalvm_svm()
            with native_image_context(svm.IMAGE_ASSERTION_FLAGS) as native_image:
                _svm_truffle_tck(native_image, 'wasm', wasm_language,
                                 # native-image sets -Djdk.internal.lambda.disableEagerInitialization=true by default,
                                 # which breaks Vector API's isNonCapturingLambda assertion. We override this property
                                 # for the GraalWasm Truffle Native TCK.
                                 additional_options=['-Djdk.internal.lambda.disableEagerInitialization=false'] + wasm_extensions.libwasmvm_dynamic_build_args())

def gate_polyglot_isolate(tasks):
    with Task('TruffleIsolate:internal:unittest', tasks, tags=[VmGateTasks.truffle_isolate_internal_unittest]) as t:
        if t:
            _polyglot_isolate_unittest('internal')

    with Task('TruffleIsolate:external:unittest', tasks, tags=[VmGateTasks.truffle_isolate_external_unittest]) as t:
        if t:
            _polyglot_isolate_unittest('external')

def gate_maven(tasks):
    # Runs truffle deploy to maven tests
    with Task('Vm: Truffle Unchained Maven Deploy Local', tasks, tags=[VmGateTasks.truffle_maven_deploy_local]) as t:
        if t:
            mx_sdk.maven_deploy_public([])
    # Polyglot isolate maven tests - internal isolation
    with Task('Vm: Truffle Unchained Maven Polyglot Internal Isolate', tasks, tags=[VmGateTasks.truffle_maven_isolate_internal]) as t:
        if t:
            run_truffle_maven_tests(use_classpath=False, use_native_image=False, use_isolate=True)
            run_truffle_maven_tests(use_classpath=False, use_native_image=True, use_isolate=True)
            run_truffle_maven_tests(use_classpath=True, use_native_image=False, use_isolate=True)
            run_truffle_maven_tests(use_classpath=True, use_native_image=True, use_isolate=True)
    # Polyglot isolate maven tests - external isolation
    with Task('Vm: Truffle Unchained Maven Polyglot External Isolate', tasks, tags=[VmGateTasks.truffle_maven_isolate_external]) as t:
        if t:
            external_isolate_options = ['engine.IsolateMode=external']
            run_truffle_maven_tests(use_classpath=False, use_native_image=False, use_isolate=True, polyglot_options=external_isolate_options)
            run_truffle_maven_tests(use_classpath=False, use_native_image=True, use_isolate=True, polyglot_options=external_isolate_options)
            run_truffle_maven_tests(use_classpath=True, use_native_image=False, use_isolate=True, polyglot_options=external_isolate_options)
            run_truffle_maven_tests(use_classpath=True, use_native_image=True, use_isolate=True, polyglot_options=external_isolate_options)

def gate_maven_downloader(tasks):
    with Task('Maven Downloader prepare maven repo', tasks, tags=[VmGateTasks.maven_downloader]) as t:
        if t:
            mx.suite('sulong')
            mx_sdk.maven_deploy_public([], licenses=['EPL-2.0', 'GPLv2-CPE', 'ICU,GPLv2', 'BSD-new', 'UPL', 'MIT'], deploy_snapshots=False)
            mx.build(["--dep", "sdk:MAVEN_DOWNLOADER"])
            jdk = mx.get_jdk()
            mvnDownloader = mx.distribution("sdk:MAVEN_DOWNLOADER")
            vm_args = mx.get_runtime_jvm_args([mvnDownloader], jdk=jdk)
            vm_args.append(mvnDownloader.mainClass)
            output_dir = os.path.join(_suite.get_mx_output_dir(), 'downloaded-mvn-modules')
            shutil.rmtree(output_dir, ignore_errors=True)
            env = os.environ.copy()
            if mx._opts.verbose:
                env["org.graalvm.maven.downloader.logLevel"] = "ALL"
            mvntoolargs = [
                "-r", f"file://{abspath(mx_sdk.maven_deploy_public_repo_dir())}",
                "-o", output_dir,
                "-v", mx_sdk_vm_impl.graalvm_version('graalvm'),
                "-a",
            ]
            mx.run_java(vm_args + mvntoolargs + ["polyglot"], jdk=jdk, env=env)
            # now we should have all jars for llvm-language in the output dir
            entries = os.listdir(output_dir)
            for entry in entries:
                if isdir(entry):
                    mx.abort("We do not expect the maven downloader to create directories")
                os.rename(join(output_dir, entry), join(output_dir, f"first_download_{entry}"))
            if not any(re.search("polyglot.*\\.jar", entry) for entry in entries):
                mx.abort("We expected polyglot to have been downloaded")
            if any(re.search("llvm.*\\.jar", entry) for entry in entries):
                mx.abort("We did not expect llvm to have been downloaded")
            # now we download something higher up the dependency tree, to check
            # that existing files (even though renamed) are not clobbered and
            # no duplicate modules are placed in the output directory
            mx.run_java(vm_args + mvntoolargs + ["llvm-community"], jdk=jdk, env=env)
            entries = os.listdir(output_dir)
            for entry in entries:
                if isdir(entry):
                    mx.abort("We do not expect the maven downloader to create directories")
                if f"first_download_{entry}" in entries:
                    mx.abort("We do not expect the maven downloader to download modules that are already there")
            if not any(re.search("llvm-language-native.*\\.jar", entry) for entry in entries):
                mx.abort("We did not expect llvm-language-native to have been downloaded via llvm-community meta pom")

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
        with open(artifacts_file_path, encoding='utf-8') as f:
            artifacts = json.load(f)
        kind = 'shared_libraries' if shared_lib else 'executables'
        if kind not in artifacts:
            mx.abort(f'{kind} not found in {artifacts_file_path}.')
        if len(artifacts[kind]) != 1:
            mx.abort(f"Expected {kind} list with one element, found {len(artifacts[kind])}: {', '.join(artifacts[kind])}.")
        tests_image_path = join(image_dir, artifacts[kind][0])
        mx.logv(f'Test image path: {tests_image_path}')
        return tests_image_path, unittests_file

def gate_truffle_native_tck_sl(tasks):
    with Task('SL Truffle Native TCK', tasks, tags=[VmGateTasks.truffle_native_tck_sl]) as t:
        if t:
            tools_suite = mx.suite('tools', fatalIfMissing=False)
            if not tools_suite:
                mx.abort("Cannot resolve tools suite. To resolve this, import the tools suite using `--dynamicimports /tools`.")
            svmbuild = mkdtemp()
            try:
                options = [
                    '-H:Class=org.junit.runner.JUnitCore',
                ] + mx_sdk_vm_impl.svm_experimental_options([
                    f'-H:Path={svmbuild}',
                ])
                tests_image_path, tests_file = build_tests_image(svmbuild, options, ['com.oracle.truffle.tck.tests'], ['truffle:TRUFFLE_SL_TCK', 'truffle:TRUFFLE_TCK_INSTRUMENTATION'])
                with open(tests_file, encoding='utf-8') as f:
                    test_classes = [l.rstrip() for l in f.readlines()]
                mx.run([tests_image_path] + test_classes)
            finally:
                if not mx._opts.verbose:
                    mx.rmtree(svmbuild)

def _build_polyglot_isolate_library(target_folder, dist_names, native_image_options=None, add_tests=None):
    image_build_options = list(native_image_options) if native_image_options else []
    optional_mpk_opts = ['-H:+ProtectionKeys'] if mx_sdk_vm_ng.is_enterprise() else []
    image_build_options += [
        '-ea', '-esa',
        '--features=com.oracle.svm.truffle.PolyglotIsolateGuestFeature',
        '-H:APIFunctionPrefix=truffle_isolate_', '-o', os.path.join(target_folder, 'truffle_isolate_test_lib')
    ] + optional_mpk_opts

    return build_tests_image(
                target_folder,
                image_build_options,
                add_tests,
                additional_deps=mx_truffle.resolve_truffle_dist_names(True) + dist_names,
                shared_lib=True,
            )[0]

def _isolate_mode_vm_options(isolate_mode):
    if isolate_mode == 'external':
        return ["-Dpolyglot.engine.AllowExperimentalOptions=true", "-Dpolyglot.engine.IsolateMode=external"]
    elif isolate_mode == 'internal':
        return []
    else:
        mx.abort(f"Invalid isolate_mode {isolate_mode}")

def _polyglot_isolate_unittest(isolate_mode):
    svmbuild = mkdtemp()
    try:
        isolate_mode_vm_options = _isolate_mode_vm_options(isolate_mode)
        truffle_isolate_common_options = [
                                             '--enable-url-protocols=http',
                                             '--add-opens org.graalvm.polyglot/org.graalvm.polyglot=ALL-UNNAMED',
                                         ] + mx_sdk_vm_impl.svm_experimental_options([
            # Disable a native-image check of `HostSpot` in element name. JFluid library used by unittest uses such a names.
            # For example, org.graalvm.visualvm.lib.jfluid.heap.NearestGCRoot#initHotSpotReference
            '-H:-VerifyNamingConventions',
        ])
        truffle_isolate_guest_options = truffle_isolate_common_options + [
            '--add-exports org.graalvm.nativeimage.builder/com.oracle.svm.core.os=ALL-UNNAMED',
        ]
        mx_truffle.append_unittest_image_build_time_options(truffle_isolate_guest_options)
        tests = ['com.oracle.truffle.api.test', 'com.oracle.truffle.tck.tests', 'com.oracle.truffle.sl.test']
        tests_image_path = _build_polyglot_isolate_library(svmbuild,
                                                           [
                                                               'truffle:TRUFFLE_SL_TCK',
                                                               'truffle:TRUFFLE_TCK_INSTRUMENTATION'
                                                           ] + mx_truffle.resolve_truffle_dist_names(),
                                                           truffle_isolate_guest_options,
                                                           tests)
        isolate_launcher = next(mx.distribution('sdk:NATIVEBRIDGE_LAUNCHER_RESOURCES').getArchivableResults(use_relpath=False))[0]
        extra_vm_arguments_isolate_library = ["-Dpolyglot.engine.AllowExperimentalOptions=true",
                                              '-Dpolyglot.engine.IsolateLibrary=' + tests_image_path,
                                              '-Dpolyglot.engine.IsolateLauncher=' + isolate_launcher]
        extra_vm_arguments_spawn_isolate = ["-Dpolyglot.engine.AllowExperimentalOptions=true",
                                            "-Dpolyglot.engine.SpawnIsolate=true"]
        unittest_args = ['--verbose']
        mx_unittest.unittest(
            unittest_args + extra_vm_arguments_isolate_library + isolate_mode_vm_options + ['com.oracle.truffle.api.test.polyglot.isolate'])
        mx_unittest.unittest(unittest_args + extra_vm_arguments_isolate_library + isolate_mode_vm_options + extra_vm_arguments_spawn_isolate + [
            'com.oracle.truffle.sandbox.test.ResourceLimitsTest',
            'com.oracle.truffle.sandbox.test.HeapMemoryLimitTest'])
        unittest_args_tck = unittest_args + ['-Dtck.inlineVerifierInstrument=false']
        mx_unittest.unittest(
            unittest_args_tck + extra_vm_arguments_isolate_library + isolate_mode_vm_options + extra_vm_arguments_spawn_isolate + tests)

        # Run PolyglotIsolateTest in native-to-native with external truffle isolate library
        tests = ['com.oracle.truffle.api.test.polyglot.isolate']
        truffle_isolate_options = truffle_isolate_common_options
        vm_telemetry_options = ['--enable-monitoring=jvmstat', '--enable-monitoring=threaddump']
        args = tests + ['--build-args'] + vm_telemetry_options + truffle_isolate_options + isolate_mode_vm_options + ['--run-args'] + extra_vm_arguments_isolate_library + isolate_mode_vm_options
        mx_truffle.native_truffle_unittest(args)
    finally:
        if not mx._opts.verbose:
            mx.rmtree(svmbuild)

def run_truffle_maven_tests(use_classpath=False, use_native_image=False, use_isolate=False, always_use_fast_build=True, use_default_truffle_runtime=False,
                             select_id=None, truffle_runtime_version=None, polyglot_options=None):
    truffle_runtime_version = truffle_runtime_version or mx_sdk_vm_impl.graalvm_version('graalvm') + '-SNAPSHOT'
    polyglot_options = polyglot_options or []

    class ComponentTest:

        def __init__(self, dists, version, expected_ids, fast_build, explicit_maven_coordinates=None, explicit_build_folder=None):
            assert dists or (explicit_maven_coordinates and explicit_build_folder)
            if explicit_build_folder:
                assert isinstance(explicit_build_folder, str)
                self.test_folder = explicit_build_folder
            else:
                self.test_folder = "_".join([dist.name.lower() for dist in dists])
            if explicit_maven_coordinates:
                assert isinstance(explicit_maven_coordinates, list)
                self.maven_coordinates = explicit_maven_coordinates
            else:
                polyglot_meta_distributions_maven_coordinates = []
                for dist in dists:
                    if dist.maven_group_id() != 'org.graalvm.polyglot':
                        dist = mx_vm.create_polyglot_meta_pom_distribution_from_base_distribution(dist)
                    polyglot_meta_distributions_maven_coordinates.append((dist.maven_group_id(), dist.maven_artifact_id(), version, 'pom'))
                self.maven_coordinates = polyglot_meta_distributions_maven_coordinates
            self.version = version
            self.fast_build = fast_build
            self.expected_ids = sorted(expected_ids)

        def __str__(self):
            return self.test_folder

    def _trim_optional_suffix(string, suffixes):
        if isinstance(suffixes, str):
            suffixes = [suffixes]
        for suffix in suffixes:
            if string.endswith(suffix):
                return string[:-len(suffix)]
        return string

    def _expected_ids(dists):
        ids = set()
        for dist in dists:
            expected_id = _trim_optional_suffix(dist.maven_artifact_id(), ['-community', '-isolate'])
            if expected_id == 'profiler':
                ids.update(['cpusampler', 'cputracer', 'heapmonitor', 'memtracer'])
            elif expected_id.startswith('llvm'):
                ids.add('llvm')
            else:
                ids.add(expected_id)
        return ids

    def _resolve_distributions(components, filter_id=None):
        dists = []
        env_unsupported = os.environ.get('UNSUPPORTED_META_POM_DISTRIBUTIONS')
        unsupported = {mx.splitqualname(s)[1] for s in env_unsupported.split(',')} if env_unsupported else {}
        for dist_name in components:
            if mx.splitqualname(dist_name)[1] in unsupported:
                mx.log(f'Ignoring {dist_name} because it\'s listed as an unsupported distribution by the UNSUPPORTED_META_POM_DISTRIBUTIONS env variable.')
            else:
                component_dist = mx.distribution(dist_name, fatalIfMissing=filter_id is None)
                if component_dist and (not filter_id or filter_id in _expected_ids([component_dist])):
                    dists.append(component_dist)
        return dists

    def _create_tests(graalvm_version, major_java_version, select_id=None, explict_tools_tests=True, enterprise=False, fast_build=False):
        suffix = '' if enterprise else '_COMMUNITY_DEPRECATED'
        catalog_languages_distribution_name = 'LANGUAGES' + suffix
        catalog_tools_distribution_name = 'TOOLS' + suffix
        languages = _resolve_distributions([d.name for d in mx.distribution(catalog_languages_distribution_name).runtimeDependencies], select_id)
        tools = _resolve_distributions([d.name for d in mx.distribution(catalog_tools_distribution_name).runtimeDependencies], select_id)

        if not enterprise and (major_java_version != 21 or not mx.is_linux() or use_native_image):
            # cannot run espresso community except on linux on a 21 JVM host
            # see GR-54293
            languages = [d for d  in languages if d.name != 'JAVA_POM_POLYGLOT']

        catalog_languages = _resolve_distributions([catalog_languages_distribution_name])[0]
        catalog_tools = _resolve_distributions([catalog_tools_distribution_name])[0]

        tests = []
        if explict_tools_tests:
            for language in languages:
                tests.append(ComponentTest([language], graalvm_version, expected_ids=_expected_ids([language]), fast_build=fast_build))
            # every tool gets its own test
            for tool in tools:
                tests.append(ComponentTest([tool], graalvm_version, expected_ids=_expected_ids([tool]), fast_build=fast_build))
        else:
            # only language tests to save time
            for language in languages:
                tests.append(ComponentTest([language], graalvm_version, expected_ids=_expected_ids([language]), fast_build=fast_build))

        # Add a test for the hprof language
        # The hprof language does not have meta pom nor is included in the languages meta pom.
        if enterprise and (not select_id or select_id == 'hprof'):
            tests.append(ComponentTest(None, graalvm_version, expected_ids={'hprof'}, fast_build=fast_build,
                                       explicit_maven_coordinates=[
                                           ('org.graalvm.tools', 'hprof-language', graalvm_version, 'jar'),
                                           ('org.graalvm.truffle', 'truffle-enterprise', graalvm_version, 'jar')
                                    ], explicit_build_folder='heap_language'))

        if not select_id:
            #  Include tests for catalog POMs only if the `select_id` filter is not set.
            tests.append(ComponentTest([catalog_tools], graalvm_version, expected_ids=_expected_ids(tools), fast_build=fast_build))
            tests.append(ComponentTest([catalog_languages, catalog_tools], graalvm_version, _expected_ids(languages) | _expected_ids(tools), fast_build=True))
        return tests

    def _created_isolate_tests(graalvm_version, variant, select_id=None, fast_build=False):
        supported_isolates = {'js', 'python'}
        tests = []
        for isolated_language_id in supported_isolates:
            if not select_id or select_id == isolated_language_id:
                tests.append(ComponentTest(None, graalvm_version, expected_ids={isolated_language_id}, fast_build=fast_build,
                                           explicit_maven_coordinates=[('org.graalvm.polyglot', f'{isolated_language_id}{variant}', graalvm_version, 'pom')],
                                           explicit_build_folder=f'{isolated_language_id}_isolate'))
        return tests

    def _add_dependency(pom, maven_coordinate):
        pom.open('dependency')
        pom.element('groupId', data=maven_coordinate[0])
        pom.element('artifactId', data=maven_coordinate[1])
        pom.element('version', data=maven_coordinate[2])
        if len(maven_coordinate) > 3:
            pom.element('type', data=maven_coordinate[3])
        pom.close('dependency')

    def _generate_assembly_descriptor(target):
        pom = mx.XMLDoc()
        pom.open('assembly', attributes={
            'xmlns': "http://maven.apache.org/ASSEMBLY/2.2.0",
            'xmlns:xsi': "http://www.w3.org/2001/XMLSchema-instance",
            'xsi:schemaLocation': "http://maven.apache.org/ASSEMBLY/2.2.0 http://maven.apache.org/xsd/assembly-2.2.0.xsd"
        })
        pom.element('id', data='jar-with-dependencies')
        pom.open('formats')
        pom.element('format', data='jar')
        pom.close('formats')
        pom.element('includeBaseDirectory', data='false')
        pom.open('dependencySets')
        pom.open('dependencySet')
        pom.element('outputDirectory', data='/')
        pom.element('useProjectArtifact', data='true')
        pom.element('unpack', data='true')
        pom.element('scope', data='runtime')
        pom.close('dependencySet')
        pom.close('dependencySets')
        pom.open('containerDescriptorHandlers')
        pom.open('containerDescriptorHandler')
        pom.element('handlerName', data='metaInf-services')
        pom.close('containerDescriptorHandler')
        pom.close('containerDescriptorHandlers')
        pom.close('assembly')
        pom_xml = pathlib.Path(target)
        text = pom.xml(indent='    ', newl='\n')
        pom_xml.write_text(text, encoding='utf-8')

    def _generate_pom(target, repository_path, maven_dependencies, use_classpath, additional_vm_args, additional_image_build_args, fast_build, assembly_descriptor):
        pom = mx.XMLDoc()
        pom.open('project', attributes={
            'xmlns': "http://maven.apache.org/POM/4.0.0",
            'xmlns:xsi': "http://www.w3.org/2001/XMLSchema-instance",
            'xsi:schemaLocation': "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
        })
        pom.element('modelVersion', data="4.0.0")
        pom.element('groupId', data='org.graalvm.polyglot')
        pom.element('artifactId', data='maven-test')
        pom.element('version', data='1.0')
        pom.open('build')
        pom.open('plugins')
        pom.open('plugin')
        pom.element('groupId', data='org.apache.maven.plugins')
        pom.element('artifactId', data='maven-compiler-plugin')
        pom.element('version', data='3.6.1')
        pom.close('plugin')
        pom.open('plugin')
        pom.element('groupId', data='org.codehaus.mojo')
        pom.element('artifactId', data='exec-maven-plugin')
        pom.element('version', data='3.1.0')
        pom.close('plugin')
        pom.close('plugins')
        pom.close('build')
        pom.open('properties')
        pom.element('project.build.sourceEncoding', data='UTF-8')
        pom.element('maven.compiler.source', data='17')
        pom.element('maven.compiler.target', data='17')
        pom.close('properties')
        pom.open('dependencies')
        _add_dependency(pom, ('org.graalvm.polyglot', 'polyglot', next(iter(maven_dependencies))[2]))
        for dependency in maven_dependencies:
            _add_dependency(pom, dependency)
        pom.close('dependencies')

        pom.open('repositories')
        pom.open('repository')
        pom.element('id', data='local-snapshots')
        pom.element('name', data='Local Snapshot Repository')
        pom.element('url', data=f'{_path_to_file_url(repository_path)}')
        pom.open('snapshots')
        pom.element('enabled', data='true')
        pom.close('snapshots')
        pom.close('repository')
        pom.close('repositories')

        pom.open('profiles')

        pom.open('profile')
        pom.element('id', data='assembly')
        pom.open('build')
        pom.open('plugins')
        pom.open('plugin')
        pom.element('groupId', data='org.apache.maven.plugins')
        pom.element('artifactId', data='maven-assembly-plugin')
        pom.element('version', data='3.6.0')
        pom.open('executions')
        pom.open('execution')
        pom.element('phase', data='package')
        pom.open('goals')
        pom.element('goal', data='single')
        pom.close('goals')
        pom.open('configuration')
        pom.open('archive')
        pom.open('manifest')
        # If there are specific elements inside <manifest>, they should be added here
        pom.close('manifest')
        pom.open('manifestEntries')
        pom.element('Multi-Release', data='true')
        pom.close('manifestEntries')
        pom.close('archive')
        pom.open('descriptors')
        pom.element('descriptor', data=assembly_descriptor)
        pom.close('descriptors')
        pom.close('configuration')
        pom.close('execution')
        pom.close('executions')
        pom.close('plugin')
        pom.close('plugins')
        pom.close('build')
        pom.close('profile')

        pom.open('profile')
        pom.element('id', data='shade')
        pom.open('build')
        pom.open('plugins')
        pom.open('plugin')
        pom.element('groupId', data='org.apache.maven.plugins')
        pom.element('artifactId', data='maven-shade-plugin')
        pom.element('version', data='3.5.1')
        pom.open('executions')
        pom.open('execution')
        pom.element('phase', data='package')
        pom.open('goals')
        pom.element('goal', data='shade')
        pom.close('goals')
        pom.close('execution')
        pom.close('executions')
        pom.open('configuration')
        pom.open('transformers')
        pom.element('transformer', attributes={
            'implementation': "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"
        })
        pom.open('transformer', attributes={
            'implementation': "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer"
        })
        pom.open('manifestEntries')
        pom.element('Multi-Release', data='true')
        pom.close('manifestEntries')
        pom.close('transformer')
        pom.close('transformers')
        pom.open('filters')
        pom.open('filter')
        pom.element('artifact', data='*:*:*:*')
        pom.open('excludes')
        pom.element('exclude', data='META-INF/*.SF')
        pom.element('exclude', data='META-INF/*.DSA')
        pom.element('exclude', data='META-INF/*.RSA')
        pom.close('excludes')
        pom.close('filter')
        pom.close('filters')
        pom.close('configuration')
        pom.close('plugin')
        pom.close('plugins')
        pom.close('build')
        pom.close('profile')

        pom.open('profile')
        pom.element('id', data='native')
        pom.open('build')
        pom.open('plugins')
        pom.open('plugin')
        pom.element('groupId', data='org.codehaus.mojo')
        pom.element('artifactId', data='exec-maven-plugin')
        pom.element('version', data='3.0.0')
        pom.open('executions')
        pom.open('execution')
        pom.element('id', data='native-image')
        pom.element('phase', data='package')
        pom.open('goals')
        pom.element('goal', data='exec')
        pom.close('goals')
        pom.open('configuration')
        pom.element('executable', data='${env.JAVA_HOME}/bin/' + mx.cmd_suffix('native-image'))
        if use_classpath:
            pom.element('commandlineArgs', data=f' -ea {"-Ob " if fast_build else ""}-cp %classpath {" ".join(additional_vm_args)} {" ".join(additional_image_build_args)} -H:+ReportExceptionStackTraces --verbose maven.test.Main maven-test')
        else:
            pom.element('commandlineArgs', data=f' -ea {"-Ob " if fast_build else ""}-p %classpath {" ".join(additional_vm_args)} {" ".join(additional_image_build_args)} -H:+ReportExceptionStackTraces --verbose -m maven.test/maven.test.Main maven-test')
        pom.close('configuration')
        pom.close('execution')
        pom.close('executions')
        pom.close('plugin')
        pom.close('plugins')
        pom.close('build')
        pom.close('profile')

        pom.close('profiles')
        pom.close('project')
        pom_xml = pathlib.Path(target)
        text = pom.xml(indent='    ', newl='\n')
        pom_xml.write_text(text, encoding='utf-8')
        return text

    snapshot_repository = mx_sdk.maven_deploy_public_repo_dir()

    projects = os.path.join(_suite.get_mx_output_dir(), 'maven-test-projects')
    # clear previous projects
    mx.rmtree(projects, ignore_errors=True)
    use_hotspot_jvm = not use_native_image

    # TRUFFLE_MAVEN_GATE_SELECT_ID env variable overrides select_id parameter
    select_id = mx.get_env('TRUFFLE_MAVEN_GATE_SELECT_ID', select_id)
    # select_id = 'python'  # uncomment to test for your language only
    graalvm_home = mx.get_env('TRUFFLE_MAVEN_GATE_JAVA_HOME', None) or mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True)
    major_java_version = mx.JDKConfig(graalvm_home).javaCompliance.value

    # languages that currently fail building native-image
    do_not_initialize = set()
    value = mx.get_env('TRUFFLE_MAVEN_GATE_SKIP_LANGUAGE_INITIALIZATION', None)
    if value:
        do_not_initialize.update(value.split(','))
    native_image_unsupported = set()
    if mx.is_darwin():
        # TODO GR-48261 The Native Image build ran into a ld64 limitation.
        # Please use ld64.lld via `gu install llvm-toolchain` and run the same command again.
        native_image_unsupported.add('python')

    # Espresso embedding on hotspot on any other platform than linux
    # requires an LLVM_JAVA_HOME. It works in a native-image though.
    # There are no isolate tests for espresso, so don't require this environment for isolate tests
    if not use_isolate and use_hotspot_jvm and not mx.get_env('ESPRESSO_LLVM_JAVA_HOME') and not mx.get_env('LLVM_JAVA_HOME'):
        if mx.get_arch() == 'amd64' and not mx.is_windows():
            if mx.is_continuous_integration():
                mx.abort("Please set ESPRESSO_LLVM_JAVA_HOME in order to be able to run tests for java")
            mx.warn("ESPRESSO_LLVM_JAVA_HOME was not set, skipping java")
        do_not_initialize.add('java')

    # GR-65366: Graalpython fails to load python-native.dll on Windows 11.
    # Windows 11 is required by the process isolate for the UNIX domain socket support
    if mx.is_windows() and use_isolate and 'engine.IsolateMode=external' in polyglot_options:
        do_not_initialize.add('python')

    if use_isolate:
        if mx_sdk_vm_ng.is_enterprise():
            community_tests = []
            enterprise_tests = _created_isolate_tests(truffle_runtime_version, '-isolate', select_id=select_id, fast_build=False)
        else:
            community_tests = _created_isolate_tests(truffle_runtime_version, '-isolate-community', select_id=select_id, fast_build=False)
            enterprise_tests = []
        additional_program_args = ['--isolated']
    else:
        community_tests = _create_tests(truffle_runtime_version, major_java_version, select_id=select_id, explict_tools_tests=not use_native_image, enterprise=False, fast_build=True)
        enterprise_tests = _create_tests(truffle_runtime_version, major_java_version, select_id=select_id, explict_tools_tests=not use_native_image, enterprise=True, fast_build=False)
        additional_program_args = []

    fast_build = always_use_fast_build

    local_repository = mkdtemp()
    for test in enterprise_tests + community_tests:
        additional_vm_args = [
            # GR-62632: Debug VM exception translation failure
            '-Djdk.internal.vm.TranslatedException.debug=true'
        ]
        # Workaround for StaticShape generating classes in the unnamed module (GR-48132)
        if 'java' in test.expected_ids:
            additional_vm_args += ['--add-exports', 'org.graalvm.espresso/com.oracle.truffle.espresso.runtime=ALL-UNNAMED']
            additional_vm_args += ['--add-exports', 'org.graalvm.espresso/com.oracle.truffle.espresso.impl=ALL-UNNAMED']

        additional_image_build_args = []
        if 'wasm' in test.expected_ids:
            additional_image_build_args += ['-H:+UnlockExperimentalVMOptions', '-H:+VectorAPISupport', '--add-modules=jdk.incubator.vector']

        mx.log(f'{datetime.now():%d %b %Y %H:%M:%S} Creating project for test run {test} in version {test.version}')

        project_path = os.path.join(projects, test.test_folder)
        source = os.path.join(_suite.dir, "tests", "all", "polyglot", "maven", "src")
        target = os.path.join(project_path, "src")
        shutil.copytree(source, target)
        assembly_descriptor = 'assembly.xml'
        _generate_assembly_descriptor(os.path.join(project_path, assembly_descriptor))
        mx.logv(_generate_pom(os.path.join(project_path, "pom.xml"), snapshot_repository, test.maven_coordinates,
                              use_classpath, additional_vm_args, additional_image_build_args, fast_build, assembly_descriptor))
        project_path = os.path.join(projects, test.test_folder)

        java_path = os.path.join(graalvm_home, 'bin', mx.exe_suffix('java'))
        mx.log(f'{datetime.now():%d %b %Y %H:%M:%S} Testing project {project_path}. To reproduce use: ')

        polyglot_args = [f'-Dpolyglot.{option}' for option in polyglot_options]
        if use_default_truffle_runtime:
            polyglot_args.append('-Dtruffle.UseFallbackRuntime=true')
        polyglot_args = ' '.join(polyglot_args)

        if use_native_image:
            if any(component in native_image_unsupported for component in test.expected_ids):
                mx.warn(f'Test native-image unsupported {project_path}')
                continue

            _gate_maven_command(local_repository, graalvm_home, project_path,
                                ['-Pnative', 'package',
                                 'exec:exec', '-Dexec.executable=' + mx.exe_suffix('maven-test'),
                                 f'-Dexec.args={polyglot_args} --do-not-initialize={",".join(do_not_initialize)} {" ".join(additional_program_args)} {" ".join(test.expected_ids)}'])
        elif use_classpath:
            # default run
            _gate_maven_command(local_repository, graalvm_home, project_path,
                                ['package', 'exec:exec',
                                 f'-Dexec.executable={java_path}',
                                 f'-Dexec.args={polyglot_args} -ea -cp %classpath {" ".join(additional_vm_args)} maven.test.Main --do-not-initialize={",".join(do_not_initialize)} {" ".join(additional_program_args)} {" ".join(test.expected_ids)}'])

            # Maven shaded run
            _gate_maven_command(local_repository, graalvm_home, project_path,
                                ['-Pshade', 'package', 'exec:exec',
                                 f'-Dexec.executable={java_path}',
                                 f'-Dexec.args={polyglot_args} -ea -cp ./target/maven-test-1.0.jar {" ".join(additional_vm_args)} maven.test.Main --do-not-initialize={",".join(do_not_initialize)} {" ".join(additional_program_args)} {" ".join(test.expected_ids)}'])

            # Maven assembly run
            _gate_maven_command(local_repository, graalvm_home, project_path,
                                ['-Passembly', 'package', 'exec:exec',
                                 f'-Dexec.executable={java_path}',
                                 f'-Dexec.args={polyglot_args} -ea -cp ./target/maven-test-1.0-jar-with-dependencies.jar {" ".join(additional_vm_args)} maven.test.Main --do-not-initialize={",".join(do_not_initialize)} {" ".join(additional_program_args)} {" ".join(test.expected_ids)}'])

        else:
            _gate_maven_command(local_repository, graalvm_home, project_path,
                                ['package', 'exec:exec',
                                 f'-Dexec.executable={java_path}',
                                 f'-Dexec.args={polyglot_args} -ea --module-path %classpath {" ".join(additional_vm_args)} --module maven.test/maven.test.Main --do-not-initialize={",".join(do_not_initialize)} {" ".join(additional_program_args)} {" ".join(test.expected_ids)}'])
    # only on success remove the local repo
    mx.rmtree(local_repository, ignore_errors=True)

def _path_to_file_url(path):
    abs_path = os.path.abspath(path)
    if mx.is_windows():
        return urlunparse(('file', '', '///' + abs_path.replace('\\', '/'), '', '', ''))
    else:
        return urlunparse(('file', '', abs_path, '', '', ''))

def _gate_maven_command(maven_repo, java_home, cwd, args):
    fd, temp_name = mkstemp()
    os.close(fd)
    with open(temp_name, 'w', encoding='utf-8') as out:
        custom_env = os.environ.copy()
        custom_env['JAVA_HOME'] = java_home
        try:
            mx.log(f'cd {cwd}')
            mx.log(f'export JAVA_HOME={java_home}')
            mx.log(f'mvn {mx.list_to_cmd_line(args)}')
            full_args = args + [f'-Dmaven.repo.local={maven_repo}', '-e']
            mx.run_maven(full_args, out=out, err=out, cwd=cwd, env=custom_env)
        except BaseException as e:
            out.flush()
            mx.log('Failed Maven output:')
            output = None
            with open(temp_name, encoding='utf-8') as f:
                output = f.read()
            mx.log(output)
            raise e
