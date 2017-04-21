
import re
import mx, mx_benchmark, mx_sulong
import os
from os.path import join, exists
from mx_benchmark import VmRegistry, java_vm_registry, Vm, GuestVm, VmBenchmarkSuite


def _benchmarksDirectory():
    return join(os.path.abspath(join(mx.suite('sulong').dir, os.pardir)), 'sulong-benchmarks')

_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')

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
        return [f for f in os.listdir(benchDir) if os.path.isdir(join(benchDir, f)) and os.path.isfile(join(join(benchDir, f), 'Makefile'))]

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

    # def before(self, bmSuiteArgs):
    #     self.currentDir = os.getcwd()
    #     os.chdir(_benchmarksDirectory())

    # def after(self, bmSuiteArgs):
    #     os.chdir(self.currentDir)

    def createCommandLineArgs(self, benchmarks, runArgs):
        if len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark csuite:<benchmark-name>) or all the benchmarks (mx benchmark csuite:*)")
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
        return self.compiler_name()

    def cpp_compiler(self):
        return self.compiler_name() + "++"

    def run(self, cwd, args):
        # save current Directory
        self.currentDir = os.getcwd()
        os.chdir(_benchmarksDirectory())

        f = open(os.devnull, 'w')
        benchmarkDir = args[0]

        # enter benchmark dir
        os.chdir(benchmarkDir)

        # create directory for executable of this vm
        if not os.path.exists(self.name()):
            os.makedirs(self.name())
        os.chdir(self.name())

        if os.path.exists('bench'):
            os.remove('bench')

        env = os.environ.copy()
        env['CFLAGS'] = ' '.join(self.options + _env_flags + ['-lm', '-lgmp'])
        env['CC'] = 'clang'
        env['VPATH'] = '..'
        cmdline = ['make', '-f', '../Makefile']

        print env
        print os.getcwd()
        print cmdline

        mx.run(cmdline, out=f, err=f, env=env)

        myStdOut = mx.OutputCapture()
        retCode = mx.run(['./bench'], out=myStdOut, err=f)
        print myStdOut.data

        # reset current Directory
        os.chdir(self.currentDir)

        return [retCode, myStdOut.data]


class GccVm(GccLikeVm):
    def __init__(self, config_name, options):
        super(GccVm, self).__init__(config_name, options)

    def name(self):
        return "gcc"

    def compiler_name(self):
        return "gcc"


class ClangVm(GccLikeVm):
    def __init__(self, config_name, options):
        super(ClangVm, self).__init__(config_name, options)

    def name(self):
        return "clang"

    def compiler_name(self):
        mx_sulong.ensureLLVMBinariesExist()
        return mx_sulong.findLLVMProgram('clang')


class SulongVm(GuestVm):
    def config_name(self):
        return "default"

    def name(self):
        return "sulong"

    def run(self, cwd, args):
        # save current Directory
        self.currentDir = os.getcwd()
        os.chdir(_benchmarksDirectory())

        f = open(os.devnull, 'w')

        mx_sulong.ensureLLVMBinariesExist()
        benchmarkDir = args[0]

        # enter benchmark dir
        os.chdir(benchmarkDir)

        # create directory for executable of this vm
        if not os.path.exists(self.name()):
            os.makedirs(self.name())
        os.chdir(self.name())

        if os.path.exists('bench'):
            os.remove('bench')

        env = os.environ.copy()
        env['CFLAGS'] = ' '.join(_env_flags + ['-lm', '-lgmp'])
        env['LLVM_COMPILER'] = 'clang'
        env['CC'] = 'wllvm'
        env['VPATH'] = '..'

        cmdline = ['make', '-f', '../Makefile']

        mx.run(cmdline, out=f, err=f, env=env)
        mx.run(['extract-bc', 'bench'], out=f, err=f)
        mx_sulong.opt(['-o', 'bench.bc', 'bench.bc'] + mx_sulong.getStandardLLVMOptFlags(), out=f, err=f)

        suTruffleOptions = [
            '-Dgraal.TruffleBackgroundCompilation=false',
            '-Dgraal.TruffleTimeThreshold=1000000',
            '-Dgraal.TruffleInliningMaxCallerSize=10000',
            '-Dgraal.TruffleCompilationExceptionsAreFatal=true',
        ]
        sulongCmdLine = suTruffleOptions + [mx_sulong.getSearchPathOption()] + mx_sulong.getBitcodeLibrariesOption() + mx_sulong.getClasspathOptions() + ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.LLVM"] + ['bench.bc']
        result = self.host_vm().run(cwd, sulongCmdLine + args)

        # reset current Directory
        os.chdir(self.currentDir)

        return result

    def hosting_registry(self):
        return java_vm_registry

_suite = mx.suite("sulong")

native_vm_registry = VmRegistry("Native", known_host_registries=[java_vm_registry])
native_vm_registry.add_vm(GccVm('O0', ['-O0']), _suite)
native_vm_registry.add_vm(ClangVm('O0', ['-O0']), _suite)
native_vm_registry.add_vm(GccVm('O1', ['-O1']), _suite)
native_vm_registry.add_vm(ClangVm('O1', ['-O1']), _suite)
native_vm_registry.add_vm(GccVm('O2', ['-O2']), _suite)
native_vm_registry.add_vm(ClangVm('O2', ['-O2']), _suite)
native_vm_registry.add_vm(GccVm('O3', ['-O3']), _suite)
native_vm_registry.add_vm(ClangVm('O3', ['-O3']), _suite)
native_vm_registry.add_vm(SulongVm(), _suite, 10)
