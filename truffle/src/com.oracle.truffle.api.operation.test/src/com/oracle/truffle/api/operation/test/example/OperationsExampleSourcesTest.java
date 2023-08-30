package com.oracle.truffle.api.operation.test.example;

import static com.oracle.truffle.api.operation.test.example.OperationsExampleCommon.parseNodeWithSource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class OperationsExampleSourcesTest extends AbstractOperationsExampleTest {

    @Test
    public void testSource() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        OperationsExample node = parseNodeWithSource(interpreterClass, "source", b -> {
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
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.");
        parseNodeWithSource(interpreterClass, "sourceNoSourceSet", b -> {
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
        OperationsExample root = parseNodeWithSource(interpreterClass, "sourceMultipleSources", b -> {
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
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j), null);
                } else {
                    assertNotNull("Mismatch at bci " + j, root.getSourceSectionAtBci(j));
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getSource(), sources[expected[i][0]]);
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getCharIndex(), expected[i][1]);
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getCharLength(), expected[i][2]);
                }
            }
        }
    }

    @Test
    public void testGetSourcePosition() {
        Source source = Source.newBuilder("test", "return 1", "testGetSourcePosition").build();
        OperationsExample node = parseNodeWithSource(interpreterClass, "source", b -> {
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
        OperationsExample node = parseNodeWithSource(interpreterClass, "source", b -> {
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
        OperationNodes<OperationsExample> nodes = OperationsExampleCommon.createNodes(interpreterClass, OperationConfig.DEFAULT, b -> {
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
        nodes.updateConfiguration(OperationConfig.WITH_SOURCE);
        assertTrue(nodes.hasSources());

        OperationsExample node = nodes.getNodes().get(0);

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
