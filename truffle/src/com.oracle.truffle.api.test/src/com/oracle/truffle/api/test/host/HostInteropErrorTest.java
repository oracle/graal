/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.fail;

import java.util.Collections;

import org.graalvm.polyglot.Value;
import org.junit.ComparisonFailure;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class HostInteropErrorTest extends ProxyLanguageEnvTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public static class MyHostObj {
        public int field;
        public final int finalField;

        public MyHostObj(int a) {
            this.finalField = a;
        }

        public void foo(@SuppressWarnings("unused") int a) {
            fail("foo called");
        }

        public void cce(Object human) {
            Value cannotCast = (Value) human;
            cannotCast.invokeMember("hello");
        }

        @Override
        public String toString() {
            return "MyHostObj";
        }
    }

    @Test
    public void testHostMethodArityError() throws InteropException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo"), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
        assertFails(() -> INTEROP.execute(foo), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
    }

    @Test
    public void testHostMethodArgumentTypeError() throws InteropException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Unsupported target type.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.execute(foo, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Unsupported target type.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostConstructorArgumentTypeError() {
        Object hostClass = env.asHostSymbol(MyHostObj.class);

        assertFails(() -> INTEROP.instantiate(hostClass, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.instantiate(hostClass, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.instantiate(hostClass, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.instantiate(hostClass, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostArrayTypeError() {
        Object hostArray = env.asGuestValue(new int[]{0, 0, 0, 0});

        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.writeArrayElement(hostArray, 0, new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFieldTypeError() {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.writeMember(hostObj, "field", new OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Unsupported target type.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", new OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFinalFieldError() {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", 42), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(null)), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(Collections.emptyMap())), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
    }

    @Test
    public void testClassCastExceptionInHostMethod() throws InteropException, ClassNotFoundException {
        Object hostObj = env.asGuestValue(new MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "cce");

        Class<? extends Exception> hostExceptionClass = Class.forName("com.oracle.truffle.polyglot.HostException").asSubclass(Exception.class);

        assertFails(() -> INTEROP.invokeMember(hostObj, "cce", 42), hostExceptionClass, null);
        assertFails(() -> INTEROP.execute(foo, 42), hostExceptionClass, null);
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherObject implements TruffleObject {
        @ExportMessage
        final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        final Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "Other";
        }

        @ExportMessage
        final boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        final Object getMetaObject() {
            return new OtherType();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherNull implements TruffleObject {
        @ExportMessage
        final boolean isNull() {
            return true;
        }

        @ExportMessage
        final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        final Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "null";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    class OtherType implements TruffleObject {
        @ExportMessage
        final boolean isMetaObject() {
            return true;
        }

        @ExportMessage(name = "getMetaQualifiedName")
        @ExportMessage(name = "getMetaSimpleName")
        final Object getMetaName() {
            return "OtherType";
        }

        @ExportMessage
        final boolean isMetaInstance(Object instance) {
            return instance instanceof OtherObject;
        }
    }

    @FunctionalInterface
    interface InteropRunnable {
        void run() throws Exception;
    }

    private static void assertFails(InteropRunnable r, Class<?> hostExceptionType, String message) {
        try {
            r.run();
            fail("No error but expected " + hostExceptionType);
        } catch (Exception e) {
            if (!hostExceptionType.isInstance(e)) {
                fail(String.format("Expected %s: \"%s\" but got %s: \"%s\"", hostExceptionType.getName(), message, e.getClass().getName(), e.getMessage()));
            }
            if (message != null && !message.equals(e.getMessage())) {
                ComparisonFailure f = new ComparisonFailure(e.getMessage(), message, e.getMessage());
                f.initCause(e);
                throw f;
            }
        }
    }
}
