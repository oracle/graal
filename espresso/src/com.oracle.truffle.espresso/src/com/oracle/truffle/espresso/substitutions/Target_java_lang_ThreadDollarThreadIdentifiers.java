/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.runtime.EspressoContext;

@EspressoSubstitutions(nameProvider = Target_java_lang_ThreadDollarThreadIdentifiers.ECJHack.class)
public final class Target_java_lang_ThreadDollarThreadIdentifiers {

    @Substitution
    public static long next(@Inject EspressoContext context) {
        return context.nextThreadId();
    }

    /* It works with javac, but ECJ trips over '$' in the class name */
    public static class ECJHack extends SubstitutionNamesProvider {
        private static String[] NAMES = {
                        TARGET_JAVA_LANG_THREAD_THREADIDENTIFIERS
        };
        public static SubstitutionNamesProvider INSTANCE = new ECJHack();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    private static final String TARGET_JAVA_LANG_THREAD_THREADIDENTIFIERS = "Target_java_lang_Thread$ThreadIdentifiers";
}
