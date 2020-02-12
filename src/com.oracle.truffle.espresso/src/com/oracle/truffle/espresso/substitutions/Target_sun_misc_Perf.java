/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * These (incomplete) substitutions are just a band-aid to run critical internal code (e.g.
 * ClassLoader). The Perf API is currently unsupported.
 */
@EspressoSubstitutions
public final class Target_sun_misc_Perf {

    // the Variability enum must be kept in synchronization with the
    // the com.sun.hotspot.perfdata.Variability class
    // enum Variability {
    public static final int V_Constant = 1;
    public static final int V_Monotonic = 2;
    public static final int V_Variable = 3;
    public static final int V_last = V_Variable;
    // };

    // the Units enum must be kept in synchronization with the
    // the com.sun.hotspot.perfdata.Units class
    // enum Units {
    public static final int U_None = 1;
    public static final int U_Bytes = 2;
    public static final int U_Ticks = 3;
    public static final int U_Events = 4;
    public static final int U_String = 5;
    public static final int U_Hertz = 6;
    public static final int U_Last = U_Hertz;
    // };

    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[8]);
        buffer.putLong(0, x);
        return buffer.array();
    }

    @Substitution(hasReceiver = true)
    public static @Host(ByteBuffer.class) StaticObject createLong(@Host(typeName = "Lsun/misc/Perf;") StaticObject self,
                    @SuppressWarnings("unused") @Host(String.class) StaticObject name, int variability, int units, long value) {
        // TODO(tg): inject meta
        Meta meta = self.getKlass().getMeta();

        if (units <= 0 || units > U_Last) {
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }

        // check that the PerfData name doesn't already exist
        // if (PerfDataManager::exists(name_utf)) {
        // THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "PerfLong name already
        // exists");
        // }

        switch (variability) {
            case V_Constant:
            case V_Monotonic:
            case V_Variable:
                break;
            default:
                throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }

        return (StaticObject) meta.java_nio_ByteBuffer_wrap.invokeDirect(null, StaticObject.wrap(longToBytes(value)));
    }

    @Substitution
    public static void registerNatives() {
        /* nop */
    }
}
