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
import os
import subprocess
from argparse import ArgumentParser
from collections import OrderedDict

import mx
import mx_subst
import mx_sulong
import mx_unittest

from mx_gate import Task, add_gate_runner, add_gate_argument

import mx_sulong_suite_constituents

_suite = mx.suite('sulong')

def _sulong_gate_unittest(title, test_suite, tasks, args, tags=None, testClasses=None, unittestArgs=None):
    f = UnittestTaskFactory()
    f.add(title, test_suite, args, tags=tags, testClasses=testClasses, unittestArgs=unittestArgs)
    f.execute(tasks)


class TestSuiteBuildTask(object):
    def __init__(self, title, test_suite, tags, extra_build_args=None):
        self.title = title
        self.test_suite = test_suite
        self.tags = tags
        self.extra_build_args = extra_build_args

    def execute(self, tasks):
        with Task('Build' + self.title, tasks, tags=self.tags) as t:
            if t:
                mx_sulong_suite_constituents.compileTestSuite(self.test_suite, self.extra_build_args or [])

    def merge(self, other):
        assert self == other
        self.tags.extend([x for x in other.tags if x not in self.tags])

    def __eq__(self, other):
        return isinstance(other, TestSuiteBuildTask) and self.title == other.title and self.test_suite == other.test_suite and self.extra_build_args == other.extra_build_args


class UnittestTaskFactory(object):

    def __init__(self):
        self.build_tasks = []
        self.test_tasks = []

    def add(self, title, test_suite, args, tags=None, testClasses=None, unittestArgs=None):
        if tags is None:
            tags = [test_suite]
        if testClasses is None:
            testClasses = [test_suite]
        build_tags = ['build_' + t for t in tags]
        run_tags = ['run_' + t for t in tags]
        if not unittestArgs:
            unittestArgs = ['--very-verbose', '--enable-timing']
        unittestArgs += args.extra_llvm_arguments

        def _run_test_task(tasks):
            with Task('Test' + title, tasks, tags=tags + run_tags) as t:
                if t: mx_sulong_suite_constituents.run(unittestArgs, testClasses)

        build_task = TestSuiteBuildTask(title, test_suite, tags + build_tags, args.extra_llvm_arguments)
        if build_task in self.build_tasks:
            self.build_tasks.index(build_task).merge(build_task)
        else:
            self.build_tasks.append(build_task)
        self.test_tasks.append(_run_test_task)

    def execute(self, tasks):
        for build_task in self.build_tasks:
            build_task.execute(tasks)
        for test_task in self.test_tasks:
            test_task(tasks)


_sulongTestConfigRoot = os.path.join(_suite.dir, "tests", "configs")


def set_sulong_test_config_root(root):
    global _sulongTestConfigRoot
    _sulongTestConfigRoot = root


def _unittest_config_participant(config):
    (vmArgs, mainClass, mainClassArgs) = config
    vmArgs += get_test_distribution_path_properties(_suite)
    vmArgs += ['-Dsulongtest.configRoot={}'.format(_sulongTestConfigRoot)]
    return (vmArgs, mainClass, mainClassArgs)


def get_test_distribution_path_properties(suite):
    return ['-Dsulongtest.path.{}={}'.format(d.name, d.get_output()) for d in suite.dists if
            d.is_test_distribution() and not d.isClasspathDependency()]


mx_unittest.add_config_participant(_unittest_config_participant)


class SulongGateEnv(object):
    """"Sets a marker environment variable."""
    def __enter__(self):
        os.environ['_MX_SULONG_GATE'] = "1"

    def __exit__(self, exc_type, exc_val, exc_tb):
        del os.environ['_MX_SULONG_GATE']


def _sulong_gate_runner(args, tasks):
    with Task('CheckCopyright', tasks, tags=['style']) as t:
        if t:
            if mx.checkcopyrights(['--primary']) != 0:
                t.abort('Copyright errors found. Please run "mx checkcopyrights --primary -- --fix" to fix them.')

    with Task('BuildLLVMorg', tasks, tags=['style', 'clangformat']) as t:
        # needed for clang-format
        if t: build_llvm_org(args)
    with Task('ClangFormat', tasks, tags=['style', 'clangformat']) as t:
        if t: clangformat([])
    _sulong_gate_unittest('Benchmarks', 'SULONG_SHOOTOUT_TEST_SUITE', tasks, args, testClasses=['ShootoutsSuite'], tags=['benchmarks', 'sulongMisc'])
    _sulong_gate_unittest('Types', 'SULONG_TEST', tasks, args, tags=['type', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.types.floating'])
    _sulong_gate_unittest('Pipe', 'SULONG_TEST', tasks, args, tags=['pipe', 'sulongMisc', 'sulongCoverage'], testClasses=['CaptureOutputTest'])
    _sulong_gate_unittest('LLVM', 'SULONG_LLVM_TEST_SUITE', tasks, args, testClasses=['LLVMSuite'], tags=['llvm', 'sulongCoverage'])
    _sulong_gate_unittest('NWCC', 'SULONG_NWCC_TEST_SUITE', tasks, args, testClasses=['NWCCSuite'], tags=['nwcc', 'sulongCoverage'])
    _sulong_gate_unittest('GCCParserTorture', 'SULONG_PARSER_TORTURE', tasks, args, testClasses=['ParserTortureSuite'], tags=['parser', 'sulongCoverage'])
    _sulong_gate_unittest('GCC_C', 'SULONG_GCC_C_TEST_SUITE', tasks, args, testClasses=['GccCSuite'], tags=['gcc_c', 'sulongCoverage'])
    _sulong_gate_unittest('GCC_CPP', 'SULONG_GCC_CPP_TEST_SUITE', tasks, args, testClasses=['GccCppSuite'], tags=['gcc_cpp', 'sulongCoverage'])
    _sulong_gate_unittest('GCC_Fortran', 'SULONG_GCC_FORTRAN_TEST_SUITE', tasks, args, testClasses=['GccFortranSuite'], tags=['gcc_fortran', 'sulongCoverage'])
    _sulong_gate_unittest('Sulong', 'SULONG_TEST_SUITES', tasks, args, testClasses='SulongSuite', tags=['sulong', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('Interop', 'SULONG_TEST_SUITES', tasks, args, testClasses='com.oracle.truffle.llvm.tests.interop', tags=['interop', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('Linker', 'SULONG_TEST_SUITES', tasks, args, testClasses='com.oracle.truffle.llvm.tests.linker', tags=['linker', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('Debug', 'SULONG_TEST_SUITES', tasks, args, testClasses='LLVMDebugTest', tags=['debug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('IRDebug', 'SULONG_TEST_SUITES', tasks, args, testClasses='LLVMIRDebugTest', tags=['irdebug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('BitcodeFormat', 'SULONG_TEST_SUITES', tasks, args, testClasses='BitcodeFormatTest', tags=['bitcodeFormat', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('DebugExpr', 'SULONG_TEST_SUITES', tasks, args, testClasses='LLVMDebugExprParserTest', tags=['debugexpr', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('OtherTests', 'SULONG_TEST_SUITES', tasks, args, testClasses=['com.oracle.truffle.llvm.tests.other', 'com.oracle.truffle.llvm.tests.bitcode.'], tags=['otherTests', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('Args', 'SULONG_EMBEDDED_TEST_SUITES', tasks, args, tags=['args', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.MainArgsTest'])
    _sulong_gate_unittest('Callback', 'SULONG_EMBEDDED_TEST_SUITES', tasks, args, tags=['callback', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.CallbackTest'])
    _sulong_gate_unittest('Varargs', 'SULONG_EMBEDDED_TEST_SUITES', tasks, args, tags=['vaargs', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.VAArgsTest'])
    with Task('TestToolchain', tasks, tags=['toolchain', 'sulongMisc', 'sulongCoverage']) as t:
        if t:
            with SulongGateEnv():
                mx.command_function('clean')(['--project', 'toolchain-launchers-tests'] + args.extra_build_args)
                mx.command_function('build')(['--project', 'toolchain-launchers-tests'] + args.extra_build_args)


add_gate_runner(_suite, _sulong_gate_runner)
add_gate_argument('--extra-llvm-argument', dest='extra_llvm_arguments', action='append',
                  help='add extra llvm arguments to gate tasks', default=[])



def testLLVMImage(image, imageArgs=None, testFilter=None, libPath=True, test=None, unittestArgs=None):
    """runs the SulongSuite tests on an AOT compiled lli image"""
    args = ['-Dsulongtest.testAOTImage=' + image]
    aotArgs = []
    if libPath:
        aotArgs += [mx_subst.path_substitutions.substitute('<sulong_home>')]
    if imageArgs is not None:
        aotArgs += imageArgs
    if aotArgs:
        args += ['-Dsulongtest.testAOTArgs=' + ' '.join(aotArgs)]
    if testFilter is not None:
        args += ['-Dsulongtest.testFilter=' + testFilter]
    testName = 'SulongSuite'
    if test is not None:
        testName += '#test[' + test + ']'
    if unittestArgs is None:
        unittestArgs = []
    test_suite = 'SULONG_TEST_SUITES'
    mx_sulong_suite_constituents.compileTestSuite(test_suite, extra_build_args=[])
    mx_sulong_suite_constituents.run(args + unittestArgs, testName)


@mx.command(_suite.name, "test-llvm-image")
def _test_llvm_image(args):
    """run the SulongSuite tests on an AOT compiled lli image"""
    parser = ArgumentParser(prog='mx test-llvm-image', description='Run the SulongSuite tests on an AOT compiled LLVM image.',
            epilog='Additional arguments are forwarded to the LLVM image command.')
    parser.add_argument('--omit-library-path', action='store_false', dest='libPath', help='do not add standard library path to arguments')
    parser.add_argument('--test', action='store', dest='test', help='run a single test (default: run all)')
    parser.add_argument('--test-filter', action='store', dest='testFilter', help='filter test variants to execute')
    parser.add_argument('image', help='path to pre-built LLVM image', metavar='<image>')
    for testArg in ['--verbose', '--very-verbose', '--enable-timing', '--color']:
        parser.add_argument(testArg, action='append_const', dest='unittestArgs', const=testArg, help='forwarded to mx unittest')
    (args, imageArgs) = parser.parse_known_args(args)
    testLLVMImage(args.image, imageArgs=imageArgs, testFilter=args.testFilter, libPath=args.libPath, test=args.test, unittestArgs=args.unittestArgs)


# routine for AOT downstream tests
def runLLVMUnittests(unittest_runner):
    """runs the interop unit tests with a different unittest runner (e.g. AOT-based)"""
    libpath = mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:SULONG_TEST_NATIVE>')
    libs = mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraries=<lib:sulongtest>')

    test_harness_dist = mx.distribution('SULONG_TEST')
    java_run_props = [x for x in mx.get_runtime_jvm_args(test_harness_dist) if x.startswith('-D')]
    java_run_props += get_test_distribution_path_properties(_suite)

    test_suite = 'SULONG_TEST_SUITES'
    mx_sulong_suite_constituents.compileTestSuite(test_suite, extra_build_args=[])

    run_args = [libpath, libs] + java_run_props
    build_args = ['--language:llvm'] + java_run_props
    unittest_runner(['com.oracle.truffle.llvm.tests.interop', '--run-args'] + run_args +
                    ['--build-args', '--initialize-at-build-time'] + build_args)


def build_llvm_org(args=None):
    defaultBuildArgs = ['-p']
    if not args.no_warning_as_error:
        defaultBuildArgs += ['--warning-as-error']
    mx.command_function('build')(defaultBuildArgs + ['--project', 'LLVM_TOOLCHAIN'] + args.extra_build_args)


@mx.command(_suite.name, "clangformat")
def clangformat(args=None):
    """ Runs clang-format on C/C++ files in native projects of the primary suite """
    parser = ArgumentParser(prog='mx clangformat')
    parser.add_argument('--with-projects', action='store_true', help='check native projects. Defaults to true unless a path is specified.')
    parser.add_argument('paths', metavar='path', nargs='*', help='check given paths')
    args = parser.parse_args(args)
    paths = [(p, "<cmd-line-argument>") for p in args.paths]

    if not paths or args.with_projects:
        paths += [(p.dir, p.name) for p in mx.projects(limit_to_primary=True) if p.isNativeProject() and getattr(p, "clangFormat", True)]

    error = False
    for f, reason in paths:
        if not checkCFiles(f, reason):
            error = True
    if error:
        mx.log_error("found formatting errors!")
        exit(-1)


def checkCFiles(target, reason):
    error = False
    files_to_check = []
    if os.path.isfile(target):
        files_to_check.append(target)
    else:
        for path, _, files in os.walk(target):
            for f in files:
                if f.endswith('.c') or f.endswith('.cpp') or f.endswith('.h') or f.endswith('.hpp'):
                    files_to_check.append(os.path.join(path, f))
    if not files_to_check:
        mx.logv("clang-format: no files found {} ({})".format(target, reason))
        return True
    mx.logv("clang-format: checking {} ({}, {} files)".format(target, reason, len(files_to_check)))
    for f in files_to_check:
        if not checkCFile(f):
            error = True
    return not error


def checkCFile(targetFile):
    mx.logvv("  checking file " + targetFile)
    """ Checks the formatting of a C file and returns True if the formatting is okay """
    clangFormat = mx_sulong.findBundledLLVMProgram('clang-format')
    formatCommand = [clangFormat, targetFile]
    formattedContent = mx_sulong._decode(subprocess.check_output(formatCommand)).splitlines()
    with open(targetFile) as f:
        originalContent = f.read().splitlines()
    if not formattedContent == originalContent:
        # modify the file to the right format
        subprocess.check_output(formatCommand + ['-i'])
        mx.log('\n'.join(formattedContent))
        mx.log('\nmodified formatting in {0} to the format above'.format(targetFile))
        mx.logv("command: " + " ".join(formatCommand))
        return False
    return True
