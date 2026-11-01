/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Value.Optimized;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.polyglot.PolyglotGeneratedProxy.DefaultMethodFallbackException;

public class GeneratedProxyNativeImageTest {

    @Optimized
    @HostAccess.Implementable
    @FunctionalInterface
    public interface Function {
        int apply(int left, int right);
    }

    @Optimized
    @HostAccess.Implementable
    public interface Operations {
        int add(int left, int right);

        String name();

        default String describe(int left, int right) {
            return name() + ":" + add(left, right);
        }
    }

    public interface DefaultMethods {
        int apply(int value);

        default String success() {
            return "default";
        }

        default String unchecked() {
            throw new IllegalStateException("default failed");
        }

        default String checked() throws IOException {
            throw new IOException("default failed");
        }

        default String undeclared() {
            return GeneratedProxyNativeImageTest.<RuntimeException, String> raise(new IOException("undeclared"));
        }

        default String error() {
            throw new AssertionError("default failed");
        }

        default String closeThenFail(Context context) {
            context.close();
            throw new IllegalStateException("closed");
        }
    }

    @Optimized
    public interface ThrowingDefaults extends DefaultMethods {
    }

    public interface AnyList {
        List<?> get();
    }

    public interface StringList {
        List<String> get();
    }

    @Optimized
    public interface StringFirstObject extends StringList, AnyList {
        int extra();
    }

    @Optimized
    public interface AnyFirstObject extends AnyList, StringList {
        int extra();
    }

    @Optimized
    @FunctionalInterface
    public interface StringFirstSam extends StringList, AnyList {
    }

    @Optimized
    @FunctionalInterface
    public interface AnyFirstSam extends AnyList, StringList {
    }

    public interface NumberResult {
        Number result();
    }

    @Optimized
    public interface GetterNames extends NumberResult {
        String languageContext = "interface context";
        String receiver = "interface receiver";

        @Override
        Integer result();

        int getGuestObject();

        int getLanguageContext();

        int getContext();

        int getReceiver();

        boolean isEligible(Class<?> type);

        Object getProxyClass(Class<?> type);
    }

    @Optimized
    public interface ObjectGetterNames {
        Object getGuestObject();

        Object getLanguageContext();

        Object getContext();

        Object getReceiver();
    }

    @Optimized
    public interface ExactNameCollision {
        int apply(int value);

        // Intentionally collides with the private exact helper's name and parameter types.
        // Checkstyle: stop method name check
        @SuppressWarnings("unused")
        default int apply$exact(CallTarget target, Object languageContext, Object receiver, int value) {
            return value * 2;
        }
        // Checkstyle: resume method name check
    }

    @Test
    public void testWrapperGetterNamesAndCovariantReturns() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.of(
                            "result", (ProxyExecutable) arguments -> 42,
                            "getGuestObject", 17,
                            "getLanguageContext", 21,
                            "getContext", 25,
                            "getReceiver", 29,
                            "isEligible", (ProxyExecutable) arguments -> true,
                            "getProxyClass", (ProxyExecutable) arguments -> "guest")));
            GetterNames proxy = value.as(GetterNames.class);
            assertEquals(Integer.valueOf(42), proxy.result());
            NumberResult parent = proxy;
            assertEquals(Integer.valueOf(42), parent.result());
            assertEquals(17, proxy.getGuestObject());
            assertEquals(21, proxy.getLanguageContext());
            assertEquals(25, proxy.getContext());
            assertEquals(29, proxy.getReceiver());
            assertTrue(proxy.isEligible(String.class));
            assertEquals("guest", proxy.getProxyClass(String.class));
            assertEquals(value, context.asValue(proxy));
            assertEquals(value, Value.asValue(proxy));
        }
    }

    @Test
    public void testObjectGetterNames() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            int[] calls = {0};
            ProxyExecutable getter = arguments -> {
                calls[0]++;
                return "guest";
            };
            Value value = context.asValue(ProxyObject.fromMap(Map.of(
                            "getGuestObject", getter,
                            "getLanguageContext", getter,
                            "getContext", getter,
                            "getReceiver", getter)));
            ObjectGetterNames proxy = value.as(ObjectGetterNames.class);
            assertEquals("guest", proxy.getGuestObject());
            assertEquals("guest", proxy.getLanguageContext());
            assertEquals("guest", proxy.getContext());
            assertEquals("guest", proxy.getReceiver());
            assertEquals(4, calls[0]);
            assertEquals(value, context.asValue(proxy));
            assertEquals(value, Value.asValue(proxy));
            assertTrue(proxy.equals(value.as(ObjectGetterNames.class)));
            proxy.hashCode();
            assertNotNull(proxy.toString());
            assertEquals(4, calls[0]);
        }
    }

    @Test
    public void testExactEntryNameCollision() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ExactNameCollision function = context.asValue((ProxyExecutable) arguments -> arguments[0].asInt() + 1).as(ExactNameCollision.class);
            assertEquals(42, function.apply(41));
            assertEquals(42, function.apply$exact(null, null, null, 21));
            ExactNameCollision object = context.asValue(ProxyObject.fromMap(Map.of("apply", (ProxyExecutable) arguments -> arguments[0].asInt() + 1))).as(ExactNameCollision.class);
            assertEquals(42, object.apply(41));
            assertEquals(42, object.apply$exact(null, null, null, 21));
        }
    }

    @Test
    public void testObjectMethodGuestOverrides() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            int[] displayCalls = {0};
            Value value = context.asValue(ProxyObject.fromMap(Map.of(
                            "add", (ProxyExecutable) arguments -> arguments[0].asInt() + arguments[1].asInt(),
                            "equals", (ProxyExecutable) arguments -> {
                                assertEquals(1, arguments.length);
                                return true;
                            },
                            "hashCode", (ProxyExecutable) arguments -> {
                                assertEquals(0, arguments.length);
                                return 37;
                            },
                            "toString", (ProxyExecutable) arguments -> {
                                assertEquals(0, arguments.length);
                                displayCalls[0]++;
                                return "guest";
                            })));
            Operations proxy = value.as(Operations.class);
            assertTrue(proxy.equals(new Object()));
            assertEquals(37, proxy.hashCode());
            assertNotNull(proxy.toString());
            assertEquals(1, displayCalls[0]);
            assertEquals(value, context.asValue(proxy));
        }
    }

    @Test
    public void testObjectMethodsRestoreEnteredContext() {
        try (Context outer = Context.create(); Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            IllegalStateException failure = new IllegalStateException("guest failed");
            Operations proxy = context.asValue(ProxyObject.fromMap(Map.of(
                            "equals", (ProxyExecutable) arguments -> {
                                assertEquals(context, Context.getCurrent());
                                return true;
                            },
                            "hashCode", (ProxyExecutable) arguments -> {
                                assertEquals(context, Context.getCurrent());
                                throw failure;
                            }))).as(Operations.class);
            outer.enter();
            try {
                assertTrue(proxy.equals(new Object()));
                assertEquals(outer, Context.getCurrent());
                PolyglotException exception = assertThrows(PolyglotException.class, proxy::hashCode);
                assertSame(failure, exception.asHostException());
                assertEquals(outer, Context.getCurrent());
                assertNotNull(proxy.toString());
                assertEquals(outer, Context.getCurrent());
            } finally {
                outer.leave();
            }
        }
    }

    @Test
    public void testObjectMethodsUseReadableMembers() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Map<String, Object> members = new HashMap<>();
            members.put("hashCode", 37);
            members.put("toString", "guest");
            Operations proxy = context.asValue(ProxyObject.fromMap(members)).as(Operations.class);
            assertEquals(37, proxy.hashCode());
            assertNotNull(proxy.toString());
            members.put("hashCode", (ProxyExecutable) arguments -> 41);
            assertEquals(41, proxy.hashCode());
            members.put("equals", (ProxyExecutable) arguments -> true);
            assertTrue(proxy.equals(new Object()));
        }
    }

    @Test
    public void testObjectMethodFallbacksAreIndependent() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value firstValue = context.asValue(ProxyObject.fromMap(Map.of("hashCode", (ProxyExecutable) arguments -> 37)));
            Value secondValue = context.asValue(ProxyObject.fromMap(Map.of("hashCode", (ProxyExecutable) arguments -> 41)));
            Operations first = firstValue.as(Operations.class);
            Operations same = firstValue.as(Operations.class);
            Operations second = secondValue.as(Operations.class);
            for (int i = 0; i < 3; i++) {
                assertTrue(first.equals(same));
                assertFalse(first.equals(second));
                assertEquals(37, first.hashCode());
                assertEquals(41, second.hashCode());
                assertNotNull(first.toString());
                assertNotNull(second.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R raise(Throwable failure) throws E {
        throw (E) failure;
    }

    private static void assertSuppressed(Throwable failure, Class<? extends Throwable> type) {
        assertEquals(1, failure.getSuppressed().length);
        boolean found = false;
        for (Throwable suppressed : failure.getSuppressed()) {
            found |= type.isInstance(suppressed);
        }
        assertTrue(found);
    }

    @Test
    public void testObjectDefaultFailures() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ThrowingDefaults proxy = context.asValue(ProxyObject.fromMap(Map.of("apply", (ProxyExecutable) arguments -> arguments[0].asInt()))).as(ThrowingDefaults.class);
            assertEquals("default", proxy.success());
            UnsupportedOperationException unchecked = assertThrows(UnsupportedOperationException.class, proxy::unchecked);
            assertSuppressed(unchecked, IllegalStateException.class);
            UnsupportedOperationException checked = assertThrows(UnsupportedOperationException.class, proxy::checked);
            assertSuppressed(checked, IOException.class);
            UnsupportedOperationException undeclared = assertThrows(UnsupportedOperationException.class, proxy::undeclared);
            assertSuppressed(undeclared, IOException.class);
            assertThrows(AssertionError.class, proxy::error);
            for (int i = 0; i < 20; i++) {
                UnsupportedOperationException next = assertThrows(UnsupportedOperationException.class, proxy::unchecked);
                assertNotSame(unchecked, next);
                assertSuppressed(next, IllegalStateException.class);
                assertSuppressed(unchecked, IllegalStateException.class);
                unchecked = next;
            }
        }
    }

    @Test
    public void testDefaultMethodFallbackSignal() {
        DefaultMethodFallbackException signal = DefaultMethodFallbackException.INSTANCE;
        assertSame(signal, signal.fillInStackTrace());
        assertEquals(0, signal.getStackTrace().length);
        assertTrue(TruffleStackTrace.getStackTrace(signal).isEmpty());
        assertEquals(0, signal.getSuppressed().length);
        assertSame(null, signal.getCause());
        assertThrows(IllegalStateException.class, () -> signal.initCause(new IOException()));
    }

    @Test
    public void testOrdinaryObjectDefaultFailures() {
        assumeFalse(TruffleOptions.AOT);
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            DefaultMethods proxy = context.asValue(ProxyObject.fromMap(Map.of(
                            "apply", (ProxyExecutable) arguments -> arguments[0].asInt(),
                            "success", (ProxyExecutable) arguments -> "guest"))).as(DefaultMethods.class);
            assertEquals("guest", proxy.success());
            UnsupportedOperationException previous = null;
            for (int i = 0; i < 20; i++) {
                UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, proxy::unchecked);
                assertNotSame(previous, failure);
                assertSuppressed(failure, IllegalStateException.class);
                previous = failure;
            }
            assertSuppressed(assertThrows(UnsupportedOperationException.class, proxy::checked), IOException.class);
            assertSuppressed(assertThrows(UnsupportedOperationException.class, proxy::undeclared), IOException.class);
            assertThrows(AssertionError.class, proxy::error);
        }
    }

    @Test
    public void testObjectDefaultClosesContextThenFails() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ThrowingDefaults proxy = context.asValue(ProxyObject.fromMap(Map.of())).as(ThrowingDefaults.class);
            UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> proxy.closeThenFail(context));
            assertSuppressed(failure, IllegalStateException.class);
        }
    }

    @Test
    public void testFunctionDefaultFailures() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ThrowingDefaults proxy = context.asValue((ProxyExecutable) arguments -> arguments[0].asInt()).as(ThrowingDefaults.class);
            assertEquals("default", proxy.success());
            assertThrows(IllegalStateException.class, proxy::unchecked);
            assertThrows(IOException.class, proxy::checked);
            UndeclaredThrowableException failure = assertThrows(UndeclaredThrowableException.class, proxy::undeclared);
            assertTrue(failure.getUndeclaredThrowable() instanceof IOException);
            assertThrows(AssertionError.class, proxy::error);
        }
    }

    @Test
    public void testInheritedGenericDeclarations() {
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ProxyExecutable get = arguments -> ProxyArray.fromArray(42);
            Value object = context.asValue(ProxyObject.fromMap(Map.of("get", get, "extra", 1)));
            StringFirstObject stringFirst = object.as(StringFirstObject.class);
            assertThrows(ClassCastException.class, () -> stringFirst.get().get(0));
            assertEquals(42, object.as(AnyFirstObject.class).get().get(0));
            StringFirstSam stringSam = object.as(StringFirstSam.class);
            assertThrows(ClassCastException.class, () -> stringSam.get().get(0));
            assertEquals(42, object.as(AnyFirstSam.class).get().get(0));

            Value function = context.asValue(get);
            assertEquals(42, function.as(StringFirstSam.class).get().get(0));
            AnyFirstSam anySam = function.as(AnyFirstSam.class);
            assertThrows(ClassCastException.class, () -> anySam.get().get(0));
        }
    }

    @Test
    public void testGeneratedProxies() {
        try (Context context = Context.create()) {
            Function function = context.asValue((ProxyExecutable) arguments -> arguments[0].asInt() + arguments[1].asInt()).as(Function.class);
            assertEquals(7, function.apply(3, 4));
            assertEquals(function, function);
            assertFalse(function.equals(new Object()));
            assertEquals(function.hashCode(), function.hashCode());
            assertNotNull(function.toString());

            Map<String, Object> members = new HashMap<>();
            members.put("add", (ProxyExecutable) arguments -> arguments[0].asInt() + arguments[1].asInt());
            members.put("name", (ProxyExecutable) arguments -> "generated");
            Operations operations = context.asValue(ProxyObject.fromMap(members)).as(Operations.class);
            assertEquals(7, operations.add(3, 4));
            assertEquals("generated", operations.name());
            assertEquals("generated:7", operations.describe(3, 4));
            assertEquals(operations, operations);
            assertNotNull(operations.toString());
        }
    }
}
