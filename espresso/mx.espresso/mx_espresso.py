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
import shutil
import signal
import subprocess

import mx
import mx_jardistribution
import mx_subst
import mx_espresso_benchmarks  # pylint: disable=unused-import
import mx_sdk_vm
import mx_sdk_vm_impl
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from os.path import join, isabs, dirname, relpath

_suite = mx.suite('espresso')

# JDK compiled with the Sulong toolchain.
espresso_llvm_java_home = mx.get_env('ESPRESSO_LLVM_JAVA_HOME') or mx.get_env('LLVM_JAVA_HOME')

def _espresso_command(launcher, args):
    bin_dir = join(mx_sdk_vm.graalvm_home(fatalIfMissing=True), 'bin')
    exe = join(bin_dir, mx.exe_suffix(launcher))
    if not os.path.exists(exe):
        exe = join(bin_dir, mx.cmd_suffix(launcher))
    return [exe] + args


def _espresso_launcher_command(args):
    """Espresso launcher embedded in GraalVM + arguments"""
    return _espresso_command('espresso', args)


def _java_truffle_command(args):
    """Java launcher using libjavavm in GraalVM + arguments"""
    return _espresso_command('java', ['-truffle'] + args)


def _espresso_standalone_command(args, use_optimized_runtime=False, with_sulong=False):
    """Espresso standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    distributions = ['ESPRESSO', 'ESPRESSO_LAUNCHER', 'ESPRESSO_LIBS_RESOURCES', 'ESPRESSO_RUNTIME_RESOURCES', 'TRUFFLE_NFI_LIBFFI']
    if with_sulong:
        distributions += ['SULONG_NFI', 'SULONG_NATIVE']
    return (
        vm_args
        + mx.get_runtime_jvm_args(distributions, jdk=mx.get_jdk())
        # We are not adding the truffle runtime
        + ['-Dpolyglot.engine.WarnInterpreterOnly=false']
        + [mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )


def _send_sigquit(p):
    if mx.is_windows():
        sig = signal.CTRL_BREAK_EVENT
    else:
        sig = signal.SIGQUIT
    mx.warn(f"Sending {sig.name} ({sig.value}) to {p.pid} on timeout")
    p.send_signal(sig)
    try:
        # wait up to 10s for process to print stack traces
        p.wait(timeout=10)
        mx.warn(f"{p.pid} exited within 10s after receiving {sig} with return code: {p.returncode}")
    except subprocess.TimeoutExpired:
        pass


def _run_espresso_launcher(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    """Run Espresso launcher within a GraalVM"""
    return mx.run(_espresso_launcher_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_espresso_standalone(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    """Run standalone Espresso (not as part of GraalVM) from distribution jars"""
    return mx.run_java(_espresso_standalone_command(args, with_sulong=True), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_java_truffle(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    """Run espresso through the standard java launcher within a GraalVM"""
    return mx.run(_java_truffle_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_espresso(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    if mx_sdk_vm_impl._skip_libraries(espresso_library_config):
        # no libespresso, we can only run with the espresso launcher
        _run_espresso_launcher(args, cwd, nonZeroIsFatal, out, err, timeout)
    else:
        _run_java_truffle(args, cwd, nonZeroIsFatal, out, err, timeout)


def _run_espresso_meta(args, nonZeroIsFatal=True, timeout=None):
    """Run Espresso (standalone) on Espresso (launcher)"""
    return _run_espresso_launcher([
        '--vm.Xss4m',
    ] + _espresso_standalone_command(args), nonZeroIsFatal=nonZeroIsFatal, timeout=timeout)


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
            run_instructions = "$ mx --dynamicimports=/substratevm --native-images=lib:javavm gate --all-suites --task '{}'".format(mokapot_header_gate_name)
            if mx_sdk_vm_impl._skip_libraries(espresso_library_config):
                mx.abort("""\
The registration of the Espresso library ('lib:javavm') is skipped. Please run this gate as follows:
{}""".format(run_instructions))

            errors = False
            mokapot_dir = join(mx.project('com.oracle.truffle.espresso.mokapot').dir, 'include')
            libjavavm_dir = mx.project(mx_sdk_vm_impl.GraalVmNativeImage.project_name(espresso_library_config)).get_output_root()

            for header in ['libjavavm_dynamic.h', 'graal_isolate_dynamic.h']:
                committed_header = join(mokapot_dir, header)
                if not mx.exists(committed_header):
                    mx.abort("Cannot locate '{}'. Was the file moved or renamed?".format(committed_header))

                generated_header = join(libjavavm_dir, header)
                if not mx.exists(generated_header):
                    mx.abort("Cannot locate '{}'. Did you forget to build? Example:\n'mx --dynamicimports=/substratevm --native-images=lib:javavm build'".format(generated_header))

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
""".format(committed=relpath(mokapot_dir, _suite.vc_dir), generated=relpath(libjavavm_dir, _suite.vc_dir), instructions=run_instructions))


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)


if mx.is_windows():
    lib_javavm_cp = '%GRAALVM_HOME%\\lib\\graalvm\\lib-javavm.jar'
else:
    lib_javavm_cp = '${GRAALVM_HOME}/lib/graalvm/lib-javavm.jar'


espresso_library_config = mx_sdk_vm.LanguageLibraryConfig(
    language='java',
    jar_distributions=['espresso:LIB_JAVAVM'],
    build_args=[
        '-R:+EnableSignalHandling',
        '-R:+InstallSegfaultHandler',
        '--features=com.oracle.truffle.espresso.ref.FinalizationFeature',
    ] + mx_sdk_vm_impl.svm_experimental_options([
        '-H:-JNIExportSymbols',
        '-H:+DumpThreadStacksOnSignal',
    ]),
)

if mx_sdk_vm.base_jdk_version() not in (17,):
    _espresso_stability = "experimental"
elif mx.get_os() != "linux" or mx.get_arch() != "amd64":
    _espresso_stability = "experimental"
else:
    _espresso_stability = "supported"

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
    polyglot_lib_jar_dependencies=['espresso:LIB_JAVAVM'],
    has_polyglot_lib_entrypoints=True,
    priority=1,
    post_install_msg="""
This version of Java on Truffle is experimental. We do not recommended it for production use.

Usage: java -truffle [-options] class [args...]
           (to execute a class)
    or java -truffle [-options] -jar jarfile [args...]
           (to execute a jar file)

To rebuild the polyglot library:
    gu rebuild-images libpolyglot -cp """ + lib_javavm_cp,
    stability=_espresso_stability,
    standalone=False,
))

if espresso_llvm_java_home:
    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
        suite=_suite,
        name='Java on Truffle LLVM Java libraries',
        short_name='ellvm',
        license_files=[],
        third_party_license_files=[],
        truffle_jars=[],
        dir_name='java',
        installable_id='espresso-llvm',
        extra_installable_qualifiers=mx_sdk_vm.extra_installable_qualifiers(jdk_home=espresso_llvm_java_home, ce_edition=['ce'], oracle_edition=['ee']),
        installable=True,
        dependencies=['Java on Truffle', 'LLVM Runtime Native'],
        support_distributions=['espresso:ESPRESSO_LLVM_SUPPORT'],
        priority=2,
        stability=_espresso_stability,
        standalone=False,
    ))


def _jdk_license(home):
    if mx_sdk_vm.ee_implementor(home):
        return "Oracle Proprietary"
    else:
        return "GPLv2-CPE"


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    espresso_java_home = mx.get_env('ESPRESSO_JAVA_HOME') or mx_sdk_vm.base_jdk().home
    java_home_dep = JavaHomeDependency(_suite, "JAVA_HOME", espresso_java_home)
    register_project(java_home_dep)

    if espresso_llvm_java_home:
        # Conditionally creates the ESPRESSO_LLVM_SUPPORT distribution if a Java home with LLVM bitcode is provided.
        lib_prefix = mx.add_lib_prefix('')
        lib_suffix = mx.add_lib_suffix('')
        jdk_lib_dir = 'bin' if mx.is_windows() else 'lib'
        llvm_java_home_dep = JavaHomeDependency(_suite, "LLVM_JAVA_HOME", espresso_llvm_java_home)

        if mx.get_env("SKIP_ESPRESSO_LLVM_CHECK", 'false').lower() in ('false', '0', 'no'):
            libjava = join(espresso_llvm_java_home, jdk_lib_dir, f'{lib_prefix}java{lib_suffix}')
            if mx.is_linux():
                objdump = shutil.which('objdump')
                if objdump:
                    objdump_out = subprocess.check_output(['objdump', '-h', libjava]).decode('utf-8')
                    if 'llvmbc' not in objdump_out:
                        raise mx.abort(f"Cannot find LLVM bitcode in provided Espresso LLVM JAVA_HOME ({libjava})")
                elif mx.is_continuous_integration():
                    raise mx.abort("objdump not found on the PATH. It is required to verify the Espresso LLVM JAVA_HOME")
            elif mx.is_darwin():
                otool = shutil.which('otool')
                if otool:
                    otool_out = subprocess.check_output(['otool', '-l', libjava]).decode('utf-8')
                    if '__LLVM' not in otool_out:
                        raise mx.abort(f"Cannot find LLVM bitcode in provided Espresso LLVM JAVA_HOME ({libjava})")
                elif mx.is_continuous_integration():
                    raise mx.abort("otool not found on the PATH. It is required to verify the Espresso LLVM JAVA_HOME")
            if java_home_dep.is_ee_implementor != llvm_java_home_dep.is_ee_implementor:
                raise mx.abort("The implementors for ESPRESSO's JAVA_HOME and LLVM JAVA_HOME don't match")

        register_distribution(mx.LayoutTARDistribution(_suite, 'ESPRESSO_LLVM_SUPPORT', [], {
            "lib/llvm/default/": [
                f"dependency:LLVM_JAVA_HOME/{jdk_lib_dir}/{lib_prefix}*{lib_suffix}",
                "dependency:LLVM_JAVA_HOME/release"
            ],
        }, None, True, _jdk_license(espresso_llvm_java_home)))
        llvm_runtime_dir = {
            "source_type": "dependency",
            "dependency": "LLVM_JAVA_HOME",
            "path": f"{jdk_lib_dir}/<lib:*>",
        }
        register_project(llvm_java_home_dep)
    else:
        llvm_runtime_dir = []

    if mx.is_windows():
        platform_specific_excludes = [
            "bin/<exe:*>",
            "bin/server",
        ]
    else:
        platform_specific_excludes = [
            "bin",
            "lib/server",
            "lib/<exe:jexec>",
            "man",
        ]
    register_distribution(mx.LayoutDirDistribution(
        _suite, "ESPRESSO_RUNTIME_DIR",
        deps=[],
        layout={
            "META-INF/resources/java/espresso-runtime/<os>/<arch>/": {
                "source_type": "dependency",
                "dependency": "JAVA_HOME",
                "path": "*",
                "exclude": [
                    "include",
                    "jmods",
                    "lib/ct.sym",
                    "lib/jfr",
                    "lib/jvm.cfg",
                    "lib/src.zip",
                    "lib/static",
                ] + platform_specific_excludes,
            },
            "META-INF/resources/java/espresso-runtime/<os>/<arch>/lib/llvm/": llvm_runtime_dir,
        },
        path=None,
        platformDependent=True,
        platforms=[
            "linux-amd64",
            "linux-aarch64",
            "darwin-amd64",
            "darwin-aarch64",
            "windows-amd64",
        ],
        theLicense=None,  # TODO
        hashEntry="META-INF/resources/java/espresso-runtime/<os>/<arch>/sha256",
        fileListEntry="META-INF/resources/java/espresso-runtime/<os>/<arch>/files",
        maven=False))

    if register_project:
        # com.oracle.truffle.espresso.resources.runtime
        register_project(EspressoRuntimeResourceProject(_suite, 'src', '?', _suite.defaultLicense))  # TODO theLicense

    runtime_resources_jar = mx_jardistribution.JARDistribution(
        _suite, "ESPRESSO_RUNTIME_RESOURCES", None, None, None,
        moduleInfo={
            "name": "org.graalvm.espresso.resources.runtime",
        },
        deps=[
            "com.oracle.truffle.espresso.resources.runtime",
            "ESPRESSO_RUNTIME_DIR",
        ],
        mainClass=None,
        excludedLibs=[],
        distDependencies=["truffle:TRUFFLE_API"],
        javaCompliance=None,
        platformDependent=True,
        theLicense=None,  # TODO
        compress=True,
        useModulePath=True,
        description="Runtime environment used by the Java on Truffle (aka Espresso) implementation",
        maven={
            "tag": ["default", "public"],
        })
    register_distribution(runtime_resources_jar)


class JavaHomeDependency(mx.ArchivableProject):
    def __init__(self, suite, name, java_home):
        assert isabs(java_home)
        self.java_home = java_home
        self.is_ee_implementor = mx_sdk_vm.ee_implementor(java_home)
        if self.is_ee_implementor:
            the_license = "Oracle Proprietary"
        else:
            the_license = "GPLv2-CPE"
        super().__init__(suite, name, deps=[], workingSets=[], theLicense=the_license)

    def output_dir(self):
        return self.java_home

    def archive_prefix(self):
        return ""

    def getResults(self):
        return JavaHomeDependency.walk(self.java_home)


class EspressoRuntimeResourceProject(mx.JavaProject):
    def __init__(self, suite, subDir, runtime_type, theLicense):
        name = f'com.oracle.truffle.espresso.resources.runtime'
        project_dir = join(suite.dir, subDir, name)
        deps = ['truffle:TRUFFLE_API']
        super().__init__(suite, name, subDir=subDir, srcDirs=[], deps=deps,
                         javaCompliance='17+', workingSets='Truffle', d=project_dir,
                         theLicense=theLicense)
        self.declaredAnnotationProcessors = ['truffle:TRUFFLE_DSL_PROCESSOR']
        self.resource_id = "espresso-runtime"
        self.checkstyleProj = name
        self.checkPackagePrefix = False

    def getBuildTask(self, args):
        jdk = mx.get_jdk(self.javaCompliance, tag=mx.DEFAULT_JDK_TAG, purpose='building ' + self.name)
        return EspressoRuntimeResourceBuildTask(args, self, jdk)


class EspressoRuntimeResourceBuildTask(mx.JavaBuildTask):
    def __str__(self):
        return f'Generating {self.subject.name} internal resource and compiling it with {self._getCompiler().name()}'

    @staticmethod
    def _template_file():
        return join(_suite.mxDir, 'espresso_runtime_resource.template')

    def needsBuild(self, newestInput):
        is_needed, reason = mx.ProjectBuildTask.needsBuild(self, newestInput)
        if is_needed:
            return True, reason
        proj = self.subject
        for outDir in [proj.output_dir(), proj.source_gen_dir()]:
            if not os.path.exists(outDir):
                return True, f"{outDir} does not exist"
        template_ts = mx.TimeStampFile.newest([
            EspressoRuntimeResourceBuildTask._template_file(),
            __file__
        ])
        if newestInput is None or newestInput.isOlderThan(template_ts):
            newestInput = template_ts
        return super().needsBuild(newestInput)

    @staticmethod
    def _target_file(root, pkg_name):
        target_folder = join(root, pkg_name.replace('.', os.sep))
        target_file = join(target_folder, 'EspressoRuntimeResource.java')
        return target_file


    def _collect_files(self):
        if self._javafiles is not None:
            # already collected
            return self
        # collect project files first, then extend with generated resource
        super(EspressoRuntimeResourceBuildTask, self)._collect_files()
        javafiles = self._javafiles
        prj = self.subject
        gen_src_dir = prj.source_gen_dir()
        pkg_name = prj.name
        target_file = EspressoRuntimeResourceBuildTask._target_file(gen_src_dir, pkg_name)
        if not target_file in javafiles:
            bin_dir = prj.output_dir()
            target_class = join(bin_dir, relpath(target_file, gen_src_dir)[:-len('.java')] + '.class')
            javafiles[target_file] = target_class
        # Remove annotation processor generated files.
        javafiles = {k: v for k, v in javafiles.items() if k == target_file}
        self._javafiles = javafiles
        return self

    def build(self):
        prj = self.subject
        pkg_name = prj.name
        with open(EspressoRuntimeResourceBuildTask._template_file(), 'r', encoding='utf-8') as f:
            file_content = f.read()
        subst_eng = mx_subst.SubstitutionEngine()
        subst_eng.register_no_arg('package', pkg_name)
        subst_eng.register_no_arg('resourceId', prj.resource_id)
        file_content = subst_eng.substitute(file_content)
        target_file = EspressoRuntimeResourceBuildTask._target_file(prj.source_gen_dir(), pkg_name)
        mx.ensure_dir_exists(dirname(target_file))
        with mx.SafeFileCreation(target_file) as sfc, open(sfc.tmpPath, 'w', encoding='utf-8') as f:
            f.write(file_content)
        super(EspressoRuntimeResourceBuildTask, self).build()


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
    # Always append `truffle` to the list of JVMs in `lib/jvm.cfg`.
    jvm_configs=[{
        'configs': ['-truffle KNOWN'],
        'priority': 2,  # 0 is invalid; < 0 prepends to the default configs; > 0 appends
    }],
    priority=2,
    stability=_espresso_stability,
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

jvm_cfg_component = mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Espresso Standalone jvm.cfg',
    short_name='ejc',
    dir_name='.',
    installable_id='espresso',
    installable=True,
    license_files=[],
    third_party_license_files=[],
    dependencies=['Java on Truffle'],
    jar_distributions=[],
    support_distributions=[],
    launcher_configs=[],
    # Espresso standalones prepend `truffle` to the list of JVMs in `lib/jvm.cfg`
    # when the Espresso native library is built.
    jvm_configs=[{
        'configs': ['-truffle KNOWN'],
        'priority': lambda: 1 if mx_sdk_vm_impl._skip_libraries(espresso_library_config) else -1,  # 0 is invalid; < 0 prepends to the default configs; > 0 appends
    }],
    stability=_espresso_stability,
)
mx_sdk_vm.register_graalvm_component(jvm_cfg_component)


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso_launcher, '[args]'],
    'espresso-standalone': [_run_espresso_standalone, '[args]'],
    'java-truffle': [_run_java_truffle, '[args]'],
    'espresso-meta': [_run_espresso_meta, '[args]'],
})


# Build configs
def register_espresso_envs(suite):
    # pylint: disable=bad-whitespace
    # pylint: disable=line-too-long
    tools = ['cov', 'dap', 'ins', 'insight', 'insightheap', 'lsp', 'pro', 'truffle-json']
    _llvm_toolchain_wrappers = ['bgraalvm-native-clang', 'bgraalvm-native-clang-cl', 'bgraalvm-native-clang++', 'bgraalvm-native-flang', 'bgraalvm-native-ld', 'bgraalvm-native-binutil']
    if espresso_llvm_java_home:
        mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'ejvm'       , 'ellvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn'                                                    , 'elau'                                                                                                                                                ] + tools, suite, env_file='jvm-llvm')
        mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'ejvm'       , 'ellvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn'         , 'svm', 'svmt'         , 'svmsl'          , 'tflm', 'elau', 'lg', 'bespresso', 'sjavavm', 'spolyglot'] + _llvm_toolchain_wrappers + tools, suite, env_file='jvm-ce-llvm')
        mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'ejvm'       , 'ellvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tfle', 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn', 'cmpee', 'svm', 'svmt', 'svmee', 'svmte', 'svmsl', 'tflllm', 'tflm', 'elau', 'lg', 'bespresso', 'sjavavm', 'spolyglot'] + _llvm_toolchain_wrappers + tools, suite, env_file='jvm-ee-llvm')
        mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'ejvm', 'ejc', 'ellvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn'         , 'svm', 'svmt'         , 'svmsl'          , 'tflm'                                      , 'spolyglot'] + _llvm_toolchain_wrappers + tools, suite, env_file='native-ce-llvm')
        mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'ejvm', 'ejc', 'ellvm', 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tfle', 'cmp', 'antlr4', 'llrc', 'llrlf', 'llrn', 'cmpee', 'svm', 'svmt', 'svmsl', 'svmee', 'svmte', 'tflllm', 'tflm'                                      , 'spolyglot'] + _llvm_toolchain_wrappers + tools, suite, env_file='native-ee-llvm')
    else:
        mx_sdk_vm.register_vm_config('espresso-jvm',       ['java', 'ejvm'                , 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp'                                                                                       , 'elau'                                                                                                                                                ] + tools, suite, env_file='jvm')
        mx_sdk_vm.register_vm_config('espresso-jvm-ce',    ['java', 'ejvm'                , 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp'                                            , 'svm', 'svmt', 'svmsl'                   , 'tflm', 'elau', 'lg', 'bespresso', 'sjavavm', 'spolyglot'                                                                                                     ] + tools, suite, env_file='jvm-ce')
        mx_sdk_vm.register_vm_config('espresso-jvm-ee',    ['java', 'ejvm'                , 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tfle', 'cmp'                                   , 'cmpee', 'svm', 'svmt', 'svmsl', 'svmee', 'svmte', 'tflllm', 'tflm', 'elau', 'lg', 'bespresso', 'sjavavm', 'spolyglot'                                                                                                     ] + tools, suite, env_file='jvm-ee')
        mx_sdk_vm.register_vm_config('espresso-native-ce', ['java', 'ejvm', 'ejc'         , 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc'        , 'cmp'                                            , 'svm', 'svmt', 'svmsl'                   , 'tflm'                                      , 'spolyglot'                                                                                                     ] + tools, suite, env_file='native-ce')
        mx_sdk_vm.register_vm_config('espresso-native-ee', ['java', 'ejvm', 'ejc'         , 'libpoly', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tfle', 'cmp'                                   , 'cmpee', 'svm', 'svmt', 'svmsl', 'svmee', 'svmte', 'tflllm', 'tflm'                                      , 'spolyglot'                                                                                                     ] + tools, suite, env_file='native-ee')


register_espresso_envs(_suite)
