/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions(nameProvider = Target_sun_reflect_NativeConstructorAccessorImpl.SharedNativeConstructorAccessorImpl.class)
public final class Target_sun_reflect_NativeConstructorAccessorImpl {
    private static final String[] NAMES = {
                    "Target_sun_reflect_NativeConstructorAccessorImpl",
                    "Target_jdk_internal_reflect_NativeConstructorAccessorImpl",
                    "Target_jdk_internal_reflect_DirectConstructorHandleAccessor$NativeAccessor"
    };

    @Substitution(methodName = "newInstance0")
    abstract static class NewInstance0 extends SubstitutionNode {
        public abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(Constructor.class) StaticObject constructor,
                        @JavaType(Object[].class) StaticObject args0,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta);

        @Specialization
        public @JavaType(Object.class) StaticObject newInstance(
                        @JavaType(Constructor.class) StaticObject constructor,
                        @JavaType(Object[].class) StaticObject args0,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Cached ToEspressoNode.DynamicToEspresso toEspressoNode) {
            Klass klass = meta.java_lang_reflect_Constructor_clazz.getObject(constructor).getMirrorKlass(meta);
            klass.safeInitialize();
            if (klass.isArray() || klass.isPrimitive() || klass.isInterface() || klass.isAbstract()) {
                throw meta.throwException(meta.java_lang_InstantiationException);
            }
            Method reflectedMethod = Method.getHostReflectiveConstructorRoot(constructor, meta);
            StaticObject instance = klass.allocateInstance(meta.getContext());
            StaticObject parameterTypes = meta.java_lang_reflect_Constructor_parameterTypes.getObject(constructor);
            Target_sun_reflect_NativeMethodAccessorImpl.callMethodReflectively(language, meta, instance, args0, reflectedMethod, klass, parameterTypes, toEspressoNode);
            return instance;
        }
    }

    public static class SharedNativeConstructorAccessorImpl extends SubstitutionNamesProvider {
        public static SubstitutionNamesProvider INSTANCE = new SharedNativeConstructorAccessorImpl();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

}
