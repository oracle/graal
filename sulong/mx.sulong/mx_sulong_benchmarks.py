#
# Copyright (c) 2016, 2022, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
import re
import shutil
import time

import mx, mx_benchmark, mx_sulong
import os
from os.path import join, exists

import mx_subst
from mx_benchmark import VmRegistry, java_vm_registry, Vm, GuestVm, VmBenchmarkSuite


def _benchmarksDirectory():
    return join(os.path.abspath(join(mx.suite('sulong').dir, os.pardir, os.pardir)), 'sulong-benchmarks')

_env_flags = []
if 'CPPFLAGS' in os.environ:
    _env_flags = os.environ['CPPFLAGS'].split(' ')

class SulongBenchmarkRule(mx_benchmark.StdOutRule):
    def __init__(self, pattern, replacement):
        super(SulongBenchmarkRule, self).__init__(
            pattern=pattern,
            replacement=replacement)

    def parseResults(self, text):
        def _parse_results_gen():
            for d in super(SulongBenchmarkRule, self).parseResults(text):
                line = d.pop('line')
                for iteration, value in enumerate(line.split(',')):
                    r = d.copy()
                    r['score'] = value.strip()
                    r['iteration'] = str(iteration)
                    yield r
        return (x for x in _parse_results_gen())

class PolybenchExcludeWarmupRule(mx_benchmark.StdOutRule):
    """Rule that behaves as the StdOutRule, but skips input until a certain pattern."""

    def __init__(self, *args, **kwargs):
        self.startPattern = re.compile(kwargs.pop('startPattern'))
        super(PolybenchExcludeWarmupRule, self).__init__(*args, **kwargs)

    def parse(self, text):
        m = self.startPattern.search(text)
        if m:
            return super(PolybenchExcludeWarmupRule, self).parse(text[m.end()+1:])
        else:
            return []

class SulongBenchmarkSuite(VmBenchmarkSuite):
    def __init__(self, use_polybench, *args, **kwargs):
        super(SulongBenchmarkSuite, self).__init__(*args, **kwargs)
        self.bench_to_exec = {}
        self.use_polybench = use_polybench

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'sulong'

    def name(self):
        return 'csuite-polybench' if self.use_polybench else 'csuite'

    def run(self, benchnames, bmSuiteArgs):
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        assert isinstance(vm, CExecutionEnvironmentMixin)

        # compile benchmarks

        # save current Directory
        current_dir = os.getcwd()
        for bench in benchnames:
            try:
                # benchmark output dir
                bench_out_dir = self.workingDirectory(benchnames, bmSuiteArgs)
                # create directory for executable of this vm
                if os.path.exists(bench_out_dir):
                    shutil.rmtree(bench_out_dir)
                os.makedirs(bench_out_dir)
                os.chdir(bench_out_dir)

                env = os.environ.copy()
                env['VPATH'] = '..'
                # prepare_env adds CC, CXX, CFLAGS, etc... and we copy the environment to avoid modifying the default one.
                env = vm.prepare_env(env)

                outName = vm.out_file()
                if self.use_polybench:
                    env['POLYBENCH'] = 'y'
                    outName += '.so'
                out = os.path.join(bench_out_dir, outName)
                cmdline = ['make', '-f', '../Makefile', out]
                if mx._opts.verbose:
                    # The Makefiles should have logic to disable the @ sign
                    # so that all executed commands are visible.
                    cmdline += ["MX_VERBOSE=y"]
                mx.run(cmdline, env=env)
                self.bench_to_exec[bench] = out
            finally:
                # reset current Directory
                os.chdir(current_dir)

        return super(SulongBenchmarkSuite, self).run(benchnames, bmSuiteArgs)

    def benchmarkList(self, bmSuiteArgs):
        benchDir = _benchmarksDirectory()
        if not exists(benchDir):
            mx.abort('Benchmarks directory {} is missing'.format(benchDir))
        return [f for f in os.listdir(benchDir) if os.path.isdir(join(benchDir, f)) and os.path.isfile(join(join(benchDir, f), 'Makefile'))]

    def failurePatterns(self):
        return [
            re.compile(r'error:'),
            re.compile(r'Exception')
        ]

    def successPatterns(self):
        return [re.compile(r'^(### )?([a-zA-Z0-9\.\-_]+): +([0-9]+(?:\.[0-9]+)?)', re.MULTILINE)]

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        # This comes into play when benchmarking with AOT auxiliary images. An AOT benchmark must
        # be run twice: the first run involves parsing only with no run. Upon closing the context,
        # the auxiliary image is saved and then loaded when the second benchmark is run. The no-run
        # preparatory benchmark is run using the llimul launcher and passing --multi-context-runs=0.
        # We can capture this argument here and instruct the benchmark infrastructure to ignore
        # the output of this benchmark.
        if self.use_polybench:
            if any(a == "store-aux-engine-cache" for a in bmSuiteArgs):
                return [re.compile(r'.*', re.MULTILINE)]
        else:
            if any(a == "--multi-context-runs=0" for a in bmSuiteArgs):
                return [re.compile(r'.*', re.MULTILINE)]
        return []

    def flakySuccessPatterns(self):
        # bzip2 is known to have a compiler error during OSR compilation, which would trigger failurePatterns
        return [re.compile(r'bzip2')]  # GR-38646

    def rules(self, out, benchmarks, bmSuiteArgs):
        if self.use_polybench:
            return self.polybenchRules(out, benchmarks, bmSuiteArgs)
        else:
            return self.legacyRules(out, benchmarks, bmSuiteArgs)

    def legacyRules(self, out, benchmarks, bmSuiteArgs):
        return [
            SulongBenchmarkRule(
		r'^run (?P<run>[\d]+) first [\d]+ warmup iterations (?P<benchmark>[\S]+):(?P<line>([ ,]+(?:\d+(?:\.\d+)?))+)',
		{
                "benchmark": ("<benchmark>", str),
                "metric.name": "warmup",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("<iteration>", int),
                "metric.fork-number": ("<run>", int),
            }),
            SulongBenchmarkRule(
		r'^run (?P<run>[\d]+) last [\d]+ iterations (?P<benchmark>[\S]+):(?P<line>([ ,]+(?:\d+(?:\.\d+)?))+)',
		{
                "benchmark": ("<benchmark>", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("<iteration>", int),
                "metric.fork-number": ("<run>", int),
            }),
            mx_benchmark.StdOutRule(r'^run (?P<run>[\d]+) Pure-startup \(microseconds\) (?P<benchmark>[\S]+): (?P<score>\d+)', {
                "benchmark": ("<benchmark>", str),
                "metric.name": "pure-startup",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("0", int),
            }),
            mx_benchmark.StdOutRule(r'^run (?P<run>[\d]+) Startup of (?P<benchmark>[\S]+): (?P<score>\d+)', {
                "benchmark": ("<benchmark>", str),
                "metric.name": "startup",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("0", int),
            }),
            mx_benchmark.StdOutRule(r'^run (?P<run>[\d]+) Early-warmup of (?P<benchmark>[\S]+): (?P<score>\d+)', {
                "benchmark": ("<benchmark>", str),
                "metric.name": "early-warmup",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("0", int),
            }),
            mx_benchmark.StdOutRule(r'^run (?P<run>[\d]+) Late-warmup of (?P<benchmark>[\S]+): (?P<score>\d+)', {
                "benchmark": ("<benchmark>", str),
                "metric.name": "late-warmup",
                "metric.type": "numeric",
                "metric.value": ("<score>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("0", int),
            }),
        ]

    def polybenchRules(self, output, benchmarks, bmSuiteArgs):
        rules = [
           mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)", {
               "bench-suite": "csuite",
               "benchmark": benchmarks[0],
               "metric.better": "lower",
               "metric.name": "warmup",
               "metric.unit": ("<unit>", str),
               "metric.value": ("<value>", float),
               "metric.type": "numeric",
               "metric.score-function": "id",
               "metric.iteration": ("$iteration", int),
           }),
           PolybenchExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
               "bench-suite": "csuite",
               "benchmark": benchmarks[0],
               "metric.better": "lower",
               "metric.name": "time",
               "metric.unit": ("<unit>", str),
               "metric.value": ("<value>", float),
               "metric.type": "numeric",
               "metric.score-function": "id",
               "metric.iteration": ("<iteration>", int),
           }, startPattern=r"::: Running :::")
        ]
        return rules

    def _get_metric_name(self, bmSuiteArgs):
        metric = None
        for arg in bmSuiteArgs:
            if arg.startswith("--metric="):
                metric = arg[len("--metric="):]
                break
        if metric == "compilation-time":
            return "compile-time"
        elif metric == "partial-evaluation-time":
            return "pe-time"
        elif metric == "one-shot":
            return "one-shot"
        else:
            return "time"

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        if len(benchmarks) != 1:
            mx.abort(
                "Please run a specific benchmark (mx benchmark csuite:<benchmark-name>) or all the benchmarks (mx benchmark csuite:*)")
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        assert isinstance(vm, CExecutionEnvironmentMixin)

        if self.use_polybench and (any(a == "store-aux-engine-cache" for a in bmSuiteArgs) or any(a == "load-aux-engine-cache" for a in bmSuiteArgs)):
            # When storing or loading an aux engine cache, the working directory must be the same (the cache for the source is selected by its URL)
            return join(_benchmarksDirectory(), benchmarks[0], 'aux-engine-cache')
        else:
            return join(_benchmarksDirectory(), benchmarks[0], vm.bin_dir())

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark csuite:<benchmark-name>) or all the benchmarks (mx benchmark csuite:*)")
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        assert isinstance(vm, CExecutionEnvironmentMixin)
        bmSuiteArgs = [a.replace("${benchmark}", benchmarks[0]) for a in vm.templateOptions() + bmSuiteArgs]
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        try:
            if not self.use_polybench:
                runArgs += ['--time', str(int(time.clock_gettime(time.CLOCK_REALTIME) * 1000000))]
        except:
            # We can end up here in case the python version we're running on doesn't have clock_gettime or CLOCK_REALTIME.
            pass
        return vmArgs + [self.bench_to_exec[benchmarks[0]]] + runArgs

    def get_vm_registry(self):
        return native_polybench_vm_registry if self.use_polybench else native_vm_registry


class CExecutionEnvironmentMixin(object):

    def out_file(self):
        return 'bench'

    def bin_dir(self):
        return '{}-{}'.format(self.name(), self.config_name())

    def prepare_env(self, env):
        return env

    def templateOptions(self):
        return []

def add_run_numbers(out):
    # Prepending the summary lines by the run number
    new_result = ""

    # "forknums"
    first_20_warmup_iters_runs = 0
    last_10_iters_runs = 0
    pure_startup_runs = 0
    startup_runs = 0
    early_warmup_runs = 0
    late_warmup_runs = 0

    def make_runs_line(runs, line):
        return "run " + str(runs) + " " + line + "\n"

    for line in out.splitlines():
        if line.startswith("first"):
            new_result += make_runs_line(first_20_warmup_iters_runs, line)
            first_20_warmup_iters_runs += 1
            continue
        if line.startswith("last"):
            new_result += make_runs_line(last_10_iters_runs, line)
            last_10_iters_runs += 1
            continue
        if line.startswith("Pure-startup"):
            new_result += make_runs_line(pure_startup_runs, line)
            pure_startup_runs += 1
            continue
        if line.startswith("Startup"):
            new_result += make_runs_line(startup_runs, line)
            startup_runs += 1
            continue
        if line.startswith("Early-warmup"):
            new_result += make_runs_line(early_warmup_runs, line)
            early_warmup_runs += 1
            continue
        if line.startswith("Late-warmup"):
            new_result += make_runs_line(late_warmup_runs, line)
            late_warmup_runs += 1
            continue

        new_result += line + "\n"

    return new_result


class GccLikeVm(CExecutionEnvironmentMixin, Vm):
    def __init__(self, config_name, options):
        super(GccLikeVm, self).__init__()
        self._config_name = config_name
        self.options = options

    def config_name(self):
        return self._config_name

    def c_compiler(self):
        return self.compiler_name()

    def cpp_compiler(self):
        return self.compiler_name() + "++"

    def compiler_name(self):
        mx.nyi('compiler_name', self)

    def c_compiler_exe(self):
        mx.nyi('c_compiler_exe', self)

    def cxx_compiler_exe(self):
        mx.nyi('cxx_compiler_exe', self)

    def run(self, cwd, args):
        myStdOut = mx.OutputCapture()
        retCode = mx.run(args, out=mx.TeeOutputCapture(myStdOut), cwd=cwd)
        return [retCode, add_run_numbers(myStdOut.data)]

    def prepare_env(self, env):
        env['CFLAGS'] = ' '.join(self.options + _env_flags)
        env['CC'] = self.c_compiler_exe() # pylint: disable=assignment-from-no-return
        env['CXX'] = self.cxx_compiler_exe()  # pylint: disable=assignment-from-no-return
        return env


class GccVm(GccLikeVm):
    def name(self):
        return "gcc"

    def compiler_name(self):
        return "gcc"

    def c_compiler_exe(self):
        return "gcc"

    def cxx_compiler_exe(self):
        return "g++"


class ClangVm(GccLikeVm):
    def name(self):
        return "clang"

    def compiler_name(self):
        return "clang"

    def c_compiler_exe(self):
        return os.path.join(mx.distribution("LLVM_TOOLCHAIN").get_output(), "bin", "clang")

    def cxx_compiler_exe(self):
        return os.path.join(mx.distribution("LLVM_TOOLCHAIN").get_output(), "bin", "clang++")

    def prepare_env(self, env):
        super(ClangVm, self).prepare_env(env)
        env["CXXFLAGS"] = env.get("CXXFLAGS", "") + " -stdlib=libc++"
        if "LIBCXXPATH" not in env:
            toolchainPath = mx.distribution("LLVM_TOOLCHAIN").get_output()
            out = mx.LinesOutputCapture()
            mx.run([os.path.join(toolchainPath, "bin", "llvm-config"), "--libdir", "--host-target"], out=out)
            env["LIBCXXPATH"] = os.path.join(*out.lines)  # os.path.join(libdir, host-target)
        return env


class SulongVm(CExecutionEnvironmentMixin, GuestVm):
    def __init__(self, config_name, options, host_vm=None, cflags=None):
        super(SulongVm, self).__init__(host_vm)
        self._config_name = config_name
        self._options = options
        self._cflags = cflags

    def with_host_vm(self, host_vm):
        return SulongVm(self._config_name, self._options, host_vm, cflags=self._cflags)

    def config_name(self):
        return self._config_name

    def toolchain_name(self):
        return "native"

    def name(self):
        return "sulong"

    def launcherClass(self):
        return "com.oracle.truffle.llvm.launcher.LLVMLauncher"

    def launcherName(self):
        return "lli"

    def run(self, cwd, args):
        bench_file_and_args = args[-3:]
        launcher_args = self.launcher_args(args[:-3]) + self._options + bench_file_and_args
        if hasattr(self.host_vm(), 'run_launcher'):
            result = self.host_vm().run_launcher(self.launcherName(), launcher_args, cwd)
        else:
            def _filter_properties(args):
                props = []
                remaining_args = []
                vm_prefix = "--vm.D"
                for arg in args:
                    if arg.startswith(vm_prefix):
                        props.append('-D' + arg[len(vm_prefix):])
                    else:
                        remaining_args.append(arg)
                return props, remaining_args

            props, launcher_args = _filter_properties(launcher_args)
            sulongCmdLine = self.launcher_vm_args() + \
                            props + \
                            [self.launcherClass()]
            result = self.host_vm().run(cwd, sulongCmdLine + launcher_args)

        ret_code, out, vm_dims = result
        return ret_code, add_run_numbers(out), vm_dims

    def prepare_env(self, env):
        # if hasattr(self.host_vm(), 'run_launcher'):
        #     import mx_sdk_vm_impl
        #     env['CC'] = os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'jre', 'languages', 'llvm', self.toolchain_name(), 'bin', 'graalvm-{}-clang'.format(self.toolchain_name()))
        # else:
        # we always use the bootstrap toolchain since the toolchain is not installed by default in a graalvm
        # change this if we can properly install components into a graalvm deployment
        env['CC'] = mx_subst.path_substitutions.substitute('<toolchainGetToolPath:{},CC>'.format(self.toolchain_name()))
        env['CXX'] = mx_subst.path_substitutions.substitute('<toolchainGetToolPath:{},CXX>'.format(self.toolchain_name()))
        if self._cflags:
            cflags_string = ' '.join(self._cflags)
            env['CFLAGS'] = cflags_string
        return env

    def out_file(self):
        return 'bench'

    def opt_phases(self):
        return []

    def launcher_vm_args(self):
        return mx_sulong.getClasspathOptions()

    def launcher_args(self, args):
        launcher_args = [
            '--experimental-options',
            '--engine.CompilationFailureAction=Diagnose',
            '--engine.TreatPerformanceWarningsAsErrors=call,instanceof,store',
        ]
        return launcher_args + args

    def hosting_registry(self):
        return java_vm_registry


class PolybenchVm(CExecutionEnvironmentMixin, GuestVm):

    def __init__(self, config_name, options, templateOptions, host_vm=None):
        super(PolybenchVm, self).__init__(host_vm)
        self._config_name = config_name
        self._options = options
        self._templateOptions = templateOptions

    def with_host_vm(self, host_vm):
        return PolybenchVm(self._config_name, self._options, self._templateOptions, host_vm)

    def config_name(self):
        return self._config_name

    def toolchain_name(self):
        return "native"

    def name(self):
        return "sulong-polybench"

    def launcherClass(self):
        return "org.graalvm.polybench.PolyBenchLauncher"

    def launcherName(self):
        return "polybench"

    def run(self, cwd, args):
        bench_file = args[-1:]
        bench_args = args[:-1]
        launcher_args = ['--path'] + bench_file + self._options + bench_args
        if hasattr(self.host_vm(), 'run_launcher'):
            result = self.host_vm().run_launcher(self.launcherName(), launcher_args, cwd)
        else:
            def _filter_properties(args):
                props = []
                remaining_args = []
                vm_prefix = "--vm.D"
                for arg in args:
                    if arg.startswith(vm_prefix):
                        props.append('-D' + arg[len(vm_prefix):])
                    else:
                        remaining_args.append(arg)
                return props, remaining_args

            props, launcher_args = _filter_properties(launcher_args)
            sulongCmdLine = self.launcher_vm_args() + \
                            props + \
                            [self.launcherClass(), '--path']  + bench_file + launcher_args
            result = self.host_vm().run(cwd, sulongCmdLine)

        ret_code, out, vm_dims = result
        return ret_code, add_run_numbers(out), vm_dims

    def prepare_env(self, env):
        # if hasattr(self.host_vm(), 'run_launcher'):
        #     import mx_sdk_vm_impl
        #     env['CC'] = os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'jre', 'languages', 'llvm', self.toolchain_name(), 'bin', 'graalvm-{}-clang'.format(self.toolchain_name()))
        # else:
        # we always use the bootstrap toolchain since the toolchain is not installed by default in a graalvm
        # change this if we can properly install components into a graalvm deployment
        env['CC'] = mx_subst.path_substitutions.substitute('<toolchainGetToolPath:{},CC>'.format(self.toolchain_name()))
        env['CXX'] = mx_subst.path_substitutions.substitute('<toolchainGetToolPath:{},CXX>'.format(self.toolchain_name()))
        return env

    def out_file(self):
        return 'bench'

    def opt_phases(self):
        return []

    def templateOptions(self):
        return self._templateOptions

    def launcher_vm_args(self):
        return mx_sulong.getClasspathOptions(['POLYBENCH'])

    def launcher_args(self, args):
        launcher_args = [
            '--experimental-options',
            '--engine.CompilationFailureAction=ExitVM',
            '--engine.TreatPerformanceWarningsAsErrors=call,instanceof,store',
        ]
        return launcher_args + args

    def hosting_registry(self):
        return java_vm_registry

class LLVMUnitTestsSuite(VmBenchmarkSuite):
    def __init__(self, *args, **kwargs):
        super(LLVMUnitTestsSuite, self).__init__(*args, **kwargs)
        self.bench_to_exec = {}

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'sulong'

    def name(self):
        return 'llvm-unit-tests'

    def benchmarkList(self, bmSuiteArgs):
        return ['llvm-test-suite']

    def failurePatterns(self):
        return []

    def successPatterns(self):
        return [re.compile(r'Testing Time')]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
		r'Passed:(\s+)(?P<count>[\d]+)',
		{
                "benchmark": ("llvm-unit-tests", str),
                # TODO: it's a borrowed metric name, a new one should be registered
                "metric.name": "jck-passed",
                "metric.type": "numeric",
                "metric.value": ("<count>", int),
                "metric.score-function": "id",
                "metric.better": "higher",
                "metric.unit": "#"
            }),
            mx_benchmark.StdOutRule(
		r'Failed:(\s+)(?P<count>[\d]+)',
		{
                "benchmark": ("llvm-unit-tests", str),
                # TODO: it's a borrowed metric name, a new one should be registered
                "metric.name": "jck-failed",
                "metric.type": "numeric",
                "metric.value": ("<count>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "#"
            }),
            mx_benchmark.StdOutRule(
		r'Testing Time:(\s+)(?P<time>\d+(?:\.\d+)?)+s',
		{
                "benchmark": ("llvm-unit-tests", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.value": ("<time>", float),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "s"
            })
        ]

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        # TODO
        return None

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        # TODO
        return []

    def get_vm_registry(self):
        return lit_vm_registry

class LitVm(mx_benchmark.OutputCapturingVm):

    def __init__(self, config_name, options):
        super(LitVm, self).__init__()
        self._config_name = config_name
        self._options = options

    def config_name(self):
        return self._config_name

    def name(self):
        # TODO: register "lit" as a new VM and return it here
        return "sulong"

    def post_process_command_line_args(self, suiteArgs):
        """Adapts command-line arguments to run the specific VM configuration."""
        return suiteArgs

    def dimensions(self, cwd, args, code, out):
        """Returns a dict of additional dimensions to put into every datapoint.
        :rtype: dict
        """
        return {}

    def run_vm(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        env = os.environ.copy()
        mx.run(["lit", "-v", "-j", "4", "-o", "results.json", "."], nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, env=env)
        return 0

    def prepare_env(self, env):
        return env

    def opt_phases(self):
        return []

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
native_vm_registry.add_vm(SulongVm('default', []), _suite, 10)
native_vm_registry.add_vm(SulongVm('default-O0', [], cflags=['-O0']), _suite, 10)
native_vm_registry.add_vm(SulongVm('default-O1', [], cflags=['-O1']), _suite, 10)
native_vm_registry.add_vm(SulongVm('default-O2', [], cflags=['-O2', '-fno-vectorize', '-fno-slp-vectorize']), _suite, 10)
native_vm_registry.add_vm(SulongVm('default-O3', [], cflags=['-O3', '-fno-vectorize', '-fno-slp-vectorize']), _suite, 10)

native_polybench_vm_registry = VmRegistry("NativePolybench", known_host_registries=[java_vm_registry])
native_polybench_vm_registry.add_vm(PolybenchVm('debug-aux-engine-cache',
    ['--experimental-options', '--eval-source-only.0=true',
     '--llvm.AOTCacheStore.0=true', '--llvm.AOTCacheLoad.0=false', '--engine.DebugCacheCompile.0=aot', '--engine.DebugCacheStore.0=true',
     '--llvm.AOTCacheStore.1=false', '--llvm.AOTCacheLoad.1=true', '--engine.DebugCacheStore.1=false', '--engine.DebugCacheLoad.1=true',
     '--engine.MultiTier=false', '--engine.CompileAOTOnCreate=false', '--engine.DebugCachePreinitializeContext=false',
     '--engine.DebugTraceCache=true', '--multi-context-runs=2', '-w', '0', '-i', '10'], []), _suite, 10)
native_polybench_vm_registry.add_vm(PolybenchVm('store-aux-engine-cache',
    ['--experimental-options', '--multi-context-runs=1', '--eval-source-only=true',
     '--llvm.AOTCacheStore=true', '--engine.CacheCompile=aot', '--engine.CachePreinitializeContext=false',
     '--engine.TraceCache=true'], ['--engine.CacheStore=' + os.path.join(os.getcwd(), 'test-${benchmark}.image')]), _suite, 10)
native_polybench_vm_registry.add_vm(PolybenchVm('load-aux-engine-cache',
    ['--experimental-options', '--multi-context-runs=1',
     '--llvm.AOTCacheLoad=true', '--engine.CachePreinitializeContext=false', '--engine.TraceCache=true',
     '-w', '0', '-i', '10'], ['--engine.CacheLoad=' + os.path.join(os.getcwd(), 'test-${benchmark}.image')]), _suite, 10)
native_polybench_vm_registry.add_vm(PolybenchVm('3-runs-exclusive-engine',
    ['--multi-context-runs=3', '--shared-engine=false', '-w', '10', '-i', '10'], []), _suite, 10)
native_polybench_vm_registry.add_vm(PolybenchVm('3-runs-shared-engine',
    ['--multi-context-runs=3', '--shared-engine=true', '-w', '10', '-i', '10'], []), _suite, 10)

lit_vm_registry = VmRegistry("Lit", known_host_registries=[java_vm_registry])
lit_vm_registry.add_vm(LitVm('sulong-native', []), _suite, 10)
