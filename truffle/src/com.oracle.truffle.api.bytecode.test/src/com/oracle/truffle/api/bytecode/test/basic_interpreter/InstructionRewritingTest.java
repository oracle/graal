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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import static com.oracle.truffle.api.bytecode.test.AbstractInstructionTest.assertInstructions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeLocal;
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
        // The bci should point to the load.constant. Walk the instructions explicitly to avoid
        // coincidentally interpreting an immediate as an opcode.
        for (var instruction : node.getBytecodeNode().getInstructions()) {
            if (instruction.getBytecodeIndex() == x.getStartIndex()) {
                Instruction localStartInstruction;
                if (run.testTracer()) {
                    // Get the instruction after trace.instruction.
                    localStartInstruction = node.getBytecodeNode().getInstruction(instruction.getNextBytecodeIndex());
                } else {
                    localStartInstruction = instruction;
                }
                assertEquals("load.constant", localStartInstruction.getName());
                break;
            }
            if (instruction.getBytecodeIndex() > x.getStartIndex()) {
                fail("Local start index does not point to a valid instruction.");
            }
        }

        assertEquals(42L, node.getCallTarget().call());
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
