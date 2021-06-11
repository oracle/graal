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

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.asm.ClassVisitor;
import com.oracle.truffle.api.impl.asm.FieldVisitor;
import com.oracle.truffle.api.impl.asm.Type;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;

abstract class ShapeGenerator<T> {
    protected static final Unsafe UNSAFE = getUnsafe();
    private static final boolean ARRAY_BASED_STORAGE = TruffleOptions.AOT || Boolean.getBoolean("com.oracle.truffle.espresso.staticobject.ArrayBasedStorage");
    private static final String DELIMITER = "$$";
    private static final AtomicInteger counter = new AtomicInteger();

    abstract StaticShape<T> generateShape(StaticShape<T> parentShape, Collection<StaticProperty> staticProperties);

    static <T> ShapeGenerator<T> getShapeGenerator(GeneratorClassLoader gcl, StaticShape<T> parentShape) {
        Class<?> parentStorageClass = parentShape.getStorageClass();
        Class<?> storageSuperclass = ARRAY_BASED_STORAGE ? parentStorageClass.getSuperclass() : parentStorageClass;
        return getShapeGenerator(gcl, storageSuperclass, parentShape.getFactoryInterface());
    }

    static <T> ShapeGenerator<T> getShapeGenerator(GeneratorClassLoader gcl, Class<?> storageSuperClass, Class<T> storageFactoryInterface) {
        if (ARRAY_BASED_STORAGE) {
            return ArrayBasedShapeGenerator.getShapeGenerator(gcl, storageSuperClass, storageFactoryInterface);
        } else {
            return FieldBasedShapeGenerator.getShapeGenerator(gcl, storageSuperClass, storageFactoryInterface);
        }
    }

    static String generateStorageName() {
        return ShapeGenerator.class.getPackage().getName().replace('.', '/') + "/GeneratedStaticObject" + DELIMITER + counter.incrementAndGet();
    }

    static String generateFactoryName(Class<?> generatedStorageClass) {
        return Type.getInternalName(generatedStorageClass) + DELIMITER + "Factory";
    }

    static String generateFieldName(StaticProperty property) {
        return property.getId();
    }

    static void addStorageFields(ClassVisitor cv, Collection<StaticProperty> staticProperties) {
        for (StaticProperty staticProperty : staticProperties) {
            addStorageField(cv, generateFieldName(staticProperty), staticProperty.getInternalKind(), staticProperty.storeAsFinal());
        }
    }

    static void addStorageField(ClassVisitor cv, String propertyName, byte internalKind, boolean storeAsFinal) {
        int access = storeAsFinal ? ACC_FINAL | ACC_PUBLIC : ACC_PUBLIC;
        FieldVisitor fv = cv.visitField(access, propertyName, StaticPropertyKind.getDescriptor(internalKind), null, null);
        fv.visitEnd();
    }

    @SuppressWarnings("unchecked")
    static <T> Class<? extends T> load(GeneratorClassLoader gcl, String internalName, byte[] bytes) {
        try {
            return (Class<T>) gcl.defineGeneratedClass(internalName.replace('/', '.'), bytes, 0, bytes.length);
        } catch (ClassFormatError e) {
            throw new RuntimeException(e);
        }
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
