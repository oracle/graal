#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import fcntl
import os
import pprint
import json
from abc import abstractmethod, ABCMeta
from contextlib import contextmanager
from os.path import relpath, join, dirname, basename, exists, normpath, isfile
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

mx_sdk.register_graalvm_component(mx_sdk.GraalVmJreComponent(
    name='Component installer',
    short_name='gu',
    dir_name='installer',
    documentation_files=[],
    license_files=[],
    third_party_license_files=[],
    jre_lib_files=['extracted-dependency:vm:INSTALLER_GRAALVM_SUPPORT'],
    provided_executables=['link:<support>/bin/gu'],
), _suite)

mx_sdk.register_graalvm_component(mx_sdk.GraalVmComponent(
    name='GraalVM license files',
    short_name='gvm',
    documentation_files=[],
    license_files=[],
    third_party_license_files=['extracted-dependency:vm:VM_GRAALVM_SUPPORT/GraalCE_license_3rd_party_license.txt'],
), _suite)


class BaseGraalVmLayoutDistribution(mx.LayoutDistribution):
    __metaclass__ = ABCMeta

    def __init__(self, suite, name, deps, components, is_graalvm, exclLibs, platformDependent, theLicense, testDistribution, add_jdk_base=False, base_dir=None, layout=None, path=None, **kw_args):
        self.components = components
        base_dir = base_dir or '.'
        _src_jdk_base, _jdk_dir = _get_jdk_dir()
        _src_jdk_base = _src_jdk_base if add_jdk_base else '.'
        if base_dir != '.':
            self.jdk_base = '/'.join([base_dir, _src_jdk_base]) if _src_jdk_base and _src_jdk_base != '.' else base_dir
        else:
            self.jdk_base = _src_jdk_base

        def _get_component_id(comp=None, **kwargs):
            """
            :type comp: mx_sdk.GraalVmComponent
            """
            return comp.dir_name

        def _get_languages_or_tool(comp=None, **kwargs):
            if isinstance(comp, mx_sdk.GraalVmLanguage):
                return 'languages'
            if isinstance(comp, mx_sdk.GraalVmTool):
                return 'tools'
            return None

        def _get_jdk_base():
            return self.jdk_base

        def _get_support(comp=None, start=None, **kwargs):
            if isinstance(comp, mx_sdk.GraalVmTruffleComponent):
                _base_location = join(self.jdk_base, 'jre', _get_languages_or_tool(comp=comp))
            elif isinstance(comp, mx_sdk.GraalVmJdkComponent):
                _base_location = join(self.jdk_base, 'lib')
            elif isinstance(comp, mx_sdk.GraalVmJreComponent):
                _base_location = join(self.jdk_base, 'jre', 'lib')
            else:
                raise mx.abort('The \'<support>\' substitution is not available for component \'{}\' of type \'{}\''.format(comp.name, type(comp)))
            return relpath(join(_base_location, _get_component_id(comp=comp)), start=start)

        def _get_version(**kwargs):
            return _suite.release_version()

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('jdk_base', _get_jdk_base)

        string_substitutions = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        string_substitutions.register_no_arg('version', _get_version)

        _layout_value_subst = mx_subst.SubstitutionEngine(path_substitutions, skip_unknown_substitutions=True)
        _layout_value_subst.register_no_arg('comp_id', _get_component_id, keywordArgs=True)
        _layout_value_subst.register_no_arg('languages_or_tools', _get_languages_or_tool, keywordArgs=True)

        _component_path_subst = mx_subst.SubstitutionEngine(path_substitutions, skip_unknown_substitutions=True)
        _component_path_subst.register_no_arg('support', _get_support, keywordArgs=True)

        def _subst_src(src, start, component):
            if isinstance(src, basestring):
                return _component_path_subst.substitute(src, comp=component, start=start)
            elif isinstance(src, list):
                return [_subst_src(s, start, component) for s in src]
            elif isinstance(src, dict):
                return {k: _subst_src(v, start, component) for k, v in src.items()}
            else:
                mx.abort("Unsupported type: {} ({})".format(src, type(src).__name__))

        _layout_provenance = {}

        def _add(_layout, dest, src, component=None):
            """
            :type _layout: dict[str, list[str] | str]
            :type dest: str
            :type src: list[str | dict] | str | dict
            """
            assert dest.startswith('<jdk_base>') or base_dir == '.' or dest.startswith(base_dir), dest
            src = src if isinstance(src, list) else [src]
            if not src:
                return
            dest = _layout_value_subst.substitute(dest, comp=component)
            start = dest
            if not start.endswith('/'):
                start = dirname(start)
            src = _subst_src(src, start, component)

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
            _layout.setdefault(dest, []).extend(src)

        if is_graalvm:
            # Add base JDK
            exclude_base = _jdk_dir
            if _src_jdk_base != '.':
                exclude_base = join(exclude_base, _src_jdk_base)
            _add(layout, base_dir, {
                'source_type': 'file',
                'path': '{}'.format(_jdk_dir),
                'exclude': [
                    exclude_base + '/COPYRIGHT',
                    exclude_base + '/LICENSE',
                    exclude_base + '/release',
                    exclude_base + '/lib/visualvm',
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

            # Add Polyglot library
            lib_polyglot_project = get_lib_polyglot_project()
            if lib_polyglot_project:
                # Note that `jre/lib/polyglot` is synchronized with `org.graalvm.polyglot.install_name_id` in `get_lib_polyglot_project`
                _add(layout, "<jdk_base>/jre/lib/polyglot/" + lib_polyglot_project.native_image_name, "dependency:" + lib_polyglot_project.name)
                _add(layout, "<jdk_base>/jre/lib/polyglot/", "dependency:" + lib_polyglot_project.name + "/*.h")

            # Add release file
            _sorted_suites = sorted(mx.suites(), key=lambda s: s.name)

            _commit_info = {}
            for _s in _sorted_suites:
                if _s.vc:
                    _info = _s.vc.parent_info(_s.vc_dir)
                    _commit_info[_s.name] = {
                        "commit.rev": _s.vc.parent(_s.vc_dir),
                        "commit.committer": _info['committer'],
                        "commit.committer-ts": _info['committer-ts'],
                    }

            _metadata = """
OS_NAME=<os>
OS_ARCH=<arch>
SOURCE={source}
COMMIT_INFO={commit_info}
GRAALVM_VERSION={version}""".format(
                source=' '.join(['{}:{}'.format(_s.name, _s.version()) for _s in _sorted_suites]),
                commit_info=json.dumps(_commit_info, sort_keys=True),
                version=_suite.release_version()
            )
            _add(layout, "<jdk_base>/release", "string:{}".format(_metadata))

        # Add the rest of the GraalVM
        _layout_dict = {
            'documentation_files': ['<jdk_base>/docs/<comp_id>/'],
            'license_files': ['<jdk_base>/'],
            'third_party_license_files': ['<jdk_base>/'],
            'provided_executables': ['<jdk_base>/bin/', '<jdk_base>/jre/bin/'],
            'boot_jars': ['<jdk_base>/jre/lib/boot/'],
            'truffle_jars': ['<jdk_base>/jre/<languages_or_tools>/<comp_id>/'],
            'support_distributions': ['<jdk_base>/jre/<languages_or_tools>/<comp_id>/'],
            'jvmci_jars': ['<jdk_base>/jre/lib/jvmci/'],
            'jdk_lib_files': ['<jdk_base>/lib/<comp_id>/'],
            'jre_lib_files': ['<jdk_base>/jre/lib/<comp_id>/'],
        }

        has_graal_compiler = False
        for _component in self.components:
            mx.logv('Adding {} to the {} {}'.format(_component.name, name, self.__class__.__name__))
            for _layout_key, _layout_values in _layout_dict.iteritems():
                assert isinstance(_layout_values, list), 'Layout value of \'{}\' has the wrong type. Expected: \'list\'; got \'{}\''.format(
                    _layout_key, _layout_values)
                _component_paths = getattr(_component, _layout_key, [])
                for _layout_value in _layout_values:
                    _add(layout, _layout_value, _component_paths, _component)
            for _launcher_config in _component.launcher_configs:
                _add(layout, '<jdk_base>/jre/lib/graalvm/', _launcher_config.jar_distributions, _component)
                if isinstance(_component, mx_sdk.GraalVmTruffleComponent):
                    _launcher_base = '<jdk_base>/jre/<languages_or_tools>/<comp_id>/'
                elif isinstance(_component, mx_sdk.GraalVmJdkComponent):
                    _launcher_base = '<jdk_base>/'
                elif isinstance(_component, mx_sdk.GraalVmJreComponent):
                    _launcher_base = '<jdk_base>/jre/'
                else:
                    raise mx.abort("Unknown component type for {}: {}".format(_component.name, type(_component).__name__))
                _launcher_dest = _launcher_base + _launcher_config.destination
                # add `LauncherConfig.destination` to the layout
                _add(layout, _launcher_dest, 'dependency:' + GraalVmNativeImage.project_name(_launcher_config), _component)
                for _link in _launcher_config.links:
                    _link_dest = _launcher_base + _link
                    # add links `LauncherConfig.links` -> `LauncherConfig.destination`
                    _add(layout, _link_dest, 'link:{}'.format(relpath(_launcher_dest, start=dirname(_link_dest))), _component)
                if isinstance(_component, mx_sdk.GraalVmTruffleComponent):
                    for _provided_exec_path in _layout_dict['provided_executables']:
                        # add links `_layout_dict['provided_executables']` -> `[LauncherConfig.destination, LauncherConfing.links]`
                        for _name in [basename(_launcher_config.destination)] + [basename(_link) for _link in _launcher_config.links]:
                            _add(layout, join(_provided_exec_path, _name), 'link:<support>/{}'.format(_launcher_config.destination), _component)
                elif isinstance(_component, mx_sdk.GraalVmJreComponent):
                    # Add jdk to jre link
                    back_to_jdk_count = len(normpath(_launcher_config.destination).split('/')) - 1
                    back_to_jdk = '/'.join(['..'] * back_to_jdk_count)
                    if back_to_jdk:
                        back_to_jdk += '/'
                    _add(layout, '<jdk_base>/' + _launcher_config.destination, 'link:' + back_to_jdk + "jre/" + _launcher_config.destination, _component)
            if isinstance(_component, mx_sdk.GraalVmJvmciComponent) and _component.graal_compiler:
                has_graal_compiler = True

        if has_graal_compiler:
            _add(layout, '<jdk_base>/jre/lib/jvmci/compiler-name', 'string:graal')

        super(BaseGraalVmLayoutDistribution, self).__init__(suite, name, deps, layout, path, platformDependent,
                                                            theLicense, exclLibs, path_substitutions=path_substitutions,
                                                            string_substitutions=string_substitutions,
                                                            testDistribution=testDistribution, **kw_args)

        mx.logv("'{}' has layout:\n{}".format(self.name, pprint.pformat(self.layout)))


class GraalVmLayoutDistribution(BaseGraalVmLayoutDistribution, mx.LayoutTARDistribution):
    def __init__(self, base_name, base_layout, theLicense=None, **kw_args):
        components = mx_sdk.graalvm_components()
        name = base_name + "_" + '_'.join(sorted((component.short_name.upper() for component in components)))
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
            base_dir='graalvm-{}'.format(_suite.release_version()),
            layout=layout,
            path=None,
            **kw_args)


def _get_jdk_dir():
    java_home = mx.get_jdk(tag='default').home
    jdk_dir = java_home
    if jdk_dir.endswith(os.path.sep):
        jdk_dir = jdk_dir[:-len(os.path.sep)]
    if jdk_dir.endswith('/Contents/Home'):
        jdk_base = 'Contents/Home'
        jdk_dir = jdk_dir[:-len('/Contents/Home')]
    else:
        jdk_base = '.'
    return jdk_base, jdk_dir


class SvmSupport(object):
    def __init__(self, svm_suite):
        """
        :type svm_suite: mx.Suite
        """
        if svm_suite:
            self.suite_native_image_root = svm_suite.extensions.suite_native_image_root
            self.fetch_languages = svm_suite.extensions.fetch_languages
            self.native_image_path = svm_suite.extensions.native_image_path
            self.get_native_image_distribution = svm_suite.extensions.native_image_distribution
            self.extract_target_name = svm_suite.extensions.extract_target_name
            self.flag_suitename_map = svm_suite.extensions.flag_suitename_map
            self._svm_supported = True
        else:
            self.suite_native_image_root = None
            self.fetch_languages = None
            self.native_image_path = None
            self.get_native_image_distribution = None
            self.extract_target_name = None
            self.flag_suitename_map = None
            self._svm_supported = False

    def is_supported(self):
        return self._svm_supported

    def is_native_image_ready(self):
        return exists(self.native_image_path(self.suite_native_image_root()))

    def native_image(self, build_args, output_file, allow_server=False, nonZeroIsFatal=True, out=None, err=None):
        native_image_root = self.suite_native_image_root()
        native_image_command = [self.native_image_path(native_image_root)] + build_args
        if not allow_server:
            native_image_command += ['--no-server']
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
            if not self.is_native_image_ready():
                return False
            out = mx.OutputCapture()
            err = mx.OutputCapture()
            self.native_image(['-g', "Dummy"], "dummy", out=out, err=err, nonZeroIsFatal=False)
            if "Could not find option" in err.data:
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
            for support_dist in component.support_distributions:
                assert support_dist.startswith("extracted-dependency:"), support_dist
                support_dist = support_dist[len("extracted-dependency:"):]
                idx = support_dist.find('/')
                if idx >= 0:
                    deps.append(support_dist[:idx])
                else:
                    deps.append(support_dist)
        self.components = components
        super(GraalVmNativeProperties, self).__init__(_suite, GraalVmNativeProperties.project_name(components[0].dir_name), subDir=None, srcDirs=[], deps=deps, workingSets=[], d=_suite.dir, theLicense=None, **kw_args)

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
                native_image_jar_distributions = []
                for d in launcher_config.jar_distributions:
                    if d.startswith("dependency:"):
                        native_image_jar_distributions.append(d[len("dependency:"):])
                    else:
                        mx.abort("Unexpected jar_distribution: {} in launcher for {}".format(d, dir_name))
                properties = {
                    'ImageName': basename(launcher_config.destination),
                    'LauncherClass': basename(launcher_config.main_class),
                    'LauncherClassPath': graalvm_home_relative_classpath(native_image_jar_distributions, _get_graalvm_archive_path('jre')),
                    'Args': ' '.join(launcher_config.build_args),
                }
                for p in ('ImageName', 'LauncherClass', 'LauncherClassPath'):
                    if provided_properties[p] != properties[p]:
                        mx.abort("Inconsistent property '{}':\n - native-image.properties: {}\n - LauncherConfig: {}".format(p, provided_properties[p], properties[p]))

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
        svm_support = _get_svm_support()
        self.native_image_jar_distributions = []
        for d in native_image_config.jar_distributions:
            if d.startswith("dependency:"):
                self.native_image_jar_distributions.append(d[len("dependency:"):])
            else:
                mx.abort("Unexpected jar_distribution: " + d)
        if svm_support.is_supported():
            deps += self.native_image_jar_distributions
        super(GraalVmNativeImage, self).__init__(suite=suite, name=name, subDir=None, srcDirs=[], deps=deps,
                                                 workingSets=workingSets, d=_suite.dir, theLicense=theLicense,
                                                 **kw_args)

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

    def resolveDeps(self):
        super(GraalVmNativeImage, self).resolveDeps()
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            if not hasattr(self, 'buildDependencies'):
                self.buildDependencies = []
            native_image = svm_support.get_native_image_distribution()
            assert isinstance(native_image, mx.Dependency)
            self.buildDependencies += [native_image]


class GraalVmLauncher(GraalVmNativeImage):
    def __init__(self, suite, name, deps, workingSets, native_image_config, theLicense=None, **kw_args):
        """
        :type native_image_config: mx_sdk.LauncherConfig
        """
        assert isinstance(native_image_config, mx_sdk.LauncherConfig), type(native_image_config).__name__
        super(GraalVmLauncher, self).__init__(suite, name, deps, workingSets, native_image_config, theLicense=theLicense, **kw_args)

        svm_support = _get_svm_support()
        if svm_support.is_supported():
            # fetch_languages will need a bunch of distributions add them as build deps
            if not hasattr(self, 'buildDependencies'):
                self.buildDependencies = []
            #  we only look at self.native_image_config.build_args because we might not be ready for more
            for arg in self.native_image_config.build_args:
                language_flag, _ = svm_support.extract_target_name(arg, 'language')
                if language_flag == 'all':
                    language_flags = [component.dir_name for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmLanguage) and component.include_in_polyglot]
                else:
                    language_flags = [language_flag]
                for language_flag in language_flags:
                    if language_flag:
                        suite_name, jars, support = svm_support.flag_suitename_map[language_flag][0:3]  # drop potential 3rd element
                        self.buildDependencies += [suite_name + ':' + e for e in jars + support]

    def getBuildTask(self, args):
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            return GraalVmSVMLauncherBuildTask(self, args, svm_support)
        else:
            return GraalVmBashLauncherBuildTask(self, args)

    def output_file(self):
        return join(self.get_output_base(), self.name, self.getBuildTask([]).launcher_type(), self.native_image_name)


class GraalVmPolyglotLauncher(GraalVmLauncher):
    def __init__(self, suite, name, deps, workingSets, launcherConfig, **kw_args):
        for component in mx_sdk.graalvm_components():
            if isinstance(component, mx_sdk.GraalVmLanguage) and component.include_in_polyglot:
                for language_launcher_config in component.launcher_configs:
                    if isinstance(language_launcher_config, mx_sdk.LanguageLauncherConfig):
                        launcherConfig['jar_distributions'] += language_launcher_config.jar_distributions
        super(GraalVmPolyglotLauncher, self).__init__(suite, name, deps, workingSets, mx_sdk.LauncherConfig(**launcherConfig), **kw_args)

    def build_args(self):
        graalvm_destination = get_graalvm_distribution().find_single_source_location('dependency:' + self.name)
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
        # fetch_languages will need a bunch of distributions add them as build deps
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        language_ids = set((component.dir_name for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmLanguage) and component.include_in_polyglot))
        for language_id in language_ids:
            suite_name, jars, support = svm_support.flag_suitename_map[language_id][0:3]  # drop potential 3rd element
            self.buildDependencies += [suite_name + ':' + e for e in jars + support]

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
    def __init__(self, native_image_config, **kw_args):
        super(GraalVmMiscLauncher, self).__init__(_suite, GraalVmNativeImage.project_name(native_image_config), [], None, native_image_config, **kw_args)


class GraalVmLanguageLauncher(GraalVmLauncher):
    def __init__(self, native_image_config, **kw_args):
        super(GraalVmLanguageLauncher, self).__init__(_suite, GraalVmNativeImage.project_name(native_image_config), [], None, native_image_config, **kw_args)

    @staticmethod
    def default_tool_options():
        return ["--tool:" + tool.dir_name for tool in mx_sdk.graalvm_components() if isinstance(tool, mx_sdk.GraalVmTool) and tool.include_by_default]

    def build_args(self):
        return super(GraalVmLanguageLauncher, self).build_args() + [
            '-H:-ParseRuntimeOptions',
            '-Dorg.graalvm.launcher.classpath=' + graalvm_home_relative_classpath(self.native_image_jar_distributions),
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


class GraalVmLauncherBuildTask(GraalVmNativeImageBuildTask):
    def __str__(self):
        return 'Building {} ({})'.format(self.subject.name, self.launcher_type())

    @abstractmethod
    def launcher_type(self):
        pass


class GraalVmBashLauncherBuildTask(GraalVmLauncherBuildTask):
    def __init__(self, subject, args):
        """
        :type subject: GraalVmNativeImage
        """
        super(GraalVmBashLauncherBuildTask, self).__init__(args, 1, subject)

    def launcher_type(self):
        return 'bash'

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
        graal_vm = get_graalvm_distribution()
        script_destination_directory = dirname(graal_vm.find_single_source_location('dependency:' + self.subject.name))
        jre_bin = _get_graalvm_archive_path('jre/bin')

        def _get_classpath():
            return graalvm_home_relative_classpath(self.subject.native_image_jar_distributions, script_destination_directory)

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


def _get_graalvm_archive_path(jdk_path):
    graal_vm = get_graalvm_distribution()
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


def graalvm_home_relative_classpath(dependencies, start=None, with_boot_jars=False):
    start = start or _get_graalvm_archive_path('')
    assert start.startswith(_get_graalvm_archive_path(''))
    graal_vm = get_graalvm_distribution()
    """:type : GraalVmLayoutDistribution"""
    boot_jars_directory = "jre/lib/boot"
    if graal_vm.jdk_base and graal_vm.jdk_base != '.':
        assert not graal_vm.jdk_base.endswith('/')
        boot_jars_directory = graal_vm.jdk_base + "/" + boot_jars_directory
    _cp = []
    for _cp_entry in mx.classpath_entries(dependencies):
        graalvm_location = graal_vm.find_single_source_location('dependency:{}:{}'.format(_cp_entry.suite, _cp_entry.name))
        if with_boot_jars or graalvm_location.startswith(boot_jars_directory):
            continue
        _cp.append(relpath(graalvm_location, start))
    return ":".join(_cp)


class GraalVmSVMNativeImageBuildTask(GraalVmNativeImageBuildTask):
    def __init__(self, subject, args, svm_support):
        """
        :type subject: GraalVmNativeImage
        :type svm_support: SvmSupport
        """
        super(GraalVmSVMNativeImageBuildTask, self).__init__(args, min(8, mx.cpu_count()), subject)
        self.svm_support = svm_support

    def prepare(self, daemons):
        args = self.get_build_args()
        mx.logvv("{}.fetch_languages({})".format(self.subject, args))
        try:
            self.svm_support.fetch_languages(args)  # Ensure languages are symlinked in native_image_root
        except IOError as e:
            mx.log_error("Error while preparing '{}'.\nSubject's dependencies are: {},\nits build dependencies are:{}.\nfetch_language arguments: {}".format(self, self.subject.deps, getattr(self.subject, 'buildDependencies'), args))
            raise e

    def build(self):
        build_args = self.get_build_args()
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
        args = self.get_build_args()
        if previous_build_args != args:
            mx.logv("{} != {}".format(previous_build_args, args))
            return 'image command changed'
        return None

    def _get_command_file(self):
        return self.subject.output_file() + '.cmd'

    def get_build_args(self):
        version = _suite.release_version()
        build_args = [
            '-Dorg.graalvm.version={}'.format(version),
            '-Dgraalvm.version={}'.format(version),
        ]
        if self.svm_support.is_debug_supported():
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


class GraalVmSVMLauncherBuildTask(GraalVmSVMNativeImageBuildTask, GraalVmLauncherBuildTask):
    def launcher_type(self):
        return 'svm'

    def get_build_args(self):
        main_class = self.subject.native_image_config.main_class
        return super(GraalVmSVMLauncherBuildTask, self).get_build_args() + [main_class]


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
x-GraalVM-Polyglot-Part: {polyglot}""".format(
            name=self.component.name,
            id=self.component.dir_name,
            version=_suite.release_version(),
            os=mx.get_os(),
            arch=mx.get_arch(),
            polyglot=isinstance(self.component, mx_sdk.GraalVmTruffleComponent) and self.component.include_in_polyglot
                        and (not isinstance(self.component, mx_sdk.GraalVmTool) or self.component.include_by_default))

        _manifest_arc_name = 'META-INF/MANIFEST.MF'

        _permissions_str = '\n'.join(self.permissions)
        _permissions_arc_name = 'META-INF/permissions'

        _symlinks_str = '\n'.join(self.symlinks)
        _symlinks_arc_name = 'META-INF/symlinks'

        for _str, _arc_name in [(_manifest_str, _manifest_arc_name), (_permissions_str, _permissions_arc_name),
                                (_symlinks_str, _symlinks_arc_name)]:
            self.add_str(_str, _arc_name, '{}<-string:{}'.format(_arc_name, _str))

        super(InstallableComponentArchiver, self).__exit__(exc_type, exc_value, traceback)


class GraalVmInstallableComponent(BaseGraalVmLayoutDistribution, mx.LayoutJARDistribution):
    def __init__(self, component, **kw_args):
        """
        :type component: mx_sdk.GraalVmLanguage
        """

        def create_archive(path, **_kw_args):
            assert len(self.components) == 1
            return InstallableComponentArchiver(path, self.components[0], **_kw_args)

        other_involved_components = []
        if _get_svm_support().is_supported() and component.launcher_configs:
            other_involved_components += [c for c in mx_sdk.graalvm_components() if c.dir_name == 'svm']

        name = '{}_INSTALLABLE'.format(component.dir_name.upper())
        if other_involved_components:
            name += '_' + '_'.join(sorted((component.short_name.upper() for component in other_involved_components)))
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


_graalvm_distribution = 'uninitialized'
_lib_polyglot_project = 'uninitialized'
_base_graalvm_layout = {
   "<jdk_base>/": [
       "file:README.md",
   ],
   "<jdk_base>/jre/lib/": ["extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include"],
   "<jdk_base>/jre/bin/polyglot": "dependency:polyglot.launcher",
   "<jdk_base>/bin/polyglot": "link:../jre/bin/polyglot",
   "<jdk_base>/jre/lib/boot/": [
       "dependency:sdk:GRAAL_SDK",
   ],
   "<jdk_base>/jre/lib/graalvm/": [
       "dependency:sdk:LAUNCHER_COMMON",
   ],
   "<jdk_base>/jre/lib/jvmci/parentClassLoader.classpath": [
       "string:../truffle/truffle-api.jar:../truffle/locator.jar:../truffle/truffle-nfi.jar",
   ],
   "<jdk_base>/jre/lib/truffle/": [
       "dependency:truffle:TRUFFLE_API",
       "dependency:truffle:TRUFFLE_DSL_PROCESSOR",
       "dependency:truffle:TRUFFLE_NFI",
       "dependency:truffle:TRUFFLE_TCK",
       "dependency:LOCATOR",
       "extracted-dependency:truffle:TRUFFLE_NFI_NATIVE/include",
   ],
}


def get_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _graalvm_distribution
    if _graalvm_distribution == 'uninitialized':
        _graalvm_distribution = GraalVmLayoutDistribution("GRAALVM", _base_graalvm_layout)
        _graalvm_distribution.description = "GraalVM distribution"
    return _graalvm_distribution


def get_lib_polyglot_project():
    global _lib_polyglot_project
    if _lib_polyglot_project == 'uninitialized':
        if not _get_svm_support().is_supported():
            _lib_polyglot_project = None
        else:
            polyglot_lib_build_args = []
            polyglot_lib_jar_dependencies = []
            polyglot_lib_build_dependencies = []
            has_polyglot_lib_entrypoints = False
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
                    ] + polyglot_lib_build_args,
                )
                _lib_polyglot_project = GraalVmLibrary(_suite, GraalVmNativeImage.project_name(lib_polyglot_config), [], None, lib_polyglot_config)

                if polyglot_lib_build_dependencies:
                    if not hasattr(_lib_polyglot_project, 'buildDependencies'):
                        _lib_polyglot_project.buildDependencies = []
                    for polyglot_lib_build_dependency in polyglot_lib_build_dependencies:
                        if not polyglot_lib_build_dependency.startswith("dependency:"):
                            mx.abort("`polyglot_lib_build_dependencies` should start with `dependency:`, got: " + polyglot_lib_build_dependency)
                        _lib_polyglot_project.buildDependencies.append(polyglot_lib_build_dependency[len("dependency:"):])
    return _lib_polyglot_project


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

    register_distribution(get_graalvm_distribution())

    id_to_component = dict()
    names = set()
    short_names = set()
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
            for launcher_config in component.launcher_configs:
                register_project(config_class(launcher_config))
        if isinstance(component, mx_sdk.GraalVmLanguage) and component.dir_name != 'js':
            register_distribution(GraalVmInstallableComponent(component))

    if register_project:
        lib_polyglot_project = get_lib_polyglot_project()
        if lib_polyglot_project:
            register_project(lib_polyglot_project)
        for identifier, components in id_to_component.items():
            truffle_components = [component for component in components if isinstance(component, mx_sdk.GraalVmTruffleComponent)]
            if truffle_components:
                register_project(GraalVmNativeProperties(truffle_components))


def has_component(name):
    return any((c.short_name == name or c.name == name for c in mx_sdk.graalvm_components()))


def graalvm_output():
    _graalvm = get_graalvm_distribution()
    _output_root = join(_suite.dir, _graalvm.output)
    return join(_output_root, _graalvm.jdk_base)


mx_gate.add_gate_runner(_suite, mx_vm_gate.gate)
