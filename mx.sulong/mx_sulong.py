import tarfile
import os
from os.path import join
import shutil
import subprocess
import sys

import mx
import mx_findbugs
import re
import argparse
import mx_benchmark
import mx_sulong_benchmarks

from mx_gate import Task, add_gate_runner
from mx_gitlogcheck import logCheck

import mx_testsuites

os.environ["LC_NUMERIC"] = "C"  # required for some testcases

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong/")
_root = join(_suite.dir, "projects/")
_libPath = join(_root, "com.oracle.truffle.llvm.libraries/src")
_testDir = join(_suite.dir, "tests/")
_argon2Dir = join(_testDir, "argon2/phc-winner-argon2/")
_toolDir = join(_suite.dir, "cache/tools/")
_clangPath = _toolDir + 'llvm/bin/clang'

_dragonEggPath = _toolDir + 'dragonegg/dragonegg-3.2.src/dragonegg.so'

_captureSrcDir = join(_root, "projects/com.oracle.truffle.llvm.pipe.native/src")


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
    _libPath,
    _captureSrcDir
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
    '3.3',
    '3.8',
    '3.9',
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
    with Task('BasicChecks', tasks) as t:
        if t: runChecks()
    mx_testsuites.runSuite()

def travis1(args=None):
    """executes the first Travis job (Javac build, benchmarks, polyglot, interop, tck, asm, types, and LLVM test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--force-javac'])
    with Task('TestBenchmarks', tasks) as t:
        if t: mx_testsuites.runSuite(['shootout'])
    with Task('TestPolglot', tasks) as t:
        if t: mx_testsuites.runSuite(['polyglot'])
    with Task('TestInterop', tasks) as t:
        if t: mx_testsuites.runSuite(['interop'])
    with Task('TestTck', tasks) as t:
        if t: mx_testsuites.runSuite(['tck'])
    with Task('TestAsm', tasks) as t:
        if t: mx_testsuites.runSuite(['assembly'])
    with Task('TestTypes', tasks) as t:
        if t: mx_testsuites.runSuite(['type'])
    with Task('TestMainArgs', tasks) as t:
        if t: mx_testsuites.runSuite(['args'])
    with Task('TestCallback', tasks) as t:
        if t: mx_testsuites.runSuite(['callback'])
    with Task('TestPipe', tasks) as t:
        if t: mx_testsuites.runSuite(['pipe'])
    with Task('TestLLVM', tasks) as t:
        if t: mx_testsuites.runSuite(['llvm'])
    with Task('TestSulong', tasks) as t:
        if t: mx_testsuites.runSuite(['sulong'])

def travis2(args=None):
    """executes the third Travis job (Javac build, NWCC, GCC compilation test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--force-javac'])
    with Task('TestNWCC', tasks) as t:
        if t: mx_testsuites.runSuite(['nwcc'])
    with Task('TestGCCSuiteCompile', tasks) as t:
        if t: mx_testsuites.runSuite(['parserTorture'])
    with Task('TestLifetime', tasks) as t:
        if t: mx_testsuites.runSuite(['lifetimeanalysis'])

def travisArgon2(args=None):
    """executes the argon2 Travis job (Javac build, argon2 test cases)"""
    tasks = []
    with Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--force-javac'])
    with Task('TestArgon2', tasks) as t:
        if t: runTestArgon2(optimize=False)

def pullTools(args=None):
    """pulls the LLVM and Dragonegg tools"""
    pullLLVMBinaries()
    pullInstallDragonEgg()

# platform dependent
def pullLLVMBinaries(args=None):
    """downloads the LLVM binaries"""
    toolDir = join(_toolDir, "llvm")
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

def dragonEggPath():
    if 'DRAGONEGG' in os.environ:
        return join(os.environ['DRAGONEGG'], 'dragonegg.so')
    else:
        return _dragonEggPath

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
    toolDir = join(_toolDir, "dragonegg")
    mx.ensure_dir_exists(toolDir)
    url = 'https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/dragonegg-3.2.src.tar.gz'
    localPath = pullsuite(toolDir, [url])
    tar(localPath, toolDir)
    os.remove(localPath)
    if mx.get_os() == 'darwin':
        gccToolDir = join(_toolDir, "gcc")
        url = 'https://lafo.ssw.uni-linz.ac.at/pub/sulong-deps/gcc-4.6.4.tar.gz'
        localPath = pullsuite(gccToolDir, [url])
        tar(localPath, gccToolDir)
        os.remove(localPath)
        mx.run(['patch', '-p1', _toolDir + 'dragonegg/dragonegg-3.2.src/Makefile', 'mx.sulong/dragonegg-mac.patch'])
    os.environ['GCC'] = getGCC()
    os.environ['CXX'] = getGPP()
    os.environ['CC'] = getGCC()
    pullLLVMBinaries()
    os.environ['LLVM_CONFIG'] = findLLVMProgram('llvm-config')
    print os.environ['LLVM_CONFIG']
    compileCommand = ['make']
    return mx.run(compileCommand, cwd=_toolDir + 'dragonegg/dragonegg-3.2.src')

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
    return mx.run_java(getCommonOptions(libNames) + vmArgs + getClasspathOptions() + ["com.oracle.truffle.llvm.LLVM"] + sulongArgs, out=out)

def runTests(args=None):
    mx_testsuites.runSuite(args)

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
        if t: mx.build(['--force-javac'])
    with Task('Findbugs', tasks) as t:
        if t and mx_findbugs.findbugs([]) != 0:
            t.abort('FindBugs warnings were found')

def compileWithEcjStrict(args=None):
    """build project with the option --warning-as-error"""
    if mx.get_env('JDT'):
        mx.clean([])
        mx.command_function('build')(['-p', '--warning-as-error'])
    else:
        exit('JDT environment variable not set. Cannot execute BuildJavaWithEcj task.')

def compileArgon2(main, optimize, cflags=None):
    if cflags is None:
        cflags = []
    argon2Src = ['src/argon2', 'src/core', 'src/blake2/blake2b', 'src/thread', 'src/encoding', 'src/ref', 'src/%s' % main, '../pthread-stub/pthread']
    argon2Bin = '%s.su' % main
    for src in argon2Src:
        inputFile = '%s.c' % src
        outputFile = '%s.bc' % src
        compileWithClang(['-c', '-emit-llvm', '-o', outputFile, '-std=c89', '-Wall', '-Wextra', '-Wno-type-limits', '-I../pthread-stub', '-Iinclude', '-Isrc'] + cflags + [inputFile])
        if optimize:
            opt(['-o', outputFile, outputFile] + getStandardLLVMOptFlags())
    link(['-o', argon2Bin] + ['%s.bc' % x for x in argon2Src])

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

def getCommonOptions(lib_args=None, versionFolder=None):
    return [
        '-Dgraal.TruffleCompilationExceptionsArePrinted=true',
        '-Dgraal.ExitVMOnException=true',
        getSearchPathOption(lib_args)
    ] + getBitcodeLibrariesOption(versionFolder)

def getSearchPathOption(lib_args=None):
    if lib_args is None:
        lib_args = ['-lgmp', '-lgfortran']

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

    libpath = join(mx.project('com.oracle.truffle.llvm.test.native').getOutput(), 'bin')
    for path, _, files in os.walk(libpath):
        for f in files:
            if f.endswith('.so'):
                lib_names.append(join(path, f))

    return '-Dsulong.DynamicNativeLibraryPath=' + ':'.join(lib_names)

def getCommonUnitTestOptions(versionFolder=None):
    return getCommonOptions(versionFolder=versionFolder) + ['-Xss56m', '-Xms2g', '-Xmx2g', getLLVMRootOption(), '-ea', '-esa']

# PE does not work yet for all test cases
def compilationSucceedsOption():
    return "-Dgraal.TruffleCompilationExceptionsAreFatal=true"

def getBenchmarkOptions():
    return [
        '-Dgraal.TruffleBackgroundCompilation=false',
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


class SulongNativeProject(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        d = join(suite.dir, subDir, name)
        mx.NativeProject.__init__(self, suite, name, subDir, [], deps, workingSets, results, output, d)

    def getBuildEnv(self, replaceVar=mx._replacePathVar):
        ret = super(SulongNativeProject, self).getBuildEnv(replaceVar=replaceVar)
        if os.environ.get('SULONG_VERSION') == '3.2':
            exportMxClang32(ret)
        elif os.environ.get('SULONG_VERSION') == '3.8':
            exportMxClang38(ret)
        else:
            exportMxClang32(ret)
            exportMxClang38(ret)
        return ret

def exportMxClang32(ret):
    exp = findLLVMProgram('clang', ['3.2', '3.3'])
    if not exp is None:
        ret['MX_CLANG_V32'] = exp
    exp = findLLVMProgram('opt', ['3.2', '3.3'])
    if not exp is None:
        ret['MX_OPT_V32'] = exp

def exportMxClang38(ret):
    exp = findLLVMProgram('clang', ['3.8', '3.9'])
    if not exp is None:
        ret['MX_CLANG_V38'] = exp

    exp = findLLVMProgram('opt', ['3.8', '3.9'])
    if not exp is None:
        ret['MX_OPT_V38'] = exp

def findLLVMProgram(llvmProgram, version=None):
    """tries to find a supported version of an installed LLVM program; if the program is not found it downloads the LLVM binaries and checks there"""
    installedProgram = findInstalledLLVMProgram(llvmProgram, version)

    if installedProgram is None:
        if not os.path.exists(_clangPath):
            pullLLVMBinaries()
        programPath = _toolDir + 'llvm/bin/' + llvmProgram
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

def compileWithClang(args=None, out=None, err=None):
    """runs Clang"""
    return mx.run([findLLVMProgram('clang')] + args, out=out, err=err)

def compileWithGCC(args=None):
    """runs GCC"""
    ensureLLVMBinariesExist()
    gccPath = _toolDir + 'llvm/bin/gcc'
    return mx.run([gccPath] + args)

def opt(args=None, out=None, err=None):
    """runs opt"""
    return mx.run([findLLVMProgram('opt')] + args, out=out, err=err)

def link(args=None):
    """Links LLVM bitcode into an su file."""
    return mx.run_java(getClasspathOptions() + ["com.oracle.truffle.llvm.runtime.Linker"] + args)

def compileWithClangPP(args=None, out=None, err=None):
    """runs Clang++"""
    return mx.run([findLLVMProgram('clang++')] + args, out=out, err=err)

def getClasspathOptions():
    """gets the classpath of the Sulong distributions"""
    return mx.get_runtime_jvm_args('SULONG')

def printOptions(args=None):
    """prints the Sulong Java property options"""
    return mx.run_java(getClasspathOptions() + ["com.oracle.truffle.llvm.runtime.options.LLVMOptions"])

def ensureDragonEggExists():
    """downloads dragonegg if not downloaded yet"""
    if not os.path.exists(dragonEggPath()):
        if 'DRAGONEGG' in os.environ:
            mx.abort('dragonegg not found at ' + os.environ['DRAGONEGG'])
        else:
            pullInstallDragonEgg()

def ensureLLVMBinariesExist():
    """downloads the LLVM binaries if they have not been downloaded yet"""
    for llvmBinary in basicLLVMDependencies:
        if findLLVMProgram(llvmBinary) is None:
            raise Exception(llvmBinary + ' not found')

def ensureArgon2Exists():
    """downloads Argon2 if not downloaded yet"""
    if not os.path.exists(_argon2Dir):
        pullTestSuite('ARGON2', _argon2Dir, stripLevels=1)

def suBench(args=None):
    """runs a given benchmark with Sulong"""
    ensureLLVMBinariesExist()
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    compileWithClang(['-c', '-emit-llvm', '-o', 'test.bc', sulongArgs[0]])
    return runLLVM(getBenchmarkOptions() + ['test.bc'] + vmArgs)

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

_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')

def compileWithClangOpt(inputFile, outputFile='test.bc', args=None, out=None, err=None):
    """compiles a program to LLVM IR with Clang using LLVM optimizations that benefit Sulong"""
    _, ext = os.path.splitext(inputFile)
    if ext == '.c':
        compileWithClang(['-c', '-emit-llvm', '-o', outputFile, inputFile] + _env_flags, out=out, err=err)
    elif ext == '.cpp':
        compileWithClangPP(['-c', '-emit-llvm', '-o', outputFile, inputFile] + _env_flags, out=out, err=err)
    else:
        exit(ext + " is not supported!")
    opt(['-o', outputFile, outputFile] + getStandardLLVMOptFlags(), out=out, err=err)

def suOptBench(args=None):
    """runs a given benchmark with Sulong after optimizing it with opt"""
    ensureLLVMBinariesExist()
    vmArgs, other = truffle_extract_VM_args(args)
    inputFile = other[0]
    outputFile = 'test.bc'
    compileWithClangOpt(inputFile, outputFile)
    return runLLVM(getBenchmarkOptions() + [getSearchPathOption(), outputFile] + vmArgs)

def suOptCompile(args=None):
    """compiles a given benchmark and optimizes it with opt"""
    ensureLLVMBinariesExist()
    inputFile = args[0]
    outputFile = 'test.bc'
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

def getBitcodeLibrariesOption(versionFolder=None):
    libraries = []
    if 'SULONG_NO_LIBRARY' not in os.environ:
        libpath = join(mx.project('com.oracle.truffle.llvm.libraries').getOutput(), 'bin')
        if not versionFolder is None:
            libpath = join(libpath, versionFolder)
        for path, _, files in os.walk(libpath):
            for f in files:
                if f.endswith('.bc'):
                    libraries.append(join(path, f))
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


mx_benchmark.add_bm_suite(mx_sulong_benchmarks.SulongBenchmarkSuite())

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
    'pylint' : mx.pylint,
    'eclipseformat' : (lambda args: mx.eclipseformat(['--primary'] + args))
}

mx.update_commands(_suite, {
    'suoptbench' : [suOptBench, ''],
    'subench' : [suBench, ''],
    'clangbench' : [clangBench, ''],
    'gccbench' : [gccBench, ''],
    'su-checks' : [runChecks, ''],
    'su-tests' : [runTests, ''],
    'su-suite' : [mx_testsuites.runSuite, ''],
    'su-clang' : [compileWithClang, ''],
    'su-options' : [printOptions, ''],
    'su-pullllvmbinaries' : [pullLLVMBinaries, ''],
    'su-pulltools' : [pullTools, ''],
    'su-pulldragonegg' : [pullInstallDragonEgg, ''],
    'su-run' : [runLLVM, ''],
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
    'su-travis-argon2' : [travisArgon2, ''],
    'su-ecj-strict' : [compileWithEcjStrict, ''],
    'su-gitlogcheck' : [logCheck, ''],
    'su-mdlcheck' : [mdlCheck, ''],
    'su-clangformatcheck' : [clangformatcheck, ''],
    'su-httpcheck' : [checkNoHttp, ''],
    'su-get-llvm-program' : [getLLVMProgramPath, ''],
    'su-get-gcc-program' : [getGCCProgramPath, ''],
    'su-compile-tests' : [mx_testsuites.compileSuite, ''],
})
