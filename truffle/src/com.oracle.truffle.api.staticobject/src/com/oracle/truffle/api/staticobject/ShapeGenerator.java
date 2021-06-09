/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject;

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
    private static final boolean ARRAY_BASED_STORAGE = TruffleOptions.AOT || Boolean.getBoolean("com.oracle.truffle.api.staticobject.ArrayBasedStorage");
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
