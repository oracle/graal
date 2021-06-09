/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject.test;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.espresso.staticobject.ClassLoaderCache;
import com.oracle.truffle.espresso.staticobject.DefaultStaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class StaticObjectTest implements ClassLoaderCache {
    static final boolean ARRAY_BASED_STORAGE = TruffleOptions.AOT || Boolean.getBoolean("com.oracle.truffle.espresso.staticobject.ArrayBasedStorage");
    private ClassLoader cl;

    String guessGeneratedFieldName(StaticProperty property) {
        assert !ARRAY_BASED_STORAGE;
        // The format of generated field names with the field-based storage might change at any
        // time. Do not depend on it!
        if (property instanceof DefaultStaticProperty) {
            return ((DefaultStaticProperty) property).getId();
        } else {
            try {
                Method getId = StaticProperty.class.getDeclaredMethod("getId");
                getId.setAccessible(true);
                return (String) getId.invoke(property);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setClassLoader(ClassLoader cl) {
        this.cl = cl;
    }

    @Override
    public ClassLoader getClassLoader() {
        return cl;
    }
}
