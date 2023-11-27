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
package com.oracle.truffle.api.bytecode.test.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class BytecodeDSLExampleSourcesTest extends AbstractBytecodeDSLExampleTest {
    public void assumeTestIsApplicable() {
        // TODO: we currently do not have a way to serialize Sources.
        assumeFalse(testSerialize);
    }

    @Test
    public void testSource() {
        assumeTestIsApplicable();
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BytecodeDSLExample node = parseNodeWithSource("source", b -> {
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

        // load constant
        assertEquals(node.getSourceSectionAtBci(0).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(0).getCharIndex(), 7);
        assertEquals(node.getSourceSectionAtBci(0).getCharLength(), 1);

        // return
        assertEquals(node.getSourceSectionAtBci(2).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(2).getCharIndex(), 0);
        assertEquals(node.getSourceSectionAtBci(2).getCharLength(), 8);
    }

    @Test
    public void testSourceNoSourceSet() {
        assumeTestIsApplicable();
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
        assumeTestIsApplicable();
        Source source1 = Source.newBuilder("test", "This is just a piece of test source.", "test1.test").build();
        Source source2 = Source.newBuilder("test", "This is another test source.", "test2.test").build();
        BytecodeDSLExample root = parseNodeWithSource("sourceMultipleSources", b -> {
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

        for (int i = 0; i < expected.length; i++) {
            // Each Void operation is encoded as two shorts: the Void opcode, and a node index.
            // The source section for both should match the expected value.
            for (int j = i * 2; j < i * 2 + 2; j++) {
                if (expected[i] == null) {
                    assertEquals("Mismatch at bci " + j, null, root.getSourceSectionAtBci(j));
                } else {
                    assertNotNull("Mismatch at bci " + j, root.getSourceSectionAtBci(j));
                    assertEquals("Mismatch at bci " + j, sources[expected[i][0]], root.getSourceSectionAtBci(j).getSource());
                    assertEquals("Mismatch at bci " + j, expected[i][1], root.getSourceSectionAtBci(j).getCharIndex());
                    assertEquals("Mismatch at bci " + j, expected[i][2], root.getSourceSectionAtBci(j).getCharLength());
                }
            }
        }
    }

    @Test
    public void testGetSourcePosition() {
        assumeTestIsApplicable();
        Source source = Source.newBuilder("test", "return 1", "testGetSourcePosition").build();
        BytecodeDSLExample node = parseNodeWithSource("source", b -> {
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
        assumeTestIsApplicable();
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
        BytecodeDSLExample node = parseNodeWithSource("source", b -> {
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
        assumeTestIsApplicable();
        // Test input taken from testSource above.
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BytecodeNodes<BytecodeDSLExample> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
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

        assertFalse(nodes.hasSources());
        nodes.updateConfiguration(BytecodeConfig.WITH_SOURCE);
        assertTrue(nodes.hasSources());

        BytecodeDSLExample node = nodes.getNodes().get(0);

        assertEquals(node.getSourceSection().getSource(), source);
        assertEquals(node.getSourceSection().getCharIndex(), 0);
        assertEquals(node.getSourceSection().getCharLength(), 8);

        // load constant
        assertEquals(node.getSourceSectionAtBci(0).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(0).getCharIndex(), 7);
        assertEquals(node.getSourceSectionAtBci(0).getCharLength(), 1);

        // return
        assertEquals(node.getSourceSectionAtBci(2).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(2).getCharIndex(), 0);
        assertEquals(node.getSourceSectionAtBci(2).getCharLength(), 8);
    }
}
