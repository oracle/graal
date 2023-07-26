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
package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.graal.code.AssignedLocation;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

/**
 * Carries information about an entrypoint for foreign function calls.
 * {@link ForeignFunctionsRuntime#getDowncallStubPointer} allows getting the associated function
 * pointer at runtime (if it exists).
 * <p>
 * {@link NativeEntryPointInfo#linkMethodType} is of the form (<>: argument; []: optional argument)
 *
 * <pre>
 * {@code
 *      [return buffer address] <call address> [capture state address] <actual arg 1> <actual arg 2> ...
 * }
 * </pre>
 * 
 * where {@code <actual arg i>}s are the arguments which end up passed to the C native function.
 */
public final class NativeEntryPointInfo {
    private final MethodType methodType;
    private final AssignedLocation[] parameterAssignments;
    private final AssignedLocation[] returnBuffering;
    private final boolean capturesState;
    private final boolean needsTransition;

    private NativeEntryPointInfo(MethodType methodType, AssignedLocation[] cc, AssignedLocation[] returnBuffering, boolean capturesState, boolean needsTransition) {
        this.methodType = methodType;
        this.parameterAssignments = cc;
        this.returnBuffering = returnBuffering;
        this.capturesState = capturesState;
        this.needsTransition = needsTransition;
    }

    public static NativeEntryPointInfo make(
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition) {
        if (returnMoves.length > 1 != needsReturnBuffer) {
            throw new AssertionError("Multiple register return, but needsReturnBuffer was false");
        }

        AssignedLocation[] parametersAssignment = AbiUtils.singleton().toMemoryAssignment(argMoves, false);
        AssignedLocation[] returnBuffering = AbiUtils.singleton().toMemoryAssignment(returnMoves, true);
        return new NativeEntryPointInfo(methodType, parametersAssignment, returnBuffering, capturedStateMask != 0, needsTransition);
    }

    public static Target_jdk_internal_foreign_abi_NativeEntryPoint makeEntryPoint(
                    @SuppressWarnings("unused") ABIDescriptor ignoreAbi,
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition) {
        var info = make(argMoves, returnMoves, methodType, needsReturnBuffer, capturedStateMask, needsTransition);
        long addr = ForeignFunctionsRuntime.singleton().getDowncallStubPointer(info).rawValue();
        return new Target_jdk_internal_foreign_abi_NativeEntryPoint(info.linkMethodType(), addr, capturedStateMask);
    }

    public int callAddressIndex() {
        return needsReturnBuffer() ? 1 : 0;
    }

    public int captureAddressIndex() {
        if (!capturesCallState()) {
            throw new IllegalArgumentException(this + " does not have a capture state argument");
        }
        return callAddressIndex() + 1;
    }

    /**
     * Method type without any of the special arguments.
     */
    public MethodType nativeMethodType() {
        if (capturesCallState()) {
            return this.methodType.dropParameterTypes(0, captureAddressIndex() + 1);
        } else {
            return this.methodType.dropParameterTypes(0, callAddressIndex() + 1);
        }
    }

    /**
     * Method type with all special arguments.
     */
    public MethodType linkMethodType() {
        return this.methodType;
    }

    public boolean needsReturnBuffer() {
        return this.returnBuffering.length >= 2;
    }

    public boolean capturesCallState() {
        return capturesState;
    }

    public AssignedLocation[] parametersAssignment() {
        assert parameterAssignments.length == this.nativeMethodType().parameterCount() : Arrays.toString(parameterAssignments) + " ; " + nativeMethodType();
        return parameterAssignments;
    }

    public AssignedLocation[] returnsAssignment() {
        return returnBuffering;
    }

    public boolean skipsTransition() {
        return !needsTransition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeEntryPointInfo that = (NativeEntryPointInfo) o;
        return capturesState == that.capturesState && needsTransition == that.needsTransition && Objects.equals(methodType, that.methodType) &&
                        Arrays.equals(parameterAssignments, that.parameterAssignments) && Arrays.equals(returnBuffering, that.returnBuffering);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodType, capturesState, needsTransition, Arrays.hashCode(parameterAssignments), Arrays.hashCode(returnBuffering));
    }
}
