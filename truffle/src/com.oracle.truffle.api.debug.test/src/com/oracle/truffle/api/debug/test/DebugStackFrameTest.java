/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Test;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.polyglot.Source;

public class DebugStackFrameTest extends AbstractDebugTest {

    @Test
    public void testEvalAndSideEffects() throws Throwable {
        final Source source = testSource("ROOT(DEFINE(a,ROOT( \n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(b, 43), \n" +
                        "  VARIABLE(c, 44), \n" +
                        "  STATEMENT(),\n" + // will start stepping here
                        "  STATEMENT())\n" +
                        "), \n" +
                        "VARIABLE(a, 42), VARIABLE(b, 43), VARIABLE(c, 44), \n" +
                        "CALL(a))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                Iterator<DebugStackFrame> stackFrames = event.getStackFrames().iterator();
                // assert changes to the current frame
                DebugStackFrame frame = stackFrames.next();
                assertDynamicFrame(frame);
                DebugValue aValue = frame.getScope().getDeclaredValue("a");
                String aStringValue = aValue.as(String.class);

                // assert changes to a parent frame
                frame = stackFrames.next();
                assertDynamicFrame(frame);

                // assign from one stack frame to another one
                frame.getScope().getDeclaredValue("a").set(aValue);
                assertEquals(aStringValue, frame.getScope().getDeclaredValue("a").as(String.class));
                event.prepareContinue();
            });
            expectDone();
        }
    }

    private static void assertDynamicFrame(DebugStackFrame frame) {
        assertEquals("42", frame.getScope().getDeclaredValue("a").as(String.class));
        assertEquals("43", frame.getScope().getDeclaredValue("b").as(String.class));
        assertEquals("44", frame.getScope().getDeclaredValue("c").as(String.class));

        // dynamic value should now be accessible
        DebugValue dStackValue = frame.getScope().getDeclaredValue("d");
        assertNull(dStackValue);

        // should change the dynamic value
        assertEquals("45", frame.eval("VARIABLE(d, 45)").as(String.class));
        dStackValue = frame.getScope().getDeclaredValue("d");
        assertEquals("45", dStackValue.as(String.class));
        assertEquals("45", frame.getScope().getDeclaredValue("d").as(String.class));

        // change an existing value
        assertEquals("45", frame.eval("VARIABLE(c, 45)").as(String.class));
        assertEquals("45", frame.getScope().getDeclaredValue("c").as(String.class));

        // set an existing value using a constant expression
        DebugValue bValue = frame.getScope().getDeclaredValue("b");
        frame.getScope().getDeclaredValue("b").set(frame.eval("CONSTANT(46)"));
        assertEquals("46", frame.getScope().getDeclaredValue("b").as(String.class));
        assertEquals("46", bValue.as(String.class));

        // set an existing value using a constant expression with side effect
        frame.getScope().getDeclaredValue("b").set(frame.eval("VARIABLE(a, 47)"));
        assertEquals("47", frame.getScope().getDeclaredValue("b").as(String.class));
        assertEquals("47", frame.getScope().getDeclaredValue("a").as(String.class));
    }

    @Test
    public void testFrameValidity() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(b, 43), \n" +
                        "  VARIABLE(c, 44), \n" +
                        "  STATEMENT(),\n" +
                        "  STATEMENT()\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            class SharedData {
                DebugStackFrame frame;
                DebugValue stackValueWithGetValue;
                DebugValue stackValueWithIterator;
                Iterator<DebugStackFrame> frameIterator2;
                DebugValue heapValue;
            }
            SharedData data = new SharedData();

            expectSuspended((SuspendedEvent event) -> {
                data.frame = event.getTopStackFrame();
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                assertSame(data.frame, frameIterator.next());
                assertFalse(frameIterator.hasNext());
                checkStack(data.frame, "a", "42", "b", "43", "c", "44");

                // values for verifying state checks
                data.frameIterator2 = event.getStackFrames().iterator();
                data.stackValueWithGetValue = data.frame.getScope().getDeclaredValue("a");
                data.stackValueWithIterator = data.frame.getScope().getDeclaredValues().iterator().next();

                // should dynamically create a local variable
                data.heapValue = data.frame.eval("VARIABLE(d, 45)");
                event.prepareStepInto(1); // should render all pointers invalid
            });

            expectSuspended((SuspendedEvent event) -> {
                // next event everything should be invalidated except heap values
                assertInvalidFrame(data.frame);
                assertInvalidIterator(data.frameIterator2);
                assertInvalidDebugValue(data.stackValueWithGetValue);
                assertInvalidDebugValue(data.stackValueWithIterator);

                assertEquals("45", data.heapValue.as(String.class));
                assertFalse(data.heapValue.isWritable());
                assertTrue(data.heapValue.isReadable());

                try {
                    data.heapValue.set(data.heapValue);
                    fail();
                } catch (IllegalStateException e) {
                }
            });

            expectDone();
        }
    }

    @Test
    public void testSourceSections() {
        final Source source = testSource("ROOT(DEFINE(a,ROOT(\n" +
                        "  STATEMENT())\n" +
                        "),\n" +
                        "DEFINE(b,ROOT(\n" +
                        "  CALL(a))\n" +
                        "), \n" +
                        "CALL(b))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                SourceSection ss = frame.getSourceSection();
                assertSection(ss, "STATEMENT()", 2, 3, 2, 13);
                SourceSection fss = getFunctionSourceSection(frame);
                assertSection(fss, "a,ROOT(\n  STATEMENT())\n", 1, 13, 2, 15);
                Iterator<DebugStackFrame> stackFrames = event.getStackFrames().iterator();
                assertEquals(frame, stackFrames.next()); // The top one
                frame = stackFrames.next(); // b
                ss = frame.getSourceSection();
                assertSection(ss, "CALL(a)", 5, 3, 5, 9);
                fss = getFunctionSourceSection(frame);
                assertSection(fss, "b,ROOT(\n  CALL(a))\n", 4, 8, 5, 11);
                frame = stackFrames.next(); // root
                ss = frame.getSourceSection();
                assertSection(ss, "CALL(b)", 7, 1, 7, 7);
                fss = getFunctionSourceSection(frame);
                assertSection(fss, source.getCharacters().toString(), 1, 1, 7, 9);
                assertFalse(stackFrames.hasNext());
                event.prepareContinue();
            });
            expectDone();
        }
    }

    private static SourceSection getFunctionSourceSection(DebugStackFrame frame) {
        // There are only function scopes in the InstrumentationTestLanguage
        assertTrue(frame.getScope().isFunctionScope());
        return frame.getScope().getSourceSection();
    }

    private static void assertSection(SourceSection ss, String code, int startLine, int startColumn, int endLine, int endcolumn) {
        assertEquals(code, ss.getCharacters());
        assertEquals("startLine", startLine, ss.getStartLine());
        assertEquals("startColumn", startColumn, ss.getStartColumn());
        assertEquals("endLine", endLine, ss.getEndLine());
        assertEquals("endColumn", endcolumn, ss.getEndColumn());
    }

    private static void assertInvalidDebugValue(DebugValue value) {
        try {
            value.as(String.class);
            fail();
        } catch (IllegalStateException s) {
        }
        try {
            value.set(value);
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            value.isReadable();
        } catch (IllegalStateException s) {
        }

        try {
            value.isWritable();
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            value.getName();
            fail();
        } catch (IllegalStateException s) {
        }

    }

    private static void assertInvalidIterator(Iterator<DebugStackFrame> iterator) {
        try {
            iterator.hasNext();
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            iterator.next();
            fail();
        } catch (IllegalStateException s) {
        }
    }

    private static void assertInvalidFrame(DebugStackFrame frame) {
        try {
            frame.eval("STATEMENT");
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            frame.getName();
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            frame.getSourceSection();
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            frame.getScope().getDeclaredValue("d");
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            frame.isInternal();
            fail();
        } catch (IllegalStateException s) {
        }

        try {
            frame.getScope().getDeclaredValues().iterator();
            fail();
        } catch (IllegalStateException s) {
        }
    }

}
