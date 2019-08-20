/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.helpers;

//Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

public class ReflectionProxyHelper {
    private static final Field hField = ReflectionUtil.lookupField(Proxy.class, "h");

    private static Object toStringInvocationHandler(Object proxy, Method method, @SuppressWarnings("unused") Object[] args) {
        if (method.getName().equals("toString")) {
            return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
        }

        if (method.getName().equals("hashCode") || method.getName().equals("equals") || method.getName().equals("invoke")) {
            throw VMError.shouldNotReachHere("You should not call " + method + " on an instance of " + proxy.getClass() + " during image building.");
        }

        throw VMError.shouldNotReachHere("Unknown method " + method + " called on an instance of " + proxy.getClass() + ".");
    }

    /**
     * Set a default invocation handler for a ReflectionProxy proxy instance. At runtime the "h"
     * field is not used as the methods (invoke, toString, hashCode, equals) that would use the
     * handler are substituted (see ReflectionSubstitutionType). However the handler is needed when
     * the proxy instance is used during image building, e.g., toString() is called on it for error
     * reporting (see NativeImageHeap.addObjectToBootImageHeap()).
     */
    public static void setDefaultInvocationHandler(Proxy proxyInstance) {
        VMError.guarantee(ReflectionProxy.class.isAssignableFrom(proxyInstance.getClass()));
        try {
            hField.set(proxyInstance, (InvocationHandler) ReflectionProxyHelper::toStringInvocationHandler);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
