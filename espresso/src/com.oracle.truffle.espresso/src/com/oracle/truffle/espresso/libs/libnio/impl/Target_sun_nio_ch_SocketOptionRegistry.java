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
package com.oracle.truffle.espresso.libs.libnio.impl;

import java.net.ProtocolFamily;
import java.net.SocketOption;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.libs.EspressoLibsFilter;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
public final class Target_sun_nio_ch_SocketOptionRegistry {
    /*
     * As we just use the int level to encode SocketOptions we define here a dummy value as the
     * (unused) name of the OptionKey.
     */
    private static final int OPTION_KEY_NAME = 0;

    @TruffleBoundary
    @Substitution(languageFilter = EspressoLibsFilter.class)
    public static @JavaType(internalName = "Lsun/nio/ch/OptionKey;") StaticObject findOption(@JavaType(SocketOption.class) StaticObject name,
                    @SuppressWarnings("unused") @JavaType(ProtocolFamily.class) StaticObject family,
                    @Inject LibsMeta libsMeta,
                    @Inject Meta meta,
                    @Inject EspressoContext context) {
        // First retrieve the name of the guest options.
        Object result = libsMeta.net.java_net_SocketOption_name.invokeDirectInterface(name);
        // Use the name to get the int encoding.
        String optionName = meta.toHostString((StaticObject) result);
        int level = LibsState.SocketOptionSync.getInt(optionName);
        if (level == -1) {
            throw JavaSubstitution.shouldNotReachHere();
        }
        return makeGuestOptionKey(level, libsMeta, context);
    }

    private static @JavaType(internalName = "Lsun/nio/ch/OptionKey;") StaticObject makeGuestOptionKey(int level, LibsMeta libsMeta, EspressoContext context) {
        StaticObject guestObject = libsMeta.net.sun_nio_ch_OptionKey.allocateInstance(context);
        libsMeta.net.sun_nio_ch_OptionKey_init.invokeDirectSpecial(guestObject, level, OPTION_KEY_NAME);
        return guestObject;
    }

}
