#
# Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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


class SulongBenchmarkSuite(VmBenchmarkSuite):
    def __init__(self, *args, **kwargs):
        super(SulongBenchmarkSuite, self).__init__(*args, **kwargs)
        self.bench_to_exec = {}

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'sulong'

    def name(self):
        return 'csuite'

    def run(self, benchnames, bmSuiteArgs):
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        assert isinstance(vm, CExecutionEnvironmentMixin)

        # compile benchmarks

        # save current Directory
        currentDir = os.getcwd()
        for bench in benchnames:
            try:
                # benchmark dir
                path = self.workingDirectory(benchnames, bmSuiteArgs)
                # create directory for executable of this vm
                if os.path.exists(path):
                    shutil.rmtree(path)
                os.makedirs(path)
                os.chdir(path)

                env = os.environ.copy()
                env['VPATH'] = '..'

                env = vm.prepare_env(env)
                out = os.path.join(path, vm.out_file())
                cmdline = ['make', '-f', '../Makefile', out]
                if mx._opts.verbose:
                    # The Makefiles should have logic to disable the @ sign
                    # so that all executed commands are visible.
                    cmdline += ["MX_VERBOSE=y"]
                mx.run(cmdline, env=env)
                self.bench_to_exec[bench] = out
            finally:
                # reset current Directory
                os.chdir(currentDir)

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

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            SulongBenchmarkRule(
		r'^first [\d]+ warmup iterations (?P<benchmark>[\S]+):(?P<line>([ ,]+(?:\d+(?:\.\d+)?))+)',
		{
                "benchmark": ("<benchmark>", str),
                "metric.name": "warmup",
                "metric.type": "numeric",
                "metric.value": ("<score>", float),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("<iteration>", int),
            }),
            SulongBenchmarkRule(
		r'^last [\d]+ iterations (?P<benchmark>[\S]+):(?P<line>([ ,]+(?:\d+(?:\.\d+)?))+)',
		{
                "benchmark": ("<benchmark>", str),
                "metric.name": "time",
                "metric.type": "numeric",
                "metric.value": ("<score>", float),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.unit": "us",
                "metric.iteration": ("<iteration>", int),
            }),
        ]

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        if len(benchmarks) != 1:
            mx.abort(
                "Please run a specific benchmark (mx benchmark csuite:<benchmark-name>) or all the benchmarks (mx benchmark csuite:*)")
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        assert isinstance(vm, CExecutionEnvironmentMixin)
        return join(_benchmarksDirectory(), benchmarks[0], vm.bin_dir())

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark csuite:<benchmark-name>) or all the benchmarks (mx benchmark csuite:*)")
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + [self.bench_to_exec[benchmarks[0]]] + runArgs

    def get_vm_registry(self):
        return native_vm_registry


class CExecutionEnvironmentMixin(object):

    def out_file(self):
        return 'bench'

    def bin_dir(self):
        return '{}-{}'.format(self.name(), self.config_name())

    def prepare_env(self, env):
        return env


class GccLikeVm(CExecutionEnvironmentMixin, Vm):
    def __init__(self, config_name, options):
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

    def run(self, cwd, args):
        myStdOut = mx.OutputCapture()
        retCode = mx.run(args, out=mx.TeeOutputCapture(myStdOut), cwd=cwd)
        return [retCode, myStdOut.data]

    def prepare_env(self, env):
        env['CFLAGS'] = ' '.join(self.options + _env_flags)
        env['CC'] = self.c_compiler_exe() # pylint: disable=assignment-from-no-return
        return env


class GccVm(GccLikeVm):
    def name(self):
        return "gcc"

    def compiler_name(self):
        return "gcc"

    def c_compiler_exe(self):
        return "gcc"


class ClangVm(GccLikeVm):
    def name(self):
        return "clang"

    def compiler_name(self):
        return "clang"

    def c_compiler_exe(self):
        return os.path.join(mx.distribution("LLVM_TOOLCHAIN").get_output(), "bin", "clang")


class SulongVm(CExecutionEnvironmentMixin, GuestVm):
    def config_name(self):
        return "default"

    def toolchain_name(self):
        return "native"

    def name(self):
        return "sulong"

    def run(self, cwd, args):
        bench_file = args[-1]
        launcher_args = self.launcher_args(args[:-1]) + [bench_file]
        if hasattr(self.host_vm(), 'run_launcher'):
            result = self.host_vm().run_launcher('lli', launcher_args, cwd)
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
                            ['-XX:-UseJVMCIClassLoader', "com.oracle.truffle.llvm.launcher.LLVMLauncher"]
            result = self.host_vm().run(cwd, sulongCmdLine + launcher_args)
        return result

    def prepare_env(self, env):
        # if hasattr(self.host_vm(), 'run_launcher'):
        #     import mx_sdk_vm_impl
        #     env['CC'] = os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'jre', 'languages', 'llvm', self.toolchain_name(), 'bin', 'graalvm-{}-clang'.format(self.toolchain_name()))
        # else:
        # we always use the bootstrap toolchain since the toolchain is not installed by default in a graalvm
        # change this if we can properly install components into a graalvm deployment
        env['CC'] = mx_subst.path_substitutions.substitute('<toolchainGetToolPath:{},CC>'.format(self.toolchain_name()))
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
            '--engine.InliningNodeBudget=10000',
            '--engine.CompilationFailureAction=ExitVM',
        ]
        return launcher_args + args

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
