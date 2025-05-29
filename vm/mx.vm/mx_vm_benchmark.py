#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
from __future__ import annotations

import os
import re
from os.path import basename, dirname, getsize
from typing import Iterable

import mx
import mx_benchmark
import mx_sdk_benchmark
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_sdk_vm_ng
from mx_benchmark import DataPoint, DataPoints
from mx_sdk_benchmark import GraalVm, NativeImageVM

_suite = mx.suite('vm')
_polybench_vm_registry = mx_benchmark.VmRegistry('PolyBench', 'polybench-vm')
_polybench_modes = [
    ('standard', ['--mode=standard']),
    ('interpreter', ['--mode=interpreter']),
]

POLYBENCH_METRIC_MAPPING = {
    "compilation-time": "compile-time",
    "partial-evaluation-time": "pe-time",
    "allocated-bytes": "allocated-memory",
    "peak-time": "time"
}  # Maps some polybench metrics to standardized metric names


class AgentScriptJsBenchmarkSuite(mx_benchmark.VmBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    def __init__(self):
        super(AgentScriptJsBenchmarkSuite, self).__init__()
        self._benchmarks = {
            'plain' : [],
            'triple' : ['--insight=sieve-filter1.js'],
            'single' : ['--insight=sieve-filter2.js'],
            'iterate' : ['--insight=sieve-filter3.js'],
        }

    def group(self):
        return 'Graal'

    def subgroup(self):
        return 'graal-js'

    def name(self):
        return 'agentscript'

    def version(self):
        return '0.1.0'

    def benchmarkList(self, bmSuiteArgs):
        return self._benchmarks.keys()

    def failurePatterns(self):
        return [
            re.compile(r'error:'),
            re.compile(r'internal error:'),
            re.compile(r'Error in'),
            re.compile(r'\tat '),
            re.compile(r'Defaulting the .*\. Consider using '),
            re.compile(r'java.lang.OutOfMemoryError'),
        ]

    def successPatterns(self):
        return [
            re.compile(r'Hundred thousand prime numbers in [0-9]+ ms', re.MULTILINE),
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        assert len(benchmarks) == 1
        return [
            mx_benchmark.StdOutRule(r'^Hundred thousand prime numbers in (?P<time>[0-9]+) ms$', {
                "bench-suite": self.benchSuiteName(),
                "benchmark": (benchmarks[0], str),
                "metric.name": "warmup",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<time>", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": ("$iteration", int),
            })
        ]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.vmArgs(bmSuiteArgs) + super(AgentScriptJsBenchmarkSuite, self).createCommandLineArgs(benchmarks, bmSuiteArgs)

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return os.path.join(_suite.dir, 'benchmarks', 'agentscript')

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        if not benchmarks:
            raise mx.abort(f"Benchmark suite '{self.name()}' cannot run multiple benchmarks in the same VM process")
        if len(benchmarks) != 1:
            raise mx.abort(f"Benchmark suite '{self.name()}' can run only one benchmark at a time")
        return self._benchmarks[benchmarks[0]] + ['-e', 'count=50'] + runArgs + ['sieve.js']

    def get_vm_registry(self):
        return mx_benchmark.js_vm_registry

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = super(AgentScriptJsBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


class ExcludeWarmupRule(mx_benchmark.StdOutRule):
    """Rule that behaves as the StdOutRule, but skips input until a certain pattern."""

    def __init__(self, *args, **kwargs):
        self.startPattern = re.compile(kwargs.pop('startPattern'))
        super(ExcludeWarmupRule, self).__init__(*args, **kwargs)

    def parse(self, text) -> Iterable[DataPoint]:
        m = self.startPattern.search(text)
        if m:
            return super(ExcludeWarmupRule, self).parse(text[m.end()+1:])
        else:
            return []


class PolyBenchBenchmarkSuite(mx_benchmark.VmBenchmarkSuite):
    def __init__(self):
        super(PolyBenchBenchmarkSuite, self).__init__()
        self._extensions = [".js", ".rb", ".wasm", ".bc", ".py", ".pmh"]

    def _get_benchmark_root(self):
        if not hasattr(self, '_benchmark_root'):
            dist_name = "POLYBENCH_BENCHMARKS"
            distribution = mx.distribution(dist_name)
            _root = distribution.get_output()
            if not os.path.exists(_root):
                msg = f"The distribution {dist_name} does not exist: {_root}{os.linesep}"
                msg += f"This might be solved by running: mx build --dependencies={dist_name}"
                mx.abort(msg)
            self._benchmark_root = _root
        return self._benchmark_root

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "polybench"

    def version(self):
        return "0.1.0"

    def benchmarkList(self, bmSuiteArgs):
        if not hasattr(self, "_benchmarks"):
            self._benchmarks = []
            graal_test = mx.distribution('GRAAL_TEST', fatalIfMissing=False)
            polybench_ee = mx.distribution('POLYBENCH_EE', fatalIfMissing=False)
            if graal_test and polybench_ee and mx.get_env('ENABLE_POLYBENCH_HPC') == 'yes':
                # If the GRAAL_TEST and POLYBENCH_EE (for instructions metric) distributions
                # are present, the CompileTheWorld benchmark is available.
                self._benchmarks = ['CompileTheWorld']
            for group in ["interpreter", "compiler", "warmup", "nfi"]:
                dir_path = os.path.join(self._get_benchmark_root(), group)
                for f in os.listdir(dir_path):
                    f_path = os.path.join(dir_path, f)
                    if os.path.isfile(f_path) and os.path.splitext(f_path)[1] in self._extensions:
                        self._benchmarks.append(os.path.join(group, f))
        return self._benchmarks

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return self._get_benchmark_root()

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None or len(benchmarks) != 1:
            mx.abort("Must specify one benchmark at a time.")
        vmArgs = self.vmArgs(bmSuiteArgs)
        benchmark = benchmarks[0]
        if benchmark == 'CompileTheWorld':
            # Run CompileTheWorld as a polybench benchmark, using instruction counting to get a stable metric.
            # The CompileTheWorld class has been reorganized to have separate "prepare" and
            # "compile" steps such that only the latter is measured by polybench.
            # PAPI instruction counters are thread-local so CTW is run on the same thread as
            # the polybench harness (i.e., CompileTheWorld.MultiThreaded=false).
            import mx_compiler
            res = mx_compiler._ctw_jvmci_export_args(arg_prefix='--vm.-') + [
                   '--ctw',
                   '--vm.cp=' + mx.distribution('GRAAL_TEST').path,
                   '--vm.DCompileTheWorld.MaxCompiles=10000',
                   '--vm.DCompileTheWorld.Classpath=' + mx.library('DACAPO_MR1_BACH').get_path(resolve=True),
                   '--vm.DCompileTheWorld.Verbose=false',
                   '--vm.DCompileTheWorld.MultiThreaded=false',
                   '--vm.Djdk.graal.ShowConfiguration=info',
                   '--metric=instructions',
                   '-w', '1',
                   '-i', '5'] + vmArgs
        else:
            benchmark_path = os.path.join(self._get_benchmark_root(), benchmark)
            res = ["--path=" + benchmark_path] + vmArgs
        return res

    def get_vm_registry(self):
        return _polybench_vm_registry

    def rules(self, output, benchmarks, bmSuiteArgs):
        metric_name = self._get_metric_name(output)
        rules = []
        if metric_name == "time":
            # Special case for metric "time": Instead of reporting the aggregate numbers,
            # report individual iterations. Two metrics will be reported:
            # - "warmup" includes all iterations (warmup and run)
            # - "time" includes only the "run" iterations
            rules += [
                mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": "warmup",
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("$iteration", int),
                }),
                ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": "time",
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("<iteration>", int),
                }, startPattern=r"::: Running :::")
            ]
        elif metric_name in ("allocated-memory", "metaspace-memory", "application-memory"):
            rules += [
                ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": metric_name,
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": ("<iteration>", int),
                }, startPattern=r"::: Running :::")
            ]
        else:
            rules += [
                mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] after run: (?P<value>.*) (?P<unit>.*)", {
                    "benchmark": ("<name>", str),
                    "metric.better": "lower",
                    "metric.name": metric_name,
                    "metric.unit": ("<unit>", str),
                    "metric.value": ("<value>", float),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.iteration": 0,
                })
            ]
        rules += [
            mx_benchmark.StdOutRule(r"### load time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmarks[0],
                "metric.name": "context-eval-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            }),
            mx_benchmark.StdOutRule(r"### init time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmarks[0],
                "metric.name": "context-init-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            })
        ]
        return rules

    def _get_metric_name(self, bench_output):
        match = re.search(r"metric class:\s*(?P<metric_class_name>\w+)Metric", bench_output)
        if match is None:
            match = re.search(r"metric class:\s*(?P<metric_class_name>\w+)", bench_output)

        metric_class_name = match.group("metric_class_name")
        metric_class_name = re.sub(r'(?<!^)(?=[A-Z])', '-', metric_class_name).lower()

        if metric_class_name in POLYBENCH_METRIC_MAPPING:
            return POLYBENCH_METRIC_MAPPING[metric_class_name]
        else:
            return metric_class_name


class FileSizeBenchmarkSuite(mx_benchmark.VmBenchmarkSuite):
    SZ_MSG_PATTERN = "== binary size == {} is {} bytes, path = {}\n"
    SZ_RGX_PATTERN = r"== binary size == (?P<image_name>[a-zA-Z0-9_\-\.:]+) is (?P<value>[0-9]+) bytes, path = (?P<path>.*)"


    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "file-size"

    def version(self):
        return "0.0.1"

    def benchmarkList(self, bmSuiteArgs):
        return ["default"]

    def get_vm_registry(self):
        return _polybench_vm_registry

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
        host_vm = None
        if isinstance(vm, mx_benchmark.GuestVm):
            host_vm = vm.host_vm()
            assert host_vm
        name = 'graalvm-ee' if mx_sdk_vm_impl.has_component('svmee', stage1=True) else 'graalvm-ce'
        dims = {
            # the vm and host-vm fields are hardcoded to one of the accepted names of the field
            "vm": name,
            "host-vm": name,
            "host-vm-config": self.host_vm_config_name(host_vm, vm),
            "guest-vm": name if host_vm else "none",
            "guest-vm-config": self.guest_vm_config_name(host_vm, vm),
        }

        def get_size_message(image_name, image_location):
            return FileSizeBenchmarkSuite.SZ_MSG_PATTERN.format(image_name, getsize(image_location), image_location)

        bmSuiteArgs = bmSuiteArgs or ['base']
        out = ""

        for arg in bmSuiteArgs:
            if arg == 'standalones':
                # Standalones
                for project in mx.projects():
                    if isinstance(project, mx_sdk_vm_ng.NativeImageProject):
                        native_image = project.output_file()
                        if os.path.exists(native_image):
                            out += get_size_message(project.options_file_name(), native_image)
            elif arg == 'base':
                # GraalVM base
                output_root = mx_sdk_vm_impl.get_final_graalvm_distribution().get_output_root()
                for location in mx_sdk_vm_impl.get_all_native_image_locations(include_libraries=True, include_launchers=False, abs_path=False):
                    lib_name = 'lib:' + mx_sdk_vm_impl.remove_lib_prefix_suffix(basename(location))
                    out += get_size_message(lib_name, os.path.join(output_root, location))
                for location in mx_sdk_vm_impl.get_all_native_image_locations(include_libraries=False, include_launchers=True, abs_path=False):
                    launcher_name = mx_sdk_vm_impl.remove_exe_suffix(basename(location))
                    out += get_size_message(launcher_name, os.path.join(output_root, location))
            else:
                mx.abort("FileSizeBenchmarkSuite expects 'base' or 'standalones' in bench suite arguments but got " + arg)

        if out:
            mx.log(out, end='')
        return 0, out, dims

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                FileSizeBenchmarkSuite.SZ_RGX_PATTERN,
                {
                    "bench-suite": self.benchSuiteName(),
                    "benchmark": ("<image_name>", str),
                    "benchmark-configuration": ("<path>", str),
                    "vm": "svm",
                    "metric.name": "binary-size",
                    "metric.value": ("<value>", int),
                    "metric.unit": "B",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                })
        ]


class PolyBenchVm(GraalVm):
    def __init__(self, name, config_name, extra_java_args, extra_launcher_args):
        super(PolyBenchVm, self).__init__(name, config_name, extra_java_args, extra_launcher_args)
        if self.debug_args:
            # The `arg[1:]` is to strip the first '-' from the args since it's
            # re-added by the subsequent processing of `--vm`
            self.debug_args = [f'--vm.{arg[1:]}' for arg in self.debug_args]

    def run(self, cwd, args):
        return self.run_launcher('polybench', args, cwd)

def polybenchmark_rules(benchmark, metric_name, mode):
    rules = []
    if metric_name == "time":
        # Special case for metric "time": Instead of reporting the aggregate numbers,
        # report individual iterations. Two metrics will be reported:
        # - "warmup" includes all iterations (warmup and run)
        # - "time" includes only the "run" iterations
        rules += [
            mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": "warmup",
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("$iteration", int),
                "engine.config": mode,
            }),
            ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": "time",
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("<iteration>", int),
                "engine.config": mode,
            }, startPattern=r"::: Running :::"),
            mx_benchmark.StdOutRule(r"### load time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmark,
                "metric.name": "context-eval-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "engine.config": mode
            }),
            mx_benchmark.StdOutRule(r"### init time \((?P<unit>.*)\): (?P<delta>[0-9]+)", {
                "benchmark": benchmark,
                "metric.name": "context-init-time",
                "metric.value": ("<delta>", float),
                "metric.unit": ("<unit>", str),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "engine.config": mode,
            }),
        ]
    elif metric_name in ("allocated-memory", "metaspace-memory", "application-memory", "instructions"):
        rules += [
            ExcludeWarmupRule(r"\[(?P<name>.*)\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": metric_name,
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("<iteration>", int),
                "engine.config": mode,
            }, startPattern=r"::: Running :::")
        ]
    elif metric_name in ("compile-time", "pe-time"):
        rules += [
            mx_benchmark.StdOutRule(r"\[(?P<name>.*)\] after run: (?P<value>.*) (?P<unit>.*)", {
                "benchmark": benchmark, #("<name>", str),
                "metric.better": "lower",
                "metric.name": metric_name,
                "metric.unit": ("<unit>", str),
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": 0,
                "engine.config": mode,
            }),
        ]
    return rules

mx_benchmark.add_bm_suite(AgentScriptJsBenchmarkSuite())
mx_benchmark.add_bm_suite(PolyBenchBenchmarkSuite())
mx_benchmark.add_bm_suite(FileSizeBenchmarkSuite())


def register_graalvm_vms():
    default_host_vm_name = mx_sdk_vm_impl.graalvm_dist_name().lower().replace('_', '-')
    short_host_vm_name = re.sub('-java[0-9]+$', '', default_host_vm_name)
    host_vm_names = [default_host_vm_name] + ([short_host_vm_name] if short_host_vm_name != default_host_vm_name else [])
    for host_vm_name in host_vm_names:
        for config_name, java_args, launcher_args, priority in mx_sdk_vm.get_graalvm_hostvm_configs():
            if config_name.startswith("jvm"):
                # needed for NFI CLinker benchmarks
                launcher_args += ['--vm.-enable-preview']
            mx_benchmark.java_vm_registry.add_vm(GraalVm(host_vm_name, config_name, java_args, launcher_args), _suite, priority)
            for mode, mode_options in _polybench_modes:
                _polybench_vm_registry.add_vm(PolyBenchVm(host_vm_name, config_name + "-" + mode, [], mode_options + launcher_args))
        if _suite.get_import("polybenchmarks") is not None:
            import mx_polybenchmarks_benchmark
            mx_polybenchmarks_benchmark.polybenchmark_vm_registry.add_vm(PolyBenchVm(host_vm_name, "jvm", [], ["--jvm"]))
            mx_polybenchmarks_benchmark.polybenchmark_vm_registry.add_vm(PolyBenchVm(host_vm_name, "native", [], ["--native"]))
            mx_polybenchmarks_benchmark.rules = polybenchmark_rules

    optimization_levels = ['O0', 'O1', 'O2', 'O3', 'Os']
    analysis_context_sensitivity = ['insens', 'allocsens', '1obj', '2obj1h', '3obj2h', '4obj3h']

    for short_name, config_suffix in [('niee', 'ee'), ('ni', 'ce')]:
        if any(component.short_name == short_name for component in mx_sdk_vm_impl.registered_graalvm_components(stage1=False)):
            config_names = list()
            for main_config in ['default', 'gate', 'llvm', 'native-architecture', 'future-defaults-all', 'preserve-all', 'preserve-classpath', 'layered'] + analysis_context_sensitivity:
                config_names.append(f'{main_config}-{config_suffix}')

            for optimization_level in optimization_levels:
                config_names.append(f'{optimization_level}-{config_suffix}')
                for main_config in ['llvm', 'native-architecture', 'g1gc', 'native-architecture-g1gc', 'preserve-all', 'preserve-classpath'] + analysis_context_sensitivity:
                    config_names.append(f'{main_config}-{optimization_level}-{config_suffix}')

            for config_name in config_names:
                mx_benchmark.add_java_vm(NativeImageVM('native-image', config_name, ['--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED']), _suite, 10)

    # Adding JAVA_HOME VMs to be able to run benchmarks on GraalVM binaries without the need of building it first
    for java_home_config in ['default', 'pgo', 'g1gc', 'g1gc-pgo', 'upx', 'upx-g1gc', 'quickbuild', 'quickbuild-g1gc', 'layered']:
        mx_benchmark.add_java_vm(NativeImageVM('native-image-java-home', java_home_config), _suite, 5)


    # Add VMs for libgraal
    if mx.suite('substratevm', fatalIfMissing=False) is not None:
        import mx_substratevm
        # Use `name` rather than `short_name` since the code that follows
        # should not be executed when "LibGraal Enterprise" is registered
        if mx_sdk_vm_impl.has_component(mx_substratevm.libgraal.name):
            libgraal_location = mx_sdk_vm_impl.get_native_image_locations(mx_substratevm.libgraal.name, 'jvmcicompiler')
            if libgraal_location is not None:
                import mx_graal_benchmark
                mx_sdk_benchmark.build_jvmci_vm_variants('server', 'graal-core-libgraal',
                                                         ['-server', '-XX:+EnableJVMCI', '-Djdk.graal.CompilerConfiguration=community', '-Djvmci.Compiler=graal', '-XX:+UseJVMCINativeLibrary', '-XX:JVMCILibPath=' + dirname(libgraal_location)],
                                                         mx_graal_benchmark._graal_variants, suite=_suite, priority=15, hosted=False)
