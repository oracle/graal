#
# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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
from os.path import exists

import mx
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_gate

from mx_espresso import _espresso_stability, espresso_library_config, _espresso_command, _send_sigquit, _llvm_toolchain_wrappers

_suite = mx.suite('espresso-compiler-stub')

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Espresso Compiler Stub',
    short_name='ecs',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=[],
    dir_name='java',
    installable_id='espresso-compiler-stub',
    installable=True,
    dependencies=['Java on Truffle'],
    support_distributions=['espresso-compiler-stub:ESPRESSO_COMPILER_SUPPORT'],
    priority=2,
    stability=_espresso_stability,
    standalone=False,
))


def _run_espresso_native_image_launcher(args, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    extra_args = ['-J--vm.' + arg for arg in mx_gate.get_jacoco_agent_args() or []]
    if mx_sdk_vm_impl._skip_libraries(espresso_library_config):
        # JVM mode
        espresso_launcher = _espresso_command('espresso', [])[0]
        if not exists(espresso_launcher):
            raise mx.abort("It looks like JVM mode but the espresso launcher does not exist")
        extra_args += [
            '--vm.Dcom.oracle.svm.driver.java.executable.override=' + espresso_launcher,
            '-J--java.GuestFieldOffsetStrategy=graal',
            '-J--java.NativeBackend=nfi-llvm',
            ]
    native_image_command = _espresso_command('native-image', extra_args + args)
    if not exists(native_image_command[0]):
        raise mx.abort("The native-image launcher does not exist")
    return mx.run(native_image_command, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


mx.update_commands(_suite, {
    'espresso-native-image': [_run_espresso_native_image_launcher, '[args]'],
})

mx_sdk_vm.register_vm_config('espresso-ni-ce', ['java', 'ejvm', 'ejc', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'cmp', 'svm', 'svmt', 'svmsl', 'tflm', 'bnative-image', 'ni', 'nil', 'tflsm', 'snative-image-agent', 'snative-image-diagnostics-agent', 'ecs'], _suite, env_file='espresso-ni')  # pylint: disable=line-too-long
mx_sdk_vm.register_vm_config('espresso-ni-jvm-ce', ['java', 'ejvm', 'elau', 'ellvm', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn', 'svm', 'svmt', 'svmsl', 'tflm', 'bnative-image', 'ni', 'nil', 'tflsm', 'snative-image-agent', 'snative-image-diagnostics-agent', 'lg', 'sjavavm', 'bespresso', 'ecs'] + _llvm_toolchain_wrappers, _suite, env_file='espresso-ni-jvm')  # pylint: disable=line-too-long
