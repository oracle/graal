/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class JavaWrapper implements InvocationHandler {
    private final PolyglotEngine.Value value;
    private final Object wrapper;
    private final InvocationHandler chain;

    public JavaWrapper(PolyglotEngine.Value value, Object wrapper, InvocationHandler chain) {
        this.value = value;
        this.chain = chain;
        this.wrapper = wrapper;
    }

    static <T> T create(Class<T> representation, Object wrapper, PolyglotEngine.Value value) {
        InvocationHandler chain = Proxy.getInvocationHandler(wrapper);
        Object instance = Proxy.newProxyInstance(representation.getClassLoader(), new Class<?>[]{representation}, new JavaWrapper(value, wrapper, chain));
        return representation.cast(instance);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        PolyglotEngine.Value retValue = value.invokeProxy(chain, wrapper, method, args);
        if (method.getReturnType() == void.class) {
            return null;
        } else {
            Object realValue = retValue.get();
            if (realValue == null) {
                return null;
            }
            if (Proxy.isProxyClass(realValue.getClass())) {
                if (Proxy.getInvocationHandler(realValue) instanceof JavaWrapper) {
                    return realValue;
                }
                final Class<?> type = method.getReturnType();
                return create(type, realValue, retValue);
            }
            return realValue;
        }
    }

}
