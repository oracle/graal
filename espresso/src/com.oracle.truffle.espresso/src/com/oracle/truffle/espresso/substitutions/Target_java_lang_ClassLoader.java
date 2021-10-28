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

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public final class Target_java_lang_ClassLoader {
    @Substitution
    public static @JavaType(String.class) StaticObject findBuiltinLib(@SuppressWarnings("unused") @JavaType(String.class) StaticObject name) {
        // The native implementation assumes builtin libraries are loaded in the default namespace,
        // Espresso loads isolated copies (mainly libjava).
        //
        // Native method linking needs special handling, since classes in the BCL must peek in
        // libjava first, try different signatures in case of overloading, the naming scheme can be
        // also platform dependent.
        //
        // A better workaround would be to load libjava the same way as libzip, we could then remove
        // the logic to link native methods by hand and rely on the pure Java implementation e.g.
        // ClassLoader.findNative.
        //
        // This substitution disables builtin libraries in Espresso.
        return StaticObject.NULL;
    }
}
