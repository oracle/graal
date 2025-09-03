#
# Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

from __future__ import print_function, annotations

import collections.abc
import os
import os.path
import shutil
import subprocess
import tempfile
import time
import signal
import threading
import json
import argparse
import zipfile
from dataclasses import dataclass
from enum import Enum
from os import PathLike
from os.path import exists, basename
from pathlib import Path
from traceback import print_tb
from typing import List, Optional, Set, Collection, Union, Iterable, Sequence, Callable, TextIO, Tuple

import mx
import mx_benchmark
import datetime
import re
import copy
import urllib.request

import mx_sdk_vm
import mx_sdk_vm_impl
import mx_util
from mx_util import Stage, StageName, Layer
from mx_benchmark import DataPoints, DataPoint, BenchmarkSuite, Vm, SingleBenchmarkExecutionContext
from mx_sdk_vm_impl import svm_experimental_options

_suite = mx.suite('sdk')


# Shorthand commands used to quickly run common benchmarks.
mx.update_commands(_suite, {
    'dacapo': [
        lambda args: createBenchmarkShortcut("dacapo", args),
        '[<benchmarks>|*] [-- [VM options] [-- [DaCapo options]]]'
    ],
    'scaladacapo': [
        lambda args: createBenchmarkShortcut("scala-dacapo", args),
        '[<benchmarks>|*] [-- [VM options] [-- [Scala DaCapo options]]]'
    ],
    'specjvm2008': [
        lambda args: createBenchmarkShortcut("specjvm2008", args),
        '[<benchmarks>|*] [-- [VM options] [-- [SPECjvm2008 options]]]'
    ],
    'specjbb2015': [
        lambda args: mx_benchmark.benchmark(["specjbb2015"] + args),
        '[-- [VM options] [-- [SPECjbb2015 options]]]'
    ],
    'barista': [
        lambda args: createBenchmarkShortcut("barista", args),
        '[<benchmarks>|*] [-- [VM options] [-- [Barista harness options]]]'
    ],
    'renaissance': [
        lambda args: createBenchmarkShortcut("renaissance", args),
        '[<benchmarks>|*] [-- [VM options] [-- [Renaissance options]]]'
    ],
    'shopcart': [
        lambda args: createBenchmarkShortcut("shopcart", args),
        '[-- [VM options] [-- [ShopCart options]]]'
    ],
    'awfy': [
        lambda args: createBenchmarkShortcut("awfy", args),
        '[<benchmarks>|*] [-- [VM options] ] [-- [AWFY options] ]]'
    ],
})


def createBenchmarkShortcut(benchSuite, args):
    if not args:
        benchname = "*"
        remaining_args = []
    elif args[0] == "--":
        # not a benchmark name
        benchname = "*"
        remaining_args = args
    else:
        benchname = args[0]
        remaining_args = args[1:]
    return mx_benchmark.benchmark([benchSuite + ":" + benchname] + remaining_args)


# Adds a java VM from JAVA_HOME without any assumption about it
mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm('java-home', 'default'), _suite, 1)


def java_home_jdk():
    return mx.get_jdk()


JVMCI_JDK_TAG = 'jvmci'
BUNDLE_EXTENSION = ".nib"

class JvmciJdkVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, raw_name, raw_config_name, extra_args):
        super(JvmciJdkVm, self).__init__()
        self.raw_name = raw_name
        self.raw_config_name = raw_config_name
        self.extra_args = extra_args

    def name(self):
        return self.raw_name

    def config_name(self):
        return self.raw_config_name

    def post_process_command_line_args(self, args):
        return [arg if not callable(arg) else arg() for arg in self.extra_args] + args

    def get_jdk(self):
        tag = mx.get_jdk_option().tag
        if tag and tag != JVMCI_JDK_TAG:
            mx.abort("The '{0}/{1}' VM requires '--jdk={2}'".format(
                self.name(), self.config_name(), JVMCI_JDK_TAG))
        return mx.get_jdk(tag=JVMCI_JDK_TAG)

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        return self.get_jdk().run_java(
            args, out=out, err=out, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, command_mapper_hooks=self.command_mapper_hooks)

    def generate_java_command(self, args):
        return self.get_jdk().generate_java_command(self.post_process_command_line_args(args))

    def rules(self, output, benchmarks, bmSuiteArgs):
        rules = super(JvmciJdkVm, self).rules(output, benchmarks, bmSuiteArgs)
        return rules


def add_or_replace_arg(option_key, value, vm_option_list):
    """
    Determines if an option with the same key as option_key is already present in vm_option_list.
    If so, it replaces the option value with the given one. If not, it appends the option
    to the end of the list. It then returns the modified list.

    For example, if arg_list contains the argument '-Djdk.graal.CompilerConfig=community', and this function
    is called with an option_key of '-Djdk.graal.CompilerConfig' and a value of 'economy', the resulting
    argument list will contain one instance of '-Djdk.graal.CompilerConfig=economy'.
    """
    arg_string = option_key + '=' + value
    idx = next((idx for idx, arg in enumerate(vm_option_list) if arg.startswith(option_key)), -1)
    if idx == -1:
        vm_option_list.append(arg_string)
    else:
        vm_option_list[idx] = arg_string
    return vm_option_list


def build_jvmci_vm_variants(raw_name, raw_config_name, extra_args, variants, include_default=True, suite=None, priority=0, hosted=True):
    prefixes = [('', ['-XX:+UseJVMCICompiler'])]
    if hosted:
        prefixes.append(('hosted-', ['-XX:-UseJVMCICompiler']))
    for prefix, args in prefixes:
        extended_raw_config_name = prefix + raw_config_name
        extended_extra_args = extra_args + args
        if include_default:
            mx_benchmark.add_java_vm(
                JvmciJdkVm(raw_name, extended_raw_config_name, extended_extra_args), suite, priority)
        for variant in variants:
            compiler_config = None
            if len(variant) == 2:
                var_name, var_args = variant
                var_priority = priority
            elif len(variant) == 3:
                var_name, var_args, var_priority = variant
            elif len(variant) == 4:
                var_name, var_args, var_priority, compiler_config = variant
            else:
                raise TypeError("unexpected tuple size for jvmci variant {} (size must be <= 4)".format(variant))

            variant_args = extended_extra_args + var_args
            if compiler_config is not None:
                variant_args = add_or_replace_arg('-Djdk.graal.CompilerConfiguration', compiler_config, variant_args)

            mx_benchmark.add_java_vm(
                JvmciJdkVm(raw_name, extended_raw_config_name + '-' + var_name, variant_args), suite, var_priority)


class GraalVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, name, config_name, extra_java_args, extra_launcher_args):
        """
        :type name: str
        :type config_name: str
        :type extra_java_args: list[str] | None
        :type extra_launcher_args: list[str] | None
        """
        super(GraalVm, self).__init__()
        self._name = name
        self._config_name = config_name
        self.extra_java_args = extra_java_args or []
        self.extra_launcher_args = extra_launcher_args or []
        self.debug_args = mx.java_debug_args() if "jvm" in config_name else []

    def name(self):
        return self._name

    def config_name(self):
        return self._config_name

    def post_process_command_line_args(self, args):
        return self.extra_java_args + self.debug_args + args

    def post_process_launcher_command_line_args(self, args):
        return self.extra_launcher_args + \
            ['--vm.' + x[1:] if x.startswith('-X') else x for x in self.debug_args] + \
            args

    def home(self):
        if self.name() == 'native-image-java-home':
            return mx.get_jdk().home
        return mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True)

    def generate_java_command(self, args):
        return [os.path.join(self.home(), 'bin', 'java')] + args

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        """Run 'java' workloads."""
        self.extract_vm_info(args)
        cmd = self.generate_java_command(args)
        cmd = mx.apply_command_mapper_hooks(cmd, self.command_mapper_hooks)
        return mx.run(cmd, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def run_lang(self, cmd, args, cwd):
        """Deprecated. Call 'run_launcher' instead."""
        mx.log_deprecation("'run_lang' is deprecated. Use 'run_launcher' instead.")
        return self.run_launcher(cmd, args, cwd)

    def run_launcher(self, cmd, args, cwd):
        """Run the 'cmd' command in the 'bin' directory."""
        args = self.post_process_launcher_command_line_args(args)
        self.extract_vm_info(args)
        mx.log(f"Running '{cmd}' on '{self.name()}' with args: '{' '.join(args)}'")
        out = mx.TeeOutputCapture(mx.OutputCapture())
        command = [os.path.join(self.home(), 'bin', cmd)] + args
        command = mx.apply_command_mapper_hooks(command, self.command_mapper_hooks)
        code = mx.run(command, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)
        out = out.underlying.data
        dims = self.dimensions(cwd, args, code, out)
        return code, out, dims


# The uncompressed file from perf script is ~25x bigger than the compressed file
ADJUSTED_ITERS_FOR_PERF = {
    "renaissance": {
        "finagle-chirper": 10, # Default: 90 iters, 7GB compressed perf file
        "fj-kmeans": 15, # Default: 30 iters, 520MB compressed perf file
        "scala-stm-bench7": 20, # Default: 60 iters, 840MB compressed perf file
        "philosophers": 10, # Default: 30 iters, 900MB compressed perf file
        "akka-uct": 5, # Default: 24 iters, 2GB compressed perf file
        "finagle-http": 5 # Default: 12 iters, 1.3GB compressed perf file
    },
    "dacapo": {
        "sunflow": 15 # Default: 49 iters, 1.2GB compressed perf file
    }
}

NUM_ITERS_FLAG_NAME = {
    "renaissance": "-r",
    "dacapo": "-n"
}


class NativeImageBenchmarkConfig:
    def __init__(self, vm: NativeImageVM, bm_suite: BenchmarkSuite | NativeImageBenchmarkMixin, args):
        self.bm_suite = bm_suite
        self.benchmark_suite_name = bm_suite.benchSuiteName(args)
        self.benchmark_name = bm_suite.benchmarkName()
        self.executable, self.classpath_arguments, self.modulepath_arguments, self.system_properties, self.image_vm_args, image_run_args, self.split_run = NativeImageVM.extract_benchmark_arguments(
            args, bm_suite.all_command_line_args_are_vm_args())
        self.extra_image_build_arguments: List[str] = bm_suite.extra_image_build_argument(self.benchmark_name, args)
        # use list() to create fresh copies to safeguard against accidental modification
        self.image_run_args = bm_suite.extra_run_arg(self.benchmark_name, args, list(image_run_args))
        self.extra_jvm_args = bm_suite.extra_jvm_arg(self.benchmark_name, args)
        self.extra_agent_run_args = bm_suite.extra_agent_run_arg(self.benchmark_name, args, list(image_run_args))
        self.extra_agentlib_options = bm_suite.extra_agentlib_options(self.benchmark_name, args, list(image_run_args))
        for option in self.extra_agentlib_options:
            if option.startswith('config-output-dir'):
                mx.abort("config-output-dir must not be set in the extra_agentlib_options.")
        # Do not strip the run arguments if safepoint-sampler configuration is active or we want pgo samples (either from instrumentation or perf)
        self.extra_profile_run_args = bm_suite.extra_profile_run_arg(self.benchmark_name, args, list(image_run_args),
                                                                     not (vm.safepoint_sampler or vm.pgo_sampler_only or vm.pgo_use_perf))
        self.extra_agent_profile_run_args = bm_suite.extra_agent_profile_run_arg(self.benchmark_name, args,
                                                                                 list(image_run_args))
        if vm.pgo_use_perf and self.benchmark_suite_name in ADJUSTED_ITERS_FOR_PERF and self.benchmark_name in ADJUSTED_ITERS_FOR_PERF[self.benchmark_suite_name]:
            # For some benches the number of iters in the instrument-run stage is too much for perf, the generated files are too large.
            desired_iters = ADJUSTED_ITERS_FOR_PERF[self.benchmark_suite_name][self.benchmark_name]
            mx.log(f"Adjusting number of iters for instrument-run stage to {desired_iters}")
            self.extra_profile_run_args = adjust_arg_with_number(NUM_ITERS_FLAG_NAME[self.benchmark_suite_name], desired_iters, self.extra_profile_run_args)
        self.params = ['extra-image-build-argument', 'extra-jvm-arg', 'extra-run-arg', 'extra-agent-run-arg',
                       'extra-profile-run-arg',
                       'extra-agent-profile-run-arg', 'benchmark-output-dir', 'stages', 'skip-agent-assertions']

        self.skip_agent_assertions = bm_suite.skip_agent_assertions(self.benchmark_name, args)
        current_stage = vm.stages_info.current_stage
        is_shared_library = current_stage is not None and current_stage.is_layered() and current_stage.layer_info.is_shared_library
        self.benchmark_output_dir = self.bm_suite.benchmark_output_dir(self.benchmark_name, args)
        executable_name, output_dir = self.get_executable_name_and_output_dir_for_stage(current_stage, vm)
        self.executable_name: str = executable_name
        self.output_dir: Path = output_dir
        self.final_image_name = self.executable_name + '-' + vm.config_name()
        self.profile_path: Path = self.output_dir / f"{self.executable_name}.iprof"
        self.source_mappings_path: Path = self.output_dir / f"{self.executable_name}.sourceMappings.json"
        self.perf_script_path: Path = self.output_dir / f"{self.executable_name}.perf.script.out"
        self.perf_data_path: Path = self.output_dir / f"{self.executable_name}.perf.data"
        self.config_dir: Path = self.output_dir / "config"
        self.log_dir: Path = self.output_dir
        self.ml_log_dump_path: Path = self.output_dir / f"{self.executable_name}.ml.log.csv"
        base_image_build_args = ['--no-fallback']
        if not vm.pgo_use_perf:
            # Can only have debug info when not using perf, [GR-66850]
            base_image_build_args.append('-g')
        base_image_build_args += ['-H:+VerifyGraalGraphs', '-H:+VerifyPhases',
                                  '--diagnostics-mode'] if vm.is_gate else []
        base_image_build_args += ['-H:+ReportExceptionStackTraces']
        base_image_build_args += bm_suite.build_assertions(self.benchmark_name, vm.is_gate)
        base_image_build_args += self.system_properties
        base_image_build_args += self.build_report_args(vm.is_gate, vm.graalvm_edition)

        # Path to the X.nib bundle file --bundle-apply is specified
        bundle_apply_path = self.get_bundle_path_if_present()
        self.is_bundle_based = bundle_apply_path is not None
        # Path to the X.output directory if a bundle is created
        # In that case, files generated by Native Image are generated in that folder structure
        bundle_create_path = self.get_bundle_create_path_if_present()

        self.bundle_output_path: Optional[Path] = None
        """
        Path to the bundle output directory where native image produces its output files.
        The native image behavior for where files are produced is as follows:

        If ``--bundle-apply`` or ``--bundle--create`` is specified, it uses the ``.output`` directory corresponding to
        the given ``.nib`` file (or inferred in the case of ``--bundle-create`` without a specific filename.
        If both are specified the ``.nib`` file for ``--bundle-create`` is used to derive the ``.output`` directory.
        """

        if bundle_create_path:
            self.bundle_output_path = bundle_create_path
        elif self.is_bundle_based:
            bundle_dir = bundle_apply_path.parent
            bundle_name = bundle_apply_path.name
            assert bundle_name.endswith(BUNDLE_EXTENSION), bundle_name
            self.bundle_output_path = bundle_dir / f"{bundle_name[:-len(BUNDLE_EXTENSION)]}.output"

        if not self.is_bundle_based:
            base_image_build_args += self.classpath_arguments
            base_image_build_args += self.modulepath_arguments
            base_image_build_args += self.executable
            base_image_build_args += [f"-H:Path={self.output_dir}"]

        base_image_build_args += [
            f"-H:ConfigurationFileDirectories={self.config_dir}",
            '-H:+PrintAnalysisStatistics',
            '-H:+PrintCallEdges',
            # Produces the image_build_statistics.json file
            '-H:+CollectImageBuildStatistics',
        ]

        self.image_build_reports_directory: Path = self.output_dir / "reports"

        # Path of the final executable
        self.image_path = self.output_dir / self.final_image_name
        self.instrumented_image_path = self.output_dir / f"{self.executable_name}-instrument"
        if is_shared_library:
            self.image_path = self.image_path.parent / f"{self.image_path.name}.so"
            self.instrumented_image_path = self.instrumented_image_path.parent / f"{self.instrumented_image_path.name}.so"

        if vm.is_quickbuild:
            base_image_build_args += ['-Ob']
        if vm.graalos or vm.graalhost_graalos:
            base_image_build_args += ['-H:+GraalOS']
        if vm.use_string_inlining:
            base_image_build_args += ['-H:+UseStringInlining']
        if vm.use_open_type_world:
            base_image_build_args += ['-H:-ClosedTypeWorld']
        if vm.use_compacting_gc:
            base_image_build_args += ['-H:+CompactingOldGen']
        if vm.is_llvm:
            base_image_build_args += ['--features=org.graalvm.home.HomeFinderFeature'] + ['-H:CompilerBackend=llvm',
                                                                                          '-H:DeadlockWatchdogInterval=0']
        if vm.gc:
            base_image_build_args += ['--gc=' + vm.gc] + ['-H:+SpawnIsolates']
        if vm.native_architecture:
            base_image_build_args += ['-march=native']
        if vm.preserve_all:
            base_image_build_args += ['-H:Preserve=all']
        if vm.preserve_classpath:
            base_image_build_args += ['-H:Preserve=module=ALL-UNNAMED']
        if vm.future_defaults_all:
            base_image_build_args += ['--future-defaults=all']
        if vm.analysis_context_sensitivity:
            base_image_build_args += ['-H:AnalysisContextSensitivity=' + vm.analysis_context_sensitivity,
                                      '-H:-RemoveSaturatedTypeFlows', '-H:+AliasArrayTypeFlows']
        if vm.optimization_level:
            base_image_build_args += ['-' + vm.optimization_level]
        if vm.async_sampler:
            base_image_build_args += ['-R:+FlightRecorder',
                                      '-R:StartFlightRecording=filename=default.jfr',
                                      '--enable-monitoring=jfr',
                                      '-R:+JfrBasedExecutionSamplerStatistics'
                                      ]
        if self.image_vm_args is not None:
            base_image_build_args += self.image_vm_args
        self.is_runnable = self.check_runnable()
        base_image_build_args += self.extra_image_build_arguments

        bundle_args = [f'--bundle-apply={bundle_apply_path}'] if self.is_bundle_based else []
        # benchmarks are allowed to use experimental options
        # the bundle might also inject experimental options, but they will be appropriately locked/unlocked.
        self.base_image_build_args = [os.path.join(vm.home(), 'bin', 'native-image')] + svm_experimental_options(
            base_image_build_args) + bundle_args

        if bundle_create_path and StageName.INSTRUMENT_IMAGE in [stage.stage_name for stage in bm_suite.stages_info.effective_stages]:
            mx.warn(
                "Building instrumented benchmarks with --bundle-create is untested and may behave in unexpected ways")

    def build_report_args(self, is_gate: bool, graalvm_edition: str):
        # Generate Build Report only when the benchmark is a part of EE gate.
        return ['--emit=build-report'] if is_gate and graalvm_edition == "ee" else []

    def get_executable_name_and_output_dir_for_stage(self, stage: Stage, vm: NativeImageVM) -> Tuple[str, Path]:
        executable_name = self.compute_executable_name()
        is_shared_library = stage is not None and stage.is_layered() and stage.layer_info.is_shared_library
        if is_shared_library:
            # Shared library layers have to start with 'lib' and are differentiated with the layer index
            executable_name = f"lib-layer{stage.layer_info.index}-{executable_name}"

        # Form output directory
        root_dir = Path(
            self.benchmark_output_dir if self.benchmark_output_dir else mx.suite('sdk').get_output_root(platformDependent=False,
                                                                                             jdkDependent=False)).absolute()
        output_dir = root_dir / "native-image-benchmarks" / f"{executable_name}-{vm.config_name()}"

        return executable_name, output_dir

    def compute_executable_name(self) -> str:
        result = self.bm_suite.executable_name()
        if result is not None:
            return result

        parts = [self.bm_suite.benchSuiteName()]
        if self.bm_suite.version() != "unknown":
            parts.append(self.bm_suite.version().replace(".", "-"))
        if self.benchmark_name:
            parts.append(self.benchmark_name.replace(os.sep, "_"))
        return "-".join(parts).lower()

    def get_build_output_json_file(self, stage: StageName) -> Path:
        """
        Path to the build output statistics JSON file (see also ``-H:BuildOutputJSONFile``).

        For image stages, specifies the location where the file should be placed.

        This file needs special handling in case of bundles. With bundles, this file is always placed in the ``other``
        directory (whose files are not moved by :meth:`NativeImageVM.move_bundle_output`.

        :param stage: The stage for which the file is required. The run stages use the file generated from the
                      corresponding image stage.
        """
        if stage.is_instrument():
            suffix = "instrument"
        elif stage.is_final():
            suffix = "final"
        else:
            raise AssertionError(f"There is no build output file for the {stage} stage")

        filename = f"build-output-{suffix}.json"

        if self.bundle_output_path:
            return self.bundle_output_path / "other" / filename
        else:
            return self.image_build_reports_directory / filename

    def get_image_build_stats_file(self, stage: StageName) -> Path:
        """
        Same concept as :meth:`get_build_output_json_file`, but for the ``image_build_statistics.json`` file.

        The file is produced with the ``-H:+CollectImageBuildStatistics`` option
        """
        if stage.is_instrument():
            suffix = "instrument"
        elif stage.is_final():
            suffix = "final"
        else:
            raise AssertionError(f"There is no image build statistics file for the {stage} stage")

        return self.image_build_reports_directory / f"image_build_statistics-{suffix}.json"

    def check_runnable(self):
        # TODO remove once there is load available for the specified benchmarks
        if self.benchmark_suite_name in ["mushop", "quarkus"]:
            return False
        return True

    def get_bundle_path_if_present(self) -> Optional[Path]:
        if isinstance(self.bm_suite, NativeImageBundleBasedBenchmarkMixin):
            cached_bundle_path = self.bm_suite.get_bundle_path()
            bundle_copy_path = self.output_dir / basename(cached_bundle_path)
            mx_util.ensure_dirname_exists(bundle_copy_path)
            mx.copyfile(cached_bundle_path, bundle_copy_path)
            return bundle_copy_path

        return None

    def get_bundle_create_path_if_present(self) -> Optional[Path]:
        """
        Scans the image build arguments and looks for ``--bundle-create``

        :return: Absolute path of the bundle's ``.output`` directory if ``--bundle-create`` was given, otherwise ``None``
        """
        bundle_create_arg = "--bundle-create="

        for arg in self.extra_image_build_arguments:
            if arg.startswith(bundle_create_arg):
                bundle_spec = arg[len(bundle_create_arg):]

                if "," in bundle_spec:
                    raise RuntimeError(
                        f"Native Image benchmarks do not support additional options, besides the file name, with --bundle-create: {arg}")

                assert bundle_spec.endswith(
                    BUNDLE_EXTENSION), f"--bundle-create path must end with {BUNDLE_EXTENSION}, was {bundle_spec}"
                bundle_path = Path(bundle_spec[:-len(BUNDLE_EXTENSION)] + ".output")

                return bundle_path.absolute()
            elif arg == "--bundle-create":
                return self.output_dir / f"{self.final_image_name}.output"

        return None


class BenchOutStream:
    """
    Writes incoming data to both the given text file and callable output stream.

    Is callable itself and can also be passed to the ``print`` function.
    """

    def __init__(self, log_file: TextIO, output_stream: Callable[[str], None]):
        self.log_file = log_file
        self.output_stream = output_stream

    def __call__(self, string: str) -> int:
        v = self.log_file.write(string)
        self.output_stream(string)
        return v

    def write(self, string: str) -> int:
        return self(string)


@dataclass
class StagesContext:
    native_image_vm: NativeImageVM
    bench_out: Callable[[str], int]
    bench_err: Callable[[str], int]
    non_zero_is_fatal: bool
    cwd: str


class StageRunner:
    def __init__(self, stages: StagesContext):
        self.stages = stages
        self.stages_info = stages.native_image_vm.stages_info
        self.config: NativeImageBenchmarkConfig = stages.native_image_vm.config
        self.bench_out = stages.bench_out
        self.bench_err = stages.bench_err
        self.final_image_name = self.config.final_image_name

        self.exit_code: Optional[int] = None
        self.stderr_path: Optional[PathLike] = None
        self.stdout_path: Optional[PathLike] = None
        self.stdout_file: Optional[TextIO] = None
        self.stderr_file: Optional[TextIO] = None

    def __enter__(self):
        self.stdout_path = (
                    self.config.log_dir / f"{self.final_image_name}-{self.stages_info.current_stage}-stdout.log").absolute()
        self.stderr_path = (
                    self.config.log_dir / f"{self.final_image_name}-{self.stages_info.current_stage}-stderr.log").absolute()
        self.stdout_file = open(self.stdout_path, 'w')
        self.stderr_file = open(self.stderr_path, 'w')

        self.separator_line()
        mx.log(f"{self.get_timestamp()}Entering stage: {self.stages_info.current_stage} for {self.final_image_name}")
        self.separator_line()

        if self.stdout_path:
            mx.log(f"The standard output is saved to {self.stdout_path}")
        if self.stderr_path:
            mx.log(f"The standard error is saved to {self.stderr_path}")

        return self

    def __exit__(self, tp, value, tb):
        self.stdout_file.flush()
        self.stderr_file.flush()

        is_success = self.exit_code == 0 and (tb is None)

        if self.config.split_run:
            suffix = "PASS" if is_success else "FAILURE"
            with open(self.config.split_run, 'a') as f:
                f.write(
                    f"{self.get_timestamp()}{self.config.bm_suite.name()}:{self.config.benchmark_name} {self.stages_info.current_stage}: {suffix}\n")

        if is_success:
            self.stages_info.success()
            if self.stages_info.current_stage == self.stages_info.last_stage:
                self.bench_out(
                    f"{self.get_timestamp()}{STAGE_LAST_SUCCESSFUL_PREFIX} {self.stages_info.current_stage} for {self.final_image_name}")
            else:
                self.bench_out(f"{self.get_timestamp()}{STAGE_SUCCESSFUL_PREFIX} {self.stages_info.current_stage}")

            self.separator_line()
        else:
            self.stages_info.fail()
            failure_prefix = f"{self.get_timestamp()}Failed in stage {self.stages_info.current_stage} for {self.final_image_name}"
            if self.exit_code is not None and self.exit_code != 0:
                mx.log(mx.colorize(f"{failure_prefix} with exit code {self.exit_code}", 'red'))
            elif tb:
                mx.log(mx.colorize(f"{failure_prefix} with exception:", 'red'))
                print_tb(tb)
            else:
                raise AssertionError(f"Unexpected error condition {self.exit_code=}, {tb=}")

            if self.stdout_path:
                mx.log(mx.colorize(f"Standard error written to {self.stdout_path}", "blue"))

            if self.stderr_path:
                mx.log(mx.colorize(f"Standard error written to {self.stderr_path}", "red"))

            self.separator_line()

            mx.log(mx.colorize('--------- To run the failed benchmark execute the following: ', 'green'))
            mx.log(mx.current_mx_command())

            if self.stages_info.stages_till_now:
                mx.log(mx.colorize(
                    '--------- To only prepare the benchmark add the following to the end of the previous command: ',
                    'green'))
                mx.log('-Dnative-image.benchmark.stages=' + ','.join(map(str, self.stages_info.stages_till_now)))

            mx.log(mx.colorize(
                '--------- To only run the failed stage add the following to the end of the previous command: ',
                'green'))
            mx.log(f"-Dnative-image.benchmark.stages={self.stages_info.current_stage}")

            mx.log(mx.colorize(
                '--------- Additional arguments that can be used for debugging the benchmark go after the final --: ',
                'green'))
            for param in self.config.params:
                mx.log('-Dnative-image.benchmark.' + param + '=')

            self.separator_line()
            if self.stages.non_zero_is_fatal:
                mx.abort(self.get_timestamp() + 'Exiting the benchmark due to the failure.')

        self.stdout_file.close()
        self.stderr_file.close()

    def stdout(self, include_bench_out):
        return BenchOutStream(self.stdout_file, lambda s: self.bench_out(s) if include_bench_out else mx.log(s, end=""))

    def stderr(self, include_bench_err):
        return BenchOutStream(self.stderr_file, lambda s: self.bench_err(s) if include_bench_err else mx.log(s, end=""))

    @staticmethod
    def separator_line():
        mx.log(mx.colorize('-' * 120, 'green'))

    @staticmethod
    def get_timestamp():
        return '[' + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") + '] '

    def execute_command(self, vm, command: Sequence[str]) -> int:
        mx.log("Running: ")
        mx.log(" ".join(command))

        write_output = self.stages_info.should_produce_datapoints()

        self.exit_code = self.config.bm_suite.run_stage(vm, self.stages_info.current_stage, command,
                                                        self.stdout(write_output), self.stderr(write_output),
                                                        self.stages.cwd, False)
        if not self.stages_info.current_stage.is_image() and self.config.bm_suite.validateReturnCode(self.exit_code):
            self.exit_code = 0

        return self.exit_code


def _native_image_time_to_int(value: str) -> int:
    return int(float(value.replace(',', '')))


def _native_image_hex_to_int(value: str) -> int:
    return int(value, 16)


class NativeImageVM(GraalVm):
    """
    A VM implementation to build and run Native Image benchmarks.

    Runs individual stages of the benchmarking process (or all stages in sequence in the fallback mode).
    See also :class:`NativeImageBenchmarkMixin` for more information on the Native Image benchmarking process.
    """

    def __init__(self, name, config_name, extra_java_args=None, extra_launcher_args=None):
        super().__init__(name, config_name, extra_java_args, extra_launcher_args)
        self.vm_args = None
        # When this is set, run the instrumentation-image and instrumentation-run stages.
        # Does not necessarily do instrumentation.
        self.pgo_instrumentation = False
        self.pgo_exclude_conditional = False
        self.pgo_sampler_only = False
        self.pgo_use_perf = False
        self.pgo_perf_invoke_profile_collection_strategy: Optional[PerfInvokeProfileCollectionStrategy] = None
        self.is_gate = False
        self.is_quickbuild = False
        self.graalos = False
        self.graalhost_graalos = False
        self.pie = False
        self.layered = False
        self.use_string_inlining = False
        self.is_llvm = False
        self.gc = None
        self.native_architecture = False
        self.preserve_all = False
        self.preserve_classpath = False
        self.future_defaults_all = False
        self.use_upx = False
        self.use_open_type_world = False
        self.use_compacting_gc = False
        self.graalvm_edition = None
        self.config: Optional[NativeImageBenchmarkConfig] = None
        self.stages_info: Optional[StagesInfo] = None
        self.stages: Optional[StagesContext] = None
        self.jdk_profiles_collect = False
        self.adopted_jdk_pgo = False
        self.async_sampler = False
        self.safepoint_sampler = False
        self.profile_inference_feature_extraction = False
        self.profile_inference_call_count = False
        self.force_profile_inference = False
        self.profile_inference_debug = False
        self.analysis_context_sensitivity = None
        self.optimization_level = None
        self._configure_comma_separated_configs(config_name)
        if ',' in config_name:
            self._canonical_configuration = False
        else:
            # we validate that programmatic configuration of the VM matches reliably re-generates the original config name
            # since this feature is relied upon for syntactic sugar for vm configs
            assert config_name == self.config_name(), f"Config name mismatch: '{config_name}' is generated as '{self.config_name()}' !"

    @staticmethod
    def canonical_config_name(config_name):
        # NativeImageVM allows syntactic sugar for its VM configs such that 'otw-ee,pgo,g1gc' is mapped to 'otw-g1gc-pgo-ee'
        # this canonicalization will take the former and return the latter
        return NativeImageVM('native-image', config_name).config_name()

    def config_name(self):
        # Generates the unique vm config name based on how the VM is actually configured.
        # It concatenates the config options in the correct order to match the expected format.
        # Note: the order of entries here must match the order of entries in _configure_from_name
        config = []
        if self.native_architecture is True:
            config += ["native-architecture"]
        if self.use_string_inlining is True:
            config += ["string-inlining"]
        if self.use_open_type_world is True:
            config += ["otw"]
        if self.use_compacting_gc is True:
            config += ["compacting-gc"]
        if self.preserve_all is True:
            config += ["preserve-all"]
        if self.preserve_classpath is True:
            config += ["preserve-classpath"]
        if self.graalos is True:
            config += ["graalos"]
        if self.graalhost_graalos is True:
            config += ["graalhost-graalos"]
        if self.pie is True:
            config += ["pie"]
        if self.layered is True:
            config += ["layered"]
        if self.future_defaults_all is True:
            config += ["future-defaults-all"]
        if self.is_gate is True:
            config += ["gate"]
        if self.use_upx is True:
            config += ["upx"]
        if self.is_quickbuild is True:
            config += ["quickbuild"]
        if self.gc == "G1":
            config += ["g1gc"]
        if self.is_llvm is True:
            config += ["llvm"]
        is_pgo_set = False
        if self.pgo_sampler_only is True:
            config += ["pgo-sampler"]
            is_pgo_set = True
        # pylint: disable=too-many-boolean-expressions
        if not is_pgo_set and self.pgo_instrumentation is True \
                and self.jdk_profiles_collect is False \
                and self.adopted_jdk_pgo is False \
                and self.safepoint_sampler is False \
                and self.async_sampler is False \
                and self.force_profile_inference is False \
                and self.profile_inference_feature_extraction is False:
            config += ["pgo"]
        if self.pgo_use_perf:
            config += ["perf-sampler"]
            if self.pgo_perf_invoke_profile_collection_strategy is not None:
                config += [str(self.pgo_perf_invoke_profile_collection_strategy)]
        if self.analysis_context_sensitivity is not None:
            sensitivity = self.analysis_context_sensitivity
            if sensitivity.startswith("_"):
                sensitivity = sensitivity[1:]
            config += [sensitivity]
        if self.jdk_profiles_collect is True:
            config += ["jdk-profiles-collect"]
        if self.adopted_jdk_pgo is True:
            config += ["adopted-jdk-pgo"]
        if self.profile_inference_feature_extraction is True:
            config += ["profile-inference-feature-extraction"]
        if self.profile_inference_call_count is True:
            config += ["profile-inference-call-count"]
        if self.pgo_instrumentation is True and self.force_profile_inference is True:
            if self.pgo_exclude_conditional is True:
                config += ["profile-inference-pgo"]
            if self.profile_inference_debug is True:
                config += ["profile-inference-debug"]
        if self.safepoint_sampler is True:
            config += ["safepoint-sampler"]
        if self.async_sampler is True:
            config += ["async-sampler"]
        if self.optimization_level is not None:
            config += [self.optimization_level]
        if not config:
            config += ["default"]
        if self.graalvm_edition is not None:
            config += [self.graalvm_edition]
        return "-".join(config)

    def _configure_comma_separated_configs(self, config_string):
        # Due to the complexity of the VM config and how hard it is to get the ordering right, it has been relaxed
        # to allow comma-separated configs. So 'pgo,g1gc-ee,native-architecture' is syntactic sugar for 'native-architecture-g1gc-pgo-ee'
        for config_part in config_string.split(','):
            if config_part:
                self._configure_from_name(config_part)

    def _configure_from_name(self, config_name):
        if not config_name:
            mx.abort(f"config_name must be set. Use 'default' for the default {self.__class__.__name__} configuration.")

        # This defines the allowed config names for NativeImageVM. The ones registered will be available via --jvm-config
        # Note: the order of entries here must match the order of statements in NativeImageVM.config_name()
        rule = r'^(?P<native_architecture>native-architecture-)?(?P<string_inlining>string-inlining-)?(?P<otw>otw-)?(?P<compacting_gc>compacting-gc-)?(?P<preserve_all>preserve-all-)?(?P<preserve_classpath>preserve-classpath-)?' \
               r'(?P<graalos>graalos-)?(?P<graalhost_graalos>graalhost-graalos-)?(?P<pie>pie-)?(?P<layered>layered-)?' \
               r'(?P<future_defaults_all>future-defaults-all-)?(?P<gate>gate-)?(?P<upx>upx-)?(?P<quickbuild>quickbuild-)?(?P<gc>g1gc-)?' \
               r'(?P<llvm>llvm-)?(?P<pgo>pgo-|pgo-sampler-|pgo-perf-sampler-invoke-multiple-|pgo-perf-sampler-invoke-|pgo-perf-sampler-)?(?P<inliner>inline-)?' \
               r'(?P<analysis_context_sensitivity>insens-|allocsens-|1obj-|2obj1h-|3obj2h-|4obj3h-)?(?P<jdk_profiles>jdk-profiles-collect-|adopted-jdk-pgo-)?' \
               r'(?P<profile_inference>profile-inference-feature-extraction-|profile-inference-call-count-|profile-inference-pgo-|profile-inference-debug-)?(?P<sampler>safepoint-sampler-|async-sampler-)?(?P<optimization_level>O0-|O1-|O2-|O3-|Os-)?(default-)?(?P<edition>ce-|ee-)?$'

        mx.logv(f"== Registering configuration: {config_name}")
        match_name = f"{config_name}-"  # adding trailing dash to simplify the regex
        matching = re.match(rule, match_name)
        if not matching:
            mx.abort(f"{self.__class__.__name__} configuration is invalid: {config_name}")

        if matching.group("native_architecture") is not None:
            mx.logv(f"'native-architecture' is enabled for {config_name}")
            self.native_architecture = True

        if matching.group("preserve_all") is not None:
            mx.logv(f"'preserve-all' is enabled for {config_name}")
            self.preserve_all = True

        if matching.group("preserve_classpath") is not None:
            mx.logv(f"'preserve-classpath' is enabled for {config_name}")
            self.preserve_classpath = True

        if matching.group("graalos") is not None:
            mx.logv(f"'graalos' is enabled for {config_name}")
            self.graalos = True

        if matching.group("graalhost_graalos") is not None:
            mx.logv(f"'graalhost-graalos' is enabled for {config_name}")
            self.graalhost_graalos = True

        if matching.group("pie") is not None:
            mx.logv(f"'pie' is enabled for {config_name}")
            self.pie = True

        if matching.group("layered") is not None:
            mx.logv(f"'layered' is enabled for {config_name}")
            self.layered = True

        if matching.group("future_defaults_all") is not None:
            mx.logv(f"'future-defaults-all' is enabled for {config_name}")
            self.future_defaults_all = True

        if matching.group("string_inlining") is not None:
            mx.logv(f"'string-inlining' is enabled for {config_name}")
            self.use_string_inlining = True

        if matching.group("gate") is not None:
            mx.logv(f"'gate' mode is enabled for {config_name}")
            self.is_gate = True

        if matching.group("upx") is not None:
            mx.logv(f"'upx' is enabled for {config_name}")
            self.use_upx = True

        if matching.group("otw") is not None:
            mx.logv(f"'otw' is enabled for {config_name}")
            self.use_open_type_world = True

        if matching.group("compacting_gc") is not None:
            mx.logv(f"'compacting-gc' is enabled for {config_name}")
            self.use_compacting_gc = True

        if matching.group("quickbuild") is not None:
            mx.logv(f"'quickbuild' is enabled for {config_name}")
            self.is_quickbuild = True

        if matching.group("gc") is not None:
            gc = matching.group("gc")[:-1]
            if gc == "g1gc":
                mx.logv(f"'g1gc' is enabled for {config_name}")
                self.gc = "G1"
            else:
                mx.abort(f"Unknown GC: {gc}")

        if matching.group("llvm") is not None:
            mx.logv(f"'llvm' mode is enabled for {config_name}")
            self.is_llvm = True

        if matching.group("pgo") is not None:
            pgo_mode = matching.group("pgo")[:-1]
            if pgo_mode == "pgo":
                mx.logv(f"'pgo' is enabled for {config_name}")
                self.pgo_instrumentation = True
            elif pgo_mode == "pgo-sampler":
                self.pgo_instrumentation = True
                self.pgo_sampler_only = True
            elif pgo_mode == "pgo-perf-sampler":
                self.pgo_instrumentation = True
                self.pgo_use_perf = True
            elif pgo_mode == "pgo-perf-sampler-invoke":
                self.pgo_instrumentation = True
                self.pgo_use_perf = True
                self.pgo_perf_invoke_profile_collection_strategy = PerfInvokeProfileCollectionStrategy.ALL
            elif pgo_mode == "pgo-perf-sampler-invoke-multiple":
                self.pgo_instrumentation = True
                self.pgo_use_perf = True
                self.pgo_perf_invoke_profile_collection_strategy = PerfInvokeProfileCollectionStrategy.MULTIPLE_CALLEES
            else:
                mx.abort(f"Unknown pgo mode: {pgo_mode}")

        if matching.group("jdk_profiles") is not None:
            config = matching.group("jdk_profiles")[:-1]
            if config == 'jdk-profiles-collect':
                self.jdk_profiles_collect = True
                self.pgo_instrumentation = True

                def generate_profiling_package_prefixes():
                    # run the native-image-configure tool to gather the jdk package prefixes
                    graalvm_home_bin = os.path.join(mx_sdk_vm.graalvm_home(), 'bin')
                    native_image_configure_command = mx.cmd_suffix(
                        os.path.join(graalvm_home_bin, 'native-image-configure'))
                    if not exists(native_image_configure_command):
                        mx.abort('Failed to find the native-image-configure command at {}. \nContent {}: \n\t{}'.format(
                            native_image_configure_command, graalvm_home_bin,
                            '\n\t'.join(os.listdir(graalvm_home_bin))))
                    tmp = tempfile.NamedTemporaryFile()
                    ret = mx.run([native_image_configure_command, 'generate-filters',
                                  '--include-packages-from-modules=java.base',
                                  '--exclude-classes=org.graalvm.**', '--exclude-classes=com.oracle.**',
                                  # remove internal packages
                                  f'--output-file={tmp.name}'], nonZeroIsFatal=True)
                    if ret != 0:
                        mx.abort('Native image configure command failed.')

                    # format the profiling package prefixes
                    with open(tmp.name, 'r') as f:
                        prefixes = json.loads(f.read())
                        if 'rules' not in prefixes:
                            mx.abort('Native image configure command failed. Can not generate rules.')
                        rules = prefixes['rules']
                        rules = map(lambda r: r['includeClasses'][:-2], filter(lambda r: 'includeClasses' in r, rules))
                        return ','.join(rules)

                self.generate_profiling_package_prefixes = generate_profiling_package_prefixes
            elif config == 'adopted-jdk-pgo':
                self.adopted_jdk_pgo = True
            else:
                mx.abort(f'Unknown jdk profiles configuration: {config}')

        if matching.group("profile_inference") is not None:
            profile_inference_config = matching.group("profile_inference")[:-1]
            if profile_inference_config == 'profile-inference-feature-extraction':
                self.profile_inference_feature_extraction = True
                self.pgo_instrumentation = True  # extract code features
            elif profile_inference_config == 'profile-inference-call-count':
                self.profile_inference_call_count = True
            elif profile_inference_config == "profile-inference-pgo":
                # We need to run instrumentation as the profile-inference-pgo JVM config requires dynamically collected
                # profiles to combine with the ML-inferred branch probabilities.
                self.pgo_instrumentation = True

                # Due to the collision between flags, we must re-enable the ML inference:
                # 1. To run the image build in the profile-inference-pgo mode, we must enable PGO to dynamically collect program profiles.
                # 2. The PGO flag disables the ML Profile Inference.
                # 3, Therefore, here we re-enable the ML Profile Inference from the command line.
                self.force_profile_inference = True

                self.pgo_exclude_conditional = True
            elif profile_inference_config == 'profile-inference-debug':
                # We need to run instrumentation as the profile-inference-debug config compares inferred profiles to profiles collected via instrumentation.
                self.pgo_instrumentation = True

                # Due to the collision between flags, we must re-enable the ML inference:
                # 1. To run the image build in the debug mode, we must enable PGO to dynamically collect program profiles.
                # 2. The PGO flag disables the ML Profile Inference.
                # 3. Therefore, here we re-enable the ML Profile Inference from the command line.
                self.force_profile_inference = True

                self.profile_inference_debug = True
            else:
                mx.abort('Unknown profile inference configuration: {}.'.format(profile_inference_config))

        if matching.group("sampler") is not None:
            config = matching.group("sampler")[:-1]
            if config == 'safepoint-sampler':
                self.safepoint_sampler = True
                self.pgo_instrumentation = True
            elif config == 'async-sampler':
                self.async_sampler = True
            else:
                mx.abort(f'Unknown type of sampler configuration: {config}')

        if matching.group("edition") is not None:
            edition = matching.group("edition")[:-1]
            mx.logv(f"GraalVM edition is set to: {edition}")
            self.graalvm_edition = edition

        if matching.group("optimization_level") is not None:
            olevel = matching.group("optimization_level")[:-1]
            mx.logv(f"GraalVM optimization level is set to: {olevel}")
            if olevel in ["O0", "O1", "O2", "O3", "Os"]:
                self.optimization_level = olevel
            else:
                mx.abort(f"Unknown configuration for optimization level: {olevel}")

        if matching.group("analysis_context_sensitivity") is not None:
            context_sensitivity = matching.group("analysis_context_sensitivity")[:-1]
            if context_sensitivity in ["insens", "allocsens"]:
                mx.logv(f"analysis context sensitivity {context_sensitivity} is enabled for {config_name}")
                self.analysis_context_sensitivity = context_sensitivity
            elif context_sensitivity in ["1obj", "2obj1h", "3obj2h", "4obj3h"]:
                mx.logv(f"analysis context sensitivity {context_sensitivity} is enabled for {config_name}")
                self.analysis_context_sensitivity = f"_{context_sensitivity}"
            else:
                mx.abort(f"Unknown analysis context sensitivity: {context_sensitivity}")

    @staticmethod
    def supported_vm_arg_prefixes():
        """
            This list is intentionally restrictive. We want to be sure that what we add is correct on the case-by-case
            basis. In the future we can convert this from a failure into a warning.
            :return: a list of args supported by native image.
        """
        return ['-D', '-H', '-Xmx', '-Xmn', '-XX:-PrintGC', '-XX:+PrintGC', '--add-opens', '--add-modules', '--add-exports',
                '--add-reads', '--enable-native-access']

    _VM_OPTS_SPACE_SEPARATED_ARG = ['-mp', '-modulepath', '-limitmods', '-addmods', '-upgrademodulepath', '-m',
                                    '--module-path', '--limit-modules', '--add-modules', '--upgrade-module-path',
                                    '--module', '--module-source-path', '--add-exports', '--add-opens', '--add-reads',
                                    '--patch-module', '--boot-class-path', '--source-path', '-cp', '-classpath', '-p']

    @staticmethod
    def _split_vm_arguments(args, all_args_are_vm_args):
        if all_args_are_vm_args:
            return args, [], []

        i = 0
        while i < len(args):
            arg = args[i]
            if arg == '-jar':
                return args[:i], args[i:i + 2], args[i + 2:]
            elif not arg.startswith('-'):
                return args[:i], [args[i]], args[i + 1:]
            elif arg in NativeImageVM._VM_OPTS_SPACE_SEPARATED_ARG:
                i += 2
            else:
                i += 1

        mx.abort('No executable found in args: ' + str(args))

    @staticmethod
    def extract_benchmark_arguments(args, all_args_are_vm_args):
        i = 0
        clean_args = args[:]
        split_run = None
        while i < len(args):
            if args[i].startswith('--split-run'):
                split_run = clean_args.pop(i + 1)
                clean_args.pop(i)
            if args[i].startswith('--jvmArgsPrepend'):
                clean_args[i + 1] = ' '.join([x for x in args[i + 1].split(' ') if "-Dnative-image" not in x])
                i += 2
            else:
                i += 1
        clean_args = [x for x in clean_args if "-Dnative-image" not in x]
        vm_args, executable, image_run_args = NativeImageVM._split_vm_arguments(clean_args, all_args_are_vm_args)

        classpath_arguments = []
        modulepath_arguments = []
        system_properties = [a for a in vm_args if a.startswith('-D')]
        image_vm_args = []
        i = 0
        while i < len(vm_args):
            vm_arg = vm_args[i]
            if vm_arg.startswith('--class-path'):
                classpath_arguments.append(vm_arg)
                i += 1
            elif vm_arg.startswith('-cp') or vm_arg.startswith('-classpath'):
                classpath_arguments += [vm_arg, vm_args[i + 1]]
                i += 2
            elif vm_arg.startswith('-p') or vm_arg.startswith('-modulepath'):
                modulepath_arguments += [vm_arg, vm_args[i + 1]]
                i += 2
            else:
                if not any(vm_arg.startswith(elem) for elem in NativeImageVM.supported_vm_arg_prefixes()):
                    mx.abort('Unsupported argument ' + vm_arg + '.' +
                             ' Currently supported argument prefixes are: ' + str(
                        NativeImageVM.supported_vm_arg_prefixes()))
                if vm_arg in NativeImageVM._VM_OPTS_SPACE_SEPARATED_ARG:
                    image_vm_args.append(vm_args[i])
                    image_vm_args.append(vm_args[i + 1])
                    i += 2
                else:
                    image_vm_args.append(vm_arg)
                    i += 1

        return executable, classpath_arguments, modulepath_arguments, system_properties, image_vm_args, image_run_args, split_run

    def dimensions(self, cwd, args, code, out):
        """
        Adds some Native-Image-specific extra fields to the produced datapoints.

        The field values are determined from the executed stage and some are extracted from build output files.
        That's why they cannot be added in fallback mode.
        """
        dims = super().dimensions(cwd, args, code, out)

        if not self.stages_info.fallback_mode and not self.stages_info.current_stage.is_agent():
            assert self.stages_info.failed or self.stages_info.current_stage in self.stages_info.stages_till_now, "dimensions method was called before stage was executed, not all information is available"

            def gc_mapper(value: str) -> str:
                """
                Maps the GC value given in the ``BuildOutputJSONFile`` to the corresponding value in the bench server schema.
                """
                if value == "G1 GC":
                    return "g1"
                elif value == "Epsilon GC":
                    return "epsilon"
                elif value == "Serial GC":
                    return "serial"
                else:
                    raise AssertionError(f"Unknown GC value: {value}")

            def opt_mapper(value: str) -> str:
                """
                Maps the optimization level value given in the ``BuildOutputJSONFile`` to the corresponding value in the bench server schema.
                """
                return f"O{value}"

            if self.pgo_instrumentation:
                if self.pgo_sampler_only:
                    pgo_value = "sampler-only"
                else:
                    pgo_value = "pgo"
            elif self.adopted_jdk_pgo:
                pgo_value = "adopted"
            else:
                pgo_value = "off"

            replacement = {
                "runtime.gc": ("<general_info.garbage_collector>", gc_mapper),
                "native-image.stage": str(self.stages_info.current_stage.stage_name),
                "native-image.instrumented": str(self.stages_info.current_stage.is_instrument()).lower(),
                "native-image.pgo": pgo_value,
                "native-image.opt": ("<general_info.graal_compiler.optimization_level>", opt_mapper),
            }
            if self.stages_info.current_stage.is_layered():
                # By encoding the app layer with a fixed value string ("app-layer") we facilitate bench-server
                # querying for the app layer specifically - enabling comparisons across benchmarks
                layer_info = self.stages_info.current_stage.layer_info
                layer_str = f"shared-layer-{layer_info.index}" if layer_info.is_shared_library else "app-layer"
                replacement["native-image.layer"] = layer_str
            rule = mx_benchmark.JsonFixedFileRule(
                self.config.get_build_output_json_file(self.stages_info.current_stage.stage_name),
                replacement,
                ["general_info.garbage_collector", "general_info.graal_compiler.optimization_level"]
            )

            datapoints = list(rule.parse(""))
            assert len(datapoints) == 1
            dims.update(datapoints[0])

        return dims

    def image_build_rules(self, benchmarks):
        return self.image_build_general_rules(benchmarks) + self.image_build_analysis_rules(benchmarks) \
            + self.image_build_statistics_rules(benchmarks) + self.image_build_timers_rules(benchmarks)

    def image_build_general_rules(self, benchmarks):
        rules = []

        if self.stages_info.should_produce_datapoints(StageName.INSTRUMENT_IMAGE):
            image_path = self.config.instrumented_image_path
        elif self.stages_info.should_produce_datapoints(StageName.IMAGE):
            image_path = self.config.image_path

            for config_type in ['jni', 'proxy', 'predefined-classes', 'reflect', 'resource', 'serialization']:
                config_path = self.config.config_dir / f"{config_type}-config.json"
                if config_path.exists():
                    rules.append(FileSizeRule(config_path, self.config.benchmark_suite_name, self.config.benchmark_name,
                                              "config-size", config_type))
        else:
            image_path = None

        if image_path:
            rules.append(ObjdumpSectionRule(image_path, self.config.benchmark_suite_name, benchmarks[0]))
            rules.append(
                FileSizeRule(image_path, self.config.benchmark_suite_name, self.config.benchmark_name, "binary-size"))

        return rules

    def image_build_analysis_rules(self, benchmarks):
        return [
            AnalysisReportJsonFileRule(self.config.image_build_reports_directory, self.config.bundle_output_path,
                                       self.is_gate, self.config.is_bundle_based, {
                                           "bench-suite": self.config.benchmark_suite_name,
                                           "benchmark": benchmarks[0],
                                           "metric.name": "analysis-stats",
                                           "metric.type": "numeric",
                                           "metric.unit": "#",
                                           "metric.value": ("<total_call_edges>", int),
                                           "metric.better": "lower",
                                           "metric.iteration": 0,
                                           "metric.object": "call-edges",
                                       }, ['total_call_edges']),
            AnalysisReportJsonFileRule(self.config.image_build_reports_directory, self.config.bundle_output_path,
                                       self.is_gate, self.config.is_bundle_based, {
                                           "bench-suite": self.config.benchmark_suite_name,
                                           "benchmark": benchmarks[0],
                                           "metric.name": "analysis-stats",
                                           "metric.type": "numeric",
                                           "metric.unit": "#",
                                           "metric.value": ("<total_reachable_types>", int),
                                           "metric.better": "lower",
                                           "metric.iteration": 0,
                                           "metric.object": "reachable-types",
                                       }, ['total_reachable_types']),
            AnalysisReportJsonFileRule(self.config.image_build_reports_directory, self.config.bundle_output_path,
                                       self.is_gate, self.config.is_bundle_based, {
                                           "bench-suite": self.config.benchmark_suite_name,
                                           "benchmark": benchmarks[0],
                                           "metric.name": "analysis-stats",
                                           "metric.type": "numeric",
                                           "metric.unit": "#",
                                           "metric.value": ("<total_reachable_methods>", int),
                                           "metric.better": "lower",
                                           "metric.iteration": 0,
                                           "metric.object": "reachable-methods",
                                       }, ['total_reachable_methods']),
            AnalysisReportJsonFileRule(self.config.image_build_reports_directory, self.config.bundle_output_path,
                                       self.is_gate, self.config.is_bundle_based, {
                                           "bench-suite": self.config.benchmark_suite_name,
                                           "benchmark": benchmarks[0],
                                           "metric.name": "analysis-stats",
                                           "metric.type": "numeric",
                                           "metric.unit": "#",
                                           "metric.value": ("<total_reachable_fields>", int),
                                           "metric.better": "lower",
                                           "metric.iteration": 0,
                                           "metric.object": "reachable-fields",
                                       }, ['total_reachable_fields']),
            AnalysisReportJsonFileRule(self.config.image_build_reports_directory, self.config.bundle_output_path,
                                       self.is_gate, self.config.is_bundle_based, {
                                           "bench-suite": self.config.benchmark_suite_name,
                                           "benchmark": benchmarks[0],
                                           "metric.name": "analysis-stats",
                                           "metric.type": "numeric",
                                           "metric.unit": "B",
                                           "metric.value": ("<total_memory_bytes>", int),
                                           "metric.better": "lower",
                                           "metric.iteration": 0,
                                           "metric.object": "memory"
                                       }, ['total_memory_bytes'])
        ]

    def _get_image_build_stats_rules(self, template: dict, keys: Sequence[str]) -> Sequence[mx_benchmark.Rule]:
        """
        Produces rules that parse the ``image_build_statistics.json`` (and its variants from instrumented builds).

        See also :meth:`NativeImageBenchmarkConfig.get_image_build_stats_file`.

        :param template: Replacement template for the datapoint. Should produce a datapoint from the
                         ``image_build_statistics.json`` file.
        :param keys: List of keys to extract from the json file.
        :return: The list of rules for the various image build stats files
        """

        stats_files = []

        if self.stages_info.should_produce_datapoints(StageName.IMAGE):
            stats_files.append(self.config.get_image_build_stats_file(StageName.IMAGE))

        if self.stages_info.should_produce_datapoints(StageName.INSTRUMENT_IMAGE):
            stats_files.append(self.config.get_image_build_stats_file(StageName.INSTRUMENT_IMAGE))

        return [mx_benchmark.JsonFixedFileRule(f, template, keys) for f in stats_files]

    def image_build_statistics_rules(self, benchmarks):
        objects_list = ["total_array_store",
                        "total_assertion_error_nullary",
                        "total_assertion_error_object",
                        "total_class_cast",
                        "total_division_by_zero",
                        "total_illegal_argument_exception_argument_is_not_an_array",
                        "total_illegal_argument_exception_negative_length",
                        "total_integer_exact_overflow",
                        "total_long_exact_overflow",
                        "total_null_pointer",
                        "total_out_of_bounds"]
        metric_objects = ["total_devirtualized_invokes"]
        for obj in objects_list:
            metric_objects.append(obj + "_after_parse_canonicalization")
            metric_objects.append(obj + "_before_high_tier")
            metric_objects.append(obj + "_after_high_tier")
        rules = []
        for i in range(0, len(metric_objects)):
            rules += self._get_image_build_stats_rules({
                "bench-suite": self.config.benchmark_suite_name,
                "benchmark": benchmarks[0],
                "metric.name": "image-build-stats",
                "metric.type": "numeric",
                "metric.unit": "#",
                "metric.value": ("<" + metric_objects[i] + ">", int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": metric_objects[i].replace("_", "-").replace("total-", ""),
            }, [metric_objects[i]])
        return rules

    def image_build_timers_rules(self, benchmarks):
        measured_phases = ['total', 'setup', 'classlist', 'analysis', 'universe', 'compile', 'layout',
                           'image', 'write']
        if not self.pgo_use_perf:
            # No debug info with perf, [GR-66850]
            measured_phases.append('dbginfo')
        rules = []
        for i in range(0, len(measured_phases)):
            phase = measured_phases[i]
            value_name = phase + "_time"
            rules += self._get_image_build_stats_rules({
                "bench-suite": self.config.benchmark_suite_name,
                "benchmark": benchmarks[0],
                "metric.name": "compile-time",
                "metric.type": "numeric",
                "metric.unit": "ms",
                "metric.value": ("<" + value_name + ">", _native_image_time_to_int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": phase,
            }, [value_name])
            value_name = phase + "_memory"
            rules += self._get_image_build_stats_rules({
                "bench-suite": self.config.benchmark_suite_name,
                "benchmark": benchmarks[0],
                "metric.name": "compile-time",
                "metric.type": "numeric",
                "metric.unit": "B",
                "metric.value": ("<" + value_name + ">", _native_image_time_to_int),
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0,
                "metric.object": phase + "_memory",
            }, [value_name])
        return rules

    def rules(self, output, benchmarks, bmSuiteArgs):
        rules = super().rules(output, benchmarks, bmSuiteArgs)

        if not self.stages_info.fallback_mode and self.stages_info.should_produce_datapoints(
                [StageName.INSTRUMENT_IMAGE, StageName.IMAGE]):
            # Only apply image build rules for the image build stages
            # In fallback mode, we don't produce any rules for the build stages,
            # see NativeImageBenchmarkMixin for more details.
            rules += self.image_build_rules(benchmarks)

        return rules

    @staticmethod
    def move_bundle_output(config: NativeImageBenchmarkConfig, image_path: Path):
        """
        Moves all files from the bundle's ``default`` folder into the benchmark build location.

        Files in the ``other`` folder are not moved.
        """

        if not config.bundle_output_path:
            return

        bundle_output = config.bundle_output_path / "default"

        shutil.copytree(bundle_output, config.output_dir, dirs_exist_ok=True)

        # Quarkus NI bundle builds do not respect the -o flag. We work around this by manually renaming the executable.
        if not image_path.exists() and (
                "quarkus" in config.benchmark_suite_name or "tika" in config.benchmark_suite_name):
            executables = list(config.output_dir.glob("*-runner"))
            assert len(executables) == 1, f"expected one Quarkus executable, found {len(executables)}: {executables}"
            executables[0].rename(image_path)

        mx.rmtree(bundle_output)

    def run_stage_agent(self):
        hotspot_vm_args = ['-ea', '-esa'] if self.is_gate and not self.config.skip_agent_assertions else []
        hotspot_vm_args += self.config.extra_jvm_args
        agentlib_options = [
                               f"native-image-agent=config-output-dir={self.config.config_dir}"] + self.config.extra_agentlib_options
        hotspot_vm_args += ['-agentlib:' + ','.join(agentlib_options)]

        # Native Image has the following option enabled by default. In order to create lambda classes in the same way
        # during the agent run and image run, we need this option for the agent too.
        hotspot_vm_args += ['-Djdk.internal.lambda.disableEagerInitialization=true']

        # Jargraal is very slow with the agent, and libgraal is usually not built for Native Image benchmarks. Therefore, don't use the GraalVM compiler.
        hotspot_vm_args += ['-XX:-UseJVMCICompiler']

        # Limit parallelism because the JVMTI operations in the agent sometimes scale badly.
        if mx.cpu_count() > 8:
            hotspot_vm_args += ['-XX:ActiveProcessorCount=8']

        if self.config.image_vm_args is not None:
            hotspot_vm_args += self.config.image_vm_args

        hotspot_args = hotspot_vm_args + self.config.classpath_arguments + self.config.modulepath_arguments + self.config.system_properties + self.config.executable + self.config.extra_agent_run_args
        with self.get_stage_runner() as s:
            s.execute_command(self, self.generate_java_command(hotspot_args))

        path = self.config.config_dir / "config.zip"
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            for root, _, files in os.walk(self.config.config_dir):
                for file in files:
                    if file.endswith(".json"):
                        zipf.write(os.path.join(root, file),
                                   os.path.relpath(os.path.join(root, file), os.path.join(path, '..')))

    def run_stage_instrument_image(self):
        executable_name_args = ['-o', str(self.config.instrumented_image_path)]
        instrument_args = []
        if self.pgo_use_perf:
            instrument_args += svm_experimental_options([f'-H:PGOPerfSourceMappings={self.config.source_mappings_path}'])
        else:
            instrument_args += ['--pgo-sampling' if self.pgo_sampler_only else '--pgo-instrument', f"-R:ProfilesDumpFile={self.config.profile_path}"]

        if self.jdk_profiles_collect:
            instrument_args += svm_experimental_options(['-H:+AOTPriorityInline', '-H:-SamplingCollect',
                                                         f'-H:ProfilingPackagePrefixes={self.generate_profiling_package_prefixes()}'])

        collection_args = []
        collection_args += svm_experimental_options(
            [f"-H:BuildOutputJSONFile={self.config.get_build_output_json_file(StageName.INSTRUMENT_IMAGE)}"])

        with self.get_stage_runner() as s:
            exit_code = s.execute_command(self,
                                          self.config.base_image_build_args + executable_name_args + instrument_args + collection_args)
            NativeImageVM.move_bundle_output(self.config, self.config.instrumented_image_path)

            if exit_code == 0:
                self._move_image_build_stats_file()

    def _move_image_build_stats_file(self):
        """
        Moves build stats file to a unique location so that it can be attributed to a specific stage and is not
        overwritten later and can be used to extract per-iteration build information.

        The initial location of the ``image_build_statistics.json`` file produced by an image build cannot be changed,
        so a move after the fact is necessary.
        """

        mx.move(
            self.config.image_build_reports_directory / "image_build_statistics.json",
            self.config.get_image_build_stats_file(self.stages_info.current_stage.stage_name)
        )

    def _ensureSamplesAreInProfile(self, profile_path: PathLike):
        # If your benchmark suite fails this assertion and the suite does not expect PGO Sampling profiles (e.g. Truffle workloads)
        # Override checkSamplesInPgo in your suite and have it return False.
        if not self.bmSuite.checkSamplesInPgo():
            return
        # GR-42738 --pgo-sampling does not work with LLVM. Sampling is disabled when doing JDK profiles collection.
        if not self.is_llvm and not self.jdk_profiles_collect:
            with open(profile_path) as profile_file:
                parsed = json.load(profile_file)
                samples = parsed["samplingProfiles"]
                assert len(samples) != 0, f"No sampling profiles in iprof file {profile_path}"
                for sample in samples:
                    assert ":" in sample["ctx"], f"Sampling profiles seem malformed in file {profile_path}"
                    assert len(
                        sample["records"]) == 1, f"Sampling profiles seem to be missing records in file {profile_path}"
                    assert sample["records"][
                               0] > 0, f"Sampling profiles seem to have a 0 in records in file {profile_path}"

    def _collect_perf_results_into_iprof(self):
        with open(self.config.perf_script_path, 'w') as outfile:
            mx.log(f"Started perf script at {self.get_stage_runner().get_timestamp()}")
            exit_code = mx.run(['perf', 'script', f'--input={self.config.perf_data_path}', '--max-stack=2048'], out=outfile)
            if exit_code == 0:
                mx.log(f"Finished perf script at {self.get_stage_runner().get_timestamp()}")
                mx.log(f"Perf compressed data file size: {os.path.getsize(self.config.perf_data_path)} bytes")
                mx.log(f"Perf script file size: {os.path.getsize(self.config.perf_script_path)} bytes")
            else:
                mx.abort(f"Perf script failed with exit code: {exit_code}")
        mx.log(f"Started generating iprof at {self.get_stage_runner().get_timestamp()}")
        nic_command = [os.path.join(self.home(), 'bin', 'native-image-configure'), 'generate-iprof-from-perf', f'--perf={self.config.perf_script_path}', f'--source-mappings={self.config.source_mappings_path}', f'--output-file={self.config.profile_path}']
        if self.pgo_perf_invoke_profile_collection_strategy == PerfInvokeProfileCollectionStrategy.ALL:
            nic_command += ["--enable-experimental-option=SampledVirtualInvokeProfilesAll"]
        elif self.pgo_perf_invoke_profile_collection_strategy == PerfInvokeProfileCollectionStrategy.MULTIPLE_CALLEES:
            nic_command += ["--enable-experimental-option=SampledVirtualInvokeProfilesMultipleCallees"]

        mx.run(nic_command)
        mx.log(f"Finished generating iprof at {self.get_stage_runner().get_timestamp()}")
        os.remove(self.config.perf_script_path)
        os.remove(self.config.perf_data_path)
        os.remove(self.config.source_mappings_path)

    def run_stage_instrument_run(self):
        image_run_cmd = [str(self.config.instrumented_image_path)]
        image_run_cmd += self.config.extra_jvm_args
        image_run_cmd += self.config.extra_profile_run_args
        if self.pgo_use_perf:
            image_run_cmd = ['perf', 'record', '-o', f'{self.config.perf_data_path}', '--call-graph', 'fp', '--freq=999'] + image_run_cmd

        with self.get_stage_runner() as s:
            if self.pgo_use_perf:
                mx.log(f"Started perf record at {self.get_stage_runner().get_timestamp()}")
                exit_code = s.execute_command(self, image_run_cmd)
                mx.log(f"Finished perf record at {self.get_stage_runner().get_timestamp()}")
            else:
                exit_code = s.execute_command(self, image_run_cmd)

            if exit_code == 0:
                if self.pgo_use_perf:
                    self._collect_perf_results_into_iprof()

                if not self.config.profile_path.exists():
                    # The shutdown hook does not trigger for certain apps (GR-60456)
                    mx.abort(
                        f"Profile file {self.config.profile_path} does not exist "
                        f"even though the instrument run terminated successfully with exit code 0. "
                    )
                print(f"Profile file {self.config.profile_path} sha1 is {mx.sha1OfFile(self.config.profile_path)}")
                self._ensureSamplesAreInProfile(self.config.profile_path)
            else:
                print(
                    f"Profile file {self.config.profile_path} not dumped. Instrument run failed with exit code {exit_code}")

    def get_layer_aware_build_args(self) -> List[str]:
        """Return extra build options that are dependent on layer information."""
        current_stage = self.stages_info.current_stage
        layer_aware_build_args = []

        if self.pie and (not self.layered or not current_stage.layer_info.is_shared_library):
            # This option should not be applied to base layers
            layer_aware_build_args += ["-H:NativeLinkerOption=-pie"]

        if self.layered and not current_stage.layer_info.is_shared_library:
            # Set LinkerRPath to point to the directories containing the shared objects of underlying layers
            shared_library_stages = [stage for stage in self.stages_info.complete_stage_list
                                     if current_stage.stage_name == stage.stage_name and stage.is_layered() and stage.layer_info.is_shared_library]
            if len(shared_library_stages) == 0:
                mx.abort("Failed to find any shared library layer image stages!")
            layer_output_dirs = []
            for stage in shared_library_stages:
                _, stage_output_dir = self.config.get_executable_name_and_output_dir_for_stage(stage, self)
                layer_output_dirs.append(stage_output_dir.absolute().as_posix())
            linker_r_path = ",".join(layer_output_dirs)
            app_layer_build_args = [f"-H:LinkerRPath={linker_r_path}"]

            # Set LayerUse to point to the .nil archive of the preceeding layer
            last_shared_library_stage_output_dir = Path(layer_output_dirs[-1])
            nil_archives = list(last_shared_library_stage_output_dir.glob("*.nil"))
            if len(nil_archives) == 0:
                mx.abort(
                    f"Could not determine the .nil archive of the preceding shared library layer!"
                    f" No .nil archives located in '{last_shared_library_stage_output_dir}' directory!"
                )
            if len(nil_archives) > 1:
                mx.abort(
                    f"Could not determine the .nil archive of the preceding shared library layer!"
                    f" Multiple files found: {nil_archives}"
                )
            app_layer_build_args.append(f"-H:LayerUse={nil_archives[0]}")
            layer_aware_build_args += app_layer_build_args

        return layer_aware_build_args

    def run_stage_image(self):
        executable_name_args = ['-o', self.config.final_image_name]
        pgo_args = [f"--pgo={self.config.profile_path}"]
        if self.pgo_use_perf:
            # -g is already set in base_image_build_args if we're not using perf. When using perf, if debug symbols
            # are present they will interfere with sample decoding using source mappings.
            # We still set -g for the optimized build to stay consistent with the other configs.
            # [GR-66850] would allow enabling -g during instrument-image even with perf.
            executable_name_args = ['-g'] + executable_name_args
            pgo_args += svm_experimental_options(['-H:+PGOPrintProfileQuality', '-H:+PGOIgnoreVersionCheck'])
        if self.adopted_jdk_pgo:
            # choose appropriate profiles
            jdk_version = mx_sdk_vm.get_jdk_version_for_profiles()
            jdk_profiles = f'JDK{jdk_version}_PROFILES'
            adopted_profiles_lib = mx.library(jdk_profiles, fatalIfMissing=True)
            adopted_profiles_dir = adopted_profiles_lib.get_path(True)
            adopted_profile = os.path.join(adopted_profiles_dir, 'jdk_profile.iprof')
            jdk_profiles_args = svm_experimental_options([f'-H:AdoptedPGOEnabled={adopted_profile}'])
        else:
            jdk_profiles_args = []
        if self.pgo_exclude_conditional:
            pgo_args += svm_experimental_options(['-H:PGOExcludeProfiles=CONDITIONAL'])

        if self.profile_inference_feature_extraction:
            ml_args = svm_experimental_options(['-H:+MLGraphFeaturesExtraction', '-H:+ProfileInferenceDumpFeatures'])
            dump_file_flag = 'ProfileInferenceDumpFile'
            if dump_file_flag not in ''.join(self.config.base_image_build_args):
                mx.warn(
                    "To dump the profile inference features to a specific location, please set the '{}' flag.".format(
                        dump_file_flag))
        elif self.profile_inference_call_count:
            ml_args = svm_experimental_options(['-H:+MLCallCountProfileInference'])
        elif self.force_profile_inference:
            ml_args = svm_experimental_options(['-H:+MLGraphFeaturesExtraction', '-H:+MLProfileInference'])
        else:
            ml_args = []
        if self.profile_inference_debug:
            ml_debug_args = svm_experimental_options(['-H:LogMLInference={}'.format(self.config.ml_log_dump_path)])
        else:
            ml_debug_args = []

        collection_args = svm_experimental_options(
            [f"-H:BuildOutputJSONFile={self.config.get_build_output_json_file(StageName.IMAGE)}"])
        final_image_command = (self.config.base_image_build_args + executable_name_args
            + (pgo_args if self.pgo_instrumentation else []) + jdk_profiles_args + ml_args + ml_debug_args
            + collection_args + self.get_layer_aware_build_args())
        with self.get_stage_runner() as s:
            exit_code = s.execute_command(self, final_image_command)
            NativeImageVM.move_bundle_output(self.config, self.config.image_path)

            if exit_code == 0:
                self._move_image_build_stats_file()

                if self.use_upx:
                    upx_directory = mx.library("UPX", True).get_path(True)
                    upx_path = os.path.join(upx_directory, mx.exe_suffix("upx"))
                    upx_cmd = [upx_path, str(self.config.image_path)]
                    mx.log(f"Compressing image: {' '.join(upx_cmd)}")
                    write_output = self.stages_info.should_produce_datapoints()
                    mx.run(upx_cmd, out=s.stdout(write_output), err=s.stderr(write_output))

    def run_stage_run(self):
        if not self.config.is_runnable:
            mx.abort(f"Benchmark {self.config.benchmark_suite_name}:{self.config.benchmark_name} is not runnable.")
        with self.get_stage_runner() as s:
            s.execute_command(self,
                              [str(self.config.image_path)] + self.config.extra_jvm_args + self.config.image_run_args)

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        # This is also called with -version to gather information about the Java VM. Since this is not technically a
        # Java VM, we delegate to the superclass
        if '-version' in args:
            return super(NativeImageVM, self).run_java(args, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

        assert self.bmSuite, "Benchmark suite was not registered."
        assert callable(getattr(self.bmSuite, "run_stage", None)), "Benchmark suite is not a NativeImageMixin."

        if not self.bmSuite.stages_info:
            def fullname(cls):
                return cls.__module__ + '.' + cls.__qualname__

            mx.abort(
                f"Invalid Native Image benchmark setup for {fullname(self.bmSuite.__class__)}.\n"
                f"Please see {fullname(NativeImageBenchmarkMixin)} for more information.",
            )

        self.stages_info: StagesInfo = self.bmSuite.stages_info
        assert not self.stages_info.failed, "In case of a failed benchmark, no further calls into the VM should be made"
        assert self.stages_info.vm_used_for_stages == self, f"VM used to prepare stages ({self.stages_info.vm_used_for_stages}) cannot be different from the VM used to run the suite ({self})!"

        if self.stages_info.fallback_mode:
            # In fallback mode, we have to run all requested stages in the same `run_java` invocation.
            # We simply emulate the dispatching of the individual stages as in `NativeImageBenchmarkMixin.intercept_run`
            first_stage = True
            while self.stages_info.has_next_stage():
                self.stages_info.next_stage()
                if first_stage:
                    self._prepare_for_running(args, out, err, cwd, nonZeroIsFatal)
                    first_stage = False
                self.run_single_stage()
        else:
            self._prepare_for_running(args, out, err, cwd, nonZeroIsFatal)
            self.run_single_stage()

        if self.stages_info.failed:
            mx.abort('Exiting the benchmark due to the failure.')

    def _prepare_for_running(self, args, out, err, cwd, nonZeroIsFatal):
        """Initialize the objects and directories necessary for stage running."""
        self.config = NativeImageBenchmarkConfig(self, self.bmSuite, args)
        self.stages = StagesContext(self, out, err, True if self.is_gate else nonZeroIsFatal,
                                    os.path.abspath(cwd if cwd else os.getcwd()))
        self.config.output_dir.mkdir(parents=True, exist_ok=True)
        self.config.config_dir.mkdir(parents=True, exist_ok=True)

    def get_stage_runner(self) -> StageRunner:
        return StageRunner(self.stages)

    def run_single_stage(self):
        stage_to_run = self.stages_info.current_stage.stage_name
        if stage_to_run == StageName.AGENT:
            self.run_stage_agent()
        elif stage_to_run == StageName.INSTRUMENT_IMAGE:
            self.run_stage_instrument_image()
        elif stage_to_run == StageName.INSTRUMENT_RUN:
            self.run_stage_instrument_run()
        elif stage_to_run == StageName.IMAGE:
            self.run_stage_image()
        elif stage_to_run == StageName.RUN:
            self.run_stage_run()
        else:
            raise ValueError(f"Unknown stage {stage_to_run}")

    def prepare_stages(self, bm_suite: NativeImageBenchmarkMixin, bm_suite_args) -> Tuple[List[Stage], List[Stage]]:
        # Default stages for chosen benchmark suite
        stages = [Stage.from_string(s) for s in bm_suite.default_stages()]
        # Removal of stages incompatible with the chosen VM config
        stages = self._remove_stages(stages)
        # Is this a layered native image benchmark?
        complete_stage_list = self._layerize_stages(bm_suite, bm_suite_args, stages)
        # Take user input as final filter
        effective_stages = bm_suite.filter_stages_with_cli_requested_stages(bm_suite_args, complete_stage_list)
        return effective_stages, complete_stage_list

    def _remove_stages(self, stages: List[Stage]) -> List[Stage]:
        # These stages are not executed, even if explicitly requested.
        # Some configurations don't need to/can't run certain stages
        removed_stages: Set[StageName] = set()

        if self.jdk_profiles_collect:
            # forbid image build/run in the profile collection execution mode
            removed_stages.update([StageName.IMAGE, StageName.RUN])
        if self.profile_inference_feature_extraction or self.profile_inference_debug:
            # do not run the image in the profile inference feature extraction or debug mode
            removed_stages.add(StageName.RUN)
        if self.async_sampler:
            removed_stages.update([StageName.INSTRUMENT_IMAGE, StageName.INSTRUMENT_RUN])
        if not self.pgo_instrumentation:
            removed_stages.update([StageName.INSTRUMENT_IMAGE, StageName.INSTRUMENT_RUN])
        if self.layered:
            # Layers have not yet been tested with PGO, unknown what would happen
            removed_stages.update([StageName.INSTRUMENT_IMAGE, StageName.INSTRUMENT_RUN])

        return [s for s in stages if s.stage_name not in removed_stages]

    def _layerize_stages(self, bm_suite, bm_suite_args, stages: List[Stage]) -> List[Stage]:
        if not self.layered:
            return stages

        if not isinstance(bm_suite, LayeredNativeImageBundleBasedBenchmarkMixin):
            mx.abort(f"The selected benchmark suite ({type(bm_suite).__name__}) does not support layered native images!")

        benchmark_layers = bm_suite.layers(bm_suite_args)
        layered_stages = []
        for s in stages:
            assert not s.is_layered(), f"Stage {s} contains layer information before it should!"
            if not s.is_image():
                layered_stages.append(s)
                continue
            for layer in benchmark_layers:
                layered_stages.append(Stage(s.stage_name, layer))
        return layered_stages


# Adds JAVA_HOME VMs so benchmarks can run on GraalVM binaries without building them first.
for java_home_config in ['default', 'pgo', 'g1gc', 'g1gc-pgo', 'upx', 'upx-g1gc', 'quickbuild', 'quickbuild-g1gc']:
    mx_benchmark.add_java_vm(NativeImageVM('native-image-java-home', java_home_config), _suite, 5)


class ObjdumpSectionRule(mx_benchmark.StdOutRule):
    PATTERN = re.compile(r"^ *(?P<section_num>\d+)[ ]+.(?P<section>[a-zA-Z0-9._-]+?) +(?P<size>[0-9a-f]+?) +",
                         re.MULTILINE)
    """
    Regex to match lines in the output of ``objdump -d`` to extract the size of individual sections.
    """

    def __init__(self, executable: Path, benchSuite: str, benchmark: str):
        super().__init__(ObjdumpSectionRule.PATTERN, {
            "benchmark": benchmark,
            "bench-suite": benchSuite,
            "metric.name": "binary-section-size",
            "metric.type": "numeric",
            "metric.unit": "B",
            "metric.value": ("<size>", _native_image_hex_to_int),
            "metric.score-function": "id",
            "metric.better": "lower",
            "metric.iteration": 0,
            "metric.object": ("<section>", str),
        })
        self.executable = executable

    def parse(self, _) -> Iterable[DataPoint]:
        # False positive in pylint, it does not know about the `text` keyword. This is fixed in pylint >=2.5.0
        # pylint: disable=unexpected-keyword-arg
        objdump_output = subprocess.check_output(["objdump", "-h", str(self.executable)], text=True)
        # Instead of the benchmark output, we pass the objdump output
        return super().parse(objdump_output)


class FileSizeRule(mx_benchmark.FixedRule):
    """
    Produces a single datapoint for the size of the given file
    """

    def __init__(self, file: Path, bench_suite: str, benchmark: str, metric_name: str,
                 metric_object: Optional[str] = None):
        """
        :param file: The file to stat
        :param metric_name: Value for the ``metric.name`` key
        :param metric_object: Value for the ``metric.object`` key. Will not be added if None
        """
        datapoint = {
            "bench-suite": bench_suite,
            "benchmark": benchmark,
            "metric.name": metric_name,
            "metric.value": file.stat().st_size,
            "metric.unit": "B",
            "metric.type": "numeric",
            "metric.score-function": "id",
            "metric.better": "lower",
            "metric.iteration": 0,
        }

        if metric_object:
            datapoint["metric.object"] = metric_object

        super().__init__(datapoint)


class AnalysisReportJsonFileRule(mx_benchmark.JsonStdOutFileRule):
    """
    Rule that looks for JSON file names in the output of the benchmark and looks up the files in the report directory

    The path printed in the output may not be the final path where the file was placed (e.g. with ``--bundle-create``).
    To account for that, only the file name is looked up in :attr:`report_directory`, which is guaranteed to be the
    final path of the ``reports`` directory, instead.
    """

    def __init__(self, report_directory, bundle_output_dir, is_diagnostics_mode, is_bundle_based, replacement, keys):
        super().__init__(r"^# Printing analysis results stats to: (?P<path>\S+?)$", "path", replacement, keys)
        self.is_diagnostics_mode = is_diagnostics_mode
        self.is_bundle_based = is_bundle_based
        self.report_directory = report_directory
        self.bundle_output_dir = bundle_output_dir

    def get_diagnostics_dir_name(self, json_file_path) -> Path:
        """Extracts the name of the diagnostics directory, the directory containing the JSON file, from the absolute path of the JSON file."""
        return Path(json_file_path).parent.name

    def get_base_search_dir(self, json_file_path) -> Path:
        """Returns the absolute path to the directory where we expect to find the JSON file containing analysis results stats.

        DEVELOPER NOTE:
        Unfortunately, the analysis results JSON file ends up in different locations depending on:
         - whether the diagnostics mode is enabled (the results end up inside the diagnostics directory)
         - whether the benchmark is bundle based (the diagnostics directory ends up in the "other" subdirectory of the bundle output directory)
        """
        if self.is_diagnostics_mode and self.is_bundle_based:
            return self.bundle_output_dir / "other" / self.get_diagnostics_dir_name(json_file_path)
        if self.is_diagnostics_mode:
            return self.report_directory / self.get_diagnostics_dir_name(json_file_path)
        return self.report_directory

    def getJsonFiles(self, text):
        json_files = super().getJsonFiles(text)
        found_json_files = []
        for json_file_path in json_files:
            json_file_name = os.path.basename(json_file_path)
            expected_json_file_path = os.path.join(self.get_base_search_dir(json_file_path), json_file_name)
            if exists(expected_json_file_path):
                found_json_files.append(expected_json_file_path)
            else:
                assert False, f"Matched file does not exist at {expected_json_file_path}. The file was matched from standard output, with the original path: {json_file_path}"
        return found_json_files


class BaseDaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
    """Base benchmark suite for DaCapo-based benchmarks.

    This suite can only run a single benchmark in one VM invocation.
    """
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def name(self):
        raise NotImplementedError()

    def daCapoClasspathEnvVarName(self):
        raise NotImplementedError()

    def daCapoLibraryName(self):
        raise NotImplementedError()

    def daCapoPath(self):
        dacapo = mx.get_env(self.daCapoClasspathEnvVarName())
        if dacapo:
            return dacapo
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def daCapoIterations(self):
        raise NotImplementedError()

    def daCapoSizes(self):
        raise NotImplementedError()

    def completeBenchmarkList(self, bmSuiteArgs):
        return sorted([bench for bench in self.daCapoIterations().keys() if self.workloadSize() in self.daCapoSizes().get(bench, [])])

    def existingSizes(self):
        return list(dict.fromkeys([s for bench, sizes in self.daCapoSizes().items() for s in sizes]))

    def workloadSize(self):
        raise NotImplementedError()

    def validateEnvironment(self):
        if not self.daCapoPath():
            raise RuntimeError(
                "Neither " + self.daCapoClasspathEnvVarName() + " variable nor " +
                self.daCapoLibraryName() + " library specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-n", "--iterations", default=None)
        parser.add_argument("-sf", default=1, type=float, help="The total number of iterations is equivalent to the value selected by the '-n' flag scaled by this factor.")
        parser.add_argument("-s", "--size", default=None)
        args, remaining = parser.parse_known_args(runArgs)

        if args.size:
            if args.size not in self.existingSizes():
                mx.abort("Unknown workload size '{}'. "
                         "Existing benchmark sizes are: {}".format(args.size, ','.join(self.existingSizes())))

            if args.size != self.workloadSize():
                mx.abort("Mismatch between suite-defined workload size ('{}') "
                         "and user-provided one ('{}')!".format(self.workloadSize(), args.size))

        otherArgs = ["-s", self.workloadSize(), "--preserve"] + remaining

        if benchname == "pmd":
            # GR-61626: known transient which is a benchmark bug (dacapobench/dacapobench#310)
            otherArgs += ["--no-validation"]

        if args.iterations:
            if args.iterations.isdigit():
                return ["-n", str(int(args.sf * int(args.iterations)))] + otherArgs
            if args.iterations == "-1":
                return None
        else:
            iterations = self.daCapoIterations()[benchname]
            if iterations == -1:
                return None
            else:
                iterations = iterations + int(self.getExtraIterationCount(iterations) * args.sf)
                return ["-n", str(iterations)] + otherArgs

    def jarPath(self, benchmark):
        if self.version() == "23.11-MR2-chopin":
            return os.path.join(self.dataLocation(), "launchers", benchmark + ".jar")
        else:
            return self.daCapoPath()

    # The directory that contains `stats`, `launchers`, `dat` and `jar`
    def dataLocation(self):
        if self.version() == "23.11-MR2-chopin":
            basePath = self.daCapoPath()
            subdir = "dacapo-23.11-MR2-chopin"
            if self.minimalArchive():
                subdir += "-minimal"
            return os.path.join(basePath, subdir)
        else:
            raise f"data location is not supported for suite version '{self.version()}'"

    def minimalArchive(self):
        return False

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            raise RuntimeError(
                "Suite runs only a single benchmark.")
        if len(benchmarks) != 1:
            raise RuntimeError(
                "Suite runs only a single benchmark, got: {0}".format(benchmarks))

        benchmark = benchmarks[0]
        runArgs = self.postprocessRunArgs(benchmark, self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return None

        jarPath = self.jarPath(benchmark)
        return self.vmArgs(bmSuiteArgs) + ["-jar"] + [jarPath, benchmark] + runArgs

    def benchmarkList(self, bmSuiteArgs):
        missing_sizes = set(self.daCapoIterations().keys()).difference(set(self.daCapoSizes().keys()))
        if len(missing_sizes) > 0:
            mx.abort("Missing size definitions for benchmark(s): {}".format(missing_sizes))
        return [b for b, it in self.daCapoIterations().items()
                if self.workloadSize() in self.daCapoSizes().get(b, []) and it != -1]

    def successPatterns(self):
        return [
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            re.compile(
                r"^===== DaCapo (?P<version>[^\n]+) ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            re.compile(
                r"^===== DaCapo (?P<version>[^\n]+) ([a-zA-Z0-9_]+) FAILED (warmup|) =====", # pylint: disable=line-too-long
                re.MULTILINE),
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def shorten_vm_flags(self, args):
        return mx_benchmark.Rule.crop_back("...")(' '.join(args))

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return []
        return [
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            mx_benchmark.StdOutRule(
                r"===== DaCapo (?P<version>[^\n]+) (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "final-time",
                    "metric.value": ("<time>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0
                }
            ),
            # The warmup metric should capture all warmup iterations (which print 'completed warmup X') in addition to
            # the last final iteration (which prints 'PASSED').
            mx_benchmark.StdOutRule(
                r"===== DaCapo (?P<version>[^\n]+) (?P<benchmark>[a-zA-Z0-9_]+) ((completed warmup [0-9]+)|PASSED) in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": ("<time>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                }
            )
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(BaseDaCapoBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


_daCapoIterations = {
    "avrora"      : 20,
    "batik"       : 40,
    "eclipse"     : -1,
    "fop"         : 40,
    "h2"          : 25,
    "jython"      : 50,
    "luindex"     : 20,
    "lusearch"    : 40,
    "lusearch-fix": -1,
    "pmd"         : 30,
    "sunflow"     : 35,
    "tomcat"      : -1,
    "tradebeans"  : -1,
    "tradesoap"   : -1,
    "xalan"       : 30,
}


_daCapoSizes = {
    "avrora":       ["default", "small", "large"],
    "batik":        ["default", "small", "large"],
    "eclipse":      ["default", "small", "large"],
    "fop":          ["default", "small"],
    "h2":           ["default", "small", "large", "huge"],
    "jython":       ["default", "small", "large"],
    "luindex":      ["default", "small"],
    "lusearch":     ["default", "small", "large"],
    "lusearch-fix": ["default", "small", "large"],
    "pmd":          ["default", "small", "large"],
    "sunflow":      ["default", "small", "large"],
    "tomcat":       ["default", "small", "large", "huge"],
    "tradebeans":   ["small", "large", "huge"],
    "tradesoap":    ["default", "small", "large", "huge"],
    "xalan":        ["default", "small", "large"]
}


def _is_batik_supported(jdk):
    """
    Determines if Batik runs on the given jdk. Batik's JPEGRegistryEntry contains a reference
    to TruncatedFileException, which is specific to the Sun/Oracle JDK. On a different JDK,
    this results in a NoClassDefFoundError: com/sun/image/codec/jpeg/TruncatedFileException
    """
    try:
        subprocess.check_output([jdk.javap, 'com.sun.image.codec.jpeg.TruncatedFileException'])
        return True
    except subprocess.CalledProcessError:
        mx.warn('Batik uses Sun internal class com.sun.image.codec.jpeg.TruncatedFileException which is not present in ' + jdk.home)
        return False


class DaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
    """DaCapo benchmark suite implementation."""

    def name(self):
        if self.workloadSize() == "default":
            return "dacapo"
        else:
            return "dacapo-{}".format(self.workloadSize())

    def defaultSuiteVersion(self):
        return self.availableSuiteVersions()[-1]

    def availableSuiteVersions(self):
        return ["9.12-bach", "9.12-MR1-bach", "9.12-MR1-git+2baec49", "23.11-MR2-chopin"]

    def workloadSize(self):
        return "default"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_CP"

    def minimalArchive(self):
        # DaCapo Chopin archive is huge. A stripped version without large and huge sizes exists
        # See dacapobench/dacapobench issue #345 on GitHub
        return self.version() in ["23.11-MR2-chopin"] and self.workloadSize() in ["default", "tiny", "small"]

    def daCapoLibraryName(self):
        library = None
        if self.version() == "9.12-bach":  # 2009 release
            library = "DACAPO"
        elif self.version() == "9.12-MR1-bach":  # 2018 maintenance release (January 2018)
            library = "DACAPO_MR1_BACH"
        elif self.version() == "9.12-MR1-git+2baec49":  # commit from July 2018
            library = "DACAPO_MR1_2baec49"
        elif self.version() == "23.11-MR2-chopin":
            library = "DACAPO_23.11_MR2_chopin"
        if library and self.minimalArchive():
            library += "_minimal"
        return library

    def daCapoIterations(self):
        iterations = _daCapoIterations.copy()
        if self.version() == "9.12-bach":
            del iterations["eclipse"]
            del iterations["tradebeans"]
            del iterations["tradesoap"]
            del iterations["lusearch-fix"]
            # Stopped working as of 8u92 on the initial release
            del iterations["tomcat"]

        jdk = mx.get_jdk()
        if jdk.javaCompliance >= '9':
            if "batik" in iterations:
                # batik crashes on JDK9+. This is fixed on the dacapo chopin branch only
                del iterations["batik"]
            if "tradesoap" in iterations:
                # validation fails transiently but frequently in the first iteration in JDK9+
                del iterations["tradesoap"]
            if "avrora" in iterations and jdk.javaCompliance >= '21':
                # avrora uses java.lang.Compiler which was removed in JDK 21 (JDK-8307125)
                del iterations["avrora"]
        elif not _is_batik_supported(java_home_jdk()):
            del iterations["batik"]

        if self.workloadSize() == "small":
            # Ensure sufficient warmup by doubling the number of default iterations for the small configuration
            iterations = {k: (2 * int(v)) if v != -1 else v for k, v in iterations.items()}
        if self.workloadSize() in {"huge", "gargantuan"}:
            # Reduce the default number of iterations for very large workloads to keep the runtime reasonable
            iterations = {k: max(int((int(v)/2)), 5) if v != -1 else v for k, v in iterations.items()}
        return iterations

    def daCapoSizes(self):
        return _daCapoSizes

    def flakySuccessPatterns(self):
        return [
            re.compile(
                r"^javax.ejb.FinderException: Cannot find account for",
                re.MULTILINE),
            re.compile(
                r"^java.lang.Exception: TradeDirect:Login failure for user:",
                re.MULTILINE)
        ]

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(DaCapoBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '16':
            vmArgs += ["--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.net=ALL-UNNAMED"]
        return vmArgs


class DacapoSmallBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "small"


class DacapoLargeBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'large' configuration."""

    def workloadSize(self):
        return "large"


class DacapoHugeBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'huge' configuration."""

    def workloadSize(self):
        return "huge"


mx_benchmark.add_bm_suite(DaCapoBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoSmallBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoLargeBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoHugeBenchmarkSuite())


class DaCapoD3SBenchmarkSuite(DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 Bach benchmark suite implementation with D3S modifications."""

    def name(self):
        return "dacapo-d3s"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_D3S_CP"

    def daCapoLibraryName(self):
        return "DACAPO_D3S"

    def successPatterns(self):
        return []

    def resultFilter(self, values, iteration, endOfWarmupIndex):
        """Count iterations, convert iteration time to milliseconds."""
        # Called from lambda, increment call counter
        iteration['value'] = iteration['value'] + 1
        # Skip warm-up?
        if iteration['value'] < endOfWarmupIndex:
            return None

        values['iteration_time_ms'] = str(int(values['iteration_time_ns']) / 1000 / 1000)
        return values

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return []
        totalIterations = int(runArgs[runArgs.index("-n") + 1])
        out = [
            mx_benchmark.CSVFixedFileRule(
                self.resultCsvFile,
                None,
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": ("<iteration_time_ms>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                },
                # Note: this lambda keeps state to count the row in the CSV file,
                # and it assumes that it will be called only in one traversal of the rows.
                filter_fn=lambda x, counter={'value': 0}: self.resultFilter(x, counter, 0)
            ),
            mx_benchmark.CSVFixedFileRule(
                self.resultCsvFile,
                None,
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "final-time",
                    "metric.value": ("<iteration_time_ms>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                },
                # Note: this lambda keeps state to count the row in the CSV file,
                # and it assumes that it will be called only in one traversal of the rows.
                filter_fn=lambda x, counter={'value': 0}: self.resultFilter(x, counter, totalIterations)
            ),
        ]

        for ev in self.extraEvents:
            out.append(
                mx_benchmark.CSVFixedFileRule(
                    self.resultCsvFile,
                    None,
                    {
                        "benchmark": ("<benchmark>", str),
                        "bench-suite": self.benchSuiteName(),
                        "vm": "jvmci",
                        "config.name": "default",
                        "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                        "metric.name": ev,
                        "metric.value": ("<" + ev + ">", int),
                        "metric.unit": "count",
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.better": "lower",
                        "metric.iteration": ("$iteration", int)
                    }
                )
            )

        return out

    def getUbenchAgentPaths(self):
        archive = mx.library("UBENCH_AGENT_DIST").get_path(resolve=True)

        agentExtractPath = os.path.join(os.path.dirname(archive), 'ubench-agent')
        agentBaseDir = os.path.join(agentExtractPath, 'java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5')
        agentPathToJar = os.path.join(agentBaseDir, 'out', 'lib', 'ubench-agent.jar')
        agentPathNative = os.path.join(agentBaseDir, 'out', 'lib', 'libubench-agent.so')

        return {
            'archive': archive,
            'extract': agentExtractPath,
            'base': agentBaseDir,
            'jar': agentPathToJar,
            'agentpath': agentPathNative
        }

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-o", default=None)
        parser.add_argument("-e", default=None)
        args, remaining = parser.parse_known_args(self.runArgs(bmSuiteArgs))

        if args.o is None:
            self.resultCsvFile = "result.csv"
        else:
            self.resultCsvFile = os.path.abspath(args.o)
        remaining.append("-o")
        remaining.append(self.resultCsvFile)

        if not args.e is None:
            remaining.append("-e")
            remaining.append(args.e)
            self.extraEvents = args.e.split(",")
        else:
            self.extraEvents = []

        parentArgs = DaCapoBenchmarkSuite.createCommandLineArgs(self, benchmarks, ['--'] + remaining)
        if parentArgs is None:
            return None

        paths = self.getUbenchAgentPaths()
        return ['-agentpath:' + paths['agentpath']] + parentArgs

    def run(self, benchmarks, bmSuiteArgs):
        agentPaths = self.getUbenchAgentPaths()

        if not os.path.exists(agentPaths['jar']):
            if not os.path.exists(os.path.join(agentPaths['base'], 'build.xml')):
                zf = zipfile.ZipFile(agentPaths['archive'], 'r')
                zf.extractall(agentPaths['extract'])
            mx.run(['ant', 'lib'], cwd=agentPaths['base'])

        return DaCapoBenchmarkSuite.run(self, benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(DaCapoD3SBenchmarkSuite())


_daCapoScalaConfig = {
    "actors"      : 10,
    "apparat"     : 5,
    "factorie"    : 6,
    "kiama"       : 40,
    "scalac"      : 30,
    "scaladoc"    : 20,
    "scalap"      : 120,
    "scalariform" : 30,
    "scalatest"   : 60,
    "scalaxb"     : 60,
    "specs"       : 20,
    "tmt"         : 12
}

_daCapoScalaSizes = {
    "actors":       ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "apparat":      ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "factorie":     ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "kiama":        ["default", "small"],
    "scalac":       ["default", "small", "large"],
    "scaladoc":     ["default", "small", "large"],
    "scalap":       ["default", "small", "large"],
    "scalariform":  ["default", "tiny", "small", "large", "huge"],
    "scalatest":    ["default", "small"],  # 'large' and 'huge' sizes fail validation
    "scalaxb":      ["default", "tiny", "small", "large", "huge"],
    "specs":        ["default", "small", "large"],
    "tmt":          ["default", "tiny", "small", "large", "huge"]
}


class ScalaDaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def name(self):
        if self.workloadSize() == "default":
            return "scala-dacapo"
        else:
            return "scala-dacapo-{}".format(self.workloadSize())

    def version(self):
        return "0.1.0"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_SCALA_CP"

    def daCapoLibraryName(self):
        return "DACAPO_SCALA"

    def workloadSize(self):
        return "default"

    def daCapoIterations(self):
        result = _daCapoScalaConfig.copy()
        if not java_home_jdk().javaCompliance < '11':
            mx.logv('Removing scala-dacapo:actors from benchmarks because corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)')
            del result['actors']
        if java_home_jdk().javaCompliance >= '16':
            # See GR-29222 for details.
            mx.logv('Removing scala-dacapo:specs from benchmarks because it uses a library that violates module permissions which is no longer allowed in JDK 16 (JDK-8255363)')
            del result['specs']
        return result

    def daCapoSizes(self):
        return _daCapoScalaSizes

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        skip_patterns = super(ScalaDaCapoBenchmarkSuite, self).flakySuccessPatterns()
        if "specs" in benchmarks:
            skip_patterns += [
                re.escape(r"Line count validation failed for stdout.log, expecting 1039 found 1040"),
            ]
        return skip_patterns

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(ScalaDaCapoBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        # Do not add corba module on JDK>=11 (http://openjdk.java.net/jeps/320)
        if java_home_jdk().javaCompliance >= '9' and java_home_jdk().javaCompliance < '11':
            vmArgs += ["--add-modules", "java.corba"]
        return vmArgs


class ScalaDacapoTinyBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "tiny"


class ScalaDacapoSmallBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "small"


class ScalaDacapoLargeBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'large' configuration."""

    def workloadSize(self):
        return "large"

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        skip_patterns = super(ScalaDacapoLargeBenchmarkSuite, self).flakySuccessPatterns()
        if "specs" in benchmarks:
            skip_patterns += [
                re.escape(r"Line count validation failed for stdout.log, expecting 1996 found 1997"),
            ]
        return skip_patterns


class ScalaDacapoHugeBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'huge' configuration."""

    def workloadSize(self):
        return "huge"


class ScalaDacapoGargantuanBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'gargantuan' configuration."""

    def workloadSize(self):
        return "gargantuan"


mx_benchmark.add_bm_suite(ScalaDaCapoBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoTinyBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoSmallBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoLargeBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoHugeBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoGargantuanBenchmarkSuite())


_allSpecJVM2008Benches = [
    'compiler.compiler',
    'compress',
    'crypto.aes',
    'crypto.rsa',
    'crypto.signverify',
    'derby',
    'mpegaudio',
    'scimark.fft.large',
    'scimark.lu.large',
    'scimark.sor.large',
    'scimark.sparse.large',
    'scimark.fft.small',
    'scimark.lu.small',
    'scimark.sor.small',
    'scimark.sparse.small',
    'scimark.monte_carlo',
    'serial',
    'sunflow',
    'xml.transform',
    'xml.validation'
]
_allSpecJVM2008BenchesJDK9 = list(_allSpecJVM2008Benches)
if 'compiler.compiler' in _allSpecJVM2008BenchesJDK9:
    # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9+
    _allSpecJVM2008BenchesJDK9.remove('compiler.compiler')
if 'startup.compiler.compiler' in _allSpecJVM2008BenchesJDK9:
    _allSpecJVM2008BenchesJDK9.remove('startup.compiler.compiler')

class SpecJvm2008BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.TemporaryWorkdirMixin):
    """SpecJVM2008 benchmark suite implementation.

    This benchmark suite can run multiple benchmarks as part of one VM run.
    """
    def name(self):
        return "specjvm2008"

    def benchSuiteName(self, bmSuiteArgs=None):
        # For historical reasons, we have the suffix. Dropping the suffix would require data migration.
        return "specjvm2008-single"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def specJvmPath(self):
        specjvm2008 = mx.get_env("SPECJVM2008")
        if specjvm2008 is None:
            mx.abort("Please set the SPECJVM2008 environment variable to a " +
                     "SPECjvm2008 directory.")
        jarname = "SPECjvm2008.jar"
        jarpath = os.path.join(specjvm2008, jarname)
        if not os.path.exists(jarpath):
            mx.abort("The SPECJVM2008 environment variable points to a directory " +
                     "without the SPECjvm2008.jar file.")

        # copy to newly-created temporary working directory
        working_dir_jarpath =  os.path.abspath(os.path.join(self.workdir, jarname))
        if not os.path.exists(working_dir_jarpath):
            mx.log("copying " + specjvm2008 + " to " + self.workdir)
            shutil.copytree(specjvm2008, self.workdir, dirs_exist_ok=True)

        return working_dir_jarpath

    def validateEnvironment(self):
        if not self.specJvmPath():
            raise RuntimeError(
                "The SPECJVM2008 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            # No benchmark specified in the command line, so run everything.
            benchmarks = self.benchmarkList(bmSuiteArgs)

        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        if "mpegaudio" in benchmarks:
            # GR-51015: run the benchmark on one thread
            # as a workaround for mpegaudio concurrency issues.
            mx.log("Setting benchmark threads to 1 due to race conditions in mpegaudio benchmark")
            runArgs += ["-bt", "1"]

        # The startup benchmarks are executed by spawning a new JVM. However, this new VM doesn't
        # inherit the flags passed to the main process.
        # According to the SpecJVM jar help message, one must use the '--jvmArgs' option to specify
        # options to pass to the startup benchmarks. It has no effect on the non startup benchmarks.
        startupJVMArgs = ["--jvmArgs", " ".join(vmArgs)] if "startup" in ' '.join(benchmarks) else []

        return vmArgs + ["-jar"] + [self.specJvmPath()] + runArgs + benchmarks + startupJVMArgs

    def runArgs(self, bmSuiteArgs):
        runArgs = super(SpecJvm2008BenchmarkSuite, self).runArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '9':
            # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9
            # Skips initial check benchmark which tests for javac.jar on classpath.
            runArgs += ["-ict"]
        return runArgs

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(SpecJvm2008BenchmarkSuite, self).vmArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '16' and \
                ("xml.transform" in self.benchmarkList(bmSuiteArgs) or
                 "startup.xml.transform" in self.benchmarkList(bmSuiteArgs)):
            vmArgs += ["--add-exports=java.xml/com.sun.org.apache.xerces.internal.parsers=ALL-UNNAMED",
                       "--add-exports=java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED"]
        return vmArgs

    def benchmarkList(self, bmSuiteArgs):
        if java_home_jdk().javaCompliance >= '9':
            return _allSpecJVM2008BenchesJDK9
        else:
            return _allSpecJVM2008Benches

    def successPatterns(self):
        return [
            re.compile(
                r"^(Noncompliant c|C)omposite result: (?P<score>[0-9]+((,|\.)[0-9]+)?)( SPECjvm2008 (Base|Peak))? ops/m$", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(r"^Errors in benchmark: ", re.MULTILINE),
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"^Score on (?P<benchmark>[a-zA-Z0-9\._]+): (?P<score>[0-9]+((,|\.)[0-9]+)?) ops/m$", # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "metric.name": "throughput",
                    "metric.value": ("<score>", float),
                    "metric.unit": "op/min",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "higher",
                    "metric.iteration": 0
                }
            )
        ]


mx_benchmark.add_bm_suite(SpecJvm2008BenchmarkSuite())


def _get_specjbb_vmArgs(java_compliance):
    args = [
        "-XX:+UseNUMA",
        "-XX:+AlwaysPreTouch",
        "-XX:-UseAdaptiveSizePolicy",
        "-XX:-UseAdaptiveNUMAChunkSizing"
    ]

    if java_compliance < '16':
        # JDK-8243161: Deprecated in JDK15 and marked obsolete in JDK16
        args.append("-XX:+UseLargePagesInMetaspace")

    if mx.is_linux():
        args.append("-XX:+UseTransparentHugePages")

    return args


class HeapSettingsMixin(object):

    def vmArgshHeapFromEnv(self, vmArgs):
        xmx_is_set = any([arg.startswith("-Xmx") for arg in vmArgs])
        xms_is_set = any([arg.startswith("-Xms") for arg in vmArgs])
        xmn_is_set = any([arg.startswith("-Xmn") for arg in vmArgs])

        heap_args = []

        xms = mx.get_env("XMS", default="")
        xmx = mx.get_env("XMX", default="")
        xmn = mx.get_env("XMN", default="")

        if xms and not xms_is_set:
            heap_args.append("-Xms{}".format(xms))
            mx.log("Setting initial heap size based on XMS env var to -Xms{}".format(xms))

        if xmx and not xmx_is_set:
            heap_args.append("-Xmx{}".format(xmx))
            mx.log("Setting maximum heap size based on XMX env var to -Xmx{}".format(xmx))

        if xmn and not xmn_is_set:
            heap_args.append("-Xmn{}".format(xmn))
            mx.log("Setting young generation size based on XMN env var to -Xmn{}".format(xmn))

        return vmArgs + heap_args


class SpecJbb2015BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, HeapSettingsMixin):
    """SPECjbb2015 benchmark suite implementation.

    This suite has only a single benchmark, and does not allow setting a specific
    benchmark in the command line.
    """
    def name(self):
        return "specjbb2015"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def version(self):
        return "1.04"

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.vmArgshHeapFromEnv(super(SpecJbb2015BenchmarkSuite, self).vmArgs(bmSuiteArgs))
        return _get_specjbb_vmArgs(mx.get_jdk().javaCompliance) + vmArgs

    def specJbbClassPath(self):
        specjbb2015 = mx.get_env("SPECJBB2015")
        if specjbb2015 is None:
            mx.abort("Please set the SPECJBB2015 environment variable to a " +
                     "SPECjbb2015 directory.")
        jbbpath = os.path.join(specjbb2015, "specjbb2015.jar")
        if not os.path.exists(jbbpath):
            mx.abort("The SPECJBB2015 environment variable points to a directory " +
                     "without the specjbb2015.jar file.")
        return jbbpath

    def validateEnvironment(self):
        if not self.specJbbClassPath():
            raise RuntimeError(
                "The SPECJBB2015 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return mx.get_env("SPECJBB2015")

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is not None:
            mx.abort("No benchmark should be specified for the selected suite.")
        vmArgs = self.vmArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '9':
            if java_home_jdk().javaCompliance < '11':
                vmArgs += ["--add-modules", "java.xml.bind"]
            else: # >= '11'
                # JEP-320: Remove the Java EE and CORBA Modules in JDK11 http://openjdk.java.net/jeps/320
                cp = []
                mx.library("JAXB_IMPL_2.1.17").walk_deps(visit=lambda d, _: cp.append(d.get_path(resolve=True)))
                vmArgs += ["--module-path", ":".join(cp), "--add-modules=jaxb.api,jaxb.impl,activation", "--add-opens=java.base/java.lang=jaxb.impl"]
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + ["-jar", self.specJbbClassPath(), "-m", "composite"] + runArgs

    def benchmarkList(self, bmSuiteArgs):
        return ["default"]

    def successPatterns(self):
        return [
            re.compile(
                r"org.spec.jbb.controller: Run finished", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        result_pattern = r"^RUN RESULT: hbIR \(max attempted\) = [0-9]+, hbIR \(settled\) = [0-9]+, max-jOPS = (?P<max>[0-9]+), critical-jOPS = (?P<critical>[0-9]+)$" # pylint: disable=line-too-long
        return [
            mx_benchmark.StdOutRule(
                result_pattern,
                {
                    "benchmark": "default",
                    "vm": "jvmci",
                    "config.name": "default",
                    "metric.name": "max",
                    "metric.value": ("<max>", float),
                    "metric.unit": "jops",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "higher",
                    "metric.iteration": 0
                }
            ),
            mx_benchmark.StdOutRule(
                result_pattern,
                {
                    "benchmark": "default",
                    "vm": "jvmci",
                    "config.name": "default",
                    "metric.name": "critical",
                    "metric.value": ("<critical>", float),
                    "metric.unit": "jops",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "higher",
                    "metric.iteration": 0
                }
            )
        ]


mx_benchmark.add_bm_suite(SpecJbb2015BenchmarkSuite())


_baristaConfig = {
    "benchmarks": {
        "vanilla-hello-world": {},
        "micronaut-hello-world": {},
        "micronaut-shopcart": {},
        "micronaut-similarity": {},
        "micronaut-pegasus": {},
        "quarkus-hello-world": {},
        "quarkus-tika": {},
        "spring-hello-world": {},
        "spring-petclinic": {},
        "helidon-hello-world": {},
        "vertx-hello-world": {},
        "ktor-hello-world": {},
        "play-scala-hello-world": {},
    },
    # Should currently only contain round numbers due to the field incorrectly being indexed as integer in the DB (GR-57487)
    "latency_percentiles": [50.0, 75.0, 90.0, 99.0, 100.0],
    "rss_percentiles": [100, 99, 98, 97, 96, 95, 90, 75, 50, 25],
    "supported_trackers": [mx_benchmark.EnergyConsumptionTracker],
}

class BaristaBenchmarkSuite(mx_benchmark.CustomHarnessBenchmarkSuite):
    """Barista benchmark suite implementation. A collection of microservice workloads running on the Barista harness.

    The run arguments are passed to the Barista harness.
    If you want to run something like `hwloc-bind` or `taskset` prefixed before the app, you should use the '--cmd-app-prefix' Barista harness option.
    If you want to pass options to the app, you should use the '--app-args' Barista harness option.
    """
    def __init__(self, custom_harness_command: mx_benchmark.CustomHarnessCommand = None):
        if custom_harness_command is None:
            custom_harness_command = BaristaBenchmarkSuite.BaristaCommand()
        super().__init__(custom_harness_command, supported_trackers=_baristaConfig["supported_trackers"])
        self._version = None

    def readBaristaVersionFromPyproject(self):
        # tomllib was included in python standard library with version 3.11
        try:
            import tomllib
            with open(self.baristaProjectConfigurationPath(), mode="rb") as pyproject:
                return tomllib.load(pyproject)["project"]["version"]
        except ImportError:
            pass

        # fallback to 'toml' library if tomllib is not present
        try:
            import toml
            with open(self.baristaProjectConfigurationPath(), mode="rt") as pyproject:
                return toml.loads(pyproject.read())["project"]["version"]
        except ImportError:
            mx.warn("Could not read the Barista version from the project's `pyproject.toml` file because there is no toml parser installed. Use python3.11+ or install `toml` with pip.")
        return self.defaultSuiteVersion()

    def version(self):
        if self._version is None:
            self._version = self.readBaristaVersionFromPyproject()
        return self._version

    def name(self):
        return "barista"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchmarkName(self):
        return self.execution_context.benchmark

    def benchmarkList(self, bmSuiteArgs):
        exclude = []
        # Barista currently does not support running 'micronaut-pegasus' on the JVM - running it results in a crash (GR-59793)
        exclude.append("micronaut-pegasus")
        return [b for b in self.completeBenchmarkList(bmSuiteArgs) if not b in exclude]

    def completeBenchmarkList(self, bmSuiteArgs):
        return _baristaConfig["benchmarks"].keys()

    def baristaDirectoryPath(self):
        barista_home = mx.get_env("BARISTA_HOME")
        if barista_home is None or not os.path.isdir(barista_home):
            mx.abort("Please set the BARISTA_HOME environment variable to a " +
                     "Barista benchmark suite directory.")
        return barista_home

    def baristaApplicationDirectoryPath(self, benchmark: str) -> Path:
        return Path(self.baristaDirectoryPath()) / "benchmarks" / benchmark

    def baristaFilePath(self, file_name):
        barista_home = self.baristaDirectoryPath()
        file_path = os.path.abspath(os.path.join(barista_home, file_name))
        if not os.path.isfile(file_path):
            raise FileNotFoundError("The BARISTA_HOME environment variable points to a directory " +
                                    f"that does not contain a '{file_name}' file.")
        return file_path

    def baristaProjectConfigurationPath(self):
        return self.baristaFilePath("pyproject.toml")

    def baristaBuilderPath(self):
        return self.baristaFilePath("build")

    def baristaHarnessPath(self):
        return self.baristaFilePath("barista")

    def baristaHarnessBenchmarkName(self):
        return _baristaConfig["benchmarks"][self.benchmarkName()].get("barista-bench-name", self.benchmarkName())

    def baristaHarnessBenchmarkWorkload(self):
        return _baristaConfig["benchmarks"][self.benchmarkName()].get("workload")

    def validateEnvironment(self):
        self.baristaProjectConfigurationPath()
        self.baristaHarnessPath()

    def new_execution_context(self, vm: Vm, benchmarks: List[str], bmSuiteArgs: List[str]) -> SingleBenchmarkExecutionContext:
        return SingleBenchmarkExecutionContext(self, vm, benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        # Pass the VM options, BaristaCommand will form the final command.
        return self.vmArgs(bmSuiteArgs)

    def all_command_line_args_are_vm_args(self):
        return True

    def rules(self, out, benchmarks, bmSuiteArgs):
        json_file_group_name = "barista_json_results_file_path"
        json_file_pattern = fr"Saving all collected metrics to JSON file: (?P<{json_file_group_name}>\S+?)$"
        all_rules = []

        # Startup
        all_rules.append(mx_benchmark.JsonArrayStdOutFileRule(json_file_pattern, json_file_group_name, {
            "benchmark": self.benchmarkName(),
            "metric.name": "request-time",
            "metric.type": "numeric",
            "metric.unit": "ms",
            "metric.value": ("<startup.measurements.response_time>", float),
            "metric.better": "lower",
            "metric.iteration": ("<startup.measurements.iteration>", int),
            "load-tester.id": ("<startup.id>", str),
            "load-tester.method-type": "requests"
        }, ["startup.id", "startup.measurements.iteration", "startup.measurements.response_time"]))

        # Warmup
        all_rules.append(mx_benchmark.JsonArrayStdOutFileRule(json_file_pattern, json_file_group_name, {
            "benchmark": self.benchmarkName(),
            "metric.name": "warmup",
            "metric.type": "numeric",
            "metric.unit": "op/s",
            "metric.value": ("<warmup.measurements.throughput>", float),
            "metric.better": "higher",
            "metric.iteration": ("<warmup.measurements.iteration>", int),
            "load-tester.id": ("<warmup.id>", str),
            "load-tester.command": ("<warmup.measurements.command>", str)
        }, ["warmup.id", "warmup.measurements.iteration", "warmup.measurements.throughput", "warmup.measurements.command"]))

        # Throughput
        all_rules.append(mx_benchmark.JsonArrayStdOutFileRule(json_file_pattern, json_file_group_name, {
            "benchmark": self.benchmarkName(),
            "metric.name": "throughput",
            "metric.type": "numeric",
            "metric.unit": "op/s",
            "metric.value": ("<throughput.measurements.throughput>", float),
            "metric.better": "higher",
            "metric.iteration": ("<throughput.measurements.iteration>", int),
            "load-tester.id": ("<throughput.id>", str),
            "load-tester.command": ("<throughput.measurements.command>", str)
        }, ["throughput.id", "throughput.measurements.iteration", "throughput.measurements.throughput", "throughput.measurements.command"]))

        # Latency
        all_rules += [mx_benchmark.JsonArrayStdOutFileRule(json_file_pattern, json_file_group_name, {
            "benchmark": self.benchmarkName(),
            "metric.name": "latency",
            "metric.type": "numeric",
            "metric.unit": "ms",
            "metric.value": (f"<latency__measurements__final_measurements__p_values__{float(percentile)}>", float),
            "metric.percentile": float(percentile),
            "metric.better": "lower",
            "metric.iteration": ("<latency__measurements__final_measurements__iteration>", int),
            "load-tester.id": ("<latency__id>", str),
            "load-tester.command": ("<latency__measurements__final_measurements__command>", str)
        }, [
            "latency__id",
            "latency__measurements__final_measurements__iteration",
            f"latency__measurements__final_measurements__p_values__{float(percentile)}",
            "latency__measurements__final_measurements__command"
        ], indexer_str="__") for percentile in _baristaConfig["latency_percentiles"]]

        # Resource Usage
        all_rules += [mx_benchmark.JsonArrayStdOutFileRule(json_file_pattern, json_file_group_name, {
            "benchmark": self.benchmarkName(),
            "metric.name": "rss",
            "metric.type": "numeric",
            "metric.unit": "MB",
            "metric.value": (f"<resource_usage__rss__p{float(percentile)}>", float),
            "metric.percentile": float(percentile),
            "metric.better": "lower",
        }, [
            f"resource_usage__rss__p{float(percentile)}"
        ], indexer_str="__") for percentile in _baristaConfig["rss_percentiles"]]

        return all_rules

    def validateStdoutWithDimensions(self, out, benchmarks, bmSuiteArgs, retcode=None, dims=None, extraRules=None) -> DataPoints:
        datapoints = super().validateStdoutWithDimensions(out, benchmarks, bmSuiteArgs, retcode=retcode, dims=dims, extraRules=extraRules)
        datapoints = self.computeDerivedDatapoints(datapoints)
        datapoints = self.extendDatapoints(datapoints)
        return datapoints

    def computeDerivedDatapoints(self, datapoints: DataPoints) -> DataPoints:
        """Adds derived datapoints to the list of datapoints captured from the benchmark stdout or generated files.
        Adds datapoints such as:
        * rss-distribution: copies of rss datapoints, naming more clearly indicates that the metric comprises the
          distribution represented by percentile values
        * latency-distribution: copies of latency datapoints, naming more clearly indicates that the metric comprises
          the distribution represented by percentile values
        * max-rss: copied from specific rss percentile values
        * time-to-first-response: copied from response_time with iteration 0
        * max-time: copied from response_time with the highest value
        * ops-per-GB-second: computed as throughput divided by max-rss
        """
        # rss-distribution
        rss_dps = filter(lambda dp: dp["metric.name"] == "rss", datapoints)
        for rss_dp in rss_dps:
            rss_dp_copy = rss_dp.copy()
            rss_dp_copy["metric.name"] = "rss-distribution"
            datapoints.append(rss_dp_copy)

        # latency-distribution
        latency_dps = filter(lambda dp: dp["metric.name"] == "latency", datapoints)
        for latency_dp in latency_dps:
            latency_dp_copy = latency_dp.copy()
            latency_dp_copy["metric.name"] = "latency-distribution"
            datapoints.append(latency_dp_copy)

        # max-rss
        percentile_to_copy_into_max_rss = float(mx_benchmark.RssPercentilesTracker.MaxRssCopyRule.percentile_to_copy_into_max_rss)
        rss_dp_to_copy_from = next(filter(lambda dp: dp["metric.name"] == "rss" and dp["metric.percentile"] == percentile_to_copy_into_max_rss, datapoints), None)
        if rss_dp_to_copy_from is not None:
            max_rss_dp = rss_dp_to_copy_from.copy()
            max_rss_dp["metric.name"] = "max-rss"
            del max_rss_dp["metric.percentile"]
            datapoints.append(max_rss_dp)

        # time-to-first-response
        first_request_time_dp = next(filter(lambda dp: dp["metric.name"] == "request-time" and dp["metric.iteration"] == 0, datapoints), None)
        if first_request_time_dp is not None:
            time_to_first_response_dp = first_request_time_dp.copy()
            time_to_first_response_dp["metric.name"] = "time-to-first-response"
            del time_to_first_response_dp["metric.iteration"]
            datapoints.append(time_to_first_response_dp)

        # max-time
        request_time_dps = filter(lambda dp: dp["metric.name"] == "request-time", datapoints)
        worst_request_time_dp = max(request_time_dps, key=lambda dp: dp["metric.value"], default=None)
        if worst_request_time_dp is not None:
            max_time_dp = worst_request_time_dp.copy()
            max_time_dp["metric.name"] = "max-time"
            del max_time_dp["metric.iteration"]
            datapoints.append(max_time_dp)

        # ops-per-GB-second
        throughput_dp = next(filter(lambda dp: dp["metric.name"] == "throughput", datapoints), None)
        if rss_dp_to_copy_from is not None and throughput_dp is not None:
            ops_per_gb_sec = throughput_dp["metric.value"] / (max_rss_dp["metric.value"] / 1024)
            ops_per_gb_sec_dp = throughput_dp.copy()
            ops_per_gb_sec_dp["metric.name"] = "ops-per-GB-second"
            ops_per_gb_sec_dp["metric.unit"] = "op/GB*s"
            ops_per_gb_sec_dp["metric.value"] = ops_per_gb_sec
            datapoints.append(ops_per_gb_sec_dp)

        return datapoints

    def extendDatapoints(self, datapoints: DataPoints) -> DataPoints:
        """
        Extends the datapoints with 'load-tester' fields.
        Relies on the intermediate 'load-tester.command' field being set up beforehand.
        """
        for datapoint in datapoints:
            # Expand the 'load-tester' field group
            if "load-tester.command" in datapoint:
                command = datapoint["load-tester.command"].split()

                if command[0] == "wrk":
                    datapoint["load-tester.method-type"] = "throughput"
                else:
                    datapoint["load-tester.method-type"] = "latency"
                datapoint["load-tester.options"] = ' '.join(command[1:])
                if "-R" in command:
                    datapoint["load-tester.rate"] = int(command[command.index("-R") + 1])
                if "-c" in command:
                    datapoint["load-tester.connections"] = int(command[command.index("-c") + 1])
                if "-t" in command:
                    datapoint["load-tester.threads"] = int(command[command.index("-t") + 1])

                del datapoint["load-tester.command"]
        return datapoints

    class BaristaCommand(mx_benchmark.CustomHarnessCommand):
        """Maps a JVM command into a command tailored for the Barista harness.
        """
        def _regexFindInCommand(self, cmd, pattern):
            """Searches through the words of a command for a regex pattern.

            :param list[str] cmd: Command to search through.
            :param str pattern: Regex pattern to search for.
            :return: The match if one is found, None otherwise.
            :rtype: re.Match
            """
            for word in cmd:
                m = re.search(pattern, word)
                if m:
                    return m
            return None

        def _updateCommandOption(self, cmd, option_name, option_short_name, new_value):
            """Updates command option value, concatenates the new value with the existing one, if it is present.

            :param list[str] cmd: Command to be updated.
            :param str option_name: Name of the option to be updated.
            :param str option_short_name: Short name of the option to be updated.
            :param str new_value: New value for the option, to be concatenated to the existing value, if it is present.
            :return: Updated command.
            :rtype: list[str]
            """
            option_pattern = f"^(?:{option_name}=|{option_short_name}=)(.+)$"
            existing_option_match = self._regexFindInCommand(cmd, option_pattern)
            if existing_option_match:
                cmd.remove(existing_option_match.group(0))
                new_value = f"{new_value} {existing_option_match.group(1)}"
            cmd.append(f"{option_name}={new_value}")

        def _energyTrackerExtraOptions(self, suite: BaristaBenchmarkSuite):
            """Returns extra options necessary for correct benchmark results when using the 'energy' tracker."""
            if not isinstance(suite._tracker, mx_benchmark.EnergyConsumptionTracker):
                return []

            required_barista_version = "0.4.5"
            if mx.VersionSpec(suite.version()) < mx.VersionSpec(required_barista_version):
                mx.abort(
                    f"The 'energy' tracker is not supported for barista benchmarks before Barista version '{required_barista_version}'."
                    " Please update your Barista repository in order to use the 'energy' tracker! Aborting!"
                )

            extra_options = []
            # If baseline has to be measured, wait for the measurement duration before looking up the app process
            if suite._tracker.baseline_power is None:
                extra_options += ["--cmd-app-prefix-init-sleep", f"{suite._tracker.baseline_duration}"]
            # Ensure that the workload is independent from the performance of the VM
            # We want to track the energy needed for a set amount of work
            extra_options += ["--startup-iteration-count", "0"]
            extra_options += ["--warmup-iteration-count", "0"]
            extra_options += ["--throughput-iteration-count", "0"]
            return extra_options

        def produceHarnessCommand(self, cmd, suite):
            """Maps a JVM command into a command tailored for the Barista harness.

            :param list[str] cmd: JVM command to be mapped.
            :param BaristaBenchmarkSuite suite: Barista benchmark suite running the benchmark on the Barista harness.
            :return: Command tailored for the Barista harness.
            :rtype: list[str]
            """
            if not isinstance(suite, BaristaBenchmarkSuite):
                raise TypeError(f"Expected an instance of {BaristaBenchmarkSuite.__name__}, instead got an instance of {suite.__class__.__name__}")
            jvm_cmd = cmd

            # Extract the path to the JVM distribution from the JVM command
            java_exe_pattern = os.path.join("^(.*)", "bin", "java$")
            java_exe_match = self._regexFindInCommand(jvm_cmd, java_exe_pattern)
            if not java_exe_match:
                raise ValueError(f"Could not find the path to the java executable in: {jvm_cmd}")

            # Extract VM options and command prefix from the JVM command
            index_of_java_exe = jvm_cmd.index(java_exe_match.group(0))
            jvm_cmd_prefix = jvm_cmd[:index_of_java_exe]
            jvm_vm_options = jvm_cmd[index_of_java_exe + 1:]

            # Verify that the run arguments don't already contain a "--mode" option
            run_args = suite.runArgs(suite.execution_context.bmSuiteArgs) + self._energyTrackerExtraOptions(suite)
            mode_pattern = r"^(?:-m|--mode)(=.*)?$"
            mode_match = self._regexFindInCommand(run_args, mode_pattern)
            if mode_match:
                raise ValueError(f"You should not set the Barista '--mode' option manually! Found '{mode_match.group(0)}' in the run arguments!")

            # Get bench name and workload to use in the barista harness - we might have custom named benchmarks that need to be mapped
            barista_bench_name = suite.baristaHarnessBenchmarkName()
            barista_workload = suite.baristaHarnessBenchmarkWorkload()

            # Construct the Barista command
            barista_cmd = [suite.baristaHarnessPath()]
            barista_cmd.append(f"--java-home={java_exe_match.group(1)}")
            if barista_workload is not None:
                barista_cmd.append(f"--config={barista_workload}")
            barista_cmd += run_args
            if jvm_vm_options:
                self._updateCommandOption(barista_cmd, "--vm-options", "-v", " ".join(jvm_vm_options))
            if jvm_cmd_prefix:
                self._updateCommandOption(barista_cmd, "--cmd-app-prefix", "-p", " ".join(jvm_cmd_prefix))
            barista_cmd += ["--mode", "jvm"]
            barista_cmd.append(barista_bench_name)
            return barista_cmd


mx_benchmark.add_bm_suite(BaristaBenchmarkSuite())


_renaissanceConfig = {
    "akka-uct"         : 24,
    "als"              : 60,
    "chi-square"       : 60,
    "db-shootout"      : 16,
    "dec-tree"         : 40,
    "dotty"            : 50,
    "finagle-chirper"  : 90,
    "finagle-http"     : 12,
    "fj-kmeans"        : 30,
    "future-genetic"   : 50,
    "gauss-mix"        : 40,
    "log-regression"   : 20,
    "mnemonics"        : 16,
    "movie-lens"       : 20,
    "naive-bayes"      : 30,
    "neo4j-analytics"  : 20,
    "page-rank"        : 20,
    "par-mnemonics"    : 16,
    "philosophers"     : 30,
    "reactors"         : 10,
    "rx-scrabble"      : 80,
    "scala-doku"       : 20,
    "scala-kmeans"     : 50,
    "scala-stm-bench7" : 60,
    "scrabble"         : 50
}


class RenaissanceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
    """Renaissance benchmark suite implementation.
    """
    def name(self):
        return "renaissance"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def renaissanceLibraryName(self):
        return "RENAISSANCE_{}".format(self.version())

    def renaissanceIterations(self):
        benchmarks = _renaissanceConfig.copy()

        if mx.get_jdk().javaCompliance >= '21' and self.version() in ["0.14.1"]:
            del benchmarks["als"]
            del benchmarks["chi-square"]
            del benchmarks["dec-tree"]
            del benchmarks["gauss-mix"]
            del benchmarks["log-regression"]
            del benchmarks["movie-lens"]
            del benchmarks["naive-bayes"]
            del benchmarks["page-rank"]
            del benchmarks["neo4j-analytics"]

        if self.version() in ["0.15.0"]:
            del benchmarks["chi-square"]
            del benchmarks["gauss-mix"]
            del benchmarks["page-rank"]
            del benchmarks["movie-lens"]
            if mx.get_jdk().javaCompliance >= '24':
                # JEP 486 Security Manager removal causes the following benchmarks to fail unconditionally.
                # See https://github.com/renaissance-benchmarks/renaissance/pull/453 for a temporary fix.
                del benchmarks["als"]
                del benchmarks["dec-tree"]
                del benchmarks["log-regression"]
                del benchmarks["naive-bayes"]

        if self.version() in ["0.16.0"]:
            del benchmarks["chi-square"]
            del benchmarks["gauss-mix"]
            del benchmarks["page-rank"]
            del benchmarks["movie-lens"]
            if mx.get_jdk().javaCompliance >= '26':
                # JDK-8361426 removes jdk.internal.ref.Cleaner and causes the following to fail
                del benchmarks["als"]
                del benchmarks["db-shootout"]
                del benchmarks["dec-tree"]
                del benchmarks["log-regression"]
                del benchmarks["naive-bayes"]

        return benchmarks

    def completeBenchmarkList(self, bmSuiteArgs):
        return sorted(bench for bench in _renaissanceConfig)

    def defaultSuiteVersion(self):
        return self.availableSuiteVersions()[-1]

    def availableSuiteVersions(self):
        return ["0.14.1", "0.15.0", "0.16.0"]

    def renaissancePath(self):
        lib = mx.library(self.renaissanceLibraryName())
        if lib:
            return lib.get_path(True)
        return None

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-r", default=None)
        parser.add_argument("-sf", default=1, type=float, help="The total number of iterations is equivalent to the value selected by the '-r' flag scaled by this factor.")
        args, remaining = parser.parse_known_args(runArgs)
        if args.r:
            if args.r.isdigit():
                return ["-r", str(int(args.sf * int(args.r)))] + remaining
            if args.r == "-1":
                return remaining
        else:
            iterations = self.renaissanceIterations()[benchname]
            if iterations == -1:
                return remaining
            else:
                return ["-r", str(int(args.sf * iterations))] + remaining

    def vmArgs(self, bmSuiteArgs):
        vm_args = super(RenaissanceBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        return vm_args

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) == 0:
            mx.abort("Must specify at least one benchmark.")
        else:
            benchArg = ",".join(benchmarks)

        vmArgs = self.vmArgs(bmSuiteArgs)
        sparkBenchmarks = set([
            "als",
            "chi-square",
            "dec-tree",
            "gauss-mix",
            "log-regression",
            "movie-lens",
            "naive-bayes",
            "page-rank",
        ])

        if any(benchmark in sparkBenchmarks for benchmark in benchmarks):
            # Spark benchmarks require a higher stack size than default in some configurations.
            # [JDK-8303076] [GR-44499] [GR-50671]
            vmArgs.append("-Xss1500K")

        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        return (vmArgs + ["-jar", self.renaissancePath()] + runArgs + [benchArg])

    def benchmarkList(self, bmSuiteArgs):
        return [b for b, it in self.renaissanceIterations().items() if it != -1]

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]",
                re.MULTILINE)
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"====== (?P<benchmark>[a-zA-Z0-9_\-]+) \((?P<benchgroup>[a-zA-Z0-9_\-]+)\)( \[(?P<config>[a-zA-Z0-9_\-]+)\])?, iteration (?P<iteration>[0-9]+) completed \((?P<value>[0-9]+(.[0-9]*)?) ms\) ======",  # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "benchmark-configuration": ("<config>", str),
                    "bench-suite": self.benchSuiteName(),
                    "bench-suite-version": self.version(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "metric.name": "warmup",
                    "metric.value": ("<value>", float),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("<iteration>", int),
                }
            )
        ]

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = super(RenaissanceBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceBenchmarkSuite())


_awfyConfig = {
    "DeltaBlue"  : 12000,
    "Richards"   : 100,
    "Json"       : 100,
    "CD"         : 250,
    "Havlak"     : 1500,
    "Bounce"     : 1500,
    "List"       : 1500,
    "Mandelbrot" : 500,
    "NBody"      : 250000,
    "Permute"    : 1000,
    "Queens"     : 1000,
    "Sieve"      : 3000,
    "Storage"    : 1000,
    "Towers"     : 600
}

class AWFYBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """Are we fast yet? benchmark suite implementation.
    """
    def name(self):
        return "awfy"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def version(self):
        return "1.1"

    def awfyLibraryName(self):
        return "AWFY_{}".format(self.version())

    def awfyBenchmarkParam(self):
        return _awfyConfig.copy()

    def awfyIterations(self):
        return 100 # Iterations for a "Slow VM" from AWFY rebench.conf

    def awfyPath(self):
        lib = mx.library(self.awfyLibraryName())
        if lib:
            return lib.get_path(True)
        return None

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument('-i', '--iterations', default=self.awfyIterations())
        parser.add_argument('-p', '--param', default=self.awfyBenchmarkParam()[benchname])
        args, remaining = parser.parse_known_args(runArgs)
        return [str(args.iterations), str(args.param)] + remaining

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark to run.")
        else:
            benchArg = benchmarks[0]
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        return (self.vmArgs(bmSuiteArgs) + ["-cp", self.awfyPath()] + ["Harness"] + [benchArg] + runArgs)

    def benchmarkList(self, bmSuiteArgs):
        return sorted(_awfyConfig.keys())

    def successPatterns(self):
        return [r"(?P<benchmark>[a-zA-Z0-9_\-]+): iterations=(?P<iterations>[0-9]+) average: (?P<average>[0-9]+)us total: (?P<total>[0-9]+)us"]

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"(?P<benchmark>[a-zA-Z0-9_\-]+): iterations=(?P<iterations>[0-9]+) runtime: (?P<runtime>[0-9]+)us",
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "config.vm-flags": ' '.join(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": ("<runtime>", float),
                    "metric.unit": "us",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                }
            )
        ]

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = super(AWFYBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(AWFYBenchmarkSuite())




STAGE_LAST_SUCCESSFUL_PREFIX: str = "Successfully finished the last specified stage:"
STAGE_SUCCESSFUL_PREFIX: str = "Successfully finished stage:"
STAGE_SKIPPED_PREFIX: str = "Skipping stage:"

SUCCESSFUL_STAGE_PATTERNS = [re.compile(p, re.MULTILINE) for p in [
    # Produced when the last stage as requested by the user (see: NativeImageBenchmarkMixin.stages) finished
    rf"{STAGE_LAST_SUCCESSFUL_PREFIX}.*$",
    # Produced when any other stage finishes
    rf"{STAGE_SUCCESSFUL_PREFIX}.*$",
    # Produced when a stage is skipped for some reason (e.g. the specific configuration does not require it)
    rf"{STAGE_SKIPPED_PREFIX}.*$",
]]
"""
List of regex patterns to use in successPatterns() to match the successful completion of a Native Image benchmark stage.
Native Image benchmarks run in stages and not all stages have the expected success pattern for the benchmark suite,
which generally only matches the benchmark run output and nothing in the build output.
Instead, each benchmark stage produces one of the following messages to signal that the stage completed and bypass the
original success pattern check.
"""


def parse_prefixed_args(prefix, args):
    ret = []
    for arg in args:
        if arg.startswith(prefix):
            words_in_arg = arg.split(' ')
            if len(words_in_arg) > 1:
                # We will monitor our logs for this warning and then fix this method once we are certain no jobs break (GR-60134)
                mx.warn(f"A prefixed arg that includes spaces is being parsed, ignoring everything that comes after the first space! The arg in question is: '{arg}'")
            parsed = words_in_arg[0].split(prefix)[1]
            if parsed not in ret:
                ret.append(parsed)
    return ret

def parse_prefixed_arg(prefix, args, errorMsg):
    ret = parse_prefixed_args(prefix, args)
    if len(ret) > 1:
        mx.abort(errorMsg)
    elif len(ret) < 1:
        return None
    else:
        return ret[0]


def convertValue(table, value, fromUnit, toUnit):
    if fromUnit not in table:
        mx.abort("Unexpected unit: " + fromUnit)
    fromFactor = float(table[fromUnit])

    if toUnit not in table:
        mx.abort("Unexpected unit: " + fromUnit)
    toFactor = float(table[toUnit])

    return float((value * fromFactor) / toFactor)


timeUnitTable = {
    'ns':   1,
    'us':   1000,
    'ms':   1000 * 1000,
    's':    1000 * 1000 * 1000,
    'min':  60 * 1000 * 1000 * 1000,
    'h':    60 * 60 * 1000 * 1000 * 1000
}


tputUnitTable = {
    'op/ns':    1.0,
    'op/us':    1.0/1000,
    'op/ms':    1.0/(1000 * 1000),
    'op/s':     1.0/(1000 * 1000 * 1000),
    'op/min':   1.0/(60 * 1000 * 1000 * 1000),
    'op/h':     1.0/(60 * 60 * 1000 * 1000 * 1000)
}


memUnitTable = {
    'B':    1,
    'kB':   1000,
    'MB':   1000 * 1000,
    'GB':   1000 * 1000 * 1000,
    'KiB':  1024,
    'MiB':  1024 * 1024,
    'GiB':  1024 * 1024 * 1024
}


def strip_args_with_number(strip_args, args):
    """Removes arguments (specified in `strip_args`) from `args`.

    The stripped arguments are expected to have a number value. For single character arguments (e.g. `-X`) the space
    before the value might be omitted (e.g. `-X8`). In this case only one element is removed from `args`. Otherwise
    (e.g. `-X 8`), two elements are removed from `args`.
    """

    if not isinstance(strip_args, list):
        strip_args = [strip_args]

    def _strip_arg_with_number_gen(_strip_arg, _args):
        skip_next = False
        for arg in _args:
            if skip_next:
                # skip value of argument
                skip_next = False
                continue
            if arg.startswith(_strip_arg):
                if arg == _strip_arg:
                    # full match - value is the next argument `-i 10`
                    skip_next = True
                    continue
                # partial match at begin - either a different option or value without space separator `-i10`
                if len(_strip_arg) == 2 and _strip_arg.startswith('-'):
                    # only look at single character options
                    remainder_arg = arg[len(_strip_arg):]
                    try:
                        int(remainder_arg)
                        # remainder is a number - skip the current arg
                        continue
                    except ValueError:
                        # not a number - probably a different option
                        pass
            # add arg to result
            yield arg

    result = args
    for strip_arg in strip_args:
        result = _strip_arg_with_number_gen(strip_arg, result)
    return list(result)

def adjust_arg_with_number(arg_name, new_value: int, user_args):
    """
    Sets the argument value of `arg_name` in `user_args` with `new_value`.
    If `arg_name` is already present in `user_args`, the value will be replaced.
    If `arg_name` is not already present, the argument and corresponding value will be added.
    """

    return [arg_name, str(new_value)] + strip_args_with_number(arg_name, user_args)

class PerfInvokeProfileCollectionStrategy(Enum):
    """
    The strategy for extracting virtual invoke method profiles from perf sampling data.
    ALL: Generate a profile for each callsite.
    MULTIPLE_CALLEES: Only generate profiles for callsites with at least 2 different sampled targets.
    """
    ALL = "invoke"
    MULTIPLE_CALLEES = "invoke-multiple"

    def __str__(self):
        return self.value

class StagesInfo:
    """
    Holds information about benchmark stages that should be persisted across multiple stages in the same
    ``mx benchmark`` command.

    Is used to pass data between the benchmark suite and the underlying :class:`mx_benchmark.Vm`.

    The information about the stages is entirely computed within :meth:`NativeImageVM.prepare_stages`. There are two
    lists of significance:
    * The stages that are actually executed for this benchmark, returned by :attr:`effective_stages`.
    * The complete list of stages that are required by the selected VM and benchmark combination,
    returned by :attr:`complete_stage_list`.

    The stages actually executed are a subset of the complete list of stages. If no filtering of stages is made by the
    user, then these two lists will be identical.
    """

    def __init__(self, effective_stages: List[Stage], complete_stage_list: List[Stage], vm_used_for_stages: NativeImageVM, fallback_mode: bool = False):
        """
        :param effective_stages:    List of stages that will actually be executed in the benchmark.
                                     See also :meth:`NativeImageBenchmarkMixin.filter_stages_with_cli_requested_stages`
        :param complete_stage_list: List of all stages required by the VM and selected benchmark, not impacted by user
                                     requests made through command line options.
                                     See also :meth:`NativeImageBenchmarkMixin.prepare_stages`
        :param vm_used_for_stages:  Virtual machine used in the computation of stages.
        :param fallback_mode:       Whether the legacy mode of executing stages should be used in the benchmark.
                                     See also :meth:`NativeImageVM.run_java`
        """
        self._complete_stage_list: List[Stage] = complete_stage_list
        self._effective_stages: List[Stage] = effective_stages
        self._current_stage: Optional[Stage] = None
        self._current_stage_index: int = -1
        self._stages_till_now: List[Stage] = []
        self._vm_used_for_stages: mx_benchmark.Vm = vm_used_for_stages
        self._failed: bool = False
        self._fallback_mode: bool = fallback_mode

    @property
    def fallback_mode(self) -> bool:
        return self._fallback_mode

    @property
    def effective_stages(self) -> List[Stage]:
        """
        List of stages that are actually executed for this benchmark.
        A subset of the complete stage list.
        """
        return self._effective_stages

    @property
    def complete_stage_list(self) -> List[Stage]:
        """
        Complete list of stages that are required by the selected VM and benchmark combination.
        A superset of the effective stages.
        """
        return self._complete_stage_list

    @property
    def vm_used_for_stages(self) -> NativeImageVM:
        """Virtual machine used in the computation of stages."""
        return self._vm_used_for_stages

    @property
    def current_stage(self) -> Stage:
        """The stage that is currently being executed."""
        assert self._current_stage, "No current stage set"
        return self._current_stage

    @property
    def last_stage(self) -> Stage:
        return self.effective_stages[-1]

    @property
    def failed(self) -> bool:
        return self._failed

    @property
    def stages_till_now(self) -> List[Stage]:
        """
        List of stages executed so far, all of which have been successful.

        Does not include the current stage.
        """
        return self._stages_till_now

    def has_next_stage(self) -> bool:
        """Whether there are more stages to be executed in the benchmark."""
        return self._current_stage_index + 1 < len(self.effective_stages)

    def next_stage(self) -> Stage:
        """Progress to the next stage in the list of effective stages."""
        self._current_stage_index += 1
        assert self._current_stage_index < len(self.effective_stages), "Next stage requested after all have been executed"
        self._current_stage = self.effective_stages[self._current_stage_index]
        return self.current_stage

    def success(self) -> None:
        """Called when the current stage has finished successfully"""
        self._stages_till_now.append(self.current_stage)

    def fail(self) -> None:
        """Called when the current stage finished with an error"""
        self._failed = True

    def should_produce_datapoints(self, stages: Union[None, StageName, Collection[StageName]] = None) -> bool:
        """
        Whether, under the current configuration, datapoints should be produced for any of the given stage.

        In fallback mode, we only produce datapoints for the ``image`` and ``run`` stage because stages are not run
        individually and datapoints from other stages may not be distinguishable from the ``image`` and ``run`` stage.

        :param stages: If None, the current effective stage is used. A single stage will be treated as a singleton list
        """

        if not stages:
            stages = [self.current_stage.stage_name]
        elif not isinstance(stages, collections.abc.Collection):
            stages = [stages]

        if self.fallback_mode:
            # In fallback mode, all datapoints are generated at once and not in a specific stage, checking whether the
            # given stage matches the current stage will almost never yield the sensible result
            return not any([s.is_final() for s in stages])
        else:
            return self.current_stage.stage_name in stages

    def get_latest_image_stage(self) -> Optional[Stage]:
        if self.current_stage.is_image():
            return self.current_stage

        latest_image_stage = None
        for stage in self.complete_stage_list:
            if stage == self.current_stage:
                return latest_image_stage
            if stage.is_image():
                latest_image_stage = stage
        complete_stage_list_stringified = ', '.join([str(s) for s in self.complete_stage_list])
        mx.abort(f"Could not find current stage '{self.current_stage}' in complete list of stages: [{complete_stage_list_stringified}]!")

class NativeImageBenchmarkMixin(object):
    """
    Mixin extended by :class:`BenchmarkSuite` classes to enable a JVM bench suite to run as a Native Image benchmark.

    IMPORTANT: All Native Image benchmarks (including JVM benchmarks that are also used in Native Image benchmarks) must
    explicitly call :meth:`NativeImageBenchmarkMixin.intercept_run` in order for benchmarking to work.
    See description of that method for more information.

    Native Image benchmarks are run in stages: agent, instrument-image, instrument-run, image, run
    Each stage produces intermediate files required by subsequent phases until the final ``run`` stage runs the final
    Native Image executable to produce performance results.
    However, it is worth collecting certain performance metrics from any of the other stages as well (e.g. compiler
    performance).

    The mixin's ``intercept_run`` method calls into the ``NativeImageVM`` once per stage to run that stage and produce
    datapoints for that stage only.
    This is a bit of a hack since each stage can be seen as its own full benchmark execution (does some operations and
    produces datapoints), but it works well in most cases.

    Limitations
    -----------

    This mode of benchmarking cannot fully support arbitrary benchmarking suites without modification.

    Because of each stage effectively being its own benchmark execution, rules that unconditionally produce datapoints
    will misbehave as they will produce datapoints in each stage (even though, it is likely only meant to produce
    benchmark performance datapoints).
    For example, if a benchmark suite has rules that read the performance data from a file (e.g. JMH), those rules will
    happily read that file and produce performance data in every stage (even the ``image`` stages).
    Such rules need to be modified to only trigger in the desired stages. Either by parsing the file location out of the
    benchmark output or by writing some Native Image specific logic (with :meth:`is_native_mode`)
    An example for such a workaround are :class:`mx_benchmark.JMHBenchmarkSuiteBase` and its subclasses (see
    ``get_jmh_result_file``, its usages and its Native Image specific implementation in
    :class:`mx_graal_benchmark.JMHNativeImageBenchmarkMixin`)

    If the benchmark suite itself dispatches into the VM multiple times (in addition to the mixin doing it once per
    stage), care must be taken in which order this happens.
    If these multiple dispatches happen in a (transitive) callee of ``intercept_run``, each dispatch will first happen
    for the first stage and only after the next stage will be run. In that order, a subsequent dispatch may overwrite
    intermediate files of the previous dispatch of the same stage (e.g. executables).
    For this to work as expected, ``intercept_run`` needs to be a callee of these multiple dispatches, i.e. these
    multiple dispatches also need to happen in the ``run`` method and (indirectly) call ``intercept_run``.

    If these limitations cannot be worked around, using the fallback mode may be required, with the caveat that it
    provides limited functionality.
    This was done for example in :meth:`mx_graal_benchmark.JMHNativeImageBenchmarkMixin.fallback_mode_reason`.

    Fallback Mode
    -------------

    Fallback mode is for benchmarks that are fundamentally incompatible with how this mixin dispatches into the
    ``NativeImageVM`` once per stage (e.g. JMH with the ``--jmh-run-individually`` flag).
    The conditional logic to enable fallback mode can be implemented by overriding :meth:`fallback_mode_reason`.

    In fallback mode, we only call into the VM once and it runs all stages in sequence. This limits what kind of
    performance data we can accurately collect (e.g. it is impossible to distinguish benchmark output from the
    ``instrument-run`` and ``run`` phases).
    Because of that, only the output of the ``image`` and ``run`` stages is returned from the VM (the remainder is still
    printed, but not used for regex matching when creating datapoints).

    In addition, the ``NativeImageVM`` will not produce any rules to generate extra datapoints (e.g. for image build
    metrics). If the benchmark suite dispatches into the VM multiple times (like for JMH with
    ``--jmh-run-individually``), those rules cannot work correctly since they cannot know for which individual benchmark
    to produce datapoint(s).

    Finally, the user cannot select only a subset of stages to run (using ``-Dnative-image.benchmark.stages``).
    All stages required for that benchmark are always run together.
    """

    def __init__(self):
        self.benchmark_name = None
        self.stages_info: Optional[StagesInfo] = None

    def benchmarkName(self):
        if not self.benchmark_name:
            raise NotImplementedError()
        return self.benchmark_name

    def fallback_mode_reason(self, bm_suite_args: List[str]) -> Optional[str]:
        """
        Reason why this Native Image benchmark should run in fallback mode.

        :return: None if no fallback is required. Otherwise, a non-empty string describing why fallback mode is necessary
        """
        return None

    def intercept_run(self, super_delegate: BenchmarkSuite, benchmarks, bm_suite_args: List[str]) -> DataPoints:
        """
        Intercepts the main benchmark execution (:meth:`BenchmarkSuite.run`) and runs a series of benchmark stages
        required for Native Image benchmarks in series.
        For non-native-image benchmarks, this simply delegates to the caller's ``super().run`` method.

        The stages are requested by the user (see :meth:`NativeImageBenchmarkMixin.stages`).

        There are no good ways to just intercept ``run`` in arbitrary ``BenchmarkSuite``s, so each
        :class:`BenchmarkSuite` subclass that is intended for Native Image benchmarking needs to make sure that the
        :meth:`BenchmarkSuite.run` calls into this method like this::

            def run(self, benchmarks, bm_suite_args: List[str]) -> DataPoints:
                return self.intercept_run(super(), benchmarks, bm_suite_args)

        It is fine if this implemented in a common (Native Image-specific) superclass of multiple benchmark suites, as
        long as the method is not overriden in a subclass in an incompatible way.

        :param super_delegate: A reference to the caller class' superclass in method-resolution order (MRO).
        :param benchmarks: Passed to :meth:`BenchmarkSuite.run`
        :param bm_suite_args: Passed to :meth:`BenchmarkSuite.run`
        :return: Datapoints accumulated from all stages
        """
        if not self.is_native_mode(bm_suite_args):
            # This is not a Native Image benchmark, just run the benchmark as regular
            return super_delegate.run(benchmarks, bm_suite_args)

        datapoints: List[DataPoint] = []
        fallback_reason = self.fallback_mode_reason(bm_suite_args)

        vm = self.get_vm_registry().get_vm_from_suite_args(bm_suite_args)
        with self.new_execution_context(vm, benchmarks, bm_suite_args):
            effective_stages, complete_stage_list = vm.prepare_stages(self, bm_suite_args)
            self.stages_info = StagesInfo(effective_stages, complete_stage_list, vm, bool(fallback_reason))

            if self.stages_info.fallback_mode:
                # In fallback mode, all stages are run at once. There is matching code in `NativeImageVM.run_java` for this.
                mx.log(f"Running benchmark in fallback mode (reason: {fallback_reason})")
                datapoints += super_delegate.run(benchmarks, bm_suite_args)
            else:
                while self.stages_info.has_next_stage():
                    stage = self.stages_info.next_stage()
                    # Start the actual benchmark execution. The stages_info attribute will be used by the NativeImageVM to
                    # determine which stage to run this time.
                    stage_dps = super_delegate.run(benchmarks, bm_suite_args)
                    NativeImageBenchmarkMixin._inject_stage_keys(stage_dps, stage)
                    datapoints += stage_dps

            self.stages_info = None
            return datapoints

    @staticmethod
    def _inject_stage_keys(dps: DataPoints, stage: Stage) -> None:
        """
        Modifies the ``host-vm-config`` key based on the current stage.
        For the agent and instrument stages ``-agent`` and ``-instrument`` are appended to distinguish the datapoints
        from the main ``image`` and ``run`` phases.

        :param dps: List of datapoints, modified in-place
        :param stage: The stage the datapoints were generated in
        """

        if stage.is_agent():
            host_vm_suffix = "-agent"
        elif stage.is_instrument():
            host_vm_suffix = "-instrument"
        elif stage.is_final():
            host_vm_suffix = ""
        else:
            raise ValueError(f"Unknown stage {stage}")

        if stage.is_layered() and stage.layer_info.is_shared_library:
            host_vm_suffix += f"-layer{stage.layer_info.index}"

        for dp in dps:
            dp["host-vm-config"] += host_vm_suffix

    def run_stage(self, vm, stage: Stage, command, out, err, cwd, nonZeroIsFatal):
        final_command = command
        # Apply command mapper hooks (e.g. trackers) for all stages that run benchmark workloads
        if self.stages_info.should_produce_datapoints(stage.stage_name):
            hooks_compatible_with_stage = [
                (name, hook, suite)
                for name, hook, suite in vm.command_mapper_hooks
                if hook.should_apply(stage)
            ]
            final_command = mx.apply_command_mapper_hooks(command, hooks_compatible_with_stage)
        return mx.run(final_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, env=self.get_stage_env())

    def is_native_mode(self, bm_suite_args: List[str]):
        """Checks whether the given arguments request a Native Image benchmark"""
        jvm_flag = self.jvm(bm_suite_args)
        if not jvm_flag:
            # In case the --jvm argument was not given explicitly, let the registry load the appropriate vm and extract
            # the name from there.
            # This is much more expensive, so it is only used as a fallback
            jvm_flag = self.get_vm_registry().get_vm_from_suite_args(bm_suite_args).name()
        return "native-image" in jvm_flag

    def apply_command_mapper_hooks(self, cmd, vm):
        return mx.apply_command_mapper_hooks(cmd, vm.command_mapper_hooks)

    def extra_image_build_argument(self, _, args):
        return parse_prefixed_args('-Dnative-image.benchmark.extra-image-build-argument=', args)

    def extraVmArgs(self):
        assert self.dist
        distribution = mx.distribution(self.dist)
        assert distribution.isJARDistribution()
        jdk = mx.get_jdk(distribution.javaCompliance)
        add_opens_add_extracts = []
        if mx_benchmark.mx_benchmark_compatibility().jmh_dist_benchmark_extracts_add_opens_from_manifest():
            add_opens_add_extracts = mx_benchmark._add_opens_and_exports_from_manifest(distribution.path)
        return mx.get_runtime_jvm_args([self.dist], jdk=jdk, exclude_names=mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes) + add_opens_add_extracts

    def extra_jvm_arg(self, benchmark, args):
        return parse_prefixed_args('-Dnative-image.benchmark.extra-jvm-arg=', args)

    def extra_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the final image.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-run-arg=', args)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the agent run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-agent-run-arg=', args)

    def extra_agentlib_options(self, benchmark, args, image_run_args):
        """Returns additional native-image-agent options.

        The returned options are added to the agentlib:native-image-agent option list.
        The config-output-dir is configured by the benchmark runner and cannot be overridden.
        """

        # All Renaissance Spark benchmarks require lambda class predefinition, so we need this additional option that
        # is used for the class predefinition feature. See GR-37506
        return ['experimental-class-define-support'] if (benchmark in ['chi-square', 'gauss-mix', 'movie-lens', 'page-rank']) else []

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        """Returns all arguments passed to the profiling run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        # either use extra profile run args if set or otherwise the extra run args
        extra_profile_run_args = parse_prefixed_args('-Dnative-image.benchmark.extra-profile-run-arg=', args) or parse_prefixed_args('-Dnative-image.benchmark.extra-run-arg=', args)
        return image_run_args + extra_profile_run_args

    def extra_agent_profile_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the agent profiling run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-agent-profile-run-arg=', args)

    def benchmark_output_dir(self, _, args):
        parsed_args = parse_prefixed_args('-Dnative-image.benchmark.benchmark-output-dir=', args)
        if parsed_args:
            return parsed_args[0]
        else:
            return None

    def filter_stages_with_cli_requested_stages(self, bm_suite_args: List[str], stages: List[Stage]) -> List[Stage]:
        """
        If the `-Dnative-image.benchmark.stages=` arg is present, filter out any stage that is not requested.
        A stage is requested if:
         * it's name without layer information is specified
           (e.g. '-Dnative-image.benchmark.stages=image' means all stages with the 'image' stage name are requested,
           for standalone images this means the 'image' stage, for layered images this means every 'image-layer*'
           stage)
         * it's full name is specified
           (e.g. '-Dnative-image.benchmark.stages=image-layer0' means that just the 'image' stage for the 0th layer is
           requested)
        """
        args = self.vmArgs(bm_suite_args)
        parsed_arg = parse_prefixed_arg('-Dnative-image.benchmark.stages=', args, 'Native Image benchmark stages should only be specified once.')
        if not parsed_arg:
            return stages

        # Abort if fallback_mode is on
        fallback_reason = self.fallback_mode_reason(bm_suite_args)
        if fallback_reason:
            mx.abort(
                "This benchmarking configuration is running in fallback mode and does not support selection of benchmark stages using -Dnative-image.benchmark.stages"
                f"Reason: {fallback_reason}\n"
                f"Arguments: {bm_suite_args}"
            )

        cli_requested_stages = parsed_arg.split(',')
        return [s for s in stages if any(s.is_requested(requested) for requested in cli_requested_stages)]

    def default_stages(self) -> List[str]:
        """Default list of stages to run if none have been specified."""
        return ["agent", "instrument-image", "instrument-run", "image", "run"]

    def skip_agent_assertions(self, _, args):
        parsed_args = parse_prefixed_args('-Dnative-image.benchmark.skip-agent-assertions=', args)
        if 'true' in parsed_args or 'True' in parsed_args:
            return True
        elif 'false' in parsed_args or 'False' in parsed_args:
            return False
        else:
            return None

    def build_assertions(self, benchmark, is_gate):
        # We are skipping build assertions when a benchmark is not a part of a gate.
        return ['-J-ea', '-J-esa'] if is_gate else []

    # Override and return False if this suite should not check for samples in runs with PGO
    def checkSamplesInPgo(self):
        return True

    def get_stage_env(self) -> Optional[dict]:
        """Return the environment to be used when executing a stage."""
        return None

    def executable_name(self) -> Optional[str]:
        """Override to allow suites to control the executable name used in image builds."""
        return None


def measureTimeToFirstResponse(bmSuite):
    protocolHost = bmSuite.serviceHost()
    servicePath = bmSuite.serviceEndpoint()
    if not (protocolHost.startswith('http') or protocolHost.startswith('https')):
        protocolHost = "http://" + protocolHost
    if not (servicePath.startswith('/') or protocolHost.endswith('/')):
        servicePath = '/' + servicePath
    url = "{}:{}{}".format(protocolHost, bmSuite.servicePort(), servicePath)

    measurementStartTime = time.time()
    sentRequests = 0
    receivedNon200Responses = 0
    last_report_time = time.time()
    req = urllib.request.Request(url, headers=bmSuite.requestHeaders())
    while time.time() - measurementStartTime < 120:
        time.sleep(.0001)
        if sentRequests > 0 and time.time() - last_report_time > 10:
            last_report_time = time.time()
            mx.log("Sent {:d} requests so far but did not receive a response with code 200 yet.".format(sentRequests))

        try:
            sentRequests += 1
            res = urllib.request.urlopen(req, timeout=10)
            responseCode = res.getcode()
            if responseCode == 200:
                processStartTime = mx.get_last_subprocess_start_time()
                finishTime = datetime.datetime.now()
                msToFirstResponse = (finishTime - processStartTime).total_seconds() * 1000
                currentOutput = "First response received in {} ms".format(msToFirstResponse)
                bmSuite.timeToFirstResponseOutputs.append(currentOutput)
                mx.log(currentOutput)
                return
            else:
                if receivedNon200Responses < 10:
                    mx.log("Received a response but it had response code " + str(responseCode) + " instead of 200")
                elif receivedNon200Responses == 10:
                    mx.log("No more response codes will be printed (already printed 10 response codes)")
                receivedNon200Responses += 1
        except IOError:
            pass

    mx.abort("Failed to measure time to first response. Service not reachable at " + url)


class BaseMicroserviceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, NativeImageBenchmarkMixin):
    """
    Base class for Microservice benchmark suites. A Microservice is an application that opens a port that is ready to
    receive requests. This benchmark suite runs a tester process in the background (such as Wrk2) and run a
    Microservice application in foreground. Once the tester finishes stress testing the application, the tester process
    terminates and the application is killed with SIGTERM.

    The number of environment variables affects the startup time of all microservice frameworks. To ensure benchmark
    stability, we therefore execute those benchmarks with an empty set of environment variables.
    """

    NumMeasureTimeToFirstResponse = 10

    def __init__(self):
        super(BaseMicroserviceBenchmarkSuite, self).__init__()
        self.timeToFirstResponseOutputs = []
        self.startupOutput = ''
        self.peakOutput = ''
        self.latencyOutput = ''
        self.bmSuiteArgs = None
        self.workloadPath = None
        self.measureLatency = None
        self.measureFirstResponse = None
        self.measureStartup = None
        self.measurePeak = None
        self.parser = argparse.ArgumentParser()
        self.parser.add_argument("--workload-configuration", type=str, default=None, help="Path to workload configuration.")
        self.parser.add_argument("--skip-latency-measurements", action='store_true', help="Determines if the latency measurements should be skipped.")
        self.parser.add_argument("--skip-first-response-measurements", action='store_true', help="Determines if the time-to-first-response measurements should be skipped.")
        self.parser.add_argument("--skip-startup-measurements", action='store_true', help="Determines if the startup performance measurements should be skipped.")
        self.parser.add_argument("--skip-peak-measurements", action='store_true', help="Determines if the peak performance measurements should be skipped.")

    def benchMicroserviceName(self):
        """
        Returns the microservice name. The convention here is that the benchmark name contains two elements separated
        by a hyphen ('-'):
        - the microservice name (shopcart, for example);
        - the tester tool name (wrk, for example).

        :return: Microservice name.
        :rtype: str
        """

        if len(self.benchSuiteName().split('-', 1)) < 2:
            mx.abort("Invalid benchmark suite name: " + self.benchSuiteName())
        return self.benchSuiteName().split("-", 1)[0]

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def validateReturnCode(self, retcode):
        return retcode == 143

    def defaultWorkloadPath(self, benchmarkName):
        """Returns the workload configuration path.

        :return: Path to configuration file.
        :rtype: str
        """
        raise NotImplementedError()

    def workloadConfigurationPath(self):
        if self.workloadPath:
            mx.log("Using user-provided workload configuration file: {0}".format(self.workloadPath))
            return self.workloadPath
        else:
            return self.defaultWorkloadPath(self.benchmarkName())

    def applicationPath(self):
        """Returns the application Jar path.

        :return: Path to Jar.
        :rtype: str
        """
        raise NotImplementedError()

    def serviceHost(self):
        """Returns the microservice host.

        :return: Host used to access the microservice.
        :rtype: str
        """
        return 'localhost'

    def servicePort(self):
        """Returns the microservice port.

        :return: Port that the microservice is using to receive requests.
        :rtype: int
        """
        return 8080

    def serviceEndpoint(self):
        """Returns the microservice path that checks if the service is running.

        :return: service path
        :rtype: str
        """
        return ''

    def requestHeaders(self):
        """Returns extra headers to be sent when markign requests to the service endpoint..
        :rtype: dict[str, str]
        """
        return {}

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.vmArgs(bmSuiteArgs) + ["-jar", self.applicationPath()]

    @staticmethod
    def waitForPort(port, timeout=60):
        try:
            import psutil
        except ImportError:
            # Note: abort fails to find the process (not registered yet in mx) if we are too fast failing here.
            time.sleep(5)
            mx.abort("Failed to import {0} dependency module: psutil".format(BaseMicroserviceBenchmarkSuite.__name__))
        for _ in range(timeout + 1):
            for proc in psutil.process_iter():
                try:
                    for conns in proc.connections(kind='inet'):
                        if conns.laddr.port == port:
                            return proc
                except:
                    pass
            time.sleep(1)
        return None

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, applicationOutput, dims = super(BaseMicroserviceBenchmarkSuite, self).runAndReturnStdOut(benchmarks, bmSuiteArgs)
        result = ret_code, "\n".join(self.timeToFirstResponseOutputs) + '\n' + self.startupOutput + '\n' + self.peakOutput + '\n' + self.latencyOutput + '\n' + applicationOutput, dims

        # For HotSpot, the rules are executed after every execution and for Native Image the rules are applied after each stage.
        # So, it is necessary to reset the data to avoid duplication of datapoints.
        self.timeToFirstResponseOutputs = []
        self.startupOutput = ''
        self.peakOutput = ''
        self.latencyOutput = ''

        return result

    @staticmethod
    def terminateApplication(port):
        proc = BaseMicroserviceBenchmarkSuite.waitForPort(port, 0)
        if proc:
            proc.send_signal(signal.SIGTERM)
            return True
        else:
            return False

    @staticmethod
    def testTimeToFirstResponseInBackground(benchmarkSuite):
        mx.log("--------------------------------------------")
        mx.log("Started time-to-first-response measurements.")
        mx.log("--------------------------------------------")
        measureTimeToFirstResponse(benchmarkSuite)
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testStartupPerformanceInBackground(benchmarkSuite):
        mx.log("-----------------------------------------")
        mx.log("Started startup performance measurements.")
        mx.log("-----------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testStartupPerformance()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testPeakPerformanceInBackground(benchmarkSuite, warmup=True):
        mx.log("--------------------------------------")
        mx.log("Started peak performance measurements.")
        mx.log("--------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testPeakPerformance(warmup)
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def calibrateLatencyTestInBackground(benchmarkSuite):
        mx.log("---------------------------------------------")
        mx.log("Started calibration for latency measurements.")
        mx.log("---------------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.calibrateLatencyTest()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testLatencyInBackground(benchmarkSuite):
        mx.log("-----------------------------")
        mx.log("Started latency measurements.")
        mx.log("-----------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testLatency()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    def get_env(self):
        return {}

    def get_image_env(self):
        # Use the existing environment by default.
        return os.environ

    def run_stage(self, vm, stage: Stage, server_command, out, err, cwd, nonZeroIsFatal):
        if stage.is_image():
            # For image stages, we just run the given command
            with PatchEnv(self.get_image_env()):
                return super(BaseMicroserviceBenchmarkSuite, self).run_stage(vm, stage, server_command, out, err, cwd, nonZeroIsFatal)
        else:
            if stage.stage_name == StageName.RUN:
                serverCommandWithTracker = self.apply_command_mapper_hooks(server_command, vm)

                mx_benchmark.disable_tracker()
                serverCommandWithoutTracker = self.apply_command_mapper_hooks(server_command, vm)
                mx_benchmark.enable_tracker()

                # Measure time-to-first-response multiple times (without any command mapper hooks as those affect the measurement significantly)
                for _ in range(self.NumMeasureTimeToFirstResponse):
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(target=BaseMicroserviceBenchmarkSuite.testTimeToFirstResponseInBackground, args=[self])
                        returnCode = mx.run(server_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                        measurementThread.join()
                    if not self.validateReturnCode(returnCode):
                        mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                # Measure startup performance (without RSS tracker)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testStartupPerformanceInBackground, [self])
                    returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                if not self.validateReturnCode(returnCode):
                    mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                # Measure peak performance (with all command mapper hooks)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self])
                    returnCode = mx.run(serverCommandWithTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                if not self.validateReturnCode(returnCode):
                    mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                if self.measureLatency:
                    if not any([c.get("requests-per-second") for c in self.loadConfiguration("latency")]):
                        # Calibrate for latency measurements (without RSS tracker) if no fixed request rate has been provided in the config
                        with PatchEnv(self.get_env()):
                            measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.calibrateLatencyTestInBackground, [self])
                            returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                            measurementThread.join()
                        if not self.validateReturnCode(returnCode):
                            mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                    # Measure latency (without RSS tracker)
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testLatencyInBackground, [self])
                        returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                        measurementThread.join()
                    if not self.validateReturnCode(returnCode):
                        mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                return returnCode
            elif stage.stage_name in [StageName.AGENT, StageName.INSTRUMENT_RUN]:
                # For the agent and the instrumented run, it is sufficient to run the peak performance workload.
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self, False])
                    returnCode = mx.run(server_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                return returnCode
            else:
                mx.abort(f"Unexpected stage: {stage}")

    def startDaemonThread(self, target, args):
        def true_target(*true_target_args):
            self.setup_application()
            target(*args)

        thread = threading.Thread(target=true_target)
        thread.setDaemon(True)
        thread.start()
        return thread

    def setup_application(self):
        pass


    def get_application_startup_regex(self):
        raise NotImplementedError()

    def get_application_startup_units(self):
        raise NotImplementedError

    def applicationStartupRule(self, benchSuiteName, benchmark):
        return [
            # Example of Micronaut startup log:
            # "[main] INFO io.micronaut.runtime.Micronaut - Startup completed in 328ms. Server Running: <url>"
            mx_benchmark.StdOutRule(
                self.get_application_startup_regex(),
                {
                    "benchmark": benchmark,
                    "bench-suite": benchSuiteName,
                    "metric.name": "app-startup",
                    "metric.value": ("<startup>", float),
                    "metric.unit": self.get_application_startup_units(),
                    "metric.better": "lower",
                }
            )
        ]

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"First response received in (?P<firstResponse>\d*[.,]?\d*) ms",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "time-to-first-response",
                    "metric.value": ("<firstResponse>", float),
                    "metric.unit": "ms",
                    "metric.better": "lower",
                }
            )
        ]

    def computePeakThroughputRSS(self, datapoints):
        tputDatapoint = None
        rssDatapoint = None
        for datapoint in datapoints:
            if datapoint['metric.name'] == 'peak-throughput':
                tputDatapoint = datapoint
            if datapoint['metric.name'] == 'max-rss':
                rssDatapoint = datapoint
        if tputDatapoint and rssDatapoint:
            newdatapoint = copy.deepcopy(tputDatapoint)
            newdatapoint['metric.name'] = 'ops-per-GB-second'
            newtput = convertValue(tputUnitTable, float(tputDatapoint['metric.value']), tputDatapoint['metric.unit'], "op/s")
            newrss = convertValue(memUnitTable, float(rssDatapoint['metric.value']), rssDatapoint['metric.unit'], "GB")
            newdatapoint['metric.value'] = newtput / newrss
            newdatapoint['metric.unit'] = 'op/GB*s'
            newdatapoint['metric.better'] = 'higher'
            return newdatapoint
        else:
            return None

    def validateStdoutWithDimensions(self, out, benchmarks, bmSuiteArgs, retcode=None, dims=None, extraRules=None) -> DataPoints:
        datapoints = super(BaseMicroserviceBenchmarkSuite, self).validateStdoutWithDimensions(
            out=out, benchmarks=benchmarks, bmSuiteArgs=bmSuiteArgs, retcode=retcode, dims=dims, extraRules=extraRules)

        newdatapoint = self.computePeakThroughputRSS(datapoints)
        if newdatapoint:
            datapoints.append(newdatapoint)

        return datapoints

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        if len(benchmarks) > 1:
            mx.abort("A single benchmark should be specified for {0}.".format(BaseMicroserviceBenchmarkSuite.__name__))
        self.bmSuiteArgs = bmSuiteArgs
        self.benchmark_name = benchmarks[0]
        args, remainder = self.parser.parse_known_args(self.bmSuiteArgs)
        self.workloadPath = args.workload_configuration
        self.measureLatency = not args.skip_latency_measurements
        self.measureFirstResponse = not args.skip_first_response_measurements
        self.measureStartup = not args.skip_startup_measurements
        self.measurePeak = not args.skip_peak_measurements

        if not self.is_native_mode(self.bmSuiteArgs):
            datapoints = []
            if self.measureFirstResponse:
                # Measure time-to-first-response (without any command mapper hooks as those affect the measurement significantly)
                mx.disable_command_mapper_hooks()
                for _ in range(self.NumMeasureTimeToFirstResponse):
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testTimeToFirstResponseInBackground, [self])
                        datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                        measurementThread.join()
                mx.enable_command_mapper_hooks()

            if self.measureStartup:
                # Measure startup performance (without RSS tracker)
                mx_benchmark.disable_tracker()
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testStartupPerformanceInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()
                mx_benchmark.enable_tracker()

            if self.measurePeak:
                # Measure peak performance (with all command mapper hooks)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()

            if self.measureLatency:
                if not [c.get("requests-per-second") for c in self.loadConfiguration("latency") if c.get("requests-per-second")]:
                    # Calibrate for latency measurements (without RSS tracker) if no fixed request rate has been provided in the config
                    mx_benchmark.disable_tracker()
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.calibrateLatencyTestInBackground, [self])
                        datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                        measurementThread.join()

                # Measure latency (without RSS tracker)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testLatencyInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()
                mx_benchmark.enable_tracker()

            return datapoints
        else:
            return super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)


class NativeImageBundleBasedBenchmarkMixin(object):
    def applicationDist(self):
        raise NotImplementedError()

    def uses_bundles(self):
        raise NotImplementedError()

    def _get_single_file_with_extension_from_dist(self, extension):
        lib = self.applicationDist()
        matching_files = [filename for filename in os.listdir(lib) if filename.endswith(extension)]
        assert len(matching_files) == 1, f"When using bundle support, the benchmark must contain a single file with extension {extension} in its mx library"
        matching_file = os.path.join(lib, matching_files[0])
        return matching_file

    def create_bundle_command_line_args(self, benchmarks, bmSuiteArgs):
        assert self.uses_bundles()
        executable_jar = self._get_single_file_with_extension_from_dist(".jar")
        return self.vmArgs(bmSuiteArgs) + ["-jar", executable_jar]

    def get_bundle_path(self):
        if self.uses_bundles():
            return self._get_single_file_with_extension_from_dist(".nib")


class LayeredNativeImageBundleBasedBenchmarkMixin(NativeImageBundleBasedBenchmarkMixin):
    """
    Mixin for Native Image Bundle (NIB) based benchmark suites that contain benchmarks that
    can be built as layered images. The implementing benchmark suite can support layered images
    for a subset of its benchmarks. Benchmarks that support layered images can also run in
    standalone mode. The layered image mode is triggered with the `layered` vm config modifier.

    Any class implementing this mixin should also implement the
    `mx_sdk_benchmark.NativeImageBenchmarkMixin` mixin.
    """
    def layers(self, bm_suite_args: List[str]) -> List[Layer]:
        """
        Produces layer information for each layer that the currently running benchmark contains.
        This information is used in order to construct the list of benchmark stages to be run.
        This method should abort if the currently running benchmark does not support layered
        images. This method is invoked only if the VM is configured with the `layered` modifier.
        """
        raise NotImplementedError()

    def get_bundle_path(self) -> str:
        """
        Returns the path to the bundle corresponding to the current benchmark and latest layer.
        If the current benchmark execution is not layered, then returns the path to the standalone
        bundle corresponding to the current benchmark.
        """
        benchmark = self.benchmarkName()
        layer_info = self.get_latest_layer()
        if layer_info is None:
            return self.get_bundle_path_for_benchmark_standalone(benchmark)
        return self.get_bundle_path_for_benchmark_layer(benchmark, layer_info)

    def get_latest_layer(self) -> Optional[Layer]:
        """
        Returns the latest layer, which is:
         * the current layer if in an IMAGE stage
         * the layer of the latest preceding IMAGE stage if in a RUN stage
        The value returned should be `None` if the benchmark is running in standalone mode.
        """
        raise NotImplementedError()

    def get_bundle_path_for_benchmark_standalone(self, benchmark: str) -> str:
        """Returns the path to the standalone bundle corresponding to the specified benchmark."""
        raise NotImplementedError()

    def get_bundle_path_for_benchmark_layer(self, benchmark: str, layer_info: Layer) -> str:
        """Returns the path to the layered bundle corresponding to the specified benchmark and layer."""
        raise NotImplementedError()


class PatchEnv:
    def __init__(self, env):
        self.env = env

    def __enter__(self):
        self._prev_environ = os.environ
        os.environ = self.env.copy()
        # urllib.request caches http_proxy, https_proxy etc. globally but doesn't cache no_proxy
        # preserve no_proxy to avoid issues with proxies
        if 'no_proxy' in self._prev_environ:
            os.environ['no_proxy'] = self._prev_environ['no_proxy']
        if 'NO_PROXY' in self._prev_environ:
            os.environ['NO_PROXY'] = self._prev_environ['NO_PROXY']

    def __exit__(self, exc_type, exc_val, exc_tb):
        os.environ = self._prev_environ

class BaseJMeterBenchmarkSuite(BaseMicroserviceBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """Base class for JMeter based benchmark suites."""

    def jmeterVersion(self):
        return '5.3'

    def rules(self, out, benchmarks, bmSuiteArgs):
        # Example of jmeter output (time = 100s):
        #
        # summary +     59 in 00:00:10 =    5.9/s Avg:   449 Min:    68 Max:  7725 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary +   1945 in 00:00:30 =   64.9/s Avg:    46 Min:    26 Max:   116 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   2004 in 00:00:40 =   50.2/s Avg:    57 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +   1906 in 00:00:30 =   63.6/s Avg:    47 Min:    26 Max:    73 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   3910 in 00:01:10 =   55.9/s Avg:    52 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +   1600 in 00:00:30 =   53.3/s Avg:    56 Min:    29 Max:    71 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   5510 in 00:01:40 =   55.1/s Avg:    53 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +      8 in 00:00:00 =   68.4/s Avg:    46 Min:    30 Max:    58 Err:     0 (0.00%) Active: 0 Started: 3 Finished: 3
        # summary =   5518 in 00:01:40 =   55.1/s Avg:    53 Min:    26 Max:  7725 Err:     0 (0.00%)
        #
        # The following rules matches `^summary \+` and reports the corresponding data points as 'warmup'.
        # Note that the `run()` function calls `addAverageAcrossLatestResults()`, which computes
        # the avg. of the last `AveragingBenchmarkMixin.getExtraIterationCount()` warmup data points
        # and reports that value as 'throughput'.
        pattern = r"^summary \+\s+(?P<requests>[0-9]+) in (?P<hours>\d+):(?P<minutes>\d\d):(?P<seconds>\d\d) =\s+(?P<throughput>\d*[.,]?\d*)/s Avg:\s+(?P<avg>\d+) Min:\s+(?P<min>\d+) Max:\s+(?P<max>\d+) Err:\s+(?P<errors>\d+) \((?P<errpct>\d*[.,]?\d*)\%\)"  # pylint: disable=line-too-long
        return [
            mx_benchmark.StdOutRule(
                pattern,
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "warmup",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                    "metric.iteration": ("$iteration", int),
                    "warnings": ("<errors>", str),
                }
            ),
            mx_benchmark.StdOutRule(
                pattern,
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency",
                    "metric.value": ("<max>", float),
                    "metric.unit": "ms",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int),
                    "warnings": ("<errors>", str),
                }
            )
        ] + super(BaseJMeterBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def testStartupPerformance(self):
        self.startupOutput = ''

    def testPeakPerformance(self, warmup):
        jmeterDirectory = mx.library("APACHE_JMETER_" + self.jmeterVersion(), True).get_path(True)
        jmeterPath = os.path.join(jmeterDirectory, "apache-jmeter-" + self.jmeterVersion(), "bin/ApacheJMeter.jar")
        extraVMArgs = []
        if mx.get_jdk(tag='default').javaCompliance >= '9':
            extraVMArgs += ["--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                            "--add-opens=java.base/java.util=ALL-UNNAMED",
                            "--add-opens=java.base/java.text=ALL-UNNAMED"]
        jmeterCmd = [mx.get_jdk(tag='default').java] + extraVMArgs + ["-jar", jmeterPath,
                                                         "-t", self.workloadConfigurationPath(),
                                                         "-n", "-j", "/dev/stdout"] + self.extraJMeterArgs()
        mx.log("Running JMeter: {0}".format(jmeterCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(jmeterCmd, out=output, err=output)
        self.peakOutput = output.underlying.data

    def extraJMeterArgs(self):
        return []

    def calibrateLatencyTest(self):
        pass

    def testLatency(self):
        self.latencyOutput = ''

    def tailDatapointsToSkip(self, results):
        return int(len(results) * .10)

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = self.intercept_run(super(), benchmarks, bmSuiteArgs)
        results = results[:len(results) - self.tailDatapointsToSkip(results)]
        self.addAverageAcrossLatestResults(results, "throughput")
        return results

class BaseWrkBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    """Base class for Wrk based benchmark suites."""

    def loadConfiguration(self, groupKey):
        """Returns a json object that describes the Wrk configuration. The following syntax is expected:
        {
          "target-url" : <URL to target, for example "http://localhost:8080">,
          "connections" : <number of connections to keep open>,
          "threads" : <number of threads to use>,
          "throughput" : {
            "script" : <path to lua script to be used>,
            "warmup-requests-per-second" : <requests per second during the warmup run>,
            "warmup-duration" : <duration of the warmup run, for example "30s">,
            "duration" : <duration of the test, for example "30s">,
          },
          "latency" : {
            "script" : [<lua scripts that will be executed sequentially>],
            "warmup-requests-per-second" : [<requests per second during the warmup run (one entry per lua script)>],
            "warmup-duration" : [<duration of the warmup run (one entry per lua script)>],
            "requests-per-second" : [<requests per second during the run> (one entry per lua script)>],
            "duration" : [<duration of the test (one entry per lua script)>]
          }
        }

        All json fields are required.

        :return: Configuration json.
        :rtype: json
        """
        with open(self.workloadConfigurationPath()) as configFile:
            config = json.load(configFile)
            mx.log("Loading configuration file for {0}: {1}".format(BaseWrkBenchmarkSuite.__name__, configFile.name))

            targetUrl = self.readConfig(config, "target-url")
            connections = self.readConfig(config, "connections")
            threads = self.readConfig(config, "threads")

            group = self.readConfig(config, groupKey)
            script = self.readConfig(group, "script")
            warmupRequestsPerSecond = self.readConfig(group, "warmup-requests-per-second")
            warmupDuration = self.readConfig(group, "warmup-duration")
            requestsPerSecond = self.readConfig(group, "requests-per-second", optional=True)
            duration = self.readConfig(group, "duration")

            scalarScriptValue = self.isScalarValue(script)
            if scalarScriptValue != self.isScalarValue(warmupRequestsPerSecond) or scalarScriptValue != self.isScalarValue(warmupDuration) or scalarScriptValue != self.isScalarValue(duration):
                mx.abort("The configuration elements 'script', 'warmup-requests-per-second', 'warmup-duration', and 'duration' must have the same number of elements.")

            results = []
            if scalarScriptValue:
                result = {}
                result["target-url"] = targetUrl
                result["connections"] = connections
                result["threads"] = threads
                result["script"] = script
                result["warmup-requests-per-second"] = warmupRequestsPerSecond
                result["warmup-duration"] = warmupDuration
                result["duration"] = duration
                if requestsPerSecond:
                    result["requests-per-second"] = requestsPerSecond
                results.append(result)
            else:
                count = len(script)
                if count != len(warmupRequestsPerSecond) or count != len(warmupDuration) or count != len(duration):
                    mx.abort("The configuration elements 'script', 'warmup-requests-per-second', 'warmup-duration', and 'duration' must have the same number of elements.")

                for i in range(count):
                    result = {}
                    result["target-url"] = targetUrl
                    result["connections"] = connections
                    result["threads"] = threads
                    result["script"] = script[i]
                    result["warmup-requests-per-second"] = warmupRequestsPerSecond[i]
                    result["warmup-duration"] = warmupDuration[i]
                    result["duration"] = duration[i]
                    if requestsPerSecond:
                        result["requests-per-second"] = requestsPerSecond[i]
                    results.append(result)

            return results

    def readConfig(self, config, key, optional=False):
        if key in config:
            return config[key]
        elif optional:
            return None
        else:
            mx.abort(f"Mandatory entry {key} not specified in Wrk configuration.")

    def isScalarValue(self, value):
        return type(value) in (int, float, bool) or isinstance(value, ("".__class__, u"".__class__)) # pylint: disable=unidiomatic-typecheck

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def testStartupPerformance(self):
        configs = self.loadConfiguration("throughput")
        if len(configs) != 1:
            mx.abort("Expected exactly one lua script in the throughput configuration.")

        # Measure throughput for 15 seconds without warmup.
        config = configs[0]
        wrkFlags = self.getStartupFlags(config)
        output = self.runWrk1(wrkFlags)
        self.startupOutput = self.writeWrk1Results('startup-throughput', 'startup-latency-co', output)

    def testPeakPerformance(self, warmup):
        configs = self.loadConfiguration("throughput")
        if len(configs) != 1:
            mx.abort("Expected exactly one lua script in the throughput configuration.")

        config = configs[0]
        if warmup:
            # Warmup with a fixed number of requests.
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        # Measure peak performance.
        wrkFlags = self.getThroughputFlags(config)
        peakOutput = self.runWrk1(wrkFlags)
        self.peakOutput = self.writeWrk1Results('peak-throughput', 'peak-latency-co', peakOutput)

    def calibrateLatencyTest(self):
        configs = self.loadConfiguration("latency")
        numScripts = len(configs)
        if numScripts < 1:
            mx.abort("Expected at least one lua script in the latency configuration.")

        for i in range(numScripts):
            # Warmup with a fixed number of requests.
            config = configs[i]
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        self.calibratedThroughput = []
        for i in range(numScripts):
            # Measure the maximum throughput.
            config = configs[i]
            wrkFlags = self.getThroughputFlags(config)
            throughputOutput = self.runWrk1(wrkFlags)
            self.calibratedThroughput.append(self.extractThroughput(throughputOutput))

    def testLatency(self):
        configs = self.loadConfiguration("latency")
        numScripts = len(configs)
        if numScripts < 1:
            mx.abort("Expected at least one lua script in the latency configuration.")

        for i in range(numScripts):
            # Warmup with a fixed number of requests.
            config = configs[i]
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        results = []
        for i in range(numScripts):
            # Measure latency using a constant rate (based on the previously measured max throughput).
            config = configs[i]
            if configs[i].get("requests-per-second"):
                expectedRate = configs[i]["requests-per-second"]
                mx.log(f"Using configured fixed throughput {expectedRate} ops/s for latency measurements.")
            else:
                expectedRate = int(self.calibratedThroughput[i] * 0.75)
                mx.log(f"Using dynamically computed throughput {expectedRate} ops/s for latency measurements (75% of max throughput).")
            wrkFlags = self.getLatencyFlags(config, expectedRate)
            constantRateOutput = self.runWrk2(wrkFlags)
            self.verifyThroughput(constantRateOutput, expectedRate)
            results.append(self.extractWrk2Results(constantRateOutput))

        self.latencyOutput = self.writeWrk2Results('throughput-for-peak-latency', 'peak-latency', results)

    def extractThroughput(self, output):
        matches = re.findall(r"^Requests/sec:\s*(\d*[.,]?\d*)\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        return float(matches[0])

    def extractWrk2Results(self, output):
        result = {}
        result["throughput"] = self.extractThroughput(output)

        matches = re.findall(r"^\s*(\d*[.,]?\d*%)\s+(\d*[.,]?\d*)([mun]?s)\s*$", output, re.MULTILINE)
        if len(matches) <= 0:
            mx.abort("No latency results found in output")

        for match in matches:
            val = convertValue(timeUnitTable, float(match[1]), match[2], 'ms')
            result[match[0]] = val

        return result

    def writeWrk2Results(self, throughputPrefix, latencyPrefix, results):
        average = self.computeAverage(results)

        output = []
        for key, value in average.items():
            if key == 'throughput':
                output.append("{} Requests/sec: {:f}".format(throughputPrefix, value))
            else:
                output.append("{} {} {:f}ms".format(latencyPrefix, key, value))

        return '\n'.join(output)

    def computeAverage(self, results):
        count = len(results)
        if count < 1:
            mx.abort("Expected at least one wrk2 result: " + str(count))
        elif count == 1:
            return results[0]

        average = results[0]
        averageKeys = set(average.keys())
        for i in range(1, count):
            result = results[i]
            if averageKeys != set(result.keys()):
                mx.abort("There is a mismatch between the keys of multiple wrk2 runs: " + str(averageKeys) + " vs. " + str(set(result.keys())))

            for key, value in result.items():
                average[key] += result[key]

        for key, value in average.items():
            average[key] = value / count

        return average

    def writeWrk1Results(self, throughputPrefix, latencyPrefix, output):
        result = []
        matches = re.findall(r"^Requests/sec:\s*\d*[.,]?\d*\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        result.append(throughputPrefix + " " + matches[0])

        matches = re.findall(r"^\s*(\d*[.,]?\d*%)\s+(\d*[.,]?\d*)([mun]?s)\s*$", output, re.MULTILINE)
        if len(matches) <= 0:
            mx.abort("No latency results found in output")

        for match in matches:
            val = convertValue(timeUnitTable, float(match[1]), match[2], 'ms')
            result.append(latencyPrefix + " {} {:f}ms".format(match[0], val))

        return '\n'.join(result)

    def verifyWarmup(self, output, config):
        expectedThroughput = float(config['warmup-requests-per-second'])
        self.verifyThroughput(output, expectedThroughput)

    def verifyThroughput(self, output, expectedThroughput):
        matches = re.findall(r"^Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        actualThroughput = float(matches[0])
        if actualThroughput < expectedThroughput * 0.97 or actualThroughput > expectedThroughput * 1.03:
            mx.warn("Throughput verification failed: expected requests/s: {:.2f}, actual requests/s: {:.2f}".format(expectedThroughput, actualThroughput))

    def runWrk1(self, wrkFlags):
        distro = self.getOS()
        arch = mx.get_arch()
        wrkDirectory = mx.library('WRK_MULTIARCH', True).get_path(True)
        wrkPath = os.path.join(wrkDirectory, "wrk-{os}-{arch}".format(os=distro, arch=arch))

        if not os.path.exists(wrkPath):
            raise ValueError("Unsupported OS or arch. Binary doesn't exist: {}".format(wrkPath))

        runWrkCmd = [wrkPath] + wrkFlags
        mx.log("Running Wrk: {0}".format(runWrkCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(runWrkCmd, out=output, err=output)
        return output.underlying.data

    def runWrk2(self, wrkFlags):
        distro = self.getOS()
        arch = mx.get_arch()
        wrkDirectory = mx.library('WRK2_MULTIARCH', True).get_path(True)
        wrkPath = os.path.join(wrkDirectory, "wrk-{os}-{arch}".format(os=distro, arch=arch))

        if not os.path.exists(wrkPath):
            raise ValueError("Unsupported OS or arch. Binary doesn't exist: {}".format(wrkPath))

        runWrkCmd = [wrkPath] + wrkFlags
        mx.log("Running Wrk2: {0}".format(runWrkCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(runWrkCmd, out=output, err=output)
        return output.underlying.data

    def getStartupFlags(self, config):
        wrkFlags = ['--duration', '15']
        wrkFlags += self.getWrkFlags(config, True)
        return wrkFlags

    def getWarmupFlags(self, config):
        wrkFlags = []
        wrkFlags += ['--duration', str(config['warmup-duration'])]
        wrkFlags += ['--rate', str(config['warmup-requests-per-second'])]
        wrkFlags += self.getWrkFlags(config, False)
        return wrkFlags

    def getThroughputFlags(self, config):
        wrkFlags = []
        wrkFlags += ['--duration', str(config['duration'])]
        wrkFlags += self.getWrkFlags(config, True)
        return wrkFlags

    def getLatencyFlags(self, config, rate):
        wrkFlags = ['--rate', str(rate)]
        wrkFlags += self.getThroughputFlags(config)
        return wrkFlags

    def getWrkFlags(self, config, latency):
        args = []
        if latency:
            args += ['--latency']

        args += ['--connections', str(config['connections'])]
        args += ['--threads', str(config['threads'])]
        args += ['--script', str(self.getScriptPath(config))]
        args.append(str(config['target-url']))
        args += ['--', str(config['threads'])]
        return args

    def getOS(self):
        if mx.get_os() == 'linux':
            return 'linux'
        elif mx.get_os() == 'darwin':
            return 'macos'
        else:
            mx.abort("{0} not supported in {1}.".format(BaseWrkBenchmarkSuite.__name__, mx.get_os()))

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def rules(self, out, benchmarks, bmSuiteArgs):
        # Example of wrk output:
        # "Requests/sec:   5453.61"
        return [
            mx_benchmark.StdOutRule(
                r"^startup-throughput Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "startup-throughput",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-throughput Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-throughput",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^throughput-for-peak-latency Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "throughput-for-peak-latency",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^startup-latency-co\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "startup-latency-co",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-latency-co\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency-co",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-latency\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            )
        ] + super(BaseWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)


class BaseSpringBenchmarkSuite(BaseMicroserviceBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)

    def get_application_startup_regex(self):
        # Example of SpringBoot 3 startup log:
        # 2023-05-16T14:08:54.033+02:00  INFO 24381 --- [           main] o.s.s.petclinic.PetClinicApplication     : Started PetClinicApplication in 3.774 seconds (process running for 4.1)
        return r"Started [^ ]+ in (?P<appstartup>\d*[.,]?\d*) seconds \(process running for (?P<startup>\d*[.,]?\d*)\)$"

    def get_application_startup_units(self):
        return 's'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']

    def uses_bundles(self):
        return True


class BasePetClinicBenchmarkSuite(BaseSpringBenchmarkSuite):
    def version(self):
        return "3.0.1"

    def applicationDist(self):
        return mx.library("PETCLINIC_" + self.version(), True).get_path(True)


class PetClinicWrkBenchmarkSuite(BasePetClinicBenchmarkSuite, BaseWrkBenchmarkSuite):
    """PetClinic benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "petclinic-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["mixed-tiny", "mixed-small", "mixed-medium", "mixed-large", "mixed-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(PetClinicWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(PetClinicWrkBenchmarkSuite())


class BaseSpringHelloWorldBenchmarkSuite(BaseSpringBenchmarkSuite):
    def version(self):
        return "3.0.6"

    def applicationDist(self):
        return mx.library("SPRING_HW_" + self.version(), True).get_path(True)


class SpringHelloWorldWrkBenchmarkSuite(BaseSpringHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    def name(self):
        return "spring-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def serviceEndpoint(self):
        return 'hello'

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(SpringHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(SpringHelloWorldWrkBenchmarkSuite())


class BaseQuarkusBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    def get_application_startup_regex(self):
        # Example of Quarkus startup log:
        # "2021-03-17 20:03:33,893 INFO  [io.quarkus] (main) tika-quickstart 1.0.0-SNAPSHOT on JVM (powered by Quarkus 1.12.1.Final) started in 1.210s. Listening on: <url>"
        return r"started in (?P<startup>\d*[.,]?\d*)s."

    def get_application_startup_units(self):
        return 's'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BaseQuarkusBundleBenchmarkSuite(BaseQuarkusBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)


class BaseTikaBenchmarkSuite(BaseQuarkusBundleBenchmarkSuite):
    def version(self):
        return "1.0.11"

    def applicationDist(self):
        return mx.library("TIKA_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "tika-quickstart-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'parse'

    def extra_image_build_argument(self, benchmark, args):
        # Older JDK versions would need -H:NativeLinkerOption=libharfbuzz as an extra build argument.
        expectedJdkVersion = mx.VersionSpec("11.0.13")
        if mx.get_jdk().version < expectedJdkVersion:
            mx.abort(benchmark + " needs at least JDK version " + str(expectedJdkVersion))
        tika_build_time_init = [
            "org.apache.pdfbox.rendering.ImageType",
            "org.apache.pdfbox.rendering.ImageType$1",
            "org.apache.pdfbox.rendering.ImageType$2",
            "org.apache.pdfbox.rendering.ImageType$3",
            "org.apache.pdfbox.rendering.ImageType$4",
            "org.apache.xmlbeans.XmlObject",
            "org.apache.xmlbeans.metadata.system.sXMLCONFIG.TypeSystemHolder",
            "org.apache.xmlbeans.metadata.system.sXMLLANG.TypeSystemHolder",
            "org.apache.xmlbeans.metadata.system.sXMLSCHEMA.TypeSystemHolder"
        ]
        return [
            f"--initialize-at-build-time={','.join(tika_build_time_init)}",
        ] + super(BaseTikaBenchmarkSuite, self).extra_image_build_argument(benchmark, args)


class TikaWrkBenchmarkSuite(BaseTikaBenchmarkSuite, BaseWrkBenchmarkSuite):
    """Tika benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "tika-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["odt-tiny", "odt-small", "odt-medium", "odt-large", "odt-huge", "pdf-tiny", "pdf-small", "pdf-medium", "pdf-large", "pdf-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(TikaWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(TikaWrkBenchmarkSuite())


class BaseQuarkusHelloWorldBenchmarkSuite(BaseQuarkusBundleBenchmarkSuite):
    def version(self):
        return "1.0.6"

    def applicationDist(self):
        return mx.library("QUARKUS_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "quarkus-hello-world-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'hello'


class QuarkusHelloWorldWrkBenchmarkSuite(BaseQuarkusHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    """Quarkus benchmark suite that measures latency using Wrk2."""

    def name(self):
        return "quarkus-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(QuarkusHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(QuarkusHelloWorldWrkBenchmarkSuite())


class BaseMicronautBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    def get_application_startup_regex(self):
        # Example of Micronaut startup log (there can be some formatting in between):
        # "[main] INFO io.micronaut.runtime.Micronaut - Startup completed in 328ms. Server Running: <url>"
        return r"^.*\[main\].*INFO.*io.micronaut.runtime.Micronaut.*- Startup completed in (?P<startup>\d+)ms."

    def get_application_startup_units(self):
        return 'ms'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def build_assertions(self, benchmark, is_gate):
        # This method overrides NativeImageMixin.build_assertions
        return []  # We are skipping build assertions due to some failed asserts while building Micronaut apps.

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BaseMicronautBundleBenchmarkSuite(BaseMicronautBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)


class BaseQuarkusRegistryBenchmark(BaseQuarkusBenchmarkSuite, BaseMicroserviceBenchmarkSuite):
    """
    This benchmark is used to measure the precision and performance of the static analysis in Native Image,
    so there is no runtime load, that's why the default stage is just image.
    """

    def version(self):
        return "0.0.2"

    def name(self):
        return "quarkus"

    def benchmarkList(self, bmSuiteArgs):
        return ["registry"]

    def default_stages(self):
        return ['image']

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            benchmark = benchmarks[0]
        return self.vmArgs(bmSuiteArgs) + ["-jar",  os.path.join(self.applicationDist(), benchmark + ".jar")]

    def applicationDist(self):
        return mx.library("QUARKUS_REGISTRY_" + self.version(), True).get_path(True)

    def extra_image_build_argument(self, benchmark, args):
        quarkus_registry_features = [
            "io.quarkus.jdbc.postgresql.runtime.graal.SQLXMLFeature",
            "org.hibernate.graalvm.internal.GraalVMStaticFeature",
            "io.quarkus.hibernate.validator.runtime.DisableLoggingFeature",
            "io.quarkus.hibernate.orm.runtime.graal.DisableLoggingFeature",
            "io.quarkus.runner.Feature",
            "io.quarkus.runtime.graal.DisableLoggingFeature",
            "io.quarkus.caffeine.runtime.graal.CacheConstructorsFeature"
        ]
        return ['-J-Dlogging.initial-configurator.min-level=500',
                '-J-Dio.quarkus.caffeine.graalvm.recordStats=true',
                '-J-Djava.util.logging.manager=org.jboss.logmanager.LogManager',
                '-J-Dsun.nio.ch.maxUpdateArraySize=100',
                '-J-DCoordinatorEnvironmentBean.transactionStatusManagerEnable=false',
                '-J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory',
                '-J-Dvertx.disableDnsResolver=true',
                '-J-Dio.netty.leakDetection.level=DISABLED',
                '-J-Dio.netty.allocator.maxOrder=3',
                '-J-Duser.language=en',
                '-J-Duser.country=GB',
                '-J-Dfile.encoding=UTF-8',
                f"--features={','.join(quarkus_registry_features)}",
                '-J--add-exports=java.security.jgss/sun.security.krb5=ALL-UNNAMED',
                '-J--add-exports=org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED',
                '-J--add-opens=java.base/java.text=ALL-UNNAMED',
                '-J--add-opens=java.base/java.io=ALL-UNNAMED',
                '-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
                '-J--add-opens=java.base/java.util=ALL-UNNAMED',
                '-H:+AllowFoldMethods',
                '-J-Djava.awt.headless=true',
                '--no-fallback',
                '--link-at-build-time',
                '-H:+ReportExceptionStackTraces',
                '-H:-AddAllCharsets',
                '--enable-url-protocols=http,https',
                '-H:-UseServiceLoaderFeature',
                '--exclude-config',
                r'io\.netty\.netty-codec',
                r'/META-INF/native-image/io\.netty/netty-codec/generated/handlers/reflect-config\.json',
                '--exclude-config',
                r'io\.netty\.netty-handler',
                r'/META-INF/native-image/io\.netty/netty-handler/generated/handlers/reflect-config\.json',
                ] + super(BaseQuarkusBenchmarkSuite, self).extra_image_build_argument(benchmark, args)  # pylint: disable=bad-super-call

mx_benchmark.add_bm_suite(BaseQuarkusRegistryBenchmark())

_mushopConfig = {
    'order': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder,java.sql.DriverInfo,kotlin.coroutines.intrinsics.CoroutineSingletons'],
    'user': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder,java.sql.DriverInfo'],
    'payment': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder']
}

class BaseMicronautMuShopBenchmark(BaseMicronautBenchmarkSuite, BaseMicroserviceBenchmarkSuite):
    """
    This benchmark suite is used to measure the precision and performance of the static analysis in Native Image,
    so there is no runtime load, that's why the default stage is just image.
    """

    def version(self):
        return "0.0.2"

    def name(self):
        return "mushop"

    def benchmarkList(self, bmSuiteArgs):
        return ["user", "order", "payment"]

    def default_stages(self):
        return ['image']

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            benchmark = benchmarks[0]
        return self.vmArgs(bmSuiteArgs) + ["-jar",  os.path.join(self.applicationDist(), benchmark + ".jar")]

    def applicationDist(self):
        return mx.library("MICRONAUT_MUSHOP_" + self.version(), True).get_path(True)

    def extra_image_build_argument(self, benchmark, args):
        return ([
                    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED',
                    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.configure=ALL-UNNAMED',
                    '--add-exports=org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED']
                + _mushopConfig[benchmark] + super(BaseMicronautBenchmarkSuite, self).extra_image_build_argument(benchmark, args))  # pylint: disable=bad-super-call

mx_benchmark.add_bm_suite(BaseMicronautMuShopBenchmark())


class BaseShopCartBenchmarkSuite(BaseMicronautBundleBenchmarkSuite):
    def version(self):
        return "0.3.10"

    def applicationDist(self):
        return mx.library("SHOPCART_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "shopcart-" + self.version() + ".jar")

    def serviceEndpoint(self):
        return 'clients'


class ShopCartWrkBenchmarkSuite(BaseShopCartBenchmarkSuite, BaseWrkBenchmarkSuite):
    """ShopCart benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "shopcart-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["mixed-tiny", "mixed-small", "mixed-medium", "mixed-large", "mixed-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(ShopCartWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(ShopCartWrkBenchmarkSuite())


class BaseMicronautHelloWorldBenchmarkSuite(BaseMicronautBundleBenchmarkSuite):
    def version(self):
        return "1.0.7"

    def applicationDist(self):
        return mx.library("MICRONAUT_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "micronaut-hello-world-" + self.version() + ".jar")

    def serviceEndpoint(self):
        return 'hello'


class MicronautHelloWorldWrkBenchmarkSuite(BaseMicronautHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    def name(self):
        return "micronaut-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(MicronautHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(MicronautHelloWorldWrkBenchmarkSuite())
