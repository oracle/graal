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
package com.oracle.truffle.espresso.nodes.interop;

import java.util.Collections;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.ParameterizedEspressoType;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeImpl;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeVariable;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class WrappedProxyKlass {

    private final ObjectKlass proxyKlass;
    // array that holds the class type arguments from index 0 followed by the partially resolved
    // generic return types
    private final EspressoType[] genericTypeArray;
    // mapping from generic class argument identifier to the index
    private final Map<String, Integer> typeVariableIdentifiers;

    WrappedProxyKlass(ObjectKlass proxyKlass, EspressoType[] genericTypeArray, Map<String, Integer> typeVariableIdentifiers) {
        this.proxyKlass = proxyKlass;
        this.genericTypeArray = genericTypeArray;
        this.typeVariableIdentifiers = typeVariableIdentifiers;
    }

    WrappedProxyKlass(ObjectKlass proxyKlass, int numTypeIdentifiers) {
        this.proxyKlass = proxyKlass;
        this.typeVariableIdentifiers = Collections.emptyMap();
        this.genericTypeArray = new EspressoType[numTypeIdentifiers];
    }

    StaticObject createProxyInstance(EspressoType[] resolvedGenericTypes, Object foreignObject, InteropLibrary interop, EspressoLanguage language) {
        StaticObject foreign = StaticObject.createForeign(language, proxyKlass, foreignObject, interop);
        language.getTypeArgumentProperty().setObject(foreign, resolvedGenericTypes);
        return foreign;
    }

    @TruffleBoundary
    EspressoType[] fillTypeArguments(EspressoType[] types) {
        EspressoType[] result = genericTypeArray.clone();
        // copy passed class type arguments into the final generic type array
        System.arraycopy(types, 0, result, 0, types.length);
        fillTypeVariableTypes(result);
        return result;
    }

    @TruffleBoundary
    EspressoType[] fillTypeArguments() {
        EspressoType[] result = genericTypeArray.clone();
        fillTypeVariableTypes(result);
        return result;
    }

    private void fillTypeVariableTypes(EspressoType[] types) {
        for (int i = typeVariableIdentifiers.size(); i < types.length; i++) {
            EspressoType returnType = types[i];
            if (returnType == null) {
                types[i] = proxyKlass.getMeta().java_lang_Object;
            } else {
                if (returnType instanceof ParameterizedEspressoType parameterizedReturnType) {
                    types[i] = resolveType(parameterizedReturnType, typeVariableIdentifiers, proxyKlass.getMeta(), types);
                }
            }
        }
    }

    @TruffleBoundary
    private EspressoType resolveType(ParameterizedEspressoType type, Map<String, Integer> identifiers, Meta meta, EspressoType[] types) {
        EspressoType[] typeArguments = type.getTypeArguments();
        for (int i = 0; i < typeArguments.length; i++) {
            EspressoType typeArgument = typeArguments[i];
            if (typeArgument instanceof ParameterizedTypeVariable variable) {
                // OK, found type variable, so lookup resolved type from foreign object
                Integer variableIndex = identifiers.get(variable.getIdentifier());
                if (variableIndex == null) {
                    typeArguments[i] = meta.java_lang_Object;
                } else {
                    typeArguments[i] = types[variableIndex];
                }
            } else if (typeArgument instanceof ParameterizedEspressoType argType) {
                typeArguments[i] = resolveType(argType, identifiers, meta, types);
            }
        }
        return ParameterizedTypeImpl.make(type.getRawType(), typeArguments, type.getOwnerType());
    }
}
