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
from os.path import exists, join
from copy import deepcopy

import mx
import mx_sdk_vm
import mx_gate

from mx_espresso import _espresso_stability, _has_native_espresso_standalone, _send_sigquit, get_java_home_dep, _jdk_lib_dir, jvm_standalone_with_llvm
from mx_sdk_vm_ng import _find_native_image_command, ThinLauncherProject  # pylint: disable=unused-import
from mx_sdk_vm_impl import get_final_graalvm_distribution, has_component, graalvm_skip_archive

_suite = mx.suite('espresso-compiler-stub')

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Espresso Compiler Stub',
    short_name='ecs',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=[],
    dir_name='java',
    dependencies=['Java on Truffle'],
    support_distributions=['espresso-compiler-stub:ESPRESSO_COMPILER_SUPPORT'],
    priority=2,
    stability=_espresso_stability,
))

def create_ni_standalone(base_standalone_name, register_distribution):
    espresso_suite = mx.suite('espresso')
    base_standalone = espresso_suite.dependency(base_standalone_name, fatalIfMissing=False)
    assert base_standalone_name.startswith('ESPRESSO_')
    ni_pos = len('ESPRESSO_')
    ni_standalone_name = base_standalone_name[:ni_pos] + 'NI_' + base_standalone_name[ni_pos:]
    if base_standalone:
        layout = deepcopy(base_standalone.layout)
        if '_NATIVE_' in base_standalone_name:
            # avoid dependency on project, copy from base standalone
            idx = layout['<jdk_lib_dir>/truffle/'].index('dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>')
            layout['<jdk_lib_dir>/truffle/'][idx] = f'dependency:espresso:{base_standalone_name}/{_jdk_lib_dir()}/truffle/<lib:jvm>'
            assert len(layout['languages/java/lib/']) == 1
            layout['languages/java/lib/'] = [
                f'dependency:espresso:{base_standalone_name}/languages/java/lib/<lib:javavm>'
            ]
        else:
            idx = layout['languages/java/lib/'].index('dependency:espresso:com.oracle.truffle.espresso.mokapot/*/<multitarget_libc_selection>/<lib:jvm>')
            layout['languages/java/lib/'][idx] = f'dependency:espresso:{base_standalone_name}/languages/java/lib/<lib:jvm>'
            idx = layout['bin/'].index('dependency:espresso:espresso')
            del layout['bin/'][idx]
            layout['bin/<exe:espresso>'] = f'dependency:espresso:{base_standalone}/bin/<exe:espresso>'
            layout['bin/<exe:java>'] = 'link:<exe:espresso>'
            layout['./'][0]['exclude'].append("bin/<exe:java>")
            if not jvm_standalone_with_llvm():
                mx.warn(f"{ni_standalone_name} requires using nfi-llvm but it looks like ESPRESSO_LLVM_JAVA_HOME wasn't set.")
        layout['languages/java/lib/'].append("dependency:espresso-compiler-stub:ESPRESSO_GRAAL/*")
        layout['./'][0]['exclude'].remove('lib/static')
        espresso_java_home = get_java_home_dep()
        if _find_native_image_command(espresso_java_home.java_home):
            # ESPRESSO_JAVA_HOME has native-image, keep that
            pass
        elif has_component('ni') and espresso_java_home.java_home == mx_sdk_vm.base_jdk().home:
            if graalvm_skip_archive():
                mx.abort("Cannot build NI standalones with GRAALVM_SKIP_ARCHIVE enabled")

            # substratevm is available and ESPRESSO_JAVA_HOME is JAVA_HOME, use GraalVM
            layout['./'][0]['source_type'] = 'extracted-dependency'
            layout['./'][0]['dependency'] = get_final_graalvm_distribution().qualifiedName()
            layout['./'][0]['path'] = '*/*'
            layout['./'][0]['exclude'] += [
                '*/languages/elau',
                '*/languages/java',
                '*/bin/espresso'
            ]
        else:
            layout = None
            if not mx.suite('substratevm', fatalIfMissing=False):
                second_issue = "the substratevm suite is not available"
            elif not has_component('ni'):
                second_issue = "the Native Image component is not available in the current GraalVM"
            else:
                second_issue = "ESPRESSO_JAVA_HOME != JAVA_HOME"
            mx.warn("ESPRESSO_JAVA_HOME doesn't contain native-image and " + second_issue + ". Cannot create " + ni_standalone_name)
        if layout:
            register_distribution(mx.LayoutDirDistribution(_suite, ni_standalone_name, [], layout, None, True, base_standalone.theLicense, pruning_mode=base_standalone.pruning_mode))
            return True
    return False

def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    native = create_ni_standalone('ESPRESSO_NATIVE_STANDALONE', register_distribution)
    jvm = create_ni_standalone('ESPRESSO_JVM_STANDALONE', register_distribution)
    if not (native or jvm):
        raise mx.abort("Couldn't create any Espresso native-image standalone")

def _run_espresso_native_image_launcher(args, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None, mode=None):
    extra_args = ['-J--vm.' + arg for arg in mx_gate.get_jacoco_agent_args() or []]
    mode = mode or _detect_espresso_native_image_mode()
    if mode == 'native':
        standalone = 'ESPRESSO_NI_NATIVE_STANDALONE'
    else:
        assert mode == 'jvm'
        standalone = 'ESPRESSO_NI_JVM_STANDALONE'
        espresso_launcher = join(mx.distribution(standalone).get_output(), 'bin', mx.exe_suffix('espresso'))
        extra_args += [
            '--vm.Dcom.oracle.svm.driver.java.executable.override=' + espresso_launcher,
            '-J--java.GuestFieldOffsetStrategy=graal',
            '-J--java.NativeBackend=nfi-llvm',
            '--vm.-java.NativeBackend=nfi-llvm'
        ]
    standalone_output = mx.distribution(standalone).get_output()
    if not exists(standalone_output):
        raise mx.abort(f"{standalone} doesn't seem to be built, please run `mx build --targets={standalone}`")
    native_image_command = _find_native_image_command(standalone_output)
    if not native_image_command:
        raise mx.abort(f"The native-image launcher does not exist in {standalone}")
    return mx.run([native_image_command] + extra_args + args, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)

def _detect_espresso_native_image_mode():
    if _has_native_espresso_standalone() and exists(mx.distribution('ESPRESSO_NI_NATIVE_STANDALONE').get_output()):
        return 'native'
    else:
        return 'jvm'

def _run_espresso_native_image_jvm_launcher(args, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    return _run_espresso_native_image_launcher(args, cwd, nonZeroIsFatal, out, err, timeout, mode='jvm')

def _run_espresso_native_image_native_launcher(args, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    return _run_espresso_native_image_launcher(args, cwd, nonZeroIsFatal, out, err, timeout, mode='native')

mx.update_commands(_suite, {
    'espresso-native-image': [_run_espresso_native_image_launcher, '[args]'],
    'espresso-native-image-jvm': [_run_espresso_native_image_jvm_launcher, '[args]'],
    'espresso-native-image-native': [_run_espresso_native_image_native_launcher, '[args]'],
})
