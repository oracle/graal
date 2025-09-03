/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.generics.factory;

import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.GenericArrayTypeImpl;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeImpl;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeVariable;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * This class is a modified version of sun.reflect.generics.factory.CoreReflectionFactory. The use
 * of Class is replaced by the Espresso representation of guest classes Klass. In Espresso, this
 * class is not used for reflective generic type objects for use by reflection. Rather it is used as
 * a factory that enables generic type hints for foreign objects being passed into Espresso where a
 * generic target type is declared.
 */
public final class CoreReflectionFactory implements GenericsFactory {
    private final Klass klass;

    private CoreReflectionFactory(Klass d) {
        klass = d;
    }

    private StaticObject getDefiningClassLoader() {
        return klass.getDefiningClassLoader();
    }

    /**
     * Factory for this klass. Returns an instance of {@code CoreReflectionFactory} for the
     * declaration and scope provided. Klasses produced will be those that would be loaded by the
     * defining class loader of the klass.
     * <p>
     * 
     * @param klass - the espresso klass
     * @return an instance of {@code CoreReflectionFactory}
     */
    public static CoreReflectionFactory make(Klass klass) {
        return new CoreReflectionFactory(klass);
    }

    public EspressoType makeParameterizedType(EspressoType declaration,
                    EspressoType[] typeArgs,
                    EspressoType owner) {
        return ParameterizedTypeImpl.make((Klass) declaration, typeArgs, owner);
    }

    public EspressoType makeNamedType(String dotName) {
        return klass.getMeta().loadKlassOrNull(klass.getTypes().getOrCreateValidType(toInternalName(dotName)), getDefiningClassLoader(), StaticObject.NULL);
    }

    @Override
    public EspressoType makeTypeVariable(String name, EspressoType javaLangObjectType) {
        return ParameterizedTypeVariable.make(name, javaLangObjectType);
    }

    private static String toInternalName(String dotName) {
        return "L" + dotName.replace('.', '/') + ";";
    }

    public EspressoType makeArrayType(EspressoType componentType) {
        if (componentType instanceof Klass) {
            return ((Klass) componentType).array();
        } else {
            return GenericArrayTypeImpl.make(componentType);
        }
    }

    public EspressoType makeJavaLangObject() {
        return klass.getMeta().java_lang_Object;
    }

    public EspressoType makeByte() {
        return klass.getMeta()._byte;
    }

    public EspressoType makeBool() {
        return klass.getMeta()._boolean;
    }

    public EspressoType makeShort() {
        return klass.getMeta()._short;
    }

    public EspressoType makeChar() {
        return klass.getMeta()._char;
    }

    public EspressoType makeInt() {
        return klass.getMeta()._int;
    }

    public EspressoType makeLong() {
        return klass.getMeta()._long;
    }

    public EspressoType makeFloat() {
        return klass.getMeta()._float;
    }

    public EspressoType makeDouble() {
        return klass.getMeta()._double;
    }

    public EspressoType makeVoid() {
        return klass.getMeta()._void;
    }
}
