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

import java.lang.reflect.Field;
import java.lang.reflect.Member;

import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Ljdk/internal/reflect/ConstantPool;", group = LibJava.class)
public final class Target_jdk_internal_reflect_ConstantPool {
    @Substitution(hasReceiver = true)
    public static int getSize0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, @Inject VM vm) {
        return vm.JVM_ConstantPoolGetSize(unused, jcpool);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Class.class) StaticObject getClassAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetClassAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(Class.class) StaticObject getClassAtIfLoaded0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static int getClassRefIndexAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAtIfLoaded0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAtIfLoaded0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getMemberRefInfoAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static int getNameAndTypeRefIndexAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getNameAndTypeRefInfoAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }

    @Substitution(hasReceiver = true)
    public static int getIntAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetIntAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static long getLongAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetLongAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static float getFloatAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetFloatAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static double getDoubleAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetDoubleAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getStringAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetStringAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getUTF8At0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetUTF8At(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static byte getTagAt0(@JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();

    }
}
