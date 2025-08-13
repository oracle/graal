/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjvm.impl;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.libs.libjvm.LibJVM;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionFlag;

@EspressoSubstitutions(type = "", group = LibJVM.class)
public final class LibJVMSubstitutions {
    private static final @Pointer TruffleObject sentinelPointer = new RawPointer(-1);

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject initializeMokapotContext(@Pointer TruffleObject unused1, @Pointer TruffleObject unused2) {
        // nop
        return null;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void disposeMokapotContext(@Pointer TruffleObject unused1, @Pointer TruffleObject unused2) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject initializeManagementContext(@Pointer TruffleObject unused1, int unused2) {
        return null;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void disposeManagementContext(@Pointer TruffleObject unused1, int unused2, @Pointer TruffleObject unused3) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void initializeStructs(@Pointer TruffleObject unused) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static long lookupMemberOffset(@Pointer TruffleObject unused1, @Pointer TruffleObject unused2) {
        return -1;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject initializeJvmtiContext(@Pointer TruffleObject unused1, int unused2) {
        return null;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void disposeJvmtiContext(@Pointer TruffleObject unused1, int unused2, @Pointer TruffleObject unused3) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject getJavaVM(@Pointer TruffleObject unused) {
        return null;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void mokapotAttachThread(@Pointer TruffleObject unused) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static void mokapotCaptureState(@Pointer TruffleObject unused1, int unused2) {
        // nop
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks, methodName = "mokapotGetRTLD_DEFAULT")
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject mokapotGetRTLDDEFAULT() {
        // nop
        return sentinelPointer;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject mokapotGetProcessHandle() {
        // nop
        return sentinelPointer;
    }

    @Substitution(flags = SubstitutionFlag.relaxTypeChecks)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject getPackageAt(@Pointer TruffleObject unused1, int unused2) {
        // nop
        return null;
    }
}
