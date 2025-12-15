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
package org.graalvm.truffle.benchmark.bytecode_dsl;

import java.lang.reflect.Method;

import org.graalvm.truffle.benchmark.bytecode_dsl.manual.BaseBytecodeRootNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;

/**
 * Basic language used for benchmarks.
 */
@Registration(id = "bm", name = "bm")
@ProvidedTags({RootTag.class, RootBodyTag.class})
public class BenchmarkLanguage extends TruffleLanguage<Object> {

    @Override
    protected Object createContext(Env env) {
        return new Object();
    }

    public static BytecodeRootNodes<BytecodeDSLBenchmarkRootNode> createBytecodeDSLNodes(BytecodeDSLBenchmarkRootNodeBuilder.BytecodeVariant variant,
                    BenchmarkLanguage language, BytecodeParser<BytecodeDSLBenchmarkRootNodeBuilder> parser) {
        return variant.create(language, BytecodeConfig.DEFAULT, parser);
    }

    public static BaseBytecodeRootNode createBytecodeNodes(Class<? extends BaseBytecodeRootNode> interpreterClass,
                    BenchmarkLanguage language, Builder builder) {
        try {
            Method method = interpreterClass.getMethod("create", BenchmarkLanguage.class, Builder.class);
            return (BaseBytecodeRootNode) method.invoke(null, language, builder);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(String.format("Bad interpreter class %s: could not reflectively invoke create method.", interpreterClass.getName()), ex);
        }
    }
}
