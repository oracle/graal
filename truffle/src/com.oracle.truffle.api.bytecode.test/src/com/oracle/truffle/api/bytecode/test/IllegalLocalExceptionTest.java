/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.test.IllegalLocalExceptionRootNodeBuilder.BytecodeVariant;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@RunWith(Parameterized.class)
public class IllegalLocalExceptionTest extends AbstractInstructionTest {

    @Parameters(name = "{0}")
    public static List<BytecodeVariant> getVariants() {
        return IllegalLocalExceptionRootNodeBuilder.variants();
    }

    private final BytecodeVariant variant;

    public IllegalLocalExceptionTest(BytecodeVariant variant) {
        this.variant = variant;
    }

    public BytecodeRootNodes<IllegalLocalExceptionRootNode> parseNodes(BytecodeParser<IllegalLocalExceptionRootNodeBuilder> builder) {
        return variant.create(null, BytecodeConfig.DEFAULT, builder);
    }

    public IllegalLocalExceptionRootNode parse(BytecodeParser<IllegalLocalExceptionRootNodeBuilder> builder) {
        return parseNodes(builder).getNode(0);
    }

    private static void assertIllegalLocalException(IllegalLocalExceptionRootNode root, String localName, Object... args) {
        MyIllegalLocalException illegalLocalException = assertThrowsIllegalLocalException(root, args);
        assertIllegalLocalExceptionFields(illegalLocalException, root.getBytecodeNode(), localName);
    }

    private static MyIllegalLocalException assertThrowsIllegalLocalException(RootNode root, Object... args) {
        try {
            root.getCallTarget().call(args);
        } catch (MyIllegalLocalException ex) {
            return ex;
        } catch (RuntimeException ex) {
            fail("Some exception other than MyIllegalLocalException was thrown: " + ex);
        }
        throw new AssertionError("No exception was thrown.");
    }

    private static void assertIllegalLocalExceptionFields(MyIllegalLocalException illegalLocalException, BytecodeNode throwingBytecodeNode, String localName) {
        assertIllegalLocalExceptionFields(illegalLocalException, throwingBytecodeNode, localName, null);
    }

    private static void assertIllegalLocalExceptionFields(MyIllegalLocalException illegalLocalException, BytecodeNode throwingBytecodeNode, String localName, String instructionName) {
        assertNotNull(illegalLocalException.node);
        assertSame(throwingBytecodeNode, illegalLocalException.bytecodeNode);
        assertEquals(localName, illegalLocalException.variable.getName());
        if (instructionName == null) {
            assertNull(illegalLocalException.location);
        } else {
            Instruction instruction = illegalLocalException.bytecodeNode.getInstruction(illegalLocalException.location.getBytecodeIndex());
            assertTrue(instruction.getName().startsWith(instructionName));
        }
    }

    @Test
    public void testSetLocal() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endRoot();
        });
        assertEquals(42, root.getCallTarget().call());
    }

    @Test
    public void testUnsetLocal() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "x");
    }

    @Test
    public void testClearedLocal() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.emitClearLocal(x);
            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "x");
    }

    @Test
    public void testRewrittenLocalLoad() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            // load.local, pop should not be rewritten because it is side-effecting.
            b.emitLoadLocal(x);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "x");
    }

    private static BytecodeParser<IllegalLocalExceptionRootNodeBuilder> conditionallySetLocalParser() {
        return b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(x);
            b.emitLoadArgument(1);
            b.endStoreLocal();
            b.endIfThen();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();
            b.endRoot();
        };
    }

    @Test
    public void testConditionallySetLocal() {
        IllegalLocalExceptionRootNode root = parse(conditionallySetLocalParser());

        if (variant.getGeneratedClass() == IllegalLocalExceptionRootNodeBE.class) {
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument$Int",
                            "store.local$Int$Int",
                            "load.local$Int",
                            "return");
            assertQuickenings(root, 3, 2);
            assertEquals(123, root.getCallTarget().call(true, 123));
            assertQuickenings(root, 3, 2);

            assertIllegalLocalException(root, "x", false, 456);
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument$Int",
                            "store.local$Int$Int",
                            "load.local$generic",
                            "return");
            assertQuickenings(root, 4, 2);
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local$generic",
                            "load.local$generic",
                            "return");
            var stable = assertQuickenings(root, 6, 3);
            for (int i = 0; i < 100; i++) {
                assertEquals(42, root.getCallTarget().call(true, 42));
                assertIllegalLocalException(root, "x", false, 456);
                assertQuickenings(root, stable);
            }

            // Reparse and repeat test with Object arguments.
            root = parse(conditionallySetLocalParser());
            assertEquals("hello", root.getCallTarget().call(true, "hello"));
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local$generic",
                            "load.local$Object",
                            "return");
            assertQuickenings(root, 3, 2);
            assertEquals("world", root.getCallTarget().call(true, "world"));
            assertQuickenings(root, 3, 2);

            assertIllegalLocalException(root, "x", false, "world");
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local$generic",
                            "load.local$generic",
                            "return");
            stable = assertQuickenings(root, 4, 2);
            for (int i = 0; i < 100; i++) {
                assertEquals("hello", root.getCallTarget().call(true, "hello"));
                assertIllegalLocalException(root, "x", false, "world");
                assertQuickenings(root, stable);
            }
        } else if (variant.getGeneratedClass() == IllegalLocalExceptionRootNodeQuickened.class) {
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local",
                            "load.local$unchecked",
                            "return");
            assertQuickenings(root, 1, 0);
            assertEquals(123, root.getCallTarget().call(true, 123));
            assertQuickenings(root, 1, 0);

            assertIllegalLocalException(root, "x", false, 456);
            assertInstructions(root,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local",
                            "load.local$checked",
                            "return");
            var stable = assertQuickenings(root, 2, 0);
            for (int i = 0; i < 100; i++) {
                assertEquals(42, root.getCallTarget().call(true, 42));
                assertIllegalLocalException(root, "x", false, 456);
                assertQuickenings(root, stable);
            }
        } else {
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertIllegalLocalException(root, "x", false, 123);
        }
    }

    private static BytecodeParser<IllegalLocalExceptionRootNodeBuilder> conditionallySetLocalReturnTypeBEParser() {
        return b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(x);
            b.emitLoadArgument(1);
            b.endStoreLocal();
            b.endIfThen();

            b.beginReturn();
            b.beginIntConsumer();
            b.emitLoadLocal(x);
            b.endIntConsumer();
            b.endReturn();
            b.endRoot();
        };
    }

    // Like the previous test, but uses an IntConsumer to return-type BE the load.
    @Test
    public void testConditionallySetLocalReturnTypeBE() {
        assumeTrue(variant.getGeneratedClass() == IllegalLocalExceptionRootNodeBE.class);
        IllegalLocalExceptionRootNode root = parse(conditionallySetLocalReturnTypeBEParser());

        assertEquals(42, root.getCallTarget().call(true, 42));
        assertInstructions(root,
                        "load.argument",
                        "branch.false",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.local$Int$unboxed",
                        "c.IntConsumer$Perform",
                        "return");
        assertQuickenings(root, 5, 3);
        assertEquals(123, root.getCallTarget().call(true, 123));
        assertQuickenings(root, 5, 3);

        assertIllegalLocalException(root, "x", false, 456);
        assertInstructions(root,
                        "load.argument",
                        "branch.false",
                        "load.argument$Int",
                        "store.local$Int$Int",
                        "load.local$generic",
                        "c.IntConsumer$Perform",
                        "return");
        assertQuickenings(root, 6, 3);

        assertEquals(42, root.getCallTarget().call(true, 42));
        assertInstructions(root,
                        "load.argument",
                        "branch.false",
                        "load.argument",
                        "store.local$generic",
                        "load.local$generic",
                        "c.IntConsumer",
                        "return");
        var stable = assertQuickenings(root, 10, 5);

        for (int i = 0; i < 100; i++) {
            assertEquals(42, root.getCallTarget().call(true, 42));
            assertIllegalLocalException(root, "x", false, 456);
            assertQuickenings(root, stable);
        }

        // Reparse and repeat test with Object arguments.
        root = parse(conditionallySetLocalReturnTypeBEParser());
        assertEquals("hello", root.getCallTarget().call(true, "hello"));
        assertInstructions(root,
                        "load.argument",
                        "branch.false",
                        "load.argument",
                        "store.local$generic",
                        "load.local$Object",
                        "c.IntConsumer",
                        "return");
        assertQuickenings(root, 5, 3);
        assertEquals("world", root.getCallTarget().call(true, "world"));
        assertQuickenings(root, 5, 3);

        assertIllegalLocalException(root, "x", false, "world");
        assertInstructions(root,
                        "load.argument",
                        "branch.false",
                        "load.argument",
                        "store.local$generic",
                        "load.local$generic",
                        "c.IntConsumer",
                        "return");
        stable = assertQuickenings(root, 6, 3);
        for (int i = 0; i < 100; i++) {
            assertEquals("hello", root.getCallTarget().call(true, "hello"));
            assertIllegalLocalException(root, "x", false, "world");
            assertQuickenings(root, stable);
        }
    }

    @Test
    public void testMaterializedLocal() {
        BytecodeRootNodes<IllegalLocalExceptionRootNode> nodes = parseNodes(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);

            b.beginRoot();
            b.beginReturn();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(0);
            b.endLoadLocalMaterialized();
            b.endReturn();
            IllegalLocalExceptionRootNode inner = b.endRoot();

            b.beginReturn();
            b.emitCallInner(inner);
            b.endReturn();
            b.endRoot();
        });
        IllegalLocalExceptionRootNode inner = nodes.getNode(1);
        IllegalLocalExceptionRootNode outer = nodes.getNode(0);

        MyIllegalLocalException ex = assertThrowsIllegalLocalException(outer);
        assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
    }

    private static BytecodeParser<IllegalLocalExceptionRootNodeBuilder> conditionallySetMaterializedLocalParser() {
        return b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(x);
            b.emitLoadArgument(1);
            b.endStoreLocal();
            b.endIfThen();

            b.beginRoot();
            b.beginReturn();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(0);
            b.endLoadLocalMaterialized();
            b.endReturn();
            IllegalLocalExceptionRootNode inner = b.endRoot();

            b.beginReturn();
            b.emitCallInner(inner);
            b.endReturn();
            b.endRoot();
        };
    }

    @Test
    public void testConditionallySetMaterializedLocal() {
        BytecodeRootNodes<IllegalLocalExceptionRootNode> nodes = parseNodes(conditionallySetMaterializedLocalParser());
        IllegalLocalExceptionRootNode inner = nodes.getNode(1);
        IllegalLocalExceptionRootNode outer = nodes.getNode(0);

        if (variant.getGeneratedClass() == IllegalLocalExceptionRootNodeBE.class) {
            assertEquals(42, outer.getCallTarget().call(true, 42));
            assertInstructions(outer,
                            "load.argument",
                            "branch.false",
                            "load.argument$Int",
                            "store.local$Int$Int",
                            "c.CallInner",
                            "return");
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$Int",
                            "return");
            assertQuickenings(outer, 2, 1);
            assertQuickenings(inner, 1, 1);
            assertEquals(123, outer.getCallTarget().call(true, 123));
            assertQuickenings(outer, 2, 1);
            assertQuickenings(inner, 1, 1);

            MyIllegalLocalException ex = assertThrowsIllegalLocalException(outer, false, 456);
            assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$generic",
                            "return");
            var stableInner = assertQuickenings(inner, 2, 1);
            assertQuickenings(outer, 2, 1);

            assertEquals(42, outer.getCallTarget().call(true, 42));
            assertInstructions(outer,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local$generic",
                            "c.CallInner",
                            "return");
            var stableOuter = assertQuickenings(outer, 4, 2);
            assertQuickenings(inner, stableInner);

            for (int i = 0; i < 100; i++) {
                assertEquals(42, outer.getCallTarget().call(true, 42));
                ex = assertThrowsIllegalLocalException(outer, false, 456);
                assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
                assertQuickenings(outer, stableOuter);
                assertQuickenings(inner, stableInner);
            }

            // Reparse and repeat test with Object arguments.
            nodes = parseNodes(conditionallySetMaterializedLocalParser());
            inner = nodes.getNode(1);
            outer = nodes.getNode(0);
            assertEquals("hello", outer.getCallTarget().call(true, "hello"));
            assertInstructions(outer,
                            "load.argument",
                            "branch.false",
                            "load.argument",
                            "store.local$generic",
                            "c.CallInner",
                            "return");
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$Object",
                            "return");
            stableOuter = assertQuickenings(outer, 2, 1);
            assertQuickenings(inner, 1, 1);
            assertEquals("world", outer.getCallTarget().call(true, "world"));
            assertQuickenings(outer, stableOuter);
            assertQuickenings(inner, 1, 1);

            ex = assertThrowsIllegalLocalException(outer, false, 456);
            assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$generic",
                            "return");
            assertQuickenings(outer, stableOuter);
            stableInner = assertQuickenings(inner, 2, 1);

            for (int i = 0; i < 100; i++) {
                assertEquals("hello", outer.getCallTarget().call(true, "hello"));
                ex = assertThrowsIllegalLocalException(outer, false, "world");
                assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
                assertQuickenings(outer, stableOuter);
                assertQuickenings(inner, stableInner);
            }
        } else if (variant.getGeneratedClass() == IllegalLocalExceptionRootNodeQuickened.class) {
            assertEquals(42, outer.getCallTarget().call(true, 42));
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$unchecked",
                            "return");
            assertQuickenings(inner, 1, 0);
            assertEquals(123, outer.getCallTarget().call(true, 123));
            assertQuickenings(inner, 1, 0);

            MyIllegalLocalException ex = assertThrowsIllegalLocalException(outer, false, 456);
            assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
            assertInstructions(inner,
                            "load.argument",
                            "load.local.mat$checked",
                            "return");
            var stable = assertQuickenings(inner, 2, 0);
            for (int i = 0; i < 100; i++) {
                assertEquals(42, outer.getCallTarget().call(true, 42));
                ex = assertThrowsIllegalLocalException(outer, false, 123);
                assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
                assertQuickenings(inner, stable);
            }
        } else {
            assertEquals(42, outer.getCallTarget().call(true, 42));
            MyIllegalLocalException ex = assertThrowsIllegalLocalException(outer, false, 456);
            assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x");
        }
    }

    @Test
    public void testLocalAccessor() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginReturn();
            b.emitLocalAccessorGet(x);
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "x");
    }

    @Test
    public void testLocalAccessorSpecialized() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginReturn();
            b.emitLocalAccessorGetInt(x);
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "x");
    }

    @Test
    public void testLocalRangeAccessor() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            BytecodeLocal y = b.createLocal("y", null);

            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginReturn();
            b.emitLocalRangeAccessorGet(new BytecodeLocal[]{x, y});
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "y");
    }

    @Test
    public void testLocalRangeAccessorSpecialized() {
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            BytecodeLocal y = b.createLocal("y", null);

            b.beginStoreLocal(x);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginReturn();
            b.emitLocalRangeAccessorGetInt(new BytecodeLocal[]{x, y});
            b.endReturn();
            b.endRoot();
        });
        assertIllegalLocalException(root, "y");
    }

    @Test
    public void testBytecodeNodeGetLocalValue() {
        // Using BytecodeNode#getLocalValue does not throw the illegal exception.
        IllegalLocalExceptionRootNode root = parse(b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginReturn();
            b.emitBytecodeNodeLoadLocal(x.getLocalOffset());
            b.endReturn();
            b.endRoot();
        });
        assertNull(root.getCallTarget().call());
    }

    /**
     * The other tests in this class do not support binding a BytecodeLocation because the bytecode
     * spec uses LocalAccessor. These tests use a separate bytecode spec that permits locations.
     */
    @RunWith(Parameterized.class)
    public static class BytecodeLocationTests {
        @Parameters(name = "{0}")
        public static List<IllegalLocalExceptionWithLocationRootNodeBuilder.BytecodeVariant> getVariants() {
            return IllegalLocalExceptionWithLocationRootNodeBuilder.variants();
        }

        private final IllegalLocalExceptionWithLocationRootNodeBuilder.BytecodeVariant variant;

        public BytecodeLocationTests(IllegalLocalExceptionWithLocationRootNodeBuilder.BytecodeVariant variant) {
            this.variant = variant;
        }

        public BytecodeRootNodes<IllegalLocalExceptionWithLocationRootNode> parseNodes(BytecodeParser<IllegalLocalExceptionWithLocationRootNodeBuilder> builder) {
            return variant.create(null, BytecodeConfig.DEFAULT, builder);
        }

        public IllegalLocalExceptionWithLocationRootNode parse(BytecodeParser<IllegalLocalExceptionWithLocationRootNodeBuilder> builder) {
            return parseNodes(builder).getNode(0);
        }

        private static void assertIllegalLocalException(IllegalLocalExceptionWithLocationRootNode root, String localName, String instructionName, Object... args) {
            MyIllegalLocalException illegalLocalException = assertThrowsIllegalLocalException(root, args);
            assertIllegalLocalExceptionFields(illegalLocalException, root.getBytecodeNode(), localName, instructionName);
        }

        @Test
        public void testUnsetLocal() {
            IllegalLocalExceptionWithLocationRootNode root = parse(b -> {
                b.beginRoot();
                BytecodeLocal x = b.createLocal("x", null);
                b.beginReturn();
                b.emitLoadLocal(x);
                b.endReturn();
                b.endRoot();
            });
            assertIllegalLocalException(root, "x", "load.local");
        }

        @Test
        public void testMaterializedLocal() {
            BytecodeRootNodes<IllegalLocalExceptionWithLocationRootNode> nodes = parseNodes(b -> {
                b.beginRoot();
                BytecodeLocal x = b.createLocal("x", null);

                b.beginRoot();
                b.beginReturn();
                b.beginLoadLocalMaterialized(x);
                b.emitLoadArgument(0);
                b.endLoadLocalMaterialized();
                b.endReturn();
                IllegalLocalExceptionWithLocationRootNode inner = b.endRoot();

                b.beginReturn();
                b.emitCallInner(inner);
                b.endReturn();
                b.endRoot();
            });
            IllegalLocalExceptionWithLocationRootNode inner = nodes.getNode(1);
            IllegalLocalExceptionWithLocationRootNode outer = nodes.getNode(0);

            MyIllegalLocalException ex = assertThrowsIllegalLocalException(outer);
            assertIllegalLocalExceptionFields(ex, inner.getBytecodeNode(), "x", "load.local.mat");
        }
    }
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true, //
                                enableQuickening = false //
                )),
                @Variant(suffix = "CustomFactoryMethod", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyOtherIllegalLocalException.class, //
                                illegalLocalExceptionFactory = "factoryMethod", //
                                enableMaterializedLocalAccesses = true, //
                                enableQuickening = false //
                )),
                @Variant(suffix = "Uncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true, //
                                enableQuickening = false, //
                                enableUncachedInterpreter = true //
                )),
                @Variant(suffix = "Quickened", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true //
                )),
                @Variant(suffix = "BE", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true, //
                                boxingEliminationTypes = {int.class})),
                @Variant(suffix = "BEWithStoreBci", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true, //
                                boxingEliminationTypes = {int.class}, //
                                storeBytecodeIndexInFrame = true)),
                @Variant(suffix = "BEUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                enableMaterializedLocalAccesses = true, //
                                enableUncachedInterpreter = true, //
                                boxingEliminationTypes = {int.class})),
})
abstract class IllegalLocalExceptionRootNode extends DebugBytecodeRootNode {
    protected IllegalLocalExceptionRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = IllegalLocalExceptionRootNode.class)
    static final class CallInner {
        @Specialization
        static Object call(VirtualFrame frame,
                        IllegalLocalExceptionRootNode interpreter,
                        @Bind Node location) {
            return interpreter.getCallTarget().call(location, frame);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    static final class LocalAccessorGet {
        @Specialization
        static Object perform(VirtualFrame frame,
                        LocalAccessor accessor,
                        @Bind BytecodeNode bytecodeNode) {
            return accessor.getObject(bytecodeNode, frame);
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalAccessor.class)
    static final class LocalAccessorGetInt {
        @Specialization
        static int perform(VirtualFrame frame,
                        LocalAccessor accessor,
                        @Bind BytecodeNode bytecodeNode) {
            try {
                return accessor.getInt(bytecodeNode, frame);
            } catch (UnexpectedResultException e) {
                fail("should not throw unexpected result");
                return -1;
            }
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class)
    static final class LocalRangeAccessorGet {
        @Specialization
        static Object[] perform(VirtualFrame frame,
                        LocalRangeAccessor accessor,
                        @Bind BytecodeNode bytecodeNode) {
            Object[] result = new Object[accessor.getLength()];
            for (int i = 0; i < result.length; i++) {
                result[i] = accessor.getObject(bytecodeNode, frame, i);
            }
            return result;
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = LocalRangeAccessor.class)
    static final class LocalRangeAccessorGetInt {
        @Specialization
        static int[] perform(VirtualFrame frame,
                        LocalRangeAccessor accessor,
                        @Bind BytecodeNode bytecodeNode) {
            int[] result = new int[accessor.getLength()];
            for (int i = 0; i < result.length; i++) {
                try {
                    result[i] = accessor.getInt(bytecodeNode, frame, i);
                } catch (UnexpectedResultException e) {
                    fail("should not throw unexpected result");
                    return null;
                }
            }
            return result;
        }
    }

    @Operation(storeBytecodeIndex = false)
    @ConstantOperand(type = int.class)
    static final class BytecodeNodeLoadLocal {
        @Specialization
        static Object perform(VirtualFrame frame,
                        int localOffset,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind("$bytecodeIndex") int bci) {
            return bytecodeNode.getLocalValue(bci, frame, localOffset);
        }
    }

    // Operation used to trigger return-type BE.
    @Operation
    static final class IntConsumer {
        @Specialization
        static int perform(int operand) {
            return operand;
        }

        @Fallback
        static Object performOther(Object operand) {
            return operand;
        }
    }
}

@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                illegalLocalExceptionFactory = "createWithLocation", //
                                enableMaterializedLocalAccesses = true, //
                                enableQuickening = false //
                )),
                @Variant(suffix = "Uncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                illegalLocalExceptionFactory = "createWithLocation", //
                                enableMaterializedLocalAccesses = true, //
                                enableQuickening = false, //
                                enableUncachedInterpreter = true //
                )),
                @Variant(suffix = "Quickened", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                illegalLocalExceptionFactory = "createWithLocation", //
                                enableMaterializedLocalAccesses = true //
                )),
                @Variant(suffix = "BE", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                illegalLocalException = MyIllegalLocalException.class, //
                                illegalLocalExceptionFactory = "createWithLocation", //
                                enableMaterializedLocalAccesses = true, //
                                boxingEliminationTypes = {int.class})),
})
abstract class IllegalLocalExceptionWithLocationRootNode extends DebugBytecodeRootNode {
    protected IllegalLocalExceptionWithLocationRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = IllegalLocalExceptionWithLocationRootNode.class)
    static final class CallInner {
        @Specialization
        static Object call(VirtualFrame frame,
                        IllegalLocalExceptionWithLocationRootNode interpreter,
                        @Bind Node location) {
            return interpreter.getCallTarget().call(location, frame);
        }
    }
}

@SuppressWarnings("serial")
class MyIllegalLocalException extends AbstractTruffleException {
    public final Node node;
    public final BytecodeNode bytecodeNode;
    public final LocalVariable variable;
    public final BytecodeLocation location;

    protected MyIllegalLocalException(Node node, BytecodeNode bytecodeNode, LocalVariable variable, BytecodeLocation location) {
        this.node = node;
        this.bytecodeNode = bytecodeNode;
        this.variable = variable;
        this.location = location;
    }

    public static MyIllegalLocalException create(Node node, BytecodeNode bytecodeNode, LocalVariable variable) {
        return new MyIllegalLocalException(node, bytecodeNode, variable, null);
    }

    public static MyIllegalLocalException createWithLocation(Node node, BytecodeNode bytecodeNode, LocalVariable variable, BytecodeLocation location) {
        return new MyIllegalLocalException(node, bytecodeNode, variable, location);
    }
}

@SuppressWarnings("serial")
final class MyOtherIllegalLocalException extends MyIllegalLocalException {
    private MyOtherIllegalLocalException(Node node, BytecodeNode bytecodeNode, LocalVariable variable) {
        super(node, bytecodeNode, variable, null);
    }

    public static MyOtherIllegalLocalException factoryMethod(Node node, BytecodeNode bytecodeNode, LocalVariable variable) {
        return new MyOtherIllegalLocalException(node, bytecodeNode, variable);
    }
}

@ExpectError("The defaultLocalValue and illegalLocalException attributes are mutually exclusive.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, defaultLocalValue = "null", illegalLocalException = MyIllegalLocalException.class)
abstract class MutuallyExclusiveLocalSettings extends RootNode implements BytecodeRootNode {
    protected MutuallyExclusiveLocalSettings(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("The defaultLocalValue and illegalLocalException attributes are mutually exclusive.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, defaultLocalValue = "null", illegalLocalException = MyOtherIllegalLocalException.class, illegalLocalExceptionFactory = "factoryMethod")
abstract class MutuallyExclusiveLocalSettings2 extends RootNode implements BytecodeRootNode {
    protected MutuallyExclusiveLocalSettings2(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("NoCreateException must declare exactly one static 'create' method for instantiating exceptions.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = NoCreateException.class)
abstract class ExceptionTypeNoCreate extends RootNode implements BytecodeRootNode {
    protected ExceptionTypeNoCreate(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class NoCreateException extends AbstractTruffleException {

}

@ExpectError("AmbiguousCreateException must declare exactly one static 'create' method for instantiating exceptions.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = AmbiguousCreateException.class)
abstract class ExceptionTypeAmbiguousCreate extends RootNode implements BytecodeRootNode {
    protected ExceptionTypeAmbiguousCreate(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class AmbiguousCreateException extends AbstractTruffleException {
    public static AmbiguousCreateException create() {
        return null;
    }

    public static AmbiguousCreateException create(@SuppressWarnings("unused") Node n) {
        return null;
    }
}

@ExpectError("PrivateCreateException's static 'create' method must be visible from this node.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = PrivateCreateException.class)
abstract class ExceptionTypePrivateCreate extends RootNode implements BytecodeRootNode {
    protected ExceptionTypePrivateCreate(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class PrivateCreateException extends AbstractTruffleException {
    @SuppressWarnings("unused")
    private static PrivateCreateException create() {
        return null;
    }
}

@ExpectError("BadCreateReturnTypeException's static 'create' method must return an instance of type BadCreateReturnTypeException.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = BadCreateReturnTypeException.class)
abstract class ExceptionTypeBadCreateReturnType extends RootNode implements BytecodeRootNode {
    protected ExceptionTypeBadCreateReturnType(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class BadCreateReturnTypeException extends AbstractTruffleException {
    public static String create() {
        return null;
    }
}

@ExpectError("UnsupportedCreateParameterException's static 'create' method declares an unsupported String parameter. Supported parameter types: %")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = UnsupportedCreateParameterException.class)
abstract class ExceptionTypeUnsupportedCreateParameter extends RootNode implements BytecodeRootNode {
    protected ExceptionTypeUnsupportedCreateParameter(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class UnsupportedCreateParameterException extends AbstractTruffleException {
    public static UnsupportedCreateParameterException create(@SuppressWarnings("unused") String unused) {
        return null;
    }
}

@ExpectError("RepeatedCreateParameterException's static 'create' method cannot declare more than one Node parameter.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = RepeatedCreateParameterException.class)
abstract class ExceptionTypeRepeatedCreateParameter extends RootNode implements BytecodeRootNode {
    protected ExceptionTypeRepeatedCreateParameter(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class RepeatedCreateParameterException extends AbstractTruffleException {
    public static RepeatedCreateParameterException create(@SuppressWarnings("unused") Node n1, @SuppressWarnings("unused") Node n2) {
        return null;
    }
}

@ExpectError("BindsLocationException's static 'create' method cannot declare a BytecodeLocation parameter because some custom operation uses a local accessor%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = BindsLocationException.class)
abstract class ExceptionBindsLocationLocalAccessor extends RootNode implements BytecodeRootNode {
    protected ExceptionBindsLocationLocalAccessor(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    @ConstantOperand(type = LocalAccessor.class)
    static final class LocalAccessorOperation {
        @Specialization
        public static void perform(@SuppressWarnings("unused") LocalAccessor accessor) {
        }
    }
}

@ExpectError("BindsLocationException's static 'create' method cannot declare a BytecodeLocation parameter because some custom operation uses a local accessor%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = BindsLocationException.class)
abstract class ExceptionBindsLocationLocalRangeAccessor extends RootNode implements BytecodeRootNode {
    protected ExceptionBindsLocationLocalRangeAccessor(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    static final class LocalRangeAccessorOperation {
        @Specialization
        public static void perform(@SuppressWarnings("unused") LocalRangeAccessor accessor) {
        }
    }
}

@ExpectError("Illegal local exceptions cannot be used because some custom operation uses a MaterializedLocalAccessor%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = BindsLocationException.class, illegalLocalExceptionFactory = "create", enableMaterializedLocalAccesses = true)
abstract class RootNodeUsesMaterializedLocalAccessor extends RootNode implements BytecodeRootNode {
    protected RootNodeUsesMaterializedLocalAccessor(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    static final class MaterializedLocalAccessorOperation {
        @Specialization
        public static void perform(@SuppressWarnings("unused") MaterializedLocalAccessor accessor) {
        }
    }
}

@SuppressWarnings("serial")
class BindsLocationException extends AbstractTruffleException {
    public static BindsLocationException create(@SuppressWarnings("unused") BytecodeLocation location) {
        return null;
    }
}

@ExpectError("The illegalLocalExceptionFactory attribute can only be used if illegalLocalException is specified.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalExceptionFactory = "factoryMethod")
abstract class BadFactoryMethodUsage extends RootNode implements BytecodeRootNode {
    protected BadFactoryMethodUsage(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("The illegalLocalExceptionFactory attribute cannot be empty.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = InvalidFactoryMethodException.class, illegalLocalExceptionFactory = "")
abstract class EmptyFactoryMethod extends RootNode implements BytecodeRootNode {
    protected EmptyFactoryMethod(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("The illegalLocalExceptionFactory attribute must be a valid method name.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = InvalidFactoryMethodException.class, illegalLocalExceptionFactory = "123foo")
abstract class InvalidFactoryMethodName extends RootNode implements BytecodeRootNode {
    protected InvalidFactoryMethodName(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("InvalidFactoryMethodException must declare exactly one static 'factoryMethod' method for instantiating exceptions.%")
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, illegalLocalException = InvalidFactoryMethodException.class, illegalLocalExceptionFactory = "factoryMethod")
abstract class NotFoundFactoryMethod extends RootNode implements BytecodeRootNode {
    protected NotFoundFactoryMethod(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@SuppressWarnings("serial")
class InvalidFactoryMethodException extends AbstractTruffleException {
    public static InvalidFactoryMethodException create() {
        return null;
    }
}
