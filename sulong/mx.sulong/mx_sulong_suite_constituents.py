#
# Copyright (c) 2016, 2022, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#

from __future__ import print_function

import abc
import fnmatch
import pipes

import mx
import mx_cmake
import mx_native
import mx_unittest
import mx_subst
import os

import mx_sulong

import mx_sulong_path_helpers as path_helpers

import sys

if sys.version_info[0] < 3:
    _unicode = unicode # pylint: disable=undefined-variable
else:
    _unicode = str

_basestring = (str, _unicode)


def run(vmArgs, unittests, extraOption=None, extraLibs=None):
    if not isinstance(unittests, list):
        unittests = [unittests]
    if extraOption is None:
        extraOption = []
    if mx.get_opts().verbose:
        command = mx_sulong.getCommonOptions(True, extraLibs) + extraOption + vmArgs + ['--very-verbose'] + unittests
        print('Running mx unittests ' + ' '.join(command))
        return mx_unittest.unittest(command)
    else:
        command = mx_sulong.getCommonOptions(True, extraLibs) + extraOption + vmArgs + unittests
        return mx_unittest.unittest(command)


def compileTestSuite(testsuiteproject, extra_build_args):
    defaultBuildArgs = ['--project', testsuiteproject]
    mx.command_function('build')(defaultBuildArgs + extra_build_args)


class DragonEggSupport:
    """Helpers for DragonEgg

    DragonEgg support is controlled by two environment variables:
      * `DRAGONEGG_GCC`: path to a GCC installation with dragonegg support
      * `DRAGONEGG_LLVM`: path to an LLVM installation that can deal with bitcode produced by DragonEgg
      * `DRAGONEGG`: (optional) path to folder that contains the `libdragonegg.so`
    """

    @staticmethod
    def haveDragonegg():
        if not hasattr(DragonEggSupport, '_haveDragonegg'):
            DragonEggSupport._haveDragonegg = DragonEggSupport.pluginPath() is not None and os.path.exists(
                DragonEggSupport.pluginPath()) and DragonEggSupport.findGCCProgram('gcc', optional=True) is not None
        return DragonEggSupport._haveDragonegg

    @staticmethod
    def pluginPath():
        if 'DRAGONEGG' in os.environ:
            return os.path.join(os.environ['DRAGONEGG'], mx.add_lib_suffix('dragonegg'))
        if 'DRAGONEGG_GCC' in os.environ:
            path = os.path.join(os.environ['DRAGONEGG_GCC'], 'lib', mx.add_lib_suffix('dragonegg'))
            if os.path.exists(path):
                return path
        return None

    @staticmethod
    def findLLVMProgram(program, optional=False):
        if 'DRAGONEGG_LLVM' in os.environ:
            path = os.environ['DRAGONEGG_LLVM']
            return os.path.join(path, 'bin', program)
        if optional:
            return None
        mx.abort("Cannot find LLVM program for dragonegg: {}\nDRAGONEGG_LLVM environment variable not set".format(program))

    @staticmethod
    def findGCCProgram(gccProgram, optional=False):
        if 'DRAGONEGG_GCC' in os.environ:
            path = os.environ['DRAGONEGG_GCC']
            return os.path.join(path, 'bin', gccProgram)
        if optional:
            return None
        mx.abort("Cannot find GCC program for dragonegg: {}\nDRAGONEGG_GCC environment variable not set".format(gccProgram))


class SulongTestSuiteMixin(object, metaclass=abc.ABCMeta):

    def getVariants(self):
        if not hasattr(self, '_variants'):
            self._variants = []
            for v in self.variants:
                if 'gcc' in v and not DragonEggSupport.haveDragonegg():
                    mx.warn('Could not find dragonegg, not building test variant "%s"' % v)
                    continue
                self._variants.append(v)
        return self._variants

    def getResults(self, replaceVar=mx_subst.results_substitutions):
        if not self.results:
            self.results = []
            for t in self.getTests():
                t = t + self.getTestDirExt()
                if self.buildRef:
                    self.results.append(os.path.join(t, mx.exe_suffix('ref.out')))
                for v in self.getVariants():
                    # TODO: [GR-41902] use mx.add_lib_suffix
                    result_file = mx_sulong._lib_suffix(v) if self.buildSharedObject else v + '.bc'
                    self.results.append(os.path.join(t, result_file))
        return super(SulongTestSuiteMixin, self).getResults(replaceVar=replaceVar)

    def getTestDirExt(self):
        return ".dir"

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = []
            # collect tests from VPATH (defaults to self.dir)
            root = os.path.join(self._get_vpath())
            for path, _, files in os.walk(root):
                for f in files:
                    absPath = os.path.join(path, f)
                    relPath = os.path.relpath(absPath, root)
                    _, ext = os.path.splitext(relPath)
                    if ext in getattr(self, "fileExts", ['.c', '.cpp', '.ll']):
                        self._tests.append(relPath)
        return self._tests

    @abc.abstractmethod
    def _get_vpath(self):
        """Return the source directory."""


def collectExcludes(path):
    def _collect(path, skip=None):
        for root, _, files in os.walk(path):
            if skip and skip(os.path.relpath(root, path)):
                continue
            for f in files:
                if f.endswith('.exclude'):
                    for line in open(os.path.join(root, f)):
                        yield line.strip()
    # use `yield from` in python 3.3
    for x in _collect(path, lambda p: p.startswith('os_arch')):
        yield x

    os_arch_root = os.path.join(path, 'os_arch')
    if os.path.exists(os_arch_root):
        try:
            os_path = next(x for x in (os.path.join(os_arch_root, os_dir) for os_dir in [mx.get_os(), 'others']) if os.path.exists(x))
            os_arch_path = next(x for x in (os.path.join(os_path, arch_dir) for arch_dir in [mx.get_arch(), 'others']) if os.path.exists(x))
            # use `yield from` in python 3.3
            for x in _collect(os_arch_path):
                yield x
        except StopIteration:
            pass


class ExternalTestSuiteMixin(object):  # pylint: disable=too-many-ancestors
    def __init__(self, *args, **kwargs):
        super(ExternalTestSuiteMixin, self).__init__(*args, **kwargs)
        if hasattr(self, 'testDir'):
            self.testDir = self.testDir.replace('/', os.sep)
        else:
            self.testDir = ''
        if hasattr(self, 'fileExts'):
            self.fileExts = self.fileExts if not isinstance(self.fileExts, _basestring) else [self.fileExts]
        else:
            self.fileExts = ['.c', '.cpp', '.C']
        if not hasattr(self, 'configDir'):
            self.configDir = 'configs'

    def _get_test_intern(self):
        ### Excludes
        def _maybe_pattern(name):
            for c in '*?[]!':
                if c in name:
                    return True
            return False

        exclude_patterns = []
        exclude_files = []

        # full name check is cheaper than pattern matching
        for exclude in collectExcludes(os.path.join(self.dir, "..", self.configDir)):
            if _maybe_pattern(exclude):
                exclude_patterns.append(exclude)
            else:
                exclude_files.append(exclude)

        def _match_pattern(name):
            for pattern in exclude_patterns:
                if fnmatch.fnmatch(name, pattern):
                    return True
            return False

        ## Testcase collection
        _tests = []
        root = self.get_test_source(resolve=True)
        for path, _, files in os.walk(os.path.join(root, self.testDir)):
            for f in files:
                absPath = os.path.join(path, f)
                relPath = os.path.relpath(absPath, root)
                relPath = relPath.replace('\\', '/') if mx.is_windows() else relPath
                _, ext = os.path.splitext(relPath)
                if ext in self.fileExts and relPath not in exclude_files and not _match_pattern(relPath):
                    _tests.append(relPath)

        return _tests

    def get_test_source(self, resolve=False):
        roots = [d.get_path(resolve=resolve) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        assert len(roots) == 1, "Roots: {}".format(", ".join(roots))
        return roots[0]

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = self._get_test_intern()
        return self._tests


class ExternalTestSuiteBuildTask(mx.NativeBuildTask):
    """Track whether we are checking if a build is required or actually building."""
    def needsBuild(self, newestInput):
        try:
            self.subject._is_needs_rebuild_call = True
            return super(ExternalTestSuiteBuildTask, self).needsBuild(newestInput)
        finally:
            self.subject._is_needs_rebuild_call = False


class ExternalTestSuite(ExternalTestSuiteMixin, SulongTestSuiteMixin, mx.NativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, buildRef=True,
                 buildSharedObject=False, bundledLLVMOnly=False, **args):
        projectDir = args.pop('dir', None)
        if projectDir:
            d_rel = projectDir
        elif subDir is None:
            d_rel = name
        else:
            d_rel = os.path.join(subDir, name)
        d = os.path.join(suite.dir, d_rel.replace('/', os.sep))
        super(ExternalTestSuite, self).__init__(suite, name, subDir, [], deps, workingSets, results, output, d, **args)
        if bundledLLVMOnly and mx.get_env('CLANG_CC', None):
            self.ignore = "Environment variable 'CLANG_CC' is set but project specifies 'bundledLLVMOnly'"
        self.vpath = True
        self.buildRef = buildRef
        self.buildSharedObject = buildSharedObject
        self._is_needs_rebuild_call = False

    def getBuildTask(self, args):
        return ExternalTestSuiteBuildTask(args, self)

    def _get_vpath(self):
        env = super(ExternalTestSuite, self).getBuildEnv()
        return env.get('VPATH', self.dir)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(ExternalTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        roots = [d.get_path(resolve=True) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        env['PROJECT'] = self.name
        env['VPATH'] = ':'.join(roots)
        env['TESTFILE'] = self.getTestFile()
        env['VARIANTS'] = ' '.join(self.getVariants())
        env['BUILD_REF'] = '1' if self.buildRef else '0'
        env['BUILD_SO'] = '1' if self.buildSharedObject else '0'
        env['SO_EXT'] = mx.add_lib_suffix("")
        env['CLANG'] = mx_sulong.findBundledLLVMProgram('clang')
        env['CLANGXX'] = mx_sulong.findBundledLLVMProgram('clang++')
        env['LLVM_OPT'] = mx_sulong.findBundledLLVMProgram('opt')
        env['LLVM_AS'] = mx_sulong.findBundledLLVMProgram('llvm-as')
        env['LLVM_DIS'] = mx_sulong.findBundledLLVMProgram('llvm-dis')
        env['LLVM_LINK'] = mx_sulong.findBundledLLVMProgram('llvm-link')
        env['LLVM_OBJCOPY'] = mx_sulong.findBundledLLVMProgram('llvm-objcopy')
        env['GRAALVM_LLVM_HOME'] = mx_subst.path_substitutions.substitute("<path:SULONG_HOME>")
        if 'OS' not in env:
            env['OS'] = mx_subst.path_substitutions.substitute("<os>")
        if DragonEggSupport.haveDragonegg():
            env['DRAGONEGG'] = DragonEggSupport.pluginPath()
            env['DRAGONEGG_GCC'] = DragonEggSupport.findGCCProgram('gcc', optional=False)
            env['DRAGONEGG_LLVMAS'] = DragonEggSupport.findLLVMProgram("llvm-as")
            env['DRAGONEGG_FC'] = DragonEggSupport.findGCCProgram('gfortran', optional=False)
            env['FC'] = DragonEggSupport.findGCCProgram('gfortran', optional=False)
        elif not self._is_needs_rebuild_call and getattr(self, 'requireDragonegg', False):
            mx.abort('Could not find dragonegg, cannot build "{}" (requireDragonegg = True).'.format(self.name))
        return env

    def getTestFile(self):
        if not hasattr(self, '_testfile'):
            self._testfile = os.path.join(self.getOutput(), 'test.include')
            with open(self._testfile, 'w') as f:
                mx.logv("Writing test file: " + self._testfile)
                # call getResults() ensure self.results is populated
                _ = self.getResults()
                f.write("\n".join(("default: " + r for r in self.results)))
        return self._testfile


class BootstrapToolchainLauncherProject(mx.Project):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        super(BootstrapToolchainLauncherProject, self).__init__(suite, name, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, theLicense=theLicense, **kwArgs)
        self.buildDependencies += ['mx:GCC_NINJA_TOOLCHAIN']

    def launchers(self):
        for tool in self.suite.toolchain._supported_tools():
            for exe in self.suite.toolchain._tool_to_aliases(tool):
                cmd = exe
                if mx.is_windows() and exe.endswith('.exe'):
                    cmd = exe[:-4] + ".cmd"
                result = os.path.join(self.get_output_root(), cmd)
                yield result, tool, exe

    def ninja_toolchain_path(self):
        return os.path.join(self.get_output_root(), 'toolchain.ninja')

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError("Cannot produce single result for BootstrapToolchainLauncherProject")
        for result, _, exe in self.launchers():
            cmd = exe
            if mx.is_windows() and exe.endswith('.exe'):
                cmd = exe[:-4] + ".cmd"
            yield result, os.path.join('bin', cmd)
        toolchain_path = self.ninja_toolchain_path()
        yield toolchain_path, os.path.basename(toolchain_path)

    def getBuildTask(self, args):
        return BootstrapToolchainLauncherBuildTask(self, args, 1)

    def isPlatformDependent(self):
        return True

    def getJavaProperties(self, replaceVar=mx_subst.path_substitutions):
        ret = {}
        if hasattr(self, "javaProperties"):
            for key, value in self.javaProperties.items():
                ret[key] = replaceVar.substitute(value, dependency=self)
        return ret


def _quote_windows(arg):
    return '"{}"'.format(arg)


def _ninja_escape_string(val):
    return val.replace(':', '$:')

class BootstrapToolchainLauncherBuildTask(mx.BuildTask):
    def __str__(self):
        return "Generating " + self.subject.name

    def newestOutput(self):
        return mx.TimeStampFile.newest([result for result, _, _ in self.subject.launchers()])

    def needsBuild(self, newestInput):
        sup = super(BootstrapToolchainLauncherBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup

        for result, tool, exe in self.subject.launchers():
            if not os.path.exists(result):
                return True, result + ' does not exist'
            with open(result, "r") as f:
                on_disk = f.read()
            if on_disk != self.contents(tool, exe):
                return True, 'command line changed for ' + os.path.basename(result)

        return False, 'up to date'

    def build(self):
        mx.ensure_dir_exists(self.subject.get_output_root())
        for result, tool, exe in self.subject.launchers():
            with open(result, "w") as f:
                f.write(self.contents(tool, exe))
            os.chmod(result, 0o755)
        with open(self.subject.ninja_toolchain_path(), "w") as f:
            f.write(self.ninja_toolchain_contents())

    def clean(self, forBuild=False):
        if os.path.exists(self.subject.get_output_root()):
            mx.rmtree(self.subject.get_output_root())

    def contents(self, tool, exe):
        # platform support
        all_params = '%*' if mx.is_windows() else '"$@"'
        _quote = _quote_windows if mx.is_windows() else pipes.quote
        # build command line
        java = mx.get_jdk().java
        classpath_deps = [dep for dep in self.subject.buildDependencies if isinstance(dep, mx.ClasspathDependency)]
        extra_props = ['-Dorg.graalvm.launcher.executablename="{}"'.format(exe)]
        main_class = self.subject.suite.toolchain._tool_to_main(tool)
        # add jvm args from dependencies
        jvm_args = [_quote(arg) for arg in mx.get_runtime_jvm_args(classpath_deps)]
        # add properties from the project
        if hasattr(self.subject, "getJavaProperties"):
            for key, value in sorted(self.subject.getJavaProperties().items()):
                jvm_args.append(_quote("-D" + key + "=" + value))
        command = [_quote(java)] + jvm_args + extra_props + [main_class, all_params]
        # create script
        if mx.is_windows():
            return "@echo off\n" + " ".join(command) + "\n"
        else:
            return "#!/usr/bin/env bash\n" + "exec " + " ".join(command) + "\n"

    def ninja_toolchain_contents(self):
        gcc_ninja_toolchain = mx.distribution('mx:GCC_NINJA_TOOLCHAIN')
        assert isinstance(gcc_ninja_toolchain, mx.AbstractDistribution) and gcc_ninja_toolchain.get_output()
        return """# Ninja rules for the LLVM toolchain
include {gcc_toolchain}
CC = {CC}
CXX = {CXX}
AR = {AR}

""".format(gcc_toolchain=_ninja_escape_string(os.path.join(gcc_ninja_toolchain.get_output(), 'toolchain.ninja')),
           CC=_ninja_escape_string(self.subject.suite.toolchain.get_toolchain_tool('CC')),
           CXX=_ninja_escape_string(self.subject.suite.toolchain.get_toolchain_tool('CXX')),
           AR=_ninja_escape_string(self.subject.suite.toolchain.get_toolchain_tool('AR')))


class AbstractSulongNativeProject(mx.NativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        projectDir = args.pop('dir', None)
        if projectDir:
            d_rel = projectDir
        elif subDir is None:
            d_rel = name
        else:
            d_rel = os.path.join(subDir, name)
        d = os.path.join(suite.dir, d_rel.replace('/', os.sep))
        srcDir = args.pop('sourceDir', d)
        if not srcDir:
            mx.abort("Exactly one 'sourceDir' is required")
        srcDir = mx_subst.path_substitutions.substitute(srcDir)
        super(AbstractSulongNativeProject, self).__init__(suite, name, subDir, [srcDir], deps, workingSets, results, output, d, **args)


class VariantCMakeNinjaBuildTask(mx_cmake.CMakeNinjaBuildTask):
    def __init__(self, args, project, target_arch=mx.get_arch(), ninja_targets=None, variant=None):
        self.variant = variant
        # abuse target_arch to inject variant
        super(VariantCMakeNinjaBuildTask, self).__init__(args, project, target_arch=os.path.join("build", variant), ninja_targets=ninja_targets)
        # set correct target_arch
        self.target_arch = target_arch

    @property
    def name(self):
        return '{} ({})'.format(self.subject.name, self.variant)

    def build(self):
        try:
            self.subject.current_variant = self.variant
            super(VariantCMakeNinjaBuildTask, self).build()
        finally:
            self.subject.current_variant = None


class SulongCMakeTestSuite(SulongTestSuiteMixin, mx_cmake.CMakeNinjaProject):  # pylint: disable=too-many-ancestors
    """Sulong test suite compiled with CMake/Ninja.

    This project automatically collects test sources (C, C++, Fortran, LL) and compiles them
    into specified variants.

    Usage (suite.py):
    ```
        "com.oracle.truffle.llvm.tests.mytest.native" : {
          "subDir" : "tests",
          "class" : "SulongCMakeTestSuite",
          "variants" : ["bitcode-O1"],
          "cmakeConfig" : {
            "CMAKE_C_FLAGS" : "-Wall",
          },
          "testProject" : True,
        },
    ```

    Attributes
        variants:
            Specifies a list of variants the test sources should be compiled to. The "variant" determines how the test
            source is compiled and the resulting file type. A "variant" has the form "<compile-mode>-<opt-level>".
            Valid optimization levels are O0, O1, O2, and O3. The following compile modes are supported:
            - "bitcode"
                The source file is compiled to bitcode using clang or another frontend.
                This mode supports appending a "-<post-opt>" specifier to the variant string,
                to post-process the result using the `opt` tool from LLVM. The following post-opt specifiers are
                supported:
                    - MISC_OPTS: run a selection of common optimizations
                    - MEM2REG: run the -mem2reg optimization
                See tests/com.oracle.truffle.llvm.tests.cmake/SulongTestSuiteVariantBitcode.cmake for more details.
            - "executable"
                The source file is compiled to an executable with an embedded bitcode section.
            - "toolchain"
                The toolchain wrappers are used to compile the test source into an executable with embedded bitcode.
            - "gcc"
                The source is compiled to bitcode using gcc and the DRAGONEGG plugin.
        buildRef:
            If True (the default), build a reference executable. Setting this to False is useful for embedded tests with
            dedicated JUnit test classes.
        buildSharedObject:
            If True, build a shared object instead of executables (for variants where this is applicable).
            Useful if the test does not contain a `main` functions. Use in combination with `buildRef: False`.
            False by default.
        bundledLLVMOnly:
            If True, the project is ignored if an alternative LLVM version is used. This is the case if the environment
            variable `CLANG_CC` is set.
        cmakeConfig:
            Customize the compilation process. See tests/com.oracle.truffle.llvm.tests.cmake/SulongTestSuite.cmake for
            more information.
        cmakeConfigVariant:
            CMake configuration for a specific variant only. The keys of the dictionary are the "variants", e.g.,
            "ref.out", "bitcode-O0", etc. The key "<others>" can be used as an "else" branch.

    Remark:
        Every variant has its own ninja.build files and will be built using its own BuildTask. The result of each
        individual variant build is then "installed" into a common directory.
    """
    def __init__(self, suite, name, deps, workingSets, subDir, buildRef=True,
                 buildSharedObject=False, bundledLLVMOnly=False, ninja_install_targets=None, max_jobs=8,
                 cmakeConfigVariant=None, **args):

        if bundledLLVMOnly and mx.get_env('CLANG_CC', None):
            self.ignore = "Environment variable 'CLANG_CC' is set but project specifies 'bundledLLVMOnly'"
        if 'buildDependencies' not in args:
            args['buildDependencies'] = []
        if 'sdk:LLVM_TOOLCHAIN' not in args['buildDependencies']:
            args['buildDependencies'].append('sdk:LLVM_TOOLCHAIN')
        self.buildRef = buildRef
        self.buildSharedObject = buildSharedObject
        self.current_variant = None
        self.cmake_config_variant = cmakeConfigVariant or {}
        super(SulongCMakeTestSuite, self).__init__(suite, name, deps, workingSets, subDir,
                                                   ninja_install_targets=ninja_install_targets or ["install"], max_jobs=max_jobs, **args)
        self._install_dir = mx.join(self.out_dir, "result")
        # self._ninja_targets = self.getResults()
        _config = self._cmake_config_raw
        _config.setdefault('CMAKE_BUILD_TYPE', 'Sulong')
        _module_path = mx.Suite._pop_list(_config, 'CMAKE_MODULE_PATH', self)
        _module_path.append('<path:com.oracle.truffle.llvm.tests.cmake>')
        _config['CMAKE_MODULE_PATH'] = ';'.join(_module_path)
        _config['SULONG_MODULE_PATH'] = '<path:com.oracle.truffle.llvm.tests.cmake>'
        self._init_cmake = False

    def getTestFile(self):
        if not hasattr(self, '_testfile'):
            self._testfile = os.path.join(self.out_dir, 'tests.cache')
            with mx.SafeFileCreation(self._testfile) as sfc, open(sfc.tmpPath, "w") as f:
                mx.logv("Writing test file: " + self._testfile)
                tests = ';'.join([x.replace('\\', '\\\\') for x in self.getTests()])
                f.write('set(SULONG_TESTS {} CACHE FILEPATH "test files")'.format(tests))
        return self._testfile

    def _default_cmake_vars(self):
        _config = dict()
        _config['CMAKE_INSTALL_PREFIX'] = self._install_dir
        _config['SULONG_PROJECT_NAME'] = self.name
        _config['SULONG_ENABLED_LANGUAGES'] = ';'.join(self._get_languages())
        _config['SULONG_BUILD_SHARED_OBJECT'] = 'YES' if self.buildSharedObject else 'NO'
        _config['CLANG'] = mx_sulong.findBundledLLVMProgram('clang')
        _config['CLANGXX'] = mx_sulong.findBundledLLVMProgram('clang++')
        _config['LLVM_OPT'] = mx_sulong.findBundledLLVMProgram('opt')
        _config['LLVM_AS'] = mx_sulong.findBundledLLVMProgram('llvm-as')
        _config['LLVM_LINK'] = mx_sulong.findBundledLLVMProgram('llvm-link')
        _config['LLVM_CONFIG'] = mx_sulong.findBundledLLVMProgram('llvm-config')
        _config['LLVM_OBJCOPY'] = mx_sulong.findBundledLLVMProgram('llvm-objcopy')
        _config['CMAKE_NM'] = mx_sulong.findBundledLLVMProgram('llvm-nm')
        if DragonEggSupport.haveDragonegg():
            _config['DRAGONEGG'] = DragonEggSupport.pluginPath()
            _config['DRAGONEGG_GCC'] = DragonEggSupport.findGCCProgram('gcc', optional=False)
            _config['DRAGONEGG_LLVM_LINK'] = DragonEggSupport.findLLVMProgram("llvm-link")
            _config['DRAGONEGG_LLVMAS'] = DragonEggSupport.findLLVMProgram("llvm-as")
            _config['DRAGONEGG_FC'] = DragonEggSupport.findGCCProgram('gfortran', optional=False)
            _config['FC'] = DragonEggSupport.findGCCProgram('gfortran', optional=False)
        return _config

    def cmake_config(self):
        if not self._init_cmake:
            self._init_cmake = True
            self._cmake_config_raw.update({k: v for k, v in self._default_cmake_vars().items()
                                           if k not in self._cmake_config_raw})
        return ['-C', self.getTestFile()] + super(SulongCMakeTestSuite, self).cmake_config()

    def _get_languages(self):
        lang_ext_map = {
            'C': ['.c', '.cint', '.gcc'],
            'CXX': ['.cpp', '.C', '.cc', '.gpp'],
            'Fortran': ['.f', '.f90', '.f03'],
            'LL': ['.ll'],
        }

        def _get_language(test):
            for lang, exts in lang_ext_map.items():
                if any(test.endswith(ext) for ext in exts):
                    return lang
            self.abort('Cannot determine language of file ' + test)

        return set(_get_language(test) for test in self.getTests())

    def getBuildTask(self, args):
        def _variant():
            if self.buildRef:
                yield 'ref.out'
            for variant in self.getVariants():
                yield variant

        class MultiVariantBuildTask(mx.Buildable, mx.TaskSequence):
            subtasks = [VariantCMakeNinjaBuildTask(args, self, target_arch=mx.get_arch(), ninja_targets=self._ninja_targets, variant=variant) for variant in _variant()]

            def execute(self):
                super(MultiVariantBuildTask, self).execute()
                self.built = any(t.built for t in self.subtasks)

            def newestOutput(self):
                return mx.TimeStampFile.newest(t.newestOutput().path for t in self.subtasks)

        return MultiVariantBuildTask(self, args)

    def _build_task(self, target_arch, args):
        mx.nyi("_build_task", self)

    def generate_manifest(self, output_dir, filename, extra_cmake_config=None):
        if not self.current_variant:
            self.abort("current_variant not set")
        _extra_cmake_config = extra_cmake_config or []
        _extra_cmake_config.append(mx_cmake.CMakeNinjaProject.config_entry("SULONG_CURRENT_VARIANT", self.current_variant))
        try:
            # get either current_variant or <other>
            variant_specific_config = next(self.cmake_config_variant[x] for x in (self.current_variant, '<others>') if x in self.cmake_config_variant)
            _extra_cmake_config.extend((mx_cmake.CMakeNinjaProject.config_entry(k, v) for k, v in variant_specific_config.items()))
        except StopIteration:
            # no variant specific config
            pass
        super(SulongCMakeTestSuite, self).generate_manifest(output_dir, filename, extra_cmake_config=_extra_cmake_config)

    def _get_vpath(self):
        return self.source_dirs()[0]

    def _archivable_results(self, target_arch, use_relpath, single):
        out_dir_arch = self._install_dir
        for file_path in self.getResults():
            assert not mx.isabs(file_path)
            abs_path = mx.join(out_dir_arch, file_path)
            archive_path = file_path if use_relpath else mx.basename(file_path)

            # if test.skip exists the test should be skipped
            if mx.exists(mx.join(mx.dirname(abs_path), "test.skip")):
                continue

            yield abs_path, archive_path


class ExternalCMakeTestSuite(ExternalTestSuiteMixin, SulongCMakeTestSuite):  # pylint: disable=too-many-ancestors

    def original_source_dirs(self):
        return super(ExternalCMakeTestSuite, self).source_dirs()

    def source_dirs(self):
        if hasattr(self, '_source_dirs'):
            return self._source_dirs
        return self.original_source_dirs()

    def _default_cmake_vars(self):
        cmake_vars = super(ExternalCMakeTestSuite, self)._default_cmake_vars()
        cmake_vars['SULONG_TEST_SOURCE_DIR'] = 'src'
        return cmake_vars

    def get_actual_test_source(self, resolve=False):
        if hasattr(self, 'testSourceDir'):
            return mx_subst.path_substitutions.substitute(self.testSourceDir)
        roots = [d.get_path(resolve=resolve) for d in self.buildDependencies if d.isPackedResourceLibrary()
                 # mx might insert dependencies on NINJA and NINJA_SYNTAX
                 # to play safe, we are ignoring all resources from mx since they are very likely
                 # not external benchmark suites
                 and d.suite is not mx._mx_suite]
        assert len(roots) == 1, "Roots: {}".format(", ".join(roots))
        return roots[0]

    def get_project_dir(self):
        """Returns the path to the generated project directory."""
        if not hasattr(self, '_project_dir'):
            source_dir = self.original_source_dirs()[0]
            # Symlink the CMakeLists.txt file and source folder into a single
            # directory to prevent max path length issues on Windows
            self._project_dir = path_helpers.ensure_dirs(self.get_output_root(), 'project')
            path_helpers.ensure_all_copy(source_dir, self._project_dir)
            path_helpers.ensure_symlink(self.get_actual_test_source(), os.path.join(self._project_dir, 'src'))
        return self._project_dir

    def get_test_source(self, resolve=False):
        """Returns the path to the source code linked into the project directory."""
        return os.path.join(self.get_project_dir(), 'src')

    def generate_manifest(self, output_dir, filename, extra_cmake_config=None):
        self._source_dirs = [self.get_project_dir()]
        return super(ExternalCMakeTestSuite, self).generate_manifest(output_dir, filename, extra_cmake_config)


class DocumentationBuildTask(mx.AbstractNativeBuildTask):
    def __str__(self):
        return 'Building {} with Documentation Build Task'.format(self.subject.name)

    def build(self):
        pass

    def needsBuild(self, newestInput):
        return False, None

    def clean(self, forBuild=False):
        pass


class DocumentationProject(AbstractSulongNativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        super(DocumentationProject, self).__init__(suite, name, deps, workingSets, subDir, results, output, **args)
        self.dir = self.source_dirs()[0]

    def getBuildTask(self, args):
        return DocumentationBuildTask(args, self)


class HeaderBuildTask(mx.NativeBuildTask):
    def __str__(self):
        return 'Building {} with Header Build Task'.format(self.subject.name)

    def build(self):
        self._newestOutput = None

    def needsBuild(self, newestInput):
        return (False, "up to date according to GNU Make")

    def clean(self, forBuild=False):
        pass


class HeaderProject(AbstractSulongNativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        super(HeaderProject, self).__init__(suite, name, deps, workingSets, subDir, results, output, **args)
        self.dir = self.source_dirs()[0]

    def getBuildTask(self, args):
        return HeaderBuildTask(args, self)

    def isPlatformDependent(self):
        return False


class CopiedNativeProject(mx_native.DefaultNativeProject):
    def __init__(self, suite, name, deps, workingSets, subDir, **kwargs):
        srcFrom = mx.project(kwargs["srcFrom"])
        srcDirs = []
        kwargs["deliverable"] = srcFrom.deliverable
        super(CopiedNativeProject, self).__init__(suite, name, subDir, srcDirs, deps, workingSets, srcFrom.dir, "shared_lib", **kwargs)
