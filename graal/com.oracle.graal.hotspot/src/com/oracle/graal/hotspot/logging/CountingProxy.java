/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.logging;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.debug.*;

/**
 * A java.lang.reflect proxy that hierarchically logs all method invocations along with their
 * parameters and return values.
 */
public class CountingProxy<T> implements InvocationHandler {

    public static final boolean ENABLED = Boolean.valueOf(System.getProperty("graal.countcalls"));

    private T delegate;

    private ConcurrentHashMap<Method, AtomicLong> calls = new ConcurrentHashMap<>();

    public CountingProxy(T delegate) {
        assert ENABLED;
        TTY.println("Counting proxy for " + delegate.getClass().getSimpleName() + " created");
        this.delegate = delegate;
        proxies.add(this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        int argCount = args == null ? 0 : args.length;
        if (method.getParameterTypes().length != argCount) {
            throw new RuntimeException("wrong parameter count");
        }
        final Object result;
        if (!calls.containsKey(method)) {
            calls.putIfAbsent(method, new AtomicLong(0));
        }
        AtomicLong count = calls.get(method);
        count.incrementAndGet();
        try {
            if (args == null) {
                result = method.invoke(delegate);
            } else {
                result = method.invoke(delegate, args);
            }
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return result;
    }

    public static <T> T getProxy(Class<T> interf, T delegate) {
        Class<?>[] interfaces = ProxyUtil.getAllInterfaces(delegate.getClass());
        Object obj = Proxy.newProxyInstance(interf.getClassLoader(), interfaces, new CountingProxy<>(delegate));
        return interf.cast(obj);
    }

    private static ArrayList<CountingProxy<?>> proxies = new ArrayList<>();

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    for (CountingProxy<?> proxy : proxies) {
                        proxy.print();
                    }
                }
            });
        }
    }

    protected void print() {
        long sum = 0;
        PrintStream out = System.out;
        for (Map.Entry<Method, AtomicLong> entry : calls.entrySet()) {
            Method method = entry.getKey();
            long count = entry.getValue().get();
            sum += count;
            out.println(delegate.getClass().getSimpleName() + "." + method.getName() + ": " + count);
        }
        out.println(delegate.getClass().getSimpleName() + " calls: " + sum);
    }
}
