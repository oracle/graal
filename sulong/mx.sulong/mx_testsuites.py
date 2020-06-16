#
# Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import mx_buildtools

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


def runTestSuite(testsuiteproject, args, testClasses=None, vmArgs=None):
    """compile and run external testsuite projects"""
    project = mx.project(testsuiteproject)
    assert isinstance(project, SulongTestSuite)
    project.runTestSuite(testClasses, vmArgs)


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
                if 'gcc' in v and not SulongTestSuite.haveDragonegg():
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
                 buildSharedObject=False, **args):
        projectDir = args.pop('dir', None)
        if projectDir:
            d = os.path.join(suite.dir, projectDir)
        elif subDir is None:
            d = os.path.join(suite.dir, name)
        else:
            d = os.path.join(suite.dir, subDir, name)
        super(SulongTestSuite, self).__init__(suite, name, subDir, deps, workingSets, results, output, d, **args)
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

    def runTestSuite(self, testClasses=None, vmArgs=None):
        if vmArgs is None:
            vmArgs = []
        if hasattr(self, 'extraLibs'):
            vmArgs.append('-Dpolyglot.llvm.libraries=' + ':'.join(self.extraLibs))
        if hasattr(self, 'fileExts'):
            vmArgs += ['-Dsulongtest.fileExtensionFilter=' + ':'.join(self.fileExts)]
        if testClasses is None:
            testClasses = self.testClasses
        return run(vmArgs, testClasses)

    @staticmethod
    def haveDragonegg():
        if not hasattr(SulongTestSuite, '_haveDragonegg'):
            SulongTestSuite._haveDragonegg = mx_sulong.dragonEggPath() is not None and os.path.exists(mx_sulong.dragonEggPath()) and mx_sulong.getGCC(optional=True) is not None
        return SulongTestSuite._haveDragonegg

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = []
            root = os.path.join(self.dir)
            for path, _, files in os.walk(root):
                for f in files:
                    absPath = os.path.join(path, f)
                    relPath = os.path.relpath(absPath, root)
                    _, ext = os.path.splitext(relPath)
                    if ext in getattr(self, "fileExts", ['.c', '.cpp', '.ll']):
                        self._tests.append(relPath + ".dir")
        return self._tests

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
        if SulongTestSuite.haveDragonegg():
            env['DRAGONEGG'] = mx_sulong.dragonEggPath()
            env['DRAGONEGG_GCC'] = mx_sulong.getGCC()
            env['DRAGONEGG_LLVMAS'] = mx_sulong.findLLVMProgramForDragonegg("llvm-as")
            env['DRAGONEGG_FC'] = mx_sulong.getGFortran()
            env['FC'] = mx_sulong.getGFortran()
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

    def runTestSuite(self, testClasses=None, vmArgs=None):
        if vmArgs is None:
            vmArgs = []
        vmArgs += [
            "-Dsulongtest.externalTestSuitePath=" + self.getOutput(),
            "-Dsulongtest.testSourcePath=" + self.get_test_source(),
            "-Dsulongtest.testConfigPath=" + os.path.join(self.dir, "..", self.configDir),
            ]
        if hasattr(self, 'extraLibs'):
            vmArgs.append('-Dpolyglot.llvm.libraries=' + ':'.join(self.extraLibs))
        if hasattr(self, 'fileExts'):
            vmArgs += ['-Dsulongtest.fileExtensionFilter=' + ':'.join(self.fileExts)]
        if testClasses is None:
            testClasses = self.testClasses
        return run(vmArgs, testClasses)

    def defaultTestClasses(self):
        return ["com.oracle.truffle.llvm.tests.GCCSuite"]

    def getTestFile(self):
        if not hasattr(self, '_testfile'):
            self._testfile = os.path.join(self.getOutput(), 'test.include')
            _tests = self._get_test_intern()
            targets = ["ref.out"] if self.buildRef else []
            targets += [var + ".bc" for var in self.getVariants()]
            with open(self._testfile, 'w') as f:
                mx.logv("Writing test file: " + self._testfile)
                for test in _tests:
                    f.write("default: {}\n".format(" ".join([os.path.join(test + ".dir", t) for t in targets])))
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
        for exclude in mx_buildtools.collectExcludes(os.path.join(self.dir, "..", self.configDir)):
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
                    _tests.append(relPath)

        return _tests

    def get_test_source(self, resolve=False):
        roots = [d.get_path(resolve=resolve) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        assert len(roots) == 1
        return roots[0]

    def getTests(self):
        # tests are in a test file
        return ""

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(ExternalTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        roots = [d.get_path(resolve=True) for d in self.buildDependencies if d.isPackedResourceLibrary()]
        env['VPATH'] = ':'.join(roots)
        env['TESTFILE'] = self.getTestFile()
        return env
