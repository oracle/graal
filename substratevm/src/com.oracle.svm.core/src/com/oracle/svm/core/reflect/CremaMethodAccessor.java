/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.util.VMError;

import jdk.internal.reflect.MethodAccessor;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@InternalVMMethod
public final class CremaMethodAccessor extends AbstractCremaAccessor implements MethodAccessor {

    public CremaMethodAccessor(ResolvedJavaMethod targetMethod, Class<?> declaringClass, Class<?>[] parameterTypes) {
        super(targetMethod, declaringClass, parameterTypes);
    }

    @Override
    public Object invoke(Object obj, Object[] initialArguments) throws IllegalArgumentException, InvocationTargetException {
        Object[] args = initialArguments == null ? NO_ARGS : initialArguments;
        if (targetMethod.isStatic()) {
            verifyArguments(args);
            ensureDeclaringClassInitialized();
        } else {
            verifyReceiver(obj);
            verifyArguments(args);
        }

        Object[] finalArgs;
        if (targetMethod.isStatic()) {
            finalArgs = args;
        } else {
            finalArgs = new Object[args.length + 1];
            finalArgs[0] = obj;
            System.arraycopy(args, 0, finalArgs, 1, args.length);
        }
        try {
            return CremaSupport.singleton().execute(targetMethod, finalArgs, !targetMethod.isStatic());
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

    @Override
    public Object invoke(Object obj, Object[] args, Class<?> caller) throws IllegalArgumentException, InvocationTargetException {
        // (GR-68603) - handle caller sensitive methods
        throw VMError.unimplemented("CremaMethodAccessor#invoke");
    }
}
