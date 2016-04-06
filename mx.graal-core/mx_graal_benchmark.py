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

import re

import mx
import mx_benchmark


class DaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """DaCapo benchmark suite implementation.
    """
    def name(self):
        return "dacapo"

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

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            raise RuntimeError(
                "Suite runs only a single benchmark.")
        if len(benchmarks) != 1:
            raise RuntimeError(
                "Suite runs only a single benchmark, got: {0}".format(benchmarks))
        return (
            self.vmArgs(bmSuiteArgs) + ["-jar"] + [self.dacapoPath()] +
            [benchmarks[0]] + self.runArgs(bmSuiteArgs))

    def benchmarks(self):
        return [
            "avrora",
            "batik",
            "fop",
            "h2",
            "jython",
            "luindex",
            "lusearch",
            "pmd",
            "sunflow",
            "tomcat",
            "tradebeans",
            "xalan"
        ]

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

    def rules(self, out):
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
            r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) completed warmup 1 in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
              "metric.name": "warmup",
              "metric.value": ("<time>", int),
              "metric.unit": "ms",
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "lower",
              "metric.iteration": "$iteration"
            }
          )
        ]

mx_benchmark.add_bm_suite(DaCapoBenchmarkSuite())
