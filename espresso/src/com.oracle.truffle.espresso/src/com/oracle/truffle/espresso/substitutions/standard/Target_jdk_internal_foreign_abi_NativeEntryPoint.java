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
package com.oracle.truffle.espresso.substitutions.standard;

import java.lang.invoke.MethodType;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.panama.VMStorage;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
public final class Target_jdk_internal_foreign_abi_NativeEntryPoint {
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution
    public static long makeDowncallStub(@JavaType(MethodType.class) StaticObject methodType,
                    @JavaType(internalName = "Ljdk/internal/foreign/abi/ABIDescriptor;") @SuppressWarnings("unused") StaticObject abi,
                    @JavaType(internalName = "[Ljdk/internal/foreign/abi/VMStorage;") StaticObject encArgMoves,
                    @JavaType(internalName = "[Ljdk/internal/foreign/abi/VMStorage;") StaticObject encRetMoves,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition,
                    @Inject EspressoContext context, @Inject Meta meta) {
        if (StaticObject.isNull(methodType) || StaticObject.isNull(encArgMoves) || StaticObject.isNull(encRetMoves)) {
            throw meta.throwNullPointerException();
        }
        Klass[] pTypes = getPTypes(methodType, meta);
        Klass rType = meta.java_lang_invoke_MethodType_rtype.getObject(methodType).getMirrorKlass(meta);

        VMStorage[] inputRegs = VMStorage.fromGuestArray(encArgMoves, meta);
        VMStorage[] outRegs = VMStorage.fromGuestArray(encRetMoves, meta);
        assert inputRegs.length == pTypes.length;

        return context.getDowncallStubs().makeStub(pTypes, rType, inputRegs, outRegs, needsReturnBuffer, capturedStateMask, needsTransition);
    }

    static Klass[] getPTypes(StaticObject methodType, Meta meta) {
        EspressoLanguage language = meta.getLanguage();
        StaticObject guestPTypes = meta.java_lang_invoke_MethodType_ptypes.getObject(methodType);
        int numParamTypes = guestPTypes.length(language);
        Klass[] pTypes = new Klass[numParamTypes];
        for (int i = 0; i < numParamTypes; i++) {
            pTypes[i] = guestPTypes.<StaticObject> get(language, i).getMirrorKlass(meta);
        }
        return pTypes;
    }

    @Substitution
    public static boolean freeDowncallStub0(long downcallStub, @Inject EspressoContext context) {
        return context.getDowncallStubs().freeStub(downcallStub);
    }
}
