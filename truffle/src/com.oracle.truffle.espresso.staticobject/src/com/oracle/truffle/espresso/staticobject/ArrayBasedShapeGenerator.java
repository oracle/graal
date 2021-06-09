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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.asm.ClassVisitor;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.Label;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.Type;
import org.graalvm.collections.Pair;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SYNTHETIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACONST_NULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ALOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.ANEWARRAY;
import static com.oracle.truffle.api.impl.asm.Opcodes.ARETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.ASTORE;
import static com.oracle.truffle.api.impl.asm.Opcodes.CHECKCAST;
import static com.oracle.truffle.api.impl.asm.Opcodes.DOUBLE;
import static com.oracle.truffle.api.impl.asm.Opcodes.DUP;
import static com.oracle.truffle.api.impl.asm.Opcodes.FLOAT;
import static com.oracle.truffle.api.impl.asm.Opcodes.F_FULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.GETFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.GOTO;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFLE;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFNONNULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ILOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.INTEGER;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKEVIRTUAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.LONG;
import static com.oracle.truffle.api.impl.asm.Opcodes.NEW;
import static com.oracle.truffle.api.impl.asm.Opcodes.NEWARRAY;
import static com.oracle.truffle.api.impl.asm.Opcodes.PUTFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.RETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.T_BYTE;
import static com.oracle.truffle.api.impl.asm.Opcodes.V1_8;

final class ArrayBasedShapeGenerator<T> extends ShapeGenerator<T> {
    private static final int UNINITIALIZED_NATIVE_OFFSET = -1;
    private static final ConcurrentHashMap<Pair<Class<?>, Class<?>>, ArrayBasedShapeGenerator<?>> generatorCache = new ConcurrentHashMap<>();
    private static final String[] ARRAY_SIZE_FIELDS = new String[]{"primitiveArraySize", "objectArraySize"};

    private final Class<?> generatedStorageClass;
    private final Class<? extends T> generatedFactoryClass;

    @CompilationFinal private int byteArrayOffset;
    @CompilationFinal private int objectArrayOffset;
    @CompilationFinal private int shapeOffset;

    static {
        if (TruffleOptions.AOT) {
            try {
                // When this class will be in truffle we will remove this code and introduce a
                // SubstrateVM feature that locates these classes at image build time.
                Class<?> defaultStorageSuperClass = Class.forName("com.oracle.truffle.espresso.runtime.StaticObject");
                Class<?> defaultStorageFactoryInterface = Class.forName("com.oracle.truffle.espresso.runtime.StaticObject$StaticObjectFactory");
                Class<?> defaultStorageClass = Class.forName("com.oracle.truffle.espresso.runtime.StaticObject$DefaultArrayBasedStaticObject");
                Class<?> defaultFactoryClass = Class.forName("com.oracle.truffle.espresso.runtime.StaticObject$DefaultArrayBasedStaticObjectFactory");
                // The offsets of the byte and object arrays cannot be computed at image build time.
                // They would refer to a Java object, not to a Native object.
                ArrayBasedShapeGenerator<?> sg = new ArrayBasedShapeGenerator<>(defaultStorageClass, defaultFactoryClass, UNINITIALIZED_NATIVE_OFFSET, UNINITIALIZED_NATIVE_OFFSET,
                                UNINITIALIZED_NATIVE_OFFSET);
                generatorCache.putIfAbsent(Pair.create(defaultStorageSuperClass, defaultStorageFactoryInterface), sg);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ArrayBasedShapeGenerator(Class<?> generatedStorageClass, Class<? extends T> generatedFactoryClass) {
        this(
                        generatedStorageClass,
                        generatedFactoryClass,
                        getObjectFieldOffset(generatedStorageClass, "primitive"),
                        getObjectFieldOffset(generatedStorageClass, "object"),
                        getObjectFieldOffset(generatedStorageClass, "shape"));
    }

    private ArrayBasedShapeGenerator(Class<?> generatedStorageClass, Class<? extends T> generatedFactoryClass, int byteArrayOffset, int objectArrayOffset, int shapeOffset) {
        this.generatedStorageClass = generatedStorageClass;
        this.generatedFactoryClass = generatedFactoryClass;
        this.byteArrayOffset = byteArrayOffset;
        this.objectArrayOffset = objectArrayOffset;
        this.shapeOffset = shapeOffset;
    }

    @SuppressWarnings("unchecked")
    static <T> ArrayBasedShapeGenerator<T> getShapeGenerator(GeneratorClassLoader gcl, Class<?> storageSuperClass, Class<T> storageFactoryInterface) {
        Pair<Class<?>, Class<?>> pair = Pair.create(storageSuperClass, storageFactoryInterface);
        ArrayBasedShapeGenerator<T> sg = (ArrayBasedShapeGenerator<T>) generatorCache.get(pair);
        if (sg == null) {
            assert !TruffleOptions.AOT;
            Class<?> generatedStorageClass = generateStorage(gcl, storageSuperClass);
            Class<? extends T> generatedFactoryClass = generateFactory(gcl, generatedStorageClass, storageFactoryInterface);
            sg = new ArrayBasedShapeGenerator<>(generatedStorageClass, generatedFactoryClass);
            ArrayBasedShapeGenerator<T> prevSg = (ArrayBasedShapeGenerator<T>) generatorCache.putIfAbsent(pair, sg);
            if (prevSg != null) {
                sg = prevSg;
            }
        } else if (TruffleOptions.AOT && sg.byteArrayOffset == UNINITIALIZED_NATIVE_OFFSET) {
            sg.byteArrayOffset = getObjectFieldOffset(sg.generatedStorageClass, "primitive");
            sg.objectArrayOffset = getObjectFieldOffset(sg.generatedStorageClass, "object");
            sg.shapeOffset = getObjectFieldOffset(sg.generatedStorageClass, "shape");
        }
        return sg;
    }

    private static int getObjectFieldOffset(Class<?> c, String fieldName) {
        try {
            return Math.toIntExact(UNSAFE.objectFieldOffset(c.getField(fieldName)));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    StaticShape<T> generateShape(StaticShape<T> parentShape, Collection<StaticProperty> staticProperties) {
        return ArrayBasedStaticShape.create(generatedStorageClass, generatedFactoryClass, (ArrayBasedStaticShape<T>) parentShape, staticProperties, byteArrayOffset, objectArrayOffset, shapeOffset);
    }

    private static String getStorageConstructorDescriptor(Constructor<?> superConstructor) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class<?> parameter : superConstructor.getParameterTypes()) {
            sb.append(Type.getDescriptor(parameter));
        }
        sb.append("Ljava/lang/Object;II");
        return sb.append(")V").toString();
    }

    private static Object getFrameLocal(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            if (clazz == Boolean.TYPE || clazz == Byte.TYPE || clazz == Character.TYPE || clazz == Integer.TYPE || clazz == Short.TYPE) {
                return INTEGER;
            } else if (clazz == Double.TYPE) {
                return DOUBLE;
            } else if (clazz == Float.TYPE) {
                return FLOAT;
            } else if (clazz == Long.TYPE) {
                return LONG;
            } else {
                throw new AssertionError();
            }
        } else {
            return Type.getInternalName(clazz);
        }
    }

    private static Object[] getFrameLocals(String storageName, Class<?>[] constructorParameters) {
        Object[] frameLocals = new Object[constructorParameters.length + 4];
        int idx = 0;
        frameLocals[idx++] = storageName; // this
        for (; idx <= constructorParameters.length; idx++) {
            frameLocals[idx] = getFrameLocal(constructorParameters[idx - 1]);
        }
        frameLocals[idx++] = "java/lang/Object"; // shape
        frameLocals[idx++] = INTEGER; // primitiveArraySize
        frameLocals[idx++] = INTEGER; // objectArraySize
        return frameLocals;
    }

    private static void addStorageConstructors(ClassVisitor cv, String storageName, Class<?> storageSuperClass, String storageSuperName) {
        for (Constructor<?> superConstructor : storageSuperClass.getDeclaredConstructors()) {
            String storageConstructorDescriptor = getStorageConstructorDescriptor(superConstructor);
            String superConstructorDescriptor = Type.getConstructorDescriptor(superConstructor);

            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", storageConstructorDescriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            int var = 1;
            Class<?>[] constructorParameters = superConstructor.getParameterTypes();
            for (int i = 0; i < constructorParameters.length; i++) {
                Class<?> constructorParameter = constructorParameters[i];
                Type parameterType = Type.getType(constructorParameter);
                int loadOpcode = parameterType.getOpcode(ILOAD);
                mv.visitVarInsn(loadOpcode, var++);
            }
            mv.visitMethodInsn(INVOKESPECIAL, storageSuperName, "<init>", superConstructorDescriptor, false);

            // Prepare array of frame locals for jumps
            Object[] frameLocals = getFrameLocals(storageName, constructorParameters);

            // this.shape = shape;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, var);
            mv.visitFieldInsn(PUTFIELD, storageName, "shape", "Ljava/lang/Object;");

            // primitive = primitiveArraySize > 0 ? new byte[primitiveArraySize] : null;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ILOAD, var + 1);
            Label lNoPrimitives = new Label();
            mv.visitJumpInsn(IFLE, lNoPrimitives);
            mv.visitVarInsn(ILOAD, var + 1);
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            Label lSetPrimitive = new Label();
            mv.visitJumpInsn(GOTO, lSetPrimitive);
            mv.visitLabel(lNoPrimitives);
            mv.visitFrame(F_FULL, frameLocals.length, frameLocals, 1, new Object[]{storageName});
            mv.visitInsn(ACONST_NULL);
            mv.visitLabel(lSetPrimitive);
            mv.visitFrame(F_FULL, frameLocals.length, frameLocals, 2, new Object[]{storageName, "[B"});
            mv.visitFieldInsn(PUTFIELD, storageName, "primitive", "[B");

            // object = objectArraySize > 0 ? new Object[objectArraySize] : null;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ILOAD, var + 2);
            Label lNoObjects = new Label();
            mv.visitJumpInsn(IFLE, lNoObjects);
            mv.visitVarInsn(ILOAD, var + 2);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            Label lSetObject = new Label();
            mv.visitJumpInsn(GOTO, lSetObject);
            mv.visitLabel(lNoObjects);
            mv.visitFrame(F_FULL, frameLocals.length, frameLocals, 1, new Object[]{storageName});
            mv.visitInsn(ACONST_NULL);
            mv.visitLabel(lSetObject);
            mv.visitFrame(F_FULL, frameLocals.length, frameLocals, 2, new Object[]{storageName, "[Ljava/lang/Object;"});
            mv.visitFieldInsn(PUTFIELD, storageName, "object", "[Ljava/lang/Object;");

            mv.visitInsn(RETURN);
            mv.visitMaxs(Math.max(var, 3), var + 3);

            mv.visitEnd();
        }
    }

    private static Method getCloneMethod(Class<?> storageSuperClass) {
        for (Class<?> clazz = storageSuperClass; clazz != null; clazz = clazz.getSuperclass()) {
            try {
                return clazz.getDeclaredMethod("clone");
            } catch (NoSuchMethodException e) {
                // Swallow the error, check the super class
            }
        }
        throw new RuntimeException("Should not reach here");
    }

    private static String[] getCloneMethodExceptions(Method cloneMethod) {
        return Arrays.stream(cloneMethod.getExceptionTypes()).map(c -> Type.getInternalName(c)).toArray(String[]::new);
    }

    private static void addCloneMethod(Class<?> storageSuperClass, ClassVisitor cv, String className) {
        // Prepare array of frame locals for jumps
        Object[] frameLocals = new Object[]{className, className};

        Method superCloneMethod = getCloneMethod(storageSuperClass);
        String superCloneMethodDescriptor = Type.getMethodDescriptor(superCloneMethod);
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "clone", superCloneMethodDescriptor, null, getCloneMethodExceptions(superCloneMethod));
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(storageSuperClass), "clone", superCloneMethodDescriptor, false);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitVarInsn(ASTORE, 1);

        // clone.primitive = (primitive == null ? null : (byte[]) primitive.clone());
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "primitive", "[B");
        Label lHasPrimitives = new Label();
        mv.visitJumpInsn(IFNONNULL, lHasPrimitives);
        mv.visitInsn(ACONST_NULL);
        Label lSetPrimitive = new Label();
        mv.visitJumpInsn(GOTO, lSetPrimitive);
        mv.visitLabel(lHasPrimitives);
        mv.visitFrame(Opcodes.F_FULL, 2, frameLocals, 1, new Object[]{className});
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "primitive", "[B");
        mv.visitMethodInsn(INVOKEVIRTUAL, "[B", "clone", "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(CHECKCAST, "[B");
        mv.visitTypeInsn(CHECKCAST, "[B");
        mv.visitLabel(lSetPrimitive);
        mv.visitFrame(Opcodes.F_FULL, 2, frameLocals, 2, new Object[]{className, "[B"});
        mv.visitFieldInsn(PUTFIELD, className, "primitive", "[B");

        // clone.object = (object == null ? null : (Object[]) object.clone());
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "object", "[Ljava/lang/Object;");
        Label lHasObjects = new Label();
        mv.visitJumpInsn(IFNONNULL, lHasObjects);
        mv.visitInsn(ACONST_NULL);
        Label lSetObject = new Label();
        mv.visitJumpInsn(GOTO, lSetObject);
        mv.visitLabel(lHasObjects);
        mv.visitFrame(Opcodes.F_FULL, 2, frameLocals, 1, new Object[]{className});
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, "object", "[Ljava/lang/Object;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "[Ljava/lang/Object;", "clone", "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
        mv.visitLabel(lSetObject);
        mv.visitFrame(Opcodes.F_FULL, 2, frameLocals, 2, new Object[]{className, "[Ljava/lang/Object;"});
        mv.visitFieldInsn(PUTFIELD, className, "object", "[Ljava/lang/Object;");

        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private static void addFactoryFields(ClassVisitor cv) {
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "shape", "Ljava/lang/Object;", null, null).visitEnd();
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "primitiveArraySize", "I", null, null).visitEnd();
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "objectArraySize", "I", null, null).visitEnd();
    }

    private static void addFactoryConstructor(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/Object;II)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, className, "shape", "Ljava/lang/Object;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitFieldInsn(PUTFIELD, className, "primitiveArraySize", "I");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitFieldInsn(PUTFIELD, className, "objectArraySize", "I");
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 4);
        mv.visitEnd();
    }

    private static void addFactoryMethods(ClassVisitor cv, Class<?> storageClass, Class<?> storageFactoryInterface, String factoryName) {
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
            int maxLocals = maxStack - 1;

            constructorDescriptor.append("Ljava/lang/Object;");
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "shape", "Ljava/lang/Object;");
            maxStack++;
            for (String fieldName : ARRAY_SIZE_FIELDS) {
                constructorDescriptor.append("I");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, factoryName, fieldName, "I");
                maxStack++;
            }

            constructorDescriptor.append(")V");
            String storageName = Type.getInternalName(storageClass);
            mv.visitMethodInsn(INVOKESPECIAL, storageName, "<init>", constructorDescriptor.toString(), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(maxStack, maxLocals);
            mv.visitEnd();
        }
    }

    private static Class<?> generateStorage(GeneratorClassLoader gcl, Class<?> storageSuperClass) {
        String storageSuperName = Type.getInternalName(storageSuperClass);
        String storageName = generateStorageName();

        ClassWriter storageWriter = new ClassWriter(0);
        int storageAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC;
        storageWriter.visit(V1_8, storageAccess, storageName, null, storageSuperName, null);
        addStorageConstructors(storageWriter, storageName, storageSuperClass, storageSuperName);
        addStorageField(storageWriter, "primitive", StaticPropertyKind.BYTE_ARRAY, true);
        addStorageField(storageWriter, "object", StaticPropertyKind.OBJECT_ARRAY, true);
        addStorageField(storageWriter, "shape", StaticPropertyKind.Object.toByte(), true);
        if (Cloneable.class.isAssignableFrom(storageSuperClass)) {
            addCloneMethod(storageSuperClass, storageWriter, storageName);
        }
        storageWriter.visitEnd();
        return load(gcl, storageName, storageWriter.toByteArray());
    }

    private static <T> Class<? extends T> generateFactory(GeneratorClassLoader gcl, Class<?> storageClass, Class<T> storageFactoryInterface) {
        ClassWriter factoryWriter = new ClassWriter(0);
        int factoryAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        String factoryName = generateFactoryName(storageClass);
        factoryWriter.visit(V1_8, factoryAccess, factoryName, null, Type.getInternalName(Object.class), new String[]{Type.getInternalName(storageFactoryInterface)});
        addFactoryFields(factoryWriter);
        addFactoryConstructor(factoryWriter, factoryName);
        addFactoryMethods(factoryWriter, storageClass, storageFactoryInterface, factoryName);
        factoryWriter.visitEnd();
        return load(gcl, factoryName, factoryWriter.toByteArray());
    }
}
