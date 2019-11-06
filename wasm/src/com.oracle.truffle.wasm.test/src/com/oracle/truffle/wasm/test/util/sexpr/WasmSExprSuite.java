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
package com.oracle.truffle.wasm.test.util.sexpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.oracle.truffle.wasm.test.WasmTestBase;
import com.oracle.truffle.wasm.test.options.WasmTestOptions;
import com.oracle.truffle.wasm.test.util.sexpr.nodes.SExprNode;
import com.oracle.truffle.wasm.test.util.sexpr.parser.SExprParser;

public abstract class WasmSExprSuite extends WasmTestBase {
    protected Path testDirectory() {
        return Paths.get(WasmTestOptions.TEST_SOURCE_PATH, "spec");
    }

    protected Collection<Path> collectTestCases(Path path) throws IOException {
        try (Stream<Path> walk = Files.list(path)) {
            return walk.filter(isWastFile).collect(Collectors.toList());
        }
    }

    @Override
    @Test
    public void test() throws IOException {
        Collection<Path> testCases = collectTestCases(testDirectory());
        for (Path testCasePath : testCases) {
            String script = readFileToString(testCasePath, StandardCharsets.UTF_8);
            List<SExprNode> sExprNodes = SExprParser.parseSexpressions(script);
            for (SExprNode sExprNode : sExprNodes) {
                try {
                    interpretSExprNode(sExprNode);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public abstract void interpretSExprNode(SExprNode sExprNode) throws IOException, InterruptedException;
}
