/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

// Represents ClassInfo instances that are cached in the global
// cache for all classes having been involved in a redefinition
public final class ImmutableClassInfo extends ClassInfo {
    private final Symbol<Name> name; // key in global cache
    private final WeakReference<ObjectKlass> klass;
    private final StaticObject classLoader;
    private final byte[] bytes;

    // fingerprint of inner class
    private final String classFingerprint;
    private final String methodFingerprint;
    private final String fieldFingerprint;
    private final String enclosingMethodFingerprint;

    private final ArrayList<ImmutableClassInfo> innerClasses;

    ImmutableClassInfo(ObjectKlass klass, Symbol<Name> originalName, StaticObject classLoader, String classFingerprint, String methodFingerprint, String fieldFingerprint,
                    String enclosingMethodFingerprint, ArrayList<ImmutableClassInfo> inners, byte[] bytes, boolean isEnumSwitchmaphelper, boolean isInnerTestKlass) {
        super(isEnumSwitchmaphelper, isInnerTestKlass);
        this.klass = new WeakReference<>(klass);
        this.name = originalName;
        this.classLoader = classLoader;
        this.classFingerprint = classFingerprint;
        this.methodFingerprint = methodFingerprint;
        this.fieldFingerprint = fieldFingerprint;
        this.enclosingMethodFingerprint = enclosingMethodFingerprint;
        this.innerClasses = inners;
        this.bytes = bytes;
    }

    @Override
    public Symbol<Name> getName() {
        return name;
    }

    @Override
    public ObjectKlass getKlass() {
        return klass.get();
    }

    @Override
    public StaticObject getClassLoader() {
        return classLoader;
    }

    @Override
    public String getClassFingerprint() {
        return classFingerprint;
    }

    @Override
    public String getMethodFingerprint() {
        return methodFingerprint;
    }

    @Override
    public String getFieldFingerprint() {
        return fieldFingerprint;
    }

    @Override
    public String getEnclosingMethodFingerprint() {
        return enclosingMethodFingerprint;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public ArrayList<? extends ClassInfo> getInnerClasses() {
        return innerClasses;
    }

    public ArrayList<ImmutableClassInfo> getImmutableInnerClasses() {
        return innerClasses;
    }
}
