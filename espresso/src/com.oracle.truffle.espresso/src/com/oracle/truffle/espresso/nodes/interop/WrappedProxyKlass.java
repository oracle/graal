/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class WrappedProxyKlass extends ProxyKlass {

    private final EspressoContext context;
    private final Field foreignWrapperField;

    public WrappedProxyKlass(ObjectKlass proxyKlass, EspressoContext context, ObjectKlass superKlass) {
        super(proxyKlass);
        this.context = context;
        this.foreignWrapperField = getForeignWrapperField(superKlass);
    }

    private Field getForeignWrapperField(ObjectKlass superKlass) {
        if (superKlass == context.getMeta().polyglot.EspressoForeignList) {
            return context.getMeta().polyglot.EspressoForeignList_foreignObject;
        }
        throw EspressoError.shouldNotReachHere();
    }

    @Override
    public StaticObject createProxyInstance(Object foreignObject, EspressoLanguage language, InteropLibrary interop) {
        Meta meta = context.getMeta();
        StaticObject foreign = StaticObject.createForeign(language, meta.java_lang_Object, foreignObject, interop);
        // special handling for wrapped classes since we need a proper guest object, not a foreign
        StaticObject guestObject = context.getAllocator().createNew(getProxyKlass());
        foreignWrapperField.setObject(guestObject, foreign);
        return guestObject;
    }
}
