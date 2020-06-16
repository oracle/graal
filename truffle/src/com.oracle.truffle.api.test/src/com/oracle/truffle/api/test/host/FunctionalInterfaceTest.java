/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class FunctionalInterfaceTest extends ProxyLanguageEnvTest {
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    static final String EXPECTED_RESULT = "narf";

    @SuppressWarnings({"static-method", "unused"})
    public static final class HttpServer {
        public String requestHandler(Supplier<String> handler) {
            return handler.get();
        }

        public Supplier<String> requestHandler() {
            return null;
        }

        public String requestHandler2(LegacyFunctionalInterface<String> handler) {
            return handler.get();
        }

        public String unsupported(NonFunctionalInterface handler) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        Object result = INTEROP.invokeMember(server, "requestHandler", new TestExecutable());
        assertEquals(EXPECTED_RESULT, result);
    }

    @Test
    public void testLegacyFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        Object result = INTEROP.invokeMember(server, "requestHandler2", new TestExecutable());
        assertEquals(EXPECTED_RESULT, result);
    }

    @Test
    public void testThread() throws InteropException {
        TruffleObject threadClass = (TruffleObject) env.lookupHostSymbol("java.lang.Thread");
        Object result = INTEROP.instantiate(threadClass, new TestExecutable());
        assertTrue(env.isHostObject(result));
        Object thread = env.asHostObject(result);
        assertTrue(thread instanceof Thread);
    }

    @Test(expected = UnsupportedTypeException.class)
    public void testNonFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        INTEROP.invokeMember(server, "unsupported", new TestExecutable());
    }

    @Test
    public void testValue() {
        Supplier<String> fi = () -> EXPECTED_RESULT;
        Value fiValue = context.asValue(fi);
        assertTrue(fiValue.canExecute());
        assertTrue(fiValue.getMember("get").canExecute());
        assertEquals(EXPECTED_RESULT, fiValue.execute().asString());

        LegacyFunctionalInterface<String> lfi = () -> EXPECTED_RESULT;
        Value lfiValue = context.asValue(lfi);
        assertFalse(lfiValue.canExecute());
        assertTrue(lfiValue.getMember("get").canExecute());
        try {
            lfiValue.execute();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ex) {
        }
    }

    @Test
    public void testExecutableValueAs() {
        Value value = context.asValue(new TestExecutable());
        assertTrue(value.canExecute());
        assertEquals(EXPECTED_RESULT, value.execute().asString());

        Supplier<?> fi = value.as(Supplier.class);
        assertEquals(EXPECTED_RESULT, fi.get());
        LegacyFunctionalInterface<?> lfi = value.as(LegacyFunctionalInterface.class);
        assertEquals(EXPECTED_RESULT, lfi.get());
        try {
            value.as(NonFunctionalInterface.class);
            fail("expected ClassCastException");
        } catch (ClassCastException ex) {
        }
    }

    @Test
    public void testExecutableAndHasMembers() {
        Value value = context.asValue(new TestRunnable(env));
        assertTrue(value.canExecute());
        assertTrue(value.hasMembers());
        assertEquals("EXECUTE", value.execute().asString());
        assertEquals("READ+EXECUTE", value.getMember("get").execute().asString());

        Supplier<?> fi = value.as(Supplier.class);
        assertEquals("EXECUTE", fi.get());
        LegacyFunctionalInterface<?> lfi = value.as(LegacyFunctionalInterface.class);
        assertEquals("EXECUTE", lfi.get());
        NonFunctionalInterface nfi = value.as(NonFunctionalInterface.class);
        assertEquals("READ+EXECUTE", nfi.get());
    }

    public interface LegacyFunctionalInterface<T> {
        T get();
    }

    public interface NonFunctionalInterface {
        default String get() {
            throw new UnsupportedOperationException();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestExecutable implements TruffleObject {
        final String result;

        TestExecutable(String result) {
            this.result = result;
        }

        TestExecutable() {
            this(EXPECTED_RESULT);
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof TestExecutable;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(@SuppressWarnings("unused") Object[] arguments) {
            return result;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"unused", "static-method"})
    static final class TestRunnable implements TruffleObject {
        final TruffleLanguage.Env env;

        TestRunnable(TruffleLanguage.Env env) {
            this.env = env;
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments) {
            return "EXECUTE";
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return member.equals("get");
        }

        @ExportMessage
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return env.asGuestValue(Collections.singletonList("get"));
        }

        @ExportMessage
        Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            if (member.equals("get")) {
                return new TestExecutable("READ+EXECUTE");
            }
            throw UnknownIdentifierException.create(member);
        }
    }
}
