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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

/**
 * Packs the address of a {@link com.oracle.svm.hosted.foreign.DowncallStub} with some extra
 * information.
 */
@TargetClass(className = "jdk.internal.foreign.abi.NativeEntryPoint", onlyWith = ForeignFunctionsEnabled.class)
@Substitute
public final class Target_jdk_internal_foreign_abi_NativeEntryPoint {

    private final MethodType methodType;
    public final long downcallStubAddress;
    public final int captureMask;

    Target_jdk_internal_foreign_abi_NativeEntryPoint(MethodType methodType, long downcallStubAddress, int captureMask) {
        this.methodType = methodType;
        this.downcallStubAddress = downcallStubAddress;
        this.captureMask = captureMask;
    }

    @Substitute
    public static Target_jdk_internal_foreign_abi_NativeEntryPoint make(ABIDescriptor abi,
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition) {
        return NativeEntryPointInfo.makeEntryPoint(abi, argMoves, returnMoves, methodType, needsReturnBuffer, capturedStateMask, needsTransition);
    }

    @Substitute
    public MethodType type() {
        return methodType;
    }
}
