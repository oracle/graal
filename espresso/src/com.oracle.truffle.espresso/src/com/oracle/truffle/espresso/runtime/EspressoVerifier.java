/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.perf.DebugCloseable;
import com.oracle.truffle.espresso.classfile.perf.DebugTimer;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.verifier.VerificationException;
import com.oracle.truffle.espresso.shared.verifier.Verifier;

public final class EspressoVerifier {
    public static final DebugTimer VERIFIER_TIMER = DebugTimer.create("verifier");

    @SuppressWarnings({"unused", "try"})
    public static void verify(EspressoContext ctx, Method method) {
        try (DebugCloseable t = VERIFIER_TIMER.scope(ctx.getTimers())) {
            Verifier.verify(ctx, method);
        } catch (VerificationException e) {
            Meta meta = ctx.getMeta();
            String message = String.format("Verification for class `%s` failed for method `%s` with message `%s`",
                            method.getDeclaringKlass().getExternalName(),
                            method.getNameAsString(),
                            e.getMessage());
            switch (e.kind()) {
                case Verify:
                    throw meta.throwExceptionWithMessage(meta.java_lang_VerifyError, message);
                case ClassFormat:
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
            }
        }
    }

    public static boolean needsVerify(EspressoLanguage language, StaticObject classLoader) {
        switch (language.getVerifyMode()) {
            case NONE:
                return false;
            case REMOTE:
                return !StaticObject.isNull(classLoader);
            case ALL:
                return true;
            default:
                return true;
        }
    }

    private EspressoVerifier() {
    }
}
