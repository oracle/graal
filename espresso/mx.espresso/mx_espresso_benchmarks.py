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

import mx
import mx_benchmark
import mx_espresso

from mx_benchmark import GuestVm, JavaVm
from mx_java_benchmarks import ScalaDaCapoBenchmarkSuite
from mx_java_benchmarks import _daCapoScalaConfig


_suite = mx.suite('espresso')


def espresso_dimensions(guest_vm):
    """
    :type guest_vm: GuestVm
    :rtype: dict[str, str]
    """
    host_vm_name = guest_vm.host_vm().name()
    if '-ce-' in host_vm_name:
        edition = 'CE'
    elif '-ee-' in host_vm_name:
        edition = 'EE'
    else:
        edition = 'unknown'
    return {'platform.graalvm-edition': edition}


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
        _host_vm = host_vm
        if hasattr(host_vm, 'run_launcher'):
            if mx.suite('vm', fatalIfMissing=False):
                import mx_vm_benchmark
                # If needed, clone the host_vm and replace the `--native` argument with `-truffle`
                if isinstance(host_vm, mx_vm_benchmark.GraalVm) and '--native' in host_vm.extra_launcher_args:
                    extra_launcher_args = list(host_vm.extra_launcher_args)
                    extra_launcher_args.remove('--native')
                    extra_launcher_args.append('-truffle')
                    _host_vm = mx_vm_benchmark.GraalVm(host_vm.name(), host_vm.config_name(), list(host_vm.extra_java_args), extra_launcher_args)
        return self.__class__(self.config_name(), self._options, _host_vm)

    def run(self, cwd, args):
        if hasattr(self.host_vm(), 'run_launcher'):
            if '-truffle' in self.host_vm().extra_launcher_args:
                code, out, dims = self.host_vm().run_launcher('java', self._options + args, cwd)
            else:
                # The host-vm is in JVM mode. Run the `espresso` launcher.
                code, out, dims = self.host_vm().run_launcher('espresso', self._options + args, cwd)
        else:
            code, out, dims = self.host_vm().run(cwd, mx_espresso._espresso_standalone_command(self._options + args))
        dims.update(espresso_dimensions(self))
        return code, out, dims


class EspressoMinHeapVm(EspressoVm):
    # Runs benchmarks multiple times until it finds the minimum size of max heap (`-Xmx`) required to complete the execution within a given overhead factor.
    # The minimum heap size is stored in an extra dimension.
    def __init__(self, ovh_factor, min_heap, max_heap, config_name, options, host_vm=None):
        super(EspressoMinHeapVm, self).__init__(config_name=config_name, options=options, host_vm=host_vm)
        self.ovh_factor = ovh_factor
        self.min_heap = min_heap
        self.max_heap = max_heap

    def name(self):
        return super(EspressoMinHeapVm, self).name() + '-minheap'

    def with_host_vm(self, host_vm):
        return self.__class__(self.ovh_factor, self.min_heap, self.max_heap, self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        class PTimeout(object):
            def __init__(self, ptimeout):
                self.ptimeout = ptimeout

            def __enter__(self):
                self.prev_ptimeout = mx.get_opts().ptimeout
                mx.get_opts().ptimeout = self.ptimeout
                return self

            def __exit__(self, exc_type, exc_value, traceback):
                mx.get_opts().ptimeout = self.prev_ptimeout

        run_info = {}
        def run_with_heap(heap, args, timeout, suppressStderr=True, nonZeroIsFatal=False):
            mx.log('Trying with %sMB of heap...' % heap)
            with PTimeout(timeout):
                if hasattr(self.host_vm(), 'run_launcher'):
                    _args = self._options + ['--jvm.Xmx{}M'.format(heap)] + args
                    _exit_code, stdout, dims = self.host_vm().run_launcher('espresso', _args, cwd)
                else:
                    _args = ['-Xmx{}M'.format(heap)] + mx_espresso._espresso_standalone_command(self._options + args)
                    _exit_code, stdout, dims = self.host_vm().run(cwd, _args)
                if _exit_code:
                    mx.log('failed')
                else:
                    mx.log('succeeded')
                    run_info['stdout'] = stdout
                    run_info['dims'] = dims
            return _exit_code

        exit_code = mx.run_java_min_heap(args=args, benchName='# MinHeap', overheadFactor=self.ovh_factor, minHeap=self.min_heap, maxHeap=self.max_heap, cwd=cwd, run_with_heap=run_with_heap)
        return exit_code, run_info['stdout'], run_info['dims']


# Register soon-to-become-default configurations.
mx_benchmark.java_vm_registry.add_vm(EspressoVm('default', []), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('interpreter', ['--experimental-options', '--engine.Compilation=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('interpreter-inline-accessors', ['--experimental-options', '--engine.Compilation=false', '--java.InlineFieldAccessors']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('inline-accessors', ['--experimental-options', '--java.InlineFieldAccessors']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('single-tier', ['--experimental-options', '--engine.MultiTier=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('multi-tier', ['--experimental-options', '--engine.MultiTier=true']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('3-compiler-threads', ['--experimental-options', '--engine.CompilerThreads=3']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('multi-tier-inline-accessors', ['--experimental-options', '--engine.MultiTier', '--java.InlineFieldAccessors']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('no-inlining', ['--experimental-options', '--engine.Inlining=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('safe', ['--experimental-options', '--engine.RelaxStaticObjectSafetyChecks=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('field-based', ['--experimental-options', '--engine.StaticObjectStorageStrategy=field-based']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('field-based-safe', ['--experimental-options', '--engine.StaticObjectStorageStrategy=field-based', '--engine.RelaxStaticObjectSafetyChecks=false']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('array-based', ['--experimental-options', '--engine.StaticObjectStorageStrategy=array-based']), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoVm('array-based-safe', ['--experimental-options', '--engine.StaticObjectStorageStrategy=array-based', '--engine.RelaxStaticObjectSafetyChecks=false']), _suite)

mx_benchmark.java_vm_registry.add_vm(EspressoMinHeapVm(0, 0, 64, 'infinite-overhead', []), _suite)
mx_benchmark.java_vm_registry.add_vm(EspressoMinHeapVm(1.5, 0, 2048, '1.5-overhead', []), _suite)


def warmupIterations(startup=None, earlyWarmup=None, lateWarmup=None):
    result = dict()
    if startup is not None:
        result["startup"] = startup
    if earlyWarmup is not None:
        assert startup and startup < earlyWarmup
        result["early-warmup"] = earlyWarmup
    if lateWarmup is not None:
        assert earlyWarmup and earlyWarmup < lateWarmup
        result["late-warmup"] = lateWarmup
    return result

scala_dacapo_warmup_iterations = {
    "actors"      : warmupIterations(1, 1 + _daCapoScalaConfig["actors"]      // 3, _daCapoScalaConfig["actors"]),
    "apparat"     : warmupIterations(1, 1 + _daCapoScalaConfig["apparat"]     // 3, _daCapoScalaConfig["apparat"]),
    "factorie"    : warmupIterations(1, 1 + _daCapoScalaConfig["factorie"]    // 3, _daCapoScalaConfig["factorie"]),
    "kiama"       : warmupIterations(1, 1 + _daCapoScalaConfig["kiama"]       // 3, _daCapoScalaConfig["kiama"]),
    "scalac"      : warmupIterations(1, 3, 15),
    "scaladoc"    : warmupIterations(1, 4, 15),
    "scalap"      : warmupIterations(1, 3, 40),
    "scalariform" : warmupIterations(1, 1 + _daCapoScalaConfig["scalariform"] // 3, _daCapoScalaConfig["scalariform"]),
    "scalatest"   : warmupIterations(1, 1 + _daCapoScalaConfig["scalatest"]   // 3, _daCapoScalaConfig["scalatest"]),
    "scalaxb"     : warmupIterations(1, 3, 20),
    "specs"       : warmupIterations(1, 1 + _daCapoScalaConfig["specs"]       // 3, _daCapoScalaConfig["specs"]),
    "tmt"         : warmupIterations(1, 1 + _daCapoScalaConfig["tmt"]         // 3, _daCapoScalaConfig["tmt"]),
}

class ScalaDaCapoWarmupBenchmarkSuite(ScalaDaCapoBenchmarkSuite): #pylint: disable=too-many-ancestors
    """Scala DaCapo (warmup) benchmark suite implementation."""

    def daCapoPath(self):
        return mx.distribution("DACAPO_SCALA_WARMUP").path

    def name(self):
        return "scala-dacapo-warmup"

    def warmupResults(self, results, warmupIterations, metricName="cumulative-warmup"):
        """
        Postprocess results to add new entries for specific iterations.
        """
        benchmarkNames = {r["benchmark"] for r in results}
        for benchmark in benchmarkNames:
            if benchmark in warmupIterations:
                entries = [result for result in results if result["metric.name"] == metricName and result["benchmark"] == benchmark]
                if entries:
                    for entry in entries:
                        for key, iteration in warmupIterations[benchmark].items():
                            if entry["metric.iteration"] == iteration - 1: # scala_dacapo_warmup_iterations is 1-based, JSON output is 0-based
                                newEntry = entry.copy()
                                newEntry["metric.name"] = key
                                results.append(newEntry)

    def rules(self, out, benchmarks, bmSuiteArgs):
        super_rules = super(ScalaDaCapoWarmupBenchmarkSuite, self).rules(out, benchmarks, bmSuiteArgs)
        return super_rules + [
            mx_benchmark.StdOutRule(
                r"===== DaCapo (?P<version>\S+) (?P<benchmark>[a-zA-Z0-9_]+) walltime [0-9]+ : (?P<time>[0-9]+) msec =====", # pylint: disable=line-too-long
                {
                    "benchmark": ("<benchmark>", str),
                    "bench-suite": self.benchSuiteName(),
                    "vm": "jvmci",
                    "config.name": "default",
                    "config.vm-flags": self.shorten_vm_flags(self.vmArgs(bmSuiteArgs)),
                    "metric.name": "walltime",
                    "metric.value": ("<time>", int),
                    "metric.unit": "ms",
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": ("$iteration", int)
                }
            )
        ]

    def postprocessRunArgs(self, benchname, runArgs):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-n", default=None)
        args, remaining = parser.parse_known_args(runArgs)
        result = ['-c', 'WallTimeCallback'] + remaining
        if args.n:
            if args.n.isdigit():
                result = ["-n", args.n] + result
        else:
            iterations = scala_dacapo_warmup_iterations[benchname]["late-warmup"]
            result = ["-n", str(iterations)] + result
        return result

    def run(self, benchmarks, bmSuiteArgs):
        results = super(ScalaDaCapoWarmupBenchmarkSuite, self).run(benchmarks, bmSuiteArgs)
        self.warmupResults(results, scala_dacapo_warmup_iterations, 'walltime')
        # walltime entries are not accepted by the bench server
        return [e for e in results if e["metric.name"] != "walltime"]

mx_benchmark.add_bm_suite(ScalaDaCapoWarmupBenchmarkSuite())
