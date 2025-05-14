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

import static com.oracle.truffle.espresso.substitutions.SubstitutionFlag.needsSignatureMangle;

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

@EspressoSubstitutions(value = StackTraceElement.class, group = LibJava.class)
public final class Target_java_lang_StackTraceElement {
    @Substitution
    public static void initStackTraceElement(@JavaType(StackTraceElement.class) StaticObject stack, @JavaType(internalName = "Ljava/lang/StackFrameInfo;") StaticObject info,
                    @Inject Meta meta) {
        VM.JVM_InitStackTraceElement(stack, info, meta);
    }

    // for JDK < 19
    @Substitution(flags = {needsSignatureMangle})
    public static void initStackTraceElements(@JavaType(StackTraceElement[].class) StaticObject stack, @JavaType(Object.class) StaticObject backtrace,
                    @Inject VM vm,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        vm.JVM_InitStackTraceElementArray(stack, backtrace, language, meta, profiler);
    }

    // for JDK >= 19
    @Substitution(flags = {needsSignatureMangle})
    public static void initStackTraceElements(
                    @JavaType(StackTraceElement[].class) StaticObject stack,
                    @JavaType(Object.class) StaticObject backtrace,
                    @SuppressWarnings("unused") int depth,
                    @Inject VM vm, @Inject EspressoLanguage language, @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        vm.JVM_InitStackTraceElementArray(stack, backtrace, language, meta, profiler);
    }
}
