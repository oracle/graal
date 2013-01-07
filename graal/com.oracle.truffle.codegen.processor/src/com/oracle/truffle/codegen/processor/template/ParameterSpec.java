/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.template;

import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class ParameterSpec {

    public enum Kind {
        EXECUTE, SIGNATURE, SUPER_ATTRIBUTE, ATTRIBUTE, CONSTRUCTOR_FIELD, SHORT_CIRCUIT
    }

    public enum Cardinality {
        ONE, MULTIPLE;
    }

    private final String name;
    private final TypeMirror[] allowedTypes;
    private final TypeMirror valueType;
    private final Kind kind;
    private final boolean optional;
    private final Cardinality cardinality;

    public ParameterSpec(String name, TypeMirror[] allowedTypes, TypeMirror valueType, Kind kind, boolean optional, Cardinality cardinality) {
        this.valueType = valueType;
        this.allowedTypes = allowedTypes;
        this.name = name;
        this.kind = kind;
        this.optional = optional;
        this.cardinality = cardinality;
    }

    public ParameterSpec(String name, TypeMirror singleFixedType, Kind kind, boolean optional) {
        this(name, new TypeMirror[]{singleFixedType}, singleFixedType, kind, optional, Cardinality.ONE);
    }

    public ParameterSpec(String name, TypeSystemData typeSystem, Kind kind, boolean optional, Cardinality cardinality) {
        this(name, typeSystem.getPrimitiveTypeMirrors(), typeSystem.getGenericType(), kind, optional, cardinality);
    }

    public final String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public final boolean isOptional() {
        return optional;
    }

    public final Cardinality getCardinality() {
        return cardinality;
    }

    public TypeMirror[] getAllowedTypes() {
        return allowedTypes;
    }

    public boolean matches(TypeMirror actualType) {
        for (int i = 0; i < allowedTypes.length; i++) {
            TypeMirror mirror = allowedTypes[i];
            if (Utils.typeEquals(actualType, mirror)) {
                return true;
            }
        }
        return false;
    }

    public TypeMirror getValueType() {
        return valueType;
    }
}
