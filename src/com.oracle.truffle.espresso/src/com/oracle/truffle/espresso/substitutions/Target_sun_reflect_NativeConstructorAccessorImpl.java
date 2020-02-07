/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.lang.reflect.Constructor;

import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_sun_reflect_NativeConstructorAccessorImpl {
    @TruffleBoundary
    @Substitution
    public static @Host(Object.class) StaticObject newInstance0(@Host(Constructor.class) StaticObject constructor, @Host(Object[].class) StaticObject args0) {
        Meta meta = constructor.getKlass().getMeta();
        Klass klass = ((StaticObject) meta.java_lang_reflect_Constructor_clazz.get(constructor)).getMirrorKlass();
        klass.safeInitialize();
        if (klass.isArray() || klass.isPrimitive() || klass.isInterface() || klass.isAbstract()) {
            throw meta.throwException(meta.java_lang_InstantiationException);
        }
        Method reflectedMethod = Method.getHostReflectiveConstructorRoot(constructor);
        StaticObject instance = klass.allocateInstance();
        StaticObject parameterTypes = (StaticObject) meta.java_lang_reflect_Constructor_parameterTypes.get(constructor);
        Target_sun_reflect_NativeMethodAccessorImpl.callMethodReflectively(meta, instance, args0, reflectedMethod, klass, parameterTypes);
        return instance;
    }
}
