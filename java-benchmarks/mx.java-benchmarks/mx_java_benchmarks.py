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
import mx_sdk_benchmark


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
mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm('java-home', 'default'), _suite, 1)


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
        mx.log_deprecation("mx_java_benchmarks.mx_benchmark.TemporaryWorkdirMixin is deprecated. Use mx_benchmark.mx_benchmark.TemporaryWorkdirMixin instead.")
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


class BaseMicroserviceBenchmarkSuite(object):
    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def version(self):
        raise NotImplementedError()

    def validateReturnCode(self, retcode):
        return retcode == 143

    def applicationDist(self):
        raise NotImplementedError()

    def applicationPath(self):
        raise NotImplementedError()

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

    def get_application_startup_regex(self):
        raise NotImplementedError()

    def get_application_startup_units(self):
        raise NotImplementedError

    def skip_agent_assertions(self, benchmark, args):
        # This method overrides NativeImageMixin.skip_agent_assertions
        user_args = super(BaseMicroserviceBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def stages(self, args):
        # This method overrides NativeImageMixin.stages
        parsed_arg = mx_sdk_benchmark.parse_prefixed_arg('-Dnative-image.benchmark.stages=', args, 'Native Image benchmark stages should only be specified once.')
        return parsed_arg.split(',') if parsed_arg else self.default_stages()

    def default_stages(self):
        raise NotImplementedError()


class BaseSpringBenchmarkSuite(BaseMicroserviceBenchmarkSuite):
    def mainClass(self):
        raise NotImplementedError()

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        lib = self.applicationDist()
        classpath = os.path.join(lib, "BOOT-INF/classes")
        for filename in os.listdir(os.path.join(lib, "BOOT-INF/lib")):
            if filename.endswith(".jar"):
                classpath = classpath + ":" + os.path.join(lib, "BOOT-INF/lib", filename)
        mainclass = self.mainClass()
        return self.vmArgs(bmSuiteArgs) + ["-cp", classpath, mainclass]

    def get_application_startup_regex(self):
        # Example of SpringBoot startup log:
        # "2021-03-08 15:49:36.155  INFO 21174 --- [           main] o.s.s.petclinic.PetClinicApplication     : Started PetClinicApplication in 4.367 seconds (JVM running for 4.812)"
        return r"Started [^ ]+ in (?P<appstartup>\d*[.,]?\d*) seconds \(JVM running for (?P<startup>\d*[.,]?\d*)\)$"

    def get_application_startup_units(self):
        return 's'

    def default_stages(self):
        # This method overrides NativeImageMixin.stages
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BasePetClinicBenchmarkSuite(BaseSpringBenchmarkSuite):
    def version(self):
        return "0.1.6"

    def applicationDist(self):
        return mx.library("PETCLINIC_" + self.version(), True).get_path(True)

    def mainClass(self):
        return "org.springframework.samples.petclinic.PetClinicApplication"


class PetClinicJMeterBenchmarkSuite(BasePetClinicBenchmarkSuite, mx_sdk_benchmark.BaseJMeterBenchmarkSuite):
    """PetClinic benchmark suite that measures throughput using JMeter."""

    def name(self):
        return "petclinic-jmeter"

    def benchmarkList(self, bmSuiteArgs):
        return ["tiny"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".jmx")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(PetClinicJMeterBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(PetClinicJMeterBenchmarkSuite())


class PetClinicWrkBenchmarkSuite(BasePetClinicBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
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
        return "1.0.1"

    def applicationDist(self):
        return mx.library("SPRING_HW_" + self.version(), True).get_path(True)

    def mainClass(self):
        return "com.example.webmvc.WebmvcApplication"


class SpringHelloWorldWrkBenchmarkSuite(BaseSpringHelloWorldBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
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

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']

    def extra_image_build_argument(self, benchmark, args):
        return ['-J-Djava.util.logging.manager=org.jboss.logmanager.LogManager',
                '-J-Dsun.nio.ch.maxUpdateArraySize=100',
                '-J-Dvertx.logger-delegate-factory-class-name=io.quarkus.vertx.core.runtime.VertxLogDelegateFactory',
                '-J-Dvertx.disableDnsResolver=true,'
                '-J-Dio.netty.leakDetection.level=DISABLED',
                '-J-Dio.netty.allocator.maxOrder=1',
                '-J-Duser.language=en',
                '-J-Duser.country=US',
                '-J-Dfile.encoding=UTF-8',
                '--initialize-at-build-time=',
                '-H:+JNI',
                '-H:+AllowFoldMethods',
                '-H:FallbackThreshold=0',
                '-H:+ReportExceptionStackTraces',
                '-H:-AddAllCharsets',
                '-H:EnableURLProtocols=http',
                '-H:NativeLinkerOption=-no-pie',
                '-H:-UseServiceLoaderFeature',
                '-H:+StackTrace'] + super(BaseQuarkusBenchmarkSuite, self).extra_image_build_argument(benchmark, args)


class BaseTikaBenchmarkSuite(BaseQuarkusBenchmarkSuite):
    def version(self):
        return "1.0.6"

    def applicationDist(self):
        return mx.library("TIKA_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "tika-quickstart-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'parse'


class TikaWrkBenchmarkSuite(BaseTikaBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
    """Tika benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "tika-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["odt-tiny", "odt-small", "odt-medium", "odt-large", "odt-huge", "pdf-tiny", "pdf-small", "pdf-medium", "pdf-large", "pdf-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(TikaWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(TikaWrkBenchmarkSuite())


class BaseQuarkusHelloWorldBenchmarkSuite(BaseQuarkusBenchmarkSuite):
    def version(self):
        return "1.0.1"

    def applicationDist(self):
        return mx.library("QUARKUS_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "quarkus-hello-world-" + self.version() + "-runner.jar")

    def serviceEndpoint(self):
        return 'hello'


class QuarkusHelloWorldWrkBenchmarkSuite(BaseQuarkusHelloWorldBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
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

    def skip_build_assertions(self, benchmark):
        # This method overrides NativeImageMixin.skip_build_assertions
        return True  # We are skipping build assertions due to some failed asserts while building Micronaut apps.

    def default_stages(self):
        return ['instrument-image', 'instrument-run', 'image', 'run']


class BaseShopCartBenchmarkSuite(BaseMicronautBenchmarkSuite):
    def version(self):
        return "0.3.5"

    def applicationDist(self):
        shopcartCache = mx.library("SHOPCART_" + self.version(), True).get_path(True)
        return os.path.join(shopcartCache, "shopcart-" + self.version())

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "shopcart-" + self.version() + "-all.jar")

    def serviceEndpoint(self):
        return 'clients'


class ShopCartJMeterBenchmarkSuite(BaseShopCartBenchmarkSuite, mx_sdk_benchmark.BaseJMeterBenchmarkSuite):
    """ShopCart benchmark suite that measures throughput using JMeter."""

    def name(self):
        return "shopcart-jmeter"

    def benchmarkList(self, bmSuiteArgs):
        return ["tiny", "small", "large"]

    def defaultWorkloadPath(self, benchmark):
        return os.path.join(self.applicationDist(), "workloads", benchmark + ".jmx")

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(ShopCartJMeterBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)


mx_benchmark.add_bm_suite(ShopCartJMeterBenchmarkSuite())


class ShopCartWrkBenchmarkSuite(BaseShopCartBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
    """ShopCart benchmark suite that measures throughput using Wrk."""

    def name(self):
        return "shopcart-wrk"

    def benchmarkList(self, bmSuiteArgs):
        return ["mixed-tiny", "mixed-small", "mixed-medium", "mixed-large", "mixed-huge"]

    def rules(self, out, benchmarks, bmSuiteArgs):
        return self.applicationStartupRule(self.benchSuiteName(), benchmarks[0]) + super(ShopCartWrkBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)

mx_benchmark.add_bm_suite(ShopCartWrkBenchmarkSuite())


class BaseMicronautHelloWorldBenchmarkSuite(BaseMicronautBenchmarkSuite):
    def version(self):
        return "1.0.2"

    def applicationDist(self):
        return mx.library("MICRONAUT_HW_" + self.version(), True).get_path(True)

    def applicationPath(self):
        return os.path.join(self.applicationDist(), "micronaut-hello-world-" + self.version() + ".jar")

    def serviceEndpoint(self):
        return 'hello'


class MicronautHelloWorldWrkBenchmarkSuite(BaseMicronautHelloWorldBenchmarkSuite, mx_sdk_benchmark.BaseWrkBenchmarkSuite):
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
        parser.add_argument("-n", default=None)
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

        if args.n:
            if args.n.isdigit():
                return ["-n", args.n] + otherArgs
            if args.n == "-1":
                return None
        else:
            iterations = self.daCapoIterations()[benchname]
            if iterations == -1:
                return None
            else:
                iterations = iterations + self.getExtraIterationCount(iterations)
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
        missing_sizes = set(self.daCapoIterations().keys()).difference(set(self.daCapoSizes().keys()))
        if len(missing_sizes) > 0:
            mx.abort("Missing size definitions for benchmark(s): {}".format(missing_sizes))
        return [b for b, it in self.daCapoIterations().items()
                if self.workloadSize() in self.daCapoSizes().get(b, []) and it != -1]

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
    "tradebeans"  : 20,
    "tradesoap"   : 20,
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
        return "9.12-MR1-bach"

    def availableSuiteVersions(self):
        return ["9.12-bach", "9.12-MR1-bach"]

    def workloadSize(self):
        return "default"

    def daCapoSuiteTitle(self):
        title = None
        if self.version() == "9.12-bach":
            title = "DaCapo 9.12"
        elif self.version() == "9.12-MR1-bach":
            title = "DaCapo 9.12-MR1"
        return title

    def daCapoClasspathEnvVarName(self):
        return "DACAPO_CP"

    def daCapoLibraryName(self):
        if self.version() == "9.12-bach":  # 2009 release
            return "DACAPO"
        elif self.version() == "9.12-MR1-bach":  # 2018 maintenance release
            return "DACAPO_MR1_BACH"
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

        if self.version() in ["9.12-bach", "9.12-MR1-bach"]:
            if mx.get_jdk().javaCompliance >= '9':
                if "batik" in iterations:
                    # batik crashes on JDK9+. This is fixed in the dacapo chopin release only
                    del iterations["batik"]
                if "tradesoap" in iterations:
                    # validation fails transiently but frequently in the first iteration in JDK9+
                    del iterations["tradesoap"]
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
            vmArgs += ["--add-opens", "java.base/java.lang=ALL-UNNAMED"]
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

    def daCapoSuiteTitle(self):
        return "DaCapo 0.1.0-SNAPSHOT"

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
        skip_patterns = super(ScalaDaCapoBenchmarkSuite, self).flakySuccessPatterns()
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
            benchmarks = self.benchmarkList(bmSuiteArgs)

        vmArgs = self.vmArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)

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
            runArgs += ["-pja", "-Dspecjvm.run.initial.check=false"]
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
        return _get_specjbb_vmArgs(mx.get_jdk().javaCompliance) + vmArgs

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
        return _get_specjbb_vmArgs(mx.get_jdk().javaCompliance) + vmArgs

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
        return _get_specjbb_vmArgs(mx.get_jdk().javaCompliance) + vmArgs

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
        if self.version() == "0.9.0":
            # benchmark was introduced in 0.10.0
            del benchmarks["scala-doku"]

        if mx.get_jdk().javaCompliance >= '17' and self.version() in ["0.9.0", "0.10.0", "0.11.0", "0.12.0"]:
            # JDK17 support for Spark benchmarks was added in 0.13.0
            # See: renaissance-benchmarks/renaissance #295
            del benchmarks["als"]
            del benchmarks["chi-square"]
            del benchmarks["dec-tree"]
            del benchmarks["gauss-mix"]
            del benchmarks["log-regression"]
            del benchmarks["movie-lens"]
            del benchmarks["naive-bayes"]
            del benchmarks["page-rank"]

        if mx.get_arch() != "amd64" or mx.get_jdk().javaCompliance > '11':
            # GR-33879
            # JNA libraries needed are currently limited to amd64: renaissance-benchmarks/renaissance #153
            del benchmarks["db-shootout"]

        if self.version() in ["0.9.0", "0.10.0", "0.11.0"]:
            if mx.get_jdk().javaCompliance > '11':
                del benchmarks["neo4j-analytics"]
        else:
            if mx.get_jdk().javaCompliance < '11' or mx.get_jdk().javaCompliance > '15':
                del benchmarks["neo4j-analytics"]
        return benchmarks

    def completeBenchmarkList(self, bmSuiteArgs):
        return sorted([bench for bench in _renaissanceConfig.keys()])

    def defaultSuiteVersion(self):
        #  return self.availableSuiteVersions()[-1]
        return "0.11.0"  # stick to 0.11.0 for both JIT and AOT until Native Image is compatible with 0.13.0 (GR-34147)

    def availableSuiteVersions(self):
        return ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]

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

    def vmArgs(self, bmSuiteArgs):
        vm_args = super(RenaissanceBenchmarkSuite, self).vmArgs(bmSuiteArgs)
        # The --add-opens flag will be available in the next Renaissance release (> 0.13.0).
        if java_home_jdk().javaCompliance > '16' and self.version() in ["0.9.0", "0.10.0", "0.11.0", "0.12.0",
                                                                        "0.13.0"]:
            vm_args += ["--add-opens", "java.management/sun.management=ALL-UNNAMED"]
            vm_args += ["--add-opens", "java.management/sun.management.counter=ALL-UNNAMED"]
            vm_args += ["--add-opens", "java.management/sun.management.counter.perf=ALL-UNNAMED"]
        return vm_args

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

    def run(self, benchmarks, bmSuiteArgs):
        results = super(RenaissanceBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(RenaissanceBenchmarkSuite())


class RenaissanceLegacyBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
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


class SparkSqlPerfBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite, mx_benchmark.AveragingBenchmarkMixin, mx_benchmark.TemporaryWorkdirMixin):
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

    def run(self, benchmarks, bmSuiteArgs):
        results = super(AWFYBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(AWFYBenchmarkSuite())


_consoleConfig = {
    "helloworld": {
        "mainClass": "bench.console.HelloWorld",
        "args": []
    },
    "scalafmt": {
        "mainClass": "bench.console.Scalafmt",
        "args": []
    }
}

class ConsoleBenchmarkSuite(mx_benchmark.JavaBenchmarkSuite):
    """Hello World benchmark suite implementation.
    """
    def name(self):
        return "console"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graal-compiler"

    def benchSuiteName(self, bmSuiteArgs=None):
        return self.name()

    def helloWorldPath(self):
        helloWorld = mx.distribution("GRAAL_BENCH_CONSOLE")
        if helloWorld:
            return helloWorld.path
        return None

    def classpathAndMainClass(self, benchmark):
        main_class = _consoleConfig.get(benchmark)["mainClass"]
        return ["-cp", self.helloWorldPath(), main_class]

    def appArgs(self, benchmark):
        return _consoleConfig.get(benchmark)["args"]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark to run.")
        elif benchmarks[0] not in self.benchmarkList(bmSuiteArgs):
            mx.abort("The specified benchmark doesn't exist. Possible values are: " + ", ".join(self.benchmarkList(bmSuiteArgs)))
        vmArgs = self.runArgs(bmSuiteArgs)
        runArgs = self.runArgs(bmSuiteArgs)
        appArgs = self.appArgs(benchmarks[0])
        return vmArgs + self.classpathAndMainClass(benchmarks[0]) + runArgs + appArgs

    def benchmarkList(self, bmSuiteArgs):
        return sorted(_consoleConfig.keys())

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return [
            re.compile(
                r"^\[\[\[Graal compilation failure\]\]\]", # pylint: disable=line-too-long
                re.MULTILINE)
        ]

    def rules(self, output, benchmarks, bmSuiteArgs):
        return []

mx_benchmark.add_bm_suite(ConsoleBenchmarkSuite())
