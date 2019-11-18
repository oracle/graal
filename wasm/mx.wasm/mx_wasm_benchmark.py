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

from mx_benchmark import JMHDistBenchmarkSuite
from mx_benchmark import add_bm_suite
from mx_benchmark import add_java_vm


_suite = mx.suite("wasm")


class WasmBenchmarkVm(mx_benchmark.Vm):
    """
    This is a special kind of Wasm VM that expects the benchmark suite to provide
    a JAR file that has each benchmark compiled to a native binary,
    a JS program that runs the Wasm benchmark (generated e.g. with Emscripten),
    and the set of files that are required by the GraalWasm test suite.
    These files must be organized in a predefined structure,
    so that the different VM implementations know where to look for them.

    If a Wasm benchmark suite consists of benchmarks in the group 'x',
    then the binaries of that benchmark must structured as follows:

    - For GraalWasm: bench/x/{*.wasm, *.init, *.result, *.wat}
    - For Node: bench/x/node/{*.wasm, *.js}
    - For native binaries: bench/x/native/*<platform-specific-binary-extension>
    """
    def name(selfs):
        return "wasm-benchmark"

    def rules(self, output, benchmarks, bmSuiteArgs):
        pass


class NodeWasmBenchmarkVm(WasmBenchmarkVm):
    def config_name(self):
        return "node"

    def run(self, cwd, args):
        mx.log(str(args))
        mx.abort("!")
        pass


class NativeWasmBenchmarkVm(WasmBenchmarkVm):
    def config_name(self):
        return "native"

    def run(self, cwd, args):
        mx.log(str(args))
        mx.abort("!")
        pass


add_java_vm(NodeWasmBenchmarkVm(), suite=_suite, priority=1)
add_java_vm(NativeWasmBenchmarkVm(), suite=_suite, priority=1)


class WasmBenchmarkSuite(JMHDistBenchmarkSuite):
    def name(self):
        return "wasm"

    def group(self):
        return "wasm"

    def subgroup(self):
        return "truffle"


add_bm_suite(WasmBenchmarkSuite())
