#
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

from abc import ABCMeta

import mx
import mx_gate
import mx_subst

from mx_gate import Task
from mx_unittest import unittest

_suite = mx.suite('sdk')

graalvm_hostvm_configs = [
    ('jvm', [], ['--jvm'], 50),
    ('native', [], ['--native'], 100)
]


def _sdk_gate_runner(args, tasks):
    with Task('SDK UnitTests', tasks, tags=['test']) as t:
        if t: unittest(['--suite', 'sdk', '--enable-timing', '--verbose', '--fail-fast'])
    with Task('Check Copyrights', tasks) as t:
        if t: mx.checkcopyrights(['--primary'])

mx_gate.add_gate_runner(_suite, _sdk_gate_runner)


def javadoc(args):
    """build the Javadoc for all API packages"""
    mx.javadoc(['--unified', '--exclude-packages', 'org.graalvm.polyglot.tck'] + args)


def add_graalvm_hostvm_config(name, java_args=None, launcher_args=None, priority=0):
    """
    :type name: str
    :type java_args: list[str] | None
    :type launcher_args: list[str] | None
    :type priority: int
    """
    graalvm_hostvm_configs.append((name, java_args, launcher_args, priority))


class AbstractNativeImageConfig(object):
    __metaclass__ = ABCMeta

    def __init__(self, destination, jar_distributions, build_args, links=None):
        """
        :type destination: str
        :type jar_distributions: list[str]
        :type build_args: list[str]
        :type links: list[str]
        """
        self.destination = mx_subst.path_substitutions.substitute(destination)
        self.jar_distributions = jar_distributions
        self.build_args = build_args
        self.links = [mx_subst.path_substitutions.substitute(link) for link in links] if links else []

        assert isinstance(self.jar_distributions, list)
        assert isinstance(self.build_args, list)


class LauncherConfig(AbstractNativeImageConfig):
    def __init__(self, destination, jar_distributions, main_class, build_args, links=None, is_main_launcher=True):
        """
        :type main_class: str
        """
        super(LauncherConfig, self).__init__(destination, jar_distributions, build_args, links=links)
        self.main_class = main_class
        self.is_main_launcher = is_main_launcher


class LanguageLauncherConfig(LauncherConfig):
    pass


class LibraryConfig(AbstractNativeImageConfig):
    pass


class GraalVmComponent(object):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files,
                 jar_distributions=None, builder_jar_distributions=None, support_distributions=None,
                 dir_name=None, launcher_configs=None, provided_executables=None,
                 polyglot_lib_build_args=None, polyglot_lib_jar_dependencies=None, polyglot_lib_build_dependencies=None,
                 has_polyglot_lib_entrypoints=False,
                 boot_jars=None, priority=None):
        """
        :param suite mx.Suite: the suite this component belongs to
        :type name: str
        :param str short_name: a short, unique name for this component
        :param str | None dir_name: the directory name in which this component lives. If `None`, the `short_name` is used.
        :type license_files: list[str]
        :type third_party_license_files: list[str]
        :type provided_executables: list[str]
        :type polyglot_lib_build_args: list[str]
        :type polyglot_lib_jar_dependencies: list[str]
        :type polyglot_lib_build_dependencies: list[str]
        :type has_polyglot_lib_entrypoints: bool
        :type boot_jars: list[str]
        :type launcher_configs: list[LauncherConfig]
        :type jar_distributions: list[str]
        :type builder_jar_distributions: list[str]
        :type support_distributions: list[str]
        :type priority: int
        """
        self.suite = suite
        self.name = name
        self.short_name = short_name
        self.dir_name = dir_name or short_name
        self.license_files = license_files
        self.third_party_license_files = third_party_license_files
        self.provided_executables = provided_executables or []
        self.polyglot_lib_build_args = polyglot_lib_build_args or []
        self.polyglot_lib_jar_dependencies = polyglot_lib_jar_dependencies or []
        self.polyglot_lib_build_dependencies = polyglot_lib_build_dependencies or []
        self.has_polyglot_lib_entrypoints = has_polyglot_lib_entrypoints
        self.boot_jars = boot_jars or []
        self.jar_distributions = jar_distributions or []
        self.builder_jar_distributions = builder_jar_distributions or []
        self.support_distributions = support_distributions or []
        self.priority = priority or 0
        """ priority with a higher value means higher priority """
        self.launcher_configs = launcher_configs or []

        assert isinstance(self.jar_distributions, list)
        assert isinstance(self.builder_jar_distributions, list)
        assert isinstance(self.support_distributions, list)
        assert isinstance(self.license_files, list)
        assert isinstance(self.third_party_license_files, list)
        assert isinstance(self.provided_executables, list)
        assert isinstance(self.polyglot_lib_build_args, list)
        assert isinstance(self.polyglot_lib_jar_dependencies, list)
        assert isinstance(self.polyglot_lib_build_dependencies, list)
        assert isinstance(self.boot_jars, list)
        assert isinstance(self.launcher_configs, list)

    def __str__(self):
        return "{} ({})".format(self.name, self.dir_name)


class GraalVmTruffleComponent(GraalVmComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, truffle_jars,
                 builder_jar_distributions=None, support_distributions=None, dir_name=None, launcher_configs=None,
                 provided_executables=None, polyglot_lib_build_args=None, polyglot_lib_jar_dependencies=None,
                 polyglot_lib_build_dependencies=None, has_polyglot_lib_entrypoints=False, boot_jars=None,
                 include_in_polyglot=True, priority=None, post_install_msg=None):
        """
        :param truffle_jars list[str]: JAR distributions that should be on the classpath for the language implementation.
        :param bool include_in_polyglot: whether this component is included in `--language:all` or `--tool:all` and should be part of polyglot images.
        :param post_install_msg: Post-installation message to be printed
        :type post_install_msg: str
        """
        super(GraalVmTruffleComponent, self).__init__(suite, name, short_name, license_files,
                                                      third_party_license_files, truffle_jars, builder_jar_distributions, support_distributions, dir_name, launcher_configs, provided_executables,
                                                      polyglot_lib_build_args, polyglot_lib_jar_dependencies, polyglot_lib_build_dependencies,
                                                      has_polyglot_lib_entrypoints, boot_jars,
                                                      priority)
        self.include_in_polyglot = include_in_polyglot
        self.post_install_msg = post_install_msg
        assert isinstance(self.include_in_polyglot, bool)


class GraalVmLanguage(GraalVmTruffleComponent):
    pass


class GraalVmTool(GraalVmTruffleComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, truffle_jars,
                 builder_jar_distributions=None, support_distributions=None, dir_name=None, launcher_configs=None,
                 provided_executables=None, polyglot_lib_build_args=None, polyglot_lib_jar_dependencies=None,
                 polyglot_lib_build_dependencies=None, has_polyglot_lib_entrypoints=False, boot_jars=None,
                 include_in_polyglot=True, include_by_default=False, priority=None):
        super(GraalVmTool, self).__init__(suite, name, short_name, license_files, third_party_license_files,
                                          truffle_jars,
                                          builder_jar_distributions,
                                          support_distributions, dir_name,
                                          launcher_configs, provided_executables,
                                          polyglot_lib_build_args,
                                          polyglot_lib_jar_dependencies,
                                          polyglot_lib_build_dependencies,
                                          has_polyglot_lib_entrypoints,
                                          boot_jars,
                                          include_in_polyglot,
                                          priority)
        self.include_by_default = include_by_default


class GraalVmJdkComponent(GraalVmComponent):
    pass


class GraalVmJreComponent(GraalVmComponent):
    pass


class GraalVmJvmciComponent(GraalVmJreComponent):
    def __init__(self, suite, name, short_name, license_files, third_party_license_files, jvmci_jars, jar_distributions=None, builder_jar_distributions=None, support_distributions=None,
                 graal_compiler=None, dir_name=None, launcher_configs=None, provided_executables=None, polyglot_lib_build_args=None, polyglot_lib_jar_dependencies=None,
                 polyglot_lib_build_dependencies=None, has_polyglot_lib_entrypoints=False, boot_jars=None, priority=None):
        """
        :type jvmci_jars: list[str]
        :type graal_compiler: str
        """
        super(GraalVmJvmciComponent, self).__init__(suite, name, short_name, license_files,
                                                    third_party_license_files,
                                                    jar_distributions,
                                                    builder_jar_distributions,
                                                    support_distributions,
                                                    dir_name,
                                                    launcher_configs,
                                                    provided_executables,
                                                    polyglot_lib_build_args,
                                                    polyglot_lib_jar_dependencies,
                                                    polyglot_lib_build_dependencies,
                                                    has_polyglot_lib_entrypoints,
                                                    boot_jars,
                                                    priority)

        self.graal_compiler = graal_compiler
        self.jvmci_jars = jvmci_jars or []

        assert isinstance(self.jvmci_jars, list)


_graalvm_components = dict()


def register_graalvm_component(component):
    """
    :type component: GraalVmComponent
    :type suite: mx.Suite
    """
    def _log_ignored_component(kept, ignored):
        """
        :type kept: GraalVmComponent
        :type ignored: GraalVmComponent
        """
        mx.logv('Suites \'{}\' and \'{}\' are registering a component with the same short name (\'{}\'), with priority \'{}\' and \'{}\' respectively.'.format(kept.suite.name, ignored.suite.name, kept.short_name, kept.priority, ignored.priority))
        mx.logv('Ignoring the one from suite \'{}\'.'.format(ignored.suite.name))

    _prev = _graalvm_components.get(component.short_name, None)
    if _prev:
        if _prev.priority == component.priority:
            mx.abort('Suites \'{}\' and \'{}\' are registering a component with the same short name (\'{}\') and priority (\'{}\')'.format(_prev.suite.name, component.suite.name, _prev.short_name, _prev.priority))
        elif _prev.priority < component.priority:
            _graalvm_components[component.short_name] = component
            _log_ignored_component(component, _prev)
        else:
            _log_ignored_component(_prev, component)
    else:
        _graalvm_components[component.short_name] = component


def graalvm_components(opt_limit_to_suite=False):
    """
    :rtype: list[GraalVmComponent]
    """
    if opt_limit_to_suite and mx.get_opts().specific_suites:
        return [c for c in _graalvm_components.values() if c.suite.name in mx.get_opts().specific_suites]
    else:
        return _graalvm_components.values()


mx.update_commands(_suite, {
    'javadoc': [javadoc, '[SL args|@VM options]'],
})
