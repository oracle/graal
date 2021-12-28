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


import mx
import mx_gate
import mx_jardistribution
import mx_sdk_vm, mx_sdk_vm_impl
import mx_vm_benchmark
import mx_vm_gate

import os
from os.path import basename, isdir, join, relpath

_suite = mx.suite('vm')
""":type: mx.SourceSuite | mx.Suite"""


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJdkComponent(
    suite=_suite,
    name='Component installer',
    short_name='gu',
    dir_name='installer',
    license_files=[],
    third_party_license_files=[],
    dependencies=['sdk'],
    jar_distributions=[
        'vm:INSTALLER',
        'truffle:TruffleJSON'
    ],
    support_distributions=['vm:INSTALLER_GRAALVM_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:gu>",
            jar_distributions=[
                'vm:INSTALLER',
                'truffle:TruffleJSON'
            ],
            dir_jars=True,
            main_class="org.graalvm.component.installer.ComponentInstaller",
            build_args=[],
            # Please see META-INF/native-image in the project for custom build options for native-image
            is_sdk_launcher=True,
            custom_launcher_script="mx.vm/gu.cmd" if mx.is_windows() else None,
        ),
    ],
    stability="supported",
))


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
    name='Polybench Launcher',
    short_name='pbm',
    license_files=[],
    third_party_license_files=[],
    dir_name='polybench',
    launcher_configs=[mx_sdk_vm.LauncherConfig(
        destination='bin/<exe:polybench>',
        jar_distributions=['vm:POLYBENCH'],
        main_class='org.graalvm.polybench.PolyBenchLauncher',
        build_args=[
            '-H:-ParseRuntimeOptions',
            '-H:Features=org.graalvm.launcher.PolyglotLauncherFeature',
            '--tool:all',
        ],
        is_main_launcher=True,
        default_symlinks=True,
        is_sdk_launcher=True,
        is_polyglot=True,
    )],
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTool(
    suite=_suite,
    name='Polybench Instruments',
    short_name='pbi',
    dir_name='pbi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'Polybench Launcher'],
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
    dependencies=['Truffle', 'Polybench Launcher'],
    truffle_jars=['vm:PMH'],
    support_distributions=['vm:PMH_SUPPORT'],
    installable=False,
))


polybench_benchmark_methods = ["_run"]


# pylint: disable=line-too-long
ce_components = ['bpolyglot', 'cmp', 'cov', 'dap', 'gu', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'js', 'lg', 'libpoly', 'llrc', 'llrl', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'pro', 'rgx', 'sdk', 'spolyglot', 'svm', 'svmnfi', 'svml', 'tfl', 'tflm', 'vvm']
ce_win_complete_components = ['bnative-image-configure', 'bpolyglot', 'cmp', 'cov', 'dap', 'ejvm', 'gu', 'gvm', 'gwa', 'icu4j', 'ins', 'insight', 'insightheap', 'java', 'js', 'lg', 'libpoly', 'lsp', 'nfi-libffi', 'nfi', 'ni', 'nic', 'nil', 'njs', 'poly', 'polynative', 'pro', 'rgx', 'sdk', 'spolyglot', 'svm', 'svmnfi', 'tfl', 'tflm', 'vvm']
ce_aarch64_complete_components = ce_win_complete_components + ['llp', 'llrc', 'llrl', 'llrn', 'rby', 'rbyl', 'svml']
ce_darwin_complete_components = ce_aarch64_complete_components + ['pyn', 'R', 'bRMain', 'pynl']
ce_complete_components = ce_darwin_complete_components + ['ellvm']
ce_ruby_components = ['cmp', 'cov', 'dap', 'gvm', 'ins', 'insight', 'insightheap', 'lg', 'llp', 'llrc', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'pro', 'rby', 'rbyl', 'rgx', 'sdk', 'svm', 'svmnfi', 'tfl', 'tflm', 'vvm']
ce_python_components = ['bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgu', 'sjsvm', 'blli', 'bnative-image', 'bnative-image-configure', 'bpolybench', 'bpolyglot', 'cmp', 'cov', 'dap', 'dis', 'gu', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'js', 'lg', 'libpoly', 'llp', 'llrc', 'llrl', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'ni', 'nic', 'nil', 'nju', 'njucp', 'pbm', 'pmh', 'poly', 'polynative', 'pro', 'pyn', 'pynl', 'rgx', 'sdk', 'snative-image-agent', 'snative-image-diagnostics-agent', 'spolyglot', 'svm', 'svml', 'svmnfi', 'tfl', 'tflm', 'vvm']
ce_fastr_components = ['R', 'bRMain', 'bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgu', 'sjsvm', 'blli', 'bpolyglot', 'cmp', 'cov', 'dap', 'gu', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'js', 'lg', 'libpoly', 'llp', 'llrc', 'llrl', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'pro', 'rgx', 'sdk', 'spolyglot', 'svm', 'svml', 'svmnfi', 'tfl', 'tflm', 'vvm']
ce_no_native_components = ['bgu', 'sjsvm', 'blli', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'cov', 'dap', 'gu', 'gvm', 'icu4j', 'ins', 'insight', 'insightheap', 'js', 'lsp', 'nfi-libffi', 'nfi', 'ni', 'nic', 'nil', 'polynative', 'pro', 'rgx', 'sdk', 'llrc', 'llrn', 'llrl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'spolyglot', 'svm', 'svmnfi', 'svml', 'tfl', 'tflm', 'libpoly', 'poly', 'vvm']

mx_sdk_vm.register_vm_config('ce', ['insight', 'insightheap', 'cmp', 'cov', 'dap', 'gu', 'gvm', 'icu4j', 'ins', 'js', 'lg', 'libpoly', 'lsp', 'nfi-libffi', 'nfi', 'poly', 'bpolyglot', 'polynative', 'pro', 'rgx', 'sdk', 'spolyglot', 'svm', 'svmnfi', 'tfl', 'tflm', 'vvm'], _suite, env_file='ce-win')
mx_sdk_vm.register_vm_config('ce', ce_components, _suite, env_file='ce-aarch64')
mx_sdk_vm.register_vm_config('ce', ce_components, _suite, env_file='ce-darwin')
mx_sdk_vm.register_vm_config('ce', ce_components, _suite)
mx_sdk_vm.register_vm_config('ce', ce_components + ['njs'], _suite, dist_name='ce', env_file='ce-nodejs')
mx_sdk_vm.register_vm_config('ce', ce_ruby_components, _suite, dist_name='ce-ruby', env_file='ce-ruby')
mx_sdk_vm.register_vm_config('ce', ce_win_complete_components, _suite, dist_name='ce-win-complete')
mx_sdk_vm.register_vm_config('ce', ce_aarch64_complete_components, _suite, dist_name='ce-aarch64-complete')
mx_sdk_vm.register_vm_config('ce', ce_darwin_complete_components, _suite, dist_name='ce-darwin-complete')
mx_sdk_vm.register_vm_config('ce', ce_complete_components, _suite, dist_name='ce-complete')
mx_sdk_vm.register_vm_config('ce-python', ce_python_components, _suite)
mx_sdk_vm.register_vm_config('ce-fastr', ce_fastr_components, _suite)
mx_sdk_vm.register_vm_config('ce-no_native', ce_no_native_components, _suite)
mx_sdk_vm.register_vm_config('libgraal', ['bgu', 'cmp', 'dis', 'gu', 'gvm', 'lg', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'sdk', 'svm', 'svmnfi', 'svml', 'tfl', 'tflm', 'bpolyglot'], _suite)
mx_sdk_vm.register_vm_config('toolchain-only', ['sdk', 'tfl', 'tflm', 'nfi-libffi', 'nfi', 'cmp', 'svm', 'svmnfi', 'llp', 'llrc', 'llrn'], _suite)
mx_sdk_vm.register_vm_config('libgraal-bash', ['bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'bgu', 'cmp', 'gu', 'gvm', 'lg', 'nfi-libffi', 'nfi', 'poly', 'polynative', 'sdk', 'svm', 'svmnfi', 'svml', 'tfl', 'tflm', 'bpolyglot'], _suite, env_file=False)
mx_sdk_vm.register_vm_config('toolchain-only-bash', ['bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'tfl', 'tflm', 'gu', 'svm', 'svmnfi', 'gvm', 'polynative', 'llp', 'nfi-libffi', 'nfi', 'svml', 'bgu', 'sdk', 'llrc', 'llrn', 'cmp'], _suite, env_file=False)
mx_sdk_vm.register_vm_config('ce', ['bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'java', 'libpoly', 'sespresso', 'spolyglot', 'ejvm', 'sjsvm', 'blli', 'bnative-image', 'srubyvm', 'pynl', 'bgraalpython', 'pyn', 'bwasm', 'cmp', 'gwa', 'icu4j', 'js', 'lg', 'llp', 'nfi-libffi', 'nfi', 'ni', 'nil', 'pbm', 'pmh', 'pbi', 'rby', 'rbyl', 'rgx', 'sdk', 'llrc', 'llrn', 'llrl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmnfi', 'tfl', 'tflm'], _suite, env_file='polybench-ce')
mx_sdk_vm.register_vm_config('ce', ['pbm', 'pmh', 'pbi', 'ni', 'icu4j', 'js', 'lg', 'nfi-libffi', 'nfi', 'tfl', 'svm', 'nil', 'rgx', 'sdk', 'cmp', 'tflm', 'svmnfi', 'bnative-image', 'sjsvm', 'snative-image-agent', 'snative-image-diagnostics-agent'], _suite, env_file='polybench-nfi-ce')
mx_sdk_vm.register_vm_config('ce', ['bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'blli', 'bnative-image', 'cmp', 'lg', 'llrc', 'llrl', 'llrn', 'nfi-libffi', 'nfi', 'ni', 'nil', 'pbm', 'pbi', 'sdk', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmnfi', 'tfl', 'tflm'], _suite, env_file='polybench-sulong-ce')

if mx.get_os() == 'windows':
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'njucp', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmnfi', 'tfl', 'tflm'], _suite, env_file=False)
else:
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gu', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'njucp', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmnfi', 'svml', 'tfl', 'tflm'], _suite, env_file=False)
# pylint: enable=line-too-long


mx_gate.add_gate_runner(_suite, mx_vm_gate.gate_body)


def mx_post_parse_cmd_line(args):
    mx_vm_benchmark.register_graalvm_vms()


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
                        javaCompliance='1.8+',
                        checkstyleProj=project_name,
                        workingSets=None,
                        theLicense=None,
                        testProject=True,
                        defaultBuild=False,
                    ))

                    dist_name = 'POLYBENCH_ESPRESSO_' + simple_name.upper()
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
                        javaCompliance='1.8+',
                        excludedLibs=[],
                        workingSets=None,
                        theLicense=None,
                        testProject=True,
                        defaultBuild=False,
                    ))
                    # add jars to the layout of the benchmark distribution
                    _add_project_to_dist('./interpreter/{}.jar'.format(simple_name), dist_name,
                        source='dependency:{name}/polybench-espresso-' + simple_name.lower() + '.jar')


class GraalVmSymlinks(mx.Project):
    def __init__(self, **kw_args):
        super(GraalVmSymlinks, self).__init__(_suite, 'vm-symlinks', subDir=None, srcDirs=[], deps=['sdk:' + mx_sdk_vm_impl.graalvm_dist_name()], workingSets=None, d=_suite.dir, theLicense=None, testProject=False, **kw_args)
        self.links = []
        sdk_suite = mx.suite('sdk')
        for link_name in 'latest_graalvm', 'latest_graalvm_home':
            self.links += [(relpath(join(sdk_suite.dir, link_name), _suite.dir), join(_suite.dir, link_name))]

    def getArchivableResults(self, use_relpath=True, single=False):
        raise mx.abort("Project '{}' cannot be archived".format(self.name))

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
                    return True, '{} does not exist'.format(dest)
                link_file = mx.TimeStampFile(dest, False)
                if newestInput and link_file.isOlderThan(newestInput):
                    return True, '{} is older than {}'.format(dest, newestInput)
                if src != os.readlink(dest):
                    return True, '{} points to the wrong file'.format(dest)
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
