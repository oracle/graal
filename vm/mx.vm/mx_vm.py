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

import mx, mx_subst, mx_sdk
import os

_suite = mx.suite('vm')

class GraalVmLayoutDistribution(mx.LayoutTARDistribution):
    def __init__(self, suite, name, deps, exclLibs, platformDependent, theLicense, testDistribution, layout=None, path=None, **kwArgs):
        def _get_component_id(self, comp=None, **kwargs):
            return comp.id

        def _get_languages_or_tool(self, comp=None, **kwargs):
            if isinstance(comp, mx_sdk.GraalVmLanguage):
                return 'languages'
            if isinstance(comp, mx_sdk.GraalVmTool):
                return 'tools'
            return None

        def _get_support(self, comp=None, start=None, **kwargs):
            foo = os.path.relpath(os.path.join('jre', _get_languages_or_tool(None, comp=comp), _get_component_id(None, comp=comp)), start=start)
            return foo

        _layout_value_subst = mx_subst.SubstitutionEngine()
        _layout_value_subst.register_with_arg('comp_id', _get_component_id, keywordArgs=True)
        _layout_value_subst.register_with_arg('languages_or_tools', _get_languages_or_tool, keywordArgs=True)

        _component_path_subst = mx_subst.SubstitutionEngine()
        _component_path_subst.register_with_arg('support', _get_support, keywordArgs=True)

        # Add base JDK
        _add(layout, './', 'file:{}/*'.format(_get_jdk_dir()))

        # Add compiler_name file
        _compiler_name = 'graal-enterprise' if mx.suite('graal-enterprise', fatalIfMissing=False) else 'graal'
        _add(layout, './jre/lib/jvmci/compiler_name', 'string:{}'.format(_compiler_name))

        # Add the rest of the GraalVM
        _layout_dict = {
            'documentation_files': ['docs/<comp_id>/'],
            'license_files': ['./'],
            'third_party_license_files': ['./'],
            'provided_executables': ['bin/', 'jre/bin/'],
            'boot_jars': ['jre/lib/boot/'],
            'truffle_jars': ['jre/<languages_or_tools>/<comp_id>/'],
            'support_distributions': ['jre/<languages_or_tools>/<comp_id>/'],
            'jvmci_jars': ['jre/lib/jvmci/'],
        }

        _layout_types_adding_deps = ['dependency', 'extracted-dependency']

        for _component in mx_sdk.graalvm_components():
            mx.log('Adding {} to the {} {}'.format(_component.name, name, self.__class__.__name__))
            for _layout_key, _layout_values in _layout_dict.iteritems():
                assert isinstance(_layout_values, list), 'Layout value of \'{}\' has the wrong type. Expected: \'list\'; got \'{}\''.format(_layout_key, _layout_values)
                _component_paths = getattr(_component, _layout_key, [])
                for _component_path in _component_paths:
                    for _layout_value in _layout_values:
                        _dest = _layout_value_subst.substitute(_layout_value, comp=_component)
                        _path = _component_path_subst.substitute(_component_path, comp=_component, start=os.path.dirname(_dest))
                        _add(layout, _dest, _path)

        super(GraalVmLayoutDistribution, self).__init__(suite, name, deps, layout, path, platformDependent, theLicense, exclLibs, testDistribution=testDistribution, **kwArgs)
        mx.logv('\'{}\' has layout \'{}\''.format(self.name, self.layout))

def _get_jdk_dir():
    java_home = mx.get_jdk(tag='default').home
    jdk_dir = java_home
    if jdk_dir.endswith(os.path.sep):
        jdk_dir = jdk_dir[:-len(os.path.sep)]
    if jdk_dir.endswith("/Contents/Home"):
        jdk_dir = jdk_dir[:-len("/Contents/Home")]
    return jdk_dir

def _add(layout, key, value):
    """
    :rtype layout: dict[str, list[str]]
    :rtype key: str
    :rtype value: list[str] or str
    """
    _val = value if isinstance(value, list) else [value] if value else []
    mx.logv('Adding \'{}: {}\' to the layout'.format(key, _val))
    layout.setdefault(key, []).extend(_val)
    mx.logvv('Layout: \'{}\''.format(layout))
