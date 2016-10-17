import tarfile
import os
from os.path import join, isfile
import shutil
import subprocess
import sys

import mx
import mx_findbugs
import re
import argparse

from mx_unittest import unittest, add_config_participant
from mx_gate import Task, add_gate_runner
from mx_gitlogcheck import logCheck

os.environ["LC_NUMERIC"] = "C"  # required for some testcases

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong/")
_libPath = join(_mx, 'libs')
_root = join(_suite.dir, "projects/")
_parserDir = join(_root, "com.intel.llvm.ireditor")
_testDir = join(_root, "com.oracle.truffle.llvm.test/")
_argon2Dir = join(_testDir, "argon2/phc-winner-argon2/")
_lifetimeReferenceDir = join(_testDir, "lifetime/")
_toolDir = join(_root, "com.oracle.truffle.llvm.tools/")
_clangPath = _toolDir + 'tools/llvm/bin/clang'

_testDir = join(_root, "com.oracle.truffle.llvm.test/tests/")
_interopTestDir = join(_root, "com.oracle.truffle.llvm.test/interoptests/")

_gccSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/gcc/")
_gccSuiteDirRoot = join(_gccSuiteDir, 'gcc-5.2.0/gcc/testsuite/')

_llvmSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/llvm/")
_llvmSuiteDirRoot = join(_llvmSuiteDir, 'test-suite-3.2.src')

_nwccSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/nwcc/")

_benchGameSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/benchmarkgame/")

_dragonEggPath = _toolDir + 'tools/dragonegg/dragonegg-3.2.src/dragonegg.so'

_inlineAssemblySrcDir = join(_root, "com.oracle.truffle.llvm.asm.amd64/src/")
_inlineAssemblyGrammer = join(_inlineAssemblySrcDir, "InlineAssembly.atg")
_inlineAssemblyPackageName = "com.oracle.truffle.llvm.asm.amd64"

def _unittest_config_participant(config):
    """modifies the classpath to use the Sulong distribution jars instead of the classfiles to enable the use of Java's ServiceLoader"""
    (vmArgs, mainClass, mainClassArgs) = config
    cpIndex, _ = mx.find_classpath_arg(vmArgs)
    junitCp = mx.classpath("com.oracle.mxtool.junit")
    sulongCp = ':'.join([mx.classpath(mx.distribution(distr), jdk=mx.get_jdk(tag='jvmci')) for distr in sulongDistributions])
    vmArgs[cpIndex] = junitCp + ":" + sulongCp
    return (vmArgs, mainClass, mainClassArgs)

add_config_participant(_unittest_config_participant)

sulongDistributions = [
    'SULONG',
    'SULONG_TEST'
]

# the supported GCC versions (see dragonegg.llvm.org)
supportedGCCVersions = [
    '4.6',
    '4.5',
    '4.7'
]

# the files that should be checked to not contain http links (but https ones)
httpCheckFiles = [
    __file__,
    _suite.dir + "/.travis.yml"
]

# the file paths that we want to check with clang-format
clangFormatCheckPaths = [
    _suite.dir + '/include',
    _testDir,
    _interopTestDir,
    _libPath
]

# the file paths on which we want to use the mdl Markdown file checker
mdlCheckDirectories = [
    _suite.dir
]


# the file paths for which we do not want to apply the mdl Markdown file checker
mdlCheckExcludeDirectories = [
    join(_suite.dir, 'projects/com.oracle.truffle.llvm.test/argon2/phc-winner-argon2'),
    join(_suite.dir, 'mx') # we exclude the mx directory since we download it into the sulong folder in the Travis gate
]

# the LLVM versions supported by the current bitcode parser that bases on the textual format
# sorted by priority in descending order (highest priority on top)
supportedLLVMVersions = [
    '3.2',
]

# the clang-format versions that can be used for formatting the test case C and C++ files
clangFormatVersions = [
    '3.4'
]

# the basic LLVM dependencies for running the test cases and executing the mx commands
basicLLVMDependencies = [
    'clang',
    'clang++',
    'opt',
    'llc',
    'llvm-as'
]

def _graal_llvm_gate_runner(args, tasks):
    """gate function"""
    executeGate()

add_gate_runner(_suite, _graal_llvm_gate_runner)

def executeGate():
    """executes the TruffleLLVM gate tasks"""
    tasks = []
    with Task('Findbugs', tasks) as t:
        if t: findBugs()
    for testSuiteName in testCases.keys():
        command = testCases[testSuiteName]
        with Task(testSuiteName, tasks) as t:
            if t: command()

def travis1(args=None):
    """executes the first Travis job (Javac build, benchmarks, polyglot, interop, tck, asm, types, and LLVM test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestBenchmarks', tasks) as t:
        if t: runBenchmarkTestCases()
    with Task('TestPolglot', tasks) as t:
        if t: runPolyglotTestCases()
    with Task('TestInterop', tasks) as t:
        if t: runInteropTestCases()
    with Task('TestTck', tasks) as t:
        if t: runTckTestCases()
    with Task('TestAsm', tasks) as t:
        if t: runAsmTestCases()
    with Task('TestTypes', tasks) as t:
        if t: runTypeTestCases()
    with Task('TestLLVM', tasks) as t:
        if t: runLLVMTestCases()
    with Task('TestMainArgs', tasks) as t:
        if t: runMainArgTestCases()

def travis2(args=None):
    """executes the second Travis job (Javac build, GCC execution test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestGCC', tasks) as t:
        if t: runGCCTestCases()

def travis3(args=None):
    """executes the third Travis job (Javac build, NWCC, GCC compilation test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestNWCC', tasks) as t:
        if t: runNWCCTestCases()
    with Task('TestGCCSuiteCompile', tasks) as t:
        if t: runCompileTestCases()
    with Task('TestLifetime', tasks) as t:
        if t: runLifetimeTestCases()

def travis4(args=None):
    """executes the fourth Travis job (Javac build, LLVM and GCC test cases with BitCode parser)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestLLVMBC', tasks) as t:
        if t: runLLVMTestCases(['-Dsulong.TestBinaryParser=true'])
    with Task('TestGCCBC', tasks) as t:
        if t: runGCCTestCases(['-Dsulong.TestBinaryParser=true'])

def travisTestSulong(args=None):
    """executes the Sulong test cases (which also stress compilation)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestSulong', tasks) as t:
        if t: runTruffleTestCases()

def travisJRuby(args=None):
    """executes the JRuby Travis job (Javac build, JRuby test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestJRuby', tasks) as t:
        if t: runTestJRuby()

def travisArgon2(args=None):
    """executes the argon2 Travis job (Javac build, argon2 test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('TestArgon2', tasks) as t:
        if t: runTestArgon2(optimize=False)

def localGate(args=None):
    """executes the gate without downloading the dependencies and without building"""
    executeGate()


def downloadDependencies(args=None):
    """downloads the external dependencies (GCC, LLVM, benchmarks, and test suites)"""
    pullTestFramework()
    pullBenchmarkGame()
    pullTools()

def pullTools(args=None):
    """pulls the LLVM tools (LLVM binaries)"""
    pullLLVMBinaries()

def pullTestFramework(args=None):
    """downloads the test suites (GCC, LLVM, NWCC, Argon2)"""
    pullGCCSuite()
    pullLLVMSuite()
    pullNWCCSuite()
    pullArgon2()

# platform independent
def pullBenchmarkGame(args=None):
    """downloads the benchmarks"""
    mx.ensure_dir_exists(_benchGameSuiteDir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/benchmarksgame-scm-latest.tar.gz"]
    localPath = pullsuite(_benchGameSuiteDir, urls)
    tar(localPath, _benchGameSuiteDir, ['benchmarksgame-2014-08-31/benchmarksgame/bench/'], stripLevels=3)
    os.remove(localPath)
    renameBenchmarkFiles()

def renameBenchmarkFiles(args=None):
    for path, _, files in os.walk(_benchGameSuiteDir):
        for f in files:
            absPath = path + '/' + f
            _, ext = os.path.splitext(absPath)
            if ext in ['.gcc', '.cint']:
                os.rename(absPath, absPath + '.c')
            if ext in ['.gpp']:
                os.rename(absPath, absPath + '.cpp')

# platform dependent
def pullLLVMBinaries(args=None):
    """downloads the LLVM binaries"""
    toolDir = join(_toolDir, "tools/llvm")
    mx.ensure_dir_exists(toolDir)
    osStr = mx.get_os()
    arch = mx.get_arch()
    if osStr == 'windows':
        print 'windows currently only supported with cygwin!'
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
        print osStr, arch, "not supported!"
    localPath = pullsuite(toolDir, urls)
    tar(localPath, toolDir, stripLevels=1)
    os.remove(localPath)

def dragonEgg(args=None):
    """executes GCC with dragonegg"""
    executeCommand = [getGCC(), "-fplugin=" + _dragonEggPath, '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def dragonEggGFortran(args=None):
    """executes GCC Fortran with dragonegg"""
    executeCommand = [getGFortran(), "-fplugin=" + _dragonEggPath, '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def dragonEggGPP(args=None):
    """executes G++ with dragonegg"""
    executeCommand = [getGPP(), "-fplugin=" + _dragonEggPath, '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def which(program):
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, _ = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
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

def getGCC():
    """tries to locate a gcc version suitable to execute Dragonegg"""
    specifiedGCC = getCommand('SULONG_GCC')
    if specifiedGCC is not None:
        return specifiedGCC
    return findGCCProgram('gcc')

def getGFortran():
    """tries to locate a gfortran version suitable to execute Dragonegg"""
    specifiedGFortran = getCommand('SULONG_GFORTRAN')
    if specifiedGFortran is not None:
        return specifiedGFortran
    return findGCCProgram('gfortran')

def getGPP():
    """tries to locate a g++ version suitable to execute Dragonegg"""
    specifiedCPP = getCommand('SULONG_GPP')
    if specifiedCPP is not None:
        return specifiedCPP
    return findGCCProgram('g++')

# platform independent
def pullInstallDragonEgg(args=None):
    """downloads and installs dragonegg (assumes that compatible GCC and G++ versions are installed)"""
    toolDir = join(_toolDir, "tools/dragonegg")
    mx.ensure_dir_exists(toolDir)
    url = 'https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/dragonegg-3.2.src.tar.gz'
    localPath = pullsuite(toolDir, [url])
    tar(localPath, toolDir)
    os.remove(localPath)
    if mx.get_os() == 'darwin':
        gccToolDir = join(_toolDir, "tools/gcc")
        url = 'https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-4.6.4.tar.gz'
        localPath = pullsuite(gccToolDir, [url])
        tar(localPath, gccToolDir)
        os.remove(localPath)
        mx.run(['patch', '-p1', _toolDir + 'tools/dragonegg/dragonegg-3.2.src/Makefile', 'mx.sulong/dragonegg-mac.patch'])
    os.environ['GCC'] = getGCC()
    os.environ['CXX'] = getGPP()
    os.environ['CC'] = getGCC()
    pullLLVMBinaries()
    os.environ['LLVM_CONFIG'] = findLLVMProgram('llvm-config')
    print os.environ['LLVM_CONFIG']
    compileCommand = ['make']
    return mx.run(compileCommand, cwd=_toolDir + 'tools/dragonegg/dragonegg-3.2.src')

# platform independent
def pullLLVMSuite(args=None):
    """downloads the official (non Truffle) LLVM test suite"""
    mx.ensure_dir_exists(_llvmSuiteDir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/test-suite-3.2.src.tar.gz"]
    localPath = pullsuite(_llvmSuiteDir, urls)
    tar(localPath, _llvmSuiteDir)
    os.remove(localPath)

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

# platform independent
def pullGCCSuite(args=None):
    """downloads the GCC test suite"""
    suiteDir = _gccSuiteDir
    mx.ensure_dir_exists(suiteDir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-5.2.0.tar.gz"]
    localPath = pullsuite(suiteDir, urls)
    tar(localPath, suiteDir, ['gcc-5.2.0/gcc/testsuite/'])
    os.remove(localPath)

def pullNWCCSuite(args=None):
    """downloads the NWCC test suite"""
    mx.ensure_dir_exists(_nwccSuiteDir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/nwcc_0.8.3.tar.gz"]
    localPath = pullsuite(_nwccSuiteDir, urls)
    tar(localPath, _nwccSuiteDir, ['nwcc_0.8.3/tests/', 'nwcc_0.8.3/test2/'], stripLevels=1)
    os.remove(localPath)

def pullArgon2(args=None):
    """downloads Argon2"""
    mx.ensure_dir_exists(_argon2Dir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/20160406.tar.gz"]
    localPath = pullsuite(_argon2Dir, urls)
    tar(localPath, _argon2Dir, ['phc-winner-argon2-20160406/'], stripLevels=1)
    os.remove(localPath)

def pullLifetime(args=None):
    """downloads the lifetime reference outputs"""
    mx.ensure_dir_exists(_lifetimeReferenceDir)
    urls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/lifetime-analysis-ref.tar.gz"]
    localPath = pullsuite(_lifetimeReferenceDir, urls)
    tar(localPath, _lifetimeReferenceDir)
    os.remove(localPath)

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

def runLLVM(args=None, out=None):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgsWithLibs = truffle_extract_VM_args(args)
    sulongArgs = []
    libNames = []
    for arg in sulongArgsWithLibs:
        if arg.startswith('-l'):
            libNames.append(arg)
        else:
            sulongArgs.append(arg)
    return mx.run_java(getCommonOptions(libNames) + vmArgs + getClasspathOptions() + ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.LLVM"] + sulongArgs, out=out, jdk=mx.get_jdk(tag='jvmci'))

def runTests(args=None):
    """runs all the test cases or selected ones (see -h or --help)"""
    vmArgs, otherArgs = truffle_extract_VM_args(args)
    parser = argparse.ArgumentParser(description="Executes all or selected test cases of Sulong's test suites.")
    parser.add_argument('suite', nargs='*', help=' '.join(testCases.keys()), default=testCases.keys())
    parser.add_argument('--verbose', dest='verbose', action='store_const', const=True, default=False, help='Display the test suite names before execution')
    parsedArgs = parser.parse_args(otherArgs)
    for testSuiteName in parsedArgs.suite:
        if parsedArgs.verbose:
            print 'executing', testSuiteName, 'test suite'
        command = testCases[testSuiteName]
        command(vmArgs)

def runChecks(args=None):
    """runs all the static analysis tools or selected ones (see -h or --help)"""
    vmArgs, otherArgs = truffle_extract_VM_args(args)
    parser = argparse.ArgumentParser(description="Executes all or selected static analysis tools")
    parser.add_argument('check', nargs='*', help=' '.join(checkCases.keys()), default=checkCases.keys())
    parser.add_argument('--verbose', dest='verbose', action='store_const', const=True, default=False, help='Display the check names before execution')
    parsedArgs = parser.parse_args(otherArgs)
    error = False
    for checkName in parsedArgs.check:
        if parsedArgs.verbose:
            print 'executing', checkName
        command = checkCases[checkName]
        optionalRetValue = command(vmArgs)
        if optionalRetValue:
            error = True
    if error:
        exit(-1)

def findBugs(args=None):
    tasks = []
    with Task('Clean', tasks) as t:
        if t: mx.clean([]) # we need a clean build before running findbugs
    with Task('Build', tasks) as t:
        if t: mx.build([])
    with Task('Findbugs', tasks) as t:
        if t and mx_findbugs.findbugs([]) != 0:
            t.abort('FindBugs warnings were found')

def compileWithEcjStrict(args=None):
    """build project with the option --warning-as-error"""
    if mx.get_env('JDT'):
        mx.clean([])
        mx.command_function('build')(['-p', '--no-native', '--warning-as-error'])
    else:
        exit('JDT environment variable not set. Cannot execute BuildJavaWithEcj task.')

def runBenchmarkTestCases(args=None):
    """runs the test cases from the language benchmark game"""
    ensureLLVMBinariesExist()
    ensureGCCSuiteExists()
    ensureDragonEggExists()
    ensureBenchmarkSuiteExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.ShootoutsTestSuite"])

def runGCCTestCases(args=None):
    """runs the GCC test suite"""
    ensureLLVMBinariesExist()
    ensureGCCSuiteExists()
    ensureDragonEggExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestGCCSuite'])

def runNWCCTestCases(args=None):
    """runs the NWCC (Nils Weller's C Compiler) test cases"""
    ensureLLVMBinariesExist()
    ensureNWCCSuiteExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + [getRemoteClasspathOption(), "com.oracle.truffle.llvm.test.NWCCTestSuite"])

def runLLVMTestCases(args=None):
    """runs the LLVM test suite"""
    ensureLLVMBinariesExist()
    ensureLLVMSuiteExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + [getRemoteClasspathOption(), "com.oracle.truffle.llvm.test.LLVMTestSuite"])

def runTruffleTestCases(args=None):
    """runs the Sulong test suite"""
    ensureLLVMBinariesExist()
    ensureDragonEggExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + ['-Dsulong.ExecutionCount=1000'] + vmArgs + ["com.oracle.truffle.llvm.test.SulongTestSuite"])

def runTypeTestCases(args=None):
    """runs the type test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.types.floating.test'])

def runLifetimeTestCases(args=None):
    """runs the lifetime analysis test cases"""
    ensureLifetimeReferenceExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestLifetimeAnalysisGCC'])

def runPolyglotTestCases(args=None):
    """runs the type test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestPolyglotEngine'])

def runInteropTestCases(args=None):
    """runs the interop test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.interop.LLVMInteropTest'])

def runTckTestCases(args=None):
    """runs the TCK test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.interop.LLVMTckTest'])

def runAsmTestCases(args=None):
    """runs the asm test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + [getRemoteClasspathOption(), 'com.oracle.truffle.llvm.test.inlineassembly.LLVMInlineAssemblyTest'])

def runMainArgTestCases(args=None):
    """runs the test cases that exercise the passing of arguments to the main function"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.LLVMMainArgTestSuite'])

def runCompileTestCases(args=None):
    """runs the compile (no execution) test cases of the GCC suite"""
    ensureGCCSuiteExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestGCCCompileSuite'])

def runTestJRuby(args=None):
    """tests that JRuby can use this version of Sulong to compile and run C extensions"""
    rubyUrl = 'https://github.com/jruby/jruby.git'
    rubyBranch = 'truffle-head'
    suitesDir = os.path.abspath(join(_suite.dir, '..'))
    jrubyDir = join(suitesDir, 'jruby')
    if os.path.isdir(jrubyDir):
        mx.run(['git', 'checkout', rubyBranch], cwd=jrubyDir)
        mx.run(['git', 'pull'], cwd=jrubyDir)
    else:
        mx.run(['git', 'clone', rubyUrl], cwd=suitesDir)
        mx.run(['git', 'checkout', rubyBranch], cwd=jrubyDir)
    rubyGemsUrl = 'https://github.com/jruby/jruby-truffle-gem-test-pack.git'
    jrubyGemsDir = join(suitesDir, 'jruby-truffle-gem-test-pack')
    if os.path.isdir(jrubyGemsDir):
        mx.run(['git', 'pull'], cwd=jrubyGemsDir)
    else:
        mx.run(['git', 'clone', rubyGemsUrl], cwd=suitesDir)
    os.environ['GRAAL_HOME'] = _suite.dir
    os.environ['SULONG_HOME'] = _suite.dir
    os.environ['GEM_HOME'] = jrubyGemsDir + '/gems'
    mx.run(['ruby', 'tool/jt.rb', 'build'], cwd=jrubyDir)
    mx.run(['ruby', 'tool/jt.rb', 'build', 'cexts', '--no-openssl'], cwd=jrubyDir)
    mx.run(['ruby', 'tool/jt.rb', 'test', 'specs', '--graal', ':capi'], cwd=jrubyDir)
    mx.run(['ruby', 'tool/jt.rb', 'test', 'cexts'], cwd=jrubyDir)

def compileArgon2(main, optimize, cflags=None):
    if cflags is None:
        cflags = []
    argon2Src = ['src/argon2', 'src/core', 'src/blake2/blake2b', 'src/thread', 'src/encoding', 'src/ref', 'src/%s' % main, '../pthread-stub/pthread']
    argon2Bin = '%s.su' % main
    for src in argon2Src:
        inputFile = '%s.c' % src
        outputFile = '%s.ll' % src
        compileWithClang(['-S', '-emit-llvm', '-o', outputFile, '-std=c89', '-Wall', '-Wextra', '-Wno-type-limits', '-I../pthread-stub', '-Iinclude', '-Isrc'] + cflags + [inputFile])
        if optimize:
            opt(['-S', '-o', outputFile, outputFile] + getStandardLLVMOptFlags())
    link(['-o', argon2Bin] + ['%s.ll' % x for x in argon2Src])

def runTestArgon2Kats(args=None):
    for v in ['16', '19']:
        for t in ['i', 'd']:
            sys.stdout.write("argon2%s v=%s: " % (t, v))

            if v == '19':
                kats = 'kats/argon2%s' % t
            else:
                kats = 'kats/argon2%s_v%s' % (t, v)

            ret = runLLVM(args + ['genkat.su', t, v], out=open('tmp', 'w'))
            if ret == 0:
                ret = mx.run(['diff', 'tmp', kats])
                if ret == 0:
                    print 'OK'
                else:
                    print 'ERROR'
                    return ret
            else:
                print 'FAILED'
                return ret

    return 0

def runTestArgon2(args=None, optimize=False):
    """runs Argon2 tests with Sulong"""

    ensureArgon2Exists()
    os.chdir(_argon2Dir)
    if args is None:
        args = []
    # TODO: Enable assertions
    #args.extend(['-ea', '-esa'])

    compileArgon2('genkat', optimize, ['-DGENKAT'])
    ret = runTestArgon2Kats(args)
    if ret != 0:
        return ret

    compileArgon2('test', optimize)
    return runLLVM(args + ['test.su'])

def getCommonOptions(lib_args=None):
    return [
        '-Dgraal.TruffleCompilationExceptionsArePrinted=true',
        '-Dgraal.ExitVMOnException=true',
        getSearchPathOption(lib_args)
    ] + getBitcodeLibrariesOption()

def getSearchPathOption(lib_args=None):
    if lib_args is None:
        lib_args = ['-lgmp', '-lgfortran', '-lpcre']

    lib_names = []

    lib_aliases = {
        '-lc': ['libc.so.6', 'libc.dylib'],
        '-lstdc++': ['libstdc++.so.6', 'libstdc++.6.dylib'],
        '-lgmp': ['libgmp.so.10', 'libgmp.10.dylib'],
        '-lgfortran': ['libgfortran.so.3', 'libgfortran.3.dylib'],
        '-lpcre': ['libpcre.so.3', 'libpcre.dylib']
    }
    osStr = mx.get_os()
    index = {'linux': 0, 'darwin': 1}[mx.get_os()]
    if index is None:
        print osStr, "not supported!"

    for lib_arg in ['-lc', '-lstdc++'] + lib_args:
        if lib_arg in lib_aliases:
            lib_arg = lib_aliases[lib_arg][index]
        else:
            lib_arg = lib_arg[2:]
        lib_names.append(lib_arg)

    return '-Dsulong.DynamicNativeLibraryPath=' + ':'.join(lib_names)

def getCommonUnitTestOptions():
    return getCommonOptions() + ['-Xss56m', '-Xms2g', '-Xmx2g', getLLVMRootOption(), '-ea', '-esa']

# PE does not work yet for all test cases
def compilationSucceedsOption():
    return "-Dgraal.TruffleCompilationExceptionsAreFatal=true"

def getRemoteClasspathOption():
    return "-Dsulong.TestRemoteBootPath=-Xbootclasspath/p:" + mx.distribution('truffle:TRUFFLE_API').path + " " + getLLVMRootOption() + " " + compilationSucceedsOption() + " -XX:-UseJVMCIClassLoader -Dsulong.Debug=false -Djvmci.Compiler=graal"

def getBenchmarkOptions():
    return [
        '-Dgraal.TruffleBackgroundCompilation=false',
        '-Dsulong.IntrinsifyCFunctions=true',
        '-Dsulong.ExecutionCount=5',
        '-Dsulong.PerformanceWarningsAreFatal=true',
        '-Dgraal.TruffleTimeThreshold=1000000',
        '-Xms4g',
        '-Xmx4g'
    ]

def getLLVMRootOption():
    return "-Dsulong.ProjectRoot=" + _root

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
        versionString = subprocess.check_output([program, '--version'])
    except subprocess.CalledProcessError as e:
        # on my machine, e.g., opt returns a non-zero opcode even on success
        versionString = e.output
    return versionString

def getLLVMVersion(llvmProgram):
    """executes the program with --version and extracts the LLVM version string"""
    versionString = getVersion(llvmProgram)
    printLLVMVersion = re.search(r'(clang |LLVM )?(version )?(3\.\d)', versionString, re.IGNORECASE)
    if printLLVMVersion is None:
        return None
    else:
        return printLLVMVersion.group(3)

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
    return findInstalledProgram(gccProgram, supportedGCCVersions, isSupportedGCCVersion)

def findInstalledProgram(program, supportedVersions, testSupportedVersion):
    """tries to find a supported version of a program

    The function takes program argument, and checks if it has the supported version.
    If not, it prepends a supported version to the version string to check if it is an executable program with a supported version.
    The function checks both for programs by appending "-" and the unmodified version string, as well as by directly adding all the digits of the version string (stripping all other characters).

    For example, for a program gcc with supportedVersions 4.6 the function produces gcc-4.6 and gcc46.

    Arguments:
    program -- the program to find, e.g., clang or gcc
    supportedVersions -- the supported versions, e.g., 3.4 or 4.9
    testSupportedVersion(path, supportedVersions) -- the test function to be called to ensure that the found program is supported
    """
    assert program is not None
    programPath = which(program)
    if programPath is not None and testSupportedVersion(programPath, supportedVersions):
        return programPath
    else:
        for version in supportedVersions:
            alternativeProgram1 = program + '-' + version
            alternativeProgram2 = program + re.sub(r"\D", "", version)
            alternativePrograms = [alternativeProgram1, alternativeProgram2]
            for alternativeProgram in alternativePrograms:
                alternativeProgramPath = which(alternativeProgram)
                if alternativeProgramPath is not None:
                    assert testSupportedVersion(alternativeProgramPath, supportedVersions)
                    return alternativeProgramPath
    return None

def findLLVMProgram(llvmProgram):
    """tries to find a supported version of an installed LLVM program; if the program is not found it downloads the LLVM binaries and checks there"""
    installedProgram = findInstalledLLVMProgram(llvmProgram)
    if installedProgram is None:
        if not os.path.exists(_clangPath):
            pullLLVMBinaries()
        programPath = _toolDir + 'tools/llvm/bin/' + llvmProgram
        if not os.path.exists(programPath):
            exit(llvmProgram + ' is not a supported LLVM program!')
        return programPath
    else:
        return installedProgram

def findGCCProgram(gccProgram):
    """tries to find a supported version of an installed GCC program"""
    installedProgram = findInstalledGCCProgram(gccProgram)
    if installedProgram is None:
        exit('found no supported version ' + str(supportedGCCVersions) + ' of ' + gccProgram)
    else:
        return installedProgram

def getGCCProgramPath(args=None):
    """gets a path with a supported version of the specified GCC program (e.g. gfortran)"""
    if args is None or len(args) != 1:
        exit("please supply one GCC program to be located!")
    else:
        print findGCCProgram(args[0])

def getLLVMProgramPath(args=None):
    """gets a path with a supported version of the specified LLVM program (e.g. clang)"""
    if args is None or len(args) != 1:
        exit("please supply one LLVM program to be located!")
    else:
        print findLLVMProgram(args[0])

def compileWithClang(args=None):
    """runs Clang"""
    return mx.run([findLLVMProgram('clang')] + args)

def compileWithGCC(args=None):
    """runs GCC"""
    ensureLLVMBinariesExist()
    gccPath = _toolDir + 'tools/llvm/bin/gcc'
    return mx.run([gccPath] + args)

def opt(args=None):
    """runs opt"""
    return mx.run([findLLVMProgram('opt')] + args)

def link(args=None):
    """Links LLVM bitcode into an su file."""
    return mx.run_java(getClasspathOptions() + ["com.oracle.truffle.llvm.tools.Linker"] + args)

def compileWithClangPP(args=None):
    """runs Clang++"""
    return mx.run([findLLVMProgram('clang++')] + args)

def getClasspathOptions():
    """gets the classpath of the Sulong distributions"""
    return ['-cp', ':'.join([mx.classpath(mx.distribution(distr), jdk=mx.get_jdk(tag='jvmci')) for distr in sulongDistributions])]

def printOptions(args=None):
    """prints the Sulong Java property options"""
    return mx.run_java(getClasspathOptions() + ["com.oracle.truffle.llvm.runtime.options.LLVMOptions"])

def ensureGCCSuiteExists():
    """downloads the GCC suite if not downloaded yet"""
    if not os.path.exists(_gccSuiteDirRoot):
        pullGCCSuite()

def ensureDragonEggExists():
    """downloads dragonegg if not downloaded yet"""
    if not os.path.exists(_dragonEggPath):
        pullInstallDragonEgg()

def ensureLLVMSuiteExists():
    """downloads the LLVM suite if not downloaded yet"""
    if not os.path.exists(_llvmSuiteDirRoot):
        pullLLVMSuite()

def ensureNWCCSuiteExists():
    """downloads the NWCC suite if not downloaded yet"""
    if not os.path.exists(_nwccSuiteDir + 'tests'):
        pullNWCCSuite()

def ensureBenchmarkSuiteExists():
    """downloads the language benchmark game if not downloaded yet"""
    if not os.path.exists(_benchGameSuiteDir):
        pullBenchmarkGame()

def ensureLLVMBinariesExist():
    """downloads the LLVM binaries if they have not been downloaded yet"""
    for llvmBinary in basicLLVMDependencies:
        if findLLVMProgram(llvmBinary) is None:
            raise Exception(llvmBinary + ' not found')

def ensureArgon2Exists():
    """downloads Argon2 if not downloaded yet"""
    if not os.path.exists(_argon2Dir):
        pullArgon2()

def ensureLifetimeReferenceExists():
    """downloads the lifetime analysis reference outputs if not downloaded yet"""
    if not os.path.exists(_lifetimeReferenceDir):
        pullLifetime()

def suBench(args=None):
    """runs a given benchmark with Sulong"""
    ensureLLVMBinariesExist()
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    compileWithClang(['-S', '-emit-llvm', '-o', 'test.ll', sulongArgs[0]])
    return runLLVM(getBenchmarkOptions() + ['test.ll'] + vmArgs)

def getStandardLLVMOptFlags():
    """gets the optimal LLVM opt flags for Sulong"""
    return ['-mem2reg', '-globalopt', '-simplifycfg', '-constprop', '-instcombine', '-dse', '-loop-simplify', '-reassociate', '-licm', '-gvn']

def getRemoveExceptionHandlingLLVMOptFlags():
    """gets the LLVM opt flags that remove C++ exception handling if possible"""
    return ['-mem2reg', '-lowerinvoke', '-prune-eh', '-simplifycfg']

def getOptimalLLVMOptFlags(sourceFile):
    """gets the optimal LLVM opt flags for Sulong based on an original file source name (such as test.c)"""
    _, ext = os.path.splitext(sourceFile)
    if ext == '.c':
        return getStandardLLVMOptFlags()
    elif ext == '.cpp':
        return getStandardLLVMOptFlags() + getRemoveExceptionHandlingLLVMOptFlags()
    else:
        exit(ext + " is not supported!")

def suOptimalOpt(args=None):
    """use opt with the optimal opt flags for Sulong"""
    opt(getStandardLLVMOptFlags() + args)

def compileWithClangOpt(inputFile, outputFile='test.ll'):
    """compiles a program to LLVM IR with Clang using LLVM optimizations that benefit Sulong"""
    _, ext = os.path.splitext(inputFile)
    if ext == '.c':
        compileWithClang(['-S', '-emit-llvm', '-o', outputFile, inputFile])
    elif ext == '.cpp':
        compileWithClangPP(['-S', '-emit-llvm', '-o', outputFile, inputFile])
    else:
        exit(ext + " is not supported!")
    opt(['-S', '-o', outputFile, outputFile] + getStandardLLVMOptFlags())

def suOptBench(args=None):
    """runs a given benchmark with Sulong after optimizing it with opt"""
    ensureLLVMBinariesExist()
    vmArgs, other = truffle_extract_VM_args(args)
    inputFile = other[0]
    outputFile = 'test.ll'
    compileWithClangOpt(inputFile, outputFile)
    return runLLVM(getBenchmarkOptions() + [getSearchPathOption(), outputFile] + vmArgs)

def suOptCompile(args=None):
    """compiles a given benchmark and optimizes it with opt"""
    ensureLLVMBinariesExist()
    inputFile = args[0]
    outputFile = 'test.ll'
    compileWithClangOpt(inputFile, outputFile)

def clangBench(args=None):
    """ Executes a benchmark with the system default Clang"""
    _, inputFiles = extract_compiler_args(args)
    _, ext = os.path.splitext(inputFiles[0])
    if ext == '.c':
        mx.run(['clang'] + args + standardLinkerCommands())
    elif ext == '.cpp':
        mx.run(['clang++'] + args + standardLinkerCommands())
    else:
        exit(ext + " is not supported!")
    return mx.run(['./a.out'])

def gccBench(args=None):
    """ executes a benchmark with the system default GCC version"""
    _, inputFiles = extract_compiler_args(args)
    _, ext = os.path.splitext(inputFiles[0])
    if ext == '.c':
        mx.run(['gcc', '-std=gnu99'] + args + standardLinkerCommands())
    elif ext == '.cpp':
        mx.run(['g++'] + args + standardLinkerCommands())
    else:
        exit(ext + " is not supported!")
    return mx.run(['./a.out'])

def standardLinkerCommands(args=None):
    return ['-lm', '-lgmp', '-lpcre']

def mdlCheck(args=None):
    """runs mdl on all .md files in the projects and root directory"""
    error = False
    for mdlCheckPath in mdlCheckDirectories:
        for path, _, files in os.walk(mdlCheckPath):
            for f in files:
                if f.endswith('.md') and not any(path.startswith(exclude) for exclude in mdlCheckExcludeDirectories):
                    absPath = path + '/' + f
                    mdlCheckCommand = 'mdl -r~MD026,~MD002,~MD029,~MD032,~MD033 ' + absPath
                    try:
                        subprocess.check_output(mdlCheckCommand, stderr=subprocess.STDOUT, shell=True)
                    except subprocess.CalledProcessError as e:
                        print e # prints command and return value
                        print e.output # prints process output
                        error = True
    if error:
        exit(-1)

def getBitcodeLibrariesOption():
    libraries = []
    if 'SULONG_NO_LIBRARY' not in os.environ:
        for path, _, files in os.walk(_libPath):
            for f in files:
                # TODO: also allow other extensions, best introduce a command "compile" that compiles C, C++, Fortran and other files
                if f.endswith('.c'):
                    bitcodeFile = f.rsplit(".", 1)[0] + '.ll'
                    absBitcodeFile = path + '/' + bitcodeFile
                    if not os.path.isfile(absBitcodeFile):
                        compileWithClangOpt(path + '/' + f, absBitcodeFile)
                    libraries.append(absBitcodeFile)
    return ['-Dsulong.DynamicBitcodeLibraries=' + ':'.join(libraries)] if libraries else []

def clangformatcheck(args=None):
    """ Performs a format check on the include/truffle.h file """
    for f in clangFormatCheckPaths:
        checkCFiles(f)

def checkCFiles(targetDir):
    error = False
    for path, _, files in os.walk(targetDir):
        for f in files:
            if f.endswith('.c') or f.endswith('.cpp') or f.endswith('.h'):
                if not checkCFile(path + '/' + f):
                    error = True
    if error:
        print "found formatting errors!"
        exit(-1)

def checkCFile(targetFile):
    """ Checks the formatting of a C file and returns True if the formatting is okay """
    clangFormat = findInstalledLLVMProgram('clang-format', clangFormatVersions)
    if clangFormat is None:
        exit("Unable to find 'clang-format' executable with one the supported versions '" + ", ".join(clangFormatVersions) + "'")
    formatCommand = [clangFormat, '-style={BasedOnStyle: llvm, ColumnLimit: 150}', targetFile]
    formattedContent = subprocess.check_output(formatCommand).splitlines()
    with open(targetFile) as f:
        originalContent = f.read().splitlines()
    if not formattedContent == originalContent:
        # modify the file to the right format
        subprocess.check_output(formatCommand + ['-i'])
        print '\n'.join(formattedContent)
        print '\nmodified formatting in', targetFile, 'to the format above'
        return False
    return True

def checkNoHttp(args=None):
    """checks that https is used instead of http in Travis and the mx script"""
    for f in httpCheckFiles:
        line_number = 0
        for line in open(f):
            if "http" + chr(58) + "//" in line:
                print "http:" + chr(58) + " in line " + str(line_number) + " of " + f + " could be a security issue! please change to https://"
                exit(-1)
            line_number += 1

def dos2unix(args=None):
    """convert file from DOS to UNIX file format. Functionality equivalent to UNIX command 'dos2unix'"""
    for fileName in args:
        if not os.path.isfile(fileName):
            exit("File '" + fileName + "' does not exists for dos2unix conversion.")
        inFile = open(fileName, 'r')
        content = inFile.read()
        inFile.close()
        outFileName = fileName + ".tmp"
        outFile = open(outFileName, 'w')
        for line in content.splitlines():
            outFile.write(line + "\n")
        outFile.close()
        os.rename(outFileName, fileName)

def genInlineAssemblyParser(args=None, out=None):
    """generate inline assembly parser and scanner if corresponding grammer is new"""
    generatedParserDir = _inlineAssemblySrcDir + _inlineAssemblyPackageName.replace('.', '/')
    generatedParserFile = generatedParserDir + "/Parser.java"
    generatedScannerFile = generatedParserDir + "/Scanner.java"
    if not isfile(generatedParserFile) or not isfile(generatedScannerFile) or os.path.getmtime(_inlineAssemblyGrammer) >= os.path.getmtime(generatedParserFile):
        localCocoJarFile = _suite.dir + "/lib/Coco.jar"
        if not isfile(localCocoJarFile):
            jarFileUrls = ["https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/Coco.jar"]
            mx.download(localCocoJarFile, jarFileUrls)
        command = [mx.get_jdk(tag='jvmci').java, "-jar", localCocoJarFile, "-package", _inlineAssemblyPackageName, "-o", generatedParserDir, _inlineAssemblyGrammer]
        mx.run(command)
        #Files get generated in Windows file format. Convert them to avoid style check failure during regression testing
        dos2unix([generatedParserFile, generatedScannerFile])

def sulongBuild(args=None):
    """custom build command to wrap inline assembly parser generation"""
    genInlineAssemblyParser()
    originalBuildCommand(args)

testCases = {
    'bench' : runBenchmarkTestCases,
    'gcc' : runGCCTestCases,
    'llvm' : runLLVMTestCases,
    'sulong' : runTruffleTestCases,
    'nwcc' : runNWCCTestCases,
    'types' : runTypeTestCases,
    'polyglot' : runPolyglotTestCases,
    'interop' : runInteropTestCases,
    'tck' : runTckTestCases,
    'asm' : runAsmTestCases,
    'compile' : runCompileTestCases,
    'jruby' : runTestJRuby,
    'argon2' : runTestArgon2,
    'lifetime' : runLifetimeTestCases,
    'main-arg' : runMainArgTestCases,
}

checkCases = {
    'gitlog' : logCheck,
    'mdl' : mdlCheck,
    'ecj' : compileWithEcjStrict,
    'checkstyle' : mx.checkstyle,
    'findbugs' : findBugs,
    'canonicalizeprojects' : mx.canonicalizeprojects,
    'httpcheck' : checkNoHttp,
    'checkoverlap' : mx.checkoverlap,
    'clangformatcheck' : clangformatcheck,
    'plyint' : mx.pylint,
    'eclipseformat' : (lambda args: mx.eclipseformat(['--primary'] + args))
}

originalBuildCommand = mx.command_function('build')

mx.update_commands(_suite, {
    'suoptbench' : [suOptBench, ''],
    'subench' : [suBench, ''],
    'clangbench' : [clangBench, ''],
    'gccbench' : [gccBench, ''],
    'build' : [sulongBuild, ''],
    'su-options' : [printOptions, ''],
    'su-pullbenchmarkgame' : [pullBenchmarkGame, ''],
    'su-pulldeps' : [downloadDependencies, ''],
    'su-pullllvmbinaries' : [pullLLVMBinaries, ''],
    'su-pullnwccsuite' : [pullNWCCSuite, ''],
    'su-pullgccsuite' : [pullGCCSuite, ''],
    'su-pullllvmsuite' : [pullLLVMSuite, ''],
    'su-pulltools' : [pullTools, ''],
    'su-pulldragonegg' : [pullInstallDragonEgg, ''],
    'su-run' : [runLLVM, ''],
    'su-tests' : [runTests, ''],
    'su-local-gate' : [localGate, ''],
    'su-clang' : [compileWithClang, ''],
    'su-clang++' : [compileWithClangPP, ''],
    'su-opt' : [opt, ''],
    'su-optimize' : [suOptimalOpt, ''],
    'su-compile-optimize' : [suOptCompile, ''],
    'su-link' : [link, ''],
    'su-gcc' : [dragonEgg, ''],
    'su-gfortran' : [dragonEggGFortran, ''],
    'su-g++' : [dragonEggGPP, ''],
    'su-travis1' : [travis1, ''],
    'su-travis2' : [travis2, ''],
    'su-travis3' : [travis3, ''],
    'su-travis4' : [travis4, ''],
    'su-travis-sulong' : [travisTestSulong, ''],
    'su-travis-jruby' : [travisJRuby, ''],
    'su-travis-argon2' : [travisArgon2, ''],
    'su-ecj-strict' : [compileWithEcjStrict, ''],
    'su-basic-checkcode' : [runChecks, ''],
    'su-gitlogcheck' : [logCheck, ''],
    'su-mdlcheck' : [mdlCheck, ''],
    'su-clangformatcheck' : [clangformatcheck, ''],
    'su-httpcheck' : [checkNoHttp, ''],
    'su-get-llvm-program' : [getLLVMProgramPath, ''],
    'su-get-gcc-program' : [getGCCProgramPath, '']
})
