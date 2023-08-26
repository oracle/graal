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
import shutil
import stat
import tempfile
from argparse import ArgumentParser
from collections import defaultdict

import mx
import mx_benchmark
import mx_sdk_vm
# noinspection PyUnresolvedReferences
import mx_wasm_benchmark  # pylint: disable=unused-import
from mx_gate import Task, add_gate_runner
import mx_unittest
from mx_unittest import unittest

_suite = mx.suite("wasm")

emcc_dir = mx.get_env("EMCC_DIR", None)
gcc_dir = mx.get_env("GCC_DIR", "")
wabt_dir = mx.get_env("WABT_DIR", None)

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
        return mx.get_jdk(tag='jvmci')
    else:
        return mx.get_jdk()

#
# Gate runners.
#

class GraalWasmDefaultTags:
    buildall = "buildall"
    wasmtest = "wasmtest"
    wasmconstantspolicytest = "wasmconstantspolicytest"
    wasmconstantspolicyextratest = "wasmconstantspolicyextratest"
    wasmextratest = "wasmextratest"
    wasmbenchtest = "wasmbenchtest"
    coverage = "coverage"


def wat2wasm_binary():
    return mx.exe_suffix("wat2wasm")


def graal_wasm_gate_runner(args, tasks):
    with Task("BuildAll", tasks, tags=[GraalWasmDefaultTags.buildall]) as t:
        if t:
            mx.build(["--all"])

    with Task("UnitTests", tasks, tags=[GraalWasmDefaultTags.wasmtest, GraalWasmDefaultTags.coverage], report=True) as t:
        if t:
            unittest(["-Dwasmtest.watToWasmExecutable=" + os.path.join(wabt_dir, wat2wasm_binary()), "WasmTestSuite"], test_report_tags={'task': t.title})
    with Task("ConstantsPolicyUnitTests", tasks, tags=[GraalWasmDefaultTags.wasmconstantspolicytest], report=True) as t:
        if t:
            unittest(["-Dwasmtest.watToWasmExecutable=" + os.path.join(wabt_dir, wat2wasm_binary()),
                      "-Dwasmtest.storeConstantsPolicy=LARGE_ONLY", "WasmTestSuite"], test_report_tags={'task': t.title})

    with Task("ExtraUnitTests", tasks, tags=[GraalWasmDefaultTags.wasmextratest, GraalWasmDefaultTags.coverage], report=True) as t:
        if t:
            unittest(["--suite", "wasm", "CSuite", "WatSuite"], test_report_tags={'task': t.title})
    with Task("ConstantsPolicyExtraUnitTests", tasks, tags=[GraalWasmDefaultTags.wasmconstantspolicyextratest], report=True) as t:
        if t:
            unittest(["--suite", "wasm", "-Dwasmtest.storeConstantsPolicy=LARGE_ONLY", "CSuite", "WatSuite"], test_report_tags={'task': t.title})

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

def _unittest_config_participant(config):
    (vmArgs, mainClass, mainClassArgs) = config
    # limit heap memory to 2G, unless otherwise specified
    if not any(a.startswith('-Xm') for a in vmArgs):
        vmArgs += ['-Xmx2g']
    return (vmArgs, mainClass, mainClassArgs)

mx_unittest.add_config_participant(_unittest_config_participant)


#
# Project types.
#

benchmark_methods = [
    "_benchmarkIterationsCount",
    "_benchmarkSetupOnce",
    "_benchmarkSetupEach",
    "_benchmarkTeardownEach",
    "_benchmarkRun",
    "_main"
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
        if not wabt_dir:
            mx.abort("No WABT_DIR specified - the source programs will not be compiled to .wasm.")
        wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")

        wat2wasm_version_cmd = [wat2wasm_cmd] + ["--version"]
        out = mx.OutputCapture()
        bulk_memory_option = None
        mx.run(wat2wasm_version_cmd, nonZeroIsFatal=False, out=out)
        wat2wasm_version = str(out.data).split(".")
        major = int(wat2wasm_version[0])
        build = int(wat2wasm_version[2])
        if major <= 1 and build <= 24:
            bulk_memory_option = "--enable-bulk-memory"

        mx.log("Building files from the source dir: " + source_dir)
        for root, filename in self.subject.getProgramSources():
            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx.ensure_dir_exists(os.path.join(output_dir, subdir))

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

    def build(self):
        source_dir = self.subject.getSourceDir()
        output_dir = self.subject.getOutputDir()
        if not emcc_dir:
            mx.abort("No EMCC_DIR specified - the source programs will not be compiled to .wasm.")
        emcc_cmd = os.path.join(emcc_dir, "emcc")
        gcc_cmd = os.path.join(gcc_dir, "gcc")
        if mx.run([emcc_cmd, "-v"], nonZeroIsFatal=False) != 0:
            mx.abort("Could not check the emcc version.")
        if mx.run([gcc_cmd, "--version"], nonZeroIsFatal=False) != 0:
            mx.abort("Could not check the gcc version.")
        if not wabt_dir:
            mx.abort("Set WABT_DIR if you want the binary to include .wat files.")
        mx.log("Building files from the source dir: " + source_dir)
        cc_flags = ["-g2", "-O3"]
        include_flags = []
        if hasattr(self.project, "includeset"):
            include_flags = ["-I", os.path.join(_suite.dir, "includes", self.project.includeset)]
        emcc_flags = ["-s", "EXIT_RUNTIME=1", "-s", "STANDALONE_WASM", "-s", "WASM_BIGINT"] + cc_flags
        if self.project.isBenchmarkProject():
            emcc_flags = emcc_flags + ["-s", "EXPORTED_FUNCTIONS=" + str(self.benchmark_methods()).replace("'", "\"") + ""]
        subdir_program_names = defaultdict(lambda: [])
        for root, filename in self.subject.getProgramSources():
            if filename.startswith("_"):
                # Ignore files starting with an underscore
                continue

            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx.ensure_dir_exists(os.path.join(output_dir, subdir))

            basename = remove_extension(filename)
            source_path = os.path.join(root, filename)
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            output_js_path = os.path.join(output_dir, subdir, basename + ".js")
            timestampedSource = mx.TimeStampFile(source_path)
            timestampedOutput = mx.TimeStampFile(output_wasm_path)
            mustRebuild = timestampedSource.isNewerThan(timestampedOutput) or not timestampedOutput.exists()

            # Step 1: build the .wasm binary.
            if mustRebuild:
                if filename.endswith(".c"):
                    # This generates both a js file and a wasm file.
                    # See https://github.com/emscripten-core/emscripten/wiki/WebAssembly-Standalone
                    build_cmd_line = [emcc_cmd] + emcc_flags + [source_path, "-o", output_js_path] + include_flags
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not build the wasm-only output of " + filename + " with emcc.")
                elif filename.endswith(".wat"):
                    # Step 1: compile the .wat file to .wasm.
                    wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")
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
                    wasm2wat_cmd = os.path.join(wabt_dir, "wasm2wat")
                    if mx.run([wasm2wat_cmd, "-o", output_wat_path, output_wasm_path], nonZeroIsFatal=False) != 0:
                        mx.abort("Could not compile .wat file for " + filename)
                elif filename.endswith(".wat"):
                    # Step 4: copy the .wat file, for easier debugging.
                    wat_path = os.path.join(root, basename + ".wat")
                    shutil.copyfile(wat_path, output_wat_path)

            # Step 5: if this is a benchmark project, create native binaries too.
            if mustRebuild:
                if filename.endswith(".c"):
                    mx.ensure_dir_exists(os.path.join(output_dir, subdir, NATIVE_BENCH_DIR))
                    output_path = os.path.join(output_dir, subdir, NATIVE_BENCH_DIR, mx.exe_suffix(basename))
                    link_flags = ["-lm"]
                    gcc_cmd_line = [gcc_cmd] + cc_flags + [source_path, "-o", output_path] + include_flags + link_flags
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


#
# Launchers and other components.
#


mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name="GraalWasm",
    short_name="gwa",
    dir_name="wasm",
    standalone_dir_name='graalwasm-community-<version>-<graalvm_os>-<arch>',
    standalone_dir_name_enterprise='graalwasm-<version>-<graalvm_os>-<arch>',
    standalone_dependencies={
        'gwal': ('', []), # GraalWasm license files
    },
    standalone_dependencies_enterprise={
        'gwal': ('', []), # GraalWasm license files
        'GraalVM enterprise license files': ('LICENSE.txt', ['GRAALVM-README.md']),
    },
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
    installable=True,
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
    installable=True,
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
    parser.add_argument('--local', action='store_true', help='Generates config file for local dev environment')
    args = parser.parse_args(args)
    config_path = os.path.join(os.getcwd(), args.config_path)
    emsdk_path = args.emsdk_path

    llvm_root = os.path.join(emsdk_path, "llvm", "git", "build_master_64", "bin")
    binaryen_root = os.path.join(emsdk_path, "binaryen", "master_64bit_binaryen")
    emscripten_root = os.path.join(emsdk_path, "emscripten", "master")
    node_js = os.path.join(emsdk_path, "node", "12.9.1_64bit", "bin", "node")

    if args.local:
        llvm_root = os.path.join(emsdk_path, "upstream", "bin")
        binaryen_root = os.path.join(emsdk_path, "upstream", "lib")
        emscripten_root = os.path.join(emsdk_path, "upstream", "emscripten")
        node_js = os.path.join(emsdk_path, "node", "14.15.5_64bit", "bin", "node")

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
    vmArgs, wasmArgs = mx.extract_VM_args(args, useDoubleDash=False, defaultAllVMArgs=False)
    path_args = mx.get_runtime_jvm_args([
        "TRUFFLE_API",
        "org.graalvm.wasm",
        "org.graalvm.wasm.launcher",
    ] + (['tools:CHROMEINSPECTOR', 'tools:TRUFFLE_PROFILER', 'tools:INSIGHT'] if mx.suite('tools', fatalIfMissing=False) is not None else []))
    return mx.run_java(vmArgs + path_args + ["org.graalvm.wasm.launcher.WasmLauncher"] + wasmArgs, jdk=get_jdk(), **kwargs)

@mx.command(_suite.name, "wasm-memory-layout")
def wasm_memory_layout(args, **kwargs):
    """Run WebAssembly memory layout extractor."""
    vmArgs, wasmArgs = mx.extract_VM_args(args, useDoubleDash=False, defaultAllVMArgs=False)
    path_args = mx.get_runtime_jvm_args([
        "org.graalvm.wasm",
        "org.graalvm.wasm.memory",
    ])
    return mx.run_java(vmArgs + path_args + ["org.graalvm.wasm.memory.MemoryLayoutRunner"] + wasmArgs, jdk=get_jdk(), **kwargs)
