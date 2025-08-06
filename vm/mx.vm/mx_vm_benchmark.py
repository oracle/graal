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

import mx
import mx_benchmark
import mx_sdk_benchmark
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_sdk_vm_ng
from mx_benchmark import DataPoints
from mx_sdk_benchmark import GraalVm, NativeImageVM

_suite = mx.suite('vm')


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
        return mx_benchmark.java_vm_registry

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

        runArgs = self.runArgs(bmSuiteArgs)
        runArgs = runArgs or ['base']
        out = ""

        for arg in runArgs:
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
                mx.abort("FileSizeBenchmarkSuite expects 'base' (default) or 'standalones' in runArgs but got " + arg)

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


mx_benchmark.add_bm_suite(AgentScriptJsBenchmarkSuite())
mx_benchmark.add_bm_suite(FileSizeBenchmarkSuite())


def register_graalvm_vms():
    default_host_vm_name = mx_sdk_vm_impl.graalvm_dist_name().lower().replace('_', '-')
    short_host_vm_name = re.sub('-java[0-9]+$', '', default_host_vm_name)
    host_vm_names = [default_host_vm_name] + ([short_host_vm_name] if short_host_vm_name != default_host_vm_name else [])
    for host_vm_name in host_vm_names:
        for config_name, java_args, launcher_args, priority in mx_sdk_vm.get_graalvm_hostvm_configs():
            extra_launcher_args = []
            if config_name.startswith("jvm"):
                # needed for NFI CLinker benchmarks
                extra_launcher_args += ['--vm.-enable-preview']
                # needed for GraalWasm SIMD benchmarks
                extra_launcher_args += ['--vm.-add-modules=jdk.incubator.vector']
            mx_benchmark.java_vm_registry.add_vm(GraalVm(host_vm_name, config_name, java_args, launcher_args + extra_launcher_args), _suite, priority)

    optimization_levels = ['O0', 'O1', 'O2', 'O3', 'Os']
    analysis_context_sensitivity = ['insens', 'allocsens', '1obj', '2obj1h', '3obj2h', '4obj3h']

    for short_name, config_suffix in [('niee', 'ee'), ('ni', 'ce')]:
        if any(component.short_name == short_name for component in mx_sdk_vm_impl.registered_graalvm_components(stage1=False)):
            config_names = list()
            for main_config in ['default', 'gate', 'llvm', 'native-architecture', 'future-defaults-all', 'preserve-all', 'preserve-classpath'] + analysis_context_sensitivity:
                config_names.append(f'{main_config}-{config_suffix}')

            for optimization_level in optimization_levels:
                config_names.append(f'{optimization_level}-{config_suffix}')
                for main_config in ['llvm', 'native-architecture', 'g1gc', 'native-architecture-g1gc', 'preserve-all', 'preserve-classpath'] + analysis_context_sensitivity:
                    config_names.append(f'{main_config}-{optimization_level}-{config_suffix}')

            for config_name in config_names:
                mx_benchmark.add_java_vm(NativeImageVM('native-image', config_name, ['--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED']), _suite, 10)

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
