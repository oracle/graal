#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import mx
import mx_benchmark

import os
import re
import shutil
import stat
import tempfile
import zipfile
import argparse

from mx_benchmark import JMHDistBenchmarkSuite
from mx_benchmark import add_bm_suite
from mx_benchmark import add_java_vm


_suite = mx.suite("wasm")


BENCHMARK_NAME_PREFIX = "-Dwasmbench.benchmarkName="
SUITE_NAME_SUFFIX = "BenchmarkSuite"
BENCHMARK_JAR_SUFFIX = "benchmarkcases.jar"


node_dir = mx.get_env("NODE_DIR", None)


def _toKebabCase(name, skewer="-"):
    s1 = re.sub("(.)([A-Z][a-z]+)", r"\1" + skewer + r"\2", name)
    return re.sub("([a-z0-9])([A-Z])", r"\1" + skewer + r"\2", s1).lower()


class WasmBenchmarkVm(mx_benchmark.OutputCapturingVm):
    """
    This is a special kind of Wasm VM that expects the benchmark suite to provide
    a JAR file that has each benchmark compiled to a native binary,
    a JS program that runs the Wasm benchmark (generated e.g. with Emscripten),
    and the set of files that are required by the GraalWasm test suite.
    These files must be organized in a predefined structure,
    so that the different VM implementations know where to look for them.

    If a Wasm benchmark suite consists of benchmarks in the category `c`,
    then the binaries of that benchmark must structured as follows:

    - For GraalWasm: bench/x/{*.wasm, *.result, *.wat}
    - For Node: bench/x/{*.wasm, *.js}
    - For native binaries: bench/x/*<platform-specific-binary-extension>

    Furthermore, these VMs expect that the benchmark suites that use them
    will provide a `-Dwasmbench.benchmarkName=<benchmark-name>` command-line flag,
    and the `CBenchmarkSuite` argument, where `<benchmark-name>` specifies a benchmark
    in the category `c`.
    """
    def name(self):
        return "wasm-benchmark"

    def post_process_command_line_args(self, args):
        return args

    def parse_suite_benchmark(self, args):
        suite = next(iter([arg for arg in args if arg.endswith(SUITE_NAME_SUFFIX)]), None)
        if suite is None:
            mx.abort("Suite must specify a flag that ends with " + SUITE_NAME_SUFFIX)
        suite = suite[:-len(SUITE_NAME_SUFFIX)]
        suite = _toKebabCase(suite, "/")

        benchmark = next(iter([arg for arg in args if arg.startswith(BENCHMARK_NAME_PREFIX)]), None)
        if benchmark is None:
            mx.abort("Suite must specify a flag that starts with " + BENCHMARK_NAME_PREFIX)
        else:
            benchmark = benchmark[len(BENCHMARK_NAME_PREFIX):]

        return suite, benchmark

    def parse_jar_suite_benchmark(self, args):
        if "-cp" not in args:
            mx.abort("Suite must specify -cp.")
        classpath = args[args.index("-cp") + 1]
        delimiter = ";" if mx.is_windows() else ":"
        jars = classpath.split(delimiter)
        jar = next(iter([jar for jar in jars if jar.endswith(BENCHMARK_JAR_SUFFIX)]), None)
        if jar is None:
            mx.abort("No benchmark jar file is specified in the classpath.")

        suite, benchmark = self.parse_suite_benchmark(args)
        return jar, suite, benchmark

    def extract_jar_to_tempdir(self, jar, suite, benchmark):
        tmp_dir = tempfile.mkdtemp()
        with zipfile.ZipFile(jar, "r") as z:
            for name in z.namelist():
                if name.startswith(os.path.join("bench", suite, benchmark)):
                    z.extract(name, tmp_dir)
        return tmp_dir

    def rules(self, output, benchmarks, bmSuiteArgs):
        suite, benchmark = self.parse_suite_benchmark(bmSuiteArgs)
        return [
            mx_benchmark.StdOutRule(
                r"ops/sec = (?P<throughput>[0-9]+.[0-9]+)",
                {
                    "benchmark": suite + "/" + benchmark,
                    "vm": self.config_name(),
                    "metric.name": "throughput",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "ops/s",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "higher",
                    "metric.iteration": 0,
                }
            )
        ]


class NodeWasmBenchmarkVm(WasmBenchmarkVm):
    def config_name(self):
        return "node"

    def run_vm(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        if node_dir is None:
            mx.abort("Must set the NODE_DIR environment variable to point to Node's bin dir.")
        jar, suite, benchmark = self.parse_jar_suite_benchmark(args)
        tmp_dir = None
        try:
            tmp_dir = self.extract_jar_to_tempdir(jar, suite, benchmark)
            node_cmd = os.path.join(node_dir, "node")
            node_cmd_line = [node_cmd, "--experimental-wasm-bigint", os.path.join(tmp_dir, "bench", suite, benchmark + ".js")]
            mx.log("Running benchmark " + benchmark + " with node.")
            mx.run(node_cmd_line, cwd=tmp_dir, out=out, err=err, nonZeroIsFatal=nonZeroIsFatal)
        finally:
            if tmp_dir:
                shutil.rmtree(tmp_dir)
        return 0


class NativeWasmBenchmarkVm(WasmBenchmarkVm):
    def config_name(self):
        return "native"

    def run_vm(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        jar, suite, benchmark = self.parse_jar_suite_benchmark(args)
        tmp_dir = None
        try:
            tmp_dir = self.extract_jar_to_tempdir(jar, suite, benchmark)
            binary_path = os.path.join(tmp_dir, "bench", suite, mx.exe_suffix(benchmark))
            os.chmod(binary_path, stat.S_IRUSR | stat.S_IXUSR)
            cmd_line = [binary_path]
            mx.log("Running benchmark " + benchmark + " natively.")
            mx.run(cmd_line, cwd=tmp_dir, out=out, err=err, nonZeroIsFatal=nonZeroIsFatal)
        finally:
            if tmp_dir:
                shutil.rmtree(tmp_dir)
        return 0


add_java_vm(NodeWasmBenchmarkVm(), suite=_suite, priority=1)
add_java_vm(NativeWasmBenchmarkVm(), suite=_suite, priority=1)


class WasmJMHJsonRule(mx_benchmark.JMHJsonRule):
    def getBenchmarkNameFromResult(self, result):
        name_flag = "-Dwasmbench.benchmarkName="
        name_arg = next(arg for arg in result["jvmArgs"] if arg.startswith(name_flag))
        return name_arg[len(name_flag):]


class WasmBenchmarkSuite(JMHDistBenchmarkSuite):
    def name(self):
        return "wasm"

    def group(self):
        return "Graal"

    def benchSuiteName(self, bmSuiteArgs):
        return next(arg for arg in bmSuiteArgs if arg.endswith("BenchmarkSuite"))

    def subgroup(self):
        return "wasm"

    def successPatterns(self):
        return []

    def isWasmBenchmarkVm(self, bmSuiteArgs):
        parser = argparse.ArgumentParser()
        parser.add_argument("--jvm-config")
        jvm_config = parser.parse_known_args(bmSuiteArgs)[0].jvm_config
        return jvm_config in ("node", "native")

    def rules(self, out, benchmarks, bmSuiteArgs):
        if self.isWasmBenchmarkVm(bmSuiteArgs):
            return []
        return [WasmJMHJsonRule(mx_benchmark.JMHBenchmarkSuiteBase.jmh_result_file, self.benchSuiteName(bmSuiteArgs))]


add_bm_suite(WasmBenchmarkSuite())


_suite = mx.suite("wasm")


MEMORY_PROFILER_CLASS_NAME = "org.graalvm.wasm.benchmark.MemoryFootprintBenchmarkRunner"
MEMORY_WARMUP_ITERATIONS = 10
BENCHMARKCASES_DISTRIBUTION = "WASM_BENCHMARKCASES"


class MemoryBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """
    Example suite used for testing and as a subclassing template.
    """

    def group(self):
        return "Graal"

    def subgroup(self):
        return "wasm"

    def name(self):
        return "wasm-memory"

    def benchmarkList(self, _):
        jdk = mx.get_jdk(mx.distribution(BENCHMARKCASES_DISTRIBUTION).javaCompliance)
        jvm_args = mx.get_runtime_jvm_args([BENCHMARKCASES_DISTRIBUTION], jdk=jdk)
        args = jvm_args + [MEMORY_PROFILER_CLASS_NAME, "--list"]

        out = mx.OutputCapture()
        jdk.run_java(args, out=out)
        return out.data.split()

    def createCommandLineArgs(self, benchmarks, bm_suite_args):
        benchmarks = benchmarks if benchmarks is not None else self.benchmarkList(bm_suite_args)
        jdk = mx.get_jdk(mx.distribution(BENCHMARKCASES_DISTRIBUTION).javaCompliance)
        vm_args = self.vmArgs(bm_suite_args) + mx.get_runtime_jvm_args([BENCHMARKCASES_DISTRIBUTION], jdk=jdk)
        run_args = ["--warmup-iterations", str(MEMORY_WARMUP_ITERATIONS),
                    "--result-iterations", str(self.getExtraIterationCount(MEMORY_WARMUP_ITERATIONS))]
        return vm_args + [MEMORY_PROFILER_CLASS_NAME] + run_args + benchmarks

    def rules(self, out, benchmarks, bm_suite_args):
        return [
            # We collect all our measures as "warmup"s. `AveragingBenchmarkMixin.addAverageAcrossLatestResults` then
            # takes care of creating one final "memory" point which is the average of the last N points, where N is
            # obtained from `AveragingBenchmarkMixin.getExtraIterationCount`.
            mx_benchmark.StdOutRule(r"(?P<path>.*): (warmup )?iteration\[(?P<iteration>.*)\]: (?P<value>.*) MB", {
                "benchmark": ("<path>", str),
                "metric.better": "lower",
                "metric.name": "warmup",
                "metric.unit": "MB",
                "metric.value": ("<value>", float),
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.iteration": ("<iteration>", int)
            })
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(MemoryBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results, "memory")
        return results


add_bm_suite(MemoryBenchmarkSuite())
