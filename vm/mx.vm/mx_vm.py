#
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function


import mx
import mx_gate
import mx_pomdistribution
import mx_sdk_vm, mx_sdk_vm_impl
import mx_vm_benchmark
import mx_vm_gate

from argparse import ArgumentParser
import os
import pathlib
from os.path import join, relpath

_suite = mx.suite('vm')
""":type: mx.SourceSuite | mx.Suite"""


@mx.command(_suite.name, "local-path-to-url")
def local_path_to_url(args):
    """Print the url representation of a canonicalized path"""
    parser = ArgumentParser(prog='mx local-path-to-url', description='Print the url representation of a canonicalized path')
    parser.add_argument('path', action='store', help='the path to canonicalize and return as url')
    args = parser.parse_args(args)

    print(pathlib.Path(os.path.realpath(args.path)).as_uri())


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmComponent(
    suite=_suite,
    name='GraalVM license files',
    short_name='gvm',
    dir_name='.',
    license_files=['LICENSE.txt'],
    third_party_license_files=['THIRD_PARTY_LICENSE.txt'],
    dependencies=[],
    support_distributions=['vm:VM_GRAALVM_SUPPORT'],
    stability="supported",
))


if mx.suite('tools', fatalIfMissing=False) is not None and mx.suite('graal-js', fatalIfMissing=False) is not None:
    mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmJdkComponent(
        suite=_suite,
        name='VisualVM',
        short_name='vvm',
        dir_name='visualvm',
        license_files=['LICENSE_VISUALVM.txt'],
        third_party_license_files=[],
        dependencies=['Graal.js'],
        support_distributions=['tools:VISUALVM_GRAALVM_SUPPORT'],
        provided_executables=[('tools:VISUALVM_PLATFORM_SPECIFIC', './bin/<exe:jvisualvm>')],
        stability="supported",
    ))


llvm_components = ['bgraalvm-native-binutil', 'bgraalvm-native-clang', 'bgraalvm-native-clang-cl', 'bgraalvm-native-clang++', 'bgraalvm-native-flang', 'bgraalvm-native-ld']

# pylint: disable=line-too-long
ce_unchained_components = ['bnative-image-configure', 'cmp', 'gvm', 'lg', 'ni', 'nic', 'nil', 'nr_lib_jvmcicompiler', 'sdkc', 'sdkni', 'ssvmjdwp', 'svm', 'svmjdwp', 'svmsl', 'svmt', 'tflc', 'tflsm']
ce_components_minimal = ['cmp', 'cov', 'dap', 'gvm', 'ins', 'insight', 'insightheap', 'lg', 'lsp', 'nfi-libffi', 'nfi', 'pro', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']
ce_components = ce_components_minimal + ['nr_lib_jvmcicompiler', 'bnative-image-configure', 'ni', 'nic', 'nil', 'svm', 'svmt', 'svmnfi', 'svmsl']
ce_python_components = ['antlr4', 'sllvmvm', 'cmp', 'cov', 'dap', 'dis', 'gvm', 'icu4j', 'xz', 'ins', 'insight', 'insightheap', 'lg', 'llp', 'llrc', 'llrl', 'llrlf', 'llrn', 'lsp', 'nfi-libffi', 'nfi', 'pro', 'pyn', 'pynl', 'rgx', 'sdk',
                        'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']
ce_fastr_components = ce_components + llvm_components + ['antlr4', 'xz', 'sllvmvm', 'llp', 'bnative-image', 'snative-image-agent', 'R', 'sRvm', 'bnative-image-configure', 'llrc', 'snative-image-diagnostics-agent', 'llrn', 'llrl', 'llrlf']
ce_no_native_components = ['cmp', 'cov', 'dap', 'gvm', 'ins', 'insight', 'insightheap', 'lsp', 'nfi-libffi', 'nfi', 'pro', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm', 'truffle-json']

# Main GraalVMs
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-win')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-aarch64')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-darwin')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce-darwin-aarch64')
mx_sdk_vm.register_vm_config('community', ce_unchained_components, _suite, env_file='ce')
# Other GraalVMs
mx_sdk_vm.register_vm_config('ce', ce_components + ['icu4j', 'xz', 'js', 'jsl', 'jss', 'rgx', 'bnative-image', 'snative-image-agent', 'snative-image-diagnostics-agent', 'tflsm'], _suite, dist_name='ce-js', env_file='ce-js')
mx_sdk_vm.register_vm_config('ce', ce_components + ['gwal', 'gwa', 'icu4j', 'xz', 'js', 'jsl', 'jss', 'njs', 'njsl', 'rgx', 'sjsvm', 'swasmvm', 'tflsm'], _suite, dist_name='ce', env_file='ce-nodejs')
mx_sdk_vm.register_vm_config('ce', ce_components_minimal + ['antlr4', 'llrn', 'llp', 'llrc', 'llrl', 'llrlf'], _suite, env_file='ce-llvm')
mx_sdk_vm.register_vm_config('ce-fastr', ce_fastr_components, _suite)
mx_sdk_vm.register_vm_config('ce-no_native', ce_no_native_components, _suite)
mx_sdk_vm.register_vm_config('libgraal', ['cmp', 'lg', 'sdkc', 'tflc'], _suite)
mx_sdk_vm.register_vm_config('libgraal-bash', llvm_components + ['cmp', 'gvm', 'lg', 'nfi-libffi', 'nfi', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file=False)

if mx.get_os() == 'windows':
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'cmp', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'nic', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file=False)
else:
    mx_sdk_vm.register_vm_config('svm', ['bnative-image', 'bnative-image-configure', 'cmp', 'gvm', 'nfi-libffi', 'nfi', 'ni', 'nil', 'nju', 'nic', 'rgx', 'sdk', 'sdkni', 'sdkc', 'sdkl', 'snative-image-agent', 'snative-image-diagnostics-agent', 'svm', 'svmt', 'svmnfi', 'svmsl', 'svml', 'tfl', 'tfla', 'tflc', 'tflm'], _suite, env_file=False)
# pylint: enable=line-too-long


mx_gate.add_gate_runner(_suite, mx_vm_gate.gate_body)


def mx_post_parse_cmd_line(args):
    mx_vm_benchmark.register_graalvm_vms()


# Lists all tool meta-POM distributions for which a corresponding meta-POM
# is generated under the 'org.graalvm.polyglot' Maven groupId and included in the
# TOOLS catalog meta-POM.
_tools_meta_distributions = [
    'tools:COVERAGE_POM',
    'tools:DAP_POM',
    'tools:HEAP_POM',
    'tools:INSPECT_POM',
    'tools:INSIGHT_POM',
    'tools:LSP_POM',
    'tools:PROFILER_POM',
]

# Lists all language meta-POM distributions for which a corresponding meta-POM
# is generated under the 'org.graalvm.polyglot' Maven groupId and included in the
# LANGUAGES catalog meta-POM.
_languages_meta_distributions = [
    'graal-js:JS_POM',
    'graalpython:PYTHON_POM',
    'sulong:LLVM_NATIVE_POM',
    'truffleruby:RUBY_POM',
    'wasm:WASM_POM',
]


def _distribution_license(dist):
    """
    Provides the distribution license if it's specified, or the default license of the suite that owns the distribution.
    :return: list of licenses
    :rtype: list[str]
    """
    _license = dist.theLicense
    if not _license:
        _license = dist.suite.defaultLicense
    return _license


def _deprecate(description, artifact_id):
    return f'Deprecated: Please use the \'org.graalvm.polyglot:{artifact_id}\' Maven coordinate instead. {description}'


def _trim_suffix(string, suffix):
    assert string.endswith(suffix), f'String {string} must end with {suffix}'
    return string[:-len(suffix)]


def _create_deprecated_community_distribution(base_distribution):
    base_name = _trim_suffix(base_distribution.name, 'POM')
    dist_name = base_name + 'COMMUNITY_DEPRECATED'
    groupId = base_distribution.maven_group_id()
    artifactId = base_distribution.maven_artifact_id()
    artifactTags = 'default'
    maven_data = getattr(base_distribution, 'maven')
    if isinstance(maven_data, dict) and 'tag' in maven_data:
        artifactTags = maven_data['tag']
    description = getattr(base_distribution, 'description')
    distDeps = []
    runtimeDeps = [base_distribution]
    licenses = set(_distribution_license(base_distribution))
    artifactId = artifactId + '-community'
    attrs = {
        'maven': {
            'groupId': groupId,
            'artifactId': artifactId,
            'tag': artifactTags,
        },
        'description': _deprecate(description, base_distribution.maven_artifact_id()),
    }
    return mx_pomdistribution.POMDistribution(_suite, dist_name, distDeps, runtimeDeps, sorted(list(licenses)), **attrs)


def register_tools_distribution(owner_suite, register_distribution):
    """
    Registers a dynamic TOOLS meta-POM distribution that aggregates all individual tool meta-POMs.
    For compatibility reasons, it also creates a legacy <TOOL>_COMMUNITY_DEPRECATED distribution
    with `<tool>-community` Maven artifact id for each tool.
    :type register_distribution: (mx.Distribution) -> None
    """
    tools_meta_poms = []
    tools_licenses = set()
    deprecated_tools_community_meta_poms = []
    for tool_name in _tools_meta_distributions:
        tool_distribution = mx.distribution(tool_name, fatalIfMissing=False)
        if tool_distribution:
            assert tool_distribution.isPOMDistribution(), f'TOOLS dependency {tool_distribution.name} must be a meta-POM distribution.'
            tools_meta_poms.append(tool_distribution)
            tools_licenses.update(_distribution_license(tool_distribution))
            deprecated_community_tool_distribution = _create_deprecated_community_distribution(tool_distribution)
            register_distribution(deprecated_community_tool_distribution)
            deprecated_tools_community_meta_poms.append(deprecated_community_tool_distribution)
    if tools_meta_poms:
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'tools',
                'tag': ['default', 'public'],
            },
            'description': 'This POM dependency includes all Truffle tools for Graal Languages.',
        }
        tools = mx_pomdistribution.POMDistribution(owner_suite, 'TOOLS', [], tools_meta_poms, sorted(list(tools_licenses)), **attrs)
        register_distribution(tools)
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'tools-community',
                'tag': ['default', 'public'],
            },
            'description': _deprecate('This POM dependency includes all Truffle tools for Graal Languages.', 'tools'),
        }
        deprecated_tools_community = mx_pomdistribution.POMDistribution(owner_suite, 'TOOLS_COMMUNITY_DEPRECATED', [], deprecated_tools_community_meta_poms, sorted(list(tools_licenses)), **attrs)
        register_distribution(deprecated_tools_community)


def create_polyglot_meta_pom_distribution_from_base_distribution(suite_local_meta_distribution):
    """
Creates a meta-POM distribution based on the `suite_local_meta_distribution` prototype with the Maven group id set to
'org.graalvm.polyglot'. The new distribution inherits properties from `suite_local_meta_distribution`
and includes it as a runtime dependency. The name of the new distribution is derived from `suite_local_meta_distribution`
by appending the '_POLYGLOT' suffix.

:param suite_local_meta_distribution: The language meta-POM distribution with the language suite group id.
"""
    dist_name = suite_local_meta_distribution.name + '_POLYGLOT'
    groupId = 'org.graalvm.polyglot'
    artifactId = suite_local_meta_distribution.maven_artifact_id()
    artifactTags = 'default'
    maven_data = getattr(suite_local_meta_distribution, 'maven')
    if isinstance(maven_data, dict) and 'tag' in maven_data:
        artifactTags = maven_data['tag']
    description = getattr(suite_local_meta_distribution, 'description')
    distDeps = []
    runtimeDeps = [suite_local_meta_distribution]
    licenses = _distribution_license(suite_local_meta_distribution)
    attrs = {
        'maven': {
            'groupId': groupId,
            'artifactId': artifactId,
            'tag': artifactTags,
        },
        'description': description,
    }
    return mx_pomdistribution.POMDistribution(_suite, dist_name, distDeps, runtimeDeps, sorted(list(licenses)), **attrs)


def register_languages_distribution(owner_suite, register_distribution,
                                    explicit_community_pom_distributions=None,
                                    vendor_specific_pom_distributions=None):
    """
    Registers a dynamic LANGUAGES meta-POM distribution that aggregates all individual language meta-POMs.
    This function also registers a deprecated `<LANGUAGE>_COMMUNITY_DEPRECATED` and
    `LANGUAGES_COMMUNITY_DEPRECATED` meta-POM distributions for compatibility, these compatibility distributions
     are using `<language>-community` as the Maven artifact ID.

    Language distributions are primarily resolved from `_languages_meta_distributions`. However, additional
    control can be provided via the following parameters:

    Parameters:
        owner_suite (mx.Suite): The owning suite that the meta-POM distributions will belong to.
        register_distribution (Callable[[mx.Distribution], None]): Function used to register each generated distribution.
        explicit_community_pom_distributions (Optional[Dict[str, str]]): A mapping for languages that maintain separate
            community and vendor-specific meta-POMs. Keys are the names of the community distributions (with Maven artifactId ending with `-community`),
            and values are names of the vendor-specific distributions or `None` if not applicable.
        vendor_specific_pom_distributions (Optional[Set[str]]): A set of distribution names that exist only in a vendor-specific
            form and have no corresponding community variant.

    Note:
        Distributions listed in either `explicit_community_pom_distributions` or `vendor_specific_pom_distributions`
        are not registered into the LANGUAGES catalog meta-POM. They are treated as standalone and excluded from the aggregated catalog.
    """
    if explicit_community_pom_distributions is None:
        explicit_community_pom_distributions = map()
    if vendor_specific_pom_distributions is None:
        vendor_specific_pom_distributions = set()
    languages_meta_poms = []
    deprecated_languages_community_meta_poms = []
    languages_licenses = set()
    base_distributions = list(_languages_meta_distributions)
    base_distributions.extend(vendor_specific_pom_distributions)
    base_distributions.extend(explicit_community_pom_distributions.keys())
    for distribution_name in base_distributions:
        language_distribution = mx.distribution(distribution_name, fatalIfMissing=False)
        if language_distribution:
            assert language_distribution.isPOMDistribution(), f'LANGUAGES dependency {language_distribution.name} must be a meta-POM distribution.'
            assert language_distribution.maven_group_id() != 'org.graalvm.polyglot', f'Language meta-POM distribution {language_distribution.name} cannot use org.graalvm.polyglot Maven group id.'
            vendor_specific_distribution = distribution_name in vendor_specific_pom_distributions
            if distribution_name in explicit_community_pom_distributions.keys():
                # Special handling for Espresso, which continues to maintain separate community and non-community meta-POMs.
                # Special handling for LLVM, which has handwritten deprecated community and non-community meta-POMs.
                assert language_distribution.maven_artifact_id().endswith("-community"), f'Language meta-POM of explicit community distribution {language_distribution.name} must have *-community Maven artifact id.'
                polyglot_language_distribution = create_polyglot_meta_pom_distribution_from_base_distribution(language_distribution)
                register_distribution(polyglot_language_distribution)
                distribution_name = explicit_community_pom_distributions[distribution_name]
                if distribution_name:
                    language_distribution = mx.distribution(distribution_name, fatalIfMissing=False)
                    if language_distribution:
                        assert not language_distribution.maven_artifact_id().endswith("-community"), f'Vendor specific meta-POM distribution {language_distribution.name} cannot have *-community Maven artifact id.'
                        polyglot_language_distribution = create_polyglot_meta_pom_distribution_from_base_distribution(language_distribution)
                        register_distribution(polyglot_language_distribution)
            else:
                assert not language_distribution.maven_artifact_id().endswith("-community"),  f'Language meta-POM distribution {language_distribution.name} cannot have *-community Maven artifact id.'
                polyglot_language_distribution = create_polyglot_meta_pom_distribution_from_base_distribution(language_distribution)
                register_distribution(polyglot_language_distribution)
                # Vendor specific distributions do not have community distribution and are not included in the languages catalog meta-POM
                if not vendor_specific_distribution:
                    languages_meta_poms.append(polyglot_language_distribution)
                    languages_licenses.update(_distribution_license(language_distribution))
                    deprecated_community_language_distribution = _create_deprecated_community_distribution(language_distribution)
                    register_distribution(deprecated_community_language_distribution)
                    polyglot_language_distribution = create_polyglot_meta_pom_distribution_from_base_distribution(deprecated_community_language_distribution)
                    register_distribution(polyglot_language_distribution)
                    deprecated_languages_community_meta_poms.append(polyglot_language_distribution)

    if languages_meta_poms:
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'languages',
                'tag': ['default', 'public'],
            },
            'description': 'This POM dependency includes all Graal Languages and Truffle.',
        }
        languages = mx_pomdistribution.POMDistribution(owner_suite, 'LANGUAGES', [], languages_meta_poms, sorted(list(languages_licenses)), **attrs)
        register_distribution(languages)
        attrs = {
            'maven': {
                'groupId': 'org.graalvm.polyglot',
                'artifactId': 'languages-community',
                'tag': ['default', 'public'],
            },
            'description': _deprecate('This POM dependency includes all Graal Languages and Truffle.', 'languages'),
        }
        deprecated_languages_community = mx_pomdistribution.POMDistribution(owner_suite, 'LANGUAGES_COMMUNITY_DEPRECATED', [], deprecated_languages_community_meta_poms, sorted(list(languages_licenses)), **attrs)
        register_distribution(deprecated_languages_community)


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    if mx_sdk_vm_impl.has_component('FastR'):
        fastr_release_env = mx.get_env('FASTR_RELEASE', None)
        if fastr_release_env != 'true':
            mx.abort(('When including FastR, please set FASTR_RELEASE to \'true\' (env FASTR_RELEASE=true mx ...). Got FASTR_RELEASE={}. '
                      'For local development, you may also want to disable recommended packages build (FASTR_NO_RECOMMENDED=true) and '
                      'capturing of system libraries (export FASTR_CAPTURE_DEPENDENCIES set to an empty value). '
                      'See building.md in FastR documentation for more details.').format(fastr_release_env))

    if register_distribution and _suite.primary:
        # Only primary suite can register languages and tools distributions.
        # If the suite is not a primary suite, languages and tools distributions might not have been loaded yet.
        # In this case the register_community_tools_distribution and register_community_languages_distribution
        # are called from the primary suite.
        register_tools_distribution(_suite, register_distribution)
        register_languages_distribution(_suite, register_distribution, explicit_community_pom_distributions={
            'espresso:JAVA_POM': None,
            'sulong:LLVM_POM': None})

    maven_bundle_path = mx.get_env('MAVEN_BUNDLE_PATH')
    maven_bundle_artifact_id = mx.get_env('MAVEN_BUNDLE_ARTIFACT_ID')
    if bool(maven_bundle_path) != bool(maven_bundle_artifact_id):
        mx.abort(f"Both $MAVEN_BUNDLE_PATH and $MAVEN_BUNDLE_ARTIFACT_ID must be either set or not set. Got:\n$MAVEN_BUNDLE_PATH={'' if maven_bundle_path is None else maven_bundle_path}\n$MAVEN_BUNDLE_ARTIFACT_ID={'' if maven_bundle_artifact_id is None else maven_bundle_artifact_id}")
    if register_distribution and maven_bundle_path is not None:
        register_distribution(mx.LayoutTARDistribution(_suite, 'MAVEN_BUNDLE', [], {
            './': 'file:' + os.path.realpath(maven_bundle_path)
        }, None, True, None, maven={
            'groupId': 'org.graalvm.polyglot',
            'artifactId': maven_bundle_artifact_id,
            'version': mx_sdk_vm_impl.graalvm_version('graalvm'),
            'tag': 'resource-bundle',
        }))

class GraalVmSymlinks(mx.Project):
    def __init__(self, **kw_args):
        super(GraalVmSymlinks, self).__init__(_suite, 'vm-symlinks', subDir=None, srcDirs=[], deps=['sdk:' + mx_sdk_vm_impl.graalvm_dist_name()], workingSets=None, d=_suite.dir, theLicense=None, testProject=False, **kw_args)
        self.links = []
        sdk_suite = mx.suite('sdk')
        for link_name in 'latest_graalvm', 'latest_graalvm_home':
            self.links += [(relpath(join(sdk_suite.dir, link_name), _suite.dir), join(_suite.dir, link_name))]

    def getArchivableResults(self, use_relpath=True, single=False):
        raise mx.abort(f"Project '{self.name}' cannot be archived")

    def getBuildTask(self, args):
        return GraalVmSymLinksBuildTask(args, 1, self)


class GraalVmSymLinksBuildTask(mx.ProjectBuildTask):
    """
    For backward compatibility, maintain `latest_graalvm` and `latest_graalvm_home` symlinks in the `vm` suite
    """
    def needsBuild(self, newestInput):
        sup = super(GraalVmSymLinksBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        if mx.get_os() != 'windows':
            for src, dest in self.subject.links:
                if not os.path.lexists(dest):
                    return True, f'{dest} does not exist'
                link_file = mx.TimeStampFile(dest, False)
                if newestInput and link_file.isOlderThan(newestInput):
                    return True, f'{dest} is older than {newestInput}'
                if src != os.readlink(dest):
                    return True, f'{dest} points to the wrong file'
        return False, None

    def build(self):
        if mx.get_os() == 'windows':
            mx.warn('Skip adding symlinks to the latest GraalVM (Platform Windows)')
            return
        self.rm_links()
        self.add_links()

    def clean(self, forBuild=False):
        self.rm_links()

    def add_links(self):
        for src, dest in self.subject.links:
            os.symlink(src, dest)

    def rm_links(self):
        if mx.get_os() == 'windows':
            return
        for _, dest in self.subject.links:
            if os.path.lexists(dest):
                os.unlink(dest)

    def __str__(self):
        return "Generating GraalVM symlinks in the vm suite"
