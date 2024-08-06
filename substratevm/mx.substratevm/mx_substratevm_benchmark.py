#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function

import os
import tempfile
import zipfile
from glob import glob

import mx
import mx_benchmark
import mx_sdk_benchmark
from mx_sdk_benchmark import SUCCESSFUL_STAGE_PATTERNS

_suite = mx.suite("substratevm")


def extract_archive(path, extracted_name):
    extracted_archive = os.path.join(os.path.dirname(path), extracted_name)
    if not os.path.exists(extracted_archive):
        # There can be multiple processes doing this so be atomic about it
        with mx.SafeDirectoryUpdater(extracted_archive, create=True) as sdu:
            with zipfile.ZipFile(path, 'r') as zf:
                zf.extractall(sdu.directory)
    return extracted_archive


def list_jars(path):
    jars = []
    for f in os.listdir(path):
        if os.path.isfile(os.path.join(path, f)) and f.endswith('.jar'):
            jars.append(f)
    return jars

# The agent fails to generate the configuration for org.apache.spark.status.JobDataWrapper.completionTime, which is not
# executed on the first iteration. Therefore, we supply the missing information manually.
# See GR-51788
movie_lens_reflection_config = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'movie-lens-reflection-config.json')

force_buildtime_init_slf4j_1_7_73 = '--initialize-at-build-time=org.slf4j,org.apache.log4j'
force_buildtime_init_slf4j_1_7_73_spark = '--initialize-at-build-time=org.apache.logging.slf4j.Log4jLoggerFactory,\
org.apache.logging.slf4j.SLF4JServiceProvider,org.apache.logging.slf4j.Log4jMarkerFactory,org.apache.logging.slf4j.Log4jMDCAdapter,\
org.apache.logging.log4j,org.apache.logging.log4j,org.apache.logging.log4j.core.util.WatchManager,org.apache.logging.log4j.core.config.xml.XmlConfiguration, \
org.apache.logging.log4j.core.config.AbstractConfiguration,org.apache.logging.log4j.util.ServiceLoaderUtil,org.slf4j.LoggerFactory'
force_buildtime_init_netty_4_1_72 = '--initialize-at-build-time=io.netty.util.internal.logging'
force_runtime_init_slf4j_1_7_73 = '--initialize-at-run-time=org.apache.log4j.LogManager'
force_runtime_init_netty_4_1_72 = '--initialize-at-run-time=io.netty.channel.unix,io.netty.channel.epoll,io.netty.handler.codec.http2,io.netty.handler.ssl,io.netty.internal.tcnative,io.netty.util.internal.logging.Log4JLogger'
force_runtime_init_netty_4_1_72_spark = '--initialize-at-run-time=io.netty.buffer.AbstractByteBufAllocator\
io.netty.channel.AbstractChannelHandlerContext,io.netty.channel.ChannelInitializer,io.netty.channel.ChannelOutboundBuffer,\
io.netty.util.internal.SystemPropertyUtil,io.netty.channel.AbstractChannel,io.netty.util.internal.PlatformDependent,\
io.netty.util.internal.InternalThreadLocalMap,io.netty.channel.socket.nio.SelectorProviderUtil,io.netty.util.concurrent.DefaultPromise, \
io.netty.util.NetUtil,io.netty.channel.DefaultChannelPipeline,io.netty.util.concurrent.FastThreadLocalThread,io.netty.util.internal.StringUtil, \
io.netty.util.internal.PlatformDependent0,io.netty.util,io.netty.bootstrap,io.netty.channel,io.netty.buffer,io.netty.resolver,io.netty.handler.codec.CodecOutputList'
_RENAISSANCE_EXTRA_IMAGE_BUILD_ARGS = {
    'als'               : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'chi-square'        : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73
                          ],
    'finagle-chirper'   : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'finagle-http'      : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'log-regression'    : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_runtime_init_netty_4_1_72
                          ],
    'movie-lens'        : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73,
                           '-H:ReflectionConfigurationFiles=' + movie_lens_reflection_config
                          ],
    'dec-tree'          : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_runtime_init_netty_4_1_72
                          ],
    'page-rank'         : [
                           force_buildtime_init_slf4j_1_7_73,
                           force_buildtime_init_slf4j_1_7_73_spark,
                           force_buildtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72,
                           force_runtime_init_netty_4_1_72_spark,
                           force_runtime_init_slf4j_1_7_73
                          ],
    'naive-bayes'       : [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'gauss-mix'       :   [
                            force_buildtime_init_slf4j_1_7_73,
                            force_buildtime_init_slf4j_1_7_73_spark,
                            force_buildtime_init_netty_4_1_72,
                            force_runtime_init_netty_4_1_72,
                            force_runtime_init_netty_4_1_72_spark,
                            force_runtime_init_slf4j_1_7_73
                          ],
    'neo4j-analytics':    [
                            force_buildtime_init_slf4j_1_7_73,
                            force_runtime_init_netty_4_1_72
                          ],
    'dotty'             : [
                            '-H:+AllowJRTFileSystem' # Don't wrap the option with `mx_sdk_vm_impl.svm_experimental_options`, as all args are wrapped already.
                          ]
}

class RenaissanceNativeImageBenchmarkSuite(mx_sdk_benchmark.RenaissanceBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    """
    Building an image for a renaissance benchmark requires all libraries for the group this benchmark belongs to
    and a harness project compiled with the same scala version as the benchmark.
    Since we don't support building an image from fat-jars, we extract them to create project dependencies.

    On recent renaissance versions (>= 0.14.0), it's only necessary to extract the fatjar and run the standalone jar
    of a given benchmark. Those standalone jars define the minimal classpath and include the matching harness for the
    scala version needed by the benchmark.
    """

    def name(self):
        return 'renaissance-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'renaissance'

    def renaissance_harness_lib_name(self):
        # Before Renaissance 0.14.0, we had to cross-compile the Renaissance harness to ensure we have a matching
        # harness for each project compiled with different scala versions.
        # As of Renaissance 0.14.0, we use the standalone mode of renaissance which already creates a native-image
        # friendly classpath and already bundles all harness versions needed.
        version_to_run = self.version()
        if version_to_run in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            version_end_index = str(version_to_run).rindex('.')
            return 'RENAISSANCE_HARNESS_v' + str(version_to_run)[0:version_end_index]
        else:
            return None

    def harness_path(self):
        harness_lib = self.renaissance_harness_lib_name()
        if harness_lib is not None:
            lib = mx.library(harness_lib)
            if lib:
                return lib.get_path(True)
        return None

    def renaissance_unpacked(self):
        return extract_archive(self.renaissancePath(), 'renaissance.extracted')

    def standalone_jar_path(self, benchmark_name):
        standalone_jars_directory = "single"
        return os.path.join(self.renaissance_unpacked(), standalone_jars_directory, "{}.jar".format(benchmark_name))

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_run_arg(self, benchmark, args, image_run_args):
        run_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_run_arg(benchmark, args, image_run_args)
        if benchmark == "dotty" and self.version() not in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            # Before Renaissance 0.14.0, mx was manually placing all dependencies on the same classpath at build time
            # and at run time. As of Renaissance 0.14.0, we use the standalone mode which uses the classpath defined
            # in the manifest file at build time only. Dotty is a special benchmark since it also needs to know
            # this classpath at runtime to be able to perform compilations. The location of the fatjar must then be
            # explicitly passed also to the final image.
            return ["-Djava.class.path={}".format(self.standalone_jar_path(self.benchmarkName()))] + run_args
        else:
            return run_args

    def renaissance_additional_lib(self, lib):
        return mx.library(lib).get_path(True)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -r X argument from image run args
        return ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        # remove -r X argument from image run args
        if should_strip_run_args:
            extra_profile_run_args = ['-r', '1'] + mx_sdk_benchmark.strip_args_with_number('-r', user_args)
        else:
            extra_profile_run_args = user_args

        if benchmark == "dotty" and self.version() not in ["0.9.0", "0.10.0", "0.11.0", "0.12.0", "0.13.0"]:
            # Before Renaissance 0.14.0, mx was manually placing all dependencies on the same classpath at build time
            # and at run time. As of Renaissance 0.14.0, we use the standalone mode which uses the classpath defined
            # in the manifest file at build time only. Dotty is a special benchmark since it also needs to know
            # this classpath at runtime to be able to perform compilations. The location of the fatjar must then be
            # explicitly passed also to the final image.
            return ["-Djava.class.path={}".format(self.standalone_jar_path(self.benchmarkName()))] + extra_profile_run_args
        else:
            return extra_profile_run_args

    def skip_agent_assertions(self, benchmark, args):
        user_args = super(RenaissanceNativeImageBenchmarkSuite, self).skip_agent_assertions(benchmark, args)
        if user_args is not None:
            return user_args
        else:
            return []

    def build_assertions(self, benchmark, is_gate):
        build_assertions = super(RenaissanceNativeImageBenchmarkSuite, self).build_assertions(benchmark, is_gate)
        if benchmark == 'db-shootout' and is_gate:
            # We are skipping build assertions in this package due to a problem with reflective access to a fields
            # annotated with InjectAccessors (GR-36056).
            return build_assertions + ['-J-da:com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider']
        else:
            return build_assertions

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
        # use renaissance standalone mode as of renaissance 0.14.0
        return vm_args + ["-jar", self.standalone_jar_path(self.benchmarkName())] + run_args + [self.benchmarkName()]

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

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
            deps.append(os.path.join(path, f))
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
                dacapo_dat_resource = extract_archive(os.path.join(dacapo_extracted, resource), benchmark)
                dat_resource_name = os.path.splitext(os.path.basename(resource))[0]
                dacapo_dat_resources.append(os.path.join(dacapo_dat_resource, dat_resource_name))
                #collects nested jar files and classes directories
                dacapo_nested_resources += self.collect_nested_dependencies(dacapo_dat_resource)
        return dacapo_extracted, dacapo_dat_resources, dacapo_nested_resources

    def collect_unique_dependencies(self, path, benchmark, exclude_libs):
        deps = BaseDaCapoNativeImageBenchmarkSuite.collect_dependencies(path)
        # if there are more versions of the same jar, we choose one and omit remaining from the classpath
        if benchmark in exclude_libs:
            for lib in exclude_libs[benchmark]:
                lib_path = os.path.join(path, lib)
                if lib_path in deps:
                    deps.remove(os.path.join(path, lib))
        return deps


def _empty_file():
    with tempfile.NamedTemporaryFile(delete=False) as empty_file:
        empty_file.write(b"")
    return empty_file.name


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
    'h2' :      [],
    'pmd':      [],
    # org.apache.crimson.parser.Parser2 is force initialized at build-time due to non-determinism in class initialization
    # order that can lead to runtime issues. See GR-26324.
    'xalan':    ['--initialize-at-build-time=org.apache.crimson.parser.Parser2,org.apache.crimson.parser.Parser2$Catalog,org.apache.crimson.parser.Parser2$NullHandler,org.apache.xml.utils.res.CharArrayWrapper'],
    # There are two main issues with fop:
    # 1. LoggingFeature is enabled by default, causing the LogManager configuration to be parsed at build-time. However
    #    DaCapo Harness sets the `java.util.logging.config.file` property at run-time. Therefore, we set
    #    `java.util.logging.config.file` to an empty file to avoid incorrectly parsing the default log configuration,
    #    leading to output on stderr. We cannot set it to scratch/fop.log as it would normally be, because the file does
    #    not exist and would fail the benchmark when assertions are enabled.
    # 2. Native-image picks a different service provider than the JVM for javax.xml.transform.TransformerFactory.
    #    We can simply remove the jar containing that provider as it is not required for the benchmark to run.
    'fop':      [f"-Djava.util.logging.config.file={_empty_file()}",
                 '--initialize-at-run-time=org.apache.fop.render.rtf.rtflib.rtfdoc.RtfList'],
    'batik':    []
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

class DaCapoNativeImageBenchmarkSuite(mx_sdk_benchmark.DaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    '''
    Some methods in DaCapo source are modified because they relied on the jar's nested structure,
    e.g. loading all configuration files for benchmarks from a nested directory.
    Therefore, this library is built from the source.
    '''
    def name(self):
        return 'dacapo-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'dacapo'

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def availableSuiteVersions(self):
        # This version also ships a custom harness class to allow native image to find the entry point in the nested jar
        return ["9.12-MR1-git+2baec49"]

    def daCapoIterations(self):
        compiler_iterations = super(DaCapoNativeImageBenchmarkSuite, self).daCapoIterations()
        return {key: _daCapo_iterations[key] for key in compiler_iterations.keys() if key in _daCapo_iterations.keys()}

    def benchmark_resources(self, benchmark):
        return _dacapo_resources[benchmark]

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        user_args = super(DaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        # remove -n X argument from image run args
        if should_strip_run_args:
            return ['-n', '1'] + mx_sdk_benchmark.strip_args_with_number('-n', user_args)
        else:
            return user_args

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
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS


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


class ScalaDaCapoNativeImageBenchmarkSuite(mx_sdk_benchmark.ScalaDaCapoBenchmarkSuite, BaseDaCapoNativeImageBenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    def name(self):
        return 'scala-dacapo-native-image'

    def daCapoPath(self):
        lib = mx.library(self.daCapoLibraryName(), False)
        if lib:
            return lib.get_path(True)
        return None

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'scala-dacapo'

    def daCapoIterations(self):
        compiler_iterations = super(ScalaDaCapoNativeImageBenchmarkSuite, self).daCapoIterations()
        return {key: _scala_dacapo_iterations[key] for key in compiler_iterations.keys() if key in _scala_dacapo_iterations.keys()}

    def benchmark_resources(self, benchmark):
        return _scala_dacapo_resources[benchmark]

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_agent_run_arg(benchmark, args, image_run_args)
        # remove -n X argument from image run args
        return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        user_args = super(ScalaDaCapoNativeImageBenchmarkSuite, self).extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args)
        # remove -n X argument from image run args if the flag is true.
        if should_strip_run_args:
            return mx_sdk_benchmark.strip_args_with_number('-n', user_args) + ['-n', '1']
        else:
            return user_args

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
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

    @staticmethod
    def substitution_path():
        path = mx.project('com.oracle.svm.bench').classpath_repr()
        if not os.path.exists(path):
            mx.abort('Path to substitutions for scala dacapo not present: ' + path + '. Did you build all of substratevm?')
        return path


mx_benchmark.add_bm_suite(ScalaDaCapoNativeImageBenchmarkSuite())


class SpecJVM2008NativeImageBenchmarkSuite(mx_sdk_benchmark.SpecJvm2008BenchmarkSuite, mx_sdk_benchmark.NativeImageBenchmarkMixin): #pylint: disable=too-many-ancestors
    """
    SpecJVM2008 for Native Image
    """
    # disables formatted report generation since chart generation with JFreeChart loads fonts from disk (from java.home) to compute string width
    disable_rendered_report = ["-ctf", "false", "-chf", "false"]
    short_run_args = disable_rendered_report + ["-wt", "1", "-it", "1", "-ikv"]
    long_run_args = disable_rendered_report + ["-wt", "10", "-it", "5", "-ikv"]

    def name(self):
        return 'specjvm2008-native-image'

    def benchSuiteName(self, bmSuiteArgs=None):
        return 'specjvm2008'

    def run(self, benchmarks, bmSuiteArgs) -> mx_benchmark.DataPoints:
        return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        args = super().createCommandLineArgs(benchmarks, bmSuiteArgs)

        if benchmarks is None:
            mx.abort("Suite can only run a single benchmark per VM instance.")
        elif len(benchmarks) != 1:
            mx.abort("Must specify exactly one benchmark.")
        else:
            self.benchmark_name = benchmarks[0]
        return args

    def extra_agent_run_arg(self, benchmark, args, image_run_args):
        return super().extra_agent_run_arg(benchmark, args, image_run_args) + SpecJVM2008NativeImageBenchmarkSuite.short_run_args

    def extra_profile_run_arg(self, benchmark, args, image_run_args, should_strip_run_args):
        return super().extra_profile_run_arg(benchmark, args, image_run_args, should_strip_run_args) + SpecJVM2008NativeImageBenchmarkSuite.short_run_args

    def extra_image_build_argument(self, benchmark, args):
        # The reason to add `-H:CompilationExpirationPeriod` is that we encounter non-deterministic compiler crash due to expiration (GR-50701).
        return super().extra_image_build_argument(benchmark, args) + ['-H:CompilationExpirationPeriod=600']

    def extra_run_arg(self, benchmark, args, image_run_args):
        return super().extra_run_arg(benchmark, args, image_run_args) + SpecJVM2008NativeImageBenchmarkSuite.long_run_args

    def successPatterns(self):
        return super().successPatterns() + SUCCESSFUL_STAGE_PATTERNS

mx_benchmark.add_bm_suite(SpecJVM2008NativeImageBenchmarkSuite())
