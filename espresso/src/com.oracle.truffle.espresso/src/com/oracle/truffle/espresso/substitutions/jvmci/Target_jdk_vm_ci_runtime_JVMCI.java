/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.jvmci;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
final class Target_jdk_vm_ci_runtime_JVMCI {

    private Target_jdk_vm_ci_runtime_JVMCI() {
    }

    @Substitution
    public static @JavaType(internalName = "Ljdk/vm/ci/runtime/JVMCIRuntime;") StaticObject initializeRuntime(@Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        return (StaticObject) context.getMeta().jvmci.EspressoJVMCIRuntime_runtime.invokeDirectStatic();
    }

    static void checkJVMCIAvailable(EspressoLanguage lang) {
        if (!lang.isInternalJVMCIEnabled()) {
            throw throwJVMCINoEnabledError();
        }
    }

    @TruffleBoundary
    static EspressoException throwJVMCINoEnabledError() {
        EspressoContext context = EspressoContext.get(null);
        Meta meta = context.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "JVMCI is not enabled");
    }
}
