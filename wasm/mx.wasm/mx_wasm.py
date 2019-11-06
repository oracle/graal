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
import mx_wasm_benchmark  # pylint: disable=unused-import

import errno
import os
import re
import shutil

from collections import defaultdict

_suite = mx.suite("wasm")

emcc_dir = mx.get_env("EMCC_DIR", None)
wabt_dir = mx.get_env("WABT_DIR", None)

benchmark_methods = [
    "_benchmarkWarmupCount",
    "_benchmarkSetupOnce",
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

    def getSources(self):
        for root, dirs, files in os.walk(self.getSourceDir()):
            for filename in files:
                if filename.endswith(".c"):
                    yield (root, filename)
                if filename.endswith(".wat"):
                    yield (root, filename)

    def getResults(self):
        output_dir = self.getOutputDir()
        subdirs = set()
        for root, filename in self.getSources():
            subdir = os.path.relpath(root, self.getSourceDir())
            subdirs.add(subdir)
            build_output_name = lambda ext: os.path.join(output_dir, subdir, remove_extension(filename) + ext)
            yield build_output_name(".wasm")
            if filename.endswith(".c"):
                # C-compiled sources generate an initialization file.
                yield build_output_name(".init")
            result_path = os.path.join(root, remove_extension(filename) + ".result")
            # Unlike tests, benchmarks do not have result files, so these files are optional.
            if os.path.isfile(result_path):
                yield build_output_name(".result")
            opts_path = os.path.join(root, remove_extension(filename) + ".opts")
            # Some benchmarks may specify custom options.
            if os.path.isfile(opts_path):
                yield build_output_name(".opts")
            yield build_output_name(".wat")
        for subdir in subdirs:
            yield os.path.join(output_dir, subdir, "wasm_test_index")

    def output_dir(self):
        return self.getOutputDir()

    def archive_prefix(self):
        return ""

    def getBuildTask(self, args):
        output_base = self.get_output_base()
        return GraalWasmSourceFileTask(self, args, output_base)

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
        if mx.run([emcc_cmd, "-v"], nonZeroIsFatal=False) != 0:
            mx.abort("Could not check the emcc version.")
        if not wabt_dir:
            mx.abort("Set WABT_DIR if you want the binary to include .wat files.")
        mx.log("Building files from the source dir: " + source_dir)
        flags = ["-O3", "-g2"]
        include_flags = []
        if hasattr(self.project, "includeset"):
            include_flags = ["-I", os.path.join(_suite.dir, "includes", self.project.includeset)]
            if self.project.includeset == "bench":
                flags = flags + ["-s", "EXPORTED_FUNCTIONS=" + str(benchmark_methods).replace("'", "\"") + ""]
        subdir_program_names = defaultdict(lambda: [])
        for root, filename in self.subject.getSources():
            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mx.ensure_dir_exists(os.path.join(output_dir, subdir))

            basename = remove_extension(filename)
            source_path = os.path.join(root, filename)
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            if filename.endswith(".c"):
                # Step 1a: compile with the JS file.
                output_js_path = os.path.join(output_dir, subdir, basename + ".js")
                build_cmd_line = [emcc_cmd] + flags + [source_path, "-o", output_js_path] + include_flags
                if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                    mx.abort("Could not build the JS output of " + filename + " with emcc.")

                # Step 1b: extract the relevant information out of the JS file, and record it into an initialization file.
                init_info = self.extractInitialization(output_js_path)
                with open(os.path.join(output_dir, subdir, basename + ".init"), "w") as f:
                    f.write(init_info)

                # Step 1c: compile to just a .wasm file, to avoid name mangling.
                build_cmd_line = [emcc_cmd] + flags + [source_path, "-o", output_wasm_path] + include_flags
                if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                    mx.abort("Could not build the wasm-only output of " + filename + " with emcc.")
            elif filename.endswith(".wat"):
                # Step 1: compile the .wat file to .wasm.
                wat2wasm_cmd = os.path.join(wabt_dir, "wat2wasm")
                build_cmd_line = [wat2wasm_cmd, "-o", output_wasm_path, source_path]
                if mx.run(build_cmd_line, nonZeroIsFatal=False) != 0:
                    mx.abort("Could not translate " + filename + " to binary format.")

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
            if filename.endswith(".c"):
                # Step 4: produce the .wat files, for easier debugging.
                wasm2wat_cmd = os.path.join(wabt_dir, "wasm2wat")
                if mx.run([wasm2wat_cmd, "-o", output_wat_path, output_wasm_path], nonZeroIsFatal=False) != 0:
                    mx.abort("Could not compile .wat file for " + filename)
            elif filename.endswith(".wat"):
                # Step 4: copy the .wait file, for easier debugging.
                wat_path = os.path.join(root, basename + ".wat")
                shutil.copyfile(wat_path, output_wat_path)

            # Remember the source name.
            subdir_program_names[subdir].append(basename)
        for subdir in subdir_program_names:
            with open(os.path.join(output_dir, subdir, "wasm_test_index"), "w") as f:
                for name in subdir_program_names[subdir]:
                    f.write(name)
                    f.write("\n")

    def extractInitialization(self, output_js_path):
        globals = {}
        stores = []
        with open(output_js_path, "r") as f:
            while True:
                # Extract some globals.
                line = f.readline()
                match = re.match(r"var DYNAMIC_BASE = (.*), DYNAMICTOP_PTR = (.*).*;\n", line)
                if match:
                    globals["DYNAMIC_BASE"] = int(match.group(1))
                    globals["DYNAMICTOP_PTR"] = int(match.group(2))
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
                        globals[name] = numeric_value
                line = f.readline()

        init_info = ""
        for name in globals:
            value = globals[name]
            init_info += name + "=" + str(value) + "\n"
        for address, value in stores:
            init_info += "[" + str(globals[address]) + "]=" + str(globals[value])

        return init_info

    def needsBuild(self, newestInput):
        return (True, None)

    def clean(self, forBuild=False):
        pass
