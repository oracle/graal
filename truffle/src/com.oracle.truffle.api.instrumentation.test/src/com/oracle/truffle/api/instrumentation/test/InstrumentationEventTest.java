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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;

public class InstrumentationEventTest {

    protected Context context;
    protected InputFilterTestInstrument instrument;
    protected Instrumenter instrumenter;
    protected List<Event> events = new ArrayList<>();
    protected ExecutionEventNodeFactory factory;
    protected Error error;

    protected class CollectEventsNode extends ExecutionEventNode {

        final EventContext c;

        CollectEventsNode(EventContext context) {
            for (int i = 0; i < getInputCount(); i++) {
                Assert.assertNotNull(getInputContext(i));
            }
            this.c = context;
        }

        @Override
        protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
            saveInputValue(frame, inputIndex, inputValue);

            assertTrue(inputIndex < getInputCount());
            assertSame(inputContext, getInputContext(inputIndex));

            events.add(new Event(EventKind.INPUT_VALUE, c, frame, null, null, inputIndex, inputValue, createInputContexts(), null));
        }

        @Override
        protected Object onUnwind(VirtualFrame frame, Object info) {
            events.add(new Event(EventKind.UNWIND, c, frame, null, null, -1, null, createInputContexts(), info));
            return super.onUnwind(frame, info);
        }

        @Override
        public void onEnter(VirtualFrame frame) {
            // Needs to be materialized because of running with immediate compilation
            events.add(new Event(EventKind.ENTER, c, frame.materialize(), null, null, -1, null, createInputContexts(), null));
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            events.add(new Event(EventKind.RETURN_VALUE, c, frame, getSavedInputValues(frame), result, -1, null, createInputContexts(), null));
        }

        private EventContext[] createInputContexts() {
            EventContext[] inputContexts = new EventContext[getInputCount()];
            for (int i = 0; i < getInputCount(); i++) {
                Assert.assertNotNull(getInputContext(i));
                inputContexts[i] = getInputContext(i);
            }
            return inputContexts;
        }
    }

    protected class CollectEventsFactory implements ExecutionEventNodeFactory {
        @Override
        public ExecutionEventNode create(EventContext c) {
            return new CollectEventsNode(c);
        }
    }

    @Before
    public final void setUp() {
        this.context = Context.create();
        this.instrument = context.getEngine().getInstruments().get(InputFilterTestInstrument.ID).lookup(InputFilterTestInstrument.class);
        this.instrumenter = instrument.environment.getInstrumenter();
        this.events = new ArrayList<>();
        this.factory = new CollectEventsFactory();
    }

    protected final void execute(org.graalvm.polyglot.Source source) {
        context.eval(source);
        if (error != null) {
            throw error;
        }
    }

    protected final void execute(String expression) {
        execute(createSource(expression));
    }

    protected static final org.graalvm.polyglot.Source createSource(String expression) {
        return org.graalvm.polyglot.Source.create(InstrumentationTestLanguage.ID, expression);
    }

    protected static void assertCharacters(Event e, String s) {
        assertEquals(s, e.context.getInstrumentedSourceSection().getCharacters().toString());
    }

    protected final void assertOn(EventKind expectedKind) {
        assertOn(expectedKind, (e) -> {
        });
    }

    protected final void assertOn(EventKind expectedKind, Consumer<Event> verify) {
        assertFalse("queue not empty", events.isEmpty());
        Event event = events.remove(0);
        assertEquals(expectedKind, event.kind);
        verify.accept(event);
    }

    protected final void assertAllEventsConsumed() {
        assertTrue("all events consumed " + events, events.isEmpty());
    }

    @After
    public final void tearDown() {
        context.close();
        assertAllEventsConsumed();
    }

    enum EventKind {
        ENTER,
        INPUT_VALUE,
        RETURN_VALUE,
        UNWIND,
    }

    protected static class Event {

        public final EventKind kind;
        public final VirtualFrame frame;
        public final EventContext context;
        public final Object result;
        public final Object[] inputs;
        public final int inputValueIndex;
        public final Object inputValue;
        public final EventContext[] inputContexts;
        public final Object unwindValue;

        Event(EventKind kind, EventContext context, VirtualFrame frame, Object[] inputs, Object result, int index, Object inputValue, EventContext[] inputContexts, Object unwindValue) {
            this.kind = kind;
            this.context = context;
            this.frame = frame;
            this.result = result;
            this.inputs = inputs;
            this.inputValue = inputValue;
            this.inputValueIndex = index;
            this.inputContexts = inputContexts;
            this.unwindValue = unwindValue;
        }

        @Override
        public String toString() {
            return "Event [kind=" + kind + "]";
        }

    }

}
