#
# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import json
import os
import shutil
import sys
from abc import ABCMeta, abstractmethod
from os import listdir, linesep
from os.path import join, exists, isfile, basename, relpath, isdir, isabs, dirname, normpath
from typing import Tuple

import mx
import mx_sdk
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_subst
import mx_util
import mx_native
import mx_pomdistribution
from mx import TimeStampFile

_suite = mx.suite('sdk')

def _find_native_image_command(java_home):
    native_image_bin = join(java_home, 'bin', mx.exe_suffix('native-image'))
    if not exists(native_image_bin) and mx.is_windows():
        native_image_bin = join(java_home, 'bin', mx.cmd_suffix('native-image'))
    if not exists(native_image_bin):
        return None
    return native_image_bin

# native images are built either with stage1 or with an external graalvm
_external_bootstrap_graalvm = mx.get_env('BOOTSTRAP_GRAALVM')
_standalone_java_home = mx.get_env('STANDALONE_JAVA_HOME')
_external_bootstrap_graalvm_jdk = None
_external_bootstrap_graalvm_version = None
_is_nativeimage_ee_cache = None
if not _external_bootstrap_graalvm:
    java_home = mx_sdk_vm.base_jdk().home
    if _find_native_image_command(java_home):
        _external_bootstrap_graalvm = java_home
if _external_bootstrap_graalvm:
    if mx.is_darwin():
        if not exists(join(_external_bootstrap_graalvm, 'release')) and exists(join(_external_bootstrap_graalvm, 'Contents', 'Home')):
            _external_bootstrap_graalvm = join(_external_bootstrap_graalvm, 'Contents', 'Home')
    _external_bootstrap_graalvm_jdk = mx.JDKConfig(_external_bootstrap_graalvm)
    release_dict = mx_sdk_vm.parse_release_file(join(_external_bootstrap_graalvm, 'release'))
    _external_bootstrap_graalvm_version = mx.VersionSpec(release_dict["GRAALVM_VERSION"])

def requires_native_image_stage1():
    return not _external_bootstrap_graalvm

def requires_standalone_jimage():
    return not _standalone_java_home and not _external_bootstrap_graalvm

def _has_stage1_components():
    return mx_sdk_vm_impl.has_component('ni', stage1=True)

def _can_build_native_images():
    return bool(_external_bootstrap_graalvm) or _has_stage1_components()

def get_bootstrap_graalvm_jdk_version():
    if _external_bootstrap_graalvm:
        return _external_bootstrap_graalvm_jdk.version
    else:
        return mx_sdk_vm.base_jdk().version

def get_bootstrap_graalvm_version():
    if _external_bootstrap_graalvm:
        return _external_bootstrap_graalvm_version
    else:
        return mx.VersionSpec(_suite.release_version())

def _get_dyn_attribute(dep, attr_name, default):
    attr = getattr(dep, attr_name, None)
    if attr is None:
        return default, attr
    if ':' in attr:
        suite_name, func_name = attr.split(':', 1)
        suite = mx.suite(suite_name, context=dep)
    else:
        suite = dep.suite
        func_name = attr
    if suite.extensions is None:
        raise mx.abort(f"Could not resolve {attr_name} '{attr}': {suite.name} has no extension (mx_{suite.name}.py)", context=dep)
    func = getattr(suite.extensions, func_name, None)
    if not func:
        raise mx.abort(f"Could not resolve {attr_name} '{attr}' in {suite.extensions.__file__}", context=dep)
    return func(), attr


def _has_suite(name):
    return mx.suite(name, fatalIfMissing=False)

# Whether any of truffle-enterprise, graal-enterprise or substratevm-enterprise are imported.
def uses_enterprise_sources():
    # Technically testing for truffle-enterprise might be enough currently, but unclear if graal-enterprise will always depend on truffle-enterprise.
    return _has_suite('truffle-enterprise') or _has_suite('graal-enterprise') or _has_suite('substratevm-enterprise')

def is_nativeimage_ee():
    global _is_nativeimage_ee_cache
    if _is_nativeimage_ee_cache is None:
        if not _external_bootstrap_graalvm:
            _is_nativeimage_ee_cache = mx_sdk_vm_impl.has_component('svmee', stage1=True)
        else:
            _is_nativeimage_ee_cache = exists(join(_external_bootstrap_graalvm, 'lib', 'svm', 'builder', 'svm-enterprise.jar'))
    return _is_nativeimage_ee_cache

# Whether the produced standalone uses anything enterprise, either from source or prebuilt (i.e., a boostrap Oracle GraalVM)
def is_enterprise():
    return uses_enterprise_sources() or is_nativeimage_ee()

class StandaloneLicenses(mx.Project):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, **kw_args):
        self.community_license_file = _require(kw_args, 'community_license_file', suite, name)
        self.community_3rd_party_license_file = _require(kw_args, 'community_3rd_party_license_file', suite, name)

        self.uses_enterprise_sources = uses_enterprise_sources()
        self.enterprise = is_enterprise()
        if self.uses_enterprise_sources:
            deps.append('lium:LICENSE_INFORMATION_USER_MANUAL')
        super().__init__(suite, name, subDir=None, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, theLicense=theLicense, **kw_args)

    def getBuildTask(self, args):
        return StandaloneLicensesBuildTask(self, args, 1)

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError('single not supported')

        if self.enterprise:
            if not _suite.is_release():
                yield join(_suite.mxDir, 'DISCLAIMER_FOR_GFTC_SNAPSHOT_ARTIFACTS.txt'), 'DISCLAIMER.txt'
            if self.uses_enterprise_sources:
                lium_suite = mx.suite('lium', fatalIfMissing=True, context=self)
                vm_enterprise_dir = join(dirname(lium_suite.dir), 'vm-enterprise')
                yield join(vm_enterprise_dir, 'GraalVM_GFTC_License.txt'), 'LICENSE.txt'
                yield from mx.distribution('lium:LICENSE_INFORMATION_USER_MANUAL').getArchivableResults(use_relpath, single=True)
            else:
                # If the only enterprise input is a bootstrap Oracle GraalVM then copy the license from there
                yield join(_external_bootstrap_graalvm, 'LICENSE.txt'), 'LICENSE.txt'
                yield join(_external_bootstrap_graalvm, 'license-information-user-manual.zip'), 'license-information-user-manual.zip'
        else:
            yield join(self.suite.dir, self.community_license_file), 'LICENSE.txt'
            yield join(self.suite.dir, self.community_3rd_party_license_file), '3rd_party_licenses.txt'

class StandaloneLicensesBuildTask(mx.BuildTask):
    subject: StandaloneLicenses
    def __str__(self):
        return 'Building {}'.format(self.subject.name)

    def newestOutput(self):
        return mx.TimeStampFile.newest(file for file, _ in self.subject.getArchivableResults())

    def needsBuild(self, newestInput):
        witness_file = self.witness_file()
        if exists(witness_file):
            with open(witness_file, 'r') as f:
                contents = f.read()
        else:
            contents = None
        if contents != self.witness_contents():
            return True, f"{contents} => {self.witness_contents()}"
        return False, 'Files are already on disk'

    def witness_file(self):
        return join(self.subject.get_output_root(), 'witness')

    def witness_contents(self):
        if self.subject.uses_enterprise_sources:
            return 'ee sources'
        elif self.subject.enterprise:
            return _external_bootstrap_graalvm
        else:
            return 'ce'

    def build(self):
        witness_file = self.witness_file()
        mx_util.ensure_dirname_exists(witness_file)
        with open(witness_file, 'w') as f:
            f.write(self.witness_contents())

    def clean(self, forBuild=False):
        mx.rmtree(self.witness_file(), ignore_errors=True)

class NativeImageProject(mx.Project, metaclass=ABCMeta):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, deliverable=None, **kw_args):
        super().__init__(suite, name, subDir=None, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, theLicense=theLicense, **kw_args)
        self.deliverable = deliverable if deliverable else name
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        if not _external_bootstrap_graalvm and _has_stage1_components():
            self.buildDependencies += [f'sdk:{mx_sdk_vm_impl.get_stage1_graalvm_distribution_name()}']

    def isPlatformDependent(self):
        return True

    def build_directory(self):
        return join(self.get_output_base(), self.name)

    def output_file(self):
        return join(self.build_directory(), self.output_file_name())

    @abstractmethod
    def output_file_name(self):
        pass

    def base_file_name(self):
        return self.deliverable

    @abstractmethod
    def options_file_name(self):
        pass

    @abstractmethod
    def name_suffix(self):
        """
        The suffix that native-image will automatically append to the image name (ImageName, -H:Name, or -o).
        """
        pass

    def getBuildTask(self, args):
        return NativeImageBuildTask(args, self)

    def get_build_args(self):
        explicit_build_args = getattr(self, 'build_args', [])
        dyn_build_args, dynamicBuildArgs = _get_dyn_attribute(self, 'dynamicBuildArgs', [])
        if not (isinstance(dyn_build_args, list) and all(isinstance(d, str) for d in dyn_build_args)):
            raise mx.abort(f"dynamicBuildArgs `{dynamicBuildArgs}` did not return a list of strings", context=self)
        return [mx_subst.string_substitutions.substitute(a) for a in explicit_build_args] + dyn_build_args

    def resolveDeps(self):
        super().resolveDeps()
        dyn_deps, dynamicDependencies = _get_dyn_attribute(self, 'dynamicDependencies', [])
        if not (isinstance(dyn_deps, list) and all(isinstance(d, str) for d in dyn_deps)):
            raise mx.abort(f"dynamicDependencies `{dynamicDependencies}` did not return a list of strings", context=self)
        self._resolveDepsHelper(dyn_deps)
        self.deps += dyn_deps
        if not _can_build_native_images():
            self.ignore = "Stage1 GraalVM doesn't have the native-image component and BOOTSTRAP_GRAALVM was not set"

    def getArchivableResults(self, use_relpath=True, single=False):
        yield self.output_file(), self.output_file_name()
        if not single:
            build_directory = self.build_directory()
            build_artifacts_file = join(build_directory, 'build-artifacts.json')
            if exists(build_artifacts_file):
                # include any additional JDK libraries
                with open(build_artifacts_file, 'r') as f:
                    build_artifacts = json.load(f)

                def _yield_files(file_type, prefix=None):
                    if file_type not in build_artifacts:
                        return
                    file_type_prefix = prefix or file_type
                    for build_artifact in build_artifacts[file_type]:
                        build_artifact_path = join(build_directory, build_artifact)
                        if isfile(build_artifact_path):
                            yield build_artifact_path, join(file_type_prefix, build_artifact)
                        elif isdir(build_artifact_path):
                            for root, _, files in os.walk(build_artifact_path):
                                relroot = join(file_type_prefix, relpath(root, build_directory))
                                for name in files:
                                    yield join(root, name), join(relroot, name)
                        else:
                            mx.logv(f"Ignoring non-existent build artifact {build_artifact_path}', referred by '{build_artifacts_file}' produced while building '{self.output_file_name()}'")

                yield from _yield_files('shared_libraries')
                yield from _yield_files('executables')
                yield from _yield_files('jdk_libraries')
                yield from _yield_files('c_headers')
                yield from _yield_files('language_resources')
                yield from _yield_files('debug_info')

                yield from _yield_files('shared_libraries', 'standard-deliverables')
                yield from _yield_files('executables', 'standard-deliverables')
                if mx_sdk_vm_impl._debug_images():
                    yield from _yield_files('debug_info', 'standard-deliverables')

class NativeImageExecutableProject(NativeImageProject):
    def resolveDeps(self):
        super().resolveDeps()
        if mx_sdk_vm_impl._force_bash_launchers(self.output_file_name(), build_by_default=True):
            self.ignore = "Skipped executable"

    def output_file_name(self):
        return mx.exe_suffix(self.base_file_name())

    def options_file_name(self):
        return self.base_file_name()

    def name_suffix(self):
        return mx.exe_suffix("")

class NativeImageLibraryProject(NativeImageProject):

    def __init__(self, suite, name, deps, workingSets, theLicense=None, deliverable=None, **kw_args):
        if not deliverable:
            deliverable = name[3:] if name.startswith('lib') else name
        super().__init__(suite, name, deps, workingSets, theLicense, deliverable, **kw_args)

    def resolveDeps(self):
        super().resolveDeps()
        if mx_sdk_vm_impl._skip_libraries(self.output_file_name(), build_by_default=True):
            self.ignore = "Skipped library"

    def getArchivableResults(self, use_relpath=True, single=False):
        for e in super().getArchivableResults(use_relpath=use_relpath, single=single):
            yield e
            if single:
                return
        output_dir = self.build_directory()
        if exists(output_dir):
            for e in listdir(output_dir):
                absolute_path = join(output_dir, e)
                if isfile(absolute_path) and e.endswith('.h'):
                    yield absolute_path, e

    def output_file_name(self):
        return mx.add_lib_prefix(mx.add_lib_suffix(self.base_file_name()))

    def options_file_name(self):
        return 'lib:' + self.base_file_name()

    def name_suffix(self):
        return mx.add_lib_suffix("")

    def get_build_args(self):
        extra_build_args = ['--shared']
        if is_nativeimage_ee():
            # PGO is supported
            extra_build_args += mx_sdk_vm_impl.svm_experimental_options(['-H:+ProfilingEnableProfileDumpHooks'])
        return super().get_build_args() + extra_build_args


# Common flags for most Truffle languages
class LanguageLibraryProject(NativeImageLibraryProject):
    def get_build_args(self):
        build_args = super().get_build_args()[:]

        # Signals flags
        build_args += [
            '-R:+EnableSignalHandling',
            '-R:+InstallSegfaultHandler',
        ] + mx_sdk_vm_impl.svm_experimental_options(['-H:+InstallExitHandlers'])

        # Monitoring flags
        if get_bootstrap_graalvm_version() >= mx.VersionSpec("24.0"):
            build_args += ['--enable-monitoring=jvmstat,heapdump,jfr,threaddump']
        else:
            build_args += ['--enable-monitoring=jvmstat,heapdump,jfr']
            build_args += mx_sdk_vm_impl.svm_experimental_options(['-H:+DumpThreadStacksOnSignal'])

        build_args += mx_sdk_vm_impl.svm_experimental_options(['-H:+DumpRuntimeCompilationOnSignal'])
        build_args += [
            '-R:-UsePerfData', # See GR-25329, reduces startup instructions significantly
        ]

        return build_args


class NativeImageBuildTask(mx.BuildTask):
    subject: NativeImageProject
    def __init__(self, args, project: NativeImageProject):
        if mx.is_continuous_integration() and mx.cpu_count() < 24:
            # keep it possible to build 2 images in parallel on 16 cores in the CI
            max_parallelism = 8
        else:
            # native image build speed doesn't scale much after 12 (on my machine ^_^)
            max_parallelism = 12
        super().__init__(project, args, min(max_parallelism, mx.cpu_count()))

    def newestOutput(self):
        return mx.TimeStampFile.newest([_path for _path, _ in self.subject.getArchivableResults()])

    def get_build_args(self):
        experimental_build_args = [
            '-H:+GenerateBuildArtifactsFile',  # generate 'build-artifacts.json'
            '-H:+AssertInitializationSpecifiedForAllClasses',
            '-H:+EnforceMaxRuntimeCompileMethods',
            '-H:+GuaranteeSubstrateTypesLinked',
            '-H:+ReportExceptionStackTraces',
            '-H:+DetectUserDirectoriesInImageHeap',
        ]
        if get_bootstrap_graalvm_version() >= mx.VersionSpec("24.1"):
            experimental_build_args.append('-H:+VerifyRuntimeCompilationFrameStates')
        build_args = []

        # GR-65661: we need to disable the check in GraalVM for 21 as it does not allow polyglot version 26.0.0-dev
        if get_bootstrap_graalvm_version() < mx.VersionSpec("25"):
            build_args += ['-Dpolyglotimpl.DisableVersionChecks=true']

        canonical_name = self.subject.base_file_name()
        profiles = mx_sdk_vm_impl._image_profiles(canonical_name)
        if profiles:
            if not is_nativeimage_ee():
                raise mx.abort("Image profiles can not be used if PGO is not supported.")
            basenames = [basename(p) for p in profiles]
            if len(set(basenames)) != len(profiles):
                raise mx.abort("Profiles for an image must have unique filenames.\nThis is not the case for {}: {}.".format(canonical_name, profiles))
            build_args += ['--pgo=' + ','.join(('${.}/' + n for n in basenames))]

        if mx_sdk_vm_impl._debug_images():
            build_args += ['-ea', '-O0']
            experimental_build_args += ['-H:+PreserveFramePointer', '-H:-DeleteLocalSymbols']
        if mx_sdk_vm_impl._generate_debuginfo(self.subject.options_file_name()):
            build_args += ['-g']
            if mx.get_opts().disable_debuginfo_stripping or mx.is_darwin():
                experimental_build_args += ['-H:-StripDebugInfo']

        alt_c_compiler = getattr(self.args, 'alt_cl' if mx.is_windows() else 'alt_cc')
        if alt_c_compiler is not None:
            experimental_build_args += ['-H:CCompilerPath=' + shutil.which(alt_c_compiler)]
        if self.args.alt_cflags is not None:
            experimental_build_args += ['-H:CCompilerOption=' + e for e in self.args.alt_cflags.split()]
        if self.args.alt_ldflags is not None:
            experimental_build_args += ['-H:NativeLinkerOption=' + e for e in self.args.alt_ldflags.split()]
        classpath_and_modulepath = mx.get_runtime_jvm_args(self.subject.deps, include_system_properties=False)
        build_args += classpath_and_modulepath + [
            '--no-fallback',
            '-march=compatibility',  # Target maximum portability
            '--parallelism=' + str(self.parallelism),
            '--link-at-build-time',
            # we want "25.0.0-dev" and not "dev" (the default used in NativeImage#prepareImageBuildArgs)
            '-Dorg.graalvm.version={}'.format(_suite.release_version()),
        ] + mx_sdk_vm_impl.svm_experimental_options(experimental_build_args)
        if os.environ.get('JVMCI_VERSION_CHECK'):
            # Propagate this env var when running native image from mx
            build_args += ['-EJVMCI_VERSION_CHECK']
        extra_build_args = mx_sdk_vm_impl._extra_image_builder_args(canonical_name)

        return build_args + self.subject.get_build_args() + extra_build_args

    def needsBuild(self, newestInput) -> Tuple[bool, str]:
        ts = TimeStampFile(self.subject.output_file())
        if not ts.exists():
            return True, f"{ts.path} does not exist"
        if newestInput and ts.isOlderThan(newestInput):
            return True, f"{ts} is older than {newestInput}"
        previous_build_args = []
        command_file = self._get_command_file()
        if exists(command_file):
            with open(command_file) as f:
                previous_build_args = [l.rstrip('\r\n') for l in f.readlines()]
        cmd = self.get_build_command()
        if previous_build_args != cmd:
            return True, 'image command changed'
        return False, None

    def get_build_command(self):
        build_args = self.get_build_args()
        output_file = self.subject.output_file()
        suffix = self.subject.name_suffix()
        if suffix:
            output_file = output_file[:-len(suffix)]
        build_args += ['-o', output_file]

        if _external_bootstrap_graalvm:
            native_image_bin = _find_native_image_command(_external_bootstrap_graalvm)
            if not native_image_bin:
                raise mx.abort(f"Couldn't find native-image in provided $BOOTSTRAP_GRAALVM")
        else:
            stage1 = mx_sdk_vm_impl.get_stage1_graalvm_distribution()
            native_image_project_name = mx_sdk_vm_impl.GraalVmLauncher.launcher_project_name(mx_sdk.LauncherConfig(mx.exe_suffix('native-image'), [], "", []), stage1=True)
            native_image_bin = join(stage1.output, stage1.find_single_source_location('dependency:' + native_image_project_name))

        native_image_command = [native_image_bin] + build_args
        return native_image_command

    def build(self):
        mx_util.ensure_dir_exists(self.subject.build_directory())
        native_image_command = self.get_build_command()

        # Prefix native-image builds that print straight to stdout or stderr with [<output_filename>:<pid>]
        out = mx.PrefixCapture(lambda l: mx.log(l, end=''), self.subject.output_file_name())
        err = mx.PrefixCapture(lambda l: mx.log(l, end='', file=sys.stderr), out.identifier)

        mx.run(native_image_command, nonZeroIsFatal=True, out=out, err=err)

        with open(self._get_command_file(), 'w') as f:
            f.writelines((l + linesep for l in native_image_command))


    def _get_command_file(self):
        return self.subject.output_file() + '.cmd'

    def clean(self, forBuild=False):
        build_directory = self.subject.build_directory()
        if exists(build_directory):
            mx.rmtree(build_directory)

    def __str__(self):
        return 'Building {}'.format(self.subject.name)


def _require(kw_args, name, suite, dependency_name):
    if name not in kw_args:
        raise mx.abort("Attribute '" + name + "' is required", context=f"'{dependency_name}' in '{suite.name}'")
    return kw_args.pop(name)

def _require_path(kw_args, name, suite, dependency_name):
    return _require(kw_args, name, suite, dependency_name).replace('/', os.sep)

def _pop_path(kw_args, name, default):
    if name not in kw_args:
        return default
    return kw_args.pop(name).replace('/', os.sep)

def _pop_list(kw_args, name, suite, dependency_name):
    if name not in kw_args:
        return []
    v = kw_args.pop(name)
    if not isinstance(v, list):
        raise mx.abort("Attribute '" + name + "' must be a list", context=f"'{dependency_name}' in '{suite.name}'")
    return v

class ThinLauncherProject(mx_native.DefaultNativeProject):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, **kw_args):
        self.relative_jre_path = _require_path(kw_args, 'relative_jre_path', suite, name)
        self.relative_module_path = _pop_path(kw_args, 'relative_module_path', None)
        self.relative_library_path = _pop_path(kw_args, 'relative_library_path', None)
        self.jar_distributions = _require(kw_args, 'jar_distributions', suite, name)
        self.main_class = _require(kw_args, 'mainClass', suite, name)
        self.option_vars = kw_args.pop('option_vars', [])
        self.default_vm_args = kw_args.pop('default_vm_args', [])
        self.relative_home_paths = {k: v.replace('/', os.sep) for k, v in kw_args.pop('relative_home_paths', {}).items()}
        self.relative_extracted_lib_paths = {k: v.replace('/', os.sep) for k, v in kw_args.pop('relative_extracted_lib_paths', {}).items()}
        self.liblang_relpath = _pop_path(kw_args, 'relative_liblang_path', None)
        self.setup_relative_resources = kw_args.pop('setup_relative_resources', None)

        if not kw_args.get('multitarget'):
            # We use our LLVM toolchain on Linux by default because we want to statically link the C++ standard library,
            # and the system toolchain rarely has libstdc++.a installed (it would be an extra CI & dev dependency).
            toolchain = 'sdk:LLVM_NINJA_TOOLCHAIN' if mx.is_linux() else 'mx:DEFAULT_NINJA_TOOLCHAIN'
        else:
            toolchain = None

        super().__init__(
            suite,
            name,
            'src',
            [],
            [],
            None,
            join(_suite.dir, "src", "org.graalvm.launcher.native"),
            'executable',
            # deliverable=None,
            use_jdk_headers=True,
            toolchain=toolchain,
            **kw_args
        )
        if len(self.jar_distributions) < 1:
            raise self.abort("ThinLauncherProject requires at least one element in 'jar_distributions'")
        if self.setup_relative_resources and 'relative_root' not in self.setup_relative_resources:
            raise self.abort("'setup_relative_resources' must have a 'relative_root' attribute")
        if self.setup_relative_resources and 'components_from' not in self.setup_relative_resources:
            raise self.abort("'setup_relative_resources' must have a 'components_from' attribute")

    def resolveDeps(self):
        if self.setup_relative_resources:
            self.buildDependencies.append(self.setup_relative_resources['components_from'])
        super().resolveDeps()

    def isJDKDependent(self):
        # This must always be False, the compiled thin launchers need to work on both latest LTS JDK and jdk-latest
        return False

    @property
    def uses_llvm_toolchain(self):
        return len(self.toolchains) == 1 and mx.dependency('LLVM_NINJA_TOOLCHAIN', fatalIfMissing=False) == self.toolchains[0].toolchain_dist

    @property
    def uses_musl_swcfi_toolchain(self):
        # once GR-67435 is fixed we can revisit if we can statically link just like in the branches guarded by uses_llvm_toolchain
        return len(self.toolchains) == 1 and mx.dependency('BOOTSTRAP_MUSL_SWCFI_NINJA_TOOLCHAIN', fatalIfMissing=False) == self.toolchains[0].toolchain_dist

    @property
    def cflags(self):
        _dynamic_cflags = [
            ('/std:c++17' if mx.is_windows() else '-std=c++17'),
            '-O3', # Note: no -g to save 0.2MB on Linux
            '-DCP_SEP=' + os.pathsep,
            '-DDIR_SEP=' + ('\\\\' if mx.is_windows() else '/'),
            '-DGRAALVM_VERSION=' + _suite.release_version(),
            ]
        if not mx.is_windows():
            _dynamic_cflags += ['-pthread']
            _dynamic_cflags += ['-Werror=undef'] # fail on undefined macro used in preprocessor
        if mx.is_linux() and self.uses_llvm_toolchain:
            _dynamic_cflags += ['-stdlib=libc++'] # to link libc++ statically, see ldlibs
        if mx.is_darwin():
            _dynamic_cflags += ['-ObjC++']

        def escaped_path(path):
            if mx.is_windows():
                return path.replace('\\', '\\\\')
            else:
                return path

        _mp = []
        _lp = []
        if self.relative_module_path :
            _mp.append(escaped_path(self.relative_module_path ))
        if self.relative_library_path:
            _lp.append(escaped_path(self.relative_library_path))

        main_class_package = self.main_class[0:self.main_class.rindex(".")]
        main_module_export = main_class_package + " to org.graalvm.launcher"
        main_module = None
        for launcher_jar in self.jar_distributions:
            dist = mx.distribution(launcher_jar)
            if hasattr(dist, 'moduleInfo') and main_module_export in dist.moduleInfo.get('exports', []):
                main_module = dist.moduleInfo['name']

        if not main_module:
            mx.abort("The distribution with main class {} among {} must have export: {}".format(self.main_class, self.jar_distributions, main_module_export))

        _dynamic_cflags.append('-DLAUNCHER_MAIN_MODULE=' + main_module)
        _dynamic_cflags.append('-DLAUNCHER_CLASS=' + self.main_class)
        if _mp:
            _dynamic_cflags.append('-DLAUNCHER_MODULE_PATH="{\\"' + '\\", \\"'.join(_mp) + '\\"}"')
        if _lp:
            _dynamic_cflags.append('-DLAUNCHER_LIBRARY_PATH="{\\"' + '\\", \\"'.join(_lp) + '\\"}"')

        # path to libjvm
        if mx.is_windows():
            _libjvm_path = join(self.relative_jre_path, 'bin', 'server', 'jvm.dll')
        else:
            _libjvm_path = join(self.relative_jre_path, 'lib', 'server', mx.add_lib_suffix("libjvm"))
        _libjvm_path = escaped_path(_libjvm_path)
        _dynamic_cflags += [
            '-DLIBJVM_RELPATH=' + _libjvm_path,
        ]

        # path to libjli - only needed on osx for AWT
        if mx.is_darwin():
            _libjli_path = join(self.relative_jre_path, 'lib', mx.add_lib_suffix("libjli"))
            _libjli_path = escaped_path(_libjli_path)
            _dynamic_cflags += [
                '-DLIBJLI_RELPATH=' + _libjli_path,
            ]

        if self.liblang_relpath:
            _dynamic_cflags += [
                '-DLIBLANG_RELPATH=' + escaped_path(self.liblang_relpath)
            ]

        if len(self.option_vars) > 0:
            _dynamic_cflags += ['-DLAUNCHER_OPTION_VARS="{\\"' + '\\", \\"'.join(self.option_vars) + '\\"}"']

        # Set the cflags that inform the launcher of the various language home dirs.
        #
        # Note that `lib_home_path` is relative to the directory containing the thin launcher.
        lang_home_names = []
        bin_home_paths = []
        for lang_home_name, lib_home_path in self.relative_home_paths.items():
            lang_home_names.append(lang_home_name)
            bin_home_paths.append(escaped_path(lib_home_path))
        if lang_home_names:
            _dynamic_cflags += [
                '-DLAUNCHER_LANG_HOME_NAMES="{\\"' + '\\", \\"'.join(lang_home_names) + '\\"}"',
                '-DLAUNCHER_LANG_HOME_PATHS="{\\"' + '\\", \\"'.join(bin_home_paths) + '\\"}"',
                ]
        extracted_lib_names = []
        extracted_lib_paths = []
        for extracted_lib_name, extracted_lib_path in self.relative_extracted_lib_paths.items():
            bin_lib_path = escaped_path(extracted_lib_path)
            extracted_lib_names.append(extracted_lib_name)
            extracted_lib_paths.append(bin_lib_path)

        if self.setup_relative_resources:
            resources_project = mx.dependency(self.setup_relative_resources['components_from'])
            if not isinstance(resources_project, ExtractedEngineResources):
                raise self.abort("'components_from' must refer to a ExtractedEngineResources project")
            relative_root = self.setup_relative_resources['relative_root']
            output_dir = resources_project.output_dir()
            for component in os.listdir(output_dir):
                assert isdir(join(output_dir, component))
                extracted_lib_names.append('polyglot.engine.resourcePath.' + component)
                extracted_lib_paths.append(join(relative_root, component))

        if extracted_lib_names:
            _dynamic_cflags += [
                '-DLAUNCHER_EXTRACTED_LIB_NAMES="{\\"' + '\\", \\"'.join(extracted_lib_names) + '\\"}"',
                '-DLAUNCHER_EXTRACTED_LIB_PATHS="{\\"' + '\\", \\"'.join(extracted_lib_paths) + '\\"}"',
                ]

        if len(self.default_vm_args) > 0:
            _dynamic_cflags += ['-DLAUNCHER_DEFAULT_VM_ARGS="{\\"' + '\\", \\"'.join(self.default_vm_args) + '\\"}"']

        return super().cflags + _dynamic_cflags

    @property
    def ldflags(self):
        _dynamic_ldflags = []
        if not mx.is_windows():
            _dynamic_ldflags += ['-pthread']
        if self.uses_musl_swcfi_toolchain:
            # Use $$ to escape the $ from expansion by mx. If we use musl swcfi
            # and their libc, the libc++, libc++abi and libunwind must be
            # either in the LD_LIBRARY_PATH (which takes precedence) or in the
            # lib folder of the standalone.
            _dynamic_ldflags.append(r"-Wl,-rpath,'$$ORIGIN/../lib'")
        return super().ldflags + _dynamic_ldflags

    @property
    def ldlibs(self):
        _dynamic_ldlibs = []
        if mx.is_linux() and self.uses_llvm_toolchain:
            # Link libc++ statically
            _dynamic_ldlibs += [
                '-stdlib=libc++',
                '-static-libstdc++', # it looks weird but this does link libc++ statically
                '-l:libc++abi.a',
            ]
        if not mx.is_windows():
            _dynamic_ldlibs += ['-ldl']
        if mx.is_darwin():
            _dynamic_ldlibs += ['-framework', 'Foundation']

            default_min_version = {'amd64': '10.13', 'aarch64': '11.0'}[mx.get_arch()]
            min_version = os.getenv('MACOSX_DEPLOYMENT_TARGET', default_min_version)
            _dynamic_ldlibs += ['-mmacosx-version-min=' + min_version]

        return super().ldlibs + _dynamic_ldlibs


class JavaHomeDependency(mx.BaseLibrary):
    def __init__(self, suite, name, java_home):
        assert isabs(java_home)
        self.java_home = java_home
        release_dict = mx_sdk_vm.parse_release_file(join(java_home, 'release'))
        self.is_ee_implementor = release_dict.get('IMPLEMENTOR') == 'Oracle Corporation'
        self.version = mx.VersionSpec(release_dict.get('JAVA_VERSION'))
        self.major_version = self.version.parts[1] if self.version.parts[0] == 1 else self.version.parts[0]
        name = name.replace('<version>', str(self.major_version))
        if self.is_ee_implementor:
            the_license = "Oracle Proprietary"
        else:
            the_license = "GPLv2-CPE"
        super().__init__(suite, name, optional=False, theLicense=the_license)
        self.deps = []

    def is_available(self):
        return True

    def getBuildTask(self, args):
        return JavaHomeBuildTask(self, args, 1)

    def getResults(self):
        for root, _, files in os.walk(self.java_home):
            for name in files:
                yield join(root, name)

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError("single not supported")
        for path in self.getResults():
            if use_relpath:
                arcname = relpath(path, self.java_home)
            else:
                arcname = basename(path)
            yield path, arcname

    def post_init(self):
        pass  # help act like a distribution since this is registered as a distribution

    def archived_deps(self):
        return []  # help act like a distribution since this is registered as a distribution

    def isJDKDependent(self):
        return False

    # Needed when somesuite._output_root_includes_config() == False
    def get_output_root(self):
        return join(self.get_output_base(), self.name)


class JavaHomeBuildTask(mx.BuildTask):
    subject: JavaHomeDependency
    def __str__(self):
        return f'Checking {self.subject}'

    def needsBuild(self, newestInput):
        witness_file = self.witness_file()
        if exists(witness_file):
            with open(witness_file, 'r') as f:
                contents = f.read()
        else:
            contents = None
        if contents != self.witness_contents():
            return True, 'Path changed'
        return False, 'Files are already on disk'

    def newestOutput(self):
        return TimeStampFile.newest(self.subject.getResults())

    def witness_file(self):
        return join(self.subject.get_output_root(), 'witness')

    def witness_contents(self):
        return self.subject.java_home

    def build(self):
        witness_file = self.witness_file()
        mx_util.ensure_dirname_exists(witness_file)
        with open(witness_file, 'w') as f:
            f.write(self.witness_contents())

    def clean(self, forBuild=False):
        mx.rmtree(self.witness_file(), ignore_errors=True)


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    if register_distribution:
        if _standalone_java_home:
            dep = JavaHomeDependency(_suite, 'STANDALONE_JAVA_HOME', _standalone_java_home)
        elif _external_bootstrap_graalvm:
            dep = JavaHomeDependency(_suite, 'STANDALONE_JAVA_HOME', _external_bootstrap_graalvm)
        else:
            # TODO: make this a "Proxy"/"Forwarding" dependency to avoid the copy
            layout = {
                "./": "dependency:sdk:java-standalone-jimage/*",
            }
            libgraal_component = mx_sdk_vm_impl._get_libgraal_component()
            if libgraal_component is not None:
                jvm_lib_dir = 'bin' if mx.is_windows() else 'lib'
                for library_config in libgraal_component.library_configs:
                    dependency = mx_sdk_vm_impl.GraalVmLibrary.project_name(library_config)
                    layout.setdefault(f'{jvm_lib_dir}/', []).append({
                        'source_type': 'dependency',
                        'dependency': dependency,
                        'exclude': [],
                        'path': None,
                    })
            dep = mx.LayoutDirDistribution(_suite, 'STANDALONE_JAVA_HOME', [], layout, None, True, None, platforms='local', defaultBuild=False)
        register_distribution(dep)

        tools_dists = []
        if mx.suite("tools", fatalIfMissing=False):
            tools_dists += [
                'tools:LSP_API', 'tools:LSP',
                'tools:DAP',
                'tools:CHROMEINSPECTOR',
                'tools:INSIGHT',
                'tools:INSIGHT_HEAP',
                'tools:TRUFFLE_PROFILER',
                'tools:TRUFFLE_COVERAGE'
            ]
        register_distribution(mx_pomdistribution.POMDistribution(_suite, "TOOLS_FOR_STANDALONE", [], tools_dists, None, maven=False))


        # register toolchains shipped by BOOTSTRAP_GRAALVM, if any
        if _external_bootstrap_graalvm:
            toolchain_dir = join(_external_bootstrap_graalvm, "lib", "toolchains")
            if exists(toolchain_dir):
                for e in listdir(toolchain_dir):
                    if not (e == "musl" or e.startswith("musl-")):
                        # currently only variants of musl are detected
                        continue

                    binpath = join(toolchain_dir, e, "bin")
                    ninja_layout = {
                        "toolchain.ninja" : {
                            "source_type": "string",
                            "value": f'''
include <ninja-toolchain:GCC_NINJA_TOOLCHAIN>
CC={binpath}/clang
CXX={binpath}/clang++
AR={binpath}/ar
'''
                        },
                    }
                    ninja_dependencies = ['mx:GCC_NINJA_TOOLCHAIN']
                    ninja_native_toolchain = {
                        'kind': 'ninja',
                        'compiler': 'llvm',
                        'target': {
                            'os': mx.get_os(),
                            'arch': mx.get_arch(),
                            'libc': 'musl',
                            'variant': e.split('-', 1)[1] if '-' in e else None
                        }
                    }
                    ninja_name = 'BOOTSTRAP_' + e.upper().replace('-', '_') + '_NINJA_TOOLCHAIN'
                    register_distribution(mx.LayoutDirDistribution(_suite, ninja_name, ninja_dependencies, ninja_layout, path=None, theLicense=None, platformDependent=True, native_toolchain=ninja_native_toolchain, native=True, maven=False))

                    cmake_layout = {
                        "toolchain.cmake" : {
                            "source_type": "string",
                            "value": f'''
set(CMAKE_C_COMPILER {binpath}/clang)
set(CMAKE_CXX_COMPILER {binpath}/clang++)
set(CMAKE_AR {binpath}/ar)
'''
                        },
                    }
                    cmake_dependencies = []
                    cmake_native_toolchain = dict(**ninja_native_toolchain)
                    cmake_native_toolchain['kind'] = 'cmake'
                    cmake_name = 'BOOTSTRAP_' + e.upper().replace('-', '_') + '_CMAKE_TOOLCHAIN'
                    register_distribution(mx.LayoutDirDistribution(_suite, cmake_name, cmake_dependencies, cmake_layout, path=None, theLicense=None, platformDependent=True, native_toolchain=cmake_native_toolchain, native=True, maven=False))

                    mx.logv(f'Registered toolchain for {e} from bootstrap GraalVM {_external_bootstrap_graalvm}')


class DynamicPOMDistribution(mx_pomdistribution.POMDistribution):
    def __init__(self, suite, name, deps, excl, platformDependent, theLicense, **kw_args):
        dist_deps = _pop_list(kw_args, 'distDependencies', suite, name)
        runtime_deps = _pop_list(kw_args, 'runtimeDependencies', suite, name)
        super().__init__(suite, name, deps + dist_deps, runtime_deps, theLicense, **kw_args)
        if excl:
            raise mx.abort("'exclude' is not supported on pom distributions", context=self)
        if platformDependent:
            raise mx.abort("'platformDependent' cannot be true for pom distributions", context=self)

    def resolveDeps(self):
        super().resolveDeps()
        dyn_deps, dynamicDependencies = _get_dyn_attribute(self, 'dynamicDistDependencies', [])
        if not (isinstance(dyn_deps, list) and all(isinstance(d, str) for d in dyn_deps)):
            raise mx.abort(f"dynamicDistDependencies `{dynamicDependencies}` did not return a list of strings", context=self)
        self._resolveDepsHelper(dyn_deps)
        self.deps += dyn_deps


class ExtractedEngineResources(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, theLicense=None, **kw_args):
        self.root_components = kw_args.pop('root_components', [])
        self.ignore_components = kw_args.pop('ignore_components', [])
        super().__init__(suite, name, deps, workingSets, theLicense, **kw_args)

    def resolveDeps(self):
        self.deps.append('sdk:RESOURCECOPY')
        super().resolveDeps()
        dyn_deps, dynamicDependencies = _get_dyn_attribute(self, 'dynamicDependencies', [])
        if not (isinstance(dyn_deps, list) and all(isinstance(d, str) for d in dyn_deps)):
            raise mx.abort(f"dynamicDependencies `{dynamicDependencies}` did not return a list of strings", context=self)
        self._resolveDepsHelper(dyn_deps)
        self.deps += dyn_deps

    def output_dir(self):
        return join(self.get_output_base(), self.name)

    def archive_prefix(self):
        return ""

    def getResults(self):
        for root, _, files in os.walk(self.output_dir()):
            for filename in files:
                yield join(root, filename)

    def getBuildTask(self, args):
        return ExtractedEngineResourcesBuildTask(self, args, 1)


class ExtractedEngineResourcesBuildTask(mx.BuildTask):
    subject: ExtractedEngineResources
    def __str__(self):
        return f'Extracting {self.subject}'

    def newestOutput(self):
        return TimeStampFile.newest(self.subject.getResults())

    def needsBuild(self, newestInput):
        result, reason = super().needsBuild(newestInput)
        if result:
            return result, reason
        out = mx.TimeStampFile(self.subject.output_dir(), followSymlinks=False)
        if newestInput and out.isOlderThan(newestInput):
            return True, f"{newestInput} is newer than the output directory"
        witness_file = self.witness_file()
        if exists(witness_file):
            with open(witness_file, 'r') as f:
                contents = f.read()
        else:
            contents = None
        if contents != self.witness_contents():
            return True, 'Configuration changed'
        return False, "Up to date"

    def build(self):
        output_dir = self.subject.output_dir()
        mx_util.ensure_dir_exists(output_dir)
        os.utime(output_dir, None)
        vm_args = ['--enable-native-access=org.graalvm.truffle']
        if mx.get_jdk(tag='default').version >= mx.VersionSpec("24"):
            # "JDK-8342380: Implement JEP 498: Warn upon Use of Memory-Access Methods in sun.misc.Unsafe"
            vm_args.append('--sun-misc-unsafe-memory-access=allow')
        mx.run_java(vm_args + mx.get_runtime_jvm_args(self.subject.deps) + ['org.graalvm.resourcecopy.CopyResources', output_dir] + self.subject.root_components)
        for ignored_component in self.subject.ignore_components:
            component_dir = join(output_dir, ignored_component)
            if exists(component_dir):
                mx.rmtree(component_dir)
        witness_file = self.witness_file()
        mx_util.ensure_dirname_exists(witness_file)
        with open(witness_file, 'w') as f:
            f.write(self.witness_contents())

    def clean(self, forBuild=False):
        mx.rmtree(self.subject.output_dir(), ignore_errors=True)
        mx.rmtree(self.witness_file(), ignore_errors=True)

    def witness_file(self):
        return self.subject.output_dir() + '.witness'

    def witness_contents(self):
        return f"roots: {', '.join(self.subject.root_components)}\nignored: {', '.join(self.subject.ignore_components)}"


def _make_windows_link(link_target):
    link_template_name = join(_suite.mxDir, 'vm', 'exe_link_template.cmd')
    with open(link_template_name, 'r') as template:
        _template_subst = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        _template_subst.register_no_arg('target', normpath(link_target))
        return _template_subst.substitute(template.read())


class ToolchainToolDistribution(mx.LayoutDirDistribution):
    def __init__(self, suite, name=None, deps=None, excludedLibs=None, platformDependent=True, theLicense=None, defaultBuild=True, **kw_args):
        self.tool_project = _require(kw_args, 'tool_project', suite, name)
        self.tool_links = _require(kw_args, 'tool_links', suite, name)

        layout = {
            './': [{
                "source_type": "dependency",
                "dependency": self.tool_project,
            }]
        }

        super().__init__(suite, name=name, deps=[], layout=layout, path=None, theLicense=theLicense, platformDependent=True, defaultBuild=defaultBuild)

    def resolveDeps(self):
        self.tool_project = mx.project(self.tool_project)
        _, main_tool_name = next(self.tool_project.getArchivableResults(single=True))

        def _add_link(name, target):
            if mx.is_windows():
                # ignore indirect symlinks on windows and link everything directly to the main tool
                # otherwise we lose the original program name
                self.layout[f'./{name}.cmd'] = f'string:{_make_windows_link(main_tool_name)}'
            else:
                self.layout[f'./{name}'] = f'link:{target}'

        for tool in self.tool_links:
            _add_link(tool, main_tool_name)
            alt_names = self.tool_links[tool]
            for alt_name in alt_names:
                _add_link(alt_name, tool)

        super().resolveDeps()


if mx.is_windows():
    DeliverableArchiveSuper = mx.LayoutZIPDistribution
else:
    DeliverableArchiveSuper = mx.LayoutTARDistribution


class DeliverableStandaloneArchive(DeliverableArchiveSuper):
    def __init__(self, suite, name=None, deps=None, excludedLibs=None, platformDependent=True, theLicense=None, defaultBuild=True, **kw_args):
        # mx deploy-artifacts takes the version from the suite, we ensure it is the same version as SDK.
        # This also checks the release field because release_version() is '...-dev' when release: False.
        assert suite.release_version() == _suite.release_version(), f"version from {suite.name} ({suite.release_version()}) does not match version in sdk ({_suite.release_version()})"

        # required
        standalone_dir_dist = _require(kw_args, 'standalone_dist', suite, name)
        community_archive_name = _require(kw_args, 'community_archive_name', suite, name)
        enterprise_archive_name = _require(kw_args, 'enterprise_archive_name', suite, name)

        # required but optional for compatibility and when the default *_dist_name are not good enough
        language_id = kw_args.pop('language_id', None)

        # TODO: remove this when language_id is set in those suites
        mapping = {
            'graal-js': 'js',
            'graal-nodejs': 'nodejs',
            'truffleruby': 'ruby',
            'graalpython': 'python',
        }
        if not language_id and suite.name in mapping:
            language_id = mapping[suite.name]

        # optional, derived from *_archive_name by default. Best left as default to avoid extra folder when extracting with some GUIs.
        community_dir_name = kw_args.pop('community_dir_name', None)
        enterprise_dir_name = kw_args.pop('enterprise_dir_name', None)

        # optional, the internal legacy distribution names for uploading, prefer setting language_id where possible
        community_dist_name = kw_args.pop('community_dist_name', None)
        enterprise_dist_name = kw_args.pop('enterprise_dist_name', None)

        if language_id:
            # Example community dist names:
            # JS_NATIVE_STANDALONE_SVM_JAVA25 (native standalone)
            # JS_JAVA_STANDALONE_SVM_JAVA25 (jvm standalone)
            assert '_NATIVE_' in standalone_dir_dist or '_JVM_' in standalone_dir_dist, f"Cannot find out whether {standalone_dir_dist} is a Native or JVM standalone, it should include _NATIVE_ or _JVM_"
            is_jvm = '_JVM_' in standalone_dir_dist
            jdk_version = get_bootstrap_graalvm_jdk_version().parts[0]
            if not community_dist_name:
                community_dist_name = f"{language_id.upper()}_{'JAVA' if is_jvm else 'NATIVE'}_STANDALONE_SVM_JAVA{jdk_version}"
            if not enterprise_dist_name:
                enterprise_dist_name = f"{language_id.upper()}_{'JAVA' if is_jvm else 'NATIVE'}_STANDALONE_SVM_SVMEE_JAVA{jdk_version}"

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('version', _suite.release_version)
        path_substitutions.register_no_arg('graalvm_os', mx_sdk_vm_impl.get_graalvm_os())
        string_substitutions = mx_subst.SubstitutionEngine(path_substitutions)

        if is_enterprise():
            dir_name = enterprise_dir_name or f'{enterprise_archive_name}-<version>-<graalvm_os>-<arch>'
            dist_name = enterprise_dist_name or 'STANDALONE_' + enterprise_archive_name.upper().replace('-', '_')
        else:
            dir_name = community_dir_name or f'{community_archive_name}-<version>-<graalvm_os>-<arch>'
            dist_name = community_dist_name or 'STANDALONE_' + community_archive_name.upper().replace('-', '_')

        layout = {
            f'{dir_name}/': {
                "source_type": "dependency",
                "dependency": standalone_dir_dist,
                "path": "*",
                "dereference": "never",
            }
        }
        self.standalone_dir_dist = standalone_dir_dist
        maven = { 'groupId': 'org.graalvm', 'tag': 'standalone' }
        assert theLicense is None, "the 'license' attribute is ignored for DeliverableStandaloneArchive"
        theLicense = ['GFTC' if is_enterprise() else 'UPL']
        super().__init__(suite, name=dist_name, deps=[], layout=layout, path=None, theLicense=theLicense, platformDependent=True, path_substitutions=path_substitutions, string_substitutions=string_substitutions, maven=maven, defaultBuild=defaultBuild)
        self.buildDependencies.append(standalone_dir_dist)
        self.reset_user_group = True

    def resolveDeps(self):
        super().resolveDeps()
        resolved = [self.standalone_dir_dist]
        self._resolveDepsHelper(resolved)
        self.standalone_dir_dist = resolved[0]

    def get_artifact_metadata(self):
        return {'edition': 'ee' if is_enterprise() else 'ce', 'type': 'standalone', 'project': 'graal'}
