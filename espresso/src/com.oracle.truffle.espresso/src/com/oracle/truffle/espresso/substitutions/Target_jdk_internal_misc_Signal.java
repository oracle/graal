/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public final class Target_jdk_internal_misc_Signal {

    // Avoid going through JVM_FindSignal which has a char* argument
    @SuppressWarnings("unused")
    @Substitution
    @TruffleBoundary
    public static int findSignal0(@JavaType(String.class) StaticObject name,
                    @Inject Meta meta) {
        return Target_sun_misc_Signal.findSignal(name, meta);
    }

    @SuppressWarnings("unused")
    @Substitution
    public static void raise(@JavaType(internalName = "Ljdk/internal/misc/Signal;") StaticObject signal,
                    @Inject Meta meta) {
        Target_sun_misc_Signal.raise(signal, meta);
    }

    @SuppressWarnings("unused")
    @Substitution
    public static @JavaType(internalName = "Ljdk/internal/misc/Signal$Handler;") StaticObject handle(@JavaType(internalName = "Ljdk/internal/misc/Signal;") StaticObject signal,
                    @JavaType(internalName = "Ljdk/internal/misc/Signal$Handler;") StaticObject handler,
                    @Inject Meta meta) {
        return Target_sun_misc_Signal.handle(signal, handler, meta);
    }
}
