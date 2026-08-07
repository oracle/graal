/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.StackValue;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectWarning;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public class StackValuesTest extends AbstractBasicInterpreterTest {

    public StackValuesTest(TestRun run) {
        super(run);
    }

    @Test
    public void testSimple() {
        BasicInterpreter root = parseNode("simple", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);

            b.emitLoadStackValue(x);

            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testTopValueUsesDup() {
        BasicInterpreter root = parseNode("topValueUsesDup", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue value = emitBindStackValue(b, 42L);
            b.emitLoadStackValue(value);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
        assertTrue(root.getBytecodeNode().getInstructionsAsList().stream().anyMatch(instruction -> instruction.getName().equals("dup")));
        assertFalse(root.getBytecodeNode().getInstructionsAsList().stream().anyMatch(instruction -> instruction.getName().equals("load.stackvalue")));
    }

    @Test
    public void testMultipleValues() {
        BasicInterpreter root = parseNode("multipleValues", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 21L);
            StackValue y = emitBindStackValue(b, 42L);

            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadStackValue(y);
            b.endAdd();

            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(63L, root.getCallTarget().call());
    }

    @Test
    public void testBranchInBlock() {
        BasicInterpreter root = parseNode("branchInBlock", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 41L);

            BytecodeLabel label = b.createLabel();
            b.emitBranch(label);
            b.beginReturn();
            b.emitLoadConstant(99L);
            b.endReturn();
            b.emitLabel(label);
            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadConstant(1L);
            b.endAdd();

            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBranchToMoreStackValuesFails() {
        assertParseFailure("Cannot branch to a label with more live stack values than at the branch.", IllegalStateException.class, b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            BytecodeLabel label = b.createLabel();
            b.emitBranch(label);
            StackValue x = emitBindStackValue(b, 42L);
            b.emitLabel(label);
            b.emitLoadStackValue(x);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });
    }

    @Test
    public void testInitializeFromStackValue() {
        BasicInterpreter root = parseNode("initializeFromStackValue", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 21L);

            b.beginBindStackValue();
            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadConstant(21L);
            b.endAdd();
            StackValue y = b.endBindStackValue();

            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadStackValue(y);
            b.endAdd();

            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(63L, root.getCallTarget().call());
    }

    @Test
    public void testInterleavedValues() {
        BasicInterpreter root = parseNode("interleavedValues", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 10L);
            b.emitLoadConstant(100L);
            StackValue y = emitBindStackValue(b, 32L);
            b.emitLoadConstant(200L);

            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadStackValue(y);
            b.endAdd();
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testStoreValue() {
        BasicInterpreter root = parseNode("storeValue", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 1L);
            emitStoreStackValue(b, x, 42L);
            b.emitLoadStackValue(x);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testStoreMultipleTimes() {
        BasicInterpreter root = parseNode("storeMultipleTimes", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 1L);
            emitStoreStackValue(b, x, 21L);
            emitStoreStackValue(b, x, 42L);
            b.emitLoadStackValue(x);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testStoreFromStackValue() {
        BasicInterpreter root = parseNode("storeFromStackValue", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 20L);
            StackValue y = emitBindStackValue(b, 0L);

            b.beginStoreStackValue(y);
            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadConstant(22L);
            b.endAdd();
            b.endStoreStackValue();

            b.emitLoadStackValue(y);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testStoreInInitializer() {
        BasicInterpreter root = parseNode("storeInInitializer", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 0L);

            b.beginBindStackValue();
            b.beginBlock();
            emitStoreStackValue(b, x, 42L);
            b.emitLoadStackValue(x);
            b.endBlock();
            StackValue y = b.endBindStackValue();

            b.emitLoadStackValue(y);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testStoreOuterValue() {
        BasicInterpreter root = parseNode("storeOuterValue", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 1L);

            b.beginBlock();
            emitStoreStackValue(b, x, 42L);
            b.endBlock();

            b.emitLoadStackValue(x);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBindStackValueAsResult() {
        BasicInterpreter root = parseNode("bindStackValueAsResult", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            emitBindStackValue(b, 42L);
            b.endBlock();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBindInCustomOperation() {
        BasicInterpreter root = parseNode("bindInCustomOperation", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginAdd();
            StackValue x = emitBindStackValue(b, 21L);
            b.emitLoadStackValue(x);
            b.endAdd();

            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBindInCustomOperationOutOfScope() {
        String message = "Stack value must belong to an active custom operation or Block in the current root node.";
        assertParseFailure(message, IllegalArgumentException.class, b -> {
            b.beginRoot();

            b.beginAdd();
            StackValue x = emitBindStackValue(b, 21L);
            b.emitLoadStackValue(x);
            b.endAdd();

            b.beginBlock();
            b.emitLoadStackValue(x);
            b.endBlock();
            b.endRoot();
        });
    }

    @Test
    public void testNestedBlocks() {
        BasicInterpreter root = parseNode("nestedBlocks", b -> {
            b.beginRoot();
            b.beginReturn();

            b.beginBlock();
            StackValue outer = emitBindStackValue(b, 40L);

            b.beginBlock();
            b.beginBindStackValue();
            b.beginAdd();
            b.emitLoadStackValue(outer);
            b.emitLoadConstant(1L);
            b.endAdd();
            StackValue inner = b.endBindStackValue();

            b.beginAdd();
            b.emitLoadStackValue(inner);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endBlock();

            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBranchOut() {
        BasicInterpreter root = parseNode("branchOut", b -> {
            b.beginRoot();
            BytecodeLabel exit = b.createLabel();

            b.beginBlock();
            emitBindStackValue(b, 41L);
            b.emitBranch(exit);
            b.emitLoadConstant(99L);
            b.endBlock();

            b.emitLabel(exit);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBranchOutFromInitializer() {
        BasicInterpreter root = parseNode("branchOutFromInitializer", b -> {
            b.beginRoot();
            BytecodeLabel exit = b.createLabel();

            b.beginReturn();
            b.beginBlock();

            b.beginBindStackValue();
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.emitBranch(exit);
            b.endIfThen();
            b.emitLoadConstant(41L);
            b.endBlock();
            StackValue x = b.endBindStackValue();

            b.emitLoadStackValue(x);
            b.endBlock();
            b.endReturn();

            b.emitLabel(exit);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call(true));
        assertEquals(41L, root.getCallTarget().call(false));
    }

    @Test
    public void testSiblingBlocks() {
        BasicInterpreter root = parseNode("siblingBlocks", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAdd();

            b.beginBlock();
            StackValue first = emitBindStackValue(b, 20L);
            b.emitLoadStackValue(first);
            b.endBlock();

            b.beginBlock();
            StackValue second = emitBindStackValue(b, 22L);
            b.emitLoadStackValue(second);
            b.endBlock();

            b.endAdd();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testConditionalBranches() {
        BasicInterpreter root = parseNode("conditionalBranches", b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginConditional();
            b.emitLoadArgument(0);

            b.beginBlock();
            StackValue thenValue = emitBindStackValue(b, 40L);
            b.beginAdd();
            b.emitLoadStackValue(thenValue);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endBlock();

            b.beginBlock();
            StackValue elseValue = emitBindStackValue(b, 50L);
            b.beginAdd();
            b.emitLoadStackValue(elseValue);
            b.emitLoadConstant(2L);
            b.endAdd();
            b.endBlock();

            b.endConditional();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(41L, root.getCallTarget().call(true));
        assertEquals(52L, root.getCallTarget().call(false));
    }

    @Test
    public void testTryFinallyOuterAccess() {
        BasicInterpreter root = parseNode("tryFinallyOuterAccess", b -> {
            b.beginRoot();
            BytecodeLocal result = b.createLocal();

            b.beginBlock();
            StackValue x = emitBindStackValue(b, 41L);

            b.beginTryFinally(() -> {
                b.beginStoreLocal(result);
                b.beginAdd();
                b.emitLoadStackValue(x);
                b.emitLoadConstant(1L);
                b.endAdd();
                b.endStoreLocal();
            });
            b.emitVoidOperation();
            b.endTryFinally();

            b.endBlock();

            b.beginReturn();
            b.emitLoadLocal(result);
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testClearedOnNormalExit() {
        BasicInterpreter root = parseNode("clearedOnNormalExit", b -> {
            b.beginRoot();

            b.beginBlock();
            for (int i = 0; i < 10; i++) {
                emitBindStackValueArgument(b, 0);
            }
            b.emitLoadConstant(42L);
            b.endBlock();

            b.beginReturn();
            b.emitMaterializeFrame();
            b.endReturn();
            b.endRoot();
        });

        Object marker = new Object();
        MaterializedFrame frame = (MaterializedFrame) root.getCallTarget().call(marker);
        assertFrameDoesNotContainValue(frame, marker);
    }

    @Test
    public void testClearedOnVoidExit() {
        BasicInterpreter root = parseNode("clearedOnVoidExit", b -> {
            b.beginRoot();

            b.beginBlock();
            for (int i = 0; i < 10; i++) {
                emitBindStackValueArgument(b, 0);
            }
            b.endBlock();

            b.beginReturn();
            b.emitMaterializeFrame();
            b.endReturn();
            b.endRoot();
        });

        Object marker = new Object();
        MaterializedFrame frame = (MaterializedFrame) root.getCallTarget().call(marker);
        assertFrameDoesNotContainValue(frame, marker);
    }

    @Test
    public void testClearedOnEarlyExit() {
        BasicInterpreter root = parseNode("clearedOnEarlyExit", b -> {
            b.beginRoot();
            BytecodeLabel exit = b.createLabel();

            b.beginBlock();
            for (int i = 0; i < 10; i++) {
                emitBindStackValueArgument(b, 0);
            }
            b.emitBranch(exit);
            b.emitLoadConstant(42L);
            b.endBlock();

            b.emitLabel(exit);
            b.beginReturn();
            b.emitMaterializeFrame();
            b.endReturn();
            b.endRoot();
        });

        Object marker = new Object();
        MaterializedFrame frame = (MaterializedFrame) root.getCallTarget().call(marker);
        assertFrameDoesNotContainValue(frame, marker);
    }

    @Test
    public void testClearedOnBranchOutFromInitializer() {
        BasicInterpreter root = parseNode("clearedOnBranchOutFromInitializer", b -> {
            b.beginRoot();
            BytecodeLabel exit = b.createLabel();

            b.beginBlock();
            for (int i = 0; i < 9; i++) {
                emitBindStackValueArgument(b, 0);
            }

            b.beginBindStackValue();
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadArgument(1);
            b.emitBranch(exit);
            b.endIfThen();
            b.emitLoadConstant(123L);
            b.endBlock();
            b.endBindStackValue();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();
            b.endBlock();

            b.emitLabel(exit);
            b.beginReturn();
            b.emitMaterializeFrame();
            b.endReturn();
            b.endRoot();
        });

        Object marker = new Object();
        MaterializedFrame frame = (MaterializedFrame) root.getCallTarget().call(marker, true);
        assertFrameDoesNotContainValue(frame, marker);
    }

    @Test
    public void testBindMustProduceValue() {
        assertParseFailure("Operation BindStackValue expected a value-producing child at position 0, but a void one was provided.", IllegalStateException.class, b -> {
            b.beginRoot();
            b.beginBlock();
            b.beginBindStackValue();
            b.emitVoidOperation();
            b.endBindStackValue();
            b.endRoot();
        });
    }

    @Test
    public void testInvalidBindLocation() {
        assertParseFailure("BindStackValue can only be used in a custom operation or Block.", IllegalStateException.class, b -> {
            b.beginRoot();
            b.beginConditional();
            b.beginBindStackValue();
            b.emitLoadConstant(true);
            b.endBindStackValue();
            b.emitLoadConstant(1L);
            b.emitLoadConstant(2L);
            b.endConditional();
            b.endRoot();
        });
    }

    @Test
    public void testBindInSourceSection() {
        Source source = Source.newBuilder("test", "return 42", "bindInSourceSection").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            b.beginSource(source);
            b.beginSourceSection(0, source.getLength());

            b.beginRoot();
            b.beginReturn();
            b.beginBlock();

            b.beginSourceSection(7, 2);
            StackValue x = emitBindStackValue(b, 42L);
            b.endSourceSection();

            b.emitLoadStackValue(x);

            b.endBlock();
            b.endReturn();
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });

        BasicInterpreter root = nodes.getNode(0);
        assertEquals(42L, root.getCallTarget().call());

        assertTrue(nodes.ensureSourceInformation());
        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBindMultipleInSourceSection() {
        Source source = Source.newBuilder("test", "return 42", "bindMultipleInSourceSection").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(source);
            b.beginSourceSection(0, source.getLength());

            b.beginRoot();
            b.beginReturn();
            b.beginBlock();

            b.beginSourceSection(7, 2);
            StackValue x = emitBindStackValue(b, 30L);
            StackValue y = emitBindStackValue(b, 12L);
            b.emitVoidOperation();
            b.endSourceSection();

            b.beginAdd();
            b.emitLoadStackValue(x);
            b.emitLoadStackValue(y);
            b.endAdd();

            b.endBlock();
            b.endReturn();
            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });

        BasicInterpreter root = nodes.getNode(0);
        assertEquals(42L, root.getCallTarget().call());
    }

    @Test
    public void testBindInTagFails() {
        try (Context context = Context.create(BytecodeDSLTestLanguage.ID)) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            context.enter();

            assertThrowsWithMessage("BindStackValue can only be used in a custom operation or Block.", IllegalStateException.class, () -> createNodes(createBytecodeConfigBuilder().addTag(
                            ExpressionTag.class).build(), b -> {
                                b.beginRoot();
                                b.beginReturn();
                                b.beginBlock();

                                b.beginTag(ExpressionTag.class);
                                b.beginBindStackValue();
                                b.emitLoadConstant(42L);
                                StackValue x = b.endBindStackValue();
                                b.endTag(ExpressionTag.class);

                                b.emitLoadStackValue(x);

                                b.endBlock();
                                b.endReturn();
                                b.endRoot();
                            }));
        }
    }

    @Test
    public void testBindInInstrumentationFails() {
        assertThrowsWithMessage("BindStackValue can only be used in a custom operation or Block.", IllegalStateException.class, () -> createNodes(createBytecodeConfigBuilder().addInstrumentation(
                        BasicInterpreter.IncrementValue.class).build(), b -> {
                            b.beginRoot();
                            b.beginReturn();
                            b.beginBlock();

                            b.beginIncrementValue();
                            b.beginBindStackValue();
                            b.emitLoadConstant(42L);
                            b.endBindStackValue();
                            b.endIncrementValue();

                            b.endBlock();
                            b.endReturn();
                            b.endRoot();
                        }));
    }

    @Test
    public void testInvalidAccesses() {
        String wrongRootMessage = "Stack value must belong to the current root node.";
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, siblingRootsTest());
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, nestedRootsInnerAccessTest());
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, nestedRootsOuterAccessTest());

        String inactiveOperationMessage = "Stack value must belong to an active custom operation or Block in the current root node.";
        assertParseFailure(inactiveOperationMessage, IllegalArgumentException.class, outOfScopeTest());
    }

    @Test
    public void testInvalidStores() {
        String wrongRootMessage = "Stack value must belong to the current root node.";
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, siblingRootsStoreTest());
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, nestedRootsInnerStoreTest());
        assertParseFailure(wrongRootMessage, IllegalArgumentException.class, nestedRootsOuterStoreTest());

        String inactiveOperationMessage = "Stack value must belong to an active custom operation or Block in the current root node.";
        assertParseFailure(inactiveOperationMessage, IllegalArgumentException.class, outOfScopeStoreTest());
    }

    private <T extends Throwable> void assertParseFailure(String message, Class<T> expectedThrowable, BytecodeParser<BasicInterpreterBuilder> parser) {
        assertThrowsWithMessage(message, expectedThrowable, () -> createNodes(BytecodeConfig.DEFAULT, parser));
    }

    private static <T extends BasicInterpreterBuilder> void emitStoreStackValue(T b, StackValue stackValue, long value) {
        b.beginStoreStackValue(stackValue);
        b.emitLoadConstant(value);
        b.endStoreStackValue();
    }

    private static <T extends BasicInterpreterBuilder> StackValue emitBindStackValue(T b, long value) {
        b.beginBindStackValue();
        b.emitLoadConstant(value);
        return b.endBindStackValue();
    }

    private static <T extends BasicInterpreterBuilder> StackValue emitBindStackValueArgument(T b, int argumentIndex) {
        b.beginBindStackValue();
        b.emitLoadArgument(argumentIndex);
        return b.endBindStackValue();
    }

    private static void assertFrameDoesNotContainValue(MaterializedFrame frame, Object value) {
        for (int i = 0; i < frame.getFrameDescriptor().getNumberOfSlots(); i++) {
            if (frame.getTag(i) != FrameSlotKind.Illegal.tag) {
                assertFalse(frame.getValue(i) == value);
            }
        }
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> siblingRootsTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);
            b.endBlock();
            b.endRoot();

            b.beginRoot();
            b.emitLoadStackValue(x);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> siblingRootsStoreTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);
            b.endBlock();
            b.endRoot();

            b.beginRoot();
            emitStoreStackValue(b, x, 43L);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsInnerAccessTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);

            b.beginRoot();
            b.emitLoadStackValue(x);
            b.endRoot();

            b.endBlock();
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsInnerStoreTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);

            b.beginRoot();
            emitStoreStackValue(b, x, 43L);
            b.endRoot();

            b.endBlock();
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsOuterAccessTest() {
        return b -> {
            b.beginRoot();

            b.beginRoot();
            b.beginBlock();
            StackValue y = emitBindStackValue(b, 42L);
            b.endBlock();
            b.endRoot();

            b.emitLoadStackValue(y);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> nestedRootsOuterStoreTest() {
        return b -> {
            b.beginRoot();

            b.beginRoot();
            b.beginBlock();
            StackValue y = emitBindStackValue(b, 42L);
            b.endBlock();
            b.endRoot();

            emitStoreStackValue(b, y, 43L);
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> outOfScopeTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);
            b.endBlock();

            b.beginBlock();
            b.emitLoadStackValue(x);
            b.endBlock();
            b.endRoot();
        };
    }

    private static <T extends BasicInterpreterBuilder> BytecodeParser<T> outOfScopeStoreTest() {
        return b -> {
            b.beginRoot();
            b.beginBlock();
            StackValue x = emitBindStackValue(b, 42L);
            b.endBlock();

            b.beginBlock();
            emitStoreStackValue(b, x, 43L);
            b.endBlock();
            b.endRoot();
        };
    }

}

@ExpectWarning({
                "Custom operation with name BindStackValue conflicts with a built-in operation with the same name. The built-in operation will not be generated.%",
                "Custom operation with name LoadStackValue conflicts with a built-in operation with the same name. The built-in operation will not be generated.%",
                "Custom operation with name StoreStackValue conflicts with a built-in operation with the same name. The built-in operation will not be generated.%"})
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class HidesStackValueBuiltins extends RootNode implements BytecodeRootNode {

    protected HidesStackValueBuiltins(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class BindStackValue {
        @Specialization
        public static Object doBind(Object value) {
            return value;
        }
    }

    @Operation
    public static final class LoadStackValue {
        @Specialization
        public static Object doLoad() {
            return null;
        }
    }

    @Operation
    public static final class StoreStackValue {
        @Specialization
        public static void doStore(@SuppressWarnings("unused") Object value) {
        }
    }

}
