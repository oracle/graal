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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

/** A descriptor for an entry point from foreign into Java code via an upcall. */
public final class JavaEntryPointInfo {
    private final MethodType methodType;
    private final VMStorage[] argumentsAssignment;
    private final VMStorage[] returnAssignment;
    private final int returnBufferSize;

    private JavaEntryPointInfo(MethodType methodType, VMStorage[] argumentsAssignment, VMStorage[] returnAssignment, long returnBufferSize) {
        this.methodType = Objects.requireNonNull(methodType);
        this.argumentsAssignment = Objects.requireNonNull(argumentsAssignment);
        this.returnAssignment = Objects.requireNonNull(returnAssignment);
        this.returnBufferSize = NumUtil.safeToInt(returnBufferSize);
    }

    static JavaEntryPointInfo make(MethodType mt, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        assert needsReturnBuffer == (conv.retRegs().length >= 2);
        return new JavaEntryPointInfo(mt, conv.argRegs(), conv.retRegs(), returnBufferSize);
    }

    static JavaEntryPointInfo make(MethodHandle mh, ABIDescriptor abi, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        return make(mh.type(), conv, needsReturnBuffer, returnBufferSize);
    }

    public MethodType cMethodType() {
        MethodType mt = methodType;
        if (buffersReturn()) {
            mt = mt.dropParameterTypes(0, 1);
        }
        return mt;
    }

    public MethodType handleType() {
        return methodType;
    }

    public boolean buffersReturn() {
        return returnAssignment.length >= 2;
    }

    public VMStorage[] parametersAssignment() {
        return argumentsAssignment;
    }

    public VMStorage[] returnAssignment() {
        return returnAssignment;
    }

    public int returnBufferSize() {
        assert buffersReturn();
        return returnBufferSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaEntryPointInfo that = (JavaEntryPointInfo) o;
        return returnBufferSize == that.returnBufferSize && Objects.equals(methodType, that.methodType) && Arrays.equals(argumentsAssignment, that.argumentsAssignment) &&
                        Arrays.equals(returnAssignment, that.returnAssignment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodType, returnBufferSize, Arrays.hashCode(argumentsAssignment), Arrays.hashCode(returnAssignment));
    }
}
