/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.nodes.RootNode;

public class VariadicTest {
    @Test
    public void testVariadic0Arguments() {
        for (int i = 0; i < 32; i++) {
            final int variadicCount = i;

            Object[] args = new Object[variadicCount];
            for (int j = 0; j < variadicCount; j++) {
                args[j] = j;
            }

            var root = parse((b) -> {
                b.beginRoot();
                b.beginReturn();
                b.beginVariadic0Operation();
                for (int j = 0; j < variadicCount; j++) {
                    b.emitLoadArgument(j);
                }
                b.endVariadic0Operation();
                b.endReturn();
                b.endRoot();
            });

            Object[] result = (Object[]) root.getCallTarget().call(args);
            assertArrayEquals(args, result);
        }
    }

    @Test
    public void testVariadic1Arguments() {
        for (int i = 1; i < 32; i++) {
            final int variadicCount = i;

            Object[] args = new Object[variadicCount];
            for (int j = 0; j < variadicCount; j++) {
                args[j] = (long) j;
            }

            var root = parse((b) -> {
                b.beginRoot();
                b.beginReturn();
                b.beginVariadic1Operation();
                for (int j = 0; j < variadicCount; j++) {
                    b.emitLoadArgument(j);
                }
                b.endVariadic1Operation();
                b.endReturn();
                b.endRoot();
            });

            Object[] result = (Object[]) root.getCallTarget().call(args);
            assertArrayEquals(Arrays.copyOfRange(args, 1, args.length), result);
        }
    }

    VariadicOperationsNode parse(BytecodeParser<VariadicOperationsNodeGen.Builder> builder) {
        return VariadicOperationsNodeGen.create(null, BytecodeConfig.WITH_SOURCE, builder).getNode(0);
    }

    @ProvidedTags(ExpressionTag.class)
    class TestLanguage extends TruffleLanguage<Env> {
        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @GenerateBytecode(boxingEliminationTypes = {long.class}, languageClass = TestLanguage.class, enableYield = true, enableSerialization = true)
    public abstract static class VariadicOperationsNode extends RootNode implements BytecodeRootNode {

        protected VariadicOperationsNode(TestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Variadic0Operation {
            @Specialization
            public static Object[] variadic(@Variadic Object[] args) {
                return args;
            }
        }

        @Operation
        static final class Variadic1Operation {
            @Specialization
            @SuppressWarnings("unused")
            public static Object[] variadic(long arg0, @Variadic Object[] args) {
                return args;
            }
        }

    }

}
