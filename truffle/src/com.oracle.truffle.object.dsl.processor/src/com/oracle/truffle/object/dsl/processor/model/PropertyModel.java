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

import com.oracle.truffle.object.dsl.processor.LayoutParser;

public class PropertyModel {

    private String name;
    private final TypeMirror type;
    private final boolean hasObjectTypeGetter;
    private final boolean hasShapeGetter;
    private final boolean hasShapeSetter;
    private final boolean hasGetter;
    private final boolean hasSetter;
    private final boolean hasUnsafeSetter;
    private final boolean nullable;
    private final boolean volatileSemantics;
    private final boolean hasCompareAndSet;
    private final boolean hasGetAndSet;
    private final boolean hasIdentifier;
    private final boolean isShapeProperty;

    public PropertyModel(String name, TypeMirror type, boolean hasObjectTypeGetter, boolean hasShapeGetter,
                    boolean hasShapeSetter, boolean hasGetter, boolean hasSetter, boolean hasUnsafeSetter,
                    boolean nullable, boolean volatileSemantics, boolean hasCompareAndSet, boolean hasGetAndSet,
                    boolean hasIdentifier, boolean isShapeProperty) {
        this.name = name;
        this.hasObjectTypeGetter = hasObjectTypeGetter;
        this.hasShapeGetter = hasShapeGetter;
        this.hasShapeSetter = hasShapeSetter;
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

    public void fixName(String realName) {
        this.name = realName;
    }

    public boolean hasGeneratedName() {
        return LayoutParser.isGeneratedName(name);
    }

    public TypeMirror getType() {
        return type;
    }

    public boolean hasObjectTypeGetter() {
        return hasObjectTypeGetter;
    }

    public boolean hasShapeSetter() {
        return hasShapeSetter;
    }

    public boolean hasShapeGetter() {
        return hasShapeGetter;
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

    @Override
    public String toString() {
        return name + " type=" + type + " shape=" + isShapeProperty;
    }

}
