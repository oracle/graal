/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.lang.ref.WeakReference;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.espresso.EspressoLanguage;

/**
 * Used to implement String interning.
 */
public final class StringTable {

    private final EconomicMap<String, WeakReference<DynamicObject>> interned = EconomicMap.create();

    public synchronized DynamicObject intern(String value) {
        WeakReference<DynamicObject> ref = interned.get(value);
        DynamicObject unique = ref != null ? ref.get() : null;
        if (unique == null) {
            unique = createStringObject(value);
            interned.put(value, new WeakReference<>(unique));
        }
        return unique;
    }

    private DynamicObject createStringObject(String value) {
        throw EspressoLanguage.unimplemented();
    }

    public synchronized DynamicObject intern(DynamicObject stringObject) {
        String key = new String((char[]) stringObject.getShape().getProperty("value").getLocation().get(stringObject));
        WeakReference<DynamicObject> ref = interned.get(key);
        DynamicObject unique = ref != null ? ref.get() : null;
        if (unique == null) {
            unique = stringObject;
            interned.put(key, new WeakReference<>(unique));
        }
        return unique;
    }
}
