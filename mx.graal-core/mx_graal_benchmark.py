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
import mx_graal_core
import os

# Short-hand commands used to quickly run common benchmarks.
mx.update_commands(mx.suite('graal-core'), {
    'dacapo': [
      lambda args: mx_benchmark.benchmark(["dacapo:" + args[0]] + args[1:]),
      '<benchmarks>|* [-- [VM options] [-- [DaCapo options]]]'
    ],
    'scaladacapo': [
      lambda args: mx_benchmark.benchmark(["scaladacapo:" + args[1]] + args[1:]),
      '<benchmarks>|* [-- [VM options] [-- [Scala DaCapo options]]]'
    ],
    'specjvm2008': [
      lambda args: mx_benchmark.benchmark(["specjvm2008:" + args[1]] + args[1:]),
      '<benchmarks>|* [-- [VM options] [-- [SPECjvm2008 options]]]'
    ],
    'specjbb2005': [
      lambda args: mx_benchmark.benchmark(["specjbb2005"] + args[1:]),
      '[-- [VM options] [-- [SPECjbb2005 options]]]'
    ],
    'specjbb2013': [
      lambda args: mx_benchmark.benchmark(["specjbb2013"] + args[1:]),
      '[-- [VM options] [-- [SPECjbb2013 options]]]'
    ],
    'specjbb2015': [
      lambda args: mx_benchmark.benchmark(["specjbb2015"] + args[1:]),
      '[-- [VM options] [-- [SPECjbb2015 options]]]'
    ],
})

class JvmciJdkVm(mx_benchmark.OutputCapturingJavaVm):
    def __init__(self, raw_name, raw_config_name, extra_args):
        self.raw_name = raw_name
        self.raw_config_name = raw_config_name
        self.extra_args = extra_args

    def name(self):
        return self.raw_name

    def config_name(self):
        return self.raw_config_name

    def dimensions(self, cwd, args, code, out):
        return {
            "host-vm": self.name(),
            "host-vm-config": self.config_name(),
            "guest-vm": "none",
            "guest-vm-config": "none"
        }

    def post_process_command_line_args(self, args):
        return self.extra_args + args

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        if mx.get_jdk_option().tag != mx_graal_core._JVMCI_JDK_TAG:
            mx.abort("To use '{0}' VM, specify '--jdk={1}'".format(
                self.name(), mx_graal_core._JVMCI_JDK_TAG))
        mx.get_jdk().run_java(
            args, out=out, err=out, cwd=cwd, nonZeroIsFatal=False)


mx_benchmark.add_java_vm(JvmciJdkVm('server', 'default', ['-server', '-XX:-EnableJVMCI']))
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'default', ['-client', '-XX:-EnableJVMCI']))
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'hosted', ['-server', '-XX:+EnableJVMCI']))
mx_benchmark.add_java_vm(JvmciJdkVm('client', 'hosted', ['-client', '-XX:+EnableJVMCI']))
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'graal-core', ['-server', '-XX:+EnableJVMCI', '-XX:+UseJVMCICompiler', '-Djvmci.Compiler=graal']))
mx_benchmark.add_java_vm(JvmciJdkVm('server', 'graal-core-tracera', ['-server', '-XX:+EnableJVMCI', '-XX:+UseJVMCICompiler', '-Djvmci.Compiler=graal',
                                                             '-Dgraal.TraceRA=true']))


class TimingBenchmarkMixin(object):
    debug_values_file = 'debug-values.csv'
    name_re = re.compile(r"(?P<name>BackEnd|FrontEnd|LIRPhaseTime_\w+)_Accm")

    def vmArgs(self, bmSuiteArgs):
        vmArgs = ['-Dgraal.Time=BackEnd,FrontEnd', '-Dgraal.DebugValueHumanReadable=false', '-Dgraal.DebugValueSummary=Complete',
                  '-Dgraal.DebugValueFile=' + TimingBenchmarkMixin.debug_values_file] + super(TimingBenchmarkMixin, self).vmArgs(bmSuiteArgs)
        return vmArgs

    def getBechmarkName(self):
        raise NotImplementedError()

    def benchSuiteName(self):
        raise NotImplementedError()

    def name(self):
        return self.benchSuiteName() + "-timing"

    def filterResult(self, r):
        m = TimingBenchmarkMixin.name_re.match(r['name'])
        if m:
            r['name'] = m.groupdict()['name']
            return r
        return None

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [
          mx_benchmark.CSVFixedFileRule(
            filename=TimingBenchmarkMixin.debug_values_file,
            colnames=['scope', 'name', 'value', 'unit'],
            replacement={
              "benchmark": self.getBechmarkName(),
              "bench-suite": self.benchSuiteName(),
              "vm": "jvmci",
              "config.name": "default",
              "extra.value.path": ("<scope>", str),
              "extra.value.name": ("<name>", str),
              "metric.name": ("compile-time", str),
              "metric.value": ("<value>", int),
              "metric.unit": ("<unit>", str),
              "metric.type": "numeric",
              "metric.score-function": "id",
              "metric.better": "higher",
              "metric.iteration": 0
            },
            filter_fn=self.filterResult,
            delimiter=';', quotechar='"', escapechar='\\'
          ),
        ]


class DaCapoTimingBenchmarkMixin(TimingBenchmarkMixin):
    def postprocessRunArgs(self, benchname, runArgs):
        self.currentBenchname = benchname
        return super(DaCapoTimingBenchmarkMixin, self).postprocessRunArgs(benchname, runArgs)

    def getBechmarkName(self):
        return self.currentBenchname


class BaseDaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """Base benchmark suite for DaCapo-based benchmarks.

    This suite can only run a single benchmark in one VM invocation.
    """
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

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

    def benchmarks(self):
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
              "vm": "jvmci",
              "config.name": "default",
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
            r"===== " + re.escape(self.daCapoSuiteTitle()) + " (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
            {
              "benchmark": ("<benchmark>", str),
              "vm": "jvmci",
              "config.name": "default",
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
              "vm": "jvmci",
              "config.name": "default",
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


_daCapoIterations = {
    "avrora"     : 20,
    "batik"      : 40,
    "eclipse"    : -1,
    "fop"        : 40,
    "h2"         : 20,
    "jython"     : 40,
    "luindex"    : 15,
    "lusearch"   : 40,
    "pmd"        : 30,
    "sunflow"    : 30,
    "tomcat"     : 50,
    "tradebeans" : -1,
    "tradesoap"  : -1,
    "xalan"      : 20,
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


_daCapoScalaConfig = {
    "actors"      : 10,
    "apparat"     : 5,
    "factorie"    : 5,
    "kiama"       : 40,
    "scalac"      : 20,
    "scaladoc"    : 15,
    "scalap"      : 120,
    "scalariform" : 30,
    "scalatest"   : 50,
    "scalaxb"     : 35,
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


mx_benchmark.add_bm_suite(ScalaDaCapoBenchmarkSuite())


class ScalaDaCapoTimingBenchmarkSuite(DaCapoTimingBenchmarkMixin, ScalaDaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def benchSuiteName(self):
        return "scala-dacapo"



mx_benchmark.add_bm_suite(ScalaDaCapoTimingBenchmarkSuite())


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

    def benchmarks(self):
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

    def benchmarks(self):
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

    def benchmarks(self):
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


class JMHJsonRule(object):
    extra_jmh_keys = [
        "mode",
        "threads",
        "forks",
        "warmupIterations",
        "warmupTime",
        "warmupBatchSize",
        "measurementIterations",
        "measurementTime",
        "measurementBatchSize",
        ]

    def __init__(self, filename, suiteName):
        self.filename = filename
        self.suiteName = suiteName

    def shortenPackageName(self, benchmark):
        s = benchmark.split(".")
        # class and method
        clazz = s[-2:]
        package = [str(x[0]) for x in s[:-2]]
        return ".".join(package + clazz)

    def benchSuiteName(self):
        return self.suiteName

    def parse(self, text):
        import json
        r = []
        with open(self.filename) as fp:
            for result in json.load(fp):

                benchmark = result["benchmark"]
                mode = result["mode"]

                pm = result["primaryMetric"]
                unit = pm["scoreUnit"]
                unit_parts = unit.split("/")

                if mode == "thrpt":
                    # Throughput, ops/time
                    metricName = "throughput"
                    better = "higher"
                    if len(unit_parts) == 2:
                        metricUnit = "op/" + unit_parts[1]
                    else:
                        metricUnit = unit
                elif mode in ["avgt", "sample", "ss"]:
                    # Average time, Sampling time, Single shot invocation time
                    metricName = "time"
                    better = "lower"
                    if len(unit_parts) == 2:
                        metricUnit = unit_parts[0]
                    else:
                        metricUnit = unit
                else:
                    raise RuntimeError("Unknown benchmark mode {0}".format(mode))


                d = {
                    "bench-suite" : self.benchSuiteName(),
                    "benchmark" : self.shortenPackageName(benchmark),
                    # full name
                    "extra.jmh.benchmark" : benchmark,
                    "metric.name": metricName,
                    "metric.unit": metricUnit,
                    "metric.score-function": "id",
                    "metric.better": better,
                    "metric.type": "numeric",
                }

                if "params" in result:
                    # add all parameter as a single string
                    d["extra.jmh.params"] = ", ".join(["=".join(kv) for kv in result["params"].iteritems()])
                    # and also the individual values
                    for k, v in result["params"].iteritems():
                        d["extra.jmh.param." + k] = str(v)

                for k in JMHJsonRule.extra_jmh_keys:
                    if k in result:
                        d["extra.jmh." + k] = str(result[k])

                for jmhFork, rawData in enumerate(pm["rawData"]):
                    for iteration, data in enumerate(rawData):
                        d2 = d.copy()
                        d2.update({
                          "metric.value": float(data),
                          "metric.iteration": int(iteration),
                          "extra.jmh.fork": str(jmhFork),
                        })
                        r.append(d2)
        return r


class JMHBenchmarkSuiteBase(mx_benchmark.JavaBenchmarkSuite):
    jmh_result_file = "jmh_result.json"

    def extraRunArgs(self):
        return ["-rff", JMHBenchmarkSuiteBase.jmh_result_file, "-rf", "json"]

    def extraVmArgs(self):
        return []

    def getJMHEntry(self):
        raise NotImplementedError()

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is not None:
            mx.abort("No benchmark should be specified for the selected suite. (Use JMH specific filtering instead.)")
        vmArgs = self.vmArgs(bmSuiteArgs) + self.extraVmArgs()
        runArgs = self.runArgs(bmSuiteArgs) + self.extraRunArgs()
        return vmArgs + self.getJMHEntry() + ['--jvmArgsPrepend', ' '.join(vmArgs)] + runArgs

    def benchmarks(self):
        return ["default"]

    def successPatterns(self):
        return [
            re.compile(
                r"# Run complete.",
                re.MULTILINE)
        ]

    def benchSuiteName(self):
        return self.name()

    def failurePatterns(self):
        return [re.compile(r"<failure>")]

    def flakySuccessPatterns(self):
        return []

    def rules(self, out, benchmarks, bmSuiteArgs):
        return [JMHJsonRule(JMHBenchmarkSuiteBase.jmh_result_file, self.benchSuiteName())]


class JMHBenchmarkSuite(JMHBenchmarkSuiteBase):
    def name(self):
        return "jmh-graal-core-whitebox"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def extraVmArgs(self):
        # find all projects with a direct JMH dependency
        jmhProjects = []
        for p in mx.projects_opt_limit_to_suites():
            if 'JMH' in [x.name for x in p.deps]:
                jmhProjects.append(p)
        cp = mx.classpath([p.name for p in jmhProjects], jdk=mx.get_jdk())

        return ['-XX:-UseJVMCIClassLoader', '-cp', cp]

    def getJMHEntry(self):
        return ["org.openjdk.jmh.Main"]


mx_benchmark.add_bm_suite(JMHBenchmarkSuite())


class JMHBenchmarkJARSuite(JMHBenchmarkSuiteBase):

    def name(self):
        return "jmh-jar"

    def benchSuiteName(self):
        return "jmh-" + self.jmhName()

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(JMHBenchmarkJARSuite, self).vmArgs(bmSuiteArgs)
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("--jmh-jar", default=None)
        parser.add_argument("--jmh-name", default=None)
        args, remaining = parser.parse_known_args(vmArgs)
        self.jmh_jar = args.jmh_jar
        self.jmh_name = args.jmh_name
        return remaining

    def getJMHEntry(self):
        return ["-jar", self.jmhJAR()]

    def jmhName(self):
        if self.jmh_name is None:
            mx.abort("Please use the --jmh-name benchmark suite argument to set the name of the JMH suite.")
        return self.jmh_name

    def jmhJAR(self):
        if self.jmh_jar is None:
            mx.abort("Please use the --jmh-jar benchmark suite argument to set the JMH jar file.")
        jmh_jar = os.path.expanduser(self.jmh_jar)
        if not exists(jmh_jar):
            mx.abort("The --jmh-jar argument points to a non-existing file: " + jmh_jar)
        return jmh_jar


mx_benchmark.add_bm_suite(JMHBenchmarkJARSuite())
