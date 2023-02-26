/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.NearestSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

/**
 *
 */
public final class NearestSectionFilterTest extends SourceSectionListenerTest {

    @Test
    @SuppressWarnings("static-method")
    public void testNearestToConstraints() {
        assertFails(() -> NearestSectionFilter.newBuilder(-1, -1), IllegalArgumentException.class,
                        ex -> assertEquals("line -1 < 1", ex.getMessage()));
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("static-method")
    public void testTagsFail1() {
        NearestSectionFilter.newBuilder(1, 1).tagIs((Class<?>[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("static-method")
    public void testTagsFail2() {
        NearestSectionFilter.newBuilder(1, 1).tagIs((Class<?>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    @SuppressWarnings("static-method")
    public void testTagsFail3() {
        NearestSectionFilter.newBuilder(1, 1).tagIs(StatementTag.class, null);
    }

    @Test
    public void testNearestLoadedSourceSections() throws IOException {
        SourceSection[] sourceSections = sections("ROOT(\n" +
                        "STATEMENT(EXPRESSION),\n" +
                        "\n" +
                        "VARIABLE(a, 10)\n" +
                        ")", "STATEMENT(EXPRESSION)", "EXPRESSION", "VARIABLE(a, 10)");
        run(sourceSections[0].getSource());

        checkLoadEvents(NearestSectionFilter.newBuilder(1, 1).build(), SourceSectionFilter.ANY, sourceSections[0]);
        checkLoadEvents(NearestSectionFilter.newBuilder(2, 1).build(), SourceSectionFilter.ANY, sourceSections[1]);
        checkLoadEvents(NearestSectionFilter.newBuilder(3, 1).build(), SourceSectionFilter.ANY, sourceSections[1]);
        checkLoadEvents(NearestSectionFilter.newBuilder(4, 1).build(), SourceSectionFilter.ANY, sourceSections[3]);

        // Test tags
        checkLoadEvents(NearestSectionFilter.newBuilder(2, 1).tagIs(ExpressionTag.class).build(), SourceSectionFilter.ANY, sourceSections[2]);
        // Test tags and anchor
        checkLoadEvents(NearestSectionFilter.newBuilder(4, 1).tagIs(StatementTag.class).anchorStart(false).build(), SourceSectionFilter.ANY, sourceSections[1]);
    }

    @Test
    public void testNearestSectionsWithBaseFilter() throws IOException {
        SourceSection[] sourceSections = sections("ROOT(DEFINE(function1,\n" +
                        "STATEMENT(),\n" +
                        "EXPRESSION(VARIABLE(a, 10)),\n" +
                        "VARIABLE(aa, 11),\n" +
                        "ROOT(DEFINE(function2,\n" +
                        "  VARIABLE(ab, 12),\n" +
                        "  STATEMENT(EXPRESSION(VARIABLE(b, 20))),\n" +
                        "  VARIABLE(ba, 21)\n" +
                        ")),\n" +
                        "EXPRESSION(VARIABLE(c, 30))\n" +
                        "))", "STATEMENT()", "EXPRESSION(VARIABLE(a, 10))", "EXPRESSION(VARIABLE(b, 20))", "EXPRESSION(VARIABLE(c, 30))");
        run(sourceSections[0].getSource());

        checkLoadEvents(NearestSectionFilter.newBuilder(2, 1).build(), SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build(), sourceSections[2]);
        checkLoadEvents(NearestSectionFilter.newBuilder(2, 1).build(), SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), sourceSections[1]);
        checkLoadEvents(NearestSectionFilter.newBuilder(6, 1).build(), SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build(), sourceSections[3]);
        checkLoadEvents(NearestSectionFilter.newBuilder(8, 1).tagIs(ExpressionTag.class).build(),
                        SourceSectionFilter.newBuilder().rootNameIs(name -> "function1".equals(name)).build(), sourceSections[4]);
        checkLoadEvents(NearestSectionFilter.newBuilder(8, 1).tagIs(ExpressionTag.class).build(),
                        SourceSectionFilter.newBuilder().rootNameIs(name -> "function2".equals(name)).build(), sourceSections[3]);
    }

    @Test
    public void testTags() throws IOException {
        SourceSection[] sourceSections = sections("ROOT(\n" +
                        "STATEMENT,\n" +
                        "ROOT(DEFINE(function,\n" +
                        "  EXPRESSION(VARIABLE(a, 10)),\n" +
                        "  VARIABLE(aa, 11)\n" +
                        ")),\n" +
                        "EXPRESSION(VARIABLE(b, 20))\n" +
                        ")", "EXPRESSION(VARIABLE(a, 10))", "EXPRESSION(VARIABLE(b, 20))");
        run(sourceSections[0].getSource());

        // The difference when tag is set on the nearest filter, or the base section filter:
        checkLoadEvents(NearestSectionFilter.newBuilder(5, 1).tagIs(ExpressionTag.class).build(), SourceSectionFilter.ANY, sourceSections[1]);
        checkLoadEvents(NearestSectionFilter.newBuilder(5, 1).build(), SourceSectionFilter.newBuilder().tagIs(ExpressionTag.class).build(), sourceSections[2]);
    }

    @Test
    public void testSplitNodes() throws IOException {
        SourceSection[] sourceSections = sections("ROOT(DEFINE(function1,\n" +
                        "  STATEMENT(),\n" +
                        "  EXPRESSION(VARIABLE(a, 10)),\n" +
                        "  VARIABLE(aa, 11)\n" +
                        "),\n" +
                        "CALL(function1))", "STATEMENT()", "EXPRESSION(VARIABLE(a, 10))");
        run(sourceSections[0].getSource());
        final List<LoadSourceSectionEvent> visitedEvents = new ArrayList<>();
        final List<EventContext> executedEvents = new ArrayList<>();
        LoadSourceSectionListener listener = new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                visitedEvents.clear();
                visitedEvents.add(event);
            }
        };
        NearestSectionFilter nearestFilter = NearestSectionFilter.newBuilder(2, 1).tagIs(ExpressionTag.class).build();
        SourceSectionFilter baseFilter = SourceSectionFilter.ANY;
        EventBinding<LoadSourceSectionListener> loadBinding;
        loadBinding = instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(nearestFilter, baseFilter, listener, true);
        EventBinding<ExecutionEventNodeFactory> executionBinding;
        executionBinding = instrumentEnv.getInstrumenter().attachExecutionEventFactory(nearestFilter, baseFilter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext ctx) {
                return new ExecutionEventNode() {
                    private final EventContext eventContext = ctx;

                    @Override
                    protected void onEnter(VirtualFrame frame) {
                        entered();
                    }

                    @CompilerDirectives.TruffleBoundary
                    private void entered() {
                        executedEvents.add(eventContext);
                    }
                };
            }
        });
        assertEvents(visitedEvents, sourceSections[2]);
        assertEquals(executedEvents.toString(), 0, executedEvents.size());

        SourceSection[] splitSections = sections("ROOT(CALL_SPLIT(function1))");
        Value split = context.eval(splitSections[0].getSource());
        if (split.isBoolean() && !split.asBoolean()) {
            return; // No split
        }
        context.eval(sections("ROOT(CALL(function1))")[0].getSource());
        assertEvents(visitedEvents, sourceSections[2]);
        assertEquals(executedEvents.toString(), 2, executedEvents.size());
        assertEquals(executedEvents.get(0).getInstrumentedSourceSection(), executedEvents.get(1).getInstrumentedSourceSection());
        assertNotEquals(executedEvents.get(0).getInstrumentedNode(), executedEvents.get(1).getInstrumentedNode());

        loadBinding.dispose();
        executionBinding.dispose();
        executedEvents.clear();
        context.eval(sections("ROOT(CALL(function1))")[0].getSource());
        context.eval(splitSections[0].getSource());
        assertEquals(executedEvents.toString(), 0, executedEvents.size());
    }

    private void checkLoadEvents(NearestSectionFilter nearestFilter, SourceSectionFilter baseFilter, SourceSection... expectedSections) {
        final List<LoadSourceSectionEvent> visitedEvents = new ArrayList<>(expectedSections.length);
        LoadSourceSectionListener listener = new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                visitedEvents.clear();
                visitedEvents.add(event);
            }
        };

        EventBinding<LoadSourceSectionListener> binding;
        binding = instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(nearestFilter, baseFilter, listener, true);
        assertEvents(visitedEvents, expectedSections);
        binding.dispose();
        visitedEvents.clear();
    }
}
