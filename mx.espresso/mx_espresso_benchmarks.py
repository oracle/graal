#
# Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import argparse
import re

import mx
import mx_benchmark
import mx_espresso

from mx_benchmark import GuestVm, JavaVm

_suite = mx.suite('espresso')


class EspressoVm(GuestVm, JavaVm):
    def __init__(self, config_name, options, host_vm=None):
        super(EspressoVm, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._options = options

    def name(self):
        return 'espresso'

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def with_host_vm(self, host_vm):
        return self.__class__(self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        args += self._options
        if hasattr(self.host_vm(), 'run_launcher'):
            return self.host_vm().run_launcher('espresso', args + self._options, cwd)
        else:
            return self.host_vm().run(cwd, mx_espresso._espresso_standalone_command(args))


# Benchmark parameter from AWFY rebench.conf
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
        elif len(benchmarks) == 0:
            mx.abort("Must specify at least one benchmark.")
        else:
            benchArg = ",".join(benchmarks)
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
                    "metric.name": "final-time",
                    "metric.value": ("<runtime>", float),
                    "metric.unit": "us",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                }
            ),
            mx_benchmark.StdOutRule(
                r"(?P<benchmark>[a-zA-Z0-9_\-]+): iterations=(?P<iterations>[0-9]+) average: (?P<average>[0-9]+)us total: (?P<total>[0-9]+)us",
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),                    
                    "config.vm-flags": ' '.join(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "total-time",
                    "metric.value": ("<total>", float),
                    "metric.unit": "us",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0
                }
            )
        ]

    def run(self, benchmarks, bmSuiteArgs):
        results = super(AWFYBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.addAverageAcrossLatestResults(results)
        return results


mx_benchmark.add_bm_suite(AWFYBenchmarkSuite())


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


mx.update_commands(_suite, {
    'awfy': [
      lambda args: createBenchmarkShortcut("awfy", args),
      '[<benchmarks>|*] [-- [VM options] ] [-- [AWFY options] ]]'
    ],
})

