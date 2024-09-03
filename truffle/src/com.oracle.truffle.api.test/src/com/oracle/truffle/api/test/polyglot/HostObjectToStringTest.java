/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Test;

/**
 * Verifies that {@code toDisplayString} respects {@link HostAccess} config for
 * {@link Object#toString() toString()}.
 */
public class HostObjectToStringTest extends AbstractHostAccessTest {

    private static final String OK_IF_ALL = "ok if all access is allowed";
    private static final String OK_IF_EXPLICIT = "ok if explicitly exported";

    public static class EvilToString {
        @Override
        public String toString() {
            return OK_IF_ALL;
        }
    }

    public static class GoodToString {
        @HostAccess.Export
        @Override
        public String toString() {
            return OK_IF_EXPLICIT;
        }
    }

    @Test
    public void noExplicitToStringAccess() {
        setupEnv(HostAccess.ALL);
        assertEquals(OK_IF_ALL, context.asValue(new EvilToString()).toString());

        setupEnv(HostAccess.EXPLICIT);
        assertEquals(EvilToString.class.getName(), context.asValue(new EvilToString()).toString());

        setupEnv(HostAccess.NONE);
        assertEquals(EvilToString.class.getName(), context.asValue(new EvilToString()).toString());
    }

    @Test
    public void explicitToStringAccess() {
        setupEnv(HostAccess.ALL);
        assertEquals(OK_IF_EXPLICIT, context.asValue(new GoodToString()).toString());

        setupEnv(HostAccess.EXPLICIT);
        assertEquals(OK_IF_EXPLICIT, context.asValue(new GoodToString()).toString());

        setupEnv(HostAccess.NONE);
        assertEquals(GoodToString.class.getName(), context.asValue(new GoodToString()).toString());
    }

    @Test
    public void lambdaToString() {
        // HotSpot lambda classes have names like "pkg.HostClass$$Lambda$69/0x<hex-id>".
        // On SVM, they are named "pkg.HostClass$$Lambda$/0x<unique-hex-digest>", which we preserve.
        final Pattern allowedClassNamePattern = Pattern.compile("^[A-Za-z.$\\d]+$");
        final Supplier<String> supplier = () -> "ignored";

        setupEnv(HostAccess.EXPLICIT);
        Value value = context.asValue(supplier);
        String string = value.toString();
        assertTrue(string, allowedClassNamePattern.matcher(string).matches());
    }

    @Test
    public void proxyToString() {
        // Proxy classes have names like "jdk.proxy2.$Proxy69"
        final Pattern allowedClassNamePattern = Pattern.compile("^[A-Za-z.$\\d]+$");
        final Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{Callable.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
                return method.invoke(obj, args);
            }
        });

        setupEnv(HostAccess.EXPLICIT);
        Value value = context.asValue(proxy);
        String string = value.toString();
        assertTrue(string, allowedClassNamePattern.matcher(string).matches());
    }

    /**
     * Java primitive types can always safely be converted to string without side effects.
     */
    @Test
    public void hostPrimitiveToString() {
        setupEnv(HostAccess.EXPLICIT);
        Object[] values = {
                        42,
                        null,
                        true,
                        false,
                        13.37d,
                        13.37f,
                        Double.MAX_VALUE,
                        Float.MAX_VALUE,
                        Double.NEGATIVE_INFINITY,
                        Float.NEGATIVE_INFINITY,
                        Double.NaN,
                        Float.NaN,
                        "sTrInG",
                        "",
                        'H',
        };
        for (Object value : values) {
            assertEquals(String.valueOf(value), context.asValue(value).toString());
        }
    }
}
