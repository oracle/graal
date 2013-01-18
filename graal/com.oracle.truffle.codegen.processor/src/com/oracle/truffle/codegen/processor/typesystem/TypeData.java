/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.typesystem;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;

public class TypeData extends Template {

    protected TypeSystemData typeSystem;
    private final TypeMirror primitiveType;
    private final TypeMirror boxedType;

    private final List<TypeCastData> typeCasts = new ArrayList<>();
    private final List<TypeCheckData> typeChecks = new ArrayList<>();

    public TypeData(TypeElement templateType, AnnotationMirror annotation,
                    TypeMirror primitiveType, TypeMirror boxedType) {
        super(templateType, annotation);
        this.primitiveType = primitiveType;
        this.boxedType = boxedType;
    }

    void addTypeCast(TypeCastData typeCast) {
        this.typeCasts.add(typeCast);
    }

    void addTypeCheck(TypeCheckData typeCheck) {
        this.typeChecks.add(typeCheck);
    }

    public List<TypeCastData> getTypeCasts() {
        return typeCasts;
    }

    public List<TypeCheckData> getTypeChecks() {
        return typeChecks;
    }

    public TypeSystemData getTypeSystem() {
        return typeSystem;
    }

    public TypeMirror getPrimitiveType() {
        return primitiveType;
    }

    public TypeMirror getBoxedType() {
        return boxedType;
    }

    public boolean isGeneric() {
        return Utils.typeEquals(boxedType, getTypeSystem().getGenericType());
    }

    public boolean isVoid() {
        if (getTypeSystem().getVoidType() == null) {
            return false;
        }
        return Utils.typeEquals(boxedType, getTypeSystem().getVoidType().getBoxedType());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + Utils.getSimpleName(primitiveType) + "]";
    }

}

