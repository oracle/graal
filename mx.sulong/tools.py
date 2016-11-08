import mx
import os

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

Optimization.register('NONE')
Optimization.register('BB_VECTORIZE')
Optimization.register('O1', '-O1')
Optimization.register('O2', '-O2')
Optimization.register('O3', '-O3')


class Tool(object):
    def supports(self, language):
        return language in self.supportedLanguages

class ClangCompiler(Tool):
    def __init__(self):
        self.name = 'clang'
        self.supportedLanguages = [ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.OBJECTIVE_C]

    def run(self, inputFile, outputFile, flags):
        try:
            f = open(os.devnull, 'w')
            return mx.run([mx_sulong.findLLVMProgram('clang'), '-S', '-emit-llvm', '-o', outputFile] + flags + [inputFile], out=f, err=f)
        except SystemExit:
            print 'Cannot compile %s with clang' % (inputFile)
        return -1

    def compileReferenceFile(self, inputFile, outputFile, flags):
        try:
            f = open(os.devnull, 'w')
            return mx.run([mx_sulong.findLLVMProgram('clang'), '-o', outputFile] + flags + [inputFile], out=f, err=f)
        except SystemExit:
            print 'Cannot compile %s with clang' % (inputFile)
        return -1

class GCCCompiler(Tool):
    def __init__(self):
        self.name = 'gcc'
        self.supportedLanguages = [ProgrammingLanguage.C]

    def run(self, inputFile, outputFile, flags):
        try:
            f = open(os.devnull, 'w')
            return mx.run([mx_sulong.findLLVMProgram('gcc'), '-std=gnu99', '-S', '-fplugin=' + mx_sulong._dragonEggPath, '-fplugin-arg-dragonegg-emit-ir', '-o', outputFile] + flags + [inputFile], out=f, err=f)
        except SystemExit:
            print 'Cannot compile %s with gcc' % (inputFile)
        return -1

class GPPCompiler(Tool):
    def __init__(self):
        self.name = 'gpp'
        self.supportedLanguages = [ProgrammingLanguage.C_PLUS_PLUS]

    def run(self, inputFile, outputFile, flags):
        try:
            f = open(os.devnull, 'w')
            return mx.run([mx_sulong.findLLVMProgram('g++'), '-S', '-fplugin=' + mx_sulong._dragonEggPath, '-fplugin-arg-dragonegg-emit-ir', '-o', outputFile] + flags + [inputFile], out=f, err=f)
        except SystemExit:
            print 'Cannot compile %s with g++' % (inputFile)
        return -1

class GFORTRANCompiler(Tool):
    def __init__(self):
        self.name = 'gfortran'
        self.supportedLanguages = [ProgrammingLanguage.C_PLUS_PLUS]

    def run(self, inputFile, outputFile, flags):
        try:
            f = open(os.devnull, 'w')
            return mx.run([mx_sulong.findLLVMProgram('gfortran'), '-S', '-fplugin=' + mx_sulong._dragonEggPath, '-fplugin-arg-dragonegg-emit-ir', '-o', outputFile] + flags + [inputFile], out=f, err=f)
        except SystemExit:
            print 'Cannot compile %s with gfortran' % (inputFile)
        return -1

class Opt(Tool):
    def __init__(self, name, passes):
        self.name = name
        self.supportedLanguages = [ProgrammingLanguage.LLVMIR]
        self.passes = passes

    def supports(self, language):
        return language in self.supportedLanguages

    def run(self, inputFile, outputFile, flags):
        return mx.run([mx_sulong.findLLVMProgram('opt')] + ['-S'] + self.passes +  [inputFile] + ['-o', outputFile])

Tool.CLANG = ClangCompiler()
Tool.GCC = GCCCompiler()
Tool.GFORTRAN = GFORTRANCompiler()
Tool.BB_VECTORIZE = Opt('BB_VECTORIZE', ['-functionattrs', '-instcombine', '-always-inline', '-jump-threading', '-simplifycfg', '-mem2reg', '-scalarrepl', '-bb-vectorize'])


class Runtime(object):
    def supports(self, language):
        return language in self.supportedLanguages

class LLIRuntime(Runtime):
    def __init__(self):
        self.name = 'lli'
        self.supportedLanguages = [ProgrammingLanguage.LLVMIR, ProgrammingLanguage.LLVMBC]

    def run(self, f, args, vmArgs=None, out=None):
        return mx.run([mx_sulong.findLLVMProgram('lli'), f] + args, nonZeroIsFatal=False)

Runtime.LLI = LLIRuntime()


def getFileExtension(f):
    _, ext = os.path.splitext(f)
    return ext[1:]

def getOutputName(inputFile, outputDir, tool, optimization, target):
    base, _ = os.path.splitext(inputFile)
    outputPath = os.path.join(outputDir, os.path.relpath(base))
    # ensure that there is one folder for each testfile
    outputPath = os.path.join(outputPath, os.path.basename(base))

    outputDir = os.path.dirname(outputPath)
    if not os.path.exists(outputDir):
        os.makedirs(outputDir)
    return '%s_%s_%s.%s' % (outputPath, tool.name, optimization.name, target.exts[0])

def getReferenceName(inputFile, outputDir, target):
    base, _ = os.path.splitext(inputFile)
    outputPath = os.path.join(outputDir, os.path.relpath(base))
    # ensure that there is one folder for each testfile
    outputPath = os.path.join(outputPath, os.path.basename(base))

    outputDir = os.path.dirname(outputPath)
    if not os.path.exists(outputDir):
        os.makedirs(outputDir)
    return '%s.%s' % (outputPath, target.exts[0])

def multicompileFile(inputFile, outputDir, tools, flags, optimizations, target, optimizerTools):
    base, ext = os.path.splitext(inputFile)
    lang = ProgrammingLanguage.lookup(getFileExtension(inputFile))
    for tool in tools:
        if tool.supports(lang):
            for optimization in optimizations:
                outputFile = getOutputName(inputFile, outputDir, tool, optimization, target)
                if not os.path.exists(outputFile) or os.path.getmtime(inputFile) >= os.path.getmtime(outputFile):
                    tool.run(inputFile, outputFile, flags + optimization.flags)
                    if os.path.exists(outputFile):
                        for optimizerTool in optimizerTools:
                            base, ext = os.path.splitext(outputFile)
                            opt_outputFile = base + '_' + optimizerTool.name + ext
                            optimizerTool.run(outputFile, opt_outputFile, [])

def multicompileFolder(path, outputDir, tools, flags, optimizations, target, optimizerTools):
    """Produces ll files for all files in given directory using the provided tool, and applies all optimizations specified by the optimizer tool"""
    for root, _, files in os.walk(path):
        for f in files:
            _, ext = os.path.splitext(f)
            if ProgrammingLanguage.lookup(ext[1:]) is not None:
                multicompileFile(os.path.join(root, f), outputDir, tools, flags, optimizations, target, optimizerTools)

def multicompileRefFile(inputFile, outputDir, tools, flags):
    lang = ProgrammingLanguage.lookup(getFileExtension(inputFile))
    for tool in tools:
        if tool.supports(lang):
            referenceFile = getReferenceName(inputFile, outputDir, ProgrammingLanguage.EXEC)
            tool.compileReferenceFile(inputFile, referenceFile, flags)

def multicompileRefFolder(path, outputDir, tools, flags):
    """Produces executables for all files in given directory using the provided tool"""
    for root, _, files in os.walk(path):
        for f in files:
            _, ext = os.path.splitext(f)
            if ProgrammingLanguage.lookup(ext[1:]) is not None:
                multicompileRefFile(os.path.join(root, f), outputDir, tools, flags)
