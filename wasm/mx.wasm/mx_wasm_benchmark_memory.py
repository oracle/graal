#
# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

from mx_benchmark import add_bm_suite


_suite = mx.suite("wasm")


MEMORY_PROFILER_CLASS_NAME = "org.graalvm.wasm.benchmark.MemoryProfiler"


class MemoryBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """
    Example suite used for testing and as a subclassing template.
    """

    def group(self):
        return "Graal"

    def subgroup(self):
        return "wasm"

    def name(self):
        return "memory"

    def benchmarkList(self, bmSuiteArgs):
        return ["WASM_BENCHMARKCASES"]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        dist = benchmarks[0]
        jdk = mx.get_jdk(mx.distribution(dist).javaCompliance)
        vmExtraArgs = mx.get_runtime_jvm_args([dist], jdk=jdk)
        vmArgs = self.vmArgs(bmSuiteArgs) + vmExtraArgs
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + [MEMORY_PROFILER_CLASS_NAME] + runArgs

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.runArgs(bmSuiteArgs)
        benchmark = runArgs[0] + "/" + runArgs[1]
        return [
            mx_benchmark.StdOutRule(r"Median:\s*(?P<median>\d+.\d+) MB", {
                "benchmark": benchmark,
                "metric.better": "lower",
                "metric.name": "heap_size_diff.median",
                "metric.unit": "#",
                "metric.value": ("<median>", float),
            }),
        ]


add_bm_suite(MemoryBenchmarkSuite())
