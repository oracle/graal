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
import mx_sdk_benchmark
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


_RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS = {
    'als'               : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime',
                           '--initialize-at-build-time=org.slf4j,org.apache.log4j', # mis-initialized from netty
                           '--initialize-at-run-time=io.netty.channel.unix.IovArray,io.netty.channel.epoll.EpollEventLoop,io.netty.channel.unix.Errors,io.netty.channel.unix.Socket,io.netty.channel.unix.Limits'
                          ],
    'chi-square'        : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime',
                          ],
    'finagle-http'      : [
                           '--allow-incomplete-classpath'
                          ],
    'log-regression'    : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime',
                          ],
    'movie-lens'        : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime',
                          ],
    'dec-tree'          : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime',
                          ],
    'page-rank'         : [
                           '--allow-incomplete-classpath',
                           '--report-unsupported-elements-at-runtime'
                          ],
    'naive-bayes'       : [
                            '--allow-incomplete-classpath',
                            '--report-unsupported-elements-at-runtime'
                          ],
}

_renaissance_config = {
    "akka-uct": {
        "group": "actors-akka",
        "legacy-group": "actors",
        "requires-recompiled-harness": ["0.9.0", "0.10.0", "0.11.0"]
    },
    "reactors": {
        "group": "actors-reactors",
        "legacy-group": "actors",
        "requires-recompiled-harness": True
    },
    "scala-kmeans": {
        "group": "scala-stdlib"
    },
    "scala-doku": {
        "group": "scala-sat"
    },
    "mnemonics": {
        "group": "jdk-streams"
    },
    "par-mnemonics": {
        "group": "jdk-streams"
    },
    "rx-scrabble": {
        "group": "rx"
    },
    "als": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "chi-square": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "db-shootout": {  # GR-17975, GR-17943 (with --report-unsupported-elements-at-runtime)
        "group": "database",
        "requires-recompiled-harness": ["0.9.0", "0.10.0", "0.11.0"]
    },
    "dec-tree": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "dotty": {
        "group": "scala-dotty"
    },
    "finagle-chirper": {
        "group": "twitter-finagle",
        "requires-recompiled-harness": True
    },
    "finagle-http": {
        "group": "twitter-finagle",
        "requires-recompiled-harness": True
    },
    "fj-kmeans": {
        "group": "jdk-concurrent"
    },
    "future-genetic": {
        "group": "jdk-concurrent"
    },
    "gauss-mix": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "log-regression": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "movie-lens": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "naive-bayes": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "page-rank": {
        "group": "apache-spark",
        "requires-recompiled-harness": True
    },
    "neo4j-analytics": {
        "group": "neo4j",
        "requires-recompiled-harness": True
    },
    "philosophers": {
        "group": "scala-stm",
        "requires-recompiled-harness": ["0.12.0", "0.13.0"]
    },
    "scala-stm-bench7": {
        "group": "scala-stm",
        "requires-recompiled-harness": ["0.12.0", "0.13.0"]
    },
    "scrabble": {
        "group": "jdk-streams"
    }
}


def benchmark_group(benchmark, suite_version):
    if suite_version in ["0.9.0", "0.10.0", "0.11.0"]:
        return _renaissance_config[benchmark].get("legacy-group", _renaissance_config[benchmark]["group"])
    else:
        return _renaissance_config[benchmark]["group"]


def requires_recompiled_harness(benchmark, suite_version):
    requires_harness = _renaissance_config[benchmark].get("requires-recompiled-harness", False)
    if isinstance(requires_harness, list):
        return suite_version in requires_harness
    return requires_harness


class RenaissanceNativeImageBenchmarkSuite(mx_java_benchmarks.RenaissanceBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
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

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'renaissance'

    def renaissance_harness_lib_name(self):
        version_to_run = self.version()
        version_end_index = str(version_to_run).rindex('.')
        return 'RENAISSANCE_HARNESS_v' + str(version_to_run)[0:version_end_index]

    def harness_path(self):
        lib = mx.library(self.renaissance_harness_lib_name())
        if lib:
            return lib.get_path(True)
        return None

    # Before supporting new Renaissance versions, we must cross-compile Renaissance harness project
    # with scala 11 for benchmarks compiled with this version of Scala.
    def availableSuiteVersions(self):
        return ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]

    def renaissance_unpacked(self):
        return extract_archive(self.renaissancePath(), 'renaissance.extracted')

    def renaissance_additional_lib(self, lib):
        return mx.library(lib).get_path(True)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -r X argument from image run args
        return ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args)
        # remove -r X argument from image run args
        return ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)

    def extra_agent_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_agent_profile_run_arg(benchmark, args, image_run_args)
        # remove -r X argument from image run args
        return ['-r', '10'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)

    def skip_agent_assertions(self, benchmark, args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def extra_image_build_argument(self, benchmark, args):
        default_args = _RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(RenaissanceNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]
        run_args = self.postprocessRunArgs(self.benchmarkName(), self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        return ['-cp', self.create_classpath(self.benchmarkName())] + vm_args + ['-jar', self.renaissancePath()] + run_args + [self.benchmarkName()]

    def successPatterns(self):
        return super(RenaissanceNativeImageBenchmarkSuite, self).successPatterns() + [
            _successful_stage_pattern
        ]

    def create_classpath(self, benchmarkName):
        custom_harness = requires_recompiled_harness(benchmarkName, self.version())
        harness_project = RenaissanceNativeImageBenchmarkSuite.RenaissanceProject('harness', custom_harness, self)
        group_project = RenaissanceNativeImageBenchmarkSuite.RenaissanceProject(benchmark_group(benchmarkName, self.version()), custom_harness, self, harness_project)
        return ':'.join([mx.classpath(harness_project), mx.classpath(group_project)])

    class RenaissanceDependency(mx.ClasspathDependency):
        def __init__(self, name, path): # pylint: disable=super-init-not-called
            mx.Dependency.__init__(self, _suite, name, None)
            self.path = path

        def classpath_repr(self, resolve=True):
            return self.path

        def _walk_deps_visit_edges(self, *args, **kwargs):
            pass

    class RenaissanceProject(mx.ClasspathDependency):
        def __init__(self, group, requires_recompiled_harness, renaissance_suite, dep_project=None): # pylint: disable=super-init-not-called
            mx.Dependency.__init__(self, _suite, group, None)
            self.suite = renaissance_suite
            self.deps = self.collect_group_dependencies(group, requires_recompiled_harness)
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

            if self.suite.version() in ["0.9.0", "0.10.0", "0.11.0"]:
                if group == 'apache-spark':
                    # breeze jar is replaced with a patched jar because of IncompatibleClassChange errors due to a bug in the Scala compiler
                    invalid_bytecode_jar = 'breeze_2.11-0.11.2.jar'
                    lib_dep = RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(invalid_bytecode_jar, mx.join(path, invalid_bytecode_jar))
                    if lib_dep in deps:
                        deps.remove(lib_dep)
                    lib_path = RenaissanceNativeImageBenchmarkSuite.renaissance_additional_lib(self.suite, 'SPARK_BREEZE_PATCHED')
                    deps.append(RenaissanceNativeImageBenchmarkSuite.RenaissanceDependency(os.path.basename(lib_path), lib_path))
            return deps

        def collect_group_dependencies(self, group, requires_recompiled_harness):
            if group == 'harness':
                if requires_recompiled_harness:
                    path = RenaissanceNativeImageBenchmarkSuite.harness_path(self.suite)
                else:
                    unpacked_renaissance = RenaissanceNativeImageBenchmarkSuite.renaissance_unpacked(self.suite)
                    path = mx.join(unpacked_renaissance, 'renaissance-harness')
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
        return None

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


# Note: If you wish to preserve the underlying benchmark stderr and stdout files after a run, you can pass the following argument: -preserve
# This argument can be added to either:
# 1. The agent stage: -Dnative-image.benchmark.extra-agent-run-arg=-preserve
# 2. The image run stage: -Dnative-image.benchmark.extra-run-arg=-preserve
_DACAPO_SKIP_AGENT_ASSERTIONS = {
    'pmd':        True,
    'sunflow':    True,
    'fop':        True
}

_DACAPO_EXTRA_IMAGE_BUILD_ARGS = {
    'h2' :      ['--allow-incomplete-classpath'],
    'pmd':      ['--allow-incomplete-classpath'],
    # org.apache.crimson.parser.Parser2 is force initialized at build-time due to non-determinism in class initialization
    # order that can lead to runtime issues. See GR-26324.
    'xalan':    ['--report-unsupported-elements-at-runtime',
                 '--initialize-at-build-time=org.apache.crimson.parser.Parser2'],
    # There are two main issues with fop:
    # 1. LoggingFeature is enabled by default, causing the LogManager configuration to be parsed at build-time. However, DaCapo Harness sets the logging config file path system property at runtime.
    #    This causes us to incorrectly parse the default log configuration, leading to output on stderr.
    # 2. Native-image picks a different service provider than the JVM for javax.xml.transform.TransformerFactory.
    #    We can simply remove the jar containing that provider as it is not required for the benchmark to run.
    'fop':      ['--allow-incomplete-classpath',
                 '--report-unsupported-elements-at-runtime',
                 '-H:-EnableLoggingFeature',
                 '--initialize-at-run-time=org.apache.fop.render.rtf.rtflib.rtfdoc.RtfList'],
    'batik':    ['--allow-incomplete-classpath']
}

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
    'batik'      : 40,
    'eclipse'    : -1, # Not supported on Hotspot
    'fop'        : 40,
    'h2'         : 25,
    'jython'     : 20,
    'luindex'    : 15,
    'lusearch'   : 40,
    'pmd'        : 30,
    'sunflow'    : 35,
    'tomcat'     : -1, # Not supported on Hotspot
    'tradebeans' : -1, # Not supported on Hotspot
    'tradesoap'  : -1, # Not supported on Hotspot
    'xalan'      : 30, # Needs both xalan.jar and xalan-2.7.2.jar. Different library versions on classpath aren't supported.
}

_daCapo_exclude_lib = {
    'h2'          : ['derbytools.jar', 'derbyclient.jar', 'derbynet.jar'],  # multiple derby classes occurrences on the classpath can cause a security error
    'pmd'         : ['derbytools.jar', 'derbyclient.jar', 'derbynet.jar'],  # multiple derby classes occurrences on the classpath can cause a security error
    'fop'         : ['saxon-9.1.0.8.jar', 'saxon-9.1.0.8-dom.jar'],  # Native-image picks the wrong service provider from these jars
}

class DaCapoNativeImageBenchmarkSuite(mx_java_benchmarks.DaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
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

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'dacapo'

    def daCapoIterations(self):
        return _daCapo_iterations

    def benchmark_resources(self, benchmark):
        return _dacapo_resources[benchmark]

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)

    def extra_agent_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_agent_profile_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return ['-n', '10'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)

    def skip_agent_assertions(self, benchmark, args):
        default_args = _DACAPO_SKIP_AGENT_ASSERTIONS[benchmark] if benchmark in _DACAPO_SKIP_AGENT_ASSERTIONS else []
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return default_args

    def extra_image_build_argument(self, benchmark, args):
        default_args = _DACAPO_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _DACAPO_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(DaCapoNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]

        run_args = self.postprocessRunArgs(self.benchmarkName(), self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        return ['-cp', self.create_classpath(self.benchmarkName())] + vm_args + ['-jar', self.daCapoPath()] + [self.benchmarkName()] + run_args

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
    'scalac'        : 30,
    'scalariform'   : 30,
    'scalap'        : 120,
    'scaladoc'      : 30,
    'scalatest'     : 60,
    'scalaxb'       : 60,
    'kiama'         : 40,
    'factorie'      : 6,
    'specs'         : 4,
    'apparat'       : 5,
    'tmt'           : 12,
}

_SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS = {
    'scalariform'   : ['--allow-incomplete-classpath'],
    'scalatest'     : ['--allow-incomplete-classpath'],
    'specs'         : ['--allow-incomplete-classpath'],
    'tmt'           : ['--allow-incomplete-classpath'],
}

_scala_daCapo_exclude_lib = {
    'scalariform' : ['scala-library-2.8.0.jar'],
    'scalap'      : ['scala-library-2.8.0.jar'],
    'scaladoc'    : ['scala-library-2.8.0.jar'],
    'scalatest'   : ['scala-library-2.8.0.jar'],
    'scalaxb'     : ['scala-library-2.8.0.jar', 'crimson-1.1.3.jar', 'xercesImpl.jar', 'xerces_2_5_0.jar', 'xalan-2.6.0.jar', 'xalan.jar'],
    'tmt'         : ['scala-library-2.8.0.jar'],
    'scalac'      : ['scala-library-2.8.0.jar'],
}

_scala_daCapo_additional_lib = {
}


class ScalaDaCapoNativeImageBenchmarkSuite(mx_java_benchmarks.ScalaDaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    def name(self):
        return 'scala-dacapo-native-image'

    def daCapoSuiteTitle(self):
        return super(ScalaDaCapoNativeImageBenchmarkSuite, self).suite_title()

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'scala-dacapo'

    def daCapoIterations(self):
        return _scala_dacapo_iterations

    def benchmark_resources(self, benchmark):
        return _scala_dacapo_resources[benchmark]

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']

    def extra_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']

    def extra_agent_profile_run_arg(self, benchmark, args, image_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_agent_profile_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '10']

    def skip_agent_assertions(self, benchmark, args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def extra_image_build_argument(self, benchmark, args):
        default_args = _SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS[benchmark] if benchmark in _SCALA_DACAPO_EXTRA_IMAGE_BUILD_ARGS else []
        return default_args + super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_image_build_argument(benchmark, args)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]

        run_args = self.postprocessRunArgs(self.benchmarkName(), self.runArgs(bmSuiteArgs))
        vm_args = self.vmArgs(bmSuiteArgs)
        return ['-cp', self.create_classpath(self.benchmarkName())] + vm_args + ['-jar', self.daCapoPath()] + [self.benchmarkName()] + run_args

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
        path = mx.project('com.oracle.svm.bench').classpath_repr()
        if not mx.exists(path):
            mx.abort('Path to substitutions for scala dacapo not present: ' + path + '. Did you build all of substratevm?')
        return path


mx_benchmark.add_bm_suite(ScalaDaCapoNativeImageBenchmarkSuite())


class ConsoleNativeImageBenchmarkSuite(mx_java_benchmarks.ConsoleBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    """
    Console applications suite for Native Image
    """

    def name(self):
        return 'console-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'console'

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        args = super(ConsoleNativeImageBenchmarkSuite, self).createCommandLineArgs(benchmarks, bmSuiteArgs)
        self.benchmark_name = benchmarks[0]
        return args


mx_benchmark.add_bm_suite(ConsoleNativeImageBenchmarkSuite())
