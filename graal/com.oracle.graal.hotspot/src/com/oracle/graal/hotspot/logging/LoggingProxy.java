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

import java.lang.reflect.*;

/**
 * A java.lang.reflect proxy that hierarchically logs all method invocations along with their
 * parameters and return values.
 */
public class LoggingProxy<T> implements InvocationHandler {

    private T delegate;

    public LoggingProxy(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        int argCount = args == null ? 0 : args.length;
        if (method.getParameterTypes().length != argCount) {
            throw new RuntimeException("wrong parameter count");
        }
        StringBuilder str = new StringBuilder();
        str.append(method.getReturnType().getSimpleName() + " " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(");
        for (int i = 0; i < argCount; i++) {
            str.append(i == 0 ? "" : ", ");
            str.append(Logger.pretty(args[i]));
        }
        str.append(")");
        Logger.startScope(str.toString());
        final Object result;
        try {
            if (args == null) {
                result = method.invoke(delegate);
            } else {
                result = method.invoke(delegate, args);
            }
        } catch (InvocationTargetException e) {
            Logger.endScope(" = Exception " + e.getMessage());
            throw e.getCause();
        }
        Logger.endScope(" = " + Logger.pretty(result));
        return result;
    }

    /**
     * The object returned by this method will implement all interfaces that are implemented by
     * delegate.
     */
    public static <T> T getProxy(Class<T> interf, T delegate) {
        Class<?>[] interfaces = ProxyUtil.getAllInterfaces(delegate.getClass());
        Object obj = Proxy.newProxyInstance(interf.getClassLoader(), interfaces, new LoggingProxy<>(delegate));
        return interf.cast(obj);
    }
}
