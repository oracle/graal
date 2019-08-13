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

import com.oracle.truffle.espresso.descriptors.Symbol;

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
        ArrayList<Method> overrides = new ArrayList<>();
        if (superKlass != null) {
            tmp = new ArrayList<>(Arrays.asList(superKlass.getVTable()));
        } else {
            tmp = new ArrayList<>();
        }
        for (Method m : declaredMethods) {
            if (m.getName() != Symbol.Name.CLINIT && m.getName() != Symbol.Name.INIT) {
                // Do not bloat the vtable with these two methods that cannot be called through
                // virtual invocation.
                checkOverride(superKlass, m, tmp, thisKlass, overrides);
            }
        }
        for (Method m : thisKlass.getMirandaMethods()) {
            m.setVTableIndex(tmp.size());
            tmp.add(m);
            // checkOverride(superKlass, m, tmp);
        }
        return tmp.toArray(Method.EMPTY_ARRAY);
    }

    private static void checkOverride(ObjectKlass superKlass, Method m, ArrayList<Method> tmp, Klass thisKlass, ArrayList<Method> overrides) {
        if (!overrides.isEmpty()) {
            overrides.clear();
        }
        if (superKlass != null) {
            superKlass.lookupVirtualMethodOverrides(m.getName(), m.getRawSignature(), thisKlass, overrides);
        }
        Method toSet = m;
        if (!overrides.isEmpty()) {
            int count = 1;
            for (Method override : overrides) {
                override.invalidateLeaf();
                int pos = override.getVTableIndex();
                if (count > 1) {
                    toSet = new Method(m);
                }
                toSet.setVTableIndex(pos);
                tmp.set(pos, toSet);
                count++;
            }
        } else {
            int pos = tmp.size();
            toSet.setVTableIndex(pos);
            tmp.add(toSet);
        }
    }
}
