#
# Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
from os.path import join
import shutil
import subprocess
from argparse import ArgumentParser

import mx
import mx_gate
import mx_subst
import mx_sdk
import re
import mx_benchmark
import mx_sulong_benchmarks
import mx_buildtools

from mx_gate import Task, add_gate_runner, add_gate_argument

import mx_testsuites

# re-export SulongTestSuite class so it can be used from suite.py
from mx_testsuites import SulongTestSuite #pylint: disable=unused-import
from mx_testsuites import ExternalTestSuite #pylint: disable=unused-import
from mx_testsuites import GlobNativeProject #pylint: disable=unused-import

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
    '8.0'
]

# the basic LLVM dependencies for running the test cases and executing the mx commands
basicLLVMDependencies = [
    mx_buildtools.ClangCompiler.CLANG,
    mx_buildtools.ClangCompiler.CLANGXX,
    mx_buildtools.Opt.OPT
]

# the file paths that we want to check with clang-format
clangFormatCheckPaths = [
    join(_suite.dir, "include"),
    join(_root, "com.oracle.truffle.llvm.libraries.bitcode", "src"),
    join(_root, "com.oracle.truffle.llvm.libraries.bitcode", "include"),
    join(_root, "com.oracle.truffle.llvm.tests.pipe.native", "src"),
    join(_testDir, "com.oracle.truffle.llvm.tests.sulong"),
    join(_testDir, "com.oracle.truffle.llvm.tests.sulongcpp"),
    join(_testDir, "interoptests"),
    join(_testDir, "inlineassemblytests"),
    join(_testDir, "other")
]

# the clang-format versions that can be used for formatting the test case C and C++ files
clangFormatVersions = [
    '3.8',
    '3.9',
    '4.0',
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
        unittestArgs = []
    unittestArgs += args.extra_llvm_arguments
    with Task('Build' + title, tasks, tags=tags + build_tags) as t:
        if t: mx_testsuites.compileTestSuite(test_suite, args.extra_build_args)
    with Task('Test' + title, tasks, tags=tags + run_tags) as t:
        if t: mx_testsuites.run(unittestArgs, testClasses)

def _sulong_gate_sulongsuite_unittest(title, tasks, args, tags=None, testClasses=None):
    test_suite = 'SULONG_TEST_SUITES'
    _sulong_gate_unittest(title, test_suite, tasks, args, tags=tags, testClasses=testClasses)

def _sulong_gate_runner(args, tasks):
    with Task('CheckCopyright', tasks, tags=['style']) as t:
        if t:
            if mx.checkcopyrights(['--primary']) != 0:
                t.abort('Copyright errors found. Please run "mx checkcopyrights --primary -- --fix" to fix them.')

    with Task('BuildLLVMorg', tasks, tags=['style', 'clangformat']) as t:
        # needed for clang-format
        if t: build_llvm_org(args)
    with Task('ClangFormat', tasks, tags=['style', 'clangformat']) as t:
        if t: clangformatcheck()
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
    _sulong_gate_sulongsuite_unittest('Interop', tasks, args, testClasses='com.oracle.truffle.llvm.tests.interop', tags=['interop', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('Debug', tasks, args, testClasses='LLVMDebugTest', tags=['debug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('IRDebug', tasks, args, testClasses='LLVMIRDebugTest', tags=['irdebug', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('BitcodeFormat', tasks, args, testClasses='BitcodeFormatTest', tags=['bitcodeFormat', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_sulongsuite_unittest('OtherTests', tasks, args, testClasses='com.oracle.truffle.llvm.tests.other', tags=['otherTests', 'sulongBasic', 'sulongCoverage'])
    _sulong_gate_testsuite('Assembly', 'inlineassemblytests', tasks, args, testClasses='InlineAssemblyTest', tags=['assembly', 'sulongMisc', 'sulongCoverage'])
    _sulong_gate_testsuite('Args', 'other', tasks, args, tags=['args', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.MainArgsTest'])
    _sulong_gate_testsuite('Callback', 'other', tasks, args, tags=['callback', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.CallbackTest'])
    _sulong_gate_testsuite('Varargs', 'other', tasks, args, tags=['vaargs', 'sulongMisc', 'sulongCoverage'], testClasses=['com.oracle.truffle.llvm.tests.VAArgsTest'])
    with Task('TestToolchain', tasks, tags=['toolchain', 'sulongMisc', 'sulongCoverage']) as t:
        if t:
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
    mx.command_function('build')(defaultBuildArgs + ['--project', 'SULONG_LLVM_ORG'] + args.extra_build_args)


def clangformatcheck(args=None):
    """ Performs a format check on the include/truffle.h file """
    for f in clangFormatCheckPaths:
        checkCFiles(f)

def checkCFiles(targetDir):
    error = False
    for path, _, files in os.walk(targetDir):
        for f in files:
            if f.endswith('.c') or f.endswith('.cpp') or f.endswith('.h') or f.endswith('.hpp'):
                if not checkCFile(path + '/' + f):
                    error = True
    if error:
        mx.log_error("found formatting errors!")
        exit(-1)

def checkCFile(targetFile):
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
    options.append(getLLVMRootOption())
    if withAssertion:
        options += ['-ea', '-esa']

    return options

def getLLVMRootOption():
    return "-Dsulongtest.projectRoot=" + _root

def pullsuite(suiteDir, urls):
    name = os.path.basename(urls[0])
    localPath = join(suiteDir, name)
    mx.download(localPath, urls)
    return localPath

def isSupportedLLVMVersion(llvmProgram, supportedVersions=None):
    """returns if the LLVM program bases on a supported LLVM version"""
    assert llvmProgram is not None
    llvmVersion = getLLVMVersion(llvmProgram)
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

def getLLVMVersion(llvmProgram):
    """executes the program with --version and extracts the LLVM version string"""
    versionString = getVersion(llvmProgram)
    printLLVMVersion = re.search(r'(clang |LLVM )?(version )?((\d)\.\d)(\.\d)?', versionString, re.IGNORECASE)
    if printLLVMVersion is None:
        return None
    else:
        return printLLVMVersion.group(3)

# the makefiles do not check which version of clang they invoke
versions_dont_have_optnone = ['3', '4']
def getLLVMExplicitArgs(mainLLVMVersion):
    if mainLLVMVersion:
        for ver in versions_dont_have_optnone:
            if mainLLVMVersion.startswith(ver):
                return []
    return ["-Xclang", "-disable-O0-optnone"]

def getClangImplicitArgs():
    mainLLVMVersion = getLLVMVersion(mx_buildtools.ClangCompiler.CLANG)
    return " ".join(getLLVMExplicitArgs(mainLLVMVersion))

mx_subst.path_substitutions.register_no_arg('clangImplicitArgs', getClangImplicitArgs)


def get_mx_exe():
    mxpy = join(mx._mx_home, 'mx.py')
    commands = [sys.executable, '-u', mxpy, '--java-home=' + mx.get_jdk().home]
    return ' '.join(commands)


mx_subst.path_substitutions.register_no_arg('mx_exe', get_mx_exe)


def get_jacoco_setting():
    return mx_gate._jacoco


mx_subst.path_substitutions.register_no_arg('jacoco', get_jacoco_setting)


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
    llvm_dist = 'SULONG_LLVM_ORG'
    dep = mx.dependency(llvm_dist, fatalIfMissing=True)
    return os.path.join(dep.get_output(), 'bin', llvm_program)

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


def runLLVM(args=None, out=None, get_classpath_options=getClasspathOptions):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    dists = []
    if "tools" in (s.name for s in mx.suites()):
        dists.append('CHROMEINSPECTOR')
    return mx.run_java(getCommonOptions(False) + vmArgs + get_classpath_options(dists) + ["com.oracle.truffle.llvm.launcher.LLVMLauncher"] + sulongArgs, out=out)


def extract_bitcode(args=None, out=None):
    return mx.run_java(mx.get_runtime_jvm_args(["com.oracle.truffle.llvm.tools"]) + ["com.oracle.truffle.llvm.tools.ExtractBitcode"] + args, out=out)


_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')


# used by mx_sulong_benchmarks:

def opt(args=None, version=None, out=None, err=None):
    """runs opt"""
    return mx.run([findLLVMProgram('opt', version)] + args, out=out, err=err)

# Project classes

import glob

class ArchiveProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, theLicense, **args):
        mx.ArchivableProject.__init__(self, suite, name, deps, workingSets, theLicense)
        assert 'prefix' in args
        assert 'outputDir' in args

    def output_dir(self):
        return join(self.dir, self.outputDir)

    def archive_prefix(self):
        return self.prefix

    def getResults(self):
        return mx.ArchivableProject.walk(self.output_dir())

class SulongDocsProject(ArchiveProject):
    doc_files = (glob.glob(join(_suite.dir, 'LICENSE')) +
        glob.glob(join(_suite.dir, '*.md')))

    def getResults(self):
        return [join(_suite.dir, f) for f in self.doc_files]


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


class ToolchainConfig(object):
    _tool_map = {
        "CC": ["graalvm-{name}-clang", "graalvm-clang", "clang", "cc", "gcc"],
        "CXX": ["graalvm-{name}-clang++", "graalvm-clang++", "clang++", "c++", "g++"],
    }

    def __init__(self, name, dist, bootstrap_dist, tools, suite):
        self.name = name
        self.dist = dist
        self.bootstrap_dist = bootstrap_dist
        self.tools = tools
        self.suite = suite
        self.mx_command = self.name + '-toolchain'
        self.tool_map = {tool: [alias.format(name=name) for alias in aliases] for tool, aliases in ToolchainConfig._tool_map.items()}
        self.exe_map = {exe: tool for tool, aliases in self.tool_map.items() for exe in aliases}
        # register mx command
        mx.update_commands(_suite, {
            self.mx_command: [self._toolchain_helper, 'launch {} toolchain commands'.format(self.name)],
        })
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
        return mx.run_java(mx.get_runtime_jvm_args([self.dist]) + [main] + tool_args, out=out)

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
            mx.abort("The {} toolchain (defined by {}) does not support tool '{}'".format(self.name, self.dist, tool))

    def get_toolchain_tool(self, tool):
        return os.path.join(mx.distribution(self.bootstrap_dist).get_output(), 'bin', self._tool_to_exe(tool))

    def get_toolchain_subdir(self):
        return self.name

    def get_launcher_configs(self):
        return [
            mx_sdk.LauncherConfig(
                destination=os.path.join(self.name, 'bin', self._tool_to_exe(tool)),
                jar_distributions=[self.suite.name + ":" + self.dist],
                main_class=self._tool_to_main(tool),
                build_args=[
                    '--macro:truffle',  # we need tool:truffle so that Engine.findHome works
                    '-H:-ParseRuntimeOptions',  # we do not want `-D` options parsed by SVM
                ],
                is_main_launcher=False,
                default_symlinks=False,
                links=[os.path.join(self.name, 'bin', e) for e in self._tool_to_aliases(tool)],
            ) for tool in self._supported_tools()
        ]


class ToolchainLauncherProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, buildRef=True, **attrs):
        results = ["bin/" + e for e in suite.toolchain._supported_exes()]
        projectDir = attrs.pop('dir', None)
        if projectDir:
            d = join(suite.dir, projectDir)
        elif subDir is None:
            d = join(suite.dir, name)
        else:
            d = join(suite.dir, subDir, name)
        super(ToolchainLauncherProject, self).__init__(suite, name, subDir, [], deps, workingSets, results, output, d, **attrs)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(ToolchainLauncherProject, self).getBuildEnv(replaceVar=replaceVar)
        env['RESULTS'] = ' '.join(self.results)
        return env


_suite.toolchain = ToolchainConfig('native', 'SULONG_TOOLCHAIN_LAUNCHERS', 'SULONG_BOOTSTRAP_TOOLCHAIN',
                                   # unfortunately, we cannot define those in the suite.py because graalvm component
                                   # registration runs before the suite is properly initialized
                                   tools={
                                       "CC": "com.oracle.truffle.llvm.toolchain.launchers.Clang",
                                       "CXX": "com.oracle.truffle.llvm.toolchain.launchers.ClangXX",
                                   },
                                   suite=_suite)


mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=_suite,
    name='Sulong',
    short_name='slg',
    dir_name='llvm',
    license_files=[],
    third_party_license_files=[],
    truffle_jars=['sulong:SULONG', 'sulong:SULONG_API'],
    support_distributions=[
        'sulong:SULONG_HOME',
        'sulong:SULONG_GRAALVM_DOCS',
    ],
    launcher_configs=[
        mx_sdk.LanguageLauncherConfig(
            destination='bin/<exe:lli>',
            jar_distributions=['sulong:SULONG_LAUNCHER'],
            main_class='com.oracle.truffle.llvm.launcher.LLVMLauncher',
            build_args=[],
            language='llvm',
        ),
    ] + _suite.toolchain.get_launcher_configs()
))

mx_sdk.register_graalvm_component(mx_sdk.GraalVmComponent(
    suite=_suite,
    name='LLVM.org toolchain',
    short_name='llp',
    installable=True,
    installable_id='llvm-toolchain',
    dir_name='jre/lib/llvm',
    license_files=[],
    third_party_license_files=['3rd_party_license_llvm-toolchain.txt'],
    support_distributions=['sulong:SULONG_LLVM_ORG']
))


COPYRIGHT_HEADER_BSD = """\
/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

def create_asm_parser(args=None, out=None):
    """create the inline assembly parser using antlr"""
    mx.suite("truffle").extensions.create_parser("com.oracle.truffle.llvm.asm.amd64", "com.oracle.truffle.llvm.asm.amd64", "InlineAssembly", COPYRIGHT_HEADER_BSD, args, out)


mx.update_commands(_suite, {
    'lli' : [runLLVM, ''],
    'test-llvm-image' : [_test_llvm_image, 'test a pre-built LLVM image'],
    'create-asm-parser' : [create_asm_parser, 'create the inline assembly parser using antlr'],
    'extract-bitcode' : [extract_bitcode, 'Extract embedded LLVM bitcode from object files'],
})
