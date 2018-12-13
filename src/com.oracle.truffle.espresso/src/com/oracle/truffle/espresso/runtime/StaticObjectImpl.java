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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;

public class StaticObjectImpl extends StaticObject {
    private Map<String, Object> hiddenFields;

    private final Object[] fields;

    public StaticObjectImpl(Klass klass, Map<String, Object> hiddenFields, Object[] fields) {
        super(klass);
        this.hiddenFields = hiddenFields;
        this.fields = fields;
    }

    public boolean isStatic() {
        return this == getKlass().getStatics();
    }

    // Shallow copy.
    public StaticObject copy() {
        HashMap<String, Object> hiddenFieldsCopy = hiddenFields != null ? new HashMap<>(hiddenFields) : null;
        return new StaticObjectImpl(getKlass(), hiddenFieldsCopy, fields.clone());
    }

    public StaticObjectImpl(ObjectKlass klass) {
        this(klass, false);
    }

    public StaticObjectImpl(ObjectKlass klass, boolean isStatic) {
        super(klass);
        assert !isStatic || klass.isInitialized();
        this.hiddenFields = null;
        this.fields = isStatic ? new Object[klass.getStaticFieldSlots()] : new Object[klass.getInstanceFieldSlots()];
        FieldInfo[] allFields = isStatic ? klass.getStaticFields() : klass.getInstanceFields(true);
        for (FieldInfo fi : allFields) {
            this.fields[fi.getSlot()] = MetaUtil.defaultFieldValue(fi.getKind());
        }
    }

    public final Object getField(FieldInfo field) {
        // TODO(peterssen): Klass check
        Object result = fields[field.getSlot()];
        assert result != null;
        return result;
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        if (getKlass() == meta.STRING.rawKlass()) {
            return Meta.toHostString((StaticObject) meta(this).method("toString", String.class).invokeDirect());
        }
        return getKlass().getName();
    }

    public final void setField(FieldInfo field, Object value) {
        // TODO(peterssen): Klass check
        fields[field.getSlot()] = value;
    }

    @CompilerDirectives.TruffleBoundary
    public void setHiddenField(String name, Object value) {
        if (hiddenFields == null) {
            hiddenFields = new HashMap<>();
        }
        hiddenFields.putIfAbsent(name, value);
    }

    @CompilerDirectives.TruffleBoundary
    public Object getHiddenField(String name) {
        if (hiddenFields == null) {
            return null;
        }
        return hiddenFields.get(name);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return StaticObjectMessageResolutionForeign.ACCESS;
    }
}
