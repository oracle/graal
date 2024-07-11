#
# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import os
import re
import tempfile
from glob import glob
from contextlib import contextmanager
from itertools import islice
from os.path import join, exists, dirname
import pipes
from argparse import ArgumentParser
import fnmatch
import collections
from io import StringIO

import mx
import mx_compiler
import mx_gate
import mx_unittest
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_javamodules
import mx_subst
import mx_util
import mx_substratevm_benchmark  # pylint: disable=unused-import
from mx_compiler import GraalArchiveParticipant
from mx_gate import Task
from mx_sdk_vm_impl import svm_experimental_options
from mx_unittest import _run_tests, _VMLauncher

import sys



suite = mx.suite('substratevm')
svmSuites = [suite]

def get_jdk():
    return mx.get_jdk(tag='default')

def graal_compiler_flags():
    version_tag = get_jdk().javaCompliance.value
    compiler_flags = mx.dependency('substratevm:svm-compiler-flags-builder').compute_graal_compiler_flags_map()
    if str(version_tag) not in compiler_flags:
        missing_flags_message = 'Missing graal-compiler-flags for {0}.\n Did you forget to run "mx build"?'
        mx.abort(missing_flags_message.format(version_tag))
    def adjusted_exports(line):
        """
        Turns e.g.
        --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=jdk.graal.compiler,org.graalvm.nativeimage.builder
        into:
        --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=ALL-UNNAMED
        """
        if line.startswith('--add-exports='):
            before, sep, _ = line.rpartition('=')
            return before + sep + 'ALL-UNNAMED'
        else:
            return line

    return [adjusted_exports(line) for line in compiler_flags[str(version_tag)]]

def classpath(args):
    if not args:
        return [] # safeguard against mx.classpath(None) behaviour

    transitive_excludes = set()
    def include_in_excludes(dep, dep_edge):
        # We need to exclude on the granularity of mx.Project entries so that classpath()
        # can also give us a builder-free classpath if args contains mx.Project entries.
        if dep.isJavaProject() or dep.isDistribution():
            transitive_excludes.add(dep)

    implicit_excludes_deps = [mx.dependency(entry) for entry in mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes]
    mx.walk_deps(implicit_excludes_deps, visit=include_in_excludes)
    cpEntries = mx.classpath_entries(names=args, includeSelf=True, preferProjects=False, excludes=transitive_excludes)
    return mx._entries_to_classpath(cpEntries=cpEntries, resolve=True, includeBootClasspath=False, jdk=mx_compiler.jdk, unique=False, ignoreStripped=False)

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


def build_native_image_agent(native_image):
    agentfile = mx_subst.path_substitutions.substitute('<lib:native-image-agent>')
    agentname = join(svmbuild_dir(), agentfile.rsplit('.', 1)[0])  # remove platform-specific file extension
    native_image(['--macro:native-image-agent-library', '-o', agentname])
    return svmbuild_dir() + '/' + agentfile


class GraalVMConfig(collections.namedtuple('GraalVMConfig', 'primary_suite_dir, dynamicimports, exclude_components, native_images')):
    @classmethod
    def build(cls, primary_suite_dir=None, dynamicimports=None,
              exclude_components=None, native_images=None):
        dynamicimports = list(dynamicimports or [])
        for x, _ in mx.get_dynamic_imports():
            if x not in dynamicimports:
                dynamicimports.append(x)
        new_config = cls(primary_suite_dir, tuple(dynamicimports),
                         tuple(exclude_components or ()), tuple(native_images or ()))
        return new_config

    def mx_args(self):
        args = ['--disable-installables=true']
        if self.dynamicimports:
            args += ['--dynamicimports', ','.join(self.dynamicimports)]
        if self.exclude_components:
            args += ['--exclude-components=' + ','.join(self.exclude_components)]
        if self.native_images:
            args += ['--native-images=' + ','.join(self.native_images)]
        else:
            args += ['--native-images=false']
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
        dynamic_imports = [('/' if subdir else '') + di for di, subdir in mx.get_dynamic_imports()]
        if dynamic_imports:
            config_args += ['--dynamicimports=' + ','.join(dynamic_imports)]
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
    'debuginfotest',
    'native_unittests',
    'build',
    'benchmarktest',
    "nativeimagehelp",
    'hellomodule',
    'condconfig',
    'truffle_unittests',
])

def vm_native_image_path(config=None):
    return vm_executable_path('native-image', config)


def vm_executable_path(executable, config=None):
    if mx.get_os() == 'windows':
        executable += '.cmd'  # links are `.cmd` on windows
    return join(_vm_home(config), 'bin', executable)


def _escape_for_args_file(arg):
    if not (arg.startswith('\\Q') and arg.endswith('\\E')):
        arg = arg.replace('\\', '\\\\')
        if ' ' in arg:
            arg = '\"' + arg + '\"'
    return arg


def _maybe_convert_to_args_file(args):
    total_command_line_args_length = sum([len(arg) for arg in args])
    if total_command_line_args_length < 80:
        # Do not use argument file when total command line length is reasonable,
        # so that both code paths are exercised on all platforms
        return args
    else:
        # Use argument file to avoid exceeding the command line length limit on Windows
        with tempfile.NamedTemporaryFile(delete=False, mode='w', prefix='ni_args_', suffix='.args') as args_file:
            args_file.write('\n'.join([_escape_for_args_file(a) for a in args]))
        return ['@' + args_file.name]


@contextmanager
def native_image_context(common_args=None, hosted_assertions=True, native_image_cmd='', config=None, build_if_missing=False):
    common_args = [] if common_args is None else common_args
    base_args = [
        '--no-fallback',
        '-H:+ReportExceptionStackTraces',
    ] + svm_experimental_options([
        '-H:+EnforceMaxRuntimeCompileMethods',
        '-H:Path=' + svmbuild_dir(),
    ])
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
        return mx.run([native_image_cmd] + _maybe_convert_to_args_file(args), **kwargs)

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

    def query_native_image(all_args):
        stdoutdata = []
        def stdout_collector(x):
            stdoutdata.append(x.rstrip())
        stderrdata = []
        def stderr_collector(x):
            stderrdata.append(x.rstrip())
        exit_code = _native_image(['--dry-run', '--verbose'] + all_args, nonZeroIsFatal=False, out=stdout_collector, err=stderr_collector)
        if exit_code != 0:
            for line in stdoutdata:
                print(line)
            for line in stderrdata:
                print(line)
            mx.abort('Failed to query native-image.')

        def remove_quotes(val):
            if len(val) >= 2 and val.startswith("'") and val.endswith("'"):
                return val[1:-1].replace("\\'", "'")
            else:
                return val

        path_regex = re.compile(r'^-H:Path(@[^=]*)?=')
        name_regex = re.compile(r'^-H:Name(@[^=]*)?=')
        path = name = None
        for line in stdoutdata:
            arg = remove_quotes(line.rstrip('\\').strip())
            path_matcher = path_regex.match(arg)
            if path_matcher:
                path = arg[path_matcher.end():]
            name_matcher = name_regex.match(arg)
            if name_matcher:
                name = arg[name_matcher.end():]

        assert path is not None and name is not None
        return path, name

    def native_image_func(args, **kwargs):
        all_args = base_args + common_args + args
        path, name = query_native_image(all_args)
        image = join(path, name)
        _native_image(all_args, **kwargs)
        return image

    yield native_image_func

native_image_context.hosted_assertions = ['-J-ea', '-J-esa']
_native_unittest_features = '--features=com.oracle.svm.test.ImageInfoTest$TestFeature,com.oracle.svm.test.ServiceLoaderTest$TestFeature,com.oracle.svm.test.SecurityServiceTest$TestFeature,com.oracle.svm.test.ReflectionRegistrationTest$TestFeature'

IMAGE_ASSERTION_FLAGS = svm_experimental_options(['-H:+VerifyGraalGraphs', '-H:+VerifyPhases'])


def image_demo_task(extra_image_args=None, flightrecorder=True):
    image_args = ['--output-path', svmbuild_dir()]
    if extra_image_args is not None:
        image_args += extra_image_args
    javac_image(image_args)
    javac_command = ['--javac-command', ' '.join(javac_image_command(svmbuild_dir()))]
    helloworld(image_args + javac_command)
    if '--static' not in image_args:
        helloworld(image_args + ['--shared'])  # Build and run helloworld as shared library
    if not mx.is_windows() and flightrecorder:
        helloworld(image_args + ['-J-XX:StartFlightRecording=dumponexit=true'])  # Build and run helloworld with FlightRecorder at image build time
    if '--static' not in image_args:
        cinterfacetutorial(extra_image_args)
    clinittest(extra_image_args)


def truffle_args(extra_build_args):
    assert isinstance(extra_build_args, list)
    build_args = [
        '--build-args', '--macro:truffle', '--language:nfi',
        '--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
        '-H:MaxRuntimeCompileMethods=5000',
    ]
    run_args = ['--run-args', '--very-verbose', '--enable-timing']
    return build_args + extra_build_args + run_args


def truffle_unittest_task(extra_build_args=None):
    extra_build_args = extra_build_args or []

    # ContextPreInitializationNativeImageTest can only run with its own image.
    # See class javadoc for details.
    truffle_context_pre_init_unittest_task(extra_build_args)

    # Regular Truffle tests that can run with isolated compilation
    truffle_tests = ['com.oracle.truffle.api.staticobject.test',
                     'com.oracle.truffle.api.test.polyglot.ContextPolicyTest']

    if '-Ob' not in extra_build_args:
        # GR-44492:
        truffle_tests += ['com.oracle.truffle.api.test.TruffleSafepointTest']

    native_unittest(truffle_tests + truffle_args(extra_build_args) + (['-Xss1m'] if '--libc=musl' in extra_build_args else []))

    # White Box Truffle compilation tests that need access to compiler graphs.
    if '-Ob' not in extra_build_args:
        # GR-44492
        native_unittest(['jdk.graal.compiler.truffle.test.ContextLookupCompilationTest'] + truffle_args(extra_build_args + svm_experimental_options(['-H:-SupportCompileInIsolates'])))


def truffle_context_pre_init_unittest_task(extra_build_args):
    native_unittest(['com.oracle.truffle.api.test.polyglot.ContextPreInitializationNativeImageTest'] + truffle_args(extra_build_args))


def svm_gate_body(args, tasks):
    with Task('image demos', tasks, tags=[GraalTags.helloworld]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                image_demo_task(args.extra_image_builder_arguments)
                helloworld(svm_experimental_options(['-H:+RunMainInNewThread']) + args.extra_image_builder_arguments)

    with Task('image debuginfotest', tasks, tags=[GraalTags.debuginfotest]) as t:
        if t:
            if mx.is_windows():
                mx.warn('debuginfotest does not work on Windows')
            else:
                with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                    debuginfotest(['--output-path', svmbuild_dir()] + args.extra_image_builder_arguments)

    with Task('native unittests', tasks, tags=[GraalTags.native_unittests]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                native_unittests_task(args.extra_image_builder_arguments)

    with Task('conditional configuration tests', tasks, tags=[GraalTags.condconfig]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                conditional_config_task(native_image)

    with Task('Run Truffle unittests with SVM image', tasks, tags=[GraalTags.truffle_unittests]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                truffle_unittest_task(args.extra_image_builder_arguments)

    with Task('Run Truffle NFI unittests with SVM image', tasks, tags=[GraalTags.truffle_unittests]) as t:
        if t:
            with native_image_context(IMAGE_ASSERTION_FLAGS) as native_image:
                if '--static' in args.extra_image_builder_arguments:
                    mx.warn('NFI unittests use dlopen and thus do not work with statically linked executables')
                else:
                    testlib = mx_subst.path_substitutions.substitute('-Dnative.test.path=<path:truffle:TRUFFLE_TEST_NATIVE>')
                    native_unittest_args = ['com.oracle.truffle.nfi.test', '--build-args',
                                            '--macro:truffle',
                                            '--language:nfi',
                                            '--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
                                            '-H:MaxRuntimeCompileMethods=2000',] + args.extra_image_builder_arguments + [
                                            '--run-args', testlib, '--very-verbose', '--enable-timing']
                    native_unittest(native_unittest_args)

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

    with Task('module build demo', tasks, tags=[GraalTags.hellomodule]) as t:
        if t:
            hellomodule(args.extra_image_builder_arguments)

    with Task('Validate JSON build info', tasks, tags=[GraalTags.helloworld]) as t:
        if t:
            import json
            try:
                from jsonschema import validate as json_validate
                from jsonschema.exceptions import ValidationError, SchemaError
            except ImportError:
                mx.abort('Unable to import jsonschema')

            json_and_schema_file_pairs = [
                ('build-artifacts.json', 'build-artifacts-schema-v0.9.0.json'),
                ('build-output.json', 'build-output-schema-v0.9.3.json'),
            ]

            build_output_file = join(svmbuild_dir(), 'build-output.json')
            helloworld(['--output-path', svmbuild_dir()] + svm_experimental_options([f'-H:BuildOutputJSONFile={build_output_file}', '-H:+GenerateBuildArtifactsFile']))

            try:
                for json_file, schema_file in json_and_schema_file_pairs:
                    with open(join(svmbuild_dir(), json_file)) as f:
                        json_contents = json.load(f)
                    with open(join(suite.dir, '..', 'docs', 'reference-manual', 'native-image', 'assets', schema_file)) as f:
                        schema_contents = json.load(f)
                    json_validate(json_contents, schema_contents)
            except IOError as e:
                mx.abort(f'Unable to load JSON build info: {e}')
            except ValidationError as e:
                mx.abort(f'Unable to validate JSON build info against the schema: {e}')
            except SchemaError as e:
                mx.abort(f'JSON schema not valid: {e}')


def native_unittests_task(extra_build_args=None):
    if mx.is_windows():
        # GR-24075
        mx_unittest.add_global_ignore_glob('com.oracle.svm.test.ProcessPropertiesTest')

    # add resources that are not in jar but in the separate directory
    cp_entry_name = join(svmbuild_dir(), 'cpEntryDir')
    resources_from_dir = join(cp_entry_name, 'resourcesFromDir')
    simple_dir = join(cp_entry_name, 'simpleDir')

    os.makedirs(cp_entry_name)
    os.makedirs(resources_from_dir)
    os.makedirs(simple_dir)

    for i in range(4):
        with open(join(cp_entry_name, "resourcesFromDir", f'cond-resource{i}.txt'), 'w') as out:
            out.write(f"Conditional file{i}" + '\n')

        with open(join(cp_entry_name, "simpleDir", f'simple-resource{i}.txt'), 'w') as out:
            out.write(f"Simple file{i}" + '\n')

    additional_build_args = svm_experimental_options([
        '-H:AdditionalSecurityProviders=com.oracle.svm.test.SecurityServiceTest$NoOpProvider',
        '-H:AdditionalSecurityServiceTypes=com.oracle.svm.test.SecurityServiceTest$JCACompliantNoOpService',
        '-cp', cp_entry_name
    ])
    if extra_build_args is not None:
        additional_build_args += extra_build_args

    if get_jdk().javaCompliance == '17':
        if mx.is_windows():
            mx_unittest.add_global_ignore_glob('com.oracle.svm.test.SecurityServiceTest')

    native_unittest(['--build-args', _native_unittest_features] + additional_build_args)


def conditional_config_task(native_image):
    agent_path = build_native_image_agent(native_image)
    conditional_config_filter_path = join(svmbuild_dir(), 'conditional-config-filter.json')
    with open(conditional_config_filter_path, 'w') as conditional_config_filter:
        conditional_config_filter.write(
'''
{
   "rules": [
        {"includeClasses": "com.oracle.svm.configure.test.conditionalconfig.**"}
   ]
}
'''
        )

    run_agent_conditional_config_test(agent_path, conditional_config_filter_path)

    run_nic_conditional_config_test(agent_path, conditional_config_filter_path)


def run_nic_conditional_config_test(agent_path, conditional_config_filter_path):
    test_cases = [
        "createConfigPartOne",
        "createConfigPartTwo",
        "createConfigPartThree",
    ]
    config_directories = []
    nic_test_dir = join(svmbuild_dir(), 'nic-cond-config-test')
    if exists(nic_test_dir):
        mx.rmtree(nic_test_dir)
    for test_case in test_cases:
        config_dir = join(nic_test_dir, test_case)
        config_directories.append(config_dir)

        agent_opts = ['config-output-dir=' + config_dir,
                      'experimental-conditional-config-part']
        jvm_unittest(['-agentpath:' + agent_path + '=' + ','.join(agent_opts),
                      '-Dcom.oracle.svm.configure.test.conditionalconfig.PartialConfigurationGenerator.enabled=true',
                      'com.oracle.svm.configure.test.conditionalconfig.PartialConfigurationGenerator#' + test_case])
    config_output_dir = join(nic_test_dir, 'config-output')
    nic_exe = mx.cmd_suffix(join(mx.JDKConfig(home=mx_sdk_vm_impl.graalvm_output()).home, 'bin', 'native-image-configure'))
    nic_command = [nic_exe, 'create-conditional'] \
                  + ['--user-code-filter=' + conditional_config_filter_path] \
                  + ['--input-dir=' + config_dir for config_dir in config_directories] \
                  + ['--output-dir=' + config_output_dir]
    mx.run(nic_command)
    jvm_unittest(
        ['-Dcom.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier.configpath=' + config_output_dir,
         "-Dcom.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier.enabled=true",
         'com.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier'])


def run_agent_conditional_config_test(agent_path, conditional_config_filter_path):
    config_dir = join(svmbuild_dir(), 'cond-config-test-config')
    if exists(config_dir):
        mx.rmtree(config_dir)

    agent_opts = ['config-output-dir=' + config_dir,
                  'experimental-conditional-config-filter-file=' + conditional_config_filter_path]
    # This run generates the configuration from different test cases
    jvm_unittest(['-agentpath:' + agent_path + '=' + ','.join(agent_opts),
                  '-Dcom.oracle.svm.configure.test.conditionalconfig.ConfigurationGenerator.enabled=true',
                  'com.oracle.svm.configure.test.conditionalconfig.ConfigurationGenerator'])
    # This run verifies that the generated configuration matches the expected one
    jvm_unittest(['-Dcom.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier.configpath=' + config_dir,
                  "-Dcom.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier.enabled=true",
                  'com.oracle.svm.configure.test.conditionalconfig.ConfigurationVerifier'])


def javac_image_command(javac_path):
    return [join(javac_path, 'javac'), '-proc:none'] + (
        # We need to set java.home as com.sun.tools.javac.file.Locations.<clinit> can't handle `null`.
        # However, the actual value isn't important because we won't use system classes from JDK jimage,
        # but from JDK jmods that we will pass as app modules.
        ['-Djava.home=', '--system', 'none', '-p', join(mx_compiler.jdk.home, 'jmods')]
    )


# replace with itertools.batched once python 3.12 is supported.
def batched(iterable, n):
    if n < 1:
        raise ValueError('n must be at least one')
    it = iter(iterable)
    while batch := tuple(islice(it, n)):
        yield batch


def _native_junit(native_image, unittest_args, build_args=None, run_args=None, blacklist=None, whitelist=None, preserve_image=False, force_builder_on_cp=False, test_classes_per_run=None):
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
    mx_util.ensure_dir_exists(junit_native_dir)
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
            test_classes = [line.rstrip() for line in f]
            mx.log('Building junit image for matching: ' + ' '.join(test_classes))
        extra_image_args = mx.get_runtime_jvm_args(unittest_deps, jdk=mx_compiler.jdk, exclude_names=mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes)
        macro_junit = '--macro:junit'
        if force_builder_on_cp:
            macro_junit += 'cp'
            custom_env = os.environ.copy()
            custom_env['USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM'] = 'false'
        else:
            custom_env = None
        unittest_image = native_image(['-ea', '-esa'] + build_args + extra_image_args + [macro_junit + '=' + unittest_file] + svm_experimental_options(['-H:Path=' + junit_test_dir]), env=custom_env)
        image_pattern_replacement = unittest_image + ".exe" if mx.is_windows() else unittest_image
        run_args = [arg.replace('${unittest.image}', image_pattern_replacement) for arg in run_args]
        mx.log('Running: ' + ' '.join(map(pipes.quote, [unittest_image] + run_args)))

        if not test_classes_per_run:
            # Run all tests in one go. The default behavior.
            test_classes_per_run = sys.maxsize

        failures = []
        for classes in batched(test_classes, test_classes_per_run):
            ret = mx.run([unittest_image] + run_args + [arg for c in classes for arg in ['--run-explicit', c]], nonZeroIsFatal=False)
            if ret != 0:
                failures.append((ret, classes))
        if len(failures) != 0:
            fail_descs = (f"> Test run of the following classes failed with exit code {ret}: {', '.join(classes)}" for ret, classes in failures)
            mx.log('Some test runs failed:\n' + '\n'.join(fail_descs))
            mx.abort(1)
    finally:
        if not preserve_image:
            mx.rmtree(junit_test_dir)

_mask_str = '$mask$'


def _mask(arg, arg_list):
    if arg in (arg_list + ['-h', '--help', '--']):
        return arg
    else:
        return arg.replace('-', _mask_str)


def unmask(args):
    return [arg.replace(_mask_str, '-') for arg in args]


def _native_unittest(native_image, cmdline_args):
    parser = ArgumentParser(prog='mx native-unittest', description='Run unittests as native image.')
    all_args = ['--build-args', '--run-args', '--blacklist', '--whitelist', '-p', '--preserve-image', '--force-builder-on-cp', '--test-classes-per-run']
    cmdline_args = [_mask(arg, all_args) for arg in cmdline_args]
    parser.add_argument(all_args[0], metavar='ARG', nargs='*', default=[])
    parser.add_argument(all_args[1], metavar='ARG', nargs='*', default=[])
    parser.add_argument('--blacklist', help='run all testcases not specified in <file>', metavar='<file>')
    parser.add_argument('--whitelist', help='run testcases specified in <file> only', metavar='<file>')
    parser.add_argument('-p', '--preserve-image', help='do not delete the generated native image', action='store_true')
    parser.add_argument('--test-classes-per-run', help='run N test classes per image run, instead of all tests at once', nargs=1, type=int)
    parser.add_argument('--force-builder-on-cp', help='force image builder to run on classpath', action='store_true')
    parser.add_argument('unittest_args', metavar='TEST_ARG', nargs='*')
    pargs = parser.parse_args(cmdline_args)

    blacklist = unmask([pargs.blacklist])[0] if pargs.blacklist else None
    whitelist = unmask([pargs.whitelist])[0] if pargs.whitelist else None
    test_classes_per_run = pargs.test_classes_per_run[0] if pargs.test_classes_per_run else None

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
    _native_junit(native_image, unittest_args, unmask(pargs.build_args), unmask(pargs.run_args), blacklist, whitelist, pargs.preserve_image, pargs.force_builder_on_cp, test_classes_per_run)


def jvm_unittest(args):
    return mx_unittest.unittest(['--suite', 'substratevm'] + args)


def js_image_test(jslib, bench_location, name, warmup_iterations, iterations, timeout=None, bin_args=None):
    bin_args = bin_args if bin_args is not None else []
    jsruncmd = [get_js_launcher(jslib)] + bin_args + [join(bench_location, 'harness.js'), '--', join(bench_location, name + '.js'),
                                      '--', '--warmup-time=' + str(15_000),
                                      '--warmup-iterations=' + str(warmup_iterations),
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

def build_js_lib(native_image):
    return mx.add_lib_suffix(native_image(['--macro:jsvm-library']))

def get_js_launcher(jslib):
    return os.path.join(os.path.dirname(jslib), "..", "bin", "js")

def test_js(js, benchmarks, bin_args=None):
    bench_location = join(suite.dir, '..', '..', 'js-benchmarks')
    for benchmark_name, warmup_iterations, iterations, timeout in benchmarks:
        js_image_test(js, bench_location, benchmark_name, warmup_iterations, iterations, timeout, bin_args=bin_args)

def test_run(cmds, expected_stdout, timeout=10, env=None):
    stdoutdata = []
    def stdout_collector(x):
        stdoutdata.append(x)
        mx.log(x.rstrip())
    stderrdata = []
    def stderr_collector(x):
        stderrdata.append(x)
        mx.warn(x.rstrip())
    returncode = mx.run(cmds, out=stdout_collector, err=stderr_collector, nonZeroIsFatal=False, timeout=timeout, env=env)
    if ''.join(stdoutdata) != expected_stdout:
        mx.abort('Error: stdout does not match expected_stdout')
    return (returncode, stdoutdata, stderrdata)

mx_gate.add_gate_runner(suite, svm_gate_body)
mx_gate.add_gate_argument('--extra-image-builder-arguments', action=mx_compiler.ShellEscapedStringAction, help='adds image builder arguments to gate tasks where applicable', default=[])


def _cinterfacetutorial(native_image, args=None):
    """Build and run the tutorial for the C interface"""

    args = [] if args is None else args
    tutorial_proj = mx.dependency('com.oracle.svm.tutorial')
    c_source_dir = join(tutorial_proj.dir, 'native')
    build_dir = join(svmbuild_dir(), tutorial_proj.name, 'build')

    # clean / create output directory
    if exists(build_dir):
        mx.rmtree(build_dir)
    mx_util.ensure_dir_exists(build_dir)

    # Build the shared library from Java code
    native_image(['--shared', '-o', join(build_dir, 'libcinterfacetutorial'), '-Dcom.oracle.svm.tutorial.headerfile=' + join(c_source_dir, 'mydata.h'),
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


_helloworld_variants = {
    'traditional': '''
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println(System.getenv("%s"));
    }
}
''',
    'noArgs': '''
// requires JDK 21 and --enable-preview
public class HelloWorld {
    static void main() {
        System.out.println(System.getenv("%s"));
    }
}
''',
    'instance': '''
// requires JDK 21 and --enable-preview
class HelloWorld {
    void main(String[] args) {
        System.out.println(System.getenv("%s"));
    }
}
''',
    'instanceNoArgs': '''
// requires JDK 21 and --enable-preview
class HelloWorld {
    void main() {
        System.out.println(System.getenv("%s"));
    }
}
''',
    'unnamedClass': '''
// requires JDK 21 and javac --enable-preview --source 21 and native-image --enable-preview
void main() {
    System.out.println(System.getenv("%s"));
}
''',
}


def _helloworld(native_image, javac_command, path, build_only, args, variant=list(_helloworld_variants.keys())[0]):
    mx_util.ensure_dir_exists(path)
    hello_file = os.path.join(path, 'HelloWorld.java')
    envkey = 'HELLO_WORLD_MESSAGE'
    output = 'Hello from native-image!'
    with open(hello_file, 'w') as fp:
        fp.write(_helloworld_variants[variant] % envkey)
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

    binary_path = join(path, "helloworld")
    native_image(["--native-image-info", "-o", binary_path,] +
                 svm_experimental_options(['-H:+VerifyNamingConventions']) +
                 ['-cp', path, 'HelloWorld'] + args)

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
                argc = 1
                argv = (ctypes.c_char_p * argc)(b'dummy')
                lib.run_main(argc, argv)  # call run_main of shared lib
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
            mx.run([binary_path], out=_collector, env=env)

        if actual_output != expected_output:
            raise Exception('Unexpected output: ' + str(actual_output) + "  !=  " + str(expected_output))

def _debuginfotest(native_image, path, build_only, with_isolates_only, args):
    mx.log("path=%s"%path)
    sourcepath = mx.project('com.oracle.svm.test').source_dirs()[0]
    mx.log("sourcepath=%s"%sourcepath)
    sourcecache = join(path, 'sources')
    mx.log("sourcecache=%s"%sourcecache)
    # the header file for foreign types resides at the root of the
    # com.oracle.svm.test source tree
    cincludepath = sourcepath
    javaProperties = {}
    for dist in suite.dists:
        if isinstance(dist, mx.ClasspathDependency):
            for cpEntry in mx.classpath_entries(dist):
                if hasattr(cpEntry, "getJavaProperties"):
                    for key, value in cpEntry.getJavaProperties().items():
                        javaProperties[key] = value
    for key, value in javaProperties.items():
        args.append("-D" + key + "=" + value)

    # set property controlling inclusion of foreign struct header
    args.append("-DbuildDebugInfoTestExample=true")

    native_image_args = [
        '--native-compiler-options=-I' + cincludepath,
        '-H:CLibraryPath=' + sourcepath,
        '--native-image-info',
        '-cp', classpath('com.oracle.svm.test'),
        '-Djdk.graal.LogFile=graal.log',
        '-g',
    ] + svm_experimental_options([
        '-H:+VerifyNamingConventions',
        '-H:+SourceLevelDebug',
        '-H:DebugInfoSourceSearchPath=' + sourcepath,
    ]) + args

    def build_debug_test(variant_name, image_name, extra_args):
        per_build_path = join(path, variant_name)
        mx_util.ensure_dir_exists(per_build_path)
        build_args = native_image_args + extra_args + [
            '-o', join(per_build_path, image_name)
        ]
        mx.log('native_image {}'.format(build_args))
        return native_image(build_args)

    # build with and without Isolates and check both work
    if '--libc=musl' in args:
        os.environ.update({'debuginfotest_musl' : 'yes'})

    gdb_utils_py = join(suite.dir, 'mx.substratevm', 'gdb_utils.py')
    testhello_py = join(suite.dir, 'mx.substratevm', 'testhello.py')
    testhello_args = [
        # We do not want to step into class initializer, so initialize everything at build time.
        '--initialize-at-build-time=hello',
        'hello.Hello'
    ]
    if mx.get_os() == 'linux' and not build_only:
        os.environ.update({'debuginfotest_arch' : mx.get_arch()})

    if not with_isolates_only:
        hello_binary = build_debug_test('isolates_off', 'hello_image', testhello_args + svm_experimental_options(['-H:-SpawnIsolates']))
        if mx.get_os() == 'linux' and not build_only:
            os.environ.update({'debuginfotest_isolates' : 'no'})
            mx.run([os.environ.get('GDB_BIN', 'gdb'), '-ex', 'python "ISOLATES=False"', '-x', gdb_utils_py, '-x', testhello_py, hello_binary])

    hello_binary = build_debug_test('isolates_on', 'hello_image', testhello_args + svm_experimental_options(['-H:+SpawnIsolates']))
    if mx.get_os() == 'linux' and not build_only:
        os.environ.update({'debuginfotest_isolates' : 'yes'})
        mx.run([os.environ.get('GDB_BIN', 'gdb'), '-ex', 'python "ISOLATES=True"', '-x', gdb_utils_py, '-x', testhello_py, hello_binary])


def _gdbdebughelperstest(native_image, path, with_isolates_only, build_only, test_only, args):
    test_proj = mx.dependency('com.oracle.svm.test')
    test_source_path = test_proj.source_dirs()[0]
    tutorial_proj = mx.dependency('com.oracle.svm.tutorial')
    tutorial_c_source_dir = join(tutorial_proj.dir, 'native')
    tutorial_source_path = tutorial_proj.source_dirs()[0]

    gdbdebughelpers_py = join(mx.dependency('com.oracle.svm.hosted.image.debug').output_dir(), 'gdb-debughelpers.py')

    test_python_source_dir = join(test_source_path, 'com', 'oracle', 'svm', 'test', 'debug', 'helper')
    test_pretty_printer_py = join(test_python_source_dir, 'test_pretty_printer.py')
    test_cinterface_py = join(test_python_source_dir, 'test_cinterface.py')
    test_class_loader_py = join(test_python_source_dir, 'test_class_loader.py')
    test_settings_py = join(test_python_source_dir, 'test_settings.py')
    test_svm_util_py = join(test_python_source_dir, 'test_svm_util.py')

    test_pretty_printer_args = [
        '-cp', classpath('com.oracle.svm.test'),
        # We do not want to step into class initializer, so initialize everything at build time.
        '--initialize-at-build-time=com.oracle.svm.test.debug.helper',
        'com.oracle.svm.test.debug.helper.PrettyPrinterTest'
    ]
    test_cinterface_args = [
        '--shared',
        '-Dcom.oracle.svm.tutorial.headerfile=' + join(tutorial_c_source_dir, 'mydata.h'),
        '-cp', tutorial_proj.output_dir()
    ]
    test_class_loader_args = [
        '-cp', classpath('com.oracle.svm.test'),
        '-Dsvm.test.missing.classes=' + classpath('com.oracle.svm.test.missing.classes'),
        '--initialize-at-build-time=com.oracle.svm.test.debug.helper',
        # We need the static initializer of the ClassLoaderTest to run at image build time
        '--initialize-at-build-time=com.oracle.svm.test.missing.classes',
        'com.oracle.svm.test.debug.helper.ClassLoaderTest'
    ]

    gdb_args = [
        os.environ.get('GDB_BIN', 'gdb'),
        '--nx',
        '-q',  # do not print the introductory and copyright messages
        '-iex', 'set logging overwrite on',
        '-iex', 'set logging redirect on',
        '-iex', 'set logging enabled on',
    ]

    def run_debug_test(image_name: str, testfile: str, source_path: str, with_isolates: bool = True,
                       build_cinterfacetutorial: bool = False, extra_args: list[str] = None, skip_build: bool = False):
        extra_args = [] if extra_args is None else extra_args
        build_dir = join(path, image_name + ("" if with_isolates else "_no_isolates"))

        if not test_only and not skip_build:
            # clean / create output directory
            if exists(build_dir):
                mx.rmtree(build_dir)
            mx.ensure_dir_exists(build_dir)

            build_args = args + [
                '-H:CLibraryPath=' + source_path,
                '--native-image-info',
                '-Djdk.graal.LogFile=graal.log',
                '-g', '-O0',
            ] + svm_experimental_options([
                '-H:+VerifyNamingConventions',
                '-H:+SourceLevelDebug',
                '-H:+IncludeDebugHelperMethods',
                '-H:DebugInfoSourceSearchPath=' + source_path,
            ]) + extra_args

            if not with_isolates:
                build_args += svm_experimental_options(['-H:-SpawnIsolates'])

            if build_cinterfacetutorial:
                build_args += ['-o', join(build_dir, 'lib' + image_name)]
            else:
                build_args += ['-o', join(build_dir, image_name)]

            mx.log(f"native_image {' '.join(build_args)}")
            native_image(build_args)

            if build_cinterfacetutorial:
                if mx.get_os() != 'windows':
                    c_command = ['cc', '-g', join(tutorial_c_source_dir, 'cinterfacetutorial.c'),
                                 '-I.', '-L.', '-lcinterfacetutorial',
                                 '-ldl', '-Wl,-rpath,' + build_dir,
                                 '-o', 'cinterfacetutorial']

                else:
                    c_command = ['cl', '-MD', join(tutorial_c_source_dir, 'cinterfacetutorial.c'), '-I.',
                                 'libcinterfacetutorial.lib']
                mx.log(' '.join(c_command))
                mx.run(c_command, cwd=build_dir)
        if not build_only and mx.get_os() == 'linux':
            # copying the most recent version of gdb-debughelpers.py (even if the native image was not built)
            mx.log(f"Copying {gdbdebughelpers_py} to {build_dir}")
            mx.copyfile(gdbdebughelpers_py, join(build_dir, 'gdb-debughelpers.py'))

            gdb_command = gdb_args + [
                '-iex', f"set auto-load safe-path {join(build_dir, 'gdb-debughelpers.py')}",
                '-x', testfile, join(build_dir, image_name)
            ]
            mx.log(' '.join(gdb_command))
            # unittest may result in different exit code, nonZeroIsFatal ensures that we can go on with other test
            mx.run(gdb_command, cwd=build_dir, nonZeroIsFatal=False)

    if not with_isolates_only:
        run_debug_test('prettyPrinterTest', test_pretty_printer_py, test_source_path, False,
                       extra_args=test_pretty_printer_args)
    run_debug_test('prettyPrinterTest', test_pretty_printer_py, test_source_path, extra_args=test_pretty_printer_args)
    run_debug_test('prettyPrinterTest', test_settings_py, test_source_path, extra_args=test_pretty_printer_args, skip_build=True)
    run_debug_test('prettyPrinterTest', test_svm_util_py, test_source_path, extra_args=test_pretty_printer_args, skip_build=True)

    run_debug_test('cinterfacetutorial', test_cinterface_py, tutorial_source_path, build_cinterfacetutorial=True,
                   extra_args=test_cinterface_args)

    run_debug_test('classLoaderTest', test_class_loader_py, test_source_path, extra_args=test_class_loader_args)



def _javac_image(native_image, path, args=None):
    args = [] if args is None else args
    mx_util.ensure_dir_exists(path)

    # Build an image for the javac compiler, so that we test and gate-check javac all the time.
    # Dynamic class loading code is reachable (used by the annotation processor), so -H:+ReportUnsupportedElementsAtRuntime is a necessary option
    native_image(["-o", join(path, "javac"), "com.sun.tools.javac.Main", "javac"] + svm_experimental_options([
                  "-H:+ReportUnsupportedElementsAtRuntime", "-H:+AllowIncompleteClasspath",
                  "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.version"]) + args)


orig_command_benchmark = mx.command_function('benchmark')


@mx.command(suite.name, 'benchmark')
def benchmark(args):
    # if '--jsvm=substratevm' in args:
    #     truffle_language_ensure('js')
    return orig_command_benchmark(args)

def mx_post_parse_cmd_line(opts):
    for dist in suite.dists:
        if dist.isJARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith('_TEST')))

def native_image_context_run(func, func_args=None, config=None, build_if_missing=False):
    func_args = [] if func_args is None else func_args
    with native_image_context(config=config, build_if_missing=build_if_missing) as native_image:
        func(native_image, func_args)

svm = mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM',
    short_name='svm',
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    # Use short name for Truffle Runtime SVM to select by priority
    dependencies=['GraalVM compiler', 'SubstrateVM Static Libraries', 'Graal SDK Native Image', 'svmt'],
    jar_distributions=['substratevm:LIBRARY_SUPPORT'],
    builder_jar_distributions=[
        'substratevm:SVM',
        'substratevm:OBJECTFILE',
        'substratevm:POINTSTO',
        'substratevm:NATIVE_IMAGE_BASE',
    ] + (['substratevm:SVM_FOREIGN'] if mx_sdk_vm.base_jdk().javaCompliance >= '22' else []),
    support_distributions=['substratevm:SVM_GRAALVM_SUPPORT'],
    extra_native_targets=['linux-default-glibc', 'linux-default-musl'] if mx.is_linux() and not mx.get_arch() == 'riscv64' else None,
    stability="earlyadopter",
    jlink=False,
    installable=False,
)
mx_sdk_vm.register_graalvm_component(svm)

svm_nfi = mx_sdk_vm.GraalVmLanguage(
    suite=suite,
    name='SVM Truffle NFI Support',
    short_name='svmnfi',
    dir_name='nfi',
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM', 'Truffle NFI'],
    truffle_jars=[],
    builder_jar_distributions=[],
    support_distributions=['substratevm:SVM_NFI_GRAALVM_SUPPORT'],
    installable=False,
)
mx_sdk_vm.register_graalvm_component(svm_nfi)

svm_static_libs = mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='SubstrateVM Static Libraries',
    short_name='svmsl',
    dir_name=False,
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    support_distributions=['substratevm:SVM_STATIC_LIBRARIES_SUPPORT'],
    installable=False,
)
mx_sdk_vm.register_graalvm_component(svm_static_libs)

def _native_image_launcher_main_class():
    """
    Gets the name of the entry point for running com.oracle.svm.driver.NativeImage.
    """
    return "com.oracle.svm.driver.NativeImage"


def _native_image_launcher_extra_jvm_args():
    """
    Gets the extra JVM args needed for running com.oracle.svm.driver.NativeImage.
    """
    # Support for running as Java module
    res = [f'-XX:{max_heap_size_flag}']
    if not mx_sdk_vm.jdk_enables_jvmci_by_default(get_jdk()):
        res.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'])
    return res

driver_build_args = [
    '--features=com.oracle.svm.driver.APIOptionFeature',
    '--initialize-at-build-time=com.oracle.svm.driver',
    '--link-at-build-time=com.oracle.svm.driver,com.oracle.svm.driver.metainf',
]

max_heap_size_flag = f"MaxHeapSize={round(0.8 * 256 * 1024 * 1024)}" # 80% of 256MB

driver_exe_build_args = driver_build_args + svm_experimental_options([
    '-H:+AllowJRTFileSystem',
    '-H:IncludeResources=com/oracle/svm/driver/launcher/.*',
    '-H:-ParseRuntimeOptions',
    f'-R:{max_heap_size_flag}',
])

additional_ni_dependencies = []

native_image = mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image',
    short_name='ni',
    dir_name='svm',
    installable_id='native-image',
    license_files=[],
    third_party_license_files=[],
    dependencies=['SubstrateVM', 'nil'] + additional_ni_dependencies,
    provided_executables=[],
    support_distributions=[],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            use_modules='image',
            main_module="org.graalvm.nativeimage.driver",
            destination="bin/<exe:native-image>",
            jar_distributions=["substratevm:SVM_DRIVER"],
            main_class=_native_image_launcher_main_class(),
            build_args=driver_exe_build_args,
            extra_jvm_args=_native_image_launcher_extra_jvm_args(),
            home_finder=False,
        ),
    ],
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            use_modules='image',
            destination="<lib:native-image-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:SVM_CONFIGURE',
                'substratevm:JVMTI_AGENT_BASE',
                'substratevm:SVM_AGENT',
            ],
            build_args=driver_build_args + [
                '--features=com.oracle.svm.agent.NativeImageAgent$RegistrationFeature',
                '--enable-url-protocols=jar',
            ],
            headers=False,
            home_finder=False,
        ),
        mx_sdk_vm.LibraryConfig(
            use_modules='image',
            destination="<lib:native-image-diagnostics-agent>",
            jvm_library=True,
            jar_distributions=[
                'substratevm:JVMTI_AGENT_BASE',
                'substratevm:SVM_DIAGNOSTICS_AGENT',
            ],
            build_args=driver_build_args + [
                '--features=com.oracle.svm.diagnosticsagent.NativeImageDiagnosticsAgent$RegistrationFeature',
            ],
            headers=False,
            home_finder=False,
        ),
    ],
    installable=True,
    stability="earlyadopter",
    jlink=False,
)
mx_sdk_vm.register_graalvm_component(native_image)

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
    installable=False,
    priority=1,
    stability="earlyadopter",
    jlink=False,
))

ce_llvm_backend = mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Native Image LLVM Backend',
    short_name='svml',
    dir_name='svm',
    installable_id='native-image-llvm-backend',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'SubstrateVM',
        'LLVM.org toolchain',
    ],
    builder_jar_distributions=[
        'substratevm:SVM_LLVM',
        'substratevm:LLVM_WRAPPER_SHADOWED',
        'substratevm:JAVACPP_SHADOWED',
        'substratevm:LLVM_PLATFORM_SPECIFIC_SHADOWED',
        'substratevm:JAVACPP_PLATFORM_SPECIFIC_SHADOWED',
    ],
    stability="experimental-earlyadopter",
    installable=True,
    extra_installable_qualifiers=['ce'],
    jlink=False,
)
# GR-34811
llvm_supported = not (mx.is_windows() or (mx.is_darwin() and mx.get_arch() == "aarch64"))
if llvm_supported:
    mx_sdk_vm.register_graalvm_component(ce_llvm_backend)

# Legacy Truffle Macro
mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Truffle Macro',
    short_name='tflm',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=['tfl'],
    support_distributions=['substratevm:TRUFFLE_GRAALVM_SUPPORT'],
    stability="supported",
))

# Truffle Unchained SVM Macro
mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVMSvmMacro(
    suite=suite,
    name='Truffle SVM Macro',
    short_name='tflsm',
    dir_name='truffle-svm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['svmt'],
    priority=0,
    support_distributions=['substratevm:TRUFFLE_SVM_GRAALVM_SUPPORT', 'substratevm:SVM_TRUFFLE_RUNTIME_GRAALVM_SUPPORT'],
    stability="supported",
))

truffle_runtime_svm = mx_sdk_vm.GraalVmTruffleLibrary(
    suite=suite,
    name='Truffle Runtime SVM',
    short_name='svmt',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    builder_jar_distributions=[
        'substratevm:TRUFFLE_RUNTIME_SVM',
    ],
    support_distributions=[],
    stability="supported",
    jlink=False,
)
mx_sdk_vm.register_graalvm_component(truffle_runtime_svm)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='Polyglot Native API',
    short_name='polynative',
    dir_name='polyglot',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=['substratevm:POLYGLOT_NATIVE_API'],
    support_distributions=[
        "substratevm:POLYGLOT_NATIVE_API_HEADERS",
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

libgraal_jar_distributions = [
    'sdk:NATIVEBRIDGE',
    'sdk:JNIUTILS',
    'substratevm:GRAAL_HOTSPOT_LIBRARY']

def allow_build_path_in_libgraal():
    """
    Determines if the ALLOW_ABSOLUTE_PATHS_IN_OUTPUT env var is any other value than ``false``.
    """
    return mx.get_env('ALLOW_ABSOLUTE_PATHS_IN_OUTPUT', None) != 'false'

def prevent_build_path_in_libgraal():
    """
    If `allow_build_path_in_libgraal() == False`, returns linker
    options to prevent the build path from showing up in a string in libgraal.
    """
    if not allow_build_path_in_libgraal():
        if mx.is_linux():
            return ['-H:NativeLinkerOption=-Wl,-soname=libjvmcicompiler.so']
        if mx.is_darwin():
            return [
                '-H:NativeLinkerOption=-Wl,-install_name,@rpath/libjvmcicompiler.dylib',

                # native-image doesn't support generating debug info on Darwin
                # but the helper C libraries are built with debug info which
                # can include the build path. Use the -S to strip the debug info
                # info from the helper C libraries to avoid these paths.
                '-H:NativeLinkerOption=-Wl,-S'
            ]
        if mx.is_windows():
            return ['-H:NativeLinkerOption=-pdbaltpath:%_PDB%']
    return []

libgraal_build_args = [
    ## Pass via JVM args opening up of packages needed for image builder early on
    '-J--add-exports=jdk.graal.compiler/jdk.graal.compiler.hotspot=ALL-UNNAMED',
    '-J--add-exports=jdk.graal.compiler/jdk.graal.compiler.options=ALL-UNNAMED',
    '-J--add-exports=jdk.graal.compiler/jdk.graal.compiler.truffle=ALL-UNNAMED',
    '-J--add-exports=jdk.graal.compiler/jdk.graal.compiler.truffle.hotspot=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.jniutils/org.graalvm.jniutils=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.truffle.compiler/com.oracle.truffle.compiler.hotspot.libgraal=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.truffle.compiler/com.oracle.truffle.compiler.hotspot=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.truffle.compiler/com.oracle.truffle.compiler=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.nativeimage/com.oracle.svm.core.annotate=ALL-UNNAMED',
    '-J--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.option=ALL-UNNAMED',
    ## Packages used after option-processing can be opened by the builder (`-J`-prefix not needed)
    # LibGraalFeature implements com.oracle.svm.core.feature.InternalFeature (needed to be able to instantiate LibGraalFeature)
    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.feature=ALL-UNNAMED',
    # Make ModuleSupport accessible to do the remaining opening-up in LibGraalFeature constructor
    '--add-exports=org.graalvm.nativeimage.base/com.oracle.svm.util=ALL-UNNAMED',
    # TruffleLibGraalJVMCIServiceLocator needs access to JVMCIServiceLocator
    '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED',

    '--initialize-at-build-time=jdk.graal.compiler,org.graalvm.libgraal,com.oracle.truffle',

    '-H:+ReportExceptionStackTraces',

    # Set minimum based on libgraal-ee-pgo
    '-J-Xms7g'
] + ([
    # If building on the console, use as many cores as available
    f'--parallelism={mx.cpu_count()}',
] if mx.is_interactive() else []) + svm_experimental_options([
    '-H:-UseServiceLoaderFeature',
    '-H:+AllowFoldMethods',
    '-Djdk.vm.ci.services.aot=true',
    '-Dtruffle.TruffleRuntime=',
    '-H:+JNIEnhancedErrorCodes',
    '-H:InitialCollectionPolicy=LibGraal',

    # These 2 arguments provide walkable call stacks for a crash in libgraal
    '-H:+PreserveFramePointer',
    '-H:-DeleteLocalSymbols',

    # Configure -Djdk.graal.internal.HeapDumpOnOutOfMemoryError=true
    '--enable-monitoring=heapdump',
    '-H:HeapDumpDefaultFilenamePrefix=libgraal_pid',

    # No VM-internal threads may be spawned for libgraal and the reference handling is executed manually.
    '-H:-AllowVMInternalThreads',
    '-R:-AutomaticReferenceHandling',

    # URLClassLoader causes considerable increase of the libgraal image size and should be excluded.
    '-H:ReportAnalysisForbiddenType=java.net.URLClassLoader',

    # No need for container support in libgraal as HotSpot already takes care of it
    '-H:-UseContainerSupport',
] + ([
    # Force page size to support libgraal on AArch64 machines with a page size up to 64K.
    '-H:PageSize=64K'
] if mx.get_arch() == 'aarch64' else []) + ([
    # Build libgraal with 'Full RELRO' to prevent GOT overwriting exploits on Linux (GR-46838)
    '-H:NativeLinkerOption=-Wl,-z,relro,-z,now',
] if mx.is_linux() else [])) + prevent_build_path_in_libgraal()

libgraal = mx_sdk_vm.GraalVmJreComponent(
    suite=suite,
    name='LibGraal',
    short_name='lg',
    dir_name=False,
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=[],
    builder_jar_distributions=[],
    support_distributions=[],
    priority=0,
    library_configs=[
        mx_sdk_vm.LibraryConfig(
            destination="<lib:jvmcicompiler>",
            jvm_library=True,
            jar_distributions=libgraal_jar_distributions,
            build_args=libgraal_build_args + ['--features=com.oracle.svm.graal.hotspot.libgraal.LibGraalFeature,com.oracle.svm.graal.hotspot.libgraal.truffle.TruffleLibGraalFeature'],
            add_to_module='java.base',
            headers=False,
            home_finder=False,
        ),
    ],
    support_libraries_distributions=[],
    stability="supported",
    jlink=False,
)
mx_sdk_vm.register_graalvm_component(libgraal)

def _native_image_configure_extra_jvm_args():
    packages = ['jdk.graal.compiler/jdk.graal.compiler.phases.common', 'jdk.internal.vm.ci/jdk.vm.ci.meta', 'jdk.internal.vm.ci/jdk.vm.ci.services', 'jdk.graal.compiler/jdk.graal.compiler.core.common.util']
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
            use_modules='image',
            main_module='org.graalvm.nativeimage.configure',
            destination='bin/<exe:native-image-configure>',
            jar_distributions=['substratevm:SVM_CONFIGURE'],
            main_class='com.oracle.svm.configure.ConfigurationTool',
            build_args=svm_experimental_options([
                '-H:-ParseRuntimeOptions',
            ]),
            extra_jvm_args=_native_image_configure_extra_jvm_args(),
            home_finder=False,
        )
    ],
    jlink=False,
    installable_id='native-image',
    installable=False,
    priority=10,
))


def run_helloworld_command(args, config, command_name):
    parser = ArgumentParser(prog='mx ' + command_name)
    all_args = ['--output-path', '--javac-command', '--build-only', '--variant', '--list']
    masked_args = [_mask(arg, all_args) for arg in args]
    default_variant = list(_helloworld_variants.keys())[0]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir(suite)])
    parser.add_argument(all_args[1], metavar='<javac-command>', help='A javac command to be used', default=mx.get_jdk().javac)
    parser.add_argument(all_args[2], action='store_true', help='Only build the native image')
    parser.add_argument(all_args[3], choices=_helloworld_variants.keys(), default=default_variant, help=f'The Hello World source code variant to use (default: {default_variant})')
    parser.add_argument(all_args[4], action='store_true', help='Print the Hello World source and exit')
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    javac_command = unmask(parsed.javac_command.split())
    output_path = unmask(parsed.output_path)[0]
    build_only = parsed.build_only
    image_args = unmask(parsed.image_args)
    if parsed.list:
        mx.log(_helloworld_variants[parsed.variant])
        return
    native_image_context_run(
        lambda native_image, a:
        _helloworld(native_image, javac_command, output_path, build_only, a, variant=parsed.variant), unmask(image_args),
        config=config,
    )


@mx.command(suite_name=suite.name, command_name='debuginfotest', usage_msg='[options]')
def debuginfotest(args, config=None):
    """
    builds a debuginfo Hello native image and tests it with gdb.
    """
    parser = ArgumentParser(prog='mx debuginfotest')
    all_args = ['--output-path', '--build-only', '--with-isolates-only']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[svmbuild_dir()])
    parser.add_argument(all_args[1], action='store_true', help='Only build the native image')
    parser.add_argument(all_args[2], action='store_true', help='Only build and test the native image with isolates')
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    output_path = unmask(parsed.output_path)[0]
    build_only = parsed.build_only
    with_isolates_only = parsed.with_isolates_only
    native_image_context_run(
        lambda native_image, a:
            _debuginfotest(native_image, output_path, build_only, with_isolates_only, a), unmask(parsed.image_args),
        config=config
    )

@mx.command(suite_name=suite.name, command_name='debuginfotestshared', usage_msg='[options]')
def debuginfotestshared(args, config=None):
    """
    builds a debuginfo ctutorial image but does not yet test it with gdb"
    """
    # set an explicit path to the source tree for the tutorial code
    sourcepath = mx.project('com.oracle.svm.tutorial').source_dirs()[0]
    all_args = svm_experimental_options(['-H:GenerateDebugInfo=1', '-H:+SourceLevelDebug', '-H:DebugInfoSourceSearchPath=' + sourcepath, '-H:-DeleteLocalSymbols']) + args
    # build and run the native image using debug info
    # ideally we ought to script a gdb run
    native_image_context_run(_cinterfacetutorial, all_args)

@mx.command(suite_name=suite.name, command_name='gdbdebughelperstest', usage_msg='[options]')
def gdbdebughelperstest(args, config=None):
    """
    builds and tests gdb-debughelpers.py with multiple native images with debuginfo
    """
    parser = ArgumentParser(prog='mx gdbdebughelperstest')
    all_args = ['--output-path', '--with-isolates-only', '--build-only', '--test-only']
    masked_args = [_mask(arg, all_args) for arg in args]
    parser.add_argument(all_args[0], metavar='<output-path>', nargs=1, help='Path of the generated image', default=[join(svmbuild_dir(), "gdbdebughelperstest")])
    parser.add_argument(all_args[1], action='store_true', help='Only build and test the native image with isolates')
    parser.add_argument(all_args[2], action='store_true', help='Only build the native image')
    parser.add_argument(all_args[3], action='store_true', help='Only run the tests')
    parser.add_argument('image_args', nargs='*', default=[])
    parsed = parser.parse_args(masked_args)
    output_path = unmask(parsed.output_path)[0]
    with_isolates_only = parsed.with_isolates_only
    build_only = parsed.build_only
    test_only = parsed.test_only
    native_image_context_run(
        lambda native_image, a:
            _gdbdebughelperstest(native_image, output_path, with_isolates_only, build_only, test_only, a), unmask(parsed.image_args),
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

    # Build a helloworld Java module with maven
    module_path = []
    proj_dir = join(suite.dir, 'src', 'native-image-module-tests', 'hello.lib')
    mx.run_maven(['-e', 'install'], cwd=proj_dir)
    module_path.append(join(proj_dir, 'target', 'hello-lib-1.0-SNAPSHOT.jar'))
    proj_dir = join(suite.dir, 'src', 'native-image-module-tests', 'hello.app')
    mx.run_maven(['-e', 'install'], cwd=proj_dir)
    module_path.append(join(proj_dir, 'target', 'hello-app-1.0-SNAPSHOT.jar'))
    with native_image_context(hosted_assertions=False) as native_image:
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
            # On Windows, java is always an .exe, never a .cmd symlink
            join(_vm_home(None), 'bin', mx.exe_suffix('java')),
            ] + moduletest_run_args)

        # Build module into native image
        mx.log('Building image from java modules: ' + str(module_path))
        built_image = native_image(
            ['--verbose'] + svm_experimental_options(['-H:Path=' + build_dir]) + args + moduletest_run_args
        )
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
    def build_and_test_clinittest_image(native_image, args):
        args = [] if args is None else args
        test_cp = classpath('com.oracle.svm.test')
        build_dir = join(svmbuild_dir(), 'clinittest')

        # clean / create output directory
        if exists(build_dir):
            mx.rmtree(build_dir)
        mx_util.ensure_dir_exists(build_dir)

        # Build and run the example
        binary_path = join(build_dir, 'clinittest')
        native_image([
                '-cp', test_cp,
                '-J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED',
                '-J-ea', '-J-esa',
                '-o', binary_path,
                '-H:+ReportExceptionStackTraces',
                '-H:Class=com.oracle.svm.test.clinit.TestClassInitialization',
                '--features=com.oracle.svm.test.clinit.TestClassInitializationFeature',
            ] + svm_experimental_options([
                '-H:+PrintClassInitialization',
            ]) + args)
        mx.run([binary_path])

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
                    checkLine(line, "MustBeSimulated", "SIMULATED", "classes are initialized at run time by default", wrongly_initialized_lines)
                    checkLine(line, "MustBeDelayed", "RUN_TIME", "classes are initialized at run time by default", wrongly_initialized_lines)

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
        self.jvm_fallbacks_path = join(self.subject.get_output_root(), 'gensrc', 'JvmFuncsFallbacks.c')
        self.register_in_libjvm(libjvm)

        staticlib_path = ['lib', 'static', mx.get_os() + '-' + mx.get_arch()]
        if mx.is_linux():
            libc = mx.get_os_variant()
            # Assume we are running under glibc by default for now.
            staticlib_path = staticlib_path + [libc if libc else 'glibc']
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
            libjvm.c_files.append(self.jvm_fallbacks_path)

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
                    if not line or line.isspace():
                        return
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
                            found_undef = line_tokens[1] == '*UND*'
                        if found_undef:
                            symbol_candiate = line_tokens[-1]
                            mx.logvv('Found undefined symbol: ' + symbol_candiate)
                            platform_prefix = '_' if mx.is_darwin() else ''
                            if symbol_candiate.startswith(platform_prefix + symbol_prefix):
                                mx.logv('Pick symbol: ' + symbol_candiate)
                                symbols.add(symbol_candiate[len(platform_prefix):])
                    except:
                        mx.logvv('Skipping line: ' + line.rstrip())
                return collector

            if mx.is_windows():
                symbol_dump_command = 'dumpbin /SYMBOLS'
            elif mx.is_darwin():
                symbol_dump_command = 'nm'
            elif mx.is_linux():
                symbol_dump_command = 'objdump --wide --syms'
            else:
                mx.abort('gen_fallbacks not supported on ' + sys.platform)

            seen_gnu_property_type_5_warnings = False
            def suppress_gnu_property_type_5_warnings(line):
                nonlocal seen_gnu_property_type_5_warnings
                if 'unsupported GNU_PROPERTY_TYPE (5)' not in line:
                    mx.log_error(line.rstrip())
                elif not seen_gnu_property_type_5_warnings:
                    mx.log_error(line.rstrip())
                    mx.log_error('(suppressing all further warnings about "unsupported GNU_PROPERTY_TYPE (5)")')
                    seen_gnu_property_type_5_warnings = True

            for staticlib_path in self.staticlibs:
                mx.logv('Collect from : ' + staticlib_path)
                mx.run(symbol_dump_command.split() + [staticlib_path], out=collect_symbols_fn('JVM_'), err=suppress_gnu_property_type_5_warnings)

            if len(symbols) == 0:
                mx.abort('Could not find any unresolved JVM_* symbols in static JDK libraries')
            return symbols

        def collect_implementations():
            impls = set()

            def collect_impls_fn(symbol_prefix):
                def collector(line):
                    if not line or line.isspace():
                        return
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
                        mx.logvv('Skipping line: ' + line.rstrip())
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
                    mx_util.ensure_dir_exists(dirname(jvm_fallbacks_path))
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
            mx.rmtree(gen_src_dir)

    def __str__(self):
        return 'JvmFuncsFallbacksBuildTask {}'.format(self.subject)

def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    register_project(SubstrateCompilerFlagsBuilder())

    base_jdk_home = mx_sdk_vm.base_jdk().home
    lib_static = join(base_jdk_home, 'lib', 'static')
    if exists(lib_static):
        layout = {
            './': ['file:' + lib_static],
        }
    else:
        lib_prefix = mx.add_lib_prefix('')
        lib_suffix = mx.add_static_lib_suffix('')
        layout = {
            './': ['file:' + join(base_jdk_home, 'lib', lib_prefix + '*' + lib_suffix)]
        }
    register_distribution(JDKLayoutTARDistribution(suite, 'SVM_STATIC_LIBRARIES_SUPPORT', [], layout, None, True, None))


class JDKLayoutTARDistribution(mx.LayoutTARDistribution):
    def isJDKDependent(self):
        return True


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
            mx_util.ensure_dir_exists(self.output_dir())
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

        # Packages to add-export
        distributions_transitive = mx.classpath_entries(self.buildDependencies)
        required_exports = mx_javamodules.requiredExports(distributions_transitive, get_jdk())
        exports_flags = mx_sdk_vm.AbstractNativeImageConfig.get_add_exports_list(required_exports)

        min_version = 21
        graal_compiler_flags_map[str(min_version)] = exports_flags

        feature_version = get_jdk().javaCompliance.value
        if str(feature_version) not in graal_compiler_flags_map and feature_version > min_version:
            # Unless specified otherwise, newer JDK versions use the same flags as JDK 21
            graal_compiler_flags_map[str(feature_version)] = graal_compiler_flags_map[str(min_version)]

        # DO NOT ADD ANY NEW ADD-OPENS OR ADD-EXPORTS HERE!
        #
        # Instead, provide the correct requiresConcealed entries in the moduleInfo
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
        if mx.get_os() == 'linux':
            libc = mx.get_os_variant() if mx.get_os_variant() else 'glibc'
            graal_compiler_flags_base.append('-Dsubstratevm.HostLibC=' + libc)

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

    jacoco_args = mx_gate.get_jacoco_agent_args(agent_option_prefix='-J')
    passedArgs = args
    if jacoco_args is not None:
        passedArgs += jacoco_args
    mx.run([executable] + _debug_args() + passedArgs, **kwargs)

@mx.command(suite.name, 'native-image-configure')
def native_image_configure_on_jvm(args, **kwargs):
    executable = vm_executable_path('native-image-configure')
    if not exists(executable):
        mx.abort("Can not find " + executable + "\nDid you forget to build? Try `mx build`")
    mx.run([executable] + _debug_args() + args, **kwargs)

def _debug_args():
    debug_args = get_jdk().debug_args
    if debug_args and not mx.is_debug_disabled():
        # prefix debug args with `--vm.` for bash launchers
        return [f'--vm.{arg[1:]}' for arg in debug_args]
    return []

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
