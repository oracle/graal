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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

public class QuickeningTest extends AbstractInstructionTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Test
    public void testAbs() {
        // return - (arg0)
        QuickeningTestRootNode node = parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAbs();
            b.emitLoadArgument(0);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");

        assertEquals(1L, node.getCallTarget().call(-1L));
        assertQuickenings(node, 1, 1);
        assertInstructions(node,
                        "load.argument",
                        "c.Abs$LessThanZero",
                        "return");

        assertEquals(1L, node.getCallTarget().call(1L));
        assertQuickenings(node, 2, 2);
        assertInstructions(node,
                        "load.argument",
                        "c.Abs$GreaterZero#LessThanZero",
                        "return");

        assertEquals("", node.getCallTarget().call(""));
        assertQuickenings(node, 3, 3);
        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");

        assertEquals(1L, node.getCallTarget().call(-1L));
        var stable = assertQuickenings(node, 3, 3);
        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return");

        assertStable(stable, node, -1L);
        assertStable(stable, node, 1L);
        assertStable(stable, node, "");
    }

    @Test
    public void testAddAndNegate() {
        // return - ((arg0 + arg1) + arg0)
        QuickeningTestRootNode node = parse(b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAbs();
            b.beginAdd();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAdd();
            b.emitLoadArgument(0);
            b.endAdd();
            b.endAbs();
            b.endReturn();

            b.endRoot();
        });

        // we start without quickening
        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.Add",
                        "load.argument",
                        "c.Add",
                        "c.Abs",
                        "return");

        // quickening during the first execution
        assertEquals(5L, node.getCallTarget().call(2L, 1L));

        assertQuickenings(node, 3, 3);
        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.Add$Long",
                        "load.argument",
                        "c.Add$Long",
                        "c.Abs$GreaterZero",
                        "return");

        // quickening remains stable
        assertEquals(5L, node.getCallTarget().call(2L, 1L));
        assertQuickenings(node, 3, 3);
        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.Add$Long",
                        "load.argument",
                        "c.Add$Long",
                        "c.Abs$GreaterZero",
                        "return");

        // switch to strings to trigger polymorphic rewrite
        assertEquals("aba", node.getCallTarget().call("a", "b"));

        var stable = assertQuickenings(node, 6, 6);
        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "c.Add",
                        "load.argument",
                        "c.Add",
                        "c.Abs",
                        "return");

        assertStable(stable, node, 3L, 1L);
        assertStable(stable, node, "a", "b");
    }

    private static QuickeningTestRootNode parse(BytecodeParser<QuickeningTestRootNodeGen.Builder> builder) {
        var nodes = QuickeningTestRootNodeGen.create(LANGUAGE, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(0);
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected QuickeningTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {

            @Specialization
            @ForceQuickening
            public static long doLong(long lhs, long rhs) {
                return lhs + rhs;
            }

            @Specialization
            @TruffleBoundary
            public static String doString(String lhs, String rhs) {
                return lhs + rhs;
            }
        }

        @Operation
        static final class Abs {

            @Specialization(guards = "v >= 0")
            @ForceQuickening
            @ForceQuickening("positiveAndNegative")
            public static long doGreaterZero(long v) {
                return v;
            }

            @Specialization(guards = "v < 0")
            @ForceQuickening
            @ForceQuickening("positiveAndNegative")
            public static long doLessThanZero(long v) {
                return -v;
            }

            @Specialization
            public static String doString(String v) {
                return v;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestError1 extends RootNode implements BytecodeRootNode {

        protected QuickeningTestError1(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Abs {

            @ExpectError("@ForceQuickening with name 'invalid0' does only match a single quickening%")
            @Specialization(guards = "v >= 0")
            @ForceQuickening("invalid0")
            public static long doGreaterZero(long v) {
                return v;
            }

            @ExpectError("@ForceQuickening with name 'invalid1' does only match a single quickening%")
            @Specialization(guards = "v < 0")
            @ForceQuickening("invalid1")
            public static long doLessThanZero(long v) {
                return -v;
            }

            @Specialization
            public static String doString(String v) {
                return v;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestError2 extends RootNode implements BytecodeRootNode {

        protected QuickeningTestError2(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Abs {

            @Specialization
            public static long doDefault(long v) {
                return v;
            }

            @ForceQuickening
            @ExpectError("Invalid location of @ForceQuickening. The annotation can only be used on method annotated with @Specialization.")
            public static String otherMethod(String v) {
                return v;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestError3 extends RootNode implements BytecodeRootNode {

        protected QuickeningTestError3(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Abs {

            @Specialization
            @ForceQuickening
            @ForceQuickening
            @ExpectError("Multiple @ForceQuickening with the same value are not allowed for one specialization.")
            public static long doDefault(long v) {
                return v;
            }

        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestError4 extends RootNode implements BytecodeRootNode {

        protected QuickeningTestError4(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Abs {

            @Specialization
            @ForceQuickening("a")
            @ForceQuickening("a")
            @ExpectError("Multiple @ForceQuickening with the same value are not allowed for one specialization.")
            public static long doLong(long v) {
                return v;
            }

            @Specialization
            @ForceQuickening("a")
            public static String doString(String v) {
                return v;
            }

        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableQuickening = true)
    public abstract static class QuickeningTestError5 extends RootNode implements BytecodeRootNode {

        protected QuickeningTestError5(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Abs {

            @Specialization
            @ForceQuickening("")
            @ExpectError("Identifier for @ForceQuickening must not be an empty string.")
            public static long doLong(long v) {
                return v;
            }

        }

    }
}
