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

package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.object.DebugCounter;

/**
 * A {@link GuestClassRegistry} maps class names to resolved {@link Klass} instances. Each class
 * loader is associated with a {@link GuestClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public final class GuestClassRegistry extends ClassRegistry {

    static final DebugCounter loadKlassCount = DebugCounter.create("Guest loadKlassCount");
    static final DebugCounter loadKlassCacheHits = DebugCounter.create("Guest loadKlassCacheHits");

    /**
     * The class loader associated with this registry.
     */
    private final StaticObject classLoader;

    // The virtual method can be cached because the receiver (classLoader) is constant.
    private final Method ClassLoader_loadClass;
    private final Method ClassLoader_addClass;

    public GuestClassRegistry(EspressoContext context, @Host(ClassLoader.class) StaticObject classLoader) {
        super(context);
        assert StaticObject.notNull(classLoader) : "cannot be the BCL";
        this.classLoader = classLoader;
        this.ClassLoader_loadClass = classLoader.getKlass().lookupMethod(Name.loadClass, Signature.Class_String);
        this.ClassLoader_addClass = classLoader.getKlass().lookupMethod(Name.addClass, Signature._void_Class);
    }

    @SuppressWarnings("unused")
    @Override
    public Klass loadKlass(Symbol<Type> type) {
        if (Types.isArray(type)) {
            Klass elemental = loadKlass(getTypes().getElementalType(type));
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }

        loadKlassCount.inc();
        Klass klass = classes.get(type);
        if (klass != null) {
            loadKlassCacheHits.inc();
            return klass;
        }
        assert StaticObject.notNull(classLoader);
        StaticObject guestClass = (StaticObject) ClassLoader_loadClass.invokeDirect(classLoader, getMeta().toGuestString(Types.binaryName(type)));
        klass = guestClass.getMirrorKlass();
        Klass previous = classes.putIfAbsent(type, klass);
        assert previous == null || previous == klass;
        return klass;
    }

    @Override
    public @Host(ClassLoader.class) StaticObject getClassLoader() {
        return classLoader;
    }

    @Override
    public ObjectKlass defineKlass(Symbol<Type> type, final byte[] bytes) {
        ObjectKlass klass = super.defineKlass(type, bytes);
        // Register class in guest CL. Mimics HotSpot behavior.
        ClassLoader_addClass.invokeDirect(classLoader, klass.mirror());
        return klass;
    }
}
