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

import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Ljdk/internal/reflect/Reflection;", group = LibJava.class)
public final class Target_jdk_internal_reflect_Reflection {
    @Substitution(languageFilter = VersionFilter.Java25OrEarlier.class)
    public static int getClassAccessFlags(@JavaType(Class.class) StaticObject cls, @Inject EspressoContext ctx) {
        return ctx.getVM().JVM_GetClassAccessFlags(cls);
    }

    @Substitution
    public static boolean areNestMates(@JavaType(Class.class) StaticObject cls1, @JavaType(Class.class) StaticObject cls2, @Inject VM vm) {
        return vm.JVM_AreNestMates(cls1, cls2);
    }
}
