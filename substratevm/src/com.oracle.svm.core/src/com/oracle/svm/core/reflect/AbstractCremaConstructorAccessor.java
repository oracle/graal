/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.espresso.shared.resolver.CallKind;

import jdk.internal.reflect.ConstructorAccessor;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Shared Crema constructor accessor support for ordinary construction and serialization
 * construction.
 */
abstract class AbstractCremaConstructorAccessor extends AbstractCremaAccessor implements ConstructorAccessor {
    private final boolean instantiationError;

    /**
     * Creates a constructor accessor with the reflected signature used for argument validation.
     */
    protected AbstractCremaConstructorAccessor(ResolvedJavaMethod targetMethod, Class<?> declaringClass, Class<?>[] parameterTypes, boolean instantiationError) {
        super(targetMethod, declaringClass, parameterTypes);
        this.instantiationError = instantiationError;
    }

    /**
     * Returns the concrete class to allocate before invoking the constructor body.
     */
    protected abstract Class<?> getInstantiatedClass();

    /**
     * Returns the constructor body that Crema executes against the newly allocated instance.
     */
    protected ResolvedJavaMethod getTargetConstructor() {
        return targetMethod;
    }

    /**
     * Allocates the target instance, validates and prepends constructor arguments, and invokes the
     * selected constructor body through Crema.
     */
    @Override
    public Object newInstance(Object[] initialArguments) throws InstantiationException, InvocationTargetException {
        Object[] args = initialArguments == null ? NO_ARGS : initialArguments;
        verifyArguments(args);
        Class<?> instantiatedClass = getInstantiatedClass();
        if (instantiationError) {
            throw new InstantiationException(instantiatedClass.getName());
        }
        EnsureClassInitializedNode.ensureClassInitialized(instantiatedClass);

        Object newReference = CremaSupport.singleton().allocateInstance(DynamicHub.fromClass(instantiatedClass).getInterpreterType());
        Object[] finalArgs = new Object[args.length + 1];
        finalArgs[0] = newReference;
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        try {
            CremaSupport.singleton().execute(getTargetConstructor(), finalArgs, CallKind.DIRECT);
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
        return newReference;
    }
}
