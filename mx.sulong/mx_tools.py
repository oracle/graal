from __future__ import print_function

import fnmatch
import mx
import os
import re

import mx_sulong

class ProgrammingLanguage(object):
    class PL(object):
        def __init__(self, name, exts):
            self.name = name
            self.exts = exts

    exts = {}

    @staticmethod
    def register(name, *exts):
        lang = ProgrammingLanguage.PL(name, exts)
        setattr(ProgrammingLanguage, name, lang)
        for ext in exts:
            ProgrammingLanguage.exts[ext] = lang

    @staticmethod
    def lookup(extension):
        return ProgrammingLanguage.exts.get(extension, None)

    @staticmethod
    def lookupFile(f):
        _, ext = os.path.splitext(f)
        return ProgrammingLanguage.lookup(ext[1:])

ProgrammingLanguage.register('FORTRAN', 'f90', 'f', 'f03')
ProgrammingLanguage.register('C', 'c')
ProgrammingLanguage.register('C_PLUS_PLUS', 'cpp', 'cc', 'C')
ProgrammingLanguage.register('OBJECTIVE_C', 'm')
ProgrammingLanguage.register('LLVMIR', 'll')
ProgrammingLanguage.register('LLVMBC', 'bc')
ProgrammingLanguage.register('LLVMSU', 'su')
ProgrammingLanguage.register('EXEC', 'out')


class Optimization(object):
    class Opt(object):
        def __init__(self, name, flags):
            self.name = name
            self.flags = flags

    @staticmethod
    def register(name, *flags):
        setattr(Optimization, name, Optimization.Opt(name, list(flags)))

Optimization.register('O0')
Optimization.register('O1', '-O1')
Optimization.register('O2', '-O2')
Optimization.register('O3', '-O3')


class Tool(object):
    def supports(self, language):
        return language in self.supportedLanguages

    def runTool(self, args, errorMsg=None):
        try:
            if not mx.get_opts().verbose:
                f = open(os.devnull, 'w')
                ret = mx.run(args, out=f, err=f)
            else:
                f = None
                ret = mx.run(args)
        except SystemExit:
            ret = -1
            if errorMsg is None:
                print('\nError: Cannot run %s' % args)
            else:
                print('\nError: %s\n%s' % (errorMsg, ' '.join(args)))
        if f is not None:
            f.close()
        return ret

class ClangCompiler(Tool):
    def __init__(self, name=None, supportedLanguages=None):
        if name is None:
            self.name = 'clang'
        else:
            self.name = name

        if supportedLanguages is None:
            self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.OBJECTIVE_C]
        else:
            self.supportedLanguages = supportedLanguages

    def getTool(self, inputFile):
        inputLanguage = ProgrammingLanguage.lookupFile(inputFile)
        if inputLanguage == ProgrammingLanguage.C or inputLanguage == ProgrammingLanguage.OBJECTIVE_C:
            return 'clang'
        elif inputLanguage == ProgrammingLanguage.C_PLUS_PLUS:
            return 'clang++'
        else:
            raise Exception('Unsupported input language')

    def run(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool, ['3.2']), '-c', '-emit-llvm', '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

    def compileReferenceFile(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool, ['3.2']), '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

class ClangV38Compiler(Tool):
    def __init__(self, name=None, supportedLanguages=None):
        if name is None:
            self.name = 'clang_v38'
        else:
            self.name = name

        if supportedLanguages is None:
            self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.OBJECTIVE_C]
        else:
            self.supportedLanguages = supportedLanguages

    def getTool(self, inputFile):
        inputLanguage = ProgrammingLanguage.lookupFile(inputFile)
        if inputLanguage == ProgrammingLanguage.C or inputLanguage == ProgrammingLanguage.OBJECTIVE_C:
            return 'clang'
        elif inputLanguage == ProgrammingLanguage.C_PLUS_PLUS:
            return 'clang++'
        else:
            raise Exception('Unsupported input language')

    def run(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool, ['3.8', '3.9', '4.0']), '-c', '-emit-llvm', '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

    def compileReferenceFile(self, inputFile, outputFile, flags):
        tool = self.getTool(inputFile)
        return self.runTool([mx_sulong.findLLVMProgram(tool, ['3.8', '3.9', '4.0']), '-o', outputFile] + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

class GCCCompiler(Tool):
    def __init__(self, name=None, supportedLanguages=None):
        if name is None:
            self.name = 'gcc'
        else:
            self.name = name

        if supportedLanguages is None:
            self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.FORTRAN]
        else:
            self.supportedLanguages = supportedLanguages

        self.gcc = None
        self.gpp = None
        self.gfortran = None

    def getTool(self, inputFile, outputFile):
        inputLanguage = ProgrammingLanguage.lookupFile(inputFile)
        if inputLanguage == ProgrammingLanguage.C:
            if self.gcc is None:
                self.gcc = mx_sulong.getGCC()
            return self.gcc, ['-std=gnu99']
        elif inputLanguage == ProgrammingLanguage.C_PLUS_PLUS:
            if self.gpp is None:
                self.gpp = mx_sulong.getGPP()
            return self.gpp, []
        elif inputLanguage == ProgrammingLanguage.FORTRAN:
            if self.gfortran is None:
                self.gfortran = mx_sulong.getGFortran()
            return self.gfortran, ['-J%s' % os.path.dirname(outputFile)]
        else:
            raise Exception('Unsupported input language')

    def run(self, inputFile, outputFile, flags):
        tool, toolFlags = self.getTool(inputFile, outputFile)
        ret = self.runTool([tool, '-S', '-fplugin=' + mx_sulong.dragonEggPath(), '-fplugin-arg-dragonegg-emit-ir', '-o', '%s.tmp.ll' % outputFile] + toolFlags + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, os.path.basename(tool)))
        if ret == 0:
            ret = self.runTool([mx_sulong.findLLVMProgram('llvm-as', ['3.2']), '-o', outputFile, '%s.tmp.ll' % outputFile], errorMsg='Cannot assemble %s with llvm-as' % inputFile)
        return ret

    def compileReferenceFile(self, inputFile, outputFile, flags):
        tool, toolFlags = self.getTool(inputFile, outputFile)
        return self.runTool([tool, '-o', outputFile] + toolFlags + flags + [inputFile], errorMsg='Cannot compile %s with %s' % (inputFile, tool))

class Opt(Tool):
    def __init__(self, name, passes):
        self.name = name
        self.supportedLanguages = [ProgrammingLanguage.LLVMBC]
        self.passes = passes

    def run(self, inputFile, outputFile, flags):
        return mx.run([mx_sulong.findLLVMProgram('opt', ['3.2']), '-o', outputFile] + self.passes + [inputFile])

class OptV38(Tool):
    def __init__(self, name, passes):
        self.name = name
        self.supportedLanguages = [ProgrammingLanguage.LLVMBC]
        self.passes = passes

    def run(self, inputFile, outputFile, flags):
        return mx.run([mx_sulong.findLLVMProgram('opt', ['3.8', '3.9', '4.0']), '-o', outputFile] + self.passes + [inputFile])

Tool.CLANG = ClangCompiler()
Tool.CLANG_C = ClangCompiler('clangc', [ProgrammingLanguage.C])
Tool.CLANG_CPP = ClangCompiler('clangcpp', [ProgrammingLanguage.C_PLUS_PLUS])

Tool.GCC = GCCCompiler()
Tool.GFORTRAN = GCCCompiler('gfortran', [ProgrammingLanguage.FORTRAN])

Tool.BB_VECTORIZE = Opt('BB_VECTORIZE', ['-functionattrs', '-instcombine', '-always-inline', '-jump-threading', '-simplifycfg', '-mem2reg', '-bb-vectorize'])
Tool.MEM2REG = Opt('MEM2REG', ['-mem2reg'])

Tool.CPP_OPT = Opt('CPP_OPT', ['-lowerinvoke', '-prune-eh', '-simplifycfg'])
Tool.C_OPT = Opt('C_OPT', ['-mem2reg', '-always-inline', '-jump-threading', '-simplifycfg'])


Tool.CLANG_V38 = ClangV38Compiler('clang_v38')
Tool.CLANG_C_V38 = ClangV38Compiler('clangc_v38', [ProgrammingLanguage.C])
Tool.CLANG_CPP_V38 = ClangV38Compiler('clangcpp_v38', [ProgrammingLanguage.C_PLUS_PLUS])

Tool.BB_VECTORIZE_V38 = OptV38('BB_VECTORIZE', ['-functionattrs', '-instcombine', '-always-inline', '-jump-threading', '-simplifycfg', '-mem2reg', '-bb-vectorize'])
Tool.MEM2REG_V38 = OptV38('MEM2REG', ['-mem2reg'])

Tool.CPP_OPT_V38 = OptV38('CPP_OPT_v38', ['-lowerinvoke', '-prune-eh', '-simplifycfg'])
Tool.C_OPT_V38 = OptV38('C_OPT_v38', ['-mem2reg', '-always-inline', '-jump-threading', '-simplifycfg'])

def createOutputPath(path, inputFile, outputDir):
    base, _ = os.path.splitext(inputFile)
    outputPath = os.path.join(outputDir, os.path.relpath(base, os.path.dirname(os.path.dirname(path))))
    # ensure that there is one folder for each testfile
    outputPath = os.path.join(outputPath, os.path.basename(base))

    outputFolder = os.path.dirname(outputPath)
    if not os.path.exists(outputFolder):
        os.makedirs(outputFolder)

    return outputPath

def getOutputName(path, inputFile, outputDir, tool, optimization, target):
    outputPath = createOutputPath(path, inputFile, outputDir)
    return '%s_%s_%s.%s' % (outputPath, tool.name, optimization.name, target.exts[0])

def getReferenceName(path, inputFile, outputDir, target):
    outputPath = createOutputPath(path, inputFile, outputDir)
    return '%s.%s' % (outputPath, target.exts[0])

def isFileUpToDate(inputFile, outputFile):
    return os.path.exists(outputFile) and os.path.getmtime(inputFile) < os.path.getmtime(outputFile)

def collectExcludes(path):
    for root, _, files in os.walk(path):
        for f in files:
            if f.endswith('.exclude'):
                for line in open(os.path.join(root, f)):
                    yield line.strip()

def collectExcludePattern(path):
    return prepareMatchPattern(collectExcludes(path))

def findRecursively(path, excludes=None):
    for root, _, files in os.walk(path):
        for f in files:
            if ProgrammingLanguage.lookupFile(f) is not None:
                absFilePath = os.path.join(root, f)
                relFilePath = os.path.relpath(absFilePath, path)
                if not matches(relFilePath, excludes):
                    yield absFilePath

def prepareMatchPattern(patterns):
    return re.compile('(%s)' % '|'.join(list(fnmatch.translate(p) for p in patterns)))

def matches(path, pattern):
    return pattern is not None and pattern.match(path) != None

_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')

def multicompileFile(path, inputFile, outputDir, tools, flags, optimizations, target, optimizers=None):
    if optimizers is None:
        optimizers = []
    lang = ProgrammingLanguage.lookupFile(inputFile)
    for tool in tools:
        if tool.supports(lang):
            for optimization in optimizations:
                outputFile = getOutputName(path, inputFile, outputDir, tool, optimization, target)
                if not isFileUpToDate(inputFile, outputFile):
                    tool.run(inputFile, outputFile, _env_flags + flags + optimization.flags)
                    if os.path.exists(outputFile):
                        yield outputFile
                if os.path.exists(outputFile):
                    for optimizer in optimizers:
                        base, ext = os.path.splitext(outputFile)
                        opt_outputFile = base + '_' + optimizer.name + ext
                        if not isFileUpToDate(outputFile, opt_outputFile):
                            optimizer.run(outputFile, opt_outputFile, [])
                            if os.path.exists(opt_outputFile):
                                yield opt_outputFile

def multicompileFolder(path, outputDir, tools, flags, optimizations, target, optimizers=None, excludes=None):
    """Produces ll files for all files in given directory using the provided tool, and applies all optimizations specified by the optimizer tool"""
    for f in findRecursively(path, excludes):
        yield f, list(multicompileFile(path, f, outputDir, tools, flags, optimizations, target, optimizers=optimizers))

def multicompileRefFile(path, inputFile, outputDir, tools, flags):
    lang = ProgrammingLanguage.lookupFile(inputFile)
    for tool in tools:
        if tool.supports(lang):
            referenceFile = getReferenceName(path, inputFile, outputDir, ProgrammingLanguage.EXEC)
            if not isFileUpToDate(inputFile, referenceFile):
                tool.compileReferenceFile(inputFile, referenceFile, _env_flags + flags)
                if os.path.exists(referenceFile):
                    yield referenceFile

def multicompileRefFolder(path, outputDir, tools, flags, excludes=None):
    """Produces executables for all files in given directory using the provided tool"""
    for f in findRecursively(path, excludes):
        yield f, list(multicompileRefFile(path, f, outputDir, tools, flags))

def printProgress(iterator):
    for x in iterator:
        if len(x) < 2 or len(x[1]) > 0:
            print('.', end='')
    print(' done')
