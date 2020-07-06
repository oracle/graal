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

import sys
import re
import argparse
import os
from os.path import join, exists
import json
from shutil import rmtree
from tempfile import mkdtemp, mkstemp

import mx
import mx_benchmark
from mx_benchmark import ParserEntry


_suite = mx.suite('java-benchmarks')


if sys.version_info[0] < 3:
    from ConfigParser import ConfigParser
    from StringIO import StringIO
    def _configparser_read_file(configp, fp):
        configp.readfp(fp)
else:
    from configparser import ConfigParser
    from io import StringIO
    def _configparser_read_file(configp, fp):
        configp.read_file(fp)


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
    'renaissance': [
        lambda args: createBenchmarkShortcut("renaissance", args),
        '[<benchmarks>|*] [-- [VM options] [-- [Renaissance options]]]'
    ],
    'awfy': [
        lambda args: createBenchmarkShortcut("awfy", args),
        '[<benchmarks>|*] [-- [VM options] ] [-- [AWFY options] ]]'
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


def _create_temporary_workdir_parser():
    parser = argparse.ArgumentParser(add_help=False, usage=mx_benchmark._mx_benchmark_usage_example + " -- <options> -- ...")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--keep-scratch", action="store_true", help="Do not delete scratch directory after benchmark execution.")
    group.add_argument("--no-scratch", action="store_true", help="Do not execute benchmark in scratch directory.")
    return parser


mx_benchmark.parsers["temporary_workdir_parser"] = ParserEntry(
    _create_temporary_workdir_parser(),
    "\n\nFlags for benchmark suites with temporary working directories:\n"
)


# Adds a java VM from JAVA_HOME without any assumption about it
mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm('java_home', 'default'), _suite, 1)


def java_home_jdk():
    return mx.get_jdk()


class TemporaryWorkdirMixin(mx_benchmark.VmBenchmarkSuite):
    def before(self, bmSuiteArgs):
        parser = mx_benchmark.parsers["temporary_workdir_parser"].parser
        bmArgs, otherArgs = parser.parse_known_args(bmSuiteArgs)
        self.keepScratchDir = bmArgs.keep_scratch
        if not bmArgs.no_scratch:
            self._create_tmp_workdir()
        else:
            mx.warn("NO scratch directory created! (--no-scratch)")
            self.workdir = None
        super(TemporaryWorkdirMixin, self).before(otherArgs)

    def _create_tmp_workdir(self):
        self.workdir = mkdtemp(prefix=self.name() + '-work.', dir='.')

    def workingDirectory(self, benchmarks, bmSuiteArgs):
        return self.workdir

    def after(self, bmSuiteArgs):
        if hasattr(self, "keepScratchDir") and self.keepScratchDir:
            mx.warn("Scratch directory NOT deleted (--keep-scratch): {0}".format(self.workdir))
        elif self.workdir:
            rmtree(self.workdir)
        super(TemporaryWorkdirMixin, self).after(bmSuiteArgs)

    def repairDatapointsAndFail(self, benchmarks, bmSuiteArgs, partialResults, message):
        try:
            super(TemporaryWorkdirMixin, self).repairDatapointsAndFail(benchmarks, bmSuiteArgs, partialResults, message)
        finally:
            if self.workdir:
                # keep old workdir for investigation, create a new one for further benchmarking
                mx.warn("Keeping scratch directory after failed benchmark: {0}".format(self.workdir))
                self._create_tmp_workdir()

    def parserNames(self):
        return super(TemporaryWorkdirMixin, self).parserNames() + ["temporary_workdir_parser"]


class BaseDaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, TemporaryWorkdirMixin):
    """Base benchmark suite for DaCapo-based benchmarks.

    This suite can only run a single benchmark in one VM invocation.
    """
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchSuiteName(self):
        return self.name()

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
        bench_list = [key for key, value in self.daCapoIterations().items() if value != -1]
        if java_home_jdk().javaCompliance >= '9' and "batik" in bench_list:
            # batik crashes on JDK9+. This is fixed in the upcoming dacapo chopin release
            bench_list.remove("batik")
        return bench_list

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
                re.MULTILINE),
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
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


class DaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
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


class DaCapoD3SBenchmarkSuite(DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 Bach benchmark suite implementation with D3S modifications."""

    def name(self):
        return "dacapo-d3s"

    def daCapoSuiteTitle(self):
        return "DaCapo 9.12-D3S-20180206"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_D3S_CP"

    def daCapoLibraryName(self):
        return "DACAPO_D3S"

    def successPatterns(self):
        return []

    def resultFilter(self, values, iteration, endOfWarmupIndex):
        """Count iterations, convert iteration time to milliseconds."""
        # Called from lambda, increment call counter
        iteration['value'] = iteration['value'] + 1
        # Skip warm-up?
        if iteration['value'] < endOfWarmupIndex:
            return None

        values['iteration_time_ms'] = str(int(values['iteration_time_ns']) / 1000 / 1000)
        return values

    def rules(self, out, benchmarks, bmSuiteArgs):
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        if runArgs is None:
            return []
        totalIterations = int(runArgs[runArgs.index("-n") + 1])
        out = [
            mx_benchmark.CSVFixedFileRule(
                self.resultCsvFile,
                None,
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": ("<iteration_time_ms>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                },
                # Note: this lambda keeps state to count the row in the CSV file,
                # and it assumes that it will be called only in one traversal of the rows.
                filter_fn=lambda x, counter={'value': 0}: self.resultFilter(x, counter, 0)
            ),
            mx_benchmark.CSVFixedFileRule(
                self.resultCsvFile,
                None,
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "final-time",
                    "metric.value": ("<iteration_time_ms>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                },
                # Note: this lambda keeps state to count the row in the CSV file,
                # and it assumes that it will be called only in one traversal of the rows.
                filter_fn=lambda x, counter={'value': 0}: self.resultFilter(x, counter, totalIterations)
            ),
        ]

        for ev in self.extraEvents:
            out.append(
                mx_benchmark.CSVFixedFileRule(
                    self.resultCsvFile,
                    None,
                    {
                        "benchmark": ("<benchmark>", str),
                        "bench-suite": self.benchSuiteName(),
                        "vm": "jvmci",
                        "config.name": "default",
                        "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                        "metric.name": ev,
                        "metric.value": ("<" + ev + ">", int),
                        "metric.unit": "count",
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.better": "lower",
                        "metric.iteration": ("$iteration", int)
                    }
                )
            )

        return out

    def getUbenchAgentPaths(self):
        archive = mx.library("UBENCH_AGENT_DIST").get_path(resolve=True)

        agentExtractPath = join(os.path.dirname(archive), 'ubench-agent')
        agentBaseDir = join(agentExtractPath, 'java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5')
        agentPathToJar = join(agentBaseDir, 'out', 'lib', 'ubench-agent.jar')
        agentPathNative = join(agentBaseDir, 'out', 'lib', 'libubench-agent.so')

        return {
            'archive': archive,
            'extract': agentExtractPath,
            'base': agentBaseDir,
            'jar': agentPathToJar,
            'agentpath': agentPathNative
        }

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-o", default=None)
        parser.add_argument("-e", default=None)
        args, remaining = parser.parse_known_args(self.runArgs(bmSuiteArgs))

        if args.o is None:
            self.resultCsvFile = "result.csv"
        else:
            self.resultCsvFile = os.path.abspath(args.o)
        remaining.append("-o")
        remaining.append(self.resultCsvFile)

        if not args.e is None:
            remaining.append("-e")
            remaining.append(args.e)
            self.extraEvents = args.e.split(",")
        else:
            self.extraEvents = []

        parentArgs = DaCapoBenchmarkSuite.createCommandLineArgs(self, benchmarks, ['--'] + remaining)
        if parentArgs is None:
            return None

        paths = self.getUbenchAgentPaths()
        return ['-agentpath:' + paths['agentpath']] + parentArgs

    def run(self, benchmarks, bmSuiteArgs):
        agentPaths = self.getUbenchAgentPaths()

        if not exists(agentPaths['jar']):
            if not exists(join(agentPaths['base'], 'build.xml')):
                import zipfile
                zf = zipfile.ZipFile(agentPaths['archive'], 'r')
                zf.extractall(agentPaths['extract'])
            mx.run(['ant', 'lib'], cwd=agentPaths['base'])

        return DaCapoBenchmarkSuite.run(self, benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(DaCapoD3SBenchmarkSuite())


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


class ScalaDaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
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
        result = _daCapoScalaConfig.copy()
        if not java_home_jdk().javaCompliance < '11':
            mx.warn('Removing scaladacapo:actors from benchmarks because corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)')
            del result['actors']
        return result

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        skip_patterns = super(ScalaDaCapoBenchmarkSuite, self).flakySuccessPatterns()
        if "specs" in benchmarks:
            skip_patterns += [
                re.escape(r"Line count validation failed for stdout.log, expecting 1039 found 1040"),
            ]
        return skip_patterns

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(ScalaDaCapoBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        # Do not add corba module on JDK>=11 (http://openjdk.java.net/jeps/320)
        if java_home_jdk().javaCompliance >= '9' and java_home_jdk().javaCompliance < '11':
            vmArgs += ["--add-modules", "java.corba"]
        return vmArgs


mx_benchmark.add_bm_suite(ScalaDaCapoBenchmarkSuite())



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
_allSpecJVM2008BenchesJDK9 = list(_allSpecJVM2008Benches)
_allSpecJVM2008BenchesJDK9.remove('compiler.compiler') # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9
_allSpecJVM2008BenchesJDK9.remove('startup.compiler.compiler')


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
            benchmarks = [b for b in self.benchmarkList(bmSuiteArgs)]

        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return vmArgs + ["-jar"] + [self.specJvmPath()] + runArgs + benchmarks

    def runArgs(self, bmSuiteArgs):
        runArgs = super(SpecJvm2008BenchmarkSuite, self).runArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '9':
            # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9
            # Skips initial check benchmark which tests for javac.jar on classpath.
            runArgs += ["-pja", "-Dspecjvm.run.initial.check=false"]
        return runArgs

    def benchmarkList(self, bmSuiteArgs):
        if java_home_jdk().javaCompliance >= '9':
            return _allSpecJVM2008BenchesJDK9
        else:
            return _allSpecJVM2008Benches

    def successPatterns(self):
        return [
            re.compile(
                r"^(Noncompliant c|C)omposite result: (?P<score>[0-9]+((,|\.)[0-9]+)?)( SPECjvm2008 (Base|Peak))? ops/m$", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            re.compile(r"^Errors in benchmark: ", re.MULTILINE),
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
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


_SpecJbb_specific_vmArgs = [
    "-XX:+UseNUMA",
    "-XX:+AlwaysPreTouch",
    "-XX:+UseLargePagesInMetaspace",
    "-XX:-UseAdaptiveSizePolicy",
    "-XX:-UseAdaptiveNUMAChunkSizing",
    "-XX:+PrintGCDetails"
]

if mx.is_linux():
    _SpecJbb_specific_vmArgs.append("-XX:+UseTransparentHugePages")


class HeapSettingsMixin(object):

    def vmArgshHeapFromEnv(self, vmArgs):
        xmx_is_set = any([arg.startswith("-Xmx") for arg in vmArgs])
        xms_is_set = any([arg.startswith("-Xms") for arg in vmArgs])
        xmn_is_set = any([arg.startswith("-Xmn") for arg in vmArgs])

        heap_args = []

        xms = mx.get_env("XMS", default="")
        xmx = mx.get_env("XMX", default="")
        xmn = mx.get_env("XMN", default="")

        if xms and not xms_is_set:
            heap_args.append("-Xms{}".format(xms))
            mx.log("Setting initial heap size based on XMS env var to -Xms{}".format(xms))

        if xmx and not xmx_is_set:
            heap_args.append("-Xmx{}".format(xmx))
            mx.log("Setting maximum heap size based on XMX env var to -Xmx{}".format(xmx))

        if xmn and not xmn_is_set:
            heap_args.append("-Xmn{}".format(xmn))
            mx.log("Setting young generation size based on XMN env var to -Xmn{}".format(xmn))

        return vmArgs + heap_args


class SpecJbb2005BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, HeapSettingsMixin):
    """SPECjbb2005 benchmark suite implementation.

    This suite has only a single benchmark, and does not allow setting a specific
    benchmark in the command line.
    """
    def __init__(self, *args, **kwargs):
        super(SpecJbb2005BenchmarkSuite, self).__init__(*args, **kwargs)
        self.prop_tmp_file = None

    def name(self):
        return "specjbb2005"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.vmArgshHeapFromEnv(super(SpecJbb2005BenchmarkSuite, self).vmArgs(bmSuiteArgs))
        return _SpecJbb_specific_vmArgs + vmArgs

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

    def extractSuiteArgs(self, bmSuiteArgs):
        """Extracts accepted suite args and removes it from bmSuiteArgs"""
        allowedSuiteArgs = [
            "input.measurement_seconds",
            "input.starting_number_warehouses",
            "input.increment_number_warehouses",
            "input.expected_peak_warehouse",
            "input.ending_number_warehouses"
        ]
        jbbprops = {}
        for suiteArg in bmSuiteArgs:
            for allowedArg in allowedSuiteArgs:
                if suiteArg.startswith("{}=".format(allowedArg)):
                    bmSuiteArgs.remove(suiteArg)
                    key, value = suiteArg.split("=", 1)
                    jbbprops[key] = value
        return jbbprops

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is not None:
            mx.abort("No benchmark should be specified for the selected suite.")

        if self.prop_tmp_file is None:
            jbbprops = self.getDefaultProperties(benchmarks, bmSuiteArgs)
            jbbprops.update(self.extractSuiteArgs(bmSuiteArgs))
            fd, self.prop_tmp_file = mkstemp(prefix="specjbb2005", suffix=".props")
            with os.fdopen(fd, "w") as f:
                f.write("\n".join(["{}={}".format(key, value) for key, value in jbbprops.items()]))

        propArgs = ["-propfile", self.prop_tmp_file]
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        mainClass = "spec.jbb.JBBmain"
        return (
                vmArgs + ["-cp"] + [self.specJbbClassPath()] + [mainClass] + propArgs +
                runArgs)

    def after(self, bmSuiteArgs):
        if self.prop_tmp_file is not None and os.path.exists(self.prop_tmp_file):
            os.unlink(self.prop_tmp_file)

    def getDefaultProperties(self, benchmarks, bmSuiteArgs):
        configfile = join(self.workingDirectory(benchmarks, bmSuiteArgs), "SPECjbb.props")
        config = StringIO()
        config.write("[root]\n")
        with open(configfile, "r") as f:
            config.write(f.read())
        config.seek(0, os.SEEK_SET)
        configp = ConfigParser()
        _configparser_read_file(configp, config)
        return dict(configp.items("root"))

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
            re.compile(r"VALIDATION ERROR", re.MULTILINE),
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
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


class SpecJbb2013BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, HeapSettingsMixin):
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

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.vmArgshHeapFromEnv(super(SpecJbb2013BenchmarkSuite, self).vmArgs(bmSuiteArgs))
        return _SpecJbb_specific_vmArgs + vmArgs

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
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

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


class SpecJbb2015BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, HeapSettingsMixin):
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

    def version(self):
        return "1.03"

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.vmArgshHeapFromEnv(super(SpecJbb2015BenchmarkSuite, self).vmArgs(bmSuiteArgs))
        return _SpecJbb_specific_vmArgs + vmArgs

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
        if java_home_jdk().javaCompliance >= '9':
            if java_home_jdk().javaCompliance < '11':
                vmArgs += ["--add-modules", "java.xml.bind"]
            else: # >= '11'
                # JEP-320: Remove the Java EE and CORBA Modules in JDK11 http://openjdk.java.net/jeps/320
                cp = []
                mx.library("JAXB_IMPL_2.1.17").walk_deps(visit=lambda d, _: cp.append(d.get_path(resolve=True)))
                vmArgs += ["--module-path", ":".join(cp), "--add-modules=jaxb.api,jaxb.impl,activation", "--add-opens=java.base/java.lang=jaxb.impl"]
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
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

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


_renaissanceConfig = {
    "akka-uct"         : 24,
    "als"              : 60,
    "chi-square"       : 60,
    "db-shootout"      : 16,
    "dec-tree"         : 40,
    "dotty"            : 50,
    "finagle-chirper"  : 90,
    "finagle-http"     : 12,
    "fj-kmeans"        : 30,
    "future-genetic"   : 50,
    "gauss-mix"        : 40,
    "log-regression"   : 20,
    "mnemonics"        : 16,
    "movie-lens"       : 20,
    "naive-bayes"      : 30,
    "neo4j-analytics"  : 20,
    "page-rank"        : 20,
    "par-mnemonics"    : 16,
    "philosophers"     : 30,
    "reactors"         : 10,
    "rx-scrabble"      : 80,
    "scala-doku"       : 20,
    "scala-kmeans"     : 50,
    "scala-stm-bench7" : 60,
    "scrabble"         : 50
}


class RenaissanceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, TemporaryWorkdirMixin):
    """Renaissance benchmark suite implementation.
    """
    def name(self):
        return "renaissance"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchSuiteName(self):
        return self.name()

    def renaissanceLibraryName(self):
        return "RENAISSANCE_{}".format(self.renaissanceVersionToRun())

    def renaissanceIterations(self):
        benchmarks = _renaissanceConfig.copy()
        if self.renaissanceVersionToRun() == "0.9.0":
            del benchmarks["scala-doku"]  # was introduced in 0.10.0
        return benchmarks

    def renaissanceVersionToRun(self):
        current_version = self.defaultRenaissanceVersion()
        version_to_run = self.desiredVersion() if self.desiredVersion() else current_version
        if version_to_run not in self.availableRenaissanceVersions():
            mx.abort("Available Renaissance versions are : {}".format(self.availableRenaissanceVersions()))
        return version_to_run

    def defaultRenaissanceVersion(self):
        return "0.11.0"

    def availableRenaissanceVersions(self):
        return ["0.9.0", "0.10.0", "0.11.0"]

    def renaissancePath(self):
        lib = mx.library(self.renaissanceLibraryName())
        if lib:
            return lib.get_path(True)
        return None

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-r", default=None)
        args, remaining = parser.parse_known_args(runArgs)
        if args.r:
            if args.r.isdigit():
                return ["-r", args.r] + remaining
            if args.r == "-1":
                return remaining
        else:
            iterations = self.renaissanceIterations()[benchname]
            if iterations == -1:
                return remaining
            else:
                return ["-r", str(iterations)] + remaining

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) == 0:
            mx.abort("Must specify at least one benchmark.")
        else:
            benchArg = ",".join(benchmarks)
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        return (self.vmArgs(bmSuiteArgs) + ["-jar", self.renaissancePath()] + runArgs + [benchArg])

    def benchmarkList(self, bmSuiteArgs):
        return sorted(_renaissanceConfig.keys())

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]",
                re.MULTILINE)
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"====== (?P<benchmark>[a-zA-Z0-9_\-]+) \((?P<benchgroup>[a-zA-Z0-9_\-]+)\)( \[(?P<config>[a-zA-Z0-9_\-]+)\])?, iteration (?P<iteration>[0-9]+) completed \((?P<value>[0-9]+(.[0-9]*)?) ms\) ======",  # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "benchmark-configuration": ("<config>", str),
                    "bench-suite": self.benchSuiteName(),
                    "bench-suite-version": self.renaissanceVersionToRun(),
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
            )
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(RenaissanceBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceBenchmarkSuite())


class RenaissanceLegacyBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, TemporaryWorkdirMixin):
    """Legacy renaissance benchmark suite implementation.
    """
    def name(self):
        return "renaissance-legacy"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def renaissancePath(self):
        renaissance = mx.get_env("RENAISSANCE_LEGACY")
        if renaissance:
            return join(renaissance, "jars")
        return None

    def validateEnvironment(self):
        if not self.renaissancePath():
            raise RuntimeError(
                "The RENAISSANCE_LEGACY environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

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
        args = ["listraw", "--list-raw-hidden"] if "--list-hidden" in bmSuiteArgs else ["listraw"]
        mx.run_java(self.classpathAndMainClass() + args, out=out)
        return str.splitlines(out.data)

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

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
        results = super(RenaissanceLegacyBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceLegacyBenchmarkSuite())


class SparkSqlPerfBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, TemporaryWorkdirMixin):
    """Benchmark suite for the spark-sql-perf benchmarks.
    """
    def name(self):
        return "spark-sql-perf"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def sparkSqlPerfPath(self):
        sparkSqlPerf = mx.get_env("SPARK_SQL_PERF")
        return sparkSqlPerf

    def validateEnvironment(self):
        if not self.sparkSqlPerfPath():
            raise RuntimeError(
                "The SPARK_SQL_PERF environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def classpathAndMainClass(self):
        mainClass = "com.databricks.spark.sql.perf.RunBenchmark"
        return ["-cp", self.sparkSqlPerfPath() + "/*", mainClass]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if not benchmarks is None:
            mx.abort("Cannot specify individual benchmarks.")
        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        return (
                vmArgs + self.classpathAndMainClass() + ["--benchmark", "DatasetPerformance"] + runArgs)

    def benchmarkList(self, bmSuiteArgs):
        self.validateEnvironment()
        return []

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return []

    def decodeStackedJson(self, content):
        notWhitespace = re.compile(r'[^\s]')
        pos = 0
        while True:
            match = notWhitespace.search(content, pos)
            if not match:
                return
            pos = match.start()
            decoder = json.JSONDecoder()
            part, pos = decoder.raw_decode(content, pos)
            yield part

    def getExtraIterationCount(self, iterations):
        # We average over the last 2 out of 3 total iterations done by this suite.
        return 2

    def run(self, benchmarks, bmSuiteArgs):
        runretval = self.runAndReturnStdOut(benchmarks, bmSuiteArgs)
        retcode, out, dims = runretval
        self.validateStdoutWithDimensions(
            out, benchmarks, bmSuiteArgs, retcode=retcode, dims=dims)
        perf_dir = next(file for file in os.listdir(self.workdir + "/performance/"))
        experiment_dir = self.workdir + "/performance/" + perf_dir + "/"
        results_filename = next(file for file in os.listdir(experiment_dir) if file.endswith("json"))
        with open(experiment_dir + results_filename, "r") as results_file:
            content = results_file.read()
        results = []
        iteration = 0
        for part in self.decodeStackedJson(content):
            for result in part["results"]:
                if "queryExecution" in result:
                    datapoint = {
                        "benchmark": result["name"].replace(" ", "-"),
                        "vm": "jvmci",
                        "config.name": "default",
                        "metric.name": "warmup",
                        "metric.value": result["executionTime"],
                        "metric.unit": "ms",
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.better": "lower",
                        "metric.iteration": iteration,
                    }
                    datapoint.update(dims)
                    results.append(datapoint)
            iteration += 1
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(SparkSqlPerfBenchmarkSuite())


# Benchmark-specific parameter from AWFY rebench.conf
_awfyConfig = {
    "DeltaBlue"  : 12000,
    "Richards"   : 100,
    "Json"       : 100,
    "CD"         : 250,
    "Havlak"     : 1500,
    "Bounce"     : 1500,
    "List"       : 1500,
    "Mandelbrot" : 500,
    "NBody"      : 250000,
    "Permute"    : 1000,
    "Queens"     : 1000,
    "Sieve"      : 3000,
    "Storage"    : 1000,
    "Towers"     : 600
}

class AWFYBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """Are we fast yet? benchmark suite implementation.
    """
    def name(self):
        return "awfy"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchSuiteName(self):
        return self.name()

    def awfyLibraryName(self):
        return "AWFY"

    def awfyBenchmarkParam(self):
        return _awfyConfig.copy()

    def awfyIterations(self):
        return 100 # Iterations for a "Slow VM" from AWFY rebench.conf

    def awfyPath(self):
        lib = mx.library(self.awfyLibraryName())
        if lib:
            return lib.get_path(True)
        return None

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument('-i', '--iterations', default=self.awfyIterations())
        parser.add_argument('-p', '--param', default=self.awfyBenchmarkParam()[benchname])
        args, remaining = parser.parse_known_args(runArgs)
        return [str(args.iterations), str(args.param)] + remaining

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark to run.")
        else:
            benchArg = benchmarks[0]
        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        return (self.vmArgs(bmSuiteArgs) + ["-cp", self.awfyPath()] + ["Harness"] + [benchArg] + runArgs)

    def benchmarkList(self, bmSuiteArgs):
        return sorted(_awfyConfig.keys())

    def successPatterns(self):
        return [r"(?P<benchmark>[a-zA-Z0-9_\-]+): iterations=(?P<iterations>[0-9]+) average: (?P<average>[0-9]+)us total: (?P<total>[0-9]+)us"]

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"(?P<benchmark>[a-zA-Z0-9_\-]+): iterations=(?P<iterations>[0-9]+) runtime: (?P<runtime>[0-9]+)us",
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "config.vm-flags": ' '.join(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "warmup",
                    "metric.value": ("<runtime>", float),
                    "metric.unit": "us",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                }
            )
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(AWFYBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(AWFYBenchmarkSuite())
