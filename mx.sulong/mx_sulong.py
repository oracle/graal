import tarfile
import os
from os.path import join
import shutil
import zipfile
import subprocess

import mx
import mx_findbugs

from mx_unittest import unittest
from mx_gate import Task, add_gate_runner, gate_clean
from mx_jvmci import VM, buildvms
from mx_gitlogcheck import logCheck

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong/")
_libPath = join(_mx, 'libs')
_root = join(_suite.dir, "projects/")
_parserDir = join(_root, "com.intel.llvm.ireditor")
_testDir = join(_root, "com.oracle.truffle.llvm.test/")
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

sulongDistributions = [
    'SULONG',
    'SULONG_TEST'
]

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
        with Task('Findbugs', tasks) as t:
            if t and mx_findbugs.findbugs([]) != 0:
                t.abort('FindBugs warnings were found')
    with VM('server', 'product'):
        with Task('TestBenchmarks', tasks) as t:
            if t: runBenchmarkTestCases()
    with VM('server', 'product'):
        with Task('TestTypes', tasks) as t:
            if t: runTypeTestCases()
    with VM('server', 'product'):
        with Task('TestPolglot', tasks) as t:
            if t: runPolyglotTestCases()
    with VM('server', 'product'):
        with Task('TestInterop', tasks) as t:
            if t: runInteropTestCases()
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
    with VM('server', 'product'):
        with Task('TestGCCSuiteCompile', tasks) as t:
            if t: runCompileTestCases()

def travis1(args=None):
    tasks = []
    with Task('BuildJavaWithEcj', tasks) as t:
        if t:
            if mx.get_env('JDT'):
                mx.command_function('build')(['-p', '--no-native', '--warning-as-error'])
                gate_clean([], tasks, name='CleanAfterEcjBuild')
            else:
                mx._warn_or_abort('JDT environment variable not set. Cannot execute BuildJavaWithEcj task.', args.strict_mode)
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--no-native', '--force-javac'])
    with Task('BuildHotSpotGraalServer: product', tasks) as t:
        if t: buildvms(['-c', '--vms', 'server', '--builds', 'product'])
    with VM('server', 'product'):
        with Task('Findbugs', tasks) as t:
            if t and mx_findbugs.findbugs([]) != 0:
                t.abort('FindBugs warnings were found')
    with VM('server', 'product'):
        with Task('TestBenchmarks', tasks) as t:
            if t: runBenchmarkTestCases()
    with VM('server', 'product'):
        with Task('TestPolglot', tasks) as t:
            if t: runPolyglotTestCases()
    with VM('server', 'product'):
        with Task('TestInterop', tasks) as t:
            if t: runInteropTestCases()
    with VM('server', 'product'):
        with Task('TestTypes', tasks) as t:
            if t: runTypeTestCases()
    with VM('server', 'product'):
        with Task('TestSulong', tasks) as t:
            if t: runTruffleTestCases()
    with VM('server', 'product'):
        with Task('TestLLVM', tasks) as t:
            if t: runLLVMTestCases()
    with VM('server', 'product'):
        with Task('TestNWCC', tasks) as t:
            if t: runNWCCTestCases()
    with VM('server', 'product'):
        with Task('TestGCCSuiteCompile', tasks) as t:
            if t: runCompileTestCases()

def travis2(args=None):
    tasks = []
    with Task('BuildHotSpotGraalServer: product', tasks) as t:
        if t: buildvms(['-c', '--vms', 'server', '--builds', 'product'])
    with VM('server', 'product'):
        with Task('TestGCC', tasks) as t:
            if t: runGCCTestCases()

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

def getDefaultGCC():
    # Ubuntu
    if which('gcc-4.6') is not None:
        return 'gcc-4.6'
    # Mac
    if which('gcc46') is not None:
        return 'gcc46'
    return None

def getDefaultGFortran():
    # Ubuntu
    if which('gfortran-4.6') is not None:
        return 'gfortran-4.6'
    # Mac
    if which('gfortran46') is not None:
        return 'gfortran46'
    return None

def getDefaultGPP():
    # Ubuntu
    if which('g++-4.6') is not None:
        return 'g++-4.6'
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
    if getDefaultGCC() is not None:
        return getDefaultGCC()
    else:
        mx.abort('Could not find a compatible GCC version to execute Dragonegg! Please install gcc-4.6 or another compatible version and specify it in the env file')

def getGFortran():
    """tries to locate a gfortran version suitable to execute Dragonegg"""
    specifiedGFortran = getCommand('SULONG_GFORTRAN')
    if specifiedGFortran is not None:
        return specifiedGFortran
    if getDefaultGFortran() is not None:
        return getDefaultGFortran()
    else:
        mx.abort('Could not find a compatible GFortran version to execute Dragonegg! Please install gfortran-4.6 or another compatible version and specify it in the env file')


def getGPP():
    """tries to locate a g++ version suitable to execute Dragonegg"""
    specifiedCPP = getCommand('SULONG_GPP')
    if specifiedCPP is not None:
        return specifiedCPP
    if getDefaultGPP() is not None:
        return getDefaultGPP()
    else:
        mx.abort('Could not find a compatible GCC version to execute Dragonegg! Please install g++-4.6 or another compatible version and specify it in the env file')

# platform independent
def pullInstallDragonEgg(args=None):
    """downloads and installs dragonegg (assumes that compatible GCC and G++ versions are installed)"""
    toolDir = join(_toolDir, "tools/dragonegg")
    mx.ensure_dir_exists(toolDir)
    url = 'http://llvm.org/releases/3.2/dragonegg-3.2.src.tar.gz'
    localPath = pullsuite(toolDir, [url])
    tar(localPath, toolDir)
    os.remove(localPath)
    if mx.get_os() == 'darwin':
        gccToolDir = join(_toolDir, "tools/gcc")
        url = 'http://ftpmirror.gnu.org/gcc/gcc-4.6.4/gcc-4.6.4.tar.gz'
        localPath = pullsuite(gccToolDir, [url])
        tar(localPath, gccToolDir)
        os.remove(localPath)
        mx.run(['patch', '-p1', _toolDir + 'tools/dragonegg/dragonegg-3.2.src/Makefile', 'mx.sulong/dragonegg-mac.patch'])
    os.environ['GCC'] = getGCC()
    os.environ['CXX'] = getGPP()
    os.environ['CC'] = getGCC()
    os.environ['LLVM_CONFIG'] = _toolDir + 'tools/llvm/bin/llvm-config'
    compileCommand = ['make']
    return mx.run(compileCommand, cwd=_toolDir + 'tools/dragonegg/dragonegg-3.2.src')

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

def runLLVM(args=None):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgsWithLibs = truffle_extract_VM_args(args)
    sulongArgs = []
    libNames = []
    for arg in sulongArgsWithLibs:
        if arg.startswith('-l'):
            libNames.append(arg)
        else:
            sulongArgs.append(arg)
    return mx.run_java(getCommonOptions(libNames) + vmArgs + getClasspathOptions() + ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.LLVM"] + sulongArgs, jdk=mx.get_jdk(tag='jvmci'))

def runTests(args=None):
    """runs all the test cases"""
    runGCCTestCases()
    runNWCCTestCases()
    runLLVMTestCases()
    runTruffleTestCases()
    runTypeTestCases()
    runPolyglotTestCases()
    runInteropTestCases()
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

def runPolyglotTestCases(args=None):
    """runs the type test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestPolyglotEngine'])

def runInteropTestCases(args=None):
    """runs the interop test cases"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.interop.LLVMInteropTest'])

def runCompileTestCases(args=None):
    """runs the compile (no execution) test cases of the GCC suite"""
    vmArgs, _ = truffle_extract_VM_args(args)
    return unittest(getCommonUnitTestOptions() + vmArgs + ['com.oracle.truffle.llvm.test.TestGCCSuiteCompile'])

def getCommonOptions(lib_args=None):
    return [
        '-Dgraal.TruffleCompilationExceptionsArePrinted=true',
        '-Dgraal.ExitVMOnException=true',
        getSearchPathOption(lib_args)
    ] + getBitcodeLibrariesOption()

def getSearchPathOption(lib_args=None):
    if lib_args is None:
        lib_args = ['-lgmp', '-lgfortran']

    lib_names = []

    lib_aliases = {
        '-lc': ['libc.so.6', 'libc.dylib'],
        '-lstdc++': ['libstdc++.so.6', 'libstdc++.6.dylib'],
        '-lgmp': ['libgmp.so.10', 'libgmp.10.dylib'],
        '-lgfortran': ['libgfortran.so.3', 'libgfortran.3.dylib']
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
    return getCommonOptions() + ['-Xss56m', getLLVMRootOption(), '-ea', '-esa']

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

def compileWithClang(args=None):
    """runs Clang"""
    ensureLLVMBinariesExist()
    clangPath = _toolDir + 'tools/llvm/bin/clang'
    return mx.run([clangPath] + args)

def compileWithGCC(args=None):
    """runs GCC"""
    ensureLLVMBinariesExist()
    gccPath = _toolDir + 'tools/llvm/bin/gcc'
    return mx.run([gccPath] + args)


def opt(args=None):
    """"Runs opt."""
    ensureLLVMBinariesExist()
    optPath = _toolDir + 'tools/llvm/bin/opt'
    return mx.run([optPath] + args)

def link(args=None):
    """Links LLVM bitcode into an su file."""
    modules = []
    libraries = []
    n = 0
    while n < len(args):
        arg = args[n]
        if arg == '-o':
            out = args[n + 1]
            n += 1
        elif arg.startswith('-l'):
            libraries += [arg[2:]]
        else:
            modules += [arg]
        n += 1
    if out is None:
        out = 'out.su'
    if len(modules) == 1:
        prefix = os.path.dirname(modules[0])
    else:
        prefix = os.path.commonprefix(modules)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for module in modules:
            z.write(module, module[len(prefix):])
        z.writestr('libs', '\n'.join(libraries))

def compileWithClangPP(args=None):
    """runs Clang++"""
    ensureLLVMBinariesExist()
    clangPath = _toolDir + 'tools/llvm/bin/clang++'
    return mx.run([clangPath] + args)

def getClasspathOptions():
    """gets the classpath of the Sulong distributions"""
    return ['-cp', ':'.join([mx.classpath(mx.distribution(distr)) for distr in sulongDistributions])]

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
    if not os.path.exists(_clangPath):
        pullLLVMBinaries()

def suBench(args=None):
    """runs a given benchmark with Sulong"""
    ensureLLVMBinariesExist()
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    compileWithClang(['-S', '-emit-llvm', '-o', 'test.ll', sulongArgs[0]])
    return runLLVM(getBenchmarkOptions() + ['test.ll'] + vmArgs)

def compileWithClangOpt(inputFile, outputFile='test.ll'):
    """compiles a program to LLVM IR with Clang using LLVM optimizations that benefit Sulong"""
    _, ext = os.path.splitext(inputFile)
    if ext == '.c':
        compileWithClang(['-S', '-emit-llvm', '-o', outputFile, inputFile])
        opt(['-S', '-o', outputFile, '-mem2reg', outputFile])
    elif ext == '.cpp':
        compileWithClangPP(['-S', '-emit-llvm', '-o', outputFile, inputFile])
        opt(['-S', '-o', outputFile, '-mem2reg', '-lowerinvoke', '-prune-eh', '-simplifycfg', outputFile])
    else:
        exit(ext + " is not supported!")
    opt(['-S', '-o', outputFile, outputFile, '-globalopt', '-simplifycfg', '-constprop', '-instcombine', '-dse', '-loop-simplify', '-reassociate', '-licm', '-gvn'])

def suOptBench(args=None):
    """runs a given benchmark with Sulong after optimizing it with opt"""
    ensureLLVMBinariesExist()
    vmArgs, other = truffle_extract_VM_args(args)
    inputFile = other[0]
    outputFile = 'test.ll'
    compileWithClangOpt(inputFile, outputFile)
    return runLLVM(getBenchmarkOptions() + [getSearchPathOption(), outputFile] + vmArgs)

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
    return ['-lm', '-lgmp']

def mdlCheck(args=None):
    """runs mdl on all .md files in the projects and root directory"""
    for path, _, files in os.walk('.'):
        for f in files:
            if f.endswith('.md') and (path.startswith('./projects') or path is '.'):
                absPath = path + '/' + f
                subprocess.check_output(['mdl', '-r~MD026,~MD002,~MD029,~MD032', absPath])

def getBitcodeLibrariesOption():
    libraries = []
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
    checkCFile(_suite.dir + '/include/truffle.h')
    checkCFiles(_testDir)
    checkCFiles(_interopTestDir)

def checkCFiles(targetDir):
    error = False
    for path, _, files in os.walk(targetDir):
        for f in files:
            if f.endswith('.c') or f.endswith('.cpp'):
                if not checkCFile(path + '/' + f):
                    error = True
    if error:
        print "found formatting errors!"
        exit(-1)

def checkCFile(targetFile):
    """ Checks the formatting of a C file and returns True if the formatting is okay """
    formattedContent = subprocess.check_output(['clang-format-3.4', '-style={BasedOnStyle: llvm, ColumnLimit: 150}', targetFile]).splitlines()
    with open(targetFile) as f:
        originalContent = f.read().splitlines()
    if not formattedContent == originalContent:
        print '\n'.join(formattedContent)
        print '\nplease fix the formatting in', targetFile, 'to the format given above'
        return False
    return True

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
    'su-tests' : [runTests, ''],
    'su-tests-bench' : [runBenchmarkTestCases, ''],
    'su-tests-gcc' : [runGCCTestCases, ''],
    'su-tests-llvm' : [runLLVMTestCases, ''],
    'su-tests-sulong' : [runTruffleTestCases, ''],
    'su-tests-nwcc' : [runNWCCTestCases, ''],
    'su-tests-types' : [runTypeTestCases, ''],
    'su-tests-polyglot' : [runPolyglotTestCases, ''],
    'su-tests-interop' : [runInteropTestCases, ''],
    'su-tests-compile' : [runCompileTestCases, ''],
    'su-local-gate' : [localGate, ''],
    'su-clang' : [compileWithClang, ''],
    'su-clang++' : [compileWithClangPP, ''],
    'su-opt' : [opt, ''],
    'su-link' : [link, ''],
    'su-gcc' : [dragonEgg, ''],
    'su-gfortran' : [dragonEggGFortran, ''],
    'su-g++' : [dragonEggGPP, ''],
    'su-travis1' : [travis1, ''],
    'su-travis2' : [travis2, ''],
    'su-gitlogcheck' : [logCheck, ''],
    'su-mdlcheck' : [mdlCheck, ''],
    'su-clangformatcheck' : [clangformatcheck, '']
})
