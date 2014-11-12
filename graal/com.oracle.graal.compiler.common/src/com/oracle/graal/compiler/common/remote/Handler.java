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

public class Handler<T> implements InvocationHandler {

    private final T delegate;
    private final Context context;

    Map<Invocation, Object> cachedInvocations = new HashMap<>();

    public Handler(T delegate, Context context) {
        this.delegate = delegate;
        this.context = context;
    }

    public T getDelegate() {
        return delegate;
    }

    static Object unproxifyObject(Object obj) {
        if (obj != null && Proxy.isProxyClass(obj.getClass())) {
            Handler<?> h = (Handler<?>) Proxy.getInvocationHandler(obj);
            return h.delegate;
        }
        return obj;
    }

    static Object[] unproxify(Object[] args) {
        if (args == null) {
            return args;
        }
        Object[] res = args;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg != null && Proxy.isProxyClass(arg.getClass())) {
                Handler<?> h = (Handler<?>) Proxy.getInvocationHandler(arg);
                if (res == args) {
                    res = args.clone();
                }
                res[i] = h.delegate;
            }
        }
        return res;
    }

    private static boolean isCacheable(Method method) {
        // TODO: use annotations for finer control of what should be cached
        return method.getReturnType() != Void.TYPE;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] a) throws Throwable {
        Object[] args = unproxify(a);
        boolean isCacheable = isCacheable(method);
        Invocation invocation = new Invocation(method, delegate, args);
        if (isCacheable) {
            if (cachedInvocations.containsKey(invocation)) {
                Object result = cachedInvocations.get(invocation);
                // System.out.println(invocation + ": " + result);
                return result;
            }
        } else {
            // System.out.println("not cacheable: " + method);
        }

        Object result = invocation.invoke();
        result = context.get(result);
        if (isCacheable) {
            cachedInvocations.put(invocation, result);
        }
        return result;
    }
}
