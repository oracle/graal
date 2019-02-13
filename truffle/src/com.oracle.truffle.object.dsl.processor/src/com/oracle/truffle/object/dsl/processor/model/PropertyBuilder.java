/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
