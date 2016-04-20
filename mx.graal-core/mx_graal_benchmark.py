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
from os.path import join, exists

import mx
import mx_benchmark


_dacapoIterations = {
    "avrora"    : 20,
    "batik"     : 40,
    "eclipse"   : -1,
    "fop"       : 40,
    "h2"        : 20,
    "jython"    : 40,
    "luindex"   : 15,
    "lusearch"  : 40,
    "pmd"       : 30,
    "sunflow"   : 30,
    "tomcat"    : 50,
    "tradebeans": -1,
    "tradesoap" : -1,
    "xalan"     : 20,
}


class DaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """DaCapo benchmark suite implementation.

    This suite can only run a single benchmark in one VM invocation.
    """
    def name(self):
        return "dacapo"

    def group(self):
        return "graal"

    def dacapoPath(self):
        dacapo = mx.get_env("DACAPO_CP")
        if dacapo:
            return dacapo
        lib = mx.library("DACAPO", False)
        if lib:
            return lib.get_path(True)
        return None

    def validateEnvironment(self):
        if not self.dacapoPath():
            raise RuntimeError(
                "Neither DACAPO_CP variable nor DACAPO library specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def vmAndRunArgs(self, bmSuiteArgs):
        return mx_benchmark.splitArgs(bmSuiteArgs, "--")

    def vmArgs(self, bmSuiteArgs):
        return self.vmAndRunArgs(bmSuiteArgs)[0]

    def runArgs(self, bmSuiteArgs):
        return self.vmAndRunArgs(bmSuiteArgs)[1]

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
            iterations = _dacapoIterations[benchname]
            if iterations == -1:
                return None
            else:
                return ["-n", str(iterations)] + remaining

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
            self.vmArgs(bmSuiteArgs) + ["-jar"] + [self.dacapoPath()] +
            [benchmarks[0]] + runArgs)

    def benchmarks(self):
        return _dacapoIterations.keys()

    def successPatterns(self):
        return [
            re.compile(
                r"^===== DaCapo 9\.12 ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====",
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(
                r"^===== DaCapo 9\.12 ([a-zA-Z0-9_]+) FAILED (warmup|) =====",
                re.MULTILINE)
        ]

    def flakySuccessPatterns(self):
        return [
            re.compile(
                r"^javax.ejb.FinderException: Cannot find account for",
                re.MULTILINE),
            re.compile(
                r"^java.lang.Exception: TradeDirect:Login failure for user:",
                re.MULTILINE),
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return []
        totalIterations = int(runArgs[runArgs.index("-n") + 1])
        return [
          mx_benchmark.StdOutRule(
            r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
              "metric.name": "time",
              "metric.value": ("<time>", int),
              "metric.unit": "ms",
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "lower",
              "metric.iteration": 0
            }
          ),
          mx_benchmark.StdOutRule(
            r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
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
            r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) completed warmup [0-9]+ in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
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

mx_benchmark.add_bm_suite(DaCapoBenchmarkSuite())

_allSpecJVM2008Benchs = [
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

    This benchmark can run multiple benchmarks as part of one VM run.
    """
    def name(self):
        return "specjvm2008"

    def group(self):
        return "graal"

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

    def vmAndRunArgs(self, bmSuiteArgs):
        return mx_benchmark.splitArgs(bmSuiteArgs, "--")

    def vmArgs(self, bmSuiteArgs):
        return self.vmAndRunArgs(bmSuiteArgs)[0]

    def runArgs(self, bmSuiteArgs):
        return self.vmAndRunArgs(bmSuiteArgs)[1]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            # No benchmark specified in the command line means .
            benchmarks = self.benchmarks()
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + ["-jar"] + [self.specJvmPath()] + runArgs + benchmarks

    def benchmarks(self):
        return _allSpecJVM2008Benchs

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
        return [
          mx_benchmark.StdOutRule(
            r"^Score on (?P<benchmark>[a-zA-Z0-9\._]+): (?P<score>[0-9]+((,|\.)[0-9]+)?) ops/m$", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
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
