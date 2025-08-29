/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Member;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_jdk_internal_reflect_ConstantPool {
    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static int getSize0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, @Inject VM vm) {
        return vm.JVM_ConstantPoolGetSize(unused, jcpool);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static int getSize0(StaticObject self, @Inject VM vm, @Inject Meta meta) {
        return vm.JVM_ConstantPoolGetSize(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self));
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static @JavaType(Class.class) StaticObject getClassAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetClassAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static @JavaType(Class.class) StaticObject getClassAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetClassAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(Class.class) StaticObject getClassAtIfLoaded0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(Class.class) StaticObject getClassAtIfLoaded0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static int getClassRefIndexAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static int getClassRefIndexAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAtIfLoaded0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(Member.class) StaticObject getMethodAtIfLoaded0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAtIfLoaded0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(Field.class) StaticObject getFieldAtIfLoaded0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getMemberRefInfoAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getMemberRefInfoAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static int getNameAndTypeRefIndexAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static int getNameAndTypeRefIndexAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getNameAndTypeRefInfoAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static @JavaType(String[].class) StaticObject getNameAndTypeRefInfoAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static int getIntAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetIntAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static int getIntAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetIntAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static long getLongAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetLongAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static long getLongAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetLongAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static float getFloatAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetFloatAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static float getFloatAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetFloatAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static double getDoubleAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetDoubleAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static double getDoubleAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetDoubleAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static @JavaType(String.class) StaticObject getStringAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetStringAt(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static @JavaType(String.class) StaticObject getStringAt0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetStringAt(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    public static @JavaType(String.class) StaticObject getUTF8At0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetUTF8At(unused, jcpool, index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    public static @JavaType(String.class) StaticObject getUTF8At0(StaticObject self, int index,
                    @Inject VM vm, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return vm.JVM_ConstantPoolGetUTF8At(null, meta.sun_reflect_ConstantPool_constantPoolOop.getObject(self), index, meta, profiler);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrEarlier.class)
    @SuppressWarnings("unused")
    public static byte getTagAt0(StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java26OrLater.class)
    @SuppressWarnings("unused")
    public static byte getTagAt0(StaticObject self, int index) {
        throw JavaSubstitution.unimplemented();
    }
}
