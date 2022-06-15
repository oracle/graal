/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import java.util.Arrays;
import java.util.Objects;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class JNICallSignature implements Signature {

    private final JavaKind[] parameterKinds;
    private final JavaKind returnKind;
    private final MetaAccessProvider originalMetaAccess;

    JNICallSignature(JavaKind[] parameterKinds, JavaKind returnKind, MetaAccessProvider originalMetaAccess) {
        this.parameterKinds = parameterKinds;
        this.returnKind = returnKind;
        this.originalMetaAccess = originalMetaAccess;
    }

    public String getIdentifier() {
        StringBuilder sb = new StringBuilder(1 + parameterKinds.length);
        sb.append(returnKind.getTypeChar());
        for (JavaKind kind : parameterKinds) {
            sb.append(kind.getTypeChar());
        }
        return sb.toString();
    }

    @Override
    public int getParameterCount(boolean receiver) {
        return parameterKinds.length;
    }

    private ResolvedJavaType resolveType(JavaKind kind) {
        Class<?> clazz = Object.class;
        if (!kind.isObject()) {
            clazz = kind.toJavaClass();
        }
        return originalMetaAccess.lookupJavaType(clazz);
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return resolveType(parameterKinds[index]);
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return resolveType(returnKind);
    }

    @Override
    public boolean equals(Object obj) {
        if (this != obj && obj instanceof JNICallSignature) {
            var other = (JNICallSignature) obj;
            return Arrays.equals(parameterKinds, other.parameterKinds) && Objects.equals(returnKind, other.returnKind);
        }
        return (this == obj);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parameterKinds) * 31 + Objects.hashCode(returnKind);
    }

    @Override
    public String toString() {
        return getIdentifier();
    }
}
