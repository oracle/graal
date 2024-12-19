/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Unresolved signature that doesn't contain any resolved type references, except for primitive
 * types. Primitive types are always and can only be resolved types.
 */
public final class InterpreterUnresolvedSignature implements Signature {

    private final JavaType returnType;
    private final JavaType[] parameterTypes;

    @Platforms(Platform.HOSTED_ONLY.class) private Signature originalSignature;

    private static boolean primitiveOrUnresolved(JavaType... types) {
        for (JavaType type : types) {
            if (!(type instanceof InterpreterResolvedPrimitiveType || type instanceof UnresolvedJavaType)) {
                return false;
            }
        }
        return true;
    }

    private InterpreterUnresolvedSignature(JavaType returnType, JavaType[] parameterTypes) {
        assert primitiveOrUnresolved(returnType);
        assert parameterTypes.getClass() == JavaType[].class;
        assert primitiveOrUnresolved(parameterTypes);
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterUnresolvedSignature(Signature originalSignature, JavaType returnType, JavaType[] parameterTypes) {
        this(returnType, parameterTypes);
        this.originalSignature = originalSignature;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @VisibleForSerialization
    public static InterpreterUnresolvedSignature create(Signature originalSignature, JavaType returnType, JavaType[] parameterTypes) {
        return new InterpreterUnresolvedSignature(originalSignature, returnType, parameterTypes);
    }

    @VisibleForSerialization
    public static InterpreterUnresolvedSignature create(JavaType returnType, JavaType[] parameterTypes) {
        return new InterpreterUnresolvedSignature(returnType, parameterTypes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Signature getOriginalSignature() {
        return originalSignature;
    }

    @Override
    public int getParameterCount(boolean receiver) {
        int count = parameterTypes.length;
        if (receiver) {
            ++count;
        }
        return count;
    }

    public int slotsForParameters(boolean receiver) {
        int slots = 0;

        if (receiver) {
            ++slots;
        }

        for (JavaType parameterType : parameterTypes) {
            slots += parameterType.getJavaKind().getSlotCount();
        }
        return slots;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return parameterTypes[index];
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return returnType;
    }

    @Override
    public String toString() {
        return "InterpreterUnresolvedSignature<" + toMethodDescriptor() + ">";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof InterpreterUnresolvedSignature that) {
            return returnType.equals(that.returnType) && MetadataUtil.arrayEquals(parameterTypes, that.parameterTypes);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = MetadataUtil.hashCode(returnType);
        result = 31 * result + MetadataUtil.arrayHashCode(parameterTypes);
        return result;
    }
}
