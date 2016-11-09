import argparse
import mx
import mx_sulong
import os
import tools


_suite = mx.suite('sulong')
_testDir = os.path.join(_suite.dir, "tests/")
_cacheDir = os.path.join(_testDir, "cache/")

_sulongSuiteDir = os.path.join(_testDir, "sulong/")
_llvmSuiteDir = os.path.join(_testDir, "llvm/")
_llvmSuiteDirRoot = os.path.join(_llvmSuiteDir, "test-suite-3.2.src/")


def compileSulongSuite():
    print "Compiling Sulong Suite reference executables..."
    tools.multicompileRefFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'])
    print "Compiling Sulong Suite with -O0..."
    tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GCC], ['-Iinclude'], [tools.Optimization.NONE], tools.ProgrammingLanguage.LLVMIR, optimizers=[tools.Tool.BB_VECTORIZE])
    print "Compiling Sulong Suite with -O1/2/3..."
    tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG, tools.Tool.GCC], ['-Iinclude'], [tools.Optimization.O1, tools.Optimization.O2, tools.Optimization.O3], tools.ProgrammingLanguage.LLVMIR)
    # MG: compared to the old test suite we do not run the ll files

def compileLLVMSuite():
    ensureLLVMSuiteNewExists()
    excludes = list(tools.collectExcludes(os.path.join(_llvmSuiteDir, "configs/")))
    print "Compiling LLVM Suite reference executables..."
    tools.multicompileRefFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], excludes=excludes)
    print "Compiling LLVM Suite with -O0..."
    tools.multicompileFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], [tools.Optimization.NONE], tools.ProgrammingLanguage.LLVMIR, excludes=excludes)


testSuites = {
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
            print 'Compiling', testSuiteName, 'test suite'
        command = testSuites[testSuiteName]
        command()

def ensureLLVMSuiteNewExists():
    """downloads the LLVM suite if not downloaded yet"""
    if not os.path.exists(_llvmSuiteDirRoot):
        pullTestSuite('LLVM_TEST_SUITE', _llvmSuiteDir)

def pullTestSuite(library, destDir, **kwargs):
    """downloads and unpacks a test suite"""
    mx.ensure_dir_exists(destDir)
    localPath = mx.library(library).get_path(True)
    mx_sulong.tar(localPath, destDir, **kwargs)
    os.remove(localPath)
    sha1Path = localPath + '.sha1'
    if os.path.exists(sha1Path):
        os.remove(sha1Path)
