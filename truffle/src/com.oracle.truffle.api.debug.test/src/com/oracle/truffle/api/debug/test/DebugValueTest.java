/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings({"static-method", "unused"})
public class DebugValueTest extends AbstractDebugTest {

    @Test
    public void testNumValue() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(b, true), \n" +
                        "  VARIABLE(inf, infinity), \n" +
                        "  STATEMENT()\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue value42 = frame.getScope().getDeclaredValue("a");
                DebugValue valueTrue = frame.getScope().getDeclaredValue("b");
                DebugValue valueInf = frame.getScope().getDeclaredValue("inf");

                assertEquals("a", value42.getName());
                assertFalse(value42.isArray());
                assertNull(value42.getArray());
                assertNull(value42.getProperties());
                assertFalse(value42.isBoolean());
                assertTrue(value42.isNumber());
                assertTrue(value42.fitsInLong());
                assertTrue(value42.fitsInInt());
                assertTrue(value42.fitsInShort());
                assertTrue(value42.fitsInByte());
                assertTrue(value42.fitsInDouble());
                assertTrue(value42.fitsInFloat());
                assertFalse(value42.isString());
                assertFalse(value42.isDate());
                assertFalse(value42.isDuration());
                assertFalse(value42.isInstant());
                assertFalse(value42.isTime());
                assertFalse(value42.isTimeZone());
                assertEquals("42", value42.toDisplayString());
                DebugValue value42Meta = value42.getMetaObject();
                assertEquals("Integer", value42Meta.toDisplayString());
                assertEquals("Integer", value42Meta.getMetaQualifiedName());
                assertEquals("Integer", value42Meta.getMetaSimpleName());
                assertTrue(value42Meta.isMetaInstance(value42));
                assertFalse(value42Meta.isMetaInstance(valueTrue));
                assertFalse(value42Meta.isMetaInstance(valueInf));
                SourceSection integerSS = value42.getSourceLocation();
                assertEquals("source integer", integerSS.getCharacters());

                assertEquals("b", valueTrue.getName());
                assertTrue(valueTrue.isBoolean());
                assertFalse(valueTrue.isNumber());
                assertFalse(valueTrue.fitsInLong());
                assertFalse(valueTrue.fitsInInt());
                assertFalse(valueTrue.fitsInDouble());
                assertEquals("true", valueTrue.toDisplayString());
                DebugValue valueTrueMeta = valueTrue.getMetaObject();
                assertEquals("Boolean", valueTrueMeta.toDisplayString());
                assertEquals("Boolean", valueTrueMeta.getMetaQualifiedName());
                assertEquals("Boolean", valueTrueMeta.getMetaSimpleName());
                assertTrue(valueTrueMeta.isMetaInstance(valueTrue));
                assertFalse(valueTrueMeta.isMetaInstance(value42));
                assertFalse(valueTrueMeta.isMetaInstance(valueInf));

                assertEquals("inf", valueInf.getName());
                assertFalse(valueInf.isBoolean());
                assertTrue(valueInf.isNumber());
                assertFalse(valueInf.fitsInLong());
                assertTrue(valueInf.fitsInDouble());
                assertEquals("Infinity", valueInf.toDisplayString());
                DebugValue valueInfMeta = valueInf.getMetaObject();
                assertEquals("Infinity", valueInfMeta.toDisplayString());
                assertEquals("Infinity", valueInfMeta.getMetaQualifiedName());
                assertEquals("Infinity", valueInfMeta.getMetaSimpleName());
                assertTrue(valueInfMeta.isMetaInstance(valueInf));
                assertFalse(valueInfMeta.isMetaInstance(value42));
                assertFalse(valueInfMeta.isMetaInstance(valueTrue));
                SourceSection infinitySS = valueInf.getSourceLocation();
                assertEquals("source infinity", infinitySS.getCharacters());
            });

            expectDone();
        }
    }

    @Test
    public void testGetRawValue() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(inf, infinity), \n" +
                        "  STATEMENT()\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue value42 = frame.getScope().getDeclaredValue("a");
                assertEquals(42, value42.getRawValue(InstrumentationTestLanguage.class));
                assertEquals(Double.POSITIVE_INFINITY, frame.getScope().getDeclaredValue("inf").getRawValue(InstrumentationTestLanguage.class));
            });

            expectDone();
        }
    }

    @Test
    public void testGetRawValueRestricted() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  VARIABLE(a, 42), \n" +
                        "  VARIABLE(inf, infinity), \n" +
                        "  STATEMENT()\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue value42 = frame.getScope().getDeclaredValue("a");
                assertNull(value42.getRawValue(ProxyLanguage.class));
                assertNull(frame.getScope().getDeclaredValue("inf").getRawValue(ProxyLanguage.class));
            });

            expectDone();
        }
    }

    /**
     * Test of {@link DebugValue#isReadable()}, {@link DebugValue#isWritable()} and
     * {@link DebugValue#isInternal()}.
     */
    @Test
    public void testValueAttributes() throws Throwable {
        final Source source = testSource("DEFINE(function, ROOT(\n" +
                        "  ARGUMENT(a), \n" +
                        "  STATEMENT()\n" +
                        "))\n");
        Context context = Context.create();
        context.eval(source);
        Value functionValue = context.getBindings(InstrumentationTestLanguage.ID).getMember("function");
        assertNotNull(functionValue);
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);

        // Test of the default attribute values:
        NoAttributesTruffleObject nao = new NoAttributesTruffleObject();
        boolean[] suspended = new boolean[]{false};
        DebuggerSession session = debugger.startSession((SuspendedEvent event) -> {
            assertFalse(suspended[0]);
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            assertNotNull(value);
            DebugValue attributesTOValue = value.getProperties().iterator().next();
            assertEquals("property", attributesTOValue.getName());
            // Property is readable by default
            assertTrue(attributesTOValue.isReadable());
            // Property is writable by default
            assertTrue(attributesTOValue.isWritable());
            // Property is not internal by default
            assertFalse(attributesTOValue.isInternal());
            // Property does not have read side-effects by default
            assertFalse(attributesTOValue.hasReadSideEffects());
            // Property does not have write side-effects by default
            assertFalse(attributesTOValue.hasWriteSideEffects());
            // Test canExecute
            assertFalse(attributesTOValue.canExecute());
            DebugValue fvalue = event.getSession().getTopScope(InstrumentationTestLanguage.ID).getDeclaredValue("function");
            assertTrue(fvalue.canExecute());
            event.prepareContinue();
            suspended[0] = true;
        });
        session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build());
        functionValue.execute(nao);
        session.close();
        assertTrue(suspended[0]);

        // Test of the modified attribute values:
        suspended[0] = false;
        final ModifiableAttributesTruffleObject mao = new ModifiableAttributesTruffleObject();
        session = debugger.startSession((SuspendedEvent event) -> {
            assertFalse(suspended[0]);
            DebugValue value = event.getTopStackFrame().getScope().getDeclaredValue("a");
            assertNotNull(value);
            DebugValue attributesTOValue = value.getProperties().iterator().next();
            assertEquals("property", attributesTOValue.getName());
            // All false initially
            assertFalse(attributesTOValue.isReadable());
            assertFalse(attributesTOValue.isWritable());
            assertFalse(attributesTOValue.isInternal());
            assertFalse(attributesTOValue.hasReadSideEffects());
            assertFalse(attributesTOValue.hasWriteSideEffects());
            mao.setIsReadable(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isReadable());
            mao.setIsWritable(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isWritable());
            mao.setIsInternal(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.isInternal());
            mao.setHasReadSideEffects(true);
            mao.setHasWriteSideEffects(true);
            attributesTOValue = value.getProperties().iterator().next();
            assertTrue(attributesTOValue.hasReadSideEffects());
            assertTrue(attributesTOValue.hasWriteSideEffects());
            event.prepareContinue();
            suspended[0] = true;
        });
        session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build());
        functionValue.execute(mao);
        session.close();
        assertTrue(suspended[0]);
    }

    @Test
    public void testCached() {
        final Source source = testSource("DEFINE(function, ROOT(\n" +
                        "  ARGUMENT(a), \n" +
                        "  STATEMENT()\n" +
                        "))\n");
        Context context = Context.create();
        context.eval(source);
        Value functionValue = context.getBindings(InstrumentationTestLanguage.ID).getMember("function");
        assertNotNull(functionValue);
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);

        boolean[] suspended = new boolean[]{false};
        final ModifiableAttributesTruffleObject ma = new ModifiableAttributesTruffleObject();
        try (DebuggerSession session = debugger.startSession((SuspendedEvent event) -> {
            assertFalse(suspended[0]);
            DebugValue a = event.getTopStackFrame().getScope().getDeclaredValue("a");
            ma.setIsReadable(true);
            DebugValue ap1 = a.getProperty("p1");
            DebugValue ap2 = a.getProperty("p2");
            assertNotNull(ap1);
            assertNotNull(ap2);

            assertEquals(0, ap1.asInt());
            assertEquals(0, ap2.asInt());
            assertEquals(0, ap1.asInt());
            ap2 = a.getProperty("p2"); // Get a fresh property value
            assertEquals(1, ap2.asInt());
            ap1.isArray();
            ap1.isNull();
            ap2.isArray();
            ap2.isNull();
            assertEquals(0, ap1.asInt());
            assertEquals(1, ap2.asInt());

            DebugValue ap1New = a.getProperty("p1");
            DebugValue ap2New = a.getProperty("p2");
            ap1New.isNull();
            ap2New.isNull();
            assertEquals(1, ap1New.asInt());
            assertEquals(2, ap2New.asInt());
            a.getProperty("p1");
            ap1New = a.getProperty("p1");
            ap1New.isNull();
            ap2New.isNull();
            assertEquals(2, ap1New.asInt());
            assertEquals(2, ap2New.asInt());
            // Original properties are unchanged:
            assertEquals(0, ap1.asInt());
            assertEquals(1, ap2.asInt());
            event.prepareContinue();
            suspended[0] = true;
        })) {
            session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build());
            functionValue.execute(ma);
        }
        assertTrue(suspended[0]);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NoAttributesTruffleObject implements TruffleObject {

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(boolean internal) {
            return new PropertyKeysTruffleObject();
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return true;
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            return true;
        }

        @ExportMessage
        boolean isMemberInsertable(String member) {
            return false;
        }

        @ExportMessage
        void writeMember(String member, Object value) {
        }

        @ExportMessage
        Object readMember(String member) {
            return "propertyValue";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ModifiableAttributesTruffleObject implements TruffleObject {

        private boolean isReadable;
        private boolean hasReadSideEffects;
        private boolean isWritable;
        private boolean hasWriteSideEffects;
        private boolean isInternal;
        private final Map<String, Integer> memberCounters = new HashMap<>();

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(boolean internal) {
            if (internal || isInternal == internal) {
                return new PropertyKeysTruffleObject();
            } else {
                return new EmptyKeysTruffleObject();
            }
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return isReadable;
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            return isWritable;
        }

        @ExportMessage
        boolean isMemberInternal(String member) {
            return isInternal;
        }

        @ExportMessage
        boolean hasMemberReadSideEffects(String member) {
            return hasReadSideEffects;
        }

        @ExportMessage
        boolean hasMemberWriteSideEffects(String member) {
            return hasWriteSideEffects;
        }

        @ExportMessage
        boolean isMemberInsertable(String member) {
            return false;
        }

        @ExportMessage
        void writeMember(String member, Object value) {
        }

        @ExportMessage
        Object readMember(String member) {
            Integer counter = memberCounters.get(member);
            if (counter == null) {
                counter = 0;
            } else {
                counter++;
            }
            memberCounters.put(member, counter);
            return counter;
        }

        public void setIsReadable(boolean isReadable) {
            this.isReadable = isReadable;
        }

        public void setHasReadSideEffects(boolean hasSideEffects) {
            this.hasReadSideEffects = hasSideEffects;
        }

        public void setIsWritable(boolean isWritable) {
            this.isWritable = isWritable;
        }

        public void setHasWriteSideEffects(boolean hasSideEffects) {
            this.hasWriteSideEffects = hasSideEffects;
        }

        public void setIsInternal(boolean isInternal) {
            this.isInternal = isInternal;
        }

        public String getMemberCounters() {
            return memberCounters.toString();
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof ModifiableAttributesTruffleObject;
        }

    }

    /**
     * Truffle object representing property keys and having one property named "property".
     */
    @ExportLibrary(InteropLibrary.class)
    static final class PropertyKeysTruffleObject implements TruffleObject {

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            switch ((int) index) {
                case 0:
                    return "property";
                case 1:
                    return "p1";
                case 2:
                    return "p2";
                default:
                    throw new IllegalStateException("Wrong index: " + index);
            }
        }

        @ExportMessage
        long getArraySize() throws UnsupportedMessageException {
            return 3L;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < 3;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class EmptyKeysTruffleObject implements TruffleObject {

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        long getArraySize() throws UnsupportedMessageException {
            return 0L;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return false;
        }
    }

}
