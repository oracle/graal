#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import mx
import mx_benchmark
import mx_sdk_vm
import mx_truffle
import mx_wasm_benchmark  # pylint: disable=unused-import

import os
import re
import shutil
import stat

from collections import defaultdict
from mx_gate import Task, add_gate_runner
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
    "hash-join",
    "merge-join",
    "phong",
    "qsort",
    "strings",
]


#
# Gate runners.
#

class GraalWasmDefaultTags:
    buildall = "buildall"
    wasmtest = "wasmtest"
    wasmextratest = "wasmextratest"
    wasmbenchtest = "wasmbenchtest"


def graal_wasm_gate_runner(args, tasks):
    with Task("BuildAll", tasks, tags=[GraalWasmDefaultTags.buildall]) as t:
        if t:
            mx.build(["--all"])
    with Task("UnitTests", tasks, tags=[GraalWasmDefaultTags.wasmtest]) as t:
        if t:
            unittest(["-Dwasmtest.watToWasmExecutable=" + os.path.join(wabt_dir, "wat2wasm"), "WasmTestSuite"])
    with Task("ExtraUnitTests", tasks, tags=[GraalWasmDefaultTags.wasmextratest]) as t:
        if t:
            unittest(["CSuite"])
            unittest(["WatSuite"])
    with Task("BenchTest", tasks, tags=[GraalWasmDefaultTags.wasmbenchtest]) as t:
        if t:
            for b in microbenchmarks:
                exitcode = mx_benchmark.benchmark([
                        "wasm:WASM_BENCHMARKCASES", "--",
                        "--jvm", "server", "--jvm-config", "graal-core",
                        "-Dwasmbench.benchmarkName=" + b, "-Dwasmtest.keepTempFiles=true", "--",
                        "CMicroBenchmarkSuite", "-wi", "1", "-i", "1"])
                if exitcode != 0:
                    mx.abort("Errors during benchmark tests, aborting.")


add_gate_runner(_suite, graal_wasm_gate_runner)


#
# Project types.
#

benchmark_methods = [
    "_benchmarkWarmupCount",
    "_benchmarkSetupOnce",
    "_benchmarkSetupEach",
    "_benchmarkTeardownEach",
    "_benchmarkRun",
    "_main",
]


def remove_extension(filename):
    if filename.endswith(".c"):
        return filename[:-2]
    if filename.endswith(".wat"):
        return filename[:-4]
    else:
        mx.abort("Unknown extension: " + filename)


class GraalWasmSourceFileProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, subDir, theLicense, **args):
        self.subDir = subDir
        mx.ArchivableProject.__init__(self, suite, name, deps, workingSets, theLicense, **args)

    def getSourceDir(self):
        src_dir = os.path.join(self.dir, "src", self.name, self.subDir, "src")
        return src_dir

    def getOutputDir(self):
        return os.path.join(self.get_output_base(), self.name)

    def getProgramSources(self):
        for root, _, files in os.walk(self.getSourceDir()):
            for filename in files:
                if filename.endswith(".c"):
                    yield (root, filename)
                if filename.endswith(".wat"):
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
            node_build_output_name = lambda ext: os.path.join(output_dir, subdir, NODE_BENCH_DIR, remove_extension(filename) + ext)
            native_build_output_name = lambda ext: os.path.join(output_dir, subdir, NATIVE_BENCH_DIR, remove_extension(filename) + ext)

            result_path = os.path.join(root, remove_extension(filename) + ".result")
            # The result files may be optional in some cases.
            if os.path.isfile(result_path):
                yield build_output_name(".result")
            opts_path = os.path.join(root, remove_extension(filename) + ".opts")
            # Some benchmarks may specify custom options.
            if os.path.isfile(opts_path):
                yield build_output_name(".opts")
            # Textual WebAssembly file is included for convenience.
            yield build_output_name(".wat")
            # A binary WebAssembly file contains the program.
            yield build_output_name(".wasm")
            if filename.endswith(".c"):
                # C-compiled sources generate an initialization file.
                yield build_output_name(".init")
            if self.isBenchmarkProject():
                # The JS file and the WebAssembly binary are used by Node.
                yield node_build_output_name(".js")
                yield node_build_output_name(".wasm")
                # The raw binary is used to run the program directly.
                yield native_build_output_name(mx.exe_suffix(""))
        for subdir in subdirs:
            yield os.path.join(output_dir, subdir, "wasm_test_index")

    def output_dir(self):
        return self.getOutputDir()

    def archive_prefix(self):
        return ""

    def getBuildTask(self, args):
        output_base = self.get_output_base()
        return GraalWasmSourceFileTask(self, args, output_base)

    def isBenchmarkProject(self):
        if hasattr(self, "includeset"):
            return self.includeset == "bench"
        return False


class GraalWasmSourceFileTask(mx.ProjectBuildTask):
    def __init__(self, project, args, output_base):
        self.output_base = output_base
        self.project = project
        mx.ProjectBuildTask.__init__(self, args, 1, project)

    def __str__(self):
        return 'Building {} with Emscripten'.format(self.subject.name)

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
        cc_flags = ["-O3", "-g2"]
        include_flags = []
        disable_test_api_flags = ["-DDISABLE_TEST_API"]
        if hasattr(self.project, "includeset"):
            include_flags = ["-I", os.path.join(_suite.dir, "includes", self.project.includeset)]
        emcc_flags = cc_flags
        if self.project.isBenchmarkProject():
            emcc_flags = emcc_flags + ["-s", "EXPORTED_FUNCTIONS=" + str(benchmark_methods).replace("'", "\"") + ""]
        subdir_program_names = defaultdict(lambda: [])
        for root, filename in self.subject.getProgramSources():
            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx.ensure_dir_exists(os.path.join(output_dir, subdir))

            basename = remove_extension(filename)
            source_path = os.path.join(root, filename)
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            timestampedSource = mx.TimeStampFile(source_path)
            timestampedOutput = mx.TimeStampFile(output_wasm_path)
            mustRebuild = timestampedSource.isNewerThan(timestampedOutput) or not timestampedOutput.exists()

            # Step 1: build the .wasm binary.
            if mustRebuild:
                if filename.endswith(".c"):
                    # Step 1a: compile with the JS file, and store as files for running Node, if necessary.
                    output_js_path = os.path.join(output_dir, subdir, basename + ".js")
                    build_cmd_line = [emcc_cmd] + emcc_flags + disable_test_api_flags + [source_path, "-o", output_js_path] + include_flags
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not build the JS output of " + filename + " with emcc.")
                    if self.subject.isBenchmarkProject():
                        node_dir = os.path.join(output_dir, subdir, NODE_BENCH_DIR)
                        mx.ensure_dir_exists(node_dir)
                        shutil.copyfile(output_js_path, os.path.join(node_dir, basename + ".js"))
                        shutil.copyfile(output_wasm_path, os.path.join(node_dir, basename + ".wasm"))

                    # Step 1b: extract the relevant information out of the JS file, and record it into an initialization file.
                    init_info = self.extractInitialization(output_js_path)
                    with open(os.path.join(output_dir, subdir, basename + ".init"), "w") as f:
                        f.write(init_info)

                    # Step 1c: compile to just a .wasm file, to avoid name mangling.
                    build_cmd_line = [emcc_cmd] + emcc_flags + ["-s", "ERROR_ON_UNDEFINED_SYMBOLS=0"] + [source_path, "-o", output_wasm_path] + include_flags
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not build the wasm-only output of " + filename + " with emcc.")
                elif filename.endswith(".wat"):
                    # Step 1: compile the .wat file to .wasm.
                    wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")
                    build_cmd_line = [wat2wasm_cmd, "-o", output_wasm_path, source_path]
                    if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                        mx.abort("Could not translate " + filename + " to binary format.")
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
                mx.ensure_dir_exists(os.path.join(output_dir, subdir, NATIVE_BENCH_DIR))
                if filename.endswith(".c"):
                    output_path = os.path.join(output_dir, subdir, NATIVE_BENCH_DIR, mx.exe_suffix(basename))
                    link_flags = ["-lm"]
                    gcc_cmd_line = [gcc_cmd] + cc_flags + disable_test_api_flags + [source_path, "-o", output_path] + include_flags + link_flags
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

    def extractInitialization(self, output_js_path):
        global_vars = {}
        stores = []
        with open(output_js_path, "r") as f:
            while True:
                # Extract some globals.
                line = f.readline()
                match = re.match(r"var DYNAMIC_BASE = (.*), DYNAMICTOP_PTR = (.*).*;\n", line)
                if match:
                    global_vars["DYNAMIC_BASE"] = self.to_int(match.group(1))
                    global_vars["DYNAMICTOP_PTR"] = self.to_int(match.group(2))
                    break
                if not line:
                    break

            while True:
                # Extract heap assignments.
                line = f.readline()
                if not line:
                    break
                match = re.match(r"HEAP32\[(.*) >> 2\] = (.*);\n", line)
                if match:
                    address = match.group(1)
                    value = match.group(2)
                    stores.append((address, value))
                if re.match(r"\s*env\[.*", line):
                    break

            while True:
                if not line:
                    break
                match = re.match(r"\s*env\[\"(.*)\"\] = (.*);\n", line)
                if match:
                    name = match.group(1)
                    value = match.group(2)
                    if name != "memory":
                        numeric_value = int(value)
                        global_vars[name] = numeric_value
                line = f.readline()

        init_info = ""
        for name in global_vars:
            value = global_vars[name]
            init_info += name + "=" + str(value) + "\n"
        for address, value in stores:
            init_info += "[" + str(global_vars[address]) + "]=" + str(global_vars[value])

        return init_info

    def newestOutput(self):
        return mx.TimeStampFile.newest(self.subject.getResults())

    def needsBuild(self, newestInput):
        is_needed, reason = super(GraalWasmSourceFileTask, self).needsBuild(newestInput)
        if is_needed:
            return True, reason

        tsNewestSource = mx.TimeStampFile.newest([os.path.join(root, f) for root, f in self.subject.getSources()])
        for result in self.subject.getResults():
            tsResult = mx.TimeStampFile(result)
            if tsResult.isOlderThan(tsNewestSource):
                return (True, "File " + result + " is older than the newest source file " + str(tsNewestSource))

        return (False, "Build outputs are up-to-date.")

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
    license_files=["LICENSE_WASM.txt"],
    third_party_license_files=[],
    dependencies=["Truffle"],
    truffle_jars=["wasm:WASM"],
    support_distributions=["wasm:WASM_GRAALVM_SUPPORT"],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination="bin/<exe:wasm>",
            jar_distributions=["wasm:WASM_LAUNCHER"],
            main_class="org.graalvm.wasm.launcher.WasmLauncher",
            build_args=[],
            language="wasm",
        ),
    ],
    installable=True,
))


#
# Mx commands.
#


@mx.command("mx", "wasm")
def wasm(args):
    """Run a WebAssembly program."""
    mx.get_opts().jdk = "jvmci"
    vmArgs, wasmArgs = mx.extract_VM_args(args)
    path_args = mx_truffle._path_args([
        "TRUFFLE_API",
        "org.graalvm.wasm",
        "org.graalvm.wasm.launcher",
    ])
    mx.run_java(vmArgs + path_args + ["org.graalvm.wasm.launcher.WasmLauncher"] + wasmArgs)
