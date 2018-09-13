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

package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Constructor;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_sun_reflect_NativeConstructorAccessorImpl {
    @Intrinsic
    public static Object newInstance0(
                    @Type(Constructor.class) StaticObject constructor,
                    @Type(Object[].class) StaticObject parameters) {

        assert parameters == StaticObject.NULL || (((StaticObjectArray) parameters)).getWrapped().length == 0;

        StaticObjectClass clazz = (StaticObjectClass) meta(constructor).field("clazz").get();
        clazz.getMirror().initialize();
        String className = clazz.getMirror().getName();

        StaticObject instance = Utils.getVm().newObject(Utils.getContext().getRegistries().resolve(Utils.getContext().getTypeDescriptors().make(className), null));
        MethodInfo emptyConstructor = instance.getKlass().findDeclaredConcreteMethod("<init>", Utils.getContext().getSignatureDescriptors().make("()V"));
        emptyConstructor.getCallTarget().call(instance);
        return instance;
    }
}
