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
package com.oracle.svm.interpreter.ristretto.meta;

import com.oracle.svm.graal.meta.SubstrateSignature;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * JVMCI representation of a {@link SubstrateSignature} used by Ristretto for compilation. Allocated
 * once per {@link RistrettoMethod} and kept as a field in the method.
 * <p>
 * Life cycle: lives until the referencing {@link RistrettoMethod} is gc-ed.
 */
public final class RistrettoUnresolvedSignature extends SubstrateSignature {
    private final InterpreterUnresolvedSignature interpreterSignature;

    RistrettoUnresolvedSignature(InterpreterUnresolvedSignature interpreterSignature) {
        this.interpreterSignature = interpreterSignature;
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        InterpreterResolvedJavaType accessingTypeResolved = accessingClass == null ? null : ((RistrettoType) accessingClass).getInterpreterType();
        JavaType returnType = interpreterSignature.getReturnType(accessingTypeResolved);
        if (returnType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.create(iType);
        }
        return returnType;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        InterpreterResolvedJavaType accessingTypeResolved = accessingClass == null ? null : ((RistrettoType) accessingClass).getInterpreterType();
        JavaType parameterType = interpreterSignature.getParameterType(index, accessingTypeResolved);
        if (parameterType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.create(iType);
        }
        return parameterType;
    }

    @Override
    public JavaKind getReturnKind() {
        return interpreterSignature.getReturnKind();
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        return interpreterSignature.getParameterCount(withReceiver);
    }

    @Override
    public JavaKind getParameterKind(int index) {
        return interpreterSignature.getParameterKind(index);
    }

    @Override
    public String toString() {
        return "RistrettoSignature{" +
                        "interpreterSignature=" + interpreterSignature +
                        '}';
    }

}
