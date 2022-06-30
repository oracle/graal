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

import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.svm.core.SubstrateUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class JNICallSignature implements Signature {

    private final JavaType[] paramTypes;
    private final JavaType returnType;

    JNICallSignature(JavaType[] paramTypes, JavaType returnType) {
        assert Arrays.stream(paramTypes).noneMatch(WrappedJavaType.class::isInstance) && !(returnType instanceof WrappedJavaType);
        this.paramTypes = paramTypes;
        this.returnType = returnType;
    }

    JNICallSignature(JavaKind[] paramKinds, JavaKind returnKind, MetaAccessProvider originalMetaAccess) {
        this.paramTypes = new ResolvedJavaType[paramKinds.length];
        for (int i = 0; i < paramKinds.length; i++) {
            this.paramTypes[i] = resolveType(paramKinds[i], originalMetaAccess);
        }
        this.returnType = resolveType(returnKind, originalMetaAccess);
    }

    private static ResolvedJavaType resolveType(JavaKind kind, MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(kind.isObject() ? Object.class : kind.toJavaClass());
    }

    public String getIdentifier() {
        StringBuilder sb = new StringBuilder(1 + paramTypes.length);
        boolean digest = false;
        for (JavaType type : paramTypes) {
            if (type.getJavaKind().isPrimitive() || (type instanceof ResolvedJavaType && ((ResolvedJavaType) type).isJavaLangObject())) {
                sb.append(type.getJavaKind().getTypeChar());
            } else {
                sb.append(type.toClassName());
                digest = true;
            }
        }
        sb.append('_').append(returnType.getJavaKind().getTypeChar());
        return digest ? SubstrateUtil.digest(sb.toString()) : sb.toString();
    }

    @Override
    public int getParameterCount(boolean receiver) {
        return paramTypes.length;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return paramTypes[index];
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return returnType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this != obj && obj instanceof JNICallSignature) {
            var other = (JNICallSignature) obj;
            return Arrays.equals(paramTypes, other.paramTypes) && Objects.equals(returnType, other.returnType);
        }
        return (this == obj);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(paramTypes) * 31 + Objects.hashCode(returnType);
    }

    @Override
    public String toString() {
        return getIdentifier();
    }
}
