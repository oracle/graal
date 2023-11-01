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
package com.oracle.truffle.espresso.substitutions;

import java.lang.invoke.MethodHandle;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.panama.VMStorage;

@EspressoSubstitutions
public final class Target_jdk_internal_foreign_abi_UpcallLinker {
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution
    public static long makeUpcallStub(@JavaType(MethodHandle.class) StaticObject mh,
                    @JavaType(internalName = "Ljdk/internal/foreign/abi/ABIDescriptor;") @SuppressWarnings("unused") StaticObject abi,
                    @JavaType(internalName = "Ljdk/internal/foreign/abi/UpcallLinker$CallRegs;") StaticObject conv,
                    boolean needsReturnBuffer,
                    long returnBufferSize,
                    @Inject EspressoContext context, @Inject Meta meta) {
        StaticObject lform = meta.java_lang_invoke_MethodHandle_form.getObject(mh);
        StaticObject mname = meta.java_lang_invoke_LambdaForm_vmentry.getObject(lform);
        Method target = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(mname);
        assert target.getDeclaringKlass().isInitialized();
        assert target.isStatic();

        StaticObject guestArgRegs = meta.jdk_internal_foreign_abi_UpcallLinker_CallRegs_argRegs.getObject(conv);
        StaticObject guestRetRegs = meta.jdk_internal_foreign_abi_UpcallLinker_CallRegs_retRegs.getObject(conv);
        VMStorage[] argRegs = VMStorage.fromGuestArray(guestArgRegs, meta);
        VMStorage[] retRegs = VMStorage.fromGuestArray(guestRetRegs, meta);

        return context.getUpcallStubs().makeStub(mh, target, argRegs, retRegs, needsReturnBuffer, returnBufferSize);
    }
}
