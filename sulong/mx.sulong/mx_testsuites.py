#
# Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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

import fnmatch

import mx
import mx_unittest
import mx_subst
import os

import mx_sulong

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


class SulongTestSuiteBuildTask(mx.NativeBuildTask):
    """Track whether we are checking if a build is required or actually building."""
    def needsBuild(self, newestInput):
        try:
            self.subject._is_needs_rebuild_call = True
            return super(SulongTestSuiteBuildTask, self).needsBuild(newestInput)
        finally:
            self.subject._is_needs_rebuild_call = False


class SulongTestSuiteBase(mx.NativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, subDir, deps, workingSets, results, output, d, **args):
        super(SulongTestSuiteBase, self).__init__(suite, name, subDir, [], deps, workingSets, results, output, d, **args)

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
                if self.buildRef:
                    self.results.append(os.path.join(t, 'ref.out'))
                for v in self.getVariants():
                    result_file = mx.add_lib_suffix(v) if self.buildSharedObject else v + '.bc'
                    self.results.append(os.path.join(t, result_file))
        return super(SulongTestSuiteBase, self).getResults(replaceVar=replaceVar)


class SulongTestSuite(SulongTestSuiteBase):  # pylint: disable=too-many-ancestors
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
        super(SulongTestSuite, self).__init__(suite, name, subDir, deps, workingSets, results, output, d, **args)
        if bundledLLVMOnly and mx.get_env('CLANG_CC', None):
            self.ignore = "Environment variable 'CLANG_CC' is set but project specifies 'bundledLLVMOnly'"
        self.vpath = True
        self.buildRef = buildRef
        self.buildSharedObject = buildSharedObject
        self._is_needs_rebuild_call = False
        if not hasattr(self, 'testClasses'):
            self.testClasses = self.defaultTestClasses()

    def getBuildTask(self, args):
        return SulongTestSuiteBuildTask(args, self)

    def defaultTestClasses(self):
        return ["SulongSuite"]

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
                        self._tests.append(relPath + ".dir")
        return self._tests

    def _get_vpath(self):
        env = super(SulongTestSuite, self).getBuildEnv()
        return env.get('VPATH', self.dir)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(SulongTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        env['PROJECT'] = self.name
        env['TESTS'] = ' '.join(self.getTests())
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


class GeneratedTestSuite(SulongTestSuiteBase):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, buildRef=True,
                 buildSharedObject=False, **args):
        d = os.path.join(suite.dir, subDir, name)
        super(GeneratedTestSuite, self).__init__(suite, name, subDir, deps, workingSets, results, output, d, **args)
        self.vpath = True
        self.buildRef = buildRef
        self.buildSharedObject = buildSharedObject
        self._is_needs_rebuild_call = False

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = []

            def enlist(line):
                line = line.strip()
                if not line.endswith(".ignore"):
                    self._tests += [line + ".dir"]

            mx_sulong.llirtestgen(["gen", "--print-filenames"], out=enlist)
        return self._tests

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(GeneratedTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        env['VPATH'] = self.dir
        env['PROJECT'] = self.name
        env['TESTS'] = ' '.join(self.getTests())
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
        return env


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


class ExternalTestSuite(SulongTestSuite):  # pylint: disable=too-many-ancestors
    def __init__(self, *args, **kwargs):
        super(ExternalTestSuite, self).__init__(*args, **kwargs)
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


    def defaultTestClasses(self):
        return ["com.oracle.truffle.llvm.tests.GCCSuite"]

    def getTestFile(self):
        if not hasattr(self, '_testfile'):
            self._testfile = os.path.join(self.getOutput(), 'test.include')
            with open(self._testfile, 'w') as f:
                mx.logv("Writing test file: " + self._testfile)
                # call getResults() ensure self.results is populated
                _ = self.getResults()
                f.write("\n".join(("default: " + r for r in self.results)))
        return self._testfile

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
                _, ext = os.path.splitext(relPath)
                if ext in self.fileExts and relPath not in exclude_files and not _match_pattern(relPath):
                    _tests.append(relPath + ".dir")

        return _tests

    def get_test_source(self, resolve=False):
        roots = [d.get_path(resolve=resolve) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        assert len(roots) == 1
        return roots[0]

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = self._get_test_intern()
        return self._tests

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(ExternalTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        roots = [d.get_path(resolve=True) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        # we pass tests via TESTFILE to avoid "argument list too long" issue
        env['TESTS'] = ''
        env['VPATH'] = ':'.join(roots)
        env['TESTFILE'] = self.getTestFile()
        return env
