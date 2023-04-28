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
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.VMStorage;

/**
 * Carries information about an entrypoint for foreign function calls.
 * {@link ForeignFunctionsRuntime} allows getting the associated function pointer (if it exists).
 */
public final class NativeEntryPointInfo {
    private final MethodType methodType;
    private final MemoryAssignment[] parameterAssignments;
    private final MemoryAssignment[] returnBuffering;
    private final int capturedStateMask;

    public NativeEntryPointInfo(MethodType methodType, MemoryAssignment[] cc, MemoryAssignment[] returnBuffering, int stateCaptureMask) {
        /*
            Method type is of the form (<>: argument; []: optional argument)
            [return buffer address] <call address> [capture state address] <actual arg 1> <actual arg 2> ...
            where <actual arg i>s are the arguments which end up passed to the C native function
         */
        this.methodType = methodType;
        this.parameterAssignments = cc;
        this.returnBuffering = returnBuffering;
        this.capturedStateMask = stateCaptureMask;
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
        capturedStateMask = capturedStateMask & AbiUtils.getInstance().supportedCaptureMask();
        return new NativeEntryPointInfo(methodType, parametersAssignment, returnBuffering, capturedStateMask);
    }

    public int callAddressIndex() {
        return needsReturnBuffer() ? 1 : 0;
    }

    public int captureAddressIndex() {
        if (!capturesCallState()) {
            throw new IllegalArgumentException(this + " doesn't have a capture state argument");
        }
        return callAddressIndex() + 1;
    }

    /**
     * Method type without any of the special arguments.
     */
    public MethodType nativeMethodType() {
        if (capturesCallState()) {
            return this.methodType.dropParameterTypes(0, captureAddressIndex() + 1);
        }
        else {
            return this.methodType.dropParameterTypes(0, callAddressIndex() + 1);
        }
    }

    /**
     * Native method type, with a potential (pointer to) return buffer as prefix argument.
     * Put differently: method type without the call address and the (pointer to) capture buffer (if present).
     */
    public MethodType stubMethodType() {
        int start = callAddressIndex();
        int end = start + 1;
        if (capturesCallState()) {
            end += 1;
        }
        return this.methodType.dropParameterTypes(start, end);
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

    public boolean capturesCallState() {
        return capturedStateMask != 0;
    }

    public boolean requiresCapture(CapturableState capture) {
        return (capture.mask() & capturedStateMask) != 0;
    }

    public MemoryAssignment[] parametersAssignment() {
        assert parameterAssignments.length == this.nativeMethodType().parameterCount(): Arrays.toString(parameterAssignments) + " ; " + nativeMethodType();
        return parameterAssignments;
    }

    public MemoryAssignment[] returnsAssignment() {
        return returnBuffering;
    }

    public int capturedStateMast() {
        return capturedStateMask;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeEntryPointInfo that = (NativeEntryPointInfo) o;
        return capturedStateMask == that.capturedStateMask && Objects.equals(methodType, that.methodType) && Arrays.equals(parameterAssignments, that.parameterAssignments) && Arrays.equals(returnBuffering, that.returnBuffering);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(methodType, capturedStateMask);
        result = 31 * result + Arrays.hashCode(parameterAssignments);
        result = 31 * result + Arrays.hashCode(returnBuffering);
        return result;
    }
}