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
import re
from glob import glob

import zipfile
import mx
import mx_benchmark
import mx_java_benchmarks

_suite = mx.suite("substratevm")
_successful_stage_pattern = re.compile(r'Successfully finished the last specified stage:.*$', re.MULTILINE)


def extract_archive(path, extracted_name):
    extracted_archive = mx.join(mx.dirname(path), extracted_name)
    if not mx.exists(extracted_archive):
        # There can be multiple processes doing this so be atomic about it
        with mx.SafeDirectoryUpdater(extracted_archive, create=True) as sdu:
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

_RENAISSANCE_EXTRA_VM_ARGS = {
    'chi-square'        : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath',
                           '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.hadoop.metrics2.MetricsSystem$Callback',
                           '-Dnative-image.benchmark.extra-image-build-argument=-H:IncludeResourceBundles=sun.security.util.Resources,javax.servlet.http.LocalStrings,javax.servlet.LocalStrings',
                           '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime',
                           '-Dnative-image.benchmark.extra-image-build-argument=-H:-ThrowUnsafeOffsetErrors'],
    'finagle-http'      : ['-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=com.fasterxml.jackson.annotation.JsonProperty$Access', '-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'log-regression'    : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath',
                           '-Dnative-image.benchmark.extra-image-build-argument=-H:+TraceClassInitialization',
                           '-Dnative-image.benchmark.extra-image-build-argument=-H:IncludeResourceBundles=sun.security.util.Resources,javax.servlet.http.LocalStrings,javax.servlet.LocalStrings',
                           '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime',
                           '-Dnative-image.benchmark.extra-image-build-argument=-H:-ThrowUnsafeOffsetErrors',
                           # GR-24903
                           '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-run-time=org.apache.hadoop.io.compress.zlib.BuiltInZlibInflater'],
    'movie-lens'        : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath', '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.hadoop.metrics2.MetricsSystem$Callback',
                           '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime', '-Dnative-image.benchmark.extra-image-build-argument=-H:IncludeResourceBundles=sun.security.util.Resources'],
    'page-rank'         : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath', '-Dnative-image.benchmark.extra-image-build-argument=--initialize-at-build-time=org.apache.hadoop.metrics2.MetricsSystem$Callback',
                           '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime']
}

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

# breeze jar is replaced with a patched jar because of IncompatibleClassChange errors due to a bug in the Scala compiler.
_renaissance_additional_lib = {
    'apache-spark'               : ['SPARK_BREEZE_PATCHED']
}

_renaissance_exclude_lib = {
    'apache-spark'               : ['breeze_2.11-0.11.2.jar']
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
        version_to_run = super(RenaissanceNativeImageBenchmarkSuite, self).renaissanceVersionToRun()
        version_end_index = str(version_to_run).rindex('.')
        return 'RENAISSANCE_HARNESS_v' + str(version_to_run)[0:version_end_index]

    def harness_path(self):
        lib = mx.library(self.renaissance_harness_lib_name())
        if lib:
            return lib.get_path(True)
        return None

    # Before supporting new Renaissance versions, we must cross-compile Renaissance harness project
    # with scala 11 for benchmarks compiled with this version of Scala.
    def availableRenaissanceVersions(self):
        return ["0.9.0", "0.10.0", "0.11.0"]

    def renaissance_unpacked(self):
        return extract_archive(self.renaissancePath(), 'renaissance.extracted')

    def renaissance_additional_lib(self, lib):
        return mx.library(lib).get_path(True)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        bench_arg = ""
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            bench_arg = benchmarks[0]
        run_args = self.postprocessRunArgs(bench_arg, self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs) + self.extra_vm_args(bench_arg)

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
        return _RENAISSANCE_EXTRA_VM_ARGS[benchmark] if benchmark in _RENAISSANCE_EXTRA_VM_ARGS else []

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

        def get_dependencies(self, path, group):
            deps = []
            for jar in list_jars(path):
                deps.append(RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(os.path.basename(jar), mx.join(path, jar)))
            if group in _renaissance_exclude_lib:
                for lib in _renaissance_exclude_lib[group]:
                    lib_dep = RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(lib, mx.join(path, lib))
                    if lib_dep in deps:
                        deps.remove(lib_dep)
            if group in _renaissance_additional_lib:
                for lib in _renaissance_additional_lib[group]:
                    lib_path = RenaissanceNativeImageBenchmarkSuite.renaissance_additional_lib(self.suite, lib)
                    deps.append(RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(os.path.basename(lib_path), lib_path))
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
            return self.get_dependencies(path, group)


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

    def additional_lib(self, lib):
        return mx.library(lib).get_path(True)

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
    'h2':         ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'pmd':        ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath', '-Dnative-image.benchmark.skip-agent-assertions=true'],
    'sunflow':    ['-Dnative-image.benchmark.skip-agent-assertions=true'],
    'xalan':      ['-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime'],
    'fop':        ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath', '-Dnative-image.benchmark.skip-agent-assertions=true', '-Dnative-image.benchmark.extra-image-build-argument=--report-unsupported-elements-at-runtime'],
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
    'specs'         : 4,
    'apparat'       : 5,
    'tmt'           : 12,
}

_SCALA_DACAPO_EXTRA_VM_ARGS = {
    'scalariform'   : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'scalatest'     : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'specs'         : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
    'tmt'           : ['-Dnative-image.benchmark.extra-image-build-argument=--allow-incomplete-classpath'],
}

_scala_daCapo_exclude_lib = {
    'scalariform' : ['scala-library-2.8.0.jar'],
    'scalap'      : ['scala-library-2.8.0.jar'],
    'scaladoc'    : ['scala-library-2.8.0.jar'],
    'scalatest'   : ['scala-library-2.8.0.jar'],
    'scalaxb'     : ['scala-library-2.8.0.jar'],
    'tmt'         : ['scala-library-2.8.0.jar'],
}

_scala_daCapo_additional_lib = {
    'scalaxb'     : ['XERCES_IMPL']
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
        if benchmark in _scala_daCapo_additional_lib:
            for lib in _scala_daCapo_additional_lib[benchmark]:
                cp += ':' +  super(ScalaDaCapoNativeImageBenchmarkSuite, self).additional_lib(lib)
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
