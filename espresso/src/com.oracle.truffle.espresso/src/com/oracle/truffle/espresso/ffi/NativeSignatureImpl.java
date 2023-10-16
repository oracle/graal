/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.ffi;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.Arrays;
import java.util.Objects;

final class NativeSignatureImpl implements NativeSignature {

    private final NativeType returnType;
    @CompilationFinal(dimensions = 1) //
    private final NativeType[] parameterTypes;
    @CompilationFinal(dimensions = 1) //
    private final NativeType[] varArgsParameterTypes;

    NativeSignatureImpl(NativeType returnType, NativeType[] parameterTypes) {
        this(returnType, parameterTypes, null);
    }

    NativeSignatureImpl(NativeType returnType, NativeType[] parameterTypes, NativeType[] varArgsParameterTypes) {
        this.returnType = Objects.requireNonNull(returnType);
        this.parameterTypes = Objects.requireNonNull(parameterTypes);
        this.varArgsParameterTypes = varArgsParameterTypes;
        for (int i = 0; i < parameterTypes.length; i++) {
            NativeType param = Objects.requireNonNull(parameterTypes[i]);
            if (param == NativeType.VOID) {
                throw new IllegalArgumentException("Invalid VOID parameter type");
            }
        }
        if (varArgsParameterTypes != null) {
            for (int i = 0; i < varArgsParameterTypes.length; i++) {
                NativeType param = Objects.requireNonNull(varArgsParameterTypes[i]);
                if (param == NativeType.VOID) {
                    throw new IllegalArgumentException("Invalid VOID parameter type");
                }
            }
        }
    }

    @Override
    public NativeType getReturnType() {
        return returnType;
    }

    @Override
    public int getParameterCount() {
        return parameterTypes.length;
    }

    @Override
    public int getVarArgsParameterCount() {
        return varArgsParameterTypes == null ? 0 : varArgsParameterTypes.length;
    }

    @Override
    public NativeType parameterTypeAt(int index) {
        if (index < parameterTypes.length) {
            return parameterTypes[index];
        }
        return varArgsParameterTypes[index - parameterTypes.length];
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NativeSignature)) {
            return false;
        }
        NativeSignature that = (NativeSignature) other;
        if (returnType != that.getReturnType()) {
            return false;
        }
        if (parameterTypes.length != that.getParameterCount()) {
            return false;
        }
        if (this.getVarArgsParameterCount() != that.getParameterCount()) {
            return false;
        }
        int totalParamaterCount = getParameterCount() + getVarArgsParameterCount();
        for (int i = 0; i < totalParamaterCount; ++i) {
            if (this.parameterTypeAt(i) != that.parameterTypeAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(returnType);
        result = 31 * result + Arrays.hashCode(parameterTypes);
        result = 31 * result + Arrays.hashCode(varArgsParameterTypes);
        return result;
    }

    @Override
    public String toString() {
        return Arrays.toString(parameterTypes) + (varArgsParameterTypes == null ? "" : "..." + Arrays.toString(varArgsParameterTypes)) + " : " + returnType;
    }
}
