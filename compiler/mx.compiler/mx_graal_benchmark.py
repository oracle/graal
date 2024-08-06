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

import re
import os
from tempfile import mkstemp
from typing import List, Optional

import mx
import mx_benchmark
import mx_sdk_benchmark
from mx_benchmark import DataPoints
from mx_sdk_benchmark import DaCapoBenchmarkSuite, ScalaDaCapoBenchmarkSuite
from mx_sdk_benchmark import JvmciJdkVm, SUCCESSFUL_STAGE_PATTERNS, Stage

_suite = mx.suite('compiler')


_graal_variants = [
    ('no-tiered-comp', ['-XX:-TieredCompilation'], 0),
    ('economy', [], 0, 'economy'),
    ('economy-no-tiered-comp', ['-XX:-TieredCompilation'], 0, 'economy'),
    ('serialgc', ['-XX:+UseSerialGC'], 12),
    ('pargc', ['-XX:+UseParallelGC'], 12),
    ('g1gc', ['-XX:+UseG1GC'], 12),
    ('zgc', ['-XX:+UseZGC', '-XX:-ZGenerational'], 12),
    # ('gen-zgc', ['-XX:+UseZGC', '-XX:+ZGenerational'], 12), # GR-45919 not yet supported
    ('zgc-avx2', ['-XX:+UseZGC', '-XX:-ZGenerational', '-XX:UseAVX=2'], 12),
    ('zgc-avx3', ['-XX:+UseZGC', '-XX:-ZGenerational', '-XX:UseAVX=3'], 12),
    ('no-comp-oops', ['-XX:-UseCompressedOops'], 0),
    ('no-profile-info', ['-Djvmci.UseProfilingInformation=false'], 0),
    ('no-splitting', ['-Dpolyglot.engine.Splitting=false'], 0),
    ('limit-truffle-inlining', ['-Dpolyglot.compiler.InliningRecursionDepth=2'], 0),
    ('no-splitting-limit-truffle-inlining', ['-Dpolyglot.engine.Splitting=false', '-Dpolyglot.compiler.InliningRecursionDepth=2'], 0),
    ('no-truffle-bg-comp', ['-Dpolyglot.engine.BackgroundCompilation=false'], 0),
    ('avx0', ['-XX:UseAVX=0'], 11),
    ('avx1', ['-XX:UseAVX=1'], 11),
    ('avx2', ['-XX:UseAVX=2'], 11),
    ('avx3', ['-XX:UseAVX=3'], 11),
]
mx_sdk_benchmark.build_jvmci_vm_variants('server', 'graal-core', ['-server', '-XX:+EnableJVMCI', '-Djdk.graal.CompilerConfiguration=community', '-Djvmci.Compiler=graal'], _graal_variants, suite=_suite, priority=15)

# On 64 bit systems -client is not supported. Nevertheless, when running with -server, we can
# force the VM to just compile code with C1 but not with C2 by adding option -XX:TieredStopAtLevel=1.
# This behavior is the closest we can get to the -client vm configuration.
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'default', ['-server', '-XX:-EnableJVMCI', '-XX:TieredStopAtLevel=1']), suite=_suite, priority=1)
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'hosted', ['-server', '-XX:+EnableJVMCI', '-XX:TieredStopAtLevel=1']), suite=_suite, priority=1)


mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default', ['-server', '-XX:-EnableJVMCI']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-serialgc', ['-server', '-XX:-EnableJVMCI', '-XX:+UseSerialGC']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-pargc', ['-server', '-XX:-EnableJVMCI', '-XX:+UseParallelGC']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-zgc', ['-server', '-XX:-EnableJVMCI', '-XX:+UseZGC', '-XX:-ZGenerational']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-gen-zgc', ['-server', '-XX:-EnableJVMCI', '-XX:+UseZGC', '-XX:+ZGenerational']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-no-tiered-comp', ['-server', '-XX:-EnableJVMCI', '-XX:-TieredCompilation']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'hosted', ['-server', '-XX:+EnableJVMCI']), _suite, 3)


class DebugValueBenchmarkMixin(object):

    def before(self, bmSuiteArgs):
        fd, self._debug_values_file = mkstemp(prefix='debug-values.', suffix='.csv', dir='.')
        # we don't need the file descriptor
        os.close(fd)
        super(DebugValueBenchmarkMixin, self).before(bmSuiteArgs)

    def after(self, bmSuiteArgs):
        os.remove(self._debug_values_file)
        super(DebugValueBenchmarkMixin, self).after(bmSuiteArgs)

    def vmArgs(self, bmSuiteArgs):
        vmArgs = ['-Djdk.graal.AggregatedMetricsFile=' + self.get_csv_filename()] +\
                  super(DebugValueBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    def getBenchmarkName(self):
        raise NotImplementedError()

    def benchSuiteName(self, bmSuiteArgs=None):
        raise NotImplementedError()

    def shorten_vm_flags(self, args):
        # no need for debug value flags
        filtered_args = [x for x in args if not x.startswith("-Djdk.graal.AggregatedMetricsFile")]
        return super(DebugValueBenchmarkMixin, self).shorten_vm_flags(filtered_args)

    def get_csv_filename(self):
        return self._debug_values_file


class DebugValueRule(mx_benchmark.CSVFixedFileRule):
    def __init__(self, debug_value_file, benchmark, bench_suite, metric_name, filter_fn, vm_flags, metric_unit=("<unit>", str)):
        # pylint: disable=expression-not-assigned
        super(DebugValueRule, self).__init__(
            filename=debug_value_file,
            colnames=['name', 'value', 'unit'],
            replacement={
                "benchmark": benchmark,
                "bench-suite": bench_suite,
                "vm": "jvmci",
                "config.name": "default",
                "config.vm-flags": vm_flags,
                "metric.object": ("<name>", str),
                "metric.name": metric_name,
                "metric.value": ("<value>", int),
                "metric.unit": metric_unit,
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            },
            filter_fn=filter_fn,
            delimiter=';', quotechar='"', escapechar='\\'
        ),


class TimingBenchmarkMixin(DebugValueBenchmarkMixin):
    timers = [
        "BackEnd",
        "FrontEnd",
        "GraalCompiler",   # only compilation
        "CompilationTime", # includes code installation
        # LIR stages
        "LIRPhaseTime_AllocationStage",
        "LIRPhaseTime_PostAllocationOptimizationStage",
        "LIRPhaseTime_PreAllocationOptimizationStage",
        # RA phases
        "LIRPhaseTime_LinearScanPhase",
        "LIRPhaseTime_GlobalLivenessAnalysisPhase",
        "LIRPhaseTime_TraceBuilderPhase",
        "LIRPhaseTime_TraceRegisterAllocationPhase",
    ]
    name_re = re.compile(r"(?P<name>\w+)_Accm")

    @staticmethod
    def timerArgs():
        return ["-Djdk.graal.Timers=" + ','.join(TimingBenchmarkMixin.timers)]

    def vmArgs(self, bmSuiteArgs):
        vmArgs = TimingBenchmarkMixin.timerArgs() + super(TimingBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    def name(self):
        return self.benchSuiteName() + "-timing"

    @staticmethod
    def filterResult(r):
        m = TimingBenchmarkMixin.name_re.match(r['name'])
        if m:
            name = m.groupdict()['name']
            if name in TimingBenchmarkMixin.timers:
                r['name'] = name
                return r
        return None

    def shorten_vm_flags(self, args):
        # no need for timer names
        filtered_args = [x for x in args if not x.startswith("-Djdk.graal.Timers=")]
        return super(TimingBenchmarkMixin, self).shorten_vm_flags(filtered_args)

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
                   DebugValueRule(
                       debug_value_file=self.get_csv_filename(),
                       benchmark=self.getBenchmarkName(),
                       bench_suite=self.benchSuiteName(),
                       metric_name="compile-time",
                       vm_flags=self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                       filter_fn=TimingBenchmarkMixin.filterResult,
                   ),
               ] + super(TimingBenchmarkMixin, self).rules(out, benchmarks, bmSuiteArgs)


class CounterBenchmarkMixin(DebugValueBenchmarkMixin):
    counters = [
        "BytecodesParsed",
        "CompiledBytecodes",
        "CompiledAndInstalledBytecodes",
        "FinalNodeCount",
        "GeneratedLIRInstructions",
        "InstalledCodeSize",
    ]

    @staticmethod
    def counterArgs():
        return "-Djdk.graal.Counters=" + ','.join(CounterBenchmarkMixin.counters)

    def vmArgs(self, bmSuiteArgs):
        vmArgs = [CounterBenchmarkMixin.counterArgs()] + super(CounterBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    @staticmethod
    def filterResult(r):
        return r if r['name'] in CounterBenchmarkMixin.counters else None

    def shorten_vm_flags(self, args):
        # not need for timer names
        filtered_args = [x for x in args if not x.startswith("-Djdk.graal.Counters=")]
        return super(CounterBenchmarkMixin, self).shorten_vm_flags(filtered_args)

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            DebugValueRule(
                debug_value_file=self.get_csv_filename(),
                benchmark=self.getBenchmarkName(),
                bench_suite=self.benchSuiteName(),
                metric_name="count",
                metric_unit="#",
                vm_flags=self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                filter_fn=CounterBenchmarkMixin.filterResult,
            ),
        ] + super(CounterBenchmarkMixin, self).rules(out, benchmarks, bmSuiteArgs)


class MemUseTrackerBenchmarkMixin(DebugValueBenchmarkMixin):
    trackers = [
        # LIR stages
        "LIRPhaseMemUse_AllocationStage",
        "LIRPhaseMemUse_PostAllocationOptimizationStage",
        "LIRPhaseMemUse_PreAllocationOptimizationStage",
        # RA phases
        "LIRPhaseMemUse_LinearScanPhase",
        "LIRPhaseMemUse_GlobalLivenessAnalysisPhase",
        "LIRPhaseMemUse_TraceBuilderPhase",
        "LIRPhaseMemUse_TraceRegisterAllocationPhase",
    ]
    name_re = re.compile(r"(?P<name>\w+)_Accm")

    @staticmethod
    def counterArgs():
        return "-Djdk.graal.MemUseTrackers=" + ','.join(MemUseTrackerBenchmarkMixin.trackers)

    def vmArgs(self, bmSuiteArgs):
        vmArgs = [MemUseTrackerBenchmarkMixin.counterArgs()] + super(MemUseTrackerBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    @staticmethod
    def filterResult(r):
        m = MemUseTrackerBenchmarkMixin.name_re.match(r['name'])
        if m:
            name = m.groupdict()['name']
            if name in MemUseTrackerBenchmarkMixin.trackers:
                r['name'] = name
                return r
        return None

    def shorten_vm_flags(self, args):
        # not need for timer names
        filtered_args = [x for x in args if not x.startswith("-Djdk.graal.MemUseTrackers=")]
        return super(MemUseTrackerBenchmarkMixin, self).shorten_vm_flags(filtered_args)

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            DebugValueRule(
                debug_value_file=self.get_csv_filename(),
                benchmark=self.getBenchmarkName(),
                bench_suite=self.benchSuiteName(),
                metric_name="allocated-memory",
                metric_unit="B",
                vm_flags=self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                filter_fn=MemUseTrackerBenchmarkMixin.filterResult,
            ),
        ] + super(MemUseTrackerBenchmarkMixin, self).rules(out, benchmarks, bmSuiteArgs)


class DaCapoTimingBenchmarkMixin(TimingBenchmarkMixin, CounterBenchmarkMixin, MemUseTrackerBenchmarkMixin):

    def host_vm_config_name(self, host_vm, vm):
        return super(DaCapoTimingBenchmarkMixin, self).host_vm_config_name(host_vm, vm) + "-timing"

    def postprocessRunArgs(self, benchname, runArgs):
        self.currentBenchname = benchname
        return super(DaCapoTimingBenchmarkMixin, self).postprocessRunArgs(benchname, runArgs)

    def getBenchmarkName(self):
        return self.currentBenchname

    def removeWarmup(self, results):
        # we do not want warmup results for timing benchmarks
        return [result for result in results if result["metric.name"] != "warmup"]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(DaCapoTimingBenchmarkMixin, self).run(benchmarks, bmSuiteArgs)
        return self.removeWarmup(results)


class DaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 (Bach) benchmark suite implementation."""

    def benchSuiteName(self, bmSuiteArgs=None):
        return "dacapo"


mx_benchmark.add_bm_suite(DaCapoTimingBenchmarkSuite())


class ScalaDaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, ScalaDaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def benchSuiteName(self, bmSuiteArgs=None):
        return "scala-dacapo"


mx_benchmark.add_bm_suite(ScalaDaCapoTimingBenchmarkSuite())


class JMHNativeImageBenchmarkMixin(mx_benchmark.JMHBenchmarkSuiteBase, mx_sdk_benchmark.NativeImageBenchmarkMixin):

    def get_jmh_result_file(self, bm_suite_args: List[str]) -> Optional[str]:
        """
        Only generate a JMH result file in the run stage. Otherwise the file-based rule (see
        :class:`mx_benchmark.JMHJsonRule`) will produce datapoints at every stage, based on results from a previous
        stage.
        """
        if self.is_native_mode(bm_suite_args) and not self.stages_info.fallback_mode:
            # At this point, the StagesInfo class may not have all the information yet, in that case we rely on the
            # requested stage. But if this function is called later again when it is fully set up, we have to use the
            # effective stage instead.
            # This is important so that the JMH parsing rule is only enabled when the stage actually ran (if it is
            # skipped, it would otherwise pick up a previous result file)
            if self.stages_info.is_set_up:
                current_stage = self.stages_info.effective_stage
            else:
                current_stage = self.stages_info.requested_stage

            if current_stage not in [Stage.AGENT, Stage.INSTRUMENT_RUN, Stage.RUN]:
                return None

        return super().get_jmh_result_file(bm_suite_args)

    def fallback_mode_reason(self, bm_suite_args: List[str]) -> Optional[str]:
        """
        JMH benchmarks need to use the fallback mode if --jmh-run-individually is used.
        The flag causes one native image to be built per JMH benchmark. This is fundamentally incompatible with the
        default benchmarking mode of running each stage on its own because a benchmark will overwrite the intermediate
        files of the previous benchmark if not all stages are run at once.

        In the fallback mode, collection of performance data is limited. Only performance data of the ``run`` stage can
        reliably be collected. Other metrics, such as image build statistics or profiling performance cannot reliably be
        collected because they cannot be attributed so a specific individual JMH benchmark.
        """
        if self.jmhArgs(bm_suite_args).jmh_run_individually:
            return "--jmh-run-individually is not compatible with selecting individual stages"
        else:
            return None

    def extra_image_build_argument(self, benchmark, args):
        # JMH does HotSpot-specific field offset checks in class initializers
        return ['--initialize-at-build-time=org.openjdk.jmh,joptsimple.internal'] + super(JMHNativeImageBenchmarkMixin, self).extra_image_build_argument(benchmark, args)

    def extra_run_arg(self, benchmark, args, image_run_args):
        # JMH does not support forks with native-image. In the distant future we can capture this case.
        user_args = super(JMHNativeImageBenchmarkMixin, self).extra_run_arg(benchmark, args, image_run_args)
        return ['-f0'] + mx_sdk_benchmark.strip_args_with_number(['-f'], user_args)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        # Don't waste time and energy collecting reflection config.
        user_args = super(JMHNativeImageBenchmarkMixin, self).extra_agent_run_arg(benchmark, args, image_run_args)
        return ['-f0', '-wi', '1', '-i1'] + mx_sdk_benchmark.strip_args_with_number(['-f', '-wi', '-i'], user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        # Don't waste time profiling the same code but still wait for compilation on HotSpot.
        user_args = super(JMHNativeImageBenchmarkMixin, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        return ['-f0', '-wi', '1', '-i5'] + mx_sdk_benchmark.strip_args_with_number(['-f', '-wi', '-i'], user_args)

    def benchmarkName(self):
        return self.name()


class JMHJarBasedNativeImageBenchmarkMixin(JMHNativeImageBenchmarkMixin):
    """Provides extra command line checking for JAR-based native image JMH suites."""

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        jmhOptions = self._extractJMHOptions(args)
        for index, option in enumerate(jmhOptions):
            argument = jmhOptions[index+1] if index + 1 < len(jmhOptions) else None
            if option == '-f' and argument != '0':
                mx.warn(f"JMH native images don't support -f with non-zero argument {argument}, ignoring it")
            elif option.startswith('-jvmArgs'):
                mx.warn(f"JMH native images don't support option {option}, ignoring it")
        return super(JMHJarBasedNativeImageBenchmarkMixin, self).extra_agent_run_arg(benchmark, args, image_run_args)


class JMHRunnerGraalCoreBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite, JMHNativeImageBenchmarkMixin):

    def alternative_suite(self):
        return "jmh-whitebox"

    def warning_only(self):
        return False

    def name(self):
        return "jmh-graal-core-whitebox"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(JMHRunnerGraalCoreBenchmarkSuite())


class JMHJarGraalCoreBenchmarkSuite(mx_benchmark.JMHJarBenchmarkSuite, JMHJarBasedNativeImageBenchmarkMixin):

    def name(self):
        return "jmh-jar"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(JMHJarGraalCoreBenchmarkSuite())


class JMHDistGraalCoreBenchmarkSuite(mx_benchmark.JMHDistBenchmarkSuite, JMHJarBasedNativeImageBenchmarkMixin):

    def name(self):
        return "jmh-dist"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def filter_distribution(self, dist):
        return super(JMHDistGraalCoreBenchmarkSuite, self).filter_distribution(dist) and \
               not JMHDistWhiteboxBenchmarkSuite.is_whitebox_dependency(dist)

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS


mx_benchmark.add_bm_suite(JMHDistGraalCoreBenchmarkSuite())


class JMHDistWhiteboxBenchmarkSuite(mx_benchmark.JMHDistBenchmarkSuite, JMHJarBasedNativeImageBenchmarkMixin):

    def name(self):
        return "jmh-whitebox"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    @staticmethod
    def is_whitebox_dependency(dist):
        return hasattr(dist, 'graalWhiteboxDistribution') and dist.graalWhiteboxDistribution

    def filter_distribution(self, dist):
        return super(JMHDistWhiteboxBenchmarkSuite, self).filter_distribution(dist) and \
               JMHDistWhiteboxBenchmarkSuite.is_whitebox_dependency(dist)


    def extraVmArgs(self):
        # This is required to use jdk.internal.module.Modules for doing arbitrary exports
        extra = ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
                 '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED',
                 '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED',
                 '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED',
                 '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED',
                 '--add-exports=jdk.graal.compiler/jdk.graal.compiler.graph=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.benchmark=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.debug=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.library=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.memory=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.strings=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED',
                 '--add-exports=org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED']
        return extra + super(JMHDistWhiteboxBenchmarkSuite, self).extraVmArgs()

    def getJMHEntry(self, bmSuiteArgs):
        assert self.dist
        return [mx.distribution(self.dist).mainClass]

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS


mx_benchmark.add_bm_suite(JMHDistWhiteboxBenchmarkSuite())
