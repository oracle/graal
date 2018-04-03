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
from abc import abstractmethod, ABCMeta
from contextlib import contextmanager
from os.path import relpath, join, dirname, basename, exists, normpath

import mx
import mx_sdk
import mx_subst

_suite = mx.suite('vm')
""":type: mx.SourceSuite | mx.Suite"""


class BaseGraalVmLayoutDistribution(mx.LayoutTARDistribution):
    def __init__(self, suite, name, deps, components, include_jdk, exclLibs, platformDependent, theLicense,
                 testDistribution, layout=None, path=None, **kw_args):
        self.components = components
        _jdk_base, _jdk_dir = _get_jdk_dir()
        self.jdk_base = _jdk_base

        def _get_component_id(comp=None, **kwargs):
            return comp.id

        def _get_languages_or_tool(comp=None, **kwargs):
            if isinstance(comp, mx_sdk.GraalVmLanguage):
                return 'languages'
            if isinstance(comp, mx_sdk.GraalVmTool):
                return 'tools'
            return None

        def _get_jdk_base():
            return _jdk_base

        def _get_support(comp=None, start=None, **kwargs):
            # this only
            return relpath(join('jre', _get_languages_or_tool(comp=comp), _get_component_id(comp=comp)), start=start)

        path_substitutions = mx_subst.SubstitutionEngine(mx_subst.path_substitutions)
        path_substitutions.register_no_arg('jdk_base', _get_jdk_base)

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

            mx.logv("'Adding '{}: {}' to the layout'".format(dest, src))
            _layout_provenance[dest] = component
            _layout.setdefault(dest, []).extend(src)

        if include_jdk:
            # Add base JDK
            _add(layout, './', {
                'source_type': 'file',
                'path': '{}/*'.format(_jdk_dir),
                'exclude': [
                    'LICENSE',
                    'lib/visualvm'
                ]
            })

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
            mx.log('Adding {} to the {} {}'.format(_component.name, name, self.__class__.__name__))
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

        lib_polyglot_project = get_lib_polyglot_project()
        if lib_polyglot_project:
            _add(layout, "<jdk_base>/jre/lib/polyglot/" + lib_polyglot_project.native_image_name, "dependency:" + lib_polyglot_project.name)

        super(BaseGraalVmLayoutDistribution, self).__init__(suite, name, deps, layout, path, platformDependent,
                                                            theLicense, exclLibs, path_substitutions=path_substitutions,
                                                            testDistribution=testDistribution, **kw_args)

        mx.logv("'{}' has layout:\n{}".format(self.name, pprint.pformat(self.layout)))


class GraalVmLayoutDistribution(BaseGraalVmLayoutDistribution):
    def __init__(self, suite, name, deps, exclLibs, platformDependent, theLicense, testDistribution, layout=None,
                 path=None, **kw_args):
        super(GraalVmLayoutDistribution, self).__init__(suite, name, deps, mx_sdk.graalvm_components(), True, exclLibs,
                                                        platformDependent, theLicense, testDistribution, layout, path,
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
            self._svm_supported = True
        else:
            self.suite_native_image_root = None
            self.fetch_languages = None
            self.native_image_path = None
            self.get_native_image_distribution = None
            self._svm_supported = False

    def is_supported(self):
        return self._svm_supported

    def native_image(self, build_args, output_file, allow_server=False, nonZeroIsFatal=True,
                     out=None, err=None):
        native_image_root = self.suite_native_image_root()
        native_image_command = [self.native_image_path(native_image_root)]
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
        ] + build_args
        return mx.run(native_image_command, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)

    _debug_supported = None

    def is_debug_supported(self):
        if SvmSupport._debug_supported is None:
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
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            if not hasattr(self, 'buildDependencies'):
                self.buildDependencies = []
            self.buildDependencies += [svm_support.get_native_image_distribution()]
        super(GraalVmNativeImage, self).resolveDeps()


class GraalVmLauncher(GraalVmNativeImage):
    def __init__(self, suite, name, deps, workingSets, native_image_config, theLicense=None, **kw_args):
        """
        :type native_image_config: mx_sdk.LauncherConfig
        """
        assert isinstance(native_image_config, mx_sdk.LauncherConfig), type(native_image_config).__name__
        super(GraalVmLauncher, self).__init__(suite, name, deps, workingSets, native_image_config, theLicense=theLicense, **kw_args)

    def getBuildTask(self, args):
        svm_support = _get_svm_support()
        if svm_support.is_supported():
            return GraalVmSVMLauncherBuildTask(self, args, svm_support)
        else:
            return GraalVmBashLauncherBuildTask(self, args)


class GraalVmPolyglotLauncher(GraalVmLauncher):
    def __init__(self, suite, name, deps, workingSets, launcherConfig, **kw_args):
        for component in mx_sdk.graalvm_components():
            if isinstance(component, mx_sdk.GraalVmLanguage):
                for language_launcher_config in component.launcher_configs:
                    if isinstance(language_launcher_config, mx_sdk.LanguageLauncherConfig):
                        launcherConfig['jar_distributions'] += language_launcher_config.jar_distributions
        super(GraalVmPolyglotLauncher, self).__init__(suite, name, deps, workingSets, mx_sdk.LauncherConfig(**launcherConfig), **kw_args)

    def build_args(self):
        return super(GraalVmPolyglotLauncher, self).build_args() + GraalVmLanguageLauncher.default_tool_options()


class GraalVmLibrary(GraalVmNativeImage):
    def __init__(self, suite, name, deps, workingSets, native_image_config, **kw_args):
        assert isinstance(native_image_config, mx_sdk.LibraryConfig), type(native_image_config).__name__
        super(GraalVmLibrary, self).__init__(suite, name, deps, workingSets, native_image_config=native_image_config, **kw_args)

    def build_args(self):
        return super(GraalVmLibrary, self).build_args() + ["-H:Kind=SHARED_LIBRARY"]

    def getBuildTask(self, args):
        svm_support = _get_svm_support()
        assert svm_support.is_supported(), "Needs svm to build " + str(self)
        return GraalVmLibraryBuildTask(self, args, svm_support)


class GraalVmMiscLauncher(GraalVmLauncher):
    def __init__(self, native_image_config, **kw_args):
        super(GraalVmMiscLauncher, self).__init__(_suite, GraalVmNativeImage.project_name(native_image_config), [], None, native_image_config, **kw_args)


class GraalVmLanguageLauncher(GraalVmLauncher):
    def __init__(self, native_image_config, **kw_args):
        super(GraalVmLanguageLauncher, self).__init__(_suite, GraalVmNativeImage.project_name(native_image_config), [], None, native_image_config, **kw_args)

    @staticmethod
    def default_tool_options():
        return ["--Tool:" + tool.id for tool in mx_sdk.graalvm_components() if
                isinstance(tool, mx_sdk.GraalVmTool) and tool.include_by_default]

    def build_args(self):
        return super(GraalVmLanguageLauncher, self).build_args() + [
            '-H:-ParseRuntimeOptions',
            '-Dorg.graalvm.launcher.classpath=' + graalvm_home_relative_classpath(self.native_image_jar_distributions, ''),
            '-Dorg.graalvm.launcher.relative.language.home=' + self.native_image_config.destination.replace('/', os.path.sep)
        ] + GraalVmLanguageLauncher.default_tool_options()


class GraalVmNativeImageBuildTask(mx.BuildTask):
    def needsBuild(self, newestInput):
        sup = super(GraalVmNativeImageBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup
        out_file = mx.TimeStampFile(self.subject.output_file())
        if not out_file.exists():
            return True, '{} does not exist'.format(out_file.path)
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
        super(GraalVmBashLauncherBuildTask, self).__init__(subject, args, 1)

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
        script_destination_directory = dirname(
            mx.distribution("GRAALVM").find_source_location('dependency:' + self.subject.name)[0])

        def _get_classpath():
            return graalvm_home_relative_classpath(self.subject.native_image_jar_distributions, script_destination_directory)

        def _get_jre_bin():
            return relpath(join('jre', 'bin'), script_destination_directory)

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


@contextmanager
def lock_directory(path):
    with open(join(path, '.lock'), 'w') as fd:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)


def graalvm_home_relative_classpath(dependencies, start, with_boot_jars=False):
    graal_vm = mx.distribution("GRAALVM")
    """:type : GraalVmLayoutDistribution"""
    boot_jars_directory = "jre/lib/boot"
    if graal_vm.jdk_base and graal_vm.jdk_base != '.':
        assert not graal_vm.jdk_base.endswith('/')
        boot_jars_directory = graal_vm.jdk_base + "/" + boot_jars_directory
    _cp = []
    for _cp_entry in mx.classpath_entries(dependencies):
        graalvm_location = graal_vm.find_source_location('dependency:{}:{}'.format(_cp_entry.suite, _cp_entry.name))[0]
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
        super(GraalVmSVMNativeImageBuildTask, self).__init__(subject, args, min(8, mx.cpu_count()))
        self.svm_support = svm_support

    def prepare(self, daemons):
        self.svm_support.fetch_languages(self.get_build_args())  # Ensure languages are symlinked in native_image_root

    def build(self):
        build_args = self.get_build_args()

        # Disable build server (different Java properties on each build prevent server reuse)
        self.svm_support.native_image(build_args, self.subject.output_file())

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

        # rewrite --Language:all & --Tool:all
        final_build_args = []
        for build_arg in build_args:
            if build_arg in ("--language:all", "--Language:all"):
                final_build_args += ["--Language:" + component.id for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmLanguage)]
            elif build_arg in ("--tool:all", "--Tool:all"):
                final_build_args += ["--Language:" + component.id for component in mx_sdk.graalvm_components() if isinstance(component, mx_sdk.GraalVmTool)]
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

    def _perm_str(self, filename):
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
            id=self.component.id,
            version=_suite.release_version(),
            os=mx.get_os(),
            arch=mx.get_arch(),
            polyglot=isinstance(self.component, mx_sdk.GraalVmLanguage) or isinstance(self.component, mx_sdk.GraalVmTool) and self.component.include_by_default)

        _manifest_arc_name = 'META-INF/MANIFEST.MF'

        _permissions_str = '\n'.join(self.permissions)
        _permissions_arc_name = 'META-INF/permissions'

        _symlinks_str = '\n'.join(self.symlinks)
        _symlinks_arc_name = 'META-INF/symlinks'

        for _str, _arc_name in [(_manifest_str, _manifest_arc_name), (_permissions_str, _permissions_arc_name),
                                (_symlinks_str, _symlinks_arc_name)]:
            self.add_str(_str, _arc_name, '{}<-string:{}'.format(_arc_name, _str))

        super(InstallableComponentArchiver, self).__exit__(exc_type, exc_value, traceback)


class GraalVmInstallableComponent(BaseGraalVmLayoutDistribution):
    def __init__(self, component, **kw_args):
        """
        :type component: mx_sdk.GraalVmLanguage
        """

        def create_archive(path, **_kw_args):
            assert len(self.components) == 1
            return InstallableComponentArchiver(path, self.components[0], **_kw_args)

        super(GraalVmInstallableComponent, self).__init__(
            suite=_suite,
            name='{}_INSTALLABLE'.format(component.id.upper()),
            deps=[],
            components=[component],
            include_jdk=False,
            exclLibs=[],
            platformDependent=True,
            theLicense=None,
            testDistribution=False,
            layout={},
            archive_factory=create_archive,
            path=None,
            **kw_args)

    def remoteExtension(self):
        return 'zip'

    def localExtension(self):
        return 'zip'


_lib_polyglot_project = 'uninitialized'


def get_lib_polyglot_project():
    global _lib_polyglot_project
    if _lib_polyglot_project == 'uninitialized':
        if not _get_svm_support().is_supported():
            _lib_polyglot_project = None
        else:
            polyglot_library_build_args = []
            polyglot_library_jar_dependencies = []
            has_polyglot_library_entrypoints = False
            for component in mx_sdk.graalvm_components():
                has_polyglot_library_entrypoints |= component.has_polyglot_library_entrypoints
                polyglot_library_build_args += component.polyglot_library_build_args
                polyglot_library_jar_dependencies += component.polyglot_library_jar_dependencies

            if not has_polyglot_library_entrypoints:
                _lib_polyglot_project = None
            else:
                lib_polyglot_config = mx_sdk.LibraryConfig(destination="<lib:polyglot>",
                                                           jar_distributions=polyglot_library_jar_dependencies,
                                                           build_args=["--Language:all"] + polyglot_library_build_args,
                                                           )
                _lib_polyglot_project = GraalVmLibrary(_suite, GraalVmNativeImage.project_name(lib_polyglot_config), [], None, lib_polyglot_config)
    return _lib_polyglot_project


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    """
    :type register_project: (mx.Project) -> None
    :type register_distribution: (mx.Distribution) -> None
    """
    if any((c.name == 'FastR' for c in mx_sdk.graalvm_components())):
        if mx.get_env('FASTR_RELEASE') != 'true':
            mx.abort("When including FastR, please set FASTR_RELEASE to true (env FASTR_RELEASE=true mx ...).")
        if mx.get_env('FASTR_RFFI') not in (None, ''):
            mx.abort("When including FastR, FASTR_RFFI should not be set. Got FASTR_RFFI=" + mx.get_env('FASTR_RFFI'))

    for component in mx_sdk.graalvm_components():
        if register_project:
            if isinstance(component, mx_sdk.GraalVmTruffleComponent):
                config_class = GraalVmLanguageLauncher
            else:
                config_class = GraalVmMiscLauncher
            for launcher_config in component.launcher_configs:
                register_project(config_class(launcher_config))
        if isinstance(component, mx_sdk.GraalVmLanguage) and component.id != 'js':
            register_distribution(GraalVmInstallableComponent(component))

    if register_project:
        lib_polyglot_project = get_lib_polyglot_project()
        if lib_polyglot_project:
            register_project(lib_polyglot_project)

