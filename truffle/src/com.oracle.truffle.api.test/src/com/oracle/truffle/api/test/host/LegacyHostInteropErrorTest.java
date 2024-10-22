/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import static com.oracle.truffle.api.test.host.HostInteropErrorTest.assertFails;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import java.util.Collections;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class LegacyHostInteropErrorTest extends ProxyLanguageEnvTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void testHostMethodArityError() throws InteropException {
        Object hostObj = env.asGuestValue(new HostInteropErrorTest.MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo"), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
        assertFails(() -> INTEROP.execute(foo), ArityException.class,
                        "Arity error - expected: 1 actual: 0");
    }

    @Test
    public void testHostMethodArgumentTypeError() throws InteropException {
        Object hostObj = env.asGuestValue(new HostInteropErrorTest.MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "foo");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new HostInteropErrorTest.OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.execute(foo, new HostInteropErrorTest.OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");

        assertFails(() -> INTEROP.invokeMember(hostObj, "foo", new HostInteropErrorTest.OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
        assertFails(() -> INTEROP.execute(foo, new HostInteropErrorTest.OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFieldTypeError() {
        Object hostObj = env.asGuestValue(new HostInteropErrorTest.MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(Collections.emptyMap())), UnsupportedTypeException.class,
                        "Cannot convert '{}'(language: Java, type: java.util.Collections$EmptyMap) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", env.asGuestValue(null)), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: Java) to Java type 'int'.");

        assertFails(() -> INTEROP.writeMember(hostObj, "field", new HostInteropErrorTest.OtherObject()), UnsupportedTypeException.class,
                        "Cannot convert 'Other'(language: proxyLanguage, type: OtherType) to Java type 'int': Invalid or lossy primitive coercion.");
        assertFails(() -> INTEROP.writeMember(hostObj, "field", new HostInteropErrorTest.OtherNull()), UnsupportedTypeException.class,
                        "Cannot convert null value 'null'(language: proxyLanguage, type: Unknown) to Java type 'int'.");
    }

    @Test
    public void testHostFinalFieldError() {
        Object hostObj = env.asGuestValue(new HostInteropErrorTest.MyHostObj(42));

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", 42), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");

        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(null)), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
        assertFails(() -> INTEROP.writeMember(hostObj, "finalField", env.asGuestValue(Collections.emptyMap())), UnknownIdentifierException.class,
                        "Unknown identifier: finalField");
    }

    @Test
    public void testClassCastExceptionInHostMethod() throws InteropException {
        Object hostObj = env.asGuestValue(new HostInteropErrorTest.MyHostObj(42));

        Object foo = INTEROP.readMember(hostObj, "cce");

        AbstractPolyglotTest.assertFails(() -> INTEROP.invokeMember(hostObj, "cce", 42), RuntimeException.class, (e) -> {
            assertTrue(env.isHostException(e));
        });
        AbstractPolyglotTest.assertFails(() -> INTEROP.execute(foo, 42), RuntimeException.class, (e) -> {
            assertTrue(env.isHostException(e));
        });
    }

}
