/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark.bytecode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;

@Registration(id = "bm", name = "bm")
public class BenchmarkLanguage extends TruffleLanguage<Object> {

    private static final Map<String, Function<BenchmarkLanguage, CallTarget>> NAMES = new HashMap<>();

    @Override
    protected Object createContext(Env env) {
        return new Object();
    }

    public static void registerName(String name, Class<? extends BytecodeBenchmarkRootNode> cls, BytecodeParser<BytecodeBenchmarkRootNodeBuilder> parser) {
        registerName(name, l -> {
            BytecodeRootNodes<BytecodeBenchmarkRootNode> nodes = createNodes(cls, l, parser);
            return nodes.getNode(nodes.count() - 1).getCallTarget();
        });
    }

    private static BytecodeRootNodes<BytecodeBenchmarkRootNode> createNodes(Class<? extends BytecodeBenchmarkRootNode> interpreterClass,
                    BenchmarkLanguage language, BytecodeParser<BytecodeBenchmarkRootNodeBuilder> builder) {
        return BytecodeBenchmarkRootNodeBuilder.invokeCreate(interpreterClass, language, BytecodeConfig.DEFAULT, builder);
    }

    public static void registerName(String name, Function<BenchmarkLanguage, CallTarget> parser) {
        NAMES.put(name, parser);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        String name = request.getSource().getCharacters().toString();
        if (!NAMES.containsKey(name)) {
            throw new AssertionError("source not registered: " + name);
        }

        return NAMES.get(name).apply(this);
    }
}
