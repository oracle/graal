/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.test;

import org.graalvm.wasm.test.suites.arithmetic.Float32Suite;
import org.graalvm.wasm.test.suites.arithmetic.Float64Suite;
import org.graalvm.wasm.test.suites.arithmetic.Integer32Suite;
import org.graalvm.wasm.test.suites.arithmetic.Integer64Suite;
import org.graalvm.wasm.test.suites.control.BlockWithLocalsSuite;
import org.graalvm.wasm.test.suites.control.BranchBlockSuite;
import org.graalvm.wasm.test.suites.control.IfThenElseSuite;
import org.graalvm.wasm.test.suites.control.LoopBlockSuite;
import org.graalvm.wasm.test.suites.control.SimpleBlockSuite;
import org.graalvm.wasm.test.suites.linker.LinkerSuite;
import org.graalvm.wasm.test.suites.memory.MemorySuite;
import org.graalvm.wasm.test.suites.wasi.WasiSuite;
import org.graalvm.wasm.test.suites.control.MultipleFunctionsSuite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.graalvm.wasm.test.suites.webassembly.EmscriptenSuite;
import org.graalvm.wasm.test.suites.webassembly.IssueSuite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
                Integer32Suite.class,
                Integer64Suite.class,
                Float32Suite.class,
                Float64Suite.class,
                SimpleBlockSuite.class,
                BlockWithLocalsSuite.class,
                BranchBlockSuite.class,
                LoopBlockSuite.class,
                IfThenElseSuite.class,
                MemorySuite.class,
                IssueSuite.class,
                MultipleFunctionsSuite.class,
                EmscriptenSuite.class,
                WasiSuite.class,
                LinkerSuite.class,
                WasmPolyglotTestSuite.class,
})
public class WasmTestSuite {
    @Test
    public void test() {
        // This is here just to make mx aware of the test suite class.
    }
}
