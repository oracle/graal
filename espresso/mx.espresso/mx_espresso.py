#
# Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import os

import mx
import mx_espresso_benchmarks  # pylint: disable=unused-import
import mx_sdk_vm
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from os.path import join

_suite = mx.suite('espresso')

# JDK compiled with the Sulong toolchain.
LLVM_JAVA_HOME = mx.get_env('LLVM_JAVA_HOME')

def _espresso_command(launcher, args):
    import mx_sdk_vm_impl
    bin_dir = join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin')
    exe = join(bin_dir, mx.exe_suffix(launcher))
    if not os.path.exists(exe):
        exe = join(bin_dir, mx.cmd_suffix(launcher))
    return [exe] + args


def _espresso_launcher_command(args):
    """Espresso launcher embedded in GraalVM + arguments"""
    return _espresso_command('espresso', args)


def _java_truffle_command(args):
    """Java launcher using libespresso in GraalVM + arguments"""
    return _espresso_command('java', ['-truffle'] + args)


def _espresso_standalone_command(args):
    """Espresso standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    return (
        vm_args
        + mx.get_runtime_jvm_args(['ESPRESSO', 'ESPRESSO_LAUNCHER'], jdk=mx.get_jdk())
        + [mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )


def _run_espresso_launcher(args=None, cwd=None, nonZeroIsFatal=True):
    """Run Espresso launcher within a GraalVM"""
    return mx.run(_espresso_launcher_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_espresso_standalone(args=None, cwd=None, nonZeroIsFatal=True):
    """Run standalone Espresso (not as part of GraalVM) from distribution jars"""
    return mx.run_java(_espresso_standalone_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_java_truffle(args=None, cwd=None, nonZeroIsFatal=True):
    """Run espresso through the standard java launcher within a GraalVM"""
    return mx.run(_java_truffle_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)


def _run_espresso_meta(args, nonZeroIsFatal=True):
    """Run Espresso (standalone) on Espresso (launcher)"""
    return _run_espresso_launcher([
        '--vm.Xss4m',
        '-Dtruffle.class.path.append=' + mx.dependency('ESPRESSO').path,  # on GraalVM the EspressoLanguageProvider must be visible to the GraalVMLocator
    ] + _espresso_standalone_command(args), nonZeroIsFatal=nonZeroIsFatal)


class EspressoTags:
    jackpot = 'jackpot'
    verify = 'verify'


def _espresso_gate_runner(args, tasks):
    # Jackpot configuration is inherited from Truffle.
    with Task('Jackpot', tasks, tags=[EspressoTags.jackpot]) as t:
        if t:
            jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)

    with Task('Espresso: GraalVM dist names', tasks, tags=['names']) as t:
        if t:
            mx_sdk_vm.verify_graalvm_configs(suites=['espresso'])

    mokapot_header_gate_name = 'Verify consistency of mokapot headers'
    with Task(mokapot_header_gate_name, tasks, tags=[EspressoTags.verify]) as t:
        if t:
            import mx_sdk_vm_impl
            run_instructions = "$ mx --dynamicimports=/substratevm --native-images=lib:espresso gate --all-suites --task '{}'".format(mokapot_header_gate_name)
            if mx_sdk_vm_impl._skip_libraries(espresso_library_config):
                mx.abort("""\
The registration of the Espresso library ('lib:espresso') is skipped. Please run this gate as follows:
{}""".format(run_instructions))

            errors = False
            mokapot_dir = join(mx.project('com.oracle.truffle.espresso.mokapot').dir, 'include')
            libespresso_dir = mx.project(mx_sdk_vm_impl.GraalVmNativeImage.project_name(espresso_library_config)).get_output_root()

            for header in ['libespresso_dynamic.h', 'graal_isolate_dynamic.h']:
                committed_header = join(mokapot_dir, header)
                if not mx.exists(committed_header):
                    mx.abort("Cannot locate '{}'. Was the file moved or renamed?".format(committed_header))

                generated_header = join(libespresso_dir, header)
                if not mx.exists(generated_header):
                    mx.abort("Cannot locate '{}'. Did you forget to build? Example:\n'mx --dynamicimports=/substratevm --native-images=lib:espresso build'".format(generated_header))

                committed_header_copyright = []
                with open(committed_header, 'r') as committed_header_file:
                    for line in committed_header_file.readlines():
                        if line == '/*\n' or line.startswith(' *') or line == '*/\n':
                            committed_header_copyright.append(line)
                        else:
                            break

                with open(generated_header, 'r') as generated_header_file:
                    generated_header_lines = []
                    for line in generated_header_file.readlines():
                        # Ignore definitions that are not needed for Espresso
                        if not line.startswith("typedef") or "(*Espresso_" in line or "__graal" in line or "(*graal_" in line:
                            generated_header_lines.append(line)
                        else:
                            newline = generated_header_lines.pop()  # Remove newline before ignored declaration
                            assert newline == "\n"

                errors = errors or mx.update_file(committed_header, ''.join(committed_header_copyright + generated_header_lines), showDiff=True)

            if errors:
                mx.abort("""\
One or more header files in the include dir of the mokapot project ('{committed}/') do not match those generated by Native Image ('{generated}/').
To fix the issue, run this gate locally:
{instructions}
And adapt the code to the modified headers in '{committed}'.
""".format(committed=os.path.relpath(mokapot_dir, _suite.vc_dir), generated=os.path.relpath(libespresso_dir, _suite.vc_dir), instructions=run_instructions))


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)


if mx_sdk_vm.base_jdk_version() > 8:
    if mx.is_windows():
        lib_espresso_cp = '%GRAALVM_HOME%\\lib\\graalvm\\lib-espresso.jar'
    else:
        lib_espresso_cp = '${GRAALVM_HOME}/lib/graalvm/lib-espresso.jar'
else:
    if mx.is_windows():
        lib_espresso_cp = '%GRAALVM_HOME%\\jre\\lib\\graalvm\\lib-espresso.jar'
    else:
        lib_espresso_cp = '${GRAALVM_HOME}/jre/lib/graalvm/lib-espresso.jar'


espresso_library_config = mx_sdk_vm.LibraryConfig(
    destination='lib/<lib:espresso>',
    jar_distributions=['espresso:LIB_ESPRESSO'],
    build_args=[
        '--language:java',
        '--tool:all',
        '-H:+EnableSignalAPI',
        '-R:+EnableSignalHandling',
        '-R:+InstallSegfaultHandler',
        '--features=com.oracle.truffle.espresso.FinalizationFeature',
    ],
    home_finder=True,
)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Java on Truffle',
    short_name='java',
    installable_id='espresso',
    installable=True,
    license_files=['LICENSE_JAVAONTRUFFLE'],
    third_party_license_files=[],
    dependencies=['Truffle', 'nfi-libffi', 'ejvm'],
    truffle_jars=['espresso:ESPRESSO'],
    support_distributions=['espresso:ESPRESSO_SUPPORT'],
    library_configs=[espresso_library_config],
    polyglot_lib_jar_dependencies=['espresso:LIB_ESPRESSO'],
    has_polyglot_lib_entrypoints=True,
    priority=1,
    post_install_msg="""
This version of Java on Truffle is experimental. We do not recommended it for production use.

Usage: java -truffle [-options] class [args...]
           (to execute a class)
    or java -truffle [-options] -jar jarfile [args...]
           (to execute a jar file)

To rebuild the polyglot library:
    gu rebuild-images libpolyglot -cp """ + lib_espresso_cp,
    stability="supported",
))

if LLVM_JAVA_HOME:
    release_dict = mx_sdk_vm.parse_release_file(join(LLVM_JAVA_HOME, 'release'))
    implementor = release_dict.get('IMPLEMENTOR')
    if implementor is not None:
        if implementor == 'Oracle Corporation':
            edition = 'ee'
        else:
            edition = 'ce'
    else:
        mx.warn('Release file for `LLVM_JAVA_HOME` ({}) is missing the IMPLEMENTOR field')
        edition = 'ce'

    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
        suite=_suite,
        name='Java on Truffle LLVM Java libraries',
        short_name='ellvm',
        license_files=[],
        third_party_license_files=[],
        truffle_jars=[],
        include_in_polyglot=False,
        dir_name='java',
        installable_id='espresso-llvm',
        extra_installable_qualifiers=[edition],
        installable=True,
        dependencies=['Java on Truffle', 'LLVM Runtime Native'],
        support_distributions=['espresso:ESPRESSO_LLVM_SUPPORT'],
        priority=2,
        stability="supported",
    ))


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """Conditionally creates the ESPRESSO_LLVM_SUPPORT distribution if a Java home with LLVM bitcode is provided.
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    if LLVM_JAVA_HOME:
        lib_prefix = mx.add_lib_prefix('')
        lib_suffix = mx.add_lib_suffix('')
        lib_path = join(LLVM_JAVA_HOME, 'lib')
        libraries = [join(lib_path, name) for name in os.listdir(lib_path) if name.startswith(lib_prefix) and name.endswith(lib_suffix)]
        register_distribution(mx.LayoutTARDistribution(_suite, 'ESPRESSO_LLVM_SUPPORT', [], {
            "lib/llvm/default/":
                ["file:" + lib for lib in libraries] +
                ["file:{}/release".format(LLVM_JAVA_HOME)],
        }, None, True, None))


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Espresso libjvm',
    short_name='ejvm',
    dir_name='truffle',
    installable_id='espresso',
    installable=True,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Java on Truffle'],
    support_libraries_distributions=['espresso:ESPRESSO_JVM_SUPPORT'],
    priority=2,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Espresso Launcher',
    short_name='elau',
    installable=False,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Java on Truffle'],
    truffle_jars=[],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination='bin/<exe:espresso>',
            jar_distributions=['espresso:ESPRESSO_LAUNCHER'],
            main_class='com.oracle.truffle.espresso.launcher.EspressoLauncher',
            build_args=[],
            language='java',
        )
    ],
))


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso_launcher, '[args]'],
    'espresso-standalone': [_run_espresso_standalone, '[args]'],
    'java-truffle': [_run_java_truffle, '[args]'],
    'espresso-meta': [_run_espresso_meta, '[args]'],
})

# Build configs
# pylint: disable=bad-whitespace
tools = ['cov', 'dap', 'ins', 'insight', 'insightheap', 'lsp', 'pro', 'vvm']
mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'ejvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'tfl', 'cmp'                                           , 'elau'                                             ] + tools, _suite, env_file='jvm')
mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'ejvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm', 'svmnfi'         , 'tflm', 'elau', 'lg', 'bespresso', 'sespresso', 'spolyglot'] + tools, _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'ejvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmnfi', 'svmee', 'tflm', 'elau', 'lg', 'bespresso', 'sespresso', 'spolyglot'] + tools, _suite, env_file='jvm-ee')
mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'ejvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm', 'svmnfi'         , 'tflm'                                        , 'spolyglot'] + tools, _suite, env_file='native-ce')
mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'ejvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmnfi', 'svmee', 'tflm'                                        , 'spolyglot'] + tools, _suite, env_file='native-ee')
