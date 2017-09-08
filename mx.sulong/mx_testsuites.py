from __future__ import print_function

import argparse
import mx
import mx_gate
import mx_unittest
import mx_subst
import os
import mx_tools
import shutil

import mx_sulong


_suite = mx.suite('sulong')
_testDir = os.path.join(_suite.dir, "tests/")
_cacheDir = os.path.join(_suite.dir, "cache/tests")

_benchmarksgameSuiteDir = os.path.join(_testDir, "benchmarksgame/")
_benchmarksgameSuiteDirRoot = os.path.join(_benchmarksgameSuiteDir, "benchmarksgame-2014-08-31/benchmarksgame/bench/")
_otherDir = os.path.join(_testDir, "other/")
_inlineassemblytestsDir = os.path.join(_testDir, "inlineassemblytests/")
_llvmSuiteDir = os.path.join(_testDir, "llvm/")
_assemblySuiteDir = os.path.join(_testDir, "inlineassemblytests/")
_llvmSuiteDirRoot = os.path.join(_llvmSuiteDir, "test-suite-3.2.src/")
_gccSuiteDir = os.path.join(_testDir, "gcc/")
_gccSuiteDirRoot = os.path.join(_gccSuiteDir, "gcc-5.2.0/gcc/testsuite/")
_parserTortureSuiteDirRoot = os.path.join(_gccSuiteDir, "gcc-5.2.0/gcc/testsuite/gcc.c-torture/compile")
_nwccSuiteDir = os.path.join(_testDir, "nwcc/")
_nwccSuiteDirRoot2 = os.path.join(_nwccSuiteDir, "nwcc_0.8.3/tests/")
_nwccSuiteDirRoot1 = os.path.join(_nwccSuiteDir, "nwcc_0.8.3/test2/")

def deleteCachedTests(folderInCache):
    p = os.path.join(_cacheDir, folderInCache)
    if os.path.exists(p):
        shutil.rmtree(p)

def run(vmArgs, unittest, extraOption=None, extraLibs=None):
    if extraOption is None:
        extraOption = []
    if mx.get_opts().verbose:
        command = mx_sulong.getCommonOptions(True, extraLibs) + extraOption + vmArgs + ['--very-verbose', unittest]
        print ('Running mx unittest ' + ' '.join(command))
        return mx_unittest.unittest(command)
    else:
        command = mx_sulong.getCommonOptions(True, extraLibs) + extraOption + vmArgs + [unittest]
        return mx_unittest.unittest(command)

def runShootoutSuite(vmArgs):
    """runs the Sulong test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['shootout'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.ShootoutsSuite", extraLibs=["libgmp.so.10"])

def runLLVMSuite(vmArgs):
    """runs the LLVM test suite"""
    compileSuite(['llvm'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.LLVMSuite")

def runNWCCSuite(vmArgs):
    """runs the NWCC test suite"""
    compileSuite(['nwcc'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.NWCCSuite")

def runGCCSuite32(vmArgs):
    """runs the LLVM test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['gcc32'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.GCCSuite", extraLibs=["libgfortran.so.3"])

def runGCCSuite38(vmArgs):
    """runs the LLVM test suite"""
    compileSuite(['gcc38'])
    return run(vmArgs + ['-Dsulongtest.ignoreFortran=true'], "com.oracle.truffle.llvm.test.alpha.GCCSuite")

def compileOtherTests():
    print("Compiling Other with clang -O0", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_otherDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude', '-lm'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC))

def runArgsTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['args'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.MainArgsTest")

def runCallbackTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['callback'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.CallbackTest")

def runVAargsTests(vmArgs):
    """runs the Sulong test suite"""
    compileSuite(['vaargs'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.VAArgsTest")

def runInlineAssemblySuite(vmArgs):
    """runs the InlineAssembly test suite"""
    compileSuite(['assembly'])
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.InlineAssemblyTest")

def runParserTortureSuite(vmArgs):
    """runs the ParserTorture test suite"""
    mx_sulong.ensureDragonEggExists()
    compileSuite(['parserTorture'])
    return run(vmArgs + ['-Dpolyglot.llvm.parseOnly=true'], "com.oracle.truffle.llvm.test.alpha.ParserTortureSuite")

def runTypeTests(vmArgs):
    """runs the Type test suite"""
    return run(vmArgs, "com.oracle.truffle.llvm.types.floating.test")

def runPipeTests(vmArgs):
    """runs the Pipe test suite"""
    return run(vmArgs, "com.oracle.truffle.llvm.test.alpha.CaptureOutputTest")

def compileLLVMSuite():
    deleteCachedTests('llvm')
    ensureLLVMSuiteExists()
    excludes = mx_tools.collectExcludePattern(os.path.join(_llvmSuiteDir, "configs/"))
    print("Compiling LLVM Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_llvmSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude'], excludes=excludes))
    print("Compiling LLVM Suite with -O0 ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_llvmSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

def compileInlineAssemblySuite():
    print("Compiling Assembly Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_assemblySuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude']))
    print("Compiling Assembly Suite with -O0 ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_assemblySuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC))

def compileV32GCCSuite():
    deleteCachedTests('gcc')
    ensureGCCSuiteExists()
    excludes = mx_tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/"))
    print("Compiling GCC Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_CPP, mx_tools.Tool.CLANG_C, mx_tools.Tool.GFORTRAN], ['-Iinclude'], excludes=excludes))
    print("Compiling GCC files with GFORTRAN ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.GFORTRAN], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, excludes=excludes))
    print("Compiling GCC files with CPP ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_CPP], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, optimizers=[mx_tools.Tool.CPP_OPT], excludes=excludes))
    print("Compiling GCC files with C ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_C], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, optimizers=[mx_tools.Tool.C_OPT], excludes=excludes))

def compileV38GCCSuite():
    deleteCachedTests('gcc')
    ensureGCCSuiteExists()
    excludes = mx_tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/"))
    print("Compiling GCC Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_CPP, mx_tools.Tool.CLANG_C], ['-Iinclude'], excludes=excludes))
    print("Compiling GCC files with CPP ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_CPP], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, optimizers=[mx_tools.Tool.CPP_OPT], excludes=excludes))
    print("Compiling GCC files with C ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_gccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG_C], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, optimizers=[mx_tools.Tool.C_OPT], excludes=excludes))


def compileParserTurtureSuite():
    ensureGCCSuiteExists()
    excludes = mx_tools.collectExcludePattern(os.path.join(_gccSuiteDir, "configs/gcc.c-torture/compile/"))
    print("Compiling parser torture files with C ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_parserTortureSuiteDirRoot, _cacheDir, [mx_tools.Tool.CLANG_C], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, excludes=excludes))


def compileShootoutSuite():
    ensureShootoutsExist()
    excludes = mx_tools.collectExcludePattern(os.path.join(_benchmarksgameSuiteDir, "configs/"))
    print("Compiling Shootout Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_benchmarksgameSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude', '-lm', '-lgmp'], excludes=excludes))
    print("Compiling Shootout Suite with -O1 ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_benchmarksgameSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude', '-lm', '-lgmp'], [mx_tools.Optimization.O1], mx_tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

def compileNWCCSuite():
    ensureNWCCSuiteExists()
    deleteCachedTests('nwcc')
    excludes = mx_tools.collectExcludePattern(os.path.join(_nwccSuiteDir, "configs/"))
    print("Compiling NWCC Suite reference executables ", end='')
    mx_tools.printProgress(mx_tools.multicompileRefFolder(_nwccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude'], excludes=excludes))
    print("Compiling NWCC Suite with -O0 ", end='')
    mx_tools.printProgress(mx_tools.multicompileFolder(_nwccSuiteDir, _cacheDir, [mx_tools.Tool.CLANG], ['-Iinclude'], [mx_tools.Optimization.O0], mx_tools.ProgrammingLanguage.LLVMBC, excludes=excludes))

testSuites = {
    'args' : (compileOtherTests, runArgsTests),
    'callback' : (compileOtherTests, runCallbackTests),
    'vaargs' : (compileOtherTests, runVAargsTests),
    'nwcc' : (compileNWCCSuite, runNWCCSuite),
    'assembly' : (compileInlineAssemblySuite, runInlineAssemblySuite),
    'gcc32' : (compileV32GCCSuite, runGCCSuite32),
    'llvm' : (compileLLVMSuite, runLLVMSuite),
    'gcc38' : (compileV38GCCSuite, runGCCSuite38),
    'shootout' : (compileShootoutSuite, runShootoutSuite),
    'parserTorture' : (compileParserTurtureSuite, runParserTortureSuite),
    'type' : (None, runTypeTests),
    'pipe' : (None, runPipeTests),
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
        if compileCommand is not None:
            compileCommand()

def runSuite(args=None):
    """executes all the test suites or selected ones (see -h or --help)"""
    vmArgs, otherArgs = mx_sulong.truffle_extract_VM_args(args)
    parser = argparse.ArgumentParser(description="Compiles all or selected test suites.")
    parser.add_argument('suite', nargs='*', help=' '.join(testSuites.keys()), default=testSuites.keys())
    parsedArgs = parser.parse_args(otherArgs)

    tasks = []
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

mx_subst.path_substitutions.register_no_arg('sulong_include', lambda: os.path.join(mx.suite('sulong').dir, 'include'))


class SulongTestSuite(mx.NativeProject):
    def __init__(self, suite, name, deps, workingSets, subDir, results=None, output=None, **args):
        d = os.path.join(suite.dir, subDir) # use common Makefile for all test suites
        mx.NativeProject.__init__(self, suite, name, subDir, [], deps, workingSets, results, output, d)
        self.vpath = True

    @staticmethod
    def haveDragonegg():
        if not hasattr(SulongTestSuite, '_haveDragonegg'):
            SulongTestSuite._haveDragonegg = os.path.exists(mx_sulong.dragonEggPath()) and mx_sulong.getGCC(optional=True) is not None
        return SulongTestSuite._haveDragonegg

    def getTests(self):
        if not hasattr(self, '_tests'):
            self._tests = []
            root = os.path.join(self.dir, self.name)
            for path, _, files in os.walk(root):
                for f in files:
                    absPath = os.path.join(path, f)
                    relPath = os.path.relpath(absPath, root)
                    test, ext = os.path.splitext(relPath)
                    if ext in ['.c', '.cpp']:
                        self._tests.append(test)
        return self._tests

    def getVariants(self):
        if not hasattr(self, '_variants'):
            self._variants = []
            for v in self.variants:
                if 'gcc' in v and not SulongTestSuite.haveDragonegg():
                    mx.warn('Could not find dragonegg, not building test variant "%s"' % v)
                    continue
                self._variants.append(v)
        return self._variants

    def getBuildRef(self):
        if hasattr(self, 'buildRef'):
            return self.buildRef
        else:
            return True

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        env = super(SulongTestSuite, self).getBuildEnv(replaceVar=replaceVar)
        env['VPATH'] = os.path.join(self.dir, self.name)
        env['PROJECT'] = self.name
        env['TESTS'] = ' '.join(self.getTests())
        env['VARIANTS'] = ' '.join(self.getVariants())
        env['BUILD_REF'] = '1' if self.getBuildRef() else '0'
        if SulongTestSuite.haveDragonegg():
            env['DRAGONEGG'] = mx_sulong.dragonEggPath()
            env['DRAGONEGG_GCC'] = mx_sulong.getGCC()
            env['DRAGONEGG_LLVMAS'] = mx_sulong.findLLVMProgramForDragonegg("llvm-as")
        return env

    def getResults(self, replaceVar=mx_subst.results_substitutions):
        if not self.results:
            self.results = []
            for t in self.getTests():
                if self.getBuildRef():
                    self.results.append(os.path.join(t, 'ref.out'))
                for v in self.getVariants():
                    self.results.append(os.path.join(t, v + '.bc'))
        return super(SulongTestSuite, self).getResults(replaceVar=replaceVar)
