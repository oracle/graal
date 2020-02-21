/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class LoadKlassNode extends RootNode {

    private final String targetClassName;

    public LoadKlassNode(EspressoLanguage language, String targetClassName) {
        super(language);
        this.targetClassName = targetClassName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert frame.getArguments().length == 0;
        EspressoContext context = EspressoLanguage.getCurrentContext();
        Meta meta = context.getMeta();
        StaticObject appClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
        try {
            StaticObject guestClass = (StaticObject) meta.java_lang_Class_forName_String_boolean_ClassLoader.invokeDirect(null, meta.toGuestString(targetClassName), false, appClassLoader);
            return guestClass.getMirrorKlass();
        } catch (EspressoException e) {
            if (InterpreterToVM.instanceOf(e.getExceptionObject(), meta.java_lang_ClassNotFoundException)) {
                return StaticObject.NULL;
            }
            throw e;
        }
    }
}
