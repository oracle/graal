/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.hub;

//Checkstyle: allow reflection

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public final class GenericInfo {

    private static final TypeVariable<?>[] EMPTY_TYPE_VARIABLE_ARRAY = new TypeVariable<?>[0];
    private static final GenericInfo EMPTY_GENERIC_INFO = new GenericInfo(EMPTY_TYPE_VARIABLE_ARRAY, null, null);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static GenericInfo factory(TypeVariable<?>[] typeParameters, Type[] genericInterfaces, Type genericSuperClass) {
        boolean hasTypeParameters = typeParameters.length > 0;
        boolean hasGenericInterfaces = genericInterfaces.length > 0 && !Arrays.stream(genericInterfaces).allMatch(Class.class::isInstance);
        boolean hasGenericSuperClass = genericSuperClass != null && !(genericSuperClass instanceof Class);
        if (hasTypeParameters || hasGenericInterfaces || hasGenericSuperClass) {
            return new GenericInfo(encodeTypeParameters(typeParameters), encodeGenericInterfaces(hasGenericInterfaces, genericInterfaces),
                            encodeGenericSuperClass(hasGenericSuperClass, genericSuperClass));
        }
        return EMPTY_GENERIC_INFO;
    }

    private static Object encodeTypeParameters(TypeVariable<?>[] typeParameters) {
        if (typeParameters.length == 1) {
            return typeParameters[0];
        }
        return typeParameters;
    }

    private static Object encodeGenericInterfaces(boolean hasGenericInterfaces, Type[] genericInterfaces) {
        if (hasGenericInterfaces) {
            if (genericInterfaces.length == 1) {
                return genericInterfaces[0];
            } else {
                return genericInterfaces;
            }
        } else {
            return null;
        }
    }

    private static Type encodeGenericSuperClass(boolean hasGenericSuperClass, Type genericSuperClass) {
        if (hasGenericSuperClass) {
            return genericSuperClass;
        }
        return null;
    }

    private final Object typeParametersEncoding;
    private final Object genericInterfacesEncoding;
    private final Type genericSuperClass;

    @Platforms(Platform.HOSTED_ONLY.class)
    private GenericInfo(Object typeParameters, Object genericInterfaces, Type genericSuperClass) {
        assert typeParameters != null;
        this.typeParametersEncoding = typeParameters;
        this.genericInterfacesEncoding = genericInterfaces;
        this.genericSuperClass = genericSuperClass;
    }

    TypeVariable<?>[] getTypeParameters() {
        if (typeParametersEncoding instanceof Type) {
            return new TypeVariable<?>[]{(TypeVariable<?>) typeParametersEncoding};
        } else {
            /* The caller is allowed to modify the array, so we have to make a copy. */
            return ((TypeVariable<?>[]) typeParametersEncoding).clone();
        }
    }

    boolean hasGenericInterfaces() {
        return genericInterfacesEncoding != null;
    }

    Type[] getGenericInterfaces() {
        assert hasGenericInterfaces();
        if (genericInterfacesEncoding instanceof Type) {
            return new Type[]{(Type) genericInterfacesEncoding};
        } else {
            /* The caller is allowed to modify the array, so we have to make a copy. */
            return ((Type[]) genericInterfacesEncoding).clone();
        }
    }

    boolean hasGenericSuperClass() {
        return genericSuperClass != null;
    }

    Type getGenericSuperClass() {
        assert hasGenericSuperClass();
        return genericSuperClass;
    }
}
