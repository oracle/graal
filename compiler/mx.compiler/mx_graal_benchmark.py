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
from mx_sdk_benchmark import DaCapoBenchmarkSuite, ScalaDaCapoBenchmarkSuite, RenaissanceBenchmarkSuite, SpecJvm2008BenchmarkSuite
from mx_sdk_benchmark import JvmciJdkVm, SUCCESSFUL_STAGE_PATTERNS

_suite = mx.suite('compiler')


_graal_variants = [
    ('no-tiered-comp', ['-XX:-TieredCompilation'], 0),
    ('economy', [], 0, 'economy'),
    ('economy-no-tiered-comp', ['-XX:-TieredCompilation'], 0, 'economy'),
    ('serialgc', ['-XX:+UseSerialGC'], 12),
    ('pargc', ['-XX:+UseParallelGC'], 12),
    ('g1gc', ['-XX:+UseG1GC'], 12),
    ('zgc', ['-XX:+UseZGC'], 12),
    ('zgc-avx2', ['-XX:+UseZGC', '-XX:UseAVX=2'], 12),
    ('zgc-avx3', ['-XX:+UseZGC', '-XX:UseAVX=3'], 12),
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
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-zgc', ['-server', '-XX:-EnableJVMCI', '-XX:+UseZGC']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default-no-tiered-comp', ['-server', '-XX:-EnableJVMCI', '-XX:-TieredCompilation']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'hosted', ['-server', '-XX:+EnableJVMCI']), _suite, 3)


class CompilerMetricsBenchmarkMixin:

    def __init__(self, metricName, suiteSuffix, metricUnit=("<unit>", str)):
        super().__init__()
        self.metricName = metricName
        self.suiteSuffix = suiteSuffix
        self.metricUnit = metricUnit

    def name(self):
        return f"{super().name()}-{self.suiteSuffix}"

    def benchSuiteName(self, bmSuiteArgs=None):
        return super().name()

    def before(self, bmSuiteArgs):
        fd, self._metrics_file = mkstemp(prefix='metrics.', suffix='.csv', dir='.')
        # we don't need the file descriptor
        os.close(fd)
        super().before(bmSuiteArgs)

    def after(self, bmSuiteArgs):
        os.remove(self._metrics_file)
        super().after(bmSuiteArgs)

    def metricsVmArgs(self):
        return ['-Djdk.graal.AggregatedMetricsFile=' + self.get_csv_filename()]

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.metricsVmArgs() + super().vmArgs(bmSuiteArgs)
        return vmArgs

    def filterResult(self, result):
        return result

    def shorten_vm_flags(self, args):
        # no need for debug value flags
        debugFlags = set(self.metricsVmArgs())
        filtered_args = [x for x in args if x not in debugFlags]
        return super().shorten_vm_flags(filtered_args)

    def get_csv_filename(self):
        return self._metrics_file

    def rules(self, out, benchmarks, bmSuiteArgs):
        assert len(benchmarks) == 1
        return [
                   MetricValueRule(
                       metric_value_file=self.get_csv_filename(),
                       benchmark=benchmarks[0],
                       bench_suite=self.benchSuiteName(),
                       metric_name=self.metricName,
                       metric_unit=self.metricUnit,
                       filter_fn=self.filterResult,
                   ),
               ] + super().rules(out, benchmarks, bmSuiteArgs)


class MetricValueRule(mx_benchmark.CSVBaseRule):
    def __init__(self, metric_value_file, benchmark, bench_suite, metric_name, filter_fn, metric_unit=("<unit>", str)):
        # pylint: disable=expression-not-assigned
        super().__init__(
            colnames=['name', 'value', 'unit'],
            replacement={
                "benchmark": benchmark,
                "bench-suite": bench_suite,
                "vm": "jvmci",
                "config.name": "default",
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
        self.filename = metric_value_file

    def getCSVFiles(self, text):
        collated_filename = self.filename[:-len('.csv')] + '.collated.csv'
        if os.path.isfile(collated_filename):
            return [collated_filename]
        return [self.filename]


class CounterBenchmarkMixin(CompilerMetricsBenchmarkMixin):
    counters = [
        "BytecodesParsed",
        "CompiledBytecodes",
        "CompiledAndInstalledBytecodes",
        "FinalNodeCount",
        "GeneratedLIRInstructions",
        "InstalledCodeSize",
    ]

    def __init__(self):
        super().__init__(metricName="count", suiteSuffix="counters", metricUnit="#")

    def metricsVmArgs(self):
        return super().metricsVmArgs() + ["-Djdk.graal.Counters=" + ','.join(CounterBenchmarkMixin.counters)]

    def filterResult(self, r):
        return r if r['name'] in CounterBenchmarkMixin.counters else None


# Default regex matching all accumulated metrics, extracting their name.
# Change this metric or the filtering function below to constrain the metrics collected.
_accm_metric_re = re.compile(r"(?P<name>\w+)_Accm")
def filterAccumulatedMetric(result):
    m = _accm_metric_re.match(result['name'])
    if m:
        name = m.groupdict()['name']
        result['name'] = name
        return result
    return None


class TimingBenchmarkMixin(CompilerMetricsBenchmarkMixin):

    def __init__(self):
        super().__init__(metricName="compile-time", suiteSuffix="timing")

    def metricsVmArgs(self):
        # Enable all timers
        return super().metricsVmArgs() + ["-Djdk.graal.Timers="]

    def filterResult(self, result):
        return filterAccumulatedMetric(result)

    def removeWarmup(self, results):
        # we do not want warmup results for timing benchmarks
        return [result for result in results if result["metric.name"] != "warmup"]

    def run(self, benchmarks, bmSuiteArgs):
        results = super().run(benchmarks, bmSuiteArgs)
        return self.removeWarmup(results)

class MemUseTrackerBenchmarkMixin(CompilerMetricsBenchmarkMixin):
    def __init__(self):
        super().__init__(metricName="allocated-memory", suiteSuffix="mem-use", metricUnit="B")

    def metricsVmArgs(self):
        # Enable all trackers
        return super().metricsVmArgs() + ["-Djdk.graal.MemUseTrackers="]

    def filterResult(self, result):
        return filterAccumulatedMetric(result)


def timingWrapper(suiteClass):
    class TimingWrapperBenchmark(TimingBenchmarkMixin, suiteClass):
        pass
    return TimingWrapperBenchmark()


def memUseWrapper(suiteClass):
    class MemUseWrapperBenchmark(MemUseTrackerBenchmarkMixin, suiteClass):
        pass
    return MemUseWrapperBenchmark()


mx_benchmark.add_bm_suite(timingWrapper(DaCapoBenchmarkSuite))
mx_benchmark.add_bm_suite(timingWrapper(ScalaDaCapoBenchmarkSuite))
mx_benchmark.add_bm_suite(timingWrapper(RenaissanceBenchmarkSuite))
mx_benchmark.add_bm_suite(timingWrapper(SpecJvm2008BenchmarkSuite))

mx_benchmark.add_bm_suite(memUseWrapper(DaCapoBenchmarkSuite))
mx_benchmark.add_bm_suite(memUseWrapper(ScalaDaCapoBenchmarkSuite))
mx_benchmark.add_bm_suite(memUseWrapper(RenaissanceBenchmarkSuite))
mx_benchmark.add_bm_suite(memUseWrapper(SpecJvm2008BenchmarkSuite))


class JMHNativeImageBenchmarkMixin(mx_benchmark.JMHBenchmarkSuiteBase, mx_sdk_benchmark.NativeImageBenchmarkMixin):

    def get_jmh_result_file(self, bm_suite_args: List[str]) -> Optional[str]:
        """
        Only generate a JMH result file in the run stage. Otherwise the file-based rule (see
        :class:`mx_benchmark.JMHJsonRule`) will produce datapoints at every stage, based on results from a previous
        stage.
        """
        if self.is_native_mode(bm_suite_args) and not self.stages_info.fallback_mode and self.stages_info.current_stage.is_image():
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
        return ['-f0', '-wi', '1', '-i3'] + mx_sdk_benchmark.strip_args_with_number(['-f', '-wi', '-i'], user_args)

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
