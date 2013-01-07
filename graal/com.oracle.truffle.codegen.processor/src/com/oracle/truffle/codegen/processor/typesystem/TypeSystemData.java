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

public class TypeSystemData extends Template {

    private final TypeData[] types;
    private final TypeMirror[] primitiveTypeMirrors;
    private final TypeMirror[] boxedTypeMirrors;

    private final TypeMirror nodeType;
    private final TypeMirror genericType;

    private final TypeData voidType;

    private List<GuardData> guards;


    public TypeSystemData(TypeElement templateType, AnnotationMirror annotation,
                    TypeData[] types, TypeMirror nodeType, TypeMirror genericType, TypeData voidType) {
        super(templateType, annotation);
        this.voidType = voidType;
        this.types = types;
        this.nodeType = nodeType;
        this.genericType = genericType;

        this.primitiveTypeMirrors = new TypeMirror[types.length];
        for (int i = 0; i < types.length; i++) {
            primitiveTypeMirrors[i] = types[i].getPrimitiveType();
        }

        this.boxedTypeMirrors = new TypeMirror[types.length];
        for (int i = 0; i < types.length; i++) {
            boxedTypeMirrors[i] = types[i].getBoxedType();
        }

        for (TypeData type : types) {
            type.typeSystem = this;
        }
        if (voidType != null)  {
            voidType.typeSystem = this;
        }
    }

    public TypeData getVoidType() {
        return voidType;
    }

    void setGuards(List<GuardData> guards) {
        this.guards = guards;
    }

    public List<GuardData> getGuards() {
        return guards;
    }

    public TypeData[] getTypes() {
        return types;
    }

    public TypeMirror[] getPrimitiveTypeMirrors() {
        return primitiveTypeMirrors;
    }

    public TypeMirror[] getBoxedTypeMirrors() {
        return boxedTypeMirrors;
    }

    public TypeMirror getNodeType() {
        return nodeType;
    }

    public TypeMirror getGenericType() {
        return genericType;
    }

    public TypeData getGenericTypeData() {
        TypeData result = types[types.length - 1];
        assert result.getBoxedType() == genericType;
        return result;
    }

    public TypeData findType(String simpleName) {
        for (TypeData type : types) {
            if (Utils.getSimpleName(type.getBoxedType()).equals(simpleName)) {
                return type;
            }
        }
        return null;
    }

    public int findType(TypeMirror type) {
        for (int i = 0; i < types.length; i++) {
            if (Utils.typeEquals(types[i].getPrimitiveType(), type)) {
                return i;
            }
        }
        return -1;
    }

}
