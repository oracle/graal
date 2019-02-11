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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.impl.Klass;

public abstract class StaticObject implements TruffleObject {
    // Context-less objects.
    public static final StaticObject NULL = new Null();
    public static final StaticObject VOID = new Void();

    private final Klass klass;

    protected StaticObject(Klass klass) {
        this.klass = klass;
    }

    public final Klass getKlass() {
        return klass;
    }

    public static boolean isNull(StaticObject object) {
        assert object != null;
        return object == StaticObject.NULL;
    }

    public static boolean notNull(StaticObject object) {
        return !isNull(object);
    }

    public final boolean isStaticStorage() {
        return this == getKlass().getStatics();
    }
}

final class Void extends StaticObject {
    Void() {
        super(null);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return VoidMessageResolutionForeign.ACCESS;
    }

    @Override
    public String toString() {
        return "void";
    }
}

final class Null extends StaticObject {
    protected Null() {
        super(null);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return NullMessageResolutionForeign.ACCESS;
    }

    @Override
    public String toString() {
        return "null";
    }
}
