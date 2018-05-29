/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Proxy;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public final class AccessorComputer implements RecomputeFieldValue.CustomFieldValueComputer {

    private static final Field hField;
    private static final InvocationHandler invocationHandler;

    static {
        try {
            hField = Proxy.class.getDeclaredField("h");
            hField.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
        invocationHandler = (proxy, method, args) -> {
            if (method.getName().equals("toString")) {
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            }

            if (method.getName().equals("hashCode") || method.getName().equals("equals") || method.getName().equals("invoke")) {
                throw VMError.shouldNotReachHere("You should not call " + method + " on an instance of " + proxy.getClass() + " during image building.");
            }

            throw VMError.shouldNotReachHere("Unknown method " + method + " called on an instance of " + proxy.getClass() + ".");
        };
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        Member member = (Member) receiver;
        ReflectionSubstitution subst = ImageSingletons.lookup(ReflectionSubstitution.class);
        Class<?> proxyClass = subst.getProxyClass(member);
        if (proxyClass == null) {
            // should never happen, but better check for it here than segfault later
            throw VMError.shouldNotReachHere();
        }
        try {
            Proxy proxyInstance = (Proxy) UnsafeAccess.UNSAFE.allocateInstance(proxyClass);
            /*
             * Set a default invocation handler for the proxy instance. In the generated image the
             * "h" field is deleted anyway (see Target_java_lang_reflect_Proxy) and the methods
             * (invoke, toString, hashCode, equals) that would use the handler are substituted (see
             * ReflectionSubstitutionType). However the handler is needed when the proxy instance is
             * used during image building, e.g., toString() is called on it for error reporting (see
             * NativeImageHeap.addObjectToBootImageHeap()).
             */
            hField.set(proxyInstance, invocationHandler);
            return proxyInstance;

        } catch (InstantiationException | IllegalAccessException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
