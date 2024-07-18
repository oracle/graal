/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.TypeSystemTest.EmptyTypeSystem;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;

// TODO this test should be rewritten once we have something working for boxing elimination.
@Ignore
public class BoxingEliminationTypeSystemTest extends AbstractInstructionTest {

    private static final BytecodeDSLTestLanguage LANGUAGE = null;

    private static BoxingEliminationTypeSystemRootNode parse(BytecodeParser<BoxingEliminationTypeSystemRootNodeGen.Builder> builder) {
        BytecodeRootNodes<BoxingEliminationTypeSystemRootNode> nodes = BoxingEliminationTypeSystemRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    @Test
    public void testCastConstantIntToLong() {
        BoxingEliminationTypeSystemRootNode node = (BoxingEliminationTypeSystemRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginLongConsumer();
            b.emitLoadConstant(42);
            b.endLongConsumer();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.constant",
                        "c.LongConsumer",
                        "return");
        assertQuickenings(node, 0, 0);

        assertEquals(BoxingEliminationTypeSystem.INT_AS_LONG_VALUE, node.getCallTarget().call());
        assertQuickenings(node, 2, 1);

        assertInstructions(node,
                        "load.constant$Int",
                        "c.LongConsumer$Default",
                        "return");

        assertEquals(BoxingEliminationTypeSystem.INT_AS_LONG_VALUE, node.getCallTarget().call());
        assertQuickenings(node, 4, 2);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero#LessThanZero",
                        "return");

        assertEquals("42", node.getCallTarget().call("42"));
        var stable = assertQuickenings(node, 7, 3);

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");

        assertStable(stable, node, 42L);
        assertStable(stable, node, "42");
        assertStable(stable, node, -42L);
    }

    @GenerateBytecode(//
                    languageClass = BytecodeDSLTestLanguage.class, //
                    enableQuickening = true, boxingEliminationTypes = {boolean.class, int.class, long.class})
    @TypeSystemReference(BoxingEliminationTypeSystem.class)
    @SuppressWarnings("unused")
    abstract static class BoxingEliminationTypeSystemRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BoxingEliminationTypeSystemRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class IntProducer {
            @Specialization
            public static int doDefault() {
                return 1;
            }
        }

        @Operation
        public static final class LongConsumer {
            @Specialization
            public static long doDefault(long v) {
                return v;
            }
        }

        @Operation
        @TypeSystemReference(EmptyTypeSystem.class)
        public static final class LongConsumerNoTypeSystem {

            @Specialization
            public static long doLong(long v) {
                return v;
            }

            @Specialization
            public static long doInt(int v) {
                return v;
            }

            @Specialization
            @TruffleBoundary
            public static long doString(String v) {
                return Long.parseLong(v);
            }
        }

    }

    @TypeSystem
    @SuppressWarnings("unused")
    static class BoxingEliminationTypeSystem {

        public static final long INT_AS_LONG_VALUE = 0xba7;

        @ImplicitCast
        static long castLong(int i) {
            return INT_AS_LONG_VALUE;
        }

        @ImplicitCast
        static long castByte(byte i) {
            return INT_AS_LONG_VALUE;
        }

        @ImplicitCast
        static long castString(String i) {
            return INT_AS_LONG_VALUE;
        }

    }

}
