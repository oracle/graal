/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class LayoutModel {

    private final TypeMirror objectTypeSuperclass;
    private final LayoutModel superLayout;
    private final String name;
    private final String packageName;
    private final String interfaceFullName;
    private final boolean hasObjectTypeGuard;
    private final boolean hasObjectGuard;
    private final boolean hasDynamicObjectGuard;
    private final boolean hasBuilder;
    private final List<PropertyModel> properties;
    private final List<VariableElement> implicitCasts;
    private final TypeMirror dispatch;

    public LayoutModel(TypeMirror objectTypeSuperclass, LayoutModel superLayout, String name, String packageName,
                    boolean hasObjectTypeGuard, boolean hasObjectGuard, boolean hasDynamicObjectGuard, boolean hasBuilder,
                    Collection<PropertyModel> properties, String interfaceFullName, Collection<VariableElement> implicitCasts,
                    TypeMirror dispatch) {
        this.objectTypeSuperclass = objectTypeSuperclass;
        this.superLayout = superLayout;
        this.name = name;
        this.packageName = packageName;
        this.interfaceFullName = interfaceFullName;
        this.hasObjectTypeGuard = hasObjectTypeGuard;
        this.hasObjectGuard = hasObjectGuard;
        this.hasDynamicObjectGuard = hasDynamicObjectGuard;
        this.hasBuilder = hasBuilder;
        this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
        this.implicitCasts = Collections.unmodifiableList(new ArrayList<>(implicitCasts));
        this.dispatch = dispatch;
    }

    public TypeMirror getObjectTypeSuperclass() {
        return objectTypeSuperclass;
    }

    public LayoutModel getSuperLayout() {
        return superLayout;
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getInterfaceFullName() {
        return interfaceFullName;
    }

    public boolean hasObjectTypeGuard() {
        return hasObjectTypeGuard;
    }

    public boolean hasObjectGuard() {
        return hasObjectGuard;
    }

    public boolean hasDynamicObjectGuard() {
        return hasDynamicObjectGuard;
    }

    public boolean hasBuilder() {
        return hasBuilder;
    }

    public boolean hasInstanceProperties() {
        return !getAllInstanceProperties().isEmpty();
    }

    public boolean hasShapeProperties() {
        return !getAllShapeProperties().isEmpty();
    }

    public boolean hasProperty(String propertyName) {
        for (PropertyModel property : getAllProperties()) {
            if (property.getName().equals(propertyName)) {
                return true;
            }
        }

        return false;
    }

    public List<PropertyModel> getProperties() {
        return selectProperties(true, true, false, true, false);
    }

    public List<PropertyModel> getInstanceProperties() {
        return selectProperties(true, false, false, true, false);
    }

    public List<PropertyModel> getShapeProperties() {
        return selectProperties(false, true, false, true, false);
    }

    public List<PropertyModel> getAllProperties() {
        return selectProperties(true, true, false, true, true);
    }

    public List<PropertyModel> getAllInstanceProperties() {
        return selectProperties(true, false, false, true, true);
    }

    public List<PropertyModel> getInheritedShapeProperties() {
        return selectProperties(false, true, false, false, true);
    }

    public List<PropertyModel> getAllShapeProperties() {
        return selectProperties(false, true, false, true, true);
    }

    public boolean hasVolatileProperties() {
        return !selectProperties(true, true, true, true, false).isEmpty();
    }

    public boolean hasNonNullableInstanceProperties() {
        for (PropertyModel property : properties) {
            if (!property.isNullable() && property.isInstanceProperty()) {
                return true;
            }
        }

        return false;
    }

    public boolean hasFinalInstanceProperties() {
        for (PropertyModel property : properties) {
            if (property.isFinal() && property.isInstanceProperty()) {
                return true;
            }
        }

        return false;
    }

    public boolean hasGettersOrSetters() {
        for (PropertyModel property : properties) {
            if (property.hasGetter() || property.hasSetter()) {
                return true;
            }
        }

        return false;
    }

    public List<VariableElement> getImplicitCasts() {
        return implicitCasts;
    }

    private List<PropertyModel> selectProperties(
                    boolean instance,
                    boolean shape,
                    boolean onlyVolatile,
                    boolean declared,
                    boolean inherited) {
        final List<PropertyModel> selectedProperties = new ArrayList<>();

        if (inherited && superLayout != null) {
            selectedProperties.addAll(superLayout.selectProperties(instance, shape, onlyVolatile, true, inherited));
        }

        if (declared) {
            for (PropertyModel property : properties) {
                if (property.isInstanceProperty() && !instance) {
                    continue;
                }

                if (property.isShapeProperty() && !shape) {
                    continue;
                }

                if (!property.isVolatile() && onlyVolatile) {
                    continue;
                }

                selectedProperties.add(property);
            }
        }

        return Collections.unmodifiableList(selectedProperties);
    }

    public TypeMirror getDispatch() {
        return dispatch;
    }
}
