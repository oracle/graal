/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.ENTER;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.INPUT_VALUE;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.RETURN_VALUE;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationEventTest.EventKind.UNWIND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

public class InputFilterTest extends InstrumentationEventTest {

    @Test
    public void testNoInputFilter() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        instrumenter.attachExecutionEventFactory(expressionFilter, null, factory);
        execute("ROOT(EXPRESSION(EXPRESSION,EXPRESSION))");
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[0], e.inputs);
        });
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[0], e.inputs);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(()+())", e.result);
            assertArrayEquals(new Object[0], e.inputs);
        });
    }

    @Test
    public void testCleanupFrameDescriptor() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();

        String code = "EXPRESSION(INTERNAL(EXPRESSION))";

        assertCleanedUp(code);

        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);

        execute(code); // lazy initialize

        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(())", e.result);
            assertArrayEquals(new Object[]{"()"}, e.inputs);
        });

        binding.dispose();

        assertCleanedUp(code);
    }

    @Test
    public void testSameInputFilter() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);
        String code = "ROOT(EXPRESSION(" +
                        "INTERNAL(INTERNAL(EXPRESSION), INTERNAL)," +
                        "STATEMENT(CONSTANT(42))," +
                        "EXPRESSION)" +
                        ")";
        execute(code);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(1, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(()+42+())", e.result);
            assertArrayEquals(new Object[]{"()", "()"}, e.inputs);
        });
        binding.dispose();

        assertCleanedUp(code);
    }

    @Test
    public void testHierarchicalInputs() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);
        String exp1 = "EXPRESSION(EXPRESSION(CONSTANT(0)), EXPRESSION(CONSTANT(1)))";
        String exp2 = "EXPRESSION(EXPRESSION(CONSTANT(2)))";
        String exp3 = "EXPRESSION(EXPRESSION(CONSTANT(3)), EXPRESSION(EXPRESSION(CONSTANT(4))))";
        String code = "EXPRESSION(" + exp1 + "," + exp2 + "," + exp3 + ")";
        execute(code);
        FrameDescriptor[] descriptor = new FrameDescriptor[1];
        assertOn(ENTER, (e) -> {
            descriptor[0] = e.frame.getFrameDescriptor();
            assertCharacters(e, code);
        });
        // exp1
        assertOn(ENTER, (e) -> {
            assertCharacters(e, exp1);
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(0))");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(0))");
            assertArrayEquals(new Object[]{}, e.inputs);
            assertEquals("(0)", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, exp1);
            assertEquals("(0)", e.inputValue);
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(1))");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(1))");
            assertArrayEquals(new Object[]{}, e.inputs);
            assertEquals("(1)", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, exp1);
            assertEquals("(1)", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, exp1);
            assertArrayEquals(new Object[]{"(0)", "(1)"}, e.inputs);
            assertEquals("((0)+(1))", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, code);
            assertEquals("((0)+(1))", e.inputValue);
        });

        // exp2
        assertOn(ENTER, (e) -> {
            assertCharacters(e, exp2);
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(2))");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(2))");
            assertArrayEquals(new Object[]{}, e.inputs);
            assertEquals("(2)", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, exp2);
            assertEquals("(2)", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, exp2);
            assertArrayEquals(new Object[]{"(2)"}, e.inputs);
            assertEquals("((2))", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, code);
            assertEquals("((2))", e.inputValue);
        });

        // exp3
        assertOn(ENTER, (e) -> {
            assertCharacters(e, exp3);
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(3))");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(3))");
            assertEquals("(3)", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, exp3);
            assertEquals("(3)", e.inputValue);
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(EXPRESSION(CONSTANT(4)))");
        });
        assertOn(ENTER, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(4))");
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(CONSTANT(4))");
            assertArrayEquals(new Object[]{}, e.inputs);
            assertEquals("(4)", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(EXPRESSION(CONSTANT(4)))");
            assertEquals("(4)", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, "EXPRESSION(EXPRESSION(CONSTANT(4)))");
            assertArrayEquals(new Object[]{"(4)"}, e.inputs);
            assertEquals("((4))", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, exp3);
            assertEquals("((4))", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, exp3);
            assertArrayEquals(new Object[]{"(3)", "((4))"}, e.inputs);
            assertEquals("((3)+((4)))", e.result);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, code);
            assertEquals("((3)+((4)))", e.inputValue);
        });

        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, code);
            assertArrayEquals(new Object[]{"((0)+(1))", "((2))", "((3)+((4)))"}, e.inputs);
            assertEquals("(((0)+(1))+((2))+((3)+((4))))", e.result);
        });

        // should use maximum four frame slots to save expression values
        assertEquals(4, descriptor[0].getIdentifiers().size());

        binding.dispose();
        assertCleanedUp(code);
    }

    @Test
    public void testFilterChildren1() {
        SourceSectionFilter line1 = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).lineStartsIn(IndexRange.between(1, 2)).build();
        SourceSectionFilter line2 = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).lineIs(2).build();
        String code = "EXPRESSION(\nEXPRESSION(CONSTANT(0)))";

        // attach after executing. uses single binding visitor
        execute(code);
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(line1, line2, factory);
        execute(code);

        assertOn(ENTER, (e) -> {
            assertCharacters(e, code);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, code);
            assertEquals("(0)", e.inputValue);
        });

        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, code);
            assertArrayEquals(new Object[]{"(0)"}, e.inputs);
            assertEquals("((0))", e.result);
        });

        binding.dispose();
        assertCleanedUp(code);
        assertAllEventsConsumed();

        // attach before executing. uses bindings visitor
        binding = instrumenter.attachExecutionEventFactory(line1, line2, factory);
        execute(code + " "); // add space to avoid caching

        assertOn(ENTER, (e) -> {
            assertCharacters(e, code);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertCharacters(e, code);
            assertEquals("(0)", e.inputValue);
        });

        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, code);
            assertArrayEquals(new Object[]{"(0)"}, e.inputs);
            assertEquals("((0))", e.result);
        });

        binding.dispose();
        assertCleanedUp(code);
        assertAllEventsConsumed();

    }

    @Test
    public void testFilterChildren2() {
        SourceSectionFilter line1 = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).lineStartsIn(IndexRange.between(1, 2)).build();
        String code = "EXPRESSION(\nEXPRESSION(CONSTANT(0)))";

        // test that input filter does not match to any inputs
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(line1, line1, factory);
        execute(code);

        assertOn(ENTER, (e) -> {
            assertCharacters(e, code);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertCharacters(e, code);
            assertArrayEquals(new Object[]{}, e.inputs);
            assertEquals("((0))", e.result);
        });

        binding.dispose();
        assertCleanedUp(code);
        assertAllEventsConsumed();

    }

    @Test
    public void testFilterChildren3() {
        SourceSectionFilter bogusLine = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).lineIs(42).build();
        SourceSectionFilter line2 = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).lineIs(2).build();
        String code = "EXPRESSION(\nEXPRESSION(CONSTANT(0)))";

        // test that input normal filter does not match to any inputs
        EventBinding<?> binding = instrumenter.attachExecutionEventFactory(bogusLine, line2, factory);
        execute(code);

        // no events happening

        binding.dispose();
        assertCleanedUp(code);
        assertAllEventsConsumed();
    }

    @Test
    public void testInnerFrames() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();

        String code = "EXPRESSION(INNER_FRAME(EXPRESSION(INNER_FRAME(EXPRESSION))))";
        EventBinding<?> binding1 = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);
        execute(code);

        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(())", e.result);
            // the expression value is not recoverable for inner frames.
            assertArrayEquals(new Object[]{null}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("(())", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("((()))", e.result);
            assertArrayEquals(new Object[]{null}, e.inputs);
        });
        binding1.dispose();
        assertCleanedUp(code);

    }

    @Test
    public void testMultipleFactories() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();

        String code = "EXPRESSION(INTERNAL(EXPRESSION))";

        EventBinding<?> binding1 = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);
        EventBinding<?> binding2 = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);
        EventBinding<?> binding3 = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, factory);

        execute(code); // lazy initialize

        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(ENTER);
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("()", e.result);
            assertArrayEquals(new Object[]{}, e.inputs);
        });
        assertOn(INPUT_VALUE, (e) -> {
            assertEquals(0, e.inputValueIndex);
            assertEquals("()", e.inputValue);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(())", e.result);
            assertArrayEquals(new Object[]{"()"}, e.inputs);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(())", e.result);
            assertArrayEquals(new Object[]{"()"}, e.inputs);
        });
        assertOn(RETURN_VALUE, (e) -> {
            assertEquals("(())", e.result);
            assertArrayEquals(new Object[]{"()"}, e.inputs);
        });

        binding1.dispose();
        binding2.dispose();
        binding3.dispose();
        assertCleanedUp(code);
    }

    private void assertCleanedUp(String code) {
        // first we capture all root nodes used by the code.
        Set<RootNode> rootNodes = new HashSet<>();
        EventBinding<?> binding = instrumenter.attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {

            public void onEnter(EventContext c, VirtualFrame frame) {
                addRoot(c);
            }

            @TruffleBoundary
            private void addRoot(EventContext c) {
                rootNodes.add(c.getInstrumentedNode().getRootNode());
            }

            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
            }
        });
        execute(code);
        binding.dispose();

        // we execute again to let the instrumentation wrappers be cleaned up
        execute(code);

        for (RootNode root : rootNodes) {
            // all frame slots got removed
            assertEquals(new HashSet<>(), root.getFrameDescriptor().getIdentifiers());

            // no wrappers left
            root.accept(new NodeVisitor() {
                public boolean visit(Node node) {
                    if (node instanceof WrapperNode) {
                        throw new AssertionError();
                    }
                    return true;
                }
            });
        }
    }

    @Test
    public void testUnwindInInputFilter() {
        SourceSectionFilter expressionFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.ExpressionTag.class).build();

        EventBinding<?> binding1 = instrumenter.attachExecutionEventFactory(expressionFilter, expressionFilter, new ExecutionEventNodeFactory() {
            public ExecutionEventNode create(EventContext c) {
                return new CollectEventsNode(c) {

                    @Override
                    protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                        super.onInputValue(frame, inputContext, inputIndex, inputValue);
                        throw c.createUnwind("unwindValue");
                    }

                    @Override
                    protected Object onUnwind(VirtualFrame frame, Object info) {
                        super.onUnwind(frame, info);
                        return info;
                    }

                };
            }
        });

        String code = "EXPRESSION(EXPRESSION, EXPRESSION)";
        execute(code);

        assertOn(ENTER, (e) -> assertEquals(code, e.context.getInstrumentedSourceSection().getCharacters()));
        assertOn(ENTER, (e) -> assertEquals("EXPRESSION", e.context.getInstrumentedSourceSection().getCharacters()));
        assertOn(RETURN_VALUE);
        assertOn(INPUT_VALUE);
        assertOn(UNWIND, (e) -> assertEquals("unwindValue", e.unwindValue));
        assertOn(ENTER, (e) -> assertEquals("EXPRESSION", e.context.getInstrumentedSourceSection().getCharacters()));
        assertOn(RETURN_VALUE);
        assertOn(INPUT_VALUE);
        assertOn(UNWIND);
        assertOn(RETURN_VALUE);

        binding1.dispose();
        assertCleanedUp(code);
    }

}
