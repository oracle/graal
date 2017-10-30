import tarfile
import os
from os.path import join
import shutil
import subprocess

import mx
import mx_subst
import re
import mx_benchmark
import mx_sulong_benchmarks
import mx_unittest

from mx_unittest import add_config_participant
from mx_gate import Task, add_gate_runner

import mx_testsuites

# re-export SulongTestSuite class so it can be used from suite.py
from mx_testsuites import SulongTestSuite #pylint: disable=unused-import

os.environ["LC_NUMERIC"] = "C"  # required for some testcases

_suite = mx.suite('sulong')
_mx = join(_suite.dir, "mx.sulong")
_root = join(_suite.dir, "projects")
_libPath = join(_root, "com.oracle.truffle.llvm.libraries.bitcode", "src")
_testDir = join(_suite.dir, "tests")
_toolDir = join(_suite.dir, "cache", "tools")
_clangPath = join(_toolDir, "llvm", "bin", "clang")

_dragonEggPath = join(_toolDir, "dragonegg", "dragonegg-3.2.src", "dragonegg.so")

_captureSrcDir = join(_root, "com.oracle.truffle.llvm.pipe.native", "src")


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
]

# the basic LLVM dependencies for running the test cases and executing the mx commands
basicLLVMDependencies = [
    'clang',
    'clang++',
    'opt',
    'llc',
    'llvm-as'
]

def _unittest_config_participant(config):
    (vmArgs, mainClass, mainClassArgs) = config
    libs = [mx_subst.path_substitutions.substitute('<path:SULONG_TEST_NATIVE>/<lib:sulongtest>')]
    vmArgs = getCommonOptions(True, libs) + vmArgs
    return (vmArgs, mainClass, mainClassArgs)

add_config_participant(_unittest_config_participant)


def _sulong_gate_runner(args, tasks):
    with Task('TestBenchmarks', tasks, tags=['benchmarks', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('shootout')
    with Task('TestTypes', tasks, tags=['type', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('type')
    with Task('TestPipe', tasks, tags=['pipe', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('pipe')
    with Task('TestLLVM', tasks, tags=['llvm']) as t:
        if t: mx_testsuites.runSuite('llvm')
    with Task('TestNWCC', tasks, tags=['nwcc']) as t:
        if t: mx_testsuites.runSuite('nwcc')
    with Task('TestGCCParserTorture', tasks, tags=['parser']) as t:
        if t: mx_testsuites.runSuite('parserTorture')
    with Task('TestGCC_C', tasks, tags=['gcc_c']) as t:
        if t: mx_testsuites.runSuite('gcc_c')
    with Task('TestGCC_CPP', tasks, tags=['gcc_cpp']) as t:
        if t: mx_testsuites.runSuite('gcc_cpp')
    with Task('TestGCC_Fortran', tasks, tags=['gcc_fortran']) as t:
        if t: mx_testsuites.runSuite('gcc_fortran')
    with Task("TestSulong", tasks, tags=['sulong', 'sulongBasic']) as t:
        if t: mx_unittest.unittest(['SulongSuite'])
    with Task("TestInterop", tasks, tags=['interop', 'sulongBasic']) as t:
        if t: mx_unittest.unittest(['LLVMInteropTest'])
    with Task('TestAssembly', tasks, tags=['assembly', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('assembly')
    with Task('TestArgs', tasks, tags=['args', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('args')
    with Task('TestCallback', tasks, tags=['callback', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('callback')
    with Task('TestVarargs', tasks, tags=['vaargs', 'sulongMisc']) as t:
        if t: mx_testsuites.runSuite('vaargs')

add_gate_runner(_suite, _sulong_gate_runner)


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
        return join(os.environ['DRAGONEGG'], 'dragonegg.so')
    if 'DRAGONEGG_GCC' in os.environ:
        path = join(os.environ['DRAGONEGG_GCC'], 'lib', 'dragonegg.so')
        if os.path.exists(path):
            return path
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
        mx.run(['patch', '-p1', join(_toolDir, 'dragonegg', 'dragonegg-3.2.src', 'Makefile'), join('mx.sulong', 'dragonegg-mac.patch')])
    os.environ['GCC'] = getGCC()
    os.environ['CXX'] = getGPP()
    os.environ['CC'] = getGCC()
    pullLLVMBinaries()
    os.environ['LLVM_CONFIG'] = findLLVMProgramForDragonegg('llvm-config')
    compileCommand = ['make']
    return mx.run(compileCommand, cwd=join(_toolDir, 'dragonegg', 'dragonegg-3.2.src'))

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

def runLLVM(args=None, out=None):
    """uses Sulong to execute a LLVM IR file"""
    vmArgs, sulongArgs = truffle_extract_VM_args(args)
    return mx.run_java(getCommonOptions(False) + vmArgs + getClasspathOptions() + ["com.oracle.truffle.llvm.Sulong"] + sulongArgs, out=out)

def getCommonOptions(withAssertion, lib_args=None):
    options = ['-Dgraal.TruffleCompilationExceptionsArePrinted=true',
        '-Dgraal.ExitVMOnException=true']

    options.append(mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:SULONG_LIBS>'))

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
        versionString = subprocess.check_output([program, '--version'])
    except subprocess.CalledProcessError as e:
        # on my machine, e.g., opt returns a non-zero opcode even on success
        versionString = e.output
    return versionString

def getLLVMVersion(llvmProgram):
    """executes the program with --version and extracts the LLVM version string"""
    versionString = getVersion(llvmProgram)
    printLLVMVersion = re.search(r'(clang |LLVM )?(version )?((3|4|5)\.\d)', versionString, re.IGNORECASE)
    if printLLVMVersion is None:
        return None
    else:
        return printLLVMVersion.group(3)

# the makefiles do not check which version of clang they invoke
def getClangImplicitArgs():
    mainLLVMVersion = getLLVMVersion('clang')
    if mainLLVMVersion and mainLLVMVersion.startswith('5'):
        return "-Xclang -disable-O0-optnone"
    else:
        return ""

mx_subst.path_substitutions.register_no_arg('clangImplicitArgs', getClangImplicitArgs)

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

def getClasspathOptions():
    """gets the classpath of the Sulong distributions"""
    return mx.get_runtime_jvm_args('SULONG')

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


mx.update_commands(_suite, {
    'pulldragonegg' : [pullInstallDragonEgg, ''],
    'lli' : [runLLVM, ''],
})
