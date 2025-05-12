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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.lang.ref.Reference;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(value = Reference.class, group = LibJava.class)
public final class Target_java_lang_Reference {
    @Substitution
    public static void waitForReferencePendingList(@Inject EspressoContext ctx) {
        ctx.getVM().JVM_WaitForReferencePendingList();
    }

    @Substitution(hasReceiver = true)
    public static boolean refersTo0(@JavaType(Reference.class) StaticObject self, @JavaType(Object.class) StaticObject obj,
                    @Inject SubstitutionProfiler profile, @Inject VM vm, @Inject Meta meta, @Inject EspressoLanguage language) {
        return vm.JVM_ReferenceRefersTo(self, obj, profile, meta, language);
    }

    @Substitution
    public static @JavaType(Reference.class) StaticObject getAndClearReferencePendingList(@Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetAndClearReferencePendingList();
    }
}
