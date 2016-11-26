from __future__ import print_function

import argparse
import mx
import mx_gate
import mx_unittest
import os
import tools

import mx_sulong


_suite = mx.suite('sulong')
_testDir = os.path.join(_suite.dir, "tests/")
_cacheDir = os.path.join(_testDir, "cache/")

_sulongSuiteDir = os.path.join(_testDir, "sulong/")
_benchmarksgameSuiteDir = os.path.join(_testDir, "benchmarksgame/")
_benchmarksgameSuiteDirRoot = os.path.join(_benchmarksgameSuiteDir, "benchmarksgame-2014-08-31/benchmarksgame/bench/")
_interoptestsDir = os.path.join(_testDir, "interoptests/")
_otherDir = os.path.join(_testDir, "other/")
_inlineassemblytestsDir = os.path.join(_testDir, "inlineassemblytests/")
_llvmSuiteDir = os.path.join(_testDir, "llvm/")
_assemblySuiteDir = os.path.join(_testDir, "inlineassemblytests/")
_llvmSuiteDirRoot = os.path.join(_llvmSuiteDir, "test-suite-3.2.src/")
_gccSuiteDir = os.path.join(_testDir, "gcc/")
_gccSuiteDirRoot = os.path.join(_gccSuiteDir, "gcc-5.2.0/gcc/testsuite/")
_parserTortureSuiteDirRoot = os.path.join(_gccSuiteDir, "gcc-5.2.0/gcc/testsuite/gcc.c-torture/compile")
_LTADirRoot = os.path.join(_gccSuiteDir, "lta/gcc-5.2.0/gcc/testsuite/")
_nwccSuiteDir = os.path.join(_testDir, "nwcc/")
_nwccSuiteDirRoot2 = os.path.join(_nwccSuiteDir, "nwcc_0.8.3/tests/")
_nwccSuiteDirRoot1 = os.path.join(_nwccSuiteDir, "nwcc_0.8.3/test2/")

def compileSulongSuite():
    print("Compiling Sulong Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm']))
    print("Compiling Sulong Suite with clang -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, optimizers=[tools.Tool.BB_VECTORIZE]))
    print("Compiling Sulong Suite with clang -O1/2/3 ", end='')
    tools.printProgress(tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], [tools.Optimization.O1, tools.Optimization.O2, tools.Optimization.O3], tools.ProgrammingLanguage.LLVMBC))
    print("Compiling Sulong Suite with gcc -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_sulongSuiteDir, _cacheDir, [tools.Tool.GCC], ['-Iinclude', '-lm'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC))

def runSulongSuite(vmArgs):
    """runs the Sulong test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['sulong'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + ['-Dgraal.TruffleCompilationThreshold=10', '-Dsulong.ExecutionCount=20'] + vmArgs + ['--verbose', "com.oracle.truffle.llvm.test.alpha.SulongSuite"])

def runShootoutSuite(vmArgs):
    """runs the Sulong test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['shootout'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--verbose', "com.oracle.truffle.llvm.test.alpha.ShootoutsSuite"])

def runShootoutSulongOnlySuite(vmArgs):
    """runs the Sulong test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['shootout'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--verbose', "com.oracle.truffle.llvm.test.alpha.ShootoutsSulongOnlySuite"])

def runLLVMSuite(vmArgs):
    """runs the LLVM test suite"""
    compileSuite(['llvm'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.alpha.LLVMSuite"])

def runNWCCSuite(vmArgs):
    """runs the NWCC test suite"""
    compileSuite(['nwcc'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--very-verbose', "com.oracle.truffle.llvm.test.alpha.NWCCSuite"])

def runGCCSuite(vmArgs):
    """runs the LLVM test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['gcc'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--very-verbose', "com.oracle.truffle.llvm.test.alpha.GCCSuite"])

def compileInteropTests():
    print("Compiling Interop with clang -O0 and mem2reg", end='')
    tools.printProgress(tools.multicompileFolder(_interoptestsDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, optimizers=[tools.Tool.MEM2REG]))

def compileOtherTests():
    print("Compiling Other with clang -O0", end='')
    tools.printProgress(tools.multicompileFolder(_otherDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC))

def runArgsTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['args'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.alpha.MainArgsTest"])

def runInteropTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['interop'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.interop.LLVMInteropTest"])

def runTCKTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['interop'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.interop.LLVMTckTest"])

def runInlineAssemblySuite(vmArgs):
    """runs the InlineAssembly test suite"""
    compileSuite(['assembly'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ["com.oracle.truffle.llvm.test.alpha.InlineAssemblyTest"])

def runLifetimeAnalysisTests(vmArgs):
    """runs the LTA test suite"""
    compileSuite(['gcc'])
    ensureLifetimeAnalysisReferenceExists()
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--very-verbose', "com.oracle.truffle.llvm.test.alpha.LifetimeAnalysisSuite"])

def runParserTortureSuite(vmArgs):
    """runs the ParserTorture test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['parserTorture'])
    return mx_unittest.unittest(mx_sulong.getCommonUnitTestOptions() + vmArgs + ['--very-verbose', "com.oracle.truffle.llvm.test.alpha.ParserTortureSuite"])


def compileLLVMSuite():
    ensureLLVMSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_llvmSuiteDir, "configs/"))
    print("Compiling LLVM Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], excludes=excludes))
    print("Compiling LLVM Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_llvmSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

def compileInlineAssemblySuite():
    print("Compiling Assembly Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_assemblySuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude']))
    print("Compiling Assembly Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_assemblySuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC))


def compileGCCSuite():
    ensureGCCSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/"))
    print("Compiling GCC Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_gccSuiteDir, _cacheDir, [tools.Tool.CLANG_CPP, tools.Tool.CLANG_C, tools.Tool.GFORTRAN], ['-Iinclude'], excludes=excludes))
    print("Compiling GCC files with GFORTRAN ", end='')
    tools.printProgress(tools.multicompileFolder(_gccSuiteDir, _cacheDir, [tools.Tool.GFORTRAN], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))
    print("Compiling GCC files with CPP ", end='')
    tools.printProgress(tools.multicompileFolder(_gccSuiteDir, _cacheDir, [tools.Tool.CLANG_CPP], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, optimizers=[tools.Tool.CPP_OPT], excludes=excludes))
    print("Compiling GCC files with C ", end='')
    tools.printProgress(tools.multicompileFolder(_gccSuiteDir, _cacheDir, [tools.Tool.CLANG_C], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, optimizers=[tools.Tool.C_OPT], excludes=excludes))

def compileParserTurtureSuite():
    ensureGCCSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/gcc.c-torture/compile/"))
    print("Compiling parser torture files with C ", end='')
    tools.printProgress(tools.multicompileFolder(_parserTortureSuiteDirRoot, _cacheDir, [tools.Tool.CLANG_C], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))


def compileShootoutSuite():
    ensureShootoutsExist()
    excludes = tools.collectExcludePattern(os.path.join(_benchmarksgameSuiteDir, "configs/"))
    print("Compiling Shootout Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_benchmarksgameSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], excludes=excludes))
    print("Compiling Shootout Suite with -O1 ", end='')
    tools.printProgress(tools.multicompileFolder(_benchmarksgameSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude', '-lm'], [tools.Optimization.O1], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

def compileNWCCSuite():
    ensureNWCCSuiteExists()
    excludes = tools.collectExcludePattern(os.path.join(_nwccSuiteDir, "configs/"))
    print("Compiling NWCC Suite reference executables ", end='')
    tools.printProgress(tools.multicompileRefFolder(_nwccSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], excludes=excludes))
    print("Compiling NWCC Suite with -O0 ", end='')
    tools.printProgress(tools.multicompileFolder(_nwccSuiteDir, _cacheDir, [tools.Tool.CLANG], ['-Iinclude'], [tools.Optimization.O0], tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

testSuites = {
    'args' : (compileOtherTests, runArgsTests),
    'nwcc' : (compileNWCCSuite, runNWCCSuite),
    'assembly' : (compileInlineAssemblySuite, runInlineAssemblySuite),
    'gcc' : (compileGCCSuite, runGCCSuite),
    'llvm' : (compileLLVMSuite, runLLVMSuite),
    'sulong' : (compileSulongSuite, runSulongSuite),
    'shootoutSulongOnly' : (compileShootoutSuite, runShootoutSulongOnlySuite),
    'shootout' : (compileShootoutSuite, runShootoutSuite),
    'interop' : (compileInteropTests, runInteropTests),
    'tck' : (compileInteropTests, runTCKTests),
    'lifetimeanalysis' : (compileGCCSuite, runLifetimeAnalysisTests),
    'parserTorture' : (compileParserTurtureSuite, runParserTortureSuite),
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
        compileCommand, _ = testSuites[testSuiteName]
        compileCommand()

def travisRunSuite(args=None):
    """executes all the test suites or selected ones (see -h or --help)"""
    vmArgs, otherArgs = mx_sulong.truffle_extract_VM_args(args)
    parser = argparse.ArgumentParser(description="Compiles all or selected test suites.")
    parser.add_argument('suite', nargs='*', help=' '.join(testSuites.keys()), default=testSuites.keys())
    parsedArgs = parser.parse_args(otherArgs)

    tasks = []
    with mx_gate.Task('BuildJavaWithJavac', tasks) as t:
        if t: mx.command_function('build')(['-p', '--warning-as-error', '--force-javac'])

    for testSuiteName in parsedArgs.suite:
        with mx_gate.Task('Test%s' % testSuiteName.capitalize(), tasks) as t:
            if t:
                _, runCommand = testSuites[testSuiteName]
                runCommand(vmArgs)

def ensureLLVMSuiteExists():
    """downloads the LLVM suite if not downloaded yet"""
    if not os.path.exists(_llvmSuiteDirRoot):
        pullTestSuite('LLVM_TEST_SUITE', _llvmSuiteDir)

def ensureShootoutsExist():
    """downloads the Shootout suite if not downloaded yet"""
    if not os.path.exists(_benchmarksgameSuiteDirRoot):
        pullTestSuite('SHOOTOUT_SUITE', _benchmarksgameSuiteDir, subDirInsideTar=[os.path.relpath(_benchmarksgameSuiteDirRoot, _benchmarksgameSuiteDir)], stripLevels=3)
        renameBenchmarkFiles(_benchmarksgameSuiteDir)

def ensureGCCSuiteExists():
    """downloads the GCC suite if not downloaded yet"""
    if not os.path.exists(_gccSuiteDirRoot):
        pullTestSuite('GCC_SOURCE', _gccSuiteDir, subDirInsideTar=[os.path.relpath(_gccSuiteDirRoot, _gccSuiteDir)])

def ensureNWCCSuiteExists():
    """downloads the NWCC suite if not downloaded yet"""
    if not os.path.exists(_nwccSuiteDirRoot1):
        pullTestSuite('NWCC_SUITE', _nwccSuiteDir, subDirInsideTar=[os.path.relpath(_nwccSuiteDirRoot1, _nwccSuiteDir)])
    if not os.path.exists(_nwccSuiteDirRoot2):
        pullTestSuite('NWCC_SUITE', _nwccSuiteDir, subDirInsideTar=[os.path.relpath(_nwccSuiteDirRoot2, _nwccSuiteDir)])

def ensureLifetimeAnalysisReferenceExists():
    """downloads the LTA ref files for GCC suite if not downloaded yet"""
    if not os.path.exists(_LTADirRoot):
        pullTestSuite('LTA_REF', _LTADirRoot)


def pullTestSuite(library, destDir, **kwargs):
    """downloads and unpacks a test suite"""
    mx.ensure_dir_exists(destDir)
    localPath = mx.library(library).get_path(True)
    mx_sulong.tar(localPath, destDir, **kwargs)
    os.remove(localPath)
    sha1Path = localPath + '.sha1'
    if os.path.exists(sha1Path):
        os.remove(sha1Path)

def renameBenchmarkFiles(directory):
    for path, _, files in os.walk(directory):
        for f in files:
            absPath = path + '/' + f
            _, ext = os.path.splitext(absPath)
            if ext in ['.gcc', '.cint']:
                os.rename(absPath, absPath + '.c')
            if ext in ['.gpp']:
                os.rename(absPath, absPath + '.cpp')
