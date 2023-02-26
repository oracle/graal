#
# Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
from abc import ABCMeta

import mx
import mx_javamodules
import mx_subst
import os
import re
import shutil
import tempfile
import textwrap
import types

from os.path import join, exists, isfile, isdir, dirname, relpath
from zipfile import ZipFile, ZIP_DEFLATED
from binascii import b2a_hex
from collections import OrderedDict
from argparse import ArgumentParser

from mx_javamodules import as_java_module, JavaModuleDescriptor

_suite = mx.suite('sdk')
_graalvm_components = dict()  # By short_name
_graalvm_components_by_name = dict()
_vm_configs = []
_graalvm_hostvm_configs = [
    ('jvm', [], ['--jvm'], 50),
    ('jvm-no-truffle-compilation', [], ['--jvm', '--experimental-options', '--engine.Compilation=false'], 29),
    ('native', [], ['--native'], 100),
    ('native-no-truffle-compilation', [], ['--native', '--experimental-options', '--engine.Compilation=false'], 39),
    ('jvm-3-compiler-threads', [], ['--jvm', '--engine.CompilerThreads=3'], 50),
    ('native-3-compiler-threads', [], ['--native', '--engine.CompilerThreads=3'], 100)
]
_known_vms = set()
_base_jdk = None


class AbstractNativeImageConfig(object, metaclass=ABCMeta):
    def __init__(self, destination, jar_distributions, build_args, use_modules=None, links=None, is_polyglot=False, dir_jars=False, home_finder=False, build_time=1, build_args_enterprise=None):  # pylint: disable=super-init-not-called
        """
        :type destination: str
        :type jar_distributions: list[str]
        :type build_args: list[str]
        :param str | None use_modules: Run (with 'launcher') or run and build image with module support (with 'image').
        :type links: list[str] | None
        :type is_polyglot: bool
        :param bool dir_jars: If true, all jars in the component directory are added to the classpath.
        :type home_finder: bool
        :type build_time: int
        :type build_args_enterprise: list[str] | None
        """
        self.destination = mx_subst.path_substitutions.substitute(destination)
        self.jar_distributions = jar_distributions
        self.build_args = build_args
        self.use_modules = use_modules
        self.links = [mx_subst.path_substitutions.substitute(link) for link in links] if links else []
        self.is_polyglot = is_polyglot
        self.dir_jars = dir_jars
        self.home_finder = home_finder
        self.build_time = build_time
        self.build_args_enterprise = build_args_enterprise or []
        self.relative_home_paths = {}

        assert isinstance(self.jar_distributions, list)
        assert isinstance(self.build_args, (list, types.GeneratorType))
        assert isinstance(self.build_args_enterprise, list)

    def __str__(self):
        return self.destination

    def __repr__(self):
        return str(self)

    @staticmethod
    def get_add_exports_list(required_exports, custom_target_module_str=None):
        add_exports = []
        for required in required_exports:
            target_modules = required_exports[required]
            target_modules_str = custom_target_module_str or ','.join(sorted(target_module.name for target_module in target_modules))
            required_module_name, required_package_name = required
            add_exports.append('--add-exports=' + required_module_name + '/' + required_package_name + "=" + target_modules_str)
        return sorted(add_exports)

    def get_add_exports(self, missing_jars):
        if self.use_modules is None:
            return ''
        distributions = self.jar_distributions
        distributions_transitive = mx.classpath_entries(distributions)
        distributions_transitive_clean = [entry for entry in distributions_transitive if str(entry) not in missing_jars]
        required_exports = mx_javamodules.requiredExports(distributions_transitive_clean, base_jdk())
        return AbstractNativeImageConfig.get_add_exports_list(required_exports)

    def add_relative_home_path(self, language, path):
        if language in self.relative_home_paths and self.relative_home_paths[language] != path:
            raise Exception('the relative home path of {} is already set to {} and cannot also be set to {} for {}'.format(
                language, self.relative_home_paths[language], path, self.destination))
        self.relative_home_paths[language] = path

class LauncherConfig(AbstractNativeImageConfig):
    def __init__(self, destination, jar_distributions, main_class, build_args, is_main_launcher=True,
                 default_symlinks=True, is_sdk_launcher=False, custom_launcher_script=None, extra_jvm_args=None,
                 use_modules=None, main_module=None, link_at_build_time=True, option_vars=None, home_finder=True, **kwargs):
        """
        :param str main_class
        :param bool is_main_launcher
        :param bool default_symlinks
        :param bool is_sdk_launcher: Whether it uses org.graalvm.launcher.Launcher
        :param str custom_launcher_script: Custom launcher script, to be used when not compiled as a native image
        :param list[str] | None extra_jvm_args
        :param str main_module: Specifies the main module. Mandatory if use_modules is not None
        :param list[str] | None option_vars
        """
        super(LauncherConfig, self).__init__(destination, jar_distributions, build_args, use_modules=use_modules, home_finder=home_finder, **kwargs)
        self.main_module = main_module
        assert self.use_modules is None or self.main_module
        self.main_class = main_class
        self.link_at_build_time = link_at_build_time
        self.is_main_launcher = is_main_launcher
        self.default_symlinks = default_symlinks
        self.is_sdk_launcher = is_sdk_launcher
        self.custom_launcher_script = custom_launcher_script
        self.extra_jvm_args = [] if extra_jvm_args is None else extra_jvm_args
        self.option_vars = [] if option_vars is None else option_vars


class LanguageLauncherConfig(LauncherConfig):
    def __init__(self, destination, jar_distributions, main_class, build_args, language,
                 is_sdk_launcher=True, **kwargs):
        """
        :param str language
        """
        super(LanguageLauncherConfig, self).__init__(destination, jar_distributions, main_class, build_args,
                                                     is_sdk_launcher=is_sdk_launcher, is_polyglot=False, **kwargs)
        self.language = language

        # Ensure the language launcher can always find the language home
        self.add_relative_home_path(language, relpath('.', dirname(destination)))


class LibraryConfig(AbstractNativeImageConfig):
    def __init__(self, destination, jar_distributions, build_args, jvm_library=False, use_modules=None, add_to_module=None, home_finder=False, headers=True, **kwargs):
        """
        :param bool jvm_library
        :param str add_to_module: the simple name of a module that should be modified to include this native library. It must not be a path or end with `.jmod`
        :param bool headers: whether headers produced by the native image build should be placed next to the native image.
        """
        super(LibraryConfig, self).__init__(destination, jar_distributions, build_args, use_modules=use_modules, home_finder=home_finder, **kwargs)
        self.jvm_library = jvm_library
        self.add_to_module = add_to_module
        self.headers = headers


class LanguageLibraryConfig(LibraryConfig):
    def __init__(self, jar_distributions, build_args, language, main_class=None, is_sdk_launcher=True, launchers=None, option_vars=None, headers=False, **kwargs):
        """
        :param str language
        :param str main_class
        """
        kwargs.pop('destination', None)
        super(LanguageLibraryConfig, self).__init__('lib/<lib:' + language + 'vm>', jar_distributions, build_args, home_finder=True, headers=headers, **kwargs)
        if not launchers:
            assert not main_class
        self.is_sdk_launcher = is_sdk_launcher
        self.main_class = main_class
        self.language = language
        self.default_symlinks = None
        self.relative_home_paths = {}
        self.launchers = [mx_subst.path_substitutions.substitute(l) for l in launchers] if launchers else []
        self.option_vars = [] if option_vars is None else option_vars

        # Ensure the language launcher can always find the language home
        self.add_relative_home_path(language, relpath('.', dirname(self.destination)))

class GraalVmComponent(object):
    def __init__(self,
                 suite,
                 name,
                 short_name,
                 license_files,
                 third_party_license_files,
                 jar_distributions=None,
                 builder_jar_distributions=None,
                 support_distributions=None,
                 support_headers_distributions=None,
                 support_libraries_distributions=None,
                 dir_name=None,
                 launcher_configs=None,
                 library_configs=None,
                 provided_executables=None,
                 polyglot_lib_build_args=None,
                 polyglot_lib_jar_dependencies=None,
                 polyglot_lib_build_dependencies=None,
                 has_polyglot_lib_entrypoints=False,
                 boot_jars=None,
                 jvmci_parent_jars=None,
                 jlink=True,
                 priority=None,
                 installable=None,
                 post_install_msg=None,
                 installable_id=None,
                 dependencies=None,
                 supported=None,
                 early_adopter=False,
                 stability=None,
                 extra_installable_qualifiers=None):
        """
        :param suite mx.Suite: the suite this component belongs to
        :type name: str
        :param str short_name: a short, unique name for this component
        :param str | None | False dir_name: the directory name in which this component lives. If `None`, the `short_name` is used. If `False`, files are copied to the root-dir for the component type.
        :param installable: Produce a distribution installable via `gu`
        :param post_install_msg: Post-installation message to be printed
        :param list[str] dependencies: a list of component names
        :param list[str | (str, str)] provided_executables: executables to be placed in the appropriate `bin` directory.
            In the list, strings represent a path inside the component (e.g., inside a support distribution).
            Tuples `(dist, exec)` represent an executable to be copied found in `dist`, at path `exec` (the same basename will be used).
        :type license_files: list[str]
        :type third_party_license_files: list[str]
        :type polyglot_lib_build_args: list[str]
        :type polyglot_lib_jar_dependencies: list[str]
        :type polyglot_lib_build_dependencies: list[str]
        :type has_polyglot_lib_entrypoints: bool
        :type boot_jars: list[str]
        :type jvmci_parent_jars: list[str]
        :type launcher_configs: list[LauncherConfig]
        :type library_configs: list[LibraryConfig]
        :type jar_distributions: list[str]
        :type builder_jar_distributions: list[str]
        :type support_distributions: list[str]
        :type support_headers_distributions: list[str]
        :type support_libraries_distributions: list[str]
        :param int priority: priority with a higher value means higher priority
        :type installable: bool
        :type installable_id: str
        :type post_install_msg: str
        :type stability: str | None
        :type extra_installable_qualifiers: list[str] | None
        """
        if dependencies is None:
            mx.logv('Component {} does not specify dependencies'.format(name))

        self.suite = suite
        self.name = name
        self.short_name = short_name
        self.dir_name = dir_name if dir_name is not None else short_name
        self.license_files = license_files
        self.third_party_license_files = third_party_license_files
        self.dependency_names = dependencies or []
        self.provided_executables = provided_executables or []
        self.polyglot_lib_build_args = polyglot_lib_build_args or []
        self.polyglot_lib_jar_dependencies = polyglot_lib_jar_dependencies or []
        self.polyglot_lib_build_dependencies = polyglot_lib_build_dependencies or []
        self.has_polyglot_lib_entrypoints = has_polyglot_lib_entrypoints
        self.boot_jars = boot_jars or []
        self.jvmci_parent_jars = jvmci_parent_jars or []
        self.jar_distributions = jar_distributions or []
        self.builder_jar_distributions = builder_jar_distributions or []
        self.support_distributions = support_distributions or []
        self.support_headers_distributions = support_headers_distributions or []
        self.support_libraries_distributions = support_libraries_distributions or []
        self.priority = priority or 0
        self.launcher_configs = launcher_configs or []
        self.library_configs = library_configs or []
        if installable is None:
            installable = isinstance(self, GraalVmLanguage)
        self.installable = installable
        self.post_install_msg = post_install_msg
        self.installable_id = installable_id or self.dir_name
        self.extra_installable_qualifiers = extra_installable_qualifiers or []

        if supported is not None or early_adopter:
            if stability is not None:
                raise mx.abort("{}: Cannot use `stability` attribute in combination with deprecated `supported` and `early_adopter` attributes".format(name))
            mx.warn("{}: `supported` and `early_adopter` attributes are deprecated, please use `stability`".format(name))

        if stability is None:
            if supported:
                if early_adopter:
                    stability = "earlyadopter"
                else:
                    stability = "supported"
            else:
                if early_adopter:
                    stability = "experimental-earlyadopter"
                else:
                    stability = "experimental"
        self.stability = stability

        self.jlink = jlink

        assert isinstance(self.jar_distributions, list)
        assert isinstance(self.builder_jar_distributions, list)
        assert isinstance(self.support_distributions, list)
        assert isinstance(self.support_headers_distributions, list)
        assert isinstance(self.support_libraries_distributions, list)
        assert isinstance(self.license_files, list)
        assert isinstance(self.third_party_license_files, list)
        assert isinstance(self.provided_executables, list)
        assert isinstance(self.polyglot_lib_build_args, list)
        assert isinstance(self.polyglot_lib_jar_dependencies, list)
        assert isinstance(self.polyglot_lib_build_dependencies, list)
        assert isinstance(self.boot_jars, list)
        assert isinstance(self.jvmci_parent_jars, list)
        assert isinstance(self.launcher_configs, list)
        assert isinstance(self.library_configs, list)
        assert isinstance(self.extra_installable_qualifiers, list)

        assert not any(cp_arg in self.polyglot_lib_build_args for cp_arg in ('-cp', '-classpath')), "the '{}' component passes a classpath argument to libpolylgot: '{}'. Use `polyglot_lib_jar_dependencies` instead".format(self.name, ' '.join(self.polyglot_lib_build_args))

    def __str__(self):
        return "{} ({})".format(self.name, self.dir_name)

    def direct_dependencies(self):
        try:
            return [graalvm_component_by_name(name) for name in self.dependency_names]
        except Exception as e:
            raise Exception("{} (required by {})".format(e, self.name))


class GraalVmTruffleComponent(GraalVmComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, truffle_jars,
                 include_in_polyglot=None, standalone_dir_name=None, standalone_dependencies=None, **kwargs):
        """
        :param list[str] truffle_jars: JAR distributions that should be on the classpath for the language implementation.
        :param bool include_in_polyglot: whether this component is included in `--language:all` or `--tool:all` and should be part of polyglot images (deprecated).
        :param str standalone_dir_name: name for the standalone archive and directory inside
        :param dict[str, (str, list[str])] standalone_dependencies: dict of dependent components to include in the standalone in the form {component name: (relative path, excluded_paths)}.
        """
        super(GraalVmTruffleComponent, self).__init__(suite, name, short_name, license_files, third_party_license_files,
                                                      jar_distributions=truffle_jars, **kwargs)
        if include_in_polyglot is not None:
            mx.warn('"include_in_polyglot" is deprecated. Please drop all uses.')
        self.standalone_dir_name = standalone_dir_name or '{}-<version>-<graalvm_os>-<arch>'.format(self.dir_name)
        self.standalone_dependencies = standalone_dependencies or {}
        assert isinstance(self.standalone_dependencies, dict)


class GraalVmLanguage(GraalVmTruffleComponent):
    """
    :param support_distributions: distributions the contents of which is added to the language's home directory.
    The contents of support distributions setting the `fileListPurpose` attribute to `native-image-resources` will end up as file list in the `native-image-resources.filelist` file in this language's home directory.
    As a part of a native image build that includes this language, the files in the merged file list will be copied as resources to a directory named `resources` next to the produced image.
    """
    pass


class GraalVmTool(GraalVmTruffleComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, truffle_jars,
                 include_by_default=False, **kwargs):
        """
        :type include_by_default: bool
        """
        super(GraalVmTool, self).__init__(suite, name, short_name, license_files, third_party_license_files,
                                          truffle_jars, **kwargs)
        self.include_by_default = include_by_default


class GraalVMSvmMacro(GraalVmComponent):
    pass


class GraalVmJdkComponent(GraalVmComponent):
    pass


class GraalVmJreComponent(GraalVmComponent):
    pass


class GraalVmJvmciComponent(GraalVmJreComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, jvmci_jars, **kwargs):
        """
        :type jvmci_jars: list[str]
        """
        super(GraalVmJvmciComponent, self).__init__(suite, name, short_name, license_files, third_party_license_files,
                                                    **kwargs)
        self.jvmci_jars = jvmci_jars or []

        assert isinstance(self.jvmci_jars, list)


def register_graalvm_component(component):
    """
    :type component: GraalVmComponent
    """
    def _log_ignored_component(kept, ignored):
        """
        :type kept: GraalVmComponent
        :type ignored: GraalVmComponent
        """
        mx.logv('Suites \'{}\' and \'{}\' are registering a component with the same short name (\'{}\'), with priority \'{}\' and \'{}\' respectively.'.format(kept.suite.name, ignored.suite.name, kept.short_name, kept.priority, ignored.priority))
        mx.logv('Ignoring the one from suite \'{}\'.'.format(ignored.suite.name))

    assert ',' not in component.short_name, component.short_name
    assert ',' not in component.name, component.name

    _prev = _graalvm_components.get(component.short_name, None)
    if _prev:
        if _prev.priority == component.priority:
            mx.abort('Suites \'{}\' and \'{}\' are registering a component with the same short name (\'{}\') and priority (\'{}\')'.format(_prev.suite.name, component.suite.name, _prev.short_name, _prev.priority))
        elif _prev.priority < component.priority:
            _graalvm_components[component.short_name] = component
            del _graalvm_components_by_name[_prev.name]
            _graalvm_components_by_name[component.name] = component
            _log_ignored_component(component, _prev)
        else:
            _log_ignored_component(_prev, component)
    else:
        _graalvm_components[component.short_name] = component
        _graalvm_components_by_name[component.name] = component


def graalvm_component_by_name(name, fatalIfMissing=True):
    """
    :rtype: GraalVmComponent
    """
    if name in _graalvm_components:
        return _graalvm_components[name]
    elif name in _graalvm_components_by_name:
        return _graalvm_components_by_name[name]
    else:
        if fatalIfMissing:
            raise Exception("Unknown component: {}".format(name))
        return None

def graalvm_components(opt_limit_to_suite=False):
    """
    :rtype: list[GraalVmComponent]
    """
    if opt_limit_to_suite and mx.get_opts().specific_suites:
        return [c for c in _graalvm_components.values() if c.suite.name in mx.get_opts().specific_suites]
    else:
        return list(_graalvm_components.values())


def graalvm_home(fatalIfMissing=False):
    import mx_sdk_vm_impl
    return mx_sdk_vm_impl.graalvm_home(fatalIfMissing=fatalIfMissing)


def add_graalvm_hostvm_config(name, java_args=None, launcher_args=None, priority=0):
    """
    :type name: str
    :type java_args: list[str] | None
    :type launcher_args: list[str] | None
    :type priority: int
    """
    _graalvm_hostvm_configs.append((name, java_args, launcher_args, priority))


def register_vm_config(config_name, components, suite, dist_name=None, env_file=None):
    """
    :type config_name: str
    :type components: list[str]
    :type suite: mx.Suite
    :type dist_name: str
    :type env_file: str or None
    """
    assert config_name is not None
    assert components is not None and len(components)
    _dist_name = dist_name or config_name
    _vm_configs.append((_dist_name, config_name, components, suite, env_file))


def get_graalvm_hostvm_configs():
    return _graalvm_hostvm_configs


def register_known_vm(name):
    if name in _known_vms:
        raise mx.abort("VM '{}' already registered".format(name))
    _known_vms.add(name)


def base_jdk():
    global _base_jdk
    if _base_jdk is None:
        _base_jdk = mx.get_jdk(tag='default')
    return _base_jdk


def base_jdk_version():
    return base_jdk().javaCompliance.value


def _probe_jvmci_info(jdk, attribute_name):
    if not hasattr(jdk, '.enables_jvmci_by_default'):
        out = mx.LinesOutputCapture()
        sink = lambda x: x
        mx.run([jdk.java, '-XX:+UnlockExperimentalVMOptions', '-XX:+PrintFlagsFinal', '-version'], out=out, err=sink)
        enableJVMCI = False
        enableJVMCIProduct = False
        jvmciThreadsPerNativeLibraryRuntime = None
        for line in out.lines:
            if 'EnableJVMCI' in line and 'true' in line:
                enableJVMCI = True
            if 'EnableJVMCIProduct' in line:
                enableJVMCIProduct = True
            if 'JVMCIThreadsPerNativeLibraryRuntime' in line:
                m = re.search(r'JVMCIThreadsPerNativeLibraryRuntime *= *(\d+)', line)
                if not m:
                    mx.abort(f'Could not extract value of JVMCIThreadsPerNativeLibraryRuntime from "{line}"')
                jvmciThreadsPerNativeLibraryRuntime = int(m.group(1))
        setattr(jdk, '.enables_jvmci_by_default', enableJVMCI)
        setattr(jdk, '.supports_enablejvmciproduct', enableJVMCIProduct)
        setattr(jdk, '.jvmciThreadsPerNativeLibraryRuntime', jvmciThreadsPerNativeLibraryRuntime)
    return getattr(jdk, attribute_name)

def jdk_enables_jvmci_by_default(jdk):
    """
    Gets the default value for the EnableJVMCI VM option in `jdk`.
    """
    return _probe_jvmci_info(jdk, '.enables_jvmci_by_default')

def jdk_supports_enablejvmciproduct(jdk):
    """
    Determines if the jdk supports flag -XX:+EnableJVMCIProduct which isn't the case
    for some OpenJDK 11u distros.
    """
    return _probe_jvmci_info(jdk, '.supports_enablejvmciproduct')

def get_JVMCIThreadsPerNativeLibraryRuntime(jdk):
    """
    Gets the value of the flag -XX:JVMCIThreadsPerNativeLibraryRuntime.

    Returns None if this flag is not supported in `jdk` otherwise returns the default value as an int
    """
    return _probe_jvmci_info(jdk, '.jvmciThreadsPerNativeLibraryRuntime')

def _probe_jlink_info(jdk, attribute_name):
    """
    Determines if the jlink executable in `jdk` supports various options such
    as those added by JDK-8232080 and JDK-8237467.
    """
    if not hasattr(jdk, '.supports_JDK_8232080'):
        output = mx.OutputCapture()
        jlink_exe = jdk.javac.replace('javac', 'jlink')
        mx.run([jlink_exe, '--list-plugins'], out=output)
        setattr(jdk, '.supports_JDK_8232080', '--add-options=' in output.data or '--add-options ' in output.data)
        setattr(jdk, '.supports_save_jlink_argfiles', '--save-jlink-argfiles=' in output.data or '--save-jlink-argfiles ' in output.data)
        setattr(jdk, '.supports_copy_files', '--copy-files=' in output.data or '--copy-files ' in output.data)
    return getattr(jdk, attribute_name)

def jlink_supports_8232080(jdk):
    """
    Determines if the jlink executable in `jdk` supports ``--add-options`` and
    ``--vendor-[bug-url|vm-bug-url|version]`` added by JDK-8232080.
    """
    return _probe_jlink_info(jdk, '.supports_JDK_8232080')

def jlink_has_save_jlink_argfiles(jdk):
    """
    Determines if the jlink executable in `jdk` supports ``--save-jlink-argfiles``.
    """
    return _probe_jlink_info(jdk, '.supports_save_jlink_argfiles')

def _jdk_omits_warning_for_jlink_set_ThreadPriorityPolicy(jdk): # pylint: disable=invalid-name
    """
    Determines if the `jdk` suppresses a warning about ThreadPriorityPolicy when it
    is non-zero if the value is set from the jimage.
    https://bugs.openjdk.java.net/browse/JDK-8235908.
    """
    if not hasattr(jdk, '.omits_ThreadPriorityPolicy_warning'):
        out = mx.OutputCapture()
        sink = lambda x: x
        tmpdir = tempfile.mkdtemp(prefix='jdk_omits_warning_for_jlink_set_ThreadPriorityPolicy')
        jlink_exe = jdk.javac.replace('javac', 'jlink')
        mx.run([jlink_exe, '--add-options=-XX:ThreadPriorityPolicy=1', '--output=' + join(tmpdir, 'jdk'), '--add-modules=java.base'])
        mx.run([mx.exe_suffix(join(tmpdir, 'jdk', 'bin', 'java')), '-version'], out=sink, err=out)
        shutil.rmtree(tmpdir)
        setattr(jdk, '.omits_ThreadPriorityPolicy_warning', '-XX:ThreadPriorityPolicy=1 may require system level permission' not in out.data)
    return getattr(jdk, '.omits_ThreadPriorityPolicy_warning')

def _read_java_base_hashes(jdk):
    """
    Read the hashes stored in the ``java.base`` module of `jdk`.
    """
    hashes = {}
    out = mx.LinesOutputCapture()
    mx.run([jdk.exe_path('jmod'), 'describe', join(jdk.home, 'jmods', 'java.base.jmod')], out=out)
    lines = out.lines
    for line in lines:
        if line.startswith('hashes'):
            parts = line.split()
            assert len(parts) == 4, 'expected hashes line to have 4 fields, got {} fields: {}'.format(len(parts), line)
            _, module_name, algorithm, hash_value = parts
            hashes[module_name] = (algorithm, hash_value)
    return hashes

def _patch_default_security_policy(build_dir, jmods_dir, dst_jdk_dir):
    """
    Edits the default security policy in the ``lib/security/default.policy`` entry of
    `jmods_dir`/java.base.jmod to grant all permissions to anything loaded from
    ``${java.home}/languages/-`` as well as the following modules:
        com.oracle.graal.graal_enterprise
        org.graalvm.truffle
        org.graalvm.sdk
    Extra permissions are also granted to:
        org.graalvm.locator

    :returns str: path to the patched version of ``java.base.jmod``
    """

    graalvm_policy = textwrap.dedent("""
        grant codeBase "jrt:/com.oracle.graal.graal_enterprise" {
            permission java.security.AllPermission;
        };
        grant codeBase "jrt:/org.graalvm.truffle" {
            permission java.security.AllPermission;
        };

        grant codeBase "jrt:/org.graalvm.sdk" {
            permission java.security.AllPermission;
        };

        grant codeBase "jrt:/org.graalvm.locator" {
          permission java.io.FilePermission "<<ALL FILES>>", "read";
          permission java.util.PropertyPermission "*", "read,write";
          permission java.lang.RuntimePermission "createClassLoader";
          permission java.lang.RuntimePermission "getClassLoader";
          permission java.lang.RuntimePermission "getenv.*";
        };

        grant codeBase "file:${java.home}/languages/-" {
            permission java.security.AllPermission;
        };
        """)
    graalvm_policy_utf8 = graalvm_policy.encode('utf-8')
    policy_entry = 'lib/security/default.policy'
    patched_java_base = join(build_dir, 'java.base.jmod')
    patched_java_base_source = join(build_dir, 'java.base.jmod.source')
    dst_patched_java_base = join(dst_jdk_dir, 'jmods', 'java.base.jmod')

    def open_jmods(to_read, to_write=None):
        in_fp = open(to_read, 'rb')
        out_fp = None if to_write is None else open(to_write, 'wb')
        jmod_header = in_fp.read(4)
        if len(jmod_header) != 4 or jmod_header != b'JM\x01\x00':
            raise mx.abort("Unexpected jmod header in {}: {}".format(to_read, b2a_hex(jmod_header).decode('ascii')))
        if out_fp:
            out_fp.write(jmod_header)
            return in_fp, out_fp
        return in_fp

    def needs_patching(java_base_jmod):
        # Return True if a different JDK was used for the current patched java.base
        if not exists(patched_java_base_source):
            return True
        with open(patched_java_base_source) as fp:
            source = fp.read()
            if source != jmods_dir:
                return True
        if exists(java_base_jmod):
            with open_jmods(java_base_jmod) as fp:
                with ZipFile(fp, 'r') as zf:
                    policy = zf.read(policy_entry).decode('utf-8')
                    return graalvm_policy not in policy
        return True

    if not needs_patching(dst_patched_java_base):
        shutil.copy(dst_patched_java_base, patched_java_base)
        return patched_java_base

    # Record the jmods directory of the original java.base module
    with mx.open(patched_java_base_source, 'w') as fp:
        fp.write(jmods_dir)

    fps = open_jmods(join(jmods_dir, 'java.base.jmod'), patched_java_base)
    with fps[0] as src_f, fps[1] as dst_f:
        policy_found = False
        with ZipFile(src_f, 'r') as src_zip, ZipFile(dst_f, 'w', src_zip.compression) as dst_zip:
            for i in src_zip.infolist():
                if i.filename[-1] == '/':
                    continue
                src_member = src_zip.read(i)
                if i.filename == policy_entry:
                    policy_found = True
                    if graalvm_policy_utf8 not in src_member:
                        src_member += graalvm_policy_utf8
                dst_zip.writestr(i, src_member)
        if not policy_found:
            raise mx.abort("Couldn't find `{}` in {}".format(policy_entry, join(jmods_dir, 'java.base.jmod')))
    return patched_java_base

def _get_image_root_modules(root_module_names, module_names, jdk_module_names, use_upgrade_module_path):
    """
    Gets the argument for the jlink ``--add-modules`` flag (i.e. the roots of the module graph to link into the image)

    :return frozenset: the set of root module names
    """
    all_names = frozenset(jdk_module_names).union(module_names)
    if root_module_names is not None:
        missing = frozenset(root_module_names).difference(jdk_module_names).difference(module_names)
        if missing:
            mx.abort('Invalid module(s): {}.\nAvailable modules: {}'.format(','.join(missing), ','.join(sorted(all_names))))
        if use_upgrade_module_path:
            # Modules that will be on --upgrade-module-path are excluded from the image
            return frozenset((n for n in root_module_names if n not in module_names))
        else:
            return frozenset(root_module_names)
    else:
        if use_upgrade_module_path:
            # Modules that will be on --upgrade-module-path are excluded from the image
            return frozenset((n for n in jdk_module_names if n not in module_names))
        else:
            return all_names

def _get_image_vm_options(jdk, use_upgrade_module_path, modules, synthetic_modules):
    """
    Gets the argument for the jlink ``--add-options`` flag.

    :return list: the list of VM options to cook into the image
    """
    vm_options = []
    if jlink_supports_8232080(jdk):
        if use_upgrade_module_path or _jdk_omits_warning_for_jlink_set_ThreadPriorityPolicy(jdk):
            vm_options.append('-XX:ThreadPriorityPolicy=1')
        else:
            mx.logv('[Creating JDK without -XX:ThreadPriorityPolicy=1]')

        if jdk_supports_enablejvmciproduct(jdk):
            non_synthetic_modules = [m.name for m in modules if m not in synthetic_modules]
            if 'jdk.internal.vm.compiler' in non_synthetic_modules:
                threads = get_JVMCIThreadsPerNativeLibraryRuntime(jdk)
                vm_options.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCIProduct'])
                if threads is not None and threads != 1:
                    vm_options.append('-XX:JVMCIThreadsPerNativeLibraryRuntime=1')
                vm_options.extend(['-XX:-UnlockExperimentalVMOptions'])
            else:
                # Don't default to using JVMCI as JIT unless Graal is being updated in the image.
                # This avoids unexpected issues with using the out-of-date Graal compiler in
                # the JDK itself.
                vm_options.extend(['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCIProduct', '-XX:-UseJVMCICompiler', '-XX:-UnlockExperimentalVMOptions'])
        else:
            mx.logv('[Creating JDK without -XX:+EnableJVMCIProduct]')
        if modules and use_upgrade_module_path:
            vm_options.append('--upgrade-module-path=' + os.pathsep.join((synthetic_modules.get(m, m.jarpath) for m in modules)))
    elif use_upgrade_module_path:
        mx.abort('Cannot create an image with an --upgrade-module-path setting since jlink does not support the --add-options flag')
    return vm_options

def _copy_src_zip(from_jdk, to_jdk, extra_modules, extra_modules_predicate):
    """
    Creates `to_jdk`/lib/src.zip from the entries in `from_jdk`/src.zip except for those
    in the sources of `extra_modules` plus the sources of the modules in `extra_modules`
    unless `extra_modules_predicate` returns False.
    """
    from_src_zip = join(from_jdk, 'lib', 'src.zip')
    to_src_zip = join(to_jdk, 'lib', 'src.zip')
    with ZipFile(to_src_zip, 'w', compression=ZIP_DEFLATED, allowZip64=True) as out_zf:
        mx.logv('[Creating ' + to_src_zip + ']')
        if isfile(from_src_zip):
            extra_module_names = frozenset((jmd.name for jmd in extra_modules))
            with ZipFile(from_src_zip, 'r') as in_zf:
                for name in in_zf.namelist():
                    module_name = name.split('/', 1)[0]
                    if not name.endswith('/') and module_name not in extra_module_names:
                        out_zf.writestr(name, in_zf.read(name))
        else:
            mx.warn("'{}' does not exist or is not a file".format(from_src_zip))
        for jmd in extra_modules:
            jmd_src_zip = jmd.jarpath[0:-len('.jar')] + '.src.zip'
            if isfile(jmd_src_zip) and extra_modules_predicate(jmd):
                mx.logv('[Extracting ' + jmd_src_zip + ']')
                with ZipFile(jmd_src_zip, 'r') as in_zf:
                    for name in in_zf.namelist():
                        if not name.endswith('/'):
                            if 'module-info' in name:
                                print('2. writing', name)
                            out_zf.writestr(jmd.name + '/' + name, in_zf.read(name))
            # Add module-info.java to sources
            out_zf.writestr(jmd.name + '/module-info.java', jmd.as_module_info(extras_as_comments=False))

def _vm_options_match(vm_options, vm_options_path):
    """
    Determines if `vm_options` matches the value persisted in `vm_options_path`.
    """
    if not exists(vm_options_path):
        return False
    with open(vm_options_path) as fp:
        previous_vm_options = fp.read().strip()
        current_vm_options = os.linesep.join(vm_options)
        return previous_vm_options == current_vm_options

def jlink_new_jdk(jdk, dst_jdk_dir, module_dists, ignore_dists,
                  root_module_names=None,
                  missing_export_target_action='create',
                  with_source=lambda x: True,
                  vendor_info=None,
                  dedup_legal_notices=True,
                  use_upgrade_module_path=False):
    """
    Uses jlink from `jdk` to create a new JDK image in `dst_jdk_dir` with `module_dists` and
    their dependencies added to the JDK image, replacing any existing modules of the same name.

    :param JDKConfig jdk: source JDK
    :param str dst_jdk_dir: path to use for the jlink --output option
    :param list module_dists: list of distributions defining modules
    :param list ignore_dists: list of distributions that should be ignored for missing_export_target_action
    :param list root_module_names: list of strings naming the module root set for the new JDK image.
                     The named modules must either be in `module_dists` or in `jdk`. If None, then
                     the root set will be all the modules in ``module_dists` and `jdk`.
    :param str missing_export_target_action: the action to perform for a qualified export target that
                     is not present in `module_dists` and does not have a hash stored in java.base.
                     The choices are:
                       "create" - an empty module is created
                        "error" - raise an error
                           None - do nothing
    :param lambda with_source: returns True if the sources of a module distribution must be included in the new JDK
    :param dict vendor_info: values for the jlink vendor options added by JDK-8232080
    :param bool use_upgrade_module_path: if True, then instead of linking `module_dists` into the image, resolve
                     them via --upgrade-module-path at image runtime
    :return bool: False if use_upgrade_module_path == True and the existing image is up to date otherwise True
    """
    assert callable(with_source)

    if jdk.javaCompliance < '9':
        mx.abort('Cannot derive a new JDK from ' + jdk.home + ' with jlink since it is not JDK 9 or later')

    exploded_java_base_module = join(jdk.home, 'modules', 'java.base')
    if exists(exploded_java_base_module):
        mx.abort('Cannot derive a new JDK from ' + jdk.home + ' since it appears to be a developer build with exploded modules')

    jimage = join(jdk.home, 'lib', 'modules')
    jmods_dir = join(jdk.home, 'jmods')
    if not isfile(jimage):
        mx.abort('Cannot derive a new JDK from ' + jdk.home + ' since ' + jimage + ' is missing or is not an ordinary file')
    if not isdir(jmods_dir):
        mx.abort('Cannot derive a new JDK from ' + jdk.home + ' since ' + jmods_dir + ' is missing or is not a directory')

    # Exclude jdk.aot due to GR-10545 and JDK-8255616
    jdk_modules = {jmd.name: jmd for jmd in jdk.get_modules() if jmd.name != 'jdk.aot'}
    modules = [as_java_module(dist, jdk) for dist in module_dists]
    module_names = frozenset((m.name for m in modules))
    all_module_names = frozenset(list(jdk_modules.keys())) | module_names

    # Read hashes stored in java.base (the only module in the JDK where hashes are stored)
    hashes = _read_java_base_hashes(jdk)

    build_dir = mx.ensure_dir_exists(join(dst_jdk_dir + ".build"))

    # Directory under dst_jdk_dir for artifacts related to use_upgrade_module_path
    upgrade_dir = join(dst_jdk_dir, 'upgrade_modules_support')

    # Map from JavaModuleDescriptors to post-jlink jar location.
    synthetic_modules = OrderedDict()
    try:
        ignore_module_names = set(mx_javamodules.get_module_name(mx.dependency(ignore_dist)) for ignore_dist in ignore_dists)
        # Synthesize modules for targets of qualified exports that are not present in `modules`.
        # Without this, runtime module resolution will fail due to missing modules.
        target_requires = {}
        for jmd in modules:
            for targets in jmd.exports.values():
                for target in targets:
                    if target not in all_module_names and target not in ignore_module_names and target not in hashes:
                        target_requires.setdefault(target, set()).add(jmd.name)
        if target_requires and missing_export_target_action is not None:
            if missing_export_target_action == 'error':
                mx.abort('Target(s) of qualified exports cannot be resolved: ' + '.'.join(target_requires.keys()))
            assert missing_export_target_action == 'create', 'invalid value for missing_export_target_action: ' + str(missing_export_target_action)

            for name, requires in sorted(target_requires.items()):
                module_jar = join(build_dir, name + '.jar')
                jmd = JavaModuleDescriptor(name, {}, requires={module: [] for module in requires}, uses=set(), provides={}, jarpath=module_jar)
                module_build_dir = mx.ensure_dir_exists(join(build_dir, name))
                module_info = jmd.as_module_info()
                module_info_java = join(module_build_dir, 'module-info.java')
                module_info_class = join(module_build_dir, 'module-info.class')
                dst_module_jar = join(upgrade_dir, name + '.jar')
                synthetic_modules[jmd] = dst_module_jar
                if use_upgrade_module_path and exists(dst_module_jar):
                    with ZipFile(dst_module_jar, 'r') as zf:
                        previous_module_info = zf.read('module-info.java').decode()
                    if previous_module_info == module_info:
                        mx.logv('[Reusing synthetic module {}]'.format(name))
                        os.rename(dst_module_jar, module_jar)
                        continue
                    mx.logv('[Rebuilding synthetic module {} as module descriptor changed]'.format(name))

                with open(module_info_java, 'w') as fp:
                    fp.write(module_info)
                mx.run([jdk.javac, '-d', module_build_dir,
                        '--limit-modules=java.base,' + ','.join(jmd.requires.keys()),
                        '--module-path=' + os.pathsep.join((m.jarpath for m in modules)),
                        module_info_java])
                with ZipFile(module_jar, 'w') as zf:
                    zf.write(module_info_java, 'module-info.java')
                    zf.write(module_info_class, 'module-info.class')
                if exists(jmd.get_jmod_path()):
                    os.remove(jmd.get_jmod_path())
                if not use_upgrade_module_path:
                    mx.run([jdk.javac.replace('javac', 'jmod'), 'create', '--class-path=' + module_build_dir, jmd.get_jmod_path()])

            modules.extend(synthetic_modules.keys())
            module_names = frozenset((m.name for m in modules))
            all_module_names = frozenset(list(jdk_modules.keys())) | module_names

        # Edit lib/security/default.policy in java.base
        patched_java_base = _patch_default_security_policy(build_dir, jmods_dir, dst_jdk_dir)

        # Now build the new JDK image with jlink
        jlink = [jdk.javac.replace('javac', 'jlink')]
        jlink_persist = []

        if jdk_enables_jvmci_by_default(jdk):
            # On JDK 9+, +EnableJVMCI forces jdk.internal.vm.ci to be in the root set
            jlink += ['-J-XX:-EnableJVMCI', '-J-XX:-UseJVMCICompiler']

        jlink.append('--add-modules=' + ','.join(_get_image_root_modules(root_module_names, module_names, jdk_modules.keys(), use_upgrade_module_path)))
        jlink_persist.append('--add-modules=jdk.internal.vm.ci')

        module_path = patched_java_base + os.pathsep + jmods_dir
        if modules and not use_upgrade_module_path:
            module_path = os.pathsep.join((m.get_jmod_path(respect_stripping=True) for m in modules)) + os.pathsep + module_path
        jlink.append('--module-path=' + module_path)
        jlink.append('--output=' + dst_jdk_dir)

        if dedup_legal_notices:
            jlink.append('--dedup-legal-notices=error-if-not-same-content')
        jlink.append('--keep-packaged-modules=' + join(dst_jdk_dir, 'jmods'))

        vm_options_path = join(upgrade_dir, 'vm_options')
        vm_options = _get_image_vm_options(jdk, use_upgrade_module_path, modules, synthetic_modules)
        if vm_options:
            jlink.append(f'--add-options={" ".join(vm_options)}')
            jlink_persist.append(f'--add-options="{" ".join(vm_options)}"')

        if jlink_supports_8232080(jdk) and vendor_info is not None:
            for name, value in vendor_info.items():
                jlink.append(f'--{name}={value}')
                jlink_persist.append(f'--{name}="{value}"')

        release_file = join(jdk.home, 'release')
        if isfile(release_file):
            jlink.append(f'--release-info={release_file}')

        if jlink_has_save_jlink_argfiles(jdk):
            jlink_persist_argfile = join(build_dir, 'jlink.persist.options')
            with open(jlink_persist_argfile, 'w') as fp:
                fp.write('\n'.join(jlink_persist))
            jlink.append(f'--save-jlink-argfiles={jlink_persist_argfile}')

        if exists(dst_jdk_dir):
            if use_upgrade_module_path and _vm_options_match(vm_options, vm_options_path):
                mx.logv('[Existing JDK image {} is up to date]'.format(dst_jdk_dir))
                return False
            mx.rmtree(dst_jdk_dir)

        # TODO: investigate the options below used by OpenJDK to see if they should be used:
        # --order-resources: specifies order of resources in generated lib/modules file.
        #       This is apparently not so important if a CDS archive is available.
        # --generate-jli-classes: pre-generates a set of java.lang.invoke classes.
        #       See https://github.com/openjdk/jdk/blob/master/make/GenerateLinkOptData.gmk
        mx.logv(f'[Creating JDK image in {dst_jdk_dir}]')
        mx.run(jlink)

        if use_upgrade_module_path:
            # Move synthetic upgrade modules into final location
            for jmd, jarpath in synthetic_modules.items():
                mx.ensure_dir_exists(dirname(jarpath))
                os.rename(jmd.jarpath, jarpath)
            # Persist VM options cooked into image to be able to skip a subsequent
            # jlink execution if the options do not change.
            with open(vm_options_path, 'w') as fp:
                fp.write(os.linesep.join(vm_options))

        # Create src.zip in new JDK image
        _copy_src_zip(jdk.home, dst_jdk_dir, modules, lambda jmd: not use_upgrade_module_path and with_source(jmd.dist))
    finally:
        if not mx.get_opts().verbose:
            # Preserve build directory so that javac command can be re-executed
            # by cutting and pasting verbose output.
            shutil.rmtree(build_dir)

    if not use_upgrade_module_path:
        # Create CDS archive (https://openjdk.java.net/jeps/341).
        out = mx.OutputCapture()
        mx.logv('[Creating CDS shared archive]')
        if mx.run([mx.exe_suffix(join(dst_jdk_dir, 'bin', 'java')), '-Xshare:dump', '-Xmx128M', '-Xms128M'], out=out, err=out, nonZeroIsFatal=False) != 0:
            if "Shared spaces are not supported in this VM" in out.data:
                # GR-37047: CDS support in darwin-aarch64 jdk11 is missing.
                assert mx.get_os() == 'darwin' and mx.get_arch() == 'aarch64' and jdk.javaCompliance == '11'
            else:
                mx.log(out.data)
                mx.abort('Error generating CDS shared archive')
    else:
        # -Xshare is incompatible with --upgrade-module-path
        pass
    return True


def parse_release_file(release_file_path):
    if not isfile(release_file_path):
        raise mx.abort("Missing expected release file: " + release_file_path)
    release_dict = OrderedDict()
    with open(release_file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            assert line.count('=') > 0, "The release file of '{}' contains a line without the '=' sign: '{}'".format(release_file_path, line)
            k, v = line.strip().split('=', 1)
            if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
                v = v[1:-1]
            release_dict[k] = v
    return release_dict


def format_release_file(release_dict, skip_quoting=None):
    skip_quoting = skip_quoting or set()
    return '\n'.join(('{}={}' if k in skip_quoting else '{}="{}"').format(k, v) for k, v in release_dict.items())


@mx.command(_suite, 'verify-graalvm-configs')
def _verify_graalvm_configs(args):
    parser = ArgumentParser(prog='mx verify-graalvm-configs', description='Verify registered GraalVM configs')
    parser.add_argument('--suites', help='comma-separated list of suites')
    parser.add_argument('--from', dest='start_from', help='start verification from the indicated env file')
    args = parser.parse_args(args)
    suites = args.suites if args.suites is None else args.suites.split(',')
    verify_graalvm_configs(suites=suites, start_from=args.start_from)


def verify_graalvm_configs(suites=None, start_from=None):
    """
    Check the consistency of registered GraalVM configs.
    :param suites: optionally restrict the check to the configs registered by this list of suites.
    :type suites: list[str] or None
    :type start_from: str
    """
    import mx_sdk_vm_impl
    child_env = os.environ.copy()
    for env_var in ['DYNAMIC_IMPORTS', 'DEFAULT_DYNAMIC_IMPORTS', 'COMPONENTS', 'EXCLUDE_COMPONENTS', 'SKIP_LIBRARIES', 'NATIVE_IMAGES', 'FORCE_BASH_LAUNCHERS', 'DISABLE_POLYGLOT', 'DISABLE_LIBPOLYGLOT']:
        if env_var in child_env:
            del child_env[env_var]
    started = start_from is None
    for dist_name, _, components, suite, env_file in _vm_configs:
        if env_file is not False and (suites is None or suite.name in suites):
            _env_file = env_file or dist_name
            started = started or _env_file == start_from

            graalvm_dist_name = '{base_name}_{dist_name}_JAVA{jdk_version}'.format(base_name=mx_sdk_vm_impl._graalvm_base_name, dist_name=dist_name, jdk_version=mx_sdk_vm_impl._src_jdk_version).upper().replace('-', '_')
            mx.log("{}Checking that the env file '{}' in suite '{}' produces a GraalVM distribution named '{}'".format('' if started else '[SKIPPED] ', _env_file, suite.name, graalvm_dist_name))

            if started:
                out = mx.LinesOutputCapture()
                err = mx.LinesOutputCapture()
                retcode = mx.run_mx(['--quiet', '--no-warning', '--env', _env_file, 'graalvm-dist-name'], suite, out=out, err=err, env=child_env, nonZeroIsFatal=False)
                if retcode != 0:
                    mx.abort("Unexpected return code '{}' for 'graalvm-dist-name' for env file '{}' in suite '{}'. Output:\n{}\nError:\n{}".format(retcode, _env_file, suite.name, '\n'.join(out.lines), '\n'.join(err.lines)))
                if len(out.lines) != 1 or out.lines[0] != graalvm_dist_name:
                    out2 = mx.LinesOutputCapture()
                    retcode2 = mx.run_mx(['--no-warning', '--env', _env_file, 'graalvm-components'], suite, out=out2, err=out2, env=child_env, nonZeroIsFatal=False)
                    if retcode2 or len(out2.lines) != 1:
                        got_components = '<error>'
                        diff = ''
                    else:
                        got_components = out2.lines[0]  # example string: "['bpolyglot', 'cmp']"
                        got_components_set = set(got_components[1:-1].replace('\'', '').split(', '))
                        components_set = set(components)
                        added = list(got_components_set - components_set)
                        removed = list(components_set - got_components_set)
                        diff = ('Added:\n{}\n'.format(added) if added else '') + ('Removed:\n{}\n'.format(removed) if removed else '')
                    mx.abort("""\
Unexpected GraalVM dist name for env file '{}' in suite '{}'.
Expected dist name: '{}'
Actual dist name: '{}'.
Expected component list:
{}
Actual component list:
{}
{}Did you forget to update the registration of the GraalVM config?""".format(_env_file, suite.name, graalvm_dist_name, '\n'.join(out.lines + err.lines), sorted(components), got_components, diff))


register_known_vm('truffle')
