#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import re
import os
from tempfile import mkstemp
import itertools

import mx
import mx_benchmark
import mx_compiler
from mx_java_benchmarks import DaCapoBenchmarkSuite, ScalaDaCapoBenchmarkSuite

_suite = mx.suite('compiler')


_IMAGE_JMH_BENCHMARK_ARGS = [
    # JMH does not support forks with native-image. In the distant future we can capture this case.
    '-Dnative-image.benchmark.extra-run-arg=-f0',

    # JMH does HotSpot-specific field offset checks in class initializers
    '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.openjdk.jmh,joptsimple.internal',

    # Don't waste time and energy collecting reflection config.
    '-Dnative-image.benchmark.extra-agent-run-arg=-f0',
    '-Dnative-image.benchmark.extra-agent-run-arg=-wi',
    '-Dnative-image.benchmark.extra-agent-run-arg=1',
    '-Dnative-image.benchmark.extra-agent-run-arg=-i1',

    # Don't waste time profiling the same code but still wait for compilation on HotSpot.
    '-Dnative-image.benchmark.extra-profile-run-arg=-f0',
    '-Dnative-image.benchmark.extra-profile-run-arg=-wi',
    '-Dnative-image.benchmark.extra-profile-run-arg=1',
    '-Dnative-image.benchmark.extra-profile-run-arg=-i5',
]


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

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        tag = mx.get_jdk_option().tag
        if tag and tag != mx_compiler._JVMCI_JDK_TAG:
            mx.abort("The '{0}/{1}' VM requires '--jdk={2}'".format(
                self.name(), self.config_name(), mx_compiler._JVMCI_JDK_TAG))
        return mx.get_jdk(tag=mx_compiler._JVMCI_JDK_TAG).run_java(
            args, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)

    def rules(self, output, benchmarks, bmSuiteArgs):
        if benchmarks and len(benchmarks) == 1:
            return [
                mx_benchmark.StdOutRule(
                    # r"Statistics for (?P<methods>[0-9]+) bytecoded nmethods for JVMCI:\n total in heap  = (?P<value>[0-9]+)",
                    r"Statistics for (?P<methods>[0-9]+) bytecoded nmethods for JVMCI:\n total in heap  = (?P<value>[0-9]+)",
                    {
                        "benchmark": benchmarks[0],
                        "vm": "jvmci",
                        "metric.name": "code-size",
                        "metric.value": ("<value>", int),
                        "metric.unit": "B",
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.better": "lower",
                        "metric.iteration": 0,
                    })
            ]
        return []


mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default', ['-server', '-XX:-EnableJVMCI', '-XX:-UseJVMCICompiler']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'hosted', ['-server', '-XX:+EnableJVMCI']), _suite, 3)

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
            if len(variant) == 2:
                var_name, var_args = variant
                var_priority = priority
            else:
                var_name, var_args, var_priority = variant
            mx_benchmark.add_java_vm(
                JvmciJdkVm(raw_name, extended_raw_config_name + '-' + var_name, extended_extra_args + var_args), suite, var_priority)

_graal_variants = [
    ('g1gc', ['-XX:+UseG1GC'], 12),
    ('no-comp-oops', ['-XX:-UseCompressedOops'], 0),
    ('no-splitting', ['-Dgraal.TruffleSplitting=false'], 0),
    ('limit-truffle-inlining', ['-Dgraal.TruffleMaximumRecursiveInlining=2'], 0),
    ('no-splitting-limit-truffle-inlining', ['-Dgraal.TruffleSplitting=false', '-Dgraal.TruffleMaximumRecursiveInlining=2'], 0),
    ('la-inline', ['-Dgraal.TruffleLanguageAgnosticInlining=true'], 0),
]
build_jvmci_vm_variants('server', 'graal-core', ['-server', '-XX:+EnableJVMCI', '-Dgraal.CompilerConfiguration=community', '-Djvmci.Compiler=graal'], _graal_variants, suite=_suite, priority=15)

# On 64 bit systems -client is not supported. Nevertheless, when running with -server, we can
# force the VM to just compile code with C1 but not with C2 by adding option -XX:TieredStopAtLevel=1.
# This behavior is the closest we can get to the -client vm configuration.
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'default', ['-server', '-XX:-EnableJVMCI', '-XX:-UseJVMCICompiler', '-XX:TieredStopAtLevel=1']), suite=_suite, priority=1)
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'hosted', ['-server', '-XX:+EnableJVMCI', '-XX:TieredStopAtLevel=1']), suite=_suite, priority=1)

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
        vmArgs = ['-Dgraal.AggregatedMetricsFile=' + self.get_csv_filename()] +\
                  super(DebugValueBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    def getBenchmarkName(self):
        raise NotImplementedError()

    def benchSuiteName(self):
        raise NotImplementedError()

    def shorten_vm_flags(self, args):
        # no need for debug value flags
        filtered_args = [x for x in args if not x.startswith("-Dgraal.AggregatedMetricsFile")]
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
        return ["-Dgraal.Timers=" + ','.join(TimingBenchmarkMixin.timers)]

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
        filtered_args = [x for x in args if not x.startswith("-Dgraal.Timers=")]
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
        return "-Dgraal.Counters=" + ','.join(CounterBenchmarkMixin.counters)

    def vmArgs(self, bmSuiteArgs):
        vmArgs = [CounterBenchmarkMixin.counterArgs()] + super(CounterBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    @staticmethod
    def filterResult(r):
        return r if r['name'] in CounterBenchmarkMixin.counters else None

    def shorten_vm_flags(self, args):
        # not need for timer names
        filtered_args = [x for x in args if not x.startswith("-Dgraal.Counters=")]
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
        return "-Dgraal.MemUseTrackers=" + ','.join(MemUseTrackerBenchmarkMixin.trackers)

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
        filtered_args = [x for x in args if not x.startswith("-Dgraal.MemUseTrackers=")]
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

    def benchSuiteName(self):
        return "dacapo"


mx_benchmark.add_bm_suite(DaCapoTimingBenchmarkSuite())


class ScalaDaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, ScalaDaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def benchSuiteName(self):
        return "scala-dacapo"


mx_benchmark.add_bm_suite(ScalaDaCapoTimingBenchmarkSuite())


class JMHRunnerGraalCoreBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite): # pylint: disable=too-many-ancestors

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

    def extraVmArgs(self):
        return ['-XX:-UseJVMCIClassLoader'] + super(JMHRunnerGraalCoreBenchmarkSuite, self).extraVmArgs() + _IMAGE_JMH_BENCHMARK_ARGS


mx_benchmark.add_bm_suite(JMHRunnerGraalCoreBenchmarkSuite())


class JMHJarGraalCoreBenchmarkSuite(mx_benchmark.JMHJarBenchmarkSuite):

    def extraVmArgs(self):
        return super(JMHJarGraalCoreBenchmarkSuite, self).extraVmArgs() + _IMAGE_JMH_BENCHMARK_ARGS

    def name(self):
        return "jmh-jar"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"


mx_benchmark.add_bm_suite(JMHJarGraalCoreBenchmarkSuite())


class JMHDistGraalCoreBenchmarkSuite(mx_benchmark.JMHDistBenchmarkSuite):

    def extraVmArgs(self):
        return super(JMHDistGraalCoreBenchmarkSuite, self).extraVmArgs() + _IMAGE_JMH_BENCHMARK_ARGS

    def name(self):
        return "jmh-dist"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def filter_distribution(self, dist):
        return super(JMHDistGraalCoreBenchmarkSuite, self).filter_distribution(dist) and \
               not any(JMHDistWhiteboxBenchmarkSuite.whitebox_dependency(dist))


mx_benchmark.add_bm_suite(JMHDistGraalCoreBenchmarkSuite())


class JMHDistWhiteboxBenchmarkSuite(mx_benchmark.JMHDistBenchmarkSuite):

    def name(self):
        return "jmh-whitebox"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    @staticmethod
    def whitebox_dependency(dist):
        return itertools.chain(
            (dep.name.startswith('GRAAL') for dep in dist.deps),
            (dep.name.startswith('org.graalvm.compiler') for dep in dist.archived_deps())
        )

    def filter_distribution(self, dist):
        return super(JMHDistWhiteboxBenchmarkSuite, self).filter_distribution(dist) and \
               any(JMHDistWhiteboxBenchmarkSuite.whitebox_dependency(dist))

    def extraVmArgs(self):
        if mx_compiler.isJDK8:
            extra = ['-XX:-UseJVMCIClassLoader']
        else:
            # This is required to use jdk.internal.module.Modules for doing arbitrary exports
            extra = ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED',
                     '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.services=ALL-UNNAMED',
                     '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED',
                     '--add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED',
                     '--add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.graph=ALL-UNNAMED']
        return extra + super(JMHDistWhiteboxBenchmarkSuite, self).extraVmArgs()

    def getJMHEntry(self, bmSuiteArgs):
        assert self.dist
        return [mx.distribution(self.dist).mainClass]


mx_benchmark.add_bm_suite(JMHDistWhiteboxBenchmarkSuite())
