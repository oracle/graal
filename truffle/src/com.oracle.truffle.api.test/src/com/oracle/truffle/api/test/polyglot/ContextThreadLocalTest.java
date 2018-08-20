/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
