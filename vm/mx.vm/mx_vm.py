#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import fcntl
import os
import pprint
import json

from abc import ABCMeta
from argparse import ArgumentParser
from contextlib import contextmanager
from os.path import relpath, join, dirname, basename, exists, isfile
from collections import OrderedDict
from zipfile import ZipFile
from tarfile import TarFile
from copy import deepcopy

import mx
import mx_gate
import mx_sdk
import mx_subst
import mx_vm_gate

_suite = mx.suite('vm')
""":type: mx.SourceSuite | mx.Suite"""

_vm_configs = {}

mx_sdk.register_graalvm_component(mx_sdk.GraalVmJreComponent(
    suite=_suite,
    name='Component installer',
    short_name='gu',
    dir_name='installer',
    license_files=[],
    third_party_license_files=[],
    support_distributions=['vm:INSTALLER_GRAALVM_SUPPORT'],
    provided_executables=['bin/gu'],
))

mx_sdk.register_graalvm_component(mx_sdk.GraalVmComponent(
    suite=_suite,
    name='GraalVM license files',
    short_name='gvm',
    dir_name='.',
    license_files=['LICENSE'],
    third_party_license_files=['3rd_party_licenses.txt'],
    support_distributions=['vm:VM_GRAALVM_SUPPORT']
))


class BaseGraalVmLayoutDistribution(mx.LayoutDistribution):
    __metaclass__ = ABCMeta

    def __init__(self, suite, name, deps, components, is_graalvm, exclLibs, platformDependent, theLicense, testDistribution,
                 add_jdk_base=False,
                 base_dir=None,
                 layout=None,
                 path=None,
                 with_polyglot_launcher=False,
                 with_lib_polyglot=False,
                 stage1=False,
                 **kw_args):
        self.components = components
        base_dir = base_dir or '.'
        _src_jdk_base, _jdk_dir = _get_jdk_dir()
        _src_jdk_base = _src_jdk_base if add_jdk_base else '.'
        if base_dir != '.':
            self.jdk_base = '/'.join([base_dir, _src_jdk_base]) if _src_jdk_base and _src_jdk_base != '.' else base_dir
        else:
            self.jdk_base = _src_jdk_base

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('jdk_base', lambda: self.jdk_base)

        string_substitutions = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        string_substitutions.register_no_arg('version', _suite.release_version)

        _layout_provenance = {}

        def _add(_layout, dest, src, component=None, with_sources=False):
            """
            :type _layout: dict[str, list[str] | str]
            :type dest: str
            :type src: list[str | dict] | str | dict
            """
            assert dest.startswith('<jdk_base>') or base_dir == '.' or dest.startswith(base_dir), dest
            src = src if isinstance(src, list) else [src]
            if not src:
                return

            if not dest.endswith('/') and dest in _layout:
                if dest not in _layout_provenance or _layout_provenance[dest] is None:
                    mx.abort(
                        "Can not override '{}' which is part of the base GraalVM layout. ({} tried to set {}<-{})".format(
                            dest, component.name if component else None, dest, src))
                previous_component = _layout_provenance[dest]
                if not component:
                    mx.abort(
                        "Suspicious override in GraalVM layout: tried to set {}<-{} without a component while it already existed ({} set it to {})".format(
                            dest, src, previous_component.name, _layout[dest]))
                if component.priority <= previous_component.priority:
                    mx.logv("'Skipping '{}<-{}' from {c}' ({c}.priority={cp} <= {pc}.priority={pcp})".format(dest, src, c=component.name, pc=previous_component.name, cp=component.priority, pcp=previous_component.priority))
                    return
                else:
                    _layout[dest] = []

            mx.logvv("'Adding '{}: {}' to the layout'".format(dest, src))
            _layout_provenance[dest] = component
            if with_sources and _include_sources():
                for _src in list(src):
                    src_dict = mx.LayoutDistribution._as_source_dict(_src, name, dest)
                    if src_dict['source_type'] == 'dependency' and src_dict['path'] is None:
                        src_src_dict = {
                            'source_type': 'dependency',
                            'dependency': src_dict['dependency'],
                            'path': '*.src.zip',
                            'optional': True,
                            'if_stripped': 'exclude',
                        }
                        src.append(src_src_dict)
            _layout.setdefault(dest, []).extend(src)

        if is_graalvm:
            # Add base JDK
            exclude_base = _jdk_dir
            if _src_jdk_base != '.':
                exclude_base = join(exclude_base, _src_jdk_base)
            if mx.get_os() == 'darwin':
                hsdis = '/jre/lib/' + mx.add_lib_suffix('hsdis-' + mx.get_arch())
            else:
                hsdis = '/jre/lib/' + mx.get_arch() + '/' + mx.add_lib_suffix('hsdis-' + mx.get_arch())
            _add(layout, base_dir, {
                'source_type': 'file',
                'path': _jdk_dir,
                'exclude': [
                    exclude_base + '/COPYRIGHT',
                    exclude_base + '/LICENSE',
                    exclude_base + '/release',
                    exclude_base + '/bin/jvisualvm',
                    exclude_base + '/lib/visualvm',
                    exclude_base + hsdis,
                ]
            })

            # Add vm.properties
            # Add TRUFFLE_NFI_NATIVE (TODO: should be part of an other component?)
            if mx.get_os() == 'darwin':
                # on macOS the <arch> directory is not used
                _add(layout, "<jdk_base>/jre/lib/", "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/bin/<lib:trufflenfi>")
                _add(layout, "<jdk_base>/jre/lib/server/vm.properties", "string:name=GraalVM <version>")
            else:
                _add(layout, "<jdk_base>/jre/lib/<arch>/", "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/bin/<lib:trufflenfi>")
                _add(layout, "<jdk_base>/jre/lib/<arch>/server/vm.properties", "string:name=GraalVM <version>")

            # Add Polyglot launcher
            if with_polyglot_launcher:
                polyglot_launcher_project = get_polyglot_launcher_project()
                _add(layout, "<jdk_base>/jre/bin/polyglot", "dependency:" + polyglot_launcher_project.name)
                _add(layout, "<jdk_base>/bin/polyglot", "link:../jre/bin/polyglot")

            # Add libpolyglot library
            if with_lib_polyglot:
                lib_polyglot_project = get_lib_polyglot_project()
                # Note that `jre/lib/polyglot` is synchronized with `org.graalvm.polyglot.install_name_id` in `get_lib_polyglot_project`
                _add(layout, "<jdk_base>/jre/lib/polyglot/" + lib_polyglot_project.native_image_name, "dependency:" + lib_polyglot_project.name)
                _add(layout, "<jdk_base>/jre/lib/polyglot/", "dependency:" + lib_polyglot_project.name + "/*.h")

            # Add release file
            _sorted_suites = sorted(mx.suites(), key=lambda s: s.name)
            _metadata = self._get_metadata(_sorted_suites)
            _add(layout, "<jdk_base>/release", "string:{}".format(_metadata))

        # Add the rest of the GraalVM

        has_graal_compiler = False
        for _component in self.components:
            mx.logv('Adding {} to the {} {}'.format(_component.name, name, self.__class__.__name__))
            if isinstance(_component, mx_sdk.GraalVmLanguage):
                _component_type_base = '<jdk_base>/jre/languages/'
            elif isinstance(_component, mx_sdk.GraalVmTool):
                _component_type_base = '<jdk_base>/jre/tools/'
            elif isinstance(_component, mx_sdk.GraalVmJdkComponent):
                _component_type_base = '<jdk_base>/lib/'
            elif isinstance(_component, mx_sdk.GraalVmJreComponent):
                _component_type_base = '<jdk_base>/jre/lib/'
            elif isinstance(_component, mx_sdk.GraalVmComponent):
                _component_type_base = '<jdk_base>/'
            else:
                raise mx.abort("Unknown component type for {}: {}".format(_component.name, type(_component).__name__))
            if _component.dir_name:
                _component_base = _component_type_base + _component.dir_name + '/'
            else:
                _component_base = _component_type_base

            _add(layout, '<jdk_base>/jre/lib/boot/', ['dependency:' + d for d in _component.boot_jars], _component, with_sources=True)
            _add(layout, _component_base, ['dependency:' + d for d in _component.jar_distributions], _component, with_sources=True)
            _add(layout, _component_base + 'builder/', ['dependency:' + d for d in _component.builder_jar_distributions], _component, with_sources=True)
            _add(layout, _component_base, ['extracted-dependency:' + d for d in _component.support_distributions], _component)
            if isinstance(_component, mx_sdk.GraalVmJvmciComponent):
                _add(layout, '<jdk_base>/jre/lib/jvmci/', ['dependency:' + d for d in _component.jvmci_jars], _component, with_sources=True)

            if isinstance(_component, mx_sdk.GraalVmJdkComponent):
                _jdk_jre_bin = '<jdk_base>/bin/'
            else:
                _jdk_jre_bin = '<jdk_base>/jre/bin/'

            def _add_link(_dest, _target):
                assert _dest.endswith('/')
                _linkname = relpath(_target, start=_dest[:-1])
                if _linkname != basename(_target):
                    _add(layout, _dest, 'link:{}'.format(_linkname), _component)

            for _license in _component.license_files + _component.third_party_license_files:
                _add_link('<jdk_base>/', _component_base + _license)

            _jre_bin_names = []

            for _launcher_config in _get_launcher_configs(_component):
                _add(layout, '<jdk_base>/jre/lib/graalvm/', ['dependency:' + d for d in _launcher_config.jar_distributions], _component, with_sources=True)
                _launcher_dest = _component_base + _launcher_config.destination
                # add `LauncherConfig.destination` to the layout
                _add(layout, _launcher_dest, 'dependency:' + GraalVmLauncher.launcher_project_name(_launcher_config, stage1), _component)
                # add links from jre/bin to launcher
                _add_link(_jdk_jre_bin, _launcher_dest)
                _jre_bin_names.append(basename(_launcher_dest))
                for _component_link in _launcher_config.links:
                    _link_dest = _component_base + _component_link
                    # add links `LauncherConfig.links` -> `LauncherConfig.destination`
                    _add(layout, _link_dest, 'link:{}'.format(relpath(_launcher_dest, start=dirname(_link_dest))), _component)
                    # add links from jre/bin to component link
                    _add_link(_jdk_jre_bin, _link_dest)
                    _jre_bin_names.append(basename(_link_dest))

            for _provided_executable in _component.provided_executables:
                if _component.short_name is 'vvm':
                    _add(layout, _jdk_jre_bin, 'extracted-dependency:tools:VISUALVM_PLATFORM_SPECIFIC/./' + _provided_executable)
                else:
                    _link_dest = _component_base + _provided_executable
                    _add_link(_jdk_jre_bin, _link_dest)
                    _jre_bin_names.append(basename(_link_dest))

            if 'jre' in _jdk_jre_bin:
                # Add jdk to jre links
                for _name in _jre_bin_names:
                    _add_link('<jdk_base>/bin/', '<jdk_base>/jre/bin/' + _name)

            if isinstance(_component, mx_sdk.GraalVmJvmciComponent) and _component.graal_compiler:
                has_graal_compiler = True

            if isinstance(_component, mx_sdk.GraalVmLanguage) and not is_graalvm:
                # add language-specific release file
                _metadata = self._get_metadata([_component.suite])
                _add(layout, _component_base + 'release', "string:{}".format(_metadata))

        if has_graal_compiler:
            _add(layout, '<jdk_base>/jre/lib/jvmci/compiler-name', 'string:graal')

        super(BaseGraalVmLayoutDistribution, self).__init__(suite, name, deps, layout, path, platformDependent,
                                                            theLicense, exclLibs, path_substitutions=path_substitutions,
                                                            string_substitutions=string_substitutions,
                                                            testDistribution=testDistribution, **kw_args)
        self.reset_user_group = True
        mx.logv("'{}' has layout:\n{}".format(self.name, pprint.pformat(self.layout)))

    @staticmethod
    def _get_metadata(suites):
        """
        :type suites: list[mx.Suite]
        :return:
        """
        _commit_info = {}
        for _s in suites:
            if _s.vc:
                _info = _s.vc.parent_info(_s.vc_dir)
                _commit_info[_s.name] = {
                    "commit.rev": _s.vc.parent(_s.vc_dir),
                    "commit.committer": _info['committer'] if _s.vc.kind != 'binary' else 'unknown',
                    "commit.committer-ts": _info['committer-ts'],
                }
        _metadata = """\
OS_NAME={os}
OS_ARCH={arch}
SOURCE="{source}"
COMMIT_INFO={commit_info}
GRAALVM_VERSION={version}""".format(
            os=get_graalvm_os(),
            arch=mx.get_arch(),
            source=' '.join(['{}:{}'.format(_s.name, _s.version()) for _s in suites]),
            commit_info=json.dumps(_commit_info, sort_keys=True),
            version=_suite.release_version()
        )
        return _metadata


class GraalVmLayoutDistribution(BaseGraalVmLayoutDistribution, mx.LayoutTARDistribution):  # pylint: disable=R0901
    def __init__(self, base_name, base_layout, theLicense=None, stage1=False, **kw_args):
        components = mx_sdk.graalvm_components()
        components_set = set([c.short_name for c in components])

        if not stage1 and _with_polyglot_lib_project() and _get_svm_support().is_supported():
            components_set.add('libpoly')
            with_lib_polyglot = True
        else:
            with_lib_polyglot = False
        if not stage1 and _with_polyglot_launcher_project():
            with_polyglot_launcher = True
            components_set.add('poly')
            if _force_bash_launchers(get_polyglot_launcher_project().native_image_config):
                components_set.add('bpolyglot')
        else:
            with_polyglot_launcher = False
        if stage1:
            components_set.add('stage1')
        else:
            for component in components:
                for launcher_config in _get_launcher_configs(component):
                    if _force_bash_launchers(launcher_config):
                        components_set.add('b' + basename(launcher_config.destination))

        # Use custom distribution name and base dir for registered vm configurations
        vm_config_name = None
        vm_config_additional_components = sorted(components_set)
        for config_name, config_components in _vm_configs.items():
            config_components_set = set(config_components)
            config_additional_components = sorted(components_set - config_components_set)
            if config_components_set <= components_set and len(config_additional_components) <= len(vm_config_additional_components):
                vm_config_name = config_name
                vm_config_additional_components = config_additional_components

        name = (base_name + (('_' + vm_config_name) if vm_config_name else '') + ('_' if vm_config_additional_components else '') + '_'.join(vm_config_additional_components)).upper()
        base_dir = name.lower().replace('_', '-') + '-{}'.format(_suite.release_version())

        layout = deepcopy(base_layout)
        super(GraalVmLayoutDistribution, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            components=components,
            is_graalvm=True,
            exclLibs=[],
            platformDependent=True,
            theLicense=theLicense,
            testDistribution=False,
            add_jdk_base=True,
            base_dir=base_dir,
            layout=layout,
            path=None,
            with_polyglot_launcher=with_polyglot_launcher,
            with_lib_polyglot=with_lib_polyglot,
            stage1=stage1,
            **kw_args)

    def getBuildTask(self, args):
        return GraalVmLayoutDistributionTask(args, self, join(_suite.dir, 'latest_graalvm'))


class GraalVmLayoutDistributionTask(mx.LayoutArchiveTask):
    def __init__(self, args, dist, link_path):
        self._link_path = link_path
        super(GraalVmLayoutDistributionTask, self).__init__(args, dist)

    def _add_link(self):
        self._rm_link()
        os.symlink(self._link_target(), self._link_path)

    def _link_target(self):
        return relpath(self.subject.output, _suite.dir)

    def _rm_link(self):
        if os.path.lexists(self._link_path):
            os.unlink(self._link_path)

    def needsBuild(self, newestInput):
        sup = super(GraalVmLayoutDistributionTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        if not os.path.lexists(self._link_path):
            return True, '{} does not exist'.format(self._link_path)
        link_file = mx.TimeStampFile(self._link_path, False)
        if link_file.isOlderThan(self.subject.output):
            return True, '{} is older than {}'.format(link_file, newestInput)
        if self.subject == get_final_graalvm_distribution():
            if self._link_target() != os.readlink(self._link_path):
                return True, '{} is pointing to the wrong directory'.format(link_file)
        return False, None

    def build(self):
        super(GraalVmLayoutDistributionTask, self).build()
        if self.subject == get_final_graalvm_distribution():
            self._add_link()

    def clean(self, forBuild=False):
        super(GraalVmLayoutDistributionTask, self).clean(forBuild)
        if self.subject == get_final_graalvm_distribution():
            self._rm_link()


def _get_jdk():
    return mx.get_jdk(tag='default')


def _get_jdk_dir():
    java_home = _get_jdk().home
    jdk_dir = java_home
    if jdk_dir.endswith(os.path.sep):
        jdk_dir = jdk_dir[:-len(os.path.sep)]
    if jdk_dir.endswith('/Contents/Home'):
        jdk_base = 'Contents/Home'
        jdk_dir = jdk_dir[:-len('/Contents/Home')]
    else:
        jdk_base = '.'
    return jdk_base, jdk_dir


def get_graalvm_os():
    os = mx.get_os()
    if os == 'darwin':
        return 'macos'
    return os


class SvmSupport(object):
    def __init__(self, svm_suite):
        """
        :type svm_suite: mx.Suite
        """
        if svm_suite:
            self._svm_supported = True
        else:
            self._svm_supported = False

    def is_supported(self):
        return self._svm_supported

    def native_image(self, build_args, output_file, allow_server=False, nonZeroIsFatal=True, out=None, err=None):
        assert self._svm_supported
        stage1 = get_stage1_graalvm_distribution()
        native_image_project_name = GraalVmLauncher.launcher_project_name(mx_sdk.LauncherConfig('native-image', [], "", []), stage1=True)
        native_image_bin = join(stage1.output, stage1.find_single_source_location('dependency:' + native_image_project_name))
        native_image_command = [native_image_bin, '-H:+EnforceMaxRuntimeCompileMethods'] + build_args
        # currently, when building with the bash version of native-image, --no-server is implied (and can not be passed)
        output_directory = dirname(output_file)
        if "-H:Kind=SHARED_LIBRARY" in build_args:
            suffix = mx.add_lib_suffix("")
        else:
            suffix = mx.exe_suffix("")
        name = basename(output_file)
        if suffix:
            name = name[:-len(suffix)]
        native_image_command += [
            '-H:Path=' + output_directory or ".",
            '-H:Name=' + name,
        ]
        return mx.run(native_image_command, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)

    _debug_supported = None

    def is_debug_supported(self):
        if SvmSupport._debug_supported is None:
            out = mx.OutputCapture()
            err = mx.OutputCapture()
            self.native_image(['-g', "Dummy"], "dummy", out=out, err=err, nonZeroIsFatal=False)
            if "Could not find option" in err.data:
                SvmSupport._debug_supported = False
            elif "Error: Unrecognized option: -g" in err.data:
                SvmSupport._debug_supported = False
            elif "Main entry point class 'Dummy' not found" in err.data:
                SvmSupport._debug_supported = True
            else:
                mx.abort("Could not figure out if 'native-image' supports '-g':\nout:\n{}\nerr:\n{}".format(out.data,
                                                                                                            err.data))
        return SvmSupport._debug_supported


def _get_svm_support():
    return SvmSupport(mx.suite('substratevm', fatalIfMissing=False))


class GraalVmNativeProperties(mx.Project):
    def __init__(self, components, **kw_args):
        """
        :param list[mx_sdk.GraalVmTruffleComponent] components: The components declaring this native-image.properties file
        """
        deps = []
        for component in components:
            deps += component.support_distributions
        self.components = components
        super(GraalVmNativeProperties, self).__init__(_suite, GraalVmNativeProperties.project_name(components[0].dir_name), subDir=None, srcDirs=[], deps=deps, workingSets=None, d=_suite.dir, theLicense=None, **kw_args)

    @staticmethod
    def project_name(dir_name):
        return dir_name + "_native-image.properties"

    def getArchivableResults(self, use_relpath=True, single=False):
        out = self.output_file()
        yield out, basename(out)

    def output_file(self):
        return join(self.get_output_base(), "native-image.properties")

    def getBuildTask(self, args):
        return NativePropertiesBuildTask(self, args)


_properties_escapes = {
    't': '\t',
    'n': '\n',
    'f': '\f',
    'r': '\r',
}
_properties_whitespaces = ' \t\f'


def read_properties(f):
    """
    :type f: file
    """
    result = OrderedDict()
    pending = ""
    for line in f.readlines():
        line = line.rstrip('\r\n').lstrip(_properties_whitespaces)
        if line.startswith('#') or line.startswith('!'):
            continue
        end_escapes = 0
        while len(line) > end_escapes and line[-end_escapes-1] == '\\':
            end_escapes += 1
        if end_escapes % 2 == 1:
            pending += line[:-1]
            continue
        line = pending + line
        pending = ""
        line_index = 0
        line_len = len(line)

        def _read(start, until=None):
            _chars = []
            _line_index = start
            while _line_index < line_len:
                _char = line[_line_index]
                _line_index += 1
                if _char == '\\':
                    _escaped = line[_line_index]  # there is an even number of `\` so this always works
                    _line_index += 1
                    if _escaped in _properties_escapes:
                        _chars.append(_properties_escapes[_escaped])
                    elif _escaped == 'u':
                        mx.abort("Unsupported unicode escape found in {}".format(f))
                    else:
                        _chars.append(_escaped)
                elif until and _char in until:
                    break
                else:
                    _chars.append(_char)
            return "".join(_chars), _line_index

        key, line_index = _read(line_index, _properties_whitespaces + ':=')

        def _skip_whitespaces(start):
            _line_index = start
            while _line_index < len(line) and line[_line_index] in _properties_whitespaces:
                _line_index += 1
            return _line_index

        line_index = _skip_whitespaces(line_index)
        if line_index < line_len and line[line_index] in ':=':
            line_index += 1
            line_index = _skip_whitespaces(line_index)
        value, line_index = _read(line_index)
        result[key] = value
    return result


class NativePropertiesBuildTask(mx.ProjectBuildTask):
    def __init__(self, subject, args):
        """
        :type subject: GraalVmNativeProperties
        """
        super(NativePropertiesBuildTask, self).__init__(args, 1, subject)

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_file())

    def __str__(self):
        return "Checking " + basename(self.subject.output_file())

    def build(self):
        file_name = basename(self.subject.output_file())
        provided_properties = dict()
        components = self.subject.components
        dir_name = components[0].dir_name
        for dep in self.subject.deps:
            ext = mx.get_file_extension(dep.path)
            if ext.endswith('zip') or ext.endswith('jar'):
                arc = ZipFile(dep.path)
                if file_name in arc.namelist():
                    f = arc.open(file_name)
                else:
                    continue
            elif 'tar' in ext or ext.endswith('tgz'):
                arc = TarFile.open(dep.path)
                try:
                    f = arc.extractfile(file_name)
                except KeyError:
                    continue
            else:
                raise mx.abort("Unsupported archive format for support distribution: {}, extension={}".format(dep.path, ext))
            if provided_properties:
                mx.abort("More than one support distribution provides a 'native-image.properties' file for dir_name {}".format(dir_name))
            provided_properties = read_properties(f)

        if provided_properties:
            """:type: list[mx_sdk.GraalVmTruffleComponent] """
            launcher_configs = [launcher_config for component in components for launcher_config in component.launcher_configs if launcher_config.is_main_launcher]
            if len(launcher_configs) > 1:
                mx.abort("More than one 'main' launcher config found for dir_name {}: can not create a 'native-image.properties' files".format(dir_name))
            if launcher_configs:
                launcher_config = launcher_configs[0]
                if any((' ' in arg for arg in launcher_config.build_args)):
                    mx.abort("Unsupported space in launcher build argument: {} in main launcher for {}".format(launcher_config.build_args, dir_name))
                properties = {
                    'ImageName': basename(launcher_config.destination),
                    'LauncherClass': basename(launcher_config.main_class),
                    'LauncherClassPath': graalvm_home_relative_classpath(launcher_config.jar_distributions, _get_graalvm_archive_path('jre')),
                    'Args': ' '.join(launcher_config.build_args),
                }
                for p in ('ImageName', 'LauncherClass'):
                    if provided_properties[p] != properties[p]:
                        mx.abort("Inconsistent property '{}':\n - native-image.properties: {}\n - LauncherConfig: {}".format(p, provided_properties[p], properties[p]))
                if set(provided_properties['LauncherClassPath'].split(os.pathsep)) != set(properties['LauncherClassPath'].split(os.pathsep)):
                    mx.abort("Inconsistent property 'LauncherClassPath':\n - native-image.properties: {}\n - LauncherConfig: {}".format(provided_properties['LauncherClassPath'], properties['LauncherClassPath']))

    def clean(self, forBuild=False):
        if exists(self.subject.output_file()):
            os.unlink(self.subject.output_file())


class GraalVmNativeImage(mx.Project):
    __metaclass__ = ABCMeta

    def __init__(self, suite, name, deps, workingSets, native_image_config, theLicense=None, **kw_args):
        """
        :type native_image_config: mx_sdk.AbstractNativeImageConfig
        """
        assert isinstance(native_image_config, mx_sdk.AbstractNativeImageConfig), type(native_image_config).__name__
        self.native_image_config = native_image_config
        self.native_image_name = basename(native_image_config.destination)
        self.native_image_jar_distributions = list(native_image_config.jar_distributions)
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            deps += self.native_image_jar_distributions
        super(GraalVmNativeImage, self).__init__(suite=suite, name=name, subDir=None, srcDirs=[], deps=deps,
                                                 workingSets=workingSets, d=_suite.dir, theLicense=theLicense,
                                                 **kw_args)
        if svm_support.is_supported() and self.is_native():
            if not hasattr(self, 'buildDependencies'):
                self.buildDependencies = []
            stage1 = get_stage1_graalvm_distribution()
            self.buildDependencies += ['{}:{}'.format(stage1.suite, stage1.name)]

    def getArchivableResults(self, use_relpath=True, single=False):
        yield self.output_file(), self.native_image_name

    def output_file(self):
        return join(self.get_output_base(), self.name, self.native_image_name)

    def build_args(self):
        return [mx_subst.string_substitutions.substitute(arg) for arg in self.native_image_config.build_args]

    def isPlatformDependent(self):
        return True

    @staticmethod
    def project_name(native_image_config):
        return basename(native_image_config.destination) + ".image"

    def is_native(self):
        return True


class GraalVmLauncher(GraalVmNativeImage):
    __metaclass__ = ABCMeta

    def __init__(self, suite, name, deps, workingSets, native_image_config, theLicense=None, stage1=False, **kw_args):
        """
        :type native_image_config: mx_sdk.LauncherConfig
        """
        assert isinstance(native_image_config, mx_sdk.LauncherConfig), type(native_image_config).__name__
        self.stage1 = stage1
        super(GraalVmLauncher, self).__init__(suite, name, deps, workingSets, native_image_config, theLicense=theLicense, **kw_args)

    def getBuildTask(self, args):
        if self.is_native():
            return GraalVmSVMLauncherBuildTask(self, args, _get_svm_support())
        else:
            return GraalVmBashLauncherBuildTask(self, args)

    def is_native(self):
        return _get_svm_support().is_supported() and not _force_bash_launchers(self.native_image_config, self.stage1 or None)

    def output_file(self):
        return join(self.get_output_base(), self.name, self.native_image_name)

    def get_containing_graalvm(self):
        if self.stage1:
            return get_stage1_graalvm_distribution()
        else:
            return get_final_graalvm_distribution()

    @staticmethod
    def launcher_project_name(native_image_config, stage1=False):
        is_bash = not _get_svm_support().is_supported() or _force_bash_launchers(native_image_config, stage1 or None)
        return GraalVmNativeImage.project_name(native_image_config) + ("-bash" if is_bash else "") + ("-stage1" if stage1 else "")


class GraalVmPolyglotLauncher(GraalVmLauncher):
    def __init__(self, suite, deps, workingSets, launcherConfig, **kw_args):
        for component in mx_sdk.graalvm_components():
            if isinstance(component, mx_sdk.GraalVmLanguage) and component.include_in_polyglot:
                for language_launcher_config in _get_launcher_configs(component):
                    if isinstance(language_launcher_config, mx_sdk.LanguageLauncherConfig):
                        launcherConfig['jar_distributions'] += language_launcher_config.jar_distributions
        launcher_config = mx_sdk.LauncherConfig(**launcherConfig)
        super(GraalVmPolyglotLauncher, self).__init__(suite, GraalVmLauncher.launcher_project_name(launcher_config), deps, workingSets, launcher_config, **kw_args)

    def build_args(self):
        graalvm_destination = get_final_graalvm_distribution().find_single_source_location('dependency:' + self.name)
        graalvm_destination = relpath(graalvm_destination, _get_graalvm_archive_path(""))
        return super(GraalVmPolyglotLauncher, self).build_args() + [
            '-H:-ParseRuntimeOptions',
            '-Dorg.graalvm.launcher.classpath=' + graalvm_home_relative_classpath(self.native_image_jar_distributions),
            '-Dorg.graalvm.launcher.relative.home=' + graalvm_destination
        ] + GraalVmLanguageLauncher.default_tool_options()


class GraalVmLibrary(GraalVmNativeImage):
    def __init__(self, suite, name, deps, workingSets, native_image_config, **kw_args):
        assert isinstance(native_image_config, mx_sdk.LibraryConfig), type(native_image_config).__name__
        super(GraalVmLibrary, self).__init__(suite, name, deps, workingSets, native_image_config=native_image_config, **kw_args)

        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        stage1 = get_stage1_graalvm_distribution()
        self.buildDependencies += ['{}:{}'.format(stage1.suite, stage1.name)]

    def build_args(self):
        return super(GraalVmLibrary, self).build_args() + ["-H:Kind=SHARED_LIBRARY"]

    def getBuildTask(self, args):
        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        return GraalVmLibraryBuildTask(self, args, svm_support)

    def getArchivableResults(self, use_relpath=True, single=False):
        for e in super(GraalVmLibrary, self).getArchivableResults(use_relpath=use_relpath, single=single):
            yield e
            if single:
                return
        output_dir = dirname(self.output_file())
        for e in os.listdir(output_dir):
            absolute_path = join(output_dir, e)
            if isfile(absolute_path) and e.endswith('.h'):
                yield absolute_path, e


class GraalVmMiscLauncher(GraalVmLauncher):
    def __init__(self, native_image_config, stage1=False, **kw_args):
        super(GraalVmMiscLauncher, self).__init__(_suite, GraalVmLauncher.launcher_project_name(native_image_config, stage1=stage1), [], None, native_image_config, stage1=stage1, **kw_args)


class GraalVmLanguageLauncher(GraalVmLauncher):
    def __init__(self, native_image_config, stage1=False, **kw_args):
        super(GraalVmLanguageLauncher, self).__init__(_suite, GraalVmLauncher.launcher_project_name(native_image_config, stage1=stage1), [], None, native_image_config, stage1=stage1, **kw_args)

    @staticmethod
    def default_tool_options():
        return ["--tool:" + tool.dir_name for tool in mx_sdk.graalvm_components() if isinstance(tool, mx_sdk.GraalVmTool) and tool.include_by_default]

    def build_args(self):
        return super(GraalVmLanguageLauncher, self).build_args() + [
            '-H:-ParseRuntimeOptions',
            '-Dorg.graalvm.launcher.classpath=' + graalvm_home_relative_classpath(self.native_image_jar_distributions, graal_vm=self.get_containing_graalvm()),
            '-Dorg.graalvm.launcher.relative.language.home=' + self.native_image_config.destination.replace('/', os.path.sep)
        ] + GraalVmLanguageLauncher.default_tool_options()


class GraalVmNativeImageBuildTask(mx.ProjectBuildTask):
    __metaclass__ = ABCMeta

    def needsBuild(self, newestInput):
        sup = super(GraalVmNativeImageBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        out_file = mx.TimeStampFile(self.subject.output_file())
        if not out_file.exists():
            return True, '{} does not exist'.format(out_file.path)
        if newestInput and out_file.isOlderThan(newestInput):
            return True, '{} is older than {}'.format(out_file, newestInput)
        reason = self.native_image_needs_build(out_file)
        if reason:
            return True, reason
        return False, None

    def native_image_needs_build(self, out_file):
        # TODO check if definition has changed
        return None

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_file())

    def clean(self, forBuild=False):
        out_file = self.subject.output_file()
        if exists(out_file):
            os.unlink(out_file)

    def __str__(self):
        return 'Building {}'.format(self.subject.name)


class GraalVmBashLauncherBuildTask(GraalVmNativeImageBuildTask):
    def __init__(self, subject, args):
        """
        :type subject: GraalVmNativeImage
        """
        super(GraalVmBashLauncherBuildTask, self).__init__(args, 1, subject)

    @staticmethod
    def _template_file():
        return join(_suite.mxDir, 'launcher_template.sh')

    def native_image_needs_build(self, out_file):
        sup = super(GraalVmBashLauncherBuildTask, self).native_image_needs_build(out_file)
        if sup:
            return sup
        if out_file.isOlderThan(self._template_file()):
            return 'template {} updated'.format(self._template_file())
        return None

    def build(self):
        output_file = self.subject.output_file()
        mx.ensure_dir_exists(dirname(output_file))
        graal_vm = self.subject.get_containing_graalvm()
        script_destination_directory = dirname(graal_vm.find_single_source_location('dependency:' + self.subject.name))
        jre_bin = _get_graalvm_archive_path('jre/bin', graal_vm=graal_vm)

        def _get_classpath():
            return graalvm_home_relative_classpath(self.subject.native_image_jar_distributions, script_destination_directory, graal_vm=graal_vm)

        def _get_jre_bin():
            return relpath(jre_bin, script_destination_directory)

        def _get_main_class():
            return self.subject.native_image_config.main_class

        _template_subst = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        _template_subst.register_no_arg('classpath', _get_classpath)
        _template_subst.register_no_arg('jre_bin', _get_jre_bin)
        _template_subst.register_no_arg('main_class', _get_main_class)

        with open(self._template_file(), 'r') as template, mx.SafeFileCreation(output_file) as sfc, open(sfc.tmpPath, 'w') as launcher:
            for line in template:
                launcher.write(_template_subst.substitute(line))
        os.chmod(output_file, 0o755)


def _get_graalvm_archive_path(jdk_path, graal_vm=None):
    if graal_vm is None:
        graal_vm = get_final_graalvm_distribution()
    if graal_vm.jdk_base and graal_vm.jdk_base != '.':
        if jdk_path and jdk_path != '.':
            return graal_vm.jdk_base + '/' + jdk_path
        else:
            return graal_vm.jdk_base
    return jdk_path


@contextmanager
def lock_directory(path):
    with open(join(path, '.lock'), 'w') as fd:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)


# Those libraries are optional runtime dependencies of SVM
_known_missing_jars = {
    'HAMCREST',
    'JUNIT',
    'JUNIT_TOOL',
    'JLINE',
    'TRUFFLE_DEBUG',
    'NANO_HTTPD',
    'NANO_HTTPD_WEBSERVER',
    'JFFI',
    'JNR_FFI',
    'JNR_INVOKE',
    'JFFI_NATIVE',
    'JNR_POSIX',
    'JNR_CONSTANTS',
    'JDK_TOOLS',
}


def graalvm_home_relative_classpath(dependencies, start=None, with_boot_jars=False, graal_vm=None):
    if graal_vm is None:
        graal_vm = get_final_graalvm_distribution()
    start = start or _get_graalvm_archive_path('', graal_vm=graal_vm)
    assert start.startswith(_get_graalvm_archive_path('', graal_vm=graal_vm)), start + " does not start with " + _get_graalvm_archive_path('', graal_vm=graal_vm)
    """:type : GraalVmLayoutDistribution"""
    boot_jars_directory = "jre/lib/boot"
    if graal_vm.jdk_base and graal_vm.jdk_base != '.':
        assert not graal_vm.jdk_base.endswith('/')
        boot_jars_directory = graal_vm.jdk_base + "/" + boot_jars_directory
    _cp = set()
    mx.logv("Composing classpath for " + str(dependencies) + ". Entries:\n" + '\n'.join(('- {}:{}'.format(d.suite, d.name) for d in mx.classpath_entries(dependencies))))
    for _cp_entry in mx.classpath_entries(dependencies):
        if _cp_entry.isJdkLibrary() or _cp_entry.isJreLibrary():
            jdk = _get_jdk()
            jdk_location = relpath(_cp_entry.classpath_repr(jdk), jdk.home)
            graalvm_location = join(graal_vm.jdk_base, jdk_location)
        else:
            graalvm_location = graal_vm.find_single_source_location('dependency:{}:{}'.format(_cp_entry.suite, _cp_entry.name), fatal_if_missing=False)
            if graalvm_location is None and _cp_entry.isDistribution():
                # Try to find an overlapping distribution
                for _, layout_source in graal_vm._walk_layout():
                    if layout_source['source_type'] == 'dependency' and layout_source['path'] is None:
                        d = mx.dependency(layout_source['dependency'])
                        if d.isDistribution():
                            if _cp_entry in d.overlapped_distributions() and set(_cp_entry.archived_deps()) <= set(d.archived_deps()):
                                mx.logv("{}:{} is not available in GraalVM, replacing with {}:{}".format(_cp_entry.suite, _cp_entry.name, d.suite, d.name))
                                graalvm_location = graal_vm.find_single_source_location('dependency:{}:{}'.format(d.suite, d.name))
                                break
            if graalvm_location is None:
                if _cp_entry.name in _known_missing_jars:
                    mx.warn("Skipping known missing dependency {} when building classpath for {}".format(_cp_entry.name, dependencies))
                    continue
                mx.abort("Could not find '{}:{}' in GraalVM ('{}')".format(_cp_entry.suite, _cp_entry.name, graal_vm.name))
        if not with_boot_jars and (graalvm_location.startswith(boot_jars_directory) or _cp_entry.isJreLibrary()):
            continue
        _cp.add(relpath(graalvm_location, start))
    return ":".join(_cp)


class GraalVmSVMNativeImageBuildTask(GraalVmNativeImageBuildTask):
    def __init__(self, subject, args, svm_support):
        """
        :type subject: GraalVmNativeImage
        :type svm_support: SvmSupport
        """
        super(GraalVmSVMNativeImageBuildTask, self).__init__(args, min(8, mx.cpu_count()), subject)
        self.svm_support = svm_support

    def build(self):
        build_args = self.get_build_args(prepare=False)
        output_file = self.subject.output_file()
        mx.ensure_dir_exists(dirname(output_file))

        # Disable build server (different Java properties on each build prevent server reuse)
        self.svm_support.native_image(build_args, output_file)

        with open(self._get_command_file(), 'w') as f:
            f.writelines((l + os.linesep for l in build_args))

    def native_image_needs_build(self, out_file):
        sup = super(GraalVmSVMNativeImageBuildTask, self).native_image_needs_build(out_file)
        if sup:
            return sup
        previous_build_args = []
        command_file = self._get_command_file()
        if exists(command_file):
            with open(command_file) as f:
                previous_build_args = [l.rstrip('\r\n') for l in f.readlines()]
        args = self.get_build_args(prepare=True)
        if previous_build_args != args:
            mx.logv("{} != {}".format(previous_build_args, args))
            return 'image command changed'
        return None

    def _get_command_file(self):
        return self.subject.output_file() + '.cmd'

    def get_build_args(self, prepare=True):
        version = _suite.release_version()
        build_args = [
            '-Dorg.graalvm.version={}'.format(version),
            '-Dgraalvm.version={}'.format(version),
        ]
        if _debug_images():
            build_args += ['-ea', '-H:-AOTInline']
        if not prepare and self.svm_support.is_debug_supported():
            build_args += ['-g']
        if self.subject.deps:
            build_args += ['-cp', mx.classpath(self.subject.native_image_jar_distributions)]
        build_args += self.subject.build_args()

        # rewrite --language:all & --tool:all
        final_build_args = []
        for build_arg in build_args:
            if build_arg == "--language:all":
                final_build_args += ["--language:" + component.dir_name for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmLanguage) and component.include_in_polyglot]
            elif build_arg == "--tool:all":
                final_build_args += ["--tool:" + component.dir_name for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmTool) and component.include_in_polyglot]
            else:
                final_build_args.append(build_arg)
        return final_build_args


class GraalVmSVMLauncherBuildTask(GraalVmSVMNativeImageBuildTask):
    def get_build_args(self, prepare=True):
        main_class = self.subject.native_image_config.main_class
        return super(GraalVmSVMLauncherBuildTask, self).get_build_args(prepare=prepare) + [main_class]


class GraalVmLibraryBuildTask(GraalVmSVMNativeImageBuildTask):
    pass


class InstallableComponentArchiver(mx.Archiver):
    def __init__(self, path, component, **kw_args):
        """
        :type path: str
        :type component: mx_sdk.GraalVmLanguage
        :type kind: str
        :type reset_user_group: bool
        :type duplicates_action: str
        :type context: object
        """
        super(InstallableComponentArchiver, self).__init__(path, **kw_args)
        self.component = component
        self.permissions = []
        self.symlinks = []

    @staticmethod
    def _perm_str(filename):
        _perm = str(oct(os.lstat(filename).st_mode)[-3:])
        _str = ''
        for _p in _perm:
            if _p == '7':
                _str += 'rwx'
            elif _p == '6':
                _str += 'rw-'
            elif _p == '5':
                _str += 'r-x'
            elif _p == '4':
                _str += 'r--'
            elif _p == '0':
                _str += '---'
            else:
                mx.abort('File {} has unsupported permission {}'.format(filename, _perm))
        return _str

    def add(self, filename, archive_name, provenance):
        self.permissions.append('{} = {}'.format(archive_name, self._perm_str(filename)))
        super(InstallableComponentArchiver, self).add(filename, archive_name, provenance)

    def add_str(self, data, archive_name, provenance):
        self.permissions.append('{} = {}'.format(archive_name, 'rw-rw-r--'))
        super(InstallableComponentArchiver, self).add_str(data, archive_name, provenance)

    def add_link(self, target, archive_name, provenance):
        self.permissions.append('{} = {}'.format(archive_name, 'rwxrwxrwx'))
        self.symlinks.append('{} = {}'.format(archive_name, target))
        # do not add symlinks, use the metadata to create them

    def __exit__(self, exc_type, exc_value, traceback):
        _manifest_str = """Bundle-Name: {name}
Bundle-Symbolic-Name: org.graalvm.{id}
Bundle-Version: {version}
Bundle-RequireCapability: org.graalvm; filter:="(&(graalvm_version={version})(os_name={os})(os_arch={arch}))"
x-GraalVM-Polyglot-Part: {polyglot}
x-GraalVM-Working-Directories: {workdir}
""".format(  # GR-10249: the manifest file must end with a newline
            name=self.component.name,
            id=self.component.dir_name,
            version=_suite.release_version(),
            os=get_graalvm_os(),
            arch=mx.get_arch(),
            polyglot=isinstance(self.component, mx_sdk.GraalVmTruffleComponent) and self.component.include_in_polyglot
                        and (not isinstance(self.component, mx_sdk.GraalVmTool) or self.component.include_by_default),
            workdir=join('jre', 'languages', self.component.dir_name))

        if self.component.post_install_msg:
            _manifest_str += """x-GraalVM-Message-PostInst: {msg}
""".format(msg=self.component.post_install_msg.replace("\\", "\\\\").replace("\n", "\\n"))

        _manifest_lines = []
        for l in _manifest_str.split('\n'):
            _first = True
            while len(l) > 72:
                _manifest_lines += [("" if _first else " ") + l[:72]]
                l = l[72:]
                _first = False
            if len(l) > 0:
                _manifest_lines += [("" if _first else " ") + l]

        _manifest_str_wrapped = '\n'.join(_manifest_lines) + "\n"
        _manifest_arc_name = 'META-INF/MANIFEST.MF'

        _permissions_str = '\n'.join(self.permissions)
        _permissions_arc_name = 'META-INF/permissions'

        _symlinks_str = '\n'.join(self.symlinks)
        _symlinks_arc_name = 'META-INF/symlinks'

        for _str, _arc_name in [(_manifest_str_wrapped, _manifest_arc_name), (_permissions_str, _permissions_arc_name),
                                (_symlinks_str, _symlinks_arc_name)]:
            self.add_str(_str, _arc_name, '{}<-string:{}'.format(_arc_name, _str))

        super(InstallableComponentArchiver, self).__exit__(exc_type, exc_value, traceback)


class GraalVmInstallableComponent(BaseGraalVmLayoutDistribution, mx.LayoutJARDistribution):  # pylint: disable=R0901
    def __init__(self, component, **kw_args):
        """
        :type component: mx_sdk.GraalVmLanguage
        """
        self.main_component = component

        def create_archive(path, **_kw_args):
            assert len(self.components) == 1
            return InstallableComponentArchiver(path, self.components[0], **_kw_args)

        other_involved_components = []
        if _get_svm_support().is_supported() and _get_launcher_configs(component):
            other_involved_components += [c for c in mx_sdk.graalvm_components() if c.dir_name == 'svm']

        name = '{}_INSTALLABLE'.format(component.dir_name.upper())
        for launcher_config in _get_launcher_configs(component):
            if _force_bash_launchers(launcher_config):
                name += '_B' + basename(launcher_config.destination).upper()
        if other_involved_components:
            name += '_' + '_'.join(sorted((component.short_name.upper() for component in other_involved_components)))
        self.maven = True
        super(GraalVmInstallableComponent, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            components=[component],
            is_graalvm=False,
            exclLibs=[],
            platformDependent=True,
            theLicense=None,
            testDistribution=False,
            layout={},
            archive_factory=create_archive,
            path=None,
            **kw_args)


class GraalVmStandaloneComponent(mx.LayoutTARDistribution):  # pylint: disable=too-many-ancestors
    def __init__(self, installable, **kw_args):
        """
        :type installable: GraalVmInstallableComponent
        """
        support_dir_pattern = '<jdk_base>/jre/languages/{}/'.format(installable.main_component.dir_name)
        other_comp_names = []
        if _get_svm_support().is_supported() and _get_launcher_configs(installable.main_component):
            other_comp_names += [c.short_name for c in mx_sdk.graalvm_components() if c.dir_name == 'svm']

        main_comp_name = installable.main_component.dir_name
        version = _suite.release_version()

        name = '_'.join([main_comp_name, 'standalone'] + other_comp_names).upper().replace('-', '_')
        base_dir = './{comp_name}-{version}-{os}-{arch}/'.format(comp_name=main_comp_name, version=version, os=get_graalvm_os(), arch=mx.get_arch()).lower().replace('_', '-')
        layout = {}

        def is_jar_distribution(val):
            def _is_jar_distribution(val):
                return isinstance(mx.dependency(val, fatalIfMissing=False), mx.JARDistribution)

            if isinstance(val, str):
                return val.startswith('dependency:') and _is_jar_distribution(val.split(':', 1)[1])
            if isinstance(val, dict):
                return val['source_type'] == 'dependency' and _is_jar_distribution(val['dependency'])
            return False

        for key, value in installable.layout.items():
            # if the key refers to the support dir
            if key.startswith(support_dir_pattern):
                # take only the values that are not JAR distributions
                new_value = [v for v in value if not is_jar_distribution(v)]
                if new_value:
                    new_key = base_dir + key.split(support_dir_pattern, 1)[1]
                    layout[new_key] = new_value

        self.maven = True
        super(GraalVmStandaloneComponent, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            layout=layout,
            path=None,
            platformDependent=True,
            theLicense=None,
            path_substitutions=installable.path_substitutions,
            string_substitutions=installable.string_substitutions,
            **kw_args)


_final_graalvm_distribution = 'uninitialized'
_stage1_graalvm_distribution = 'uninitialized'
_lib_polyglot_project = 'uninitialized'
_polyglot_launcher_project = 'uninitialized'
_base_graalvm_layout = {
    "<jdk_base>/": [
        "file:GRAALVM-README.md",
    ],
    "<jdk_base>/jre/lib/": ["extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include"],
    "<jdk_base>/jre/lib/boot/": [
        "dependency:sdk:GRAAL_SDK",
        "dependency:sdk:GRAAL_SDK/*.src.zip",
    ],
    "<jdk_base>/jre/lib/graalvm/": [
        "dependency:sdk:LAUNCHER_COMMON",
        "dependency:sdk:LAUNCHER_COMMON/*.src.zip",
    ],
    "<jdk_base>/jre/lib/jvmci/parentClassLoader.classpath": [
        "string:../truffle/truffle-api.jar:../truffle/locator.jar:../truffle/truffle-nfi.jar",
    ],
    "<jdk_base>/jre/lib/truffle/": [
        "dependency:truffle:TRUFFLE_API",
        "dependency:truffle:TRUFFLE_API/*.src.zip",
        "dependency:truffle:TRUFFLE_DSL_PROCESSOR",
        "dependency:truffle:TRUFFLE_DSL_PROCESSOR/*.src.zip",
        "dependency:truffle:TRUFFLE_NFI",
        "dependency:truffle:TRUFFLE_NFI/*.src.zip",
        "dependency:truffle:TRUFFLE_TCK",
        "dependency:truffle:TRUFFLE_TCK/*.src.zip",
        "dependency:LOCATOR",
        "dependency:LOCATOR/*.src.zip",
        "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include",
    ],
}


def get_stage1_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _stage1_graalvm_distribution
    if _stage1_graalvm_distribution == 'uninitialized':
        _stage1_graalvm_distribution = GraalVmLayoutDistribution("graalvm", _base_graalvm_layout, stage1=True)
        _stage1_graalvm_distribution.description = "GraalVM distribution (stage1)"
        _stage1_graalvm_distribution.maven = False
    return _stage1_graalvm_distribution


def get_final_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _final_graalvm_distribution
    if _final_graalvm_distribution == 'uninitialized':
        _final_graalvm_distribution = GraalVmLayoutDistribution("graalvm", _base_graalvm_layout)
        _final_graalvm_distribution.description = "GraalVM distribution"
        _final_graalvm_distribution.maven = True
    return _final_graalvm_distribution


def get_lib_polyglot_project():
    global _lib_polyglot_project
    if _lib_polyglot_project == 'uninitialized':
        if not _get_svm_support().is_supported() or not _with_polyglot_lib_project():
            _lib_polyglot_project = None
        else:
            polyglot_lib_build_args = []
            polyglot_lib_jar_dependencies = []
            polyglot_lib_build_dependencies = []
            has_polyglot_lib_entrypoints = False
            if "LIBPOLYGLOT_DISABLE_BACKGROUND_COMPILATION" in os.environ:
                polyglot_lib_build_args += ["-R:-TruffleBackgroundCompilation"]
            for component in mx_sdk.graalvm_components():
                has_polyglot_lib_entrypoints |= component.has_polyglot_lib_entrypoints
                polyglot_lib_build_args += component.polyglot_lib_build_args
                polyglot_lib_jar_dependencies += component.polyglot_lib_jar_dependencies
                polyglot_lib_build_dependencies += component.polyglot_lib_build_dependencies

            if not has_polyglot_lib_entrypoints:
                _lib_polyglot_project = None
            else:
                lib_polyglot_config = mx_sdk.LibraryConfig(
                    destination="<lib:polyglot>",
                    jar_distributions=polyglot_lib_jar_dependencies,
                    build_args=[
                        "--language:all",
                        "-Dgraalvm.libpolyglot=true",
                        "-Dorg.graalvm.polyglot.install_name_id=@rpath/jre/lib/polyglot/<lib:polyglot>"
                    ] + GraalVmLanguageLauncher.default_tool_options() + polyglot_lib_build_args,
                )
                _lib_polyglot_project = GraalVmLibrary(_suite, GraalVmNativeImage.project_name(lib_polyglot_config), [], None, lib_polyglot_config)

                if polyglot_lib_build_dependencies:
                    if not hasattr(_lib_polyglot_project, 'buildDependencies'):
                        _lib_polyglot_project.buildDependencies = []
                    _lib_polyglot_project.buildDependencies += polyglot_lib_build_dependencies
    return _lib_polyglot_project


def get_polyglot_launcher_project():
    """:rtype: GraalVmPolyglotLauncher"""
    global _polyglot_launcher_project
    if _polyglot_launcher_project == 'uninitialized':
        if _with_polyglot_launcher_project():
            _polyglot_launcher_project = GraalVmPolyglotLauncher(
                suite=_suite,
                deps=[],
                workingSets=None,
                launcherConfig={
                    "build_args": [
                        "-H:-ParseRuntimeOptions",
                        "-H:Features=org.graalvm.launcher.PolyglotLauncherFeature",
                        "--language:all"
                    ],
                    "jar_distributions": [
                        "sdk:LAUNCHER_COMMON",
                    ],
                    "main_class": "org.graalvm.launcher.PolyglotLauncher",
                    "destination": "polyglot",
                }
            )
        else:
            _polyglot_launcher_project = None
    return _polyglot_launcher_project


def register_vm_config(config_name, components):
    """
    :type config_name: str
    :type components: list[str]
    """
    _vm_configs[config_name] = components


_launcher_configs = None


def _get_launcher_configs(component):
    """ :rtype : list[mx_sdk.LauncherConfig]"""
    global _launcher_configs
    if _launcher_configs is None:
        launchers = {}
        for component_ in mx_sdk.graalvm_components():
            for launcher_config in component_.launcher_configs:
                launcher_name = launcher_config.destination
                if launcher_name in launchers:
                    _, prev_component = launchers[launcher_name]
                    if prev_component.priority > component_.priority:
                        continue
                    if prev_component.priority == component_.priority:
                        raise mx.abort("Conflicting launchers: {} and {} both declare a launcher called {}".format(component_.name, prev_component.name, launcher_name))
                launchers[launcher_name] = launcher_config, component_
        _launcher_configs = {}
        for launcher_config, component_ in launchers.values():
            _launcher_configs.setdefault(component_.name, []).append(launcher_config)
    return _launcher_configs.get(component.name, [])


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    if has_component('FastR'):
        fastr_release_env = mx.get_env('FASTR_RELEASE', None)
        if fastr_release_env is None:
            mx.abort("When including FastR, please set FASTR_RELEASE to true (env FASTR_RELEASE=true mx ...). Got FASTR_RELEASE={}".format(fastr_release_env))
        if mx.get_env('FASTR_RFFI') not in (None, ''):
            mx.abort("When including FastR, FASTR_RFFI should not be set. Got FASTR_RFFI=" + mx.get_env('FASTR_RFFI'))

    register_distribution(get_final_graalvm_distribution())

    id_to_component = dict()
    names = set()
    short_names = set()
    needs_stage1 = False
    for component in mx_sdk.graalvm_components():
        if component.name in names:
            mx.abort("Two components are named '{}'. The name should be unique".format(component.name))
        if component.short_name in short_names:
            mx.abort("Two components have short name '{}'. The short names should be unique".format(component.short_name))
        names.add(component.name)
        short_names.add(component.short_name)
        id_to_component.setdefault(component.dir_name, []).append(component)
        if register_project:
            if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                config_class = GraalVmLanguageLauncher
            else:
                config_class = GraalVmMiscLauncher
            for launcher_config in _get_launcher_configs(component):
                launcher_project = config_class(launcher_config)
                register_project(launcher_project)
                if launcher_project.is_native():
                    needs_stage1 = True
        # The JS components have issues ATM since they share the same directory
        if isinstance(component, mx_sdk.GraalVmLanguage) and not (_disable_installable(component) or component.dir_name == 'js'):
            installable_component = GraalVmInstallableComponent(component)
            register_distribution(installable_component)
            if has_svm_launcher(component):
                register_distribution(GraalVmStandaloneComponent(installable_component))

    if register_project:
        lib_polyglot_project = get_lib_polyglot_project()
        if lib_polyglot_project:
            needs_stage1 = True
            register_project(lib_polyglot_project)
        for components in id_to_component.values():
            truffle_components = [component for component in components if isinstance(component, mx_sdk.GraalVmTruffleComponent)]
            if truffle_components:
                register_project(GraalVmNativeProperties(truffle_components))

        polyglot_launcher_project = get_polyglot_launcher_project()
        if polyglot_launcher_project:
            needs_stage1 = True
            register_project(polyglot_launcher_project)

    if needs_stage1:
        if register_project:
            for component in mx_sdk.graalvm_components():
                if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                    config_class = GraalVmLanguageLauncher
                else:
                    config_class = GraalVmMiscLauncher
                for launcher_config in _get_launcher_configs(component):
                    register_project(config_class(launcher_config, stage1=True))
        register_distribution(get_stage1_graalvm_distribution())


def has_svm_launcher(component):
    """:type component: mx.GraalVmComponent | str"""
    component = get_component(component) if isinstance(component, str) else component
    return _get_svm_support().is_supported() and not _has_forced_launchers(component) and bool(component.launcher_configs)


def has_svm_polyglot_lib():
    return _get_svm_support().is_supported() and _with_polyglot_lib_project()


def get_component(name):
    """:type name: str"""
    for c in mx_sdk.graalvm_components():
        if c.short_name == name or c.name == name:
            return c
    return None


def has_component(name, fatalIfMissing=False):
    """
    :type name: str
    :type fatalIfMissing: bool
    """
    result = get_component(name)
    if fatalIfMissing and not result:
        mx.abort("'{}' is not registered as GraalVM component. Did you forget to dynamically import it?".format(name))
    return result


def has_components(names, fatalIfMissing=False):
    """
    :type names: list[str]
    :type fatalIfMissing: bool
    """
    return all((has_component(name, fatalIfMissing=fatalIfMissing) for name in names))


def graalvm_output():
    _graalvm = get_final_graalvm_distribution()
    _output_root = join(_suite.dir, _graalvm.output)
    return join(_output_root, _graalvm.jdk_base)


def graalvm_dist_name():
    return get_final_graalvm_distribution().name


def graalvm_version():
    return _suite.release_version()


def graalvm_home():
    _graalvm_dist = get_final_graalvm_distribution()
    return join(_graalvm_dist.output, _graalvm_dist.jdk_base)


def log_graalvm_dist_name(args):
    """print the name of the GraalVM distribution"""
    parser = ArgumentParser(prog='mx graalvm-dist-name', description='Print the name of the GraalVM distribution')
    _ = parser.parse_args(args)
    mx.log(graalvm_dist_name())


def log_graalvm_version(args):
    """print the GraalVM version"""
    parser = ArgumentParser(prog='mx graalvm-version', description='Print the GraalVM version')
    _ = parser.parse_args(args)
    mx.log(graalvm_version())


def log_graalvm_home(args):
    """print the GraalVM home dir"""
    parser = ArgumentParser(prog='mx graalvm-home', description='Print the GraalVM home directory')
    _ = parser.parse_args(args)
    mx.log(graalvm_home())


def graalvm_show(args):
    """print the GraalVM config"""
    parser = ArgumentParser(prog='mx graalvm-show', description='Print the GraalVM config')
    _ = parser.parse_args(args)

    graalvm_dist = get_final_graalvm_distribution()
    mx.log("GraalVM distribution: {}".format(graalvm_dist))
    mx.log("Version: {}".format(_suite.release_version()))
    mx.log("Components:")
    for component in mx_sdk.graalvm_components():
        mx.log(" - {} ('{}', /{})".format(component.name, component.short_name, component.dir_name))

    launchers = [p for p in _suite.projects if isinstance(p, GraalVmLauncher) and p.get_containing_graalvm() == graalvm_dist]
    if launchers:
        mx.log("Launchers:")
        for launcher in launchers:
            mx.log(" - {} ({})".format(launcher.native_image_name, "native" if launcher.is_native() else "bash"))
    else:
        mx.log("No launcher")

    libraries = [p for p in _suite.projects if isinstance(p, GraalVmLibrary)]
    if libraries:
        mx.log("Libraries:")
        for library in libraries:
            mx.log(" - {}".format(library.native_image_name))
    else:
        mx.log("No library")

    installables = [d for d in _suite.dists if isinstance(d, GraalVmInstallableComponent)]
    if installables:
        mx.log("Installables:")
        for i in installables:
            mx.log(" - {}".format(i))
    else:
        mx.log("No installable")

    standalones = [d for d in _suite.dists if isinstance(d, GraalVmStandaloneComponent)]
    if standalones:
        mx.log("Standalones:")
        for s in standalones:
            mx.log(" - {}".format(s))
    else:
        mx.log("No standalone")


def _env_var_to_bool(name, default='false'):
    val = mx.get_env(name, default)
    b = _str_to_bool(val)
    if isinstance(b, bool):
        return b
    else:
        raise mx.abort("Invalid boolean env var value {}={}; expected: <true | false>".format(name, val))


def _str_to_bool(val):
    """
    :type val: str
    :rtype: str | bool
    """
    low_val = val.lower()
    if low_val in ('false', '0', 'no'):
        return False
    elif low_val in ('true', '1', 'yes'):
        return True
    return val


mx_gate.add_gate_runner(_suite, mx_vm_gate.gate_body)
mx.add_argument('--disable-libpolyglot', action='store_true', help='Disable the \'polyglot\' library project')
mx.add_argument('--disable-polyglot', action='store_true', help='Disable the \'polyglot\' launcher project')
mx.add_argument('--disable-installables', action='store', help='Disable the \'installable\' distributions for gu.'
                                                               'This can be a comma-separated list of disabled components short names or `true` to disable all installables.', default=None)
mx.add_argument('--debug-images', action='store_true', help='Build native images in debug mode: -H:-AOTInline and with -ea')
mx.add_argument('--force-bash-launchers', action='store', help='Force the use of bash launchers instead of native images.'
                                                               'This can be a comma-separated list of disabled launchers or `true` to disable all native launchers.', default=None)
mx.add_argument('--no-sources', action='store_true', help='Do not include the archives with the source files of open-source components')

register_vm_config('ce', ['cmp', 'gu', 'gvm', 'ins', 'js', 'njs', 'polynative', 'pro', 'rgx', 'slg', 'svm', 'tfl', 'libpoly', 'poly', 'vvm'])


def _debug_images():
    return mx.get_opts().debug_images or _env_var_to_bool('DEBUG_IMAGES')


def _with_polyglot_lib_project():
    return not (mx.get_opts().disable_libpolyglot or _env_var_to_bool('DISABLE_LIBPOLYGLOT'))


def _with_polyglot_launcher_project():
    return not (mx.get_opts().disable_polyglot or _env_var_to_bool('DISABLE_POLYGLOT'))


def _force_bash_launchers(launcher, forced=None):
    """
    :type launcher: str | mx_sdk.AbstractNativeImageConfig
    :type forced: bool | None | str | list[str]
    """
    if forced is None:
        forced = _str_to_bool(mx.get_opts().force_bash_launchers or mx.get_env('FORCE_BASH_LAUNCHERS', 'false'))
    if isinstance(forced, bool):
        return forced
    if isinstance(forced, str):
        forced = forced.split(',')
    if isinstance(launcher, mx_sdk.AbstractNativeImageConfig):
        launcher = launcher.destination
    launcher_name = basename(launcher)
    return launcher_name in forced


def _disable_installable(component):
    """ :type component: str | mx_sdk.GraalVmComponent """
    disabled = _str_to_bool(mx.get_opts().disable_installables or mx.get_env('DISABLE_INSTALLABLES', 'false'))
    if isinstance(disabled, bool):
        return disabled
    if isinstance(disabled, str):
        disabled = disabled.split(',')
    if isinstance(component, mx_sdk.GraalVmComponent):
        component = component.short_name
    return component in disabled


def _has_forced_launchers(component, forced=None):
    """:type component: mx.GraalVmComponent"""
    for launcher_config in _get_launcher_configs(component):
        if _force_bash_launchers(launcher_config, forced):
            return True
    return False


def _include_sources():
    return not (mx.get_opts().no_sources or _env_var_to_bool('NO_SOURCES'))


mx.update_commands(_suite, {
    'graalvm-dist-name': [log_graalvm_dist_name, ''],
    'graalvm-version': [log_graalvm_version, ''],
    'graalvm-home': [log_graalvm_home, ''],
    'graalvm-show': [graalvm_show, ''],
})
