#
# Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
from argparse import ArgumentParser, RawTextHelpFormatter
from collections import OrderedDict
from zipfile import ZipFile
import hashlib
import io
import inspect
import json
import os
from os.path import relpath, join, dirname, basename, exists, isfile, normpath, abspath, isdir
import pprint
import re
import shlex
import shutil
import subprocess
import sys
import textwrap
import zipfile

import mx_sdk_vm_ng

try:
    # Use more secure defusedxml library, if available
    from defusedxml.ElementTree import parse as etreeParse
except ImportError:
    from xml.etree.ElementTree import parse as etreeParse

import mx
import mx_gate
import mx_javamodules
import mx_jardistribution
import mx_native
import mx_subst
import mx_sdk
import mx_sdk_vm
import mx_util


def unicode_utf8(string):
    if isinstance(string, bytes):
        return str(string)
    return string

_suite = mx.suite('sdk')
""":type: mx.SourceSuite | mx.Suite"""

_exe_suffix = mx.exe_suffix('')
_cmd_suffix = mx.cmd_suffix('')
""":type: str"""
_lib_suffix = mx.add_lib_suffix("")
_lib_prefix = mx.add_lib_prefix("")


_graalvm_base_name = 'GraalVM'
_registered_graalvm_components = {}
_project_name = 'graal'

_base_jdk_version_info = None

default_components = []

mx.add_argument('--base-dist-name', help='Sets the name of the GraalVM base image ( for complete, ruby ... images), default to "base"', default='base')


def svm_experimental_options(experimental_options):
    return ['-H:+UnlockExperimentalVMOptions'] + experimental_options + ['-H:-UnlockExperimentalVMOptions']


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

        def is_excluded(component):
            return component.name in excluded or component.short_name in excluded

        def add_dependencies(dependencies, excludes=True):
            components = dependencies[:]
            while components:
                component = components.pop(0)
                if component.final_stage_only and stage1:
                    continue
                if component not in components_to_build and not (excludes and is_excluded(component)):
                    components_to_build.append(component)
                    components.extend(component.direct_dependencies())

        # Expand dependencies
        add_dependencies([mx_sdk.graalvm_component_by_name(name) for name in default_components], excludes=True)
        add_dependencies(components_include_list, excludes=True)

        ni_component = mx_sdk_vm.graalvm_component_by_name('ni', fatalIfMissing=False)
        niee_component = mx_sdk_vm.graalvm_component_by_name('niee', fatalIfMissing=False)
        if stage1:
            if mx.suite('truffle', fatalIfMissing=False):
                import mx_truffle
                if has_component(mx_truffle.truffle_nfi_component.name, stage1=False):
                    svmnfi_component = mx_sdk_vm.graalvm_component_by_name('svmnfi', fatalIfMissing=False)
                    if svmnfi_component is not None:
                        # SVM support for Truffle NFI must be added to the stage1 distribution if the Truffle NFI
                        # component is part of the final distribution
                        add_dependencies([svmnfi_component], excludes=False)
            # To build native launchers or libraries we need Native Image and its dependencies in stage1, even when
            # these components are not included in the final distribution
            if niee_component is not None:
                add_dependencies([niee_component], excludes=False)
            elif ni_component is not None:
                add_dependencies([ni_component], excludes=False)

        if ni_component is None:
            # Remove GraalVMSvmMacro if the 'svm' component is not found, most likely because the `/substratevm` suite
            # is not loaded
            mx.logv("Cannot find 'ni' component; removing macros: {}".format([component.name for component in components_to_build if isinstance(component, mx_sdk.GraalVMSvmMacro)]))
            components_to_build = [component for component in components_to_build if not isinstance(component, mx_sdk.GraalVMSvmMacro)]

        mx.logv('Components: {}'.format([c.name for c in components_to_build]))
        _registered_graalvm_components[stage1] = components_to_build
    return _registered_graalvm_components[stage1]


def _get_component_type_base(c, graalvm_dist_for_substitutions=None):
    if isinstance(c, mx_sdk.GraalVmLanguage):
        result = '<jre_base>/languages/'
    elif isinstance(c, mx_sdk.GraalVmSvmTool):
        result = _get_svm_component_base(graalvm_dist_for_substitutions) + '/tools/'
    elif isinstance(c, mx_sdk.GraalVmTool):
        result = '<jre_base>/tools/'
    elif isinstance(c, mx_sdk.GraalVmJdkComponent):
        result = '<jdk_base>/lib/'
    elif isinstance(c, mx_sdk.GraalVmJreComponent):
        result = '<jre_base>/lib/'
    elif isinstance(c, mx_sdk.GraalVMSvmMacro):
        result = _get_svm_component_base(graalvm_dist_for_substitutions) + '/macros/'
    elif isinstance(c, mx_sdk.GraalVmComponent):
        result = '<jdk_base>/'
    else:
        raise mx.abort("Unknown component type for {}: {}".format(c.name, type(c).__name__))
    if graalvm_dist_for_substitutions is not None:
        result = graalvm_dist_for_substitutions.path_substitutions.substitute(result)
    return result


def _get_svm_component_base(graalvm_dist_for_substitutions=None) -> str:
    # Get the 'svm' component, even if it's not part of the GraalVM image
    svm_component = mx_sdk_vm.graalvm_component_by_name('svm', fatalIfMissing=True)
    return _get_component_type_base(svm_component, graalvm_dist_for_substitutions=graalvm_dist_for_substitutions) + svm_component.dir_name


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

def get_graalvm_edition():
    return 'ee' if mx_sdk_vm.graalvm_component_by_name('cmpee', fatalIfMissing=False) else 'ce'

class BaseGraalVmLayoutDistribution(mx.LayoutDistribution, metaclass=ABCMeta):
    def __init__(self, suite, name, deps, components, is_graalvm, exclLibs, platformDependent, theLicense, testDistribution,
                 add_jdk_base=False,
                 base_dir=None,
                 path=None,
                 stage1=False,
                 include_native_image_resources_filelists=False,
                 **kw_args): # pylint: disable=super-init-not-called
        self.components = components or registered_graalvm_components(stage1)
        self.stage1 = stage1
        self.skip_archive = stage1 or graalvm_skip_archive()  # *.tar archives for stage1 distributions are never built

        layout = {}
        src_jdk_base = _src_jdk_base if add_jdk_base else '.'
        assert src_jdk_base
        base_dir = base_dir or '.'

        if base_dir != '.':
            self.jdk_base = '/'.join([base_dir, src_jdk_base]) if src_jdk_base != '.' else base_dir
        else:
            self.jdk_base = src_jdk_base

        self.jre_base = self.jdk_base

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('jdk_base', lambda: self.jdk_base)
        path_substitutions.register_no_arg('jre_base', lambda: self.jre_base)
        path_substitutions.register_no_arg('jre_home', lambda: relpath(self.jre_base, self.jdk_base))

        string_substitutions = mx_subst.SubstitutionEngine(path_substitutions)
        string_substitutions.register_no_arg('version', _suite.release_version)
        string_substitutions.register_no_arg('graalvm_os', get_graalvm_os())
        string_substitutions.register_with_arg('esc', lambda s: '<' + s + '>')

        _layout_provenance = {}

        self._post_build_warnings = []

        self.jimage_jars = set()
        self.jimage_ignore_jars = set()
        if is_graalvm:
            for component in self.components:
                if component.jlink:
                    self.jimage_jars.update(component.boot_jars + component.jvmci_parent_jars)
                    if isinstance(component, mx_sdk.GraalVmJvmciComponent):
                        self.jimage_jars.update(component.jvmci_jars)
            for component in mx_sdk_vm.graalvm_components():
                if not component.jlink:
                    self.jimage_ignore_jars.update(component.jar_distributions)
                    self.jimage_ignore_jars.update(component.builder_jar_distributions)
                    for config in component.launcher_configs + component.library_configs:
                        if config.jar_distributions:
                            self.jimage_ignore_jars.update(config.jar_distributions)
                if isinstance(component, mx_sdk_vm.GraalVmTruffleLibrary):
                    # In order to transition to Truffle Unchained we need to
                    # exclude Truffle libraries from the mechanism that produces
                    # dummy modules for qualified exports when boot modules are installed in
                    # the GraalVM JDK to be able to later load it from the module-path.
                    self.jimage_ignore_jars.update(component.boot_jars)
                    self.jimage_ignore_jars.update(component.jar_distributions)
                    self.jimage_ignore_jars.update(component.jvmci_parent_jars)
                    self.jimage_ignore_jars.update(component.builder_jar_distributions)

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
                    if with_sources and _include_sources(src_dict['dependency']) and (src_dict['dependency'] not in self.jimage_jars or not _jlink_libraries()):
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
            Since the jimage is added to `Contents/Home`, `incl_list` must contain `Contents/MacOS` and `Contents/Info.plist`, with a different CFBundleName
            :rtype: list[(str, source_dict)]
            """

            _incl_list = []
            orig_info_plist = join(_src_jdk_dir, 'Contents', 'Info.plist')
            if exists(orig_info_plist):
                root = etreeParse(orig_info_plist)
                found_el = False
                for el in root.iter():
                    if el.tag == 'key' and el.text == 'CFBundleName':
                        found_el = True
                    elif found_el:
                        assert el.tag == 'string'
                        el.text = graalvm_vendor_version()
                        bio = io.BytesIO()
                        root.write(bio)  # When porting to Python 3, we can use root.write(StringIO(), encoding="unicode")
                        plist_src = {
                            'source_type': 'string',
                            'value': bio.getvalue().decode(),
                            'ignore_value_subst': True
                        }
                        _incl_list.append((base_dir + '/Contents/Info.plist', plist_src))
                        break
                _incl_list.append((base_dir + '/Contents/MacOS', {
                    'source_type': 'file',
                    'path': join(_src_jdk_dir, 'Contents', 'MacOS')
                }))
            return _incl_list

        svm_component = get_component('svm', stage1=True)

        def _add_native_image_macro(image_config, component, stage1):
            # Add the macros if SubstrateVM is included, as images could be created later with an installable Native Image
            if svm_component and (component is None or has_component(component.short_name, stage1=False)):
                # create macro to build this launcher
                _macro_dir = _get_macros_dir() + '/' + GraalVmNativeProperties.macro_name(image_config) + '/'
                _project_name = GraalVmNativeProperties.project_name(image_config, stage1)
                _add(layout, _macro_dir, 'dependency:{}'.format(_project_name), component)  # native-image.properties is the main output
                # Add profiles
                for profile in _image_profiles(GraalVmNativeProperties.canonical_image_name(image_config)):
                    _add(layout, _macro_dir, 'file:{}'.format(abspath(profile)))

        def _add_link(_dest, _target, _component=None, _dest_base_name=None):
            assert _dest.endswith('/')
            _linkname = relpath(path_substitutions.substitute(_target), start=path_substitutions.substitute(_dest[:-1]))
            dest_base_name = _dest_base_name or basename(_target)
            if _linkname != dest_base_name:
                if mx.is_windows():
                    if _target.endswith('.exe') or _target.endswith('.cmd'):
                        contents = mx_sdk_vm_ng._make_windows_link(_linkname)
                        full_dest = _dest + dest_base_name[:-len('.exe')] + '.cmd'
                        _add(layout, full_dest, 'string:{}'.format(contents), _component)
                        return full_dest
                    else:
                        mx.abort("Cannot create link on windows for {}->{}".format(_dest, _target))
                else:
                    _add(layout, _dest + dest_base_name, 'link:{}'.format(_linkname), _component)
                    return _dest + dest_base_name

        if is_graalvm:
            if stage1:
                # 1. we do not want a GraalVM to be used as base-JDK
                # 2. we don't need to check if the base JDK is JVMCI-enabled, since JVMCIVersionCheck takes care of that when the GraalVM compiler is a registered component
                check_versions(_src_jdk, expect_graalvm=False, check_jvmci=False)

            # Add base JDK
            if mx.get_os() == 'darwin':
                # Since on Darwin the jimage is added to `Contents/Home`, `incl_list` must contain `Contents/MacOS` and `Contents/Info.plist`
                incl_list = _patch_darwin_jdk()
                for d, s in incl_list:
                    _add(layout, d, s)

            jimage_exclusion_list = ['lib/jvm.cfg']
            if not stage1:
                for lc, c in [(lc, c) for c in self.components for lc in c.library_configs if lc.add_to_module]:
                    assert isinstance(c, (mx_sdk.GraalVmJreComponent, mx_sdk.GraalVmJdkComponent)), "'{}' is not a GraalVmJreComponent nor a GraalVmJdkComponent but defines a library config ('{}') with 'add_to_module' attribute".format(c.name, lc.destination)
                    assert not lc.add_to_module.endswith('.jmod'), "Library config '{}' of component '{}' has an invalid 'add_to_module' attribute: '{}' cannot end with '.jmod'".format(lc.destination, c.name, lc.add_to_module)
                    assert all(c not in lc.add_to_module for c in ('/', '\\')), "Library config '{}' of component '{}' has an invalid 'add_to_module' attribute: '{}' cannot contain '/' or '\\'".format(lc.destination, c.name, lc.add_to_module)
                    jmod_file = lc.add_to_module + '.jmod'
                    jimage_exclusion_list.append('jmods/' + jmod_file)
                    _add(layout, '<jre_base>/jmods/' + jmod_file, 'dependency:' + JmodModifier.project_name(jmod_file))
            mx.logv("jimage_exclusion_list of {}: {}".format(name, jimage_exclusion_list))

            _add(layout, self.jdk_base + '/', {
                'source_type': 'dependency',
                'dependency': 'graalvm-stage1-jimage' if stage1 and _needs_stage1_jimage(self, get_final_graalvm_distribution()) else 'graalvm-jimage',
                'path': '*',
                'exclude': jimage_exclusion_list,
            })

        # Add the rest of the GraalVM

        component_suites = {}
        graalvm_dists = set()  # the jar distributions mentioned by launchers and libraries
        component_dists = set()  # the jar distributions directly mentioned by components

        _lang_homes_with_ni_resources = []
        jvm_configs = {}

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
            _jvm_library_dest = _component_jvmlib_base

            if _component.dir_name:
                _component_base = normpath(_component_type_base + _component.dir_name) + '/'
            else:
                _component_base = _component_type_base

            if not _jlink_libraries():
                _add(layout, '<jre_base>/lib/jvmci/', ['dependency:' + d for d in _component.boot_jars], _component, with_sources=True)
            _add(layout, _component_base, ['dependency:' + d for d in _component.jar_distributions + _component.jvmci_parent_jars], _component, with_sources=True)
            _add(layout, _component_base + 'builder/', ['dependency:' + d for d in _component.builder_jar_distributions], _component, with_sources=True)
            _add(layout, _component_base, [{
                'source_type': 'extracted-dependency',
                'dependency': d,
                'exclude': _component.license_files if _no_licenses() else [],
                'path': None,
            } for d in _component.support_distributions], _component)
            if include_native_image_resources_filelists and isinstance(_component, mx_sdk.GraalVmLanguage) and _component.support_distributions:
                # A support distribution of a GraalVmLanguage component might have the `fileListPurpose` attribute with value 'native-image-resources' specifying that all the files from the distribution should
                # be used as native image resources. Any value of the attribute other than 'native-image-resources' or None for a support distribution of a GraalVmLanguage is invalid. If the attribute is specified,
                # there is a '<distribution archive file path>.filename' file containing a file list of all the files from the distribution. The support distributions specifying the attribute together specify
                # a subset of files from this component's home directory. The file lists will be merged by the NativeImageResourcesFileList project into a single file `native-image-resources.filelist` that will be
                # written into this component's home directory. As a part of a native image build that includes this component, the files in the merged file list will be copied as resources to a directory named
                # `resources` next to the produced image. This impacts only the native images built by GraalVM that are not a part of the GraalVM itself.
                if _component_base not in _lang_homes_with_ni_resources:
                    _add(layout, _component_base, 'dependency:{}/native-image-resources.filelist'.format(NativeImageResourcesFileList.project_name(_component.dir_name)), _component)
                    _lang_homes_with_ni_resources.append(_component_base)
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
            if isinstance(_component, mx_sdk.GraalVmJvmciComponent) and (not _jlink_libraries()):
                _add(layout, '<jre_base>/lib/jvmci/', ['dependency:' + d for d in _component.jvmci_jars], _component, with_sources=True)

            if isinstance(_component, mx_sdk.GraalVmJdkComponent):
                _jdk_jre_bin = '<jdk_base>/bin/'
            else:
                _jdk_jre_bin = '<jre_base>/bin/'

            if _component_base == '<jdk_base>/':
                pass  # already in place from the support dist
            else:
                _licenses = _component.third_party_license_files
                if not _no_licenses():
                    _licenses = _licenses + _component.license_files
                for _license in _licenses:
                    if mx.is_windows() or isinstance(self, mx.AbstractJARDistribution):
                        if len(_component.support_distributions) == 1:
                            _support = _component.support_distributions[0]
                            _add(layout, '<jdk_base>/', 'extracted-dependency:{}/{}'.format(_support, _license), _component)
                        elif any(_license.startswith(sd + '/') for sd in _component.support_distributions):
                            _add(layout, '<jdk_base>/', 'extracted-dependency:{}'.format(_license), _component)
                        else:
                            mx.warn("Can not add license: " + _license)
                    else:
                        for sd in _component.support_distributions:
                            if _license.startswith(sd + '/'):
                                _add_link('<jdk_base>/', _component_base + _license[len(sd) + 1:], _component)
                                break
                        else:
                            _add_link('<jdk_base>/', _component_base + _license, _component)

            _jre_bin_names = []

            for _launcher_config in sorted(_get_launcher_configs(_component), key=lambda c: c.destination):
                graalvm_dists.update(_launcher_config.jar_distributions)
                self.jimage_ignore_jars.update(_launcher_config.jar_distributions)
                _launcher_dest = _component_base + GraalVmLauncher.get_launcher_destination(_launcher_config, stage1)
                # add `LauncherConfig.destination` to the layout
                launcher_project = GraalVmLauncher.launcher_project_name(_launcher_config, stage1)
                _add(layout, _launcher_dest, 'dependency:' + launcher_project, _component)
                if not GraalVmLauncher.is_launcher_native(_launcher_config, stage1) and mx.is_windows():
                    assert _launcher_dest.endswith('.cmd')
                    export_list_dest = _launcher_dest[:-len('cmd')] + 'export-list'
                    _add(layout, export_list_dest, f'dependency:{launcher_project}/*.export-list', _component)
                if _debug_images() and GraalVmLauncher.is_launcher_native(_launcher_config, stage1) and _get_svm_support().generate_debug_info(_launcher_config):
                    if _get_svm_support().generate_separate_debug_info(_launcher_config):
                        _add(layout, dirname(_launcher_dest) + '/', 'dependency:' + launcher_project + '/*' + _get_svm_support().separate_debuginfo_ext(), _component)
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
                if stage1 or _rebuildable_image(_launcher_config):
                    _add_native_image_macro(_launcher_config, _component, stage1)
            for _library_config in sorted(_get_library_configs(_component), key=lambda c: c.destination):
                if stage1 or _rebuildable_image(_library_config) or isinstance(_library_config, mx_sdk_vm.LanguageLibraryConfig):
                    # language libraries can run in `--jvm` mode
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
                    if _library_config.headers:
                        _add(layout, _svm_library_home, _source_type + ':' + _library_project_name + '/*.h', _component)
                    if _debug_images() and not _skip_libraries(_library_config) and _get_svm_support().generate_debug_info(_library_config):
                        if _get_svm_support().generate_separate_debug_info(_library_config):
                            _add(layout, dirname(_svm_library_dest) + '/', 'dependency:' + _library_project_name + '/*' + _get_svm_support().separate_debuginfo_ext(), _component)
                        if _include_sources(_library_config):
                            _add(layout, dirname(_svm_library_dest) + '/', 'dependency:' + _library_project_name + '/sources', _component)
                if not stage1 and isinstance(_library_config, mx_sdk.LanguageLibraryConfig) and _library_config.launchers:
                    # add native launchers for language libraries
                    for _executable in _library_config.launchers:
                        _add(layout, join(_component_base, _executable), 'dependency:{}'.format(NativeLibraryLauncherProject.library_launcher_project_name(_library_config)), _component)
                        _link_path = _add_link(_jdk_jre_bin, _component_base + _executable)
                        _jre_bin_names.append(basename(_link_path))
                if stage1 or _rebuildable_image(_library_config):
                    _add_native_image_macro(_library_config, _component, stage1)

            if not _jlink_libraries():
                component_dists.update(_component.boot_jars)
            component_dists.update(_component.jar_distributions)
            component_dists.update(_component.jvmci_parent_jars)
            component_dists.update(_component.builder_jar_distributions)

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

            if isinstance(_component, mx_sdk.GraalVmLanguage) and not is_graalvm:
                # add language-specific release file
                component_suites.setdefault(_component_base, []).append(_component.suite)

            if not stage1:
                for jvm_config in _component.jvm_configs:
                    supported_keys = ('configs', 'priority')
                    if any(key not in supported_keys for key in jvm_config.keys()):
                        raise mx.abort("Component '{}' defines a jvm_config with an unsupported property: '{}'. Supported properties are: {}".format(_component.name, jvm_config, supported_keys))
                    for supported_key in supported_keys:
                        if supported_key not in jvm_config:
                            raise mx.abort("Component '{}' defines a jvm_config that misses the '{}' property: '{}'".format(_component.name, supported_key, jvm_config))
                    if not isinstance(jvm_config['configs'], list):
                        raise mx.abort("The type of the 'configs' property of a jvm_config defined by component '{}' must be 'list': '{}'".format(_component.name, jvm_config))
                    priority = jvm_config['priority']
                    if callable(priority):
                        priority = priority()
                    if not isinstance(priority, int):
                        raise mx.abort("The type of the 'priority' property of a jvm_config defined by component '{}' must be 'int' or a callable that returns an int: '{}'".format(_component.name, jvm_config))
                    if priority == 0:
                        raise mx.abort("Component '{}' registers a jvm_config with default priority (0): '{}'\nSet a priority less than 0 to prepend to the default list of JVMs and more than 0 to append.".format(_component.name, jvm_config))
                    if priority in jvm_configs:
                        raise mx.abort("Two components define jvm_configs with the same priority:\n1. '{}': {}\n2. '{}': {}".format(jvm_configs[priority]['source'], jvm_configs[priority]['configs'], _component.name, jvm_config['configs']))
                    jvm_configs[priority] = {
                        'configs': jvm_config['configs'],
                        'source': _component.name,
                    }

        if is_graalvm:
            _add(layout, "<jre_base>/lib/jvm.cfg", "string:" + _get_jvm_cfg_contents(jvm_configs))

        graalvm_dists.difference_update(component_dists)
        _add(layout, '<jre_base>/lib/graalvm/', ['dependency:' + d for d in sorted(graalvm_dists)], with_sources=True)

        for _base, _suites in component_suites.items():
            _metadata = self._get_metadata(_suites)
            _add(layout, _base + 'release', "string:{}".format(_metadata))

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
            # When the distribution is not archived we cannot rely only on the archive file.
            # Therefore, we must compare the contents of the output directory.
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
    def _get_metadata(suites, parent_release_file=None, java_version=None):
        """
        :type suites: list[mx.Suite]
        :type parent_release_file: str | None
        :rtype: str
        """

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
        if parent_release_file:
            _metadata_dict = mx_sdk_vm.parse_release_file(parent_release_file)
        else:
            _metadata_dict = OrderedDict()

        _metadata_dict.setdefault('JAVA_VERSION', java_version or _src_jdk.version)
        _metadata_dict.setdefault('OS_NAME', get_graalvm_os())
        _metadata_dict.setdefault('OS_ARCH', mx.get_arch())
        if mx.get_os_variant():
            _metadata_dict.setdefault('OS_VARIANT', mx.get_os_variant())

        _metadata_dict['GRAALVM_VERSION'] = _suite.release_version()
        _source = _metadata_dict.get('SOURCE') or ''
        if _source:
            _source += ' '
        _source += ' '.join(['{}:{}'.format(_s.name, _s.version()) for _s in suites])
        _metadata_dict['SOURCE'] = _source
        _metadata_dict['COMMIT_INFO'] = json.dumps(_commit_info, sort_keys=True)

        # COMMIT_INFO is unquoted to simplify JSON parsing
        return mx_sdk_vm.format_release_file(_metadata_dict, {'COMMIT_INFO'})

    def get_artifact_metadata(self):
        return {'edition': get_graalvm_edition(), 'type': mx.get_opts().base_dist_name, 'project': _project_name}


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


class AbstractGraalVmLayoutDistribution(BaseGraalVmLayoutDistribution):
    def __init__(self, base_name, theLicense=None, stage1=False, components=None, include_native_image_resources_filelists=None, add_component_dependencies=True, is_graalvm=True, add_jdk_base=True, allow_incomplete_launchers=False, **kw_args):
        self.base_name = base_name
        _include_native_image_resources_filelists = not stage1 if include_native_image_resources_filelists is None else include_native_image_resources_filelists

        if components is None:
            components_with_dependencies = []
        elif add_component_dependencies:
            components_with_dependencies = AbstractGraalVmLayoutDistribution._add_dependencies(components)
        else:
            components_with_dependencies = components

        if not allow_incomplete_launchers and components_with_dependencies is not None:
            for c in components_with_dependencies:
                if c.launcher_configs or c.library_configs:
                    mx.abort('Cannot define a GraalVM layout distribution with a forced list of components that includes launcher or library configs. '
                    'The corresponding projects refer to the global stage1 and final GraalVM distributions.')

        if is_graalvm:
            name, base_dir, self.vm_config_name = _get_graalvm_configuration(base_name, components=components_with_dependencies, stage1=stage1)
        else:
            name = base_name
            base_dir = base_name.lower().replace('_', '-')
            self.vm_config_name = None

        super(AbstractGraalVmLayoutDistribution, self).__init__(
            suite=_suite,
            name=name,
            deps=[],
            components=components_with_dependencies,
            is_graalvm=is_graalvm,
            exclLibs=[],
            platformDependent=True,
            theLicense=theLicense,
            testDistribution=False,
            add_jdk_base=add_jdk_base,
            base_dir=base_dir,
            path=None,
            stage1=stage1,
            include_native_image_resources_filelists=_include_native_image_resources_filelists,
            **kw_args)

    @staticmethod
    def _add_dependencies(components, excluded_components=None):
        components_with_repetitions = components[:]
        components_with_dependencies = []
        excluded_components = excluded_components or []
        while components_with_repetitions:
            component = components_with_repetitions.pop(0)
            if component not in components_with_dependencies and component not in excluded_components:
                components_with_dependencies.append(component)
                components_with_repetitions.extend(component.direct_dependencies())
        return components_with_dependencies

    def extra_suite_revisions_data(self):
        base_jdk_info = _base_jdk_info()
        if self == get_final_graalvm_distribution() and base_jdk_info:
            assert len(base_jdk_info) == 2
            yield "basejdk", {
                "name": base_jdk_info[0],
                "version": base_jdk_info[1]
            }

    def remoteName(self, platform=None):
        remote_name = super(AbstractGraalVmLayoutDistribution, self).remoteName(platform=platform)
        # maven artifactId cannot contain '+'
        # Example: 'graalvm-community-openjdk-17.0.7+4.1-linux-amd64' -> 'graalvm-community-openjdk-17.0.7-4.1-linux-amd64'
        return remote_name.replace('+', '-')


class GraalVmLayoutDistribution(AbstractGraalVmLayoutDistribution, LayoutSuper):  # pylint: disable=R0901
    def __init__(self, base_name, **kw_args):
        super(GraalVmLayoutDistribution, self).__init__(base_name, **kw_args)

    def getBuildTask(self, args):
        return GraalVmLayoutDistributionTask(args, self, 'latest_graalvm', 'latest_graalvm_home')


class GraalVmLayoutCompressedTARDistribution(AbstractGraalVmLayoutDistribution, mx.LayoutTARDistribution):  # pylint: disable=R0901
    def __init__(self, base_name, **kw_args):
        super(GraalVmLayoutCompressedTARDistribution, self).__init__(base_name, compress=True, **kw_args)

    def compress_locally(self):
        return True

    def compress_remotely(self):
        return True

    def getBuildTask(self, args):
        return GraalVmLayoutDistributionTask(args, self)


def _components_set(components=None, stage1=False):
    components = components or registered_graalvm_components(stage1)
    components_set = set([c.short_name for c in components])
    if stage1:
        components_set.add('stage1')
    elif mx_sdk_vm.graalvm_component_by_name('ni', fatalIfMissing=False) is not None:
        # forced bash launchers and skipped libraries only make a difference if Native Image is involved in the build
        for component in components:
            for launcher_config in _get_launcher_configs(component):
                simple_name = remove_exe_suffix(basename(launcher_config.destination))
                if _force_bash_launchers(launcher_config):
                    components_set.add('b' + simple_name)
                if not _rebuildable_image(launcher_config):
                    components_set.add('nr_lau_' + simple_name)
            for library_config in _get_library_configs(component):
                simple_name = remove_lib_prefix_suffix(basename(library_config.destination))
                if _skip_libraries(library_config):
                    components_set.add('s' + simple_name)
                if not _rebuildable_image(library_config):
                    components_set.add('nr_lib_' + simple_name)
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

        if vm_dist_name is not None:
            # Examples (later we call `.lower().replace('_', '-')`):
            # GraalVM_community_openjdk_17.0.7+4.1
            # GraalVM_jdk_17.0.7+4.1
            # GraalVM_jit_jdk_17.0.7+4.1
            base_dir = '{base_name}{vm_dist_name}_{jdk_type}_{version}'.format(
                base_name=base_name,
                vm_dist_name=('_' + vm_dist_name) if vm_dist_name else '',
                jdk_type='jdk' if mx_sdk_vm.ee_implementor() else 'openjdk',
                version=graalvm_version(version_type='base-dir')
            )
            name_prefix = '{base_name}{vm_dist_name}_java{jdk_version}'.format(
                base_name=base_name,
                vm_dist_name=('_' + vm_dist_name) if vm_dist_name else '',
                jdk_version=_src_jdk_version
            )
            name = '{name_prefix}{stage_suffix}'.format(name_prefix=name_prefix, stage_suffix='_stage1' if stage1 else '')
        else:
            components_sorted_set = sorted(components_set)
            if mx.get_opts().verbose:
                mx.logv("No dist name for {}".format(components_sorted_set))
            m = hashlib.sha1()
            for component in components_sorted_set:
                m.update(component.encode())
            if _jlink_libraries():
                m.update("jlinked".encode())
            else:
                m.update("not-jlinked".encode())
            short_sha1_digest = m.hexdigest()[:10]  # to keep paths short
            base_dir = '{base_name}_{hash}_java{jdk_version}'.format(base_name=base_name, hash=short_sha1_digest, jdk_version=_src_jdk_version)
            name = '{base_dir}{stage_suffix}'.format(base_dir=base_dir, stage_suffix='_stage1' if stage1 else '')
            base_dir += '_' + _suite.release_version()
        name = name.upper()
        base_dir = base_dir.lower().replace('_', '-')

        _graal_vm_configs_cache[key] = name, base_dir, vm_config_name
    return _graal_vm_configs_cache[key]


class GraalVmLayoutDistributionTask(BaseGraalVmLayoutDistributionTask):
    def __init__(self, args, dist, root_link_name=None, home_link_name=None):
        """
        :type args: list[str]
        :type dist: AbstractGraalVmLayoutDistribution
        :type root_link_name: str or None
        :type home_link_name: str or None
        """
        self._root_link_path = join(_suite.dir, root_link_name) if root_link_name is not None else None
        self._home_link_path = join(_suite.dir, home_link_name) if home_link_name is not None else None
        self._library_projects = None
        super(GraalVmLayoutDistributionTask, self).__init__(args, dist)

    def _add_link(self):
        if mx.get_os() == 'windows':
            mx.warn('Skip adding symlink to ' + self._home_link_target() + ' (Platform Windows)')
            return
        self._rm_link()
        if self._root_link_path is not None:
            os.symlink(self._root_link_target(), self._root_link_path)
        if self._home_link_path is not None:
            os.symlink(self._home_link_target(), self._home_link_path)

    def _root_link_target(self):
        return relpath(self.subject.output, _suite.dir)

    def _home_link_target(self):
        return relpath(join(self.subject.output, self.subject.jdk_base), _suite.dir)

    def _rm_link(self):
        if mx.get_os() == 'windows':
            return
        for l in [self._root_link_path, self._home_link_path]:
            if l is not None and os.path.lexists(l):
                os.unlink(l)

    def needsBuild(self, newestInput):
        sup = super(GraalVmLayoutDistributionTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        if mx.get_os() != 'windows' and self.subject == get_final_graalvm_distribution():
            for link_path, link_target in [(self._root_link_path, self._root_link_target()), (self._home_link_path, self._home_link_target())]:
                if link_path is None:
                    continue
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
        def _add(dep_names, layout):
            root_contents = layout['./']
            for dep_name in dep_names:
                dep = mx.dependency(dep_name)
                if isinstance(dep, mx_jardistribution.JARDistribution):
                    if dep.is_stripped():
                        root_contents += ['dependency:{}:{}/*.map'.format(dep.suite.name, dep.name)]
                elif isinstance(dep, GraalVmNativeImage):
                    image_config = dep.native_image_config
                    if dep.debug_file():
                        source_type = 'skip' if isinstance(image_config, mx_sdk.LibraryConfig) and _skip_libraries(image_config) else 'dependency'
                        root_contents += [source_type + ':{}:{}/*{}'.format(dep.suite.name, dep.name, _get_svm_support().separate_debuginfo_ext())]
                        layout[dep.native_image_name + '-sources/'] = source_type + ':{}:{}/sources'.format(dep.suite.name, dep.name)
                    if not _rebuildable_image(image_config):
                        macro_dir = GraalVmNativeProperties.macro_name(image_config) + '/'
                        # Debuginfo dists include the Stage1 macro, used to build the native image. Non-rebuildable
                        # native images don't have a macro in the final distribution.
                        layout.setdefault(macro_dir, []).append('dependency:{}'.format(GraalVmNativeProperties.project_name(image_config, stage1=True)))
                        for profile in _image_profiles(GraalVmNativeProperties.canonical_image_name(image_config)):
                            layout[macro_dir].append('file:{}'.format(abspath(profile)))
                        if isinstance(image_config, mx_sdk_vm.LibraryConfig) and not isinstance(image_config, mx_sdk_vm.LanguageLibraryConfig):
                            for jar_distribution in image_config.jar_distributions:
                                layout[macro_dir].append('dependency:{}'.format(jar_distribution))

                elif isinstance(dep, GraalVmJImage):
                    _add(dep.deps, layout)

        if not self._layout_initialized:
            self.layout = {
                './': []
            }
            _add(getattr(self.subject_distribution, 'buildDependencies', []), self.layout)
            self._layout_initialized = True
        return super(DebuginfoDistribution, self)._walk_layout()

    def remoteName(self, platform=None):
        remote_name = super(DebuginfoDistribution, self).remoteName(platform=platform)
        # maven artifactId cannot contain '+'
        return remote_name.replace('+', '-')


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

def remove_cmd_or_exe_suffix(name, require_suffix=True):
    if not _cmd_suffix and not _exe_suffix:
        return name
    elif name.endswith(_cmd_suffix):
        return name[:-len(_cmd_suffix)]
    elif name.endswith(_exe_suffix):
        return name[:-len(_exe_suffix)]
    elif require_suffix:
        raise mx.abort("Missing cmd suffix: " + name)
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
        self._svm_ee_supported = self._svm_supported and has_component('svmee', stage1=True)
        self._debug_supported = self._svm_supported and (mx.is_linux() or mx.is_windows())
        self._separate_debuginfo_ext = {
            'linux': '.debug',
            'windows': '.pdb',
        }.get(mx.get_os(), None)

    def is_supported(self):
        return self._svm_supported

    def is_ee_supported(self):
        return self._svm_ee_supported

    def is_pgo_supported(self):
        return self.is_ee_supported()

    def native_image(self, build_args, output_file, out=None, err=None):
        assert self._svm_supported
        stage1 = get_stage1_graalvm_distribution()
        native_image_project_name = GraalVmLauncher.launcher_project_name(mx_sdk.LauncherConfig(mx.exe_suffix('native-image'), [], "", []), stage1=True)
        native_image_bin = join(stage1.output, stage1.find_single_source_location('dependency:' + native_image_project_name))
        native_image_command = [native_image_bin] + build_args
        output_directory = dirname(output_file)
        native_image_command += svm_experimental_options([
            '-H:Path=' + output_directory or ".",
        ])

        # Prefix native-image builds that print straight to stdout or stderr with [<output_filename>:<pid>]
        out = out or mx.PrefixCapture(lambda l: mx.log(l, end=''), basename(output_file))
        err = err or mx.PrefixCapture(lambda l: mx.log(l, end='', file=sys.stderr), basename(output_file))

        mx.run(native_image_command, nonZeroIsFatal=True, out=out, err=err)

    def is_debug_supported(self):
        return self._debug_supported

    def generate_debug_info(self, image_config):
        return self.is_debug_supported() and _generate_debuginfo(image_config)

    def generate_separate_debug_info(self, image_config):
        return self.generate_debug_info(image_config) and not mx.get_opts().disable_debuginfo_stripping and self._separate_debuginfo_ext

    def separate_debuginfo_ext(self):
        return self._separate_debuginfo_ext

    def get_debug_flags(self, image_config):
        assert self.is_debug_supported()
        flags = ['-g']
        if not self.generate_separate_debug_info(image_config):
            flags += svm_experimental_options(['-H:-StripDebugInfo'])
        return flags


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
    def __init__(self, component, image_config, stage1=False, **kw_args):
        """
        :type component: mx_sdk.GraalVmComponent | None
        :type image_config: mx_sdk.AbstractNativeImageConfig
        :type stage1: bool
        """
        self.image_config = image_config
        self.stage1 = stage1
        # With Java > 8 there are cases where image_config.get_add_exports is getting called in
        # mx_sdk_vm_impl.NativePropertiesBuildTask.contents. This only works after the jar_distributions
        # are made into proper modules. Therefore they have to be specified as dependencies here.
        deps = list(image_config.jar_distributions)
        super(GraalVmNativeProperties, self).__init__(component, GraalVmNativeProperties.project_name(image_config, stage1), deps=deps, **kw_args)

    @staticmethod
    def project_name(image_config, stage1):
        """
        :type image_config: mx_sdk.AbstractNativeImageConfig
        :type stage1: bool
        """
        return GraalVmNativeProperties.macro_name(image_config) + ("_stage1" if stage1 else "") + "_native-image.properties"

    @staticmethod
    def canonical_image_name(image_config):
        canonical_name = basename(image_config.destination)
        if isinstance(image_config, mx_sdk.LauncherConfig):
            canonical_name = remove_cmd_or_exe_suffix(canonical_name)
        elif isinstance(image_config, mx_sdk.LibraryConfig):
            canonical_name = remove_lib_prefix_suffix(canonical_name)
        return canonical_name

    @staticmethod
    def macro_name(image_config):
        macro_name = basename(image_config.destination)
        if isinstance(image_config, mx_sdk.LauncherConfig):
            macro_name = remove_cmd_or_exe_suffix(macro_name) + '-launcher'
        elif isinstance(image_config, mx_sdk.LibraryConfig):
            macro_name = remove_lib_prefix_suffix(macro_name) + '-library'
        return macro_name

    def getArchivableResults(self, use_relpath=True, single=False):
        out = self.properties_output_file()
        yield out, basename(out)

    def properties_output_file(self):
        return join(self.get_output_base(), "native-image.properties", GraalVmNativeProperties.macro_name(self.image_config) + ("_stage1" if self.stage1 else ""), "native-image.properties")

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
        self._graalvm_dist = get_stage1_graalvm_distribution() if self.subject.stage1 else get_final_graalvm_distribution()
        self._graalvm_location = self._graalvm_dist.find_single_source_location('dependency:' + self.subject.name)

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.properties_output_file())

    def __str__(self):
        return "Creating native-image.properties for " + basename(dirname(self._graalvm_location))

    def _get_location_classpath(self):
        if self._location_classpath is None:
            self._location_classpath = NativePropertiesBuildTask.get_launcher_classpath(self._graalvm_dist, dirname(self._graalvm_location), self.subject.image_config, self.subject.component, exclude_implicit=True)
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
            component_dir = _get_component_type_base(component, graalvm_dist_for_substitutions=graalvm_dist)
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
                '-march=compatibility',  # Target maximum portability of all GraalVM images.
                '-Dorg.graalvm.version={}'.format(_suite.release_version()),
            ] + svm_experimental_options([
                '-H:+AssertInitializationSpecifiedForAllClasses',
                '-H:+EnforceMaxRuntimeCompileMethods',
                '-H:+VerifyRuntimeCompilationFrameStates',
                '-H:+GuaranteeSubstrateTypesLinked',
            ])
            if _debug_images():
                build_args += ['-ea', '-O0',] + svm_experimental_options(['-H:+PreserveFramePointer', '-H:-DeleteLocalSymbols'])
            if _get_svm_support().generate_debug_info(image_config):
                build_args += _get_svm_support().get_debug_flags(image_config)
            if getattr(image_config, 'link_at_build_time', True):
                build_args += ['--link-at-build-time']

            location_classpath = self._get_location_classpath()
            graalvm_home = _get_graalvm_archive_path("", self._graalvm_dist)

            if isinstance(image_config, mx_sdk.LibraryConfig):
                suffix = _lib_suffix
                if _get_svm_support().is_pgo_supported():
                    # If pgo is supported, we should dump on exit also for library launchers
                    build_args += svm_experimental_options(['-H:+ProfilingEnableProfileDumpHooks'])
                build_args.append('--shared')
                project_name_f = GraalVmNativeImage.project_name
            elif isinstance(image_config, mx_sdk.LauncherConfig):
                suffix = _exe_suffix
                project_name_f = GraalVmLauncher.launcher_project_name
            else:
                raise mx.abort("Unsupported image config type: " + str(type(image_config)))

            if isinstance(image_config, (mx_sdk.LanguageLauncherConfig, mx_sdk.LanguageLibraryConfig)):
                build_args += ['--language:' + image_config.language, '--tool:all']

            if isinstance(image_config, mx_sdk.LanguageLibraryConfig):
                if image_config.main_class:
                    build_args += ['-Dorg.graalvm.launcher.class=' + image_config.main_class]
                # GR-47952: Espresso relies on graal_isolate_ prefix in headers
                if has_component('svmee', stage1=True) and self.subject.component.name != 'Java on Truffle':
                    build_args += ['--macro:truffle-language-library']

            source_type = 'skip' if isinstance(image_config, mx_sdk.LibraryConfig) and _skip_libraries(image_config) else 'dependency'
            # The launcher home is relative to the native image, which only exists in the final distribution.
            final_graalvm_dist = get_final_graalvm_distribution()
            final_graalvm_home = _get_graalvm_archive_path("", final_graalvm_dist)
            final_graalvm_image_destination = final_graalvm_dist.find_single_source_location(source_type + ':' + project_name_f(image_config))

            if image_config.home_finder:
                build_args += [
                    '--features=org.graalvm.home.HomeFinderFeature',
                    '-Dorg.graalvm.launcher.relative.home=' + relpath(final_graalvm_image_destination, final_graalvm_home),
                ]

            if isinstance(image_config, mx_sdk.LauncherConfig) or (isinstance(image_config, mx_sdk.LanguageLibraryConfig) and image_config.launchers):
                build_args += [
                    '-R:+EnableSignalHandling',
                    '-R:+InstallSegfaultHandler',
                    '--enable-monitoring=jvmstat,heapdump,jfr,threaddump',
                ] + svm_experimental_options([
                    '-H:+InstallExitHandlers',
                    '-H:+DumpRuntimeCompilationOnSignal',
                    '-H:+ReportExceptionStackTraces',
                ])

            if isinstance(image_config, (mx_sdk.LauncherConfig, mx_sdk.LanguageLibraryConfig)):
                if image_config.is_sdk_launcher:
                    launcher_classpath = NativePropertiesBuildTask.get_launcher_classpath(self._graalvm_dist, graalvm_home, image_config, self.subject.component, exclude_implicit=True)
                    build_args += ['-Dorg.graalvm.launcher.classpath=' + os.pathsep.join(launcher_classpath)]
                    if isinstance(image_config, mx_sdk.LauncherConfig):
                        build_args += svm_experimental_options(['-H:-ParseRuntimeOptions'])

                if has_component('svmee', stage1=True):
                    build_args += [
                        '-R:-UsePerfData'
                    ]

                for language, path in sorted(image_config.relative_home_paths.items()):
                    build_args += ['-Dorg.graalvm.launcher.relative.' + language + '.home=' + path]

            image_config_build_args = image_config.build_args + (image_config.build_args_enterprise if has_component('svmee', stage1=True) else [])
            build_args += [self._graalvm_dist.string_substitutions.substitute(arg) for arg in image_config_build_args]

            name = basename(image_config.destination)
            if suffix:
                name = name[:-len(suffix)]
            canonical_name = GraalVmNativeProperties.canonical_image_name(image_config)
            build_args += _extra_image_builder_args(canonical_name)
            profiles = _image_profiles(canonical_name)
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
                mx.abort("Unsupported space in launcher build argument: {} in config for {}".format(image_config_build_args, image_config.destination))

            self._contents = u""

            def _write_ln(s):
                self._contents += s + u"\n"

            myself = basename(__file__)
            if myself.endswith('.pyc'):
                myself = myself[:-1]
            _write_ln(u"# Generated with \u2764 by " + myself)
            _write_ln(u'ImageName=' + java_properties_escape(name))
            if not self.subject.stage1:
                # Only macros in the final distribution need `ImagePath`.
                #
                # During a `mx build`, `mx_sdk_vm_impl` always provides an explicit value for `-H:Path` when building a
                # native-image in order to have the output land in the appropriate mxbuild directory instead of inside
                # the stage1.
                _write_ln(u'ImagePath=' + java_properties_escape("${.}/" + relpath(dirname(final_graalvm_image_destination), dirname(self._graalvm_location)).replace(os.sep, '/')))
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
        with mx_util.SafeFileCreation(self.subject.properties_output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
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
        with mx_util.SafeFileCreation(self.subject.output_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
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

    def __init__(self, suite, name, jimage_jars, jimage_ignore_jars, workingSets, theLicense=None, default_to_jvmci=False, missing_export_target_action=None, **kw_args):
        super(GraalVmJImage, self).__init__(suite=suite, name=name, subDir=None, srcDirs=[], deps=jimage_jars,
                                            workingSets=workingSets, d=_suite.dir, theLicense=theLicense,
                                            default_to_jvmci=default_to_jvmci, **kw_args)
        self.jimage_ignore_jars = jimage_ignore_jars or []
        self.default_to_jvmci = default_to_jvmci
        self.missing_export_target_action = missing_export_target_action

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
        vendor_info = {'vendor-version': graalvm_vendor_version()}
        out_dir = self.subject.output_directory()

        if _jlink_libraries():
            use_upgrade_module_path = mx.get_env('MX_BUILD_EXPLODED') == 'true'

            built = mx_sdk.jlink_new_jdk(_src_jdk,
                                 out_dir,
                                 self.subject.deps,
                                 self.subject.jimage_ignore_jars,
                                 with_source=with_source,
                                 vendor_info=vendor_info,
                                 use_upgrade_module_path=use_upgrade_module_path,
                                 default_to_jvmci=self.subject.default_to_jvmci,
                                 missing_export_target_action=self.subject.missing_export_target_action)
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
        if not exists(self._config_file()):
            return True, '{} does not exits'.format(self._config_file())
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
        # Save the path and timestamp of the JDK image so that graalvm-jimage
        # is rebuilt if the JDK at JAVA_HOME is rebuilt. The JDK image file is
        # always updated when the JDK is rebuilt.
        src_jimage = mx.TimeStampFile(join(_src_jdk.home, 'lib', 'modules'))
        return [
            f'include sources: {_include_sources_str()}',
            f'strip jars: {mx.get_opts().strip_jars}',
            f'vendor-version: {graalvm_vendor_version()}',
            f'use jlink{_jlink_libraries()}',
            f'build exploded: {mx.get_env("MX_BUILD_EXPLODED") == "true"}',
            f'source jimage: {src_jimage}',
            f'default_to_jvmci: {self.subject.default_to_jvmci}',
            f'missing_export_target_action: {self.subject.missing_export_target_action}',
            f'jars: {sorted("{}:{}".format(d.suite, d.name) for d in self.subject.deps)}',
            f'ignore jars: {sorted(self.subject.jimage_ignore_jars)}',
        ]

    def _config_file(self):
        return self.subject.output_directory() + '.config'


class GraalVmNativeImage(GraalVmProject, metaclass=ABCMeta):
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
            build_directory = self.build_directory()
            build_artifacts_file = join(build_directory, 'build-artifacts.json')
            if exists(build_artifacts_file):
                # include any additional JDK libraries
                with open(build_artifacts_file, 'r') as f:
                    build_artifacts = json.load(f)
                if 'jdk_libraries' in build_artifacts:
                    for build_artifact in build_artifacts['jdk_libraries']:
                        build_artifact_path = join(build_directory, build_artifact)
                        if not exists(build_artifact_path):
                            mx.abort("Could not find build artifact '{}', referred by '{}' and produced while building '{}'".format(build_artifact_path, build_artifacts_file, self.native_image_name))
                        yield build_artifact_path, join('jdk_libraries', build_artifact)
            # include debug file if it exists
            debug = self.debug_file()
            if debug:
                yield debug, basename(debug)
            # include sources/ directory if it exists
            src_dir = join(build_directory, 'sources')
            if exists(src_dir):
                logical_root = dirname(src_dir)
                for root, _, files in os.walk(src_dir):
                    for name in files:
                        yield join(root, name), join(relpath(root, logical_root), name)

    def debug_file(self):
        if not self.is_native():
            return None
        if _get_svm_support().generate_separate_debug_info(self.native_image_config):
            debug_file_base_name = self.native_image_name
            if mx.is_windows():
                assert debug_file_base_name.lower().endswith('.exe') or debug_file_base_name.lower().endswith('.dll')
                debug_file_base_name = debug_file_base_name[:-4]
            return join(self.get_output_base(), self.name, debug_file_base_name + _get_svm_support().separate_debuginfo_ext())
        return None

    @property
    def native_image_name(self):
        return basename(self.native_image_config.destination)

    def build_directory(self):
        return join(self.get_output_base(), self.name)

    def output_file(self):
        return join(self.build_directory(), self.native_image_name)

    def isPlatformDependent(self):
        return True

    @staticmethod
    def project_name(native_image_config):
        return basename(native_image_config.destination) + ".image"

    def is_native(self):
        return True


class GraalVmLauncher(GraalVmNativeImage, metaclass=ABCMeta):
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

    def getArchivableResults(self, use_relpath=True, single=False):
        yield from super().getArchivableResults(use_relpath=use_relpath, single=single)
        if not single and not self.is_native() and mx.is_windows():
            assert self.native_image_name.endswith('.cmd')
            export_list_arc_name = self.native_image_name[:-len('cmd')] + 'export-list'
            export_list_file = join(self.build_directory(), export_list_arc_name)
            yield export_list_file, export_list_arc_name

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


class GraalVmLibrary(GraalVmNativeImage):
    def __init__(self, component, name, deps, native_image_config, **kw_args):
        assert isinstance(native_image_config, mx_sdk.LibraryConfig), type(native_image_config).__name__
        super(GraalVmLibrary, self).__init__(component, name, deps, native_image_config=native_image_config, **kw_args)

        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        if not hasattr(self, 'buildDependencies'):
            self.buildDependencies = []
        self.buildDependencies += ['{}:{}'.format(_suite, get_stage1_graalvm_distribution_name())]

        if self.is_skipped():
            # Skipped libraries do not have deps nor build deps
            self.deps = []
            self.buildDependencies = []

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


class NativeImageResourcesFileList(GraalVmProject, metaclass=ABCMeta):
    def __init__(self, component, components, language_dir, deps, **kw_args):
        super(NativeImageResourcesFileList, self).__init__(component, NativeImageResourcesFileList.project_name(language_dir), deps, **kw_args)
        self.language_dir = language_dir
        self.components = components

    def native_image_resources_filelist_file(self):
        return join(self.get_output_base(), self.name, "native-image-resources.filelist")

    def getArchivableResults(self, use_relpath=True, single=False):
        out = self.native_image_resources_filelist_file()
        yield out, basename(out)

    def get_containing_graalvm(self):
        return get_final_graalvm_distribution()

    def getBuildTask(self, args):
        return NativeImageResourcesFileListBuildTask(self, args)

    @staticmethod
    def project_name(language_dir):
        return "org.graalvm.language." + language_dir + ".ni_resources_filelist"


class NativeImageResourcesFileListBuildTask(mx.ProjectBuildTask, metaclass=ABCMeta):
    def __init__(self, project, args):
        super(NativeImageResourcesFileListBuildTask, self).__init__(args, 1, project)
        self._native_image_resources_filelist_contents = None

    def needsBuild(self, newestInput):
        reason = _file_needs_build(newestInput, self.subject.native_image_resources_filelist_file(), self.native_image_resources_filelist_contents)
        if reason:
            return True, reason
        return False, None

    def native_image_resources_filelist_contents(self):
        if self._native_image_resources_filelist_contents is None:
            contents = []
            for c in self.subject.components:
                for dep in c.support_distributions:
                    d = mx.dependency(dep)
                    if d.fileListPurpose:
                        if d.fileListPurpose != 'native-image-resources':
                            mx.abort("Since distribution {} is a GraalVmLanguage support distribution, the only allowed value of its fileListPurpose attribute is 'native-image-resources', but was {}.".format(d.name, d.fileListPurpose))
                        (filelist_file,) = (p for p, n in d.getArchivableResults(single=False) if n.endswith(".filelist"))
                        if not exists(filelist_file):
                            mx.abort("Distribution {} specifies a fileListPurpose {}, but file {} was not found.".format(d.name, d.fileListPurpose, filelist_file))
                        with open(filelist_file, "r") as fp:
                            for line in fp:
                                contents.append(line.strip())

            self._native_image_resources_filelist_contents = os.linesep.join(contents)
        return self._native_image_resources_filelist_contents

    def newestOutput(self):
        paths = [self.subject.native_image_resources_filelist_file()]
        return mx.TimeStampFile.newest(paths)

    def build(self):
        with mx_util.SafeFileCreation(self.subject.native_image_resources_filelist_file()) as sfc, io.open(sfc.tmpFd, mode='w', closefd=False, encoding='utf-8') as f:
            f.write(self.native_image_resources_filelist_contents())

    def clean(self, forBuild=False):
        if exists(self.subject.native_image_resources_filelist_file()):
            os.unlink(self.subject.native_image_resources_filelist_file())

    def __str__(self):
        return 'Building {}'.format(self.subject.name)

class GraalVmNativeImageBuildTask(mx.ProjectBuildTask, metaclass=ABCMeta):
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
        paths = [self.subject.output_file()]
        return mx.TimeStampFile.newest(paths)

    def build(self):
        pass

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
        mx_util.ensure_dir_exists(dirname(output_file))
        graal_vm = self.subject.get_containing_graalvm()
        script_destination_directory = dirname(graal_vm.find_single_source_location('dependency:' + self.subject.name))
        jre_bin = _get_graalvm_archive_path('bin', graal_vm=graal_vm)

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
            if isinstance(self.subject.component, mx_sdk.GraalVmTruffleComponent):
                extra_jvm_args = ' '.join([extra_jvm_args, '--enable-native-access=org.graalvm.truffle'])
                # GR-59703: Migrate sun.misc.* usages.
                if mx.VersionSpec("23.0.0") <= mx.get_jdk(tag='default').version:
                    extra_jvm_args = ' '.join([extra_jvm_args, '--sun-misc-unsafe-memory-access=allow'])
            if not _jlink_libraries():
                if mx.is_windows():
                    extra_jvm_args = ' '.join([extra_jvm_args, r'--upgrade-module-path "%location%\..\..\jvmci\graal.jar"'])
                else:
                    extra_jvm_args = ' '.join([extra_jvm_args, '--upgrade-module-path "${location}/../../jvmci/graal.jar"'])
            return extra_jvm_args

        def _get_option_vars():
            image_config = self.subject.native_image_config
            return ' '.join(image_config.option_vars)

        def _get_launcher_args():
            if not _jlink_libraries():
                return '-J--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=jdk.graal.compiler'
            return ''

        def _get_add_exports():
            return ' '.join(self.subject.native_image_config.get_add_exports(_known_missing_jars))

        _template_subst = mx_subst.SubstitutionEngine(mx_subst.string_substitutions)
        _template_subst.register_no_arg('module_launcher', _is_module_launcher)
        if not mx.is_windows():
            _template_subst.register_no_arg('add_exports', _get_add_exports)
        _template_subst.register_no_arg('classpath', _get_classpath)
        _template_subst.register_no_arg('jre_bin', _get_jre_bin)
        _template_subst.register_no_arg('main_class', _get_main_class)
        _template_subst.register_no_arg('main_module', _get_main_module)
        _template_subst.register_no_arg('extra_jvm_args', _get_extra_jvm_args)
        _template_subst.register_no_arg('macro_name', GraalVmNativeProperties.macro_name(self.subject.native_image_config))
        _template_subst.register_no_arg('option_vars', _get_option_vars)
        _template_subst.register_no_arg('launcher_args', _get_launcher_args)

        if mx.is_windows():
            add_exports_argfile = output_file[:-len('cmd')] + 'export-list'
            with open(add_exports_argfile, 'w') as argfile:
                argfile.write('\n'.join(_get_add_exports().split()))

        with open(self._template_file(), 'r') as template, mx_util.SafeFileCreation(output_file) as sfc, open(sfc.tmpPath, 'w') as launcher:
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
    'TRUFFLE_DEBUG',
    'NANO_HTTPD',
    'NANO_HTTPD_WEBSERVER',
    'GSON_SHADOWED',
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
    jimage = mx.project('graalvm-stage1-jimage' if graal_vm.stage1 and _needs_stage1_jimage(graal_vm, get_final_graalvm_distribution()) else 'graalvm-jimage', fatalIfMissing=False)
    jimage_deps = jimage.deps if jimage else None
    mx.logv("Composing classpath for " + str(dependencies) + ". Entries:\n" + '\n'.join(('- {}:{}'.format(d.suite, d.name) for d in mx.classpath_entries(dependencies))))
    cp_entries = mx.classpath_entries(dependencies)

    # Compute the set-difference of the transitive dependencies of `dependencies` and the transitive dependencies of `exclude_names`
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
        mx_util.ensure_dir_exists(dirname(output_file))

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
        experimental_build_args = [
            '-H:+GenerateBuildArtifactsFile',  # generate 'build-artifacts.json'
        ]

        alt_c_compiler = getattr(self.args, 'alt_cl' if mx.is_windows() else 'alt_cc')
        if alt_c_compiler is not None:
            experimental_build_args += ['-H:CCompilerPath=' + shutil.which(alt_c_compiler)]
        if self.args.alt_cflags is not None:
            experimental_build_args += ['-H:CCompilerOption=' + e for e in self.args.alt_cflags.split()]
        if self.args.alt_ldflags is not None:
            experimental_build_args += ['-H:NativeLinkerOption=' + e for e in self.args.alt_ldflags.split()]

        build_args = [
            '-EJVMCI_VERSION_CHECK', # Propagate this env var when running native image from mx
            '--parallelism=' + str(self.parallelism),
        ] + svm_experimental_options(experimental_build_args) + [
            '--macro:' + GraalVmNativeProperties.macro_name(self.subject.native_image_config), # last to allow overrides
        ]
        return build_args


class GraalVmSVMLauncherBuildTask(GraalVmSVMNativeImageBuildTask):
    pass


class GraalVmLibraryBuildTask(GraalVmSVMNativeImageBuildTask):
    pass


class JmodModifier(mx.Project):
    def __init__(self, jmod_file, library_projects, jimage_project, **kw_args):
        """
        Add native libraries defined by library projects to an existing jmod file copied from a jimage.
        `jimage_project` is only used as input and not modified
        :param jmod_file: the simple name of the module to be copied and modified. It must not be a path or end with `.jmod`
        :type jmod_file: str
        :type library_projects: list[GraalVmLibrary]
        :type jimage_project_name: GraalVmJImage
        """
        self.jmod_file = jmod_file
        self.library_projects = library_projects
        self.jimage_project = jimage_project

        super(JmodModifier, self).__init__(
            _suite,
            name=JmodModifier.project_name(self.jmod_file),
            subDir=None,
            srcDirs=[],
            deps=[p.name for p in library_projects] + [jimage_project.name],
            workingSets=None,
            d=_suite.dir,
            theLicense=None,
            **kw_args
        )

    @staticmethod
    def project_name(jmod_file):
        return jmod_file + '_modifier'

    def getArchivableResults(self, use_relpath=True, single=False):
        out = self.output_file()
        yield out, basename(out)

    def output_file(self):
        return join(self.get_output_base(), self.jmod_file)

    def getBuildTask(self, args):
        return JmodModifierBuildTask(self, args)


class JmodModifierBuildTask(mx.ProjectBuildTask, metaclass=ABCMeta):
    def __init__(self, subject, args):
        """
        Add native libraries defined by the native projects to a jmod file copied from a jimage
        :type subject: JmodModifier
        """
        super(JmodModifierBuildTask, self).__init__(args, min(8, mx.cpu_count()), subject)

    def newestOutput(self):
        return mx.TimeStampFile(self.subject.output_file())

    def needsBuild(self, newestInput):
        sup = super(JmodModifierBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        out_file = mx.TimeStampFile(self.subject.output_file())
        if not out_file.exists():
            return True, '{} does not exist'.format(out_file.path)
        if newestInput and out_file.isOlderThan(newestInput):
            return True, '{} is older than {}'.format(out_file, newestInput)
        return False, None

    def build(self):
        mx_util.ensure_dir_exists(dirname(self.subject.output_file()))
        graalvm_jimage_home = self.subject.jimage_project.output_directory()

        # 1. copy the jmod file from the jimage to the output path
        jmod_copy_src = join(graalvm_jimage_home, 'jmods', self.subject.jmod_file)
        jmod_copy_dst = self.subject.output_file()
        assert os.path.exists(jmod_copy_src), "Library projects {} have an invalid 'add_to_modules' attribute: '{}' does not exist".format([lp.name for lp in self.subject.library_projects], jmod_copy_src)
        mx.copyfile(jmod_copy_src, jmod_copy_dst)
        for library_project in [lp for lp in self.subject.library_projects if not lp.is_skipped()]:
            # 2. append the native libraries defined by the library projects to the copy of the jmod file
            library_abs_location = library_project.output_file()
            library_rel_location = join('lib', basename(library_abs_location))
            mx.logv("Adding '{}' to '{}' ('{}')".format(library_abs_location, jmod_copy_dst, library_rel_location))
            with ZipFile(jmod_copy_dst, 'a', compression=zipfile.ZIP_DEFLATED) as zf:
                zf.write(library_abs_location, library_rel_location)

    def clean(self, forBuild=False):
        out_file = self.subject.output_file()
        if exists(out_file):
            os.unlink(out_file)

    def __str__(self):
        return 'Building {}'.format(self.subject.name)


def _get_component_stability(component):
    if _src_jdk_version not in (17, 20):
        return "experimental"
    return component.stability


def default_jvm_components():
    """
    Components that, for now, must be included in the JVM.
    @rtype list[mx_sdk_vm.GraalVmComponent]
    """
    return [mx_sdk.graal_sdk_compiler_component]


def _get_jvm_cfg():
    candidates = (['lib', 'jvm.cfg'], ['jre', 'lib', 'jvm.cfg'], ['jre', 'lib', mx.get_arch(), 'jvm.cfg'])
    probed = []
    for candidate in candidates:
        jvm_cfg = join(_src_jdk.home, *candidate)
        if exists(jvm_cfg):
            return jvm_cfg
        probed.append(jvm_cfg)
    nl = os.linesep
    probed = f'{nl}  '.join(probed)
    raise mx.abort(f"Could not find jvm.cfg. Locations probed:{nl}  {probed}")


def _get_jvm_cfg_contents(cfgs_to_add):

    def validate_cfg_line(line, source):
        if line.startswith('#') or len(line.strip()) == 0:
            return
        if not line.startswith('-'):
            raise mx.abort("Invalid line in {}:\n{}".format(source, line))
        parts = re.split('[ \t]', line)
        if len(parts) < 2:
            raise mx.abort("Invalid line in {}:\n{}".format(source, line))

    assert 0 not in cfgs_to_add
    all_cfgs = cfgs_to_add.copy()

    jvm_cfg = _get_jvm_cfg()
    with open(jvm_cfg, 'r') as orig_f:
        orig_lines = orig_f.readlines()
    all_cfgs[0] = {
        'configs': orig_lines,
        'source': jvm_cfg
    }

    new_lines = []
    for _, cfg in sorted(all_cfgs.items()):
        for config in cfg['configs']:
            validate_cfg_line(config, cfg['source'])
            new_lines.append(config.strip() + os.linesep)
    # escape things that look like string substitutions
    return re.sub(r'<[\w\-]+?(:(.+?))?>', lambda m: '<esc:' + m.group(0)[1:], ''.join(new_lines))


_vm_suite = 'uninitialized'
_final_graalvm_distribution = 'uninitialized'
_stage1_graalvm_distribution = 'uninitialized'


def _platform_classpath(cp_entries):
    return os.pathsep.join(os.path.normpath(entry) for entry in cp_entries)


def get_stage1_graalvm_distribution_name():
    name, _, _ = _get_graalvm_configuration('GraalVM', stage1=True)
    return name


def get_stage1_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _stage1_graalvm_distribution
    if _stage1_graalvm_distribution == 'uninitialized':
        _stage1_graalvm_distribution = GraalVmLayoutDistribution(_graalvm_base_name, stage1=True, defaultBuild=False)
        _stage1_graalvm_distribution.description = "GraalVM distribution (stage1)"
        _stage1_graalvm_distribution.maven = False
    return _stage1_graalvm_distribution


def get_final_graalvm_distribution():
    """:rtype: GraalVmLayoutDistribution"""
    global _final_graalvm_distribution
    if _final_graalvm_distribution == 'uninitialized':
        _final_graalvm_distribution = GraalVmLayoutDistribution(_graalvm_base_name, stage1=False)
        _final_graalvm_distribution.description = "GraalVM distribution"
        _final_graalvm_distribution.maven = _graalvm_maven_attributes()
    return _final_graalvm_distribution


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


class NativeLibraryLauncherProject(mx_native.DefaultNativeProject):
    def __init__(self, component, language_library_config, **kwargs):
        """
        :type component: mx_sdk.GraalVmComponent
        :type language_library_config: mx_sdk_vm.LibraryConfig
        """
        _dir = join(_suite.dir, "src", "org.graalvm.launcher.native")
        self.component = component
        self.language_library_config = language_library_config
        self.jvm_launcher = _skip_libraries(self.language_library_config) or not _get_svm_support().is_supported()
        _dependencies = [] if (self.jvm_launcher) else [GraalVmNativeImage.project_name(self.language_library_config)]
        self.jre_base = get_final_graalvm_distribution().path_substitutions.substitute('<jre_base>')
        # We use our LLVM toolchain on Linux because we want to statically link the C++ standard library,
        # and the system toolchain rarely has libstdc++.a installed (it would be an extra CI & dev dependency).
        toolchain = 'sdk:LLVM_NINJA_TOOLCHAIN' if mx.is_linux() else 'mx:DEFAULT_NINJA_TOOLCHAIN'
        super(NativeLibraryLauncherProject, self).__init__(
            _suite,
            NativeLibraryLauncherProject.library_launcher_project_name(self.language_library_config),
            'src',
            [],
            _dependencies,
            None,
            _dir,
            'executable',
            deliverable=self.language_library_config.language,
            use_jdk_headers=True,
            toolchain=toolchain,
            **kwargs
        )

    def isJDKDependent(self):
        # This must always be False, the compiled thin launchers need to work on both latest LTS JDK and jdk-latest
        return False

    @staticmethod
    def library_launcher_project_name(language_library_config):
        return "org.graalvm.launcher.native." + language_library_config.language

    @property
    def cflags(self):
        _dist = get_final_graalvm_distribution()
        _exe_paths = _dist.find_source_location('dependency:' + NativeLibraryLauncherProject.library_launcher_project_name(self.language_library_config))
        _exe_dirs = set([dirname(p) for p in _exe_paths])
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
        if mx.is_linux():
            _dynamic_cflags += ['-stdlib=libc++'] # to link libc++ statically, see ldlibs
        if mx.is_darwin():
            _dynamic_cflags += ['-ObjC++']

        def escaped_path(path):
            if mx.is_windows():
                return path.replace('\\', '\\\\')
            else:
                return path

        def escaped_relpath(path):
            relative = {relpath(path, start=_exe_dir) for _exe_dir in _exe_dirs}
            if len(relative) > 1:
                mx.abort("If multiple launcher targets are specified they need to be in directories agreeing on all relative paths: {} relative to {} lead to {}".format(_exe_dirs, path, relative))
            return escaped_path(relative.pop())

        _graalvm_home = _get_graalvm_archive_path("")

        # launcher classpath for launching via jvm
        _mp = NativePropertiesBuildTask.get_launcher_classpath(_dist, _graalvm_home, self.language_library_config, self.component, exclude_implicit=True)
        _mp = [join(_dist.path_substitutions.substitute('<jdk_base>'), x) for x in _mp]
        # path from language launcher to jars
        _mp = [escaped_relpath(x) for x in _mp]
        _lp = []

        launcher_jars = self.language_library_config.jar_distributions
        assert len(launcher_jars) > 0, launcher_jars
        main_class = self.language_library_config.main_class
        main_class_package = main_class[0:main_class.rindex(".")]
        main_module_export = main_class_package + " to org.graalvm.launcher"
        main_module = None
        for launcher_jar in launcher_jars:
            dist = mx.distribution(launcher_jar)
            if hasattr(dist, 'moduleInfo') and main_module_export in dist.moduleInfo.get('exports', []):
                main_module = dist.moduleInfo['name']

        if not main_module:
            mx.abort("The distribution with main class {} among {} must have export: {}".format(main_class, launcher_jars, main_module_export))

        _dynamic_cflags.append('-DLAUNCHER_MAIN_MODULE=' + main_module)
        _dynamic_cflags.append('-DLAUNCHER_CLASS=' + self.language_library_config.main_class)
        if _mp:
            _dynamic_cflags.append('-DLAUNCHER_MODULE_PATH="{\\"' + '\\", \\"'.join(_mp) + '\\"}"')
        if _lp:
            _dynamic_cflags.append('-DLAUNCHER_LIBRARY_PATH="{\\"' + '\\", \\"'.join(_lp) + '\\"}"')

        # path to libjvm
        if mx.is_windows():
            _libjvm_path = join(self.jre_base, 'bin', 'server', 'jvm.dll')
        else:
            _libjvm_path = join(self.jre_base, 'lib', 'server', mx.add_lib_suffix("libjvm"))
        _libjvm_path = escaped_relpath(_libjvm_path)
        _dynamic_cflags += [
            '-DLIBJVM_RELPATH=' + _libjvm_path,
        ]

        languages_dir = join(_graalvm_home, "languages")
        tools_dir = join(_graalvm_home, "tools")
        _dynamic_cflags += [
            '-DLANGUAGES_DIR=' + escaped_relpath(languages_dir),
            '-DTOOLS_DIR=' + escaped_relpath(tools_dir),
        ]

        # path to libjli - only needed on osx for AWT
        if mx.is_darwin():
            _libjli_path = join(self.jre_base, 'lib')
            if mx_sdk_vm.base_jdk_version() < 17:
                _libjli_path = join(_libjli_path, 'jli')
            _libjli_path = join(_libjli_path, mx.add_lib_suffix("libjli"))
            _libjli_path = escaped_relpath(_libjli_path)
            _dynamic_cflags += [
                '-DLIBJLI_RELPATH=' + _libjli_path,
            ]

        # path to native image language library - this is set even if the library is not built, as it may be built after the fact
        if self.jvm_launcher:
            # since this distribution has no native library, we can only assume the default path: language_home/lib<lang>vm.so
            _lib_path = join(_graalvm_home, "languages", self.language_library_config.language, self.default_language_home_relative_libpath())
        else:
            _lib_path = _dist.find_single_source_location('dependency:' + GraalVmLibrary.project_name(self.language_library_config))
        _dynamic_cflags += [
            '-DLIBLANG_RELPATH=' + escaped_relpath(_lib_path)
        ]

        if len(self.language_library_config.option_vars) > 0:
            _dynamic_cflags += ['-DLAUNCHER_OPTION_VARS="{\\"' + '\\", \\"'.join(self.language_library_config.option_vars) + '\\"}"']

        if len(self.language_library_config.default_vm_args) > 0:
            _dynamic_cflags += ['-DLAUNCHER_DEFAULT_VM_ARGS="{\\"' + '\\", \\"'.join(self.language_library_config.default_vm_args) + '\\"}"']

        return super(NativeLibraryLauncherProject, self).cflags + _dynamic_cflags

    @property
    def ldflags(self):
        _dynamic_ldflags = []
        if not mx.is_windows():
            _dynamic_ldflags += ['-pthread']
        return super(NativeLibraryLauncherProject, self).ldflags + _dynamic_ldflags

    @property
    def ldlibs(self):
        _dynamic_ldlibs = []
        if mx.is_linux():
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

        return super(NativeLibraryLauncherProject, self).ldlibs + _dynamic_ldlibs

    def default_language_home_relative_libpath(self):
        name = self.language_library_config.language + "vm"
        name = mx.add_lib_prefix(name)
        name = mx.add_lib_suffix(name)
        return join("lib", name)


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
    def _release_version():
        version = _suite.release_version()
        if version.endswith('-dev'):
            version = version[:-len('-dev')]
        return version

    string_substitutions = mx_subst.SubstitutionEngine(mx_subst.SubstitutionEngine(mx_subst.path_substitutions))
    string_substitutions.register_no_arg('version', _release_version)
    attrs = {
        'description': 'SDK version file.',
        'maven': False,
    }
    register_distribution(mx.LayoutDirDistribution(
        suite=_suite,
        name='VERSION',
        deps=[],
        layout={
            'version': 'string:<version>'
        },
        path=None,
        platformDependent=False,
        theLicense=None,
        string_substitutions=string_substitutions,
        **attrs
    ))
    main_dists = {
        'graalvm': [],
    }
    with_non_rebuildable_configs = False

    def register_main_dist(dist, label):
        assert label in main_dists, f"Unknown label: '{label}'. Expected: '{list(main_dists.keys())}'"
        register_distribution(dist)
        main_dists[label].append(dist.name)
        if _debuginfo_dists():
            if _get_svm_support().is_debug_supported() or mx.get_opts().strip_jars or with_non_rebuildable_configs:
                debuginfo_dist = DebuginfoDistribution(dist)
                register_distribution(debuginfo_dist)
                main_dists[label].append(debuginfo_dist.name)

    final_graalvm_distribution = get_final_graalvm_distribution()

    from mx_native import TargetSelection
    for c in final_graalvm_distribution.components:
        if c.extra_native_targets:
            for t in c.extra_native_targets:
                mx.logv(f"Selecting extra target '{t}' from GraalVM component '{c.short_name}'.")
                TargetSelection.add_extra(t)

    # Add the macros if SubstrateVM is in stage1, as images could be created later with an installable Native Image
    with_svm = has_component('svm', stage1=True)

    names = set()
    short_names = set()
    needs_stage1 = mx_sdk_vm_ng.requires_native_image_stage1()
    jvmci_parent_jars = []
    modified_jmods = {}
    dir_name_to_ni_resources_components = {}

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
                    if _rebuildable_image(launcher_config):
                        register_project(GraalVmNativeProperties(component, launcher_config))
                    else:
                        with_non_rebuildable_configs = True
            for library_config in _get_library_configs(component):
                if with_svm:
                    library_project = GraalVmLibrary(component, GraalVmNativeImage.project_name(library_config), [], library_config)
                    register_project(library_project)
                    if _rebuildable_image(library_config):
                        register_project(GraalVmNativeProperties(component, library_config))
                    else:
                        with_non_rebuildable_configs = True
                    if library_config.add_to_module:
                        jmod_file = library_config.add_to_module + ('' if library_config.add_to_module.endswith('.jmod') else '.jmod')
                        modified_jmods.setdefault(jmod_file, []).append(library_project)
                    needs_stage1 = True  # library configs need a stage1 even when they are skipped
                if isinstance(library_config, mx_sdk.LanguageLibraryConfig):
                    if library_config.launchers:
                        launcher_project = NativeLibraryLauncherProject(component, library_config)
                        register_project(launcher_project)
            if isinstance(component, mx_sdk.GraalVmLanguage) and component.support_distributions:
                ni_resources_components = dir_name_to_ni_resources_components.get(component.dir_name)
                if not ni_resources_components:
                    ni_resources_components = []
                    dir_name_to_ni_resources_components[component.dir_name] = ni_resources_components
                ni_resources_components.append(component)

    for dir_name in dir_name_to_ni_resources_components:
        ni_resources_components = dir_name_to_ni_resources_components.get(dir_name)
        deps = []
        for component in ni_resources_components:
            deps.extend(component.support_distributions)
        native_image_resources_filelist_project = NativeImageResourcesFileList(None, ni_resources_components, dir_name, deps)
        register_project(native_image_resources_filelist_project)

    # Register main distribution
    register_main_dist(final_graalvm_distribution, 'graalvm')

    # Register java-standalone-jimage
    needs_java_standalone_jimage = mx_sdk_vm_ng.requires_standalone_jimage()
    if needs_java_standalone_jimage:
        has_lib_graal = _get_libgraal_component() is not None
        components_with_jimage_jars = default_jvm_components()
        if not has_lib_graal:
            cmpee = get_component('cmpee', fatalIfMissing=False, stage1=False)
            if cmpee is not None:
                components_with_jimage_jars += [cmpee]
            else:
                cmp = get_component('cmp', fatalIfMissing=False, stage1=False)
                if cmp is not None:
                    components_with_jimage_jars += [cmp]
        java_standalone_jimage_jars = set()
        for component in GraalVmLayoutDistribution._add_dependencies(components_with_jimage_jars):
            java_standalone_jimage_jars.update(component.boot_jars + component.jvmci_parent_jars)
            if isinstance(component, mx_sdk.GraalVmJvmciComponent):
                java_standalone_jimage_jars.update(component.jvmci_jars)
        java_standalone_jimage = GraalVmJImage(
            suite=_suite,
            name='java-standalone-jimage',
            jimage_jars=sorted(java_standalone_jimage_jars),
            jimage_ignore_jars=sorted(final_graalvm_distribution.jimage_ignore_jars),
            workingSets=None,
            defaultBuild=False,
            missing_export_target_action='warn',
            default_to_jvmci='lib' if has_lib_graal else False,
        )
        register_project(java_standalone_jimage)

    if needs_stage1:
        if register_project:
            for component in registered_graalvm_components(stage1=True):
                if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                    config_class = GraalVmLanguageLauncher
                else:
                    config_class = GraalVmMiscLauncher
                for launcher_config in _get_launcher_configs(component):
                    register_project(config_class(component, launcher_config, stage1=True, defaultBuild=False))
            if get_component('svm', stage1=True):
                for component in registered_graalvm_components(stage1=False):
                    # native properties in the final distribution also need native properties in the stage1 distribution
                    for launcher_config in _get_launcher_configs(component):
                        register_project(GraalVmNativeProperties(component, launcher_config, stage1=True, defaultBuild=False))
                    for library_config in _get_library_configs(component):
                        register_project(GraalVmNativeProperties(component, library_config, stage1=True, defaultBuild=False))
        register_distribution(get_stage1_graalvm_distribution())

    if register_project:
        if needs_stage1:
            stage1_graalvm_distribution = get_stage1_graalvm_distribution()
            if _needs_stage1_jimage(stage1_graalvm_distribution, final_graalvm_distribution):
                register_project(GraalVmJImage(
                    suite=_suite,
                    name='graalvm-stage1-jimage',
                    jimage_jars=sorted(stage1_graalvm_distribution.jimage_jars),
                    jimage_ignore_jars=sorted(stage1_graalvm_distribution.jimage_ignore_jars),
                    workingSets=None,
                    default_to_jvmci=False,  # decide depending on the included modules
                    defaultBuild=False,
                ))
        final_jimage_project = GraalVmJImage(
            suite=_suite,
            name='graalvm-jimage',
            jimage_jars=sorted(final_graalvm_distribution.jimage_jars),
            jimage_ignore_jars=sorted(final_graalvm_distribution.jimage_ignore_jars),
            workingSets=None,
            default_to_jvmci=_get_libgraal_component() is not None,
        )
        register_project(final_jimage_project)

        for jmod_file, library_projects in modified_jmods.items():
            register_project(JmodModifier(
                jmod_file=jmod_file,
                library_projects=library_projects,
                jimage_project=final_jimage_project,
        ))

    # Trivial distributions to trigger the build of the final GraalVM distribution
    for label, dists in main_dists.items():
        if dists:
            register_distribution(mx.LayoutDirDistribution(
                suite=_suite,
                name=label.upper(),
                deps=dists,
                layout={
                    "./deps": "string:" + ",".join(dists),
                },
                path=None,
                platformDependent=False,
                theLicense=None,
                defaultBuild=False,
            ))


def _needs_stage1_jimage(stage1_dist, final_dist):
    assert isinstance(stage1_dist.jimage_jars, set) and isinstance(final_dist.jimage_jars, set)
    return stage1_dist.jimage_jars != final_dist.jimage_jars


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


def get_native_image_locations(component, image_name, fatal_if_missing=True):
    """
    :type component: mx_sdk.GraalVmComponent | str
    :type image_name: str
    :type fatal_if_missing: bool
    :rtype: str | None
    """
    def _get_library_config():
        name = mx.add_lib_prefix(mx.add_lib_suffix(remove_lib_prefix_suffix(image_name, require_suffix_prefix=False)))
        configs = _get_library_configs(component)
        return _get_config(name, configs)

    def _get_launcher_config():
        name = mx.exe_suffix(remove_exe_suffix(image_name, require_suffix=False))
        configs = _get_launcher_configs(component)
        return _get_config(name, configs)

    def _get_config(name, configs):
        configs = [c for c in configs if basename(c.destination) == name]
        if configs:
            if len(configs) > 1:
                raise mx.abort("Found multiple locations for '{}' in '{}': {}".format(image_name, component.name, configs))
            return configs[0]
        return None

    component = component if isinstance(component, mx_sdk.GraalVmComponent) else get_component(component)
    graalvm_dist = get_final_graalvm_distribution()
    source_location = None
    lib_config = _get_library_config()
    if lib_config:
        source_type = 'skip' if _skip_libraries(lib_config) else 'dependency'
        source_location = graalvm_dist.find_single_source_location(source_type + ':' + GraalVmLibrary.project_name(lib_config), abort_on_multiple=True)
    if source_location is None:
        launcher_config = _get_launcher_config()
        if launcher_config:
            source_location = graalvm_dist.find_single_source_location('dependency:' + GraalVmNativeImage.project_name(launcher_config), fatal_if_missing=False, abort_on_multiple=True)
    if source_location is None and fatal_if_missing:
        raise mx.abort("Cannot find native image location of '{}' in '{}'".format(image_name, component.name))
    return source_location if source_location is None else join(graalvm_output_root(), source_location)


def get_all_native_image_locations(include_libraries=True, include_launchers=True, abs_path=False):
    configs = []
    for component in mx_sdk_vm.graalvm_components():
        if include_libraries:
            configs += [c for c in _get_library_configs(component) if not _skip_libraries(c)]
        if include_launchers:
            configs += [c for c in _get_launcher_configs(component) if not _force_bash_launchers(c)]
    graalvm_dist = get_final_graalvm_distribution()
    locations = [graalvm_dist.find_single_source_location('dependency:' + GraalVmNativeImage.project_name(config)) for config in configs]
    if abs_path:
        locations = [join(graalvm_output_root(), location) for location in locations]
    return locations


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


def graalvm_version(version_type):
    """
    Example: 17.0.1-dev+4.1
    :type version_type: str
    :rtype: str
    """
    global _base_jdk_version_info

    assert version_type in ['graalvm', 'vendor', 'base-dir'], version_type

    def base_jdk_version_info():
        """
        :rtype: (str, str, str, str)
        """
        out = mx.OutputCapture()
        with mx.DisableJavaDebugging():
            code = mx_sdk_vm.base_jdk().run_java(['-Xlog:disable', join(_suite.mxDir, 'vm', 'java', 'src', 'JDKVersionInfo.java')], out=out, err=out)
        if code == 0:
            m = re.search(r'JDK_VERSION_INFO="(?P<java_vnum>[^\|]*)\|(?P<java_pre>[^\|]*)\|(?P<java_build>[^\|]*)|(?P<java_opt>[^\|]*)"', out.data, re.DOTALL)
            if m:
                return m.group('java_vnum', 'java_pre', 'java_build', 'java_opt')
        raise mx.abort(f'VM info extraction failed. Exit code: {code}\nOutput: {out.data}')

    if version_type == 'graalvm':
        return _suite.release_version()
    else:
        if _base_jdk_version_info is None:
            _base_jdk_version_info = base_jdk_version_info()

        java_vnum, java_pre, java_build, _ = _base_jdk_version_info
        if version_type == 'vendor':
            graalvm_pre = '' if _suite.is_release() else '-dev'
            if java_pre:
                graalvm_pre += '.' if graalvm_pre else '-'
                graalvm_pre += java_pre
        else:
            assert version_type == 'base-dir', version_type
            graalvm_pre = ''
        # Ignores `java_opt` (`Runtime.version().optional()`)
        #
        # Examples:
        #
        # ```
        # openjdk version "17.0.7" 2023-04-18
        # OpenJDK Runtime Environment (build 17.0.7+4-jvmci-23.0-b10)
        # ```
        # -> `17.0.7-dev+4.1`
        #
        # ```
        # openjdk version "21-ea" 2023-09-19
        # OpenJDK Runtime Environment (build 21-ea+16-1326)
        # ```
        # -> `21-dev.ea+16.1`
        return '{java_vnum}{graalvm_pre}{java_build}.{release_build}'.format(
            java_vnum=java_vnum,
            graalvm_pre=graalvm_pre,
            java_build=java_build,
            release_build=mx_sdk_vm.release_build
        )


def graalvm_home(stage1=False, fatalIfMissing=False):
    _graalvm_dist = get_stage1_graalvm_distribution() if stage1 else get_final_graalvm_distribution()
    _graalvm_home = join(_graalvm_dist.output, _graalvm_dist.jdk_base)
    if fatalIfMissing and not exists(_graalvm_home):
        mx.abort("{}GraalVM home '{}' does not exist. Did you forget to build with this set of dynamic imports and mx options?".format('Stage1 ' if stage1 else '', _graalvm_home))
    return _graalvm_home


def graalvm_home_from_env(extra_mx_args, env, stage1=False, suite=None):
    args = ['--quiet'] + extra_mx_args + ['graalvm-home'] + (['--stage1'] if stage1 else [])
    out = mx.OutputCapture()
    err = mx.OutputCapture()
    exit_status = mx.run_mx(args, suite=suite, out=out, err=err, env=env, nonZeroIsFatal=False)
    if exit_status:
        mx.warn(f"'mx {mx.list_to_cmd_line(args)}' returned {exit_status}. Stdout:\n{out.data.strip()}\nStderr: {err.data.strip()}")
        mx.abort(exit_status)
    return out.data.strip()


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
    parser = ArgumentParser(prog='mx graalvm-version', description='Print the GraalVM version', formatter_class=RawTextHelpFormatter)
    parser.add_argument('--type', default='graalvm', choices=['graalvm', 'vendor', 'base-dir'], help=textwrap.dedent("""\
        'graalvm' taken from the 'suite.py' file of the 'sdk' suite;
        'vendor' is an extension of the base-JDK version;
        'base-dir' similar to 'vendor', without extra identifiers like 'dev' or 'ea';"""))
    args = parser.parse_args(args)
    print(graalvm_version(args.type))


def print_graalvm_home(args):
    """print the GraalVM home dir"""
    parser = ArgumentParser(prog='mx graalvm-home', description='Print the GraalVM home directory')
    parser.add_argument('--stage1', action='store_true', help='show the home directory of the stage1 distribution')
    args = parser.parse_args(args)
    print(graalvm_home(stage1=args.stage1, fatalIfMissing=False))


def print_graalvm_type(args):
    """print the GraalVM artifact type"""
    # Required by the CI jsonnet files that trigger structure checks
    print('release' if _suite.is_release() else 'snapshot')


def _infer_env(graalvm_dist):
    dynamicImports = set()
    components = []
    for component in registered_graalvm_components():
        components.append(component.short_name)
        suite = component.suite
        if suite.dir == suite.vc_dir:
            dynamicImports.add(os.path.basename(suite.dir))
        else:
            dynamicImports.add("/" + os.path.basename(suite.dir))
    excludeComponents = []

    nativeImages = []
    for p in _suite.projects:
        if isinstance(p, GraalVmLauncher) and p.get_containing_graalvm() == graalvm_dist:
            if p.is_native():
                nativeImages.append(p.native_image_name)
        elif not graalvm_dist.stage1 and isinstance(p, GraalVmLibrary):
            if not p.is_skipped():
                library_name = remove_lib_prefix_suffix(p.native_image_name, require_suffix_prefix=False)
                nativeImages.append('lib:' + library_name)
    if nativeImages:
        if mx.suite('substratevm-enterprise', fatalIfMissing=False) is not None:
            dynamicImports.add('/substratevm-enterprise')
        elif mx.suite('substratevm', fatalIfMissing=False) is not None:
            dynamicImports.add('/substratevm')
    else:
        nativeImages = ['false']

    non_rebuildable_images = _non_rebuildable_images()
    if isinstance(non_rebuildable_images, bool):
        non_rebuildable_images = [str(non_rebuildable_images)]

    extra_image_builder_args = _parse_cmd_arg('extra_image_builder_argument', env_var_name='EXTRA_IMAGE_BUILDER_ARGUMENTS', separator=None, parse_bool=False) or []

    return sorted(list(dynamicImports)), sorted(components), sorted(excludeComponents), sorted(nativeImages), sorted(non_rebuildable_images), _debuginfo_dists(), _no_licenses(), sorted(extra_image_builder_args)


def graalvm_clean_env(out_env=None):
    """
    Returns an env var that does not define variables that configure the GraalVM
    """
    env = out_env or os.environ.copy()
    for env_var in ['DYNAMIC_IMPORTS', 'COMPONENTS', 'NATIVE_IMAGES', 'EXCLUDE_COMPONENTS', 'NON_REBUILDABLE_IMAGES', 'BUILD_TARGETS', 'MX_ENV_PATH', 'MX_PRIMARY_SUITE_PATH']:
        if env_var in env:
            env.pop(env_var)
    return env


def graalvm_env(out_env=None):
    """
    Gets an environment that captures variables configuring the GraalVM configured in the current mx environment.

    :out_env dict or None: the dict into which the variables will be added. A copy of `os.environ` is used if None.
    :return: a tuple of the current GraalVM distribution and an environment of the variables reflecting its configuration
    """
    env = out_env or os.environ.copy()
    graalvm_dist = get_final_graalvm_distribution()
    dynamicImports, components, exclude_components, nativeImages, non_rebuildable_images, debuginfo_dists, noLicenses, extra_image_builder_args = _infer_env(graalvm_dist)

    env['GRAALVM_HOME'] = graalvm_home()

    env['DYNAMIC_IMPORTS'] = ','.join(dynamicImports)
    env['COMPONENTS'] = ','.join(components)
    env['NATIVE_IMAGES'] = ','.join(nativeImages)
    env['EXCLUDE_COMPONENTS'] = ','.join(exclude_components)
    env['NON_REBUILDABLE_IMAGES'] = ','.join(non_rebuildable_images)
    env['EXTRA_IMAGE_BUILDER_ARGUMENTS'] = ' '.join(extra_image_builder_args)
    if debuginfo_dists:
        env['DEBUGINFO_DISTS'] = 'true'
    if noLicenses:
        env['NO_LICENSES'] = 'true'
    return graalvm_dist, env

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

    graalvm_dist, env = graalvm_env()

    # Disable loading of the global ~/.mx/env file in the subshell. The contents of this file are already in the current
    # environment. Parsing the ~/.mx/env file again would lead to confusing results, especially if it contains settings
    # for any of the variables we are modifying here.
    env['MX_GLOBAL_ENV'] = ''

    mx.log("Entering {}... (close shell to leave, e.g. ``exit``)".format(graalvm_dist))
    mx.run(args.cmd, env=env)


def graalvm_show(args, forced_graalvm_dist=None):
    """print the GraalVM config

    :param forced_graalvm_dist: the GraalVM distribution whose config is printed. If None, then the
                         config of the global stage1 or final GraalVM distribution is printed.
    """
    parser = ArgumentParser(prog='mx graalvm-show', description='Print the GraalVM config')
    parser.add_argument('--stage1', action='store_true', help='show the components for stage1')
    parser.add_argument('--print-env', action='store_true', help='print the contents of an env file that reproduces the current GraalVM config')
    parser.add_argument('-v', '--verbose', action='store_true', help='print additional information')
    args = parser.parse_args(args)

    graalvm_dist = forced_graalvm_dist or (get_stage1_graalvm_distribution() if args.stage1 else get_final_graalvm_distribution())
    print("GraalVM distribution: {}".format(graalvm_dist))
    print("Version: {}".format(_suite.release_version()))
    print("Config name: {}".format(graalvm_dist.vm_config_name))
    print("Components:")
    for component in sorted(graalvm_dist.components, key=lambda c: c.name):
        print(" - {} ('{}', /{}, {})".format(component.name, component.short_name, component.dir_name, _get_component_stability(component)))

    if forced_graalvm_dist is None:
        # Custom GraalVM distributions with a forced component list do not yet support launchers and libraries.
        # No standalone is derived from them.
        launchers = [p for p in _suite.projects if isinstance(p, GraalVmLauncher) and p.get_containing_graalvm() == graalvm_dist]
        if launchers:
            print("Launchers:")
            for launcher in sorted(launchers, key=lambda l: l.native_image_name):
                suffix = ''
                name = GraalVmNativeProperties.canonical_image_name(launcher.native_image_config)
                profile_cnt = len(_image_profiles(name))
                if profile_cnt > 0:
                    suffix += " ({} pgo profile file{})".format(profile_cnt, 's' if profile_cnt > 1 else '')
                extra_args = _extra_image_builder_args(name)
                if extra_args:
                    suffix += " (" + mx.list_to_cmd_line(extra_args) + ")"
                print(" - {name} ({native}, {rebuildable}){suffix}".format(
                    name=launcher.native_image_name,
                    native="native" if launcher.is_native() else "bash",
                    rebuildable="rebuildable" if _rebuildable_image(launcher.native_image_config) else "non-rebuildable",
                    suffix=suffix
                ))
        else:
            print("No launcher")

        libraries = [p for p in _suite.projects if isinstance(p, GraalVmLibrary)]
        if libraries and not args.stage1:
            print("Libraries:")
            for library in sorted(libraries, key=lambda l: l.native_image_name):
                suffix = ' ('
                if library.is_skipped():
                    suffix += "skipped, "
                else:
                    suffix += "native, "
                if _rebuildable_image(library.native_image_config):
                    suffix += "rebuildable)"
                else:
                    suffix += "non-rebuildable)"
                name = GraalVmNativeProperties.canonical_image_name(library.native_image_config)
                profile_cnt = len(_image_profiles(name))
                if profile_cnt > 0:
                    suffix += " ({} pgo profile file{})".format(profile_cnt, 's' if profile_cnt > 1 else '')
                extra_args = _extra_image_builder_args(name)
                if extra_args:
                    suffix += " (" + mx.list_to_cmd_line(extra_args) + ")"
                print(" - {name}{suffix}".format(
                    name=library.native_image_name,
                    suffix=suffix))
        else:
            print("No library")

        if not args.stage1:
            jvm_configs = {}
            for component in graalvm_dist.components:
                for jvm_config in component.jvm_configs:
                    priority = jvm_config['priority']
                    if callable(priority):
                        priority = priority()
                    jvm_configs[priority] = {
                        'configs': jvm_config['configs'],
                        'source': component.name,
                    }
            if jvm_configs:
                jvm_configs[0] = {
                    'configs': ['<original VMs>'],
                    'source': _get_jvm_cfg(),
                }
                print("JVMs:")
                for _, cfg in sorted(jvm_configs.items()):
                    for config in cfg['configs']:
                        print(f" {config} (from {cfg['source']})")
            if args.verbose:
                for dist_name in 'GRAALVM':
                    dist = mx.distribution(dist_name, fatalIfMissing=False)
                    if dist is not None:
                        print(f"Dependencies of the '{dist_name}' distribution:\n -", '\n - '.join(sorted(dep.name for dep in dist.deps)))

        if args.print_env:
            def _print_env(name, val, separator=','):
                if val:
                    print(name + '=' + separator.join(val))
            print('Inferred env file:')
            dynamic_imports, components, exclude_components, native_images, non_rebuildable_images, debuginfo_dists, no_licenses, extra_image_builder_args = _infer_env(graalvm_dist)
            _print_env('DYNAMIC_IMPORTS', dynamic_imports)
            _print_env('COMPONENTS', components)
            _print_env('EXCLUDE_COMPONENTS', exclude_components)
            _print_env('NATIVE_IMAGES', native_images)
            _print_env('NON_REBUILDABLE_IMAGES', non_rebuildable_images)
            _print_env('EXTRA_IMAGE_BUILDER_ARGUMENTS', extra_image_builder_args, separator=' ')
            if debuginfo_dists:
                print('DEBUGINFO_DISTS=true')
            if no_licenses:
                print('NO_LICENSES=true')


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


def check_versions(jdk, expect_graalvm, check_jvmci):
    """
    :type jdk: mx.JDKConfig | str
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

    out = subprocess.check_output([jdk.java, '-version'], stderr=subprocess.STDOUT).decode().rstrip()

    jdk_version = jdk.version
    if os.environ.get('JDK_VERSION_CHECK', None) != 'ignore' and (jdk_version <= mx.VersionSpec('1.8') or mx.VersionSpec('9') <= jdk_version < mx.VersionSpec('11')):
        mx.abort("GraalVM requires >=JDK11 as base-JDK, while the selected JDK ('{}') is '{}':\n{}\n\n{}.".format(jdk.home, jdk_version, out, check_env))

    # Benchmarks can be executed with --jvm=native-image-java-home, in which case the JAVA_HOME points to GraalVM and it is correct.
    if not expect_graalvm and "GraalVM" in out and 'benchmark' not in sys.argv:
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
    out = subprocess.check_output([jdk.java, '-version'], stderr=subprocess.STDOUT).decode().rstrip()
    match = re.search(r'^(?P<base_vm_name>[a-zA-Z() ]+64-Bit Server VM )', out.split('\n')[-1])
    vm_name = match.group('base_vm_name') if match else ''
    return vm_name + graalvm_vendor_version()

def graalvm_vendor_version():
    """
    :rtype str:
    """
    # Examples:
    # GraalVM CE 17.0.1+4.1
    # Oracle GraalVM 17.0.1+4.1
    return '{vendor} {version}'.format(
        vendor=('Oracle ' + _graalvm_base_name) if mx_sdk_vm.ee_implementor() else (_graalvm_base_name + ' CE'),
        version=graalvm_version(version_type='vendor')
    )


# GR-37542 current debug info on darwin bloats binary (stripping to a separate .dSYM folder is not implemented) and
# doesn't bring much benefit yet
_debuginfo_default = not mx.is_darwin()

mx.add_argument('--components', action='store', help='Comma-separated list of component names to build. Only those components and their dependencies are built. suite:NAME can be used to exclude all components of a suite.')
mx.add_argument('--exclude-components', action='store', help='Comma-separated list of component names to be excluded from the build. suite:NAME can be used to exclude all components of a suite.')
mx.add_argument('--debug-images', action='store_true', help='Build native images in debug mode: \'-O0\' and with \'-ea\'.')
mx.add_argument('--native-images', action='store', help='Comma-separated list of launchers and libraries (syntax: LAUNCHER_NAME or lib:jsvm or suite:NAME) to build with Native Image.')
mx.add_argument('--non-rebuildable-images', action='store', help='Comma-separated list of launchers and libraries (syntax: LAUNCHER_NAME or lib:jsvm or suite:NAME) in the final GraalVM that cannot be rebuilt using Native Image.')
mx.add_argument('--force-bash-launchers', action='store', help='Force the use of bash launchers instead of native images.'
                                                               'This can be a comma-separated list of disabled launchers or `true` to disable all native launchers.', default=None)
mx.add_argument('--skip-libraries', action='store', help='Do not build native images for these libraries.'
                                                         'This can be a comma-separated list of disabled libraries or `true` to disable all libraries.', default=None)
mx.add_argument('--sources', action='store', help='Comma-separated list of projects and distributions of open-source components for which source file archives must be included' + (' (all by default).' if _debuginfo_default else '.'), default=None)
mx.add_argument('--debuginfo-dists', action='store_true', help='Generate debuginfo distributions.')
mx.add_argument('--generate-debuginfo', action='store', help='Comma-separated list of launchers and libraries (syntax: lib:jsvm) for which to generate debug information (`native-image -g`) (all by default)', default=None)
mx.add_argument('--disable-debuginfo-stripping', action='store_true', help='Disable the stripping of debug symbols from the native image.')
mx.add_argument('--extra-image-builder-argument', action='append', help='Add extra arguments to the image builder.', default=[])
mx.add_argument('--image-profile', action='append', help='Add a profile to be used while building a native image.', default=[])
mx.add_argument('--no-licenses', action='store_true', help='Do not add license files in the archives.')
mx.add_argument('--base-jdk-info', action='store', help='Colon-separated tuple of base JDK `NAME:VERSION`, to be added on deployment to the \'basejdk\' attribute of the \'suite-revisions.xml\' file on maven-deployment.')
mx.add_argument('--graalvm-skip-archive', action='store_true', help='Do not archive GraalVM distributions.')
mx.add_argument('--svmtest-target-arch', action='store', dest='svmtest_target_arch', help='specify targeted arch for GraalVM output', default=mx.get_arch())
mx.add_argument('--default-jlink-missing-export-action', choices=['create', 'error', 'warn', 'none'], default='create', help='The action to perform for a qualified export that targets a module not included in the runtime image.')


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
    included = _parse_cmd_arg('components', parse_bool=True, default_value=None)
    if included is None:
        return None
    if isinstance(included, bool):
        return mx_sdk_vm.graalvm_components() if included else []
    components = []
    for name in included:
        if name.startswith('suite:'):
            suite_name = name[len('suite:'):]
            components.extend([c for c in mx_sdk_vm.graalvm_components() if c.suite.name == suite_name])
        else:
            component = mx_sdk.graalvm_component_by_name(name, False)
            if component:
                components.append(component)
            else:
                mx.warn("The component inclusion list ('--components' or '$COMPONENTS') includes an unknown component: '{}'".format(name))
    return components


def _excluded_components():
    excluded = _parse_cmd_arg('exclude_components', parse_bool=False, default_value='')

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
    # separator=None means any whitespace and there will be no empty elements
    extra_args = _parse_cmd_arg('extra_image_builder_argument', env_var_name='EXTRA_IMAGE_BUILDER_ARGUMENTS', separator=None, parse_bool=False) or []
    for arg in extra_args:
        if arg.startswith(prefix):
            args.append(arg[prefix_len:])
        elif arg.startswith('-'):
            args.append(arg)
    return args


def _image_profiles(image):
    prefix = image + ':'
    prefix_len = len(prefix)
    profiles = []
    for arg in _parse_cmd_arg('image_profile', env_var_name='IMAGE_PROFILES', separator=';', parse_bool=False, default_value=''):
        if arg.startswith(prefix):
            profiles += arg[prefix_len:].split(',')
    return profiles

def _multitarget_libc_selection():
    full_spec = mx_native.TargetSelection.get_selection()
    if len(full_spec) > 1:
        mx.logv(f"Multiple targets selected: {full_spec} (first one wins)")
    full_spec = full_spec[0]
    ret = full_spec.libc
    if full_spec.variant is not None:
        ret = f"{ret}-{full_spec.variant}"
    return ret

mx_subst.results_substitutions.register_no_arg('multitarget_libc_selection', _multitarget_libc_selection)

def _no_licenses():
    return mx.get_opts().no_licenses or _env_var_to_bool('NO_LICENSES')


def graalvm_skip_archive():
    return mx.get_opts().graalvm_skip_archive or _env_var_to_bool('GRAALVM_SKIP_ARCHIVE')


def _expand_native_images_list(only):
    if isinstance(only, list):
        native_images = []
        for name in only:
            if name.startswith('suite:'):
                suite_name = name[len('suite:'):]
                for c in registered_graalvm_components(stage1=False):
                    if c.suite.name == suite_name:
                        for config in _get_launcher_configs(c):
                            native_images.append(_get_launcher_name(config))
                        for config in _get_library_configs(c):
                            native_images.append(_get_library_name(config))
            else:
                native_images.append(name)
        return native_images
    else:
        return only


def _force_bash_launchers(launcher, build_by_default=None):
    """
    :type launcher: str | mx_sdk.LauncherConfig
    """
    launcher_name = _get_launcher_name(launcher)

    forced = _parse_cmd_arg('force_bash_launchers')
    if build_by_default is None:
        build_by_default = has_vm_suite() or forced is not None # for compatibility with legacy behavior
    only = _parse_cmd_arg('native_images', default_value=str(build_by_default))
    only = _expand_native_images_list(only)
    if isinstance(only, bool):
        included = only
    else:
        included = launcher_name in only

    if forced is True:
        included = False
    elif isinstance(forced, list) and launcher_name in forced:
        included = False

    return not included


def _skip_libraries(library, build_by_default=None):
    """
    :type library: str | mx_sdk.LibraryConfig
    """
    library_name = _get_library_name(library)

    skipped = _parse_cmd_arg('skip_libraries')
    if build_by_default is None:
        build_by_default = has_vm_suite() or skipped is not None # for compatibility with legacy behavior
    only = _parse_cmd_arg('native_images', default_value=str(build_by_default))
    only = _expand_native_images_list(only)
    if isinstance(only, bool):
        included = only
    else:
        only = [lib[4:] for lib in only if lib.startswith('lib:')]
        included = library_name in only

    if skipped is True:
        included = False
    elif isinstance(skipped, list) and library_name in skipped:
        included = False

    return not included


def _get_launcher_name(image_config):
    """
    :type launcher: str | mx_sdk.LauncherConfig
    """
    if isinstance(image_config, mx_sdk.LauncherConfig):
        destination = image_config.destination
    elif isinstance(image_config, str):
        destination = image_config
    else:
        raise mx.abort('Unknown launcher config type: {}'.format(type(image_config)))

    return basename(remove_exe_suffix(destination, require_suffix=False))


def _get_library_name(image_config):
    """
    :type library: str | mx_sdk.LibraryConfig
    """
    if isinstance(image_config, mx_sdk.LibraryConfig):
        destination = image_config.destination
    elif isinstance(image_config, str):
        destination = image_config
    else:
        raise mx.abort('Unknown library config type: {}'.format(type(image_config)))

    return remove_lib_prefix_suffix(basename(destination), require_suffix_prefix=False)


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

def _get_libgraal_component():
    """
    Returns the LibGraal component, if any, that is part of the final GraalVM distribution.
    :rtype:mx_sdk_vm.GraalVmJreComponent
    """
    if mx.suite('substratevm', fatalIfMissing=False) is not None:
        try:
            import mx_substratevm
        except ModuleNotFoundError:
            return None
        # Use `short_name` rather than `name` since the code that follows
        # should be executed also when "LibGraal Enterprise" is registered
        libgraal_component = get_component(mx_substratevm.libgraal.short_name, fatalIfMissing=False, stage1=False)
        if libgraal_component is not None and not _has_skipped_libraries(libgraal_component):
            return libgraal_component
    return None

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


def _debuginfo_dists():
    return mx.get_opts().debuginfo_dists or _env_var_to_bool('DEBUGINFO_DISTS')


def _generate_debuginfo(image_config):
    generate_debuginfo = _parse_cmd_arg('generate_debuginfo')
    if generate_debuginfo is None:
        return _debuginfo_default or _debug_images()
    elif isinstance(generate_debuginfo, bool):
        return generate_debuginfo
    else:
        if isinstance(image_config, str):
            name = image_config
        else:
            destination = image_config.destination if isinstance(image_config, mx_sdk.AbstractNativeImageConfig) else image_config
            if isinstance(image_config, mx_sdk.LauncherConfig):
                name = basename(remove_exe_suffix(destination, require_suffix=False))
            elif isinstance(image_config, mx_sdk.LibraryConfig):
                name = remove_lib_prefix_suffix(basename(destination), require_suffix_prefix=False)
                generate_debuginfo = [lib[4:] for lib in generate_debuginfo if lib.startswith('lib:')]
        return name in generate_debuginfo


def _non_rebuildable_images():
    return _parse_cmd_arg('non_rebuildable_images', default_value=str(False))


def _rebuildable_image(image_config):
    """
    :type image_config: mx_sdk.AbstractNativeImageConfig
    :rtype: bool
    """
    if isinstance(image_config, mx_sdk_vm.LauncherConfig):
        name = _get_launcher_name(image_config)
    elif isinstance(image_config, mx_sdk_vm.LibraryConfig):
        name = 'lib:' + _get_library_name(image_config)
    else:
        raise mx.abort('Unknown image config type: {}'.format(type(image_config)))

    non_rebuildable = _non_rebuildable_images()
    non_rebuildable = _expand_native_images_list(non_rebuildable)
    if isinstance(non_rebuildable, bool):
        return not non_rebuildable
    else:
        return name not in non_rebuildable


def _base_jdk_info():
    base_jdk_info = mx.get_opts().base_jdk_info or mx.get_env('BASE_JDK_INFO')
    if base_jdk_info is None:
        return base_jdk_info
    elif base_jdk_info.count(':') != 1:
        mx.abort("Unexpected base JDK info: '{}'. Expected format: 'NAME:VERSION'.".format(base_jdk_info))
    else:
        return base_jdk_info.split(':')


def default_jlink_missing_export_action():
    return mx.get_opts().default_jlink_missing_export_action or mx.get_env('DEFAULT_JLINK_MISSING_EXPORT_ACTION')


def mx_post_parse_cmd_line(args):
    for component in registered_graalvm_components():
        for boot_jar in component.boot_jars:
            if not mx_javamodules.get_module_name(mx.distribution(boot_jar)):
                mx.abort("Component '{}' declares a boot jar distribution ('{}') that does not define a module.\nPlease set 'moduleInfo' or 'moduleName'.".format(component.name, boot_jar))


mx.update_commands(_suite, {
    'graalvm-components': [print_graalvm_components, ''],
    'graalvm-dist-name': [print_graalvm_dist_name, ''],
    'graalvm-version': [print_graalvm_version, ''],
    'graalvm-home': [print_graalvm_home, ''],
    'graalvm-type': [print_graalvm_type, ''],
    'graalvm-enter': [graalvm_enter, ''],
    'graalvm-show': [graalvm_show, ''],
    'graalvm-vm-name': [print_graalvm_vm_name, ''],
})
