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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;

public class TypeData extends MessageContainer implements Comparable<TypeData> {

    private final TypeSystemData typeSystem;
    private final AnnotationValue annotationValue;
    private final TypeMirror primitiveType;
    private final TypeMirror boxedType;

    private final int index;
    private final List<TypeCastData> typeCasts = new ArrayList<>();
    private final List<TypeCheckData> typeChecks = new ArrayList<>();

    public TypeData(TypeSystemData typeSystem, int index, AnnotationValue value, TypeMirror primitiveType, TypeMirror boxedType) {
        this.index = index;
        this.typeSystem = typeSystem;
        this.annotationValue = value;
        this.primitiveType = primitiveType;
        this.boxedType = boxedType;
    }

    @Override
    public Element getMessageElement() {
        return typeSystem.getMessageElement();
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return typeSystem.getMessageAnnotation();
    }

    @Override
    public AnnotationValue getMessageAnnotationValue() {
        return annotationValue;
    }

    public void addTypeCast(TypeCastData typeCast) {
        this.typeCasts.add(typeCast);
    }

    public void addTypeCheck(TypeCheckData typeCheck) {
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
        return ElementUtils.typeEquals(boxedType, getTypeSystem().getGenericType());
    }

    public boolean isVoid() {
        if (getTypeSystem().getVoidType() == null) {
            return false;
        }
        return ElementUtils.typeEquals(boxedType, getTypeSystem().getVoidType().getBoxedType());
    }

    public int compareTo(TypeData o) {
        if (this.equals(o)) {
            return 0;
        }
        return index - o.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, primitiveType);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TypeData)) {
            return false;
        }
        TypeData otherType = (TypeData) obj;
        return index == otherType.index && ElementUtils.typeEquals(primitiveType, otherType.primitiveType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + ElementUtils.getSimpleName(primitiveType) + "]";
    }

    public boolean equalsType(TypeData actualTypeData) {
        return ElementUtils.typeEquals(boxedType, actualTypeData.boxedType);
    }

    public boolean needsCastTo(TypeData targetType) {
        return ElementUtils.needsCastTo(getPrimitiveType(), targetType.getPrimitiveType());
    }

    public boolean needsCastTo(TypeMirror targetType) {
        return ElementUtils.needsCastTo(getPrimitiveType(), targetType);
    }

    public boolean isPrimitive() {
        return ElementUtils.isPrimitive(getPrimitiveType());
    }

    public boolean isImplicitSubtypeOf(TypeData other) {
        List<ImplicitCastData> casts = other.getTypeSystem().lookupByTargetType(other);
        for (ImplicitCastData cast : casts) {
            if (isSubtypeOf(cast.getSourceType())) {
                return true;
            }
        }
        return isSubtypeOf(other);
    }

    public boolean isSubtypeOf(TypeData other) {
        return ElementUtils.isSubtype(boxedType, other.boxedType);
    }

}
