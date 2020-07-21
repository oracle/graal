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
import sys
import tarfile
import os
import pipes
import tempfile
from os.path import join, exists, basename
import shutil
import subprocess
from argparse import ArgumentParser

import mx
import mx_gate
import mx_subst
import mx_sdk_vm
import re
import mx_benchmark
import mx_sulong_benchmarks
import mx_buildtools
import mx_sulong_fuzz #pylint: disable=unused-import
import mx_sulong_llvm_config

from mx_gate import Task, add_gate_runner, add_gate_argument

import mx_testsuites

# re-export SulongTestSuite class so it can be used from suite.py
from mx_testsuites import SulongTestSuite #pylint: disable=unused-import
from mx_testsuites import GeneratedTestSuite #pylint: disable=unused-import
from mx_testsuites import ExternalTestSuite #pylint: disable=unused-import

if sys.version_info[0] < 3:
    def _decode(x):
        return x
else:
    def _decode(x):
        return x.decode()

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong")
_root = join(_suite.dir, "projects")
_testDir = join(_suite.dir, "tests")
_toolDir = join(_suite.dir, "cache", "tools")
_clangPath = join(_toolDir, "llvm", "bin", "clang")



# the supported GCC versions (see dragonegg.llvm.org)
supportedGCCVersions = [
    '4.6',
    '4.5',
    '4.7'
]

# the LLVM versions supported by the current bitcode parser that bases on the textual format
# sorted by priority in descending order (highest priority on top)
supportedLLVMVersions = [
    '3.2',
    '3.3',
    '3.8',
    '3.9',
    '4.0',
    '5.0',
    '6.0',
    '7.0',
    '8.0',
    '9.0',
]

toolchainLLVMVersion = mx_sulong_llvm_config.VERSION

# the basic LLVM dependencies for running the test cases and executing the mx commands
basicLLVMDependencies = [
    mx_buildtools.ClangCompiler.CLANG,
    mx_buildtools.ClangCompiler.CLANGXX,
    mx_buildtools.Opt.OPT
]


def _sulong_gate_testdist(title, test_dist, tasks, args, tags=None, testClasses=None, vmArgs=None):
    if tags is None:
        tags = [test_dist]
    build_tags = ['build_' + t for t in tags]
    run_tags = ['run_' + t for t in tags]
    with Task('Build' + title, tasks, tags=tags + build_tags) as t:
        if t: mx_testsuites.compileTestSuite(test_dist, args.extra_build_args)
    with Task('Test' + title, tasks, tags=tags + run_tags) as t:
        if t: mx_testsuites.runTestSuite(test_dist, args, testClasses, vmArgs)

def _sulong_gate_testsuite(title, test_suite, tasks, args, tags=None, testClasses=None, vmArgs=None):
    if tags is None:
        tags = [test_suite]
    build_tags = ['build_' + t for t in tags]
    run_tags = ['run_' + t for t in tags]
    with Task('Build' + title, tasks, tags=tags + build_tags) as t:
        if t: mx_testsuites.compileTestSuite(test_suite, args.extra_build_args)
    with Task('Test' + title, tasks, tags=tags + run_tags) as t:
        if t: mx_testsuites.runTestSuite(test_suite, args, testClasses, (vmArgs or []) + args.extra_llvm_arguments)

def _sulong_gate_unittest(title, test_suite, tasks, args, tags=None, testClasses=None, unittestArgs=None):
    if tags is None:
        tags = [test_suite]
    if testClasses is None:
        testClasses = [test_suite]
    build_tags = ['build_' + t for t in tags]
    run_tags = ['run_' + t for t in tags]
    if not unittestArgs:
        unittestArgs = ['--very-verbose', '--enable-timing']
    unittestArgs += args.extra_llvm_arguments
    with Task('Build' + title, tasks, tags=tags + build_tags) as t:
        if t: mx_testsuites.compileTestSuite(test_suite, args.extra_build_args)
    with Task('Test' + title, tasks, tags=tags + run_tags) as t:
        if t: mx_testsuites.run(unittestArgs, testClasses)

def _sulong_gate_sulongsuite_unittest(title, tasks, args, tags=None, testClasses=None):
    test_suite = 'SULONG_TEST_SUITES'
    _sulong_gate_unittest(title, test_suite, tasks, args, tags=tags, testClasses=testClasses)


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
    _sulong_gate_testsuite('Benchmarks', 'shootout', tasks, args, tags=['benchmarks', 'sulongMisc'])
    _sulong_gate_unittest('Types', 'SULONG_TEST', tasks, args, tags=['type', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.types.floating'])
    _sulong_gate_unittest('Pipe', 'SULONG_TEST', tasks, args, tags=['pipe', 'sulongMisc', 'sulongCoverage'], testClasses=['CaptureOutputTest'])
    _sulong_gate_testsuite('LLVM', 'llvm', tasks, args, tags=['llvm', 'sulongCoverage'])
    _sulong_gate_testsuite('NWCC', 'nwcc', tasks, args, tags=['nwcc', 'sulongCoverage'])
    _sulong_gate_testsuite('GCCParserTorture', 'parserTorture', tasks, args, tags=['parser', 'sulongCoverage'], vmArgs=['-Dpolyglot.llvm.parseOnly=true'])
    _sulong_gate_testsuite('GCC_C', 'gcc_c', tasks, args, tags=['gcc_c', 'sulongCoverage'])
    _sulong_gate_testsuite('GCC_CPP', 'gcc_cpp', tasks, args, tags=['gcc_cpp', 'sulongCoverage'])
    _sulong_gate_testsuite('GCC_Fortran', 'gcc_fortran', tasks, args, tags=['gcc_fortran', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('Sulong', tasks, args, testClasses='SulongSuite', tags=['sulong', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_unittest('SulongLL', 'SULONG_LL_TEST_SUITES', tasks, args, testClasses='com.oracle.truffle.llvm.tests.bitcode.', tags=['sulongLL', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('Interop', tasks, args, testClasses='com.oracle.truffle.llvm.tests.interop', tags=['interop', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('Linker', tasks, args, testClasses='com.oracle.truffle.llvm.tests.linker', tags=['linker', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('Debug', tasks, args, testClasses='LLVMDebugTest', tags=['debug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('IRDebug', tasks, args, testClasses='LLVMIRDebugTest', tags=['irdebug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('BitcodeFormat', tasks, args, testClasses='BitcodeFormatTest', tags=['bitcodeFormat', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('DebugExpr', tasks, args, testClasses='LLVMDebugExprParserTest', tags=['debugexpr', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('OtherTests', tasks, args, testClasses='com.oracle.truffle.llvm.tests.other', tags=['otherTests', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_testsuite('Assembly', 'inlineassemblytests', tasks, args, testClasses='InlineAssemblyTest', tags=['assembly', 'sulongMisc', 'sulongCoverage'])
    _sulong_gate_testsuite('Args', 'other', tasks, args, tags=['args', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.MainArgsTest'])
    _sulong_gate_testsuite('Callback', 'other', tasks, args, tags=['callback', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.CallbackTest'])
    _sulong_gate_testsuite('Varargs', 'other', tasks, args, tags=['vaargs', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.VAArgsTest'])
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
    mx_testsuites.compileTestSuite(test_suite, extra_build_args=[])
    mx_testsuites.run(args + unittestArgs, testName)

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

    test_suite = 'SULONG_TEST_SUITES'
    mx_testsuites.compileTestSuite(test_suite, extra_build_args=[])

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
    paths = args.paths

    if not paths or args.with_projects:
        paths += [p.dir for p in mx.projects(limit_to_primary=True) if p.isNativeProject() and getattr(p, "clangFormat", True)]

    error = False
    for f in paths:
        if not checkCFiles(f):
            error = True
    if error:
        mx.log_error("found formatting errors!")
        exit(-1)


def checkCFiles(target):
    error = False
    files_to_check = []
    if os.path.isfile(target):
        files_to_check.append(target)
    else:
        for path, _, files in os.walk(target):
            for f in files:
                if f.endswith('.c') or f.endswith('.cpp') or f.endswith('.h') or f.endswith('.hpp'):
                    files_to_check.append(join(path, f))
    if not files_to_check:
        mx.logv("clang-format: no files found {}".format(target))
        return True
    mx.logv("clang-format: checking {} ({} files)".format(target, len(files_to_check)))
    for f in files_to_check:
        if not checkCFile(f):
            error = True
    return not error

def checkCFile(targetFile):
    mx.logvv("  checking file " + targetFile)
    """ Checks the formatting of a C file and returns True if the formatting is okay """
    clangFormat = findBundledLLVMProgram('clang-format')
    formatCommand = [clangFormat, targetFile]
    formattedContent = _decode(subprocess.check_output(formatCommand)).splitlines()
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

# platform dependent
def pullLLVMBinaries(args=None):
    """downloads the LLVM binaries"""
    toolDir = join(_toolDir, "llvm")
    mx.ensure_dir_exists(toolDir)
    osStr = mx.get_os()
    arch = mx.get_arch()
    if osStr == 'windows':
        mx.log_error('windows currently only supported with cygwin!')
        return
    elif osStr == 'linux':
        if arch == 'amd64':
            urls = ['https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/clang+llvm-3.2-x86_64-linux-ubuntu-12.04.tar.gz']
        else:
            urls = ['https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/clang+llvm-3.2-x86-linux-ubuntu-12.04.tar.gz']
    elif osStr == 'darwin':
        urls = ['https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/clang+llvm-3.2-x86_64-apple-darwin11.tar.gz']
    elif osStr == 'cygwin':
        urls = ['https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/clang+llvm-3.2-x86-mingw32-EXPERIMENTAL.tar.gz']
    else:
        mx.log_error("{0} {1} not supported!".format(osStr, arch))
    localPath = pullsuite(toolDir, urls)
    tar(localPath, toolDir, stripLevels=1)
    os.remove(localPath)

def dragonEggPath():
    if 'DRAGONEGG' in os.environ:
        return join(os.environ['DRAGONEGG'], mx.add_lib_suffix('dragonegg'))
    if 'DRAGONEGG_GCC' in os.environ:
        path = join(os.environ['DRAGONEGG_GCC'], 'lib', mx.add_lib_suffix('dragonegg'))
        if os.path.exists(path):
            return path
    return None

def dragonEgg(args=None):
    """executes GCC with dragonegg"""
    executeCommand = [getGCC(), "-fplugin=" + dragonEggPath(), '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def dragonEggGFortran(args=None):
    """executes GCC Fortran with dragonegg"""
    executeCommand = [getGFortran(), "-fplugin=" + dragonEggPath(), '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def dragonEggGPP(args=None):
    """executes G++ with dragonegg"""
    executeCommand = [getGPP(), "-fplugin=" + dragonEggPath(), '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def which(program, searchPath=None):
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, _ = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        if searchPath is None:
            searchPath = os.environ["PATH"].split(os.pathsep)
        for path in searchPath:
            path = path.strip('"')
            exe_file = os.path.join(path, program)
            if is_exe(exe_file):
                return exe_file
    return None

def getCommand(envVariable):
    """gets an environment variable and checks that it is an executable program"""
    command = os.getenv(envVariable)
    if command is None:
        return None
    else:
        if which(command) is None:
            mx.abort(envVariable + '=' + command +' specifies an invalid command!')
        else:
            return command

def getGCC(optional=False):
    """tries to locate a gcc version suitable to execute Dragonegg"""
    specifiedGCC = getCommand('SULONG_GCC')
    if specifiedGCC is not None:
        return specifiedGCC
    return findGCCProgram('gcc', optional=optional)

def getGFortran(optional=False):
    """tries to locate a gfortran version suitable to execute Dragonegg"""
    specifiedGFortran = getCommand('SULONG_GFORTRAN')
    if specifiedGFortran is not None:
        return specifiedGFortran
    return findGCCProgram('gfortran', optional=optional)

def getGPP(optional=False):
    """tries to locate a g++ version suitable to execute Dragonegg"""
    specifiedCPP = getCommand('SULONG_GPP')
    if specifiedCPP is not None:
        return specifiedCPP
    return findGCCProgram('g++', optional=optional)

def findLLVMProgramForDragonegg(program):
    """tries to find a supported version of an installed LLVM program; if the program is not found it downloads the LLVM binaries and checks there"""
    installedProgram = findInstalledLLVMProgram(program, ['3.2', '3.3'])

    if installedProgram is None:
        if 'DRAGONEGG_LLVM' in os.environ:
            path = os.environ['DRAGONEGG_LLVM']
        else:
            if not os.path.exists(_clangPath):
                pullLLVMBinaries()
            path = os.path.join(_toolDir, 'llvm')
        return os.path.join(path, 'bin', program)
    else:
        return installedProgram

def tar(tarFile, currentDir, subDirInsideTar=None, stripLevels=None):
    with tarfile.open(tarFile) as tar:
        if subDirInsideTar is None:
            files = tar.getmembers()
        else:
            files = []
            for tarinfo in tar.getmembers():
                for curDir in subDirInsideTar:
                    if tarinfo.name.startswith(curDir):
                        files.append(tarinfo)
        tar.extractall(members=files, path=currentDir)
    if not stripLevels is None:
        if subDirInsideTar is None:
            implicitPathComponents = files[0].name.split(os.sep)[:stripLevels]
            implicitPath = ""
            for comp in implicitPathComponents:
                implicitPath += comp + os.sep
            implicitPath = implicitPath.rstrip('/')
            stripDirectoryList = [implicitPath]
        else:
            stripDirectoryList = subDirInsideTar
        for currentSubDir in stripDirectoryList:
            stripDir(currentDir, currentSubDir, stripLevels)
        toDelete = os.path.join(currentDir, files[0].name.split(os.sep)[0])
        shutil.rmtree(toDelete)

def stripDir(dirPath, dirToStrip, nrLevels):
    cleanedDirPath = dirToStrip.rstrip('/')
    pathComponents = cleanedDirPath.split(os.sep)[nrLevels:]
    strippedPath = ""
    for component in pathComponents:
        strippedPath += component + os.sep
    srcPath = os.path.join(dirPath, dirToStrip)
    destPath = os.path.join(dirPath, strippedPath)
    copytree(srcPath, destPath)

def copytree(src, dst, symlinks=False, ignore=None):
    if not os.path.exists(dst):
        os.makedirs(dst)
    for item in os.listdir(src):
        s = os.path.join(src, item)
        d = os.path.join(dst, item)
        if os.path.isdir(s):
            copytree(s, d, symlinks, ignore)
        else:
            if not os.path.exists(d) or os.stat(s).st_mtime - os.stat(d).st_mtime > 1:
                shutil.copy2(s, d)

def pullTestSuite(library, destDir, **kwargs):
    """downloads and unpacks a test suite"""
    mx.ensure_dir_exists(destDir)
    localPath = mx.library(library).get_path(True)
    tar(localPath, destDir, **kwargs)
    os.remove(localPath)
    sha1Path = localPath + '.sha1'
    if os.path.exists(sha1Path):
        os.remove(sha1Path)

def truffle_extract_VM_args(args, useDoubleDash=False):
    vmArgs, remainder = [], []
    if args is not None:
        for (i, arg) in enumerate(args):
            if any(arg.startswith(prefix) for prefix in ['-X', '-G:', '-D', '-verbose', '-ea', '-da', '-agentlib']) or arg in ['-esa']:
                vmArgs += [arg]
            elif useDoubleDash and arg == '--':
                remainder += args[i:]
                break
            else:
                remainder += [arg]
    return vmArgs, remainder


def extract_compiler_args(args, useDoubleDash=False):
    compilerArgs, remainder = [], []
    if args is not None:
        for (_, arg) in enumerate(args):
            if any(arg.startswith(prefix) for prefix in ['-']):
                compilerArgs += [arg]
            else:
                remainder += [arg]
    return compilerArgs, remainder

def getCommonOptions(withAssertion, lib_args=None):
    options = ['-Dgraal.TruffleCompilationExceptionsArePrinted=true',
        '-Dgraal.ExitVMOnException=true']

    if lib_args is not None:
        options.append('-Dpolyglot.llvm.libraries=' + ':'.join(lib_args))

    options += ['-Xss56m', '-Xms4g', '-Xmx4g']
    if withAssertion:
        options += ['-ea', '-esa']

    return options

def pullsuite(suiteDir, urls):
    name = os.path.basename(urls[0])
    localPath = join(suiteDir, name)
    mx.download(localPath, urls)
    return localPath

def isSupportedLLVMVersion(llvmProgram, supportedVersions=None):
    """returns if the LLVM program bases on a supported LLVM version"""
    assert llvmProgram is not None
    llvmVersion = getLLVMMajorVersion(llvmProgram)
    if supportedVersions is None:
        return llvmVersion in supportedLLVMVersions
    else:
        return llvmVersion in supportedVersions

def isSupportedGCCVersion(gccProgram, supportedVersions=None):
    """returns if the LLVM program bases on a supported LLVM version"""
    assert gccProgram is not None
    gccVersion = getGCCVersion(gccProgram)
    if supportedVersions is None:
        return gccVersion in supportedGCCVersions
    else:
        return gccVersion in supportedVersions

def getVersion(program):
    """executes --version on the supplied program and returns the version string"""
    assert program is not None
    try:
        versionString = _decode(subprocess.check_output([program, '--version']))
    except subprocess.CalledProcessError as e:
        # on my machine, e.g., opt returns a non-zero opcode even on success
        versionString = _decode(e.output)
    return versionString

def getLLVMMajorVersion(llvmProgram):
    """executes the program with --version and extracts the LLVM version string"""
    try:
        versionString = getVersion(llvmProgram)
        printLLVMVersion = re.search(r'(clang |LLVM )?(version )?((\d)\.\d)(\.\d)?', versionString, re.IGNORECASE)
        if printLLVMVersion is None:
            return None
        else:
            return printLLVMVersion.group(3)
    except OSError:
        # clang/llvm not found -> assume we will be using the toolchain
        return toolchainLLVMVersion.split('.')[0]


def getLLVMExplicitArgs(mainLLVMVersion):
    no_optnone = mx.get_env("CLANG_NO_OPTNONE", False)
    return [] if no_optnone else ["-Xclang", "-disable-O0-optnone"]


def get_mx_exe():
    mxpy = join(mx._mx_home, 'mx.py')
    commands = [sys.executable, '-u', mxpy, '--java-home=' + mx.get_jdk().home]
    return ' '.join(commands)


mx_subst.path_substitutions.register_no_arg('mx_exe', get_mx_exe)


def get_jacoco_setting():
    return mx_gate._jacoco


mx_subst.path_substitutions.register_no_arg('jacoco', get_jacoco_setting)


def _subst_get_jvm_args(dep):
    java = mx.get_jdk().java
    main_class = mx.distribution(dep).mainClass
    jvm_args = [pipes.quote(arg) for arg in mx.get_runtime_jvm_args([dep])]
    cmd = [java] + jvm_args + [main_class]
    return " ".join(cmd)


mx_subst.path_substitutions.register_with_arg('get_jvm_cmd_line', _subst_get_jvm_args)

mx.add_argument('--jacoco-exec-file', help='the coverage result file of JaCoCo', default='jacoco.exec')


def mx_post_parse_cmd_line(opts):
    mx_gate.JACOCO_EXEC = opts.jacoco_exec_file


def getGCCVersion(gccProgram):
    """executes the program with --version and extracts the GCC version string"""
    versionString = getVersion(gccProgram)
    gccVersion = re.search(r'((\d\.\d).\d)', versionString, re.IGNORECASE)
    if gccVersion is None:
        exit("could not find the GCC version string in " + str(versionString))
    else:
        return gccVersion.group(2)

def findInstalledLLVMProgram(llvmProgram, supportedVersions=None):
    """tries to find a supported version of a program by checking for the argument string (e.g., clang) and appending version numbers (e.g., clang-3.4) as specified by the postfixes (or supportedLLVMVersions by default)"""
    if supportedVersions is None:
        appends = supportedLLVMVersions
    else:
        appends = supportedVersions
    return findInstalledProgram(llvmProgram, appends, isSupportedLLVMVersion)

def findInstalledGCCProgram(gccProgram):
    """tries to find a supported version of a GCC program by checking for the argument string (e.g., gfortran) and appending version numbers (e.g., gfortran-4.9)"""
    path = None
    if 'DRAGONEGG_GCC' in os.environ:
        path = [os.path.join(os.environ['DRAGONEGG_GCC'], 'bin')]
    return findInstalledProgram(gccProgram, supportedGCCVersions, isSupportedGCCVersion, searchPath=path)

def findInstalledProgram(program, supportedVersions, testSupportedVersion, searchPath=None):
    """tries to find a supported version of a program

    The function takes program argument, and checks if it has the supported version.
    If not, it prepends a supported version to the version string to check if it is an executable program with a supported version.
    The function checks both for programs by appending "-" and the unmodified version string, as well as by directly adding all the digits of the version string (stripping all other characters).

    For example, for a program gcc with supportedVersions 4.6 the function produces gcc-4.6 and gcc46.

    Arguments:
    program -- the program to find, e.g., clang or gcc
    supportedVersions -- the supported versions, e.g., 3.4 or 4.9
    testSupportedVersion(path, supportedVersions) -- the test function to be called to ensure that the found program is supported
    searchPath -- search path to find binaries (defaults to PATH environment variable)
    """
    assert program is not None
    programPath = which(program, searchPath=searchPath)
    if programPath is not None and testSupportedVersion(programPath, supportedVersions):
        return programPath
    else:
        for version in supportedVersions:
            alternativeProgram1 = program + '-' + version
            alternativeProgram2 = program + re.sub(r"\D", "", version)
            alternativePrograms = [alternativeProgram1, alternativeProgram2]
            for alternativeProgram in alternativePrograms:
                alternativeProgramPath = which(alternativeProgram, searchPath=searchPath)
                if alternativeProgramPath is not None:
                    assert testSupportedVersion(alternativeProgramPath, supportedVersions)
                    return alternativeProgramPath
    return None

def findLLVMProgram(llvmProgram, version=None):
    """tries to find a supported version of the given LLVM program; exits if none can be found"""
    installedProgram = findInstalledLLVMProgram(llvmProgram, version)

    if installedProgram is None:
        exit('found no supported version of ' + llvmProgram)
    else:
        return installedProgram

def findGCCProgram(gccProgram, optional=False):
    """tries to find a supported version of an installed GCC program"""
    installedProgram = findInstalledGCCProgram(gccProgram)
    if installedProgram is None and not optional:
        exit('found no supported version ' + str(supportedGCCVersions) + ' of ' + gccProgram)
    else:
        return installedProgram

def findBundledLLVMProgram(llvm_program):
    llvm_dist = 'LLVM_TOOLCHAIN'
    dep = mx.dependency(llvm_dist, fatalIfMissing=True)
    return os.path.join(dep.get_output(), 'bin', llvm_program)

@mx.command(_suite.name, 'llvm-tool', 'Run a tool from the LLVM_TOOLCHAIN distribution')
def llvm_tool(args=None, out=None, **kwargs):
    if len(args) < 1:
        mx.abort("usage: mx llvm-tool <llvm-tool> [args...]")
    llvm_program = findBundledLLVMProgram(args[0])
    mx.run([llvm_program] + args[1:], out=out, **kwargs)


_LLVM_EXTRA_TOOL_DIST = 'LLVM_TOOLCHAIN_FULL'
@mx.command(_suite.name, 'llvm-extra-tool', 'Run a tool from the ' + _LLVM_EXTRA_TOOL_DIST + ' distribution')
def llvm_extra_tool(args=None, out=None, **kwargs):
    if len(args) < 1:
        mx.abort("usage: llvm-extra-tool <llvm-tool> [args...]")
    program = args[0]
    dep = mx.dependency(_LLVM_EXTRA_TOOL_DIST, fatalIfMissing=True)
    llvm_program = os.path.join(dep.get_output(), 'bin', program)
    mx.run([llvm_program] + args[1:], out=out, **kwargs)

def getClasspathOptions(extra_dists=None):
    """gets the classpath of the Sulong distributions"""
    return mx.get_runtime_jvm_args(['SULONG', 'SULONG_LAUNCHER', 'TRUFFLE_NFI'] + (extra_dists or []))

def ensureLLVMBinariesExist():
    """downloads the LLVM binaries if they have not been downloaded yet"""
    for llvmBinary in basicLLVMDependencies:
        if findLLVMProgram(llvmBinary) is None:
            raise Exception(llvmBinary + ' not found')


def _get_sulong_home():
    return mx_subst.path_substitutions.substitute('<path:SULONG_HOME>')

_the_get_sulong_home = _get_sulong_home

def get_sulong_home():
    return _the_get_sulong_home()

def update_sulong_home(new_home):
    global _the_get_sulong_home
    _the_get_sulong_home = new_home

mx_subst.path_substitutions.register_no_arg('sulong_home', get_sulong_home)


def runLLVM(args=None, out=None, err=None, timeout=None, nonZeroIsFatal=True, get_classpath_options=getClasspathOptions):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    dists = []
    if "tools" in (s.name for s in mx.suites()):
        dists.append('CHROMEINSPECTOR')
    return mx.run_java(getCommonOptions(False) + vmArgs + get_classpath_options(dists) + ["com.oracle.truffle.llvm.launcher.LLVMLauncher"] + sulongArgs, timeout=timeout, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err)


def extract_bitcode(args=None, out=None):
    return mx.run_java(mx.get_runtime_jvm_args(["com.oracle.truffle.llvm.tools"]) + ["com.oracle.truffle.llvm.tools.ExtractBitcode"] + args, out=out)


def llvm_dis(args=None, out=None):
    parser = ArgumentParser(prog='mx llvm-dis', description='Disassemble (embedded) LLVM bitcode to LLVM assembly.')
    parser.add_argument('input', help='The input file.', metavar='<input>')
    parser.add_argument('output', help='The output file. If omitted, <input>.ll is used. If <input> ends with ".bc", the ".bc" part is replaced with ".ll".', metavar='<output>', default=None, nargs='?')
    parser.add_argument('llvm_dis_args', help='Additional arguments forwarded to the llvm-dis command', metavar='<arg>', nargs='*')
    parsed_args = parser.parse_args(args)

    def get_bc_filename(orig_path):
        filename, ext = os.path.splitext(orig_path)
        return orig_path if ext == ".bc" else filename + ".bc"

    def get_ll_filename(orig_path):
        filename, ext = os.path.splitext(orig_path)
        return filename + ".ll" if ext == ".bc" else orig_path + ".ll"

    tmp_dir = None
    try:
        # create temp dir
        tmp_dir = tempfile.mkdtemp()
        in_file = parsed_args.input
        tmp_path = os.path.join(tmp_dir, os.path.basename(get_bc_filename(in_file)))

        extract_bitcode([in_file, tmp_path])

        # disassemble into temporary file
        ll_tmp_path = get_ll_filename(tmp_path)
        llvm_tool(["llvm-dis", tmp_path, "-o", ll_tmp_path] + parsed_args.llvm_dis_args)

        # write output file and patch paths
        ll_path = parsed_args.output or get_ll_filename(in_file)

        def _open_for_writing(path):
            if path == "-":
                return sys.stdout
            return open(path, 'w')

        def _open_for_reading(path):
            if path == "-":
                return sys.stdin
            return open(path, 'r')

        with _open_for_reading(ll_tmp_path) as ll_tmp_f, _open_for_writing(ll_path) as ll_f:
            ll_f.writelines((l.replace(tmp_path, in_file) for l in ll_tmp_f))

    finally:
        if tmp_dir:
            shutil.rmtree(tmp_dir)


_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')


# used by mx_sulong_benchmarks:

def opt(args=None, version=None, out=None, err=None):
    """runs opt"""
    return mx.run([findLLVMProgram('opt', version)] + args, out=out, err=err)


mx_benchmark.add_bm_suite(mx_sulong_benchmarks.SulongBenchmarkSuite())


_toolchains = {}


def _get_toolchain(toolchain_name):
    if toolchain_name not in _toolchains:
        mx.abort("Toolchain '{}' does not exists! Known toolchains {}".format(toolchain_name, ", ".join(_toolchains.keys())))
    return _toolchains[toolchain_name]


def _get_toolchain_tool(name_tool):
    name, tool = name_tool.split(",", 2)
    return _get_toolchain(name).get_toolchain_tool(tool)


mx_subst.path_substitutions.register_with_arg('toolchainGetToolPath', _get_toolchain_tool)
mx_subst.path_substitutions.register_with_arg('toolchainGetIdentifier',
                                              lambda name: _get_toolchain(name).get_toolchain_subdir())


def create_toolchain_root_provider(name, dist):
    def provider():
        bootstrap_graalvm = mx.get_env('SULONG_BOOTSTRAP_GRAALVM')
        if bootstrap_graalvm:
            ret = os.path.join(bootstrap_graalvm, 'jre', 'languages', 'llvm', name)
            if os.path.exists(ret): # jdk8 based graalvm
                return ret
            else: # jdk11+ based graalvm
                return os.path.join(bootstrap_graalvm, 'languages', 'llvm', name)
        return mx.distribution(dist).get_output()
    return provider


class ToolchainConfig(object):
    # Please keep this list in sync with Toolchain.java (method documentation) and ToolchainImpl.java (lookup switch block).
    _llvm_tool_map = ["ar", "nm", "objcopy", "objdump", "ranlib", "readelf", "readobj", "strip"]
    _tool_map = {
        "CC": ["graalvm-{name}-clang", "graalvm-clang", "clang", "cc", "gcc"],
        "CXX": ["graalvm-{name}-clang++", "graalvm-clang++", "clang++", "c++", "g++"],
        "LD": ["graalvm-{name}-ld", "ld", "ld.lld", "lld", "ld64"],
        "BINUTIL": ["graalvm-{name}-binutil"] + _llvm_tool_map + ["llvm-" + i for i in _llvm_tool_map]
    }

    def __init__(self, name, dist, bootstrap_dist, tools, suite):
        self.name = name
        self.dist = dist if isinstance(dist, list) else [dist]
        self.bootstrap_provider = create_toolchain_root_provider(name, bootstrap_dist)
        self.tools = tools
        self.suite = suite
        self.mx_command = self.name + '-toolchain'
        self.tool_map = {tool: [alias.format(name=name) for alias in aliases] for tool, aliases in ToolchainConfig._tool_map.items()}
        self.exe_map = {exe: tool for tool, aliases in self.tool_map.items() for exe in aliases}
        # register mx command
        mx.update_commands(_suite, {
            self.mx_command: [self._toolchain_helper, 'launch {} toolchain commands'.format(self.name)],
        })
        # register bootstrap toolchain substitution
        mx_subst.path_substitutions.register_no_arg(name + 'ToolchainRoot', self.bootstrap_provider)
        if self.name in _toolchains:
            mx.abort("Toolchain '{}' registered twice".format(self.name))
        _toolchains[self.name] = self

    def _toolchain_helper(self, args=None, out=None):
        parser = ArgumentParser(prog='mx ' + self.mx_command, description='launch toolchain commands',
                                epilog='Additional arguments are forwarded to the LLVM image command.', add_help=False)
        parser.add_argument('command', help='toolchain command', metavar='<command>',
                            choices=self._supported_exes())
        parsed_args, tool_args = parser.parse_known_args(args)
        main = self._tool_to_main(self.exe_map[parsed_args.command])
        if "JACOCO" in os.environ:
            mx_gate._jacoco = os.environ["JACOCO"]
        return mx.run_java(mx.get_runtime_jvm_args([mx.splitqualname(d)[1] for d in self.dist]) + ['-Dorg.graalvm.launcher.executablename=' + parsed_args.command] + [main] + tool_args, out=out)

    def _supported_exes(self):
        return [exe for tool in self._supported_tools() for exe in self._tool_to_aliases(tool)]

    def _supported_tools(self):
        return self.tools.keys()

    def _tool_to_exe(self, tool):
        return self._tool_to_aliases(tool)[0]

    def _tool_to_aliases(self, tool):
        self._check_tool(tool)
        return self.tool_map[tool]

    def _tool_to_main(self, tool):
        self._check_tool(tool)
        return self.tools[tool]

    def _check_tool(self, tool):
        if tool not in self._supported_tools():
            mx.abort("The {} toolchain (defined by {}) does not support tool '{}'".format(self.name, self.dist[0], tool))

    def get_toolchain_tool(self, tool):
        return os.path.join(self.bootstrap_provider(), 'bin', self._tool_to_exe(tool))

    def get_toolchain_subdir(self):
        return self.name

    def get_launcher_configs(self):
        return [
            mx_sdk_vm.LauncherConfig(
                destination=os.path.join(self.name, 'bin', self._tool_to_exe(tool)),
                jar_distributions=self._get_jar_dists(),
                main_class=self._tool_to_main(tool),
                build_args=[
                    '-H:-ParseRuntimeOptions',  # we do not want `-D` options parsed by SVM
                ],
                is_main_launcher=False,
                default_symlinks=False,
                links=[os.path.join(self.name, 'bin', e) for e in self._tool_to_aliases(tool)[1:]],
            ) for tool in self._supported_tools()
        ]

    def _get_jar_dists(self):
        return [d if ":" in d else self.suite.name + ":" + d for d in self.dist]


class BootstrapToolchainLauncherProject(mx.Project):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        super(BootstrapToolchainLauncherProject, self).__init__(suite, name, srcDirs=[], deps=deps, workingSets=workingSets, d=suite.dir, theLicense=theLicense, **kwArgs)

    def launchers(self):
        for tool in self.suite.toolchain._supported_tools():
            for exe in self.suite.toolchain._tool_to_aliases(tool):
                result = join(self.get_output_root(), exe)
                yield result, tool, join('bin', exe)

    def getArchivableResults(self, use_relpath=True, single=False):
        for result, _, prefixed in self.launchers():
            yield result, prefixed

    def getBuildTask(self, args):
        return BootstrapToolchainLauncherBuildTask(self, args, 1)


class BootstrapToolchainLauncherBuildTask(mx.BuildTask):
    def __str__(self):
        return "Generating " + self.subject.name

    def newestOutput(self):
        return mx.TimeStampFile.newest([result for result, _, _ in self.subject.launchers()])

    def needsBuild(self, newestInput):
        sup = super(BootstrapToolchainLauncherBuildTask, self).needsBuild(newestInput)
        if sup[0]:
            return sup

        for result, tool, _ in self.subject.launchers():
            if not exists(result):
                return True, result + ' does not exist'
            with open(result, "r") as f:
                on_disk = f.read()
            if on_disk != self.contents(tool):
                return True, 'command line changed for ' + basename(result)

        return False, 'up to date'

    def build(self):
        mx.ensure_dir_exists(self.subject.get_output_root())
        for result, tool, _ in self.subject.launchers():
            with open(result, "w") as f:
                f.write(self.contents(tool))
            os.chmod(result, 0o755)

    def clean(self, forBuild=False):
        if exists(self.subject.get_output_root()):
            mx.rmtree(self.subject.get_output_root())

    def contents(self, tool):
        java = mx.get_jdk().java
        classpath_deps = [dep for dep in self.subject.buildDependencies if isinstance(dep, mx.ClasspathDependency)]
        jvm_args = [pipes.quote(arg) for arg in mx.get_runtime_jvm_args(classpath_deps)]
        extra_props = ['-Dorg.graalvm.launcher.executablename="$0"']
        main_class = self.subject.suite.toolchain._tool_to_main(tool)
        command = [java] + jvm_args + extra_props + [main_class, '"$@"']
        return "#!/usr/bin/env bash\n" + "exec " + " ".join(command) + "\n"


class CMakeBuildTask(mx.NativeBuildTask):

    def __str__(self):
        return 'Building {} with CMake'.format(self.subject.name)

    def _build_run_args(self):
        cmdline, cwd, env = super(CMakeBuildTask, self)._build_run_args()

        def _flatten(lst):
            for e in lst:
                if isinstance(e, list):
                    for sub in _flatten(e):
                        yield sub
                else:
                    yield e

        # flatten cmdline to support for multiple make targets
        return list(_flatten(cmdline)), cwd, env

    def build(self):
        # get cwd and env
        self._configure()
        # This is copied from the super call because we want to make it
        # less verbose but calling super does not allow us to do that.
        # super(CMakeBuildTask, self).build()
        cmdline, cwd, env = self._build_run_args()
        if mx._opts.verbose:
            mx.run(cmdline, cwd=cwd, env=env)
        else:
            with open(os.devnull, 'w') as fnull:
                mx.run(cmdline, cwd=cwd, env=env, out=fnull)
        self._newestOutput = None
        # END super(CMakeBuildTask, self).build()
        source_dir = self.subject.source_dirs()[0]
        self._write_guard(self.guard_file(), source_dir, self.subject.cmake_config())

    def needsBuild(self, newestInput):
        mx.logv('Checking whether to build {} with CMake'.format(self.subject.name))
        need_configure, reason = self._need_configure()
        return need_configure, "rebuild needed by CMake ({})".format(reason)

    def _write_guard(self, guard_file, source_dir, cmake_config):
        with open(guard_file, 'w') as fp:
            fp.write(self._guard_data(source_dir, cmake_config))

    def _guard_data(self, source_dir, cmake_config):
        return source_dir + '\n' + '\n'.join(cmake_config)

    def _check_cmake(self):
        try:
            self.run_cmake(["--version"], silent=False, nonZeroIsFatal=False)
        except OSError as e:
            mx.abort(str(e) + "\nError executing 'cmake --version'. Are you sure 'cmake' is installed? ")

    def _need_configure(self):
        source_dir = self.subject.source_dirs()[0]
        guard_file = self.guard_file()
        if not os.path.exists(os.path.join(self.subject.dir, 'Makefile')):
            return True, "No existing Makefile - reconfigure"
        cmake_config = self.subject.cmake_config()
        if not os.path.exists(guard_file):
            return True, "No guard file - reconfigure"
        with open(guard_file, 'r') as fp:
            if fp.read() != self._guard_data(source_dir, cmake_config):
                return True, "Guard file changed - reconfigure"
            return False, None

    def _configure(self, silent=False):
        need_configure, _ = self._need_configure()
        if not need_configure:
            return
        _, cwd, env = self._build_run_args()
        source_dir = self.subject.source_dirs()[0]
        cmakefile = os.path.join(self.subject.dir, 'CMakeCache.txt')
        if os.path.exists(cmakefile):
            # remove cache file if it exist
            os.remove(cmakefile)
        cmdline = ["-G", "Unix Makefiles", source_dir] + self.subject.cmake_config()
        self._check_cmake()
        self.run_cmake(cmdline, silent=silent, cwd=cwd, env=env)
        return True

    def run_cmake(self, cmdline, silent, *args, **kwargs):
        if mx._opts.verbose:
            mx.run(["cmake"] + cmdline, *args, **kwargs)
        else:
            with open(os.devnull, 'w') as fnull:
                err = fnull if silent else None
                mx.run(["cmake"] + cmdline, out=fnull, err=err, *args, **kwargs)

    def guard_file(self):
        return os.path.join(self.subject.dir, 'mx.cmake.rebuild.guard')


class CMakeProject(mx.NativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        projectDir = args.pop('dir', None)
        if projectDir:
            d = join(suite.dir, projectDir)
        elif subDir is None:
            d = join(suite.dir, name)
        else:
            d = join(suite.dir, subDir, name)
        srcDir = args.pop('sourceDir', d)
        if not srcDir:
            mx.abort("Exactly one 'sourceDir' is required")
        srcDir = mx_subst.path_substitutions.substitute(srcDir)
        cmake_config = args.pop('cmakeConfig', {})
        self.cmake_config = lambda: ['-D{}={}'.format(k, mx_subst.path_substitutions.substitute(v).replace('{{}}', '$')) for k, v in sorted(cmake_config.items())]

        super(CMakeProject, self).__init__(suite, name, subDir, [srcDir], deps, workingSets, results, output, d, **args)
        self.dir = self.getOutput()

    def getBuildTask(self, args):
        return CMakeBuildTask(args, self)


class DocumentationBuildTask(mx.AbstractNativeBuildTask):
    def __str__(self):
        return 'Building {} with Documentation Build Task'.format(self.subject.name)

    def build(self):
        pass

    def needsBuild(self, newestInput):
        return False, None

    def clean(self, forBuild=False):
        pass


class DocumentationProject(mx.NativeProject):  # pylint: disable=too-many-ancestors
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        projectDir = args.pop('dir', None)
        if projectDir:
            d = join(suite.dir, projectDir)
        elif subDir is None:
            d = join(suite.dir, name)
        else:
            d = join(suite.dir, subDir, name)
        srcDir = args.pop('sourceDir', d)
        if not srcDir:
            mx.abort("Exactly one 'sourceDir' is required")
        srcDir = mx_subst.path_substitutions.substitute(srcDir)
        super(DocumentationProject, self).__init__(suite, name, subDir, [srcDir], deps, workingSets, results, output, d, **args)
        self.dir = d

    def getBuildTask(self, args):
        return DocumentationBuildTask(args, self)


_suite.toolchain = ToolchainConfig('native', 'SULONG_TOOLCHAIN_LAUNCHERS', 'SULONG_BOOTSTRAP_TOOLCHAIN',
                                   # unfortunately, we cannot define those in the suite.py because graalvm component
                                   # registration runs before the suite is properly initialized
                                   tools={
                                       "CC": "com.oracle.truffle.llvm.toolchain.launchers.Clang",
                                       "CXX": "com.oracle.truffle.llvm.toolchain.launchers.ClangXX",
                                       "LD": "com.oracle.truffle.llvm.toolchain.launchers.Linker",
                                       "BINUTIL": "com.oracle.truffle.llvm.toolchain.launchers.BinUtil",
                                   },
                                   suite=_suite)


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Sulong',
    short_name='slg',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle', 'Truffle NFI'],
    truffle_jars=['sulong:SULONG', 'sulong:SULONG_API'],
    support_distributions=[
        'sulong:SULONG_HOME',
        'sulong:SULONG_GRAALVM_DOCS',
    ],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination='bin/<exe:lli>',
            jar_distributions=['sulong:SULONG_LAUNCHER'],
            main_class='com.oracle.truffle.llvm.launcher.LLVMLauncher',
            build_args=[],
            language='llvm',
        ),
    ] + _suite.toolchain.get_launcher_configs(),
    installable=False,
))


COPYRIGHT_HEADER_BSD = """\
/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// Checkstyle: stop
//@formatter:off
{0}
"""


COPYRIGHT_HEADER_BSD_HASH = """\
#
# Copyright (c) 2020, 2020, Oracle and/or its affiliates.
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
"""


def create_asm_parser(args=None, out=None):
    """create the inline assembly parser using antlr"""
    mx.suite("truffle").extensions.create_parser("com.oracle.truffle.llvm.asm.amd64", "com.oracle.truffle.llvm.asm.amd64", "InlineAssembly", COPYRIGHT_HEADER_BSD, args, out)

def create_debugexpr_parser(args=None, out=None):
    """create the debug expression parser using antlr"""
    mx.suite("truffle").extensions.create_parser(grammar_project="com.oracle.truffle.llvm.runtime",
                                                 grammar_package="com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr",
                                                 grammar_name="DebugExpression",
                                                 copyright_template=COPYRIGHT_HEADER_BSD, args=args, out=out)

def create_parsers(args=None, out=None):
    create_asm_parser(args, out)
    create_debugexpr_parser(args, out)


def _write_llvm_config_java(constants, file_comment=None):
    package_name = "com.oracle.truffle.llvm.toolchain.config"
    project_name = package_name
    class_name = "LLVMConfig"
    source_gen_dir = mx.dependency(project_name).source_dirs()[0]
    rel_file = package_name.split(".") + [class_name + ".java"]
    src_file = os.path.join(source_gen_dir, *rel_file)
    mx.ensure_dir_exists(os.path.dirname(src_file))
    with open(src_file, "w") as fp:
        mx.log("Generating {}".format(src_file))
        fp.write(COPYRIGHT_HEADER_BSD.format("package {package};".format(package=package_name)))
        if file_comment:
            fp.write("\n/**\n * {}\n */\n".format(file_comment))
        fp.write("public abstract class {class_name} {{\n".format(class_name=class_name))
        fp.write("\n    private {class_name}() {{}}\n\n".format(class_name=class_name))
        for const_name, value, description in constants:
            fp.write("    /** {} */\n".format(description))
            if isinstance(value, int):
                fp.write("    public static final int {} = {};\n".format(const_name, value))
            else:
                fp.write("    public static final String {} = \"{}\";\n".format(const_name, value))
        fp.write("}\n")


def _write_llvm_config_mx(constants, file_comment=None):
    file_name = "mx_sulong_llvm_config.py"
    src_file = os.path.join(_suite.mxDir, file_name)
    mx.ensure_dir_exists(os.path.dirname(src_file))
    with open(src_file, "w") as fp:
        mx.log("Generating {}".format(src_file))
        fp.write(COPYRIGHT_HEADER_BSD_HASH)
        if file_comment:
            fp.write("\n# {}\n\n".format(file_comment))
        for const_name, value, description in constants:
            fp.write("# {}\n".format(description))
            if isinstance(value, int):
                fp.write("{} = {}\n".format(const_name, value))
            else:
                fp.write("{} = \"{}\"\n".format(const_name, value))


GENERATE_LLVM_CONFIG = "generate-llvm-config"


@mx.command(_suite.name, GENERATE_LLVM_CONFIG)
def generate_llvm_config(args=None, **kwargs):

    constants = []

    # get config full string
    out = mx.OutputCapture()
    llvm_tool(["llvm-config", "--version"] + list(args), out=out)
    full_version = out.data.strip()
    # NOTE: do not add full version until we need it to avoid regeneration
    # constants.append(("VERSION_FULL", full_version, "Full LLVM version string."))
    # version without suffix
    s = full_version.split("-", 3)
    version = s[0]
    constants.append(("VERSION", version, "LLVM version string."))
    # major, minor, patch
    s = version.split(".", 3)
    major_version, minor_version, patch_version = s[0], s[1], s[2]
    constants.append(("VERSION_MAJOR", int(major_version), "Major version of the LLVM API."))
    constants.append(("VERSION_MINOR", int(minor_version), "Minor version of the LLVM API."))
    constants.append(("VERSION_PATCH", int(patch_version), "Patch version of the LLVM API."))

    file_comment = "GENERATED BY 'mx {}'. DO NOT MODIFY.".format(GENERATE_LLVM_CONFIG)

    _write_llvm_config_java(constants, file_comment)
    _write_llvm_config_mx(constants, file_comment)


@mx.command(_suite.name, "create-generated-sources", usage_msg="# recreate generated source files (parsers, config files)")
def create_generated_sources(args=None, out=None):
    create_parsers(args, out=out)
    generate_llvm_config(args, out=out)


def llirtestgen(args=None, out=None):
    return mx.run_java(mx.get_runtime_jvm_args(["LLIR_TEST_GEN"]) + ["com.oracle.truffle.llvm.tests.llirtestgen.LLIRTestGen"] + args, out=out)

mx.update_commands(_suite, {
    'lli' : [runLLVM, ''],
    'test-llvm-image' : [_test_llvm_image, 'test a pre-built LLVM image'],
    'create-asm-parser' : [create_asm_parser, 'create the inline assembly parser using antlr'],
    'create-debugexpr-parser' : [create_debugexpr_parser, 'create the debug expression parser using antlr'],
    'create-parsers' : [create_parsers, 'create the debug expression and the inline assembly parser using antlr'],
    'extract-bitcode' : [extract_bitcode, 'Extract embedded LLVM bitcode from object files'],
    'llvm-dis' : [llvm_dis, 'Disassemble (embedded) LLVM bitcode to LLVM assembly'],
})
