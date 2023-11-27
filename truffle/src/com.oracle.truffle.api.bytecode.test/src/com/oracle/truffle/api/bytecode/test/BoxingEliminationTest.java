/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class BoxingEliminationTest extends AbstractQuickeningTest {

    protected static final BytecodeDSLExampleLanguage LANGUAGE = null;

    @Test
    public void testArgumentAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = (BoxingEliminationTestRootNode) parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();
            b.emitLoadArgument(0);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        }).getRootNode();

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return",
                        "pop");
        assertQuickenings(node, 0, 0);

        assertEquals(42L, node.getCallTarget().call(42L));
        assertQuickenings(node, 2, 1);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call(-42L));
        assertQuickenings(node, 4, 2);

        assertInstructions(node,
                        "load.argument$Long",
                        "c.Abs$GreaterZero#LessThanZero",
                        "return",
                        "pop");

        assertEquals("42", node.getCallTarget().call("42"));
        var stable = assertQuickenings(node, 7, 3);

        assertInstructions(node,
                        "load.argument",
                        "c.Abs",
                        "return",
                        "pop");

        assertStable(stable, node, 42L);
        assertStable(stable, node, "42");
        assertStable(stable, node, -42L);
    }

    @Test
    public void testConstantAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();
            b.emitLoadConstant(42L);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "c.Abs",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "c.Abs$GreaterZero",
                        "return",
                        "pop");

    }

    @Test
    public void testConditional() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAbs();
            b.beginConditional();
            b.emitTrue();
            b.emitLoadConstant(-42L);
            b.emitLoadConstant(22L);
            b.endConditional();

            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        printInstructions(node);

        assertInstructions(node,
                        "c.True",
                        "branch.false",
                        "load.constant",
                        "branch",
                        "load.constant",
                        "c.Abs",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "c.Abs$GreaterZero",
                        "return",
                        "pop");

    }

    @Test
    public void testLocalAbs() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAbs();
            b.emitLoadLocal(local);
            b.endAbs();
            b.endReturn();
            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "load.local",
                        "c.Abs",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "store.local$Long$unboxed",
                        "load.local$Long$unboxed",
                        "c.Abs$GreaterZero",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "load.constant$Long",
                        "store.local$Long$unboxed",
                        "load.local$Long$unboxed",
                        "c.Abs$GreaterZero",
                        "return",
                        "pop");

    }

    /*
     * Test that if the generic type of a custom node uses boxing eliminate type we automatically
     * quicken.
     */
    @Test
    public void testGenericBoxingElimination() {
        // return - (arg0)
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginGenericOperationWithLong();
            b.beginGenericOperationWithLong();
            b.emitLoadConstant(-42L);
            b.endGenericOperationWithLong();
            b.endGenericOperationWithLong();
            b.endReturn();
            b.endRoot();

        });

        assertInstructions(node,
                        "load.constant",
                        "c.GenericOperationWithLong",
                        "c.GenericOperationWithLong",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        var stable = assertQuickenings(node, 4, 2);
        assertInstructions(node,
                        "load.constant$Long",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return",
                        "pop");

        assertStable(stable, node);
    }

    @Test
    public void testIfEnd() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThen();
            b.emitTrue();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endIfThen();

            b.beginReturn();
            b.emitLoadConstant(41L);
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);

        assertInstructions(node,
                        "c.True",
                        "branch.false",
                        "load.constant",
                        "return",
                        "pop",
                        "load.constant",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.True$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "pop",
                        "load.constant",
                        "return",
                        "pop");
        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    @Test
    public void testIfEndElse() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThenElse();
            b.emitFalse();
            b.beginReturn();
            b.emitLoadConstant(41L);
            b.endReturn();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endIfThenElse();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "c.False",
                        "branch.false",
                        "load.constant",
                        "return",
                        "pop",
                        "branch",
                        "load.constant",
                        "return",
                        "pop",
                        "trap");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.False$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "pop",
                        "branch",
                        "load.constant",
                        "return",
                        "pop",
                        "trap");
        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    @Test
    public void testWhile() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginWhile();
            b.emitTrue();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endWhile();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "c.True",
                        "branch.false",
                        "load.constant",
                        "return",
                        "pop",
                        "branch.backward",
                        "load.constant",
                        "return",
                        "pop");

        assertEquals(42L, node.getCallTarget().call());

        assertInstructions(node,
                        "c.True$unboxed",
                        "branch.false$Boolean",
                        "load.constant",
                        "return",
                        "pop",
                        "branch.backward",
                        "load.constant",
                        "return",
                        "pop");

        var quickenings = assertQuickenings(node, 2, 1);
        assertStable(quickenings, node);
    }

    /*
     * Tests that if you switch from a boxing eliminated operand to a non boxing eliminated operand
     * that the boxing elimination is disabled.
     */
    @Test
    public void testSwitchQuickening0() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginSwitchQuickening0();
            b.emitLoadArgument(0);
            b.endSwitchQuickening0();
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0",
                        "return",
                        "pop");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.SwitchQuickening0$One",
                        "return",
                        "pop");

        assertEquals("42", node.getCallTarget().call("42"));

        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0$NonNull",
                        "return",
                        "pop");

        assertEquals(null, node.getCallTarget().call((Object) null));

        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening0$Object",
                        "return",
                        "pop");

        var quickenings = assertQuickenings(node, 7, 3);
        assertStable(quickenings, node, "42");
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, (Object) null);
    }

    @Test
    public void testSwitchQuickening1() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginGenericOperationWithLong();
            b.beginGenericOperationWithLong();
            b.beginSwitchQuickening1();
            b.emitLoadArgument(0);
            b.endSwitchQuickening1();
            b.endGenericOperationWithLong();
            b.endGenericOperationWithLong();
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.SwitchQuickening1",
                        "c.GenericOperationWithLong",
                        "c.GenericOperationWithLong",
                        "return",
                        "pop");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.SwitchQuickening1$One$unboxed",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return",
                        "pop");

        assertEquals(2L, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        // assert that instructions stay unboxed during respecializations
                        "c.SwitchQuickening1$GreaterEqualOne$unboxed",
                        "c.GenericOperationWithLong$Long$unboxed",
                        "c.GenericOperationWithLong$Long",
                        "return",
                        "pop");

        var quickenings = assertQuickenings(node, 8, 4);
        assertStable(quickenings, node, 1L);
    }

    @Test
    public void testSwitchQuickening2() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginPassLongOrInt();
            b.beginPassLongOrInt();
            b.beginLongToInt();
            b.emitLoadArgument(0);
            b.endLongToInt();
            b.endPassLongOrInt();
            b.endPassLongOrInt();
            b.endReturn();

            b.endRoot();

        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.LongToInt",
                        "c.PassLongOrInt",
                        "c.PassLongOrInt",
                        "return",
                        "pop");

        assertEquals(1L, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$One$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "c.PassLongOrInt$Long",
                        "return",
                        "pop");

        assertEquals(2, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$GreaterEqualOne$unboxed",
                        // test that this is unboxed even if
                        // it was previously specialized to long
                        "c.PassLongOrInt$Int$unboxed",
                        "c.PassLongOrInt$Int",
                        "return",
                        "pop");

        var quickenings = assertQuickenings(node, 12, 6);
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, 2L);
    }

    @Test
    public void testPopUnboxed() {
        BoxingEliminationTestRootNode node = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();
            b.beginPassLongOrInt();
            b.beginPassLongOrInt();
            b.beginLongToInt();
            b.emitLoadArgument(0);
            b.endLongToInt();
            b.endPassLongOrInt();
            b.endPassLongOrInt();
            b.endBlock();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });

        assertQuickenings(node, 0, 0);
        assertInstructions(node,
                        "load.argument",
                        "c.LongToInt",
                        "c.PassLongOrInt",
                        "c.PassLongOrInt",
                        "pop",
                        "load.constant",
                        "return",
                        "pop");

        assertEquals(42, node.getCallTarget().call(1L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$One$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "c.PassLongOrInt$Long$unboxed",
                        "pop$Long",
                        "load.constant",
                        "return",
                        "pop");

        assertEquals(42, node.getCallTarget().call(2L));

        assertInstructions(node,
                        "load.argument$Long",
                        "c.LongToInt$GreaterEqualOne$unboxed",
                        "c.PassLongOrInt$Int$unboxed",
                        "c.PassLongOrInt$Int$unboxed",
                        "pop$Int",
                        "load.constant",
                        "return",
                        "pop");

        var quickenings = assertQuickenings(node, 16, 6);
        assertStable(quickenings, node, 1L);
        assertStable(quickenings, node, 2L);
    }

    private static BoxingEliminationTestRootNode parse(BytecodeParser<BoxingEliminationTestRootNodeGen.Builder> builder) {
        BytecodeNodes<BoxingEliminationTestRootNode> nodes = BoxingEliminationTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    @GenerateBytecode(languageClass = BytecodeDSLExampleLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableQuickening = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class BoxingEliminationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BoxingEliminationTestRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Add {
            @Specialization
            public static long doLong(long lhs, long rhs) {
                return lhs + rhs;
            }

            @Specialization
            public static int doInt(int lhs, int rhs) {
                return lhs + rhs;
            }

            @TruffleBoundary
            @Specialization
            public static String doString(String lhs, String rhs) {
                return lhs + rhs;
            }
        }

        @Operation
        static final class Abs {

            @Specialization(guards = "v >= 0")
            @ForceQuickening("positiveAndNegative")
            @ForceQuickening
            public static long doGreaterZero(long v) {
                return v;
            }

            @Specialization(guards = "v < 0")
            @ForceQuickening("positiveAndNegative")
            @ForceQuickening
            public static long doLessThanZero(long v) {
                return -v;
            }

            @Specialization
            public static int doInt(int v) {
                return -v;
            }

            @Specialization
            public static String doString(String v) {
                return v;
            }
        }

        /*
         * If the type is known statically we should be able to do boxing elimination without
         * quickening.
         */
        @Operation
        static final class GenericOperationWithLong {

            @Specialization
            static long doLong(long v) {
                if (v < 0L) {
                    return -v;
                }
                return v;
            }

        }

        @Operation
        static final class False {

            @Specialization
            static boolean doBoolean() {
                return false;
            }

        }

        @Operation
        static final class True {

            @Specialization
            static boolean doBoolean() {
                return true;
            }

        }

        @Operation
        static final class ConsumeObject {

            @Specialization
            static void doObject(@SuppressWarnings("unused") Object o) {
            }

        }

        @Operation
        static final class ProvideUnexpectedObject {

            @Specialization(rewriteOn = UnexpectedResultException.class)
            static boolean doObject(@SuppressWarnings("unused") Object o) throws UnexpectedResultException {
                throw new UnexpectedResultException(o);
            }

            @Specialization(replaces = "doObject")
            static boolean doExpected(@SuppressWarnings("unused") Object o) {
                return o != null;
            }

        }

        @Operation
        static final class SwitchQuickening0 {

            @Specialization(guards = "o == 1")
            @ForceQuickening
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o != null", replaces = "doOne")
            @ForceQuickening
            static Object doNonNull(Object o) {
                return o;
            }

            @Specialization(replaces = "doNonNull")
            @ForceQuickening
            static Object doObject(Object o) {
                return o;
            }

        }

        @Operation
        static final class SwitchQuickening1 {

            @Specialization(guards = "o == 1")
            @ForceQuickening
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o >= 1", replaces = "doOne")
            @ForceQuickening
            static long doGreaterEqualOne(long o) {
                return o;
            }

        }

        @Operation
        static final class LongToInt {

            @Specialization(guards = "o == 1")
            static long doOne(long o) {
                return o;
            }

            @Specialization(guards = "o >= 1", replaces = "doOne")
            static int doGreaterEqualOne(long o) {
                return (int) o;
            }

        }

        @Operation
        static final class PassLongOrInt {

            @Specialization
            static long doLong(long o) {
                return o;
            }

            @Specialization(replaces = "doLong")
            static int doInt(int o) {
                return o;
            }

        }

    }

}
