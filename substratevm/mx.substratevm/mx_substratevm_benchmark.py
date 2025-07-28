#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function

import os
import tempfile
import zipfile
import re
import json
import datetime
from glob import glob
from pathlib import Path
from typing import List, Optional
from contextlib import contextmanager

import mx
import mx_benchmark
import mx_sdk_benchmark
from mx_benchmark import BenchmarkSuite, DataPoints, Rule, Vm, SingleBenchmarkExecutionContext
from mx._impl.mx_codeowners import _load_toml_from_fd
from mx_sdk_benchmark import SUCCESSFUL_STAGE_PATTERNS, parse_prefixed_args
from mx_util import StageName, Layer

_suite = mx.suite("substratevm")


def extract_archive(path, extracted_name):
    extracted_archive = os.path.join(os.path.dirname(path), extracted_name)
    if not os.path.exists(extracted_archive):
        # There can be multiple processes doing this so be atomic about it
        with mx.SafeDirectoryUpdater(extracted_archive, create=True) as sdu:
            with zipfile.ZipFile(path, 'r') as zf:
                zf.extractall(sdu.directory)
    return extracted_archive


def list_jars(path):
    jars = []
    for f in os.listdir(path):
        if os.path.isfile(os.path.join(path, f)) and f.endswith('.jar'):
            jars.append(f)
    return jars

# The agent fails to generate the configuration for org.apache.spark.status.JobDataWrapper.completionTime, which is not
# executed on the first iteration. Therefore, we supply the missing information manually.
# See GR-51788
movie_lens_reflection_config = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'movie-lens-reflection-config.json')

force_buildtime_init_slf4j_1_7_73 = '--initialize-at-build-time=org.slf4j,org.apache.log4j'
force_buildtime_init_slf4j_1_7_73_spark = '--initialize-at-build-time=org.apache.logging.slf4j.Log4jLoggerFactory,\
org.apache.logging.slf4j.SLF4JServiceProvider,org.apache.logging.slf4j.Log4jMarkerFactory,org.apache.logging.slf4j.Log4jMDCAdapter,\
org.apache.logging.log4j,org.apache.logging.log4j,org.apache.logging.log4j.core.util.WatchManager,org.apache.logging.log4j.core.config.xml.XmlConfiguration, \
org.apache.logging.log4j.core.config.AbstractConfiguration,org.apache.logging.log4j.util.ServiceLoaderUtil,org.slf4j.LoggerFactory'
force_buildtime_init_netty_4_1_72 = '--initialize-at-build-time=io.netty.util.internal.logging'
force_runtime_init_slf4j_1_7_73 = '--initialize-at-run-time=org.apache.log4j.LogManager'
force_runtime_init_netty_4_1_72 = '--initialize-at-run-time=io.netty.channel.unix,io.netty.channel.epoll,io.netty.handler.codec.http2,io.netty.handler.ssl,io.netty.internal.tcnative,io.netty.util.internal.logging.Log4JLogger'
force_runtime_init_netty_4_1_72_spark = '--initialize-at-run-time=io.netty.buffer.AbstractByteBufAllocator\
io.netty.channel.AbstractChannelHandlerContext,io.netty.channel.ChannelInitializer,io.netty.channel.ChannelOutboundBuffer,\
io.netty.util.internal.SystemPropertyUtil,io.netty.channel.AbstractChannel,io.netty.util.internal.PlatformDependent,\
io.netty.util.internal.InternalThreadLocalMap,io.netty.channel.socket.nio.SelectorProviderUtil,io.netty.util.concurrent.DefaultPromise, \
io.netty.util.NetUtil,io.netty.channel.DefaultChannelPipeline,io.netty.util.concurrent.FastThreadLocalThread,io.netty.util.internal.StringUtil, \
io.netty.util.internal.PlatformDependent0,io.netty.util,io.netty.bootstrap,io.netty.channel,io.netty.buffer,io.netty.resolver,io.netty.handler.codec.CodecOutputList'
_RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS = {
    'als'               : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'chi-square'        : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73
                          ],
    'finagle-chirper'   : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'finagle-http'      : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'log-regression'    : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_runtime_init_netty_4_1_72
                          ],
    'movie-lens'        : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73,
                           '-H:ReflectionConfigurationFiles=' + movie_lens_reflection_config
                          ],
    'dec-tree'          : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_runtime_init_netty_4_1_72
                          ],
    'page-rank'         : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73
                          ],
    'naive-bayes'       : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'gauss-mix'       :   [
                            force_buildtime_init_slf4j_1_7_73,
                            force_buildtime_init_slf4j_1_7_73_spark,
                            force_buildtime_init_netty_4_1_72,
                            force_runtime_init_netty_4_1_72,
                            force_runtime_init_netty_4_1_72_spark,
                            force_runtime_init_slf4j_1_7_73
                          ],
    'neo4j-analytics':    [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'dotty'             : [
                            '-H:+AllowJRTFileSystem' # Don't wrap the option with `mx_sdk_vm_impl.svm_experimental_options`, as all args are wrapped already.
                          ]
}

class RenaissanceNativeImageBenchmarkSuite(mx_sdk_benchmark.RenaissanceBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    """
    Building an image for a renaissance benchmark requires all libraries for the group this benchmark belongs to
    and a harness project compiled with the same scala version as the benchmark.
    Since we don't support building an image from fat-jars, we extract them to create project dependencies.

    On recent renaissance versions (>= 0.14.0), it's only necessary to extract the fatjar and run the standalone jar
    of a given benchmark. Those standalone jars define the minimal classpath and include the matching harness for the
    scala version needed by the benchmark.
    """

    def name(self):
        return 'renaissance-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'renaissance'

    def renaissance_harness_lib_name(self):
        # Before Renaissance 0.14.0, we had to cross-compile the Renaissance harness to ensure we have a matching
        # harness for each project compiled with different scala versions.
        # As of Renaissance 0.14.0, we use the standalone mode of renaissance which already creates a native-image
        # friendly classpath and already bundles all harness versions needed.
        version_to_run = self.version()
        if version_to_run in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            version_end_index = str(version_to_run).rindex('.')
            return 'RENAISSANCE_HARNESS_v' + str(version_to_run)[0:version_end_index]
        else:
            return None

    def harness_path(self):
        harness_lib = self.renaissance_harness_lib_name()
        if harness_lib is not None:
            lib = mx.library(harness_lib)
            if lib:
                return lib.get_path(True)
        return None

    def renaissance_unpacked(self):
        return extract_archive(self.renaissancePath(), 'renaissance.extracted')

    def standalone_jar_path(self, benchmark_name):
        standalone_jars_directory = "single"
        return os.path.join(self.renaissance_unpacked(), standalone_jars_directory, "{}.jar".format(benchmark_name))

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_run_arg(self, benchmark, args, image_run_args):
        run_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_run_arg(benchmark, args, image_run_args)
        if benchmark == "dotty" and self.version() not in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            # Before Renaissance 0.14.0, mx was manually placing all dependencies on the same classpath at build time
            # and at run time. As of Renaissance 0.14.0, we use the standalone mode which uses the classpath defined
            # in the manifest file at build time only. Dotty is a special benchmark since it also needs to know
            # this classpath at runtime to be able to perform compilations. The location of the fatjar must then be
            # explicitly passed also to the final image.
            return ["-Djava.class.path={}".format(self.standalone_jar_path(self.benchmarkName()))] + run_args
        else:
            return run_args

    def renaissance_additional_lib(self, lib):
        return mx.library(lib).get_path(True)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -r X argument from image run args
        return ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        # remove -r X argument from image run args
        if should_strip_run_args:
            extra_profile_run_args = ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)
        else:
            extra_profile_run_args = user_args

        if benchmark == "dotty" and self.version() not in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            # Before Renaissance 0.14.0, mx was manually placing all dependencies on the same classpath at build time
            # and at run time. As of Renaissance 0.14.0, we use the standalone mode which uses the classpath defined
            # in the manifest file at build time only. Dotty is a special benchmark since it also needs to know
            # this classpath at runtime to be able to perform compilations. The location of the fatjar must then be
            # explicitly passed also to the final image.
            return ["-Djava.class.path={}".format(self.standalone_jar_path(self.benchmarkName()))] + extra_profile_run_args
        else:
            return extra_profile_run_args

    def skip_agent_assertions(self, benchmark, args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def build_assertions(self, benchmark, is_gate):
        build_assertions = super(RenaissanceNativeImageBenchmarkSuite, self).build_assertions(benchmark, is_gate)
        return build_assertions

    def extra_image_build_argument(self, benchmark, args):
        default_args = _RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(RenaissanceNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]
        run_args = self.postprocessRunArgs(self.benchmarkName(), self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        # use renaissance standalone mode as of renaissance 0.14.0
        return vm_args + ["-jar", self.standalone_jar_path(self.benchmarkName())] + run_args + [self.benchmarkName()]

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

mx_benchmark.add_bm_suite(RenaissanceNativeImageBenchmarkSuite())


class BaristaNativeImageBenchmarkSuite(mx_sdk_benchmark.BaristaBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin, mx_sdk_benchmark.LayeredNativeImageBundleBasedBenchmarkMixin):
    """Native Image variant of the Barista benchmark suite implementation. A collection of microservice workloads running in native execution mode on the Barista harness.

    The run arguments are passed to the Barista harness.
    If you want to run something like `hwloc-bind` or `taskset` prefixed before the app image, you should use the '--cmd-app-prefix' Barista harness option.
    If you want to pass options to the app image, you should use the '--app-args' Barista harness option.
    """
    def __init__(self, custom_harness_command: mx_benchmark.CustomHarnessCommand = None):
        if custom_harness_command is None:
            custom_harness_command = BaristaNativeImageBenchmarkSuite.BaristaNativeImageCommand()
        super().__init__(custom_harness_command)
        self._application_nibs = {}
        # because of an issue in handling image build args in the intended order [GR-58214]
        # we need the image name that is set inside the nib
        self._application_fixed_image_names = {}

    def name(self):
        return "barista-native-image"

    def benchSuiteName(self, bmSuiteArgs=None):
        return "barista"

    def benchmarkList(self, bmSuiteArgs):
        exclude = []
        if mx.get_jdk().javaCompliance == "21":
            # ktor-hello-world fails with UnsupportedClassVersionError on JDK 21 (GR-60507)
            exclude.append("ktor-hello-world")
        return [b for b in self.completeBenchmarkList(bmSuiteArgs) if b not in exclude]

    def default_stages(self) -> List[str]:
        if self.benchmarkName() == "micronaut-pegasus":
            # The 'agent' stage is not supported, as currently we cannot run micronaut-pegasus on the JVM (GR-59793)
            return ["instrument-image", "instrument-run", "image", "run"]
        return super().default_stages()

    def layers(self, bm_suite_args: List[str]) -> List[Layer]:
        if self.benchmarkName() == "micronaut-pegasus":
            return [Layer(0, True), Layer(1, False)]
        # Currently, "micronaut-pegasus" is the only benchmark that supports running with layers
        # Support for other benchmarks, or even suites? (GR-64772)
        mx.abort(f"The '{self.benchmarkName()}' benchmark does not support layered native images!")

    def get_bundle_path_for_benchmark_standalone(self, benchmark) -> str:
        if benchmark not in self._application_nibs:
            # Run subprocess retrieving the application nib from the Barista 'build' script
            out = mx.OutputCapture()
            mx.run([self.baristaBuilderPath(), "--get-nib", self.baristaHarnessBenchmarkName()], out=out)
            # Capture the application nib from the Barista 'build' script output
            nib_pattern = r"application nib file path is: ([^\n]+)\n"
            nib_match = re.search(nib_pattern, out.data)
            if not nib_match:
                raise ValueError(f"Could not extract the nib file path from the command output! Expected to match pattern {repr(nib_pattern)}.")
            # Cache for future access
            self._application_nibs[benchmark] = nib_match.group(1)
            # Try to capture the fixed image name from the Barista 'build' script output
            fixed_image_name_pattern = r"fixed image name is: ([^\n]+)\n"
            fixed_image_name_match = re.search(fixed_image_name_pattern, out.data)
            # Cache fixed image name, if present
            if fixed_image_name_match:
                self._application_fixed_image_names[benchmark] = fixed_image_name_match.group(1)
        return self._application_nibs[benchmark]

    def get_bundle_path_for_benchmark_layer(self, benchmark, layer_info) -> str:
        app_dir = self.baristaApplicationDirectoryPath(benchmark)
        nib_candidates = list(app_dir.glob(f"**/layer{layer_info.index}-*.nib"))
        if len(nib_candidates) == 0:
            mx.abort(f"Expected to find exactly one 'layer{layer_info.index}-*.nib' file somewhere in the '{app_dir}' directory subtree, instead found none!")
        if len(nib_candidates) > 1:
            mx.abort(f"Expected to find exactly one 'layer{layer_info.index}-*.nib' file somewhere in the '{app_dir}' directory subtree, instead found "
                     + "multiple: [" + ", ".join(str(path) for path in nib_candidates) + "]")
        return str(nib_candidates[0])

    def get_latest_layer(self) -> Optional[Layer]:
        latest_image_stage = self.stages_info.get_latest_image_stage()
        if latest_image_stage is None or not latest_image_stage.is_layered():
            return None
        return latest_image_stage.layer_info

    def application_fixed_image_name(self):
        benchmark = self.benchmarkName()
        self.get_bundle_path_for_benchmark_standalone(benchmark)
        return self._application_fixed_image_names.get(benchmark, None)

    def applicationDist(self):
        return Path(self.get_bundle_path_for_benchmark_standalone(self.benchmarkName())).parent

    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        # Pass the VM options, BaristaNativeImageCommand will form the final command.
        return self.vmArgs(bmSuiteArgs)

    def extra_jvm_arg(self, benchmark, args):
        # Added by BaristaNativeImageCommand
        return []

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        # Added by BaristaNativeImageCommand
        return []

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        # Added by BaristaNativeImageCommand
        return []

    def extra_run_arg(self, benchmark, args, image_run_args):
        # Added by BaristaNativeImageCommand
        return []

    def build_assertions(self, benchmark: str, is_gate: bool) -> List[str]:
        # We cannot enable assertions along with emitting a build report for layered images, due to GR-65751
        if self.stages_info.current_stage.is_layered():
            return []
        return super().build_assertions(benchmark, is_gate)

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def ensure_image_is_at_desired_location(self, bmSuiteArgs):
        if self.stages_info.current_stage.is_image() and self.application_fixed_image_name() is not None:
            # Because of an issue in handling image build args in the intended order [GR-58214]
            # we need to move the image from the path that is set inside the nib to the path expected by our vm.
            # This code has no effect if the image is already at the desired location.
            vm = self.get_vm_registry().get_vm_from_suite_args(bmSuiteArgs)
            if self.stages_info.should_produce_datapoints(StageName.INSTRUMENT_IMAGE):
                desired_image_path = vm.config.instrumented_image_path
            elif self.stages_info.should_produce_datapoints(StageName.IMAGE):
                desired_image_path = vm.config.image_path
            else:
                return
            actual_image_path = desired_image_path.parent / self.application_fixed_image_name()
            if actual_image_path.is_file() and not desired_image_path.is_file():
                mx.move(actual_image_path, desired_image_path)

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        retcode, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)
        self.ensure_image_is_at_desired_location(bmSuiteArgs)
        return retcode, out, dims

    class BaristaNativeImageCommand(mx_sdk_benchmark.BaristaBenchmarkSuite.BaristaCommand):
        """Maps the command produced by NativeImageVM into a command tailored for the Barista harness.
        """
        def _short_load_testing_phases(self):
            """Configures the main barista load-testing phases to be quite short.

            Useful for the `agent` and `instrument-run` stages.
            """
            return [
                "--startup-iteration-count", "1",
                "--warmup-iteration-count", "1",
                "--warmup-duration", "5",
                "--throughput-iteration-count", "1",
                "--throughput-duration", "5",
                "--latency-iteration-count", "1",
                "--latency-duration", "5",
            ]

        def _get_built_app_image(self, suite, stage):
            """Retrieves the path to the app image built in the previous stage.

            In the case of `instrument-run`, retrieves the image built during `instrument-image`.
            In the case of `run`, retrieves the image built during `image`.
            """
            vm = suite.execution_context.virtual_machine
            if stage.stage_name == StageName.INSTRUMENT_RUN:
                return vm.config.instrumented_image_path
            else:
                return vm.config.image_path

        def produce_JVM_harness_command(self, cmd, suite):
            """Maps a JVM command into a command tailored for the Barista harness.

            Utilizes the implementation of the ``mx_sdk_benchmark.BaristaBenchmarkSuite.BaristaCommand`` base class
            """
            return super().produceHarnessCommand(cmd, suite)

        def produceHarnessCommand(self, cmd, suite):
            """Maps a NativeImageVM command into a command tailored for the Barista harness.

            This method is invoked only in the `agent`, `instrument-run` and `run` stages, because hooks are
            only applied in these stages (defined in ``NativeImageBenchmarkMixin.run_stage``).
            In the case of the `agent` stage, relies on the parent ``BaristaCommand`` class for the mapping.

            :param list[str] cmd: NativeImageVM command to be mapped.
            :param BaristaNativeImageBenchmarkSuite suite: Barista benchmark suite running the benchmark on the Barista harness.
            :return: Command tailored for the Barista harness.
            :rtype: list[str]
            """
            if not isinstance(suite, BaristaNativeImageBenchmarkSuite):
                raise TypeError(f"Expected an instance of {BaristaNativeImageBenchmarkSuite.__name__}, instead got an instance of {suite.__class__.__name__}")

            stage = suite.stages_info.current_stage
            if stage.is_agent():
                # BaristaCommand works for agent stage, since it's a JVM stage
                cmd = self.produce_JVM_harness_command(cmd, suite)
                # Make agent run short
                cmd += self._short_load_testing_phases()
                # Add explicit agent stage args
                cmd += self._energyTrackerExtraOptions(suite)
                cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-jvm-arg=", suite.execution_context.bmSuiteArgs)
                cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-agent-run-arg=", suite.execution_context.bmSuiteArgs)
                return cmd

            # Extract app image options and command prefix from the NativeImageVM command
            app_image = str(self._get_built_app_image(suite, stage))
            try:
                index_of_app_image = cmd.index(app_image)
            except:
                mx.log_error(f"Cannot produce harness command because app image '{app_image}' was not found in {cmd}")
                raise
            nivm_cmd_prefix = cmd[:index_of_app_image]
            nivm_app_options = cmd[index_of_app_image + 1:]

            # Get bench name and workload to use in the barista harness - we might have custom named benchmarks that need to be mapped
            barista_bench_name = suite.baristaHarnessBenchmarkName()
            barista_workload = suite.baristaHarnessBenchmarkWorkload()

            # Provide image built in the previous stage to the Barista harnesss using the `--app-executable` option
            ni_barista_cmd = [suite.baristaHarnessPath(), "--mode", "native", "--app-executable", app_image]
            if barista_workload is not None:
                ni_barista_cmd.append(f"--config={barista_workload}")
            ni_barista_cmd += suite.runArgs(suite.execution_context.bmSuiteArgs) + self._energyTrackerExtraOptions(suite)
            ni_barista_cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-jvm-arg=", suite.execution_context.bmSuiteArgs)
            if stage.is_instrument():
                # Make instrument run short
                ni_barista_cmd += self._short_load_testing_phases()
                if suite.execution_context.benchmark == "play-scala-hello-world":
                    self._updateCommandOption(ni_barista_cmd, "--vm-options", "-v", "-Dpidfile.path=/dev/null")
                # Add explicit instrument stage args
                ni_barista_cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-profile-run-arg=", suite.execution_context.bmSuiteArgs) or parse_prefixed_args("-Dnative-image.benchmark.extra-run-arg=", suite.execution_context.bmSuiteArgs)
            else:
                # Add explicit run stage args
                ni_barista_cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-run-arg=", suite.execution_context.bmSuiteArgs)
            if nivm_cmd_prefix:
                self._updateCommandOption(ni_barista_cmd, "--cmd-app-prefix", "-p", " ".join(nivm_cmd_prefix))
            if nivm_app_options:
                self._updateCommandOption(ni_barista_cmd, "--app-args", "-a", " ".join(nivm_app_options))
            ni_barista_cmd += [barista_bench_name]
            return ni_barista_cmd


mx_benchmark.add_bm_suite(BaristaNativeImageBenchmarkSuite())


# Revisit this at some point, maybe derive the config from the file/structure
# and the scenario files (GR-65000)
_graalosConfig = {
    "benchmarks": {
        "round-robin-micronaut-pegasus-function": {
            "app": "micronaut-pegasus-function",
            "scenario-path": "graalos-bench-suite/round-robin/micronaut-pegasus-function.toml",
        },
        "round-robin-rawhttp-function": {
            "app": "rawhttp-function",
            "scenario-path": "graalos-bench-suite/round-robin/rawhttp-function.toml",
        },
        "single-app-micronaut-pegasus-function": {
            "app": "micronaut-pegasus-function",
            "scenario-path": "graalos-bench-suite/single-app/micronaut-pegasus-function.toml",
        },
        "single-app-rawhttp-function": {
            "app": "rawhttp-function",
            "scenario-path": "graalos-bench-suite/single-app/rawhttp-function.toml",
        },
    },
}

class GraalOSNativeImageBenchmarkSuite(mx_benchmark.CustomHarnessBenchmarkSuite,
                                       mx_sdk_benchmark.NativeImageBenchmarkMixin,
                                       mx_sdk_benchmark.LayeredNativeImageBundleBasedBenchmarkMixin):
    """
    A collection of benchmarks designed for benchmarking the performance of apps built for GraalOS.

    This benchmark suite utilizes the `graalos-load-tester` harness to execute scenarios that run workloads against
    images of apps located in the `vm-benchmarks/graalos` repository.

    The run arguments (arguments that are specified after the second '--' instance of the 'mx benchmark' command) are
    passed to the 'gos-scenario' tool. For example:
    ```
    mx ... benchmark graalos:... -- ... -- -p app_count=1
    ```
    will propagate '-p app_count=1' to the 'gos-scenario' harness and thus set the 'app_count' parameter of the
    selected scenario to 1.

    DEVELOPER NOTE:
    The automatic dependency setup implemented as part of this suite does not work when multiple 'mx benchmark'
    processes are running concurrently. Namely, the bench suite automatically executes the following steps, if they
    haven't already been executed:
    * cloning the 'graalos-load-tester' repository as a sibling to the 'graal' repository
    * downloading the tools which graalos-load-tester depends on (e.g. wrk, wrk2, nginx)
    * generating the native image bundle from which the application image is built
    These steps aren't executed if:
    * the 'graalos-load-tester' repository is already present as a sibling to the 'graal' repository
    * all of the tools which graalos-load-tester depends on are installed and available on the PATH
    * the native image bundle for the application is present in the application source code directory
    Only in the case that these requirements are satisfied running concurrent 'mx benchmark' processes will not crash.
    (GR-66385)
    """
    def __init__(self, custom_harness_command: mx_benchmark.CustomHarnessCommand = None):
        if custom_harness_command is None:
            custom_harness_command = GraalOSNativeImageBenchmarkSuite.GraalOSLoadTesterCommand()
        super().__init__(custom_harness_command)
        self._version = None
        self._gos_scenario_home: Optional[Path] = None
        self._deployment = None
        self._bundle_paths: dict[str, str] = {}
        self._stage_env = os.environ.copy()

    def name(self):
        return "graalos"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "substratevm"

    def benchmarkName(self):
        return self.execution_context.benchmark

    def version(self):
        if self._version is None:
            self._version = self._read_gos_scenario_version()
            mx.log(f"Running GraalOS Load Tester version '{self._version}'")
        return self._version

    def layers(self, bm_suite_args: List[str]) -> List[Layer]:
        if self._get_benchmark_config(self.benchmarkName())["app"] == "micronaut-pegasus-function":
            return [Layer(0, True), Layer(1, False)]
        # Currently, "micronaut-pegasus-function" is the only app that supports running with layers
        # Support for other benchmarks, or even suites? (GR-64772)
        mx.abort(f"The '{self.benchmarkName()}' benchmark does not support layered native images!")

    def get_latest_layer(self) -> Optional[Layer]:
        latest_image_stage = self.stages_info.get_latest_image_stage()
        if latest_image_stage is None or not latest_image_stage.is_layered():
            return None
        return latest_image_stage.layer_info

    @property
    def gos_scenario_home(self) -> Path:
        if self._gos_scenario_home is None:
            self._gos_scenario_home = self._load_gos_scenario_home()
        return self._gos_scenario_home

    def _load_gos_scenario_home(self) -> Path:
        """
        Returns the path to the 'graalos-load-tester' directory sibling to the root 'graal' directory,
        cloning the remote repository if it is missing.

        :raises StopIteration: If 'graalos-load-tester' is not found in the list of ignored suites.
        """
        try:
            glt_suite = mx.suite("graalos-load-tester", fatalIfMissing=False)
            if glt_suite is None:
                glt_suite = mx.primary_suite().clone_foreign_suite("graalos-load-tester", clone_binary_first=False)
            return Path(glt_suite.dir)
        except StopIteration:
            mx.abort("Cloning of 'graalos-load-tester' as a sibling of the current suite has failed!")

    def _read_gos_scenario_version(self):
        """
        Dynamically gets the version of the graalos-load-tester based on the pyproject.toml file,
        falls back to 'unknown' if unsuccessful.
        """
        try:
            out = mx.OutputCapture()
            mx.run([self._gos_scenario_command(), "--version"], out=out, env=self.get_stage_env())
            return out.data.strip()
        except:
            return self.defaultSuiteVersion()

    def _gos_scenario_command(self) -> str:
        """Returns the command that executes the `graalos-load-tester` benchmarking harness."""
        return "gos-scenario"

    def _gos_scenarios_dir(self) -> Path:
        """Verifies that the root scenarios directory exists and returns the path to it."""
        scenarios_dir = self.gos_scenario_home / "scenarios"
        if not scenarios_dir.is_dir():
            raise ValueError(f"Directory '{scenarios_dir}' is supposed to contain load-testing scenarios"
                             f" but instead it does not exist!")
        return scenarios_dir

    def _vm_benchmarks_graalos_dir(self) -> Path:
        """Returns the path to the directory containing the applications that comprise the scenarios."""
        return Path(mx.primary_suite().vc_dir) / "vm-benchmarks" / "graalos"

    def _app_source_dir(self, app: str) -> Path:
        """Returns the path to the source code directory of the application."""
        return self._vm_benchmarks_graalos_dir() / app / "app"

    @contextmanager
    def catch_all_errors(self, propagate):
        """
        Context manager that catches any error raised by the managed code and either suppresses or propagates it.

        The error is either propagated or suppressed based on the value of the `propagate` parameter:
          * If `propagate` is `True` then any error raised by the managed code is propagated - the behaviour is as if
            the manager was absent.
          * If `propagate` is `False` then any error raised by the managed code is suppressed.
        """
        try:
            yield
        except:
            if propagate:
                raise

    def completeBenchmarkList(self, bmSuiteArgs):
        return _graalosConfig["benchmarks"].keys()

    def _get_scenario_path(self, benchmark: str) -> Path:
        """Returns the path to the scenario '.toml' configuration file for the benchmark, verifying that it exists."""
        bench_path = self._get_benchmark_config(benchmark)["scenario-path"]
        scenario_path = self._gos_scenarios_dir() / bench_path
        if not scenario_path.is_file():
            raise ValueError(f"Scenario file '{scenario_path}' does not exist!")
        return scenario_path

    def benchmarkList(self, bmSuiteArgs):
        # Exclude any benchmarks unsupported on the current platform, JDK version, VM
        return self.completeBenchmarkList(bmSuiteArgs)

    def _install_graalos_load_tester(self):
        """
        Installs the 'graalos-load-tester' project and its dependencies and modifies the PATH
        environment variable inside `_stage_env` to include paths emitted by the installation script.
        """
        install_script_path = self.gos_scenario_home / "mx-devenv" / "local-install.py"
        install_cmd = [str(install_script_path)]

        mx.log(f"Installing `graalos-load-tester' with: {install_cmd}")
        out = mx.OutputCapture()
        err = mx.OutputCapture()
        try:
            mx.run(install_cmd, out=out, err=err)
        except BaseException as e:
            for line in out.data.split("\n"):
                mx.log(line)
            for line in err.data.split("\n"):
                mx.log_error(line)
            if isinstance(e, SystemExit):
                mx.abort(f"Installing 'graalos-load-tester' failed with exit code {e}!")
            else:
                mx.abort(f"{e}\nInstalling 'graalos-load-tester' failed!")

        for line in out.data.split("\n"):
            mx.log(line)
        path_env_var_pattern = r"^.*export PATH=(.*):\$PATH$"
        for line in out.data.split("\n"):
            path_entry_match = re.match(path_env_var_pattern, line)
            if path_entry_match:
                new_path_entry = path_entry_match.group(1)
                mx.log(f"Prepending '{new_path_entry}' to the PATH environment variable for the duration of the benchmark.")
                self._stage_env["PATH"] = f"{new_path_entry}:{self._stage_env['PATH']}"

    def validateEnvironment(self):
        # Make sure 'graalos-load-tester' repo is present and dependencies are installed
        self._install_graalos_load_tester()

    def new_execution_context(self, vm: Vm, benchmarks: List[str], bmSuiteArgs: List[str]) -> SingleBenchmarkExecutionContext:
        return SingleBenchmarkExecutionContext(self, vm, benchmarks, bmSuiteArgs)

    def default_stages(self) -> List[str]:
        return ["instrument-image", "instrument-run", "image", "run"]

    def all_command_line_args_are_vm_args(self):
        return True

    def _get_benchmark_config(self, benchmark):
        """
        Get the configuration dictionary for the selected benchmark.
        The configuration dictionary contains information such as the app name and scenario .toml configuration file.
        """
        return _graalosConfig["benchmarks"][benchmark]

    def get_bundle_path(self) -> str:
        app_name = self._get_benchmark_config(self.benchmarkName())["app"]
        layer_info = self.get_latest_layer()
        if layer_info is not None:
            key = f"{app_name}-layer{layer_info.index}"
        else:
            key = app_name

        if key not in self._bundle_paths:
            app_dir = self._app_source_dir(app_name)
            self._bundle_paths[key] = self.generate_or_lookup_bundle(app_name, layer_info, app_dir)
            mx.log(f"Using bundle at '{self._bundle_paths[key]}' to generate the image for '{key}'.")
        return self._bundle_paths[key]

    def lookup_bundle(self, app_name: str, layer_info: Layer, app_dir: Path) -> List[Path]:
        """
        Looks up the path to the NIB file for the app-layer pair associated with the current benchmark stage.

        The files are searched for in the subtree of the app's root directory. All the files that match the expected
        naming pattern are matched. The naming pattern depends on whether the bundle has been generated for a standalone
        application or a single layer of a layered application build. The name of the NIB file should:
        * start with anything other than 'layer<NUMBER>-' if it is meant for building a standalone app image.
        * start with 'layer<NUMBER>-' if it is meant for building a layer, where NUMBER is the index of the layer.
        """
        # Lookup all the NIB files located inside the subtree of the app root directory
        nib_candidates = list(app_dir.glob("**/*.nib"))

        # Filter for only the NIB files that correspond to the naming scheme associated with the current layer
        if layer_info is None:
            # Select only the nib files that do not start with r'layer\d+-'
            nib_naming_pattern = r"^(?!layer\d+-).*\.nib$"
        else:
            # Select only the nib files that start with fr'layer{layer_info.index}-'
            nib_naming_pattern = fr"^layer{layer_info.index}-.*\.nib$"
        return [nib for nib in nib_candidates if re.match(nib_naming_pattern, nib.name)]

    def generate_bundle(self, app_name: str, layer_info: Layer):
        """Generates the NIB file for the app-layer pair associated with the current benchmark stage."""
        if app_name == "micronaut-pegasus-function" and mx.get_env("JDK17_HOME") is None:
            mx.abort(f"App '{app_name}' requires JDK17_HOME env var to point to a JDK 17 distribution in order to build maven project.")

        nib_generation_cmd = ["./graalos-gate.py", "build", "--build-profile", "nib", app_name]
        if layer_info is not None:
            assert app_name == "micronaut-pegasus-function", f"Cannot generate a layer bundle for '{app_name}' app!"
            assert layer_info.index in [0, 1], f"Cannot generate layer#{layer_info.index} bundle for '{app_name}' app!"
            if layer_info.index == 0:
                nib_generation_cmd += ["--extra-build-options=--maven-options=-Pbase-layer"]
            else:
                nib_generation_cmd += ["--extra-build-options=--maven-options=-Papp-layer"]
        working_dir = self._vm_benchmarks_graalos_dir()
        mx.log(f"Generating the NIB file by running {nib_generation_cmd} in working dir '{working_dir}'. This can take a while.")
        try:
            mx.run(nib_generation_cmd, cwd=working_dir, env=self.get_nib_generation_env())
        except BaseException as e:
            if isinstance(e, SystemExit):
                mx.abort(f"Generating the NIB file failed with exit code {e}!")
            else:
                mx.abort(f"{e}\nGenerating the NIB file failed!")

    def generate_or_lookup_bundle(self, app_name: str, layer_info: Layer, app_dir: Path) -> Path:
        """
        Looks up the path to the NIB file for the app asociated with the current benchmark,
        generating it first if it does not exist.
        """
        nib_candidates = self.lookup_bundle(app_name, layer_info, app_dir)

        # Generate a NIB file for the app if none exists
        if len(nib_candidates) == 0:
            self.generate_bundle(app_name, layer_info)
            # Repeat the lookup
            nib_candidates = self.lookup_bundle(app_name, layer_info, app_dir)

        # Final check
        if len(nib_candidates) == 0:
            mx.abort(f"Expected to find exactly one '.nib' file in the '{app_dir}' app directory, instead found none!")
        if len(nib_candidates) > 1:
            mx.abort(f"Expected to find exactly one '.nib' file in the '{app_dir}' app directory, instead found "
                     + "multiple: [" + ", ".join(str(path) for path in nib_candidates) + "]")
        return nib_candidates[0]

    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        # Pass the VM options, GraalOSLoadTesterCommand will form the final command.
        return self.vmArgs(bmSuiteArgs)

    def extra_jvm_arg(self, benchmark, args):
        # Added by GraalOSLoadTesterCommand
        return []

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        # Added by GraalOSLoadTesterCommand
        return []

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        # Added by GraalOSLoadTesterCommand
        return []

    def extra_run_arg(self, benchmark, args, image_run_args):
        # Added by GraalOSLoadTesterCommand
        return []

    def build_assertions(self, benchmark: str, is_gate: bool) -> List[str]:
        # We cannot enable assertions along with emitting a build report for layered images, due to GR-65751
        if self.stages_info.current_stage.is_layered():
            return []
        return super().build_assertions(benchmark, is_gate)

    def rules(self, output, benchmarks, bmSuiteArgs) -> List[Rule]:
        json_file_group_name = "graalos_json_results_file_path"
        json_file_pattern = fr"- saved to: (?P<{json_file_group_name}>\S+?)$"
        # Copies all the datapoints dumped by the graalos-load-tester
        class DatapointsCopyRule(mx_benchmark.JsonStdOutFileRule):
            def __init__(self, json_file_pattern, json_file_group_name, suite):
                super().__init__(json_file_pattern, json_file_group_name, {}, [])
                self.suite = suite

            def parse(self, text) -> DataPoints:
                all_datapoints = []
                for json_file_path in self.getJsonFiles(text):
                    with open(json_file_path, "r") as json_file:
                        all_datapoints += json.load(json_file)["queries"]
                # The following keys should be removed as they are populated in a different way
                # by the graalos-load-tester than they are populated by `mx benchmark`.
                # These fields will be repopulated appropriately.
                keys_to_remove = ["benchmark", "bench-suite", "bench-suite-version"]
                for dp in all_datapoints:
                    for key in keys_to_remove:
                        dp.pop(key, None)
                    dp["benchmark"] = self.suite.benchmarkName()
                return all_datapoints
        return [DatapointsCopyRule(json_file_pattern, json_file_group_name, self)]

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        retcode, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)
        if self.stages_info.current_stage.is_image() and self._check_if_dataplane_scenario():
            self._ensure_dataplane_scenario_can_run()
        return retcode, out, dims

    def _check_if_dataplane_scenario(self) -> bool:
        """Returns whether the scenario uses 'dataplane' deployment."""
        return self._get_scenario_deployment() == "dataplane"

    def _get_scenario_deployment(self):
        """Retrieves the deployment used in the scenario."""
        if self._deployment is None:
            self._deployment = self._read_scenario_deployment()
        return self._deployment

    def _read_scenario_deployment(self):
        """Reads the deployment field of the first app in the scenario."""
        scenario_path = self._get_scenario_path(self.benchmarkName())
        with open(scenario_path, "rb") as scenario_fd:
            return _load_toml_from_fd(scenario_fd)["apps"][0]["deployment"]

    def _ensure_dataplane_scenario_can_run(self):
        """
        Ensures that the app image is in an accessible directory and has adequate permissions
        so graalhost can execute it.
        """
        original_app_image_path = self._get_built_app_image()
        accessible_app_image_path = Path("/") / "opt" / "preinstalled" / "applications" / original_app_image_path.name
        copy_cmd = ["cp", str(original_app_image_path), str(accessible_app_image_path)]
        mx.logv(f"Running {copy_cmd} to ensure that the binary is in a directory accessible to graalhost.")
        mx.run(copy_cmd)
        chmod_cmd = ["chmod", "755", str(accessible_app_image_path)]
        mx.logv(f"Running {chmod_cmd} to ensure that the binary has adequate permissions for graalhost to execute it.")
        mx.run(chmod_cmd)

    def _get_built_app_image(self):
        """Retrieves the path to the app image built in the last image stage.

        In the case of `instrument-run`, retrieves the image built during `instrument-image`.
        In the case of `run`, retrieves the image built during `image`.
        In the case of an `image` stage, retrieves the image built during the stage.
        """
        stage = self.stages_info.current_stage
        if stage.is_agent():
            raise ValueError(f"Cannot retrieve the path of the latest image from the '{stage.stage_name}' stage!")
        vm = self.execution_context.virtual_machine
        if stage.is_instrument():
            return vm.config.instrumented_image_path
        return vm.config.image_path

    def _get_runnable_app_image(self):
        """Retrieves the path to the accessible copy of the app image built in the last stage."""
        original_app_image_path = self._get_built_app_image()
        if self._check_if_dataplane_scenario():
            accessible_app_image_path = Path("/") / "opt" / "preinstalled" / "applications" / original_app_image_path.name
            return accessible_app_image_path
        return original_app_image_path

    def get_stage_env(self) -> Optional[dict]:
        return self._stage_env

    def get_nib_generation_env(self):
        env = self.get_stage_env().copy()
        graalvm_home = self.execution_context.virtual_machine.home()
        # graalos-gate.py (the script we use to generate the NIB files) requires these env vars
        # - GRAALVM_HOME so the underlying maven/gradle/custom build command can invoke native-image
        # - GRADLE_CLIENT_JAVA_HOME so that gradle has a JVM to execute its goals
        if not env.get("GRAALVM_HOME"):
            env["GRAALVM_HOME"] = graalvm_home
        if not env.get("GRADLE_CLIENT_JAVA_HOME"):
            env["GRADLE_CLIENT_JAVA_HOME"] = graalvm_home
        return env

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    class GraalOSLoadTesterCommand(mx_benchmark.CustomHarnessCommand):
        """Maps the command produced by NativeImageVM into a command tailored for the `graalos-load-tester` harness."""
        def produceHarnessCommand(self, cmd: List[str], suite: BenchmarkSuite) -> List[str]:
            if not isinstance(suite, GraalOSNativeImageBenchmarkSuite):
                raise TypeError(f"Expected an instance of {GraalOSNativeImageBenchmarkSuite.__name__},"
                                f" instead got an instance of {suite.__class__.__name__}")
            scenario = suite._get_scenario_path(suite.benchmarkName())
            bmSuiteArgs = suite.execution_context.bmSuiteArgs

            original_app_image = str(suite._get_built_app_image())
            try:
                index_of_app_image = cmd.index(original_app_image)
            except:
                mx.log_error(f"Cannot produce harness command because app image '{original_app_image}' was not found in {cmd}")
                raise
            source_cmd_prefix = cmd[:index_of_app_image]
            options_from_source_cmd = cmd[index_of_app_image + 1:]

            app_cmd = source_cmd_prefix
            app_cmd += [str(suite._get_runnable_app_image())]
            app_cmd += options_from_source_cmd
            app_cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-jvm-arg=", bmSuiteArgs)
            if suite.stages_info.current_stage.is_instrument():
                # Add explicit instrument stage args
                app_cmd += (parse_prefixed_args("-Dnative-image.benchmark.extra-profile-run-arg=", bmSuiteArgs)
                            or parse_prefixed_args("-Dnative-image.benchmark.extra-run-arg=", bmSuiteArgs))
            else:
                # Add explicit run stage args
                app_cmd += parse_prefixed_args("-Dnative-image.benchmark.extra-run-arg=", bmSuiteArgs)

            gos_cmd = [suite._gos_scenario_command(), f"{scenario}", "--local-load-testers", "--skip-upload"]
            timestamp = datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S")
            gos_log_file_name = f"{timestamp}-gos-out.log"
            gos_cmd += ["--log-to", f"stdout,file:{gos_log_file_name}"]
            if suite.execution_context.virtual_machine.graalhost_graalos:
                gos_cmd += ["-p", f"deployment='nginx-tinyinit-graalhost'"]
            app_cmd_str = " ".join(app_cmd)
            gos_cmd += ["-p", f"command=\"{app_cmd_str}\""]
            gos_cmd += suite.runArgs(bmSuiteArgs)
            mx.log(f"Produced 'gos-scenario' command: '{' '.join(gos_cmd)}'")
            return gos_cmd


mx_benchmark.add_bm_suite(GraalOSNativeImageBenchmarkSuite())


class BaseDaCapoNativeImageBenchmarkSuite():

    '''`SetBuildInfo` method in DaCapo source reads from the file nested in daCapo jar.
    This is not supported with native image, hence it returns `unknown` for code version.'''

    def suite_title(self):
        return 'DaCapo unknown'

    @staticmethod
    def collect_dependencies(path):
        deps = []
        for f in list_jars(path):
            deps.append(os.path.join(path, f))
        return deps

    @staticmethod
    def collect_nested_dependencies(path):
        deps = []
        deps += [y for x in os.walk(path) for y in glob(os.path.join(x[0], '*.jar'))]
        deps += [y for x in os.walk(path) for y in glob(os.path.join(x[0], 'classes'))]
        return deps

    @staticmethod
    def extract_dacapo(dacapo_path):
        return extract_archive(dacapo_path, 'dacapo.extracted')

    def benchmark_resources(self, benchmark):
        return None

    def additional_lib(self, lib):
        return mx.library(lib).get_path(True)

    def create_dacapo_classpath(self, dacapo_path, benchmark):
        dacapo_nested_resources = []
        dacapo_dat_resources = []
        dacapo_extracted = self.extract_dacapo(dacapo_path)
        benchmark_resources = self.benchmark_resources(benchmark)
        if benchmark_resources:
            for resource in benchmark_resources:
                dacapo_dat_resource = extract_archive(os.path.join(dacapo_extracted, resource), benchmark)
                dat_resource_name = os.path.splitext(os.path.basename(resource))[0]
                dacapo_dat_resources.append(os.path.join(dacapo_dat_resource, dat_resource_name))
                #collects nested jar files and classes directories
                dacapo_nested_resources += self.collect_nested_dependencies(dacapo_dat_resource)
        return dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources

    def collect_unique_dependencies(self, path, benchmark, exclude_libs):
        deps = BaseDaCapoNativeImageBenchmarkSuite.collect_dependencies(path)
        # if there are more versions of the same jar, we choose one and omit remaining from the classpath
        if benchmark in exclude_libs:
            for lib in exclude_libs[benchmark]:
                lib_path = os.path.join(path, lib)
                if lib_path in deps:
                    deps.remove(os.path.join(path, lib))
        return deps


def _empty_file():
    with tempfile.NamedTemporaryFile(delete=False) as empty_file:
        empty_file.write(b"")
    return empty_file.name


# Note: If you wish to preserve the underlying benchmark stderr and stdout files after a run, you can pass the following argument: -preserve
# This argument can be added to either:
# 1. The agent stage: -Dnative-image.benchmark.extra-agent-run-arg=-preserve
# 2. The image run stage: -Dnative-image.benchmark.extra-run-arg=-preserve
_DACAPO_SKIP_AGENT_ASSERTIONS = {
    'pmd':        True,
    'sunflow':    True,
    'fop':        True
}

_DACAPO_EXTRA_IMAGE_BUILD_ARGS = {
    'h2' :      [],
    'pmd':      [],
    # org.apache.crimson.parser.Parser2 is force initialized at build-time due to non-determinism in class initialization
    # order that can lead to runtime issues. See GR-26324.
    'xalan':    ['--initialize-at-build-time=org.apache.crimson.parser.Parser2,org.apache.crimson.parser.Parser2$Catalog,org.apache.crimson.parser.Parser2$NullHandler,org.apache.xml.utils.res.CharArrayWrapper'],
    # There are two main issues with fop:
    # 1. LoggingFeature is enabled by default, causing the LogManager configuration to be parsed at build-time. However
    #    DaCapo Harness sets the `java.util.logging.config.file` property at run-time. Therefore, we set
    #    `java.util.logging.config.file` to an empty file to avoid incorrectly parsing the default log configuration,
    #    leading to output on stderr. We cannot set it to scratch/fop.log as it would normally be, because the file does
    #    not exist and would fail the benchmark when assertions are enabled.
    # 2. Native-image picks a different service provider than the JVM for javax.xml.transform.TransformerFactory.
    #    We can simply remove the jar containing that provider as it is not required for the benchmark to run.
    'fop':      [f"-Djava.util.logging.config.file={_empty_file()}",
                 '--initialize-at-run-time=org.apache.fop.render.rtf.rtflib.rtfdoc.RtfList'],
    'batik':    []
}

_DACAPO_EXTRA_IMAGE_RUN_ARGS = {
    # JDK21 ForeignAPISupport is broken --- disable `enableMemorySegments` for now
    'lusearch':    ['-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false', '--no-validation'],
    'luindex':     ['-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false', '--no-validation'],
    'fop':         ['-Djava.home=' + os.environ['JAVA_HOME']]
}

'''
Benchmarks from DaCapo suite may require one or more zip archives from `dat` directory on the classpath.
After the agent run we have all necessary resources (from `jar` and `dat` folders inside DaCapo fat jar).
We don't support nested archives and classes directories in a jar so we have to specify them directly on the classpath.
Since we don't have produced config files available in the suite, we will store paths in `_dacapo_resources`,
load all resources from specified archives, and collect them on a benchmark classpath.
'''
_dacapo_resources = {
    'avrora'     : ['dat/avrora.zip'],
    'batik'      : ['dat/batik.zip'],
    'eclipse'    : ['dat/eclipse.zip'],
    'fop'        : ['dat/fop.zip'],
    'h2'         : [],
    'jython'     : ['dat/jython.zip'],
    'luindex'    : ['dat/luindex.zip'],
    'lusearch'   : ['dat/lusearch.zip'],
    'pmd'        : ['dat/pmd.zip'],
    'sunflow'    : [],
    'tomcat'     : ['dat/tomcat.zip'],
    'tradebeans' : ['dat/daytrader.zip'],
    'tradesoap'  : ['dat/daytrader.zip'],
    'xalan'      : ['dat/xalan.zip'],
}

_daCapo_iterations = {
    'avrora'     : 20,
    'batik'      : 40,
    'eclipse'    : -1, # Not supported on Hotspot
    'fop'        : 40,
    'h2'         : 25,
    'jython'     : 20,
    'luindex'    : 15,
    'lusearch'   : 40,
    'pmd'        : 30,
    'sunflow'    : 35,
    'tomcat'     : -1, # Not supported on Hotspot
    'tradebeans' : -1, # Not supported on Hotspot
    'tradesoap'  : -1, # Not supported on Hotspot
    'xalan'      : 30, # Needs both xalan.jar and xalan-2.7.2.jar. Different library versions on classpath aren't supported.
}

_daCapo_exclude_lib = {
    'h2'          : ['derbytools.jar', 'derbyclient.jar', 'derbynet.jar'],  # multiple derby classes occurrences on the classpath can cause a security error
    'pmd'         : ['derbytools.jar', 'derbyclient.jar', 'derbynet.jar'],  # multiple derby classes occurrences on the classpath can cause a security error
    'fop'         : ['saxon-9.1.0.8.jar', 'saxon-9.1.0.8-dom.jar'],  # Native-image picks the wrong service provider from these jars
}

class DaCapoNativeImageBenchmarkSuite(mx_sdk_benchmark.DaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    '''
    Some methods in DaCapo source are modified because they relied on the jar's nested structure,
    e.g. loading all configuration files for benchmarks from a nested directory.
    Therefore, this library is built from the source.
    '''
    def name(self):
        return 'dacapo-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'dacapo'

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def availableSuiteVersions(self):
        # The version 9.12-MR1-git+2baec49 also ships a custom harness class to allow native image to find the entry point in the nested jar
        return ["9.12-MR1-git+2baec49", "23.11-MR2-chopin"]

    def daCapoIterations(self):
        compiler_iterations = super(DaCapoNativeImageBenchmarkSuite, self).daCapoIterations()
        return {key: _daCapo_iterations[key] for key in compiler_iterations.keys() if key in _daCapo_iterations.keys()}

    def benchmark_resources(self, benchmark):
        if self.version() == "23.11-MR2-chopin":
            return []
        else:
            return _dacapo_resources[benchmark]

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        self.fixDataLocation()
        user_args = ["-Duser.home=" + str(Path.home())]
        user_args += super(DaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)

        if benchmark in _DACAPO_EXTRA_IMAGE_RUN_ARGS:
            user_args = user_args + _DACAPO_EXTRA_IMAGE_RUN_ARGS[benchmark]

        # remove -n X argument from image run args
        if should_strip_run_args:
            return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)
        else:
            return user_args

    def fixDataLocation(self):
        if self.version() == "23.11-MR2-chopin":
            print("Fixing data location...")
            # DaCapo can get data location either from the JAR path or "~/.dacapo-config.properties"
            # See official dacapobench issue #341
            dataLocation = self.dataLocation()
            configFilePath = os.path.join(Path.home(), ".dacapo-config.properties")
            with open(configFilePath, "w") as config:
                config.write(f"Data-Location={dataLocation}\n")

            with open(configFilePath) as f:
                print("Reading " + configFilePath + ":")
                print("------")
                print(f.read())
                print("------")

    def extra_run_arg(self, benchmark, args, image_run_args):
        self.fixDataLocation()
        run_args = ["-Duser.home=" + str(Path.home())]
        run_args += super(DaCapoNativeImageBenchmarkSuite, self).extra_run_arg(benchmark, args, image_run_args)
        if benchmark in _DACAPO_EXTRA_IMAGE_RUN_ARGS:
            run_args = run_args + _DACAPO_EXTRA_IMAGE_RUN_ARGS[benchmark]
        return run_args

    def skip_agent_assertions(self, benchmark, args):
        default_args = _DACAPO_SKIP_AGENT_ASSERTIONS[benchmark] if benchmark in _DACAPO_SKIP_AGENT_ASSERTIONS else []
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return default_args

    def extra_image_build_argument(self, benchmark, args):
        default_args = _DACAPO_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _DACAPO_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(DaCapoNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]

        benchmark = benchmarks[0]

        run_args = self.postprocessRunArgs(benchmark, self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        return self.create_classpath(benchmark) + vm_args + ['-jar', self.jarPath(benchmark)] + [benchmark] + run_args

    def create_classpath(self, benchmark):
        if self.version() == "9.12-MR1-git+2baec49":
            dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources = self.create_dacapo_classpath(self.daCapoPath(), benchmark)
            dacapo_jars = super(DaCapoNativeImageBenchmarkSuite, self).collect_unique_dependencies(os.path.join(dacapo_extracted, 'jar'), benchmark, _daCapo_exclude_lib)
            cp = ':'.join([dacapo_extracted] + dacapo_jars + dacapo_dat_resources + dacapo_nested_resources)
            return ["-cp", cp]
        else:
            return []

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS


mx_benchmark.add_bm_suite(DaCapoNativeImageBenchmarkSuite())


_scala_dacapo_resources = {
    'scalac'      : ['dat/scalac.zip'],
    'scalariform' : ['dat/scalariform.zip'],
    'scalap'      : ['dat/scalap.zip'],
    'scaladoc'    : ['dat/scaladoc.zip'],
    'scalatest'   : ['dat/scalatest.zip'],
    'scalaxb'     : ['dat/scalaxb.zip'],
    'kiama'       : ['dat/kiama.zip'],
    'factorie'    : ['dat/factorie.zip'],
    'specs'       : ['dat/specs.zip'],
    'apparat'     : ['dat/apparat.zip'],
    'tmt'         : ['dat/tmt.zip']
}

_scala_dacapo_iterations = {
    'scalac'        : 30,
    'scalariform'   : 30,
    'scalap'        : 120,
    'scaladoc'      : 30,
    'scalatest'     : 60,
    'scalaxb'       : 60,
    'kiama'         : 40,
    'factorie'      : 6,
    'specs'         : 4,
    'apparat'       : 5,
    'tmt'           : 12,
}

_SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS = {
    'scalariform'   : ['--allow-incomplete-classpath'],
    'scalatest'     : ['--allow-incomplete-classpath'],
    'specs'         : ['--allow-incomplete-classpath'],
    'tmt'           : ['--allow-incomplete-classpath'],
}

_scala_daCapo_exclude_lib = {
    'scalariform' : ['scala-library-2.8.0.jar'],
    'scalap'      : ['scala-library-2.8.0.jar'],
    'scaladoc'    : ['scala-library-2.8.0.jar'],
    'scalatest'   : ['scala-library-2.8.0.jar'],
    'scalaxb'     : ['scala-library-2.8.0.jar', 'crimson-1.1.3.jar', 'xercesImpl.jar', 'xerces_2_5_0.jar', 'xalan-2.6.0.jar', 'xalan.jar'],
    'tmt'         : ['scala-library-2.8.0.jar'],
    'scalac'      : ['scala-library-2.8.0.jar'],
}

_scala_daCapo_additional_lib = {
}


class ScalaDaCapoNativeImageBenchmarkSuite(mx_sdk_benchmark.ScalaDaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    def name(self):
        return 'scala-dacapo-native-image'

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'scala-dacapo'

    def daCapoIterations(self):
        compiler_iterations = super(ScalaDaCapoNativeImageBenchmarkSuite, self).daCapoIterations()
        return {key: _scala_dacapo_iterations[key] for key in compiler_iterations.keys() if key in _scala_dacapo_iterations.keys()}

    def benchmark_resources(self, benchmark):
        return _scala_dacapo_resources[benchmark]

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        # remove -n X argument from image run args if the flag is true.
        if should_strip_run_args:
            return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']
        else:
            return user_args

    def skip_agent_assertions(self, benchmark, args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def extra_image_build_argument(self, benchmark, args):
        default_args = _SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]

        run_args = self.postprocessRunArgs(self.benchmarkName(), self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        return ['-cp', self.create_classpath(self.benchmarkName())] + vm_args + ['-jar', self.daCapoPath()] + [self.benchmarkName()] + run_args

    def create_classpath(self, benchmark):
        dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources = self.create_dacapo_classpath(self.daCapoPath(), benchmark)
        dacapo_jars = super(ScalaDaCapoNativeImageBenchmarkSuite, self).collect_unique_dependencies(os.path.join(dacapo_extracted, 'jar'), benchmark, _scala_daCapo_exclude_lib)
        cp = ':'.join([self.substitution_path()] + [dacapo_extracted] + dacapo_jars + dacapo_dat_resources + dacapo_nested_resources)
        if benchmark in _scala_daCapo_additional_lib:
            for lib in _scala_daCapo_additional_lib[benchmark]:
                cp += ':' +  super(ScalaDaCapoNativeImageBenchmarkSuite, self).additional_lib(lib)
        return cp

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

    @staticmethod
    def substitution_path():
        path = mx.project('com.oracle.svm.bench').classpath_repr()
        if not os.path.exists(path):
            mx.abort('Path to substitutions for scala dacapo not present: ' + path + '. Did you build all of substratevm?')
        return path


mx_benchmark.add_bm_suite(ScalaDaCapoNativeImageBenchmarkSuite())


class SpecJVM2008NativeImageBenchmarkSuite(mx_sdk_benchmark.SpecJvm2008BenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    """
    SpecJVM2008 for Native Image
    """
    # disables formatted report generation since chart generation with JFreeChart loads fonts from disk (from java.home) to compute string width
    disable_rendered_report = ["-ctf", "false", "-chf", "false"]
    short_run_args = disable_rendered_report + ["-wt", "1", "-it", "1", "-ikv"]
    long_run_args = disable_rendered_report + ["-wt", "10", "-it", "5", "-ikv"]

    def name(self):
        return 'specjvm2008-native-image'

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        args = super().createCommandLineArgs(benchmarks, bmSuiteArgs)

        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]
        return args

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        return super().extra_agent_run_arg(benchmark, args, image_run_args) + SpecJVM2008NativeImageBenchmarkSuite.short_run_args

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        return super().extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args) + SpecJVM2008NativeImageBenchmarkSuite.short_run_args

    def extra_image_build_argument(self, benchmark, args):
        # The reason to add `-H:CompilationExpirationPeriod` is that we encounter non-deterministic compiler crash due to expiration (GR-50701).
        return super().extra_image_build_argument(benchmark, args) + ['-H:CompilationExpirationPeriod=600']

    def extra_run_arg(self, benchmark, args, image_run_args):
        return super().extra_run_arg(benchmark, args, image_run_args) + SpecJVM2008NativeImageBenchmarkSuite.long_run_args

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

mx_benchmark.add_bm_suite(SpecJVM2008NativeImageBenchmarkSuite())
