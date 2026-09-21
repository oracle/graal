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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.espresso.shared.meta.MethodAccess;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.shared.util.VMError;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.MethodAccessor;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Crema method accessor used for caller-sensitive method that don't have a caller-sensitive-adapter.
 */
public final class FallbackCallerSensitiveCremaMethodAccessor extends AbstractCremaAccessor implements MethodAccessor {
    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();
    private static final MethodHandle DO_INVOKE = findDoInvoke();
    private static final MethodHandle INSERT_RECEIVER = findInsertReceiver();

    private MethodHandle invoker;

    public FallbackCallerSensitiveCremaMethodAccessor(ResolvedJavaMethod targetMethod, Class<?> declaringClass, Class<?>[] parameterTypes) {
        super(targetMethod, declaringClass, parameterTypes);
    }

    @Override
    public Object invoke(Object obj, Object[] initialArguments) throws IllegalArgumentException, InvocationTargetException {
        throw VMError.shouldNotReachHere("Cannot invoke caller sensitive method without an explicit caller");
    }

    @Override
    public Object invoke(Object obj, Object[] initialArguments, Class<?> caller) throws IllegalArgumentException, InvocationTargetException {
        Object[] args = initialArguments == null ? NO_ARGS : initialArguments;
        if (targetMethod.isStatic()) {
            verifyArguments(args);
            ensureDeclaringClassInitialized();
        } else {
            verifyReceiver(obj);
            verifyArguments(args);
        }
        try {
            return JLIA.reflectiveInvoker(caller).invokeExact(getInvoker(), obj, args);
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

    @SuppressWarnings("unused")
    private static Object doInvoke(ResolvedJavaMethod targetMethod, CallKind callKind, Object[] finalArgs) {
        return CremaSupport.singleton().execute(targetMethod, finalArgs, callKind);
    }

    @SuppressWarnings("unused")
    private static Object[] insertReceiver(Object obj, Object[] args) {
        Object[] finalArgs = new Object[args.length + 1];
        finalArgs[0] = obj;
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        return finalArgs;
    }

    private MethodHandle getInvoker() {
        // benign race
        if (invoker == null) {
            // (ResolvedJavaMethod, CallKind, Object[]) -> Object
            MethodHandle mh = DO_INVOKE;
            // (Object[]) -> Object
            mh = MethodHandles.insertArguments(mh, 0, targetMethod, CallKind.getCallKind((MethodAccess<?, ?, ?>) targetMethod));
            if (targetMethod.isStatic()) {
                // (Object, Object[]) -> Object
                mh = MethodHandles.dropArguments(mh, 0, Object.class);
            } else {
                // (Object, Object[]) -> Object
                mh = MethodHandles.collectArguments(mh, 0, INSERT_RECEIVER);
            }
            invoker = mh;
        }
        return invoker;
    }

    private static MethodHandle findDoInvoke() {
        try {
            return MethodHandles.lookup().findStatic(FallbackCallerSensitiveCremaMethodAccessor.class, "doInvoke", MethodType.methodType(Object.class, ResolvedJavaMethod.class, CallKind.class,
                            Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static MethodHandle findInsertReceiver() {
        try {
            return MethodHandles.lookup().findStatic(FallbackCallerSensitiveCremaMethodAccessor.class, "insertReceiver", MethodType.methodType(Object[].class, Object.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
