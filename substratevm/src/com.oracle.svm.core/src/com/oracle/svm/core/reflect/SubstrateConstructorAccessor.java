/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Executable;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder.MethodInvokeFunctionPointer;

import jdk.internal.reflect.ConstructorAccessor;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class SubstrateConstructorAccessor extends SubstrateAccessor implements ConstructorAccessor {

    private final Class<?> declaringClass;
    private final MethodRef factoryMethodTarget;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final ResolvedJavaMethod factoryMethod;

    public SubstrateConstructorAccessor(Executable member, MethodRef expandSignature, MethodRef directTarget, ResolvedJavaMethod targetMethod, MethodRef factoryMethodTarget,
                    ResolvedJavaMethod factoryMethod, DynamicHub initializeBeforeInvoke) {
        super(member, expandSignature, directTarget, targetMethod, initializeBeforeInvoke);
        Class<?> constructorDeclaringClass = member.getDeclaringClass();
        this.declaringClass = constructorDeclaringClass;
        this.factoryMethodTarget = factoryMethodTarget;
        this.factoryMethod = factoryMethod;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaMethod getFactoryMethod() {
        return factoryMethod;
    }

    @Override
    public Object newInstance(Object[] args) {
        if (initializeBeforeInvoke != null) {
            EnsureClassInitializedNode.ensureClassInitialized(DynamicHub.toClass(initializeBeforeInvoke));
        }
        return ((MethodInvokeFunctionPointer) getExpandSignature()).invoke(null, args, getCodePointer(factoryMethodTarget));
    }

    /**
     * This variant of {@link #newInstance(Object[])} is considered @Hidden by
     * {@link com.oracle.svm.core.jdk.StackTraceUtils#shouldShowFrame(Class, String, boolean, boolean)}.
     * This is important when this is called as part of the method handle implementation where this
     * frame is not expected to appear.
     *
     * When @Hidden becomes available per-method (GR-76134) we should use that annotation instead.
     */
    public Object methodHandleNewInstance(Object[] args) {
        if (initializeBeforeInvoke != null) {
            EnsureClassInitializedNode.ensureClassInitialized(DynamicHub.toClass(initializeBeforeInvoke));
        }
        return ((MethodInvokeFunctionPointer) getExpandSignature()).invoke(null, args, getCodePointer(factoryMethodTarget));
    }

    public static void checkReceiver(Class<?> declaringClass, Object obj) {
        if (obj == null) {
            throw new NullPointerException();
        } else if (!declaringClass.isInstance(obj)) {
            throw new IllegalArgumentException("Receiver type " + obj.getClass().getName() + " is not an instance of the constructor's declaring class " + declaringClass.getName());
        }
    }

    @Override
    public Object methodHandleInvokeSpecial(Object obj, Object[] args) {
        if (initializeBeforeInvoke != null) {
            EnsureClassInitializedNode.ensureClassInitialized(DynamicHub.toClass(initializeBeforeInvoke));
        }
        checkReceiver(declaringClass, obj);
        return super.methodHandleInvokeSpecial(obj, args);
    }
}
