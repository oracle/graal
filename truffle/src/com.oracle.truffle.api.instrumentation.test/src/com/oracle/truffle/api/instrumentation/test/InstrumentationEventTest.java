/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
            events.add(new Event(EventKind.ENTER, c, frame, null, null, -1, null, createInputContexts(), null));
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
