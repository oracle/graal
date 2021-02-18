#
# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function

import os.path
import time
import subprocess
import signal
import threading

import mx
import mx_benchmark


class NativeImageBenchmarkMixin(object):

    def __init__(self):
        self.benchmark_name = None

    def benchmarkName(self):
        if not self.benchmark_name:
            raise NotImplementedError()
        return self.benchmark_name

    def run_stage(self, stage, command, out, err, cwd, nonZeroIsFatal):
        return mx.run(command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def parse_native_image_args(self, prefix, args):
        ret = []
        for arg in args:
            if arg.startswith(prefix):
                parsed = arg.split(' ')[0].split(prefix)[1]
                if parsed not in ret:
                    ret.append(parsed)
        return ret

    def extra_image_build_argument(self, _, args):
        return self.parse_native_image_args('-Dnative-image.benchmark.extra-image-build-argument=', args)

    def extra_run_arg(self, _, args):
        return self.parse_native_image_args('-Dnative-image.benchmark.extra-run-arg=', args)

    def extra_agent_run_arg(self, _, args):
        return self.parse_native_image_args('-Dnative-image.benchmark.extra-agent-run-arg=', args)

    def extra_profile_run_arg(self, _, args):
        return self.parse_native_image_args('-Dnative-image.benchmark.extra-profile-run-arg=', args)

    def extra_agent_profile_run_arg(self, _, args):
        return self.parse_native_image_args('-Dnative-image.benchmark.extra-agent-profile-run-arg=', args)

    def benchmark_output_dir(self, _, args):
        parsed_args = self.parse_native_image_args('-Dnative-image.benchmark.benchmark-output-dir=', args)
        if parsed_args:
            return parsed_args[0]
        else:
            return None

    def stages(self, args):
        parsed_args = self.parse_native_image_args('-Dnative-image.benchmark.stages=', args)

        if len(parsed_args) > 1:
            mx.abort('Native Image benchmark stages should only be specified once.')

        return parsed_args[0].split(',') if parsed_args else ['agent', 'instrument-image', 'instrument-run', 'image', 'run']

    def skip_agent_assertions(self, _, args):
        parsed_args = self.parse_native_image_args('-Dnative-image.benchmark.skip-agent-assertions=', args)
        if 'true' in parsed_args or 'True' in parsed_args:
            return True
        elif 'false' in parsed_args or 'False' in parsed_args:
            return False
        else:
            return None


class JMeterBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """Base class for JMeter based benchmark suites."""

    def __init__(self):
        super(JMeterBenchmarkSuite, self).__init__()
        self.jmeterOutput = None

    def benchSuiteName(self):
        return self.name()

    def applicationPath(self):
        """Returns the application Jar path.

        :return: Path to Jar.
        :rtype: str
        """
        raise NotImplementedError()

    def applicationPort(self):
        """Returns the application port.

        :return: Port that the application is using to receive requests.
        :rtype: int
        """
        return 8080

    def workloadPath(self, benchmark):
        """Returns the JMeter workload (.jmx file) path.

        :return: Path to workload file.
        :rtype: str
        """
        raise NotImplementedError()

    def jmeterVersion(self):
        return '5.3'

    def jmeterPath(self):
        jmeterDirectory = mx.library("APACHE_JMETER_" + self.jmeterVersion(), True).get_path(True)
        return os.path.join(jmeterDirectory, "apache-jmeter-" + self.jmeterVersion(), "bin/ApacheJMeter.jar")

    def tailDatapointsToSkip(self, results):
        return int(len(results) * .10)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.vmArgs(bmSuiteArgs) + ["-jar", self.applicationPath()]

    def rules(self, out, benchmarks, bmSuiteArgs):
        # Example of jmeter output:
        # "summary =     70 in 00:00:01 =   47.6/s Avg:    12 Min:     3 Max:   592 Err:     0 (0.00%)"
        return [
            mx_benchmark.StdOutRule(
                r"^summary \+\s+(?P<requests>[0-9]+) in (?P<hours>\d+):(?P<minutes>\d\d):(?P<seconds>\d\d) =\s+(?P<throughput>\d*[.,]?\d*)/s Avg:\s+(?P<avg>\d+) Min:\s+(?P<min>\d+) Max:\s+(?P<max>\d+) Err:\s+(?P<errors>\d+) \((?P<errpct>\d*[.,]?\d*)\%\)", # pylint: disable=line-too-long
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
            )
        ]

    @staticmethod
    def findApplication(port, timeout=10):
        try:
            import psutil
        except ImportError:
            mx.abort("Failed to import JMeterBenchmarkSuite dependency module: psutil")
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

    @staticmethod
    def terminateApplication(port):
        proc = JMeterBenchmarkSuite.findApplication(port, 0)
        if proc:
            proc.send_signal(signal.SIGTERM)
            return True
        else:
            return False

    @staticmethod
    def runJMeterInBackground(jmeterBenchmarkSuite, benchmarkName):
        if not JMeterBenchmarkSuite.findApplication(jmeterBenchmarkSuite.applicationPort()):
            mx.abort("Failed to find server application in JMeterBenchmarkSuite")
        jmeterCmd = [mx.get_jdk().java, "-jar", jmeterBenchmarkSuite.jmeterPath(), "-n", "-t", jmeterBenchmarkSuite.workloadPath(benchmarkName), "-j", "/dev/stdout"] # pylint: disable=line-too-long
        mx.log("Running JMeter: {0}".format(jmeterCmd))
        jmeterBenchmarkSuite.jmeterOutput = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(jmeterCmd, out=jmeterBenchmarkSuite.jmeterOutput, err=subprocess.PIPE)
        if not jmeterBenchmarkSuite.terminateApplication(jmeterBenchmarkSuite.applicationPort()):
            mx.abort("Failed to terminate server application in JMeterBenchmarkSuite")

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, _, dims = super(JMeterBenchmarkSuite, self).runAndReturnStdOut(benchmarks, bmSuiteArgs)
        return ret_code, self.jmeterOutput.underlying.data, dims

    def run(self, benchmarks, bmSuiteArgs):
        if len(benchmarks) > 1:
            mx.abort("A single benchmark should be specified for the selected suite.")
        if isinstance(self, NativeImageBenchmarkMixin):
            self.benchmark_name = benchmarks[0]
        else:
            threading.Thread(target=JMeterBenchmarkSuite.runJMeterInBackground, args=[self, benchmarks[0]]).start()
        results = super(JMeterBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        results = results[:len(results) - self.tailDatapointsToSkip(results)]
        self.addAverageAcrossLatestResults(results, "throughput")
        return results
