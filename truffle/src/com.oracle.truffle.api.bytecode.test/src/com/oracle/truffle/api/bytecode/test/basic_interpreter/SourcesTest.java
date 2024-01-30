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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.introspection.Instruction;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class SourcesTest extends AbstractBasicInterpreterTest {

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

        assertEquals(node.getSourceSection().getSource(), source);
        assertEquals(node.getSourceSection().getCharIndex(), 0);
        assertEquals(node.getSourceSection().getCharLength(), 8);

        BytecodeNode bytecode = node.getBytecodeNode();
        List<Instruction> instructions = bytecode.getIntrospectionData().getInstructions();
        BytecodeLocation location1 = instructions.get(0).getLocation();
        BytecodeLocation location2 = instructions.get(1).getLocation();

        assertEquals(location1.getSourceLocation().getSource(), source);
        assertEquals(location1.getSourceLocation().getCharIndex(), 7);
        assertEquals(location1.getSourceLocation().getCharLength(), 1);

        // return
        assertEquals(location2.getSourceLocation().getSource(), source);
        assertEquals(location2.getSourceLocation().getCharIndex(), 0);
        assertEquals(location2.getSourceLocation().getCharLength(), 8);
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
            b.beginRoot(LANGUAGE);

            b.emitVoidOperation(); // no source

            b.beginSource(source1);
            b.beginBlock();

            b.emitVoidOperation(); // no source

            b.beginSourceSection(1, 2);
            b.beginBlock();

            b.emitVoidOperation(); // source1, 1, 2

            b.beginSource(source2);
            b.beginBlock();

            b.emitVoidOperation(); // no source

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

            b.emitVoidOperation(); // no source

            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // source1, 1, 2

            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // no source

            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // no source

            b.endRoot();
        });

        assertEquals(root.getSourceSection().getSource(), source1);
        assertEquals(root.getSourceSection().getCharIndex(), 1);
        assertEquals(root.getSourceSection().getCharLength(), 2);

        Source[] sources = {null, source1, source2};

        int[][] expected = {
                        null,
                        null,
                        {1, 1, 2},
                        null,
                        {2, 3, 4},
                        {2, 5, 1},
                        {2, 3, 4},
                        null,
                        {1, 1, 2},
                        null,
                        null,
        };

        List<Instruction> instructions = root.getBytecodeNode().getIntrospectionData().getInstructions();
        for (int i = 0; i < expected.length; i++) {
            BytecodeLocation location = instructions.get(i).getLocation();
            SourceSection sourceSection = location.getSourceLocation();
            if (expected[i] == null) {
                assertEquals("Mismatch at bci " + location, null, sourceSection);
            } else {
                assertNotNull("Mismatch at bci " + location, sourceSection);
                assertEquals("Mismatch at bci " + location, sources[expected[i][0]],
                                sourceSection.getSource());
                assertEquals("Mismatch at bci " + location, expected[i][1],
                                sourceSection.getCharIndex());
                assertEquals("Mismatch at bci " + location, expected[i][2],
                                sourceSection.getCharLength());
            }
        }
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
            b.emitGetSourcePosition();
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        Object result = node.getCallTarget().call();
        assertTrue(result instanceof SourceSection);
        SourceSection ss = (SourceSection) result;
        assertEquals(source, ss.getSource());
        assertEquals(7, ss.getCharIndex());
        assertEquals(1, ss.getCharLength());
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

        Source source = Source.newBuilder("test", "try finally", "testGetSourcePosition").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 11);

            b.beginFinallyTry(b.createLocal());

            // finally
            b.beginSourceSection(4, 7);
            b.beginReturn();
            b.emitGetSourcePosition();
            b.endReturn();
            b.endSourceSection();

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
            Object result = node.getCallTarget().call(inputs[i]);
            assertTrue(result instanceof SourceSection);
            SourceSection ss = (SourceSection) result;
            assertEquals(source, ss.getSource());
            assertEquals(4, ss.getCharIndex());
            assertEquals(7, ss.getCharLength());
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

        assertEquals(node.getSourceSection().getSource(), source);
        assertEquals(node.getSourceSection().getCharIndex(), 0);
        assertEquals(node.getSourceSection().getCharLength(), 8);

        List<Instruction> instructions = node.getBytecodeNode().getIntrospectionData().getInstructions();
        SourceSection sourceSection0 = instructions.get(0).getLocation().getSourceLocation();
        SourceSection sourceSection1 = instructions.get(1).getLocation().getSourceLocation();

        // load constant
        assertEquals(sourceSection0.getSource(), source);
        assertEquals(sourceSection0.getCharIndex(), 7);
        assertEquals(sourceSection0.getCharLength(), 1);

        // return
        assertEquals(sourceSection1.getSource(), source);
        assertEquals(sourceSection1.getCharIndex(), 0);
        assertEquals(sourceSection1.getCharLength(), 8);
    }
}
