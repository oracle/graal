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
package com.oracle.svm.preview.panama.core;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.graal.code.MemoryAssignment;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

/**
 * Carries information about an entrypoint for foreign function calls.
 * {@link ForeignFunctionsRuntime} allows getting the associated function pointer (if it exists).
 */
public final class NativeEntryPointInfo {
    private final MethodType methodType;
    private final MemoryAssignment[] parameterAssignments;
    private final MemoryAssignment[] returnBuffering;

    public NativeEntryPointInfo(MethodType methodType, MemoryAssignment[] cc, MemoryAssignment[] returnBuffering) {
        this.methodType = methodType;
        this.parameterAssignments = cc;
        this.returnBuffering = returnBuffering;
    }
    public static void checkType(MethodType methodType, boolean needsReturnBuffer, int savedValueMask) {
        if (methodType.parameterType(0) != long.class) {
            throw new AssertionError("Address expected as first param: " + methodType);
        }
        int checkIdx = 1;
        if ((needsReturnBuffer && methodType.parameterType(checkIdx++) != long.class)
                || (savedValueMask != 0 && methodType.parameterType(checkIdx) != long.class)) {
            throw new AssertionError("return buffer and/or preserved value address expected: " + methodType);
        }
    }

    public static NativeEntryPointInfo make(@SuppressWarnings("unused") ABIDescriptor abi,
                                            VMStorage[] argMoves, VMStorage[] returnMoves,
                                            MethodType methodType,
                                            boolean needsReturnBuffer,
                                            int capturedStateMask) {
        if (returnMoves.length > 1 != needsReturnBuffer) {
            throw new AssertionError("Multiple register return, but needsReturnBuffer was false");
        }

        checkType(methodType, needsReturnBuffer, capturedStateMask);
        var parametersAssignment = AbiUtils.getInstance().toMemoryAssignment(argMoves, false);
        var returnBuffering = needsReturnBuffer ? AbiUtils.getInstance().toMemoryAssignment(returnMoves, true) : null;
        return new NativeEntryPointInfo(methodType, parametersAssignment, returnBuffering);
    }

    public int callAddressIndex() {
        return needsReturnBuffer() ? 1 : 0;
    }

    /**
     * Method type without any of the special arguments.
     */
    public MethodType nativeMethodType() {
        return this.methodType.dropParameterTypes(0, callAddressIndex()+1);
    }

    /**
     * Method type without the call address
     */
    public MethodType stubMethodType() {
        int idx = callAddressIndex();
        return this.methodType.dropParameterTypes(idx, idx+1);
    }

    /**
     * Method type with all special arguments.
     */
    public MethodType linkMethodType() {
        return this.methodType;
    }

    public boolean needsReturnBuffer() {
        return this.returnBuffering != null;
    }

    public MemoryAssignment[] parametersAssignment() {
        assert parameterAssignments.length == this.nativeMethodType().parameterCount();
        return parameterAssignments;
    }

    public MemoryAssignment[] returnsAssignment() {
        return returnBuffering;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeEntryPointInfo that = (NativeEntryPointInfo) o;
        return methodType.equals(that.methodType) && Arrays.equals(parameterAssignments, that.parameterAssignments) && Arrays.equals(returnBuffering, that.returnBuffering);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(methodType);
        result = 31 * result + Arrays.hashCode(parameterAssignments);
        result = 31 * result + Arrays.hashCode(returnBuffering);
        return result;
    }
}