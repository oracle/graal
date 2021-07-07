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
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SYNTHETIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ALOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.ARETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.DUP;
import static com.oracle.truffle.api.impl.asm.Opcodes.ILOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.NEW;
import static com.oracle.truffle.api.impl.asm.Opcodes.RETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.V1_8;

final class FieldBasedShapeGenerator<T> extends ShapeGenerator<T> {
    private final GeneratorClassLoader gcl;
    private final Class<?> storageSuperClass;
    private final Class<T> storageFactoryInterface;

    private FieldBasedShapeGenerator(GeneratorClassLoader gcl, Class<?> storageSuperClass, Class<T> storageFactoryInterface) {
        this.gcl = gcl;
        this.storageSuperClass = storageSuperClass;
        this.storageFactoryInterface = storageFactoryInterface;
    }

    @SuppressWarnings("unchecked")
    static <T> FieldBasedShapeGenerator<T> getShapeGenerator(GeneratorClassLoader gcl, Class<?> storageSuperClass, Class<T> storageFactoryInterface) {
        return new FieldBasedShapeGenerator<>(gcl, storageSuperClass, storageFactoryInterface);
    }

    @Override
    StaticShape<T> generateShape(StaticShape<T> parentShape, Collection<StaticProperty> staticProperties) {
        Class<?> generatedStorageClass = generateStorage(gcl, storageSuperClass, staticProperties);
        Class<? extends T> generatedFactoryClass = generateFactory(gcl, generatedStorageClass, storageFactoryInterface);
        for (StaticProperty staticProperty : staticProperties) {
            int offset = getObjectFieldOffset(generatedStorageClass, generateFieldName(staticProperty));
            staticProperty.initOffset(offset);
        }
        return FieldBasedStaticShape.create(generatedStorageClass, generatedFactoryClass);
    }

    private static int getObjectFieldOffset(Class<?> c, String fieldName) {
        try {
            return Math.toIntExact(UNSAFE.objectFieldOffset(c.getField(fieldName)));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getStorageConstructorDescriptor(Constructor<?> superConstructor) {
        return Type.getConstructorDescriptor(superConstructor);
    }

    private static void addStorageConstructors(ClassVisitor cv, Class<?> storageSuperClass, String storageSuperName) {
        for (Constructor<?> superConstructor : storageSuperClass.getDeclaredConstructors()) {
            String storageConstructorDescriptor = getStorageConstructorDescriptor(superConstructor);
            String superConstructorDescriptor = storageConstructorDescriptor;
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", storageConstructorDescriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            int var = 1;
            for (Class<?> constructorParameter : superConstructor.getParameterTypes()) {
                int loadOpcode = Type.getType(constructorParameter).getOpcode(ILOAD);
                mv.visitVarInsn(loadOpcode, var++);
            }
            mv.visitMethodInsn(INVOKESPECIAL, storageSuperName, "<init>", superConstructorDescriptor, false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(var + 1, var);
            mv.visitEnd();
        }
    }

    private static void addFactoryConstructor(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(II)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    private static void addFactoryMethods(ClassVisitor cv, Class<?> storageClass, Class<?> storageFactoryInterface) {
        for (Method m : storageFactoryInterface.getMethods()) {
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, m.getName(), Type.getMethodDescriptor(m), null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, Type.getInternalName(storageClass));
            mv.visitInsn(DUP);
            int maxStack = 2;
            StringBuilder constructorDescriptor = new StringBuilder();
            constructorDescriptor.append('(');
            Class<?>[] params = m.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                int loadOpcode = Type.getType(params[i]).getOpcode(ILOAD);
                mv.visitVarInsn(loadOpcode, i + 1);
                constructorDescriptor.append(Type.getDescriptor(params[i]));
                maxStack++;
            }
            constructorDescriptor.append(")V");
            String storageName = Type.getInternalName(storageClass);
            mv.visitMethodInsn(INVOKESPECIAL, storageName, "<init>", constructorDescriptor.toString(), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(maxStack, maxStack - 1);
            mv.visitEnd();
        }
    }

    private static Class<?> generateStorage(GeneratorClassLoader gcl, Class<?> storageSuperClass, Collection<StaticProperty> staticProperties) {
        String storageSuperName = Type.getInternalName(storageSuperClass);
        String storageName = generateStorageName();
        ClassWriter storageWriter = new ClassWriter(0);
        int storageAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC;
        storageWriter.visit(V1_8, storageAccess, storageName, null, storageSuperName, null);
        addStorageConstructors(storageWriter, storageSuperClass, storageSuperName);
        addStorageFields(storageWriter, staticProperties);
        storageWriter.visitEnd();
        return load(gcl, storageName, storageWriter.toByteArray());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> generateFactory(GeneratorClassLoader gcl, Class<?> storageClass, Class<T> storageFactoryInterface) {
        ClassWriter factoryWriter = new ClassWriter(0);
        int factoryAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        String factoryName = generateFactoryName(storageClass);
        factoryWriter.visit(V1_8, factoryAccess, factoryName, null, Type.getInternalName(Object.class), new String[]{Type.getInternalName(storageFactoryInterface)});
        addFactoryConstructor(factoryWriter);
        addFactoryMethods(factoryWriter, storageClass, storageFactoryInterface);
        factoryWriter.visitEnd();
        return (Class<? extends T>) load(gcl, factoryName, factoryWriter.toByteArray());
    }
}
