/*
 *  Copyright (c) 2019, Oracle and/or its affiliates.
 *
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this list of
 *  conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *  conditions and the following disclaimer in the documentation and/or other materials provided
 *  with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *  endorse or promote products derived from this software without specific prior written
 *  permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 *  OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 *  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *  AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.test.next.control;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.wasm.test.next.WasmSuiteBase;
import com.oracle.truffle.wasm.test.next.options.WasmTestOptions;

public class SimpleBlockSuite extends WasmSuiteBase {
    private static Path testDirectory = new File(WasmTestOptions.TEST_SOURCE_PATH).toPath();

    private static WasmStringTestCase[] testCases = {
            testCase("PRODUCE_CONST_VALUE", expected(32),
                        "(module (func (result i32) block $B0 (result i32) i32.const 11 end i32.const 21 i32.add))"),
    };

    @Override
    protected Collection<? extends WasmTestCase> collectTestCases() throws IOException {
        return Stream.concat(Arrays.stream(testCases), collectFileTestCases(testDirectory).stream()).collect(Collectors.toList());
    }
}
