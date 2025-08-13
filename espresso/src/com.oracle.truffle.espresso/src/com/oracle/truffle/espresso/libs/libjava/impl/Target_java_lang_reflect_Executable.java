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

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(value = Executable.class, group = LibJava.class)
public final class Target_java_lang_reflect_Executable {
    @Substitution(hasReceiver = true)
    public static @JavaType(Parameter[].class) StaticObject getParameters0(@JavaType(Executable.class) StaticObject self,
                    @Inject VM vm, @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_GetMethodParameters(self, language, meta, profiler);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static @JavaType(byte[].class) StaticObject getTypeAnnotationBytes0(@JavaType(Executable.class) StaticObject self, @Inject VM vm) {
        return vm.JVM_GetMethodTypeAnnotations(self);
    }
}
