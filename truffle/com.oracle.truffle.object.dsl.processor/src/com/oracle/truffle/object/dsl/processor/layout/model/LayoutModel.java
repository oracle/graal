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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LayoutModel {

    private final String objectTypeSuperclass;
    private final LayoutModel superLayout;
    private final String name;
    private final String packageName;
    private final String interfaceFullName;
    private final boolean hasObjectTypeGuard;
    private final boolean hasObjectGuard;
    private final boolean hasDynamicObjectGuard;
    private final List<PropertyModel> properties;
    private final boolean hasShapeProperties;

    public LayoutModel(String objectTypeSuperclass, LayoutModel superLayout, String name, String packageName,
                    boolean hasObjectTypeGuard, boolean hasObjectGuard, boolean hasDynamicObjectGuard,
                    Collection<PropertyModel> properties, String interfaceFullName,
                    boolean hasShapeProperties) {
        // assert objectTypeSuperclass != null;
        // assert name != null;
        // assert packageName != null;
        // assert interfaceFullName != null;
        // assert properties != null;

        this.objectTypeSuperclass = objectTypeSuperclass;
        this.superLayout = superLayout;
        this.name = name;
        this.packageName = packageName;
        this.interfaceFullName = interfaceFullName;
        this.hasObjectTypeGuard = hasObjectTypeGuard;
        this.hasObjectGuard = hasObjectGuard;
        this.hasDynamicObjectGuard = hasDynamicObjectGuard;
        this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
        this.hasShapeProperties = hasShapeProperties;
    }

    public String getObjectTypeSuperclass() {
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

    public boolean hasObjectGuard() {
        return hasObjectGuard;
    }

    public boolean hasDynamicObjectGuard() {
        return hasDynamicObjectGuard;
    }

    public List<PropertyModel> getProperties() {
        return properties;
    }

    public List<PropertyModel> getNonShapeProperties() {
        final List<PropertyModel> nonShapeProperties = new ArrayList<>();

        for (PropertyModel property : getProperties()) {
            if (!property.isShapeProperty()) {
                nonShapeProperties.add(property);
            }
        }

        return nonShapeProperties;
    }

    public List<PropertyModel> getShapeProperties() {
        final List<PropertyModel> shapeProperties = new ArrayList<>();

        for (PropertyModel property : getProperties()) {
            if (property.isShapeProperty()) {
                shapeProperties.add(property);
            }
        }

        return shapeProperties;
    }

    public List<PropertyModel> getAllProperties() {
        final List<PropertyModel> allProperties = new ArrayList<>();

        if (superLayout != null) {
            allProperties.addAll(superLayout.getAllProperties());
        }

        allProperties.addAll(properties);

        return allProperties;
    }

    public List<PropertyModel> getAllNonShapeProperties() {
        final List<PropertyModel> allNonShapeProperties = new ArrayList<>();

        for (PropertyModel property : getAllProperties()) {
            if (!property.isShapeProperty()) {
                allNonShapeProperties.add(property);
            }
        }

        return allNonShapeProperties;
    }

    public List<PropertyModel> getInheritedShapeProperties() {
        final List<PropertyModel> inheritedShapeProperties = new ArrayList<>();

        for (PropertyModel property : getAllProperties()) {
            if (!properties.contains(property) && property.isShapeProperty()) {
                inheritedShapeProperties.add(property);
            }
        }

        return inheritedShapeProperties;
    }

    public List<PropertyModel> getAllShapeProperties() {
        final List<PropertyModel> allShapeProperties = new ArrayList<>();

        for (PropertyModel property : getAllProperties()) {
            if (property.isShapeProperty()) {
                allShapeProperties.add(property);
            }
        }

        return allShapeProperties;
    }

    public boolean hasShapeProperties() {
        if (superLayout != null && superLayout.hasShapeProperties()) {
            return true;
        }

        return hasShapeProperties;
    }

    public boolean hasNonShapeProperties() {
        return !getAllNonShapeProperties().isEmpty();
    }

    public boolean hasObjectTypeGuard() {
        return hasObjectTypeGuard;
    }
}
