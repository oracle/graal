#
# Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import os
import pathlib
import re
import shutil
import stat
import tempfile
from argparse import ArgumentParser
from collections import defaultdict

import mx
import mx_benchmark
import mx_sdk_vm
import mx_sdk_vm_ng
import mx_truffle
import mx_unittest
import mx_util
# noinspection PyUnresolvedReferences
import mx_wasm_benchmark  # pylint: disable=unused-import
from mx_gate import Task, add_gate_runner
from mx_unittest import unittest

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_vm_ng import StandaloneLicenses, ThinLauncherProject, LanguageLibraryProject, DynamicPOMDistribution, DeliverableStandaloneArchive  # pylint: disable=unused-import

_suite = mx.suite("wasm")

emcc_dir = mx.get_env("EMCC_DIR", None)
gcc_dir = mx.get_env("GCC_DIR", "")
wabt_dir = mx.get_env("WABT_DIR", "")

NODE_BENCH_DIR = "node"
NATIVE_BENCH_DIR = "native"

microbenchmarks = [
    "cdf",
    "digitron",
    "event-sim",
    "fft",
    "fib-vm",
    "hash-join",
    "merge-join",
    "phong",
    "qsort",
    "strings",
]

def get_jdk(forBuild=False):
    if not forBuild and mx.suite('compiler', fatalIfMissing=False):
        return mx.get_jdk(tag='graalvm')
    else:
        return mx.get_jdk()

# Called from suite.py
def graalwasm_standalone_deps():
    include_truffle_runtime = not mx.env_var_to_bool("EXCLUDE_TRUFFLE_RUNTIME")
    return mx_truffle.resolve_truffle_dist_names(use_optimized_runtime=include_truffle_runtime)

def libwasmvm_build_args():
    image_build_args = []
    if mx_sdk_vm_ng.get_bootstrap_graalvm_jdk_version() < mx.VersionSpec("25"):
        image_build_args.extend([
            '--exclude-config',
            r'wasm\.jar',
            r'META-INF/native-image/org\.graalvm\.wasm/wasm-language/native-image\.properties',
            '--initialize-at-build-time=org.graalvm.wasm',
            '-H:MaxRuntimeCompileMethods=2000',
        ])
    return image_build_args

#
# Gate runners.
#

class GraalWasmDefaultTags:
    buildall = "buildall"
    wasmtest = "wasmtest"
    wasmextratest = "wasmextratest"
    wasmbenchtest = "wasmbenchtest"
    coverage = "coverage"

def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    if register_project and register_distribution:
        isolate_build_options = []
        meta_pom = [p for p in _suite.dists if p.name == 'WASM_POM'][0]
        mx_truffle.register_polyglot_isolate_distributions(_suite, register_project, register_distribution,'wasm',
                                        'src', meta_pom.name, meta_pom.maven_group_id(), meta_pom.theLicense,
                                        isolate_build_options=isolate_build_options)


def wabt_test_args():
    if not wabt_dir:
        mx.warn("No WABT_DIR specified")
        return []
    return ["-Dwasmtest.watToWasmExecutable=" + os.path.join(wabt_dir, mx.exe_suffix("wat2wasm")), "-Dwasmtest.watToWasmVerbose=true"]


def graal_wasm_gate_runner(args, tasks):
    unittest_args = []
    if mx.suite('compiler', fatalIfMissing=False) is not None:
        unittest_args = ["--use-graalvm"]

    with Task("BuildAll", tasks, tags=[GraalWasmDefaultTags.buildall]) as t:
        if t:
            mx.build(["--all"])

    with Task("UnitTests", tasks, tags=[GraalWasmDefaultTags.wasmtest], report=True) as t:
        if t:
            unittest(unittest_args + [*wabt_test_args(), "WasmTestSuite"], test_report_tags={'task': t.title})
            unittest(unittest_args + [*wabt_test_args(), "-Dwasmtest.sharedEngine=true", "WasmTestSuite"], test_report_tags={'task': t.title})

    with Task("ExtraUnitTests", tasks, tags=[GraalWasmDefaultTags.wasmextratest], report=True) as t:
        if t:
            unittest(unittest_args + ["--suite", "wasm", "CSuite", "WatSuite"], test_report_tags={'task': t.title})

    with Task("CoverageTests", tasks, tags=[GraalWasmDefaultTags.coverage], report=True) as t:
        if t:
            unittest(unittest_args + [*wabt_test_args(), "-Dwasmtest.coverageMode=true", "WasmTestSuite"], test_report_tags={'task': t.title})
            unittest(unittest_args + [*wabt_test_args(), "-Dwasmtest.coverageMode=true", "-Dwasmtest.sharedEngine=true", "WasmTestSuite"], test_report_tags={'task': t.title})
            unittest(unittest_args + ["-Dwasmtest.coverageMode=true", "--suite", "wasm", "CSuite", "WatSuite"], test_report_tags={'task': t.title})

    # This is a gate used to test that all the benchmarks return the correct results. It does not upload anything,
    # and does not run on a dedicated machine.
    with Task("BenchTest", tasks, tags=[GraalWasmDefaultTags.wasmbenchtest]) as t:
        if t:
            for b in microbenchmarks:
                exitcode = mx_benchmark.benchmark([
                        "wasm:WASM_BENCHMARKCASES", "--",
                        "--jvm", "server", "--jvm-config", "graal-core",
                        "-Dwasmbench.benchmarkName=" + b, "--",
                        "CMicroBenchmarkSuite", "-wi", "1", "-i", "1"])
                if exitcode != 0:
                    mx.abort("Errors during benchmark tests, aborting.")


add_gate_runner(_suite, graal_wasm_gate_runner)


class WasmUnittestConfig(mx_unittest.MxUnittestConfig):

    def __init__(self):
        super(WasmUnittestConfig, self).__init__('wasm')

    def apply(self, config):
        (vmArgs, mainClass, mainClassArgs) = config
        # Disable DefaultRuntime warning
        vmArgs += ['-Dpolyglot.engine.WarnInterpreterOnly=false']
        vmArgs += ['-Dpolyglot.engine.AllowExperimentalOptions=true']
        # This is needed for Truffle since JEP 472: Prepare to Restrict the Use of JNI
        mx_truffle.enable_truffle_native_access(vmArgs)
        # GR-59703: This is needed for Truffle since JEP 498: Warn upon Use of Memory-Access Methods in sun.misc.Unsafe
        mx_truffle.enable_sun_misc_unsafe(vmArgs)
        # Assert for enter/return parity of ProbeNode (if assertions are enabled only)
        if next((arg.startswith('-e') for arg in reversed(vmArgs) if arg in ['-ea', '-da', '-enableassertions', '-disableassertions']), False):
            vmArgs += ['-Dpolyglot.engine.AssertProbes=true']
        # limit heap memory to 4G, unless otherwise specified
        if not any(a.startswith('-Xm') for a in vmArgs):
            vmArgs += ['-Xmx4g']
        # Export GraalWasm implementation to JUnit test runner
        mainClassArgs += ['-JUnitOpenPackages', 'org.graalvm.wasm/*=org.graalvm.wasm.test']
        mainClassArgs += ['-JUnitOpenPackages', 'org.graalvm.wasm/*=com.oracle.truffle.wasm.closedtestcases']
        mainClassArgs += ['-JUnitOpenPackages', 'org.graalvm.wasm/*=com.oracle.truffle.wasm.debugtests']
        return (vmArgs, mainClass, mainClassArgs)

    def processDeps(self, deps):
        super().processDeps(deps)
        truffle_runtime_dist_names = mx_truffle.resolve_truffle_dist_names(use_optimized_runtime=True, use_enterprise=True)
        mx.logv(f"Adding Truffle runtime distributions {', '.join(truffle_runtime_dist_names)} to unittest dependencies.")
        deps.update((mx.distribution(d) for d in truffle_runtime_dist_names))


mx_unittest.register_unittest_config(WasmUnittestConfig())

#
# Project types.
#

benchmark_methods = [
    "_benchmarkIterationsCount",
    "_benchmarkSetupOnce",
    "_benchmarkSetupEach",
    "_benchmarkTeardownEach",
    "_benchmarkRun",
]


def remove_extension(filename):
    if filename.endswith(".c"):
        return filename[:-2]
    if filename.endswith(".wat"):
        return filename[:-4]
    if filename.endswith(".wasm"):
        return filename[:-5]
    else:
        mx.abort("Unknown extension: " + filename)


class GraalWasmProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, subDir, theLicense, **args):
        mx.ArchivableProject.__init__(self, suite, name, deps, workingSets, theLicense, **args)
        self.subDir = subDir

    def getSourceDir(self):
        src_dir = os.path.join(self.dir, self.subDir, self.name, "src")
        return src_dir

    def getOutputDir(self):
        return os.path.join(self.get_output_base(), self.name)

    def output_dir(self):
        return self.getOutputDir()

    def archive_prefix(self):
        return ""

    def isBenchmarkProject(self):
        if hasattr(self, "includeset"):
            return self.includeset == "bench"
        return False


class GraalWasmBuildTask(mx.ProjectBuildTask):
    def __init__(self, project, args, output_base):
        self.output_base = output_base
        self.project = project
        mx.ProjectBuildTask.__init__(self, args, 1, project)

    def newestOutput(self):
        return mx.TimeStampFile.newest(self.subject.getResults())

    def needsBuild(self, newestInput):
        is_needed, reason = super(GraalWasmBuildTask, self).needsBuild(newestInput)
        if is_needed:
            return True, reason

        ts_newest_source = mx.TimeStampFile.newest([os.path.join(root, f) for root, f in self.subject.getSources()])
        for result in self.subject.getResults():
            tsResult = mx.TimeStampFile(result)
            if tsResult.isOlderThan(ts_newest_source):
                return (True, "File " + result + " is older than the newest source file " + str(ts_newest_source))

        return (False, "Build outputs are up-to-date.")

class WatProject(GraalWasmProject):
    def __init__(self, suite, name, deps, workingSets, subDir, theLicense, **args):
        GraalWasmProject.__init__(self, suite, name, deps, workingSets, subDir, theLicense, **args)
        self.subDir = subDir

    def getProgramSources(self):
        for root, _, files in os.walk(self.getSourceDir()):
            for filename in files:
                if filename.endswith(".wat"):
                    yield (root, filename)

    def getSources(self):
        for root, filename in self.getProgramSources():
            yield (root, filename)

    def getResults(self):
        output_dir = self.getOutputDir()
        for root, filename in self.getProgramSources():
            subdir = os.path.relpath(root, self.getSourceDir())
            build_output_name = lambda ext: os.path.join(output_dir, subdir, remove_extension(filename) + ext)
            yield build_output_name(".wat")
            yield build_output_name(".wasm")

    def getBuildTask(self, args):
        output_base = self.get_output_base()
        return WatBuildTask(self, args, output_base)


class WatBuildTask(GraalWasmBuildTask):
    def __init__(self, project, args, output_base):
        GraalWasmBuildTask.__init__(self, project, args, output_base)

    def __str__(self):
        return 'Building {} with WABT'.format(self.subject.name)

    def build(self):
        source_dir = self.subject.getSourceDir()
        output_dir = self.subject.getOutputDir()

        wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")
        out = mx.OutputCapture()
        bulk_memory_option = None
        if mx.run([wat2wasm_cmd, "--version"], nonZeroIsFatal=False, out=out) != 0:
            if not wabt_dir:
                mx.warn("No WABT_DIR specified.")
            mx.abort("Could not check the wat2wasm version.")

        try:
            wat2wasm_version = re.match(r'^(\d+)\.(\d+)(?:\.(\d+))?', str(out.data)).groups()

            major, minor, build = wat2wasm_version
            if int(major) == 1 and int(minor) == 0 and int(build) <= 24:
                bulk_memory_option = "--enable-bulk-memory"
        except:
            mx.warn(f"Could not parse wat2wasm version. Output: '{out.data}'")

        mx.log("Building files from the source dir: " + source_dir)
        for root, filename in self.subject.getProgramSources():
            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx_util.ensure_dir_exists(os.path.join(output_dir, subdir))

            basename = remove_extension(filename)
            source_path = os.path.join(root, filename)
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            output_wat_path = os.path.join(output_dir, subdir, basename + ".wat")
            timestamped_source = mx.TimeStampFile(source_path)
            timestamped_output = mx.TimeStampFile(output_wasm_path)
            must_rebuild = timestamped_source.isNewerThan(timestamped_output) or not timestamped_output.exists()

            if must_rebuild:
                build_cmd_line = [wat2wasm_cmd] + [source_path, "-o", output_wasm_path]
                if bulk_memory_option is not None:
                    build_cmd_line += [bulk_memory_option]
                if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                    mx.abort("Could not build the wasm binary of '" + filename + "' with wat2wasm.")
                shutil.copyfile(source_path, output_wat_path)

    def clean(self, forBuild=False):
        if forBuild:
            output_dir = self.subject.getOutputDir()
            for root, filename in self.subject.getProgramSources():
                output_wasm = mx.TimeStampFile(os.path.join(output_dir, remove_extension(filename) + ".wasm"))
                if mx.TimeStampFile(os.path.join(root, filename)).isNewerThan(output_wasm):
                    mx.logv(str(output_wasm) + " is older than " + os.path.join(root, filename) + ", removing.")
                    os.remove(output_wasm.path)
        else:
            mx.rmtree(self.subject.output_dir(), ignore_errors=True)


class EmscriptenProject(GraalWasmProject):
    def __init__(self, suite, name, deps, workingSets, subDir, theLicense, **args):
        GraalWasmProject.__init__(self, suite, name, deps, workingSets, subDir, theLicense, **args)
        self.subDir = subDir

    def getProgramSources(self):
        for root, _, files in os.walk(self.getSourceDir()):
            for filename in files:
                if filename.endswith(".c"):
                    yield (root, filename)
                if filename.endswith(".wat"):
                    yield (root, filename)
                if filename.endswith(".wasm"):
                    yield (root, filename)

    def getSources(self):
        for root, filename in self.getProgramSources():
            yield (root, filename)
            result_file = remove_extension(filename) + ".result"
            # The result files may be optional in some cases.
            if os.path.isfile(os.path.join(root, result_file)):
                yield (root, result_file)
            opts_file = remove_extension(filename) + ".opts"
            # Some benchmarks may specify custom options.
            if os.path.isfile(os.path.join(root, opts_file)):
                yield (root, opts_file)

    def getResults(self):
        output_dir = self.getOutputDir()
        subdirs = set()
        for root, filename in self.getProgramSources():
            subdir = os.path.relpath(root, self.getSourceDir())
            subdirs.add(subdir)
            build_output_name = lambda ext: os.path.join(output_dir, subdir, remove_extension(filename) + ext)
            native_build_output_name = lambda ext: os.path.join(output_dir, subdir, NATIVE_BENCH_DIR, remove_extension(filename) + ext)

            result_path = os.path.join(root, remove_extension(filename) + ".result")
            # The result files may be optional in some cases.
            if os.path.isfile(result_path):
                yield build_output_name(".result")
            opts_path = os.path.join(root, remove_extension(filename) + ".opts")
            # Some benchmarks may specify custom options.
            if os.path.isfile(opts_path):
                yield build_output_name(".opts")

            # A binary WebAssembly file contains the program.
            yield build_output_name(".wasm")

            if filename.endswith(".c") or filename.endswith(".wat"):
                # Textual WebAssembly file is included for convenience.
                yield build_output_name(".wat")

            if filename.endswith(".c"):
                yield build_output_name(".js")
                # If benchmark is compiled from C code, we will produce Node and GCC binaries.
                if self.isBenchmarkProject():
                    # The raw binary is used to run the program directly.
                    yield native_build_output_name(mx.exe_suffix(""))
        for subdir in subdirs:
            yield os.path.join(output_dir, subdir, "wasm_test_index")

    def getBuildTask(self, args):
        output_base = self.get_output_base()
        return EmscriptenBuildTask(self, args, output_base)


class EmscriptenBuildTask(GraalWasmBuildTask):
    def __init__(self, project, args, output_base):
        GraalWasmBuildTask.__init__(self, project, args, output_base)

    def __str__(self):
        return 'Building {} with Emscripten'.format(self.subject.name)

    def benchmark_methods(self):
        return benchmark_methods

    def test_methods(self, opts_path):
        if not os.path.isfile(opts_path):
            return []
        with open(opts_path) as opts_file:
            for line in opts_file:
                line = line.strip()
                if line.startswith("entry-point"):
                    _, value = line.split("=", 1)
                    return ['_' + value.strip()]
        return []

    def build(self):
        source_dir = self.subject.getSourceDir()
        output_dir = self.subject.getOutputDir()
        if not emcc_dir:
            mx.abort("No EMCC_DIR specified - the source programs will not be compiled to .wasm.")
        emcc_cmd = os.path.join(emcc_dir, "emcc")
        gcc_cmd = os.path.join(gcc_dir, "gcc")
        wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")
        wasm2wat_cmd = os.path.join(wabt_dir, "wasm2wat")
        if mx.run([emcc_cmd, "-v"], nonZeroIsFatal=False) != 0:
            mx.abort("Could not check the emcc version.")
        if mx.run([gcc_cmd, "--version"], nonZeroIsFatal=False) != 0:
            mx.abort("Could not check the gcc version.")
        if mx.run([wat2wasm_cmd, "--version"], nonZeroIsFatal=False) != 0:
            if not wabt_dir:
                mx.warn("No WABT_DIR specified.")
            mx.abort("Could not check the wat2wasm version.")

        mx.log("Building files from the source dir: " + source_dir)
        cc_flags = ["-g2", "-O3"]
        include_flags = []
        if hasattr(self.project, "includeset"):
            include_flags = ["-I", os.path.join(_suite.dir, "includes", self.project.includeset)]
        emcc_flags = ["-s", "STANDALONE_WASM", "-s", "WASM_BIGINT"] + cc_flags
        subdir_program_names = defaultdict(lambda: [])
        for root, filename in self.subject.getProgramSources():
            if filename.startswith("_"):
                # Ignore files starting with an underscore
                continue

            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx_util.ensure_dir_exists(os.path.join(output_dir, subdir))

            basename = remove_extension(filename)
            source_path = os.path.join(root, filename)
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            output_js_path = os.path.join(output_dir, subdir, basename + ".js")
            timestampedSource = mx.TimeStampFile(source_path)
            timestampedOutput = mx.TimeStampFile(output_wasm_path)
            mustRebuild = timestampedSource.isNewerThan(timestampedOutput) or not timestampedOutput.exists()

            source_cc_flags = []
            native_bench = True
            if filename.endswith(".c"):
                with open(source_path) as f:
                    source_file = f.read()
                    for flags in re.findall(r'//\s*CFLAGS\s*=\s*(.*)\n', source_file):
                        source_cc_flags.extend(flags.split())
                    native_bench_option = re.search(r'//\s*NATIVE_BENCH\s*=\s*(.*)\n', source_file)
                    if native_bench_option:
                        native_bench = native_bench_option.group(1).lower() == "true"

            # Step 1: build the .wasm binary.
            if mustRebuild:
                if filename.endswith(".c"):
                    if self.project.isBenchmarkProject():
                        emcc_export_flags = ["-s", "EXPORTED_FUNCTIONS=" + str(self.benchmark_methods()).replace("'", "\"") + ""]
                    else:
                        emcc_export_flags = ["-s", "EXPORTED_FUNCTIONS=" + str(self.test_methods(os.path.join(root, basename + ".opts"))).replace("'", "\"") + ""]
                    # This generates both a js file and a wasm file.
                    # See https://github.com/emscripten-core/emscripten/wiki/WebAssembly-Standalone
                    build_cmd_line = [emcc_cmd] + emcc_flags + emcc_export_flags + source_cc_flags + [source_path, "-o", output_js_path] + include_flags
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not build the wasm-only output of " + filename + " with emcc.")
                elif filename.endswith(".wat"):
                    # Step 1: compile the .wat file to .wasm.
                    build_cmd_line = [wat2wasm_cmd, "-o", output_wasm_path, source_path]
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not translate " + filename + " to binary format.")
                elif filename.endswith(".wasm"):
                    shutil.copyfile(source_path, output_wasm_path)

            else:
                mx.logv("skipping, file is up-to-date: " + source_path)

            # Step 2: copy the result file if it exists.
            result_path = os.path.join(root, basename + ".result")
            if os.path.isfile(result_path):
                result_output_path = os.path.join(output_dir, subdir, basename + ".result")
                shutil.copyfile(result_path, result_output_path)

            # Step 3: copy the opts file if it exists.
            opts_path = os.path.join(root, basename + ".opts")
            if os.path.isfile(opts_path):
                opts_output_path = os.path.join(output_dir, subdir, basename + ".opts")
                shutil.copyfile(opts_path, opts_output_path)

            output_wat_path = os.path.join(output_dir, subdir, basename + ".wat")
            if mustRebuild:
                if filename.endswith(".c"):
                    # Step 4: produce the .wat files, for easier debugging.
                    if mx.run([wasm2wat_cmd, "-o", output_wat_path, output_wasm_path], nonZeroIsFatal=False) != 0:
                        mx.abort("Could not compile .wat file for " + filename)
                elif filename.endswith(".wat"):
                    # Step 4: copy the .wat file, for easier debugging.
                    wat_path = os.path.join(root, basename + ".wat")
                    shutil.copyfile(wat_path, output_wat_path)

            # Step 5: if this is a benchmark project, create native binaries too.
            if mustRebuild:
                if filename.endswith(".c") and native_bench:
                    mx_util.ensure_dir_exists(os.path.join(output_dir, subdir, NATIVE_BENCH_DIR))
                    output_path = os.path.join(output_dir, subdir, NATIVE_BENCH_DIR, mx.exe_suffix(basename))
                    link_flags = ["-lm"]
                    gcc_cmd_line = [gcc_cmd] + cc_flags + source_cc_flags + [source_path, "-o", output_path] + include_flags + link_flags
                    if mx.run(gcc_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not build the native binary of " + filename + ".")
                    os.chmod(output_path, stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR)
                elif filename.endswith(".wat"):
                    mx.warn("The .wat files are not translated to native binaries: " + filename)

            # Remember the source name.
            subdir_program_names[subdir].append(basename)
        for subdir in subdir_program_names:
            with open(os.path.join(output_dir, subdir, "wasm_test_index"), "w") as f:
                for name in subdir_program_names[subdir]:
                    f.write(name)
                    f.write("\n")

    def to_int(self, string):
        fv = float(string)
        iv = int(fv)
        if float(iv) != fv:
            mx.abort("Cannot parse initialization directive: " + string)
        return iv

    def clean(self, forBuild=False):
        if forBuild:
            output_dir = self.subject.getOutputDir()
            for root, filename in self.subject.getProgramSources():
                output_wasm = mx.TimeStampFile(os.path.join(output_dir, remove_extension(filename) + ".wasm"))
                if mx.TimeStampFile(os.path.join(root, filename)).isNewerThan(output_wasm):
                    mx.logv(str(output_wasm) + " is older than " + os.path.join(root, filename) + ", removing.")
                    os.remove(output_wasm.path)
        else:
            mx.rmtree(self.subject.output_dir(), ignore_errors=True)


class GraalVmWatProject(WatProject):
    def getSourceDir(self):
        return os.path.join(self.dir, self.subDir)

    def isBenchmarkProject(self):
        return True

#
# Launchers and other components.
#


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name="GraalWasm",
    short_name="gwa",
    dir_name="wasm",
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'gwal', # GraalWasm license files
        "Truffle",
    ],
    truffle_jars=["wasm:WASM"],
    support_distributions=["wasm:WASM_GRAALVM_SUPPORT"],
    library_configs=[
        mx_sdk_vm.LanguageLibraryConfig(
            launchers=["bin/<exe:wasm>"],
            jar_distributions=["wasm:WASM_LAUNCHER"],
            main_class="org.graalvm.wasm.launcher.WasmLauncher",
            build_args=[],
            language="wasm",
        ),
    ],
    stability="experimental",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='GraalWasm license files',
    short_name='gwal',
    dir_name='wasm',
    license_files=["LICENSE_WASM.txt"],
    third_party_license_files=[],
    dependencies=[],
    truffle_jars=[],
    support_distributions=["wasm:WASM_GRAALVM_LICENSES"],
    priority=5,
    stability="experimental",
))


#
# Mx commands.
#


@mx.command(_suite.name, "emscripten-init")
def emscripten_init(args):
    """Initialize the Emscripten environment."""
    parser = ArgumentParser(prog='mx emscripten-init', description='initialize the Emscripten environment.')
    parser.add_argument('config_path', help='path of the config file to be generated')
    parser.add_argument('emsdk_path', help='path of the emsdk')

    path_mode_group = parser.add_mutually_exclusive_group()
    path_mode_group.add_argument('--detect', action='store_true', help='Try to detect the necessary directories in the emsdk automatically')
    path_mode_group.add_argument('--local', action='store_true', help='Generates config file for local dev environment')
    args = parser.parse_args(args)
    config_path = os.path.join(os.getcwd(), args.config_path)
    emsdk_path = args.emsdk_path

    llvm_root = os.path.join(emsdk_path, "upstream", "bin")
    binaryen_root = os.path.join(emsdk_path, "binaryen", "main_64bit_binaryen")
    emscripten_root = os.path.join(emsdk_path, "upstream", "emscripten")
    node_js = os.path.join(emsdk_path, "node", "22.16.0_64bit", "bin", "node")

    def find_executable(exe_name):
        for root, _, files in os.walk(args.emsdk_path):
            if exe_name in files:
                full_path = pathlib.Path(root, exe_name)
                if os.access(full_path, os.X_OK):
                    return full_path

        mx.abort(f"Unable to find {exe_name} in {args.emsdk_path}")

    if args.detect:
        llvm_root = str(find_executable("llvm-ar").parent)
        binaryen_root = str(find_executable("binaryen-lit").parent.parent)
        emscripten_root = str(find_executable("emcc").parent)
        node_js = str(find_executable("node"))

    if args.local:
        llvm_root = os.path.join(emsdk_path, "upstream", "bin")
        binaryen_root = os.path.join(emsdk_path, "upstream", "lib")
        emscripten_root = os.path.join(emsdk_path, "upstream", "emscripten")
        node_js = os.path.join(emsdk_path, "node", "22.16.0_64bit", "bin", "node")

    mx.log("Generating Emscripten configuration...")
    mx.log("Config file path:    " + str(config_path))
    mx.log("Emscripten SDK path: " + str(emsdk_path))

    with open(config_path, "w") as fp:
        fp.write("LLVM_ROOT='" + llvm_root + "'" + os.linesep)
        fp.write("BINARYEN_ROOT='" + binaryen_root + "'" + os.linesep)
        fp.write("EMSCRIPTEN_ROOT='" + emscripten_root + "'" + os.linesep)
        fp.write("NODE_JS='" + node_js + "'" + os.linesep)
        fp.write("COMPILER_ENGINE=NODE_JS" + os.linesep)
        fp.write("JS_ENGINES=[NODE_JS]")

    mx.log("Successfully generated Emscripten config file at " + str(config_path))
    mx.log("Triggering cache generation...")

    temp_dir = tempfile.mkdtemp()
    test_file = os.path.join(temp_dir, "test.c")
    with open(test_file, "w") as fp:
        fp.write("int main() { return 0; }")
    cmd = os.path.join(emscripten_root, "emcc")

    if mx.run([cmd, test_file], nonZeroIsFatal=True) != 0:
        mx.abort("Error while triggering cache generation")
    shutil.rmtree(temp_dir)
    mx.log("Successfully initialized Emscripten")


@mx.command(_suite.name, "wasm")
def wasm(args, **kwargs):
    """Run a WebAssembly program."""

    vmArgs, wasmArgs = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    # This is needed for Truffle since JEP 472: Prepare to Restrict the Use of JNI
    vmArgs += ['--enable-native-access=org.graalvm.truffle']
    # GR-59703: This is needed for Truffle since JEP 498: Warn upon Use of Memory-Access Methods in sun.misc.Unsafe
    mx_truffle.enable_sun_misc_unsafe(vmArgs)

    path_args = mx.get_runtime_jvm_args([
        *mx_truffle.resolve_truffle_dist_names(use_optimized_runtime=True, use_enterprise=True),
        "WASM",
        "WASM_LAUNCHER",
    ] + (['tools:CHROMEINSPECTOR', 'tools:TRUFFLE_PROFILER', 'tools:INSIGHT'] if mx.suite('tools', fatalIfMissing=False) is not None else []))

    main_dist = mx.distribution('WASM_LAUNCHER')
    main_class_arg = '--module=' + main_dist.get_declaring_module_name() + '/' + main_dist.mainClass if main_dist.use_module_path() else main_dist.mainClass
    return mx.run_java(vmArgs + path_args + [main_class_arg] + wasmArgs, jdk=get_jdk(), **kwargs)

@mx.command(_suite.name, "wasm-memory-layout")
def wasm_memory_layout(args, **kwargs):
    """Run WebAssembly memory layout extractor."""
    vmArgs, wasmArgs = mx.extract_VM_args(args, useDoubleDash=False, defaultAllVMArgs=False)
    path_args = mx.get_runtime_jvm_args([
        "org.graalvm.wasm",
        "org.graalvm.wasm.memory",
    ])
    return mx.run_java(vmArgs + path_args + ["org.graalvm.wasm.memory.MemoryLayoutRunner"] + wasmArgs, jdk=get_jdk(), **kwargs)
