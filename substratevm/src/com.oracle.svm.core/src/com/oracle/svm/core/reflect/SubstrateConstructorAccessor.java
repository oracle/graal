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
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder.MethodInvokeFunctionPointer;

import jdk.internal.reflect.ConstructorAccessor;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@InternalVMMethod
public final class SubstrateConstructorAccessor extends SubstrateAccessor implements ConstructorAccessor {

    private final CFunctionPointer factoryMethodTarget;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final ResolvedJavaMethod factoryMethod;

    public SubstrateConstructorAccessor(Executable member, CFunctionPointer expandSignature, CFunctionPointer directTarget, ResolvedJavaMethod targetMethod, CFunctionPointer factoryMethodTarget,
                    ResolvedJavaMethod factoryMethod, DynamicHub initializeBeforeInvoke) {
        super(member, expandSignature, directTarget, targetMethod, initializeBeforeInvoke);
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
        return ((MethodInvokeFunctionPointer) expandSignature).invoke(null, args, factoryMethodTarget);
    }

    @Override
    public Object invokeSpecial(Object obj, Object[] args) {
        if (initializeBeforeInvoke != null) {
            EnsureClassInitializedNode.ensureClassInitialized(DynamicHub.toClass(initializeBeforeInvoke));
        }
        return super.invokeSpecial(obj, args);
    }
}
