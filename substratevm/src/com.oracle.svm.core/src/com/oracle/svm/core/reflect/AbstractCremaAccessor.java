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

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.jdk.InternalVMMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@InternalVMMethod
abstract class AbstractCremaAccessor {
    static final Object[] NO_ARGS = new Object[0];

    protected final ResolvedJavaMethod targetMethod;
    private final Class<?> declaringClass;
    private final Class<?>[] parameterTypes;

    protected AbstractCremaAccessor(ResolvedJavaMethod targetMethod, Class<?> declaringClass, Class<?>[] parameterTypes) {
        this.targetMethod = targetMethod;
        this.declaringClass = declaringClass;
        this.parameterTypes = parameterTypes;
    }

    protected void ensureDeclaringClassInitialized() {
        EnsureClassInitializedNode.ensureClassInitialized(declaringClass);
    }

    protected void verifyReceiver(Object receiver) {
        assert !targetMethod.isStatic();
        if (receiver == null) {
            String msg = "Cannot invoke " + declaringClass.getName() + "." + targetMethod.getName() + "() because obj is null";
            throw new NullPointerException(msg);
        }
        if (!declaringClass.isAssignableFrom(receiver.getClass())) {
            String msg = "object of type " + receiver.getClass().getName() + " is not an instance of " + declaringClass.getName();
            throw new IllegalArgumentException(msg);
        }
    }

    protected void verifyArguments(Object[] args) {
        if (args.length != parameterTypes.length) {
            String msg = "wrong number of arguments: " + args.length + " expected: " + targetMethod.getSignature().getParameterCount(false);
            throw new IllegalArgumentException(msg);
        }
        for (int i = 0; i < args.length; i++) {
            args[i] = verifyArgument(args[i], parameterTypes[i]);
        }
    }

    private static Object verifyArgument(Object obj, Class<?> type) {
        if (type.isPrimitive()) {
            return checkConversion(obj, type);
        }
        try {
            return type.cast(obj);
        } catch (ClassCastException e) {
            String msg = "argument type mismatch : " + obj.getClass().getName() + " != " + type.getName();
            throw new IllegalArgumentException(msg);
        }
    }

    private static Object checkConversion(Object obj, Class<?> type) {
        if (type == boolean.class) {
            if (obj instanceof Boolean) {
                return obj;
            }
        } else if (type == int.class) {
            if (obj instanceof Integer || obj instanceof Byte || obj instanceof Short) {
                return ((Number) obj).intValue();
            } else if (obj instanceof Character) {
                return (int) (char) obj;
            }
        } else if (type == long.class) {
            if (obj instanceof Long || obj instanceof Integer || obj instanceof Byte || obj instanceof Short) {
                return ((Number) obj).longValue();
            } else if (obj instanceof Character) {
                return (long) (char) obj;
            }
        } else if (type == double.class) {
            if (obj instanceof Double || obj instanceof Float || obj instanceof Long || obj instanceof Integer || obj instanceof Byte || obj instanceof Short) {
                return ((Number) obj).doubleValue();
            } else if (obj instanceof Character) {
                return (double) (char) obj;
            }
        } else if (type == short.class) {
            if (obj instanceof Short || obj instanceof Byte) {
                return ((Number) obj).shortValue();
            }
        } else if (type == float.class) {
            if (obj instanceof Float || obj instanceof Long || obj instanceof Integer || obj instanceof Byte || obj instanceof Short) {
                return ((Number) obj).floatValue();
            } else if (obj instanceof Character) {
                return (float) (char) obj;
            }
        } else if (type == byte.class) {
            if (obj instanceof Byte) {
                return obj;
            }
        } else if (type == char.class) {
            if (obj instanceof Character) {
                return obj;
            }
        }
        String msg = "argument type mismatch";
        throw new IllegalArgumentException(msg);
    }
}
