/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class TypeSystemData extends Template {

    private final List<ImplicitCastData> implicitCasts = new ArrayList<>();
    private final List<TypeCastData> casts = new ArrayList<>();
    private final List<TypeCheckData> checks = new ArrayList<>();
    private final List<TypeMirror> legacyTypes = new ArrayList<>();

    private Set<String> legacyTypeIds;

    private final boolean isDefault;

    public TypeSystemData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, boolean isDefault) {
        super(context, templateType, annotation);
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }

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
        return ElementUtils.uniqueSortedTypes(sourceTypes, true);
    }

    public List<TypeMirror> lookupSourceTypes(TypeMirror targetType) {
        List<TypeMirror> sourceTypes = new ArrayList<>();
        sourceTypes.add(targetType);
        for (ImplicitCastData cast : getImplicitCasts()) {
            if (ElementUtils.typeEquals(cast.getTargetType(), targetType)) {
                sourceTypes.add(cast.getSourceType());
            }
        }
        return ElementUtils.uniqueSortedTypes(sourceTypes, true);
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
