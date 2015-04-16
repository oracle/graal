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

import com.oracle.truffle.api.dsl.internal.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;

public class TypeSystemData extends Template {

    private final List<ImplicitCastData> implicitCasts = new ArrayList<>();
    private final List<TypeCastData> casts = new ArrayList<>();
    private final List<TypeCheckData> checks = new ArrayList<>();
    private final List<TypeMirror> legacyTypes = new ArrayList<>();

    private Set<String> legacyTypeIds;

    private final boolean isDefault;
    private final DSLOptions options;

    public TypeSystemData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, DSLOptions options, boolean isDefault) {
        super(context, templateType, annotation);
        this.options = options;
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public DSLOptions getOptions() {
        return options;
    }

    @Override
    public TypeSystemData getTypeSystem() {
        return this;
    }

    public List<TypeMirror> getLegacyTypes() {
        return legacyTypes;
    }

    public TypeCastData getCast(TypeMirror targetType) {
        for (TypeCastData cast : casts) {
            if (ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                return cast;
            }
        }
        return null;
    }

    public TypeCheckData getCheck(TypeMirror type) {
        for (TypeCheckData check : checks) {
            if (ElementUtils.typeEquals(check.getCheckedType(), type)) {
                return check;
            }
        }
        return null;
    }

    public List<ImplicitCastData> getImplicitCasts() {
        return implicitCasts;
    }

    public List<TypeCastData> getCasts() {
        return casts;
    }

    public List<TypeCheckData> getChecks() {
        return checks;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> sinks = new ArrayList<>();
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

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[template = " + ElementUtils.getSimpleName(getTemplateType()) + "]";
    }

    public List<ImplicitCastData> lookupByTargetType(TypeMirror targetType) {
        if (getImplicitCasts() == null) {
            return Collections.emptyList();
        }
        List<ImplicitCastData> foundCasts = new ArrayList<>();
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                foundCasts.add(cast);
            }
        }
        return foundCasts;
    }

    public ImplicitCastData lookupCast(TypeMirror sourceType, TypeMirror targetType) {
        if (getImplicitCasts() == null) {
            return null;
        }
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (ElementUtils.typeEquals(cast.getSourceType(), sourceType) && ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                return cast;
            }
        }
        return null;
    }

    public boolean hasImplicitSourceTypes(TypeMirror targetType) {
        if (getImplicitCasts() == null) {
            return false;
        }
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                return true;
            }
        }
        return false;
    }

    public List<TypeMirror> lookupTargetTypes() {
        List<TypeMirror> sourceTypes = new ArrayList<>();
        for (ImplicitCastData cast : getImplicitCasts()) {
            sourceTypes.add(cast.getTargetType());
        }
        return ElementUtils.uniqueSortedTypes(sourceTypes);
    }

    public List<TypeMirror> lookupSourceTypes(TypeMirror targetType) {
        List<TypeMirror> sourceTypes = new ArrayList<>();
        sourceTypes.add(targetType);
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                sourceTypes.add(cast.getSourceType());
            }
        }
        return sourceTypes;
    }

    public boolean isImplicitSubtypeOf(TypeMirror source, TypeMirror target) {
        List<ImplicitCastData> targetCasts = lookupByTargetType(target);
        for (ImplicitCastData cast : targetCasts) {
            if (ElementUtils.isSubtype(boxType(source), boxType(cast.getSourceType()))) {
                return true;
            }
        }
        return ElementUtils.isSubtype(boxType(source), boxType(target));
    }

    public TypeMirror boxType(TypeMirror type) {
        return ElementUtils.boxType(getContext(), type);
    }

    public boolean hasType(TypeMirror type) {
        if (legacyTypeIds == null) {
            legacyTypeIds = new HashSet<>();
            for (TypeMirror legacyType : legacyTypes) {
                legacyTypeIds.add(ElementUtils.getTypeId(legacyType));
            }
        }
        return legacyTypeIds.contains(ElementUtils.getTypeId(type));
    }

}
