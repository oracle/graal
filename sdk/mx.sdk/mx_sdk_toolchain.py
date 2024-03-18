#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

from __future__ import print_function
import os
import os.path
import re

import mx
import mx_util

class ToolchainTestProject(mx.Project):
    def __init__(self, suite, name, deps, workingSets, theLicense, **kwArgs):
        d = os.path.join(suite.dir, kwArgs['subDir'], name)
        super(ToolchainTestProject, self).__init__(suite, name, srcDirs=[], deps=deps, workingSets=workingSets, d=d, theLicense=theLicense, **kwArgs)

    def getBuildTask(self, args):
        return ToolchainTestBuildTask(self, args, 1)

    def isPlatformDependent(self):
        return True

    def getOutput(self, name):
        return os.path.join(self.get_output_root(), mx.exe_suffix(name))

    def getSource(self, name):
        return os.path.join(self.dir, name)

    def getArchivableResults(self, use_relpath=True, single=False):
        return []

    def getResults(self):
        return [self.getOutput(n) for n in ["hello", "hello-cxx"]]

class ToolchainTestBuildTask(mx.BuildTask):
    def __str__(self):
        return "Testing " + self.subject.name

    def newestOutput(self):
        return mx.TimeStampFile.newest(self.subject.getResults())

    def needsBuild(self, newestInput):
        for result in self.subject.getResults():
            if not os.path.exists(result):
                return True, f'{result} does not exist'
        return False, 'up to date'

    # this is not really a native project, but it's testing a native project
    # so skip it if we're not building native projects
    def buildForbidden(self):
        if not self.args.native:
            return True
        return super(ToolchainTestBuildTask, self).buildForbidden()

    def cleanForbidden(self):
        if not self.args.native:
            return True
        return super(ToolchainTestBuildTask, self).cleanForbidden()

    def build(self):
        mx_util.ensure_dir_exists(self.subject.get_output_root())

        toolchainPath = mx.distribution('LLVM_TOOLCHAIN').output
        clang = os.path.join(toolchainPath, 'bin', 'clang')
        clangxx = os.path.join(toolchainPath, 'bin', 'clang++')

        def runTest(cmd, onError=None, rerunOnFailure=False):
            out = mx.OutputCapture()
            status = mx.run(cmd, nonZeroIsFatal=False, out=out, err=out)
            if status != 0:
                mx.log_error("Failed command: " + " ".join(cmd))
                if rerunOnFailure:
                    mx.log("rerunning with -v")
                    mx.run(cmd + ['-v'], nonZeroIsFatal=False)
                if onError:
                    onError(status)
            return str(out)

        def runCompile(compiler, src, binary, onError):
            sourceFile = self.subject.getSource(src)
            binFile = self.subject.getOutput(binary)
            if mx.is_darwin():
                compileCmd = ["xcrun", compiler]
            else:
                compileCmd = [compiler, "-fuse-ld=lld"]
            runTest(compileCmd + [sourceFile, '-o', binFile], onError=onError, rerunOnFailure=True)
            out = runTest([binFile], onError=lambda status:
                mx.abort(f"{os.path.basename(compiler)} could compile {src}, but the result doesn't work. It returned with exit code {status}.")
            )
            expected = "Hello, World!"
            result = out.strip()
            if result != expected:
                mx.abort(f"{os.path.basename(compiler)} could compile {src}, but the result does not match (expected: \"{expected}\", got: \"{result}\").")

        runCompile(clang, "hello.c", "hello", onError=lambda status:
            mx.abort("The LLVM toolchain does not work. Do you have development packages installed?")
        )

        runCompile(clangxx, "hello.cc", "hello-cxx", onError=lambda status: check_multiple_gcc_issue(clang))

    def clean(self, forBuild=False):
        if os.path.exists(self.subject.get_output_root()):
            mx.rmtree(self.subject.get_output_root())

_known_gcc_packages = [
    (r"/usr/lib/gcc/x86_64-redhat-linux/([0-9]+)", "Oracle Linux or Redhat", r"yum install gcc-c++"),
    (r"/opt/rh/gcc-toolset-([0-9]+)/.*", "Oracle Linux or Redhat with gcc-toolset", r"yum install gcc-toolset-\1-libstdc++-devel"),
    (r"/usr/lib/gcc/x86_64-linux-gnu/([0-9]+)", "Ubuntu", r"apt install libstdc++-\1-dev")
]

def check_multiple_gcc_issue(clang):
    mx.log_error("The LLVM C++ compiler does not work. Do you have the libstdc++ development package installed?")

    # If there is more than one GCC version installed, the LLVM toolchain will always pick the
    # newest version. If that version does not have the libstdc++ development package installed, it
    # will not work. This can lead to confusing errors for the user, especially if an older version
    # of gcc is installed with the proper development headers available.
    candidates = []
    selected = None

    def captureCandidates(line):
        nonlocal selected
        if line.startswith("Found candidate GCC installation: "):
            candidates.append(line.split(':')[1].strip())
        elif line.startswith("Selected GCC installation: "):
            selected = line.split(':')[1].strip()
    mx.run([clang, '-v'], err=captureCandidates)

    if len(candidates) > 1:
        mx.log("Note that LLVM found multiple installations of GCC:")
        for c in candidates:
            mx.log(f"\t{c}")
        mx.log(f"It decided to use this version:\n\t{selected}")
        mx.log("Make sure you have the libstdc++-dev package for that specific version installed.")

    if selected:
        for (regex, dist, suggestion) in _known_gcc_packages:
            m = re.fullmatch(regex, selected)
            if m:
                mx.log(f"Based on the GCC path, I'm guessing you're running on {dist}.\nTry running '{m.expand(suggestion)}'.")
                break

    mx.abort(1)
