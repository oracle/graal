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

from __future__ import print_function
import os

import mx
import mx_gate
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_sdk_vm_ng
import pathlib
import mx_sdk_benchmark # pylint: disable=unused-import
import mx_sdk_clangformat # pylint: disable=unused-import
import argparse
import datetime
import shutil
import tempfile
from mx_bisect import define_bisect_default_build_steps
from mx_bisect_strategy import BuildStepsGraalVMStrategy

from mx_gate import Task
from mx_unittest import unittest

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_toolchain import ToolchainTestProject # pylint: disable=unused-import
from mx_sdk_shaded import ShadedLibraryProject # pylint: disable=unused-import

_suite = mx.suite('sdk')


define_bisect_default_build_steps(BuildStepsGraalVMStrategy())


def _sdk_gate_runner(args, tasks):
    with Task('SDK UnitTests', tasks, tags=['test'], report=True) as t:
        if t:
            unittest(['--suite', 'sdk', '--enable-timing', '--verbose', '--max-class-failures=25'], test_report_tags={'task': t.title})
    with Task('Check Copyrights', tasks) as t:
        if t:
            if mx.command_function('checkcopyrights')(['--primary', '--', '--projects', 'src']) != 0:
                t.abort('Copyright errors found. Please run "mx checkcopyrights --primary -- --fix" to fix them.')


mx_gate.add_gate_runner(_suite, _sdk_gate_runner)


def build_oracle_compliant_javadoc_args(suite, product_name, feature_name):
    """
    :type product_name: str
    :type feature_name: str
    """
    version = suite.release_version()
    if suite.vc:
        revision = suite.vc.parent(suite.vc_dir)
        copyright_year = str(datetime.datetime.fromtimestamp(suite.vc.parent_info(suite.vc_dir)['committer-ts']).year)
    else:
        revision = None
        copyright_year = datetime.datetime.now().year
    return ['--arg', '@-header', '--arg', '<b>%s %s Java API Reference<br>%s</b><br>%s' % (product_name, feature_name, version, revision),
            '--arg', '@-bottom', '--arg', '<center>Copyright &copy; 2012, %s, Oracle and/or its affiliates. All rights reserved.</center>' % (copyright_year),
            '--arg', '@-windowtitle', '--arg', '%s %s Java API Reference' % (product_name, feature_name)]


def javadoc(args):
    """build the Javadoc for all API packages"""
    extraArgs = build_oracle_compliant_javadoc_args(_suite, 'GraalVM', 'SDK')
    excludedPackages = [
        'org.graalvm.polyglot.tck',
        'org.graalvm.sdk',
        'org.graalvm.shadowed.org.jline.reader',
        'org.graalvm.shadowed.org.jline.reader.impl.completer',
        'org.graalvm.shadowed.org.jline.reader.impl.history',
        'org.graalvm.shadowed.org.jline.terminal.impl',
        'org.graalvm.shadowed.org.jline.utils',
    ]
    mx.javadoc(['--unified', '--disallow-all-warnings', '--exclude-packages', ','.join(excludedPackages)] + extraArgs + args)

def upx(args):
    """compress binaries using the upx tool"""
    upx_directory = mx.library("UPX", True).get_path(True)
    upx_path = os.path.join(upx_directory, mx.exe_suffix("upx"))
    upx_cmd = [upx_path] + args
    mx.run(upx_cmd, mx.TeeOutputCapture(mx.OutputCapture()), mx.TeeOutputCapture(mx.OutputCapture()))


# SDK modules included if truffle, compiler and native-image is included
graalvm_sdk_component = mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Graal SDK',
    short_name='sdk',
    dir_name='graalvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['sdkni'],
    jar_distributions=[],
    boot_jars=['sdk:POLYGLOT', 'sdk:GRAAL_SDK'],
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(graalvm_sdk_component)

# SDK modules included if the compiler (jargraal) is included
graal_sdk_compiler_component = mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Graal SDK Compiler',
    short_name='sdkc',
    dir_name='graalvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=[],
    boot_jars=['sdk:WORD', 'sdk:COLLECTIONS', 'sdk:NATIVEIMAGE'],
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(graal_sdk_compiler_component)

# SDK modules included if the compiler and native-image is included
graalvm_sdk_native_image_component = mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Graal SDK Native Image',
    short_name='sdkni',
    dir_name='graalvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['sdkc'],
    jar_distributions=[],
    boot_jars=['sdk:NATIVEIMAGE', 'sdk:NATIVEIMAGE_LIBGRAAL', 'sdk:WEBIMAGE_PREVIEW'],
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(graalvm_sdk_native_image_component)

graalvm_launcher_common_component = mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='GraalVM Launcher Common',
    short_name='sdkl',
    dir_name='graalvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Graal SDK'],
    jar_distributions=['sdk:LAUNCHER_COMMON', 'sdk:JLINE3'],
    boot_jars=[],
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(graalvm_launcher_common_component)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='LLVM.org toolchain',
    short_name='llp',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=['3rd_party_license_llvm-toolchain.txt'],
    dependencies=[],
    support_distributions=['LLVM_TOOLCHAIN'],
    stability="supported",
))

def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    mx_sdk_vm_impl.mx_register_dynamic_suite_constituents(register_project, register_distribution)
    mx_sdk_vm_ng.mx_register_dynamic_suite_constituents(register_project, register_distribution)


def mx_post_parse_cmd_line(args):
    mx_sdk_vm_impl.mx_post_parse_cmd_line(args)


mx.update_commands(_suite, {
    'javadoc': [javadoc, '[SL args|@VM options]'],
    'upx': [upx, ''],
})


# For backward compatibility

AbstractNativeImageConfig = mx_sdk_vm.AbstractNativeImageConfig
LauncherConfig = mx_sdk_vm.LauncherConfig
LanguageLauncherConfig = mx_sdk_vm.LanguageLauncherConfig
LibraryConfig = mx_sdk_vm.LibraryConfig
LanguageLibraryConfig = mx_sdk_vm.LanguageLibraryConfig
GraalVmComponent = mx_sdk_vm.GraalVmComponent
GraalVmTruffleComponent = mx_sdk_vm.GraalVmTruffleComponent
GraalVmLanguage = mx_sdk_vm.GraalVmLanguage
GraalVmTool = mx_sdk_vm.GraalVmTool
GraalVMSvmMacro = mx_sdk_vm.GraalVMSvmMacro
GraalVmSvmTool = mx_sdk_vm.GraalVmSvmTool
GraalVmJdkComponent = mx_sdk_vm.GraalVmJdkComponent
GraalVmJreComponent = mx_sdk_vm.GraalVmJreComponent
GraalVmJvmciComponent = mx_sdk_vm.GraalVmJvmciComponent


def register_graalvm_component(component):
    return mx_sdk_vm.register_graalvm_component(component)


def graalvm_component_by_name(name, fatalIfMissing=True):
    return mx_sdk_vm.graalvm_component_by_name(name, fatalIfMissing=fatalIfMissing)


def graalvm_components(opt_limit_to_suite=False):
    return mx_sdk_vm.graalvm_components(opt_limit_to_suite=opt_limit_to_suite)


def add_graalvm_hostvm_config(name, java_args=None, launcher_args=None, priority=0):
    return add_graalvm_hostvm_config(name, java_args=java_args, launcher_args=launcher_args, priority=priority)


def jdk_enables_jvmci_by_default(jdk):
    return mx_sdk_vm.jdk_enables_jvmci_by_default(jdk)


def jlink_new_jdk(jdk, dst_jdk_dir, module_dists, ignore_dists,
                  root_module_names=None,
                  missing_export_target_action=None,
                  with_source=lambda x: True,
                  vendor_info=None,
                  use_upgrade_module_path=False,
                  default_to_jvmci=False):
    return mx_sdk_vm.jlink_new_jdk(jdk, dst_jdk_dir, module_dists, ignore_dists,
                                   root_module_names=root_module_names,
                                   missing_export_target_action=missing_export_target_action,
                                   with_source=with_source,
                                   vendor_info=vendor_info,
                                   use_upgrade_module_path=use_upgrade_module_path,
                                   default_to_jvmci=default_to_jvmci)

class GraalVMJDKConfig(mx.JDKConfig):

    # Oracle JDK includes the libjvmci compiler, allowing it to function as GraalVM.
    # However, the Graal compiler is disabled by default and must be explicitly
    # enabled using the -XX:+UseGraalJIT option.
    libgraal_additional_vm_args = [
        '-XX:+UnlockExperimentalVMOptions',
        '-XX:+EnableJVMCI',
        '-XX:+UseGraalJIT',
        '-XX:-UnlockExperimentalVMOptions'
    ]
    """
    A JDKConfig that configures the built GraalVM as a JDK config.
    """
    def __init__(self):
        default_jdk = mx.get_jdk(tag='default')
        if GraalVMJDKConfig.is_graalvm(default_jdk.home):
            graalvm_home = default_jdk.home
            additional_vm_args = []
        elif GraalVMJDKConfig.is_libgraal_jdk(default_jdk.home):
            graalvm_home = default_jdk.home
            additional_vm_args = GraalVMJDKConfig.libgraal_additional_vm_args
        else:
            graalvm_home = mx_sdk_vm.graalvm_home(fatalIfMissing=True)
            additional_vm_args = []
        self._home_internal = graalvm_home
        self._vm_args = additional_vm_args
        mx.JDKConfig.__init__(self, graalvm_home, tag='graalvm')

    @property
    def home(self):
        return self._home_internal

    @home.setter
    def home(self, home):
        return

    def processArgs(self, args, addDefaultArgs=True):
        processed_args = super(GraalVMJDKConfig, self).processArgs(args, addDefaultArgs)
        if addDefaultArgs and self._vm_args:
            processed_args = self._vm_args + processed_args
        return processed_args

    @staticmethod
    def is_graalvm(java_home):
        release_file = os.path.join(java_home, 'release')
        if not os.path.isfile(release_file):
            return False
        with open(release_file, 'r') as file:
            for line in file:
                if line.startswith('GRAALVM_VERSION'):
                    return True
        return False

    @staticmethod
    def is_libgraal_jdk(java_home):
        release_file = os.path.join(java_home, 'release')
        if not os.path.isfile(release_file):
            return False
        with open(release_file, 'r') as file:
            for line in file:
                if line.startswith('MODULES') and 'jdk.graal.compiler.lib' in line:
                    # Oracle JDK has libjvmcicompiler
                    return True
        return False

class GraalVMJDK(mx.JDKFactory):

    def getJDKConfig(self):
        return GraalVMJDKConfig()

    def description(self):
        return "GraalVM JDK"

mx.addJDKFactory('graalvm', mx.get_jdk(tag='default').javaCompliance, GraalVMJDK())


def maven_deploy_public_repo_dir():
    return os.path.join(_suite.get_mx_output_dir(), 'public-maven-repo')

@mx.command(_suite.name, 'maven-deploy-public-repo-dir')
def print_maven_deploy_public_repo_dir(args):
    print(maven_deploy_public_repo_dir())

@mx.command(_suite.name, 'maven-deploy-public')
def maven_deploy_public(args, licenses=None, deploy_snapshots=True):
    """Helper to simplify deploying all public Maven dependendencies into the mxbuild directory"""
    if deploy_snapshots:
        artifact_version = f'{mx_sdk_vm_impl.graalvm_version("graalvm")}-SNAPSHOT'
    else:
        artifact_version = f'{mx_sdk_vm_impl.graalvm_version("graalvm")}'
    path = maven_deploy_public_repo_dir()
    mx.rmtree(path, ignore_errors=True)
    os.mkdir(path)

    if not licenses:
        # default licenses used
        licenses = ['GFTC', 'EPL-2.0', 'PSF-License', 'GPLv2-CPE', 'ICU,GPLv2', 'BSD-simplified', 'BSD-new', 'UPL', 'MIT']

    deploy_args = [
            '--tags=public',
            '--all-suites',
            '--all-distribution-types',
            f'--version-string={artifact_version}',
            '--validate=full',
            '--licenses', ','.join(licenses),
            'local',
            pathlib.Path(path).as_uri(),
        ]
    if mx.get_jdk().javaCompliance > '17':
        mx.warn("Javadoc won't be deployed as a JDK > 17 is not yet compatible with javadoc doclets. In order to deploy javadoc use a JDK 17 as a JDK on JAVA_HOME.")
        deploy_args += ["--suppress-javadoc"]

    mx.log(f'mx maven-deploy {" ".join(deploy_args)}')
    mx.maven_deploy(deploy_args)
    mx.log(f'Deployed Maven artefacts to {path}')

@mx.command(_suite.name, 'nativebridge-benchmark')
def nativebridge_benchmark(args):
    parser = argparse.ArgumentParser(prog='mx nativebridge-benchmark', description='Executes nativebridge benchmarks',
                            usage='mx nativebridge-benchmark [--target-folder <folder> | --isolate-library <isolate-library> | mode+]')
    parser.add_argument('--target-folder', help='Folder where the benchmark isolate library will be generated.', default=None)
    parser.add_argument('--isolate-library', help='Use the given isolate library.', default=None)
    parsed_args, args = parser.parse_known_args(args)

    jdk = mx.get_jdk(tag='graalvm')
    benchmark_dist = mx.distribution('NATIVEBRIDGE_BENCHMARK')
    isolate_library = parsed_args.isolate_library if parsed_args.isolate_library else None
    try:
        if not isolate_library:
            native_image_path = jdk.exe_path('native-image')
            if not os.path.exists(native_image_path):
                native_image_path = os.path.join(jdk.home, 'bin', mx.cmd_suffix('native-image'))
            if not os.path.exists(native_image_path):
                mx.abort(f"No native-image installed in GraalVM {jdk.home}. Switch to an environment that has an installed native-image command.")

            target_dir = parsed_args.target_folder if parsed_args.target_folder else tempfile.mkdtemp()
            target = os.path.join(target_dir, "bench")
            native_image_args = mx.get_runtime_jvm_args(benchmark_dist, jdk=jdk) + [
                '--shared',
                '-o',
                target
            ]
            mx.run([native_image_path] + native_image_args)
            for n in os.listdir(target_dir):
                _, ext = os.path.splitext(n)
                if ext in ('.so', '.dylib', '.dll'):
                    isolate_library = os.path.join(target_dir, n)
                    break

        launcher_project = mx.project('org.graalvm.nativebridge.launcher')
        launcher = next(launcher_project.getArchivableResults(single=True))[0]
        java_args = mx.get_runtime_jvm_args(benchmark_dist, jdk=jdk) + [
            '--enable-native-access=ALL-UNNAMED',
            'org.graalvm.nativebridge.benchmark.Main',
            launcher,
            isolate_library
        ] + args
        mx.run_java(java_args, jdk=jdk)
    finally:
        if not parsed_args.isolate_library and not parsed_args.target_folder:
            shutil.rmtree(target_dir)
