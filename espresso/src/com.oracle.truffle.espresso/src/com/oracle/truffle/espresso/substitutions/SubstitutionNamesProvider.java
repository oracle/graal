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

import java.util.Arrays;

public abstract class SubstitutionNamesProvider {
    @SuppressWarnings("this-escape")
    protected SubstitutionNamesProvider() {
        assert substitutionClassNames().length == getMethodNames("").length;
    }

    public abstract String[] substitutionClassNames();

    public String[] getMethodNames(String name) {
        String[] names = new String[substitutionClassNames().length];
        Arrays.fill(names, name);
        return names;
    }

    public static String[] append0(SubstitutionNamesProvider self, String name) {
        assert self.substitutionClassNames().length == 2;
        String[] names = new String[self.substitutionClassNames().length];
        for (int i = 0; i < names.length; i++) {
            names[i] = name + APPENDS[i];
        }
        return names;
    }

    private static final String[] APPENDS = {
                    "",
                    "0"
    };

    public abstract static class NoProvider extends SubstitutionNamesProvider {
    }
}
