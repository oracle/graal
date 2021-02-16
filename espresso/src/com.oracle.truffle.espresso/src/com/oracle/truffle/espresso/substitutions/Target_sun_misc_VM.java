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
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(nameProvider = Target_sun_misc_VM.SharedVM.class)
public final class Target_sun_misc_VM {
    @Substitution
    public static void initialize() {
        /* nop */
    }

    /**
     * This JVM method is registered through a call to VM.initialize. Since we are already
     * substituting the initialize method, We are using this substitution to manually link to the
     * JVM method.
     * 
     * TODO: Investigate if the initialize substitution is really necessary.
     */
    @Substitution
    public static long getNanoTimeAdjustment(long offset) {
        return VM.JVM_GetNanoTimeAdjustment(StaticObject.NULL, offset);
    }

    public static class SharedVM extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        "Target_sun_misc_VM",
                        "Target_jdk_internal_misc_VM"
        };
        public static SubstitutionNamesProvider INSTANCE = new SharedVM();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }
}
