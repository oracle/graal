/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.oracle.truffle.wasm.test.suites.arithmetic.Float32Suite;
import com.oracle.truffle.wasm.test.suites.arithmetic.Float64Suite;
import com.oracle.truffle.wasm.test.suites.arithmetic.Integer32Suite;
import com.oracle.truffle.wasm.test.suites.arithmetic.Integer64Suite;
import com.oracle.truffle.wasm.test.suites.control.BlockWithLocalsSuite;
import com.oracle.truffle.wasm.test.suites.control.BranchBlockSuite;
import com.oracle.truffle.wasm.test.suites.control.IfThenElseSuite;
import com.oracle.truffle.wasm.test.suites.control.LoopBlockSuite;
import com.oracle.truffle.wasm.test.suites.control.SimpleBlockSuite;
import com.oracle.truffle.wasm.test.suites.memory.MemorySuite;
import com.oracle.truffle.wasm.test.suites.webassembly.EmscriptenSuite;
import com.oracle.truffle.wasm.test.suites.webassembly.IssueSuite;
import com.oracle.truffle.wasm.test.suites.webassembly.MultipleFunctionsSuite;

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
})
public class WasmTestSuite {
    @Test
    public void test() {
        // This is here just to make mx aware of the test suite class.
    }
}
