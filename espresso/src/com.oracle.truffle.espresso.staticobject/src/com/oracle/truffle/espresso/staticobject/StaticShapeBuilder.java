/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Objects;

public final class StaticShapeBuilder {
    private final HashMap<String, ExtendedProperty> extendedProperties = new HashMap<>();

    StaticShapeBuilder() {
    }

    public StaticShapeBuilder property(StaticProperty property, String name, boolean isFinal) {
        Objects.requireNonNull(property);
        if (extendedProperties.containsKey(name)) {
            throw new IllegalArgumentException("This builder already contains a property named '" + name + "'");
        }
        extendedProperties.put(name, new ExtendedProperty(property, name, isFinal));
        return this;
    }

    public StaticShape<DefaultStaticObject.DefaultStaticObjectFactory> build() {
        // The classloader that loaded the default superClass must be able to load the default
        // factory.
        // Therefore, we can't use java.lang.Object as default superClass.
        return build(DefaultStaticObject.class, DefaultStaticObject.DefaultStaticObjectFactory.class);
    }

    public <T> StaticShape<T> build(StaticShape<T> parentShape) {
        Objects.requireNonNull(parentShape);
        ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(parentShape, extendedProperties.values());
        return build(sg);

    }

    public <T> StaticShape<T> build(Class<?> superClass, Class<T> factoryInterface) {
        validate(factoryInterface, superClass);
        ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(superClass, factoryInterface, extendedProperties.values());
        return build(sg);
    }

    private <T> StaticShape<T> build(ShapeGenerator<T> sg) {
        StaticShape<T> shape = sg.generateShape();
        for (ExtendedProperty extendedProperty : extendedProperties.values()) {
            extendedProperty.property.initShape(shape);
        }
        return shape;
    }

    private static void validate(Class<?> storageFactoryInterface, Class<?> storageSuperClass) {
        if (!storageFactoryInterface.isInterface()) {
            throw new RuntimeException(storageFactoryInterface.getName() + " must be an interface.");
        }
        for (Method m : storageFactoryInterface.getMethods()) {
            if (!m.getReturnType().isAssignableFrom(storageSuperClass)) {
                throw new RuntimeException("The return type of '" + m.getReturnType().getName() + " " + storageFactoryInterface.getName() + "." + m.toString() + "' is not assignable from '" +
                                storageSuperClass.getName() + "'");
            }
            try {
                storageSuperClass.getDeclaredConstructor(m.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Method '" + m.toString() + "' does not match any constructor in '" + storageSuperClass.getName() + "'", e);
            }
        }
    }

    static final class ExtendedProperty {
        private final StaticProperty property;
        private final String name;
        private final boolean isFinal;

        ExtendedProperty(StaticProperty property, String name, boolean isFinal) {
            this.property = property;
            this.name = name;
            this.isFinal = isFinal;
        }

        StaticProperty getProperty() {
            return property;
        }

        String getName() {
            return name;
        }

        boolean isFinal() {
            return isFinal;
        }
    }
}
