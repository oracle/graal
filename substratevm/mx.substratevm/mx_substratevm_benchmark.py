#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function

import os
from contextlib import contextmanager
from os.path import exists
import subprocess
import re
from glob import glob

import time

import functools
import zipfile
import mx
import mx_benchmark
import mx_substratevm
import mx_java_benchmarks
import mx_compiler

_suite = mx.suite("substratevm")

_default_image_options = ['-R:+PrintGCSummary', '-R:+PrintGC', '-H:+PrintImageHeapPartitionSizes',
                          '-H:+PrintImageElementSizes']
_bench_configs = {
    'default': (1, _default_image_options),  # multi-threaded, with graal-enterprise
    'single-threaded': (1, _default_image_options + ['-H:-MultiThreaded']),  # single-threaded
    'native': (1, _default_image_options + ['-H:+NativeArchitecture']),
}


_NANOS_PER_SEC = 10 ** 9

_conf_arg_prefix = '--config='
_IMAGE_BENCH_REPETITIONS = 3
_IMAGE_WARM_UP_ITERATIONS = 3
_successful_stage_pattern = re.compile(r'Successfully finished the last specified stage:.*$', re.MULTILINE)

@contextmanager
def _timedelta(name, out=print):
    start = time.time()
    yield
    end = time.time()
    elapsed = end - start
    lf = '' if out == print else '\n' # pylint: disable=comparison-with-callable
    out('INFO: TIMEDELTA: ' + name + '%0.2f' % elapsed + lf)


def _bench_result(benchmark, metric_name, metric_value, metric_unit, better="lower", m_iteration=0, m_type="numeric",
                  extra=None):
    if extra is None:
        extra = {}
    unit = {"metric.unit": metric_unit} if metric_unit else {}
    return dict(list({
                    "benchmark": benchmark,
                    "metric.name": metric_name,
                    "metric.type": m_type,
                    "metric.value": metric_value,
                    "metric.score-function": "id",
                    "metric.better": better,
                    "metric.iteration": m_iteration
                }.items()) + list(unit.items()) + list(extra.items()))


def _get_bench_conf(args):
    conf_arg = next((arg for arg in args if arg.startswith(_conf_arg_prefix)), '')
    server_prefix = '--bench-compilation-server'
    server_arg = next((True for arg in args if arg == server_prefix), False)
    if conf_arg:
        conf_arg = conf_arg[len(_conf_arg_prefix):]
        if conf_arg == 'list' or not conf_arg in _bench_configs:
            print('Possible configurations: ' + ','.join(_bench_configs.keys()))
            conf_arg = ''
    if not conf_arg:
        conf_arg = 'default'
    return conf_arg, server_arg


# Called by js-benchmarks to allow customization of (host_vm, host_vm_config) tuple
def host_vm_tuple(args):
    config, _ = _get_bench_conf(args)
    return 'svm', config


def find_collections(log):
    regex = re.compile(r'^\[(?P<type>Incremental|Full) GC.+?, (?P<gctime>[0-9]+(?:\.[0-9]+)?) secs\]$', re.MULTILINE)
    results = [(gc_run.group('type'), int(float(gc_run.group('gctime')) * _NANOS_PER_SEC)) for gc_run in regex.finditer(log)]
    return results


def report_gc_results(benchmark, score, execution_time_ns, gc_times_aggregate):
    gc_time_ns = sum([x for _, x in gc_times_aggregate.values()])
    gc_load_percent = (float(gc_time_ns) / execution_time_ns) * 100

    return [
        _bench_result(benchmark, "gc-load-percent", gc_load_percent, "%"),
        _bench_result(benchmark, "incremental-gc-nanos", gc_times_aggregate["Incremental"][1], "ns"),
        _bench_result(benchmark, "complete-gc-nanos", gc_times_aggregate["Full"][1], "ns"),
        _bench_result(benchmark, "incremental-gc-count", gc_times_aggregate["Incremental"][0], "ns"),
        _bench_result(benchmark, "complete-gc-count", gc_times_aggregate["Full"][1], "ns"),
        _bench_result(benchmark, "throughput-no-gc",
                      execution_time_ns * score / (execution_time_ns - gc_time_ns),
                      None, better="higher"),
    ]


# Called by js-benchmarks to extract additional metrics from benchmark-run output.
def output_processors(host_vm_config, common_functions):
    return [functools.partial(common_functions.gc_stats, find_collections, report_gc_results)]


# Called by js-benchmarks to extract additional metrics from benchmark-run output.
def rule_snippets(host_vm_config):
    image_build_start = r'^INFO: EXECUTE IMAGEBUILD: (?P<imageconf>.+?)\n(?:[\s\S]*?)'
    after_bench_run = r'^(?P<benchmark>[a-zA-Z0-9\.\-_]+):[ \t]+(?:[0-9]+(?:\.[0-9]+)?)(?:[\s\S]+?)'

    default_snippets = [
        (
            image_build_start + r'INFO: TIMEDELTA: IMAGEBUILD-SERVER: (?P<buildtime>.+?)\n',
            _bench_result(("<imageconf>", str), "aot-image-buildtime-server", ("<buildtime>", float), "s")
        ),
        (
            image_build_start + r'INFO: TIMEDELTA: IMAGEBUILD: (?P<buildtime>.+?)\n',
            _bench_result(("<imageconf>", str), "aot-image-buildtime", ("<buildtime>", float), "s")
        ),
        (
            image_build_start + r'INFO: IMAGESIZE: (?P<size>.+?) MiB\n',
            _bench_result(("<imageconf>", str), "aot-image-size", ("<size>", float), "MiB")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: readOnlyPrimitive  size: (?P<readonly_primitive_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-readonly-primitive-size",
                          ("<readonly_primitive_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: readOnlyReference  size: (?P<readonly_reference_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-readonly-reference-size",
                          ("<readonly_reference_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: writablePrimitive  size: (?P<writable_primitive_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-writable-primitive-size",
                          ("<writable_primitive_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageHeapPartitionSizes:  partition: writableReference  size: (?P<writable_reference_size>.+?)\n',
            _bench_result(("<imageconf>", str), "image-heap-writable-reference-size",
                          ("<writable_reference_size>", int), "B")
        ),
        (
            image_build_start + r'PrintImageElementSizes:  size: +(?P<element_size>.+?)  name: (?P<element_name>.+?)\n',
            _bench_result(("element-" + "<element_name>", str), "size", ("<element_size>", int), "B",
                          extra={"bench-suite": "js-image"})
        ),
        (
            after_bench_run + r'INFO: TIMEDELTA: IMAGERUN: (?P<runtime>.+?)\n',
            _bench_result(("<benchmark>", str), "time", ("<runtime>", float), "s")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteCollectionsTime: (?P<gctime>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-nanos-whole", ("<gctime>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteCollectionsCount: (?P<gccount>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-count-whole", ("<gccount>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: IncrementalGCCount: (?P<incremental_gc_count>.+?)\n',
            _bench_result(("<benchmark>", str), "incremental-gc-count-whole", ("<incremental_gc_count>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: IncrementalGCNanos: (?P<incremental_gc_nanos>.+?)\n',
            _bench_result(("<benchmark>", str), "incremental-gc-nanos-whole", ("<incremental_gc_nanos>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteGCCount: (?P<complete_gc_count>.+?)\n',
            _bench_result(("<benchmark>", str), "complete-gc-count-whole", ("<complete_gc_count>", int), "#")
        ),
        (
            after_bench_run + r'PrintGCSummary: CompleteGCNanos: (?P<complete_gc_nanos>.+?)\n',
            _bench_result(("<benchmark>", str), "complete-gc-nanos-whole", ("<complete_gc_nanos>", int), "ns")
        ),
        (
            after_bench_run + r'PrintGCSummary: GCLoadPercent: (?P<gc_load_percent>.+?)\n',
            _bench_result(("<benchmark>", str), "gc-load-percent-whole", ("<gc_load_percent>", int), "%")
        ),
        (
            after_bench_run + r'PrintGCSummary: AllocatedTotalObjectBytes: (?P<mem>.+?)\n',
            _bench_result(("<benchmark>", str), "allocated-memory", ("<mem>", int), "B", better="lower")
        ),
    ]
    return default_snippets


def _bench_image_params(bench_conf):
    image_dir = os.path.join(mx.suite('substratevm').dir, 'svmbenchbuild')
    js_image_name = 'js_' + bench_conf
    image_path = os.path.join(image_dir, js_image_name)
    return image_dir, js_image_name, image_path


def bench_jsimage(bench_conf, out, err, extra_options=None):
    if extra_options is None:
        extra_options = []

    image_dir, js_image_name, image_path = _bench_image_params(bench_conf)
    if not exists(image_path):
        native_image = mx_substratevm.vm_native_image_path(mx_substratevm._graalvm_js_config)
        if not exists(native_image):
            mx_substratevm.build_native_image_image(mx_substratevm._graalvm_js_config)
        with _timedelta('IMAGEBUILD: ', out=out):
            out('INFO: EXECUTE IMAGEBUILD: svmimage-%s\n' % bench_conf)
            _, image_building_options = _bench_configs[bench_conf]
            command = [native_image, '--language:js', '-H:Path=' + image_dir,
                       '-H:Name=' + js_image_name] + image_building_options + extra_options
            # Print out the command.
            print(' '.join(command))
            # Run the command and copy the output to out so it can be examined for metrics.
            runner = mx_substratevm.ProcessRunner(command, stderr=subprocess.STDOUT)
            returncode, stdoutdata, _ = runner.run(timeout=240)
            out(stdoutdata)
            if runner.timedout:
                mx.abort('Javascript image building for js-benchmarks timed out.')
            if returncode != 0:
                mx.abort('Javascript image building for js-benchmarks failed.')
            # Generate the image size metric.
            image_statinfo = os.stat(image_path)
            image_size = image_statinfo.st_size / 1024.0 / 1024.0
            out('INFO: IMAGESIZE: %0.2f MiB\n' % image_size)
    return image_path


def _bench_compile_server(bench_conf, out):
    def delete_image(image_path):
        if os.path.exists(image_path):
            os.remove(image_path)

    def build_js_image_in_server(stdouterr):
        return bench_jsimage(bench_conf, stdouterr, stdouterr)

    devnull = open(os.devnull, 'w').write
    for i in range(_IMAGE_WARM_UP_ITERATIONS):
        print("Building js image in the image build server: " + str(i))
        image_path = build_js_image_in_server(devnull)
        delete_image(image_path)

    print('Measuring performance of js image compilation in the server.')
    for _ in range(_IMAGE_BENCH_REPETITIONS):
        with _timedelta("IMAGEBUILD-SERVER: ", out=out):
            out('INFO: EXECUTE IMAGEBUILD: svmimage-%s\n' % bench_conf)
            image_path = build_js_image_in_server(devnull)
        delete_image(image_path)


# Called by js-benchmarks to run a javascript benchmark
def run_js(vmArgs, jsArgs, nonZeroIsFatal, out, err, cwd):
    bench_conf, should_bench_compile_server = _get_bench_conf(vmArgs)
    _, _, image_path = _bench_image_params(bench_conf)
    if should_bench_compile_server and not exists(image_path):
        for _ in range(_IMAGE_BENCH_REPETITIONS):
            with mx_substratevm.native_image_context(config=mx_substratevm._graalvm_js_config, build_if_missing=True):
                _bench_compile_server(bench_conf, out)

    image_path = bench_jsimage(bench_conf, out=out, err=err)
    if image_path:
        vmArgs = [vmArg for vmArg in vmArgs if not (vmArg.startswith(_conf_arg_prefix) or vmArg == '--bench-compilation-server')]

        all_args = vmArgs + jsArgs
        mx.logv("Running substratevm image '%s' with '%s' i.e.:" % (image_path, all_args))
        mx.logv(image_path + " " + " ".join(all_args))
        runner = mx_substratevm.ProcessRunner([image_path] + all_args, stderr=subprocess.STDOUT)

        timeout_factor, _ = _bench_configs[bench_conf]
        with _timedelta('IMAGERUN: ', out=out):
            returncode, stdoutdata, _ = runner.run(8 * 60 * timeout_factor)
            if runner.timedout:
                if nonZeroIsFatal:
                    mx.abort('Javascript benchmark timeout')
                return -1
            out(stdoutdata)
            return returncode
    if nonZeroIsFatal:
        mx.abort('Javascript image building for js-benchmarks failed')
    return -1


def extract_archive(path, extracted_name):
    extracted_archive = mx.join(mx.dirname(path), extracted_name)
    if not mx.exists(extracted_archive):
        # There can be multiple processes doing this so be atomic about it
        with mx_compiler.SafeDirectoryUpdater(extracted_archive, create=True) as sdu:
            with zipfile.ZipFile(path, 'r') as zf:
                zf.extractall(sdu.directory)
    return extracted_archive

def list_jars(path):
    jars = []
    for f in os.listdir(path):
        if os.path.isfile(mx.join(path, f)) and f.endswith('.jar'):
            jars.append(f)
    return jars


_RENAISSANCE_EXTRA_AGENT_ARGS = [
    '-Dnative-image.benchmark.extra-agent-run-arg=-r',
    '-Dnative-image.benchmark.extra-agent-run-arg=1'
]

RENAISSANCE_EXTRA_PROFILE_ARGS = [
    # extra-profile-run-arg is used to pass a number of instrumentation image run iterations
    '-Dnative-image.benchmark.extra-profile-run-arg=-r',
    '-Dnative-image.benchmark.extra-profile-run-arg=1',
    # extra-agent-profile-run-arg is used to pass a number of agent runs to provide profiles
    '-Dnative-image.benchmark.extra-agent-profile-run-arg=-r',
    '-Dnative-image.benchmark.extra-agent-profile-run-arg=5'
]

_renaissance_config = {
    "akka-uct"         : ("actors", 11), # GR-17994
    "reactors"         : ("actors", 11),
    "scala-kmeans"     : ("scala-stdlib", 12),
    "mnemonics"        : ("jdk-streams", 12),
    "par-mnemonics"    : ("jdk-streams", 12),
    "rx-scrabble"      : ("rx", 12),
    "als"              : ("apache-spark", 11),
    "chi-square"       : ("apache-spark", 11),
    "db-shootout"      : ("database", 11), # GR-17975, GR-17943 (with --report-unsupported-elements-at-runtime)
    "dec-tree"         : ("apache-spark", 11),
    "dotty"            : ("scala-dotty", 12), # GR-17985
    "finagle-chirper"  : ("twitter-finagle", 11),
    "finagle-http"     : ("twitter-finagle", 11),
    "fj-kmeans"        : ("jdk-concurrent", 12),
    "future-genetic"   : ("jdk-concurrent", 12), # GR-17988
    "gauss-mix"        : ("apache-spark", 11),
    "log-regression"   : ("apache-spark", 11),
    "movie-lens"       : ("apache-spark", 11),
    "naive-bayes"      : ("apache-spark", 11),
    "neo4j-analytics"  : ("neo4j", 11),
    "page-rank"        : ("apache-spark", 11),
    "philosophers"     : ("scala-stm", 12),
    "scala-stm-bench7" : ("scala-stm", 12),
    "scrabble"         : ("jdk-streams", 12)
}


def benchmark_group(benchmark):
    return _renaissance_config[benchmark][0]


def benchmark_scalaversion(benchmark):
    return _renaissance_config[benchmark][1]


class RenaissanceNativeImageBenchmarkSuite(mx_java_benchmarks.RenaissanceBenchmarkSuite): #pylint: disable=too-many-ancestors
    """
    Building an image for a renaissance benchmark requires all libraries for the group this benchmark belongs to
    and a harness project compiled with the same scala version as the benchmark.
    Since we don't support building an image from fat-jars, we extract them to create project dependencies.
    Depending on the benchmark's scala version we create corresponding renaissance harness and benchmark projects,
    we set this harness project as a dependency for the benchmark project and collect project's classpath.
    For each renaissance benchmark we store an information about the group and scala version in _renaissance-config.
    We build an image from renaissance jar with the classpath as previously described, provided configurations and extra arguments while neccessary.
    """

    def name(self):
        return 'renaissance-native-image'

    def benchSuiteName(self):
        return 'renaissance'

    def renaissance_harness_lib_name(self):
        return "RENAISSANCE_HARNESS_11"

    def harness_path(self):
        lib = mx.library(self.renaissance_harness_lib_name())
        if lib:
            return lib.get_path(True)
        return None

    def renaissance_unpacked(self):
        return extract_archive(self.renaissancePath(), 'renaissance.extracted')

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        bench_arg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            bench_arg = benchmarks[0]
        run_args = self.postprocessRunArgs(bench_arg, self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)

        agent_args = _RENAISSANCE_EXTRA_AGENT_ARGS + ['-Dnative-image.benchmark.extra-agent-run-arg=' + bench_arg]
        pgo_args = RENAISSANCE_EXTRA_PROFILE_ARGS + ['-Dnative-image.benchmark.extra-profile-run-arg=' + bench_arg, '-Dnative-image.benchmark.extra-agent-profile-run-arg=' + bench_arg]
        benchmark_name = '-Dnative-image.benchmark.benchmark-name=' + bench_arg

        return agent_args + pgo_args + [benchmark_name] + ['-cp', self.create_classpath(bench_arg)] + vm_args + ['-jar', self.renaissancePath()] + run_args + [bench_arg]

    def successPatterns(self):
        return super(RenaissanceNativeImageBenchmarkSuite, self).successPatterns() + [
            _successful_stage_pattern
        ]

    def create_classpath(self, benchArg):
        harness_project = RenaissanceNativeImageBenchmarkSuite.RenaissanceProject('harness', benchmark_scalaversion(benchArg), self)
        group_project = RenaissanceNativeImageBenchmarkSuite.RenaissanceProject(benchmark_group(benchArg), benchmark_scalaversion(benchArg), self, harness_project)
        return ':'.join([mx.classpath(harness_project), mx.classpath(group_project)])

    def extra_vm_args(self, benchmark):
        return []

    class RenaissanceDependency(mx.ClasspathDependency):
        def __init__(self, name, path): # pylint: disable=super-init-not-called
            mx.Dependency.__init__(self, _suite, name, None)
            self.path = path

        def classpath_repr(self, resolve=True):
            return self.path

        def _walk_deps_visit_edges(self, *args, **kwargs):
            pass

    class RenaissanceProject(mx.ClasspathDependency):
        def __init__(self, group, scala_version=12, renaissance_suite=None, dep_project=None): # pylint: disable=super-init-not-called
            mx.Dependency.__init__(self, _suite, group, None)
            self.suite = renaissance_suite
            self.deps = self.collect_group_dependencies(group, scala_version)
            if dep_project is not None:
                self.deps.append(dep_project)

        def _walk_deps_visit_edges(self, visited, in_edge, preVisit=None, visit=None, ignoredEdges=None, visitEdge=None):
            deps = [(mx.DEP_STANDARD, self.deps)]
            self._walk_deps_visit_edges_helper(deps, visited, in_edge, preVisit, visit, ignoredEdges, visitEdge)

        def classpath_repr(self, resolve=True):
            return None

        def get_dependencies(self, group):
            deps = []
            for jar in list_jars(group):
                deps.append(RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(os.path.basename(jar), mx.join(group, jar)))
            return deps

        def collect_group_dependencies(self, group, scala_version):
            if group == 'harness':
                if scala_version == 12:
                    unpacked_renaissance = RenaissanceNativeImageBenchmarkSuite.renaissance_unpacked(self.suite)
                    path = mx.join(unpacked_renaissance, 'renaissance-harness')
                else:
                    path = RenaissanceNativeImageBenchmarkSuite.harness_path(self.suite)
            else:
                unpacked_renaissance = RenaissanceNativeImageBenchmarkSuite.renaissance_unpacked(self.suite)
                path = mx.join(unpacked_renaissance, 'benchmarks', group)
            return self.get_dependencies(path)


mx_benchmark.add_bm_suite(RenaissanceNativeImageBenchmarkSuite())


class BaseDaCapoNativeImageBenchmarkSuite():

    '''`SetBuildInfo` method in DaCapo source reads from the file nested in daCapo jar.
    This is not supported with native image, hence it returns `unknown` for code version.'''

    def suite_title(self):
        return 'DaCapo unknown'

    @staticmethod
    def collect_dependencies(path):
        deps = []
        for f in list_jars(path):
            deps.append(mx.join(path, f))
        return deps

    @staticmethod
    def collect_nested_dependencies(path):
        deps = []
        deps += [y for x in os.walk(path) for y in glob(os.path.join(x[0], '*.jar'))]
        deps += [y for x in os.walk(path) for y in glob(os.path.join(x[0], 'classes'))]
        return deps

    @staticmethod
    def extract_dacapo(dacapo_path):
        return extract_archive(dacapo_path, 'dacapo.extracted')

    def benchmark_resources(self, benchmark):
        pass

    def create_dacapo_classpath(self, dacapo_path, benchmark):
        dacapo_nested_resources = []
        dacapo_dat_resources = []
        dacapo_extracted = self.extract_dacapo(dacapo_path)
        benchmark_resources = self.benchmark_resources(benchmark)
        if benchmark_resources:
            for resource in benchmark_resources:
                dacapo_dat_resource = extract_archive(mx.join(dacapo_extracted, resource), benchmark)
                dat_resource_name = os.path.splitext(os.path.basename(resource))[0]
                dacapo_dat_resources.append(mx.join(dacapo_dat_resource, dat_resource_name))
                #collects nested jar files and classes directories
                dacapo_nested_resources += self.collect_nested_dependencies(dacapo_dat_resource)
        return dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources

    def collect_unique_dependencies(self, path, benchmark, exclude_libs):
        deps = BaseDaCapoNativeImageBenchmarkSuite.collect_dependencies(path)
        # if there are more versions of the same jar, we choose one and omit remaining from the classpath
        if benchmark in exclude_libs:
            for lib in exclude_libs[benchmark]:
                lib_path = mx.join(path, lib)
                if lib_path in deps:
                    deps.remove(mx.join(path, lib))
        return deps


_DACAPO_EXTRA_VM_ARGS = {
    'avrora':     ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.derby.jdbc.ClientDriver,'
                    'org.h2.Driver,org.apache.derby.jdbc.AutoloadedDriver,'
                    'org.apache.derby.client.am.Configuration,org.apache.derby.iapi.services.info.ProductVersionHolder'],
    'h2':         ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=java.sql.DriverManager,org.apache.derby.jdbc.AutoloadedDriver,org.h2.Driver,org.apache.derby.jdbc.ClientDriver',
                    '-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'pmd':        ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=org.apache.derby.jdbc.ClientDriver,org.h2.Driver,java.sql.DriverManager,org.apache.derby.jdbc.AutoloadedDriver,'
                    'org.apache.derby.iapi.services.info.ProductVersionHolder', '-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'xalan':      ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.xml.utils.res.CharArrayWrapper', '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime'],
    'sunflow':    ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=sun.awt.dnd.SunDropTargetContextPeer$EventDispatcher'],
    'fop':        ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=com.sun.proxy.$Proxy188,com.sun.proxy.$Proxy187',
                   '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.fop.render.RendererEventProducer,org.apache.fop.layoutmgr.BlockLevelEventProducer',
                   '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=sun.awt.dnd.SunDropTargetContextPeer$EventDispatcher',
                   '-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    # GR-19371
    'batik':       ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath']
}

_DACAPO_EXTRA_AGENT_ARGS = [
    '-Dnative-image.benchmark.extra-agent-run-arg=-n',
    '-Dnative-image.benchmark.extra-agent-run-arg=1'
]

_DACAPO_EXTRA_PROFILE_ARGS = [
    # extra-profile-run-arg is used to pass a number of instrumentation image run iterations
    '-Dnative-image.benchmark.extra-profile-run-arg=-n',
    '-Dnative-image.benchmark.extra-profile-run-arg=1',
    # extra-agent-profile-run-arg is used to pass a number of agent runs to provide profiles
    '-Dnative-image.benchmark.extra-agent-profile-run-arg=-n',
    '-Dnative-image.benchmark.extra-agent-profile-run-arg=5'
]

'''
Benchmarks from DaCapo suite may require one or more zip archives from `dat` directory on the classpath.
After the agent run we have all necessary resources (from `jar` and `dat` folders inside DaCapo fat jar).
We don't support nested archives and classes directories in a jar so we have to specify them directly on the classpath.
Since we don't have produced config files available in the suite, we will store paths in `_dacapo_resources`,
load all resources from specified archives, and collect them on a benchmark classpath.
'''
_dacapo_resources = {
    'avrora'     : ['dat/avrora.zip'],
    'batik'      : ['dat/batik.zip'],
    'eclipse'    : ['dat/eclipse.zip'],
    'fop'        : ['dat/fop.zip'],
    'h2'         : [],
    'jython'     : ['dat/jython.zip'],
    'luindex'    : ['dat/luindex.zip'],
    'lusearch'   : ['dat/lusearch.zip'],
    'pmd'        : ['dat/pmd.zip'],
    'sunflow'    : [],
    'tomcat'     : ['dat/tomcat.zip'],
    'tradebeans' : ['dat/daytrader.zip'],
    'tradesoap'  : ['dat/daytrader.zip'],
    'xalan'      : ['dat/xalan.zip'],
}

_daCapo_iterations = {
    'avrora'     : 20,
    'batik'      : 40, # GR-21832
    'eclipse'    : -1, # Not supported on Hotspot
    'fop'        : 40, # GR-21831, GR-21832
    'h2'         : 25,
    'jython'     : -1, # Dynamically generates classes, hence can't be supported on SVM for now
    'luindex'    : 15, # GR-17943
    'lusearch'   : 40, # GR-17943
    'pmd'        : 30,
    'sunflow'    : 35,
    'tomcat'     : -1, # Not supported on Hotspot
    'tradebeans' : -1, # Not supported on Hotspot
    'tradesoap'  : -1, # Not supported on Hotspot
    'xalan'      : 30, # Needs both xalan.jar and xalan-2.7.2.jar. Different library versions on classpath aren't supported.
}

_daCapo_exclude_lib = {
    'h2'          : ['derbytools.jar', 'derbyclient.jar', 'derbynet.jar']  # multiple derby classes occurrences on the classpath can cause a security error
}

class DaCapoNativeImageBenchmarkSuite(mx_java_benchmarks.DaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite): #pylint: disable=too-many-ancestors
    def name(self):
        return 'dacapo-native-image'

    '''
    Some methods in DaCapo source are modified because they relied on the jar's nested structure,
    e.g. loading all configuration files for benchmarks from a nested directory.
    Therefore, this library is built from the source.
    '''
    def dacapo_libname(self):
        return 'DACAPO_SVM'

    def daCapoPath(self):
        lib = mx.library(self.dacapo_libname(), False)
        if lib:
            return lib.get_path(True)
        return None

    def daCapoSuiteTitle(self):
        return super(DaCapoNativeImageBenchmarkSuite, self).suite_title()

    def benchSuiteName(self):
        return 'dacapo'

    def daCapoIterations(self):
        return _daCapo_iterations

    def benchmark_resources(self, benchmark):
        return _dacapo_resources[benchmark]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        bench_arg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            bench_arg = benchmarks[0]
        agent_args = ['-Dnative-image.benchmark.extra-agent-run-arg=' + bench_arg] + _DACAPO_EXTRA_AGENT_ARGS
        pgo_args = ['-Dnative-image.benchmark.extra-profile-run-arg=' + bench_arg, '-Dnative-image.benchmark.extra-agent-profile-run-arg=' + bench_arg] + _DACAPO_EXTRA_PROFILE_ARGS
        benchmark_name = '-Dnative-image.benchmark.benchmark-name=' + bench_arg

        run_args = self.postprocessRunArgs(bench_arg, self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs) + (_DACAPO_EXTRA_VM_ARGS[bench_arg] if bench_arg in _DACAPO_EXTRA_VM_ARGS else [])
        return agent_args + pgo_args + [benchmark_name] + ['-cp', self.create_classpath(bench_arg)] + vm_args + ['-jar', self.daCapoPath()] + [bench_arg] + run_args

    def create_classpath(self, benchmark):
        dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources = self.create_dacapo_classpath(self.daCapoPath(), benchmark)
        dacapo_jars = super(DaCapoNativeImageBenchmarkSuite, self).collect_unique_dependencies(os.path.join(dacapo_extracted, 'jar'), benchmark, _daCapo_exclude_lib)
        cp = ':'.join([dacapo_extracted] + dacapo_jars + dacapo_dat_resources + dacapo_nested_resources)
        return cp

    def successPatterns(self):
        return super(DaCapoNativeImageBenchmarkSuite, self).successPatterns() + [
            _successful_stage_pattern
        ]


mx_benchmark.add_bm_suite(DaCapoNativeImageBenchmarkSuite())


_scala_dacapo_resources = {
    'scalac'      : ['dat/scalac.zip'],
    'scalariform' : ['dat/scalariform.zip'],
    'scalap'      : ['dat/scalap.zip'],
    'scaladoc'    : ['dat/scaladoc.zip'],
    'scalatest'   : ['dat/scalatest.zip'],
    'scalaxb'     : ['dat/scalaxb.zip'],
    'kiama'       : ['dat/kiama.zip'],
    'factorie'    : ['dat/factorie.zip'],
    'specs'       : ['dat/specs.zip'],
    'apparat'     : ['dat/apparat.zip'],
    'tmt'         : ['dat/tmt.zip']
}

_scala_dacapo_iterations = {
    'scalac'        : -1, # depends on awt
    'scalariform'   : 30,
    'scalap'        : 120,
    'scaladoc'      : -1, # depends on awt
    'scalatest'     : 60, # GR-21548
    'scalaxb'       : 60,
    'kiama'         : 40,
    'factorie'      : 6,  # GR-21543
    'specs'         : -1, # depends on awt
    'apparat'       : 5,
    'tmt'           : 12,
}

_SCALA_DACAPO_EXTRA_VM_ARGS = {
    'scalac'        : ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=sun.awt.dnd.SunDropTargetContextPeer$EventDispatcher'],
    'scalariform'   : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'scalatest'     : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'specs'         : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.builder.AbstractXmlDocumentBuilder',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder$ElementInfo',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.DocumentBuilder$SpanType',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.DocumentBuilder',
                       '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.eclipse.mylyn.wikitext.core.parser.ImageAttributes$Align'],
    'tmt'           : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath']
}

_scala_daCapo_exclude_lib = {
    'scalariform' : ['scala-library-2.8.0.jar'],
    'scalap'      : ['scala-library-2.8.0.jar'],
    'scaladoc'    : ['scala-library-2.8.0.jar'],
    'scalatest'   : ['scala-library-2.8.0.jar'],
    'scalaxb'     : ['scala-library-2.8.0.jar'],
    'tmt'         : ['scala-library-2.8.0.jar'],
}


class ScalaDaCapoNativeImageBenchmarkSuite(mx_java_benchmarks.ScalaDaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite): #pylint: disable=too-many-ancestors
    def name(self):
        return 'scala-dacapo-native-image'

    def daCapoSuiteTitle(self):
        return super(ScalaDaCapoNativeImageBenchmarkSuite, self).suite_title()

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def benchSuiteName(self):
        return 'scala-dacapo'

    def daCapoIterations(self):
        return _scala_dacapo_iterations

    def daCapoAdditionalLib(self):
        return mx.library('XERCES_IMPL').get_path(True)

    def benchmark_resources(self, benchmark):
        return _scala_dacapo_resources[benchmark]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        bench_arg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            bench_arg = benchmarks[0]
        agent_args = ['-Dnative-image.benchmark.extra-agent-run-arg=' + bench_arg] + _DACAPO_EXTRA_AGENT_ARGS
        pgo_args = ['-Dnative-image.benchmark.extra-profile-run-arg=' + bench_arg, '-Dnative-image.benchmark.extra-agent-profile-run-arg=' + bench_arg] + _DACAPO_EXTRA_PROFILE_ARGS
        benchmark_name = '-Dnative-image.benchmark.benchmark-name=' + bench_arg

        run_args = self.postprocessRunArgs(bench_arg, self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs) + (_SCALA_DACAPO_EXTRA_VM_ARGS[bench_arg] if bench_arg in _SCALA_DACAPO_EXTRA_VM_ARGS else [])
        return agent_args + pgo_args + [benchmark_name] + ['-cp', self.create_classpath(bench_arg)] + vm_args + ['-jar', self.daCapoPath()] + [bench_arg] + run_args

    def create_classpath(self, benchmark):
        dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources = self.create_dacapo_classpath(self.daCapoPath(), benchmark)
        dacapo_jars = super(ScalaDaCapoNativeImageBenchmarkSuite, self).collect_unique_dependencies(os.path.join(dacapo_extracted, 'jar'), benchmark, _scala_daCapo_exclude_lib)
        cp = ':'.join([self.substitution_path()] + [dacapo_extracted] + dacapo_jars + dacapo_dat_resources + dacapo_nested_resources)
        if benchmark == 'scalaxb':
            cp += ':' + self.daCapoAdditionalLib()
        return cp


    def successPatterns(self):
        return super(ScalaDaCapoNativeImageBenchmarkSuite, self).successPatterns() + [
            _successful_stage_pattern
        ]


    @staticmethod
    def substitution_path():
        bench_suite = mx.suite('substratevm')
        root_dir = mx.join(bench_suite.dir, 'mxbuild')
        path = os.path.abspath(mx.join(root_dir, 'src', 'com.oracle.svm.bench', 'bin'))
        if not mx.exists(path):
            mx.abort('Path to substitutions for scala dacapo not present: ' + path + '. Did you build all of substratevm?')
        return path


mx_benchmark.add_bm_suite(ScalaDaCapoNativeImageBenchmarkSuite())
