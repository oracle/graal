#
# Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import collections.abc
import os.path
import shutil
import time
import signal
import threading
import json
import argparse
from enum import Enum
from typing import List, Optional, Set, Collection, Union

import mx
import mx_benchmark
import datetime
import re
import copy
import urllib.request

import mx_sdk_vm_impl
from mx_benchmark import DataPoints, DataPoint, BenchmarkSuite


_suite = mx.suite('sdk')


# Shorthand commands used to quickly run common benchmarks.
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
    'specjbb2015': [
        lambda args: mx_benchmark.benchmark(["specjbb2015"] + args),
        '[-- [VM options] [-- [SPECjbb2015 options]]]'
    ],
    'renaissance': [
        lambda args: createBenchmarkShortcut("renaissance", args),
        '[<benchmarks>|*] [-- [VM options] [-- [Renaissance options]]]'
    ],
    'shopcart': [
        lambda args: createBenchmarkShortcut("shopcart", args),
        '[-- [VM options] [-- [ShopCart options]]]'
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


# Adds a java VM from JAVA_HOME without any assumption about it
mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm('java-home', 'default'), _suite, 1)


def java_home_jdk():
    return mx.get_jdk()


JVMCI_JDK_TAG = 'jvmci'


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

    def get_jdk(self):
        tag = mx.get_jdk_option().tag
        if tag and tag != JVMCI_JDK_TAG:
            mx.abort("The '{0}/{1}' VM requires '--jdk={2}'".format(
                self.name(), self.config_name(), JVMCI_JDK_TAG))
        return mx.get_jdk(tag=JVMCI_JDK_TAG)

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        return self.get_jdk().run_java(
            args, out=out, err=out, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal, command_mapper_hooks=self.command_mapper_hooks)

    def generate_java_command(self, args):
        return self.get_jdk().generate_java_command(self.post_process_command_line_args(args))

    def rules(self, output, benchmarks, bmSuiteArgs):
        rules = super(JvmciJdkVm, self).rules(output, benchmarks, bmSuiteArgs)
        return rules


def add_or_replace_arg(option_key, value, vm_option_list):
    """
    Determines if an option with the same key as option_key is already present in vm_option_list.
    If so, it replaces the option value with the given one. If not, it appends the option
    to the end of the list. It then returns the modified list.

    For example, if arg_list contains the argument '-Djdk.graal.CompilerConfig=community', and this function
    is called with an option_key of '-Djdk.graal.CompilerConfig' and a value of 'economy', the resulting
    argument list will contain one instance of '-Djdk.graal.CompilerConfig=economy'.
    """
    arg_string = option_key + '=' + value
    idx = next((idx for idx, arg in enumerate(vm_option_list) if arg.startswith(option_key)), -1)
    if idx == -1:
        vm_option_list.append(arg_string)
    else:
        vm_option_list[idx] = arg_string
    return vm_option_list


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
            compiler_config = None
            if len(variant) == 2:
                var_name, var_args = variant
                var_priority = priority
            elif len(variant) == 3:
                var_name, var_args, var_priority = variant
            elif len(variant) == 4:
                var_name, var_args, var_priority, compiler_config = variant
            else:
                raise TypeError("unexpected tuple size for jvmci variant {} (size must be <= 4)".format(variant))

            variant_args = extended_extra_args + var_args
            if compiler_config is not None:
                variant_args = add_or_replace_arg('-Djdk.graal.CompilerConfiguration', compiler_config, variant_args)

            mx_benchmark.add_java_vm(
                JvmciJdkVm(raw_name, extended_raw_config_name + '-' + var_name, variant_args), suite, var_priority)


class BaseDaCapoBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
    """Base benchmark suite for DaCapo-based benchmarks.

    This suite can only run a single benchmark in one VM invocation.
    """
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def name(self):
        raise NotImplementedError()

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

    def daCapoSizes(self):
        raise NotImplementedError()

    def completeBenchmarkList(self, bmSuiteArgs):
        return sorted([bench for bench in self.daCapoIterations().keys() if self.workloadSize() in self.daCapoSizes().get(bench, [])])

    def existingSizes(self):
        return list(dict.fromkeys([s for bench, sizes in self.daCapoSizes().items() for s in sizes]))

    def workloadSize(self):
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
        parser.add_argument("-n", "--iterations", default=None)
        parser.add_argument("-sf", default=1, type=float, help="The total number of iterations is equivalent to the value selected by the '-n' flag scaled by this factor.")
        parser.add_argument("-s", "--size", default=None)
        args, remaining = parser.parse_known_args(runArgs)

        if args.size:
            if args.size not in self.existingSizes():
                mx.abort("Unknown workload size '{}'. "
                         "Existing benchmark sizes are: {}".format(args.size, ','.join(self.existingSizes())))

            if args.size != self.workloadSize():
                mx.abort("Mismatch between suite-defined workload size ('{}') "
                         "and user-provided one ('{}')!".format(self.workloadSize(), args.size))

        otherArgs = ["-s", self.workloadSize()] + remaining

        if args.iterations:
            if args.iterations.isdigit():
                return ["-n", str(int(args.sf * int(args.iterations)))] + otherArgs
            if args.iterations == "-1":
                return None
        else:
            iterations = self.daCapoIterations()[benchname]
            if iterations == -1:
                return None
            else:
                iterations = iterations + int(self.getExtraIterationCount(iterations) * args.sf)
                return ["-n", str(iterations)] + otherArgs

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

    def benchmarkList(self, bmSuiteArgs):
        missing_sizes = set(self.daCapoIterations().keys()).difference(set(self.daCapoSizes().keys()))
        if len(missing_sizes) > 0:
            mx.abort("Missing size definitions for benchmark(s): {}".format(missing_sizes))
        return [b for b, it in self.daCapoIterations().items()
                if self.workloadSize() in self.daCapoSizes().get(b, []) and it != -1]

    def successPatterns(self):
        return [
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            re.compile(
                r"^===== DaCapo (?P<version>[^\n]+) ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def failurePatterns(self):
        return [
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            re.compile(
                r"^===== DaCapo (?P<version>[^\n]+) ([a-zA-Z0-9_]+) FAILED (warmup|) =====", # pylint: disable=line-too-long
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
            # Due to the non-determinism of DaCapo version printing, we only match the name.
            mx_benchmark.StdOutRule(
                r"===== DaCapo (?P<version>[^\n]+) (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
                r"===== DaCapo (?P<version>[^\n]+) (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
                r"===== DaCapo (?P<version>[^\n]+) (?P<benchmark>[a-zA-Z0-9_]+) completed warmup [0-9]+ in (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
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
    "avrora"      : 20,
    "batik"       : 40,
    "eclipse"     : -1,
    "fop"         : 40,
    "h2"          : 25,
    "jython"      : 50,
    "luindex"     : 20,
    "lusearch"    : 40,
    "lusearch-fix": -1,
    "pmd"         : 30,
    "sunflow"     : 35,
    "tomcat"      : -1,
    "tradebeans"  : -1,
    "tradesoap"   : -1,
    "xalan"       : 30,
}


_daCapoSizes = {
    "avrora":       ["default", "small", "large"],
    "batik":        ["default", "small", "large"],
    "eclipse":      ["default", "small", "large"],
    "fop":          ["default", "small"],
    "h2":           ["default", "small", "large", "huge"],
    "jython":       ["default", "small", "large"],
    "luindex":      ["default", "small"],
    "lusearch":     ["default", "small", "large"],
    "lusearch-fix": ["default", "small", "large"],
    "pmd":          ["default", "small", "large"],
    "sunflow":      ["default", "small", "large"],
    "tomcat":       ["default", "small", "large", "huge"],
    "tradebeans":   ["small", "large", "huge"],
    "tradesoap":    ["default", "small", "large", "huge"],
    "xalan":        ["default", "small", "large"]
}


def _is_batik_supported(jdk):
    """
    Determines if Batik runs on the given jdk. Batik's JPEGRegistryEntry contains a reference
    to TruncatedFileException, which is specific to the Sun/Oracle JDK. On a different JDK,
    this results in a NoClassDefFoundError: com/sun/image/codec/jpeg/TruncatedFileException
    """
    import subprocess
    try:
        subprocess.check_output([jdk.javap, 'com.sun.image.codec.jpeg.TruncatedFileException'])
        return True
    except subprocess.CalledProcessError:
        mx.warn('Batik uses Sun internal class com.sun.image.codec.jpeg.TruncatedFileException which is not present in ' + jdk.home)
        return False


class DaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
    """DaCapo benchmark suite implementation."""

    def name(self):
        if self.workloadSize() == "default":
            return "dacapo"
        else:
            return "dacapo-{}".format(self.workloadSize())

    def defaultSuiteVersion(self):
        return self.availableSuiteVersions()[-1]

    def availableSuiteVersions(self):
        return ["9.12-bach", "9.12-MR1-bach", "9.12-MR1-git+2baec49"]

    def workloadSize(self):
        return "default"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_CP"

    def daCapoLibraryName(self):
        if self.version() == "9.12-bach":  # 2009 release
            return "DACAPO"
        elif self.version() == "9.12-MR1-bach":  # 2018 maintenance release (January 2018)
            return "DACAPO_MR1_BACH"
        elif self.version() == "9.12-MR1-git+2baec49":  # commit from July 2018
            return "DACAPO_MR1_2baec49"
        else:
            return None

    def daCapoIterations(self):
        iterations = _daCapoIterations.copy()
        if self.version() == "9.12-bach":
            del iterations["eclipse"]
            del iterations["tradebeans"]
            del iterations["tradesoap"]
            del iterations["lusearch-fix"]
            # Stopped working as of 8u92 on the initial release
            del iterations["tomcat"]

        jdk = mx.get_jdk()
        if jdk.javaCompliance >= '9':
            if "batik" in iterations:
                # batik crashes on JDK9+. This is fixed on the dacapo chopin branch only
                del iterations["batik"]
            if "tradesoap" in iterations:
                # validation fails transiently but frequently in the first iteration in JDK9+
                del iterations["tradesoap"]
            if "avrora" in iterations and jdk.javaCompliance >= '21':
                # avrora uses java.lang.Compiler which was removed in JDK 21 (JDK-8307125)
                del iterations["avrora"]
        elif not _is_batik_supported(java_home_jdk()):
            del iterations["batik"]

        if self.workloadSize() == "small":
            # Ensure sufficient warmup by doubling the number of default iterations for the small configuration
            iterations = {k: (2 * int(v)) if v != -1 else v for k, v in iterations.items()}
        if self.workloadSize() in {"huge", "gargantuan"}:
            # Reduce the default number of iterations for very large workloads to keep the runtime reasonable
            iterations = {k: max(int((int(v)/2)), 5) if v != -1 else v for k, v in iterations.items()}
        return iterations

    def daCapoSizes(self):
        return _daCapoSizes

    def flakySuccessPatterns(self):
        return [
            re.compile(
                r"^javax.ejb.FinderException: Cannot find account for",
                re.MULTILINE),
            re.compile(
                r"^java.lang.Exception: TradeDirect:Login failure for user:",
                re.MULTILINE),
        ]

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(DaCapoBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '16':
            vmArgs += ["--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.net=ALL-UNNAMED"]
        return vmArgs


class DacapoSmallBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "small"


class DacapoLargeBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'large' configuration."""

    def workloadSize(self):
        return "large"


class DacapoHugeBenchmarkSuite(DaCapoBenchmarkSuite):
    """The subset of DaCapo benchmarks supporting the 'huge' configuration."""

    def workloadSize(self):
        return "huge"


mx_benchmark.add_bm_suite(DaCapoBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoSmallBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoLargeBenchmarkSuite())
mx_benchmark.add_bm_suite(DacapoHugeBenchmarkSuite())


class DaCapoD3SBenchmarkSuite(DaCapoBenchmarkSuite): # pylint: disable=too-many-ancestors
    """DaCapo 9.12 Bach benchmark suite implementation with D3S modifications."""

    def name(self):
        return "dacapo-d3s"

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

        agentExtractPath = os.path.join(os.path.dirname(archive), 'ubench-agent')
        agentBaseDir = os.path.join(agentExtractPath, 'java-ubench-agent-2e5becaf97afcf64fd8aef3ac84fc05a3157bff5')
        agentPathToJar = os.path.join(agentBaseDir, 'out', 'lib', 'ubench-agent.jar')
        agentPathNative = os.path.join(agentBaseDir, 'out', 'lib', 'libubench-agent.so')

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

        if not os.path.exists(agentPaths['jar']):
            if not os.path.exists(os.path.join(agentPaths['base'], 'build.xml')):
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

_daCapoScalaSizes = {
    "actors":       ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "apparat":      ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "factorie":     ["default", "tiny", "small", "large", "huge", "gargantuan"],
    "kiama":        ["default", "small"],
    "scalac":       ["default", "small", "large"],
    "scaladoc":     ["default", "small", "large"],
    "scalap":       ["default", "small", "large"],
    "scalariform":  ["default", "tiny", "small", "large", "huge"],
    "scalatest":    ["default", "small"],  # 'large' and 'huge' sizes fail validation
    "scalaxb":      ["default", "tiny", "small", "large", "huge"],
    "specs":        ["default", "small", "large"],
    "tmt":          ["default", "tiny", "small", "large", "huge"]
}


class ScalaDaCapoBenchmarkSuite(BaseDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
    """Scala DaCapo benchmark suite implementation."""

    def name(self):
        if self.workloadSize() == "default":
            return "scala-dacapo"
        else:
            return "scala-dacapo-{}".format(self.workloadSize())

    def version(self):
        return "0.1.0"

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_SCALA_CP"

    def daCapoLibraryName(self):
        return "DACAPO_SCALA"

    def workloadSize(self):
        return "default"

    def daCapoIterations(self):
        result = _daCapoScalaConfig.copy()
        if not java_home_jdk().javaCompliance < '11':
            mx.logv('Removing scala-dacapo:actors from benchmarks because corba has been removed since JDK11 (http://openjdk.java.net/jeps/320)')
            del result['actors']
        if java_home_jdk().javaCompliance >= '16':
            # See GR-29222 for details.
            mx.logv('Removing scala-dacapo:specs from benchmarks because it uses a library that violates module permissions which is no longer allowed in JDK 16 (JDK-8255363)')
            del result['specs']
        return result

    def daCapoSizes(self):
        return _daCapoScalaSizes

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


class ScalaDacapoTinyBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "tiny"


class ScalaDacapoSmallBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'small' configuration."""

    def workloadSize(self):
        return "small"


class ScalaDacapoLargeBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'large' configuration."""

    def workloadSize(self):
        return "large"

    def flakySkipPatterns(self, benchmarks, bmSuiteArgs):
        skip_patterns = super(ScalaDacapoLargeBenchmarkSuite, self).flakySuccessPatterns()
        if "specs" in benchmarks:
            skip_patterns += [
                re.escape(r"Line count validation failed for stdout.log, expecting 1996 found 1997"),
            ]
        return skip_patterns


class ScalaDacapoHugeBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'huge' configuration."""

    def workloadSize(self):
        return "huge"


class ScalaDacapoGargantuanBenchmarkSuite(ScalaDaCapoBenchmarkSuite):
    """The subset of Scala DaCapo benchmarks supporting the 'gargantuan' configuration."""

    def workloadSize(self):
        return "gargantuan"


mx_benchmark.add_bm_suite(ScalaDaCapoBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoTinyBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoSmallBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoLargeBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoHugeBenchmarkSuite())
mx_benchmark.add_bm_suite(ScalaDacapoGargantuanBenchmarkSuite())


_allSpecJVM2008Benches = [
    'compiler.compiler',
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
if 'compiler.compiler' in _allSpecJVM2008BenchesJDK9:
    # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9+
    _allSpecJVM2008BenchesJDK9.remove('compiler.compiler')
if 'startup.compiler.compiler' in _allSpecJVM2008BenchesJDK9:
    _allSpecJVM2008BenchesJDK9.remove('startup.compiler.compiler')

class SpecJvm2008BenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.TemporaryWorkdirMixin):
    """SpecJVM2008 benchmark suite implementation.

    This benchmark suite can run multiple benchmarks as part of one VM run.
    """
    def name(self):
        return "specjvm2008"

    def benchSuiteName(self, bmSuiteArgs=None):
        return self.name()

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def specJvmPath(self):
        specjvm2008 = mx.get_env("SPECJVM2008")
        if specjvm2008 is None:
            mx.abort("Please set the SPECJVM2008 environment variable to a " +
                     "SPECjvm2008 directory.")
        jarname = "SPECjvm2008.jar"
        jarpath = os.path.join(specjvm2008, jarname)
        if not os.path.exists(jarpath):
            mx.abort("The SPECJVM2008 environment variable points to a directory " +
                     "without the SPECjvm2008.jar file.")

        # copy to newly-created temporary working directory
        working_dir_jarpath =  os.path.abspath(os.path.join(self.workdir, jarname))
        if not os.path.exists(working_dir_jarpath):
            mx.log("copying " + specjvm2008 + " to " + self.workdir)
            shutil.copytree(specjvm2008, self.workdir, dirs_exist_ok=True)

        return working_dir_jarpath

    def validateEnvironment(self):
        if not self.specJvmPath():
            raise RuntimeError(
                "The SPECJVM2008 environment variable was not specified.")

    def validateReturnCode(self, retcode):
        return retcode == 0

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            # No benchmark specified in the command line, so run everything.
            benchmarks = self.benchmarkList(bmSuiteArgs)

        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        if "mpegaudio" in benchmarks:
            # GR-51015: run the benchmark on one thread
            # as a workaround for mpegaudio concurrency issues.
            mx.log("Setting benchmark threads to 1 due to race conditions in mpegaudio benchmark")
            runArgs += ["-bt", "1"]

        # The startup benchmarks are executed by spawning a new JVM. However, this new VM doesn't
        # inherit the flags passed to the main process.
        # According to the SpecJVM jar help message, one must use the '--jvmArgs' option to specify
        # options to pass to the startup benchmarks. It has no effect on the non startup benchmarks.
        startupJVMArgs = ["--jvmArgs", " ".join(vmArgs)] if "startup" in ' '.join(benchmarks) else []

        return vmArgs + ["-jar"] + [self.specJvmPath()] + runArgs + benchmarks + startupJVMArgs

    def runArgs(self, bmSuiteArgs):
        runArgs = super(SpecJvm2008BenchmarkSuite, self).runArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '9':
            # GR-8452: SpecJVM2008 compiler.compiler does not work on JDK9
            # Skips initial check benchmark which tests for javac.jar on classpath.
            runArgs += ["-ict"]
        return runArgs

    def vmArgs(self, bmSuiteArgs):
        vmArgs = super(SpecJvm2008BenchmarkSuite, self).vmArgs(bmSuiteArgs)
        if java_home_jdk().javaCompliance >= '16' and \
                ("xml.transform" in self.benchmarkList(bmSuiteArgs) or
                 "startup.xml.transform" in self.benchmarkList(bmSuiteArgs)):
            vmArgs += ["--add-exports=java.xml/com.sun.org.apache.xerces.internal.parsers=ALL-UNNAMED",
                       "--add-exports=java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED"]
        return vmArgs

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
        # For historical reasons, we have the suffix. Dropping the suffix would require data migration.
        suite_name = self.benchSuiteName() +  "-single"
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


def _get_specjbb_vmArgs(java_compliance):
    args = [
        "-XX:+UseNUMA",
        "-XX:+AlwaysPreTouch",
        "-XX:-UseAdaptiveSizePolicy",
        "-XX:-UseAdaptiveNUMAChunkSizing",
        "-XX:+PrintGCDetails"
    ]

    if java_compliance < '16':
        # JDK-8243161: Deprecated in JDK15 and marked obsolete in JDK16
        args.append("-XX:+UseLargePagesInMetaspace")

    if mx.is_linux():
        args.append("-XX:+UseTransparentHugePages")

    return args


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
        return "1.04"

    def vmArgs(self, bmSuiteArgs):
        vmArgs = self.vmArgshHeapFromEnv(super(SpecJbb2015BenchmarkSuite, self).vmArgs(bmSuiteArgs))
        return _get_specjbb_vmArgs(mx.get_jdk().javaCompliance) + vmArgs

    def specJbbClassPath(self):
        specjbb2015 = mx.get_env("SPECJBB2015")
        if specjbb2015 is None:
            mx.abort("Please set the SPECJBB2015 environment variable to a " +
                     "SPECjbb2015 directory.")
        jbbpath = os.path.join(specjbb2015, "specjbb2015.jar")
        if not os.path.exists(jbbpath):
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


class RenaissanceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
    """Renaissance benchmark suite implementation.
    """
    def name(self):
        return "renaissance"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def renaissanceLibraryName(self):
        return "RENAISSANCE_{}".format(self.version())

    def renaissanceIterations(self):
        benchmarks = _renaissanceConfig.copy()

        if mx.get_jdk().javaCompliance >= '21' and self.version() in ["0.14.1"]:
            del benchmarks["als"]
            del benchmarks["chi-square"]
            del benchmarks["dec-tree"]
            del benchmarks["gauss-mix"]
            del benchmarks["log-regression"]
            del benchmarks["movie-lens"]
            del benchmarks["naive-bayes"]
            del benchmarks["page-rank"]
            del benchmarks["neo4j-analytics"]

        if self.version() in ["0.15.0"]:
            del benchmarks["chi-square"]
            del benchmarks["gauss-mix"]
            del benchmarks["page-rank"]
            del benchmarks["movie-lens"]

        return benchmarks

    def completeBenchmarkList(self, bmSuiteArgs):
        return sorted(bench for bench in _renaissanceConfig)

    def defaultSuiteVersion(self):
        return self.availableSuiteVersions()[-1]

    def availableSuiteVersions(self):
        return ["0.14.1", "0.15.0"]

    def renaissancePath(self):
        lib = mx.library(self.renaissanceLibraryName())
        if lib:
            return lib.get_path(True)
        return None

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-r", default=None)
        parser.add_argument("-sf", default=1, type=float, help="The total number of iterations is equivalent to the value selected by the '-r' flag scaled by this factor.")
        args, remaining = parser.parse_known_args(runArgs)
        if args.r:
            if args.r.isdigit():
                return ["-r", str(int(args.sf * int(args.r)))] + remaining
            if args.r == "-1":
                return remaining
        else:
            iterations = self.renaissanceIterations()[benchname]
            if iterations == -1:
                return remaining
            else:
                return ["-r", str(int(args.sf * iterations))] + remaining

    def vmArgs(self, bmSuiteArgs):
        vm_args = super(RenaissanceBenchmarkSuite, self).vmArgs(bmSuiteArgs)

        # Renaissance issue #439
        vm_args.append("-Djava.security.manager=allow")
        return vm_args

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchArg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) == 0:
            mx.abort("Must specify at least one benchmark.")
        else:
            benchArg = ",".join(benchmarks)

        vmArgs = self.vmArgs(bmSuiteArgs)
        sparkBenchmarks = set([
            "als",
            "chi-square",
            "dec-tree",
            "gauss-mix",
            "log-regression",
            "movie-lens",
            "naive-bayes",
            "page-rank",
        ])

        if any(benchmark in sparkBenchmarks for benchmark in benchmarks):
            # Spark benchmarks require a higher stack size than default in some configurations.
            # [JDK-8303076] [GR-44499] [GR-50671]
            vmArgs.append("-Xss1090K")

        runArgs = self.postprocessRunArgs(benchmarks[0], self.runArgs(bmSuiteArgs))
        return (vmArgs + ["-jar", self.renaissancePath()] + runArgs + [benchArg])

    def benchmarkList(self, bmSuiteArgs):
        return [b for b, it in self.renaissanceIterations().items() if it != -1]

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
                    "bench-suite-version": self.version(),
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

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = super(RenaissanceBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceBenchmarkSuite())


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

    def version(self):
        return "1.1"

    def awfyLibraryName(self):
        return "AWFY_{}".format(self.version())

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

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = super(AWFYBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(AWFYBenchmarkSuite())




STAGE_LAST_SUCCESSFUL_PREFIX: str = "Successfully finished the last specified stage:"
STAGE_SUCCESSFUL_PREFIX: str = "Successfully finished stage:"
STAGE_SKIPPED_PREFIX: str = "Skipping stage:"

SUCCESSFUL_STAGE_PATTERNS = [re.compile(p, re.MULTILINE) for p in [
    # Produced when the last stage as requested by the user (see: NativeImageBenchmarkMixin.stages) finished
    rf"{STAGE_LAST_SUCCESSFUL_PREFIX}.*$",
    # Produced when any other stage finishes
    rf"{STAGE_SUCCESSFUL_PREFIX}.*$",
    # Produced when a stage is skipped for some reason (e.g. the specific configuration does not require it)
    rf"{STAGE_SKIPPED_PREFIX}.*$",
]]
"""
List of regex patterns to use in successPatterns() to match the successful completion of a Native Image benchmark stage.
Native Image benchmarks run in stages and not all stages have the expected success pattern for the benchmark suite,
which generally only matches the benchmark run output and nothing in the build output.
Instead, each benchmark stage produces one of the following messages to signal that the stage completed and bypass the
original success pattern check.
"""


def parse_prefixed_args(prefix, args):
    ret = []
    for arg in args:
        if arg.startswith(prefix):
            parsed = arg.split(' ')[0].split(prefix)[1]
            if parsed not in ret:
                ret.append(parsed)
    return ret

def parse_prefixed_arg(prefix, args, errorMsg):
    ret = parse_prefixed_args(prefix, args)
    if len(ret) > 1:
        mx.abort(errorMsg)
    elif len(ret) < 1:
        return None
    else:
        return ret[0]


def convertValue(table, value, fromUnit, toUnit):
    if fromUnit not in table:
        mx.abort("Unexpected unit: " + fromUnit)
    fromFactor = float(table[fromUnit])

    if toUnit not in table:
        mx.abort("Unexpected unit: " + fromUnit)
    toFactor = float(table[toUnit])

    return float((value * fromFactor) / toFactor)


timeUnitTable = {
    'ns':   1,
    'us':   1000,
    'ms':   1000 * 1000,
    's':    1000 * 1000 * 1000,
    'min':  60 * 1000 * 1000 * 1000,
    'h':    60 * 60 * 1000 * 1000 * 1000
}


tputUnitTable = {
    'op/ns':    1.0,
    'op/us':    1.0/1000,
    'op/ms':    1.0/(1000 * 1000),
    'op/s':     1.0/(1000 * 1000 * 1000),
    'op/min':   1.0/(60 * 1000 * 1000 * 1000),
    'op/h':     1.0/(60 * 60 * 1000 * 1000 * 1000)
}


memUnitTable = {
    'B':    1,
    'kB':   1000,
    'MB':   1000 * 1000,
    'GB':   1000 * 1000 * 1000,
    'KiB':  1024,
    'MiB':  1024 * 1024,
    'GiB':  1024 * 1024 * 1024
}


def strip_args_with_number(strip_args, args):
    """Removes arguments (specified in `strip_args`) from `args`.

    The stripped arguments are expected to have a number value. For single character arguments (e.g. `-X`) the space
    before the value might be omitted (e.g. `-X8`). In this case only one element is removed from `args`. Otherwise
    (e.g. `-X 8`), two elements are removed from `args`.
    """

    if not isinstance(strip_args, list):
        strip_args = [strip_args]

    def _strip_arg_with_number_gen(_strip_arg, _args):
        skip_next = False
        for arg in _args:
            if skip_next:
                # skip value of argument
                skip_next = False
                continue
            if arg.startswith(_strip_arg):
                if arg == _strip_arg:
                    # full match - value is the next argument `-i 10`
                    skip_next = True
                    continue
                # partial match at begin - either a different option or value without space separator `-i10`
                if len(_strip_arg) == 2 and _strip_arg.startswith('-'):
                    # only look at single character options
                    remainder_arg = arg[len(_strip_arg):]
                    try:
                        int(remainder_arg)
                        # remainder is a number - skip the current arg
                        continue
                    except ValueError:
                        # not a number - probably a different option
                        pass
            # add arg to result
            yield arg

    result = args
    for strip_arg in strip_args:
        result = _strip_arg_with_number_gen(strip_arg, result)
    return list(result)


class Stage(Enum):
    AGENT = "agent"
    INSTRUMENT_IMAGE = "instrument-image"
    INSTRUMENT_RUN = "instrument-run"
    IMAGE = "image"
    RUN = "run"

    def __str__(self):
        return self.value

    def is_image(self) -> bool:
        """Whether this is an image stage (a stage that performs an image build)"""
        return self in [Stage.INSTRUMENT_IMAGE, Stage.IMAGE]

    def is_instrument(self) -> bool:
        """Whether this is an image stage (a stage that performs an image build)"""
        return self in [Stage.INSTRUMENT_IMAGE, Stage.INSTRUMENT_RUN]

class StagesInfo:
    """
    Holds information about benchmark stages that should be persisted across multiple stages in the same
    ``mx benchmark`` command.

    Is used to pass data between the benchmark suite and the underlying :class:`mx_benchmark.Vm`.

    The information about the stages comes from two layers:

    * The stages requested by the user and passed into the object during creation
    * And the effectively executed stages, which are determined in the `NativeImageBenchmarkConfig` class and passed to
      this object the first time we call into the ``NativeImageVM``.
      The effective stages are determined by removing stages that don't need to run in the current benchmark
      configuration from the requested changes.
      This information is only available if :attr:`is_set_up` returns ``True``.
    """

    def __init__(self, requested_stages: List[Stage], fallback_mode: bool = False):
        """
        :param requested_stages: List of stages requested by the user. See also :meth:`NativeImageBenchmarkMixin.stages`
                                 and :attr:`StagesInfo.effective_stages`
        """
        self._is_set_up: bool = False
        self._requested_stages = requested_stages
        self._removed_stages: Set[Stage] = set()
        self._effective_stages: Optional[List[Stage]] = None
        self._stages_till_now: List[Stage] = []
        self._requested_stage: Optional[Stage] = None
        # Computed lazily
        self._skip_current_stage: Optional[bool] = None
        self._failed: bool = False
        self._fallback_mode = fallback_mode

    @property
    def fallback_mode(self) -> bool:
        return self._fallback_mode

    @property
    def is_set_up(self) -> bool:
        return self._is_set_up

    def setup(self, removed_stages: Set[Stage]) -> None:
        """
        Fully configures the object with information about removed stages.

        From that, the effective list of stages can be computed.
        Only after this method is called for the first time can the effective stages be accessed

        :param removed_stages: Set of stages that should not be executed under this benchmark configuration
        """
        if self.is_set_up:
            # This object is used again for an additional stage.
            # Sanity check to make sure the removed stages are the same as in the first call
            assert self._removed_stages == removed_stages, f"Removed stages differ between executed stages: {self._removed_stages} != {removed_stages}"
        else:
            self._removed_stages = removed_stages
            # requested_stages - removed_stages while preserving order of requested_stages
            self._effective_stages = [s for s in self._requested_stages if s not in removed_stages]
            self._is_set_up = True

    @property
    def effective_stages(self) -> List[Stage]:
        """
        List of stages that are actually executed for this benchmark (is equal to requested_stages - removed_stages)
        """
        assert self.is_set_up
        return self._effective_stages

    @property
    def skip_current_stage(self) -> bool:
        if self._skip_current_stage is None:
            self._skip_current_stage = self.requested_stage not in self.effective_stages
        return self._skip_current_stage

    @property
    def requested_stage(self) -> Stage:
        """
        The stage that was last requested to be executed.
        It is not guaranteed that this stage will be executed, it could be a skipped stage (see
        :attr:`StagesInfo.skip_current_stage`)

        Use this for informational output, prefer :attr:`StagesInfo.effective_stage` to compare against the effectively
        executed stage.
        """
        assert self._requested_stage, "No current stage set"
        return self._requested_stage

    @property
    def effective_stage(self) -> Optional[Stage]:
        """
        Same as :meth:`StagesInfo.requested_stage`, but returns None if the stage is skipped.
        """
        return None if self.skip_current_stage else self.requested_stage

    @property
    def last_stage(self) -> Stage:
        return self.effective_stages[-1]

    @property
    def failed(self) -> bool:
        return self._failed

    @property
    def stages_till_now(self) -> List[Stage]:
        """
        List of stages executed so far, all of which have been successful.

        Does not include the current stage.
        """
        return self._stages_till_now

    def change_stage(self, stage_name: Stage) -> None:
        self._requested_stage = stage_name
        # Force recomputation
        self._skip_current_stage = None

    def success(self) -> None:
        assert self.is_set_up
        """Called when the current stage has finished successfully"""
        self._stages_till_now.append(self.requested_stage)

    def fail(self) -> None:
        assert self.is_set_up
        """Called when the current stage finished with an error"""
        self._failed = True

    def should_produce_datapoints(self, stages: Union[None, Stage, Collection[Stage]] = None) -> bool:
        """
        Whether, under the current configuration, datapoints should be produced for any of the given stage.

        In fallback mode, we only produce datapoints for the ``image`` and ``run`` stage because stages are not run
        individually and datapoints from other stages may not be distinguishable from the ``image`` and ``run`` stage.

        :param stages: If None, the current effective stage is used. A single stage will be treated as a singleton list
        """

        if not stages:
            stages = [self.effective_stage]
        elif not isinstance(stages, collections.abc.Collection):
            stages = [stages]

        if self.fallback_mode:
            # In fallback mode, all datapoints are generated at once and not in a specific stage, checking whether the
            # given stage matches the current stage will almost never yield the sensible result
            return not {Stage.IMAGE, Stage.RUN}.isdisjoint(stages)
        else:
            return self.effective_stage in stages


class NativeImageBenchmarkMixin(object):
    """
    Mixin extended by :class:`BenchmarkSuite` classes to enable a JVM bench suite to run as a Native Image benchmark.

    IMPORTANT: All Native Image benchmarks (including JVM benchmarks that are also used in Native Image benchmarks) must
    explicitly call :meth:`NativeImageBenchmarkMixin.intercept_run` in order for benchmarking to work.
    See description of that method for more information.

    Native Image benchmarks are run in stages: agent, instrument-image, instrument-run, image, run
    Each stage produces intermediate files required by subsequent phases until the final ``run`` stage runs the final
    Native Image executable to produce performance results.
    However, it is worth collecting certain performance metrics from any of the other stages as well (e.g. compiler
    performance).

    The mixin's ``intercept_run`` method calls into the ``mx_vm_benchmark.NativeImageVM`` once per stage to run that
    stage and produce datapoints for that stage only.
    This is a bit of a hack since each stage can be seen as its own full benchmark execution (does some operations and
    produces datapoints), but it works well in most cases.

    Limitations
    -----------

    This mode of benchmarking cannot fully support arbitrary benchmarking suites without modification.

    Because of each stage effectively being its own benchmark execution, rules that unconditionally produce datapoints
    will misbehave as they will produce datapoints in each stage (even though, it is likely only meant to produce
    benchmark performance datapoints).
    For example, if a benchmark suite has rules that read the performance data from a file (e.g. JMH), those rules will
    happily read that file and produce performance data in every stage (even the ``image`` stages).
    Such rules need to be modified to only trigger in the desired stages. Either by parsing the file location out of the
    benchmark output or by writing some Native Image specific logic (with :meth:`is_native_mode`)
    An example for such a workaround are :class:`mx_benchmark.JMHBenchmarkSuiteBase` and its subclasses (see
    ``get_jmh_result_file``, its usages and its Native Image specific implementation in
    :class:`mx_graal_benchmark.JMHNativeImageBenchmarkMixin`)

    If the benchmark suite itself dispatches into the VM multiple times (in addition to the mixin doing it once per
    stage), care must be taken in which order this happens.
    If these multiple dispatches happen in a (transitive) callee of ``intercept_run``, each dispatch will first happen
    for the first stage and only after the next stage will be run. In that order, a subsequent dispatch may overwrite
    intermediate files of the previous dispatch of the same stage (e.g. executables).
    For this to work as expected, ``intercept_run`` needs to be a callee of these multiple dispatches, i.e. these
    multiple dispatches also need to happen in the ``run`` method and (indirectly) call ``intercept_run``.

    If these limitations cannot be worked around, using the fallback mode may be required, with the caveat that it
    provides limited functionality.
    This was done for example in :meth:`mx_graal_benchmark.JMHNativeImageBenchmarkMixin.fallback_mode_reason`.

    Fallback Mode
    -------------

    Fallback mode is for benchmarks that are fundamentally incompatible with how this mixin dispatches into the
    ``NativeImageVM`` once per stage (e.g. JMH with the ``--jmh-run-individually`` flag).
    The conditional logic to enable fallback mode can be implemented by overriding :meth:`fallback_mode_reason`.

    In fallback mode, we only call into the VM once and it runs all stages in sequence. This limits what kind of
    performance data we can accurately collect (e.g. it is impossible to distinguish benchmark output from the
    ``instrument-run`` and ``run`` phases).
    Because of that, only the output of the ``image`` and ``run`` stages is returned from the VM (the remainder is still
    printed, but not used for regex matching when creating datapoints).

    In addition, the ``NativeImageVM`` will not produce any rules to generate extra datapoints (e.g. for image build
    metrics). If the benchmark suite dispatches into the VM multiple times (like for JMH with
    ``--jmh-run-individually``), those rules cannot work correctly since they cannot know for which individual benchmark
    to produce datapoint(s).

    Finally, the user cannot select only a subset of stages to run (using ``-Dnative-image.benchmark.stages``).
    All stages required for that benchmark are always run together.
    """

    def __init__(self):
        self.benchmark_name = None
        self.stages_info: Optional[StagesInfo] = None

    def benchmarkName(self):
        if not self.benchmark_name:
            raise NotImplementedError()
        return self.benchmark_name

    def fallback_mode_reason(self, bm_suite_args: List[str]) -> Optional[str]:
        """
        Reason why this Native Image benchmark should run in fallback mode.

        :return: None if no fallback is required. Otherwise, a non-empty string describing why fallback mode is necessary
        """
        return None

    def intercept_run(self, super_delegate: BenchmarkSuite, benchmarks, bm_suite_args: List[str]) -> DataPoints:
        """
        Intercepts the main benchmark execution (:meth:`BenchmarkSuite.run`) and runs a series of benchmark stages
        required for Native Image benchmarks in series.
        For non-native-image benchmarks, this simply delegates to the caller's ``super().run`` method.

        The stages are requested by the user (see :meth:`NativeImageBenchmarkMixin.stages`).

        There are no good ways to just intercept ``run`` in arbitrary ``BenchmarkSuite``s, so each
        :class:`BenchmarkSuite` subclass that is intended for Native Image benchmarking needs to make sure that the
        :meth:`BenchmarkSuite.run` calls into this method like this::

            def run(self, benchmarks, bm_suite_args: List[str]) -> DataPoints:
                return self.intercept_run(super(), benchmarks, bm_suite_args)

        It is fine if this implemented in a common (Native Image-specific) superclass of multiple benchmark suites, as
        long as the method is not overriden in a subclass in an incompatible way.

        :param super_delegate: A reference to the caller class' superclass in method-resolution order (MRO).
        :param benchmarks: Passed to :meth:`BenchmarkSuite.run`
        :param bm_suite_args: Passed to :meth:`BenchmarkSuite.run`
        :return: Datapoints accumulated from all stages
        """
        if not self.is_native_mode(bm_suite_args):
            # This is not a Native Image benchmark, just run the benchmark as regular
            return super_delegate.run(benchmarks, bm_suite_args)

        datapoints: List[DataPoint] = []

        requested_stages = self.stages(bm_suite_args)

        fallback_reason = self.fallback_mode_reason(bm_suite_args)
        if fallback_reason:
            # In fallback mode, all stages are run at once. There is matching code in `NativeImageVM.run_java` for this.
            mx.log(f"Running benchmark in fallback mode (reason: {fallback_reason})")
            self.stages_info = StagesInfo(requested_stages, True)
            datapoints += super_delegate.run(benchmarks, bm_suite_args)
        else:
            self.stages_info = StagesInfo(requested_stages)

            for stage in requested_stages:
                self.stages_info.change_stage(stage)
                # Start the actual benchmark execution. The stages_info attribute will be used by the NativeImageVM to
                # determine which stage to run this time.
                stage_dps = super_delegate.run(benchmarks, bm_suite_args)
                NativeImageBenchmarkMixin._inject_stage_keys(stage_dps, stage)
                datapoints += stage_dps

        self.stages_info = None
        return datapoints

    @staticmethod
    def _inject_stage_keys(dps: DataPoints, stage: Stage) -> None:
        """
        Modifies the ``host-vm-config`` key based on the current stage.
        For the agent and instrument stages ``-agent`` and ``-instrument`` are appended to distinguish the datapoints
        from the main ``image`` and ``run`` phases.

        :param dps: List of datapoints, modified in-place
        :param stage: The stage the datapoints were generated in
        """

        if stage == Stage.AGENT:
            host_vm_suffix = "-agent"
        elif stage in [Stage.INSTRUMENT_IMAGE, Stage.INSTRUMENT_RUN]:
            host_vm_suffix = "-instrument"
        elif stage in [Stage.IMAGE, Stage.RUN]:
            host_vm_suffix = ""
        else:
            raise ValueError(f"Unknown stage {stage}")

        for dp in dps:
            dp["host-vm-config"] += host_vm_suffix

    def run_stage(self, vm, stage: Stage, command, out, err, cwd, nonZeroIsFatal):
        final_command = command
        # Apply command mapper hooks (e.g. trackers) for all stages that run benchmark workloads
        # We cannot apply them for the image stages because the datapoints are indistinguishable from datapoints
        # produced in the corresponding run stages.
        if stage in [Stage.AGENT, Stage.INSTRUMENT_RUN, Stage.RUN] and self.stages_info.should_produce_datapoints(stage):
            final_command = self.apply_command_mapper_hooks(command, vm)
        return mx.run(final_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def is_native_mode(self, bm_suite_args: List[str]):
        """Checks whether the given arguments request a Native Image benchmark"""
        jvm_flag = self.jvm(bm_suite_args)
        if not jvm_flag:
            # In case the --jvm argument was not given explicitly, let the registry load the appropriate vm and extract
            # the name from there.
            # This is much more expensive, so it is only used as a fallback
            jvm_flag = self.get_vm_registry().get_vm_from_suite_args(bm_suite_args).name()
        return "native-image" in jvm_flag

    def apply_command_mapper_hooks(self, cmd, vm):
        return mx.apply_command_mapper_hooks(cmd, vm.command_mapper_hooks)

    def extra_image_build_argument(self, _, args):
        return parse_prefixed_args('-Dnative-image.benchmark.extra-image-build-argument=', args)

    def extraVmArgs(self):
        assert self.dist
        distribution = mx.distribution(self.dist)
        assert distribution.isJARDistribution()
        jdk = mx.get_jdk(distribution.javaCompliance)
        add_opens_add_extracts = []
        if mx_benchmark.mx_benchmark_compatibility().jmh_dist_benchmark_extracts_add_opens_from_manifest():
            add_opens_add_extracts = mx_benchmark._add_opens_and_exports_from_manifest(distribution.path)
        return mx.get_runtime_jvm_args([self.dist], jdk=jdk, exclude_names=mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes) + add_opens_add_extracts

    def extra_jvm_arg(self, benchmark, args):
        return parse_prefixed_args('-Dnative-image.benchmark.extra-jvm-arg=', args)

    def extra_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the final image.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-run-arg=', args)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the agent run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-agent-run-arg=', args)

    def extra_agentlib_options(self, benchmark, args, image_run_args):
        """Returns additional native-image-agent options.

        The returned options are added to the agentlib:native-image-agent option list.
        The config-output-dir is configured by the benchmark runner and cannot be overridden.
        """

        # All Renaissance Spark benchmarks require lambda class predefinition, so we need this additional option that
        # is used for the class predefinition feature. See GR-37506
        return ['experimental-class-define-support'] if (benchmark in ['chi-square', 'gauss-mix', 'movie-lens', 'page-rank']) else []

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        """Returns all arguments passed to the profiling run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        # either use extra profile run args if set or otherwise the extra run args
        extra_profile_run_args = parse_prefixed_args('-Dnative-image.benchmark.extra-profile-run-arg=', args) or parse_prefixed_args('-Dnative-image.benchmark.extra-run-arg=', args)
        return image_run_args + extra_profile_run_args

    def extra_agent_profile_run_arg(self, benchmark, args, image_run_args):
        """Returns all arguments passed to the agent profiling run.

        This includes those passed globally on the `mx benchmark` command line after the last `--`.
        These arguments are passed via the `image_run_args` parameter.
        Conflicting global arguments might be filtered out. The function `strip_args_with_number()` can help with that.
        """
        return image_run_args + parse_prefixed_args('-Dnative-image.benchmark.extra-agent-profile-run-arg=', args)

    def benchmark_output_dir(self, _, args):
        parsed_args = parse_prefixed_args('-Dnative-image.benchmark.benchmark-output-dir=', args)
        if parsed_args:
            return parsed_args[0]
        else:
            return None

    def stages(self, bm_suite_args: List[str]) -> List[Stage]:
        """
        Benchmark stages requested by the user with ``-Dnative-image.benchmark.stages=``.

        Falls back to :meth:`NativeImageBenchmarkMixin.default_stages` if not specified.
        """
        args = self.vmArgs(bm_suite_args)
        parsed_arg = parse_prefixed_arg('-Dnative-image.benchmark.stages=', args, 'Native Image benchmark stages should only be specified once.')

        fallback_reason = self.fallback_mode_reason(bm_suite_args)
        if parsed_arg and fallback_reason:
            mx.abort(
                "This benchmarking configuration is running in fallback mode and does not support selection of benchmark stages using -Dnative-image.benchmark.stages"
                f"Reason: {fallback_reason}\n"
                f"Arguments: {bm_suite_args}"
            )

        return list(map(Stage, parsed_arg.split(',') if parsed_arg else self.default_stages()))

    def default_stages(self) -> List[str]:
        """Default list of stages to run if none have been specified."""
        return ["agent", "instrument-image", "instrument-run", "image", "run"]

    def skip_agent_assertions(self, _, args):
        parsed_args = parse_prefixed_args('-Dnative-image.benchmark.skip-agent-assertions=', args)
        if 'true' in parsed_args or 'True' in parsed_args:
            return True
        elif 'false' in parsed_args or 'False' in parsed_args:
            return False
        else:
            return None

    def build_assertions(self, benchmark, is_gate):
        # We are skipping build assertions when a benchmark is not a part of a gate.
        return ['-J-ea', '-J-esa'] if is_gate else []

    # Override and return False if this suite should not check for samples in runs with PGO
    def checkSamplesInPgo(self):
        return True


def measureTimeToFirstResponse(bmSuite):
    protocolHost = bmSuite.serviceHost()
    servicePath = bmSuite.serviceEndpoint()
    if not (protocolHost.startswith('http') or protocolHost.startswith('https')):
        protocolHost = "http://" + protocolHost
    if not (servicePath.startswith('/') or protocolHost.endswith('/')):
        servicePath = '/' + servicePath
    url = "{}:{}{}".format(protocolHost, bmSuite.servicePort(), servicePath)

    measurementStartTime = time.time()
    sentRequests = 0
    receivedNon200Responses = 0
    last_report_time = time.time()
    req = urllib.request.Request(url, headers=bmSuite.requestHeaders())
    while time.time() - measurementStartTime < 120:
        time.sleep(.0001)
        if sentRequests > 0 and time.time() - last_report_time > 10:
            last_report_time = time.time()
            mx.log("Sent {:d} requests so far but did not receive a response with code 200 yet.".format(sentRequests))

        try:
            sentRequests += 1
            res = urllib.request.urlopen(req, timeout=10)
            responseCode = res.getcode()
            if responseCode == 200:
                processStartTime = mx.get_last_subprocess_start_time()
                finishTime = datetime.datetime.now()
                msToFirstResponse = (finishTime - processStartTime).total_seconds() * 1000
                currentOutput = "First response received in {} ms".format(msToFirstResponse)
                bmSuite.timeToFirstResponseOutputs.append(currentOutput)
                mx.log(currentOutput)
                return
            else:
                if receivedNon200Responses < 10:
                    mx.log("Received a response but it had response code " + str(responseCode) + " instead of 200")
                elif receivedNon200Responses == 10:
                    mx.log("No more response codes will be printed (already printed 10 response codes)")
                receivedNon200Responses += 1
        except IOError:
            pass

    mx.abort("Failed to measure time to first response. Service not reachable at " + url)


class BaseMicroserviceBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, NativeImageBenchmarkMixin):
    """
    Base class for Microservice benchmark suites. A Microservice is an application that opens a port that is ready to
    receive requests. This benchmark suite runs a tester process in the background (such as Wrk2) and run a
    Microservice application in foreground. Once the tester finishes stress testing the application, the tester process
    terminates and the application is killed with SIGTERM.

    The number of environment variables affects the startup time of all microservice frameworks. To ensure benchmark
    stability, we therefore execute those benchmarks with an empty set of environment variables.
    """

    NumMeasureTimeToFirstResponse = 10

    def __init__(self):
        super(BaseMicroserviceBenchmarkSuite, self).__init__()
        self.timeToFirstResponseOutputs = []
        self.startupOutput = ''
        self.peakOutput = ''
        self.latencyOutput = ''
        self.bmSuiteArgs = None
        self.workloadPath = None
        self.measureLatency = None
        self.measureFirstResponse = None
        self.measureStartup = None
        self.measurePeak = None
        self.parser = argparse.ArgumentParser()
        self.parser.add_argument("--workload-configuration", type=str, default=None, help="Path to workload configuration.")
        self.parser.add_argument("--skip-latency-measurements", action='store_true', help="Determines if the latency measurements should be skipped.")
        self.parser.add_argument("--skip-first-response-measurements", action='store_true', help="Determines if the time-to-first-response measurements should be skipped.")
        self.parser.add_argument("--skip-startup-measurements", action='store_true', help="Determines if the startup performance measurements should be skipped.")
        self.parser.add_argument("--skip-peak-measurements", action='store_true', help="Determines if the peak performance measurements should be skipped.")

    def benchMicroserviceName(self):
        """
        Returns the microservice name. The convention here is that the benchmark name contains two elements separated
        by a hyphen ('-'):
        - the microservice name (shopcart, for example);
        - the tester tool name (wrk, for example).

        :return: Microservice name.
        :rtype: str
        """

        if len(self.benchSuiteName().split('-', 1)) < 2:
            mx.abort("Invalid benchmark suite name: " + self.benchSuiteName())
        return self.benchSuiteName().split("-", 1)[0]

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def validateReturnCode(self, retcode):
        return retcode == 143

    def defaultWorkloadPath(self, benchmarkName):
        """Returns the workload configuration path.

        :return: Path to configuration file.
        :rtype: str
        """
        raise NotImplementedError()

    def workloadConfigurationPath(self):
        if self.workloadPath:
            mx.log("Using user-provided workload configuration file: {0}".format(self.workloadPath))
            return self.workloadPath
        else:
            return self.defaultWorkloadPath(self.benchmarkName())

    def applicationPath(self):
        """Returns the application Jar path.

        :return: Path to Jar.
        :rtype: str
        """
        raise NotImplementedError()

    def serviceHost(self):
        """Returns the microservice host.

        :return: Host used to access the microservice.
        :rtype: str
        """
        return 'localhost'

    def servicePort(self):
        """Returns the microservice port.

        :return: Port that the microservice is using to receive requests.
        :rtype: int
        """
        return 8080

    def serviceEndpoint(self):
        """Returns the microservice path that checks if the service is running.

        :return: service path
        :rtype: str
        """
        return ''

    def requestHeaders(self):
        """Returns extra headers to be sent when markign requests to the service endpoint..
        :rtype: dict[str, str]
        """
        return {}

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.vmArgs(bmSuiteArgs) + ["-jar", self.applicationPath()]

    @staticmethod
    def waitForPort(port, timeout=60):
        try:
            import psutil
        except ImportError:
            # Note: abort fails to find the process (not registered yet in mx) if we are too fast failing here.
            time.sleep(5)
            mx.abort("Failed to import {0} dependency module: psutil".format(BaseMicroserviceBenchmarkSuite.__name__))
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

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, applicationOutput, dims = super(BaseMicroserviceBenchmarkSuite, self).runAndReturnStdOut(benchmarks, bmSuiteArgs)
        result = ret_code, "\n".join(self.timeToFirstResponseOutputs) + '\n' + self.startupOutput + '\n' + self.peakOutput + '\n' + self.latencyOutput + '\n' + applicationOutput, dims

        # For HotSpot, the rules are executed after every execution and for Native Image the rules are applied after each stage.
        # So, it is necessary to reset the data to avoid duplication of datapoints.
        self.timeToFirstResponseOutputs = []
        self.startupOutput = ''
        self.peakOutput = ''
        self.latencyOutput = ''

        return result

    @staticmethod
    def terminateApplication(port):
        proc = BaseMicroserviceBenchmarkSuite.waitForPort(port, 0)
        if proc:
            proc.send_signal(signal.SIGTERM)
            return True
        else:
            return False

    @staticmethod
    def testTimeToFirstResponseInBackground(benchmarkSuite):
        mx.log("--------------------------------------------")
        mx.log("Started time-to-first-response measurements.")
        mx.log("--------------------------------------------")
        measureTimeToFirstResponse(benchmarkSuite)
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testStartupPerformanceInBackground(benchmarkSuite):
        mx.log("-----------------------------------------")
        mx.log("Started startup performance measurements.")
        mx.log("-----------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testStartupPerformance()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testPeakPerformanceInBackground(benchmarkSuite, warmup=True):
        mx.log("--------------------------------------")
        mx.log("Started peak performance measurements.")
        mx.log("--------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testPeakPerformance(warmup)
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def calibrateLatencyTestInBackground(benchmarkSuite):
        mx.log("---------------------------------------------")
        mx.log("Started calibration for latency measurements.")
        mx.log("---------------------------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.calibrateLatencyTest()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    @staticmethod
    def testLatencyInBackground(benchmarkSuite):
        mx.log("-----------------------------")
        mx.log("Started latency measurements.")
        mx.log("-----------------------------")
        if not BaseMicroserviceBenchmarkSuite.waitForPort(benchmarkSuite.servicePort()):
            mx.abort("Failed to find server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))
        benchmarkSuite.testLatency()
        if not BaseMicroserviceBenchmarkSuite.terminateApplication(benchmarkSuite.servicePort()):
            mx.abort("Failed to terminate server application in {0}".format(BaseMicroserviceBenchmarkSuite.__name__))

    def get_env(self):
        return {}

    def get_image_env(self):
        # Use the existing environment by default.
        return os.environ

    def run_stage(self, vm, stage: Stage, server_command, out, err, cwd, nonZeroIsFatal):
        if stage.is_image():
            # For image stages, we just run the given command
            with PatchEnv(self.get_image_env()):
                return super(BaseMicroserviceBenchmarkSuite, self).run_stage(vm, stage, server_command, out, err, cwd, nonZeroIsFatal)
        else:
            if stage == Stage.RUN:
                serverCommandWithTracker = self.apply_command_mapper_hooks(server_command, vm)

                mx_benchmark.disable_tracker()
                serverCommandWithoutTracker = self.apply_command_mapper_hooks(server_command, vm)
                mx_benchmark.enable_tracker()

                # Measure time-to-first-response multiple times (without any command mapper hooks as those affect the measurement significantly)
                for _ in range(self.NumMeasureTimeToFirstResponse):
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(target=BaseMicroserviceBenchmarkSuite.testTimeToFirstResponseInBackground, args=[self])
                        returnCode = mx.run(server_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                        measurementThread.join()
                    if not self.validateReturnCode(returnCode):
                        mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                # Measure startup performance (without RSS tracker)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testStartupPerformanceInBackground, [self])
                    returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                if not self.validateReturnCode(returnCode):
                    mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                # Measure peak performance (with all command mapper hooks)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self])
                    returnCode = mx.run(serverCommandWithTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                if not self.validateReturnCode(returnCode):
                    mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                if self.measureLatency:
                    if not any([c.get("requests-per-second") for c in self.loadConfiguration("latency")]):
                        # Calibrate for latency measurements (without RSS tracker) if no fixed request rate has been provided in the config
                        with PatchEnv(self.get_env()):
                            measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.calibrateLatencyTestInBackground, [self])
                            returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                            measurementThread.join()
                        if not self.validateReturnCode(returnCode):
                            mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                    # Measure latency (without RSS tracker)
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testLatencyInBackground, [self])
                        returnCode = mx.run(serverCommandWithoutTracker, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                        measurementThread.join()
                    if not self.validateReturnCode(returnCode):
                        mx.abort("The server application unexpectedly ended with return code " + str(returnCode))

                return returnCode
            elif stage in [Stage.AGENT, Stage.INSTRUMENT_RUN]:
                # For the agent and the instrumented run, it is sufficient to run the peak performance workload.
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self, False])
                    returnCode = mx.run(server_command, out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)
                    measurementThread.join()
                return returnCode
            else:
                mx.abort(f"Unexpected stage: {stage}")

    def startDaemonThread(self, target, args):
        def true_target(*true_target_args):
            self.setup_application()
            target(*args)

        thread = threading.Thread(target=true_target)
        thread.setDaemon(True)
        thread.start()
        return thread

    def setup_application(self):
        pass


    def get_application_startup_regex(self):
        raise NotImplementedError()

    def get_application_startup_units(self):
        raise NotImplementedError

    def applicationStartupRule(self, benchSuiteName, benchmark):
        return [
            # Example of Micronaut startup log:
            # "[main] INFO io.micronaut.runtime.Micronaut - Startup completed in 328ms. Server Running: <url>"
            mx_benchmark.StdOutRule(
                self.get_application_startup_regex(),
                {
                    "benchmark": benchmark,
                    "bench-suite": benchSuiteName,
                    "metric.name": "app-startup",
                    "metric.value": ("<startup>", float),
                    "metric.unit": self.get_application_startup_units(),
                    "metric.better": "lower",
                }
            )
        ]

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [
            mx_benchmark.StdOutRule(
                r"First response received in (?P<firstResponse>\d*[.,]?\d*) ms",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "time-to-first-response",
                    "metric.value": ("<firstResponse>", float),
                    "metric.unit": "ms",
                    "metric.better": "lower",
                }
            )
        ]

    def computePeakThroughputRSS(self, datapoints):
        tputDatapoint = None
        rssDatapoint = None
        for datapoint in datapoints:
            if datapoint['metric.name'] == 'peak-throughput':
                tputDatapoint = datapoint
            if datapoint['metric.name'] == 'max-rss':
                rssDatapoint = datapoint
        if tputDatapoint and rssDatapoint:
            newdatapoint = copy.deepcopy(tputDatapoint)
            newdatapoint['metric.name'] = 'ops-per-GB-second'
            newtput = convertValue(tputUnitTable, float(tputDatapoint['metric.value']), tputDatapoint['metric.unit'], "op/s")
            newrss = convertValue(memUnitTable, float(rssDatapoint['metric.value']), rssDatapoint['metric.unit'], "GB")
            newdatapoint['metric.value'] = newtput / newrss
            newdatapoint['metric.unit'] = 'op/GB*s'
            newdatapoint['metric.better'] = 'higher'
            return newdatapoint
        else:
            return None

    def validateStdoutWithDimensions(self, out, benchmarks, bmSuiteArgs, retcode=None, dims=None, extraRules=None) -> DataPoints:
        datapoints = super(BaseMicroserviceBenchmarkSuite, self).validateStdoutWithDimensions(
            out=out, benchmarks=benchmarks, bmSuiteArgs=bmSuiteArgs, retcode=retcode, dims=dims, extraRules=extraRules)

        newdatapoint = self.computePeakThroughputRSS(datapoints)
        if newdatapoint:
            datapoints.append(newdatapoint)

        return datapoints

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        if len(benchmarks) > 1:
            mx.abort("A single benchmark should be specified for {0}.".format(BaseMicroserviceBenchmarkSuite.__name__))
        self.bmSuiteArgs = bmSuiteArgs
        self.benchmark_name = benchmarks[0]
        args, remainder = self.parser.parse_known_args(self.bmSuiteArgs)
        self.workloadPath = args.workload_configuration
        self.measureLatency = not args.skip_latency_measurements
        self.measureFirstResponse = not args.skip_first_response_measurements
        self.measureStartup = not args.skip_startup_measurements
        self.measurePeak = not args.skip_peak_measurements

        if not self.is_native_mode(self.bmSuiteArgs):
            datapoints = []
            if self.measureFirstResponse:
                # Measure time-to-first-response (without any command mapper hooks as those affect the measurement significantly)
                mx.disable_command_mapper_hooks()
                for _ in range(self.NumMeasureTimeToFirstResponse):
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testTimeToFirstResponseInBackground, [self])
                        datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                        measurementThread.join()
                mx.enable_command_mapper_hooks()

            if self.measureStartup:
                # Measure startup performance (without RSS tracker)
                mx_benchmark.disable_tracker()
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testStartupPerformanceInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()
                mx_benchmark.enable_tracker()

            if self.measurePeak:
                # Measure peak performance (with all command mapper hooks)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testPeakPerformanceInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()

            if self.measureLatency:
                if not [c.get("requests-per-second") for c in self.loadConfiguration("latency") if c.get("requests-per-second")]:
                    # Calibrate for latency measurements (without RSS tracker) if no fixed request rate has been provided in the config
                    mx_benchmark.disable_tracker()
                    with PatchEnv(self.get_env()):
                        measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.calibrateLatencyTestInBackground, [self])
                        datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                        measurementThread.join()

                # Measure latency (without RSS tracker)
                with PatchEnv(self.get_env()):
                    measurementThread = self.startDaemonThread(BaseMicroserviceBenchmarkSuite.testLatencyInBackground, [self])
                    datapoints += super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)
                    measurementThread.join()
                mx_benchmark.enable_tracker()

            return datapoints
        else:
            return super(BaseMicroserviceBenchmarkSuite, self).run(benchmarks, remainder)


class NativeImageBundleBasedBenchmarkMixin(object):
    def applicationDist(self):
        raise NotImplementedError()

    def uses_bundles(self):
        raise NotImplementedError()

    def _get_single_file_with_extension_from_dist(self, extension):
        lib = self.applicationDist()
        matching_files = [filename for filename in os.listdir(lib) if filename.endswith(extension)]
        assert len(matching_files) == 1, f"When using bundle support, the benchmark must contain a single file with extension {extension} in its mx library"
        matching_file = os.path.join(lib, matching_files[0])
        return matching_file

    def create_bundle_command_line_args(self, benchmarks, bmSuiteArgs):
        assert self.uses_bundles()
        executable_jar = self._get_single_file_with_extension_from_dist(".jar")
        return self.vmArgs(bmSuiteArgs) + ["-jar", executable_jar]

    def get_bundle_path(self):
        if self.uses_bundles():
            return self._get_single_file_with_extension_from_dist(".nib")


class PatchEnv:
    def __init__(self, env):
        self.env = env

    def __enter__(self):
        self._prev_environ = os.environ
        os.environ = self.env.copy()
        # urllib.request caches http_proxy, https_proxy etc. globally but doesn't cache no_proxy
        # preserve no_proxy to avoid issues with proxies
        if 'no_proxy' in self._prev_environ:
            os.environ['no_proxy'] = self._prev_environ['no_proxy']
        if 'NO_PROXY' in self._prev_environ:
            os.environ['NO_PROXY'] = self._prev_environ['NO_PROXY']

    def __exit__(self, exc_type, exc_val, exc_tb):
        os.environ = self._prev_environ

class BaseJMeterBenchmarkSuite(BaseMicroserviceBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin):
    """Base class for JMeter based benchmark suites."""

    def jmeterVersion(self):
        return '5.3'

    def rules(self, out, benchmarks, bmSuiteArgs):
        # Example of jmeter output (time = 100s):
        #
        # summary +     59 in 00:00:10 =    5.9/s Avg:   449 Min:    68 Max:  7725 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary +   1945 in 00:00:30 =   64.9/s Avg:    46 Min:    26 Max:   116 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   2004 in 00:00:40 =   50.2/s Avg:    57 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +   1906 in 00:00:30 =   63.6/s Avg:    47 Min:    26 Max:    73 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   3910 in 00:01:10 =   55.9/s Avg:    52 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +   1600 in 00:00:30 =   53.3/s Avg:    56 Min:    29 Max:    71 Err:     0 (0.00%) Active: 3 Started: 3 Finished: 0
        # summary =   5510 in 00:01:40 =   55.1/s Avg:    53 Min:    26 Max:  7725 Err:     0 (0.00%)
        # summary +      8 in 00:00:00 =   68.4/s Avg:    46 Min:    30 Max:    58 Err:     0 (0.00%) Active: 0 Started: 3 Finished: 3
        # summary =   5518 in 00:01:40 =   55.1/s Avg:    53 Min:    26 Max:  7725 Err:     0 (0.00%)
        #
        # The following rules matches `^summary \+` and reports the corresponding data points as 'warmup'.
        # Note that the `run()` function calls `addAverageAcrossLatestResults()`, which computes
        # the avg. of the last `AveragingBenchmarkMixin.getExtraIterationCount()` warmup data points
        # and reports that value as 'throughput'.
        pattern = r"^summary \+\s+(?P<requests>[0-9]+) in (?P<hours>\d+):(?P<minutes>\d\d):(?P<seconds>\d\d) =\s+(?P<throughput>\d*[.,]?\d*)/s Avg:\s+(?P<avg>\d+) Min:\s+(?P<min>\d+) Max:\s+(?P<max>\d+) Err:\s+(?P<errors>\d+) \((?P<errpct>\d*[.,]?\d*)\%\)"  # pylint: disable=line-too-long
        return [
            mx_benchmark.StdOutRule(
                pattern,
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
            ),
            mx_benchmark.StdOutRule(
                pattern,
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency",
                    "metric.value": ("<max>", float),
                    "metric.unit": "ms",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int),
                    "warnings": ("<errors>", str),
                }
            )
        ] + super(BaseJMeterBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def testStartupPerformance(self):
        self.startupOutput = ''

    def testPeakPerformance(self, warmup):
        jmeterDirectory = mx.library("APACHE_JMETER_" + self.jmeterVersion(), True).get_path(True)
        jmeterPath = os.path.join(jmeterDirectory, "apache-jmeter-" + self.jmeterVersion(), "bin/ApacheJMeter.jar")
        extraVMArgs = []
        if mx.get_jdk(tag='default').javaCompliance >= '9':
            extraVMArgs += ["--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
                            "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                            "--add-opens=java.base/java.util=ALL-UNNAMED",
                            "--add-opens=java.base/java.text=ALL-UNNAMED"]
        jmeterCmd = [mx.get_jdk(tag='default').java] + extraVMArgs + ["-jar", jmeterPath,
                                                         "-t", self.workloadConfigurationPath(),
                                                         "-n", "-j", "/dev/stdout"] + self.extraJMeterArgs()
        mx.log("Running JMeter: {0}".format(jmeterCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(jmeterCmd, out=output, err=output)
        self.peakOutput = output.underlying.data

    def extraJMeterArgs(self):
        return []

    def calibrateLatencyTest(self):
        pass

    def testLatency(self):
        self.latencyOutput = ''

    def tailDatapointsToSkip(self, results):
        return int(len(results) * .10)

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        results = self.intercept_run(super(), benchmarks, bmSuiteArgs)
        results = results[:len(results) - self.tailDatapointsToSkip(results)]
        self.addAverageAcrossLatestResults(results, "throughput")
        return results

class BaseWrkBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    """Base class for Wrk based benchmark suites."""

    def loadConfiguration(self, groupKey):
        """Returns a json object that describes the Wrk configuration. The following syntax is expected:
        {
          "target-url" : <URL to target, for example "http://localhost:8080">,
          "connections" : <number of connections to keep open>,
          "threads" : <number of threads to use>,
          "throughput" : {
            "script" : <path to lua script to be used>,
            "warmup-requests-per-second" : <requests per second during the warmup run>,
            "warmup-duration" : <duration of the warmup run, for example "30s">,
            "duration" : <duration of the test, for example "30s">,
          },
          "latency" : {
            "script" : [<lua scripts that will be executed sequentially>],
            "warmup-requests-per-second" : [<requests per second during the warmup run (one entry per lua script)>],
            "warmup-duration" : [<duration of the warmup run (one entry per lua script)>],
            "requests-per-second" : [<requests per second during the run> (one entry per lua script)>],
            "duration" : [<duration of the test (one entry per lua script)>]
          }
        }

        All json fields are required.

        :return: Configuration json.
        :rtype: json
        """
        with open(self.workloadConfigurationPath()) as configFile:
            config = json.load(configFile)
            mx.log("Loading configuration file for {0}: {1}".format(BaseWrkBenchmarkSuite.__name__, configFile.name))

            targetUrl = self.readConfig(config, "target-url")
            connections = self.readConfig(config, "connections")
            threads = self.readConfig(config, "threads")

            group = self.readConfig(config, groupKey)
            script = self.readConfig(group, "script")
            warmupRequestsPerSecond = self.readConfig(group, "warmup-requests-per-second")
            warmupDuration = self.readConfig(group, "warmup-duration")
            requestsPerSecond = self.readConfig(group, "requests-per-second", optional=True)
            duration = self.readConfig(group, "duration")

            scalarScriptValue = self.isScalarValue(script)
            if scalarScriptValue != self.isScalarValue(warmupRequestsPerSecond) or scalarScriptValue != self.isScalarValue(warmupDuration) or scalarScriptValue != self.isScalarValue(duration):
                mx.abort("The configuration elements 'script', 'warmup-requests-per-second', 'warmup-duration', and 'duration' must have the same number of elements.")

            results = []
            if scalarScriptValue:
                result = {}
                result["target-url"] = targetUrl
                result["connections"] = connections
                result["threads"] = threads
                result["script"] = script
                result["warmup-requests-per-second"] = warmupRequestsPerSecond
                result["warmup-duration"] = warmupDuration
                result["duration"] = duration
                if requestsPerSecond:
                    result["requests-per-second"] = requestsPerSecond
                results.append(result)
            else:
                count = len(script)
                if count != len(warmupRequestsPerSecond) or count != len(warmupDuration) or count != len(duration):
                    mx.abort("The configuration elements 'script', 'warmup-requests-per-second', 'warmup-duration', and 'duration' must have the same number of elements.")

                for i in range(count):
                    result = {}
                    result["target-url"] = targetUrl
                    result["connections"] = connections
                    result["threads"] = threads
                    result["script"] = script[i]
                    result["warmup-requests-per-second"] = warmupRequestsPerSecond[i]
                    result["warmup-duration"] = warmupDuration[i]
                    result["duration"] = duration[i]
                    if requestsPerSecond:
                        result["requests-per-second"] = requestsPerSecond[i]
                    results.append(result)

            return results

    def readConfig(self, config, key, optional=False):
        if key in config:
            return config[key]
        elif optional:
            return None
        else:
            mx.abort(f"Mandatory entry {key} not specified in Wrk configuration.")

    def isScalarValue(self, value):
        return type(value) in (int, float, bool) or isinstance(value, ("".__class__, u"".__class__)) # pylint: disable=unidiomatic-typecheck

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def testStartupPerformance(self):
        configs = self.loadConfiguration("throughput")
        if len(configs) != 1:
            mx.abort("Expected exactly one lua script in the throughput configuration.")

        # Measure throughput for 15 seconds without warmup.
        config = configs[0]
        wrkFlags = self.getStartupFlags(config)
        output = self.runWrk1(wrkFlags)
        self.startupOutput = self.writeWrk1Results('startup-throughput', 'startup-latency-co', output)

    def testPeakPerformance(self, warmup):
        configs = self.loadConfiguration("throughput")
        if len(configs) != 1:
            mx.abort("Expected exactly one lua script in the throughput configuration.")

        config = configs[0]
        if warmup:
            # Warmup with a fixed number of requests.
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        # Measure peak performance.
        wrkFlags = self.getThroughputFlags(config)
        peakOutput = self.runWrk1(wrkFlags)
        self.peakOutput = self.writeWrk1Results('peak-throughput', 'peak-latency-co', peakOutput)

    def calibrateLatencyTest(self):
        configs = self.loadConfiguration("latency")
        numScripts = len(configs)
        if numScripts < 1:
            mx.abort("Expected at least one lua script in the latency configuration.")

        for i in range(numScripts):
            # Warmup with a fixed number of requests.
            config = configs[i]
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        self.calibratedThroughput = []
        for i in range(numScripts):
            # Measure the maximum throughput.
            config = configs[i]
            wrkFlags = self.getThroughputFlags(config)
            throughputOutput = self.runWrk1(wrkFlags)
            self.calibratedThroughput.append(self.extractThroughput(throughputOutput))

    def testLatency(self):
        configs = self.loadConfiguration("latency")
        numScripts = len(configs)
        if numScripts < 1:
            mx.abort("Expected at least one lua script in the latency configuration.")

        for i in range(numScripts):
            # Warmup with a fixed number of requests.
            config = configs[i]
            wrkFlags = self.getWarmupFlags(config)
            warmupOutput = self.runWrk2(wrkFlags)
            self.verifyWarmup(warmupOutput, config)

        results = []
        for i in range(numScripts):
            # Measure latency using a constant rate (based on the previously measured max throughput).
            config = configs[i]
            if configs[i].get("requests-per-second"):
                expectedRate = configs[i]["requests-per-second"]
                mx.log(f"Using configured fixed throughput {expectedRate} ops/s for latency measurements.")
            else:
                expectedRate = int(self.calibratedThroughput[i] * 0.75)
                mx.log(f"Using dynamically computed throughput {expectedRate} ops/s for latency measurements (75% of max throughput).")
            wrkFlags = self.getLatencyFlags(config, expectedRate)
            constantRateOutput = self.runWrk2(wrkFlags)
            self.verifyThroughput(constantRateOutput, expectedRate)
            results.append(self.extractWrk2Results(constantRateOutput))

        self.latencyOutput = self.writeWrk2Results('throughput-for-peak-latency', 'peak-latency', results)

    def extractThroughput(self, output):
        matches = re.findall(r"^Requests/sec:\s*(\d*[.,]?\d*)\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        return float(matches[0])

    def extractWrk2Results(self, output):
        result = {}
        result["throughput"] = self.extractThroughput(output)

        matches = re.findall(r"^\s*(\d*[.,]?\d*%)\s+(\d*[.,]?\d*)([mun]?s)\s*$", output, re.MULTILINE)
        if len(matches) <= 0:
            mx.abort("No latency results found in output")

        for match in matches:
            val = convertValue(timeUnitTable, float(match[1]), match[2], 'ms')
            result[match[0]] = val

        return result

    def writeWrk2Results(self, throughputPrefix, latencyPrefix, results):
        average = self.computeAverage(results)

        output = []
        for key, value in average.items():
            if key == 'throughput':
                output.append("{} Requests/sec: {:f}".format(throughputPrefix, value))
            else:
                output.append("{} {} {:f}ms".format(latencyPrefix, key, value))

        return '\n'.join(output)

    def computeAverage(self, results):
        count = len(results)
        if count < 1:
            mx.abort("Expected at least one wrk2 result: " + str(count))
        elif count == 1:
            return results[0]

        average = results[0]
        averageKeys = set(average.keys())
        for i in range(1, count):
            result = results[i]
            if averageKeys != set(result.keys()):
                mx.abort("There is a mismatch between the keys of multiple wrk2 runs: " + str(averageKeys) + " vs. " + str(set(result.keys())))

            for key, value in result.items():
                average[key] += result[key]

        for key, value in average.items():
            average[key] = value / count

        return average

    def writeWrk1Results(self, throughputPrefix, latencyPrefix, output):
        result = []
        matches = re.findall(r"^Requests/sec:\s*\d*[.,]?\d*\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        result.append(throughputPrefix + " " + matches[0])

        matches = re.findall(r"^\s*(\d*[.,]?\d*%)\s+(\d*[.,]?\d*)([mun]?s)\s*$", output, re.MULTILINE)
        if len(matches) <= 0:
            mx.abort("No latency results found in output")

        for match in matches:
            val = convertValue(timeUnitTable, float(match[1]), match[2], 'ms')
            result.append(latencyPrefix + " {} {:f}ms".format(match[0], val))

        return '\n'.join(result)

    def verifyWarmup(self, output, config):
        expectedThroughput = float(config['warmup-requests-per-second'])
        self.verifyThroughput(output, expectedThroughput)

    def verifyThroughput(self, output, expectedThroughput):
        matches = re.findall(r"^Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$", output, re.MULTILINE)
        if len(matches) != 1:
            mx.abort("Expected exactly one throughput result in the output: " + str(matches))

        actualThroughput = float(matches[0])
        if actualThroughput < expectedThroughput * 0.97 or actualThroughput > expectedThroughput * 1.03:
            mx.warn("Throughput verification failed: expected requests/s: {:.2f}, actual requests/s: {:.2f}".format(expectedThroughput, actualThroughput))

    def runWrk1(self, wrkFlags):
        distro = self.getOS()
        arch = mx.get_arch()
        wrkDirectory = mx.library('WRK_MULTIARCH', True).get_path(True)
        wrkPath = os.path.join(wrkDirectory, "wrk-{os}-{arch}".format(os=distro, arch=arch))

        if not os.path.exists(wrkPath):
            raise ValueError("Unsupported OS or arch. Binary doesn't exist: {}".format(wrkPath))

        runWrkCmd = [wrkPath] + wrkFlags
        mx.log("Running Wrk: {0}".format(runWrkCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(runWrkCmd, out=output, err=output)
        return output.underlying.data

    def runWrk2(self, wrkFlags):
        distro = self.getOS()
        arch = mx.get_arch()
        wrkDirectory = mx.library('WRK2_MULTIARCH', True).get_path(True)
        wrkPath = os.path.join(wrkDirectory, "wrk-{os}-{arch}".format(os=distro, arch=arch))

        if not os.path.exists(wrkPath):
            raise ValueError("Unsupported OS or arch. Binary doesn't exist: {}".format(wrkPath))

        runWrkCmd = [wrkPath] + wrkFlags
        mx.log("Running Wrk2: {0}".format(runWrkCmd))
        output = mx.TeeOutputCapture(mx.OutputCapture())
        mx.run(runWrkCmd, out=output, err=output)
        return output.underlying.data

    def getStartupFlags(self, config):
        wrkFlags = ['--duration', '15']
        wrkFlags += self.getWrkFlags(config, True)
        return wrkFlags

    def getWarmupFlags(self, config):
        wrkFlags = []
        wrkFlags += ['--duration', str(config['warmup-duration'])]
        wrkFlags += ['--rate', str(config['warmup-requests-per-second'])]
        wrkFlags += self.getWrkFlags(config, False)
        return wrkFlags

    def getThroughputFlags(self, config):
        wrkFlags = []
        wrkFlags += ['--duration', str(config['duration'])]
        wrkFlags += self.getWrkFlags(config, True)
        return wrkFlags

    def getLatencyFlags(self, config, rate):
        wrkFlags = ['--rate', str(rate)]
        wrkFlags += self.getThroughputFlags(config)
        return wrkFlags

    def getWrkFlags(self, config, latency):
        args = []
        if latency:
            args += ['--latency']

        args += ['--connections', str(config['connections'])]
        args += ['--threads', str(config['threads'])]
        args += ['--script', str(self.getScriptPath(config))]
        args.append(str(config['target-url']))
        args += ['--', str(config['threads'])]
        return args

    def getOS(self):
        if mx.get_os() == 'linux':
            return 'linux'
        elif mx.get_os() == 'darwin':
            return 'macos'
        else:
            mx.abort("{0} not supported in {1}.".format(BaseWrkBenchmarkSuite.__name__, mx.get_os()))

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def rules(self, out, benchmarks, bmSuiteArgs):
        # Example of wrk output:
        # "Requests/sec:   5453.61"
        return [
            mx_benchmark.StdOutRule(
                r"^startup-throughput Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "startup-throughput",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-throughput Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-throughput",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^throughput-for-peak-latency Requests/sec:\s*(?P<throughput>\d*[.,]?\d*)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "throughput-for-peak-latency",
                    "metric.value": ("<throughput>", float),
                    "metric.unit": "op/s",
                    "metric.better": "higher",
                }
            ),
            mx_benchmark.StdOutRule(
                r"^startup-latency-co\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "startup-latency-co",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-latency-co\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency-co",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            ),
            mx_benchmark.StdOutRule(
                r"^peak-latency\s+(?P<percentile>\d*[.,]?\d*)%\s+(?P<latency>\d*[.,]?\d*)(?P<unit>ms)\s*$",
                {
                    "benchmark": benchmarks[0],
                    "bench-suite": self.benchSuiteName(),
                    "metric.name": "peak-latency",
                    "metric.value": ("<latency>", float),
                    "metric.unit": ("ms", str),
                    "metric.better": "lower",
                    "metric.percentile": ("<percentile>", float),
                }
            )
        ] + super(BaseWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)


class BaseSpringBenchmarkSuite(BaseMicroserviceBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)

    def get_application_startup_regex(self):
        # Example of SpringBoot 3 startup log:
        # 2023-05-16T14:08:54.033+02:00  INFO 24381 --- [           main] o.s.s.petclinic.PetClinicApplication     : Started PetClinicApplication in 3.774 seconds (process running for 4.1)
        return r"Started [^ ]+ in (?P<appstartup>\d*[.,]?\d*) seconds \(process running for (?P<startup>\d*[.,]?\d*)\)$"

    def get_application_startup_units(self):
        return 's'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']

    def uses_bundles(self):
        return True


class BasePetClinicBenchmarkSuite(BaseSpringBenchmarkSuite):
    def version(self):
        return "3.0.1"

    def applicationDist(self):
        return mx.library("PETCLINIC_" + self.version(), True).get_path(True)


class PetClinicWrkBenchmarkSuite(BasePetClinicBenchmarkSuite, BaseWrkBenchmarkSuite):
    """PetClinic benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "petclinic-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["mixed-tiny", "mixed-small", "mixed-medium", "mixed-large", "mixed-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(PetClinicWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(PetClinicWrkBenchmarkSuite())


class BaseSpringHelloWorldBenchmarkSuite(BaseSpringBenchmarkSuite):
    def version(self):
        return "3.0.6"

    def applicationDist(self):
        return mx.library("SPRING_HW_" + self.version(), True).get_path(True)


class SpringHelloWorldWrkBenchmarkSuite(BaseSpringHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    def name(self):
        return "spring-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def serviceEndpoint(self):
        return 'hello'

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(SpringHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(SpringHelloWorldWrkBenchmarkSuite())


class BaseQuarkusBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    def get_application_startup_regex(self):
        # Example of Quarkus startup log:
        # "2021-03-17 20:03:33,893 INFO  [io.quarkus] (main) tika-quickstart 1.0.0-SNAPSHOT on JVM (powered by Quarkus 1.12.1.Final) started in 1.210s. Listening on: <url>"
        return r"started in (?P<startup>\d*[.,]?\d*)s."

    def get_application_startup_units(self):
        return 's'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BaseQuarkusBundleBenchmarkSuite(BaseQuarkusBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)


class BaseTikaBenchmarkSuite(BaseQuarkusBundleBenchmarkSuite):
    def version(self):
        return "1.0.11"

    def applicationDist(self):
        return mx.library("TIKA_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "tika-quickstart-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'parse'

    def extra_image_build_argument(self, benchmark, args):
        # Older JDK versions would need -H:NativeLinkerOption=libharfbuzz as an extra build argument.
        expectedJdkVersion = mx.VersionSpec("11.0.13")
        if mx.get_jdk().version < expectedJdkVersion:
            mx.abort(benchmark + " needs at least JDK version " + str(expectedJdkVersion))
        tika_build_time_init = [
            "org.apache.pdfbox.rendering.ImageType",
            "org.apache.pdfbox.rendering.ImageType$1",
            "org.apache.pdfbox.rendering.ImageType$2",
            "org.apache.pdfbox.rendering.ImageType$3",
            "org.apache.pdfbox.rendering.ImageType$4",
            "org.apache.xmlbeans.XmlObject",
            "org.apache.xmlbeans.metadata.system.sXMLCONFIG.TypeSystemHolder",
            "org.apache.xmlbeans.metadata.system.sXMLLANG.TypeSystemHolder",
            "org.apache.xmlbeans.metadata.system.sXMLSCHEMA.TypeSystemHolder"
        ]
        return [
            f"--initialize-at-build-time={','.join(tika_build_time_init)}",
        ] + super(BaseTikaBenchmarkSuite, self).extra_image_build_argument(benchmark, args)


class TikaWrkBenchmarkSuite(BaseTikaBenchmarkSuite, BaseWrkBenchmarkSuite):
    """Tika benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "tika-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["odt-tiny", "odt-small", "odt-medium", "odt-large", "odt-huge", "pdf-tiny", "pdf-small", "pdf-medium", "pdf-large", "pdf-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(TikaWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(TikaWrkBenchmarkSuite())


class BaseQuarkusHelloWorldBenchmarkSuite(BaseQuarkusBundleBenchmarkSuite):
    def version(self):
        return "1.0.6"

    def applicationDist(self):
        return mx.library("QUARKUS_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "quarkus-hello-world-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'hello'


class QuarkusHelloWorldWrkBenchmarkSuite(BaseQuarkusHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    """Quarkus benchmark suite that measures latency using Wrk2."""

    def name(self):
        return "quarkus-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(QuarkusHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(QuarkusHelloWorldWrkBenchmarkSuite())


class BaseMicronautBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    def get_application_startup_regex(self):
        # Example of Micronaut startup log (there can be some formatting in between):
        # "[main] INFO io.micronaut.runtime.Micronaut - Startup completed in 328ms. Server Running: <url>"
        return r"^.*\[main\].*INFO.*io.micronaut.runtime.Micronaut.*- Startup completed in (?P<startup>\d+)ms."

    def get_application_startup_units(self):
        return 'ms'

    def get_image_env(self):
        # Disable experimental option checking.
        return {**os.environ, "NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL": "false"}

    def build_assertions(self, benchmark, is_gate):
        # This method overrides NativeImageMixin.build_assertions
        return []  # We are skipping build assertions due to some failed asserts while building Micronaut apps.

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BaseMicronautBundleBenchmarkSuite(BaseMicronautBenchmarkSuite, NativeImageBundleBasedBenchmarkMixin):
    def uses_bundles(self):
        return True

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.create_bundle_command_line_args(benchmarks, bmSuiteArgs)


class BaseQuarkusRegistryBenchmark(BaseQuarkusBenchmarkSuite, BaseMicroserviceBenchmarkSuite):
    """
    This benchmark is used to measure the precision and performance of the static analysis in Native Image,
    so there is no runtime load, that's why the default stage is just image.
    """

    def version(self):
        return "0.0.2"

    def name(self):
        return "quarkus"

    def benchmarkList(self, bmSuiteArgs):
        return ["registry"]

    def default_stages(self):
        return ['image']

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            benchmark = benchmarks[0]
        return self.vmArgs(bmSuiteArgs) + ["-jar",  os.path.join(self.applicationDist(), benchmark + ".jar")]

    def applicationDist(self):
        return mx.library("QUARKUS_REGISTRY_" + self.version(), True).get_path(True)

    def extra_image_build_argument(self, benchmark, args):
        quarkus_registry_features = [
            "io.quarkus.jdbc.postgresql.runtime.graal.SQLXMLFeature",
            "org.hibernate.graalvm.internal.GraalVMStaticFeature",
            "io.quarkus.hibernate.validator.runtime.DisableLoggingFeature",
            "io.quarkus.hibernate.orm.runtime.graal.DisableLoggingFeature",
            "io.quarkus.runner.Feature",
            "io.quarkus.runtime.graal.DisableLoggingFeature",
            "io.quarkus.caffeine.runtime.graal.CacheConstructorsFeature"
        ]
        return ['-J-Dlogging.initial-configurator.min-level=500',
                '-J-Dio.quarkus.caffeine.graalvm.recordStats=true',
                '-J-Djava.util.logging.manager=org.jboss.logmanager.LogManager',
                '-J-Dsun.nio.ch.maxUpdateArraySize=100',
                '-J-DCoordinatorEnvironmentBean.transactionStatusManagerEnable=false',
                '-J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory',
                '-J-Dvertx.disableDnsResolver=true',
                '-J-Dio.netty.leakDetection.level=DISABLED',
                '-J-Dio.netty.allocator.maxOrder=3',
                '-J-Duser.language=en',
                '-J-Duser.country=GB',
                '-J-Dfile.encoding=UTF-8',
                f"--features={','.join(quarkus_registry_features)}",
                '-J--add-exports=java.security.jgss/sun.security.krb5=ALL-UNNAMED',
                '-J--add-exports=org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED',
                '-J--add-opens=java.base/java.text=ALL-UNNAMED',
                '-J--add-opens=java.base/java.io=ALL-UNNAMED',
                '-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
                '-J--add-opens=java.base/java.util=ALL-UNNAMED',
                '-H:+AllowFoldMethods',
                '-J-Djava.awt.headless=true',
                '--no-fallback',
                '--link-at-build-time',
                '-H:+ReportExceptionStackTraces',
                '-H:-AddAllCharsets',
                '--enable-url-protocols=http,https',
                '-H:-UseServiceLoaderFeature',
                '--exclude-config',
                r'io\.netty\.netty-codec',
                r'/META-INF/native-image/io\.netty/netty-codec/generated/handlers/reflect-config\.json',
                '--exclude-config',
                r'io\.netty\.netty-handler',
                r'/META-INF/native-image/io\.netty/netty-handler/generated/handlers/reflect-config\.json',
                ] + super(BaseQuarkusBenchmarkSuite, self).extra_image_build_argument(benchmark, args)  # pylint: disable=bad-super-call

mx_benchmark.add_bm_suite(BaseQuarkusRegistryBenchmark())

_mushopConfig = {
    'order': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder,java.sql.DriverInfo,kotlin.coroutines.intrinsics.CoroutineSingletons'],
    'user': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder,java.sql.DriverInfo'],
    'payment': ['--initialize-at-build-time=io.netty.handler.codec.http.cookie.ServerCookieEncoder']
}

class BaseMicronautMuShopBenchmark(BaseMicronautBenchmarkSuite, BaseMicroserviceBenchmarkSuite):
    """
    This benchmark suite is used to measure the precision and performance of the static analysis in Native Image,
    so there is no runtime load, that's why the default stage is just image.
    """

    def version(self):
        return "0.0.2"

    def name(self):
        return "mushop"

    def benchmarkList(self, bmSuiteArgs):
        return ["user", "order", "payment"]

    def default_stages(self):
        return ['image']

    def run(self, benchmarks, bmSuiteArgs):
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            benchmark = benchmarks[0]
        return self.vmArgs(bmSuiteArgs) + ["-jar",  os.path.join(self.applicationDist(), benchmark + ".jar")]

    def applicationDist(self):
        return mx.library("MICRONAUT_MUSHOP_" + self.version(), True).get_path(True)

    def extra_image_build_argument(self, benchmark, args):
        return ([
                    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED',
                    '--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.configure=ALL-UNNAMED',
                    '--add-exports=org.graalvm.nativeimage/org.graalvm.nativeimage.impl=ALL-UNNAMED']
                + _mushopConfig[benchmark] + super(BaseMicronautBenchmarkSuite, self).extra_image_build_argument(benchmark, args))  # pylint: disable=bad-super-call

mx_benchmark.add_bm_suite(BaseMicronautMuShopBenchmark())


class BaseShopCartBenchmarkSuite(BaseMicronautBundleBenchmarkSuite):
    def version(self):
        return "0.3.10"

    def applicationDist(self):
        return mx.library("SHOPCART_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "shopcart-" + self.version() + ".jar")

    def serviceEndpoint(self):
        return 'clients'


class ShopCartWrkBenchmarkSuite(BaseShopCartBenchmarkSuite, BaseWrkBenchmarkSuite):
    """ShopCart benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "shopcart-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["mixed-tiny", "mixed-small", "mixed-medium", "mixed-large", "mixed-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(ShopCartWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(ShopCartWrkBenchmarkSuite())


class BaseMicronautHelloWorldBenchmarkSuite(BaseMicronautBundleBenchmarkSuite):
    def version(self):
        return "1.0.7"

    def applicationDist(self):
        return mx.library("MICRONAUT_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "micronaut-hello-world-" + self.version() + ".jar")

    def serviceEndpoint(self):
        return 'hello'


class MicronautHelloWorldWrkBenchmarkSuite(BaseMicronautHelloWorldBenchmarkSuite, BaseWrkBenchmarkSuite):
    def name(self):
        return "micronaut-helloworld-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["helloworld"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".wrk")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(MicronautHelloWorldWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

    def getScriptPath(self, config):
        return os.path.join(self.applicationDist(), "workloads", config["script"])


mx_benchmark.add_bm_suite(MicronautHelloWorldWrkBenchmarkSuite())
