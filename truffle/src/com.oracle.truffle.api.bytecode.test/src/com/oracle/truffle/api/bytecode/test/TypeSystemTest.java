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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Basic tests for type system usage. Just a smoke test for Bytecode DSL-specific behavior and
 * errors. Also see {@link BoxingEliminationTypeSystemTest} for boxing elimination specific tests.
 */
public class TypeSystemTest extends AbstractInstructionTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static TypeSystemTestRootNode parse(BytecodeParser<TypeSystemTestRootNodeGen.Builder> builder) {
        BytecodeRootNodes<TypeSystemTestRootNode> nodes = TypeSystemTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @Test
    public void testIntToLongCastTypeSystem() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumer();
            b.emitIntProducer();
            b.endLongConsumer();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);

        result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);
    }

    @Test
    public void testIntToLongCastTypeSystemProxy() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerProxy();
            b.emitIntProducer();
            b.endLongConsumerProxy();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);

        result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);
    }

    @Test
    public void testIntToLongCastNoTypeSystem() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerNoTypeSystem();
            b.emitIntProducer();
            b.endLongConsumerNoTypeSystem();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(1L, result);

        result = root.getCallTarget().call();
        assertEquals(1L, result);

    }

    @Test
    public void testIntToLongCastNoTypeSystemProxy() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerNoTypeSystemProxy();
            b.emitIntProducer();
            b.endLongConsumerNoTypeSystemProxy();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(1L, result);

        result = root.getCallTarget().call();
        assertEquals(1L, result);

    }

    @Test
    public void testStringToLongCastTypeSystem() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumer();
            b.emitStringProducer();
            b.endLongConsumer();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);

        result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);
    }

    @Test
    public void testStringToLongCastTypeSystemProxy() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerProxy();
            b.emitStringProducer();
            b.endLongConsumerProxy();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);

        result = root.getCallTarget().call();
        assertEquals(TypeSystemTestTypeSystem.INT_AS_LONG_VALUE, result);
    }

    @Test
    public void testStringToLongCastNoTypeSystem() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerNoTypeSystem();
            b.emitStringProducer();
            b.endLongConsumerNoTypeSystem();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(1L, result);

        result = root.getCallTarget().call();
        assertEquals(1L, result);

    }

    @Test
    public void testStringToLongCastNoTypeSystemProxy() {
        TypeSystemTestRootNode root = parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginLongConsumerNoTypeSystemProxy();
            b.emitStringProducer();
            b.endLongConsumerNoTypeSystemProxy();
            b.endReturn();
            b.endRoot();
        });

        Object result = root.getCallTarget().call();
        assertEquals(1L, result);

        result = root.getCallTarget().call();
        assertEquals(1L, result);

    }

    @TypeSystem
    @SuppressWarnings("unused")
    static class TypeSystemTestTypeSystem {

        public static final long INT_AS_LONG_VALUE = 0xba7;

        @ImplicitCast
        static long castString(String b) {
            return INT_AS_LONG_VALUE;
        }

        @ImplicitCast
        static long castLong(int i) {
            return INT_AS_LONG_VALUE;
        }

    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    @TypeSystemReference(TypeSystemTestTypeSystem.class)
    @SuppressWarnings("unused")
    @OperationProxy(LongConsumerProxy.class)
    @OperationProxy(LongConsumerNoTypeSystemProxy.class)
    abstract static class TypeSystemTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        private static final boolean LOG = false;
        int totalInvalidations = 0;

        protected void transferToInterpreterAndInvalidate() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.totalInvalidations++;
            if (LOG) {
                System.err.println("[INVAL] --------------------");
                StackWalker.getInstance().forEach(sf -> {
                    System.err.println("   " + sf);
                });
            }
        }

        protected TypeSystemTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class IntProducer {
            @Specialization
            public static int produce() {
                return 1;
            }
        }

        @Operation
        public static final class StringProducer {
            @Specialization
            public static String produce() {
                return "1";
            }
        }

        @Operation
        public static final class LongConsumer {
            @Specialization
            public static long produce(long v) {
                return v;
            }
        }

        @Operation
        @TypeSystemReference(EmptyTypeSystem.class)
        public static final class LongConsumerNoTypeSystem {
            @Specialization
            public static long produce(long v) {
                return v;
            }

            @Specialization
            public static long produce(int v) {
                return v;
            }

            @Specialization
            @TruffleBoundary
            public static long produce(String v) {
                return Long.parseLong(v);
            }
        }

    }

    @OperationProxy.Proxyable
    @SuppressWarnings("truffle-inlining")
    public abstract static class LongConsumerProxy extends Node {
        public abstract long execute(Object o);

        @Specialization
        public static long produce(long v) {
            return v;
        }
    }

    @OperationProxy.Proxyable
    @TypeSystemReference(EmptyTypeSystem.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class LongConsumerNoTypeSystemProxy extends Node {
        public abstract long execute(Object o);

        @Specialization
        public static long produce(long v) {
            return v;
        }

        @Specialization
        public static long produce(int v) {
            return v;
        }

        @Specialization
        @TruffleBoundary
        public static long produce(String v) {
            return Long.parseLong(v);
        }
    }

    @TypeSystem
    @SuppressWarnings("unused")
    static class EmptyTypeSystem {

    }

    @TypeSystem
    @SuppressWarnings("unused")
    static class InvalidTypeSystem {
        @ExpectError("Target type and source type of an @ImplicitCast must not be the same type.")
        @ImplicitCast
        static String castString(String b) {
            return b;
        }
    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    @TypeSystemReference(InvalidTypeSystem.class)
    @SuppressWarnings("unused")
    @ExpectError("The used type system is invalid. Fix errors in the type system first.")
    abstract static class InvalidTypeSystemRootNode1 extends RootNode implements BytecodeRootNode {

        protected InvalidTypeSystemRootNode1(BytecodeDSLTestLanguage language, FrameDescriptor d) {
            super(language, d);
        }

    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class)
    @SuppressWarnings("unused")
    abstract static class InvalidTypeSystemRootNode2 extends RootNode implements BytecodeRootNode {

        protected InvalidTypeSystemRootNode2(BytecodeDSLTestLanguage language, FrameDescriptor d) {
            super(language, d);
        }

        @Operation
        @TypeSystemReference(InvalidTypeSystem.class)
        @ExpectError("Error parsing type system for operation. Fix problems in the referenced type system class first.")
        public static final class StringOperator {
            @Specialization
            public static String operate(String value) {
                return value;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
    @TypeSystemReference(TypeSystemTestTypeSystem.class)
    abstract static class SameTypeSystemRootNode extends RootNode implements BytecodeRootNode {

        protected SameTypeSystemRootNode(BytecodeDSLTestLanguage language, FrameDescriptor d) {
            super(language, d);
        }

        @ExpectError("Type system referenced by this operation is the same as the type system referenced by the parent bytecode root node. Remove the operation type system reference to resolve this warning.%")
        @Operation
        @TypeSystemReference(TypeSystemTestTypeSystem.class)
        public static final class StringOperator {
            @Specialization
            public static String operate(String value) {
                return value;
            }
        }

    }

}
