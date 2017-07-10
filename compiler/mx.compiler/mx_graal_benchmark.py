#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

import argparse
import re
import os
from os.path import join, exists
from tempfile import mkdtemp, mkstemp
from shutil import rmtree

import mx
import mx_benchmark
import mx_compiler
from mx_benchmark import ParserEntry
from argparse import ArgumentParser

_suite = mx.suite('compiler')

# Short-hand commands used to quickly run common benchmarks.
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
    'specjbb2005': [
      lambda args: mx_benchmark.benchmark(["specjbb2005"] + args),
      '[-- [VM options] [-- [SPECjbb2005 options]]]'
    ],
    'specjbb2013': [
      lambda args: mx_benchmark.benchmark(["specjbb2013"] + args),
      '[-- [VM options] [-- [SPECjbb2013 options]]]'
    ],
    'specjbb2015': [
      lambda args: mx_benchmark.benchmark(["specjbb2015"] + args),
      '[-- [VM options] [-- [SPECjbb2015 options]]]'
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


# dacapo suite parsers.
def _create_dacapo_parser():
    parser = ArgumentParser(add_help=False, usage=mx_benchmark._mx_benchmark_usage_example + " -- <options> -- ...")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--keep-scratch", action="store_true", help="Do not delete scratch directory after benchmark execution.")
    group.add_argument("--no-scratch", action="store_true", help="Do not execute benchmark in scratch directory.")
    return parser

mx_benchmark.parsers["dacapo_benchmark_suite"] = ParserEntry(
    _create_dacapo_parser(),
    "\n\nFlags for DaCapo-style benchmark suites:\n"
)

class JvmciJdkVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, raw_name, raw_config_name, extra_args):
        self.raw_name = raw_name
        self.raw_config_name = raw_config_name
        self.extra_args = extra_args

    def name(self):
        return self.raw_name

    def config_name(self):
        return self.raw_config_name

    def post_process_command_line_args(self, args):
        return self.extra_args + args

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        tag = mx.get_jdk_option().tag
        if tag and tag != mx_compiler._JVMCI_JDK_TAG:
            mx.abort("The '{0}/{1}' VM requires '--jdk={2}'".format(
                self.name(), self.config_name(), mx_compiler._JVMCI_JDK_TAG))
        mx.get_jdk(tag=mx_compiler._JVMCI_JDK_TAG).run_java(
            args, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)


mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default', ['-server', '-XX:-EnableJVMCI']), _suite, 2)
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'hosted', ['-server', '-XX:+EnableJVMCI']), _suite, 3)

def build_jvmci_vm_variants(raw_name, raw_config_name, extra_args, variants, include_default=True, suite=None, priority=0):
    for prefix, args in [('', ['-XX:+UseJVMCICompiler']), ('hosted-', ['-XX:-UseJVMCICompiler'])]:
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
    ('tracera', ['-Dgraal.TraceRA=true'], 11),
    ('tracera-bu', ['-Dgraal.TraceRA=true', '-Dgraal.TraceRAPolicy=BottomUpOnly'], 10),
    ('g1gc', ['-XX:+UseG1GC'], 12)
]
build_jvmci_vm_variants('server', 'graal-core', ['-server', '-XX:+EnableJVMCI', '-Dgraal.CompilerConfiguration=core', '-Djvmci.Compiler=graal'], _graal_variants, suite=_suite, priority=15)

# On 64 bit systems -client is not supported. Nevertheless, when running with -server, we can
# force the VM to just compile code with C1 but not with C2 by adding option -XX:TieredStopAtLevel=1.
# This behavior is the closest we can get to the -client vm configuration.
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'default', ['-server', '-XX:-EnableJVMCI', '-XX:TieredStopAtLevel=1']), suite=_suite, priority=1)
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

    def getBechmarkName(self):
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
                       benchmark=self.getBechmarkName(),
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
                benchmark=self.getBechmarkName(),
                bench_suite=self.benchSuiteName(),
                metric_name="count",
                metric_unit="#",
                vm_flags=self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                filter_fn=CounterBenchmarkMixin.filterResult,
            ),
        ] + super(CounterBenchmarkMixin, self).rules(out, benchmarks, bmSuiteArgs)


class DaCapoTimingBenchmarkMixin(TimingBenchmarkMixin, CounterBenchmarkMixin):

    def host_vm_config_name(self, host_vm, vm):
        return super(DaCapoTimingBenchmarkMixin, self).host_vm_config_name(host_vm, vm) + "-timing"

    def postprocessRunArgs(self, benchname, runArgs):
        self.currentBenchname = benchname
        return super(DaCapoTimingBenchmarkMixin, self).postprocessRunArgs(benchname, runArgs)

    def getBechmarkName(self):
        return self.currentBenchname

    def removeWarmup(self, results):
        # we do not want warmup results for timing benchmarks
        return [result for result in results if result["metric.name"] != "warmup"]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(DaCapoTimingBenchmarkMixin, self).run(benchmarks, bmSuiteArgs)
        return self.removeWarmup(results)


class MoveProfilingBenchmarkMixin(object):
    """Benchmark-mixin for measuring the number of dynamically executed move instructions.

    See org.graalvm.compiler.lir.profiling.MoveProfilingPhase for more details.
    """
    benchmark_counters_file = 'benchmark-counters.csv'

    def vmArgs(self, bmSuiteArgs):
        vmArgs = [
                  self.get_dynamic_counters_argument(),
                  '-XX:JVMCICounterSize=10',
                  '-Dgraal.LIRProfileMoves=true',
                  '-Dgraal.DynamicCountersPrintGroupSeparator=false',
                  '-Dgraal.BenchmarkCountersFile=' + MoveProfilingBenchmarkMixin.benchmark_counters_file] + super(MoveProfilingBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    def get_dynamic_counters_argument(self):
        """ The argument to select the desired dynamic counters mode. Possible values are
        `-Dgraal.GenericDynamicCounters=...`, `-Dgraal.TimedDynamicCounters=...` or
        `-Dgraal.BenchmarkDynamicCounters=...`. See org.graalvm.compiler.hotspot.debug.BenchmarkCounters
        for more information.
        """
        raise NotImplementedError()

    def getBechmarkName(self):
        raise NotImplementedError()

    def benchSuiteName(self):
        raise NotImplementedError()

    def name(self):
        return self.benchSuiteName() + "-move-profiling"

    def shorten_flags(self, args):
        def _shorten(x):
            if any(p in x for p in ["DynamicCounter", "BenchmarkCounter"]):
                return "..."
            return x

        arg_str = " ".join((_shorten(x) for x in args))
        return mx_benchmark.Rule.crop_back("...")(arg_str)

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
          mx_benchmark.CSVStdOutFileRule(
            pattern="Writing benchmark counters to '(?P<name>[^']*)'",
            match_name="name",
            colnames=['type', 'group', 'name', 'value'],
            replacement={
              "benchmark": self.getBechmarkName(),
              "bench-suite": self.benchSuiteName(),
              "vm": "jvmci",
              "config.name": "default",
              "config.vm-flags": self.shorten_flags(self.vmArgs(bmSuiteArgs)),
              "metric.object": ("<name>", str),
              "metric.name": ("dynamic-moves", str),
              "metric.value": ("<value>", int),
              "metric.unit": "#",
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "lower",
              "metric.iteration": 0
            },
            delimiter=';', quotechar='"', escapechar='\\'
          ),
        ]


class DaCapoMoveProfilingBenchmarkMixin(MoveProfilingBenchmarkMixin):

    def vmArgs(self, bmSuiteArgs):
        # we need to boostrap to eagerly initialize Graal otherwise we cannot intercept
        # stdio since it is rerouted by the dacapo harness
        return ['-XX:+BootstrapJVMCI', '-Dgraal.BootstrapInitializeOnly=true'] +  super(DaCapoMoveProfilingBenchmarkMixin, self).vmArgs(bmSuiteArgs)

    def get_dynamic_counters_argument(self):
        # we only count the moves executed during the last (the measurement) iteration
        return '-Dgraal.BenchmarkDynamicCounters=err, starting ====, PASSED in '

    def postprocessRunArgs(self, benchname, runArgs):
        self.currentBenchname = benchname
        return super(DaCapoMoveProfilingBenchmarkMixin, self).postprocessRunArgs(benchname, runArgs)

    def getBechmarkName(self):
        return self.currentBenchname


class AveragingBenchmarkMixin(object):
    """Provides utilities for computing the average time of the latest warmup runs.

    Note that this mixin expects that the main benchmark class produces a sequence of
    datapoints that have the metric.name dimension set to "warmup".
    To add the average, this mixin appends a new datapoint whose metric.name dimension
    is set to "time".

    Benchmarks that mix in this class must manually invoke methods for computing extra
    iteration counts and averaging, usually in their run method.
    """

    def getExtraIterationCount(self, iterations):
        # Uses the number of warmup iterations to calculate the number of extra
        # iterations needed by the benchmark to compute a more stable average result.
        return min(20, iterations, max(6, int(iterations * 0.4)))

    def addAverageAcrossLatestResults(self, results):
        # Postprocess results to compute the resulting time by taking the average of last N runs,
        # where N is 20% of the maximum number of iterations, at least 5 and at most 10.
        warmupResults = [result for result in results if result["metric.name"] == "warmup"]
        if warmupResults:
            lastIteration = max((result["metric.iteration"] for result in warmupResults))
            resultIterations = self.getExtraIterationCount(lastIteration + 1)
            totalTimeForAverage = 0.0
            for i in range(lastIteration - resultIterations + 1, lastIteration + 1):
                result = next(result for result in warmupResults if result["metric.iteration"] == i)
                totalTimeForAverage += result["metric.value"]
            averageResult = next(result for result in warmupResults if result["metric.iteration"] == 0).copy()
            averageResult["metric.value"] = totalTimeForAverage / resultIterations
            averageResult["metric.name"] = "time"
            results.append(averageResult)


class BaseDaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, AveragingBenchmarkMixin):
    """Base benchmark suite for DaCapo-based benchmarks.

    This suite can only run a single benchmark in one VM invocation.
    """
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchSuiteName(self):
        return self.name()

    def before(self, bmSuiteArgs):
        parser = mx_benchmark.parsers["dacapo_benchmark_suite"].parser
        bmArgs, _ = parser.parse_known_args(bmSuiteArgs)
        self.keepScratchDir = bmArgs.keep_scratch
        if not bmArgs.no_scratch:
            self._create_tmp_workdir()
        else:
            mx.warn("NO scratch directory created! (--no-scratch)")
            self.workdir = None

    def _create_tmp_workdir(self):
        self.workdir = mkdtemp(prefix='dacapo-work.', dir='.')

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return self.workdir

    def after(self, bmSuiteArgs):
        if hasattr(self, "keepScratchDir") and self.keepScratchDir:
            mx.warn("Scratch directory NOT deleted (--keep-scratch): {0}".format(self.workdir))
        elif self.workdir:
            rmtree(self.workdir)

    def repairDatapointsAndFail(self, benchmarks, bmSuiteArgs, partialResults, message):
        try:
            super(BaseDaCapoBenchmarkSuite, self).repairDatapointsAndFail(benchmarks, bmSuiteArgs, partialResults, message)
        finally:
            if self.workdir:
                # keep old workdir for investigation, create a new one for further benchmarking
                mx.warn("Keeping scratch directory after failed benchmark: {0}".format(self.workdir))
                self._create_tmp_workdir()

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

    def parserNames(self):
        return super(BaseDaCapoBenchmarkSuite, self).parserNames() + ["dacapo_benchmark_suite"]

    def validateEnvironment(self):
        if not self.daCapoPath():
            raise RuntimeError(
                "Neither " + self.daCapoClasspathEnvVarName() + " variable nor " +
                self.daCapoLibraryName() + " library specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-n", default=None)
        args, remaining = parser.parse_known_args(runArgs)
        if args.n:
            if args.n.isdigit():
                return ["-n", args.n] + remaining
            if args.n == "-1":
                return None
        else:
            iterations = self.daCapoIterations()[benchname]
            if iterations == -1:
                return None
            else:
                iterations = iterations + self.getExtraIterationCount(iterations)
                return ["-n", str(iterations)] + remaining

    def vmArgs(self, bmSuiteArgs):
        parser = mx_benchmark.parsers["dacapo_benchmark_suite"].parser
        _, remainingBmSuiteArgs = parser.parse_known_args(bmSuiteArgs)
        vmArgs = super(BaseDaCapoBenchmarkSuite, self).vmArgs(remainingBmSuiteArgs)
        return vmArgs

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            raise RuntimeError(
                "Suite runs only a single benchmark.")
        if len(benchmarks) != 1:
            raise RuntimeError(
                "Suite runs only a single benchmark, got: {0}".format(benchmarks))
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return None
        return (
            self.vmArgs(bmSuiteArgs) + ["-jar"] + [self.daCapoPath()] +
            [benchmarks[0]] + runArgs)

    def repairDatapoints(self, benchmarks, bmSuiteArgs, partialResults):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-n", default=None)
        args, _ = parser.parse_known_args(self.runArgs(bmSuiteArgs))
        if args.n and args.n.isdigit():
            iterations = int(args.n)
        else:
            iterations = self.daCapoIterations()[benchmarks[0]]
            iterations = iterations + self.getExtraIterationCount(iterations)
        for i in range(0, iterations):
            if next((p for p in partialResults if p["metric.iteration"] == i), None) is None:
                datapoint = {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": -1,
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function":  "id",
                    "metric.better": "lower",
                    "metric.iteration": i
                }
                partialResults.append(datapoint)
        datapoint = {
            "benchmark": benchmarks[0],
            "bench-suite": self.benchSuiteName(),
            "vm": "jvmci",
            "config.name": "default",
            "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
            "metric.name": "time",
            "metric.value": -1,
            "metric.unit": "ms",
            "metric.type": "numeric",
            "metric.score-function": "id",
            "metric.better": "lower",
            "metric.iteration": 0
        }
        partialResults.append(datapoint)

    def benchmarkList(self, bmSuiteArgs):
        return [key for key, value in self.daCapoIterations().iteritems() if value != -1]

    def daCapoSuiteTitle(self):
        """Title string used in the output next to the performance result."""
        raise NotImplementedError()

    def successPatterns(self):
        return [
            re.compile(
                r"^===== " + re.escape(self.daCapoSuiteTitle()) + " ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(
                r"^===== " + re.escape(self.daCapoSuiteTitle()) + " ([a-zA-Z0-9_]+) FAILED (warmup|) =====", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def shorten_vm_flags(self, args):
        return mx_benchmark.Rule.crop_back("...")(' '.join(args))

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return []
        totalIterations = int(runArgs[runArgs.index("-n") + 1])
        return [
          mx_benchmark.StdOutRule(
            r"===== " + re.escape(self.daCapoSuiteTitle()) + " (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
          mx_benchmark.StdOutRule(
            r"===== " + re.escape(self.daCapoSuiteTitle()) + " (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
              "metric.iteration": totalIterations - 1
            }
          ),
          mx_benchmark.StdOutRule(
            r"===== " + re.escape(self.daCapoSuiteTitle()) + " (?P<benchmark>[a-zA-Z0-9_]+) completed warmup [0-9]+ in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
    "avrora"     : 20,
    "batik"      : 40,
    "eclipse"    : -1,
    "fop"        : 40,
    "h2"         : 25,
    "jython"     : 40,
    "luindex"    : 15,
    "lusearch"   : 40,
    "pmd"        : 30,
    "sunflow"    : 35,
    "tomcat"     : -1, # Stopped working as of 8u92
    "tradebeans" : -1,
    "tradesoap"  : -1,
    "xalan"      : 30,
}


class DaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite):
    """DaCapo 9.12 (Bach) benchmark suite implementation."""

    def name(self):
        return "dacapo"

    def daCapoSuiteTitle(self):
        return "DaCapo 9.12"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_CP"

    def daCapoLibraryName(self):
        return "DACAPO"

    def daCapoIterations(self):
        return _daCapoIterations

    def flakySuccessPatterns(self):
        return [
            re.compile(
                r"^javax.ejb.FinderException: Cannot find account for",
                re.MULTILINE),
            re.compile(
                r"^java.lang.Exception: TradeDirect:Login failure for user:",
                re.MULTILINE),
        ]


mx_benchmark.add_bm_suite(DaCapoBenchmarkSuite())


class DaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 (Bach) benchmark suite implementation."""

    def benchSuiteName(self):
        return "dacapo"


mx_benchmark.add_bm_suite(DaCapoTimingBenchmarkSuite())


class DaCapoMoveProfilingBenchmarkSuite(DaCapoMoveProfilingBenchmarkMixin, DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 (Bach) benchmark suite implementation."""

    def benchSuiteName(self):
        return "dacapo"


mx_benchmark.add_bm_suite(DaCapoMoveProfilingBenchmarkSuite())


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


class ScalaDaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite):
    """Scala DaCapo benchmark suite implementation."""

    def name(self):
        return "scala-dacapo"

    def daCapoSuiteTitle(self):
        return "DaCapo 0.1.0-SNAPSHOT"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_SCALA_CP"

    def daCapoLibraryName(self):
        return "DACAPO_SCALA"

    def daCapoIterations(self):
        return _daCapoScalaConfig

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        skip_patterns = super(ScalaDaCapoBenchmarkSuite, self).flakySuccessPatterns()
        if "specs" in benchmarks:
            skip_patterns += [
                    re.escape(r"Line count validation failed for stdout.log, expecting 1039 found 1040"),
                ]
        return skip_patterns


mx_benchmark.add_bm_suite(ScalaDaCapoBenchmarkSuite())


class ScalaDaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, ScalaDaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def benchSuiteName(self):
        return "scala-dacapo"


mx_benchmark.add_bm_suite(ScalaDaCapoTimingBenchmarkSuite())


class ScalaDaCapoMoveProfilingBenchmarkSuite(DaCapoMoveProfilingBenchmarkMixin, ScalaDaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def benchSuiteName(self):
        return "scala-dacapo"


mx_benchmark.add_bm_suite(ScalaDaCapoMoveProfilingBenchmarkSuite())


_allSpecJVM2008Benches = [
    'startup.helloworld',
    'startup.compiler.compiler',
    # 'startup.compiler.sunflow', # disabled until timeout problem in jdk8 is resolved
    'startup.compress',
    'startup.crypto.aes',
    'startup.crypto.rsa',
    'startup.crypto.signverify',
    'startup.mpegaudio',
    'startup.scimark.fft',
    'startup.scimark.lu',
    'startup.scimark.monte_carlo',
    'startup.scimark.sor',
    'startup.scimark.sparse',
    'startup.serial',
    'startup.sunflow',
    'startup.xml.transform',
    'startup.xml.validation',
    'compiler.compiler',
    # 'compiler.sunflow',
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


class SpecJvm2008BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """SpecJVM2008 benchmark suite implementation.

    This benchmark suite can run multiple benchmarks as part of one VM run.
    """
    def name(self):
        return "specjvm2008"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def specJvmPath(self):
        specjvm2008 = mx.get_env("SPECJVM2008")
        if specjvm2008 is None:
            mx.abort("Please set the SPECJVM2008 environment variable to a " +
                "SPECjvm2008 directory.")
        jarpath = join(specjvm2008, "SPECjvm2008.jar")
        if not exists(jarpath):
            mx.abort("The SPECJVM2008 environment variable points to a directory " +
                "without the SPECjvm2008.jar file.")
        return jarpath

    def validateEnvironment(self):
        if not self.specJvmPath():
            raise RuntimeError(
                "The SPECJVM2008 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return mx.get_env("SPECJVM2008")

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            # No benchmark specified in the command line, so run everything.
            benchmarks = self.benchmarkList(bmSuiteArgs)
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + ["-jar"] + [self.specJvmPath()] + runArgs + benchmarks

    def benchmarkList(self, bmSuiteArgs):
        return _allSpecJVM2008Benches

    def successPatterns(self):
        return [
            re.compile(
                r"^(Noncompliant c|C)omposite result: (?P<score>[0-9]+((,|\.)[0-9]+)?)( SPECjvm2008 (Base|Peak))? ops/m$", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(r"^Errors in benchmark: ", re.MULTILINE)
        ]

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        suite_name = self.name()
        if benchmarks and len(benchmarks) == 1:
            suite_name = suite_name +  "-single"
        return [
          mx_benchmark.StdOutRule(
            r"^Score on (?P<benchmark>[a-zA-Z0-9\._]+): (?P<score>[0-9]+((,|\.)[0-9]+)?) ops/m$", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
              "bench-suite": suite_name,
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


class SpecJbb2005BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """SPECjbb2005 benchmark suite implementation.

    This suite has only a single benchmark, and does not allow setting a specific
    benchmark in the command line.
    """
    def name(self):
        return "specjbb2005"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def specJbbClassPath(self):
        specjbb2005 = mx.get_env("SPECJBB2005")
        if specjbb2005 is None:
            mx.abort("Please set the SPECJBB2005 environment variable to a " +
                "SPECjbb2005 directory.")
        jbbpath = join(specjbb2005, "jbb.jar")
        if not exists(jbbpath):
            mx.abort("The SPECJBB2005 environment variable points to a directory " +
                "without the jbb.jar file.")
        checkpath = join(specjbb2005, "check.jar")
        if not exists(checkpath):
            mx.abort("The SPECJBB2005 environment variable points to a directory " +
                "without the check.jar file.")
        return jbbpath + ":" + checkpath

    def validateEnvironment(self):
        if not self.specJbbClassPath():
            raise RuntimeError(
                "The SPECJBB2005 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return mx.get_env("SPECJBB2005")

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is not None:
            mx.abort("No benchmark should be specified for the selected suite.")
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        mainClass = "spec.jbb.JBBmain"
        propArgs = ["-propfile", "SPECjbb.props"]
        return (
            vmArgs + ["-cp"] + [self.specJbbClassPath()] + [mainClass] + propArgs +
            runArgs)

    def benchmarkList(self, bmSuiteArgs):
        return ["default"]

    def successPatterns(self):
        return [
            re.compile(
                r"^Valid run, Score is  [0-9]+$", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(r"VALIDATION ERROR", re.MULTILINE)
        ]

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
          mx_benchmark.StdOutRule(
            r"^Valid run, Score is  (?P<score>[0-9]+)$", # pylint: disable=line-too-long
            {
              "benchmark": "default",
              "vm": "jvmci",
              "config.name": "default",
              "metric.name": "throughput",
              "metric.value": ("<score>", float),
              "metric.unit": "bops",
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "higher",
              "metric.iteration": 0
            }
          )
        ]


mx_benchmark.add_bm_suite(SpecJbb2005BenchmarkSuite())


class SpecJbb2013BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """SPECjbb2013 benchmark suite implementation.

    This suite has only a single benchmark, and does not allow setting a specific
    benchmark in the command line.
    """
    def name(self):
        return "specjbb2013"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def specJbbClassPath(self):
        specjbb2013 = mx.get_env("SPECJBB2013")
        if specjbb2013 is None:
            mx.abort("Please set the SPECJBB2013 environment variable to a " +
                "SPECjbb2013 directory.")
        jbbpath = join(specjbb2013, "specjbb2013.jar")
        if not exists(jbbpath):
            mx.abort("The SPECJBB2013 environment variable points to a directory " +
                "without the specjbb2013.jar file.")
        return jbbpath

    def validateEnvironment(self):
        if not self.specJbbClassPath():
            raise RuntimeError(
                "The SPECJBB2013 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return mx.get_env("SPECJBB2013")

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is not None:
            mx.abort("No benchmark should be specified for the selected suite.")
        vmArgs = self.vmArgs(bmSuiteArgs)
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
        return []

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


mx_benchmark.add_bm_suite(SpecJbb2013BenchmarkSuite())


class SpecJbb2015BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
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

    def specJbbClassPath(self):
        specjbb2015 = mx.get_env("SPECJBB2015")
        if specjbb2015 is None:
            mx.abort("Please set the SPECJBB2015 environment variable to a " +
                "SPECjbb2015 directory.")
        jbbpath = join(specjbb2015, "specjbb2015.jar")
        if not exists(jbbpath):
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
        return []

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


class JMHRunnerGraalCoreBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite):

    def name(self):
        return "jmh-graal-core-whitebox"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def extraVmArgs(self):
        return ['-XX:-UseJVMCIClassLoader'] + super(JMHRunnerGraalCoreBenchmarkSuite, self).extraVmArgs()


mx_benchmark.add_bm_suite(JMHRunnerGraalCoreBenchmarkSuite())


class JMHJarGraalCoreBenchmarkSuite(mx_benchmark.JMHJarBenchmarkSuite):

    def name(self):
        return "jmh-jar"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"


mx_benchmark.add_bm_suite(JMHJarGraalCoreBenchmarkSuite())


class RenaissanceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, AveragingBenchmarkMixin):
    """Renaissance benchmark suite implementation.
    """
    def name(self):
        return "renaissance"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def renaissancePath(self):
        renaissance = mx.get_env("RENAISSANCE")
        if renaissance:
            return join(renaissance, "jars")
        return None

    def validateEnvironment(self):
        if not self.renaissancePath():
            raise RuntimeError(
                "The RENAISSANCE environment variable was not specified.")

    def before(self, bmSuiteArgs):
        self.workdir = mkdtemp(prefix='renaissance-work.', dir='.')

    def after(self, bmSuiteArgs):
        if self.workdir:
            rmtree(self.workdir)

    def validateReturnCode(self, retcode):
        return retcode == 0

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return self.workdir

    def classpathAndMainClass(self):
        mainClass = "org.renaissance.RenaissanceSuite"
        return ["-cp", self.renaissancePath() + "/*", mainClass]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            benchArg = "all"
        elif len(benchmarks) == 0:
            mx.abort("Must specify at least one benchmark.")
        else:
            benchArg = ",".join(benchmarks)
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return (
            vmArgs + self.classpathAndMainClass() + runArgs + [benchArg])

    def benchmarkList(self, bmSuiteArgs):
        self.validateEnvironment()
        out = mx.OutputCapture()
        mx.run_java(self.classpathAndMainClass() + ["listraw"], out=out)
        return str.splitlines(out.data)

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return []

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
          mx_benchmark.StdOutRule(
            r"====== (?P<benchmark>[a-zA-Z0-9_]+) \((?P<benchgroup>[a-zA-Z0-9_]+)\), iteration (?P<iteration>[0-9]+) completed \((?P<value>[0-9]+(.[0-9]*)?) ms\) ======",
            {
              "benchmark": ("<benchmark>", str),
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
          ),
          mx_benchmark.StdOutRule(
            r"====== (?P<benchmark>[a-zA-Z0-9_]+) \((?P<benchgroup>[a-zA-Z0-9_]+)\), final iteration completed \((?P<value>[0-9]+(.[0-9]*)?) ms\) ======",
            {
              "benchmark": ("<benchmark>", str),
              "vm": "jvmci",
              "config.name": "default",
              "metric.name": "final-time",
              "metric.value": ("<value>", float),
              "metric.unit": "ms",
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "lower",
              "metric.iteration": 0,
            }
          )
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(RenaissanceBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceBenchmarkSuite())
