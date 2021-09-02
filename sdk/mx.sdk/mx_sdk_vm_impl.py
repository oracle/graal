#
# Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
from argparse import ArgumentParser
from collections import OrderedDict
import hashlib
import io
import inspect
import json
import os
from os.path import relpath, join, dirname, basename, exists, isfile, normpath, abspath, isdir, islink, isabs
import pprint
import re
import shlex
import shutil
import subprocess
import sys

import mx
import mx_gate
import mx_subst
import mx_sdk
import mx_sdk_vm


if sys.version_info[0] < 3:
    _unicode = unicode # pylint: disable=undefined-variable
    def _decode(x):
        return x
    def _encode(x):
        return x
else:
    _unicode = str
    def _decode(x):
        return x.decode()
    def _encode(x):
        return x.encode()


def unicode_utf8(string):
    if sys.version_info[0] < 3:
        if isinstance(string, str):
            return unicode(string, 'utf-8') # pylint: disable=undefined-variable
    elif isinstance(string, bytes):
        return str(string)
    return string


def _with_metaclass(meta, *bases):
    """Create a base class with a metaclass."""

    # Copyright (c) 2010-2018 Benjamin Peterson
    # Taken from six, Python compatibility library
    # MIT license

    # This requires a bit of explanation: the basic idea is to make a dummy
    # metaclass for one level of class instantiation that replaces itself with
    # the actual metaclass.
    class MetaClass(type):

        def __new__(mcs, name, this_bases, d):
            return meta(name, bases, d)

        @classmethod
        def __prepare__(mcs, name, this_bases):
            return meta.__prepare__(name, bases)
    return type.__new__(MetaClass, '_with_metaclass({}, {})'.format(meta, bases), (), {}) #pylint: disable=unused-variable


_suite = mx.suite('sdk')
""":type: mx.SourceSuite | mx.Suite"""

_exe_suffix = mx.exe_suffix('')
""":type: str"""
_lib_suffix = mx.add_lib_suffix("")
_lib_prefix = mx.add_lib_prefix("")


_graalvm_base_name = 'GraalVM'


default_components = []

graalvm_version_regex = re.compile(r'.*\n.*\n[0-9a-zA-Z()\- ]+GraalVM[a-zA-Z_ ]+(?P<graalvm_version>[0-9a-z_\-.+]+) \(build [0-9a-z\-.+]+, mixed mode\)')

_registered_graalvm_components = {}
_env_tests = []


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJreComponent(
    suite=_suite,
    name='Polyglot Launcher',
    short_name='poly',
    license_files=[],
    third_party_license_files=[],
    dir_name='polyglot',
    launcher_configs=[mx_sdk_vm.LauncherConfig(
        destination='bin/<exe:polyglot>',
        jar_distributions=['sdk:LAUNCHER_COMMON'],
        main_class='org.graalvm.launcher.PolyglotLauncher',
        build_args=[
            '-H:-ParseRuntimeOptions',
            '-H:Features=org.graalvm.launcher.PolyglotLauncherFeature',
            '--tool:all',
        ],
        is_main_launcher=True,
        default_symlinks=True,
        is_sdk_launcher=True,
        is_polyglot=True,
    )],
))


def gate_body(args, tasks):
    with mx_gate.Task('Sdk: GraalVM dist names', tasks, tags=['names']) as t:
        if t:
            mx_sdk_vm.verify_graalvm_configs()


mx_gate.add_gate_runner(_suite, gate_body)


def registered_graalvm_components(stage1=False):
    """
    :type stage1: bool
    :rtype: list[mx_sdk_vm.GraalVmComponent]
    """
    if stage1 not in _registered_graalvm_components:
        components_include_list = _components_include_list()
        if components_include_list is None:
            components_include_list = mx_sdk.graalvm_components()

        excluded = _excluded_components()
        components_to_build = []

        libpoly_build_dependencies = []
        libpoly_has_entrypoints = []

        def is_excluded(component):
            return component.name in excluded or component.short_name in excluded

        def add_dependencies(dependencies, excludes=True):
            components = dependencies[:]
            while components:
                component = components.pop(0)
                if component not in components_to_build and not (excludes and is_excluded(component)):
                    components_to_build.append(component)
                    components.extend(component.direct_dependencies())
                    libpoly_build_dependencies.extend(component.polyglot_lib_build_dependencies)
                    if component.has_polyglot_lib_entrypoints:
                        libpoly_has_entrypoints.append(component.name)

        # Expand dependencies
        add_dependencies([mx_sdk.graalvm_component_by_name(name) for name in default_components], excludes=True)
        add_dependencies(components_include_list, excludes=True)

        # The polyglot library must be the last component that we register, since it depends on the other ones.
        # To avoid registering it twice (once when `stage1 == False` and once when `stage1 == True`), we check that
        # `libpoly` is not part of `registered_short_names`.
        # Even when the polyglot library is already registered, we still need to add its dependencies to the current
        # GraalVM (see call to `add_dependencies()`.
        registered_short_names = [c.short_name for c in mx_sdk_vm.graalvm_components()]
        if _with_polyglot_lib_project() and libpoly_has_entrypoints:
            if 'libpoly' in registered_short_names:
                libpolyglot_component = mx_sdk_vm.graalvm_component_by_name('libpoly')
            else:
                libpolyglot_component = mx_sdk_vm.GraalVmJreComponent(
                    suite=_suite,
                    name='Polyglot Library',
                    short_name='libpoly',
                    license_files=[],
                    third_party_license_files=[],
                    dir_name='polyglot',
                    library_configs=[mx_sdk_vm.LibraryConfig(
                        destination='<lib:polyglot>',
                        # We can ignore `component.polyglot_lib_jar_dependencies` because, when building a native image,
                        # the `GraalVmNativeImage` project has a build-time dependency to Stage1
                        jar_distributions=[],
                        build_args=[
                               '-Dgraalvm.libpolyglot=true',
                               '-Dorg.graalvm.polyglot.install_name_id=@rpath/jre/lib/polyglot/<lib:polyglot>',
                               '--tool:all',
                           ],
                        is_polyglot=True,
                        build_time=25,
                    )],
                )
                mx_sdk_vm.register_graalvm_component(libpolyglot_component)
            add_dependencies([libpolyglot_component])

            if libpoly_build_dependencies:
                mx.warn("Ignoring build dependency '{}' of '{}'. It should be already part of stage 1.".format(libpoly_build_dependencies, libpolyglot_component.name))

        # If we are going to build native launchers or libraries, i.e., if SubstrateVM is included,
        # we need native-image in stage1 to build them, even if the Native Image component is excluded.
        if stage1:
            if any(component.short_name == 'svmee' for component in components_to_build):
                add_dependencies([mx_sdk.graalvm_component_by_name('niee')], excludes=False)
            elif any(component.short_name == 'svm' for component in components_to_build):
                add_dependencies([mx_sdk.graalvm_component_by_name('ni')], excludes=False)

        if not any(component.short_name == 'svm' for component in components_to_build):
            # SVM is not included, remove GraalVMSvmMacros
            components_to_build = [component for component in components_to_build if not isinstance(component, mx_sdk.GraalVMSvmMacro)]

        mx.logv('Components: {}'.format([c.name for c in components_to_build]))
        _registered_graalvm_components[stage1] = components_to_build
    return _registered_graalvm_components[stage1]


def _get_component_type_base(c, apply_substitutions=False):
    if isinstance(c, mx_sdk.GraalVmLanguage):
        result = '<jre_base>/languages/'
    elif isinstance(c, mx_sdk.GraalVmTool):
        result = '<jre_base>/tools/'
    elif isinstance(c, mx_sdk.GraalVmJdkComponent):
        result = '<jdk_base>/lib/'
    elif isinstance(c, mx_sdk.GraalVmJreComponent):
        result = '<jre_base>/lib/'
    elif isinstance(c, mx_sdk.GraalVMSvmMacro):
        svm_component = get_component('svm', stage1=True)
        result = _get_component_type_base(svm_component, apply_substitutions=apply_substitutions) + svm_component.dir_name + '/macros/'
    elif isinstance(c, mx_sdk.GraalVmComponent):
        result = '<jdk_base>/'
    else:
        raise mx.abort("Unknown component type for {}: {}".format(c.name, type(c).__name__))
    if apply_substitutions:
        result = get_final_graalvm_distribution().path_substitutions.substitute(result)
    return result


def _get_jdk_base(jdk):
    jdk_dir = jdk.home
    if jdk_dir.endswith(os.path.sep):
        jdk_dir = jdk_dir[:-len(os.path.sep)]
    if jdk_dir.endswith('/Contents/Home'):
        jdk_base = 'Contents/Home'
        jdk_dir = jdk_dir[:-len('/Contents/Home')]
    else:
        jdk_base = '.'
    return jdk_dir, jdk_base


def _get_macros_dir():
    svm_component = get_component('svm', stage1=True)
    if not svm_component:
        return None
    return _get_component_type_base(svm_component) + svm_component.dir_name + '/macros'


def _get_main_component(components):
    """
    :type components: list[mx_sdk_vm.GraalVmComponent]
    :rtype: mx_sdk_vm.GraalVmComponent
    """
    assert len(components)
    main_component = min(components, key=lambda c: (c.priority, not has_svm_launcher(c)))  # we prefer components with low priority and native launchers (note that this is a `min` function)
    if any([comp for comp in components if comp != main_component and comp.priority == main_component.priority and has_svm_launcher(comp)]):
        raise mx.abort("""\
Cannot determine the main component between:
 - {}
More than one component has priority {} and native launchers.\
""".format('\n - '.join(['{} (has native launchers: {})'.format(c.name, has_svm_launcher(c)) for c in components]), main_component.priority))
    return main_component


_src_jdk = mx_sdk_vm.base_jdk()
_src_jdk_version = mx_sdk_vm.base_jdk_version()

# Example:
#   macOS:
#     _src_jdk_dir  = /Library/Java/JavaVirtualMachines/oraclejdk1.8.0_221-jvmci-19.2-b02
#     _src_jdk_base = Contents/Home
#   Others:
#     _src_jdk_dir  = $JAVA_HOME (e.g. /usr/lib/jvm/oraclejdk1.8.0_212-jvmci-19.2-b01)
#     _src_jdk_base = .
_src_jdk_dir, _src_jdk_base = _get_jdk_base(_src_jdk)

""":type: dict[str, (str, str)]"""
_parent_info_cache = {}

def _graalvm_maven_attributes(tag='graalvm'):
    """
    :type tag: str
    :rtype: dict[str, str]
    """
    return {'groupId': 'org.graalvm', 'tag': tag}

mx.add_argument('--no-jlinking', action='store_true', help='Do not jlink graal libraries in the generated JDK. Warning: The resulting VM will be HotSpot, not GraalVM.')

def _jlink_libraries():
    return not (mx.get_opts().no_jlinking or mx.env_var_to_bool('NO_JLINKING'))


class BaseGraalVmLayoutDistribution(_with_metaclass(ABCMeta, mx.LayoutDistribution)):
    def __init__(self, suite, name, deps, components, is_graalvm, exclLibs, platformDependent, theLicense, testDistribution,
                 add_jdk_base=False,
                 base_dir=None,
                 path=None,
                 stage1=False,
                 **kw_args): # pylint: disable=super-init-not-called
        self.components = components or registered_graalvm_components(stage1)
        self.skip_archive = stage1 # Do not build *.tar archive for stage1 distributions
        layout = {}
        src_jdk_base = _src_jdk_base if add_jdk_base else '.'
        assert src_jdk_base
        base_dir = base_dir or '.'

        if base_dir != '.':
            self.jdk_base = '/'.join([base_dir, src_jdk_base]) if src_jdk_base != '.' else base_dir
        else:
            self.jdk_base = src_jdk_base

        if _src_jdk_version == 8:
            self.jre_base = '/'.join((self.jdk_base, 'jre'))
        else:
            self.jre_base = self.jdk_base

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('jdk_base', lambda: self.jdk_base)
        path_substitutions.register_no_arg('jre_base', lambda: self.jre_base)

        string_substitutions = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        string_substitutions.register_no_arg('version', _suite.release_version)
        string_substitutions.register_no_arg('graalvm_os', get_graalvm_os())

        _layout_provenance = {}

        self._post_build_warnings = []

        self.jimage_jars = set()
        self.jimage_ignore_jars = set()
        if is_graalvm and _src_jdk_version >= 9:
            for component in self.components:
                if component.jlink:
                    self.jimage_jars.update(component.boot_jars + component.jvmci_parent_jars)
                    if isinstance(component, mx_sdk.GraalVmJvmciComponent):
                        self.jimage_jars.update(component.jvmci_jars)
                else:
                    self.jimage_ignore_jars.update(component.jar_distributions)
                    self.jimage_ignore_jars.update(component.builder_jar_distributions)
                    for launcher_config in component.launcher_configs:
                        if launcher_config.jar_distributions:
                            self.jimage_ignore_jars.update(launcher_config.jar_distributions)

        def _add(_layout, dest, src, component=None, with_sources=False):
            """
            :type _layout: dict[str, list[str] | str]
            :type dest: str
            :type src: list[str | dict] | str | dict
            """
            assert dest.startswith('<jdk_base>') or dest.startswith('<jre_base>') or base_dir == '.' or dest.startswith(base_dir), dest
            src = src if isinstance(src, list) else [src]
            if not src:
                return

            if not dest.endswith('/') and dest in _layout:
                if dest not in _layout_provenance or _layout_provenance[dest] is None:
                    mx.abort(
                        "Can not override '{dest}' which is part of the base GraalVM layout. ({component} tried to set {dest}<-{src})".format(
                            dest=dest, src=src, component=component.name if component else None))
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
            for _src in list(src):
                src_dict = mx.LayoutDistribution._as_source_dict(_src, name, dest)
                if src_dict['source_type'] == 'dependency' and src_dict['path'] is None:
                    if with_sources and _include_sources(src_dict['dependency']) and (_src_jdk_version == 8 or src_dict['dependency'] not in self.jimage_jars or not _jlink_libraries()):
                        src_src_dict = {
                            'source_type': 'dependency',
                            'dependency': src_dict['dependency'],
                            'path': '*.src.zip',
                            'optional': True,
                            'if_stripped': 'exclude',
                        }
                        src.append(src_src_dict)
            _layout.setdefault(dest, []).extend(src)

        def _patch_darwin_jdk():
            """
            :rtype: list[(str, source_dict)], list[str]
            """
            _incl_list = []
            _excl_list = []
            orig_info_plist = join(_src_jdk_dir, 'Contents', 'Info.plist')
            if exists(orig_info_plist):
                from mx import etreeParse
                root = etreeParse(orig_info_plist)
                found_el = False
                for el in root.iter():
                    if el.tag == 'key' and el.text == 'CFBundleName':
                        found_el = True
                    elif found_el:
                        assert el.tag == 'string'
                        graalvm_bundle_name = '{} {}'.format(self.base_name, self.vm_config_name.upper()) if self.vm_config_name is not None else name.lower()
                        graalvm_bundle_name += ' ' + graalvm_version()
                        el.text = graalvm_bundle_name
                        bio = io.BytesIO()
                        root.write(bio)  # When porting to Python 3, we can use root.write(StringIO(), encoding="unicode")
                        plist_src = {
                            'source_type': 'string',
                            'value': _decode(bio.getvalue()),
                            'ignore_value_subst': True
                        }
                        _incl_list.append((base_dir + '/Contents/Info.plist', plist_src))
                        _excl_list.append(orig_info_plist)
                        break
                if _src_jdk_version != 8:
                    # on JDK>8, we copy the jimage into Contents/Home so we need to separately
                    # add Contents/MacOS
                    _incl_list.append((base_dir + '/Contents/MacOS', {
                        'source_type': 'file',
                        'path': join(_src_jdk_dir, 'Contents', 'MacOS')
                    }))
            return _incl_list, _excl_list

        svm_component = get_component('svm', stage1=True)

        def _add_native_image_macro(image_config, component=None):
            # Add the macros if SubstrateVM is included, as images could be created later with an installable Native Image
            if svm_component and (component is None or has_component(component.short_name, stage1=False)):
                # create macro to build this launcher
                _macro_dir = _get_macros_dir() + '/' + GraalVmNativeProperties.macro_name(image_config) + '/'
                _project_name = GraalVmNativeProperties.project_name(image_config)
                _add(layout, _macro_dir, 'dependency:{}'.format(_project_name), component)  # native-image.properties is the main output
                # Add profiles
                for profile in _image_profile(GraalVmNativeProperties.canonical_image_name(image_config)):
                    _add(layout, _macro_dir, 'file:{}'.format(abspath(profile)))

        def _add_link(_dest, _target, _component=None, _dest_base_name=None):
            assert _dest.endswith('/')
            _linkname = relpath(path_substitutions.substitute(_target), start=path_substitutions.substitute(_dest[:-1]))
            dest_base_name = _dest_base_name or basename(_target)
            if _linkname != dest_base_name:
                if mx.is_windows():
                    if _target.endswith('.exe') or _target.endswith('.cmd'):
                        link_template_name = join(_suite.mxDir, 'vm', 'exe_link_template.cmd')
                        with open(link_template_name, 'r') as template:
                            _template_subst = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
                            _template_subst.register_no_arg('target', normpath(_linkname))
                            contents = _template_subst.substitute(template.read())
                        full_dest = _dest + dest_base_name[:-len('.exe')] + '.cmd'
                        _add(layout, full_dest, 'string:{}'.format(contents), _component)
                        return full_dest
                    else:
                        mx.abort("Cannot create link on windows for {}->{}".format(_dest, _target))
                else:
                    _add(layout, _dest + dest_base_name, 'link:{}'.format(_linkname), _component)
                    return _dest + dest_base_name

        def _find_escaping_links(root_dir):
            escaping_links = []
            for root, dirs, files in os.walk(root_dir, followlinks=True):
                for _file in dirs + files:
                    _abs_file = join(root, _file)
                    if islink(_abs_file):
                        _link_target = os.readlink(_abs_file)
                        if isabs(_link_target):
                            self._post_build_warnings.append("The base JDK contains an absolute symbolic link that has been excluded from the build: '{}' points to '{}".format(_abs_file, _link_target))
                            escaping_links.append(_abs_file)
                        else:
                            _resolved_link_target = join(dirname(_abs_file), _link_target)
                            if not normpath(join(root_dir, relpath(_resolved_link_target, root_dir))).startswith(root_dir):
                                self._post_build_warnings.append("The base JDK contains a symbolic link that escapes the root dir '{}' and has been excluded from the build: '{}' points to '{}'.".format(root_dir, _abs_file, _link_target))
                                escaping_links.append(_abs_file)
            return escaping_links

        if is_graalvm:
            if stage1:
                # 1. we do not want a GraalVM to be used as base-JDK
                # 2. we don't need to check if the base JDK is JVMCI-enabled, since JVMCIVersionCheck takes care of that when the GraalVM compiler is a registered component
                check_versions(_src_jdk, graalvm_version_regex=graalvm_version_regex, expect_graalvm=False, check_jvmci=False)

            # Add base JDK
            exclude_base = _src_jdk_dir
            exclusion_list = []
            if src_jdk_base != '.':
                exclude_base = join(exclude_base, src_jdk_base)
            if mx.get_os() == 'darwin':
                hsdis = '/jre/lib/' + mx.add_lib_suffix('hsdis-' + mx.get_arch())
                incl_list, excl_list = _patch_darwin_jdk()
                for d, s in incl_list:
                    _add(layout, d, s)
                exclusion_list += excl_list
            else:
                hsdis = '/jre/lib/' + mx.get_arch() + '/' + mx.add_lib_suffix('hsdis-' + mx.get_arch())
            if _src_jdk_version == 8:
                if mx.get_os() == 'darwin':
                    jvm_cfg = '/lib/jvm.cfg'
                else:
                    jvm_cfg = '/lib/' + mx.get_arch() + '/jvm.cfg'
                _escaping_links = _find_escaping_links(_src_jdk_dir)
                _add(layout, base_dir, {
                    'source_type': 'file',
                    'path': _src_jdk_dir,
                    'exclude': exclusion_list + sorted(_escaping_links) + [
                        exclude_base + '/COPYRIGHT',
                        exclude_base + '/LICENSE',
                        exclude_base + '/README.html',
                        exclude_base + '/THIRDPARTYLICENSEREADME.txt',
                        exclude_base + '/THIRDPARTYLICENSEREADME-JAVAFX.txt',
                        exclude_base + '/THIRD_PARTY_README',
                        exclude_base + '/release',
                        exclude_base + '/bin/jvisualvm',
                        exclude_base + '/bin/jvisualvm.exe',
                        exclude_base + '/lib/visualvm',
                        exclude_base + hsdis,
                        exclude_base + '/jre' + jvm_cfg,
                    ] + ([
                        exclude_base + '/bin/jmc',
                        exclude_base + '/lib/missioncontrol',
                    ] if mx.get_os() == 'darwin' else [])
                })
                if exists(join(exclude_base, "THIRD_PARTY_README")):
                    _add(layout, "THIRD_PARTY_README_JDK" if base_dir == '.' else base_dir + '/THIRD_PARTY_README_JDK', "file:" + exclude_base + "/THIRD_PARTY_README")
                _add(layout, "<jre_base>" + jvm_cfg, "string:" + _get_jvm_cfg_contents())
            else:
                # TODO(GR-8329): add exclusions
                _add(layout, self.jdk_base + '/', {
                    'source_type': 'dependency',
                    'dependency': 'graalvm-jimage',
                    'path': '*',
                    'exclude': [
                        'lib/jvm.cfg'
                    ],
                })
                _add(layout, "<jre_base>/lib/jvm.cfg", "string:" + _get_jvm_cfg_contents())

            # Add vm.properties
            vm_name = graalvm_vm_name(self, _src_jdk)

            if mx.get_os() == 'windows':
                _add(layout, "<jre_base>/bin/server/vm.properties", "string:name=" + vm_name)
            elif mx.get_os() == 'darwin' or _src_jdk_version >= 9:
                # on macOS and jdk >= 9, the <arch> directory is not used
                _add(layout, "<jre_base>/lib/server/vm.properties", "string:name=" + vm_name)
            else:
                _add(layout, "<jre_base>/lib/<arch>/server/vm.properties", "string:name=" + vm_name)

            if _src_jdk_version == 8:
                # Generate and add the 'release' file.
                # On JDK9+ this is done in GraalVmJImageBuildTask because it depends on the result of jlink.
                _sorted_suites = sorted(mx.suites(), key=lambda s: s.name)
                _metadata = self._get_metadata(_sorted_suites, join(exclude_base, 'release'))
                _add(layout, "<jdk_base>/release", "string:{}".format(_metadata))
                if any(comp.jvmci_parent_jars for comp in registered_graalvm_components(stage1)):
                    _add(layout, '<jre_base>/lib/jvmci/parentClassLoader.classpath', 'dependency:{}'.format(GraalVmJvmciParentClasspath.project_name()))

        # Add the rest of the GraalVM

        component_suites = {}
        installables = {}
        has_graal_compiler = False
        _macros_dir = _get_macros_dir()
        _libpolyglot_component = mx_sdk_vm.graalvm_component_by_name('libpoly', fatalIfMissing=False)
        assert _libpolyglot_component is None or len(_libpolyglot_component.library_configs) == 1
        _libpolyglot_macro_dir = (_macros_dir + '/' + GraalVmNativeProperties.macro_name(_libpolyglot_component.library_configs[0]) + '/') if _macros_dir is not None and _libpolyglot_component is not None else None

        for _component in sorted(self.components, key=lambda c: c.name):
            mx.logv('Adding {} ({}) to the {} {}'.format(_component.name, _component.__class__.__name__, name, self.__class__.__name__))
            _component_type_base = _get_component_type_base(_component)
            if isinstance(_component, (mx_sdk.GraalVmJreComponent, mx_sdk.GraalVmJdkComponent)):
                if mx.get_os() == 'windows':
                    _component_jvmlib_base = _component_type_base[:-len('lib/')] + 'bin/'
                else:
                    _component_jvmlib_base = _component_type_base
            elif mx.get_os() == 'windows':
                _component_jvmlib_base = '<jre_base>/bin/'
            else:
                _component_jvmlib_base = '<jre_base>/lib/'
            if _src_jdk_version < 9 and mx.get_os() not in ['darwin', 'windows']:
                _jvm_library_dest = _component_jvmlib_base + mx.get_arch() + '/'
            else:
                _jvm_library_dest = _component_jvmlib_base

            if _component.dir_name:
                _component_base = _component_type_base + _component.dir_name + '/'
            else:
                _component_base = _component_type_base

            if _src_jdk_version == 8:
                _add(layout, '<jre_base>/lib/boot/', ['dependency:' + d for d in _component.boot_jars], _component, with_sources=True)
            elif not _jlink_libraries():
                _add(layout, '<jre_base>/lib/jvmci/', ['dependency:' + d for d in _component.boot_jars], _component, with_sources=True)
            _add(layout, _component_base, ['dependency:' + d for d in _component.jar_distributions + _component.jvmci_parent_jars], _component, with_sources=True)
            _add(layout, _component_base + 'builder/', ['dependency:' + d for d in _component.builder_jar_distributions], _component, with_sources=True)
            _add(layout, _component_base, [{
                'source_type': 'extracted-dependency',
                'dependency': d,
                'exclude': _component.license_files if _no_licenses() else [],
                'path': None,
            } for d in _component.support_distributions], _component)
            _add(layout, '<jdk_base>/include/', [{
                'source_type': 'extracted-dependency',
                'dependency': d,
                'exclude': [],
                'path': None,
            } for d in _component.support_headers_distributions], _component)
            _add(layout, _jvm_library_dest, [{
                'source_type': 'extracted-dependency',
                'dependency': d,
                'exclude': [],
                'path': None,
            } for d in _component.support_libraries_distributions], _component)
            if isinstance(_component, mx_sdk.GraalVmJvmciComponent) and (_src_jdk_version == 8 or not _jlink_libraries()):
                _add(layout, '<jre_base>/lib/jvmci/', ['dependency:' + d for d in _component.jvmci_jars], _component, with_sources=True)

            if isinstance(_component, mx_sdk.GraalVmJdkComponent):
                _jdk_jre_bin = '<jdk_base>/bin/'
            else:
                _jdk_jre_bin = '<jre_base>/bin/'

            _licenses = _component.third_party_license_files
            if not _no_licenses():
                _licenses = _licenses + _component.license_files
            for _license in _licenses:
                if mx.is_windows() or isinstance(self, mx.AbstractJARDistribution):
                    if _component_base == '<jdk_base>/':
                        pass  # already in place from the support dist
                    elif len(_component.support_distributions) == 1:
                        _support = _component.support_distributions[0]
                        _add(layout, '<jdk_base>/', 'extracted-dependency:{}/{}'.format(_support, _license), _component)
                    else:
                        mx.warn("Can not add license: " + _license)
                else:
                    _add_link('<jdk_base>/', _component_base + _license, _component)

            _jre_bin_names = []
            graalvm_dists = set()

            for _launcher_config in sorted(_get_launcher_configs(_component), key=lambda c: c.destination):
                graalvm_dists.update(_launcher_config.jar_distributions)
                self.jimage_ignore_jars.update(_launcher_config.jar_distributions)
                _launcher_dest = _component_base + GraalVmLauncher.get_launcher_destination(_launcher_config, stage1)
                # add `LauncherConfig.destination` to the layout
                launcher_project = GraalVmLauncher.launcher_project_name(_launcher_config, stage1)
                _add(layout, _launcher_dest, 'dependency:' + launcher_project, _component)
                if _debug_images() and GraalVmLauncher.is_launcher_native(_launcher_config, stage1) and _get_svm_support().is_debug_supported():
                    _add(layout, dirname(_launcher_dest) + '/', 'dependency:' + launcher_project + '/*.debug', _component)
                    if _include_sources(launcher_project):
                        _add(layout, dirname(_launcher_dest) + '/', 'dependency:' + launcher_project + '/sources', _component)
                # add links from jre/bin to launcher
                if _launcher_config.default_symlinks:
                    _link_path = _add_link(_jdk_jre_bin, _launcher_dest, _component)
                    _jre_bin_names.append(basename(_link_path))
                for _component_link in _launcher_config.links:
                    _link_dest = _component_base + _component_link
                    # add links `LauncherConfig.links` -> `LauncherConfig.destination`
                    _link_dest_dir, _link_dest_base_name = os.path.split(_link_dest)
                    _add_link(_link_dest_dir + '/', _launcher_dest, _component, _dest_base_name=_link_dest_base_name)
                    # add links from jre/bin to component link
                    if _launcher_config.default_symlinks:
                        _link_path = _add_link(_jdk_jre_bin, _link_dest, _component)
                        _jre_bin_names.append(basename(_link_path))
                _add_native_image_macro(_launcher_config, _component)
                if isinstance(_launcher_config, mx_sdk.LanguageLauncherConfig):
                    _add(layout, _component_base, 'dependency:{}/polyglot.config'.format(launcher_project), _component)
            for _library_config in sorted(_get_library_configs(_component), key=lambda c: c.destination):
                graalvm_dists.update(_library_config.jar_distributions)
                self.jimage_ignore_jars.update(_library_config.jar_distributions)
                if _library_config.jvm_library:
                    assert isinstance(_component, (mx_sdk.GraalVmJdkComponent, mx_sdk.GraalVmJreComponent))
                    _svm_library_home = _jvm_library_dest
                else:
                    _svm_library_home = _component_base
                _svm_library_dest = _svm_library_home + _library_config.destination
                if not stage1 and _get_svm_support().is_supported():
                    _source_type = 'skip' if _skip_libraries(_library_config) else 'dependency'
                    _library_project_name = GraalVmNativeImage.project_name(_library_config)
                    # add `LibraryConfig.destination` and the generated header files to the layout
                    _add(layout, _svm_library_dest, _source_type + ':' + _library_project_name, _component)
                    _add(layout, _svm_library_home, _source_type + ':' + _library_project_name + '/*.h', _component)
                _add_native_image_macro(_library_config, _component)

            graalvm_dists.update(_component.polyglot_lib_jar_dependencies)
            if _libpolyglot_macro_dir is not None and GraalVmLibPolyglotNativeProperties.needs_lib_polyglot_native_properties(_component):
                _add(layout, _libpolyglot_macro_dir, 'dependency:' + _libpolyglot_macro_dist_name(_component))

            if _src_jdk_version == 8 or not _jlink_libraries():
                graalvm_dists.difference_update(_component.boot_jars)
            graalvm_dists.difference_update(_component.jar_distributions)
            graalvm_dists.difference_update(_component.jvmci_parent_jars)
            graalvm_dists.difference_update(_component.builder_jar_distributions)
            _add(layout, '<jre_base>/lib/graalvm/', ['dependency:' + d for d in sorted(graalvm_dists)], _component, with_sources=True)

            for _provided_executable in _component.provided_executables:
                if isinstance(_provided_executable, tuple):
                    # copied executable
                    _src_dist, _executable = _provided_executable
                    _executable = mx_subst.results_substitutions.substitute(_executable)
                    _add(layout, _jdk_jre_bin, 'extracted-dependency:{}/{}'.format(_src_dist, _executable), _component)
                else:
                    # linked executable
                    _link_dest = _component_base + mx_subst.results_substitutions.substitute(_provided_executable)
                    _link_path = _add_link(_jdk_jre_bin, _link_dest, _component)
                    _jre_bin_names.append(basename(_link_path))

            if _src_jdk_version == 8 and 'jre' in _jdk_jre_bin:
                # Add jdk to jre links
                for _name in _jre_bin_names:
                    _add_link('<jdk_base>/bin/', '<jre_base>/bin/' + _name, _component)

            if isinstance(_component, mx_sdk.GraalVmJvmciComponent) and _component.graal_compiler:
                has_graal_compiler = True

            if isinstance(_component, mx_sdk.GraalVmLanguage) and not is_graalvm:
                # add language-specific release file
                component_suites.setdefault(_component_base, []).append(_component.suite)

            if _component.installable and not _disable_installable(_component):
                installables.setdefault(_component.installable_id, []).append(_component)

        installer = get_component('gu', stage1=stage1)
        if installer:
            # Register pre-installed components
            components_dir = _get_component_type_base(installer) + installer.dir_name + '/components/'
            for installable_components in installables.values():
                manifest_str = _gen_gu_manifest(installable_components, _format_properties, bundled=True)
                main_component = _get_main_component(installable_components)
                _add(layout, components_dir + 'org.graalvm.' + main_component.installable_id + '.component', "string:" + manifest_str)

        for _base, _suites in component_suites.items():
            _metadata = self._get_metadata(_suites)
            _add(layout, _base + 'release', "string:{}".format(_metadata))

        if has_graal_compiler:
            _add(layout, '<jre_base>/lib/jvmci/compiler-name', 'string:graal')

        if "archive_factory" not in kw_args and self.skip_archive:
            kw_args["archive_factory"] = mx.NullArchiver

        super(BaseGraalVmLayoutDistribution, self).__init__(suite, name, deps, layout, path, platformDependent,
                                                            theLicense, exclLibs, path_substitutions=path_substitutions,
                                                            string_substitutions=string_substitutions,
                                                            testDistribution=testDistribution, **kw_args)
        self.reset_user_group = True
        mx.logvv("'{}' has layout:\n{}".format(self.name, pprint.pformat(self.layout)))

    def getBuildTask(self, args):
        return BaseGraalVmLayoutDistributionTask(args, self)

    def needsUpdate(self, newestInput):
        if self.skip_archive:
            # For stage1 distributions we do not have an archive file, therefore we must compare against
            # output directory
            output = self.get_output()
            if exists(output):
                ts = mx.TimeStampFile(output)
                if newestInput and ts.isOlderThan(newestInput):
                    return "{} is older than {}".format(ts, newestInput)
                else:
                    return None
            else:
                return "{} does not exist".format(output)
        else:
            return super(BaseGraalVmLayoutDistribution, self).needsUpdate(newestInput)

    @staticmethod
    def _get_metadata(suites, parent_release_file=None):
        """
        :type suites: list[mx.Suite]
        :type parent_release_file: str | None
        :rtype: str
        """
        def quote(string):
            return '"{}"'.format(string)

        _commit_info = {}
        for _s in suites:
            if _s.vc:
                if _s.vc_dir not in _parent_info_cache:
                    _parent_info_cache.setdefault(_s.vc_dir, (_s.vc.parent_info(_s.vc_dir), _s.vc.parent(_s.vc_dir)))
                _info, _parent_rev = _parent_info_cache[_s.vc_dir]
                _commit_info[_s.name] = {
                    "commit.rev": _parent_rev,
                    "commit.committer": _info['committer'] if _s.vc.kind != 'binary' else 'unknown',
                    "commit.committer-ts": _info['committer-ts'],
                }
        _metadata_dict = OrderedDict()
        if parent_release_file is not None and exists(parent_release_file):
            with open(parent_release_file, 'r') as f:
                for line in f:
                    if line.strip() != '':  # on Windows, the release file might have extra line terminators
                        assert line.count('=') > 0, "The release file of the base JDK ('{}') contains a line without the '=' sign: '{}'".format(parent_release_file, line)
                        k, v = line.strip().split('=', 1)
                        _metadata_dict[k] = v

        _metadata_dict.setdefault('JAVA_VERSION', quote(_src_jdk.version))
        _metadata_dict.setdefault('OS_NAME', quote(get_graalvm_os()))
        _metadata_dict.setdefault('OS_ARCH', quote(mx.get_arch()))

        _metadata_dict['GRAALVM_VERSION'] = quote(_suite.release_version())
        _source = _metadata_dict.get('SOURCE') or ''
        if _source:
            if len(_source) > 1 and _source[0] == '"' and _source[-1] == '"':
                _source = _source[1:-1]
            _source += ' '
        _source += ' '.join(['{}:{}'.format(_s.name, _s.version()) for _s in suites])
        _metadata_dict['SOURCE'] = quote(_source)
        _metadata_dict['COMMIT_INFO'] = json.dumps(_commit_info, sort_keys=True)  # unquoted to simplify JSON parsing
        if _suite.is_release():
            catalog = _release_catalog()
        else:
            snapshot_catalog = _snapshot_catalog()
            if snapshot_catalog and _suite.vc:
                catalog = "{}/{}".format(snapshot_catalog, _suite.vc.parent(_suite.vc_dir))
            else:
                catalog = None
        if catalog:
            _metadata_dict['component_catalog'] = quote(catalog)

        return '\n'.join(['{}={}'.format(k, v) for k, v in _metadata_dict.items()])


class BaseGraalVmLayoutDistributionTask(mx.LayoutArchiveTask):
    def build(self):
        assert isinstance(self.subject, BaseGraalVmLayoutDistribution)
        super(BaseGraalVmLayoutDistributionTask, self).build()
        for warning in self.subject._post_build_warnings:
            mx.warn(warning, context=self)


if mx.is_windows():
    LayoutSuper = mx.LayoutZIPDistribution
else:
    LayoutSuper = mx.LayoutTARDistribution


class GraalVmLayoutDistribution(BaseGraalVmLayoutDistribution, LayoutSuper):  # pylint: disable=R0901
    def __init__(self, base_name, theLicense=None, stage1=False, components=None, **kw_args):
        self.base_name = base_name
        name, base_dir, self.vm_config_name = _get_graalvm_configuration(base_name, components=components, stage1=stage1)

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
            path=None,
            stage1=stage1,
            **kw_args)

    def extra_suite_revisions_data(self):
        base_jdk_info = _base_jdk_info()
        if self == get_final_graalvm_distribution() and base_jdk_info:
            assert len(base_jdk_info) == 2
            yield "basejdk", {
                "name": base_jdk_info[0],
                "version": base_jdk_info[1]
            }

    def getBuildTask(self, args):
        return GraalVmLayoutDistributionTask(args, self, 'latest_graalvm', 'latest_graalvm_home')


def _components_set(components=None, stage1=False):
    components = components or registered_graalvm_components(stage1)
    components_set = set([c.short_name for c in components])
    if stage1:
        components_set.add('stage1')
    elif 'svm' in components_set:
        # forced bash launchers and skipped libraries only make a difference if we have svm
        for component in components:
            for launcher_config in _get_launcher_configs(component):
                if _force_bash_launchers(launcher_config):
                    components_set.add('b' + remove_exe_suffix(basename(launcher_config.destination)))
            for library_config in _get_library_configs(component):
                if _skip_libraries(library_config):
                    components_set.add('s' + remove_lib_prefix_suffix(basename(library_config.destination)))
    if _no_licenses():
        components_set.add('nolic')
    return components_set


_graal_vm_configs_cache = {}


def _get_graalvm_configuration(base_name, components=None, stage1=False):
    key = base_name, stage1
    if key not in _graal_vm_configs_cache:
        components_set = _components_set(components, stage1)

        # Use custom distribution name and base dir for registered vm configurations
        vm_dist_name = None
        vm_config_name = None
        for dist_name, config_name, config_components, _, _ in mx_sdk_vm._vm_configs:
            config_components_set = set(config_components)
            if components_set == config_components_set:
                vm_dist_name = dist_name.replace('-', '_')
                vm_config_name = config_name.replace('-', '_')
                break

        if vm_dist_name:
            base_dir = '{base_name}_{vm_dist_name}_java{jdk_version}'.format(base_name=base_name, vm_dist_name=vm_dist_name, jdk_version=_src_jdk_version)
            name = base_dir
        else:
            components_sorted_set = sorted(components_set)
            if mx.get_opts().verbose:
                mx.logv("No dist name for {}".format(components_sorted_set))
            m = hashlib.sha1()
            for component in components_sorted_set:
                m.update(_encode(component))
            short_sha1_digest = m.hexdigest()[:10]  # to keep paths short
            base_dir = '{base_name}_{hash}_java{jdk_version}'.format(base_name=base_name, hash=short_sha1_digest, jdk_version=_src_jdk_version)
            name = '{base_dir}{stage_suffix}'.format(base_dir=base_dir, stage_suffix='_stage1' if stage1 else '')
        name = name.upper()
        base_dir = base_dir.lower().replace('_', '-') + '-' + _suite.release_version()

        _graal_vm_configs_cache[key] = name, base_dir, vm_config_name
    return _graal_vm_configs_cache[key]


class GraalVmLayoutDistributionTask(BaseGraalVmLayoutDistributionTask):
    def __init__(self, args, dist, root_link_name, home_link_name):
        self._root_link_path = join(_suite.dir, root_link_name)
        self._home_link_path = join(_suite.dir, home_link_name)
        super(GraalVmLayoutDistributionTask, self).__init__(args, dist)

    def _add_link(self):
        if mx.get_os() == 'windows':
            mx.warn('Skip adding symlink to ' + self._home_link_target() + ' (Platform Windows)')
            return
        self._rm_link()
        os.symlink(self._root_link_target(), self._root_link_path)
        os.symlink(self._home_link_target(), self._home_link_path)

    def _root_link_target(self):
        return relpath(self.subject.output, _suite.dir)

    def _home_link_target(self):
        return relpath(join(self.subject.output, self.subject.jdk_base), _suite.dir)

    def _rm_link(self):
        if mx.get_os() == 'windows':
            return
        for l in [self._root_link_path, self._home_link_path]:
            if os.path.lexists(l):
                os.unlink(l)

    def needsBuild(self, newestInput):
        sup = super(GraalVmLayoutDistributionTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        if mx.get_os() != 'windows' and self.subject == get_final_graalvm_distribution():
            for link_path, link_target in [(self._root_link_path, self._root_link_target()), (self._home_link_path, self._home_link_target())]:
                if not os.path.lexists(link_path):
                    return True, '{} does not exist'.format(link_path)
                link_file = mx.TimeStampFile(link_path, False)
                if link_file.isOlderThan(self.subject.output):
                    return True, '{} is older than {}'.format(link_file, newestInput)
                if link_target != os.readlink(link_path):
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


class DebuginfoDistribution(mx.LayoutTARDistribution):  # pylint: disable=too-many-ancestors
    def __init__(self, subject_distribution, theLicense=None, **kw_args):
        super(DebuginfoDistribution, self).__init__(_suite,
                                                    name=subject_distribution.name + '_DEBUGINFO',
                                                    deps=[subject_distribution.name],
                                                    layout={},
                                                    path=None,
                                                    platformDependent=subject_distribution.platformDependent,
                                                    theLicense=theLicense, **kw_args)
        self._layout_initialized = False
        self.maven = subject_distribution.maven
        self.subject_distribution = subject_distribution

    def _walk_layout(self):
        if not self._layout_initialized:
            root_contents = []
            self.layout = {
                './': root_contents
            }
            for dep_name in getattr(self.subject_distribution, 'buildDependencies', []):
                dep = mx.dependency(dep_name)
                if isinstance(dep, mx.JARDistribution):
                    if dep.is_stripped():
                        root_contents += ['dependency:{}:{}/*.map'.format(dep.suite.name, dep.name)]
                elif isinstance(dep, GraalVmNativeImage):
                    if dep.debug_file():
                        source_type = 'skip' if isinstance(dep.native_image_config, mx_sdk.LibraryConfig) and _skip_libraries(dep.native_image_config) else 'dependency'
                        root_contents += [source_type + ':{}:{}/*.debug'.format(dep.suite.name, dep.name)]
                        self.layout[dep.native_image_name + '-sources/'] = source_type + ':{}:{}/sources'.format(dep.suite.name, dep.name)
            self._layout_initialized = True
        return super(DebuginfoDistribution, self)._walk_layout()


def get_graalvm_os():
    os = mx.get_os()
    if os == 'darwin':
        return 'macos'
    return os


def remove_exe_suffix(name, require_suffix=True):
    if not _exe_suffix:
        return name
    if name.endswith(_exe_suffix):
        return name[:-len(_exe_suffix)]
    elif require_suffix:
        raise mx.abort("Missing exe suffix: " + name)
    return name


def remove_lib_prefix_suffix(libname, require_suffix_prefix=True):
    result = libname
    if _lib_suffix:
        if result.endswith(_lib_suffix):
            result = result[:-len(_lib_suffix)]
        elif require_suffix_prefix:
            raise mx.abort("Missing lib suffix: " + libname)
    if _lib_prefix:
        if result.startswith(_lib_prefix):
            result = result[len(_lib_prefix):]
        elif require_suffix_prefix:
            raise mx.abort("Missing lib prefix: " + libname)
    return result


class SvmSupport(object):
    def __init__(self):
        self._svm_supported = has_component('svm', stage1=True)
        self._debug_supported = self._svm_supported and has_component('svmee', stage1=True)
        self._pgo_supported = self._svm_supported and has_component('svmee', stage1=True)

    def is_supported(self):
        return self._svm_supported

    def native_image(self, build_args, output_file, allow_server=False, nonZeroIsFatal=True, out=None, err=None):
        assert self._svm_supported
        stage1 = get_stage1_graalvm_distribution()
        native_image_project_name = GraalVmLauncher.launcher_project_name(mx_sdk.LauncherConfig(mx.exe_suffix('native-image'), [], "", []), stage1=True)
        native_image_bin = join(stage1.output, stage1.find_single_source_location('dependency:' + native_image_project_name))
        native_image_command = [native_image_bin] + build_args
        output_directory = dirname(output_file)
        native_image_command += [
            '-H:Path=' + output_directory or ".",
        ]
        return mx.run(native_image_command, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)

    def is_debug_supported(self):
        return self._debug_supported

    def is_pgo_supported(self):
        return self._debug_supported


def _get_svm_support():
    return SvmSupport()


class GraalVmProject(mx.Project):
    def __init__(self, component, name, deps, **kw_args):
        """
        :type component: mx_sdk.GraalVmComponent | None
        :type name:  str
        """
        self.component = component
        super(GraalVmProject, self).__init__(_suite, name, subDir=None, srcDirs=[], deps=deps, workingSets=None, d=_suite.dir, theLicense=None, **kw_args)

    def get_origin_suite(self):
        if self.component:
            return self.component.suite
        return self.suite


class GraalVmNativeProperties(GraalVmProject):
    def __init__(self, component, image_config, **kw_args):
        """
        :type component: mx_sdk.GraalVmComponent | None
        :type image_config: mx_sdk.AbstractNativeImageConfig
        """
        self.image_config = image_config
        # With Java > 8 there are cases where image_config.get_add_exports is getting called in
        # mx_sdk_vm_impl.NativePropertiesBuildTask.contents. This only works after the jar_distributions
        # are made into proper modules. Therefore they have to be specified as dependencies here.
        deps = [] if _src_jdk_version == 8 else list(image_config.jar_distributions)
        super(GraalVmNativeProperties, self).__init__(component, GraalVmNativeProperties.project_name(image_config), deps=deps, **kw_args)

    @staticmethod
    def project_name(image_config):
        """
        :type image_config: mx_sdk.AbstractNativeImageConfig
        """
        return GraalVmNativeProperties.macro_name(image_config) + "_native-image.properties"

    @staticmethod
    def canonical_image_name(image_config):
        canonical_name = basename(image_config.destination)
        if isinstance(image_config, mx_sdk.LauncherConfig):
            canonical_name = remove_exe_suffix(canonical_name)
        elif isinstance(image_config, mx_sdk.LibraryConfig):
            canonical_name = remove_lib_prefix_suffix(canonical_name)
        return canonical_name

    @staticmethod
    def macro_name(image_config):
        macro_name = basename(image_config.destination)
        if isinstance(image_config, mx_sdk.LauncherConfig):
            macro_name = remove_exe_suffix(macro_name) + '-launcher'
        elif isinstance(image_config, mx_sdk.LibraryConfig):
            macro_name = remove_lib_prefix_suffix(macro_name) + '-library'
        return macro_name

    def getArchivableResults(self, use_relpath=True, single=False):
        out = self.properties_output_file()
        yield out, basename(out)

    def properties_output_file(self):
        return join(self.get_output_base(), "native-image.properties", GraalVmNativeProperties.macro_name(self.image_config), "native-image.properties")

    def getBuildTask(self, args):
        return NativePropertiesBuildTask(self, args)


class NativePropertiesBuildTask(mx.ProjectBuildTask):

    implicit_excludes = ['substratevm:LIBRARY_SUPPORT']

    def __init__(self, subject, args):
        """
        :type subject: GraalVmNativeProperties
        """
        super(NativePropertiesBuildTask, self).__init__(args, 1, subject)
        self._contents = None
        self._location_classpath = None

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.properties_output_file())

    def __str__(self):
        graalvm_dist = get_final_graalvm_distribution()
        gralvm_location = graalvm_dist.find_single_source_location('dependency:' + self.subject.name)
        return "Creating native-image.properties for " + basename(dirname(gralvm_location))

    def _get_location_classpath(self):
        if self._location_classpath is None:
            graalvm_dist = get_final_graalvm_distribution()
            image_config = self.subject.image_config
            graalvm_location = dirname(graalvm_dist.find_single_source_location('dependency:' + self.subject.name))
            self._location_classpath = NativePropertiesBuildTask.get_launcher_classpath(graalvm_dist, graalvm_location, image_config, self.subject.component, exclude_implicit=True)
        return self._location_classpath

    @staticmethod
    def get_launcher_classpath(graalvm_dist, start, image_config, component, exclude_implicit=False):
        with_substratevm = 'substratevm' in [s.name for s in mx.suites()]
        exclude_names = NativePropertiesBuildTask.implicit_excludes if with_substratevm and exclude_implicit else None
        location_cp = graalvm_home_relative_classpath(image_config.jar_distributions, start, graal_vm=graalvm_dist, exclude_names=exclude_names)
        location_classpath = location_cp.split(os.pathsep) if location_cp else []
        if image_config.dir_jars:
            if not component:
                raise mx.abort("dir_jars=True can only be used on launchers associated with a component")
            component_dir = _get_component_type_base(component, apply_substitutions=True)
            dir_name = component.dir_name
            if dir_name:
                component_dir = component_dir + dir_name + os.sep
            component_dir_rel = relpath(component_dir, start)
            if not component_dir_rel.endswith(os.sep):
                component_dir_rel += os.sep
            location_classpath.append(component_dir_rel + '*')
        return location_classpath

    def contents(self):
        if self._contents is None:
            image_config = self.subject.image_config
            build_args = [
                '--no-fallback',
                '--initialize-at-build-time=org,com,net,jdk,javax,java,sun,apple',
                '-H:+AssertInitializationSpecifiedForAllClasses',
                '-H:+EnforceMaxRuntimeCompileMethods',
                '-Dorg.graalvm.version={}'.format(_suite.release_version()),
            ]
            if _debug_images():
                build_args += ['-ea', '-H:-AOTInline', '-H:+PreserveFramePointer', '-H:-DeleteLocalSymbols']
            if _get_svm_support().is_debug_supported():
                build_args += ['-g']

            graalvm_dist = get_final_graalvm_distribution()
            graalvm_location = dirname(graalvm_dist.find_single_source_location('dependency:' + self.subject.name))
            location_classpath = self._get_location_classpath()
            graalvm_home = _get_graalvm_archive_path("")

            if isinstance(image_config, mx_sdk.LibraryConfig):
                suffix = _lib_suffix
                build_args.append('--shared')
                project_name_f = GraalVmNativeImage.project_name
            elif isinstance(image_config, mx_sdk.LauncherConfig):
                suffix = _exe_suffix
                project_name_f = GraalVmLauncher.launcher_project_name
            else:
                raise mx.abort("Unsupported image config type: " + str(type(image_config)))

            if isinstance(image_config, mx_sdk.LanguageLauncherConfig):
                build_args += ['--language:' + image_config.language, '--tool:all']

            source_type = 'skip' if isinstance(image_config, mx_sdk.LibraryConfig) and _skip_libraries(image_config) else 'dependency'
            graalvm_image_destination = graalvm_dist.find_single_source_location(source_type + ':' + project_name_f(image_config))

            if image_config.home_finder:
                build_args += [
                    '--features=org.graalvm.home.HomeFinderFeature',
                    '-Dorg.graalvm.launcher.relative.home=' + relpath(graalvm_image_destination, graalvm_home),
                ]

            if isinstance(image_config, mx_sdk.LauncherConfig):
                if image_config.is_sdk_launcher:
                    launcher_classpath = NativePropertiesBuildTask.get_launcher_classpath(graalvm_dist, graalvm_home, image_config, self.subject.component, exclude_implicit=True)
                    build_args += [
                        '-H:-ParseRuntimeOptions',
                        '-Dorg.graalvm.launcher.classpath=' + os.pathsep.join(launcher_classpath),
                    ]

                build_args += [
                    '--install-exit-handlers',
                    '-H:+AllowVMInspection'
                ]
                if has_component('svmee', stage1=True):
                    build_args += [
                        '-R:-UsePerfData'
                    ]

                for language, path in sorted(image_config.relative_home_paths.items()):
                    build_args += ['-Dorg.graalvm.launcher.relative.' + language + '.home=' + path]

            build_args += [mx_subst.string_substitutions.substitute(arg) for arg in image_config.build_args]

            name = basename(image_config.destination)
            if suffix:
                name = name[:-len(suffix)]
            canonical_name = GraalVmNativeProperties.canonical_image_name(image_config)
            build_args += _extra_image_builder_args(canonical_name)
            profiles = _image_profile(canonical_name)
            if profiles:
                if not _get_svm_support().is_pgo_supported():
                    raise mx.abort("Image profiles can not be used if PGO is not supported.")
                basenames = [basename(p) for p in profiles]
                if len(set(basenames)) != len(profiles):
                    raise mx.abort("Profiles for an image must have unique filenames.\nThis is not the case for {}: {}.".format(canonical_name, profiles))
                build_args += ['--pgo=' + ','.join(('${.}/' + n for n in basenames))]

            build_with_module_path = image_config.use_modules == 'image'
            if build_with_module_path:
                export_deps_to_exclude = [str(dep) for dep in mx.classpath_entries(['substratevm:LIBRARY_SUPPORT'])] + list(_known_missing_jars)
                build_args += image_config.get_add_exports(set(export_deps_to_exclude))

            requires = [arg[2:] for arg in build_args if arg.startswith('--language:') or arg.startswith('--tool:') or arg.startswith('--macro:')]
            build_args = [arg for arg in build_args if not (arg.startswith('--language:') or arg.startswith('--tool:') or arg.startswith('--macro:'))]

            if any((' ' in arg for arg in build_args)):
                mx.abort("Unsupported space in launcher build argument: {} in config for {}".format(image_config.build_args, image_config.destination))

            self._contents = u""

            def _write_ln(s):
                self._contents += s + u"\n"

            myself = basename(__file__)
            if myself.endswith('.pyc'):
                myself = myself[:-1]
            _write_ln(u"# Generated with \u2764 by " + myself)
            _write_ln(u'ImageName=' + java_properties_escape(name))
            _write_ln(u'ImagePath=' + java_properties_escape("${.}/" + relpath(dirname(graalvm_image_destination), graalvm_location).replace(os.sep, '/')))
            if requires:
                _write_ln(u'Requires=' + java_properties_escape(' '.join(requires), ' ', len('Requires')))
            if isinstance(image_config, mx_sdk.LauncherConfig):
                _write_ln(u'ImageClass=' + java_properties_escape(image_config.main_class))
                if build_with_module_path:
                    _write_ln(u'ImageModule=' + java_properties_escape(image_config.main_module))
            if location_classpath:
                image_path_arg = u'ImageModulePath=' if build_with_module_path else u'ImageClasspath='
                _write_ln(image_path_arg + java_properties_escape(':'.join(("${.}/" + e.replace(os.sep, '/') for e in location_classpath)), ':', len(image_path_arg)))
            _write_ln(u'Args=' + java_properties_escape(' '.join(build_args), ' ', len('Args')))
        return self._contents

    def build(self):
        with mx.SafeFileCreation(self.subject.properties_output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
            f.write(self.contents())

    def needsBuild(self, newestInput):
        sup = super(NativePropertiesBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        reason = _file_needs_build(newestInput, self.subject.properties_output_file(), self.contents)
        if reason:
            return True, reason
        return False, None

    def clean(self, forBuild=False):
        if exists(self.subject.properties_output_file()):
            os.unlink(self.subject.properties_output_file())


class GraalVmLibPolyglotNativeProperties(mx.LayoutJARDistribution):
    def __init__(self, component):
        """
        :type component: mx_sdk.GraalVmComponent
        """
        super(GraalVmLibPolyglotNativeProperties, self).__init__(
            suite=component.suite,
            name=_libpolyglot_macro_dist_name(component),
            deps=[],
            layout={},
            path=None,
            platformDependent=False,
            theLicense=None,
            excludedLibs=None,
            path_substitutions=None,
            string_substitutions=None,
            archive_factory=None
        )
        self.component = component
        self._layout_initialized = False

    @staticmethod
    def needs_lib_polyglot_native_properties(component):
        """
        :type component: mx_sdk.GraalVmComponent
        """
        return component.polyglot_lib_jar_dependencies or component.polyglot_lib_build_args

    def _walk_layout(self):
        if not self._layout_initialized:
            native_image_args = self.component.polyglot_lib_build_args
            if self.component.polyglot_lib_jar_dependencies:
                final_dist = get_final_graalvm_distribution()
                native_image_args += ['-cp', os.pathsep.join(("${java.home}" + os.sep + e for e in graalvm_home_relative_classpath(self.component.polyglot_lib_jar_dependencies, _get_graalvm_archive_path('', final_dist), graal_vm=final_dist).split(os.pathsep)))]
            assert native_image_args
            # self.layout['META-INF/native-image/{}/native-image.properties'.format(self.component.short_name)] = 'string:' + _format_properties({'Args': ' '.join(native_image_args)})
            self.layout['META-INF/native-image/{}/native-image.properties'.format(self.component.short_name)] = 'string:Args=' + java_properties_escape(' '.join(native_image_args), ' ', len('Args'))
                                                                                                                # _format_properties({'Args': ' '.join(native_image_args)})
            self._layout_initialized = True
        return super(GraalVmLibPolyglotNativeProperties, self)._walk_layout()


def java_properties_escape(s, split_long=None, key_length=0):
    parts = []
    first = True
    if split_long and len(s) <= 80:
        split_long = False
    for c in s:
        if c == ' ' and first:
            parts.append('\\')
            parts.append(c)
        elif c == '\t':
            parts.append('\\t')
        elif c == '\n':
            parts.append('\\n')
        elif c == '\r':
            parts.append('\\r')
        elif c == '\f':
            parts.append('\\f')
        elif c in '\\#!:=':
            parts.append('\\')
            parts.append(c)
        elif split_long and c == split_long:
            parts.append(c)
            parts.append('\\\n')
            parts.append(' ' * (key_length + 1))
        else:
            parts.append(c)
        first = False
    return ''.join(parts)


def _file_needs_build(newest_input, filepath, contents_getter):
    ts = mx.TimeStampFile(filepath)
    if not ts.exists():
        return filepath + " does not exist"
    if newest_input and ts.isOlderThan(newest_input):
        return "{} is older than {}".format(ts, newest_input)
    with io.open(filepath, mode='r', encoding='utf-8') as f:
        on_disk = unicode_utf8(f.read())
    if contents_getter() != on_disk:
        return "content not up to date"
    return None


class GraalVmJvmciParentClasspath(GraalVmProject):  # based on GraalVmNativeProperties
    def __init__(self, jvmci_parent_jars, **kw_args):
        """
        :type jvmci_parent_jars: list[str]
        """
        assert jvmci_parent_jars
        self.jvmci_parent_jars = jvmci_parent_jars
        super(GraalVmJvmciParentClasspath, self).__init__(None, GraalVmJvmciParentClasspath.project_name(), deps=[], **kw_args)

    @staticmethod
    def project_name():
        return 'jvmci-parent-classpath'

    def output_file(self):
        return join(self.get_output_base(), GraalVmJvmciParentClasspath.output_file_name(), GraalVmJvmciParentClasspath.project_name(), GraalVmJvmciParentClasspath.output_file_name())

    @staticmethod
    def output_file_name():
        return 'parentClassLoader.classpath'

    def getArchivableResults(self, use_relpath=True, single=False):
        _output_file = self.output_file()
        yield _output_file, basename(_output_file)

    def getBuildTask(self, args):
        return JvmciParentClasspathBuildTask(self, args)


class JvmciParentClasspathBuildTask(mx.ProjectBuildTask):  # based NativePropertiesBuildTask
    def __init__(self, subject, args):
        """
        :type subject: GraalVmJvmciParentClasspath
        """
        super(JvmciParentClasspathBuildTask, self).__init__(args, 1, subject)
        graalvm_dist = get_final_graalvm_distribution()
        start_path = dirname(graalvm_dist.find_single_source_location('dependency:{}'.format(GraalVmJvmciParentClasspath.project_name())))
        cp_entries = [relpath(graalvm_dist.find_single_source_location('dependency:{}'.format(jpj)), start_path) for jpj in self.subject.jvmci_parent_jars]
        self._contents = unicode_utf8(_platform_classpath(cp_entries))

    def contents(self):
        return self._contents

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_file())

    def __str__(self):
        return "Creating '{}' file".format(GraalVmJvmciParentClasspath.output_file_name())

    def build(self):
        with mx.SafeFileCreation(self.subject.output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
            f.write(self.contents())

    def needsBuild(self, newestInput):
        sup = super(JvmciParentClasspathBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        reason = _file_needs_build(newestInput, self.subject.output_file(), self.contents)
        if reason:
            return True, reason
        return False, None

    def clean(self, forBuild=False):
        _output_file = self.subject.output_file()
        if exists(_output_file):
            os.unlink(_output_file)


class GraalVmJImage(mx.Project):
    """
    A GraalVmJImage produces the lib/modules jimage in a JDK9+ based GraalVM.
    """
    __metaclass__ = ABCMeta

    def __init__(self, suite, jimage_jars, jimage_ignore_jars, workingSets, theLicense=None, **kw_args):
        super(GraalVmJImage, self).__init__(suite=suite, name='graalvm-jimage', subDir=None, srcDirs=[], deps=jimage_jars,
                                            workingSets=workingSets, d=_suite.dir, theLicense=theLicense,
                                            **kw_args)
        self.jimage_ignore_jars = jimage_ignore_jars or []

    def isPlatformDependent(self):
        return True

    def getBuildTask(self, args):
        return GraalVmJImageBuildTask(self, args)

    def output_directory(self):
        return join(self.get_output_base(), self.name)

    def output_witness(self):
        return join(self.output_directory(), 'lib', 'modules')

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError("{} only produces multiple output".format(self))
        out_dir = self.output_directory()
        if exists(out_dir):
            logical_root = out_dir
            for root, _, files in os.walk(out_dir):
                for name in files:
                    yield join(root, name), relpath(join(root, name), logical_root)

class GraalVmJImageBuildTask(mx.ProjectBuildTask):
    def __init__(self, subject, args):
        super(GraalVmJImageBuildTask, self).__init__(args, 1, subject)

    def build(self):
        def with_source(dep):
            return not isinstance(dep, mx.Dependency) or (_include_sources(dep.qualifiedName()) and dep.isJARDistribution() and not dep.is_stripped())
        vendor_info = {'vendor-version': graalvm_vendor_version(get_final_graalvm_distribution())}
        out_dir = self.subject.output_directory()

        if _jlink_libraries():
            use_upgrade_module_path = mx.get_env('MX_BUILD_EXPLODED') == 'true'
            built = mx_sdk.jlink_new_jdk(_src_jdk,
                                 out_dir,
                                 self.subject.deps,
                                 self.subject.jimage_ignore_jars,
                                 with_source=with_source,
                                 vendor_info=vendor_info,
                                 use_upgrade_module_path=use_upgrade_module_path)
        else:
            mx.warn("--no-jlinking flag used. The resulting VM will be HotSpot, not GraalVM")
            if exists(out_dir):
                mx.rmtree(out_dir)
            shutil.copytree(_src_jdk.home, out_dir, symlinks=True)
            built = True

        release_file = join(out_dir, 'release')
        _sorted_suites = sorted(mx.suites(), key=lambda s: s.name)
        _metadata = BaseGraalVmLayoutDistribution._get_metadata(_sorted_suites, release_file)
        with open(release_file, 'w') as f:
            f.write(_metadata)

        with open(self._config_file(), 'w') as f:
            f.write('\n'.join(self._config()))
        return built

    def needsBuild(self, newestInput):
        sup = super(GraalVmJImageBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        out_file = self.newestOutput()
        if not out_file.exists():
            return True, '{} does not exist'.format(out_file.path)
        if newestInput and out_file.isOlderThan(newestInput):
            return True, '{} is older than {}'.format(out_file, newestInput)
        if exists(self._config_file()):
            with open(self._config_file(), 'r') as f:
                old_config = [l.strip() for l in f.readlines()]
                if set(old_config) != set(self._config()):
                    return True, 'the configuration changed'
        return False, None

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_witness())

    def clean(self, forBuild=False):
        if not forBuild:
            out_dir = self.subject.output_directory()
            if exists(out_dir):
                mx.rmtree(out_dir)
        else:
            # Cleaning will be done by self.build() if necessary
            pass

    def __str__(self):
        return 'Building {}'.format(self.subject.name)

    def _config(self):
        return [
            'components: {}'.format(', '.join(sorted(_components_set()))),
            'include sources: {}'.format(_include_sources_str()),
            'strip jars: {}'.format(mx.get_opts().strip_jars),
            'vendor-version: {}'.format(graalvm_vendor_version(get_final_graalvm_distribution())),
            'source JDK: {}'.format(_src_jdk.home),
            'use_upgrade_module_path: {}'.format(mx.get_env('GRAALVM_JIMAGE_USE_UPGRADE_MODULE_PATH', None))
        ]

    def _config_file(self):
        return self.subject.output_directory() + '.config'


class GraalVmNativeImage(_with_metaclass(ABCMeta, GraalVmProject)):
    def __init__(self, component, name, deps, native_image_config, **kw_args): # pylint: disable=super-init-not-called
        """
        :type component: mx_sdk.GraalVmComponent | None
        :type native_image_config: mx_sdk.AbstractNativeImageConfig
        """
        assert isinstance(native_image_config, mx_sdk.AbstractNativeImageConfig), type(native_image_config).__name__
        self.native_image_config = native_image_config
        self.native_image_jar_distributions = list(native_image_config.jar_distributions)
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            deps += self.native_image_jar_distributions
        super(GraalVmNativeImage, self).__init__(component, name=name, deps=deps, **kw_args)
        self.build_time = native_image_config.build_time
        if svm_support.is_supported() and self.is_native():
            if not hasattr(self, 'buildDependencies'):
                self.buildDependencies = []
            self.buildDependencies += ['{}:{}'.format(_suite, get_stage1_graalvm_distribution_name())]

    def getArchivableResults(self, use_relpath=True, single=False):
        yield self.output_file(), self.native_image_name
        if not single:
            debug = self.debug_file()
            if debug:
                yield debug, basename(debug)
            src_dir = self.image_sources_dir()
            if exists(src_dir):
                logical_root = dirname(src_dir)
                for root, _, files in os.walk(src_dir):
                    for name in files:
                        yield join(root, name), join(relpath(root, logical_root), name)

    def debug_file(self):
        if not self.is_native():
            return None
        if _get_svm_support().is_debug_supported() and mx.get_os() == 'linux':
            return join(self.get_output_base(), self.name, self.native_image_name + '.debug')
        return None

    @property
    def native_image_name(self):
        return basename(self.native_image_config.destination)

    def output_file(self):
        return join(self.get_output_base(), self.name, self.native_image_name)

    def image_sources_dir(self):
        return join(self.get_output_base(), self.name, "sources")

    def isPlatformDependent(self):
        return True

    @staticmethod
    def project_name(native_image_config):
        return basename(native_image_config.destination) + ".image"

    def is_native(self):
        return True


class GraalVmLauncher(_with_metaclass(ABCMeta, GraalVmNativeImage)):
    def __init__(self, component, name, deps, native_image_config, stage1=False, **kw_args): # pylint: disable=super-init-not-called
        """
        :type native_image_config: mx_sdk.LauncherConfig
        """
        assert isinstance(native_image_config, mx_sdk.LauncherConfig), type(native_image_config).__name__
        self.stage1 = stage1
        super(GraalVmLauncher, self).__init__(component, name, deps, native_image_config, **kw_args)

    def getBuildTask(self, args):
        if self.is_native():
            return GraalVmSVMLauncherBuildTask(self, args, _get_svm_support())
        else:
            return GraalVmBashLauncherBuildTask(self, args)

    def is_native(self):
        return GraalVmLauncher.is_launcher_native(self.native_image_config, self.stage1)

    @property
    def native_image_name(self):
        super_name = super(GraalVmLauncher, self).native_image_name
        if mx.get_os() == 'windows':
            if not self.is_native():
                return os.path.splitext(super_name)[0] + '.cmd'
        return super_name

    def get_containing_graalvm(self):
        if self.stage1:
            return get_stage1_graalvm_distribution()
        else:
            return get_final_graalvm_distribution()

    @staticmethod
    def launcher_project_name(native_image_config, stage1=False):
        is_bash = not GraalVmLauncher.is_launcher_native(native_image_config, stage1)
        return GraalVmNativeImage.project_name(native_image_config) + ("-bash" if is_bash else "") + ("-stage1" if stage1 else "")

    @staticmethod
    def is_launcher_native(native_image_config, stage1=False):
        return _get_svm_support().is_supported() and not stage1 and not _force_bash_launchers(native_image_config)

    @staticmethod
    def get_launcher_destination(config, stage1):
        """
        :type config: mx_sdk.LauncherConfig
        :type stage1: bool
        """
        if mx.get_os() == 'windows':
            if not GraalVmLauncher.is_launcher_native(config, stage1):
                return os.path.splitext(config.destination)[0] + '.cmd'
        return config.destination


class GraalVmPolyglotLauncher(GraalVmLauncher):         #pylint: disable=too-many-ancestors
    def __init__(self, component, deps, launcherConfig, **kw_args):
        launcher_config = mx_sdk.LauncherConfig(**launcherConfig)
        super(GraalVmPolyglotLauncher, self).__init__(component, GraalVmLauncher.launcher_project_name(launcher_config), deps, launcher_config, **kw_args)


class GraalVmLibrary(GraalVmNativeImage):
    def __init__(self, component, name, deps, native_image_config, **kw_args):
        assert isinstance(native_image_config, mx_sdk.LibraryConfig), type(native_image_config).__name__
        super(GraalVmLibrary, self).__init__(component, name, deps, native_image_config=native_image_config, **kw_args)

        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        self.buildDependencies += ['{}:{}'.format(_suite, get_stage1_graalvm_distribution_name())]

    def getBuildTask(self, args):
        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        if not self.is_skipped():
            return GraalVmLibraryBuildTask(self, args, svm_support)
        else:
            return mx.NoOpTask(self, args)

    def getArchivableResults(self, use_relpath=True, single=False):
        for e in super(GraalVmLibrary, self).getArchivableResults(use_relpath=use_relpath, single=single):
            yield e
            if single:
                return
        output_dir = dirname(self.output_file())
        if exists(output_dir):
            for e in os.listdir(output_dir):
                absolute_path = join(output_dir, e)
                if isfile(absolute_path) and e.endswith('.h'):
                    yield absolute_path, e

    def is_skipped(self):
        return _skip_libraries(self.native_image_config)


class GraalVmMiscLauncher(GraalVmLauncher):  # pylint: disable=too-many-ancestors
    def __init__(self, component, native_image_config, stage1=False, **kw_args):
        super(GraalVmMiscLauncher, self).__init__(component, GraalVmLauncher.launcher_project_name(native_image_config, stage1=stage1), [], native_image_config, stage1=stage1, **kw_args)


class GraalVmLanguageLauncher(GraalVmLauncher):  # pylint: disable=too-many-ancestors
    def __init__(self, component, native_image_config, stage1=False, **kw_args):
        super(GraalVmLanguageLauncher, self).__init__(component, GraalVmLauncher.launcher_project_name(native_image_config, stage1=stage1), [], native_image_config, stage1=stage1, **kw_args)

    def polyglot_config_output_file(self):
        return join(self.get_output_base(), self.name, "polyglot.config")

    def getArchivableResults(self, use_relpath=True, single=False):
        for e in super(GraalVmLanguageLauncher, self).getArchivableResults(use_relpath=use_relpath, single=single):
            yield e
            if single:
                return
        out = self.polyglot_config_output_file()
        yield out, basename(out)


class GraalVmNativeImageBuildTask(_with_metaclass(ABCMeta, mx.ProjectBuildTask)):
    def __init__(self, args, parallelism, project):
        super(GraalVmNativeImageBuildTask, self).__init__(args, parallelism, project)
        self._polyglot_config_contents = None

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
        if self.with_polyglot_config():
            reason = _file_needs_build(newestInput, self.subject.polyglot_config_output_file(), self.polyglot_config_contents)
            if reason:
                return True, reason
        return False, None

    def polyglot_config_contents(self):
        if self._polyglot_config_contents is None:
            image_config = self.subject.native_image_config
            assert self.with_polyglot_config()
            graalvm_dist = self.subject.get_containing_graalvm()
            graalvm_location = dirname(graalvm_dist.find_single_source_location('dependency:{}/polyglot.config'.format(self.subject.name)))
            classpath = NativePropertiesBuildTask.get_launcher_classpath(graalvm_dist, graalvm_location, image_config, self.subject.component, exclude_implicit=True)
            main_class = image_config.main_class
            return u"|".join((u":".join(classpath), main_class))
        return self._polyglot_config_contents

    def with_polyglot_config(self):
        return isinstance(self.subject.native_image_config, mx_sdk.LanguageLauncherConfig)

    def native_image_needs_build(self, out_file):
        # TODO check if definition has changed
        return None

    def newestOutput(self):
        paths = [self.subject.output_file()]
        if self.with_polyglot_config():
            paths.append(self.subject.polyglot_config_output_file())
        return mx.TimeStampFile.newest(paths)

    def build(self):
        if self.with_polyglot_config():
            with mx.SafeFileCreation(self.subject.polyglot_config_output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
                f.write(self.polyglot_config_contents())

    def clean(self, forBuild=False):
        out_file = self.subject.output_file()
        if exists(out_file):
            os.unlink(out_file)
        if self.with_polyglot_config():
            if exists(self.subject.polyglot_config_output_file()):
                os.unlink(self.subject.polyglot_config_output_file())

    def __str__(self):
        return 'Building {}'.format(self.subject.name)


class GraalVmBashLauncherBuildTask(GraalVmNativeImageBuildTask):
    def __init__(self, subject, args):
        """
        :type subject: GraalVmNativeImage
        """
        super(GraalVmBashLauncherBuildTask, self).__init__(args, 1, subject)

    def _template_file(self):
        custom_launcher_script = self.subject.native_image_config.custom_launcher_script
        if custom_launcher_script:
            return join(self.subject.component.suite.dir, custom_launcher_script.replace('/', os.sep))
        return join(_suite.mxDir, 'vm', 'launcher_template.{}'.format('cmd' if mx.is_windows() else 'sh'))

    def native_image_needs_build(self, out_file):
        sup = super(GraalVmBashLauncherBuildTask, self).native_image_needs_build(out_file)
        if sup:
            return sup
        if out_file.isOlderThan(self._template_file()):
            return 'template {} updated'.format(self._template_file())
        return None

    def build(self):
        super(GraalVmBashLauncherBuildTask, self).build()
        output_file = self.subject.output_file()
        mx.ensure_dir_exists(dirname(output_file))
        graal_vm = self.subject.get_containing_graalvm()
        script_destination_directory = dirname(graal_vm.find_single_source_location('dependency:' + self.subject.name))
        if _src_jdk_version >= 9:
            jre_bin = _get_graalvm_archive_path('bin', graal_vm=graal_vm)
        else:
            jre_bin = _get_graalvm_archive_path('jre/bin', graal_vm=graal_vm)

        def _get_classpath():
            cp = NativePropertiesBuildTask.get_launcher_classpath(graal_vm, script_destination_directory, self.subject.native_image_config, self.subject.component)
            return os.pathsep.join(cp)

        def _get_jre_bin():
            return relpath(jre_bin, script_destination_directory)

        def _get_main_class():
            return self.subject.native_image_config.main_class

        def _is_module_launcher():
            return str(self.subject.native_image_config.use_modules is not None)

        def _get_main_module():
            return str(self.subject.native_image_config.main_module)

        def _get_extra_jvm_args():
            image_config = self.subject.native_image_config
            extra_jvm_args = mx.list_to_cmd_line(image_config.extra_jvm_args)
            if not _jlink_libraries() and _src_jdk_version >= 9:
                if mx.is_windows():
                    extra_jvm_args = ' '.join([extra_jvm_args, r"--upgrade-module-path %location%\..\..\jvmci\graal.jar",
                                               r"--add-modules org.graalvm.truffle,org.graalvm.sdk",
                                               r"--module-path %location%\..\..\truffle\truffle-api.jar:%location%\..\..\jvmci\graal-sdk.jar"])
                else:
                    extra_jvm_args = ' '.join([extra_jvm_args, "--upgrade-module-path ${location}/../../jvmci/graal.jar",
                                               "--add-modules org.graalvm.truffle,org.graalvm.sdk",
                                               "--module-path ${location}/../../truffle/truffle-api.jar:${location}/../../jvmci/graal-sdk.jar"])
            return extra_jvm_args

        def _get_option_vars():
            image_config = self.subject.native_image_config
            return ' '.join(image_config.option_vars)

        def _get_launcher_args():
            if not _jlink_libraries() and _src_jdk_version >= 9:
                return '-J--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=jdk.internal.vm.compiler'
            return ''

        def _get_add_exports():
            res = ' '.join(self.subject.native_image_config.get_add_exports(_known_missing_jars))
            if mx.is_windows():
                res = ' '.join(('"{}"'.format(a) for a in res.split()))
            return res

        _template_subst = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        _template_subst.register_no_arg('module_launcher', _is_module_launcher)
        _template_subst.register_no_arg('add_exports', _get_add_exports)
        _template_subst.register_no_arg('classpath', _get_classpath)
        _template_subst.register_no_arg('jre_bin', _get_jre_bin)
        _template_subst.register_no_arg('main_class', _get_main_class)
        _template_subst.register_no_arg('main_module', _get_main_module)
        _template_subst.register_no_arg('extra_jvm_args', _get_extra_jvm_args)
        _template_subst.register_no_arg('macro_name', GraalVmNativeProperties.macro_name(self.subject.native_image_config))
        _template_subst.register_no_arg('option_vars', _get_option_vars)
        _template_subst.register_no_arg('launcher_args', _get_launcher_args)

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


# Those libraries are optional runtime dependencies of SVM
_known_missing_jars = {
    'HAMCREST',
    'JUNIT',
    'JUNIT_TOOL',
    'JLINE',
    'TRUFFLE_DEBUG',
    'NANO_HTTPD',
    'NANO_HTTPD_WEBSERVER',
    'JDK_TOOLS',
}

def graalvm_home_relative_classpath(dependencies, start=None, with_boot_jars=False, graal_vm=None, exclude_names=None):
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
    jimage = mx.project('graalvm-jimage', fatalIfMissing=False)
    jimage_deps = jimage.deps if jimage else None
    mx.logv("Composing classpath for " + str(dependencies) + ". Entries:\n" + '\n'.join(('- {}:{}'.format(d.suite, d.name) for d in mx.classpath_entries(dependencies))))
    cp_entries = mx.classpath_entries(dependencies)

    if exclude_names:
        for exclude_entry in mx.classpath_entries(names=exclude_names):
            if exclude_entry in cp_entries:
                cp_entries.remove(exclude_entry)

    for _cp_entry in cp_entries:
        if jimage_deps and _jlink_libraries() and _cp_entry in jimage_deps:
            continue
        if _cp_entry.isJdkLibrary() or _cp_entry.isJreLibrary():
            jdk = _src_jdk
            if hasattr(_cp_entry, 'jdkStandardizedSince') and jdk.javaCompliance >= _cp_entry.jdkStandardizedSince:
                continue
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
    return os.pathsep.join(sorted(_cp))


class GraalVmSVMNativeImageBuildTask(GraalVmNativeImageBuildTask):
    def __init__(self, subject, args, svm_support):
        """
        :type subject: GraalVmNativeImage
        :type svm_support: SvmSupport
        """
        super(GraalVmSVMNativeImageBuildTask, self).__init__(args, min(8, mx.cpu_count()), subject)
        self.svm_support = svm_support

    def build(self):
        super(GraalVmSVMNativeImageBuildTask, self).build()
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
        build_args = [
            '--macro:' + GraalVmNativeProperties.macro_name(self.subject.native_image_config),
            '-H:NumberOfThreads=' + str(self.parallelism),
        ]
        if self.subject.native_image_config.is_polyglot:
            build_args += ["--macro:truffle", "--language:all"]
        return build_args


class GraalVmSVMLauncherBuildTask(GraalVmSVMNativeImageBuildTask):
    pass


class GraalVmLibraryBuildTask(GraalVmSVMNativeImageBuildTask):
    pass


def _format_manifest(data):
    manifest_lines = []
    for k, v in data.items():
        _first = True
        manifest_line = "{}: {}".format(k, v)
        while len(manifest_line) > 72:
            manifest_lines += [("" if _first else " ") + manifest_line[:72]]
            manifest_line = manifest_line[72:]
            _first = False
        if len(manifest_line) > 0:
            manifest_lines += [("" if _first else " ") + manifest_line]

    # GR-10249: the manifest file must end with a newline
    return '\n'.join(manifest_lines) + "\n"


def _format_properties(data):
    return '\n'.join(
        "{}={}".format(k, java_properties_escape(v))
        for k, v in data.items()
    ) + "\n"


def _gen_gu_manifest(components, formatter, bundled=False):
    main_component = _get_main_component(components)
    version = _suite.release_version()
    manifest = OrderedDict()
    manifest["Bundle-Name"] = main_component.name
    manifest["Bundle-Symbolic-Name"] = "org.graalvm." + main_component.installable_id
    manifest["Bundle-Version"] = version
    capability_fmt = 'org.graalvm; filter:="(&(graalvm_version={version})(os_name={os})(os_arch={arch})(java_version={java_version}))"'
    manifest["Bundle-RequireCapability"] = capability_fmt.format(os=get_graalvm_os(),
                                                                 arch=mx.get_arch(),
                                                                 java_version=_src_jdk_version,
                                                                 version=version)
    manifest["x-GraalVM-Polyglot-Part"] = str(isinstance(main_component, mx_sdk.GraalVmTruffleComponent) and main_component.include_in_polyglot
                                              and (not isinstance(main_component, mx_sdk.GraalVmTool) or main_component.include_by_default))

    if main_component.stability is not None:
        stability = main_component.stability
        if _src_jdk_version > 11:
            stability = "experimental"
        manifest["x-GraalVM-Stability-Level"] = stability
        if stability in ("experimental", "earlyadopter", "supported"):
            # set x-GraalVM-Stability for backward compatibility when possible
            manifest["x-GraalVM-Stability"] = stability

    dependencies = set()
    for comp in components:
        for c in comp.direct_dependencies():
            if c.installable and c not in components:
                dependencies.add(c.installable_id)
    dependencies = sorted(dependencies)
    if dependencies:
        manifest["Require-Bundle"] = ','.join(("org.graalvm." + d for d in dependencies))
    if isinstance(main_component, mx_sdk.GraalVmLanguage):
        _wd_base = join('jre', 'languages') if _src_jdk_version < 9 else 'languages'
        manifest["x-GraalVM-Working-Directories"] = join(_wd_base, main_component.dir_name)

    post_install_msg = None
    for component in components:
        if getattr(component, 'post_install_msg', None):
            if post_install_msg:
                post_install_msg = post_install_msg + '\n' + component.post_install_msg
            else:
                post_install_msg = component.post_install_msg
    if post_install_msg:
        manifest["x-GraalVM-Message-PostInst"] = post_install_msg.replace("\\", "\\\\").replace("\n", "\\n")
    if bundled:
        manifest["x-GraalVM-Component-Distribution"] = "bundled"

    return formatter(manifest)


class InstallableComponentArchiver(mx.Archiver):
    def __init__(self, path, components, **kw_args):
        """
        :type path: str
        :type components: list[mx_sdk.GraalVmLanguage]
        :type kind: str
        :type reset_user_group: bool
        :type duplicates_action: str
        :type context: object
        """
        super(InstallableComponentArchiver, self).__init__(path, **kw_args)
        self.components = components
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
        assert self.components[0] == _get_main_component(self.components)
        _manifest_str_wrapped = _gen_gu_manifest(self.components, _format_manifest)
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
    def __init__(self, component, extra_components=None, **kw_args):
        """
        :type component: mx_sdk.GraalVmComponent
        """
        self.main_component = component

        def create_archive(path, **_kw_args):
            return InstallableComponentArchiver(path, self.components, **_kw_args)

        launcher_configs = list(_get_launcher_configs(component))
        for component_ in extra_components:
            launcher_configs += _get_launcher_configs(component_)

        library_configs = list(_get_library_configs(component))
        for component_ in extra_components:
            library_configs += _get_library_configs(component_)

        other_involved_components = []
        if self.main_component.short_name not in ('svm', 'svmee') \
                and _get_svm_support().is_supported() \
                and (
                    any(not _force_bash_launchers(lc) for lc in launcher_configs) or
                    any(not _skip_libraries(lc) for lc in library_configs)):
            other_involved_components += [c for c in registered_graalvm_components(stage1=True) if c.short_name in ('svm', 'svmee')]

        name = '{}_INSTALLABLE'.format(component.installable_id.replace('-', '_').upper())
        if other_involved_components:
            for launcher_config in launcher_configs:
                if _force_bash_launchers(launcher_config):
                    name += '_B' + basename(launcher_config.destination).upper()
        for library_config in library_configs:
            if _skip_libraries(library_config):
                name += '_S' + basename(library_config.destination).upper()
        if other_involved_components:
            name += '_' + '_'.join(sorted((component.short_name.upper() for component in other_involved_components)))
        name += '_JAVA{}'.format(_src_jdk_version)
        self.maven = _graalvm_maven_attributes(tag='installable')
        components = [component]
        if extra_components:
            components += extra_components
        super(GraalVmInstallableComponent, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            components=components,
            is_graalvm=False,
            exclLibs=[],
            platformDependent=True,
            theLicense=None,
            testDistribution=False,
            archive_factory=create_archive,
            path=None,
            **kw_args)


class GraalVmStandaloneComponent(LayoutSuper):  # pylint: disable=R0901
    def __init__(self, component, graalvm, **kw_args):
        """
        :type component: mx_sdk.GraalVmTruffleComponent
        :type graalvm: GraalVmLayoutDistribution
        """
        def require_svm(components):
            """
            :type components: list[mx_sdk.GraalVmComponent]
            :rtype: bool
            """
            return any(_get_launcher_configs(comp) or _get_library_configs(comp) for comp in components)

        other_comp_names = []
        involved_components = [component] + [get_component(dep) for dep in component.standalone_dependencies]
        if _get_svm_support().is_supported() and require_svm(involved_components):
            if 'svm' in [c.short_name for c in registered_graalvm_components(stage1=True)]:
                other_comp_names.append('svm')
            if 'svmee' in [c.short_name for c in registered_graalvm_components(stage1=True)]:
                other_comp_names.append('svmee')

        self.main_comp_dir_name = component.dir_name

        name = '_'.join([component.installable_id, 'standalone'] + other_comp_names + ['java{}'.format(_src_jdk_version)]).upper().replace('-', '_')
        self.base_dir_name = graalvm.string_substitutions.substitute(component.standalone_dir_name)
        base_dir = './{}/'.format(self.base_dir_name)
        layout = {}

        # Compute paths from standalone component launchers to other homes
        home_paths = {}
        for dependency_name, details in component.standalone_dependencies.items():
            dependency_path = details[0]
            comp = get_component(dependency_name, fatalIfMissing=True)
            home_paths[comp.installable_id] = base_dir + dependency_path

        def add_files_from_component(comp, path_prefix, excluded_paths):
            launcher_configs = _get_launcher_configs(comp)
            library_configs = _get_library_configs(comp)

            for support_dist in comp.support_distributions:
                layout.setdefault(path_prefix, []).append({
                    'source_type': 'extracted-dependency',
                    'dependency': support_dist,
                    'exclude': excluded_paths,
                    'path': None,
                })

            for launcher_config in launcher_configs:
                launcher_dest = path_prefix + launcher_config.destination
                if launcher_config.destination not in excluded_paths:
                    layout.setdefault(launcher_dest, []).append({
                        'source_type': 'dependency',
                        'dependency': GraalVmLauncher.launcher_project_name(launcher_config, stage1=False),
                        'exclude': excluded_paths,
                        'path': None,
                    })
                    for link in launcher_config.links:
                        if link not in excluded_paths:
                            link_dest = path_prefix + link
                            link_target = relpath(launcher_dest, start=dirname(link_dest))
                            layout.setdefault(link_dest, []).append({
                                'source_type': 'link',
                                'path': link_target,
                            })

            for library_config in library_configs:
                if library_config.destination not in excluded_paths:
                    layout.setdefault(path_prefix + library_config.destination, []).append({
                        'source_type': 'dependency',
                        'dependency': GraalVmLibrary.project_name(library_config),
                        'exclude': excluded_paths,
                        'path': None,
                    })

            for launcher_config in launcher_configs:
                destination = path_prefix + launcher_config.destination
                for language, path_from_root in home_paths.items():
                    relative_path_from_launcher_dir = relpath(path_from_root, dirname(destination))
                    launcher_config.add_relative_home_path(language, relative_path_from_launcher_dir)

        add_files_from_component(component, base_dir, [])

        sorted_suites = sorted(mx.suites(), key=lambda s: s.name)
        metadata = BaseGraalVmLayoutDistribution._get_metadata(sorted_suites)
        layout.setdefault(base_dir + 'release', []).append('string:' + metadata)

        for dependency_name, details in component.standalone_dependencies.items():
            dependency_path = details[0]
            excluded_paths = details[1] if len(details) > 1 else []
            dependency = get_component(dependency_name, fatalIfMissing=True)
            excluded_paths = [mx_subst.path_substitutions.substitute(excluded) for excluded in excluded_paths]
            dependency_path_prefix = base_dir + ((dependency_path + '/') if dependency_path else '')
            add_files_from_component(dependency, dependency_path_prefix, excluded_paths)

        mx.logvv("Standalone '{}' has layout:\n{}".format(name, pprint.pformat(layout)))

        self.maven = _graalvm_maven_attributes(tag='standalone')
        super(GraalVmStandaloneComponent, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            layout=layout,
            path=None,
            platformDependent=True,
            theLicense=None,
            path_substitutions=graalvm.path_substitutions,
            string_substitutions=graalvm.string_substitutions,
            **kw_args)


mx_subst.string_substitutions.register_with_arg('esc', lambda s: '<' + s + '>')


def _get_jvm_cfg_contents():
    if _src_jdk.javaCompliance < '9':
        if mx.get_os() == 'darwin':
            jvm_cfg = join(_src_jdk.home, 'jre', 'lib', 'jvm.cfg')
        else:
            jvm_cfg = join(_src_jdk.home, 'jre', 'lib', mx.get_arch(), 'jvm.cfg')
    else:
        jvm_cfg = join(_src_jdk.home, 'lib', 'jvm.cfg')
    if not exists(jvm_cfg):
        raise mx.abort("Could not find jvm.cfg from source JDK at " + jvm_cfg)
    with open(jvm_cfg, 'r') as orig_f:
        orig_lines = orig_f.readlines()
    new_lines = []
    vms_to_add = set(mx_sdk_vm._known_vms)
    for line in orig_lines:
        if line.startswith('#'):
            new_lines.append(line)
            continue
        if not line.startswith('-'):
            raise mx.abort("Invalid line in {}:\n{}".format(jvm_cfg, line))
        parts = re.split('[ \t]', line)
        if len(parts) < 2:
            raise mx.abort("Invalid line in {}:\n{}".format(jvm_cfg, line))
        vm_name = parts[0][1:]
        vm_type = parts[1]
        if vm_name in vms_to_add:
            if vm_type == "KNOWN":
                vms_to_add.remove(vm_name)
                new_lines.append(line)
            # else skip the line that would remove this VM
        else:
            new_lines.append(line)
    for vm_to_add in vms_to_add:
        new_lines.append('-' + vm_to_add + ' KNOWN' + os.linesep)
    # escape things that look like string substitutions
    return re.sub(r'<[\w\-]+?(:(.+?))?>', lambda m: '<esc:' + m.group(0)[1:], ''.join(new_lines))


_vm_suite = 'uninitialized'
_final_graalvm_distribution = 'uninitialized'
_stage1_graalvm_distribution = 'uninitialized'


def _platform_classpath(cp_entries):
    return os.pathsep.join(mx.normpath(entry) for entry in cp_entries)


def get_stage1_graalvm_distribution_name():
    name, _, _ = _get_graalvm_configuration('GraalVM', stage1=True)
    return name


def get_stage1_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _stage1_graalvm_distribution
    if _stage1_graalvm_distribution == 'uninitialized':
        _stage1_graalvm_distribution = GraalVmLayoutDistribution(_graalvm_base_name, stage1=True)
        _stage1_graalvm_distribution.description = "GraalVM distribution (stage1)"
        _stage1_graalvm_distribution.maven = False
        global _final_graalvm_distribution
        assert _final_graalvm_distribution
        # Ensure final_graalvm_distribution knows about jars that must not be jlinked that were added during stage1
        # (this ensures we are not jlinking of SVM_DRIVER component)
        _final_graalvm_distribution.jimage_ignore_jars.update(_stage1_graalvm_distribution.jimage_ignore_jars)
    return _stage1_graalvm_distribution


def get_final_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _final_graalvm_distribution
    if _final_graalvm_distribution == 'uninitialized':
        _final_graalvm_distribution = GraalVmLayoutDistribution(_graalvm_base_name)
        _final_graalvm_distribution.description = "GraalVM distribution"
        _final_graalvm_distribution.maven = _graalvm_maven_attributes()
    return _final_graalvm_distribution


def get_standalone_distribution(comp_dir_name):
    """
    :type comp_dir_name: str
    :rtype: GraalVmStandaloneComponent
    """
    standalones = _get_dists(GraalVmStandaloneComponent)
    if standalones:
        for standalone in standalones:
            if standalone.main_comp_dir_name == comp_dir_name:
                return standalone
        raise mx.abort("Cannot find a standalone with dir_name '{}'.\nAvailable standalones:\n{}".format(comp_dir_name, '\n'.join((('- ' + s.main_comp_dir_name for s in standalones)))))
    raise mx.abort('No standalones available. Did you forget to dynamically import a component?')


def has_svm_polyglot_lib():
    libraries = [p for p in _suite.projects if isinstance(p, GraalVmLibrary) and p.component.name == 'Polyglot Library']
    assert len(libraries) <= 1
    return len(libraries) == 1 and not libraries[0].is_skipped()


_native_image_configs = {}


def _get_launcher_configs(component):
    """
    :type component: mx_sdk_vm.GraalVmComponent
    :rtype : list[mx_sdk_vm.LauncherConfig]
    """
    return _get_native_image_configs(component, 'launcher_configs')


def _get_library_configs(component):
    """
    :type component: mx_sdk_vm.GraalVmComponent
    :rtype : list[mx_sdk_vm.LibraryConfig]
    """
    return _get_native_image_configs(component, 'library_configs')


def _get_native_image_configs(component, config_type):
    if _native_image_configs.get(config_type) is None:
        new_configs = {}
        for component_ in registered_graalvm_components(stage1=True):
            for config in getattr(component_, config_type):
                config_name = config.destination
                if config_name in new_configs:
                    _, prev_component = new_configs[config_name]
                    if prev_component.priority > component_.priority:
                        continue
                    if prev_component.priority == component_.priority:
                        raise mx.abort("Conflicting native-image configs: {} and {} both declare a config called {}".format(component_.name, prev_component.name, config_name))
                new_configs[config_name] = config, component_
        configs = {}
        for config, component_ in new_configs.values():
            configs.setdefault(component_.name, []).append(config)
        _native_image_configs[config_type] = configs
    return _native_image_configs.get(config_type).get(component.name, [])


def _libpolyglot_macro_dist_name(component):
    return component.short_name + "_libpolyglot_macro"

def has_vm_suite():
    global _vm_suite
    if _vm_suite == 'uninitialized':
        _vm_suite = mx.suite('vm', fatalIfMissing=False)
    return _vm_suite is not None


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    with_debuginfo = []
    register_distribution(get_final_graalvm_distribution())
    with_debuginfo.append(get_final_graalvm_distribution())

    # Add the macros if SubstrateVM is included, as images could be created later with an installable Native Image
    with_svm = has_component('svm')
    libpolyglot_component = mx_sdk_vm.graalvm_component_by_name('libpoly', fatalIfMissing=False) if with_svm else None

    names = set()
    short_names = set()
    needs_stage1 = False
    installables = {}
    jvmci_parent_jars = []

    for component in registered_graalvm_components(stage1=False):
        if component.name in names:
            mx.abort("Two components are named '{}'. The name should be unique".format(component.name))
        if component.short_name in short_names:
            mx.abort("Two components have short name '{}'. The short names should be unique".format(component.short_name))
        names.add(component.name)
        short_names.add(component.short_name)
        jvmci_parent_jars.extend(component.jvmci_parent_jars)
        if register_project:
            if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                config_class = GraalVmLanguageLauncher
            else:
                config_class = GraalVmMiscLauncher
            for launcher_config in _get_launcher_configs(component):
                launcher_project = config_class(component, launcher_config)
                register_project(launcher_project)
                if launcher_project.is_native():
                    needs_stage1 = True
                if with_svm:
                    register_project(GraalVmNativeProperties(component, launcher_config))
            if with_svm:
                for library_config in _get_library_configs(component):
                    register_project(GraalVmLibrary(component, GraalVmNativeImage.project_name(library_config), [], library_config))
                    assert with_svm
                    register_project(GraalVmNativeProperties(component, library_config))
                    needs_stage1 = True  # library configs need a stage1 even when they are skipped
        if component.installable and not _disable_installable(component):
            installables.setdefault(component.installable_id, []).append(component)
        if libpolyglot_component is not None and GraalVmLibPolyglotNativeProperties.needs_lib_polyglot_native_properties(component):
            register_distribution(GraalVmLibPolyglotNativeProperties(component))

    # Create installables
    for components in installables.values():
        main_component = _get_main_component(components)
        installable_component = GraalVmInstallableComponent(main_component, extra_components=[c for c in components if c != main_component])
        register_distribution(installable_component)
        with_debuginfo.append(installable_component)

    # Create standalones
    for components in installables.values():
        main_component = _get_main_component(components)
        only_native_launchers = not main_component.launcher_configs or has_svm_launcher(main_component)
        only_native_libraries = not main_component.library_configs or (_get_svm_support().is_supported() and not _has_skipped_libraries(main_component))
        if isinstance(main_component, mx_sdk.GraalVmTruffleComponent) and only_native_launchers and only_native_libraries:
            dependencies = main_component.standalone_dependencies.keys()
            missing_dependencies = [dep for dep in dependencies if not has_component(dep) or _has_skipped_libraries(get_component(dep)) or (get_component(dep).library_configs and not _get_svm_support().is_supported())]
            if missing_dependencies:
                if mx.get_opts().verbose:
                    mx.warn("Skipping standalone {} because the components {} are excluded".format(main_component.name, missing_dependencies))
            else:
                standalone = GraalVmStandaloneComponent(get_component(main_component.name, fatalIfMissing=True), get_final_graalvm_distribution())
                register_distribution(standalone)
                with_debuginfo.append(standalone)

    if register_project:
        if _src_jdk_version == 8 and jvmci_parent_jars:
            register_project(GraalVmJvmciParentClasspath(jvmci_parent_jars))

    if needs_stage1:
        if register_project:
            for component in registered_graalvm_components(stage1=True):
                if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                    config_class = GraalVmLanguageLauncher
                else:
                    config_class = GraalVmMiscLauncher
                for launcher_config in _get_launcher_configs(component):
                    register_project(config_class(component, launcher_config, stage1=True))
        register_distribution(get_stage1_graalvm_distribution())

    if register_project:
        if _src_jdk.javaCompliance >= '9':
            register_project(GraalVmJImage(
                suite=_suite,
                jimage_jars=sorted(get_final_graalvm_distribution().jimage_jars),
                jimage_ignore_jars=sorted(get_final_graalvm_distribution().jimage_ignore_jars),
                workingSets=None,
            ))

    if _with_debuginfo():
        if _get_svm_support().is_debug_supported() or mx.get_opts().strip_jars:
            for d in with_debuginfo:
                register_distribution(DebuginfoDistribution(d))


def has_svm_launcher(component, fatalIfMissing=False):
    """
    :type component: mx_sdk.GraalVmComponent | str
    :type fatalIfMissing: bool
    :rtype: bool
    """
    component = get_component(component, fatalIfMissing) if isinstance(component, str) else component
    result = _get_svm_support().is_supported() and not _has_forced_launchers(component) and bool(component.launcher_configs)
    if fatalIfMissing and not result:
        hint = None
        if _has_forced_launchers(component):
            hint = "Are you forcing bash launchers?"
        elif not bool(component.launcher_configs):
            hint = "Does '{}' register launcher configs?".format(component.name)
        mx.abort("'{}' does not have a native launcher.".format(component.name) + ("\n" + hint if hint else ""))
    return result


def has_svm_launchers(components, fatalIfMissing=False):
    """
    :type components: list[mx_sdk.GraalVmComponent | str]
    :type fatalIfMissing: bool
    :rtype: bool
    """
    return all((has_svm_launcher(component, fatalIfMissing=fatalIfMissing) for component in components))


def get_native_image_locations(name, image_name):
    libgraal_libs = [l for l in _get_library_configs(get_component(name)) if image_name in basename(l.destination)]
    if libgraal_libs:
        library_config = libgraal_libs[0]
        dist = get_final_graalvm_distribution()
        source_type = 'skip' if _skip_libraries(library_config) else 'dependency'
        return join(graalvm_output_root(), dist.find_single_source_location(source_type + ':' + GraalVmLibrary.project_name(library_config)))
    return None


def get_component(name, fatalIfMissing=False, stage1=False):
    """
    :type name: str
    :type fatalIfMissing: bool
    :type stage1: bool
    :rtype: mx_sdk.GraalVmComponent | None
    """
    for c in registered_graalvm_components(stage1=stage1):
        if name in (c.short_name, c.name):
            return c
    if fatalIfMissing:
        mx.abort("'{}' is not registered as GraalVM component. Did you forget to dynamically import it?".format(name))
    return None


def has_component(name, stage1=False):
    """
    :type name: str
    :type stage1: bool
    :rtype: bool
    """
    return get_component(name, fatalIfMissing=False, stage1=stage1) is not None


def has_components(names, stage1=False):
    """
    :type names: list[str]
    :type stage1: bool
    :rtype: bool
    """
    return all((has_component(name, stage1=stage1) for name in names))


def graalvm_output_root():
    return join(_suite.dir, get_final_graalvm_distribution().output)


def graalvm_output():
    return join(graalvm_output_root(), get_final_graalvm_distribution().jdk_base)


def graalvm_dist_name():
    return get_final_graalvm_distribution().name


def graalvm_version():
    return _suite.release_version()


def graalvm_home(fatalIfMissing=False):
    _graalvm_dist = get_final_graalvm_distribution()
    _graalvm_home = join(_graalvm_dist.output, _graalvm_dist.jdk_base)
    if fatalIfMissing and not exists(_graalvm_home):
        mx.abort("GraalVM home '{}' does not exist. Did you forget to build with this set of dynamic imports and mx options?".format(_graalvm_home))
    return _graalvm_home


def standalone_home(comp_dir_name):
    _standalone_dist = get_standalone_distribution(comp_dir_name)
    return join(_standalone_dist.output, _standalone_dist.base_dir_name)


def print_graalvm_components(args):
    """print the name of the GraalVM distribution"""
    parser = ArgumentParser(prog='mx graalvm-components', description='Print the list of GraalVM components')
    parser.add_argument('--stage1', action='store_true', help='print the list of components for the stage1 distribution')
    args = parser.parse_args(args)
    components = _components_set(stage1=args.stage1)
    print(sorted(components))


def print_graalvm_dist_name(args):
    """print the name of the GraalVM distribution"""
    parser = ArgumentParser(prog='mx graalvm-dist-name', description='Print the name of the GraalVM distribution')
    _ = parser.parse_args(args)
    print(graalvm_dist_name())


def print_graalvm_version(args):
    """print the GraalVM version"""
    parser = ArgumentParser(prog='mx graalvm-version', description='Print the GraalVM version')
    _ = parser.parse_args(args)
    print(graalvm_version())


def print_graalvm_home(args):
    """print the GraalVM home dir"""
    parser = ArgumentParser(prog='mx graalvm-home', description='Print the GraalVM home directory')
    _ = parser.parse_args(args)
    print(graalvm_home())


def print_standalone_home(args):
    """print the GraalVM standalone home dir"""
    parser = ArgumentParser(prog='mx standalone-home', description='Print the standalone home directory')
    parser.add_argument('comp_dir_name', action='store', help='component dir name', metavar='<comp_dir_name>')
    args = parser.parse_args(args)
    print(standalone_home(args.comp_dir_name))


def graalvm_enter(args):
    """enter a subshell for developing with a particular GraalVM config"""
    env = os.environ.copy()

    parser = ArgumentParser(prog='mx [graalvm args...] graalvm-enter',
                            description="""Enter a subshell for developing with a particular GraalVM configuration.
                                           This sets up environment variables to configure mx so that all mx invocations
                                           from the subshell will default to the same configuration as selected by the
                                           'graalvm args' provided to the graalvm-enter command. It also sets
                                           GRAALVM_HOME to the root of the built GraalVM. Note that manual options and
                                           env files still apply in the subshell, and might lead to a different GraalVM
                                           configuration.""")
    parser.add_argument('--print-bashrc', action='store_true',
                        help="""Print an example bashrc script for setting up a nice PS1 prompt and putting the GraalVM
                                on the PATH. The output of this can be safely appended to .bashrc, it will only do something
                                if GRAALVM_HOME is set.""")
    parser.add_argument('cmd', action='store', nargs='*',
                        default=shlex.split(env['MX_SHELL_CMD']) if 'MX_SHELL_CMD' in env else [env['SHELL'], '-i'],
                        help="""The subshell command to execute. Can also be set with the environment variable
                                MX_SHELL_CMD. Defaults to '$SHELL -i'.""")
    args = parser.parse_args(args)

    if args.print_bashrc:
        print(inspect.cleandoc("""# generated by `mx graalvm-enter --print-bashrc`
                                  if [ -n "$GRAALVM_HOME" ]
                                  then
                                      export PATH=$GRAALVM_HOME/bin:$PATH

                                      if [ -n "$MX_PRIMARY_SUITE_PATH" ]
                                      then
                                          PS1="\\[\\e[33m\\]($(basename $MX_PRIMARY_SUITE_PATH))\\[\\e[0m\\] $PS1"
                                      fi

                                      PS1="\\[\\e[32m\\]$(basename $GRAALVM_HOME)\\[\\e[0m\\] $PS1"

                                      if [ -n "$COMPONENTS" ]
                                      then
                                          graalvm_infoline="\\[\\e[34m\\]$COMPONENTS\\[\\e[0m\\]"
                                      fi

                                      if [ -n "$NATIVE_IMAGES" ]
                                      then
                                          graalvm_infoline="$graalvm_infoline / \\[\\e[31m\\]native: $NATIVE_IMAGES\\[\\e[0m\\]"
                                      fi

                                      if [ -n "$graalvm_infoline" ]
                                      then
                                          PS1="\\n$graalvm_infoline\\n$PS1"
                                      fi
                                      unset graalvm_infoline
                                  fi
                                  """))
        return

    graalvm_dist = get_final_graalvm_distribution()

    components = []
    dynamicImports = set()
    foundLibpoly = False
    for component in registered_graalvm_components():
        if component.short_name == 'libpoly':
            foundLibpoly = True  # 'libpoly' is special, we need to exclude it instead of including
        else:
            components.append(component.short_name)
        suite = component.suite
        if suite.dir == suite.vc_dir:
            dynamicImports.add(os.path.basename(suite.dir))
        else:
            dynamicImports.add("/" + os.path.basename(suite.dir))

    nativeImages = []
    for p in _suite.projects:
        if isinstance(p, GraalVmLauncher) and p.get_containing_graalvm() == graalvm_dist:
            if p.is_native():
                nativeImages.append(p.native_image_name)
        elif isinstance(p, GraalVmLibrary):
            if not p.is_skipped():
                library_name = remove_lib_prefix_suffix(p.native_image_name, require_suffix_prefix=False)
                nativeImages.append('lib:' + library_name)

    env['GRAALVM_HOME'] = graalvm_home()

    env['DYNAMIC_IMPORTS'] = ','.join(dynamicImports)
    env['COMPONENTS'] = ','.join(components)
    env['NATIVE_IMAGES'] = ','.join(nativeImages)
    if not foundLibpoly:
        env['EXCLUDE_COMPONENTS'] = 'libpoly'

    # Disable loading of the global ~/.mx/env file in the subshell. The contents of this file are already in the current
    # environment. Parsing the ~/.mx/env file again would lead to confusing results, especially if it contains settings
    # for any of the variables we are modifying here.
    env['MX_GLOBAL_ENV'] = ''

    mx.log("Entering {}... (close shell to leave, e.g. ``exit``)".format(graalvm_dist))
    mx.run(args.cmd, env=env)


def graalvm_show(args):
    """print the GraalVM config"""
    parser = ArgumentParser(prog='mx graalvm-show', description='Print the GraalVM config')
    parser.add_argument('--stage1', action='store_true', help='show the components for stage1')
    args = parser.parse_args(args)

    graalvm_dist = get_stage1_graalvm_distribution() if args.stage1 else get_final_graalvm_distribution()
    print("GraalVM distribution: {}".format(graalvm_dist))
    print("Version: {}".format(_suite.release_version()))
    print("Config name: {}".format(graalvm_dist.vm_config_name))
    print("Components:")
    for component in registered_graalvm_components(stage1=args.stage1):
        print(" - {} ('{}', /{})".format(component.name, component.short_name, component.dir_name))

    launchers = [p for p in _suite.projects if isinstance(p, GraalVmLauncher) and p.get_containing_graalvm() == graalvm_dist]
    if launchers:
        print("Launchers:")
        for launcher in launchers:
            suffix = ''
            profile_cnt = len(_image_profile(GraalVmNativeProperties.canonical_image_name(launcher.native_image_config)))
            if profile_cnt > 0:
                suffix += " ({} pgo profile file{})".format(profile_cnt, 's' if profile_cnt > 1 else '')
            print(" - {} ({}){}".format(launcher.native_image_name, "native" if launcher.is_native() else "bash", suffix))
    else:
        print("No launcher")

    libraries = [p for p in _suite.projects if isinstance(p, GraalVmLibrary)]
    if libraries and not args.stage1:
        print("Libraries:")
        for library in libraries:
            suffix = ''
            if library.is_skipped():
                suffix += " (skipped)"
            profile_cnt = len(_image_profile(GraalVmNativeProperties.canonical_image_name(library.native_image_config)))
            if profile_cnt > 0:
                suffix += " ({} pgo profile file{})".format(profile_cnt, 's' if profile_cnt > 1 else '')
            print(" - {}{}".format(library.native_image_name, suffix))
    else:
        print("No library")

    installables = _get_dists(GraalVmInstallableComponent)
    if installables and not args.stage1:
        print("Installables:")
        for i in installables:
            print(" - {}".format(i))
    else:
        print("No installable")

    standalones = _get_dists(GraalVmStandaloneComponent)
    if standalones and not args.stage1:
        print("Standalones:")
        for s in standalones:
            print(" - {}".format(s))
    else:
        print("No standalone")


def _get_dists(dist_class):
    """
    :type dist_class: mx.Distribution
    :rtype: list[mx.Distribution]
    """
    return [d for d in _suite.dists if isinstance(d, dist_class)]


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


def check_versions(jdk, graalvm_version_regex, expect_graalvm, check_jvmci):
    """
    :type jdk: mx.JDKConfig | str
    :type graalvm_version_regex: typing.Pattern
    :type expect_graalvm: bool
    :type check_jvmci: bool
    """
    check_env = "Please check the value of the 'JAVA_HOME' environment variable, your mx 'env' files, and the documentation of this suite"

    if isinstance(jdk, str):
        assert isdir(jdk), 'Not a directory: ' + jdk
        jdk = mx.JDKConfig(jdk)
    out = mx.OutputCapture()
    if check_jvmci and mx.run([jdk.java, '-XX:+JVMCIPrintProperties'], nonZeroIsFatal=False, out=out, err=out):
        mx.log_error(out.data)
        mx.abort("'{}' is not a JVMCI-enabled JDK ('java -XX:+JVMCIPrintProperties' fails).\n{}.".format(jdk.home, check_env))

    out = _decode(subprocess.check_output([jdk.java, '-version'], stderr=subprocess.STDOUT)).rstrip()

    jdk_version = jdk.version
    if jdk_version < mx.VersionSpec('1.8') or mx.VersionSpec('9') <= jdk_version < mx.VersionSpec('11'):
        mx.abort("GraalVM requires JDK8 or >=JDK11 as base-JDK, while the selected JDK ('{}') is '{}':\n{}\n\n{}.".format(jdk.home, jdk_version, out, check_env))

    match = graalvm_version_regex.match(out)
    if expect_graalvm and match is None:
        mx.abort("'{}' is not a GraalVM. Its version string:\n{}\ndoes not match:\n{}".format(jdk.home, out, graalvm_version_regex.pattern))
    elif expect_graalvm and match.group('graalvm_version') != _suite.release_version():
        mx.abort("'{}' has a wrong GraalVM version:\n{}\nexpected:\n{}".format(jdk.home, match.group('graalvm_version'), _suite.release_version()))
    elif not expect_graalvm and match:
        mx.abort("GraalVM cannot be built using a GraalVM as base-JDK ('{}').\n{}.".format(jdk.home, check_env))


def print_graalvm_vm_name(args):
    """Print the VM name of GraalVM"""
    parser = ArgumentParser(prog='mx graalvm-vm-name', description='Print the VM name of GraalVM')
    _ = parser.parse_args(args)
    print(graalvm_vm_name(get_final_graalvm_distribution(), _src_jdk))

def graalvm_vm_name(graalvm_dist, jdk):
    """
    :type jdk_home: str
    :rtype str:
    """
    out = _decode(subprocess.check_output([jdk.java, '-version'], stderr=subprocess.STDOUT)).rstrip()
    match = re.search(r'^(?P<base_vm_name>[a-zA-Z() ]+64-Bit Server VM )', out.split('\n')[-1])
    vm_name = match.group('base_vm_name') if match else ''
    return vm_name + graalvm_vendor_version(graalvm_dist)

def graalvm_vendor_version(graalvm_dist):
    """
    :type jdk_home: str
    :rtype str:
    """
    vendor_version = '{} {}'.format(graalvm_dist.base_name, graalvm_dist.vm_config_name.upper()) if graalvm_dist.vm_config_name else graalvm_dist.base_name
    vendor_version += ' {}'.format(graalvm_version())
    return vendor_version

mx.add_argument('--components', action='store', help='Comma-separated list of component names to build. Only those components and their dependencies are built. suite:NAME can be used to exclude all components of a suite.')
mx.add_argument('--exclude-components', action='store', help='Comma-separated list of component names to be excluded from the build. suite:NAME can be used to exclude all components of a suite.')
mx.add_argument('--disable-libpolyglot', action='store_true', help='Disable the \'polyglot\' library project.')
mx.add_argument('--disable-polyglot', action='store_true', help='Disable the \'polyglot\' launcher project.')
mx.add_argument('--disable-installables', action='store', help='Disable the \'installable\' distributions for gu.'
                                                               'This can be a comma-separated list of disabled components short names or `true` to disable all installables.', default=None)
mx.add_argument('--debug-images', action='store_true', help='Build native images in debug mode: \'-H:-AOTInline\' and with \'-ea\'.')
mx.add_argument('--native-images', action='store', help='Comma-separated list of launchers and libraries (syntax: lib:polyglot) to build with Native Image.')
mx.add_argument('--force-bash-launchers', action='store', help='Force the use of bash launchers instead of native images.'
                                                               'This can be a comma-separated list of disabled launchers or `true` to disable all native launchers.', default=None)
mx.add_argument('--skip-libraries', action='store', help='Do not build native images for these libraries.'
                                                         'This can be a comma-separated list of disabled libraries or `true` to disable all libraries.', default=None)
mx.add_argument('--sources', action='store', help='Comma-separated list of projects and distributions of open-source components for which source file archives must be included (all by default).', default=None)
mx.add_argument('--with-debuginfo', action='store_true', help='Generate debuginfo distributions.')
mx.add_argument('--snapshot-catalog', action='store', help='Change the default URL of the component catalog for snapshots.', default=None)
mx.add_argument('--release-catalog', action='store', help='Change the default URL of the component catalog for releases.', default=None)
mx.add_argument('--extra-image-builder-argument', action='append', help='Add extra arguments to the image builder.', default=[])
mx.add_argument('--image-profile', action='append', help='Add a profile to be used while building a native image.', default=[])
mx.add_argument('--no-licenses', action='store_true', help='Do not add license files in the archives.')
mx.add_argument('--base-jdk-info', action='store', help='Colon-separated tuple of base JDK `NAME:VERSION`, to be added on deployment to the \'basejdk\' attribute of the \'suite-revisions.xml\' file on maven-deployment.')


def _parse_cmd_arg(arg_name, env_var_name=None, separator=',', parse_bool=True, default_value=None):
    """
    :type arg_name: str
    :type env_var_name: str | None
    :type separator: str
    :type parse_bool: bool
    :type default_value: None | bool | str
    :rtype: None | bool | lst[str]
    """
    def expand_env_placeholder(values):
        """
        :type values: lst[str]
        :rtype: lst[str]
        """
        for i in range(len(values)):
            if values[i] == 'env.' + env_var_name:
                return values[:i] + mx.get_env(env_var_name, default_value).split(separator) + values[i + 1:]
        return values

    env_var_name = arg_name.upper() if env_var_name is None else env_var_name

    value = getattr(mx.get_opts(), arg_name)
    value_from_env = value in (None, [])
    if value_from_env:
        value = mx.get_env(env_var_name, default_value)

    if value is None:
        return value
    elif parse_bool and isinstance(_str_to_bool(value), bool):
        return _str_to_bool(value)
    else:
        value_list = value if isinstance(value, list) else value.split(separator)
        if not value_from_env:
            value_list = expand_env_placeholder(value_list)
        if parse_bool:
            for val in value_list:
                val = _str_to_bool(val)
                if isinstance(val, bool):
                    return val
        return value_list


def _debug_images():
    return mx.get_opts().debug_images or _env_var_to_bool('DEBUG_IMAGES')


def _components_include_list():
    included = _parse_cmd_arg('components', parse_bool=False, default_value=None)
    if included is None:
        return None
    components = []
    for name in included:
        if name.startswith('suite:'):
            suite_name = name[len('suite:'):]
            components.extend([c for c in mx_sdk_vm.graalvm_components() if c.suite.name == suite_name])
        else:
            components.append(mx_sdk.graalvm_component_by_name(name))
    return components


def _excluded_components():
    excluded = _parse_cmd_arg('exclude_components', parse_bool=False, default_value='')
    if mx.get_opts().disable_polyglot or _env_var_to_bool('DISABLE_POLYGLOT'):
        excluded.append('poly')

    expanded = []
    for name in excluded:
        if name.startswith('suite:'):
            suite_name = name[len('suite:'):]
            expanded.extend([c.name for c in mx_sdk_vm.graalvm_components() if c.suite.name == suite_name])
        else:
            expanded.append(name)

    return expanded


def _extra_image_builder_args(image):
    prefix = image + ':'
    prefix_len = len(prefix)
    args = []
    extra_args = _parse_cmd_arg('extra_image_builder_argument', env_var_name='EXTRA_IMAGE_BUILDER_ARGUMENTS', separator=None, parse_bool=False, default_value='')
    for arg in extra_args:
        if arg.startswith(prefix):
            args.append(arg[prefix_len:])
        elif arg.startswith('-'):
            args.append(arg)
    return args


def _image_profile(image):
    prefix = image + ':'
    prefix_len = len(prefix)
    profiles = []
    for arg in _parse_cmd_arg('image_profile', env_var_name='IMAGE_PROFILES', separator=';', parse_bool=False, default_value=''):
        if arg.startswith(prefix):
            profiles += arg[prefix_len:].split(',')
    return profiles


def _no_licenses():
    return mx.get_opts().no_licenses or _env_var_to_bool('NO_LICENSES')


def _with_polyglot_lib_project():
    return not (mx.get_opts().disable_libpolyglot or _env_var_to_bool('DISABLE_LIBPOLYGLOT'))


def _with_polyglot_launcher_project():
    return 'poly' in [c.short_name for c in registered_graalvm_components()]


def _force_bash_launchers(launcher):
    """
    :type launcher: str | mx_sdk.AbstractNativeImageConfig
    """
    if isinstance(launcher, mx_sdk.AbstractNativeImageConfig):
        launcher = launcher.destination
    launcher = remove_exe_suffix(launcher, require_suffix=False)
    launcher_name = basename(launcher)

    only = _parse_cmd_arg('native_images')
    if only is not None:
        if isinstance(only, bool):
            return not only
        else:
            return launcher_name not in only
    else:
        forced = _parse_cmd_arg('force_bash_launchers', default_value=str(not has_vm_suite()))
        if isinstance(forced, bool):
            return forced
        else:
            return launcher_name in forced


def _skip_libraries(library):
    """
    :type library: str | mx_sdk.LibraryConfig
    """
    if isinstance(library, mx_sdk.AbstractNativeImageConfig):
        library = library.destination
    library_name = remove_lib_prefix_suffix(basename(library), require_suffix_prefix=False)

    only = _parse_cmd_arg('native_images')
    if only is not None:
        if isinstance(only, bool):
            return not only
        else:
            only = [lib[4:] for lib in only if lib.startswith('lib:')]
            return library_name not in only
    else:
        skipped = _parse_cmd_arg('skip_libraries', default_value=str(not has_vm_suite()))
        if isinstance(skipped, bool):
            return skipped
        else:
            return library_name in skipped


def _disable_installable(component):
    """ :type component: str | mx_sdk.GraalVmComponent """
    disabled = _parse_cmd_arg('disable_installables', default_value=str(not has_vm_suite()))
    if isinstance(disabled, bool):
        return disabled
    else:
        if isinstance(component, mx_sdk.GraalVmComponent):
            component = component.short_name
        return component in disabled


def _has_forced_launchers(component):
    """:type component: mx_sdk.GraalVmComponent"""
    for launcher_config in _get_launcher_configs(component):
        if _force_bash_launchers(launcher_config):
            return True
    return False


def _has_skipped_libraries(component):
    """:type component: mx_sdk.GraalVmComponent"""
    for library_config in _get_library_configs(component):
        if _skip_libraries(library_config):
            return True
    return False


def _sources_arg():
    return _parse_cmd_arg('sources', default_value=str(True))


def _include_sources(dependency):
    sources = _sources_arg()
    if isinstance(sources, bool):
        return sources
    return dependency in sources


def _include_sources_str():
    sources = _sources_arg()
    if isinstance(sources, bool):
        return str(sources)
    return ','.join(sources)


def _with_debuginfo():
    return mx.get_opts().with_debuginfo or _env_var_to_bool('WITH_DEBUGINFO')


def _snapshot_catalog():
    return mx.get_opts().snapshot_catalog or mx.get_env('SNAPSHOT_CATALOG')


def _release_catalog():
    return mx.get_opts().release_catalog or mx.get_env('RELEASE_CATALOG')


def _base_jdk_info():
    base_jdk_info = mx.get_opts().base_jdk_info or mx.get_env('BASE_JDK_INFO')
    if base_jdk_info is None:
        return base_jdk_info
    elif base_jdk_info.count(':') != 1:
        mx.abort("Unexpected base JDK info: '{}'. Expected format: 'NAME:VERSION'.".format(base_jdk_info))
    else:
        return base_jdk_info.split(':')


def mx_post_parse_cmd_line(args):
    if _src_jdk_version >= 9:
        for component in registered_graalvm_components():
            for boot_jar in component.boot_jars:
                if not mx.get_module_name(mx.distribution(boot_jar)):
                    mx.abort("Component '{}' declares a boot jar distribution ('{}') that does not define a module.\nPlease set 'moduleInfo' or 'moduleName'.".format(component.name, boot_jar))


mx.update_commands(_suite, {
    'graalvm-components': [print_graalvm_components, ''],
    'graalvm-dist-name': [print_graalvm_dist_name, ''],
    'graalvm-version': [print_graalvm_version, ''],
    'graalvm-home': [print_graalvm_home, ''],
    'graalvm-enter': [graalvm_enter, ''],
    'graalvm-show': [graalvm_show, ''],
    'graalvm-vm-name': [print_graalvm_vm_name, ''],
    'standalone-home': [print_standalone_home, 'comp-dir-name'],
})
