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

import sun.misc.Signal;

@EspressoSubstitutions(nameProvider = Target_sun_misc_Signal.SharedSignal.class)
public final class Target_sun_misc_Signal {
    @Substitution(nameProvider = SharedSignalAppend0.class)
    @TruffleBoundary
    public static int findSignal(@Host(String.class) StaticObject name,
                    @InjectMeta Meta meta) {
        return new Signal(meta.toHostString(name)).getNumber();
    }

    @SuppressWarnings("unused")
    @Substitution
    public static long handle0(int sig, long nativeH) {
        // TODO(peterssen): Find out how to properly manage host/guest signals.
        /* nop */
        return 0;
    }

    public static class SharedSignal extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_SUN_MISC_SIGNAL,
                        TARGET_JDK_INTERNAL_MISC_SIGNAL
        };
        public static SubstitutionNamesProvider INSTANCE = new SharedSignal();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    public static class SharedSignalAppend0 extends SharedSignal {
        public static SubstitutionNamesProvider INSTANCE = new SharedSignalAppend0();

        @Override
        public String[] getMethodNames(String name) {
            return append0(this, name);
        }
    }

    private static final String TARGET_SUN_MISC_SIGNAL = "Target_sun_misc_Signal";
    private static final String TARGET_JDK_INTERNAL_MISC_SIGNAL = "Target_jdk_internal_misc_Signal";
}
