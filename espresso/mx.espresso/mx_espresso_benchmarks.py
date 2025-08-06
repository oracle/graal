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
import os
import re
from abc import ABCMeta, abstractmethod

import mx
import mx_benchmark
import mx_espresso
import mx_jardistribution
import mx_polybench

from mx_benchmark import GuestVm, JavaVm, OutputCapturingJavaVm
from mx_sdk_benchmark import _daCapoScalaConfig

_suite = mx.suite('espresso')


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    benchmark_dist = _suite.dependency("ESPRESSO_POLYBENCH_BENCHMARKS")
    polybench_benchmarks = os.path.join(_suite.dir, 'benchmarks', 'interpreter')
    for f in os.listdir(polybench_benchmarks):
        if os.path.isdir(os.path.join(polybench_benchmarks, f)) and not f.startswith("."):
            main_class = os.path.basename(f)
            simple_name = main_class.split(".")[-1]

            project_name = 'benchmarks.interpreter.espresso.' + simple_name.lower()
            register_project(mx.JavaProject(
                suite=_suite,
                subDir=None,
                srcDirs=[os.path.join(_suite.dir, 'benchmarks', 'interpreter', main_class)],
                deps=[],
                name=project_name,
                d=os.path.join(_suite.dir, 'benchmarks', 'interpreter', main_class),
                javaCompliance='11+',
                workingSets=None,
                testProject=True,
                eclipseformat=False,
                skipVerifyImports=True,
                # javac and JDT both produce warnings on these sources. suppress javac warnings and avoid JDT.
                forceJavac=True,
                checkstyleProj=project_name,
                **{
                    "javac.lint.overrides": "none",
                }
            ))

            dist_name = 'ESPRESSO_POLYBENCH_BENCHMARK_' + simple_name.upper()
            jar_dist = mx_jardistribution.JARDistribution(
                suite=_suite,
                subDir=None,
                srcDirs=[''],
                sourcesPath=None,
                deps=[project_name],
                mainClass=main_class,
                name=dist_name,
                path='',
                platformDependent=False,
                distDependencies=[],
                javaCompliance='11+',
                excludedLibs=[],
                workingSets=None,
                theLicense=None,
                testDistribution=True,
                maven=False,
            )
            register_distribution(jar_dist)

            benchmark_dist.layout[f'./interpreter/{simple_name}.jar'] = [
                f'dependency:{dist_name}/*.jar']
            benchmark_dist.buildDependencies.append(dist_name)

    espresso_runtime_resources_distribution = mx_espresso.espresso_runtime_resources_distribution()
    mx_polybench.register_polybench_language(mx_suite=_suite, language="espresso",
                                             distributions=["ESPRESSO", "ESPRESSO_LIBS_RESOURCES",
                                                            espresso_runtime_resources_distribution, "truffle:TRUFFLE_NFI_LIBFFI"])

    def espresso_polybench_runner(polybench_run: mx_polybench.PolybenchRunFunction, tags) -> None:
        if "gate" in tags:
            polybench_run(["--jvm", "interpreter/*.jar", "--experimental-options", "--engine.Compilation=false", "-w", "1", "-i", "1"])
            polybench_run(["--native", "interpreter/*.jar", "--experimental-options", "--engine.Compilation=false", "-w", "1", "-i", "1"])
        if "benchmark" in tags:
            polybench_run(["--jvm", "interpreter/*.jar", "--experimental-options", "--engine.Compilation=false"])
            polybench_run(["--native", "interpreter/*.jar", "--experimental-options", "--engine.Compilation=false"])
            polybench_run(["--jvm", "interpreter/*.jar"])
            polybench_run(["--native", "interpreter/*.jar"])
            polybench_run(["--jvm", "interpreter/*.jar", "--metric=metaspace-memory"])
            polybench_run(["--jvm", "interpreter/*.jar", "--metric=application-memory"])
            polybench_run(["--jvm", "interpreter/*.jar", "--metric=allocated-bytes", "-w", "40", "-i", "10", "--experimental-options", "--engine.Compilation=false"])
            polybench_run(["--native", "interpreter/*.jar", "--metric=allocated-bytes", "-w", "40", "-i", "10", "--experimental-options", "--engine.Compilation=false"])
            polybench_run(["--jvm", "interpreter/*.jar", "--metric=allocated-bytes", "-w", "40", "-i", "10"])
            polybench_run(["--native", "interpreter/*.jar", "--metric=allocated-bytes", "-w", "40", "-i", "10"])
        if "instructions" in tags:
            assert mx_polybench.is_enterprise()
            fork_count_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "polybench-fork-counts.json")
            polybench_run(["--native", "interpreter/*.jar", "--metric=instructions", "--experimental-options", "--engine.Compilation=false",
                           "--mx-benchmark-args", "--fork-count-file", fork_count_file])

    mx_polybench.register_polybench_benchmark_suite(mx_suite=_suite, name="espresso", languages=["espresso"],
                                                    benchmark_distribution=benchmark_dist.name,
                                                    benchmark_file_filter=".*jar", runner=espresso_polybench_runner,
                                                    tags={"gate", "benchmark", "instructions"})


class EspressoStandaloneVm(OutputCapturingJavaVm, metaclass=ABCMeta):
    def __init__(self, config_name, options):
        super().__init__()
        self._config_name = config_name
        self._options = options

    def config_name(self):
        return self._config_name

    def run_java(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False):
        return mx.run(self.generate_java_command(args), out=out, err=err, cwd=cwd, nonZeroIsFatal=nonZeroIsFatal)

    def post_process_command_line_args(self, suiteArgs):
        return self._options + suiteArgs

    @abstractmethod
    def home(self):
        pass

    @abstractmethod
    def _print_flags_opt(self):
        pass

    def extract_vm_info(self, args=None):
        assert args is not None
        args = self.post_process_command_line_args(args)
        args_str = ' '.join(args)
        if not self.currently_extracting_vm_info and args_str not in self._vm_info:
            self.currently_extracting_vm_info = True
            try:
                vm_info = {}
                hooks = self.command_mapper_hooks
                self.command_mapper_hooks = None
                with mx.DisableJavaDebugging():
                    vm_opts = _get_vm_options_for_config_extraction(args, self._print_flags_opt())
                    vm_args = vm_opts + ["-version"]
                    mx.logv(f"Extracting vm info by calling : java {' '.join(vm_args)}")
                    java_version_out = mx.TeeOutputCapture(mx.OutputCapture(), mx.logv)
                    code = self.run_java(vm_args, out=java_version_out, err=java_version_out, cwd=".")
                    if code == 0:
                        command_output = java_version_out.data
                        gc, initial_heap, max_heap = _get_gc_info(command_output)
                        vm_info["platform.gc"] = gc
                        vm_info["platform.initial-heap-size"] = initial_heap
                        vm_info["platform.max-heap-size"] = max_heap

                        version_output = command_output.splitlines()
                        assert len(version_output) >= 3
                        version_start_line = 0
                        for i, line in enumerate(version_output):
                            if " version " in line:
                                version_start_line = i
                                break
                        version_output = version_output[version_start_line:version_start_line+3]
                        jdk_version_number = version_output[0].split("\"")[1]
                        version = mx.VersionSpec(jdk_version_number)
                        jdk_major_version = version.parts[1] if version.parts[0] == 1 else version.parts[0]
                        jdk_version_string = version_output[2]
                        vm_info["platform.jdk-version-number"] = jdk_version_number
                        vm_info["platform.jdk-major-version"] = jdk_major_version
                        vm_info["platform.jdk-version-string"] = jdk_version_string
                        if mx.suite('graal-enterprise', fatalIfMissing=False) or mx.suite('truffle-enterprise', fatalIfMissing=False) or mx.suite('substratevm-enterprise', fatalIfMissing=False):
                            vm_info["platform.graalvm-edition"] = "EE"
                        else:
                            vm_info["platform.graalvm-edition"] = "CE"
                    else:
                        mx.log_error(f"VM info extraction failed ! (code={code})")
            finally:
                self.currently_extracting_vm_info = False
                self.command_mapper_hooks = hooks

            self._vm_info[args_str] = vm_info

def _get_vm_options_for_config_extraction(run_args, print_flags_opt):
    vm_opts = []
    for arg in run_args:
        vm_arg = arg
        if vm_arg.startswith("--vm."):
            vm_arg = '-' + vm_arg[len("--vm."):]
        if vm_arg.startswith("-Xm"):
            vm_opts.append(arg)
        if (vm_arg.startswith("-XX:+Use") or vm_arg.startswith("-XX:-Use")) and vm_arg.endswith("GC"):
            vm_opts.append(arg)
    vm_opts.append(print_flags_opt)
    return vm_opts

flags_re = re.compile(r' *(?P<type>[a-z_0-9]+) (?P<name>[A-Za-z0-9_]+) * = (?P<value>.+?) *(\{[A-Za-z0-9]+( [A-Za-z0-9]+)*\})? *\{(?P<origin>[A-Za-z0-9 ,]+)\}')

def _get_gc_info(version_out):
    gc = ""
    initial_heap_size = -1
    max_heap_size = -1

    for line in version_out.splitlines():
        m = flags_re.match(line)
        if not m:
            continue
        flag = m.group('name')
        value = m.group('value')
        origin = m.group('origin')
        if origin == 'default':
            continue
        if flag.startswith("Use") and flag.endswith("GC") and value == 'true':
            assert gc == ''
            gc = flag[3:]
        if flag.startswith("InitialHeapSize"):
            initial_heap_size = int(value)
        if flag.startswith("MaxHeapSize"):
            max_heap_size = int(value)
    mx.logv(f"Detected GC is '{gc}'. Heap size : Initial = {initial_heap_size}, Max = {max_heap_size}")
    return gc, initial_heap_size, max_heap_size

class EspressoJvmStandaloneVm(EspressoStandaloneVm):
    def name(self):
        return 'espresso-jvm-standalone'

    def generate_java_command(self, args):
        return mx_espresso._espresso_launcher_command(self.post_process_command_line_args(args))

    def home(self):
        return mx.distribution('ESPRESSO_JVM_STANDALONE').get_output()

    def _print_flags_opt(self):
        return "--vm.XX:+PrintFlagsFinal"


class EspressoNativeStandaloneVm(EspressoStandaloneVm):
    def name(self):
        return 'espresso-native-standalone'

    def generate_java_command(self, args):
        return mx_espresso._java_truffle_command(self.post_process_command_line_args(args))

    def home(self):
        return mx.distribution('ESPRESSO_NATIVE_STANDALONE').get_output()

    def _print_flags_opt(self):
        return "-XX:+PrintFlagsFinal"


class EspressoGuestVm(GuestVm, JavaVm):
    def __init__(self, config_name, options, host_vm=None):
        super().__init__(host_vm=host_vm)
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
        code, out, dims = self.host_vm().run(cwd, mx_espresso._espresso_standalone_command(self._options + args))
        guest_jdk = mx_espresso.get_java_home_dep()
        def _preserve_host_dim(name):
            if name in dims:
                dims["host." + name] = dims[name]
        _preserve_host_dim("platform.jdk-version-number")
        _preserve_host_dim("platform.jdk-major-version")
        _preserve_host_dim("platform.jdk-version-string")
        dims["platform.jdk-version-number"] = str(guest_jdk.version)
        dims["platform.jdk-major-version"] = guest_jdk.major_version
        del dims["platform.jdk-version-string"]
        return code, out, dims


class EspressoMinHeapVm(EspressoGuestVm):
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


def register_standalone_vm(config_name, options):
    mx_benchmark.java_vm_registry.add_vm(EspressoJvmStandaloneVm(config_name, options), _suite)
    mx_benchmark.java_vm_registry.add_vm(EspressoNativeStandaloneVm(config_name, options), _suite)
    mx_benchmark.java_vm_registry.add_vm(EspressoGuestVm(config_name, options), _suite)

register_standalone_vm('default', [])
register_standalone_vm('interpreter', ['--experimental-options', '--engine.Compilation=false'])
register_standalone_vm('interpreter-inline-accessors', ['--experimental-options', '--engine.Compilation=false', '--java.InlineFieldAccessors'])
register_standalone_vm('inline-accessors', ['--experimental-options', '--java.InlineFieldAccessors'])
register_standalone_vm('single-tier', ['--experimental-options', '--engine.MultiTier=false'])
register_standalone_vm('multi-tier', ['--experimental-options', '--engine.MultiTier=true'])
register_standalone_vm('3-compiler-threads', ['--experimental-options', '--engine.CompilerThreads=3'])
register_standalone_vm('multi-tier-inline-accessors', ['--experimental-options', '--engine.MultiTier', '--java.InlineFieldAccessors'])
register_standalone_vm('no-inlining', ['--experimental-options', '--engine.Inlining=false'])
register_standalone_vm('safe', ['--experimental-options', '--engine.RelaxStaticObjectSafetyChecks=false'])
register_standalone_vm('field-based', ['--experimental-options', '--engine.StaticObjectStorageStrategy=field-based'])
register_standalone_vm('field-based-safe', ['--experimental-options', '--engine.StaticObjectStorageStrategy=field-based', '--engine.RelaxStaticObjectSafetyChecks=false'])
register_standalone_vm('array-based', ['--experimental-options', '--engine.StaticObjectStorageStrategy=array-based'])
register_standalone_vm('array-based-safe', ['--experimental-options', '--engine.StaticObjectStorageStrategy=array-based', '--engine.RelaxStaticObjectSafetyChecks=false'])

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
