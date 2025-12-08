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

import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(group = LibJava.class)
public final class Target_java_lang_ref_PhantomReference {
    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java25OrLater.class)
    public static void clear0(@JavaType(Reference.class) StaticObject ref,
                    @Inject SubstitutionProfiler profiler, @Inject VM vm) {
        vm.JVM_ReferenceClear(ref, profiler);

    }

    @Substitution(hasReceiver = true)
    public static boolean refersTo0(@JavaType(Reference.class) StaticObject ref, @JavaType(Object.class) StaticObject object,
                    @Inject SubstitutionProfiler profiler, @Inject VM vm) {
        return vm.JVM_PhantomReferenceRefersTo(ref, object, profiler);
    }

}
