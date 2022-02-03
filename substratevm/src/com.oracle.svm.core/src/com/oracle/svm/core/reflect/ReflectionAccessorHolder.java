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

import java.lang.reflect.InvocationTargetException;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor.MethodInvokeFunctionPointer;
import com.oracle.svm.core.util.VMError;

/**
 * This class is used as the declaring class for reflection invocation methods. These methods have
 * manually created Graal IR, and are invoked via a function pointer call from
 * {@link SubstrateMethodAccessor} and {@link SubstrateConstructorAccessor}
 */
@InternalVMMethod
@SuppressWarnings("unused")
public final class ReflectionAccessorHolder {

    /**
     * Signature prototype for invoking a method via a {@link SubstrateMethodAccessor}. Must match
     * the signature of {@link MethodInvokeFunctionPointer#invoke}
     */
    private static Object invokePrototype(boolean invokeSpecial, Object obj, Object[] args) {
        throw VMError.shouldNotReachHere("Only used as a prototype for generated methods");
    }

    /*
     * Methods for throwing exceptions when a method or constructor is used in an illegal way. These
     * methods are invoked via function pointers, so must have the same signature as the prototype
     * above.
     */

    private static void methodHandleInvokeError(boolean invokeSpecial, Object obj, Object[] args) throws InvocationTargetException {
        /* The nested exceptions are required by the specification. */
        throw new InvocationTargetException(new UnsupportedOperationException("MethodHandle.invoke() and MethodHandle.invokeExact() cannot be invoked through reflection"));
    }

    private static Object newInstanceError(boolean invokeSpecial, Object obj, Object[] args) throws InstantiationException {
        throw new InstantiationException("Only non-abstract instance classes can be instantiated using reflection");
    }

    /*
     * Methods for throwing exceptions from within the generated Graal IR. The signature depends on
     * the call site, i.e., it does not need to be the prototype signature.
     */

    @NeverInline("Exception slow path")
    private static void throwIllegalArgumentExceptionWithReceiver(Object member, Object obj, Object[] args) {
        throwIllegalArgumentException(member, true, obj, args);
    }

    @NeverInline("Exception slow path")
    private static void throwIllegalArgumentExceptionWithoutReceiver(Object member, Object[] args) {
        throwIllegalArgumentException(member, false, null, args);
    }

    /**
     * We do not know which check in the generated method caused the exception, so we cannot print
     * detailed information about that. But printing the signature of the method and all the types
     * of the actual arguments should make it obvious what the problem is.
     */
    private static void throwIllegalArgumentException(Object member, boolean withReceiver, Object obj, Object[] args) {
        String sep = System.lineSeparator();
        StringBuilder msg = new StringBuilder();
        msg.append("Illegal arguments for invoking ").append(member);
        if (withReceiver) {
            msg.append(sep).append("  obj: ").append(obj == null ? "null" : obj.getClass().getTypeName());
        }
        if (args == null) {
            msg.append(sep).append("  args: null");
        } else {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                msg.append(sep).append("  args[").append(i).append("]: ").append(arg == null ? "null" : arg.getClass().getTypeName());
            }
        }
        throw new IllegalArgumentException(msg.toString());
    }
}
