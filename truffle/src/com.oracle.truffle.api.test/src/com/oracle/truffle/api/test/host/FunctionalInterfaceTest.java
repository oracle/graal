/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public class FunctionalInterfaceTest extends ProxyLanguageEnvTest {
    private static final String EXPECTED_RESULT = "narf";

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
        Object result = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), server, "requestHandler", new TestExecutable());
        assertEquals(EXPECTED_RESULT, result);
    }

    @Test
    public void testLegacyFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        Object result = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), server, "requestHandler2", new TestExecutable());
        assertEquals(EXPECTED_RESULT, result);
    }

    @Test
    public void testThread() throws InteropException {
        TruffleObject threadClass = (TruffleObject) env.lookupHostSymbol("java.lang.Thread");
        Object result = ForeignAccess.sendNew(Message.NEW.createNode(), threadClass, new TestExecutable());
        assertTrue(env.isHostObject(result));
        Object thread = env.asHostObject(result);
        assertTrue(thread instanceof Thread);
    }

    @Test(expected = UnsupportedTypeException.class)
    public void testNonFunctionalInterface() throws InteropException {
        TruffleObject server = (TruffleObject) env.asGuestValue(new HttpServer());
        ForeignAccess.sendInvoke(Message.INVOKE.createNode(), server, "unsupported", new TestExecutable());
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

    @MessageResolution(receiverType = TestExecutable.class)
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

        @Override
        public ForeignAccess getForeignAccess() {
            return TestExecutableForeign.ACCESS;
        }

        @Resolve(message = "EXECUTE")
        abstract static class Execute extends Node {
            String access(TestExecutable obj, @SuppressWarnings("unused") Object[] args) {
                return obj.result;
            }
        }
    }

    @MessageResolution(receiverType = TestRunnable.class)
    static final class TestRunnable implements TruffleObject {
        final TruffleLanguage.Env env;

        TestRunnable(TruffleLanguage.Env env) {
            this.env = env;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof TestRunnable;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return TestRunnableForeign.ACCESS;
        }

        @Resolve(message = "EXECUTE")
        abstract static class Execute extends Node {
            @SuppressWarnings("unused")
            String access(TestRunnable obj, Object[] args) {
                return "EXECUTE";
            }
        }

        @Resolve(message = "KEYS")
        abstract static class Keys extends Node {
            @TruffleBoundary
            Object access(TestRunnable obj) {
                return obj.env.asGuestValue(Collections.singletonList("get"));
            }
        }

        @SuppressWarnings("unused")
        @Resolve(message = "READ")
        abstract static class Read extends Node {
            @TruffleBoundary
            Object access(TestRunnable obj, String name) {
                if (name.equals("get")) {
                    return new TestExecutable("READ+EXECUTE");
                }
                throw UnknownIdentifierException.raise(name);
            }
        }

        @SuppressWarnings("unused")
        @Resolve(message = "KEY_INFO")
        abstract static class KeyI extends Node {
            @TruffleBoundary
            Object access(TestRunnable obj, String name) {
                return name.equals("get") ? KeyInfo.READABLE : KeyInfo.NONE;
            }
        }
    }
}
