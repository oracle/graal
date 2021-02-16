/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

@RunWith(Parameterized.class)
public class ProxyTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @GenerateLibrary
    public abstract static class ProxyTestLibrary extends Library {

        public abstract void decoratedVoid(Object receiver);

        public abstract int incrementMethod(Object receiver, int primitive);

    }

    @ExportLibrary(ProxyTestLibrary.class)
    static class Proxied {

        @ExportMessage
        void decoratedVoid() {
        }

        @ExportMessage
        int incrementMethod(int primitive) {
            return primitive;
        }

    }

    @ExportLibrary(ReflectionLibrary.class)
    static class Proxy {

        final Object delegate;

        Proxy(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        Object send(Message message, Object... arguments) {
            try {
                ReflectionLibrary reflect = ReflectionLibrary.getFactory().getUncached(delegate);
                if (!"incrementMethod".equals(message.getSimpleName())) {
                    return reflect.send(delegate, message, arguments);
                } else {
                    arguments[0] = (int) arguments[0] + 1;
                    int result = (int) reflect.send(delegate, message, arguments);
                    return result + 1;
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Test
    public void testForwarding() {
        Proxied proxied = new Proxied();
        Proxy proxy = new Proxy(proxied);

        ProxyTestLibrary lib = createLibrary(ProxyTestLibrary.class, proxy);

        lib.decoratedVoid(proxy);
        Assert.assertEquals(3, lib.incrementMethod(proxy, 1));

        lib = createLibrary(ProxyTestLibrary.class, proxied);
        Assert.assertEquals(1, lib.incrementMethod(proxied, 1));
    }

}
