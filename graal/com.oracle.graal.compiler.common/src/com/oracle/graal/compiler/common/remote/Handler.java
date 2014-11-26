/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.remote;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class Handler<T> implements InvocationHandler {

    private final T delegate;

    /**
     * Proxies objects may be visible to multiple threads.
     */
    Map<Invocation, Object> cachedInvocations = new ConcurrentHashMap<>();

    public Handler(T delegate) {
        this.delegate = delegate;
    }

    static Object[] unproxify(Object[] args) {
        if (args == null) {
            return args;
        }
        Object[] res = args;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null) {
                if (Proxy.isProxyClass(arg.getClass())) {
                    Handler<?> h = (Handler<?>) Proxy.getInvocationHandler(arg);
                    if (res == args) {
                        res = args.clone();
                    }
                    res[i] = h.delegate;
                } else if (arg instanceof Object[]) {
                    Object[] arrayArg = (Object[]) arg;
                    arrayArg = unproxify(arrayArg);
                    if (arrayArg != arg) {
                        if (res == args) {
                            res = args.clone();
                        }
                        res[i] = arrayArg;
                    }
                }
            }
        }
        return res;
    }

    private static boolean isCacheable(Method method) {
        // TODO: use annotations for finer control of what should be cached
        return method.getReturnType() != Void.TYPE;
    }

    private static final Object NULL_RESULT = new Object();

    @Override
    public Object invoke(Object proxy, Method method, Object[] a) throws Throwable {
        Object[] args = unproxify(a);
        boolean isCacheable = isCacheable(method);
        Invocation invocation = new Invocation(method, delegate, args);
        if (isCacheable) {
            Object result = cachedInvocations.get(invocation);
            if (result != null) {
                if (result == NULL_RESULT) {
                    result = null;
                }
                // System.out.println(invocation + ": " + result);
                return result;
            }
        } else {
            // System.out.println("not cacheable: " + method);
        }

        Context context = Context.getCurrent();
        assert context != null;
        try {
            assert context.activeInvocations >= 0;
            context.activeInvocations++;
            Object result = invocation.invoke();
            result = context.get(result);
            if (isCacheable) {
                context.invocationCacheHits--;
                cachedInvocations.put(invocation, result == null ? NULL_RESULT : result);
            }
            return result;
        } finally {
            assert context.activeInvocations > 0;
            context.activeInvocations--;
        }
    }
}
