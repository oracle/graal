#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import mx_sdk_vm, mx_sdk_vm_impl
import mx_vm_benchmark
import mx_vm_gate

import os
from os.path import join, relpath


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
    jar_distributions=['vm:INSTALLER'],
    support_distributions=['vm:INSTALLER_GRAALVM_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            destination="bin/<exe:gu>",
            jar_distributions=['vm:INSTALLER'],
            dir_jars=True,
            main_class="org.graalvm.component.installer.ComponentInstaller",
            build_args=[],
            # Please see META-INF/native-image in the project for custom build options for native-image
            is_sdk_launcher=True,
            custom_launcher_script="mx.vm/gu.cmd" if mx.is_windows() else None,
        ),
    ],
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
))

# pylint: disable=line-too-long
ce_components = ['cmp', 'cov', 'gu', 'gvm', 'ins', 'ats', 'js', 'lg', 'lsp', 'nfi', 'njs', 'polynative', 'pro', 'rgx', 'sdk', 'slg', 'svm', 'svml', 'tfl', 'tflm', 'libpoly', 'poly', 'bpolyglot', 'vvm']
ce_complete_components = ['cmp', 'cov', 'gu', 'gvm', 'gwa', 'ins', 'ats', 'js', 'lg', 'llp', 'lsp', 'nfi', 'ni', 'nil', 'njs', 'polynative', 'pro', 'pyn', 'pynl', 'rby', 'rbyl', 'rgx', 'sdk', 'slg', 'svm', 'svml', 'tfl', 'tflm', 'libpoly', 'poly', 'bpolyglot', 'vvm']
ce_python_components = ['cmp', 'cov', 'gu', 'gvm', 'ins', 'ats', 'js', 'lg', 'llp', 'lsp', 'nfi', 'ni', 'nil', 'njs', 'nju', 'nic', 'polynative', 'pyn', 'pynl', 'pro', 'rgx', 'sdk', 'slg', 'svm', 'svml', 'tfl', 'tflm', 'libpoly', 'poly', 'bpolyglot', 'vvm']
ce_no_native_components = ['bgu', 'bjs', 'blli', 'bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'bnative-image', 'bpolyglot', 'cmp', 'cov', 'gu', 'gvm', 'ins', 'ats', 'js', 'lsp', 'nfi', 'ni', 'nil', 'njs', 'polynative', 'pro', 'rgx', 'sdk', 'slg', 'snative-image-agent', 'spolyglot', 'svm', 'svml', 'tfl', 'tflm', 'libpoly', 'poly', 'vvm']

mx_sdk_vm.register_vm_config('ce', ['ats', 'cmp', 'cov', 'gu', 'gvm', 'ins', 'js', 'lg', 'libpoly', 'lsp', 'nfi', 'njs', 'poly', 'bpolyglot', 'polynative', 'pro', 'rgx', 'sdk', 'svm', 'tfl', 'tflm', 'vvm'], _suite, env_file='ce-win')
mx_sdk_vm.register_vm_config('ce', ce_components, _suite)
mx_sdk_vm.register_vm_config('ce', ce_complete_components, _suite, dist_name='ce-complete')
mx_sdk_vm.register_vm_config('ce-python', ce_python_components, _suite)
mx_sdk_vm.register_vm_config('ce-no_native', ce_no_native_components, _suite)
mx_sdk_vm.register_vm_config('libgraal', ['bgu', 'cmp', 'gu', 'gvm', 'lg', 'nfi', 'poly', 'polynative', 'sdk', 'svm', 'svml', 'tfl', 'tflm', 'bpolyglot'], _suite)
mx_sdk_vm.register_vm_config('toolchain-only', ['sdk', 'tfl', 'tflm', 'nfi', 'cmp', 'svm', 'llp', 'slg', 'blli'], _suite)
mx_sdk_vm.register_vm_config('libgraal-bash', ['bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'bgu', 'cmp', 'gu', 'gvm', 'lg', 'nfi', 'poly', 'polynative', 'sdk', 'svm', 'svml', 'tfl', 'tflm', 'bpolyglot'], _suite, env_file=False)
mx_sdk_vm.register_vm_config('toolchain-only-bash', ['bgraalvm-native-clang', 'bgraalvm-native-clang++', 'bgraalvm-native-ld', 'bgraalvm-native-binutil', 'tfl', 'tflm', 'gu', 'svm', 'gvm', 'polynative', 'llp', 'nfi', 'svml', 'bgu', 'blli', 'sdk', 'slg', 'cmp'], _suite, env_file=False)
# pylint: enable=line-too-long

if mx.get_os() == 'windows':
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gvm', 'nfi', 'ni', 'nil', 'nju', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'snative-image-agent', 'svm', 'tfl', 'tflm'], _suite, env_file=False)
else:
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'bpolyglot', 'cmp', 'gu', 'gvm', 'nfi', 'ni', 'nil', 'nju', 'nic', 'poly', 'polynative', 'rgx', 'sdk', 'snative-image-agent', 'svm', 'svml', 'tfl', 'tflm'], _suite, env_file=False)


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
