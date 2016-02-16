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
package com.oracle.truffle.object.dsl.processor.layout.model;

import javax.lang.model.type.TypeMirror;

public class PropertyModel {

    private final String name;
    private final boolean hasObjectTypeGetter;
    private final boolean hasFactoryGetter;
    private final boolean hasFactorySetter;
    private final boolean hasGetter;
    private final boolean hasSetter;
    private final boolean hasUnsafeSetter;
    private final TypeMirror type;
    private final boolean nullable;
    private final boolean volatileSemantics;
    private final boolean hasCompareAndSet;
    private final boolean hasGetAndSet;
    private final boolean hasIdentifier;
    private final boolean isShapeProperty;

    public PropertyModel(String name, boolean hasObjectTypeGetter, boolean hasFactoryGetter, boolean hasFactorySetter,
                    boolean hasGetter, boolean hasSetter, boolean hasUnsafeSetter,
                    TypeMirror type, boolean nullable,
                    boolean volatileSemantics, boolean hasCompareAndSet, boolean hasGetAndSet,
                    boolean hasIdentifier, boolean isShapeProperty) {
        // assert name != null;
        // assert type != null;

        // if (hasFactoryGetter || hasFactorySetter || hasObjectTypeGetter) {
        // assert isShapeProperty;
        // }

        // assert !(volatileSemantics && isShapeProperty);
        // assert !(volatileSemantics && hasUnsafeSetter);

        this.name = name;
        this.hasObjectTypeGetter = hasObjectTypeGetter;
        this.hasFactoryGetter = hasFactoryGetter;
        this.hasFactorySetter = hasFactorySetter;
        this.hasGetter = hasGetter;
        this.hasSetter = hasSetter;
        this.hasUnsafeSetter = hasUnsafeSetter;
        this.type = type;
        this.nullable = nullable;
        this.volatileSemantics = volatileSemantics;
        this.hasCompareAndSet = hasCompareAndSet;
        this.hasGetAndSet = hasGetAndSet;
        this.hasIdentifier = hasIdentifier;
        this.isShapeProperty = isShapeProperty;
    }

    public String getName() {
        return name;
    }

    public boolean hasObjectTypeGetter() {
        return hasObjectTypeGetter;
    }

    public boolean hasFactorySetter() {
        return hasFactorySetter;
    }

    public boolean hasFactoryGetter() {
        return hasFactoryGetter;
    }

    public boolean hasGetter() {
        return hasGetter;
    }

    public boolean hasSetter() {
        return hasSetter;
    }

    public boolean isFinal() {
        return !(hasSetter || hasGetAndSet || hasCompareAndSet);
    }

    public boolean hasUnsafeSetter() {
        return hasUnsafeSetter;
    }

    public TypeMirror getType() {
        return type;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isVolatile() {
        return volatileSemantics;
    }

    public boolean hasCompareAndSet() {
        return hasCompareAndSet;
    }

    public boolean hasGetAndSet() {
        return hasGetAndSet;
    }

    public boolean hasIdentifier() {
        return hasIdentifier;
    }

    public boolean isInstanceProperty() {
        return !isShapeProperty;
    }

    public boolean isShapeProperty() {
        return isShapeProperty;
    }
}
