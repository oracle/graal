
import re
import mx, mx_benchmark, mx_sulong
import os
from os.path import join, exists
from mx_benchmark import VmRegistry, java_vm_registry, Vm, GuestVm, VmBenchmarkSuite


def _benchmarksDirectory():
    return join(os.path.abspath(join(mx.suite('sulong').dir, os.pardir)), 'sulong-benchmarks')


class SulongBenchmarkSuite(VmBenchmarkSuite):
    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'sulong'

    def name(self):
        return 'csuite'

    def benchmarkList(self, bmSuiteArgs):
        benchDir = _benchmarksDirectory()
        if not exists(benchDir):
            mx.abort('Benchmarks directory {} is missing'.format(benchDir))
        return [f for f in os.listdir(benchDir) if f.endswith('.c') and os.path.isfile(join(benchDir, f))]

    def benchHigherScoreRegex(self):
        return r'^(### )?(?P<benchmark>[a-zA-Z0-9\.\-_]+): +(?P<score>[0-9]+(?:\.[0-9]+)?)'

    def failurePatterns(self):
        return [
            re.compile(r'error:'),
            re.compile(r'Exception')
        ]

    def successPatterns(self):
        return [re.compile(r'^(### )?([a-zA-Z0-9\.\-_]+): +([0-9]+(?:\.[0-9]+)?)', re.MULTILINE)]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(self.benchHigherScoreRegex(), {
                "benchmark": ("<benchmark>", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.value": ("<score>", float),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
            }),
        ]

    def before(self, bmSuiteArgs):
        self.currentDir = os.getcwd()
        os.chdir(_benchmarksDirectory())

    def after(self, bmSuiteArgs):
        os.chdir(self.currentDir)

    def createCommandLineArgs(self, benchmarks, runArgs):
        if len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark sulong:<benchmark-name>) or all the benchmarks (mx benchmark sulong:*)")
        return [benchmarks[0]] + runArgs

    def get_vm_registry(self):
        return native_vm_registry


class GccLikeVm(Vm):
    def __init__(self, config_name, options):
        self._config_name = config_name
        self.options = options

    def config_name(self):
        return self._config_name

    def c_compiler(self):
        return self.name()

    def cpp_compiler(self):
        return self.name() + "++"

    def run(self, cwd, args):
        inputFile = args[0]
        _, ext = os.path.splitext(inputFile)
        f = open(os.devnull, 'w')
        if ext == '.c':
            mx.run([self.c_compiler()] + self.options + ['-lm', '-lgmp'] + [inputFile], out=f, err=f)
        elif ext == '.cpp':
            mx.run([self.cpp_compiler()] + self.options + ['-lm', '-lgmp'] + [inputFile], out=f, err=f)
        else:
            exit(ext + " is not supported!")

        myStdOut = mx.OutputCapture()
        retCode = mx.run(['./a.out'], out=myStdOut, err=f)
        return [retCode, myStdOut.data]


class GccVm(GccLikeVm):
    def __init__(self, config_name, options):
        super(GccVm, self).__init__(config_name, options)

    def name(self):
        return "gcc"


class ClangVm(GccLikeVm):
    def __init__(self, config_name, options):
        super(ClangVm, self).__init__(config_name, options)

    def name(self):
        return "clang"


class SulongVm(GuestVm):
    def config_name(self):
        return "default"

    def name(self):
        return "sulong"

    def run(self, cwd, args):
        f = open(os.devnull, 'w')

        mx_sulong.ensureLLVMBinariesExist()
        inputFile = args[0]
        outputFile = 'test.bc'
        mx_sulong.compileWithClangOpt(inputFile, outputFile, out=f, err=f)

        sulongCmdLine = [mx_sulong.getSearchPathOption()] + mx_sulong.getBitcodeLibrariesOption() + mx_sulong.getClasspathOptions() + ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.LLVM"] + [outputFile]
        return self.host_vm().run(cwd, sulongCmdLine + args)

    def hosting_registry(self):
        return java_vm_registry

_suite = mx.suite("sulong")

native_vm_registry = VmRegistry("Native", known_host_registries=[java_vm_registry])
native_vm_registry.add_vm(GccVm('O3', ['-O3']), _suite)
native_vm_registry.add_vm(ClangVm('O3', ['-O3']), _suite)
native_vm_registry.add_vm(SulongVm(), _suite, 10)
