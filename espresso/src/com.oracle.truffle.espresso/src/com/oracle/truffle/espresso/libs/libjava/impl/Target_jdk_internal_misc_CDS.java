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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.VM;

@SuppressWarnings("unused")
@EspressoSubstitutions(type = "Ljdk/internal/misc/CDS;", group = LibJava.class)
public final class Target_jdk_internal_misc_CDS {

    @Substitution
    public static void initializeFromArchive(@JavaType(Class.class) StaticObject c, @Inject EspressoContext ctx) {
        VM.JVM_InitializeFromArchive(c, ctx);
    }

    @Substitution
    public static void defineArchivedModules(
                    @JavaType(Object.class) StaticObject platformLoader,
                    @JavaType(Object.class) StaticObject systemLoader,
                    @Inject EspressoContext context) {
        VM.JVM_DefineArchivedModules(platformLoader, systemLoader, context);
    }

    @Substitution
    public static long getRandomSeedForDumping(@Inject EspressoContext context) {
        return VM.JVM_GetRandomSeedForDumping(context);
    }

    @Substitution
    public static boolean isDumpingArchive0(@Inject EspressoContext context) {
        return VM.JVM_IsCDSDumpingEnabled(context);
    }

    @Substitution
    public static boolean isSharingEnabled0(@Inject EspressoContext context) {
        return VM.JVM_IsSharingEnabled(context);
    }

    @Substitution
    public static boolean isDumpingClassList0() {
        return VM.JVM_IsDumpingClassList();
    }

    @Substitution
    public static void logLambdaFormInvoker(@JavaType(Target_java_lang_String.class) StaticObject line) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.unimplemented();
    }

    @Substitution
    public static void dumpClassList(@JavaType(Target_java_lang_String.class) StaticObject fileName) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.unimplemented();
    }

    @Substitution
    public static void dumpDynamicArchive(@JavaType(Target_java_lang_String.class) StaticObject archiveName) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.unimplemented();
    }
}
