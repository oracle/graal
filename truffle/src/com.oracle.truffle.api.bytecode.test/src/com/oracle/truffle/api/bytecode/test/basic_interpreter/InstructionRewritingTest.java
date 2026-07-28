/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import static com.oracle.truffle.api.bytecode.test.AbstractInstructionTest.assertInstructions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.ExceptionHandler.HandlerKind;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class InstructionRewritingTest extends AbstractBasicInterpreterTest {

    public InstructionRewritingTest(TestRun run) {
        super(run);
    }

    @Test
    public void testLoadConstantPop() {
        BasicInterpreter node = parseNode("loadConstantPop", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();
            // this result is popped.
            b.emitLoadConstant(21L);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "load.constant",
                            "pop",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testLoadNullPop() {
        BasicInterpreter node = parseNode("loadNullPop", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();
            // this result is popped.
            b.emitLoadNull();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "load.null",
                            "pop",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testLoadArgPop() {
        BasicInterpreter node = parseNode("loadArgPop", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();
            // this result is popped.
            b.emitLoadArgument(0);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "load.argument",
                            "pop",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call(123L));
    }

    @Test
    public void testLoadLocalPop() {
        BasicInterpreter node = parseNode("loadLocalPop", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();
            // initialize the local, else a load is invalid.
            b.beginStoreLocal(local);
            b.emitLoadConstant(123L);
            b.endStoreLocal();

            b.beginBlock();

            // this result is popped.
            b.emitLoadLocal(local);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.constant",
                            "store.local",
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "load.constant",
                            "store.local",
                            "load.local",
                            "pop",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call());

    }

    @Test
    public void testClearLocalDuplicate() {
        BasicInterpreter node = parseNode("clearLocalDuplicate", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginBlock();
            b.emitClearLocal(local);
            b.emitClearLocal(local);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "clear.local",
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "clear.local",
                            "clear.local",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRewriteAcrossBlockStart() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("rewriteAcrossBlockStart", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.emitClearLocal(x);

            b.beginBlock();
            b.emitClearLocal(x);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRewriteAcrossBlockEnd() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("rewriteAcrossBlockEnd", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.beginBlock();
            b.emitClearLocal(x); // kept
            b.endBlock();
            b.emitClearLocal(x); // removed

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");

        Instruction last = node.getBytecodeNode().getInstructionsAsList().getLast();
        List<LocalVariable> locals = node.getBytecodeNode().getLocals();
        assertEquals(1, locals.size());
        if (run.hasBlockScoping()) {
            assertEquals(last.getNextBytecodeIndex(), locals.getFirst().getEndIndex());
        } else {
            assertEquals(-1, locals.getFirst().getEndIndex());
        }
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRewriteAcrossBranchUnwind() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("rewriteAcrossBranchUnwind", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLabel lbl = b.createLabel();

            b.beginBlock();
            BytecodeLocal x = b.createLocal();
            b.emitClearLocal(x); // explicit
            b.emitBranch(lbl); // block unwind emits another clear.local(x)
            b.endBlock();

            b.emitLabel(lbl);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "branch",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRewriteAcrossBranchUnwindFixSource() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "clear;nop;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("rewriteAcrossBranchUnwindFixSource", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            b.beginSource(source);
            b.beginSourceSection(0, 20); // clear;nop;return 42;
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();

            b.beginBlock();
            BytecodeLocal x = b.createLocal();

            b.beginSourceSection(0, 6); // clear;
            b.emitClearLocal(x); // explicit
            b.endSourceSection();

            b.beginSourceSection(6, 4); // nop;
            b.emitBranch(lbl); // block unwind emits another clear.local(x)
            b.endSourceSection();
            b.endBlock();

            b.emitLabel(lbl);
            b.beginSourceSection(10, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endBlock();
            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "branch",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "clear;", "clear;nop;return 42;");
        assertSourceSectionsForInstructions(node, 1, 2, "nop;", "clear;nop;return 42;");
        assertSourceSectionsForInstructions(node, 2, 4, "return 42;", "clear;nop;return 42;");
    }

    @Test
    public void testMultipleRewrites() {
        BasicInterpreter node = parseNode("multipleRewrites", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();
            // these results are popped.
            b.emitLoadConstant(21L);
            b.emitLoadArgument(0);
            b.emitLoadNull();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.constant",
                            "return");
        } else {
            assertInstructions(node,
                            "load.constant",
                            "pop",
                            "load.argument",
                            "pop",
                            "load.null",
                            "pop",
                            "load.constant",
                            "return");
        }
        assertEquals(42L, node.getCallTarget().call(123L));
    }

    @Test
    public void testBranchCleanupRewrites() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("branchCleanupRewrites", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLabel lbl = b.createLabel();

            b.beginReturn();
            for (int i = 0; i < 5; i++) {
                b.beginAdd();
                b.emitLoadArgument(0);
            }

            b.beginBlock();
            b.emitBranch(lbl);
            b.emitLoadArgument(0);
            b.endBlock();

            for (int i = 0; i < 5; i++) {
                b.endAdd();
            }
            b.endReturn();

            b.emitLabel(lbl);
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "branch",
                        "load.constant",
                        "return");
        List<Instruction> oldInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertEquals(42L, node.getCallTarget().call(123L));

        BytecodeConfig config = run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build();
        node.getRootNodes().update(config);

        assertInstructions(node,
                        "load.argument",
                        "load.argument",
                        "load.argument",
                        "load.argument",
                        "load.argument",
                        "pop",
                        "pop",
                        "pop",
                        "pop",
                        "pop",
                        "branch",
                        "load.constant",
                        "return");
        List<Instruction> newInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertUpdatedInstructions(oldInstructions, newInstructions, 10, 11, 12);
    }

    @Test
    public void testNestedTryFinallyBranchDuringReturn() {
        // This is a regression test that revealed the need to remap the delta remap table.
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("nestedTryFinallyBranchDuringReturn", b -> {
            b.beginRoot();

            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginTryFinally(() -> b.emitBranch(lbl));
                emitReturn(b, 2);
                b.endTryFinally();
            });
            emitReturn(b, 1);
            b.endTryFinally();

            b.emitLabel(lbl);
            emitReturn(b, 42);

            b.endRoot();
        });

        List<Instruction> oldInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertEquals(42L, node.getCallTarget().call());

        BytecodeConfig config = run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build();
        node.getRootNodes().update(config);

        assertEquals(42L, node.getCallTarget().call());
        for (Instruction oldInstruction : oldInstructions) {
            Instruction newInstruction = oldInstruction.getLocation().update().getInstruction();
            assertEquals(oldInstruction.getName(), newInstruction.getName());
        }
    }

    /**
     * Rewriting complicates bci translation. We should generate and use the necessary metadata to remap a bci from the rewritten world to the non-rewritten world.
     */
    @Test
    public void testTranslateRewrittenBytecodeIndex() {
        BasicInterpreter node = parseNode("translateRewrittenBytecodeIndex", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadNull();
            b.endStoreLocal();

            b.beginIncrementValue();
            // rewrite 1: load.constant, pop ->
            b.emitLoadConstant(21L);
            b.endIncrementValue();

            b.emitClearLocal(x);

            // rewrite 2: load.argument, pop ->
            b.beginIncrementValue();
            b.emitLoadArgument(0);
            b.endIncrementValue();
            // rewrite 3: load.null, pop ->
            b.beginIncrementValue();
            b.emitLoadNull();
            b.endIncrementValue();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endRoot();
        });

        List<Instruction> instructionsToTest;
        if (run.hasInstructionRewriting()) {
            assertInstructions(node,
                            "load.null",
                            "store.local",
                            "clear.local",
                            "load.constant",
                            "return");

            instructionsToTest = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        } else {
            assertInstructions(node,
                            "load.null",
                            "store.local",
                            "load.constant",
                            "pop",
                            "clear.local",
                            "load.argument",
                            "pop",
                            "load.null",
                            "pop",
                            "load.constant",
                            "return");
            List<Instruction> filtered = filterTrace(node.getBytecodeNode().getInstructionsAsList());
            instructionsToTest = List.of(filtered.get(0), filtered.get(1), filtered.get(4), filtered.get(9), filtered.get(10));
        }
        assertEquals(42L, node.getCallTarget().call(123));
        assertEquals("load.null", instructionsToTest.get(0).getName());
        assertEquals("store.local", instructionsToTest.get(1).getName());
        assertEquals("clear.local", instructionsToTest.get(2).getName());
        assertEquals("load.constant", instructionsToTest.get(3).getName());
        assertEquals("return", instructionsToTest.get(4).getName());

        BytecodeConfig config = run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build();
        node.getRootNodes().update(config);

        assertInstructions(node,
                        "load.null",
                        "store.local",
                        "load.constant",
                        "c.IncrementValue",
                        "pop",
                        "clear.local",
                        "load.argument",
                        "c.IncrementValue",
                        "pop",
                        "load.null",
                        "c.IncrementValue",
                        "pop",
                        "load.constant",
                        "return");
        List<Instruction> newInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertUpdatedInstructions(instructionsToTest, newInstructions, 0, 1, 5, 12, 13);
    }

    @Test
    public void testClearLocalDuplicateTranslateRewrittenBytecodeIndex() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("clearLocalDuplicateTranslateRewrittenBytecodeIndex", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.emitClearLocal(x);
            b.emitClearLocal(x);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        List<Instruction> oldInstructions = node.getBytecodeNode().getInstructionsAsList();

        BytecodeConfig config = run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build();
        node.getRootNodes().update(config);

        List<Instruction> newInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertInstructions(node,
                        "clear.local",
                        "clear.local",
                        "load.constant",
                        "return");
        assertUpdatedInstructions(oldInstructions, newInstructions, 0, 2, 3);
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testMultipleChainedRewrites() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("multipleChainedRewrites", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(1L);
            b.endStoreLocal();
            b.emitClearLocal(x);
            b.emitClearLocal(x);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testFixLocalIndices() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("fixLocalIndices", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();

            // This load will be popped, triggering a rewrite.
            b.emitLoadNull();

            // This local is initially created with startBci after the load.null. When pop is
            // emitted, and the load.null is rewritten, the builder should fix up startBci.
            b.createLocal();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        assertInstructions(node,
                        "load.constant",
                        "return");

        List<LocalVariable> locals = node.getBytecodeNode().getLocals();
        assertEquals(1, locals.size());
        LocalVariable x = locals.getFirst();
        assertLocalStartInstruction(node, x, "load.constant");

        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testClearLocalDuplicateFixLocalIndices() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("clearLocalDuplicateFixLocalIndices", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();

            b.beginBlock();
            b.emitClearLocal(x); // kept
            b.createLocal(); // y
            b.emitClearLocal(x); // deleted

            b.createLocal(); // z

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endBlock();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");

        List<LocalVariable> locals = node.getBytecodeNode().getLocals();
        assertEquals(3, locals.size());
        LocalVariable x = locals.get(0);
        LocalVariable y = locals.get(1);
        LocalVariable z = locals.get(2);
        assertLocalStartInstruction(node, x, "clear.local");
        assertLocalStartInstruction(node, y, "load.constant");
        assertLocalStartInstruction(node, z, "load.constant");
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRedundantStoreFixLocalIndices() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("redundantStoreFixLocalIndices", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            b.beginBlock();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x); // deleted
            b.emitLoadConstant(1L); // deleted
            b.endStoreLocal();
            b.endBlock(); // emits clear.local(x), which is kept by the rewrite

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");

        List<LocalVariable> locals = node.getBytecodeNode().getLocals();
        assertEquals(1, locals.size());
        LocalVariable y = locals.getFirst();
        assertLocalStartInstruction(node, y, "clear.local");
        assertLocalEndInstruction(node, y, "clear.local");
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRedundantStoreTranslateRewrittenBytecodeIndex() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("redundantStoreTranslateRewrittenBytecodeIndex", (BasicInterpreterBuilder b) -> {
            b.beginRoot();

            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(1L);
            b.endStoreLocal();
            b.emitClearLocal(x);

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        List<Instruction> oldInstructions = node.getBytecodeNode().getInstructionsAsList();

        BytecodeConfig config = run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build();
        node.getRootNodes().update(config);

        List<Instruction> newInstructions = filterTrace(node.getBytecodeNode().getInstructionsAsList());
        assertInstructions(node,
                        "load.constant",
                        "store.local",
                        "clear.local",
                        "load.constant",
                        "return");
        assertUpdatedInstructions(oldInstructions, newInstructions, 2, 3, 4);
        assertEquals(42L, node.getCallTarget().call());
    }

    @Test
    public void testRedundantStoreFixSourceKeptSuffix() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "x=1;clear;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("redundantStoreFixSourceKeptSuffix", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginSource(source);

            b.beginSourceSection(0, 10); // x=1;clear;
            b.beginSourceSection(0, 4); // x=1;
            b.beginStoreLocal(x);
            b.emitLoadConstant(1L); // deleted
            b.endStoreLocal();
            b.endSourceSection();

            b.beginSourceSection(4, 6); // clear;
            b.emitClearLocal(x); // kept
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(10, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "clear;", "x=1;clear;");
        assertSourceSectionsForInstructions(node, 1, 3, "return 42;");
        assertDeletedSourceSection(node, "x=1;");
    }

    /**
     * Tests a source section that starts before a deleted prefix and ends inside it.
     */
    @Test
    public void testRedundantStoreFixSourcePrefix() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "void;x=1;clear;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("redundantStoreFixSourcePrefix", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginSource(source);

            b.beginSourceSection(0, 9); // void;x=1;
            b.emitVoidOperation();
            b.beginStoreLocal(x);
            b.emitLoadConstant(1L); // deleted
            b.endStoreLocal();
            b.endSourceSection();

            b.beginSourceSection(9, 6); // clear;
            b.emitClearLocal(x); // kept
            b.endSourceSection();

            b.beginSourceSection(15, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "clear.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "void;x=1;");
        assertSourceSectionsForInstructions(node, 1, 2, "clear;");
        assertSourceSectionsForInstructions(node, 2, 4, "return 42;");
    }

    @Test
    public void testClearLocalDuplicateFixSourceSuffix() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "keep;drop;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("clearLocalDuplicateFixSourceSuffix", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal();

            b.beginBlock();
            b.beginSource(source);

            b.beginSourceSection(0, 5); // keep;
            b.emitClearLocal(x); // kept
            b.endSourceSection();

            b.beginSourceSection(5, 5); // drop;
            b.emitClearLocal(x); // deleted
            b.endSourceSection();

            b.beginSourceSection(10, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endBlock();
            b.endRoot();
        });

        assertInstructions(node,
                        "clear.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "keep;");
        assertSourceSectionsForInstructions(node, 1, 3, "return 42;");
        assertEmptySourceSection(node, "drop;");
    }

    /**
     * Tests a source section that ends inside the LHS of a deletion rule.
     */
    @Test
    public void testDeletionFixSourcePrefix() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "void;x=1;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("deletionFixSourcePrefix", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            b.beginBlock();
            b.beginSource(source);

            b.beginSourceSection(0, 5); // void;
            b.emitVoidOperation();
            b.endSourceSection();

            b.beginSourceSection(5, 4); // x=1;
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(1L);
            b.endStoreLocal();
            // This instruction will be deleted. Shrink the enclosing source section.
            b.emitLoadNull();
            b.endSourceSection();

            b.beginSourceSection(9, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endBlock();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "load.constant",
                        "store.local",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "void;");
        assertSourceSectionsForInstructions(node, 1, 3, "x=1;");
        assertSourceSectionsForInstructions(node, 3, 5, "return 42;");
    }

    /**
     * Tests source sections contained in the LHS of a deletion rule.
     */
    @Test
    public void testDeletionFixSourceContained() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "void;nop;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("deletionFixSourceContained", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            b.beginBlock();
            b.beginSource(source);

            b.beginSourceSection(0, 5); // void;
            b.emitVoidOperation();
            b.endSourceSection();

            b.beginSourceSection(5, 4); // nop;
            b.beginSourceSection(5, 3); // nop
            b.emitLoadNull(); // will be deleted
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(9, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endBlock();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "void;");
        assertSourceSectionsForInstructions(node, 1, 3, "return 42;");

        // The source sections corresponding to the deleted instruction should be deleted.
        assertDeletedSourceSection(node, "nop;");
        assertDeletedSourceSection(node, "nop");
    }

    /**
     * Tests source sections contained in the LHS of a deletion rule.
     */
    @Test
    public void testDeletionFixSourceContained2() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "void;nop;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("deletionFixSourceContained2", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            b.beginBlock();
            b.beginSource(source);

            // Unlike the previous test, this test contains an outer enclosing entry. Fix-ups should
            // work the same, but the removed entries will have an empty bytecode range instead of
            // being deleted.
            b.beginSourceSection(0, 9); // void;nop;
            b.beginSourceSection(0, 5); // void;
            b.emitVoidOperation();
            b.endSourceSection();

            b.beginSourceSection(5, 4); // nop;
            b.beginSourceSection(5, 3); // nop
            b.emitLoadNull(); // will be deleted
            b.endSourceSection();
            b.endSourceSection();
            b.endSourceSection();

            b.beginSourceSection(9, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();

            b.endSource();
            b.endBlock();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "void;", "void;nop;");
        assertSourceSectionsForInstructions(node, 1, 3, "return 42;");

        // The source sections corresponding to the deleted instruction should have empty bci
        // ranges.
        assertEmptySourceSection(node, "nop;");
        assertEmptySourceSection(node, "nop");
    }

    /**
     * Tests source sections that start inside the LHS of a deletion rule.
     */
    @Test
    public void testDeletionFixSourceSuffix() {
        assumeTrue(run.hasInstructionRewriting());

        Source source = Source.newBuilder("test", "void;nop;return 42;", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("deletionFixSourceSuffix", (BasicInterpreterBuilder b) -> {
            b.beginRoot();
            b.beginBlock();
            b.beginSource(source);

            b.beginSourceSection(0, 5); // void;
            b.emitVoidOperation();
            b.endSourceSection();

            b.beginSourceSection(5, 14); // nop;return 42;
            b.beginSourceSection(6, 3); // nop
            b.emitLoadNull(); // will be deleted
            b.endSourceSection();

            b.beginSourceSection(9, 10); // return 42;
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endSourceSection();
            b.endSourceSection();

            b.endSource();
            b.endBlock();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "load.constant",
                        "return");
        assertEquals(42L, node.getCallTarget().call());

        assertSourceSectionsForInstructions(node, 0, 1, "void;");
        assertSourceSectionsForInstructions(node, 1, 3, "return 42;", "nop;return 42;");

        assertDeletedSourceSection(node, "nop");
    }

    /**
     * Tests that rewriting across try entry remaps an on-stack try-finally start BCI.
     */
    @Test
    public void testTryFinallyFixHandlerStartBci() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("tryFinallyFixHandlerStartBci", b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.beginTryFinally(() -> b.emitVoidOperation());
            b.beginBlock();
            b.emitClearLocal(x);
            b.emitVoidOperation();
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(1, handlers.size());
        assertEquals(HandlerKind.CUSTOM, handlers.get(0).getKind());
        assertGuards(handlers.get(0), node, "clear.local", "c.VoidOperation");
    }

    /**
     * Tests that rewriting across try entry remaps an on-stack try-catch start BCI.
     */
    @Test
    public void testTryCatchFixHandlerStartBci() {
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("tryCatchFixHandlerStartBci", b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.beginTryCatch();
            b.beginBlock();
            b.emitClearLocal(x);
            b.emitVoidOperation();
            b.endBlock();
            b.emitVoidOperation();
            b.endTryCatch();
            b.endRoot();
        });

        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(1, handlers.size());
        assertEquals(HandlerKind.CUSTOM, handlers.get(0).getKind());
        assertGuards(handlers.get(0), node, "clear.local", "c.VoidOperation");
    }

    /**
     * Tests that rewriting across branch unwind fixes an already-emitted try-catch range end BCI.
     */
    @Test
    public void testTryCatchFixEmittedHandlerEndBci() {
        assumeTrue(run.hasBlockScoping());
        assumeTrue(run.hasInstructionRewriting());

        BasicInterpreter node = parseNode("tryCatchFixEmittedHandlerEndBci", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginBlock();
            BytecodeLocal x = b.createLocal();
            b.beginTryCatch();
            b.beginBlock();
            b.emitVoidOperation();
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.emitBranch(lbl); // block unwind emits clear.local(x)
            b.endBlock();
            b.emitVoidOperation();
            b.endTryCatch();
            b.endBlock();

            b.emitLabel(lbl);
            b.emitVoidOperation();
            b.endRoot();
        });

        assertInstructions(node,
                        "c.VoidOperation",
                        "clear.local",
                        "branch",
                        "c.VoidOperation",
                        "pop",
                        "clear.local",
                        "c.VoidOperation",
                        "load.null",
                        "return");

        List<ExceptionHandler> handlers = node.getBytecodeNode().getExceptionHandlers();
        assertEquals(2, handlers.size());
        assertEquals(HandlerKind.CUSTOM, handlers.get(0).getKind());
        assertEquals(HandlerKind.CUSTOM, handlers.get(1).getKind());
        assertGuards(handlers.get(0), node, "c.VoidOperation");
        assertGuards(handlers.get(1), node, "branch");
    }

    private static void assertGuards(ExceptionHandler handler, BasicInterpreter node, String... expectedInstructions) {
        List<String> actualInstructions = new java.util.ArrayList<>();
        for (Instruction instruction : node.getBytecodeNode().getInstructions()) {
            int bci = instruction.getBytecodeIndex();
            if (handler.getStartBytecodeIndex() <= bci && bci < handler.getEndBytecodeIndex()) {
                if (!instruction.getName().equals("trace.instruction")) {
                    actualInstructions.add(instruction.getName());
                }
            }
        }
        assertEquals(expectedInstructions.length, actualInstructions.size());
        for (int i = 0; i < expectedInstructions.length; i++) {
            assertTrue(actualInstructions.get(i).startsWith(expectedInstructions[i]));
        }
    }

    private void assertSourceSectionsForInstructions(BasicInterpreter node, int instructionStartIndex, int instructionEndIndex, String... sourceStrings) {
        int startIndex = instructionStartIndex;
        int endIndex = instructionEndIndex;
        if (run.testTracer()) {
            startIndex *= 2;
            endIndex *= 2;
        }

        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        for (int i = startIndex; i < endIndex; i++) {
            SourceSection[] sourceSections = instructions.get(i).getSourceSections();
            assertEquals("Instruction " + i + " did not have the expected number of source sections.", sourceStrings.length, sourceSections.length);
            for (int j = 0; j < sourceStrings.length; j++) {
                assertEquals("Instruction " + i + " did not have the expected source section at index " + j, sourceStrings[j], sourceSections[j].getCharacters());
            }
        }
    }

    private void assertLocalStartInstruction(BasicInterpreter node, LocalVariable local, String instructionName) {
        assertInstructionAtBci(node, local.getStartIndex(), instructionName, "Local start index");
    }

    private void assertLocalEndInstruction(BasicInterpreter node, LocalVariable local, String instructionName) {
        assertInstructionAtBci(node, local.getEndIndex(), instructionName, "Local end index");
    }

    private void assertInstructionAtBci(BasicInterpreter node, int bci, String instructionName, String description) {
        // Walk the instructions explicitly to avoid coincidentally interpreting an immediate as an
        // opcode.
        for (var instruction : node.getBytecodeNode().getInstructions()) {
            if (instruction.getBytecodeIndex() == bci) {
                Instruction actualInstruction;
                if (run.testTracer()) {
                    // Get the instruction after trace.instruction.
                    actualInstruction = node.getBytecodeNode().getInstruction(instruction.getNextBytecodeIndex());
                } else {
                    actualInstruction = instruction;
                }
                assertEquals(instructionName, actualInstruction.getName());
                return;
            }
            if (instruction.getBytecodeIndex() > bci) {
                fail(description + " does not point to a valid instruction.");
            }
        }
        fail(description + " does not point to a valid instruction.");
    }

    private static void assertUpdatedInstructions(List<Instruction> oldInstructions, List<Instruction> newInstructions, int... newInstructionIndexes) {
        assertEquals(newInstructionIndexes.length, oldInstructions.size());
        for (int i = 0; i < oldInstructions.size(); i++) {
            Instruction expectedInstruction = newInstructions.get(newInstructionIndexes[i]);
            Instruction actualInstruction = oldInstructions.get(i).getLocation().update().getInstruction();
            assertEquals(expectedInstruction.getName(), actualInstruction.getName());
            assertEquals(expectedInstruction.getBytecodeIndex(), actualInstruction.getBytecodeIndex());
        }
    }

    private static void assertDeletedSourceSection(BasicInterpreter node, String sourceString) {
        for (var sourceInfo : node.getBytecodeNode().getSourceInformation()) {
            if (sourceString.equals(sourceInfo.getSourceSection().getCharacters())) {
                fail("Source \"%s\" should have been deleted from the table.".formatted(sourceString));
            }
        }
    }

    private static void assertEmptySourceSection(BasicInterpreter node, String sourceString) {
        for (var sourceInfo : node.getBytecodeNode().getSourceInformation()) {
            if (sourceString.equals(sourceInfo.getSourceSection().getCharacters())) {
                assertEquals(sourceInfo.getStartBytecodeIndex(), sourceInfo.getEndBytecodeIndex());
                return;
            }
        }
        fail("Source \"%s\" not found.".formatted(sourceString));
    }

}
