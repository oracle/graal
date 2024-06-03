#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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


import mx
import mx_gate
import mx_jardistribution
import mx_pomdistribution
import mx_sdk_vm, mx_sdk_vm_impl
import mx_vm_benchmark
import mx_vm_gate

from argparse import ArgumentParser
import os
import pathlib
from os.path import basename, isdir, join, relpath

_suite = mx.suite('vm')
""":type: mx.SourceSuite | mx.Suite"""


@mx.command(_suite.name, "local-path-to-url")
def local_path_to_url(args):
    """Print the url representation of a canonicalized path"""
    parser = ArgumentParser(prog='mx local-path-to-url', description='Print the url representation of a canonicalized path')
    parser.add_argument('path', action='store', help='the path to canonicalize and return as url')
    args = parser.parse_args(args)

    print(pathlib.Path(os.path.realpath(args.path)).as_uri())


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmComponent(
    suite=_suite,
    name='GraalVM license files',
    short_name='gvm',
    dir_name='.',
    license_files=['LICENSE.txt'],
    third_party_license_files=['THIRD_PARTY_LICENSE.txt'],
    dependencies=[],
    support_distributions=['vm:VM_GRAALVM_SUPPORT'],
    stability="supported",
))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='PolyBench Launcher',
    short_name='pbm',
    license_files=[],
    third_party_license_files=[],
    dir_name='polybench',
    launcher_configs=[mx_sdk_vm.LauncherConfig(
        destination='bin/<exe:polybench>',
        jar_distributions=['vm:POLYBENCH'],
        main_class='org.graalvm.polybench.PolyBenchLauncher',
        build_args=[
            '--features=org.graalvm.launcher.PolyglotLauncherFeature',
            '--initialize-at-build-time=org.graalvm.polybench',
            '--tool:all',
        ] + mx_sdk_vm_impl.svm_experimental_options([
            '-H:-ParseRuntimeOptions',
        ]),
        is_main_launcher=True,
        default_symlinks=True,
        is_sdk_launcher=True,
        is_polyglot=True,
    )],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='PolyBench Instruments',
    short_name='pbi',
    dir_name='pbi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'PolyBench Launcher'],
    truffle_jars=['vm:POLYBENCH_INSTRUMENTS'],
    support_distributions=['vm:POLYBENCH_INSTRUMENTS_SUPPORT'],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Polyglot Microbenchmark Harness',
    short_name='pmh',
    dir_name='pmh',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'PolyBench Launcher'],
    truffle_jars=['vm:PMH'],
    support_distributions=['vm:PMH_SUPPORT'],
    installable=False,
))

if mx.suite('tools', fatalIfMissing=False) is not None and mx.suite('graal-js', fatalIfMissing=False) is not None:
    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJdkComponent(
        suite=_suite,
        name='VisualVM',
        short_name='vvm',
        dir_name='visualvm',
        license_files=['LICENSE_VISUALVM.txt'],
        third_party_license_files=[],
        dependencies=['Graal.js'],
        support_distributions=['tools:VISUALVM_GRAALVM_SUPPORT'],
        provided_executables=[('tools:VISUALVM_PLATFORM_SPECIFIC', './bin/<exe:jvisualvm>')],
        installable=True,
        extra_installable_qualifiers=['ce'],
        stability="supported",
    ))

polybench_benchmark_methods = ["_run"]


llvm_components = ['bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang-cl', 'bgraalvm-native-clang++', 'bgraalvm-native-flang', 'bgraalvm-native-ld']

# pylint: disable=line-too-long
ce_unchained_components = ['bnative-image-configure', 'cmp', 'lg', 'ni', 'nic', 'nil', 'nr_lib_jvmcicompiler', 'sdkc', 'sdkni', 'svm', 'svmforeign', 'svmsl', 'svmt', 'tflc', 'tflsm']
ce_components_minimal = ['bpolyglot', 'cmp', 'cov', 'dap', 'gvm', 'ins', 'insight', 'insightheap', 'lg', 'libpoly', 'lsp', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'pro', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'spolyglot', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']
ce_components = ce_components_minimal + ['nr_lib_jvmcicompiler', 'bnative-image-configure', 'ni', 'nic', 'nil', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign']
ce_win_complete_components = ['antlr4', 'bnative-image-configure', 'bpolyglot', 'cmp', 'cov', 'dap', 'gvm', 'gwa', 'gwal', 'icu4j', 'ins', 'insight', 'insightheap', 'js', 'jsl', 'jss', 'lg', 'libpoly', 'llp', 'llrc', 'llrl', 'llrlf', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'ni', 'nic', 'nil', 'njs', 'njsl', 'poly', 'polynative', 'pro', 'pyn', 'pynl', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'spolyglot', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json', 'vvm']
ce_aarch64_complete_components = ce_win_complete_components + ['rby', 'rbyl', 'svml']
ce_complete_components = ce_aarch64_complete_components + ['R', 'bRMain']
ce_darwin_complete_components = list(ce_complete_components)
ce_darwin_complete_components.remove('bRMain')  # GR-49842 / GR-49835
ce_darwin_complete_components.remove('R')  # GR-49842 / GR-49835
ce_darwin_aarch64_complete_components = list(ce_aarch64_complete_components)
ce_darwin_aarch64_complete_components.remove('svml') # GR-34811 / GR-40147
ce_ruby_components = ['antlr4', 'cmp', 'cov', 'dap', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'lg', 'llp', 'llrc', 'llrlf', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'pro', 'rby', 'rbyl', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']
ce_python_components = llvm_components + ['antlr4', 'sllvmvm', 'bpolybench', 'bpolyglot', 'cmp', 'cov', 'dap', 'dis', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'lg', 'libpoly', 'llp', 'llrc', 'llrl', 'llrlf', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'pbm', 'pmh', 'poly', 'polynative', 'pro', 'pyn', 'pynl', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'spolyglot', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']
ce_fastr_components = ce_components + llvm_components + ['antlr4', 'sllvmvm', 'llp', 'bnative-image', 'snative-image-agent', 'R', 'bRMain', 'bnative-image-configure', 'llrc', 'snative-image-diagnostics-agent', 'llrn', 'llrl', 'llrlf']
ce_no_native_components = ['bpolyglot', 'cmp', 'cov', 'dap', 'gvm', 'ins', 'insight', 'insightheap', 'lsp', 'nfi-libffi', 'nfi', 'polynative', 'pro', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'spolyglot', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json', 'libpoly', 'poly']

# Main GraalVMs
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-win')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-aarch64')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-darwin')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-darwin-aarch64')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce')
# Other GraalVMs
mx_sdk_vm.register_vm_config('ruby-community', ce_ruby_components, _suite, env_file='ce-ruby')
mx_sdk_vm.register_vm_config('espresso-community', ['antlr4', 'ejc', 'ejvm', 'ellvm', 'gvm', 'java', 'llp', 'llrc', 'llrlf', 'llrn', 'nfi', 'nfi-libffi', 'nr_lib_javavm', 'sdk', 'sdkc', 'sdkl', 'sdkni', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file='ce-llvm-espresso')
mx_sdk_vm.register_vm_config('espresso-community', ['ejc', 'ejvm', 'gvm', 'java', 'nfi', 'nfi-libffi', 'nr_lib_javavm', 'sdk', 'sdkc', 'sdkl', 'sdkni', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file='ce-espresso')
mx_sdk_vm.register_vm_config('ce', ce_components + ['icu4j', 'js', 'jsl', 'jss', 'rgx', 'bnative-image', 'snative-image-agent', 'snative-image-diagnostics-agent'], _suite, dist_name='ce-js', env_file='ce-js')
mx_sdk_vm.register_vm_config('ce', ce_components + ['icu4j', 'js', 'jsl', 'jss', 'njs', 'njsl', 'rgx', 'sjsvm'], _suite, dist_name='ce', env_file='ce-nodejs')
mx_sdk_vm.register_vm_config('ce', ce_components_minimal + ['antlr4', 'llrn', 'llp', 'llrc', 'llrl', 'llrlf'], _suite, env_file='ce-llvm')
mx_sdk_vm.register_vm_config('ce', ce_win_complete_components, _suite, dist_name='ce-complete', env_file='ce-win-complete')
mx_sdk_vm.register_vm_config('ce', ce_aarch64_complete_components, _suite, dist_name='ce-complete', env_file='ce-aarch64-complete')
mx_sdk_vm.register_vm_config('ce', ce_darwin_aarch64_complete_components, _suite, dist_name='ce-complete', env_file='ce-darwin-aarch64-complete')
mx_sdk_vm.register_vm_config('ce', ce_complete_components, _suite, dist_name='ce-complete')
mx_sdk_vm.register_vm_config('ce', ce_darwin_complete_components, _suite, dist_name='ce-complete', env_file='ce-darwin-complete')
mx_sdk_vm.register_vm_config('ce-python', ce_python_components, _suite)
mx_sdk_vm.register_vm_config('ce-fastr', ce_fastr_components, _suite)
mx_sdk_vm.register_vm_config('ce-no_native', ce_no_native_components, _suite)
mx_sdk_vm.register_vm_config('libgraal', ['cmp', 'lg', 'sdkc', 'tflc'], _suite)
mx_sdk_vm.register_vm_config('toolchain-only', ['antlr4', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'nfi-libffi', 'nfi', 'cmp', 'llp', 'llrc', 'llrlf', 'llrn'], _suite)
mx_sdk_vm.register_vm_config('libgraal-bash', llvm_components + ['cmp', 'gvm', 'lg', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'bpolyglot'], _suite, env_file=False)
mx_sdk_vm.register_vm_config('toolchain-only-bash', llvm_components + ['antlr4', 'tfl', 'tfla', 'tflc', 'tflm', 'gvm', 'polynative', 'llp', 'nfi-libffi', 'nfi', 'svml', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'llrc', 'llrlf', 'llrn', 'cmp'], _suite, env_file=False)
mx_sdk_vm.register_vm_config('ce', llvm_components + ['antlr4', 'java', 'libpoly', 'sjavavm', 'spolyglot', 'ejvm', 'sjsvm', 'sllvmvm', 'bnative-image', 'srubyvm', 'pynl', 'spythonvm', 'pyn', 'cmp', 'gwa', 'gwal', 'icu4j', 'js', 'jsl', 'jss', 'lg', 'llp', 'nfi-libffi', 'nfi', 'ni', 'nil', 'pbm', 'pmh', 'pbi', 'rby', 'rbyl', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'llrc', 'llrn', 'llrl', 'llrlf', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign', 'swasmvm', 'tfl', 'tfla', 'tflc', 'tflm', 'bgraalpy-polyglot-get'], _suite, env_file='polybench-ce')
mx_sdk_vm.register_vm_config('ce', ['bnative-image', 'bpolybench', 'cmp', 'icu4j', 'lg', 'nfi', 'ni', 'nil', 'pbi', 'pbm', 'pmh', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, dist_name='ce', env_file='polybench-ctw-ce')
mx_sdk_vm.register_vm_config('ce', ['pbm', 'pmh', 'pbi', 'ni', 'icu4j', 'js', 'jsl', 'jss', 'lg', 'nfi-libffi', 'nfi', 'tfl', 'tfla', 'tflc', 'svm', 'svmt', 'nil', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'cmp', 'tflm', 'svmnfi', 'svmsl', 'svmforeign', 'bnative-image', 'sjsvm', 'snative-image-agent', 'snative-image-diagnostics-agent'], _suite, env_file='polybench-nfi-ce')
mx_sdk_vm.register_vm_config('ce', llvm_components + ['antlr4', 'sllvmvm', 'bnative-image', 'cmp', 'lg', 'llrc', 'llrl', 'llrlf', 'llrn', 'nfi-libffi', 'nfi', 'ni', 'nil', 'pbm', 'pbi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file='polybench-sulong-ce')

if mx.get_os() == 'windows':
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'njucp', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svmforeign', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file=False)
else:
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'njucp', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svml', 'svmforeign', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file=False)
# pylint: enable=line-too-long


mx_gate.add_gate_runner(_suite, mx_vm_gate.gate_body)


def mx_post_parse_cmd_line(args):
    mx_vm_benchmark.register_graalvm_vms()


# When this list is changed, the _enterprise_tools_meta_distributions
# in the mx_vm_enterprise.py must also be updated.
_community_tools_meta_distributions = [
    'tools:COVERAGE_COMMUNITY',
    'tools:DAP_COMMUNITY',
    'tools:HEAP_COMMUNITY',
    'tools:INSPECT_COMMUNITY',
    'tools:INSIGHT_COMMUNITY',
    'tools:LSP_COMMUNITY',
    'tools:PROFILER_COMMUNITY',
]

# When this list is changed, the _enterprise_languages_meta_distributions
# in the mx_vm_enterprise.py must also be updated.
_community_languages_meta_distributions = [
    'graal-js:JS_COMMUNITY',
    'espresso:JAVA_COMMUNITY',
    'graalpython:PYTHON_COMMUNITY',
    'sulong:LLVM_NATIVE_COMMUNITY',
    'sulong:LLVM_COMMUNITY',
    'truffleruby:RUBY_COMMUNITY',
    'wasm:WASM_COMMUNITY',
]


def _distribution_license(dist):
    """
    Provides the distribution license if it's specified, or the default license of the suite that owns the distribution.
    :return: list of licenses
    :rtype: list[str]
    """
    _license = dist.theLicense
    if not _license:
        _license = dist.suite.defaultLicense
    return _license


def register_community_tools_distribution(owner_suite, register_distribution):
    """
    Creates a dynamic TOOLS_COMMUNITY meta-POM distribution containing all
    community tool meta POMs.
    :type register_distribution: (mx.Distribution) -> None
    """
    tools_meta_poms = []
    tools_licenses = set()
    for tool_name in _community_tools_meta_distributions:
        tool_distribution = mx.distribution(tool_name, fatalIfMissing=False)
        if tool_distribution:
            assert tool_distribution.isPOMDistribution(), f'TOOLS_COMMUNITY dependency {tool_distribution.name} must be a meta-POM distribution.'
            tools_meta_poms.append(tool_distribution)
            tools_licenses.update(_distribution_license(tool_distribution))
    if tools_meta_poms:
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'tools-community',
                'tag': ['default', 'public'],
            },
            'description': 'Graalvm community tools.',
        }
        tools_community = mx_pomdistribution.POMDistribution(owner_suite, 'TOOLS_COMMUNITY', [], tools_meta_poms, sorted(list(tools_licenses)), **attrs)
        register_distribution(tools_community)


def register_community_languages_distribution(owner_suite, register_distribution):
    """
    Creates a dynamic LANGUAGES_COMMUNITY meta-POM distribution containing all
    community language meta POMs.
    :type register_distribution: (mx.Distribution) -> None
    """
    languages_meta_poms = []
    languages_licenses = set()
    for distribution_name in _community_languages_meta_distributions:
        language_distribution = mx.distribution(distribution_name, fatalIfMissing=False)
        if language_distribution:
            assert language_distribution.isPOMDistribution(), f'LANGUAGES_COMMUNITY dependency {language_distribution.name} must be a meta-POM distribution.'
            languages_meta_poms.append(language_distribution)
            languages_licenses.update(_distribution_license(language_distribution))
    if languages_meta_poms:
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'languages-community',
                'tag': ['default', 'public'],
            },
            'description': 'Graalvm community languages.',
        }
        languages_community = mx_pomdistribution.POMDistribution(owner_suite, 'LANGUAGES_COMMUNITY', [], languages_meta_poms, sorted(list(languages_licenses)), **attrs)
        register_distribution(languages_community)


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    if mx_sdk_vm_impl.has_component('FastR'):
        fastr_release_env = mx.get_env('FASTR_RELEASE', None)
        if fastr_release_env != 'true':
            mx.abort(('When including FastR, please set FASTR_RELEASE to \'true\' (env FASTR_RELEASE=true mx ...). Got FASTR_RELEASE={}. '
                      'For local development, you may also want to disable recommended packages build (FASTR_NO_RECOMMENDED=true) and '
                      'capturing of system libraries (export FASTR_CAPTURE_DEPENDENCIES set to an empty value). '
                      'See building.md in FastR documentation for more details.').format(fastr_release_env))
    if register_project:
        register_project(GraalVmSymlinks())

        benchmark_dist = _suite.dependency("POLYBENCH_BENCHMARKS")

        def _add_project_to_dist(destination, name, source='dependency:{name}/*'):
            if destination not in benchmark_dist.layout:
                benchmark_dist.layout[destination] = []
            benchmark_dist.layout[destination].append(source.format(name=name))
            benchmark_dist.buildDependencies.append(name)

        if mx_sdk_vm_impl.has_component('GraalWasm'):
            import mx_wasm

            class GraalVmWatProject(mx_wasm.WatProject):
                def getSourceDir(self):
                    return self.subDir

                def isBenchmarkProject(self):
                    return self.name.startswith("benchmarks.")

            register_project(GraalVmWatProject(
                suite=_suite,
                name='benchmarks.interpreter.wasm',
                deps=[],
                workingSets=None,
                subDir=join(_suite.dir, 'benchmarks', 'interpreter'),
                theLicense=None,
                testProject=True,
                defaultBuild=False,
            ))
            # add wasm to the layout of the benchmark distribution
            _add_project_to_dist('./interpreter/', 'benchmarks.interpreter.wasm')

        if mx_sdk_vm_impl.has_component('LLVM Runtime Native'):
            register_project(mx.NativeProject(
                suite=_suite,
                name='benchmarks.interpreter.llvm.native',
                results=['interpreter/'],
                buildEnv={
                    'NATIVE_LLVM_CC': '<toolchainGetToolPath:native,CC>',
                },
                buildDependencies=[
                    'sulong:SULONG_BOOTSTRAP_TOOLCHAIN',
                ],
                vpath=True,
                deps=[],
                workingSets=None,
                d=join(_suite.dir, 'benchmarks', 'interpreter'),
                subDir=None,
                srcDirs=[''],
                output=None,
                theLicense=None,
                testProject=True,
                defaultBuild=False,
            ))
            # add bitcode to the layout of the benchmark distribution
            _add_project_to_dist('./', 'benchmarks.interpreter.llvm.native')

        if mx_sdk_vm_impl.has_component('Java on Truffle'):
            java_benchmarks = join(_suite.dir, 'benchmarks', 'interpreter', 'java')
            for f in os.listdir(java_benchmarks):
                if isdir(join(java_benchmarks, f)) and not f.startswith("."):
                    main_class = basename(f)
                    simple_name = main_class.split(".")[-1]

                    project_name = 'benchmarks.interpreter.espresso.' + simple_name.lower()
                    register_project(mx.JavaProject(
                        suite=_suite,
                        subDir=None,
                        srcDirs=[join(_suite.dir, 'benchmarks', 'interpreter', 'java', main_class)],
                        deps=[],
                        name=project_name,
                        d=join(_suite.dir, 'benchmarks', 'interpreter', 'java', main_class),
                        javaCompliance='11+',
                        checkstyleProj=project_name,
                        workingSets=None,
                        theLicense=None,
                        testProject=True,
                        defaultBuild=False,
                    ))

                    dist_name = 'POLYBENCH_ESPRESSO_' + simple_name.upper()
                    attrs = {
                        'maven': False,
                    }
                    register_distribution(mx_jardistribution.JARDistribution(
                        suite=_suite,
                        subDir=None,
                        srcDirs=[''],
                        sourcesPath=[],
                        deps=[project_name],
                        mainClass=main_class,
                        name=dist_name,
                        path=simple_name + '.jar',
                        platformDependent=False,
                        distDependencies=[],
                        javaCompliance='11+',
                        excludedLibs=[],
                        workingSets=None,
                        theLicense=None,
                        testProject=True,
                        defaultBuild=False,
                        **attrs
                    ))
                    # add jars to the layout of the benchmark distribution
                    _add_project_to_dist(f'./interpreter/{simple_name}.jar', dist_name,
                        source='dependency:{name}/polybench-espresso-' + simple_name.lower() + '.jar')
    if register_distribution and _suite.primary:
        # Only primary suite can register languages and tools distributions.
        # If the suite is not a primary suite, languages and tools distributions might not have been loaded yet.
        # In this case the register_community_tools_distribution and register_community_languages_distribution
        # are called from the primary suite.
        register_community_tools_distribution(_suite, register_distribution)
        register_community_languages_distribution(_suite, register_distribution)

    maven_bundle_path = mx.get_env('MAVEN_BUNDLE_PATH')
    maven_bundle_artifact_id = mx.get_env('MAVEN_BUNDLE_ARTIFACT_ID')
    if bool(maven_bundle_path) != bool(maven_bundle_artifact_id):
        mx.abort(f"Both $MAVEN_BUNDLE_PATH and $MAVEN_BUNDLE_ARTIFACT_ID must be either set or not set. Got:\n$MAVEN_BUNDLE_PATH={'' if maven_bundle_path is None else maven_bundle_path}\n$MAVEN_BUNDLE_ARTIFACT_ID={'' if maven_bundle_artifact_id is None else maven_bundle_artifact_id}")
    if register_distribution and maven_bundle_path is not None:
        register_distribution(mx.LayoutTARDistribution(_suite, 'MAVEN_BUNDLE', [], {
            './': 'file:' + os.path.realpath(maven_bundle_path)
        }, None, True, None, maven={
            'groupId': 'org.graalvm.polyglot',
            'artifactId': maven_bundle_artifact_id,
            'version': mx_sdk_vm_impl.graalvm_version('graalvm'),
            'tag': 'resource-bundle',
        }))

class GraalVmSymlinks(mx.Project):
    def __init__(self, **kw_args):
        super(GraalVmSymlinks, self).__init__(_suite, 'vm-symlinks', subDir=None, srcDirs=[], deps=['sdk:' + mx_sdk_vm_impl.graalvm_dist_name()], workingSets=None, d=_suite.dir, theLicense=None, testProject=False, **kw_args)
        self.links = []
        sdk_suite = mx.suite('sdk')
        for link_name in 'latest_graalvm', 'latest_graalvm_home':
            self.links += [(relpath(join(sdk_suite.dir, link_name), _suite.dir), join(_suite.dir, link_name))]

    def getArchivableResults(self, use_relpath=True, single=False):
        raise mx.abort(f"Project '{self.name}' cannot be archived")

    def getBuildTask(self, args):
        return GraalVmSymLinksBuildTask(args, 1, self)


class GraalVmSymLinksBuildTask(mx.ProjectBuildTask):
    """
    For backward compatibility, maintain `latest_graalvm` and `latest_graalvm_home` symlinks in the `vm` suite
    """
    def needsBuild(self, newestInput):
        sup = super(GraalVmSymLinksBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        if mx.get_os() != 'windows':
            for src, dest in self.subject.links:
                if not os.path.lexists(dest):
                    return True, f'{dest} does not exist'
                link_file = mx.TimeStampFile(dest, False)
                if newestInput and link_file.isOlderThan(newestInput):
                    return True, f'{dest} is older than {newestInput}'
                if src != os.readlink(dest):
                    return True, f'{dest} points to the wrong file'
        return False, None

    def build(self):
        if mx.get_os() == 'windows':
            mx.warn('Skip adding symlinks to the latest GraalVM (Platform Windows)')
            return
        self.rm_links()
        self.add_links()

    def clean(self, forBuild=False):
        self.rm_links()

    def add_links(self):
        for src, dest in self.subject.links:
            os.symlink(src, dest)

    def rm_links(self):
        if mx.get_os() == 'windows':
            return
        for _, dest in self.subject.links:
            if os.path.lexists(dest):
                os.unlink(dest)

    def __str__(self):
        return "Generating GraalVM symlinks in the vm suite"
