/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;

public final class DiffVersionLoadHelper {

    private final Meta meta;
    private Symbol<Name> name;
    private Symbol<Type> type;
    private Symbol<Signature> signature;

    public DiffVersionLoadHelper(Meta meta) {
        this.meta = meta;
    }

    public DiffVersionLoadHelper klass(VersionRange range, Symbol<Type> t) {
        if (range.contains(meta.getJavaVersion())) {
            this.type = t;
        }
        return this;
    }

    public ObjectKlass klass() {
        if (type == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return meta.knownKlass(type);
    }

    public ObjectKlass notRequiredKlass() {
        if (type == null) {
            return null;
        }
        return meta.loadKlassWithBootClassLoader(type);
    }

    public DiffVersionLoadHelper method(VersionRange range, Symbol<Name> n, Symbol<Signature> s) {
        if (range.contains(meta.getJavaVersion())) {
            this.name = n;
            this.signature = s;
        }
        return this;
    }

    public Method method(ObjectKlass klass) {
        if (name == null || signature == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return klass.requireDeclaredMethod(name, signature);
    }

    public Method notRequiredMethod(ObjectKlass klass) {
        if (name == null || signature == null) {
            return null;
        }
        if (klass == null) {
            throw EspressoError.shouldNotReachHere("Missing klass for method " + name + ":" + signature);
        }
        return klass.requireDeclaredMethod(name, signature);
    }

    public DiffVersionLoadHelper field(VersionRange range, Symbol<Name> n, Symbol<Type> t) {
        if (range.contains(meta.getJavaVersion())) {
            this.name = n;
            this.type = t;
        }
        return this;
    }

    public Field field(ObjectKlass klass) {
        if (name == null || type == null) {
            throw EspressoError.shouldNotReachHere();
        }
        return klass.requireDeclaredField(name, type);
    }

    public Field notRequiredField(ObjectKlass klass) {
        if (name == null || type == null) {
            return null;
        }
        if (klass == null) {
            throw EspressoError.shouldNotReachHere("Missing klass for field " + name + ":" + type);
        }
        return klass.requireDeclaredField(name, type);
    }

    public Field maybeHiddenfield(ObjectKlass klass) {
        if (name == null || type == null) {
            return null;
        }
        Field f = klass.lookupDeclaredField(name, type);
        if (f != null) {
            return f;
        }
        return klass.requireHiddenField(name);
    }
}
