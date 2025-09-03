/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.util.VMError;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

/**
 * Carries information about an entrypoint for foreign function calls.
 * {@link ForeignFunctionsRuntime#getDowncallStubPointer} allows getting the associated function
 * pointer at runtime (if it exists).
 * <p>
 * {@link NativeEntryPointInfo#methodType} is of the form (<>: argument; []: optional argument)
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
    private final VMStorage[] parameterAssignments;
    private final VMStorage[] returnBuffering;
    private final boolean needsReturnBuffer;
    private final boolean capturesState;
    private final boolean needsTransition;
    private final boolean allowHeapAccess;

    private NativeEntryPointInfo(MethodType methodType, VMStorage[] cc, VMStorage[] returnBuffering, boolean needsReturnBuffer, boolean capturesState, boolean needsTransition,
                    boolean allowHeapAccess) {
        assert methodType.parameterCount() == cc.length;
        assert needsReturnBuffer == (returnBuffering.length > 1);
        // when no transition, allowHeapAccess is unused, so it must be set to false
        assert !(needsTransition && allowHeapAccess);
        this.methodType = methodType;
        this.parameterAssignments = cc;
        this.returnBuffering = returnBuffering;
        this.needsReturnBuffer = needsReturnBuffer;
        this.capturesState = capturesState;
        this.needsTransition = needsTransition;
        this.allowHeapAccess = allowHeapAccess;
    }

    public static NativeEntryPointInfo make(
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition,
                    boolean allowHeapAccessParam) {
        if ((returnMoves.length > 1) != needsReturnBuffer) {
            throw new AssertionError("Multiple register return, but needsReturnBuffer was false");
        }
        var hasNull = Arrays.stream(argMoves).anyMatch(Objects::isNull);
        VMError.guarantee(!hasNull || allowHeapAccessParam, "null VMStorages should only appear with Linker.Option.critical(true).");
        boolean allowHeapAccess = allowHeapAccessParam;
        if (!hasNull) {
            /*
             * hasNull is only true if this entry point's FunctionDescriptor contains an
             * AddressLayout and allowHeapAccess is true. (see
             * jdk.internal.foreign.abi.x64.sysv.CallArranger.UnboxBindingCalculator.getBindings).
             * Additionally, if no AddressLayout is passed, then the value of allowHeapAccess
             * doesn't matter (since then no heap access may occur anyway). Hence, we set
             * allowHeapAccess to false if no AddressLayout is found: indeed, sometimes we need to
             * find whether allowHeapAccess should be true or not, (see
             * com.oracle.svm.core.foreign.Target_jdk_internal_foreign_abi_NativeEntryPoint.make),
             * and we rely on the presence of null VMStorages as an indicator of whether to set
             * allowHeapAccess to true.
             */
            allowHeapAccess = false;
        }
        return new NativeEntryPointInfo(methodType, argMoves, returnMoves, needsReturnBuffer, capturedStateMask != 0, needsTransition, allowHeapAccess);
    }

    public static Target_jdk_internal_foreign_abi_NativeEntryPoint makeEntryPoint(
                    @SuppressWarnings("unused") ABIDescriptor ignoreAbi,
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition,
                    boolean allowHeapAccess) {
        var info = make(argMoves, returnMoves, methodType, needsReturnBuffer, capturedStateMask, needsTransition, allowHeapAccess);
        long addr = ForeignFunctionsRuntime.singleton().getDowncallStubPointer(info).rawValue();
        return new Target_jdk_internal_foreign_abi_NativeEntryPoint(info.methodType(), addr, capturedStateMask);
    }

    public MethodType methodType() {
        return this.methodType;
    }

    public boolean needsReturnBuffer() {
        return needsReturnBuffer;
    }

    public boolean capturesCallState() {
        return capturesState;
    }

    public boolean allowHeapAccess() {
        assert !(needsTransition && allowHeapAccess);
        return allowHeapAccess;
    }

    public VMStorage[] parametersAssignment() {
        return parameterAssignments;
    }

    public VMStorage[] returnsAssignment() {
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
        return capturesState == that.capturesState && needsTransition == that.needsTransition && needsReturnBuffer == that.needsReturnBuffer && allowHeapAccess == that.allowHeapAccess &&
                        Objects.equals(methodType, that.methodType) &&
                        Arrays.equals(parameterAssignments, that.parameterAssignments) && Arrays.equals(returnBuffering, that.returnBuffering);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodType, needsReturnBuffer, capturesState, needsTransition, allowHeapAccess, Arrays.hashCode(parameterAssignments), Arrays.hashCode(returnBuffering));
    }
}
