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

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;

public class TypeSystemData extends Template {

    private List<TypeData> types;
    private List<TypeMirror> primitiveTypeMirrors = new ArrayList<>();
    private List<TypeMirror> boxedTypeMirrors = new ArrayList<>();

    private List<ImplicitCastData> implicitCasts;
    private List<TypeCastData> casts;
    private List<TypeCheckData> checks;

    private TypeMirror genericType;
    private TypeData voidType;

    public TypeSystemData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        super(context, templateType, null, annotation);
    }

    @Override
    public TypeSystemData getTypeSystem() {
        return this;
    }

    public void setTypes(List<TypeData> types) {
        this.types = types;
        if (types != null) {
            for (TypeData typeData : types) {
                primitiveTypeMirrors.add(typeData.getPrimitiveType());
                boxedTypeMirrors.add(typeData.getBoxedType());
            }
        }
    }

    public void setImplicitCasts(List<ImplicitCastData> implicitCasts) {
        this.implicitCasts = implicitCasts;
    }

    public List<ImplicitCastData> getImplicitCasts() {
        return implicitCasts;
    }

    public void setCasts(List<TypeCastData> casts) {
        this.casts = casts;
    }

    public void setChecks(List<TypeCheckData> checks) {
        this.checks = checks;
    }

    public void setGenericType(TypeMirror genericType) {
        this.genericType = genericType;
    }

    public void setVoidType(TypeData voidType) {
        this.voidType = voidType;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> sinks = new ArrayList<>();
        if (types != null) {
            sinks.addAll(types);
        }
        if (checks != null) {
            sinks.addAll(checks);
        }
        if (casts != null) {
            sinks.addAll(casts);
        }
        if (implicitCasts != null) {
            sinks.addAll(implicitCasts);
        }
        return sinks;
    }

    public TypeData getVoidType() {
        return voidType;
    }

    public List<TypeMirror> getBoxedTypeMirrors() {
        return boxedTypeMirrors;
    }

    public List<TypeMirror> getPrimitiveTypeMirrors() {
        return primitiveTypeMirrors;
    }

    public List<TypeData> getTypes() {
        return types;
    }

    public TypeMirror getGenericType() {
        return genericType;
    }

    public TypeData getGenericTypeData() {
        TypeData result = types.get(types.size() - 1);
        assert result.getBoxedType() == genericType;
        return result;
    }

    public TypeData findType(String simpleName) {
        for (TypeData type : types) {
            if (ElementUtils.getSimpleName(type.getBoxedType()).equals(simpleName)) {
                return type;
            }
        }
        return null;
    }

    public TypeData findTypeData(TypeMirror type) {
        if (ElementUtils.typeEquals(voidType.getPrimitiveType(), type)) {
            return voidType;
        }

        int index = findType(type);
        if (index == -1) {
            return null;
        }
        return types.get(index);
    }

    public int findType(TypeMirror type) {
        for (int i = 0; i < types.size(); i++) {
            if (ElementUtils.typeEquals(types.get(i).getPrimitiveType(), type)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[template = " + ElementUtils.getSimpleName(getTemplateType()) + ", types = " + types + "]";
    }

    public List<ImplicitCastData> lookupByTargetType(TypeData targetType) {
        if (getImplicitCasts() == null) {
            return Collections.emptyList();
        }
        List<ImplicitCastData> foundCasts = new ArrayList<>();
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (cast.getTargetType().equals(targetType)) {
                foundCasts.add(cast);
            }
        }
        return foundCasts;
    }

    public ImplicitCastData lookupCast(TypeData sourceType, TypeData targetType) {
        if (getImplicitCasts() == null) {
            return null;
        }
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (cast.getSourceType().equals(sourceType) && cast.getTargetType().equals(targetType)) {
                return cast;
            }
        }
        return null;
    }

    public List<TypeData> lookupSourceTypes(TypeData type) {
        List<TypeData> sourceTypes = new ArrayList<>();
        sourceTypes.add(type);
        if (getImplicitCasts() != null) {
            for (ImplicitCastData cast : getImplicitCasts()) {
                if (cast.getTargetType() == type) {
                    sourceTypes.add(cast.getSourceType());
                }
            }
        }
        Collections.sort(sourceTypes);
        return sourceTypes;
    }

}
