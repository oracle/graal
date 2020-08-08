#
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import mx
import mx_gate
import mx_sdk_vm
import mx_sdk_vm_impl
import datetime

from mx_gate import Task
from mx_unittest import unittest


_suite = mx.suite('sdk')


def _sdk_gate_runner(args, tasks):
    with Task('SDK UnitTests', tasks, tags=['test']) as t:
        if t: unittest(['--suite', 'sdk', '--enable-timing', '--verbose', '--fail-fast'])
    with Task('Check Copyrights', tasks) as t:
        if t:
            if mx.checkcopyrights(['--primary', '--', '--projects', 'src']) != 0:
                t.abort('Copyright errors found. Please run "mx checkcopyrights --primary -- --fix" to fix them.')


mx_gate.add_gate_runner(_suite, _sdk_gate_runner)


def build_oracle_compliant_javadoc_args(suite, product_name, feature_name):
    """
    :type product_name: str
    :type feature_name: str
    """
    version = suite.release_version()
    revision = suite.vc.parent(suite.vc_dir)
    copyright_year = str(datetime.datetime.fromtimestamp(suite.vc.parent_info(suite.vc_dir)['committer-ts']).year)
    return ['--arg', '@-header', '--arg', '<b>%s %s Java API Reference<br>%s</b><br>%s' % (product_name, feature_name, version, revision),
            '--arg', '@-bottom', '--arg', '<center>Copyright &copy; 2012, %s, Oracle and/or its affiliates. All rights reserved.</center>' % (copyright_year),
            '--arg', '@-windowtitle', '--arg', '%s %s Java API Reference' % (product_name, feature_name)]


def javadoc(args):
    """build the Javadoc for all API packages"""
    extraArgs = build_oracle_compliant_javadoc_args(_suite, 'GraalVM', 'SDK')
    mx.javadoc(['--unified', '--exclude-packages', 'org.graalvm.polyglot.tck'] + extraArgs + args)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Graal SDK',
    short_name='sdk',
    dir_name='graalvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=['sdk:LAUNCHER_COMMON'],
    boot_jars=['sdk:GRAAL_SDK']
))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='LLVM.org toolchain',
    short_name='llp',
    installable=True,
    installable_id='llvm-toolchain',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=['3rd_party_license_llvm-toolchain.txt'],
    dependencies=[],
    support_distributions=['LLVM_TOOLCHAIN']
))


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    mx_sdk_vm_impl.mx_register_dynamic_suite_constituents(register_project, register_distribution)


def mx_post_parse_cmd_line(args):
    mx_sdk_vm_impl.mx_post_parse_cmd_line(args)


mx.update_commands(_suite, {
    'javadoc': [javadoc, '[SL args|@VM options]'],
})


# For backward compatibility

AbstractNativeImageConfig = mx_sdk_vm.AbstractNativeImageConfig
LauncherConfig = mx_sdk_vm.LauncherConfig
LanguageLauncherConfig = mx_sdk_vm.LanguageLauncherConfig
LibraryConfig = mx_sdk_vm.LibraryConfig
GraalVmComponent = mx_sdk_vm.GraalVmComponent
GraalVmTruffleComponent = mx_sdk_vm.GraalVmTruffleComponent
GraalVmLanguage = mx_sdk_vm.GraalVmLanguage
GraalVmTool = mx_sdk_vm.GraalVmTool
GraalVMSvmMacro = mx_sdk_vm.GraalVMSvmMacro
GraalVmJdkComponent = mx_sdk_vm.GraalVmJdkComponent
GraalVmJreComponent = mx_sdk_vm.GraalVmJreComponent
GraalVmJvmciComponent = mx_sdk_vm.GraalVmJvmciComponent


def register_graalvm_component(component):
    return mx_sdk_vm.register_graalvm_component(component)


def graalvm_component_by_name(name):
    return mx_sdk_vm.graalvm_component_by_name(name)


def graalvm_components(opt_limit_to_suite=False):
    return mx_sdk_vm.graalvm_components(opt_limit_to_suite=opt_limit_to_suite)


def add_graalvm_hostvm_config(name, java_args=None, launcher_args=None, priority=0):
    return add_graalvm_hostvm_config(name, java_args=java_args, launcher_args=launcher_args, priority=priority)


def jdk_enables_jvmci_by_default(jdk):
    return mx_sdk_vm.jdk_enables_jvmci_by_default(jdk)


def jlink_new_jdk(jdk, dst_jdk_dir, module_dists, root_module_names=None, missing_export_target_action='create', with_source=lambda x: True, vendor_info=None):
    return mx_sdk_vm.jlink_new_jdk(jdk, dst_jdk_dir, module_dists,
                                   root_module_names=root_module_names,
                                   missing_export_target_action=missing_export_target_action,
                                   with_source=with_source,
                                   vendor_info=vendor_info)
