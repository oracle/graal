#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import re
import tempfile
from glob import glob
from contextlib import contextmanager
from distutils.dir_util import mkpath, remove_tree  # pylint: disable=no-name-in-module
from os.path import join, exists, dirname, relpath
import pipes
from argparse import ArgumentParser
import fnmatch
import collections

import mx
import mx_compiler
import mx_gate
import mx_unittest
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_javamodules
import mx_subst
import mx_substratevm_benchmark  # pylint: disable=unused-import
from mx_compiler import GraalArchiveParticipant
from mx_gate import Task
from mx_unittest import _run_tests, _VMLauncher

import sys

if sys.version_info[0] < 3:
    from StringIO import StringIO
else:
    from io import StringIO

suite = mx.suite('substratevm')
svmSuites = [suite]

def get_jdk():
    return mx.get_jdk(tag='default')

def svm_java8():
    return get_jdk().javaCompliance <= mx.JavaCompliance('1.8')

def graal_compiler_flags():
    version_tag = get_jdk().javaCompliance.value
    compiler_flags = mx.dependency('substratevm:svm-compiler-flags-builder').compute_graal_compiler_flags_map()
    if version_tag not in compiler_flags:
        missing_flags_message = 'Missing graal-compiler-flags for {0}.\n Did you forget to run "mx build"?'
        mx.abort(missing_flags_message.format(version_tag))
    def adjusted_exports(line):
        """
        Turns e.g.
        --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=jdk.internal.vm.compiler,org.graalvm.nativeimage.builder
        into:
        --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=ALL-UNNAMED
        """
        if line.startswith('--add-exports='):
            before, sep, _ = line.rpartition('=')
            return before + sep + 'ALL-UNNAMED'
        else:
            return line
    return [adjusted_exports(line) for line in compiler_flags[version_tag]]

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

def platform_name():
    return mx.get_os() + "-" + mx.get_arch()

def svm_suite():
    return svmSuites[-1]

def svmbuild_dir(suite=None):
    if not suite:
        suite = svm_suite()
    return join(suite.dir, 'svmbuild')


def is_musl_supported():
    jdk = get_jdk()
    if mx.is_linux() and mx.get_arch() == "amd64" and jdk.javaCompliance == '11':
        musl_library_path = join(jdk.home, 'lib', 'static', 'linux-amd64', 'musl')
        return exists(musl_library_path)
    return False


class GraalVMConfig(collections.namedtuple('GraalVMConfig', 'primary_suite_dir, dynamicimports, disable_libpolyglot, force_bash_launchers, skip_libraries, exclude_components, native_images')):
    @classmethod
    def build(cls, primary_suite_dir=None, dynamicimports=None, disable_libpolyglot=True, force_bash_launchers=True, skip_libraries=True,
              exclude_components=None, native_images=None):
        dynamicimports = list(dynamicimports or [])
        for x, _ in mx.get_dynamic_imports():
            if x not in dynamicimports:
                dynamicimports.append(x)
        new_config = cls(primary_suite_dir, tuple(dynamicimports), disable_libpolyglot,
                         force_bash_launchers if isinstance(force_bash_launchers, bool) else tuple(force_bash_launchers),
                         skip_libraries if isinstance(skip_libraries, bool) else tuple(skip_libraries),
                         tuple(exclude_components or ()), tuple(native_images or ()))
        return new_config

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
        if self.native_images:
            args += ['--native-images=' + ','.join(self.native_images)]
        return args


def _run_graalvm_cmd(cmd_args, config, nonZeroIsFatal=True, out=None, err=None, timeout=None, env=None, quiet=False):
    if config:
        config_args = config.mx_args()
        primary_suite_dir = config.primary_suite_dir
    else:
        config_args = []
        if not mx_sdk_vm_impl._jlink_libraries():
            config_args += ['--no-jlinking']
        native_images = mx_sdk_vm_impl._parse_cmd_arg('native_images')
        if native_images:
            config_args += ['--native-images=' + ','.join(native_images)]
        components = mx_sdk_vm_impl._components_include_list()
        if components:
            config_args += ['--components=' + ','.join(c.name for c in components)]
        dynamic_imports = [x for x, _ in mx.get_dynamic_imports()]
        if dynamic_imports:
            config_args += ['--dynamicimports', ','.join(dynamic_imports)]
        primary_suite_dir = None

    args = config_args + cmd_args
    suite = primary_suite_dir or svm_suite().dir
    return mx.run_mx(args, suite=suite, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, env=env, quiet=quiet)


_vm_homes = {}


def _vm_home(config):
    if config not in _vm_homes:
        # get things initialized (e.g., cloning)
        _run_graalvm_cmd(['graalvm-home'], config, out=mx.OutputCapture())
        capture = mx.OutputCapture()
        _run_graalvm_cmd(['graalvm-home'], config, out=capture, quiet=True)
        _vm_homes[config] = capture.data.strip()
    return _vm_homes[config]


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
    'js',
    'build',
    'benchmarktest',
    "nativeimagehelp",
    'muslcbuild',
    'hellomodule'
])


def vm_native_image_path(config=None):
    return vm_executable_path('native-image', config)


def vm_executable_path(executable, config=None):
    if mx.get_os() == 'windows':
        executable += '.cmd'  # links are `.cmd` on windows
    return join(_vm_home(config), 'bin', executable)


def run_musl_basic_tests():
    if is_musl_supported():
        helloworld(['--output-path', svmbuild_dir(), '--static', '--libc=musl'])


@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, native_image_cmd='', config=None, build_if_missing=False):
    common_args = [] if common_args is None else common_args
    base_args = ['--no-fallback', '-H:+EnforceMaxRuntimeCompileMethods']
    base_args += ['-H:Path=' + svmbuild_dir()]
    if mx.get_opts().verbose:
        base_args += ['--verbose']
    if mx.get_opts().very_verbose:
        base_args += ['--verbose']
    if hosted_assertions:
        base_args += native_image_context.hosted_assertions
    if native_image_cmd:
        if not exists(native_image_cmd):
            mx.abort('Given native_image_cmd does not exist')
    else:
        native_image_cmd = vm_native_image_path(config)

    if not exists(native_image_cmd):
        mx.log('Building GraalVM for config ' + str(config) + ' ...')
        _run_graalvm_cmd(['build'], config)
        native_image_cmd = vm_native_image_path(config)
        if not exists(native_image_cmd):
            raise mx.abort('The built GraalVM for config ' + str(config) + ' does not contain a native-image command')

    def _native_image(args, **kwargs):
        mx.run([native_image_cmd] + args, **kwargs)

    def is_launcher(launcher_path):
        with open(launcher_path, 'rb') as fp:
            first_two_bytes = fp.read(2)
            first_two_bytes_launcher = b'::' if mx.is_windows() else b'#!'
            return first_two_bytes == first_two_bytes_launcher
        return False

    if build_if_missing and is_launcher(native_image_cmd):
        mx.log('Building image from launcher ' + native_image_cmd + ' ...')
        verbose_image_build_option = ['--verbose'] if mx.get_opts().verbose else []
        _native_image(verbose_image_build_option + ['--macro:native-image-launcher'])

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

        result = None
        for line in stdoutdata:
            arg = remove_quotes(line.rstrip('\\').strip())
            m = re.match(option, arg)
            if m:
                result = arg[m.end():]

        return result

    def native_image_func(args, **kwargs):
        all_args = base_args + common_args + args
        path = query_native_image(all_args, r'^-H:Path(@[^=]*)?=')
        name = query_native_image(all_args, r'^-H:Name(@[^=]*)?=')
        image = join(path, name)
        _native_image(all_args, **kwargs)
        return image

    yield native_image_func

native_image_context.hosted_assertions = ['-J-ea', '-J-esa']
_native_unittest_features = '--features=com.oracle.svm.test.ImageInfoTest$TestFeature,com.oracle.svm.test.ServiceLoaderTest$TestFeature,com.oracle.svm.test.SecurityServiceTest$TestFeature'

IMAGE_ASSERTION_FLAGS = ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases']


def svm_gate_body(args, tasks):

    with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                javac_image(['--output-path', svmbuild_dir()])
                javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir()))]
                helloworld(['--output-path', svmbuild_dir()] + javac_command)
                helloworld(['--output-path', svmbuild_dir(), '--shared'])  # Build and run helloworld as shared library
                cinterfacetutorial([])
                clinittest([])

    with Task('image demos debuginfo', tasks, tags=[GraalTags.helloworld_debug]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                javac_image(['--output-path', svmbuild_dir(), '-H:GenerateDebugInfo=1'])
                javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir())), '-H:GenerateDebugInfo=1']
                helloworld(['--output-path', svmbuild_dir()] + javac_command)
                helloworld(['--output-path', svmbuild_dir(), '--shared', '-H:GenerateDebugInfo=1'])  # Build and run helloworld as shared library
                cinterfacetutorial(['-H:GenerateDebugInfo=1'])
                clinittest([])

    with Task('image debuginfotest', tasks, tags=[GraalTags.debuginfotest]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                debuginfotest(['--output-path', svmbuild_dir()])

    with Task('native unittests', tasks, tags=[GraalTags.test]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                native_unittests_task()

    with Task('Run Truffle unittests with SVM image', tasks, tags=["svmjunit"]) as t:
        if t:
            truffle_args = ['--build-args', '--macro:truffle',
                                        '-H:MaxRuntimeCompileMethods=5000',
                                        '-H:+TruffleCheckBlackListedMethods',
                                        '--run-args', '--very-verbose', '--enable-timing']

            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                # ContextPreInitializationNativeImageTest can only run with its own image.
                # See class javadoc for details.
                native_unittest(['com.oracle.truffle.api.test.polyglot.ContextPreInitializationNativeImageTest'] + truffle_args)

                # Regular Truffle tests that can run with isolated compilation
                native_unittest(['com.oracle.truffle.api.test.TruffleSafepointTest',
                                 'com.oracle.truffle.api.staticobject.test',
                                 'com.oracle.truffle.api.test.polyglot.ContextPolicyTest'] + truffle_args)

                # White Box Truffle compilation tests that need access to compiler graphs.
                compiler_args = truffle_args + ['-H:-SupportCompileInIsolates']
                native_unittest(['org.graalvm.compiler.truffle.test.ContextLookupCompilationTest'] + compiler_args)

    with Task('Run Truffle NFI unittests with SVM image', tasks, tags=["svmjunit"]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                testlib = mx_subst.path_substitutions.substitute('-Dnative.test.lib=<path:truffle:TRUFFLE_TEST_NATIVE>/<lib:nativetest>')
                isolation_testlib = mx_subst.path_substitutions.substitute('-Dnative.isolation.test.lib=<path:truffle:TRUFFLE_TEST_NATIVE>/<lib:isolationtest>')
                native_unittest_args = ['com.oracle.truffle.nfi.test', '--build-args', '--language:nfi',
                                        '-H:MaxRuntimeCompileMethods=2000',
                                        '-H:+TruffleCheckBlackListedMethods',
                                        '--run-args', testlib, isolation_testlib, '--very-verbose', '--enable-timing']
                native_unittest(native_unittest_args)

    with Task('Musl static hello world and JVMCI version check', tasks, tags=[GraalTags.muslcbuild]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
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
            config = GraalVMConfig.build(primary_suite_dir=join(suite.vc_dir, 'vm'), # Run from `vm` to clone the right revision of `graal-js` if needed
                                         dynamicimports=['/' + svm_suite().name, '/graal-js'])
            with native_image_context(IMAGE_ASSERTION_FLAGS, config=config) as native_image:
                js = build_js(native_image)
                test_run([js, '-e', 'print("hello:" + Array.from(new Array(10), (x,i) => i*i ).join("|"))'], 'hello:0|1|4|9|16|25|36|49|64|81\n')
                test_js(js, [('octane-richards', 1000, 100, 300)])

    with Task('module build demo', tasks, tags=[GraalTags.hellomodule]) as t:
        if t:
            hellomodule([])


def native_unittests_task():
    if mx.is_windows():
        # GR-24075
        mx_unittest.add_global_ignore_glob('com.oracle.svm.test.ProcessPropertiesTest')

    additional_build_args = [
        '-H:AdditionalSecurityProviders=com.oracle.svm.test.SecurityServiceTest$NoOpProvider',
        '-H:AdditionalSecurityServiceTypes=com.oracle.svm.test.SecurityServiceTest$JCACompliantNoOpService'
    ]

    if get_jdk().javaCompliance == '17':
        if mx.is_windows():
            mx_unittest.add_global_ignore_glob('com.oracle.svm.test.SecurityServiceTest')

    native_unittest(['--builder-on-modulepath', '--build-args', _native_unittest_features] + additional_build_args)


def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), '-proc:none'] + (
        ['-bootclasspath', join(mx_compiler.jdk.home, 'jre', 'lib', 'rt.jar')]
        if svm_java8() else
        # We need to set java.home as com.sun.tools.javac.file.Locations.<clinit> can't handle `null`.
        # However, the actual value isn't important because we won't use system classes from JDK jimage,
        # but from JDK jmods that we will pass as app modules.
        ['-Djava.home=', '--system', 'none', '-p', join(mx_compiler.jdk.home, 'jmods')]
    )


def _native_junit(native_image, unittest_args, build_args=None, run_args=None, blacklist=None, whitelist=None, preserve_image=False, builder_on_modulepath=False):
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
    junit_test_dir = junit_native_dir if preserve_image else tempfile.mkdtemp(dir=junit_native_dir)
    try:
        unittest_deps = []
        def dummy_harness(test_deps, vm_launcher, vm_args):
            unittest_deps.extend(test_deps)
        unittest_file = join(junit_test_dir, 'svmjunit.tests')
        _run_tests(unittest_args, dummy_harness, _VMLauncher('dummy_launcher', None, mx_compiler.jdk), ['@Test', '@Parameters'], unittest_file, blacklist, whitelist, None, None)
        if not exists(unittest_file):
            mx.abort('No matching unit tests found. Skip image build and execution.')
        with open(unittest_file, 'r') as f:
            mx.log('Building junit image for matching: ' + ' '.join(l.rstrip() for l in f))
        extra_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk, exclude_names=['substratevm:LIBRARY_SUPPORT'])
        macro_junit = '--macro:junit' + ('' if builder_on_modulepath else 'cp')
        unittest_image = native_image(['-ea', '-esa'] + build_args + extra_image_args + [macro_junit + '=' + unittest_file, '-H:Path=' + junit_test_dir])
        mx.log('Running: ' + ' '.join(map(pipes.quote, [unittest_image] + run_args)))
        mx.run([unittest_image] + run_args)
    finally:
        if not preserve_image:
            remove_tree(junit_test_dir)

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
    all_args = ['--build-args', '--run-args', '--blacklist', '--whitelist', '-p', '--preserve-image', '--builder-on-modulepath']
    cmdline_args = [_mask(arg, all_args) for arg in cmdline_args]
    parser.add_argument(all_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(all_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument('--blacklist', help='run all testcases not specified in <file>', metavar='<file>')
    parser.add_argument('--whitelist', help='run testcases specified in <file> only', metavar='<file>')
    parser.add_argument('-p', '--preserve-image', help='do not delete the generated native image', action='store_true')
    parser.add_argument('--builder-on-modulepath', help='perform image build with builder on module-path', action='store_true')
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

    unittest_args = unmask(pargs.unittest_args) if unmask(pargs.unittest_args) else ['com.oracle.svm.test', 'com.oracle.svm.configure.test']
    builder_on_modulepath = pargs.builder_on_modulepath
    if builder_on_modulepath and svm_java8():
        mx.log('On Java 8, unittests cannot be built with imagebuilder on module-path. Reverting to imagebuilder on classpath.')
        builder_on_modulepath = False
    _native_junit(native_image, unittest_args, unmask(pargs.build_args), unmask(pargs.run_args), blacklist, whitelist, pargs.preserve_image, builder_on_modulepath)


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
    return native_image(['--macro:js-launcher'])

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
        expected_output = [(output + os.linesep).encode()]
        actual_output = []
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
            finally:
                del os.environ[envkey]
                os.close(pin)
                os.close(pout)
        else:
            env = os.environ.copy()
            env[envkey] = output
            def _collector(x):
                actual_output.append(x.encode())
                mx.log(x)
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
                         '-g',
                         '-H:-OmitInlinedMethodDebugLineInfo',
                         '-H:DebugInfoSourceSearchPath=' + sourcepath,
                         '-H:DebugInfoSourceCacheRoot=' + join(path, 'sources'),
                         'hello.Hello'] + args

    def build_debug_test(extra_args):
        build_args = native_image_args + extra_args
        mx.log('native_image {}'.format(build_args))
        native_image(build_args)

    # build with and without Isolates and check both work

    build_debug_test(['-H:+SpawnIsolates'])
    if mx.get_os() == 'linux' and not build_only:
        os.environ.update({'debuginfotest.isolates' : 'yes'})
        mx.run([os.environ.get('GDB_BIN', 'gdb'), '-ex', 'python "ISOLATES=True"', '-x', join(parent, 'mx.substratevm/testhello.py'), join(path, 'hello.hello')])

    build_debug_test(['-H:-SpawnIsolates'])
    if mx.get_os() == 'linux' and not build_only:
        os.environ.update({'debuginfotest.isolates' : 'no'})
        mx.run([os.environ.get('GDB_BIN', 'gdb'), '-ex', 'python "ISOLATES=False"', '-x', join(parent, 'mx.substratevm/testhello.py'), join(path, 'hello.hello')])

def _javac_image(native_image, path, args=None):
    args = [] if args is None else args
    mkpath(path)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    native_image((['-cp', mx_compiler.jdk.toolsjar] if svm_java8() else []) + ["-H:Path=" + path, "com.sun.tools.javac.Main", "javac",
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

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM',
    short_name='svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['GraalVM compiler', 'Truffle Macro', 'SVM Truffle NFI Support'],
    jar_distributions=['substratevm:LIBRARY_SUPPORT'],
    builder_jar_distributions=[
        'substratevm:SVM',
        'substratevm:OBJECTFILE',
        'substratevm:POINTSTO',
        'substratevm:NATIVE_IMAGE_BASE',
    ],
    support_distributions=['substratevm:SVM_GRAALVM_SUPPORT'],
    stability="earlyadopter",
    jlink=False,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=suite,
    name='SVM Truffle NFI Support',
    short_name='svmnfi',
    license_files=[],
    third_party_license_files=[],
    dir_name='nfi',
    dependencies=['SubstrateVM', 'Truffle NFI'],
    truffle_jars=[],
    builder_jar_distributions=['substratevm:SVM_LIBFFI'],
    support_distributions=['substratevm:SVM_NFI_GRAALVM_SUPPORT'],
    installable=False,
))

def _native_image_launcher_main_class():
    """
    Gets the name of the entry point for running com.oracle.svm.driver.NativeImage.
    """
    return "com.oracle.svm.driver.NativeImage"


def _native_image_launcher_extra_jvm_args():
    """
    Gets the extra JVM args needed for running com.oracle.svm.driver.NativeImage.
    """
    if svm_java8():
        return []
    # Support for running as Java module
    res = []
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(get_jdk()):
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
            use_modules='image' if not svm_java8() else None,
            main_module="org.graalvm.nativeimage.driver",
            destination="bin/<exe:native-image>",
            jar_distributions=["substratevm:SVM_DRIVER"],
            main_class=_native_image_launcher_main_class(),
            build_args=[],
            extra_jvm_args=_native_image_launcher_extra_jvm_args(),
        ),
    ],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            use_modules='image' if not svm_java8() else None,
            destination="<lib:native-image-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:SVM_CONFIGURE',
                'substratevm:JVMTI_AGENT_BASE',
                'substratevm:SVM_AGENT',
            ],
            build_args=[
                '--features=com.oracle.svm.agent.NativeImageAgent$RegistrationFeature',
                '--enable-url-protocols=jar',
            ],
        ),
        mx_sdk_vm.LibraryConfig(
            use_modules='image' if not svm_java8() else None,
            destination="<lib:native-image-diagnostics-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:JVMTI_AGENT_BASE',
                'substratevm:SVM_DIAGNOSTICS_AGENT',
            ],
            build_args=[
                '--features=com.oracle.svm.diagnosticsagent.NativeImageDiagnosticsAgent$RegistrationFeature',
            ],
        ),
    ],
    provided_executables=['bin/<cmd:rebuild-images>'],
    installable=True,
    stability="earlyadopter",
    jlink=False,
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
    stability="earlyadopter",
    jlink=False,
))

# GR-34811
if not (mx.is_windows() or (mx.is_darwin() and mx.get_arch() == "aarch64")):
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
        stability="experimental-earlyadopter",
        jlink=False,
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
        "-Dorg.graalvm.polyglot.nativeapi.libraryPath=${java.home}" + ("/jre" if svm_java8() else "") + "/lib/polyglot/",
        "-H:CStandard=C11",
        "-H:+SpawnIsolates",
    ],
    polyglot_lib_jar_dependencies=[
        "substratevm:POLYGLOT_NATIVE_API",
    ],
    has_polyglot_lib_entrypoints=True,
    stability="earlyadopter",
    jlink=False,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Native Image JUnit',
    short_name='nju',
    dir_name='junit',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=['substratevm:JUNIT_SUPPORT', 'mx:JUNIT_TOOL', 'mx:JUNIT', 'mx:HAMCREST'],
    support_distributions=['substratevm:NATIVE_IMAGE_JUNIT_SUPPORT'],
    jlink=False,
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Native Image JUnit with image-builder on classpath',
    short_name='njucp',
    dir_name='junitcp',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM'],
    jar_distributions=['substratevm:JUNIT_SUPPORT', 'mx:JUNIT_TOOL', 'mx:JUNIT', 'mx:HAMCREST'],
    support_distributions=['substratevm:NATIVE_IMAGE_JUNITCP_SUPPORT'],
    jlink=False,
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
    stability="supported",
    jlink=False,
))

def _native_image_configure_extra_jvm_args():
    if svm_java8():
        return []
    packages = ['jdk.internal.vm.compiler/org.graalvm.compiler.phases.common', 'jdk.internal.vm.ci/jdk.vm.ci.meta', 'jdk.internal.vm.compiler/org.graalvm.compiler.core.common.util']
    args = ['--add-exports=' + packageName + '=ALL-UNNAMED' for packageName in packages]
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(get_jdk()):
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
            use_modules='image' if not svm_java8() else None,
            main_module="org.graalvm.nativeimage.configure",
            destination="bin/<exe:native-image-configure>",
            jar_distributions=["substratevm:SVM_CONFIGURE"],
            main_class="com.oracle.svm.configure.ConfigurationTool",
            build_args=[
                "-H:-ParseRuntimeOptions",
            ],
            extra_jvm_args=_native_image_configure_extra_jvm_args(),
        )
    ],
    jlink=False,
    installable_id='native-image',
    installable=True,
    priority=10,
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
        config=config
    )


@mx.command(suite_name=suite.name, command_name='helloworld', usage_msg='[options]')
def helloworld(args, config=None):
    """
    builds a Hello, World! native image.
    """
    run_helloworld_command(args, config, "helloworld")


@mx.command(suite_name=suite.name, command_name='hellomodule')
def hellomodule(args):
    """
    builds a Hello, World! native image from a Java module.
    """
    if svm_java8():
        mx.abort('Experimental module support requires Java 11+')

    # Build a helloworld Java module with maven
    module_path = []
    proj_dir = join(suite.dir, 'src', 'native-image-module-tests', 'hello.lib')
    mx.run_maven(['-e', 'install'], cwd=proj_dir)
    module_path.append(join(proj_dir, 'target', 'hello-lib-1.0-SNAPSHOT.jar'))
    proj_dir = join(suite.dir, 'src', 'native-image-module-tests', 'hello.app')
    mx.run_maven(['-e', 'install'], cwd=proj_dir)
    module_path.append(join(proj_dir, 'target', 'hello-app-1.0-SNAPSHOT.jar'))
    config = GraalVMConfig.build(native_images=['native-image', 'lib:native-image-agent', 'lib:native-image-diagnostics-agent'])
    with native_image_context(hosted_assertions=False, config=config) as native_image:
        module_path_sep = ';' if mx.is_windows() else ':'
        moduletest_run_args = [
            '-ea',
            '--add-exports=moduletests.hello.lib/hello.privateLib=moduletests.hello.app',
            '--add-opens=moduletests.hello.lib/hello.privateLib2=moduletests.hello.app',
            '-p', module_path_sep.join(module_path), '-m', 'moduletests.hello.app'
        ]
        mx.log('Running module-tests on JVM:')
        build_dir = join(svmbuild_dir(), 'hellomodule')
        mx.run([
            vm_executable_path('java', config),
            # also test if native-image-agent works
            '-agentlib:native-image-agent=config-output-dir=' + join(build_dir, 'config-output-dir-{pid}-{datetime}/'),
            ] + moduletest_run_args)

        # Build module into native image
        mx.log('Building image from java modules: ' + str(module_path))
        built_image = native_image([
            '--verbose', '-H:Path=' + build_dir,
            '--trace-class-initialization=hello.lib.Greeter', # also test native-image-diagnostics-agent
            ] + moduletest_run_args)
        mx.log('Running image ' + built_image + ' built from module:')
        mx.run([built_image])


@mx.command(suite.name, 'cinterfacetutorial', 'Runs the ')
def cinterfacetutorial(args):
    """
    runs all tutorials for the C interface.
    """
    native_image_context_run(_cinterfacetutorial, args)


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
            ['-H:Path=' + build_dir, '-cp', test_cp, '-H:Class=com.oracle.svm.test.clinit.TestClassInitializationMustBeSafeEarly',
             '-H:Features=com.oracle.svm.test.clinit.TestClassInitializationMustBeSafeEarlyFeature',
             '-H:+PrintClassInitialization', '-H:Name=clinittest', '-H:+ReportExceptionStackTraces'] + args)
        mx.run([join(build_dir, 'clinittest')])

        # Check the reports for initialized classes
        def check_class_initialization(classes_file_name):
            classes_file = os.path.join(build_dir, 'reports', classes_file_name)
            wrongly_initialized_lines = []

            def checkLine(line, marker, init_kind, msg, wrongly_initialized_lines):
                if marker + "," in line and not ((init_kind + ",") in line and msg in line):
                    wrongly_initialized_lines += [(line,
                                                   "Classes marked with " + marker + " must have init kind " + init_kind + " and message " + msg)]
            with open(classes_file) as f:
                for line in f:
                    checkLine(line, "MustBeDelayed", "RUN_TIME", "classes are initialized at run time by default", wrongly_initialized_lines)
                    checkLine(line, "MustBeSafeEarly", "BUILD_TIME", "class proven as side-effect free before analysis", wrongly_initialized_lines)
                    checkLine(line, "MustBeSafeLate", "BUILD_TIME", "class proven as side-effect free after analysis", wrongly_initialized_lines)
                if len(wrongly_initialized_lines) > 0:
                    msg = ""
                    for (line, error) in wrongly_initialized_lines:
                        msg += "In line \n" + line + error + "\n"
                    mx.abort("Error in initialization reporting:\n" + msg)

        reports = os.listdir(os.path.join(build_dir, 'reports'))
        all_classes_file = next(report for report in reports if report.startswith('class_initialization_report'))

        check_class_initialization(all_classes_file)

    native_image_context_run(build_and_test_clinittest_image, args)


class SubstrateJvmFuncsFallbacksBuilder(mx.Project):
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        mx.Project.__init__(self, suite, name, None, [], deps, workingSets, suite.dir, theLicense, **kwArgs)

    def getBuildTask(self, args):
        return JvmFuncsFallbacksBuildTask(self, args, 1)

class JvmFuncsFallbacksBuildTask(mx.BuildTask):
    def __init__(self, subject, args, parallelism):
        super(JvmFuncsFallbacksBuildTask, self).__init__(subject, args, parallelism)

        libjvm = mx.dependency('substratevm:com.oracle.svm.native.jvm.' + ('windows' if mx.is_windows() else 'posix'))

        try:
            # Remove any remaining leftover src_gen subdirs in native_project_dir
            native_project_src_gen_dir = join(libjvm.dir, 'src', 'src_gen')
            if exists(native_project_src_gen_dir):
                mx.rmtree(native_project_src_gen_dir)
        except OSError:
            pass

        self.jvm_funcs_path = join(libjvm.dir, 'src', 'JvmFuncs.c')
        self.jvm_fallbacks_path = join(self.subject.get_output_root(), 'src_gen', 'JvmFuncsFallbacks.c')
        self.register_in_libjvm(libjvm)

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

    # Needed because SubstrateJvmFuncsFallbacksBuilder.getBuildTask gets called from mx.clean and mx.build
    registered_in_libjvm = False

    def register_in_libjvm(self, libjvm):
        if not JvmFuncsFallbacksBuildTask.registered_in_libjvm:
            JvmFuncsFallbacksBuildTask.registered_in_libjvm = True
            # Ensure generated JvmFuncsFallbacks.c will be part of the generated libjvm
            rel_jvm_fallbacks_dir = relpath(dirname(self.jvm_fallbacks_path), libjvm.dir)
            libjvm.srcDirs.append(rel_jvm_fallbacks_dir)

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
                new_fallback.write('#include <stdlib.h>\n')
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

def mx_register_dynamic_suite_constituents(register_project, _):
    register_project(SubstrateCompilerFlagsBuilder())

class SubstrateCompilerFlagsBuilder(mx.ArchivableProject):

    flags_build_dependencies = [
        'substratevm:SVM'
    ]

    def __init__(self):
        mx.ArchivableProject.__init__(self, suite, 'svm-compiler-flags-builder', [], None, None)
        self.buildDependencies = list(SubstrateCompilerFlagsBuilder.flags_build_dependencies)

    def config_file(self, ver):
        return 'graal-compiler-flags-' + str(ver) + '.config'

    def result_file_path(self, version):
        return join(self.output_dir(), self.config_file(version))

    def output_dir(self):
        return self.get_output_root()

    def archive_prefix(self):
        return ''

    def _computeResults(self):
        """
        Returns a lazily computed tuple of the paths for the files storing the configuration
        managed by this builder and a bool denoting whether any of the files were updated
        as their paths were computed.
        """
        if not hasattr(self, '.results'):
            graal_compiler_flags_map = self.compute_graal_compiler_flags_map()
            mx.ensure_dir_exists(self.output_dir())
            versions = sorted(graal_compiler_flags_map.keys())
            file_paths = []
            changed = self.config_file_update(self.result_file_path("versions"), versions, file_paths)
            for version in versions:
                changed = self.config_file_update(self.result_file_path(version), graal_compiler_flags_map[version], file_paths) or changed
            setattr(self, '.results', (file_paths, changed))
        return getattr(self, '.results')

    def getResults(self):
        return self._computeResults()[0]

    def getBuildTask(self, args):
        return SubstrateCompilerFlagsBuildTask(self, args)

    def config_file_update(self, file_path, lines, file_paths):
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

        file_paths.append(file_path)
        return changed

    # If renaming or moving this method, please update the error message in
    # com.oracle.svm.driver.NativeImage.BuildConfiguration.getBuilderJavaArgs().
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
            distributions_transitive = mx.classpath_entries(self.buildDependencies)
            required_exports = mx_javamodules.requiredExports(distributions_transitive, get_jdk())
            exports_flags = mx_sdk_vm.AbstractNativeImageConfig.get_add_exports_list(required_exports)
            graal_compiler_flags_map[11].extend(exports_flags)
            # Currently JDK 17 and JDK 11 have the same flags
            graal_compiler_flags_map[17] = graal_compiler_flags_map[11]
            # DO NOT ADD ANY NEW ADD-OPENS OR ADD-EXPORTS HERE!
            #
            # Instead provide the correct requiresConcealed entries in the moduleInfo
            # section of org.graalvm.nativeimage.builder in the substratevm suite.py.

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


class SubstrateCompilerFlagsBuildTask(mx.ArchivableBuildTask):
    def __init__(self, subject, args):
        mx.ArchivableBuildTask.__init__(self, subject, args, 1)

    def __str__(self):
        return 'Building SVM compiler flags'

    def needsBuild(self, newestInput):
        if self.subject._computeResults()[1]:
            return (True, 'SVM compiler flags configuration changed')
        return (False, None)

    def build(self):
        self.subject._computeResults()

    def clean(self, forBuild=False):
        driver_resources_dir = join(mx.dependency('substratevm:com.oracle.svm.driver').dir, 'resources')
        ancient_config_files = glob(join(driver_resources_dir, 'graal-compiler-flags-*.config'))
        for f in ancient_config_files:
            mx.warn('Removing leftover ' + f)
            os.remove(f)


@mx.command(suite.name, 'native-image')
def native_image_on_jvm(args, **kwargs):
    executable = vm_native_image_path()
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")

    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    if not any(arg.startswith('--help') or arg == '--version' for arg in args):
        for key, value in javaProperties.items():
            args.append("-D" + key + "=" + value)

    mx.run([executable] + args, **kwargs)


@mx.command(suite.name, 'native-image-configure')
def native_image_configure_on_jvm(args, **kwargs):
    executable = vm_executable_path('native-image-configure')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")
    mx.run([executable] + args, **kwargs)


@mx.command(suite.name, 'native-unittest')
def native_unittest(args):
    """builds a native image of JUnit tests and runs them."""
    native_image_context_run(_native_unittest, args)


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
            _javac_image(native_image, output_path, command_args), unmask(parsed.image_args)
    )

if is_musl_supported():
    doc_string = "Runs a musl based Hello World static native-image with custom build arguments."
    @mx.command(suite.name, command_name='muslhelloworld', usage_msg='[options]', doc_function=lambda: doc_string)
    def musl_helloworld(args, config=None):
        final_args = ['--static', '--libc=musl'] + args
        run_helloworld_command(final_args, config, 'muslhelloworld')
