/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.dsl.processor.model;

import javax.lang.model.type.TypeMirror;

public class PropertyBuilder {

    private final String name;
    private TypeMirror type;
    private boolean hasObjectTypeGetter;
    private boolean hasShapeGetter;
    private boolean hasShapeSetter;
    private boolean hasGetter;
    private boolean hasSetter;
    private boolean hasUnsafeSetter;
    private boolean nullable;
    private boolean volatileSemantics;
    private boolean hasCompareAndSet;
    private boolean hasGetAndSet;
    private boolean hasIdentifier;
    private boolean isShapeProperty;

    public PropertyBuilder(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }

    public void setType(TypeMirror type) {
        this.type = type;
    }

    public void setHasObjectTypeGetter(boolean hasObjectTypeGetter) {
        this.hasObjectTypeGetter = hasObjectTypeGetter;
    }

    public void setHasShapeGetter(boolean hasShapeGetter) {
        this.hasShapeGetter = hasShapeGetter;
    }

    public void setHasShapeSetter(boolean hasShapeSetter) {
        this.hasShapeSetter = hasShapeSetter;
    }

    public void setHasGetter(boolean hasGetter) {
        this.hasGetter = hasGetter;
    }

    public void setHasSetter(boolean hasSetter) {
        this.hasSetter = hasSetter;
    }

    public void setHasUnsafeSetter(boolean hasUnsafeSetter) {
        this.hasUnsafeSetter = hasUnsafeSetter;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public void setVolatile(boolean volatileSemantics) {
        this.volatileSemantics = volatileSemantics;
    }

    public void setHasCompareAndSet(boolean hasCompareAndSet) {
        this.hasCompareAndSet = hasCompareAndSet;
    }

    public void setHasGetAndSet(boolean hasGetAndSet) {
        this.hasGetAndSet = hasGetAndSet;
    }

    public void setHasIdentifier(boolean hasIdentifier) {
        this.hasIdentifier = hasIdentifier;
    }

    public boolean isShapeProperty() {
        return isShapeProperty;
    }

    public void setIsShapeProperty(boolean isShapeProperty) {
        this.isShapeProperty = isShapeProperty;
    }

    public PropertyModel build() {
        return new PropertyModel(name, type, hasObjectTypeGetter, hasShapeGetter, hasShapeSetter,
                        hasGetter, hasSetter, hasUnsafeSetter, nullable, volatileSemantics, hasCompareAndSet,
                        hasGetAndSet, hasIdentifier, isShapeProperty);
    }

}
