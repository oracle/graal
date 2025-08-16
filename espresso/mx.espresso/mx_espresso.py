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

import io
import os
import shutil
import signal
import subprocess
import argparse
import sys
from abc import ABCMeta, abstractmethod

import mx
import mx_jardistribution
import mx_sdk_vm_ng
import mx_subst
import mx_util
import mx_gate
import mx_espresso_benchmarks
import mx_sdk_vm
import mx_sdk_vm_impl
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from os.path import join, exists, dirname, relpath
from import_order import verify_order, validate_format
from mx_truffle import resolve_truffle_dist_names

_suite = mx.suite('espresso')

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_shaded import ShadedLibraryProject  # pylint: disable=unused-import
from mx_sdk_vm_ng import NativeImageLibraryProject, ThinLauncherProject, JavaHomeDependency, DynamicPOMDistribution, ExtractedEngineResources, DeliverableStandaloneArchive, StandaloneLicenses  # pylint: disable=unused-import

# JDK compiled with the Sulong toolchain.
espresso_llvm_java_home = mx.get_env('ESPRESSO_LLVM_JAVA_HOME') or mx.get_env('LLVM_JAVA_HOME')


def _has_native_espresso_standalone():
    return bool(mx.distribution('ESPRESSO_NATIVE_STANDALONE', fatalIfMissing=False))


def _espresso_launcher_command(args):
    """Espresso launcher running on top of GraalVM + arguments"""
    jacoco_args = ['--vm.' + arg for arg in mx_gate.get_jacoco_agent_args() or []]
    java = join(mx.distribution('ESPRESSO_JVM_STANDALONE').get_output(), 'bin', mx.exe_suffix('espresso'))
    return [java] + jacoco_args + args


def _java_truffle_command(args):
    """Java launcher using libjavavm in Espresso native standalone + arguments"""
    java = join(mx.distribution('ESPRESSO_NATIVE_STANDALONE').get_output(), 'bin', mx.exe_suffix('java'))
    return [java, '-truffle'] + args


def _espresso_standalone_command(args, with_sulong=False, allow_jacoco=True, jdk=None, use_optimized_runtime=True):
    """Espresso standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    distributions = ['ESPRESSO', 'ESPRESSO_LAUNCHER', 'ESPRESSO_LIBS_RESOURCES', 'ESPRESSO_RUNTIME_RESOURCES', 'TRUFFLE_NFI_LIBFFI']
    distributions += resolve_truffle_dist_names(use_optimized_runtime=use_optimized_runtime)
    if with_sulong:
        distributions += ['SULONG_NFI', 'SULONG_NATIVE']
    if allow_jacoco:
        jacoco_args = ['--vm.' + arg for arg in mx_gate.get_jacoco_agent_args() or []]
    else:
        jacoco_args = []
    jdk = jdk or mx.get_jdk()
    if jdk.version >= mx.VersionSpec("24"):
        # adopt "JDK-8342380: Implement JEP 498: Warn upon Use of Memory-Access Methods in sun.misc.Unsafe"
        vm_args.append('--sun-misc-unsafe-memory-access=allow')
    if not use_optimized_runtime:
        vm_args.append('-Dpolyglot.engine.WarnInterpreterOnly=false')
    return (
        vm_args
        + mx.get_runtime_jvm_args(distributions, jdk=jdk)
        + jacoco_args
        # This is needed for Truffle since JEP 472: Prepare to Restrict the Use of JNI
        + ['--enable-native-access=org.graalvm.truffle']
        + ["--module", "org.graalvm.espresso.launcher/" + mx.distribution('ESPRESSO_LAUNCHER').mainClass] + args
    )

def javavm_deps():
    result = [espresso_runtime_resources_distribution()]
    if mx.suite('truffle-enterprise', fatalIfMissing=False):
        result.append('truffle-enterprise:TRUFFLE_ENTERPRISE')
    if mx.suite('regex', fatalIfMissing=False):
        result.append('regex:TREGEX')
    return result

def javavm_build_args():
    result = []
    # GR-64948: On GraalVM 21 CopyLanguageResources is incorrectly detected as experimental
    if mx_sdk_vm_ng.get_bootstrap_graalvm_version() >= mx.VersionSpec("24.0"):
        result += ['--enable-monitoring=threaddump', '-H:+CopyLanguageResources']
    else:
        result += mx_sdk_vm_impl.svm_experimental_options(['-H:+DumpThreadStacksOnSignal', '-H:+CopyLanguageResources'])
    if mx_sdk_vm_ng.get_bootstrap_graalvm_version() >= mx.VersionSpec("25.0"):
        result.append('-H:-IncludeLanguageResources')
    if mx.is_linux() and (mx.get_os_variant() != "musl" and mx_subst.path_substitutions.substitute("<multitarget_libc_selection>") == "glibc"):
        # Currently only enabled if the native image build runs on glibc and also targets glibc.
        # In practice it's enough that host and target are matching, but we do not get this info out of mx.
        result += [
            '-Dpolyglot.image-build-time.PreinitializeContexts=java',
            '-Dpolyglot.image-build-time.PreinitializeContextsWithNative=true',
        ]
    return result

def java_community_deps():
    # extra dependencies
    if get_java_home_dep().is_ee_implementor:
        return []
    else:
        return ["ESPRESSO_RUNTIME_RESOURCES"]

def jvm_standalone_deps():
    # extra dependencies for JVM_STANDALONE_JARS
    result = []
    if mx.suite('regex', fatalIfMissing=False):
        result.append('regex:TREGEX')
    if jvm_standalone_with_llvm():
        result += [
            "sulong:SULONG_CORE",
            "sulong:SULONG_NATIVE",
            "sulong:SULONG_NFI",
        ]
    if mx.suite('truffle-enterprise', fatalIfMissing=False):
        result.append('truffle-enterprise:TRUFFLE_ENTERPRISE')
    return result

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
    """Run Espresso launcher within a JVM standalone"""
    return mx.run(_espresso_launcher_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_espresso_embedded(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    """Run embedded Espresso (not as part of GraalVM) from distribution jars"""
    return mx.run_java(_espresso_standalone_command(args, with_sulong=True), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_java_truffle(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    """Run espresso through the standard java launcher within a native standalone"""
    return mx.run(_java_truffle_command(args), cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, timeout=timeout, on_timeout=_send_sigquit)


def _run_espresso(args=None, cwd=None, nonZeroIsFatal=True, out=None, err=None, timeout=None):
    if _has_native_espresso_standalone():
        return _run_java_truffle(args, cwd, nonZeroIsFatal, out, err, timeout)
    else:
        return _run_espresso_launcher(args, cwd, nonZeroIsFatal, out, err, timeout)


def _run_espresso_meta(args, nonZeroIsFatal=True, timeout=None):
    """Run Espresso (standalone) on Espresso (launcher)"""
    return _run_espresso([
        '--vm.Xss4m',
    ] + _espresso_standalone_command(args, allow_jacoco=False, jdk=_espresso_input_jdk(), use_optimized_runtime=False), nonZeroIsFatal=nonZeroIsFatal, timeout=timeout)


def _run_verify_imports(s):
    # Look for the format specification in the suite
    prefs = s.eclipse_settings_sources().get('org.eclipse.jdt.ui.prefs')
    prefix_order = []
    if prefs:
        for pref in prefs:
            with open(pref) as f:
                for line in f.readlines():
                    if line.startswith('org.eclipse.jdt.ui.importorder'):
                        key_value_sep_index = line.find('=')
                        if key_value_sep_index != -1:
                            value = line.strip()[key_value_sep_index + 1:]
                            prefix_order = value.split(';')

    # Validate import order format
    err = validate_format(prefix_order)
    if err:
        mx.abort(err)

    # Find invalid files
    invalid_files = []
    for project in s.projects:
        if getattr(project, "skipVerifyImports", False):
            continue
        output_root = project.get_output_root()
        for src_dir in project.source_dirs():
            if src_dir.startswith(output_root):
                # ignore source that are under the output root since
                # those are probably generated
                continue
            invalid_files += verify_order(src_dir, prefix_order)

    if invalid_files:
        mx.abort("The following files have wrong imports order:\n" + '\n'.join(invalid_files))

    print("All imports correctly ordered!")

def _run_verify_imports_espresso(args):
    if args:
        mx.abort("No arguments expected for verify-imports")
    _run_verify_imports(_suite)

class EspressoTags:
    jackpot = 'jackpot'
    verify = 'verify'
    imports = 'imports'


def _espresso_gate_runner(args, tasks):
    # Jackpot configuration is inherited from Truffle.
    with Task('Jackpot', tasks, tags=[EspressoTags.jackpot]) as t:
        if t:
            jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)

    with Task('Espresso: verify import order', tasks, tags=[EspressoTags.imports]) as t:
        if t:
            _run_verify_imports(_suite)
            _run_verify_imports(mx.suite('espresso-shared'))

    mokapot_header_gate_name = 'Verify consistency of mokapot headers'
    with Task(mokapot_header_gate_name, tasks, tags=[EspressoTags.verify]) as t:
        if t:
            run_instructions = "$ mx --dynamicimports=/substratevm --native-images=lib:javavm gate --all-suites --task '{}'".format(mokapot_header_gate_name)
            errors = False
            mokapot_dir = join(mx.project('com.oracle.truffle.espresso.mokapot').dir, 'include')
            libjavavm_dir = mx.project("javavm").get_output_root()

            for header in ['libjavavm_dynamic.h', 'graal_isolate_dynamic.h']:
                committed_header = join(mokapot_dir, header)
                if not os.path.exists(committed_header):
                    mx.abort("Cannot locate '{}'. Was the file moved or renamed?".format(committed_header))

                generated_header = join(libjavavm_dir, header)
                if not os.path.exists(generated_header):
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


class AbstractSimpleGeneratedFileProject(mx.Project, metaclass=ABCMeta):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, **attr):
        super().__init__(suite, name, "", [], deps, workingSets, suite.dir, theLicense, **attr)

    def isPlatformDependent(self):
        return True

    def isJDKDependent(self):
        return False

    def output_dir(self):
        return join(self.get_output_base(), self.name)

    @abstractmethod
    def output_file_name(self):
        pass

    @abstractmethod
    def contents(self):
        pass

    def output_file(self):
        return join(self.output_dir(), self.output_file_name())

    def getArchivableResults(self, use_relpath=True, single=False):
        yield self.output_file(), self.output_file_name()

    def getBuildTask(self, args):
        return SimpleGeneratedFileBuildTask(self, args, 1)


class SimpleGeneratedFileBuildTask(mx.BuildTask):
    subject: AbstractSimpleGeneratedFileProject

    def __str__(self):
        return f'Create {self.subject}'

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_file())

    def needsBuild(self, newestInput):
        r = super().needsBuild(newestInput)
        if r[0]:
            return r
        out_file = self.subject.output_file()
        if not exists(out_file):
            return True, out_file + " doesn't exist"
        expected = self.subject.contents()
        with open(out_file, 'r', encoding='utf-8') as f:
            if f.read() != expected:
                return True, "Outdated content"
        return False, None

    def clean(self, forBuild=False):
        if exists(self.subject.output_dir()):
            mx.rmtree(self.subject.output_dir())

    def build(self):
        mx_util.ensure_dir_exists(self.subject.output_dir())
        with mx_util.SafeFileCreation(self.subject.output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as outfile:
            outfile.write(self.subject.contents())


class EspressoLegacyNativeImageProperties(AbstractSimpleGeneratedFileProject):
    def isPlatformDependent(self):
        return True

    def isJDKDependent(self):
        return False

    def output_file_name(self):
        return "native-image.properties"

    def contents(self):
        with_tregex = mx_sdk_vm_impl.has_component('TRegex')
        with_pre_init = mx.is_linux() and mx.get_os_variant() != "musl"
        contents = "Requires = language:nfi"
        if with_tregex:
            contents = contents + " language:regex"
        if with_pre_init:
            contents = contents + """

JavaArgs = -Dpolyglot.image-build-time.PreinitializeContexts=java \\
      -Dpolyglot.image-build-time.PreinitializeContextsWithNative=true
"""
        return contents


class EspressoReleaseFileProject(AbstractSimpleGeneratedFileProject):
    def isPlatformDependent(self):
        return True

    def isJDKDependent(self):
        return False

    def output_file_name(self):
        return "release"

    def contents(self):
        sorted_suites = sorted(mx.suites(), key=lambda s: s.name)
        parent_release_file = join(get_java_home_dep().java_home, 'release')
        return mx_sdk_vm_impl.BaseGraalVmLayoutDistribution._get_metadata(sorted_suites, parent_release_file, java_version=_espresso_input_jdk().version)


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _espresso_gate_runner)

espresso_library_config = mx_sdk_vm.LanguageLibraryConfig(
    language='java',
    jar_distributions=['espresso:LIB_JAVAVM'],
    build_args=[
        '-Dpolyglot.java.GuestFieldOffsetStrategy=graal',
        '-R:+EnableSignalHandling',
        '-R:+InstallSegfaultHandler',
        '--enable-monitoring=threaddump',
    ] + mx_sdk_vm_impl.svm_experimental_options([
        '-H:-JNIExportSymbols',
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
    license_files=['LICENSE_JAVAONTRUFFLE'],
    third_party_license_files=[],
    dependencies=['Truffle', 'nfi-libffi', 'ejvm'],
    truffle_jars=['espresso:ESPRESSO', 'espresso-shared:ESPRESSO_SHARED'],
    support_distributions=['espresso:ESPRESSO_GRAALVM_SUPPORT'],
    library_configs=[espresso_library_config],
    priority=1,
    stability=_espresso_stability,
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
        dependencies=['Java on Truffle', 'LLVM Runtime Native'],
        support_distributions=['espresso:ESPRESSO_LLVM_SUPPORT'],
        priority=2,
        stability=_espresso_stability,
    ))


def _resource_license(ee_implementor):
    if ee_implementor:
        return "GFTC"
    else:
        return "GPLv2-CPE"


_espresso_input_jdk_value = None
_java_home_dep = None
_llvm_java_home_dep = None
_jvm_standalone_with_llvm = None


def _espresso_input_jdk():
    global _espresso_input_jdk_value
    if not _espresso_input_jdk_value:
        espresso_java_home = mx.get_env('ESPRESSO_JAVA_HOME')
        if espresso_java_home:
            _espresso_input_jdk_value = mx.JDKConfig(espresso_java_home)
        else:
            _espresso_input_jdk_value = mx_sdk_vm.base_jdk()
    return _espresso_input_jdk_value


def get_java_home_dep():
    global _java_home_dep
    if _java_home_dep is None:
        _java_home_dep = JavaHomeDependency(_suite, "ESPRESSO_JAVA_HOME", _espresso_input_jdk().home)
    return _java_home_dep


def get_llvm_java_home_dep():
    global _llvm_java_home_dep
    if _llvm_java_home_dep is None and jvm_standalone_with_llvm():
        _llvm_java_home_dep = JavaHomeDependency(_suite, "ESPRESSO_LLVM_JAVA_HOME", espresso_llvm_java_home)
    return _llvm_java_home_dep

def jvm_standalone_with_llvm():
    global _jvm_standalone_with_llvm
    if _jvm_standalone_with_llvm is None:
        from_env = mx.get_env('JVM_STANDALONE_WITH_LLVM')
        if from_env is None:
            _jvm_standalone_with_llvm = bool(espresso_llvm_java_home)
        elif from_env.lower() in ('1', 'yes', 'true'):
            if espresso_llvm_java_home:
                _jvm_standalone_with_llvm = True
            else:
                raise mx.abort(f"JVM_STANDALONE_WITH_LLVM was requested ('{from_env}) but ESPRESSO_LLVM_JAVA_HOME was not specified")
        else:
            _jvm_standalone_with_llvm = False
    return _jvm_standalone_with_llvm

def _jdk_lib_dir():
    if mx.is_windows():
        return 'bin'
    else:
        return 'lib'

mx_subst.path_substitutions.register_no_arg('jdk_lib_dir', _jdk_lib_dir)


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    java_home_dep = get_java_home_dep()
    register_distribution(java_home_dep)  # a "library" registered as a distribution is not ideal

    llvm_java_home_dep = get_llvm_java_home_dep()
    if jvm_standalone_with_llvm():
        # Conditionally fill the ESPRESSO_LLVM_SUPPORT distribution if a Java home with LLVM bitcode is provided.
        lib_prefix = mx.add_lib_prefix('')
        lib_suffix = mx.add_lib_suffix('')
        jdk_lib_dir = 'bin' if mx.is_windows() else 'lib'
        register_distribution(llvm_java_home_dep)
        register_distribution(mx.LayoutTARDistribution(_suite, 'ESPRESSO_LLVM_SUPPORT', [], {
            "lib/llvm/default/": [
                f"dependency:ESPRESSO_LLVM_JAVA_HOME/{jdk_lib_dir}/{lib_prefix}*{lib_suffix}",
                "dependency:ESPRESSO_LLVM_JAVA_HOME/release"
            ],
        }, None, True, None))
        register_distribution(mx.LayoutDirDistribution(_suite, 'ESPRESSO_STANDALONE_LLVM_HOME', [], {
            "./": [
                {
                    "source_type": "extracted-dependency",
                    "dependency": "sulong:SULONG_BITCODE_HOME",
                    "path": "*",
                    "exclude": [
                        # "native/lib/*++*",
                    ],
                },
                {
                    "source_type": "extracted-dependency",
                    "dependency": "sulong:SULONG_NATIVE_HOME",
                    "path": "*",
                    "exclude": [
                        "native/cmake",
                        "native/include",
                        # "native/lib/*++*",
                    ],
                },
            ],
        }, None, True, None, platforms='local'))
        _suite.dependency('espresso').relative_home_paths["llvm"] = "../languages/llvm"
    else:
        # An empty, ignored ESPRESSO_LLVM_SUPPORT distribution is created if no LLVM bitcode are available
        ignore_msg = "ESPRESSO_LLVM_JAVA_HOME was not set or JVM_STANDALONE_WITH_LLVM was false"
        register_distribution(mx.LayoutTARDistribution(_suite, 'ESPRESSO_LLVM_SUPPORT', [], {
            "lib/llvm/default/": [],
        }, None, True, None, ignore=ignore_msg))
        register_distribution(mx.LayoutDirDistribution(_suite, 'ESPRESSO_STANDALONE_LLVM_HOME', [], {
            "./": [],
        }, None, True, None, platforms='local', ignore=ignore_msg))

    register_espresso_runtime_resources(register_project, register_distribution, _suite)
    deliverable_variant = mx.get_env('ESPRESSO_DELIVERABLE_VARIANT')
    if deliverable_variant:
        suffix = '-' + deliverable_variant.lower()
        dist_suffix = '_' + deliverable_variant.upper()
    else:
        suffix = ''
        dist_suffix = ''
    register_distribution(DeliverableStandaloneArchive(_suite,
        standalone_dist='ESPRESSO_NATIVE_STANDALONE',
        community_archive_name=f"espresso-community-java{java_home_dep.major_version}{suffix}",
        enterprise_archive_name=f"espresso-java{java_home_dep.major_version}{suffix}",
        community_dist_name=f'GRAALVM_ESPRESSO_COMMUNITY_JAVA{java_home_dep.major_version}{dist_suffix}',
        enterprise_dist_name=f'GRAALVM_ESPRESSO_JAVA{java_home_dep.major_version}{dist_suffix}'))

    mx_espresso_benchmarks.mx_register_dynamic_suite_constituents(register_project, register_distribution)


def espresso_resources_suite(java_home_dep=None):
    # Espresso resources are in the CE/EE suite depending on espresso java home type
    # or in espresso if there is no EE suite
    java_home_dep = java_home_dep or get_java_home_dep()
    if java_home_dep.is_ee_implementor and mx.suite('espresso-tests', fatalIfMissing=False):
        return 'espresso-tests'
    else:
        return 'espresso'


def espresso_runtime_resources_distribution(java_home_dep=None):
    return espresso_resources_suite(java_home_dep=java_home_dep) + ':ESPRESSO_RUNTIME_RESOURCES'


def register_espresso_runtime_resources(register_project, register_distribution, suite):
    if espresso_resources_suite() != suite.name:
        return
    register_espresso_runtime_resource(get_java_home_dep(), get_llvm_java_home_dep(), register_project, register_distribution, suite, True)
    extra_java_homes = mx.get_env('EXTRA_ESPRESSO_JAVA_HOMES')
    if extra_java_homes:
        extra_java_homes = extra_java_homes.split(os.pathsep)
    else:
        extra_java_homes = []
    extra_llvm_java_homes = mx.get_env('EXTRA_ESPRESSO_LLVM_JAVA_HOMES')
    if extra_llvm_java_homes:
        extra_llvm_java_homes = extra_llvm_java_homes.split(os.pathsep)
    else:
        extra_llvm_java_homes = []
    if extra_llvm_java_homes:
        if len(extra_llvm_java_homes) != len(extra_java_homes):
            raise mx.abort("EXTRA_ESPRESSO_LLVM_JAVA_HOMES must either be empty or contain as many elements as EXTRA_ESPRESSO_JAVA_HOMES")
    else:
        extra_llvm_java_homes = [None] * len(extra_java_homes)

    versions = {get_java_home_dep().major_version}
    for extra_java_home, extra_llvm_java_home in zip(extra_java_homes, extra_llvm_java_homes):
        extra_java_home_dep = JavaHomeDependency(suite, "ESPRESSO_JAVA_HOME_<version>", extra_java_home)
        if extra_java_home_dep.major_version in versions:
            raise mx.abort("Each entry in EXTRA_ESPRESSO_JAVA_HOMES should have a different java version, and they should all be different from ESPRESSO_JAVA_HOME's version")
        versions.add(extra_java_home_dep.major_version)
        if extra_llvm_java_home:
            extra_llvm_java_home_dep = JavaHomeDependency(suite, "ESPRESSO_LLVM_JAVA_HOME_<version>", extra_llvm_java_home)
        else:
            extra_llvm_java_home_dep = None
        if espresso_resources_suite(extra_java_home_dep) != suite.name:
            continue
        register_distribution(extra_java_home_dep)
        if extra_llvm_java_home_dep:
            register_distribution(extra_llvm_java_home_dep)
        register_espresso_runtime_resource(extra_java_home_dep, extra_llvm_java_home_dep, register_project, register_distribution, suite, False)


def register_espresso_runtime_resource(java_home_dep, llvm_java_home_dep, register_project, register_distribution, suite, is_main):
    is_ee_suite = suite != _suite
    if llvm_java_home_dep:
        lib_prefix = mx.add_lib_prefix('')
        lib_suffix = mx.add_lib_suffix('')
        jdk_lib_dir = 'bin' if mx.is_windows() else 'lib'

        if mx.get_env("SKIP_ESPRESSO_LLVM_CHECK", 'false').lower() in ('false', '0', 'no'):
            libjava = join(llvm_java_home_dep.java_home, jdk_lib_dir, f'{lib_prefix}java{lib_suffix}')
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
        llvm_runtime_dir = {
            "source_type": "dependency",
            "dependency": llvm_java_home_dep.qualifiedName(),
            "path": f"{jdk_lib_dir}/<lib:*>",
        }
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
    if java_home_dep.is_ee_implementor:
        espresso_runtime_resource_name = f"jdk{java_home_dep.major_version}"
    else:
        espresso_runtime_resource_name = f"openjdk{java_home_dep.major_version}"
    runtime_dir_dist_name = f"ESPRESSO_RUNTIME_DIR_{java_home_dep.major_version}"
    register_distribution(mx.LayoutDirDistribution(
        suite, runtime_dir_dist_name,
        deps=[],
        layout={
            f"META-INF/resources/java/espresso-runtime-{espresso_runtime_resource_name}/<os>/<arch>/": {
                "source_type": "dependency",
                "dependency": java_home_dep.qualifiedName(),
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
            f"META-INF/resources/java/espresso-runtime-{espresso_runtime_resource_name}/<os>/<arch>/lib/llvm/": llvm_runtime_dir,
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
        theLicense=_resource_license(java_home_dep.is_ee_implementor),
        hashEntry=f"META-INF/resources/java/espresso-runtime-{espresso_runtime_resource_name}/<os>/<arch>/sha256",
        fileListEntry=f"META-INF/resources/java/espresso-runtime-{espresso_runtime_resource_name}/<os>/<arch>/files",
        maven=False))
    runtime_resources_project_name = f'com.oracle.truffle.espresso.resources.runtime.{espresso_runtime_resource_name}'
    if register_project:
        # com.oracle.truffle.espresso.resources.runtime
        register_project(EspressoRuntimeResourceProject(suite, runtime_resources_project_name, 'src', espresso_runtime_resource_name, suite.defaultLicense))

    if is_main:
        runtime_resources_dist_name = "ESPRESSO_RUNTIME_RESOURCES"
    else:
        runtime_resources_dist_name = f"ESPRESSO_RUNTIME_RESOURCES_{java_home_dep.major_version}"
    register_distribution(mx_jardistribution.JARDistribution(
        suite, runtime_resources_dist_name, None, None, None,
        moduleInfo={
            "name": f"org.graalvm.espresso.resources.runtime.{espresso_runtime_resource_name}",
        },
        deps=[
            runtime_resources_project_name,
            runtime_dir_dist_name,
        ],
        mainClass=None,
        excludedLibs=[],
        distDependencies=["truffle:TRUFFLE_API"],
        javaCompliance=None,
        platformDependent=True,
        theLicense=_resource_license(java_home_dep.is_ee_implementor),
        compress=True,
        useModulePath=True,
        description="Runtime environment used by the Java on Truffle (aka Espresso) implementation",
        maven={
            "groupId": "org.graalvm.espresso",
            "artifactId": "espresso-runtime-resources-" + espresso_runtime_resource_name,
            "tag": ["default", "public"],
        } if java_home_dep.is_ee_implementor == is_ee_suite else False))


class EspressoRuntimeResourceProject(mx.JavaProject):
    def __init__(self, suite, name, subDir, runtime_name, theLicense):
        project_dir = join(suite.dir, subDir, name)
        deps = ['truffle:TRUFFLE_API']
        super().__init__(suite, name, subDir=subDir, srcDirs=[], deps=deps,
                         javaCompliance='17+', workingSets='Truffle', d=project_dir,
                         theLicense=theLicense)
        self.declaredAnnotationProcessors = ['truffle:TRUFFLE_DSL_PROCESSOR']
        self.resource_id = "espresso-runtime-" + runtime_name
        self.checkstyleProj = name
        self.checkPackagePrefix = False

    def getBuildTask(self, args):
        jdk = mx.get_jdk(self.javaCompliance, tag=mx.DEFAULT_JDK_TAG, purpose='building ' + self.name)
        return EspressoRuntimeResourceBuildTask(args, self, jdk)


class EspressoRuntimeResourceBuildTask(mx.JavaBuildTask):
    subject: EspressoRuntimeResourceProject

    def __str__(self):
        return f'Generating {self.subject.name} internal resource and compiling it with {self._getCompiler().name()}'

    @staticmethod
    def _template_file():
        return join(_suite.mxDir, 'espresso_runtime_resource.template')

    def witness_file(self):
        return join(self.subject.get_output_root(), 'witness')

    def witness_contents(self):
        return self.subject.resource_id

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
        witness_file = self.witness_file()
        if not exists(witness_file):
            return True, witness_file + " doesn't exist"
        expected = self.witness_contents()
        with open(witness_file, 'r', encoding='utf-8') as f:
            if f.read() != expected:
                return True, "Outdated content"
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
        mx_util.ensure_dir_exists(dirname(target_file))
        with mx_util.SafeFileCreation(target_file) as sfc, open(sfc.tmpPath, 'w', encoding='utf-8') as f:
            f.write(file_content)
        super(EspressoRuntimeResourceBuildTask, self).build()
        witness_file = self.witness_file()
        mx_util.ensure_dirname_exists(witness_file)
        with mx_util.SafeFileCreation(witness_file) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as outfile:
            outfile.write(self.witness_contents())


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Espresso libjvm',
    short_name='ejvm',
    dir_name='truffle',
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


def _gen_option_probe_switch(options, out, ident):
    assert options
    next_checks_map = {}
    common_prefix = ""
    while True:
        for suffix, is_boolean in options:
            if len(suffix) == 0:
                next_checks_map['\0'] = is_boolean
            else:
                next_checks_map.setdefault(suffix[0], []).append((suffix[1:], is_boolean))
        if len(next_checks_map) > 1:
            break
        common_first_char = next(iter(next_checks_map.keys()))
        if common_first_char == '\0':
            break
        next_checks_map = {}
        common_prefix = common_prefix + common_first_char
        options = [(suffix[1:], is_boolean) for suffix, is_boolean in options]

    def write_line(line):
        out.write("    " * ident)
        out.write(line)
        out.write("\n")

    if common_prefix:
        write_line(f"if (strncmp(option, \"{common_prefix}\", strlen(\"{common_prefix}\")) != 0) {{")
        write_line("    return OPTION_UNKNOWN;")
        write_line("}")
        write_line(f"option += strlen(\"{common_prefix}\");")
    write_line("switch(*option) {")
    for first_char in sorted(next_checks_map.keys()):
        assert len(first_char) > 0
        next_checks = next_checks_map[first_char]
        if first_char == '\0':
            assert isinstance(next_checks, bool)
            if next_checks:
                write_line("    case '\\0':")
                write_line(f"        return OPTION_BOOLEAN;")
            else:
                write_line("    case '=':")
                write_line("        return OPTION_STRING;")
        else:
            write_line(f"    case '{first_char}':")
            assert isinstance(next_checks, list)
            if len(next_checks) > 1:
                write_line("        option++;")
                _gen_option_probe_switch(next_checks, out, ident + 2)
            else:
                rest_str = next_checks[0][0]
                if next_checks[0][1]:
                    write_line(
                        f"        return strncmp(option + 1, \"{rest_str}\", sizeof(\"{rest_str}\")) == 0 ? OPTION_BOOLEAN : OPTION_UNKNOWN;")
                else:
                    write_line(
                        f"        return strncmp(option + 1, \"{rest_str}=\", strlen(\"{rest_str}=\")) == 0 ? OPTION_STRING : OPTION_UNKNOWN;")
    write_line("    default:")
    write_line("        return OPTION_UNKNOWN;")
    write_line("}")

def gen_gc_option_check(args):
    parser = argparse.ArgumentParser(prog='mx gen-gc-option-check')
    parser.add_argument('input', type=argparse.FileType('r'), help='Input G1 options dump file (From -H:+DumpIsolateCreationOnlyOptions)')
    args = parser.parse_args(args)
    options = []
    for line in args.input.readlines():
        java_type, name = line.rstrip('\n').split(' ', 1)
        options.append((name, java_type == 'java.lang.Boolean'))
    if not options:
        raise mx.abort("No option found in input file")
    options.sort(key=lambda x: x[0])

    sys.stdout.write("// Probing for the following options:\n")
    for suffix, is_boolean in options:
        if is_boolean:
            sys.stdout.write(f"// * ±{suffix}\n")
        else:
            sys.stdout.write(f"// * {suffix}=\n")

    sys.stdout.write("""
#define OPTION_UNKNOWN 0
#define OPTION_BOOLEAN 1
#define OPTION_STRING 2

static int probe_option_type(const char* option) {
""")
    _gen_option_probe_switch(options, sys.stdout, 1)
    sys.stdout.write("}")


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'espresso': [_run_espresso, '[args]'],
    'espresso-launcher': [_run_espresso_launcher, '[args]'],
    'espresso-embedded': [_run_espresso_embedded, '[args]'],
    'java-truffle': [_run_java_truffle, '[args]'],
    'espresso-meta': [_run_espresso_meta, '[args]'],
    'gen-gc-option-check': [gen_gc_option_check, '[path to isolate-creation-only-options.txt]'],
    'verify-imports': [_run_verify_imports_espresso, ''],
})

# CE with some skipped native images
ce_unchained_components = ['bnative-image', 'bnative-image-configure', 'cmp', 'gvm', 'lg', 'ni', 'nic', 'nil', 'nr_lib_jvmcicompiler', 'sdkc', 'sdkni', 'snative-image-agent', 'snative-image-diagnostics-agent', 'ssvmjdwp', 'svm', 'svmjdwp', 'svmsl', 'svmt', 'tflc', 'tflsm']
mx_sdk_vm.register_vm_config('ce', ce_unchained_components, _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('ce', ce_unchained_components, _suite, env_file='jvm-ce-llvm')
