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
package com.oracle.truffle.espresso.libs.libnespresso.impl;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.libs.libnespresso.LibNespresso;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionFlag;

@EspressoSubstitutions(type = "", group = LibNespresso.class)
public final class LibNespressoSubstitutions {
    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static @Pointer TruffleObject initializeNativeContext(@Pointer TruffleObject unused) {
        // nop
        return RawPointer.nullInstance();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static void disposeNativeContext(@Pointer TruffleObject unused1, @Pointer TruffleObject unused2) {
        // nop
    }

    // Checkstyle: stop field name check
    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static boolean pop_boolean(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static byte pop_byte(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static char pop_char(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static short pop_short(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static int pop_int(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static float pop_float(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static double pop_double(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static long pop_long(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }

    @Substitution(flags = SubstitutionFlag.allowPointerType)
    @SuppressWarnings("unused")
    public static StaticObject pop_object(@Pointer TruffleObject unused) {
        throw JavaSubstitution.shouldNotReachHere();
    }
    // Checkstyle: resume field name check
}
