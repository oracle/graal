from __future__ import print_function

import argparse
import mx
import os
import tools

import mx_sulong


_suite = mx.suite('sulong')
_testDir = os.path.join(_suite.dir, "tests/")
_cacheDir = os.path.join(_testDir, "cache/")

_sulongSuiteDir = os.path.join(_testDir, "sulong/")
_llvmSuiteDir = os.path.join(_testDir, "llvm/")
_llvmSuiteDirRoot = os.path.join(_llvmSuiteDir, "test-suite-3.2.src/")
_gccSuiteDir = os.path.join(_testDir, "gcc/")
_gccSuiteDirRoot = os.path.join(_gccSuiteDir, "gcc-5.2.0/gcc/testsuite/")


def compileSulongSuite():
    print("Compiling Sulong Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm']))
    print("Compiling Sulong Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GCC], ['-Iinclude', '-lm'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, optimizers=[tools.Tool.BB_VECTORIZE]))
    print("Compiling Sulong Suite with -O1/2/3 ", end='')
    tools.printProgress(tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GCC], ['-Iinclude', '-lm'], [tools.Optimization.O1, tools.Optimization.O2, tools.Optimization.O3], tools.ProgrammingLanguage.LLVMBC))

def compileLLVMSuite():
    ensureLLVMSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_llvmSuiteDir, "configs/"))
    print("Compiling LLVM Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], excludes=excludes))
    print("Compiling LLVM Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

def compileGCCSuite():
    ensureGCCSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/"))
    print("Compiling GCC Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_gccSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GFORTRAN], ['-Iinclude'], excludes=excludes))
    print("Compiling GCC Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_gccSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GFORTRAN], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

testSuites = {
    'gcc' : compileGCCSuite,
    'llvm' : compileLLVMSuite,
    'sulong' : compileSulongSuite,
}


def compileSuite(args=None):
    """compile all the test suites or selected ones (see -h or --help)"""
    parser = argparse.ArgumentParser(description="Compiles all or selected test suites.")
    parser.add_argument('suite', nargs='*', help=' '.join(testSuites.keys()), default=testSuites.keys())
    parser.add_argument('--verbose', dest='verbose', action='store_const', const=True, default=False, help='Display the test suite names before execution')
    parsedArgs = parser.parse_args(args)
    for testSuiteName in parsedArgs.suite:
        if parsedArgs.verbose:
            print('Compiling', testSuiteName, 'test suite')
        command = testSuites[testSuiteName]
        command()

def ensureLLVMSuiteExists():
    """downloads the LLVM suite if not downloaded yet"""
    if not os.path.exists(_llvmSuiteDirRoot):
        pullTestSuite('LLVM_TEST_SUITE', _llvmSuiteDir)

def ensureGCCSuiteExists():
    """downloads the GCC suite if not downloaded yet"""
    if not os.path.exists(_gccSuiteDirRoot):
        pullTestSuite('GCC_SOURCE', _gccSuiteDir, subDirInsideTar=[os.path.relpath(_gccSuiteDirRoot, _gccSuiteDir)])

def pullTestSuite(library, destDir, **kwargs):
    """downloads and unpacks a test suite"""
    mx.ensure_dir_exists(destDir)
    localPath = mx.library(library).get_path(True)
    mx_sulong.tar(localPath, destDir, **kwargs)
    os.remove(localPath)
    sha1Path = localPath + '.sha1'
    if os.path.exists(sha1Path):
        os.remove(sha1Path)
