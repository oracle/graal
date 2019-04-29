/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.List;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A straightforward implementation of {@link Signature}.
 */
public class SimpleSignature implements Signature {
    private final JavaType[] parameterTypes;
    private final JavaType returnType;

    public SimpleSignature(JavaType[] parameterTypes, JavaType returnType) {
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public SimpleSignature(List<JavaType> parameterTypes, JavaType returnType) {
        this(parameterTypes.toArray(new JavaType[0]), returnType);
    }

    @Override
    public int getParameterCount(boolean receiver) {
        return parameterTypes.length;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        return parameterTypes[index];
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return returnType;
    }
}
