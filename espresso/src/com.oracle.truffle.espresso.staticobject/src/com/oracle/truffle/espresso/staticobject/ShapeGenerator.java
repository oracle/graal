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

import com.oracle.truffle.api.impl.asm.ClassVisitor;
import com.oracle.truffle.api.impl.asm.FieldVisitor;
import com.oracle.truffle.api.impl.asm.Type;
import com.oracle.truffle.espresso.staticobject.StaticShapeBuilder.ExtendedProperty;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;

abstract class ShapeGenerator<T> {
    protected static final Unsafe UNSAFE = getUnsafe();
    private static final String DELIMITER = "$$";
    private static final AtomicInteger counter = new AtomicInteger();

    protected final Class<?> generatedStorageClass;
    protected final Class<? extends T> generatedFactoryClass;
    protected final Collection<ExtendedProperty> extendedProperties;
    protected final StaticShape<T> parentShape;

    ShapeGenerator(Class<?> generatedStorageClass, Class<? extends T> generatedFactoryClass, Collection<ExtendedProperty> extendedProperties, StaticShape<T> parentShape) {
        this.generatedStorageClass = generatedStorageClass;
        this.generatedFactoryClass = generatedFactoryClass;
        this.extendedProperties = extendedProperties;
        this.parentShape = parentShape;
    }

    abstract StaticShape<T> generateShape();

    @SuppressWarnings("unchecked")
    static <T> ShapeGenerator<T> getShapeGenerator(StaticShape<T> parentShape, Collection<ExtendedProperty> extendedProperties) {
        return ArrayBasedShapeGenerator.getShapeGenerator(parentShape, extendedProperties);
    }

    @SuppressWarnings("unchecked")
    static <T> ShapeGenerator<T> getShapeGenerator(Class<?> storageSuperClass, Class<T> storageFactoryInterface, Collection<ExtendedProperty> extendedProperties) {
        return ArrayBasedShapeGenerator.getShapeGenerator(storageSuperClass, storageFactoryInterface, extendedProperties);
    }

    static String generateStorageName(Class<?> storageSuperClass) {
        String internalStorageSuperClassName = Type.getInternalName(storageSuperClass);
        String baseName;
        int index = internalStorageSuperClassName.indexOf(DELIMITER);
        if (index == -1) {
            baseName = internalStorageSuperClassName + DELIMITER;
        } else {
            baseName = internalStorageSuperClassName.substring(0, index + DELIMITER.length());
        }
        return baseName + counter.incrementAndGet();
    }

    static String generateFactoryName(Class<?> generatedStorageClass) {
        return Type.getInternalName(generatedStorageClass) + DELIMITER + "Factory";
    }

    static void addStorageFields(ClassVisitor cv, Collection<ExtendedProperty> extendedProperties) {
        for (ExtendedProperty extendedProperty : extendedProperties) {
            int access = ACC_PUBLIC;
            if (extendedProperty.isFinal()) {
                access |= ACC_FINAL;
            }
            FieldVisitor fv = cv.visitField(access, extendedProperty.getName(), StaticPropertyKind.getDescriptor(extendedProperty.getProperty().getInternalKind()), null, null);
            fv.visitEnd();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Class<? extends T> load(String name, byte[] bytes, Class<T> referenceClass) {
        return (Class<T>) UNSAFE.defineClass(name, bytes, 0, bytes.length, referenceClass.getClassLoader(), referenceClass.getProtectionDomain());
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }
}
