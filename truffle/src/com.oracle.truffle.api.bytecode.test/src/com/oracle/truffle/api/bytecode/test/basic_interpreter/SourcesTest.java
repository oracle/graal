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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.ExpectedSourceTree.expectedSourceTree;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class SourcesTest extends AbstractBasicInterpreterTest {

    private final SourceTestRun sourceRun;

    public SourcesTest(SourceTestRun run) {
        super(run.run);
        this.sourceRun = run;
    }

    @Parameters(name = "{0}")
    public static List<SourceTestRun> getRuns() {
        List<SourceTestRun> runs = new ArrayList<>();
        for (TestRun run : getParameters()) {
            runs.add(new SourceTestRun(Mode.PREFIX, run));
            runs.add(new SourceTestRun(Mode.SUFFIX, run));
        }
        return runs;
    }

    private void beginSourceSection(BasicInterpreterBuilder b, int start, int length) {
        if (sourceRun.mode() == Mode.PREFIX) {
            b.beginSourceSection(start, length);
        } else {
            b.beginSourceSection();
        }
    }

    private void endSourceSection(BasicInterpreterBuilder b, int start, int length) {
        if (sourceRun.mode() == Mode.PREFIX) {
            b.endSourceSection();
        } else {
            b.endSourceSection(start, length);
        }
    }

    @Test
    public void testSource() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 8);

            b.beginReturn();

            beginSourceSection(b, 7, 1);
            b.emitLoadConstant(1L);
            endSourceSection(b, 7, 1);

            b.endReturn();

            endSourceSection(b, 0, 8);
            b.endSource();
            b.endRoot();
        });

        assertSourceSection(node.getSourceSection(), source, 0, 8);

        BytecodeNode bytecode = node.getBytecodeNode();
        List<Instruction> instructions = bytecode.getInstructionsAsList();
        assertInstructionSourceSection(instructions.get(0), source, 7, 1);
        assertInstructionSourceSection(instructions.get(1), source, 0, 8);

        assertSourceInformationTree(bytecode, est("return 1", est("1")));

    }

    @Test
    public void testManySourceSections() {
        final int numSourceSections = 1000;
        StringBuilder sb = new StringBuilder(numSourceSections);
        for (int i = 0; i < numSourceSections; i++) {
            sb.append("x");
        }
        String sourceString = sb.toString();
        Source source = Source.newBuilder("test", sourceString, "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginSource(source);
            for (int i = 0; i < numSourceSections; i++) {
                beginSourceSection(b, 0, numSourceSections - i);
            }
            b.beginRoot();
            b.beginReturn();
            b.emitGetSourcePositions();
            b.endReturn();
            b.endRoot();
            for (int i = 0; i < numSourceSections; i++) {
                endSourceSection(b, 0, i + 1);
            }
            b.endSource();
        });

        SourceSection[] result = (SourceSection[]) node.getCallTarget().call();
        assertEquals(1, result.length);
        // sections are emitted in order of closing
        assertSourceSection(result[0], source, 0, 1);

        ExpectedSourceTree expectedSourceTree = est(sourceString.substring(0, 1));
        assertSourceInformationTree(node.getBytecodeNode(), expectedSourceTree);
    }

    @Test
    public void testSourceSplitByUnwind() {
        // When an operation emits unwind instructions, we have to close and reopen the bc range
        // that a source section applies to.
        Source source = Source.newBuilder("test", "try finally", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("sourceSplitByUnwind", b -> {
            b.beginSource(source);
            beginSourceSection(b, 0, 11); // root
            b.beginRoot();

            b.beginTryFinally(() -> {
                beginSourceSection(b, 4, 7);
                b.emitVoidOperation();
                endSourceSection(b, 4, 7);
            });

            beginSourceSection(b, 0, 3); // try
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginReturn(); // early return causes finally handler to be emitted.
            b.emitLoadConstant(42L);
            b.endReturn();
            b.endIfThen();
            endSourceSection(b, 0, 3);
            b.endTryFinally();

            b.beginReturn();
            b.emitLoadConstant(123L);
            b.endReturn();

            b.endRoot();
            endSourceSection(b, 0, 11); // root
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();

        assertSourceInformationTree(bytecode, est("try finally",
                        est("try"), // before early return
                        est("finally"), // inlined finally
                        est("try"), // after early return
                        est("finally"), // fallthrough finally
                        est("finally") // exceptional finally
        ));
    }

    @Test
    public void testRootNodeSourceSection() {
        Source source = Source.newBuilder("test", "0123456789", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginSource(source);
            beginSourceSection(b, 0, 10);
            beginSourceSection(b, 1, 9);
            beginSourceSection(b, 2, 8);

            b.beginRoot();
            b.emitLoadArgument(0);
            beginSourceSection(b, 3, 7);
            b.beginReturn();
            beginSourceSection(b, 4, 6);
            b.emitLoadConstant(1L);
            endSourceSection(b, 4, 6);
            b.endReturn();
            endSourceSection(b, 3, 7);
            b.endRoot();

            endSourceSection(b, 2, 8);
            endSourceSection(b, 1, 9);
            endSourceSection(b, 0, 10);
            b.endSource();
        });
        // The most specific source section should be chosen.
        assertSourceSection(node.getSourceSection(), source, 2, 8);

        assertSourceInformationTree(node.getBytecodeNode(),
                        est("23456789", est("3456789", est("456789"))));
    }

    @Test
    public void testWithoutSource() {
        BasicInterpreter node = parseNode("source", b -> {
            b.beginRoot();
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
        assertNull(node.getBytecodeNode().getSourceInformationTree());
    }

    @Test
    public void testWithoutSourceSection() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BasicInterpreter node = parseNode("source", b -> {
            b.beginSource(source);
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(1L);
            b.endReturn();
            b.endRoot();
            b.endSource();
        });
        assertNull(node.getSourceSection());
        assertNull(node.getBytecodeNode().getSourceInformationTree());
    }

    @Test
    public void testSourceUnavailable() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginSource(source);
            beginSourceSection(b, -1, -1);
            b.beginRoot();

            b.beginReturn();
            beginSourceSection(b, 7, 1);
            b.emitLoadConstant(1L);
            endSourceSection(b, 7, 1);
            b.endReturn();

            b.endRoot();
            endSourceSection(b, -1, -1);
            b.endSource();
        });

        assertTrue(!node.getSourceSection().isAvailable());

        BytecodeNode bytecode = node.getBytecodeNode();
        List<Instruction> instructions = bytecode.getInstructionsAsList();
        assertInstructionSourceSection(instructions.get(0), source, 7, 1);
        assertTrue(!instructions.get(1).getSourceSection().isAvailable());

        assertSourceInformationTree(bytecode, ExpectedSourceTree.expectedSourceTreeUnavailable(est("1")));
    }

    @Test
    public void testSourceNoSourceSet() {
        assertThrowsWithMessage("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.", IllegalStateException.class, () -> {
            parseNodeWithSource("sourceNoSourceSet", b -> {
                b.beginRoot();
                beginSourceSection(b, 0, 8);

                b.beginReturn();

                beginSourceSection(b, 7, 1);
                b.emitLoadConstant(1L);
                endSourceSection(b, 7, 1);

                b.endReturn();

                endSourceSection(b, 0, 8);
                b.endRoot();
            });
        });
    }

    @Test
    public void testSourceMultipleSources() {
        Source source1 = Source.newBuilder("test", "abc", "test1.test").build();
        Source source2 = Source.newBuilder("test", "01234567", "test2.test").build();
        BasicInterpreter root = parseNodeWithSource("sourceMultipleSources", b -> {
            b.beginSource(source1);
            beginSourceSection(b, 0, source1.getLength());

            b.beginRoot();

            b.emitVoidOperation(); // source1, 0, length
            b.beginBlock();
            b.emitVoidOperation(); // source1, 0, length

            beginSourceSection(b, 1, 2);

            b.beginBlock();
            b.emitVoidOperation(); // source1, 1, 2
            b.beginSource(source2);
            b.beginBlock();
            b.emitVoidOperation(); // source1, 1, 2

            beginSourceSection(b, 3, 4);
            b.beginBlock();
            b.emitVoidOperation(); // source2, 3, 4

            beginSourceSection(b, 5, 1);
            b.beginBlock();
            b.emitVoidOperation(); // source2, 5, 1
            b.endBlock();
            endSourceSection(b, 5, 1);

            b.emitVoidOperation(); // source2, 3, 4
            b.endBlock();
            endSourceSection(b, 3, 4);

            b.emitVoidOperation(); // source1, 1, 2
            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // source1, 1, 2

            b.endBlock();
            endSourceSection(b, 1, 2);

            b.emitVoidOperation(); // source1, 0, length

            b.endBlock();

            b.emitVoidOperation(); // source1, 0, length

            b.endRoot();

            endSourceSection(b, 0, source1.getLength());
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

        assertSourceInformationTree(root.getBytecodeNode(),
                        est("abc", est("bc", est("3456", est("5")))));
    }

    @Test
    public void testGetSourcePosition() {
        Source source = Source.newBuilder("test", "return 1", "testGetSourcePosition").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitGetSourcePosition();
            b.endSourceSection();

            b.endReturn();

            endSourceSection(b, 0, 8);
            b.endSource();
            b.endRoot();
        });

        SourceSection result = (SourceSection) node.getCallTarget().call();

        assertSourceSection(result, source, 7, 1);
    }

    @Test
    public void testGetSourcePositions() {
        Source source = Source.newBuilder("test", "return 1", "testGetSourcePositions").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 8);

            b.beginReturn();

            beginSourceSection(b, 7, 1);
            b.emitGetSourcePositions();
            endSourceSection(b, 7, 1);

            b.endReturn();

            endSourceSection(b, 0, 8);
            b.endSource();
            b.endRoot();
        });

        SourceSection[] result = (SourceSection[]) node.getCallTarget().call();

        assertSourceSections(result, source, 7, 1, 0, 8);
    }

    @Test
    public void testGetSourcePositionsEnclosingRootSections() {
        Source source = Source.newBuilder("test", "other; def test() { return 1 }", "testGetSourcePositions").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginSource(source);
            beginSourceSection(b, 0, 30);
            beginSourceSection(b, 7, 23);

            b.beginRoot();
            beginSourceSection(b, 20, 8);

            b.beginReturn();

            beginSourceSection(b, 27, 1);
            b.emitGetSourcePositions();
            endSourceSection(b, 27, 1);

            b.endReturn();

            endSourceSection(b, 20, 8);
            b.endRoot();

            endSourceSection(b, 7, 23);
            endSourceSection(b, 0, 30);
            b.endSource();
        });

        SourceSection[] result = (SourceSection[]) node.getCallTarget().call();

        // Only the directly enclosing source section should be included.
        assertSourceSections(result, source, 27, 1, 20, 8, 7, 23);
    }

    @Test
    public void testGetSourcePositionFrameInstance() {
        Source fooSource = Source.newBuilder("test", "return arg0()", "testGetSourcePositionFrameInstance#foo").build();
        BasicInterpreter foo = parseNodeWithSource("foo", b -> {
            b.beginRoot();
            b.beginSource(fooSource);
            beginSourceSection(b, 0, 13);

            b.beginReturn();
            beginSourceSection(b, 7, 6);
            b.beginInvoke();
            beginSourceSection(b, 7, 4);
            b.emitLoadArgument(0);
            endSourceSection(b, 7, 4);
            b.endInvoke();
            endSourceSection(b, 7, 6);
            b.endReturn();

            endSourceSection(b, 0, 13);
            b.endSource();
            b.endRoot();
        });

        Source barSource = Source.newBuilder("test", "return <position>", "testGetSourcePositionFrameInstance#bar").build();
        BasicInterpreter bar = parseNodeWithSource("bar", b -> {
            b.beginRoot();
            b.beginSource(barSource);
            beginSourceSection(b, 0, 17);

            b.beginReturn();

            beginSourceSection(b, 7, 10);
            b.emitCollectSourceLocations();
            endSourceSection(b, 7, 10);

            b.endReturn();

            endSourceSection(b, 0, 17);
            b.endSource();
            b.endRoot();
        });

        @SuppressWarnings("unchecked")
        List<SourceSection> result = (List<SourceSection>) foo.getCallTarget().call(new Object[]{bar});
        assertEquals(2, result.size());
        assertSourceSection(result.get(0), barSource, 7, 10);
        assertSourceSection(result.get(1), fooSource, 7, 6);
    }

    @Test
    public void testGetSourcePositionsFrameInstance() {
        Source fooSource = Source.newBuilder("test", "return arg0()", "testGetSourcePositionFrameInstance#foo").build();
        BasicInterpreter foo = parseNodeWithSource("foo", b -> {
            b.beginRoot();
            b.beginSource(fooSource);
            beginSourceSection(b, 0, 13);

            b.beginReturn();
            beginSourceSection(b, 7, 6);
            b.beginInvoke();
            beginSourceSection(b, 7, 4);
            b.emitLoadArgument(0);
            endSourceSection(b, 7, 4);
            b.endInvoke();
            endSourceSection(b, 7, 6);
            b.endReturn();

            endSourceSection(b, 0, 13);
            b.endSource();
            b.endRoot();
        });

        Source barSource = Source.newBuilder("test", "return <position>", "testGetSourcePositionFrameInstance#bar").build();
        BasicInterpreter bar = parseNodeWithSource("bar", b -> {
            b.beginRoot();
            b.beginSource(barSource);
            beginSourceSection(b, 0, 17);

            b.beginReturn();

            beginSourceSection(b, 7, 10);
            b.emitCollectAllSourceLocations();
            endSourceSection(b, 7, 10);

            b.endReturn();

            endSourceSection(b, 0, 17);
            b.endSource();
            b.endRoot();
        });

        @SuppressWarnings("unchecked")
        List<SourceSection[]> result = (List<SourceSection[]>) foo.getCallTarget().call(new Object[]{bar});
        assertEquals(2, result.size());
        assertSourceSections(result.get(0), barSource, 7, 10, 0, 17);
        assertSourceSections(result.get(1), fooSource, 7, 6, 0, 13);
    }

    @Test
    public void testSourceTryFinally() {
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

        Source source = Source.newBuilder("test", "try finally", "sourceTryFinally").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 11);

            b.beginTryFinally(() -> {
                // finally
                beginSourceSection(b, 4, 7);
                b.beginReturn();
                b.emitGetSourcePositions();
                b.endReturn();
                endSourceSection(b, 4, 7);
            });
            // try
            beginSourceSection(b, 0, 4);
            b.beginBlock();
            // if arg0 < 0, throw
            b.beginIfThen();

            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();

            b.beginThrowOperation();
            b.emitLoadConstant(0L);
            b.endThrowOperation();

            b.endIfThen();

            // if 0 < arg0, return
            b.beginIfThen();

            b.beginLess();
            b.emitLoadConstant(0L);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endIfThen();
            b.endBlock();
            endSourceSection(b, 0, 4);

            b.endTryFinally();

            endSourceSection(b, 0, 11);
            b.endSource();
            b.endRoot();
        });
        long[] inputs = new long[]{0, -1, 1};
        for (int i = 0; i < inputs.length; i++) {
            SourceSection[] result = (SourceSection[]) node.getCallTarget().call(inputs[i]);
            assertSourceSections(result, source, 4, 7, 0, 11);
        }
    }

    @Test
    public void testSourceOfRootWithTryFinally() {
        // The root node is nested in Source/SourceSection operations, so there is a source section
        // for the root.

        /** @formatter:off
         *  try:
         *    if arg0 < 0: return
         *    nop
         *  finally:
         *    nop
         *  @formatter:on
         */

        Source source = Source.newBuilder("test", "try finally", "sourceOfRootWithTryFinally").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginSource(source);
            beginSourceSection(b, 0, 11);
            b.beginRoot();

            b.beginTryFinally(() -> {
                // finally
                beginSourceSection(b, 4, 7);
                b.emitVoidOperation();
                endSourceSection(b, 4, 7);
            });
            // try
            beginSourceSection(b, 0, 3);
            b.beginBlock();
            emitReturnIf(b, 0, 42L);
            b.emitVoidOperation();
            b.endBlock();
            endSourceSection(b, 0, 3);

            b.endTryFinally();

            b.endRoot();
            endSourceSection(b, 0, 11);
            b.endSource();
        });
        assertEquals(42L, node.getCallTarget().call(true));
        assertEquals(null, node.getCallTarget().call(false));

        assertSourceSection(node.getSourceSection(), source, 0, 11);

        // @formatter:off
        assertSourceInformationTree(node.getBytecodeNode(),
            est("try finally",
                // try body before early return
                est("try"),
                // inlined finally
                est("finally"),
                // return and remainder of try
                est("try"),
                // fallthrough finally
                est("finally"),
                // exceptional finally
                est("finally")
            )
        );
        // @formatter:on
    }

    @Test
    public void testSourceOfRootWithTryFinallyNotNestedInSource() {
        // Unlike the previous test, a root node may not have a valid source section when it is not
        // enclosed in Source/SourceSection operations because the full bytecode range is not always
        // enclosed by a single source table entry (due to early exits).

        /** @formatter:off
         *  try:
         *    if arg0 < 0: return
         *    nop
         *  finally:
         *    nop
         *  @formatter:on
         */

        Source source = Source.newBuilder("test", "try finally", "sourceOfRootWithTryFinallyNotNestedInSource").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 11);

            b.beginTryFinally(() -> {
                // finally
                beginSourceSection(b, 4, 7);
                b.emitVoidOperation();
                endSourceSection(b, 4, 7);
            });
            // try
            beginSourceSection(b, 0, 3);
            b.beginBlock();
            emitReturnIf(b, 0, 42L);
            b.emitVoidOperation();
            b.endBlock();
            endSourceSection(b, 0, 3);

            b.endTryFinally();

            endSourceSection(b, 0, 11);
            b.endSource();
            b.endRoot();
        });
        assertEquals(42L, node.getCallTarget().call(true));
        assertEquals(null, node.getCallTarget().call(false));

        assertNull(node.getSourceSection());

        // @formatter:off
        assertSourceInformationTree(node.getBytecodeNode(),
            est(null,
                est("try finally",
                    // try body before early return
                    est("try"),
                    // inlined finally
                    est("finally")
                ),
                est("try finally",
                    // return and remainder of try
                    est("try"),
                    // fallthrough finally
                    est("finally"),
                    // exceptional finally
                    est("finally")
                )
                // fallthrough return
            )
        );
        // @formatter:on
    }

    @Test
    public void testSourceRootNodeDeclaredInTryFinally() {
        // Ensures root nodes declared in a finally handler inherit sources declared outside the
        // root node.

        /** @formatter:off
         *  try:
         *    if arg0 < 0: throw
         *    if 0 < arg0: return
         *  finally:
         *    def f() { return sourcePosition }
         *    return f()
         *  @formatter:on
         */

        Source source = Source.newBuilder("test", "try finally { def f(){body}; return f }", "sourceRootNodeDeclaredInTryFinally").build();
        BasicInterpreter node = parseNodeWithSource("source", b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 39);

            b.beginTryFinally(() -> {
                // finally
                beginSourceSection(b, 14, 23);

                beginSourceSection(b, 14, 13);
                b.beginRoot();
                beginSourceSection(b, 22, 4);
                b.emitGetSourcePositions();
                endSourceSection(b, 22, 4);
                BasicInterpreter f = b.endRoot();
                endSourceSection(b, 14, 13);

                beginSourceSection(b, 29, 8);
                b.beginReturn();
                b.beginInvoke();
                b.emitLoadConstant(f);
                b.endInvoke();
                b.endReturn();
                endSourceSection(b, 29, 8);

                endSourceSection(b, 14, 23);
            });
            // try
            beginSourceSection(b, 0, 3);
            b.beginBlock();
            // if arg0 < 0, throw
            b.beginIfThen();

            b.beginLess();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLess();

            b.beginThrowOperation();
            b.emitLoadConstant(0L);
            b.endThrowOperation();

            b.endIfThen();

            // if 0 < arg0, return
            b.beginIfThen();

            b.beginLess();
            b.emitLoadConstant(0L);
            b.emitLoadArgument(0);
            b.endLess();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endIfThen();
            b.endBlock();
            endSourceSection(b, 0, 3);

            b.endTryFinally();

            endSourceSection(b, 0, 39);
            b.endSource();
            b.endRoot();
        });

        long[] inputs = new long[]{0, -1, 1};
        for (int i = 0; i < inputs.length; i++) {
            SourceSection[] result = (SourceSection[]) node.getCallTarget().call(inputs[i]);
            assertSourceSections(result, source, 22, 4, 14, 13);
        }
    }

    @Test
    public void testSourceReparse() {
        // Test input taken from testSource above.
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginSource(source);
            beginSourceSection(b, 0, 8);

            b.beginReturn();

            beginSourceSection(b, 7, 1);
            b.emitLoadConstant(1L);
            endSourceSection(b, 7, 1);

            b.endReturn();

            endSourceSection(b, 0, 8);
            b.endSource();
            b.endRoot();
        });

        assertTrue(nodes.ensureSourceInformation());

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
            b.beginRoot();

            beginSourceSection(b, 0, 27);
            b.beginReturn();
            b.beginConditional();

            beginSourceSection(b, 7, 4);
            b.emitLoadArgument(0);
            endSourceSection(b, 7, 4);

            beginSourceSection(b, 14, 2);
            b.emitLoadConstant(42L);
            endSourceSection(b, 14, 2);

            beginSourceSection(b, 19, 8);
            b.emitGetSourcePositions();
            endSourceSection(b, 19, 8);

            b.endConditional();
            b.endReturn();
            endSourceSection(b, 0, 27);

            b.endRoot();
            b.endSource();
        });

        BasicInterpreter node = nodes.getNode(0);

        // call it once to transition to cached
        assertEquals(42L, node.getCallTarget().call(true));

        BytecodeLocation aLocation = node.getBytecodeNode().getBytecodeLocation(node.getBytecodeNode().getInstructionsAsList().get(3).getBytecodeIndex());

        assertNull(aLocation.getSourceInformation());

        nodes.ensureSourceInformation();
        SourceSection[] result = (SourceSection[]) node.getCallTarget().call(false);
        assertSourceSections(result, source, 19, 8, 0, 27);

        aLocation = aLocation.update();
        assertNotNull(aLocation.getSourceInformation());

    }

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

    private static ExpectedSourceTree est(String contents, ExpectedSourceTree... children) {
        return expectedSourceTree(contents, children);
    }

    private static void assertSourceInformationTree(BytecodeNode bytecode, ExpectedSourceTree expected) {
        expected.assertTreeEquals(bytecode.getSourceInformationTree());
    }

    enum Mode {
        PREFIX,
        SUFFIX
    }

    record SourceTestRun(Mode mode, TestRun run) {

        @Override
        public String toString() {
            return mode.toString() + ":" + run.toString();
        }
    }

}
