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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

@TargetClass(className = "jdk.internal.foreign.abi.UpcallLinker", onlyWith = ForeignFunctionsEnabled.class)
final class Target_jdk_internal_foreign_abi_UpcallLinker {

    @Substitute
    static long makeUpcallStub(MethodHandle mh, ABIDescriptor abi, Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs conv,
                    boolean needsReturnBuffer, long returnBufferSize) {
        var info = JavaEntryPointInfo.make(mh, abi, conv, needsReturnBuffer, returnBufferSize);
        return ForeignFunctionsRuntime.singleton().registerForUpcall(mh, info).rawValue();
    }

    @Substitute
    private static void registerNatives() {
    }
}

@TargetClass(className = "jdk.internal.foreign.abi.UpcallLinker", innerClass = "CallRegs", onlyWith = ForeignFunctionsEnabled.class)
final class Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs {
    @Alias private VMStorage[] argRegs;
    @Alias private VMStorage[] retRegs;

    @Substitute
    Target_jdk_internal_foreign_abi_UpcallLinker_CallRegs(VMStorage[] argRegs, VMStorage[] retRegs) {
        this.argRegs = argRegs;
        this.retRegs = retRegs;
    }

    @Substitute
    public VMStorage[] argRegs() {
        return this.argRegs;
    }

    @Substitute
    public VMStorage[] retRegs() {
        return this.retRegs;
    }
}
