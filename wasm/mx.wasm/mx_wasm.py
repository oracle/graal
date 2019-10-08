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

_suite = mx.suite('wasm')

emcc_dir = mx.get_env("EMCC_DIR", None)
wabt_dir = mx.get_env("WABT_DIR", None)

def mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError as exc:
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise

def remove_extension(filename):
    if filename.endswith(".c"):
        return filename[:-2]
    else:
        mx.abort("Unknown extension: " + filename)

class GraalWasmSourceFileProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, subDir, theLicense, **args):
        self.suite = suite
        self.name = name
        self.subDir = subDir
        mx.ArchivableProject.__init__(self, suite, name, deps, workingSets, theLicense, **args)

    def getSourceDir(self):
        return os.path.join(self.dir, "src", self.name, self.subDir, "src")

    def getOutputDir(self):
        return os.path.join(self.get_output_base(), self.name)

    def getSources(self):
        for root, dirs, files in os.walk(self.getSourceDir()):
            for filename in files:
                if filename.endswith(".c"):
                    yield (root, filename)

    def getResults(self):
        output_dir = self.getOutputDir()
        subdirs = set()
        for root, filename in self.getSources():
            subdir = os.path.relpath(root, self.getSourceDir())
            subdirs.add(subdir)
            build_output_name = lambda ext: os.path.join(output_dir, subdir, remove_extension(filename) + ext)
            yield build_output_name(".wasm")
            yield build_output_name(".init")
            result_path = os.path.join(output_dir, subdir, remove_extension(filename) + ".result")
            # Unlike tests, benchmarks do not have result files, so these files are optional.
            if os.path.isfile(result_path):
                yield result_path
            if wabt_dir:
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
        mx.run([emcc_cmd, "-v"])
        if not wabt_dir:
            mx.warn("Set WABT_DIR if you want the binary to include .wat files.")
        mx.log("Building files from the source dir: " + source_dir)
        flags = ["-O3", "-g2"]
        subdir_program_names = defaultdict(lambda: [])
        for root, filename in self.subject.getSources():
            subdir = os.path.relpath(root, self.subject.getSourceDir())
            mkdir_p(os.path.join(output_dir, subdir))

            # Step 1: compile with the JS file.
            source_path = os.path.join(root, filename)
            basename = remove_extension(filename)
            output_js_path = os.path.join(output_dir, subdir, basename + ".js")
            build_cmd_line = [emcc_cmd] + flags + [source_path, "-o", output_js_path]
            print build_cmd_line
            mx.run(build_cmd_line)

            # Step 2: extract the relevant information out of the JS file, and record it into an initialization file.
            init_info = self.extractInitialization(output_js_path)
            with open(os.path.join(output_dir, subdir, basename + ".init"), "w") as f:
                f.write(init_info)

            # Step 3: compile to just a .wasm file, to avoid name mangling.
            output_wasm_path = os.path.join(output_dir, subdir, basename + ".wasm")
            build_cmd_line = [emcc_cmd] + flags + [source_path, "-o", output_wasm_path]
            mx.run(build_cmd_line)

            # Step 4: copy the result file if it exists.
            result_path = os.path.join(root, basename + ".result")
            if os.path.isfile(result_path):
                result_output_path = os.path.join(output_dir, subdir, basename + ".result")
                shutil.copyfile(result_path, result_output_path)

            # Step 5: optionally produce the .wat files.
            if wabt_dir:
                wasm2wat_cmd = os.path.join(wabt_dir, "wasm2wat")
                output_wat_path = os.path.join(output_dir, subdir, basename + ".wat")
                mx.run([wasm2wat_cmd, "-o", output_wat_path, output_wasm_path])

            # Remember the source name.
            subdir_program_names[subdir].append(basename)
        for subdir in subdir_program_names:
            with open(os.path.join(output_dir, subdir, "wasm_test_index"), "w") as f:
                for name in subdir_program_names[subdir]:
                    f.write(name)

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
                match = re.match(r"HEAP32\[(.*) >> 2\] = (.*);\n", line)
                if match:
                    address = match.group(1)
                    value = match.group(2)
                    stores.append((address, value))
                if not line or re.match(r"\s*env\[.*", line):
                    break

            while True:
                match = re.match(r"\s*env\[\"(.*)\"\] = (.*);\n", line)
                if match:
                    name = match.group(1)
                    value = match.group(2)
                    if name != "memory":
                        numeric_value = int(value)
                        globals[name] = numeric_value
                if not line:
                    break
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
