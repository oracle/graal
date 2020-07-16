#
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

from __future__ import print_function

import os
import time
import re
import tempfile
from glob import glob
from contextlib import contextmanager
from distutils.dir_util import mkpath, remove_tree  # pylint: disable=no-name-in-module
from os.path import join, exists, basename, dirname
from shutil import move
import pipes
from xml.dom.minidom import parse
from argparse import ArgumentParser
import fnmatch

import mx
import mx_compiler
import mx_gate
import mx_unittest
import mx_sdk_vm
import mx_javamodules
import mx_subst
import mx_substratevm_benchmark  # pylint: disable=unused-import
from mx_compiler import GraalArchiveParticipant
from mx_gate import Task
from mx_unittest import _run_tests, _VMLauncher

import sys

if sys.version_info[0] < 3:
    from StringIO import StringIO
    def _decode(x):
        return x
else:
    from io import StringIO
    def _decode(x):
        return x.decode()

suite = mx.suite('substratevm')
svmSuites = [suite]

def svm_java_compliance():
    return mx.get_jdk(tag='default').javaCompliance

def svm_java8():
    return svm_java_compliance() <= mx.JavaCompliance('1.8')

def graal_compiler_flags(version_tag=None):
    version_tag = version_tag or svm_java_compliance().value
    config_path = mx.dependency('substratevm:svm-compiler-flags-builder').result_file_path(version_tag)
    if not exists(config_path):
        missing_flags_message = '''
Missing graal-compiler-flags config-file {0}. Possible causes:
* Forgot to run "mx build" before using SubstrateVM.
* Generating config-file for Java {1} missing in SubstrateCompilerFlagsBuilder.compute_graal_compiler_flags_map().
'''
        mx.abort(missing_flags_message.format(config_path, version_tag))
    with open(config_path, 'r') as config_file:
        return config_file.read().splitlines()

def svm_unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Run the VM in a mode where application/test classes can
    # access JVMCI loaded classes.
    vmArgs = graal_compiler_flags() + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

if mx.primary_suite() == suite:
    mx_unittest.add_config_participant(svm_unittest_config_participant)

def classpath(args):
    if not args:
        return [] # safeguard against mx.classpath(None) behaviour
    return mx.classpath(args, jdk=mx_compiler.jdk)

clibraryDists = ['SVM_HOSTED_NATIVE']
def clibrary_paths():
    return (mx._get_dependency_path(d) for d in clibraryDists)

def platform_name():
    return mx.get_os() + "-" + mx.get_arch()

def clibrary_libpath():
    return ','.join(join(path, platform_name()) for path in clibrary_paths())

def svm_suite():
    return svmSuites[-1]

def svmbuild_dir(suite=None):
    if not suite:
        suite = svm_suite()
    return join(suite.dir, 'svmbuild')


def is_musl_gcc_wrapper_on_path():
    mx.logv('Probing if musl-gcc exists on path.')
    throwaway_capture = mx.LinesOutputCapture()
    try:
        ret_code = mx.run(['musl-gcc', '-v'], nonZeroIsFatal=False, out=throwaway_capture, err=throwaway_capture)
        return ret_code == 0
    except OSError as _:
        return False


def is_musl_supported():
    jdk = mx.get_jdk(tag='default')
    if mx.is_linux() and mx.get_arch() == "amd64" and mx.get_jdk(tag='default').javaCompliance == '11':
        musl_library_path = join(jdk.home, 'lib', 'static', 'linux-amd64', 'musl')
        return exists(musl_library_path)
    return False


class GraalVMConfig(object):
    def __init__(self, primary_suite_dir, dynamicimports=None, disable_libpolyglot=False, force_bash_launchers=None, skip_libraries=None, exclude_components=None):
        self._primary_suite_dir = primary_suite_dir
        self.dynamicimports = dynamicimports or []
        self.disable_libpolyglot = disable_libpolyglot
        self.force_bash_launchers = force_bash_launchers or []
        self.skip_libraries = skip_libraries or []
        self.exclude_components = exclude_components or []
        for x, _ in mx.get_dynamic_imports():
            self.dynamicimports.append(x)

    def primary_suite_dir(self):
        return self._primary_suite_dir

    def mx_args(self):
        args = ['--disable-installables=true']
        if self.dynamicimports:
            args += ['--dynamicimports', ','.join(self.dynamicimports)]
        if self.disable_libpolyglot:
            args += ['--disable-libpolyglot']
        if self.force_bash_launchers:
            if self.force_bash_launchers is True:
                args += ['--force-bash-launchers=true']
            else:
                args += ['--force-bash-launchers=' + ','.join(self.force_bash_launchers)]
        if self.skip_libraries:
            if self.skip_libraries is True:
                args += ['--skip-libraries=true']
            else:
                args += ['--skip-libraries=' + ','.join(self.skip_libraries)]
        if self.exclude_components:
            args += ['--exclude-components=' + ','.join(self.exclude_components)]
        return args

    def _tuple(self):
        _force_bash_launchers = tuple(self.force_bash_launchers) if isinstance(self.force_bash_launchers, list) else self.force_bash_launchers
        _skip_libraries = tuple(self.skip_libraries) if isinstance(self.skip_libraries, list) else self.skip_libraries
        return tuple(self.dynamicimports), self.disable_libpolyglot, _force_bash_launchers, _skip_libraries, tuple(self.exclude_components)

    def __hash__(self):
        return hash(self._tuple())

    def __eq__(self, other):
        if not isinstance(other, GraalVMConfig):
            return False
        return self._tuple() == self._tuple()

    def __repr__(self):
        return "GraalVMConfig[{}]".format(self._tuple())


def _run_graalvm_cmd(args, config, nonZeroIsFatal=True, out=None, err=None, timeout=None, env=None, quiet=False):
    return mx.run_mx(config.mx_args() + args, suite=config.primary_suite_dir(), nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, env=env, quiet=quiet)


_vm_homes = {}


def _vm_home(config):
    if config not in _vm_homes:
        # get things initialized (e.g., cloning)
        _run_graalvm_cmd(['graalvm-home'], config, out=mx.OutputCapture())
        capture = mx.OutputCapture()
        _run_graalvm_cmd(['graalvm-home'], config, out=capture, quiet=True)
        _vm_homes[config] = capture.data.strip()
    return _vm_homes[config]


_graalvm_force_bash_launchers = ['polyglot', 'native-image-configure', 'gu']
_graalvm_skip_libraries = ['native-image-agent']
_graalvm_exclude_components = ['gu'] if mx.is_windows() else []  # gu does not work on Windows atm

def _graalvm_config():
    return GraalVMConfig(primary_suite_dir=svm_suite().dir)

def _graalvm_jvm_config():
    return GraalVMConfig(primary_suite_dir=svm_suite().dir,
                         disable_libpolyglot=True,
                         force_bash_launchers=True,
                         skip_libraries=True,
                         exclude_components=_graalvm_exclude_components)

def _graalvm_js_config():
    return GraalVMConfig(primary_suite_dir=join(suite.vc_dir, 'vm'),  # Run from `vm` to clone the right revision of `graal-js` if needed
                         dynamicimports=['/' + svm_suite().name, '/graal-js'],
                         disable_libpolyglot=True,
                         force_bash_launchers=_graalvm_force_bash_launchers + ['js'],
                         skip_libraries=_graalvm_skip_libraries,
                         exclude_components=_graalvm_exclude_components)


graalvm_config = _graalvm_config
graalvm_jvm_config = _graalvm_jvm_config


def build_native_image_image(config=None, args=None):
    config = config or graalvm_config()
    mx.log('Building GraalVM with native-image in ' + _vm_home(config))
    env = os.environ.copy()
    if mx.version < mx.VersionSpec("5.219"):
        mx.warn("mx version is older than 5.219, SVM's GraalVM build will not be built with links.\nConsider updating mx to improve IDE compile-on-save workflow.")
    if not mx.is_windows():
        if 'LINKY_LAYOUT' not in env:
            env['LINKY_LAYOUT'] = '*.jar'
        elif '*.jar' not in env['LINKY_LAYOUT']:
            mx.warn("LINKY_LAYOUT already set")
    _run_graalvm_cmd(['build'] + (args or []), config, env=env)


def locale_US_args():
    return ['-Duser.country=US', '-Duser.language=en']

class Tags(set):
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError

GraalTags = Tags([
    'helloworld',
    'helloworld_debug',
    'debuginfotest',
    'test',
    'maven',
    'js',
    'build',
    'benchmarktest',
    "nativeimagehelp",
    'muslcbuild'
])


def vm_native_image_path(config):
    return vm_executable_path('native-image', config)


def vm_executable_path(executable, config):
    if mx.get_os() == 'windows':
        executable += '.cmd'  # links are `.cmd` on windows
    return join(_vm_home(config), 'bin', executable)


def run_musl_basic_tests():
    if is_musl_supported():
        if is_musl_gcc_wrapper_on_path():
            helloworld(['--output-path', svmbuild_dir(), '--static', '--libc=musl'])
        else:
            mx.abort('Attempted to run musl tests without a musl-gcc wrapper.')


@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, native_image_cmd='', config=None, build_if_missing=False):
    config = config or graalvm_config()
    common_args = [] if common_args is None else common_args
    base_args = ['--no-fallback', '-H:+EnforceMaxRuntimeCompileMethods', '-H:+TraceClassInitialization']
    base_args += ['-H:Path=' + svmbuild_dir()]
    has_server = mx.get_os() != 'windows'
    if mx.get_opts().verbose:
        base_args += ['--verbose']
    if mx.get_opts().very_verbose:
        if has_server:
            base_args += ['--verbose-server']
        else:
            base_args += ['--verbose']
    if hosted_assertions:
        base_args += native_image_context.hosted_assertions
    if native_image_cmd:
        if not exists(native_image_cmd):
            mx.abort('Given native_image_cmd does not exist')
    else:
        native_image_cmd = vm_native_image_path(config)

    if build_if_missing and not exists(native_image_cmd):
        build_native_image_image(config)
    if exists(native_image_cmd):
        mx.log('Use ' + native_image_cmd + ' for remaining image builds')
        def _native_image(args, **kwargs):
            mx.run([native_image_cmd, '-H:CLibraryPath=' + clibrary_libpath()] + args, **kwargs)
    else:
        raise mx.abort("GraalVM not built? could not find " + native_image_cmd)

    def query_native_image(all_args, option):

        stdoutdata = []
        def stdout_collector(x):
            stdoutdata.append(x.rstrip())
        _native_image(['--dry-run'] + all_args, out=stdout_collector)

        def remove_quotes(val):
            if len(val) >= 2 and val.startswith("'") and val.endswith("'"):
                return val[1:-1].replace("\\'", "'")
            else:
                return val

        for line in stdoutdata:
            arg = remove_quotes(line.rstrip('\\').strip())
            _, sep, after = arg.partition(option)
            if sep:
                return after.split(' ')[0].rstrip()
        return None

    server_use = set()
    def native_image_func(args, **kwargs):
        all_args = base_args + common_args + args
        if '--experimental-build-server' in all_args:
            server_use.add(True)
        path = query_native_image(all_args, '-H:Path=')
        name = query_native_image(all_args, '-H:Name=')
        image = join(path, name)
        if not has_server and '--no-server' in all_args:
            all_args = [arg for arg in all_args if arg != '--no-server']

        _native_image(all_args, **kwargs)
        return image
    try:
        if exists(native_image_cmd) and has_server and server_use:
            _native_image(['--server-wipe'])
        yield native_image_func
    finally:
        if exists(native_image_cmd) and has_server and server_use:
            def timestr():
                return time.strftime('%d %b %Y %H:%M:%S') + ' - '
            mx.log(timestr() + 'Shutting down image build servers for ' + native_image_cmd)
            _native_image(['--server-shutdown'])
            mx.log(timestr() + 'Shutting down completed')

native_image_context.hosted_assertions = ['-J-ea', '-J-esa']
_native_unittest_features = '--features=com.oracle.svm.test.ImageInfoTest$TestFeature,com.oracle.svm.test.ServiceLoaderTest$TestFeature,com.oracle.svm.test.SecurityServiceTest$TestFeature'

IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases']

def svm_gate_body(args, tasks):
    build_native_image_image()
    with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
        with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
            if t:
                if svm_java8():
                    javac_image(['--output-path', svmbuild_dir()])
                    javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir()))]
                else:
                    # Building javac image currently only supported for Java 8
                    javac_command = []
                helloworld(['--output-path', svmbuild_dir()] + javac_command)
                helloworld(['--output-path', svmbuild_dir(), '--shared'])  # Build and run helloworld as shared library
                cinterfacetutorial([])
                clinittest([])

        with Task('image demos debuginfo', tasks, tags=[GraalTags.helloworld_debug]) as t:
            if t:
                if svm_java8():
                    javac_image(['--output-path', svmbuild_dir(), '-H:GenerateDebugInfo=1'])
                    javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir())), '-H:GenerateDebugInfo=1']
                else:
                    # Building javac image currently only supported for Java 8
                    javac_command = ['-H:GenerateDebugInfo=1']
                helloworld(['--output-path', svmbuild_dir()] + javac_command)
                helloworld(['--output-path', svmbuild_dir(), '--shared', '-H:GenerateDebugInfo=1'])  # Build and run helloworld as shared library
                cinterfacetutorial(['-H:GenerateDebugInfo=1'])
                clinittest([])

        with Task('image debuginfotest', tasks, tags=[GraalTags.debuginfotest]) as t:
            if t:
                debuginfotest(['--output-path', svmbuild_dir()])

        with Task('native unittests', tasks, tags=[GraalTags.test]) as t:
            if t:
                with tempfile.NamedTemporaryFile(mode='w') as blacklist:
                    if svm_java8():
                        blacklist_args = []
                    else:
                        # Currently not working on Java > 8
                        blacklist.write('com.oracle.svm.test.ServiceLoaderTest')
                        blacklist.flush()
                        blacklist_args = ['--blacklist', blacklist.name]

                    # We need the -H:+EnableAllSecurityServices for com.oracle.svm.test.SecurityServiceTest
                    native_unittest(['--build-args', _native_unittest_features, '-H:+EnableAllSecurityServices'] + blacklist_args)

        with Task('Run Truffle NFI unittests with SVM image', tasks, tags=["svmjunit"]) as t:
            if t:
                testlib = mx_subst.path_substitutions.substitute('-Dnative.test.lib=<path:truffle:TRUFFLE_TEST_NATIVE>/<lib:nativetest>')
                native_unittest_args = ['com.oracle.truffle.nfi.test', '--build-args', '--language:nfi',
                                        '-H:MaxRuntimeCompileMethods=2000',
                                        '-H:+RemoveSaturatedTypeFlows', '-H:+TruffleCheckBlackListedMethods',
                                        '--run-args', testlib, '--very-verbose', '--enable-timing']
                native_unittest(native_unittest_args)

            with Task('Musl static hello world and JVMCI version check', tasks, tags=[GraalTags.muslcbuild]) as t:
                if t:
                    run_musl_basic_tests()

    with Task('Check mx native-image --help', tasks, tags=[GraalTags.nativeimagehelp]) as t:
        if t:
            mx.log('Running mx native-image --help output check.')
            # This check works by scanning stdout for the 'Usage' keyword. If that keyword does not appear, it means something broke mx native-image --help.
            def help_stdout_check(output):
                if 'Usage' in output:
                    help_stdout_check.found_usage = True

            help_stdout_check.found_usage = False
            # mx native-image --help is definitely broken if a non zero code is returned.
            mx.run(['mx', 'native-image', '--help'], out=help_stdout_check, nonZeroIsFatal=True)
            if not help_stdout_check.found_usage:
                mx.abort('mx native-image --help does not seem to output the proper message. This can happen if you add extra arguments the mx native-image call without checking if an argument was --help or --help-extra.')

            mx.log('mx native-image --help output check detected no errors.')

    with Task('JavaScript', tasks, tags=[GraalTags.js]) as t:
        if t:
            build_native_image_image(config=_graalvm_js_config())
            with native_image_context(IMAGE_ASSERTION_FLAGS, config=_graalvm_js_config()) as native_image:
                js = build_js(native_image)
                test_run([js, '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
                test_js(js, [('octane-richards', 1000, 100, 300)])

    with Task('maven plugin checks', tasks, tags=[GraalTags.maven]) as t:
        if t:
            maven_plugin_install(["--deploy-dependencies"])
            maven_plugin_test([])


def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), "-proc:none", "-bootclasspath",
            join(mx_compiler.jdk.home, "jre", "lib", "rt.jar")]


def _native_junit(native_image, unittest_args, build_args=None, run_args=None, blacklist=None, whitelist=None, preserve_image=False):
    unittest_args = unittest_args
    build_args = build_args or []

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        build_args.append("-D" + key + "=" + value)

    run_args = run_args or ['--verbose']
    junit_native_dir = join(svmbuild_dir(), platform_name(), 'junit')
    mkpath(junit_native_dir)
    junit_tmp_dir = tempfile.mkdtemp(dir=junit_native_dir)
    try:
        unittest_deps = []
        def dummy_harness(test_deps, vm_launcher, vm_args):
            unittest_deps.extend(test_deps)
        unittest_file = join(junit_tmp_dir, 'svmjunit.tests')
        _run_tests(unittest_args, dummy_harness, _VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, blacklist, whitelist, None, None)
        if not exists(unittest_file):
            mx.abort('No matching unit tests found. Skip image build and execution.')
        with open(unittest_file, 'r') as f:
            mx.log('Building junit image for matching: ' + ' '.join(l.rstrip() for l in f))
        extra_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk)
        unittest_image = native_image(build_args + extra_image_args + ['--macro:junit=' + unittest_file, '-H:Path=' + junit_tmp_dir])
        if preserve_image:
            build_dir = join(svmbuild_dir(), 'junit')
            mkpath(build_dir)
            unittest_image_dst = join(build_dir, basename(unittest_image))
            move(unittest_image, unittest_image_dst)
            unittest_image = unittest_image_dst
        mx.log('Running: ' + ' '.join(map(pipes.quote, [unittest_image] + run_args)))
        mx.run([unittest_image] + run_args)
    finally:
        remove_tree(junit_tmp_dir)

_mask_str = '#'


def _mask(arg, arg_list):
    if arg in (arg_list + ['-h', '--help', '--']):
        return arg
    else:
        return arg.replace('-', _mask_str)


def unmask(args):
    return [arg.replace(_mask_str, '-') for arg in args]


def _native_unittest(native_image, cmdline_args):
    parser = ArgumentParser(prog='mx native-unittest', description='Run unittests as native image.')
    all_args = ['--build-args', '--run-args', '--blacklist', '--whitelist', '-p', '--preserve-image']
    cmdline_args = [_mask(arg, all_args) for arg in cmdline_args]
    parser.add_argument(all_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(all_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument('--blacklist', help='run all testcases not specified in <file>', metavar='<file>')
    parser.add_argument('--whitelist', help='run testcases specified in <file> only', metavar='<file>')
    parser.add_argument('-p', '--preserve-image', help='do not delete the generated native image', action='store_true')
    parser.add_argument('unittest_args', metavar='TEST_ARG', nargs='*')
    pargs = parser.parse_args(cmdline_args)

    blacklist = unmask([pargs.blacklist])[0] if pargs.blacklist else None
    whitelist = unmask([pargs.whitelist])[0] if pargs.whitelist else None

    if whitelist:
        try:
            with open(whitelist) as fp:
                whitelist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read whitelist: ' + whitelist)
    if blacklist:
        try:
            with open(blacklist) as fp:
                blacklist = [re.compile(fnmatch.translate(l.rstrip())) for l in fp.readlines() if not l.startswith('#')]
        except IOError:
            mx.log('warning: could not read blacklist: ' + blacklist)

    unittest_args = unmask(pargs.unittest_args) if unmask(pargs.unittest_args) else ['com.oracle.svm.test']
    _native_junit(native_image, unittest_args, unmask(pargs.build_args), unmask(pargs.run_args), blacklist, whitelist, pargs.preserve_image)


def js_image_test(binary, bench_location, name, warmup_iterations, iterations, timeout=None, bin_args=None):
    bin_args = bin_args if bin_args is not None else []
    jsruncmd = [binary] + bin_args + [join(bench_location, 'harness.js'), '--', join(bench_location, name + '.js'),
                                      '--', '--warmup-iterations=' + str(warmup_iterations),
                                      '--iterations=' + str(iterations)]
    mx.log(' '.join(jsruncmd))

    passing = []

    stdoutdata = []
    def stdout_collector(x):
        stdoutdata.append(x)
        mx.log(x.rstrip())
    stderrdata = []
    def stderr_collector(x):
        stderrdata.append(x)
        mx.warn(x.rstrip())

    returncode = mx.run(jsruncmd, cwd=bench_location, out=stdout_collector, err=stderr_collector, nonZeroIsFatal=False, timeout=timeout)

    if returncode == mx.ERROR_TIMEOUT:
        print('INFO: TIMEOUT (> %d): %s' % (timeout, name))
    elif returncode >= 0:
        matches = 0
        for line in stdoutdata:
            if re.match(r'^\S+: *\d+(\.\d+)?\s*$', line):
                matches += 1
        if matches > 0:
            passing = stdoutdata

    if not passing:
        mx.abort('JS benchmark ' + name + ' failed')


def build_js(native_image):
    return native_image(['--macro:js-launcher', '--no-server'])

def test_js(js, benchmarks, bin_args=None):
    bench_location = join(suite.dir, '..', '..', 'js-benchmarks')
    for benchmark_name, warmup_iterations, iterations, timeout in benchmarks:
        js_image_test(js, bench_location, benchmark_name, warmup_iterations, iterations, timeout, bin_args=bin_args)

def test_run(cmds, expected_stdout, timeout=10):
    stdoutdata = []
    def stdout_collector(x):
        stdoutdata.append(x)
        mx.log(x.rstrip())
    stderrdata = []
    def stderr_collector(x):
        stderrdata.append(x)
        mx.warn(x.rstrip())
    returncode = mx.run(cmds, out=stdout_collector, err=stderr_collector, nonZeroIsFatal=False, timeout=timeout)
    if ''.join(stdoutdata) != expected_stdout:
        mx.abort('Error: stdout does not match expected_stdout')
    return (returncode, stdoutdata, stderrdata)

mx_gate.add_gate_runner(suite, svm_gate_body)


def _cinterfacetutorial(native_image, args=None):
    """Build and run the tutorial for the C interface"""

    args = [] if args is None else args
    tutorial_proj = mx.dependency('com.oracle.svm.tutorial')
    c_source_dir = join(tutorial_proj.dir, 'native')
    build_dir = join(svmbuild_dir(), tutorial_proj.name, 'build')

    # clean / create output directory
    if exists(build_dir):
        remove_tree(build_dir)
    mkpath(build_dir)

    # Build the shared library from Java code
    native_image(['--shared', '-H:Path=' + build_dir, '-H:Name=libcinterfacetutorial',
                  '-H:CLibraryPath=' + tutorial_proj.dir, '-cp', tutorial_proj.output_dir()] + args)

    # Build the C executable
    if mx.get_os() != 'windows':
        mx.run(['cc', '-g', join(c_source_dir, 'cinterfacetutorial.c'),
                '-I.', '-L.', '-lcinterfacetutorial',
                '-ldl', '-Wl,-rpath,' + build_dir,
                '-o', 'cinterfacetutorial'],
               cwd=build_dir)
    else:
        mx.run(['cl', '-MD', join(c_source_dir, 'cinterfacetutorial.c'),
                '-I.', 'libcinterfacetutorial.lib'],
               cwd=build_dir)

    # Start the C executable
    mx.run([join(build_dir, 'cinterfacetutorial')])


def _helloworld(native_image, javac_command, path, build_only, args):
    mkpath(path)
    hello_file = os.path.join(path, 'HelloWorld.java')
    envkey = 'HELLO_WORLD_MESSAGE'
    output = 'Hello from native-image!'
    with open(hello_file, 'w') as fp:
        fp.write('public class HelloWorld { public static void main(String[] args) { System.out.println(System.getenv("' + envkey + '")); } }')
        fp.flush()
    mx.run(javac_command + [hello_file])

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        args.append("-D" + key + "=" + value)

    native_image(["--native-image-info", "-H:Path=" + path, '-H:+VerifyNamingConventions', '-cp', path, 'HelloWorld'] + args)

    if not build_only:
        expected_output = [output + os.linesep]
        actual_output = []
        def _collector(x):
            actual_output.append(x)
            mx.log(x)

        if '--shared' in args:
            # If helloword got built into a shared library we use python to load the shared library
            # and call its `run_main`. We are capturing the stdout during the call into an unnamed
            # pipe so that we can use it in the actual vs. expected check below.
            try:
                import ctypes
                so_name = mx.add_lib_suffix('helloworld')
                lib = ctypes.CDLL(join(path, so_name))
                stdout = os.dup(1)  # save original stdout
                pout, pin = os.pipe()
                os.dup2(pin, 1)  # connect stdout to pipe
                os.environ[envkey] = output
                lib.run_main(1, 'dummy')  # call run_main of shared lib
                call_stdout = os.read(pout, 120)  # get pipe contents
                actual_output.append(call_stdout)
                os.dup2(stdout, 1)  # restore original stdout
                mx.log('Stdout from calling run_main in shared object {}:'.format(so_name))
                mx.log(call_stdout)
                actual_output = list(map(_decode, actual_output))
            finally:
                del os.environ[envkey]
                os.close(pin)
                os.close(pout)
        else:
            env = os.environ.copy()
            env[envkey] = output
            mx.run([join(path, 'helloworld')], out=_collector, env=env)

        if actual_output != expected_output:
            raise Exception('Unexpected output: ' + str(actual_output) + "  !=  " + str(expected_output))

def _debuginfotest(native_image, path, build_only, args):
    mkpath(path)
    parent = os.path.dirname(path)
    mx.log("parent=%s"%parent)
    sourcepath = mx.project('com.oracle.svm.test').source_dirs()[0]
    mx.log("sourcepath=%s"%sourcepath)
    sourcecache = join(path, 'sources')
    mx.log("sourcecache=%s"%sourcecache)

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        args.append("-D" + key + "=" + value)

    native_image_args = ["--native-image-info", "-H:Path=" + path,
                         '-H:+VerifyNamingConventions',
                         '-cp', classpath('com.oracle.svm.test'),
                         '-Dgraal.LogFile=graal.log',
                         '-H:GenerateDebugInfo=1',
                         '-H:DebugInfoSourceSearchPath=' + sourcepath,
                         '-H:DebugInfoSourceCacheRoot=' + join(path, 'sources'),
                         'hello.Hello'] + args
    mx.log('native_image {}'.format(native_image_args))
    native_image(native_image_args)

    if mx.get_os() == 'linux' and not build_only:
        mx.run(['gdb', '-x', join(parent, 'mx.substratevm/testhello.py'), join(path, 'hello.hello')])


def _javac_image(native_image, path, args=None):
    args = [] if args is None else args
    mkpath(path)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    native_image(["-H:Path=" + path, '-cp', mx_compiler.jdk.toolsjar, "com.sun.tools.javac.Main", "javac",
                  "-H:+ReportUnsupportedElementsAtRuntime", "-H:+AllowIncompleteClasspath",
                  "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.version"] + args)


orig_command_benchmark = mx.command_function('benchmark')


@mx.command(suite.name, 'benchmark')
def benchmark(args):
    # if '--jsvm=substratevm' in args:
    #     truffle_language_ensure('js')
    return orig_command_benchmark(args)

def mx_post_parse_cmd_line(opts):
    for dist in suite.dists:
        if not dist.isTARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

def native_image_context_run(func, func_args=None, config=None, build_if_missing=False):
    func_args = [] if func_args is None else func_args
    with native_image_context(config=config, build_if_missing=build_if_missing) as native_image:
        func(native_image, func_args)

def pom_from_template(proj_dir, svmVersion):
    # Create native-image-maven-plugin pom with correct version info from template
    dom = parse(join(proj_dir, 'pom_template.xml'))
    for svmVersionElement in dom.getElementsByTagName('svmVersion'):
        svmVersionElement.parentNode.replaceChild(dom.createTextNode(svmVersion), svmVersionElement)
    with open(join(proj_dir, 'pom.xml'), 'w') as pom_file:
        dom.writexml(pom_file)

def deploy_native_image_maven_plugin(svmVersion, repo, gpg, keyid):
    proj_dir = join(suite.dir, 'src', 'native-image-maven-plugin')
    pom_from_template(proj_dir, svmVersion)
    # Build and install native-image-maven-plugin into local repository

    maven_args = []
    if keyid:
        maven_args += ['-Dgpg.keyname=' + keyid]
    elif not gpg:
        maven_args += ['-Dgpg.skip=true']
    if repo == mx.maven_local_repository():
        maven_args += ['install']
    else:
        maven_args += [
            '-DaltDeploymentRepository={}::default::{}'.format(repo.name, repo.get_url(svmVersion)),
            'deploy'
        ]
    mx.run_maven(maven_args, cwd=proj_dir)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM',
    short_name='svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['GraalVM compiler', 'Truffle Macro', 'Truffle NFI'],
    jar_distributions=['substratevm:LIBRARY_SUPPORT'],
    builder_jar_distributions=[
        'substratevm:SVM',
        'substratevm:OBJECTFILE',
        'substratevm:POINTSTO',
    ],
    support_distributions=['substratevm:SVM_GRAALVM_SUPPORT'],
))

def _native_image_launcher_main_class():
    """
    Gets the name of the entry point for running com.oracle.svm.driver.NativeImage.
    """
    return "com.oracle.svm.driver.NativeImage" + ("" if svm_java8() else "$JDK9Plus")

def _native_image_launcher_extra_jvm_args():
    """
    Gets the extra JVM args needed for running com.oracle.svm.driver.NativeImage.
    """
    if svm_java8():
        return []
    jdk = mx.get_jdk(tag='default')
    # Support for com.oracle.svm.driver.NativeImage$JDK9Plus
    res = ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(jdk):
        res.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'])
    return res

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image',
    short_name='ni',
    dir_name='svm',
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM', 'nil'],
    support_distributions=['substratevm:NATIVE_IMAGE_GRAALVM_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:native-image>",
            jar_distributions=["substratevm:SVM_DRIVER"],
            main_class=_native_image_launcher_main_class(),
            build_args=[],
            extra_jvm_args=_native_image_launcher_extra_jvm_args(),
        ),
    ],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination="<lib:native-image-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:JVMTI_AGENT_BASE',
                'substratevm:SVM_AGENT',
            ],
            build_args=[
                '--features=com.oracle.svm.agent.NativeImageAgent$RegistrationFeature'
            ],
        ),
    ],
    provided_executables=['bin/<cmd:rebuild-images>'],
    installable=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image licence files',
    short_name='nil',
    dir_name='svm',
    installable_id='native-image',
    license_files=['LICENSE_NATIVEIMAGE.txt'],
    third_party_license_files=[],
    dependencies=[],
    support_distributions=['substratevm:NATIVE_IMAGE_LICENSE_GRAALVM_SUPPORT'],
    installable=True,
    priority=1,
))

if not mx.is_windows():
    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
        suite=suite,
        name='SubstrateVM LLVM',
        short_name='svml',
        dir_name='svm',
        license_files=[],
        third_party_license_files=[],
        dependencies=['SubstrateVM'],
        builder_jar_distributions=[
            'substratevm:SVM_LLVM',
            'substratevm:LLVM_WRAPPER_SHADOWED',
            'substratevm:JAVACPP_SHADOWED',
            'substratevm:LLVM_PLATFORM_SPECIFIC_SHADOWED',
        ],
    ))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Polyglot Native API',
    short_name='polynative',
    dir_name='polyglot',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=['substratevm:POLYGLOT_NATIVE_API'],
    support_distributions=[
        "substratevm:POLYGLOT_NATIVE_API_HEADERS",
    ],
    polyglot_lib_build_args=[
        "--macro:truffle",
        "-H:Features=org.graalvm.polyglot.nativeapi.PolyglotNativeAPIFeature",
        "-Dorg.graalvm.polyglot.nativeapi.libraryPath=${.}/../../../polyglot/",
        "-H:CStandard=C11",
        "-H:+SpawnIsolates",
    ],
    polyglot_lib_jar_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API",
    ],
    has_polyglot_lib_entrypoints=True,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Native Image JUnit',
    short_name='nju',
    dir_name='junit',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    builder_jar_distributions=['mx:JUNIT_TOOL', 'mx:JUNIT', 'mx:HAMCREST'],
    support_distributions=['substratevm:NATIVE_IMAGE_JUNIT_SUPPORT'],
))

jar_distributions = [
    'substratevm:GRAAL_HOTSPOT_LIBRARY',
    'compiler:GRAAL_TRUFFLE_COMPILER_LIBGRAAL',
    'compiler:GRAAL_MANAGEMENT_LIBGRAAL']

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='LibGraal',
    short_name='lg',
    dir_name=False,
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=[],
    builder_jar_distributions=[],
    support_distributions=[],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination="<lib:jvmcicompiler>",
            jvm_library=True,
            jar_distributions=jar_distributions,
            build_args=[
                '--features=com.oracle.svm.graal.hotspot.libgraal.LibGraalFeature',
                '--initialize-at-build-time',
                '-H:-UseServiceLoaderFeature',
                '-H:+AllowFoldMethods',
                '-H:+ReportExceptionStackTraces',
                '-Djdk.vm.ci.services.aot=true',
                '-Dtruffle.TruffleRuntime=',

                # These 2 arguments provide walkable call stacks for a crash in libgraal
                '-H:+PreserveFramePointer',
                '-H:-DeleteLocalSymbols',
            ],
        ),
    ],
))

def _native_image_configure_extra_jvm_args():
    if svm_java8():
        return []
    packages = ['jdk.internal.vm.compiler/org.graalvm.compiler.phases.common', 'jdk.internal.vm.ci/jdk.vm.ci.meta', 'jdk.internal.vm.compiler/org.graalvm.compiler.core.common.util']
    args = ['--add-exports=' + packageName + '=ALL-UNNAMED' for packageName in packages]
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(mx.get_jdk(tag='default')):
        args.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'])
    return args

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image Configure Tool',
    short_name='nic',
    dir_name='svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['ni'],
    support_distributions=[],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:native-image-configure>",
            jar_distributions=["substratevm:SVM_CONFIGURE"],
            main_class="com.oracle.svm.configure.ConfigurationTool",
            build_args=[
                "-H:-ParseRuntimeOptions",
            ],
            extra_jvm_args=_native_image_configure_extra_jvm_args(),
        )
    ],
))


def run_helloworld_command(args, config, command_name):
    parser = ArgumentParser(prog='mx ' + command_name)
    all_args = ['--output-path', '--javac-command', '--build-only']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument(all_args[1], metavar='<javac-command>', help='A javac command to be used', default=mx.get_jdk().javac)
    parser.add_argument(all_args[2], action='store_true', help='Only build the native image', default=False)
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    javac_command = unmask(parsed.javac_command.split())
    output_path = unmask(parsed.output_path)[0]
    build_only = parsed.build_only
    image_args = unmask(parsed.image_args)
    native_image_context_run(
        lambda native_image, a:
        _helloworld(native_image, javac_command, output_path, build_only, a), unmask(image_args),
        config=config,
        build_if_missing=True
    )


@mx.command(suite_name=suite.name, command_name='debuginfotest', usage_msg='[options]')
def debuginfotest(args, config=None):
    """
    builds a debuginfo Hello native image and tests it with gdb.
    """
    parser = ArgumentParser(prog='mx debuginfotest')
    all_args = ['--output-path', '--build-only']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument(all_args[1], action='store_true', help='Only build the native image', default=False)
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    output_path = unmask(parsed.output_path)[0]
    build_only = parsed.build_only
    native_image_context_run(
        lambda native_image, a:
            _debuginfotest(native_image, output_path, build_only, a), unmask(parsed.image_args),
        config=config,
        build_if_missing=True
    )


@mx.command(suite_name=suite.name, command_name='helloworld', usage_msg='[options]')
def helloworld(args, config=None):
    """
    builds a Hello, World! native image.
    """
    run_helloworld_command(args, config, "helloworld")


@mx.command(suite.name, 'cinterfacetutorial', 'Runs the ')
def cinterfacetutorial(args):
    """
    runs all tutorials for the C interface.
    """
    native_image_context_run(_cinterfacetutorial, args, build_if_missing=True)


@mx.command(suite.name, 'clinittest', 'Runs the ')
def clinittest(args):
    def build_and_test_clinittest_image(native_image, args=None):
        args = [] if args is None else args
        test_cp = classpath('com.oracle.svm.test')
        build_dir = join(svmbuild_dir(), 'clinittest')

        # clean / create output directory
        if exists(build_dir):
            remove_tree(build_dir)
        mkpath(build_dir)

        # Build and run the example
        native_image(
            ['-H:Path=' + build_dir, '-cp', test_cp, '-H:Class=com.oracle.svm.test.TestClassInitializationMustBeSafe',
             '-H:Features=com.oracle.svm.test.TestClassInitializationMustBeSafeFeature',
             '-H:+TraceClassInitialization',
             '-H:+PrintClassInitialization', '-H:Name=clinittest', '-H:+ReportExceptionStackTraces'] + args)
        mx.run([join(build_dir, 'clinittest')])

        # Check the reports for initialized classes
        def check_class_initialization(classes_file_name, marker, prefix=''):
            classes_file = os.path.join(build_dir, 'reports', classes_file_name)
            with open(classes_file) as f:
                wrongly_initialized_classes = [line.strip() for line in f if line.strip().startswith(prefix) and marker not in line.strip()]
                if len(wrongly_initialized_classes) > 0:
                    mx.abort("Only classes with marker " + marker + " must be in file " + classes_file + ". Found:\n" +
                             str(wrongly_initialized_classes))

        reports = os.listdir(os.path.join(build_dir, 'reports'))
        delayed_classes = next(report for report in reports if report.startswith('run_time_classes'))
        safe_classes = next(report for report in reports if report.startswith('safe_classes'))

        check_class_initialization(delayed_classes, 'MustBeDelayed', prefix='com.oracle.svm.test')
        check_class_initialization(safe_classes, 'MustBeSafe')

    native_image_context_run(build_and_test_clinittest_image, args, build_if_missing=True)


class SubstrateJvmFuncsFallbacksBuilder(mx.Project):
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        mx.Project.__init__(self, suite, name, "", [], deps, workingSets, suite.dir, theLicense, **kwArgs)

    def getBuildTask(self, args):
        return JvmFuncsFallbacksBuildTask(self, args, 1)

class JvmFuncsFallbacksBuildTask(mx.BuildTask):
    def __init__(self, subject, args, parallelism):
        super(JvmFuncsFallbacksBuildTask, self).__init__(subject, args, parallelism)

        self.native_project_dir = join(mx.dependency('substratevm:com.oracle.svm.native.jvm.' + ('windows' if mx.is_windows() else 'posix')).dir, 'src')
        self.jvm_funcs_path = join(self.native_project_dir, 'JvmFuncs.c')

        native_project_src_gen_dir = join(self.native_project_dir, 'src_gen')
        self.jvm_fallbacks_path = join(native_project_src_gen_dir, 'JvmFuncsFallbacks.c')

        if svm_java8():
            staticlib_path = ['jre', 'lib']
        else:
            staticlib_path = ['lib', 'static', mx.get_os() + '-' + mx.get_arch()]
            if mx.is_linux():
                # Assume we are running under glibc by default for now.
                staticlib_path = staticlib_path + ['glibc']
            # Allow older labsjdk versions to work
            if not exists(join(mx_compiler.jdk.home, *staticlib_path)):
                staticlib_path = ['lib']

        staticlib_wildcard = staticlib_path + [mx_subst.path_substitutions.substitute('<staticlib:*>')]
        staticlib_wildcard_path = join(mx_compiler.jdk.home, *staticlib_wildcard)
        self.staticlibs = glob(staticlib_wildcard_path)

    def newestOutput(self):
        return mx.TimeStampFile(self.jvm_fallbacks_path)

    def needsBuild(self, newestInput):
        sup = super(JvmFuncsFallbacksBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup

        outfile = self.newestOutput()
        if not outfile.timestamp:
            return True, outfile.path + ' does not exist'

        if not self.staticlibs:
            mx.abort('Please use a JDK that contains static JDK libraries.\n'
                     + 'See: https://github.com/oracle/graal/tree/master/substratevm#quick-start')

        infile = mx.TimeStampFile.newest([self.jvm_funcs_path] + self.staticlibs)
        needs_build = infile.isNewerThan(outfile)
        return needs_build, infile.path + ' is newer than ' + outfile.path

    def build(self):

        def collect_missing_symbols():
            symbols = set()

            def collect_symbols_fn(symbol_prefix):
                def collector(line):
                    try:
                        mx.logvv('Processing line: ' + line.rstrip())
                        line_tokens = line.split()
                        if mx.is_windows():
                            # Windows dumpbin /SYMBOLS output
                            # 030 00000000 UNDEF  notype ()    External     | JVM_GetArrayLength
                            found_undef = line_tokens[2] == 'UNDEF'
                        elif mx.is_darwin():
                            # Darwin nm
                            #                  U _JVM_InitStackTraceElement
                            found_undef = line_tokens[0].upper() == 'U'
                        else:
                            # Linux objdump objdump --wide --syms
                            # 0000000000000000         *UND*	0000000000000000 JVM_InitStackTraceElement
                            found_undef = line_tokens[1] = '*UND*'
                        if found_undef:
                            symbol_candiate = line_tokens[-1]
                            mx.logvv('Found undefined symbol: ' + symbol_candiate)
                            platform_prefix = '_' if mx.is_darwin() else ''
                            if symbol_candiate.startswith(platform_prefix + symbol_prefix):
                                mx.logv('Pick symbol: ' + symbol_candiate)
                                symbols.add(symbol_candiate[len(platform_prefix):])
                    except:
                        mx.logv('Skipping line: ' + line.rstrip())
                return collector

            if mx.is_windows():
                symbol_dump_command = 'dumpbin /SYMBOLS'
            elif mx.is_darwin():
                symbol_dump_command = 'nm'
            elif mx.is_linux():
                symbol_dump_command = 'objdump --wide --syms'
            else:
                mx.abort('gen_fallbacks not supported on ' + sys.platform)

            for staticlib_path in self.staticlibs:
                mx.logv('Collect from : ' + staticlib_path)
                mx.run(symbol_dump_command.split() + [staticlib_path], out=collect_symbols_fn('JVM_'))

            if len(symbols) == 0:
                mx.abort('Could not find any unresolved JVM_* symbols in static JDK libraries')
            return symbols

        def collect_implementations():
            impls = set()

            def collect_impls_fn(symbol_prefix):
                def collector(line):
                    mx.logvv('Processing line: ' + line.rstrip())
                    # JNIEXPORT void JNICALL JVM_DefineModule(JNIEnv *env, jobject module, jboolean is_open, jstring version
                    tokens = line.split()
                    try:
                        index = tokens.index('JNICALL')
                        name_part = tokens[index + 1]
                        if name_part.startswith(symbol_prefix):
                            impl_name = name_part.split('(')[0].rstrip()
                            mx.logv('Found matching implementation: ' + impl_name)
                            impls.add(impl_name)
                    except:
                        mx.logv('Skipping line: ' + line.rstrip())
                return collector

            with open(self.jvm_funcs_path) as f:
                collector = collect_impls_fn('JVM_')
                for line in f:
                    collector(line)

            if len(impls) == 0:
                mx.abort('Could not find any implementations for JVM_* symbols in JvmFuncs.c')
            return impls

        def write_fallbacks(required_fallbacks, jvm_fallbacks_path):
            try:
                new_fallback = StringIO()
                new_fallback.write('/* Fallback implementations autogenerated by mx_substratevm.py */\n\n')
                new_fallback.write('#include <jni.h>\n')
                jnienv_function_stub = '''
JNIEXPORT jobject JNICALL {0}(JNIEnv *env) {{
    (*env)->FatalError(env, "{0} called:  Unimplemented");
    return NULL;
}}
'''
                plain_function_stub = '''
JNIEXPORT void JNICALL {0}() {{
    fprintf(stderr, "{0} called:  Unimplemented\\n");
    abort();
}}
'''
                noJNIEnvParam = [
                    'JVM_GC',
                    'JVM_ActiveProcessorCount',
                    'JVM_GetInterfaceVersion',
                    'JVM_GetManagement',
                    'JVM_IsSupportedJNIVersion',
                    'JVM_MaxObjectInspectionAge',
                    'JVM_NativePath',
                    'JVM_ReleaseUTF',
                    'JVM_SupportsCX8',
                    'JVM_BeforeHalt', 'JVM_Halt',
                    'JVM_LoadLibrary', 'JVM_UnloadLibrary', 'JVM_FindLibraryEntry',
                    'JVM_FindSignal', 'JVM_RaiseSignal', 'JVM_RegisterSignal',
                    'JVM_FreeMemory', 'JVM_MaxMemory', 'JVM_TotalMemory',
                    'JVM_RawMonitorCreate', 'JVM_RawMonitorDestroy', 'JVM_RawMonitorEnter', 'JVM_RawMonitorExit'
                ]

                for name in required_fallbacks:
                    function_stub = plain_function_stub if name in noJNIEnvParam else jnienv_function_stub
                    new_fallback.write(function_stub.format(name))

                same_content = False
                if exists(jvm_fallbacks_path):
                    with open(jvm_fallbacks_path) as old_fallback:
                        if old_fallback.read() == new_fallback.getvalue():
                            same_content = True
                if same_content:
                    mx.TimeStampFile(jvm_fallbacks_path).touch()
                else:
                    mx.ensure_dir_exists(dirname(jvm_fallbacks_path))
                    with open(jvm_fallbacks_path, mode='w') as new_fallback_file:
                        new_fallback_file.write(new_fallback.getvalue())
                        mx.log('Updated ' + jvm_fallbacks_path)
            finally:
                if new_fallback:
                    new_fallback.close()

        required_fallbacks = collect_missing_symbols() - collect_implementations()
        write_fallbacks(sorted(required_fallbacks), self.jvm_fallbacks_path)

    def clean(self, forBuild=False):
        gen_src_dir = dirname(self.jvm_fallbacks_path)
        if exists(gen_src_dir):
            remove_tree(gen_src_dir)

    def __str__(self):
        return 'JvmFuncsFallbacksBuildTask {}'.format(self.subject)

class SubstrateCompilerFlagsBuilder(mx.ArchivableProject):

    def config_file(self, ver):
        return 'graal-compiler-flags-' + str(ver) + '.config'

    def result_file_path(self, version):
        return join(self.output_dir(), self.config_file(version))

    def output_dir(self):
        return self.get_output_root()

    def archive_prefix(self):
        return ''

    def getResults(self):
        graal_compiler_flags_map = self.compute_graal_compiler_flags_map()
        mx.ensure_dir_exists(self.output_dir())
        yield self.config_file_update(self.result_file_path("versions"), self.config_file_versions())
        for version in self.config_file_versions():
            if version not in graal_compiler_flags_map:
                mx.abort('Missing support for generating ' + self.config_file(version))
            yield self.config_file_update(self.result_file_path(version), graal_compiler_flags_map[version])

    def config_file_update(self, file_path, lines):
        changed = True
        file_contents = '\n'.join(str(line) for line in lines)
        try:
            with open(file_path, 'r') as config_file:
                if config_file.read() == file_contents:
                    changed = False
        except:
            pass

        if changed:
            with open(file_path, 'w') as f:
                print('Write file ' + file_path)
                f.write(file_contents)

        return file_path

    def config_file_versions(self):
        versions = [8, 11, 13, 14, 15]
        if svm_java8():
            return versions[:1]
        return versions

    def compute_graal_compiler_flags_map(self):
        graal_compiler_flags_map = dict()
        graal_compiler_flags_map[8] = [
            '-d64',
            '-XX:-UseJVMCIClassLoader'
        ]

        if not svm_java8():
            graal_compiler_flags_map[11] = [
                # Disable the check for JDK-8 graal version.
                '-Dsubstratevm.IgnoreGraalVersionCheck=true',
            ]

            # Packages to add-export
            distributions_transitive = mx.classpath_entries(self.deps)
            jdk = mx.get_jdk(tag='default')
            required_exports = mx_javamodules.requiredExports(distributions_transitive, jdk)
            target_module = 'ALL-UNNAMED'
            exports_flags = mx_sdk_vm.AbstractNativeImageConfig.get_add_exports_list(required_exports, target_module)
            graal_compiler_flags_map[11].extend(exports_flags)

            # Packages to add-open
            add_opens_packages = [
                # Reflective access to jdk.internal.vm.compiler
                'jdk.internal.vm.compiler/org.graalvm.compiler.debug',
                'jdk.internal.vm.compiler/org.graalvm.compiler.nodes',

                # Reflective access
                'jdk.unsupported/sun.reflect',
                # Reflective access to jdk.internal.module.Modules, using which I can export and open other modules.
                'java.base/jdk.internal.module',

                # These packages should be opened at runtime calls to Modules.addOpens, if they are still needed.
                # Reflective access to jdk.internal.ref.CleanerImpl$PhantomCleanableRef.
                'java.base/jdk.internal.ref',
                # Reflective access to jdk.internal.reflect.MethodAccessor.
                'java.base/jdk.internal.reflect',
                # Reflective access to java.io.ExpiringCache
                'java.base/java.io',
                # Reflective access to private fields of java.lang.Class.
                'java.base/java.lang',
                # Reflective access to java.lang.reflect.ProxyGenerator.generateProxyClass
                'java.base/java.lang.reflect',
                # Reflective access to java.lang.invoke.VarHandle*.
                'java.base/java.lang.invoke',
                # Reflective access to java.lang.Reference.referent.
                'java.base/java.lang.ref',
                # Reflective access to java.net.URL.getURLStreamHandler.
                'java.base/java.net',
                # Reflective access to java.nio.MappedByteBuffer.fd.
                'java.base/java.nio',
                # Reflective access to java.nio.files.FileTypeDetector
                'java.base/java.nio.file',
                # Reflective access to java.security.Provider.knownEngines
                'java.base/java.security',
                # Reflective access javax.crypto.JceSecurity.getVerificationResult
                'java.base/javax.crypto',
                # Reflective access to java.util.Bits.words.
                'java.base/java.util',
                # Reflective access to java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl.tclass.
                'java.base/java.util.concurrent.atomic',
                # Reflective access to sun.security.x509.OIDMap.nameMap
                'java.base/sun.security.x509',
                'java.base/jdk.internal.logger',

                # Reflective access to org.graalvm.nativeimage.impl.ImageSingletonsSupport.
                'org.graalvm.sdk/org.graalvm.nativeimage.impl',
                'org.graalvm.sdk/org.graalvm.polyglot',

                'org.graalvm.truffle/com.oracle.truffle.polyglot',
                'org.graalvm.truffle/com.oracle.truffle.api.impl',
            ]
            graal_compiler_flags_map[11].extend(['--add-opens=' + entry + '=' + target_module for entry in add_opens_packages])

            # Currently JDK 13, 14, 15 and JDK 11 have the same flags
            graal_compiler_flags_map[13] = graal_compiler_flags_map[11]
            graal_compiler_flags_map[14] = graal_compiler_flags_map[11]
            graal_compiler_flags_map[15] = graal_compiler_flags_map[11]

        graal_compiler_flags_base = [
            '-XX:+UseParallelGC',  # native image generation is a throughput-oriented task
            '-XX:+UnlockExperimentalVMOptions',
            '-XX:+EnableJVMCI',
            '-Dtruffle.TrustAllTruffleRuntimeProviders=true', # GR-7046
            '-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime', # use truffle interpreter as fallback
            '-Dgraalvm.ForcePolyglotInvalid=true', # use PolyglotInvalid PolyglotImpl fallback (when --tool:truffle is not used)
            '-Dgraalvm.locatorDisabled=true',
        ]
        for key in graal_compiler_flags_map:
            graal_compiler_flags_map[key] = graal_compiler_flags_base + graal_compiler_flags_map[key]

        return graal_compiler_flags_map


def _ensure_vm_built(config):
    # build "jvm" config used by native-image and native-image-configure commands
    rebuild_vm = False
    mx.ensure_dir_exists(svmbuild_dir())
    if not mx.is_windows():
        vm_link = join(svmbuild_dir(), 'vm')
        if not os.path.exists(vm_link):
            rebuild_vm = True
            if os.path.lexists(vm_link):
                os.unlink(vm_link)
            vm_linkname = os.path.relpath(_vm_home(config), dirname(vm_link))
            os.symlink(vm_linkname, vm_link)
    rev_file_name = join(svmbuild_dir(), 'vm-rev')
    rev_value = svm_suite().vc.parent(svm_suite().vc_dir)
    if not os.path.exists(rev_file_name):
        rebuild_vm = True
    else:
        with open(rev_file_name, 'r') as f:
            if f.read() != rev_value:
                rebuild_vm = True
    if rebuild_vm:
        with open(rev_file_name, 'w') as f:
            f.write(rev_value)
        build_native_image_image(config)

@mx.command(suite.name, 'native-image')
def native_image_on_jvm(args, **kwargs):
    config = graalvm_jvm_config()
    _ensure_vm_built(config)
    if mx.is_windows():
        executable = vm_native_image_path(config)
    else:
        vm_link = join(svmbuild_dir(), 'vm')
        executable = join(vm_link, 'bin', 'native-image')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    if not any(arg.startswith('--help') for arg in args):
        for key, value in javaProperties.items():
            args.append("-D" + key + "=" + value)

    mx.run([executable, '-H:CLibraryPath=' + clibrary_libpath()] + args, **kwargs)

@mx.command(suite.name, 'native-image-configure')
def native_image_configure_on_jvm(args, **kwargs):
    config = graalvm_jvm_config()
    _ensure_vm_built(config)
    if mx.is_windows():
        executable = vm_executable_path('native-image-configure', config)
    else:
        vm_link = join(svmbuild_dir(), 'vm')
        executable = join(vm_link, 'bin', 'native-image-configure')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")
    mx.run([executable] + args, **kwargs)


@mx.command(suite.name, 'native-unittest')
def native_unittest(args):
    """builds a native image of JUnit tests and runs them."""
    native_image_context_run(_native_unittest, args, build_if_missing=True)


@mx.command(suite.name, 'maven-plugin-install')
def maven_plugin_install(args):
    parser = ArgumentParser(prog='mx maven-plugin-install')
    parser.add_argument("--deploy-dependencies", action='store_true', help="This will deploy all the artifacts from all suites before building and deploying the plugin")
    parser.add_argument('--licenses', help='Comma-separated list of licenses that are cleared for upload. Only used if no url is given. Otherwise licenses are looked up in suite.py')
    parser.add_argument('--gpg', action='store_true', help='Sign files with gpg before deploying')
    parser.add_argument('--gpg-keyid', help='GPG keyid to use when signing files (implies --gpg)', default=None)
    parser.add_argument('repository_id', metavar='repository-id', nargs='?', action='store', help='Repository ID used for binary deploy. If none is given, mavens local repository is used instead.')
    parser.add_argument('url', metavar='repository-url', nargs='?', action='store', help='Repository URL used for binary deploy. If no url is given, the repository-id is looked up in suite.py')
    parsed = parser.parse_args(args)

    if not suite.isSourceSuite():
        raise mx.abort("maven-plugin-install requires {} to be a source suite, no a binary suite".format(suite.name))

    if parsed.url:
        if parsed.licenses:
            licenses = mx.get_license(parsed.licenses.split(','))
        elif parsed.repository_id:
            licenses = mx.repository(parsed.repository_id).licenses
        else:
            licenses = []
        repo = mx.Repository(suite, parsed.repository_id, parsed.url, parsed.url, licenses)
    elif parsed.repository_id:
        repo = mx.repository(parsed.repository_id)
    else:
        repo = mx.maven_local_repository()

    svm_version = suite.release_version(snapshotSuffix='SNAPSHOT')

    if parsed.deploy_dependencies:
        deploy_args = [
            '--suppress-javadoc',
            '--all-distribution-types',
            '--validate=full',
            '--all-suites',
            '--skip=GRAALVM_*_JAVA*',  # do not deploy GraalVM distributions
        ]
        if parsed.licenses:
            deploy_args += ["--licenses", parsed.licenses]
        if parsed.gpg:
            deploy_args += ["--gpg"]
        if parsed.gpg_keyid:
            deploy_args += ["--gpg-keyid", parsed.gpg_keyid]
        if parsed.repository_id:
            deploy_args += [parsed.repository_id]
            if parsed.url:
                deploy_args += [parsed.url]
        suites = set()

        def collect_imports(s):
            if s.name not in suites:
                suites.add(s.name)
                s.visit_imports(visitor)

        def visitor(_, suite_import):
            collect_imports(mx.suite(suite_import.name))

        collect_imports(suite)
        new_env = os.environ.copy()
        if 'DYNAMIC_IMPORTS' in new_env:
            del new_env['DYNAMIC_IMPORTS']
        if 'MX_ENV_PATH' in new_env:
            del new_env['MX_ENV_PATH']
        mx.run_mx(['--suite=' + s for s in suites] + ['maven-deploy'] + deploy_args, suite, env=new_env)

    deploy_native_image_maven_plugin(svm_version, repo, parsed.gpg, parsed.gpg_keyid)

    success_message = [
        '',
        'Use the following plugin snippet to enable native-image building for your maven project:',
        '',
        '<plugin>',
        '    <groupId>org.graalvm.nativeimage</groupId>',
        '    <artifactId>native-image-maven-plugin</artifactId>',
        '    <version>' + svm_version + '</version>',
        '    <executions>',
        '        <execution>',
        '            <goals>',
        '                <goal>native-image</goal>',
        '            </goals>',
        '            <phase>package</phase>',
        '        </execution>',
        '    </executions>',
        '</plugin>',
        '',
        ]
    mx.log('\n'.join(success_message))

@mx.command(suite.name, 'maven-plugin-test')
def maven_plugin_test(args):
    # Create native-image-maven-plugin-test pom with correct version info from template
    proj_dir = join(suite.dir, 'src', 'native-image-maven-plugin-test')
    svm_version = suite.release_version(snapshotSuffix='SNAPSHOT')
    pom_from_template(proj_dir, svm_version)
    # Build native image with native-image-maven-plugin
    env = os.environ.copy()
    maven_opts = env.get('MAVEN_OPTS', '').split()
    if not svm_java8():
        # On Java 9+ without native-image executable the plugin needs access to jdk.internal.module
        maven_opts.append('-XX:+UnlockExperimentalVMOptions')
        maven_opts.append('-XX:+EnableJVMCI')
        maven_opts.append('--add-exports=java.base/jdk.internal.module=ALL-UNNAMED')
    env['MAVEN_OPTS'] = ' '.join(maven_opts)
    mx.run_maven(['-e', 'package'], cwd=proj_dir, env=env)
    mx.run([join(proj_dir, 'target', 'com.oracle.substratevm.nativeimagemojotest')])


@mx.command(suite, 'javac-image', '[image-options]')
def javac_image(args):
    """builds a javac image"""
    parser = ArgumentParser(prog='mx javac-image')
    all_args = ['--output-path']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    output_path = unmask(parsed.output_path)[0]
    native_image_context_run(
        lambda native_image, command_args:
            _javac_image(native_image, output_path, command_args), unmask(parsed.image_args),
        build_if_missing=True
    )

if is_musl_supported():
    doc_string = "Runs a musl based Hello World static native-image with custom build arguments."
    @mx.command(suite.name, command_name='muslhelloworld', usage_msg='[options]', doc_function=lambda: doc_string)
    def musl_helloworld(args, config=None):
        if not is_musl_gcc_wrapper_on_path():
            mx.abort('musl-gcc wrapper not detected on path. Cannot run musl helloworld. Please consult substratevm/STATIC-IMAGES.md')
        final_args = ['--static', '--libc=musl'] + args
        run_helloworld_command(final_args, config, 'muslhelloworld')
