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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.object.Layout.ImplicitCast;

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
    private final List<ImplicitCast> implicitCasts;

    public LayoutModel(TypeMirror objectTypeSuperclass, LayoutModel superLayout, String name, String packageName,
                    boolean hasObjectTypeGuard, boolean hasObjectGuard, boolean hasDynamicObjectGuard, boolean hasBuilder,
                    Collection<PropertyModel> properties, String interfaceFullName, Collection<ImplicitCast> implicitCasts) {
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

    public List<ImplicitCast> getImplicitCasts() {
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

}
