/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ForceCachedTest {
    @Test
    public void testOperation() {
        ForceCachedRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);
            b.beginAdd();
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endAdd();
            b.beginAddForceCached();
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endAddForceCached();
            b.endConditional();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, root.getCallTarget().call(true, 40, 2));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call(false, 40, 2));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
    }

    @Test
    public void testOperationProxy() {
        ForceCachedRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);
            b.beginAddProxy();
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endAddProxy();
            b.beginAddProxyForceCached();
            b.emitLoadArgument(1);
            b.emitLoadArgument(2);
            b.endAddProxyForceCached();
            b.endConditional();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, root.getCallTarget().call(true, 40, 2));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call(false, 40, 2));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
    }

    @Test
    public void testInstrumentation() {
        ForceCachedRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);
            b.beginPlusOne();
            b.emitLoadArgument(1);
            b.endPlusOne();
            b.beginPlusOneForceCached();
            b.emitLoadArgument(1);
            b.endPlusOneForceCached();
            b.endConditional();
            b.endReturn();
            b.endRoot();
        });
        assertEquals(41, root.getCallTarget().call(true, 41));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(41, root.getCallTarget().call(false, 41));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        root.getRootNodes().update(BytecodeConfig.COMPLETE);
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call(true, 41));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call(false, 41));
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
    }

    ForceCachedRootNode parse(BytecodeParser<ForceCachedRootNodeGen.Builder> builder) {
        return ForceCachedRootNodeGen.create(null, BytecodeConfig.DEFAULT, builder).getNode(0);
    }

    @GenerateBytecode(boxingEliminationTypes = {long.class}, languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true)
    @OperationProxy(AddProxy.class)
    @OperationProxy(value = AddProxyForceCached.class, forceCached = true)
    public abstract static class ForceCachedRootNode extends RootNode implements BytecodeRootNode {

        protected ForceCachedRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {
            @Specialization
            public static int doInts(int a, int b) {
                return a + b;
            }
        }

        @SuppressWarnings("truffle-force-cached")
        @Operation(forceCached = true)
        static final class AddForceCached {
            @Specialization
            public static int doInts(int a, int b) {
                return a + b;
            }
        }

        @Instrumentation
        static final class PlusOne {
            @Specialization
            public static int doInt(int x) {
                return x + 1;
            }
        }

        @SuppressWarnings("truffle-force-cached")
        @Instrumentation(forceCached = true)
        static final class PlusOneForceCached {
            @Specialization
            public static int doInt(int x) {
                return x + 1;
            }
        }

    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = true)
    public abstract static class AddProxy extends Node {
        abstract int execute(int x, int y);

        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

    @SuppressWarnings("truffle-inlining")
    @OperationProxy.Proxyable(allowUncached = false)
    public abstract static class AddProxyForceCached extends Node {
        abstract int execute(int x, int y);

        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

}
