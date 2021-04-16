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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;

abstract class ShapeGenerator<T> {
    protected static final Unsafe UNSAFE = getUnsafe();
    private static final boolean FIELD_BASED_STORAGE = Boolean.getBoolean("com.oracle.truffle.espresso.staticobject.FieldBasedStorage");
    private static final String DELIMITER = "$$";
    private static final AtomicInteger counter = new AtomicInteger();
    private static final int JAVA_SPEC_VERSION;
    private static final Method DEFINE_CLASS;

    static {
        String value = System.getProperty("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        JAVA_SPEC_VERSION = Integer.parseInt(value);
        try {
            if (JAVA_SPEC_VERSION == 8) {
                DEFINE_CLASS = Unsafe.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ClassLoader.class, ProtectionDomain.class);
            } else {
                DEFINE_CLASS = Lookup.class.getDeclaredMethod("defineClass", byte[].class);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    abstract StaticShape<T> generateShape(StaticShape<T> parentShape, Collection<ExtendedProperty> extendedProperties);

    static <T> ShapeGenerator<T> getShapeGenerator(StaticShape<T> parentShape) {
        Class<?> parentStorageClass = parentShape.getStorageClass();
        Class<?> storageSuperclass = FIELD_BASED_STORAGE ? parentStorageClass : parentStorageClass.getSuperclass();
        return getShapeGenerator(storageSuperclass, parentShape.getFactoryInterface());
    }

    static <T> ShapeGenerator<T> getShapeGenerator(Class<?> storageSuperClass, Class<T> storageFactoryInterface) {
        if (FIELD_BASED_STORAGE) {
            return FieldBasedShapeGenerator.getShapeGenerator(storageSuperClass, storageFactoryInterface);
        } else {
            return ArrayBasedShapeGenerator.getShapeGenerator(storageSuperClass, storageFactoryInterface);
        }
    }

    static String generateStorageName() {
        return ShapeGenerator.class.getPackage().getName().replace('.', '/') + "/GeneratedStaticObject" + DELIMITER + counter.incrementAndGet();
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
        Object clazz;
        try {
            if (JAVA_SPEC_VERSION == 8) {
                clazz = DEFINE_CLASS.invoke(UNSAFE, name, bytes, 0, bytes.length, referenceClass.getClassLoader(), referenceClass.getProtectionDomain());
            } else {
                clazz = DEFINE_CLASS.invoke(MethodHandles.lookup(), bytes);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return (Class<T>) clazz;
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
