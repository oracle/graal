/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper for creating virtual tables in ObjectKlass
 */
public class VirtualTable {

    private VirtualTable() {

    }

    // Mirandas are already in the Klass, there is not much left to do.
    public static Method[] create(ObjectKlass superKlass, Method[] declaredMethods, ObjectKlass thisKlass) {
        ArrayList<Method> tmp;
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getVTable()));
        } else {
            tmp = new ArrayList<>();
        }
        for (Method m : declaredMethods) {
            checkOverride(superKlass, m, tmp);
        }
        for (Method m : thisKlass.getMirandaMethods()) {
            checkOverride(superKlass, m, tmp);
        }
        return tmp.toArray(Method.EMPTY_ARRAY);
    }

    private static void checkOverride(ObjectKlass superKlass, Method m, ArrayList<Method> tmp) {
        Method override;
        if (superKlass != null) {
            override = superKlass.lookupVirtualMethod(m.getName(), m.getRawSignature());
        } else {
            override = null;
        }
        if (override != null) {
            int pos = override.getVTableIndex();
            m.setVTableIndex(pos);
            tmp.set(pos, m);
        } else {
            int pos = tmp.size();
            m.setVTableIndex(pos);
            tmp.add(m);
        }
    }
}
