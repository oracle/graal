/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class ContextThreadLocalTest {

    private static final String CLASS_NAME = "com.oracle.truffle.polyglot.ContextThreadLocal";
    private static final String CONTEXT_CLASS = "com.oracle.truffle.polyglot.PolyglotContextImpl";

    @Before
    public void setup() {
        MultiThreadedLanguage.isThreadAccessAllowed = (req) -> true;
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Object> createContextThreadLocal() {
        try {
            return (ThreadLocal<Object>) createDefaultInstance(CLASS_NAME);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object createDefaultInstance(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object createContext() {
        return createDefaultInstance(CONTEXT_CLASS);
    }

    Object setReturnParent(ThreadLocal<Object> tl, Object value) {
        try {
            Method method = tl.getClass().getDeclaredMethod("setReturnParent", Object.class);
            method.setAccessible(true);
            return method.invoke(tl, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testSingleThread() {
        ThreadLocal<Object> tl = createContextThreadLocal();
        Object context1 = createContext();
        Object context2 = createContext();

        testEnterLeave(tl, context1, context2);
        testEnterLeave(tl, context1, context1);
        testEnterLeave(tl, context2, context1);
        testEnterLeave(tl, context2, null);
        testEnterLeave(tl, null, context1);
    }

    private void testEnterLeave(ThreadLocal<Object> tl, Object context1, Object context2) {
        assertNull(setReturnParent(tl, context1));
        assertSame(context1, tl.get());
        assertSame(context1, tl.get());
        assertSame(context1, setReturnParent(tl, null));
        assertNull(tl.get());

        assertNull(setReturnParent(tl, context1));

        assertSame(context1, setReturnParent(tl, context2));
        assertSame(context2, tl.get());

        assertSame(context2, setReturnParent(tl, context1));
        assertSame(context1, tl.get());
        assertSame(context1, setReturnParent(tl, null));
        assertSame(null, tl.get());
    }

    @Test
    public void testMultipleThreads() throws InterruptedException, ExecutionException {

        // should be still constant mode
        ExecutorService t1 = Executors.newFixedThreadPool(1);
        ExecutorService t2 = Executors.newFixedThreadPool(1);

        for (int i = 0; i < 1000; i++) {
            ThreadLocal<Object> tl = createContextThreadLocal();

            Object context1 = createContext();
            Object context2 = createContext();
            Future<?> t1result = t1.submit(() -> {
                testEnterLeave(tl, context1, context2);
                testEnterLeave(tl, context1, context1);
                testEnterLeave(tl, context2, context1);
                testEnterLeave(tl, context2, null);
                testEnterLeave(tl, null, context1);
            });
            Future<?> t2result = t2.submit(() -> {
                testEnterLeave(tl, null, context1);
                testEnterLeave(tl, context2, null);
                testEnterLeave(tl, context2, context1);
                testEnterLeave(tl, context1, context1);
                testEnterLeave(tl, context1, context2);
            });
            t1result.get();
            t2result.get();
        }
    }

    @Test
    public void testCompilation() {
        ThreadLocal<Object> tl = createContextThreadLocal();

        Object context1 = createContext();

        CallTarget compiled = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object result = tl.get();
                return result;
            }
        });

        tl.set(context1);
        for (int i = 0; i < 10000; i++) {
            compiled.call();
        }
    }
}
