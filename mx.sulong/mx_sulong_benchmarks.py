
import re
import mx, mx_benchmark, mx_sulong
import os
from os.path import join
from mx_benchmark import JavaBenchmarkSuite


def _benchmarksDirectory():
    return join(os.path.abspath(join(mx.suite('sulong').dir, os.pardir)), 'sulong-benchmarks')

class SulongBenchmarkSuite(JavaBenchmarkSuite):
    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'sulong'

    def name(self):
        return 'csuite'

    def benchmarkList(self, bmSuiteArgs):
        benchDir = _benchmarksDirectory()
        return [f for f in os.listdir(benchDir) if f.endswith('.c') and os.path.isfile(join(benchDir, f))]

    def getSulongEngine(self, bmSuiteArgs):
        return self.parseCommandLineArgs(bmSuiteArgs)[0][0]

    def getNativeArgs(self, bmSuiteArgs):
        return self.parseCommandLineArgs(bmSuiteArgs)[1]

    def parseCommandLineArgs(self, bmSuiteArgs):
        vmArgs, runArgs = mx_benchmark.splitArgs(bmSuiteArgs, "--")

        mx.logv('vmArgs: %s' % str(vmArgs))
        mx.logv('runArgs: %s' % str(runArgs))

        return vmArgs, runArgs

    def benchHigherScoreRegex(self):
        return r'^(### )?(?P<benchmark>[a-zA-Z0-9\.\-_]+): +(?P<score>[0-9]+(?:\.[0-9]+)?)'

    def failurePatterns(self):
        return [
            re.compile(r'error:'),
            re.compile(r'Exception')
        ]

    def successPatterns(self):
        return [re.compile(r'^(### )?([a-zA-Z0-9\.\-_]+): +([0-9]+(?:\.[0-9]+)?)', re.MULTILINE)]

    def configName(self, bmSuiteArgs):
        if self.getSulongEngine(bmSuiteArgs) == '--jvm':
            if mx.suite('graal-enterprise', fatalIfMissing=False):
                return 'enterprise'
            elif mx.suite('graal-core', fatalIfMissing=False):
                return 'core'
            else:
                return 'default'
        else:
            return 'native'


    def host_vm_tuple(self, bmSuiteArgs):
        if self.getSulongEngine(bmSuiteArgs) == '--jvm':
            if mx.suite('graal-enterprise', fatalIfMissing=False):
                hostVm = 'jvmci'
                hostVmConfigPrefix = 'graal-enterprise'
            elif mx.suite('graal-core', fatalIfMissing=False):
                hostVm = 'jvmci'
                hostVmConfigPrefix = 'graal-core'
            else:
                hostVm = 'server'
                hostVmConfigPrefix = 'default'
        elif self.getSulongEngine(bmSuiteArgs) == '--clang':
            hostVm = 'clang'
            hostVmConfigPrefix = '_'.join(self.getNativeArgs(bmSuiteArgs))
        elif self.getSulongEngine(bmSuiteArgs) == '--gcc':
            hostVm = 'gcc'
            hostVmConfigPrefix = '_'.join(self.getNativeArgs(bmSuiteArgs))
        else:
            mx.abort('unknown engine')
        return (hostVm, hostVmConfigPrefix)

    def guest_vm_tuple(self, bmSuiteArgs):
        if self.getSulongEngine(bmSuiteArgs) == '--jvm':
            return ('sulong', 'default')
        else:
            return ('none', 'none')

    def rules(self, out, benchmarks, bmSuiteArgs):
        host_vm, host_vm_config = self.host_vm_tuple(bmSuiteArgs)
        guest_vm, guest_vm_config = self.guest_vm_tuple(bmSuiteArgs)
        return [
          mx_benchmark.StdOutRule(self.benchHigherScoreRegex(), {
                "vm": "sulong",
                "benchmark": ("<benchmark>", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.value": ("<score>", float),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "config.name": self.configName(bmSuiteArgs),
                "host-vm": host_vm,
                "host-vm-config": host_vm_config,
                "guest-vm": guest_vm,
                "guest-vm-config": guest_vm_config,
          }),
        ]

    def before(self, bmSuiteArgs):
        self.currentDir = os.getcwd()
        os.chdir(_benchmarksDirectory())

    def after(self, bmSuiteArgs):
        os.chdir(self.currentDir)

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        if self.getSulongEngine(bmSuiteArgs) == '--jvm':
            return self.runSulong(benchmarks, bmSuiteArgs)
        elif self.getSulongEngine(bmSuiteArgs) == '--clang':
            return self.runCLANG(benchmarks, bmSuiteArgs)
        elif self.getSulongEngine(bmSuiteArgs) == '--gcc':
            return self.runGCC(benchmarks, bmSuiteArgs)
        else:
            mx.abort('unknown engine')

    def runSulong(self, benchmarks, bmSuiteArgs):
        return JavaBenchmarkSuite.runAndReturnStdOut(self, benchmarks, bmSuiteArgs)

    def runGCC(self, benchmarks, bmSuiteArgs):
        inputFile = benchmarks[0]
        _, ext = os.path.splitext(benchmarks[0])
        f = open(os.devnull, 'w')
        if ext == '.c':
            mx.run(['gcc', '-std=gnu99'] + ['-lm', '-lgmp'] + self.getNativeArgs(bmSuiteArgs) + [inputFile], out=f, err=f)
        elif ext == '.cpp':
            mx.run(['g++'] + ['-lm', '-lgmp'] + self.getNativeArgs(bmSuiteArgs) + [inputFile], out=f, err=f)
        else:
            exit(ext + " is not supported!")

        myStdOut = mx.OutputCapture()
        retCode = mx.run(['./a.out'], out=myStdOut, err=f)
        return [retCode, myStdOut.data]

    def runCLANG(self, benchmarks, bmSuiteArgs):
        print benchmarks
        inputFile = benchmarks[0]
        _, ext = os.path.splitext(benchmarks[0])
        f = open(os.devnull, 'w')
        if ext == '.c':
            mx.run(['clang'] + ['-lm', '-lgmp'] + self.getNativeArgs(bmSuiteArgs)   + [inputFile], out=f, err=f)
        elif ext == '.cpp':
            mx.run(['clang++'] + ['-lm', '-lgmp'] + self.getNativeArgs(bmSuiteArgs) + [inputFile], out=f, err=f)
        else:
            exit(ext + " is not supported!")

        myStdOut = mx.OutputCapture()
        retCode = mx.run(['./a.out'], out=myStdOut, err=f)
        print myStdOut.data
        return [retCode, myStdOut.data]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        f = open(os.devnull, 'w')

        mx_sulong.ensureLLVMBinariesExist()
        inputFile = benchmarks[0]
        outputFile = 'test.bc'
        mx_sulong.compileWithClangOpt(inputFile, outputFile, out=f, err=f)

        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        sulongCmdLine = [mx_sulong.getSearchPathOption()] + mx_sulong.getBitcodeLibrariesOption() + mx_sulong.getClasspathOptions() + ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.LLVM"] + [outputFile]
        cmdLine = vmArgs + sulongCmdLine + runArgs
        return cmdLine
