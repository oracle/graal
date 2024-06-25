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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class SourcesTest extends AbstractBasicInterpreterTest {

    private static void assertInstructionSourceSection(Instruction i, Source source, int startIndex, int length) {
        assertSourceSection(i.getLocation().getSourceLocation(), source, startIndex, length);
    }

    private static void assertSourceSection(SourceSection section, Source source, int startIndex, int length) {
        assertSame(source, section.getSource());
        assertEquals(startIndex, section.getCharIndex());
        assertEquals(length, section.getCharLength());
    }

    private static void assertSourceSections(SourceSection[] sections, Source source, int... pairs) {
        assert pairs.length % 2 == 0;
        assertEquals(pairs.length / 2, sections.length);

        for (int i = 0; i < sections.length; i++) {
            assertSourceSection(sections[i], source, pairs[2 * i], pairs[2 * i + 1]);
        }
    }

    @Test
    public void testSource() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        assertSourceSection(node.getSourceSection(), source, 0, 8);

        BytecodeNode bytecode = node.getBytecodeNode();
        List<Instruction> instructions = bytecode.getInstructionsAsList();
        assertInstructionSourceSection(instructions.get(0), source, 7, 1);
        assertInstructionSourceSection(instructions.get(1), source, 0, 8);
    }

    @Test
    public void testManySourceSections() {
        final int numSourceSections = 1000;
        StringBuilder sb = new StringBuilder(numSourceSections);
        for (int i = 0; i < numSourceSections; i++) {
            sb.append("x");
        }
        Source source = Source.newBuilder("test", sb.toString(), "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            for (int i = 0; i < numSourceSections; i++) {
                b.beginSourceSection(0, numSourceSections - i);
            }

            b.beginReturn();
            b.emitGetSourcePositions();
            b.endReturn();

            for (int i = 0; i < numSourceSections; i++) {
                b.endSourceSection();
            }
            b.endSource();
            b.endRoot();
        });

        SourceSection[] result = (SourceSection[]) node.getCallTarget().call();
        assertEquals(numSourceSections, result.length);
        for (int i = 0; i < numSourceSections; i++) {
            // sections are emitted in order of closing
            assertSourceSection(result[i], source, 0, i + 1);
        }
    }

    @Test
    public void testWithoutSource() {
        BasicInterpreter node = parseNode("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.emitLoadConstant(1L);
            b.endReturn();
            b.endRoot();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        List<Instruction> instructions = bytecode.getInstructionsAsList();
        BytecodeLocation location1 = instructions.get(0).getLocation();
        BytecodeLocation location2 = instructions.get(1).getLocation();

        assertNull(location1.getSourceLocation());
        assertNull(location2.getSourceLocation());
    }

    @Test
    public void testSourceNoSourceSet() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.");
        parseNodeWithSource("sourceNoSourceSet", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endRoot();
        });
    }

    @Test
    public void testSourceMultipleSources() {
        Source source1 = Source.newBuilder("test", "This is just a piece of test source.", "test1.test").build();
        Source source2 = Source.newBuilder("test", "This is another test source.", "test2.test").build();
        BasicInterpreter root = parseNodeWithSource("sourceMultipleSources", b -> {
            b.beginSource(source1);
            b.beginSourceSection(0, source1.getLength());

            b.beginRoot(LANGUAGE);

            b.emitVoidOperation(); // no source
            b.beginBlock();
            b.emitVoidOperation(); // no source

            b.beginSourceSection(1, 2);

            b.beginBlock();
            b.emitVoidOperation(); // source1, 1, 2
            b.beginSource(source2);
            b.beginBlock();
            b.emitVoidOperation(); // source1, 1, 2

            b.beginSourceSection(3, 4);
            b.beginBlock();
            b.emitVoidOperation(); // source2, 3, 4

            b.beginSourceSection(5, 1);
            b.beginBlock();
            b.emitVoidOperation(); // source2, 5, 1
            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // source2, 3, 4
            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // source1, 1, 2
            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // source1, 1, 2

            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // no source

            b.endBlock();

            b.emitVoidOperation(); // no source

            b.endRoot();

            b.endSourceSection();
            b.endSource();
        });

        assertSourceSection(root.getSourceSection(), source1, 0, source1.getLength());

        List<Instruction> instructions = root.getBytecodeNode().getInstructionsAsList();
        assertInstructionSourceSection(instructions.get(0), source1, 0, source1.getLength());
        assertInstructionSourceSection(instructions.get(1), source1, 0, source1.getLength());
        assertInstructionSourceSection(instructions.get(2), source1, 1, 2);
        assertInstructionSourceSection(instructions.get(3), source1, 1, 2);
        assertInstructionSourceSection(instructions.get(4), source2, 3, 4);
        assertInstructionSourceSection(instructions.get(5), source2, 5, 1);
        assertInstructionSourceSection(instructions.get(6), source2, 3, 4);
        assertInstructionSourceSection(instructions.get(7), source1, 1, 2);
        assertInstructionSourceSection(instructions.get(8), source1, 1, 2);
        assertInstructionSourceSection(instructions.get(9), source1, 0, source1.getLength());
        assertInstructionSourceSection(instructions.get(10), source1, 0, source1.getLength());
    }

    @Test
    public void testGetSourcePosition() {
        Source source = Source.newBuilder("test", "return 1", "testGetSourcePosition").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitGetSourcePositions();
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        SourceSection[] result = (SourceSection[]) node.getCallTarget().call();

        assertSourceSections(result, source, 7, 1, 0, 8);
    }

    @Test
    public void testSourceFinallyTry() {
        // Finally handlers get emitted multiple times. Each handler's source info should be emitted
        // as expected.

        /** @formatter:off
         *  try:
         *    if arg0 < 0: throw
         *    if 0 < arg0: return
         *  finally:
         *    return sourcePosition
         *  @formatter:on
         */

        Source source = Source.newBuilder("test", "try finally", "sourceFinallyTry").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 11);

            b.beginFinallyTry(b.createLocal(), () -> {
                // finally
                b.beginSourceSection(4, 7);
                b.beginReturn();
                b.emitGetSourcePositions();
                b.endReturn();
                b.endSourceSection();
            });
            // try
            b.beginSourceSection(0, 4);
            b.beginBlock();
            // if arg0 < 0, throw
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.beginThrowOperation();
            b.emitLoadConstant(0L);
            b.endThrowOperation();

            b.endIfThen();

            // if 0 < arg0, return
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadConstant(0L);
            b.emitLoadArgument(0);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endIfThen();
            b.endBlock();
            b.endSourceSection();

            b.endFinallyTry();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        long[] inputs = new long[]{0, -1, 1};
        for (int i = 0; i < inputs.length; i++) {
            SourceSection[] result = (SourceSection[]) node.getCallTarget().call(inputs[i]);
            // TODO: source bci ranges need to be opened/closed when emitting finally handlers
            // in-line.
            if (inputs[i] == 1) {
                assertSourceSections(result, source, 4, 7, 0, 4, 0, 11);
            } else {
                assertSourceSections(result, source, 4, 7, 0, 11);
            }
        }
    }

    @Test
    public void testSourceRootNodeDeclaredInFinallyTry() {
        // Same idea as above, but ensures root nodes declared in a finally handler inherit sources
        // declared outside the root node.

        /** @formatter:off
         *  try:
         *    if arg0 < 0: throw
         *    if 0 < arg0: return
         *  finally:
         *    def f() { return sourcePosition }
         *    return f()
         *  @formatter:on
         */

        Source source = Source.newBuilder("test", "try finally { def f(){body}; return f }", "sourceRootNodeDeclaredInFinallyTry").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 39);

            b.beginFinallyTry(b.createLocal(), () -> {
                // finally
                b.beginSourceSection(14, 23);

                b.beginSourceSection(14, 13);
                b.beginRoot(LANGUAGE);
                b.beginSourceSection(22, 4);
                b.emitGetSourcePositions();
                b.endSourceSection();
                BasicInterpreter f = b.endRoot();
                b.endSourceSection();

                b.beginSourceSection(29, 8);
                b.beginReturn();
                b.beginInvoke();
                b.emitLoadConstant(f);
                b.endInvoke();
                b.endReturn();
                b.endSourceSection();

                b.endSourceSection();
            });
            // try
            b.beginSourceSection(0, 3);
            b.beginBlock();
            // if arg0 < 0, throw
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.beginThrowOperation();
            b.emitLoadConstant(0L);
            b.endThrowOperation();

            b.endIfThen();

            // if 0 < arg0, return
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadConstant(0L);
            b.emitLoadArgument(0);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endIfThen();
            b.endBlock();
            b.endSourceSection();

            b.endFinallyTry();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        long[] inputs = new long[]{0, -1, 1};
        for (int i = 0; i < inputs.length; i++) {
            SourceSection[] result = (SourceSection[]) node.getCallTarget().call(inputs[i]);
            assertSourceSections(result, source, 22, 4, 14, 13, 14, 23, 0, 39);
        }
    }

    @Test
    public void testSourceReparse() {
        // Test input taken from testSource above.
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        assertTrue(nodes.ensureSources());

        BasicInterpreter node = nodes.getNode(0);
        assertSourceSection(node.getSourceSection(), source, 0, 8);
        List<Instruction> instructions = node.getBytecodeNode().getInstructionsAsList();
        assertInstructionSourceSection(instructions.get(0), source, 7, 1);
        assertInstructionSourceSection(instructions.get(1), source, 0, 8);
    }

    @Test
    public void testReparseAfterTransitionToCached() {
        /**
         * This is a regression test for a bug caused by cached nodes being reused (because of a
         * source reparse) but not getting adopted by the new BytecodeNode.
         */
        Source source = Source.newBuilder("test", "return arg0 ? 42 : position", "file").build();

        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            b.beginSource(source);
            b.beginRoot(LANGUAGE);

            b.beginSourceSection(0, 27);
            b.beginReturn();
            b.beginConditional();

            b.beginSourceSection(7, 4);
            b.emitLoadArgument(0);
            b.endSourceSection();

            b.beginSourceSection(14, 2);
            b.emitLoadConstant(42L);
            b.endSourceSection();

            b.beginSourceSection(19, 8);
            b.emitGetSourcePositions();
            b.endSourceSection();

            b.endConditional();
            b.endReturn();
            b.endSourceSection();

            b.endRoot();
            b.endSource();
        });

        BasicInterpreter node = nodes.getNode(0);

        // call it once to transition to cached
        assertEquals(42L, node.getCallTarget().call(true));

        nodes.ensureSources();
        SourceSection[] result = (SourceSection[]) node.getCallTarget().call(false);
        assertSourceSections(result, source, 19, 8, 0, 27);
    }
}
