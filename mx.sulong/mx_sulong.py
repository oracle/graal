import tarfile
import os
from os.path import join
import shutil

import mx
from mx_unittest import unittest
from mx_gate import Task, add_gate_runner
from mx_jvmci import VM, buildvms

_suite = mx.suite('sulong')
_root = join(_suite.dir, "projects/")
_parserDir = join(_root, "com.intel.llvm.ireditor")
_testDir = join(_root, "com.oracle.truffle.llvm.test/")
_toolDir = join(_root, "com.oracle.truffle.llvm.tools/")
_clangPath = _toolDir + 'tools/llvm/bin/clang'

_gccSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/gcc/")
_gccSuiteDirRoot = join(_gccSuiteDir, 'gcc-5.2.0/gcc/testsuite/')

_llvmSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/llvm/")
_llvmSuiteDirRoot = join(_llvmSuiteDir, 'test-suite-3.2.src')

_nwccSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/nwcc/")

_benchGameSuiteDir = join(_root, "com.oracle.truffle.llvm.test/suites/benchmarkgame/")

_dragonEggPath = _toolDir + 'tools/dragonegg/dragonegg-3.2.src/dragonegg.so'

def _graal_llvm_gate_runner(args, tasks):
    """gate function"""
    executeGate()

add_gate_runner(_suite, _graal_llvm_gate_runner)

def executeGate():
    """executes the TruffleLLVM gate tasks"""
    tasks = []
    with Task('BuildHotSpotGraalServer: product', tasks) as t:
        if t: buildvms(['-c', '--vms', 'server', '--builds', 'product'])
    with VM('server', 'product'):
        with Task('TestBenchmarks', tasks) as t:
            if t: runBenchmarkTestCases()
    with VM('server', 'product'):
        with Task('TestTypes', tasks) as t:
            if t: runTypeTestCases()
    with VM('server', 'product'):
        with Task('TestSulong', tasks) as t:
            if t: runTruffleTestCases()
    with VM('server', 'product'):
        with Task('TestGCC', tasks) as t:
            if t: runGCCTestCases()
    with VM('server', 'product'):
        with Task('TestLLVM', tasks) as t:
            if t: runLLVMTestCases()
    with VM('server', 'product'):
        with Task('TestNWCC', tasks) as t:
            if t: runNWCCTestCases()

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
    """downloads the test suites (GCC, LLVM, NWCC)"""
    pullGCCSuite()
    pullLLVMSuite()
    pullNWCCSuite()

# platform independent
def pullBenchmarkGame(args=None):
    """downloads the benchmarks"""
    mx.ensure_dir_exists(_benchGameSuiteDir)
    urls = ["http://lafo.ssw.uni-linz.ac.at/sulong-deps/benchmarksgame-scm-latest.tar.gz",
            "https://alioth.debian.org/snapshots.php?group_id=100815"]
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
            urls = ['http://lafo.ssw.uni-linz.ac.at/sulong-deps/clang+llvm-3.2-x86_64-linux-ubuntu-12.04.tar.gz',
                    'http://llvm.org/releases/3.2/clang+llvm-3.2-x86_64-linux-ubuntu-12.04.tar.gz']
        else:
            urls = ['http://lafo.ssw.uni-linz.ac.at/sulong-deps/clang+llvm-3.2-x86-linux-ubuntu-12.04.tar.gz',
                    'http://llvm.org/releases/3.2/clang+llvm-3.2-x86-linux-ubuntu-12.04.tar.gz']
    elif osStr == 'darwin':
        urls = ['http://lafo.ssw.uni-linz.ac.at/sulong-deps/clang+llvm-3.2-x86_64-apple-darwin11.tar.gz',
                'http://llvm.org/releases/3.2/clang+llvm-3.2-x86_64-apple-darwin11.tar.gz']
    elif osStr == 'cygwin':
        urls = ['http://lafo.ssw.uni-linz.ac.at/sulong-deps/clang+llvm-3.2-x86-mingw32-EXPERIMENTAL.tar.gz',
                'http://llvm.org/releases/3.2/clang+llvm-3.2-x86-mingw32-EXPERIMENTAL.tar.gz']
    else:
        print osStr, arch, "not supported!"
    localPath = pullsuite(toolDir, urls)
    tar(localPath, toolDir, stripLevels=1)
    os.remove(localPath)

def dragonEgg(args=None):
    """executes GCC with dragonegg"""
    executeCommand = ["gcc-4.6", "-fplugin=" + _dragonEggPath, '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def dragonEggGPP(args=None):
    """executes G++ with dragonegg"""
    executeCommand = ["g++-4.6", "-fplugin=" + _dragonEggPath, '-fplugin-arg-dragonegg-emit-ir']
    return mx.run(executeCommand + args)

def hasDragoneggGCCInstalled():
    return os.system('gcc-4.6 --version') == 0

# platform independent
def pullInstallDragonEgg(args=None):
    """downloads and installs dragonegg (assumes that GCC 4.6 is on the path)"""
    if hasDragoneggGCCInstalled():
        toolDir = join(_toolDir, "tools/dragonegg")
        mx.ensure_dir_exists(toolDir)
        url = 'http://llvm.org/releases/3.2/dragonegg-3.2.src.tar.gz'
        localPath = pullsuite(toolDir, [url])
        tar(localPath, toolDir)
        os.remove(localPath)
        os.environ['GCC'] = 'gcc-4.6'
        os.environ['LLVM_CONFIG'] = _toolDir + 'tools/llvm/bin/llvm-config'
        compileCommand = ['make']
        return mx.run(compileCommand, cwd=_toolDir + 'tools/dragonegg/dragonegg-3.2.src')
    else:
        print 'could not find gcc-4.6, skip installing dragonegg!'

# platform independent
def pullLLVMSuite(args=None):
    """downloads the official (non Truffle) LLVM test suite"""
    mx.ensure_dir_exists(_llvmSuiteDir)
    urls = ["http://lafo.ssw.uni-linz.ac.at/sulong-deps/test-suite-3.2.src.tar.gz",
            "http://llvm.org/releases/3.2/test-suite-3.2.src.tar.gz"]
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
    urls = ["http://lafo.ssw.uni-linz.ac.at/sulong-deps/gcc-5.2.0.tar.gz",
            "ftp://gd.tuwien.ac.at/gnu/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
            "ftp://ftp.fu-berlin.de/unix/languages/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz",
            "http://mirrors-usa.go-parts.com/gcc/releases/gcc-5.2.0/gcc-5.2.0.tar.gz"]
    localPath = pullsuite(suiteDir, urls)
    tar(localPath, suiteDir, ['gcc-5.2.0/gcc/testsuite/'])
    os.remove(localPath)

def pullNWCCSuite(args=None):
    """downloads the NWCC test suite"""
    mx.ensure_dir_exists(_nwccSuiteDir)
    urls = ["http://lafo.ssw.uni-linz.ac.at/sulong-deps/nwcc_0.8.3.tar.gz",
            "http://sourceforge.net/projects/nwcc/files/nwcc/nwcc%200.8.3/nwcc_0.8.3.tar.gz/download"]
    localPath = pullsuite(_nwccSuiteDir, urls)
    tar(localPath, _nwccSuiteDir, ['nwcc_0.8.3/tests/', 'nwcc_0.8.3/test2/'], stripLevels=1)
    os.remove(localPath)

def truffle_extract_VM_args(args, useDoubleDash=False):
    vmArgs, remainder = [], []
    if args is not None:
        for (i, arg) in enumerate(args):
            if any(arg.startswith(prefix) for prefix in ['-X', '-G:', '-D', '-verbose', '-ea', '-agentlib']) or arg in ['-esa']:
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


def runDebugLLVM(args=None):
    """uses Sulong to execute a LLVM IR file and starts debugging on port 5005"""
    return runLLVM(['-Xdebug', '-Xrunjdwp:server=y,transport=dt_socket,address=5005,suspend=y'] + args)

def runLLVM(args=None):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    return mx.run_java(getCommonOptions() + vmArgs + ['-XX:-UseJVMCIClassLoader', '-cp', mx.classpath(['com.oracle.truffle.llvm']), "com.oracle.truffle.llvm.LLVM"] + sulongArgs, jdk=mx.get_jdk(tag='jvmci'))

def runTests(args=None):
    """runs all the test cases"""
    runGCCTestCases()
    runNWCCTestCases()
    runLLVMTestCases()
    runTruffleTestCases()
    runTypeTestCases()
    runBenchmarkTestCases()

def runBenchmarkTestCases(args=None):
    """runs the test cases from the language benchmark game"""
    ensureLLVMBinariesExist()
    ensureGCCSuiteExists()
    ensureDragonEggExists()
    ensureBenchmarkSuiteExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.BenchmarkGameSuite"])

def runGCCTestCases(args=None):
    """runs the GCC test suite"""
    ensureLLVMBinariesExist()
    ensureGCCSuiteExists()
    ensureDragonEggExists()
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.TestGCCSuite"])

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
    return unittest(getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.SulongTestSuite"])

def runTypeTestCases(args=None):
    """runs the type test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.types.floating.test'])

def getCommonOptions():
    return ['-Dgraal.TruffleCompilationExceptionsArePrinted=true', '-Dgraal.ExitVMOnException=true']

# other OSs?
def getSearchPathOption():
    return '-Dllvm-dyn-libs=libgfortran.so.3:libstdc++.so.6:libc.so.6'


def getCommonUnitTestOptions():
    return getCommonOptions() + ['-Xss56m', getSearchPathOption(), getLLVMRootOption()]

# PE does not work yet for all test cases
def compilationSucceedsOption():
    return "-Dgraal.TruffleCompilationExceptionsAreFatal=true"

def getRemoteClasspathOption():
    return "-Dllvm-test-boot=-Xbootclasspath/p:" + mx.classpath(['com.oracle.truffle.llvm.nodes']) + " " + getLLVMRootOption() + " " + compilationSucceedsOption() + " " " -Dllvm-debug=false"

def getLLVMRootOption():
    return "-Dllvm-root=" + _root

def pullsuite(suiteDir, urls):
    name = os.path.basename(urls[0])
    localPath = join(suiteDir, name)
    mx.download(localPath, urls)
    return localPath

def compileWithClang(args=None):
    """runs Clang"""
    ensureLLVMBinariesExist()
    clangPath = _toolDir + 'tools/llvm/bin/clang'
    return mx.run([clangPath] + args)

def compileWithGCC(args=None):
    """runs GCC"""
    ensureLLVMBinariesExist()
    clangPath = _toolDir + 'tools/llvm/bin/clang'
    return mx.run([clangPath] + args)


def opt(args=None):
    """"Runs opt."""
    ensureLLVMBinariesExist()
    optPath = _toolDir + 'tools/llvm/bin/opt'
    return mx.run([optPath] + args)

def compileWithClangPP(args=None):
    """runs Clang++"""
    ensureLLVMBinariesExist()
    clangPath = _toolDir + 'tools/llvm/bin/clang++'
    return mx.run([clangPath] + args)

def printOptions(args=None):
    """prints the Sulong Java property options"""
    return mx.run_java(['-cp', mx.classpath(['com.oracle.truffle.llvm.runtime']), "com.oracle.truffle.llvm.runtime.LLVMOptions"])

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
    if not os.path.exists(_clangPath):
        pullLLVMBinaries()

def suBench(args=None):
    """runs a given benchmark with Sulong"""
    ensureLLVMBinariesExist()
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    compileWithClang(['-S', '-emit-llvm', '-o', 'test.ll', sulongArgs[0]])
    return runLLVM(['test.ll'] + vmArgs)

def suOptBench(args=None):
    """runs a given benchmark with Sulong after optimizing it with opt"""
    ensureLLVMBinariesExist()
    vmArgs, other = truffle_extract_VM_args(args)
    inputFile = other[0]
    outputFile = 'test.ll'
    _, ext = os.path.splitext(inputFile)
    if ext == '.c':
        mx.run(['clang', '-S', '-emit-llvm', '-o', outputFile, inputFile])
        mx.run(['opt', '-S', '-o', outputFile, '-mem2reg', outputFile])
    elif ext == '.cpp':
        mx.run(['clang++', '-S', '-emit-llvm', '-o', outputFile, inputFile])
        mx.run(['opt', '-S', '-o', outputFile, '-mem2reg', '-lowerinvoke', '-prune-eh', '-simplifycfg', outputFile])
    else:
        exit(ext + " is not supported!")
    return runLLVM([getSearchPathOption(), 'test.ll'] + vmArgs)

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
    return ['-lm']

mx.update_commands(_suite, {
    'suoptbench' : [suOptBench, ''],
    'subench' : [suBench, ''],
    'clangbench' : [clangBench, ''],
    'gccbench' : [gccBench, ''],
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
    'su-debug' : [runDebugLLVM, ''],
    'su-tests' : [runTests, ''],
    'su-tests-bench' : [runBenchmarkTestCases, ''],
    'su-tests-gcc' : [runGCCTestCases, ''],
    'su-tests-llvm' : [runLLVMTestCases, ''],
    'su-tests-sulong' : [runTruffleTestCases, ''],
    'su-tests-nwcc' : [runNWCCTestCases, ''],
    'su-tests-types' : [runTypeTestCases, ''],
    'su-local-gate' : [localGate, ''],
    'su-clang' : [compileWithClang, ''],
    'su-clang++' : [compileWithClangPP, ''],
    'su-opt' : [opt, ''],
    'su-gcc' : [dragonEgg, ''],
    'su-g++' : [dragonEggGPP, '']
})
