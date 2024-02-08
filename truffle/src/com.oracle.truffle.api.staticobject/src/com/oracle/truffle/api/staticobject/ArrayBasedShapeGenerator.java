/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.asm.AnnotationVisitor;
import com.oracle.truffle.api.impl.asm.ClassVisitor;
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.Label;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.Type;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;

import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_FINAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PRIVATE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_PUBLIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_STATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SUPER;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACC_SYNTHETIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.ACONST_NULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ALOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.ANEWARRAY;
import static com.oracle.truffle.api.impl.asm.Opcodes.ARETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.ASTORE;
import static com.oracle.truffle.api.impl.asm.Opcodes.ATHROW;
import static com.oracle.truffle.api.impl.asm.Opcodes.CHECKCAST;
import static com.oracle.truffle.api.impl.asm.Opcodes.DOUBLE;
import static com.oracle.truffle.api.impl.asm.Opcodes.DUP;
import static com.oracle.truffle.api.impl.asm.Opcodes.FLOAT;
import static com.oracle.truffle.api.impl.asm.Opcodes.F_FULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.GETFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.GETSTATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.GOTO;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFEQ;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFLE;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFNE;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFNONNULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.IFNULL;
import static com.oracle.truffle.api.impl.asm.Opcodes.ILOAD;
import static com.oracle.truffle.api.impl.asm.Opcodes.INTEGER;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKESPECIAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKESTATIC;
import static com.oracle.truffle.api.impl.asm.Opcodes.INVOKEVIRTUAL;
import static com.oracle.truffle.api.impl.asm.Opcodes.LONG;
import static com.oracle.truffle.api.impl.asm.Opcodes.NEW;
import static com.oracle.truffle.api.impl.asm.Opcodes.NEWARRAY;
import static com.oracle.truffle.api.impl.asm.Opcodes.POP;
import static com.oracle.truffle.api.impl.asm.Opcodes.PUTFIELD;
import static com.oracle.truffle.api.impl.asm.Opcodes.RETURN;
import static com.oracle.truffle.api.impl.asm.Opcodes.T_BYTE;
import static com.oracle.truffle.api.impl.asm.Opcodes.V1_8;

final class ArrayBasedShapeGenerator<T> extends ShapeGenerator<T> {
    private static final ConcurrentHashMap<Pair<Class<?>, Class<?>>, Object> generatorCache = TruffleOptions.AOT ? new ConcurrentHashMap<>() : null;
    private static final String STATIC_SHAPE_INTERNAL_NAME = Type.getInternalName(ArrayBasedStaticShape.class);
    private static final String STATIC_SHAPE_DESCRIPTOR = Type.getDescriptor(ArrayBasedStaticShape.class);

    private final Class<?> generatedStorageClass;
    private final Class<? extends T> generatedFactoryClass;

    @CompilationFinal private int byteArrayOffset;
    @CompilationFinal private int objectArrayOffset;
    @CompilationFinal private int shapeOffset;

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

    int getByteArrayOffset() {
        return byteArrayOffset;
    }

    int getObjectArrayOffset() {
        return objectArrayOffset;
    }

    int getShapeOffset() {
        return shapeOffset;
    }

    // Invoked also from TruffleBaseFeature.StaticObjectSupport
    @SuppressWarnings("unchecked")
    static <T> ArrayBasedShapeGenerator<T> getShapeGenerator(TruffleLanguage<?> language, GeneratorClassLoaders gcls, Class<?> storageSuperClass, Class<T> storageFactoryInterface,
                    String storageClassName) {
        ConcurrentHashMap<Pair<Class<?>, Class<?>>, Object> cache;
        if (TruffleOptions.AOT) {
            cache = generatorCache;
        } else {
            cache = SomAccessor.ENGINE.getGeneratorCache(SomAccessor.LANGUAGE.getPolyglotLanguageInstance(language));
        }
        Pair<Class<?>, Class<?>> pair = Pair.create(storageSuperClass, storageFactoryInterface);
        ArrayBasedShapeGenerator<T> sg = (ArrayBasedShapeGenerator<T>) cache.get(pair);
        if (sg == null) {
            if (ImageInfo.inImageRuntimeCode()) {
                throw new IllegalStateException("This code should not be executed at Native Image run time. Please report this issue");
            }
            Class<?> generatedStorageClass = generateStorage(gcls, storageSuperClass, storageClassName);
            Class<? extends T> generatedFactoryClass = generateFactory(gcls, generatedStorageClass, storageFactoryInterface);
            sg = new ArrayBasedShapeGenerator<>(generatedStorageClass, generatedFactoryClass);
            ArrayBasedShapeGenerator<T> prevSg = (ArrayBasedShapeGenerator<T>) cache.putIfAbsent(pair, sg);
            if (prevSg != null) {
                sg = prevSg;
            }
        }
        return sg;
    }

    @SuppressWarnings("deprecation"/* JDK-8277863 */)
    private static int getObjectFieldOffset(Class<?> c, String fieldName) {
        try {
            return Math.toIntExact(UNSAFE.objectFieldOffset(c.getField(fieldName)));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    StaticShape<T> generateShape(StaticShape<T> parentShape, Map<String, StaticProperty> staticProperties, boolean safetyChecks, String storageClassName) {
        return ArrayBasedStaticShape.create(this, generatedStorageClass, generatedFactoryClass, (ArrayBasedStaticShape<T>) parentShape, staticProperties.values(), safetyChecks);
    }

    // Invoked from TruffleBaseFeature$StaticObjectSupport
    void patchOffsets(int nativeByteArrayOffset, int nativeObjectArrayOffset, int nativeShapeOffset) {
        assert TruffleOptions.AOT;
        CompilerAsserts.neverPartOfCompilation();
        byteArrayOffset = nativeByteArrayOffset;
        objectArrayOffset = nativeObjectArrayOffset;
        shapeOffset = nativeShapeOffset;
    }

    private static String getStorageConstructorDescriptor(Constructor<?> superConstructor) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class<?> parameter : superConstructor.getParameterTypes()) {
            sb.append(Type.getDescriptor(parameter));
        }
        sb.append(STATIC_SHAPE_DESCRIPTOR); // ArrayBasedStaticShape shape
        sb.append("[B");  // byte[] primitive
        sb.append("[Ljava/lang/Object;"); // Object[] object
        return sb.append(")V").toString();
    }

    private static void addStorageConstructors(ClassVisitor cv, String storageName, Class<?> storageSuperClass, String storageSuperName) {
        for (Constructor<?> superConstructor : storageSuperClass.getDeclaredConstructors()) {
            String storageConstructorDescriptor = getStorageConstructorDescriptor(superConstructor);
            String superConstructorDescriptor = Type.getConstructorDescriptor(superConstructor);

            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", storageConstructorDescriptor, null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            int maxStack = 1; // this
            int maxLocals = 1; // ALOAD_0
            Class<?>[] constructorParameters = superConstructor.getParameterTypes();
            for (Class<?> constructorParameter : constructorParameters) {
                Type parameterType = Type.getType(constructorParameter);
                int loadOpcode = parameterType.getOpcode(ILOAD);
                mv.visitVarInsn(loadOpcode, maxLocals);
                int parameterSize = parameterType.getSize();
                maxStack += parameterSize;
                maxLocals += parameterSize;
            }
            mv.visitMethodInsn(INVOKESPECIAL, storageSuperName, "<init>", superConstructorDescriptor, false);

            // this.shape = shape;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, maxLocals++);
            mv.visitFieldInsn(PUTFIELD, storageName, "shape", STATIC_SHAPE_DESCRIPTOR);

            // this.primitive = primitive;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, maxLocals++);
            mv.visitFieldInsn(PUTFIELD, storageName, "primitive", "[B");

            // this.object = object;
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, maxLocals++);
            mv.visitFieldInsn(PUTFIELD, storageName, "object", "[Ljava/lang/Object;");

            mv.visitInsn(RETURN);
            mv.visitMaxs(Math.max(maxStack, 2), maxLocals);

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
        cv.visitField(ACC_PUBLIC | ACC_STATIC, "replacements", "Ljava/util/concurrent/ConcurrentHashMap;",
                        "Ljava/util/concurrent/ConcurrentHashMap<Ljava/lang/Object;Ljava/lang/Object;>;", null).visitEnd();

        cv.visitField(ACC_PUBLIC | ACC_FINAL, "shape", STATIC_SHAPE_DESCRIPTOR, null, null).visitEnd();
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "primitiveArraySize", "I", null, null).visitEnd();
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "objectArraySize", "I", null, null).visitEnd();
        cv.visitField(ACC_PUBLIC | ACC_FINAL, "doRegister", "Z", null, null).visitEnd();
    }

    private static void addFactoryConstructor(ClassVisitor cv, String factoryName) {
        /**
         * Example:
         *
         * Interface provided by the user:
         *
         * <pre>
         * public interface StaticObjectFactory {
         *     StaticObject create(Klass klass);
         * }
         * </pre>
         *
         * Generated factory fields and constructor:
         *
         * <pre>
         * public final class GeneratedStaticObject$$1$$Factory implements ArrayBasedFactory, StaticObjectFactory {
         *     static final ConcurrentHashMap<Object, Object> replacements; // initialized via reflection
         *     final ArrayBasedStaticShape shape;
         *     final int primitiveArraySize;
         *     final int objectArraySize;
         *     final boolean doRegister;
         *
         *     GeneratedStaticObject$$1$$Factory(ArrayBasedStaticShape shape, int primitiveArraySize, int objectArraySize, boolean doRegister) {
         *         this.shape = shape;
         *         this.primitiveArraySize = primitiveArraySize;
         *         this.objectArraySize = objectArraySize;
         *         this.doRegister = doRegister;
         *
         *         if (doRegister) {
         *             register(this);
         *         }
         *     }
         * ...
         * }
         * </pre>
         */
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(" + STATIC_SHAPE_DESCRIPTOR + "IIZ)V", null, null);
        mv.visitCode();

        /**
         * <pre>
         * this.shape = shape;
         * this.primitiveArraySize = primitiveArraySize;
         * this.objectArraySize = objectArraySize;
         * this.doRegister = doRegister;
         * </pre>
         */
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, factoryName, "shape", STATIC_SHAPE_DESCRIPTOR);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitFieldInsn(PUTFIELD, factoryName, "primitiveArraySize", "I");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 3);
        mv.visitFieldInsn(PUTFIELD, factoryName, "objectArraySize", "I");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 4);
        mv.visitFieldInsn(PUTFIELD, factoryName, "doRegister", "Z");

        /**
         * <pre>
         * if (doRegister) {
         *     register(this);
         * }
         * </pre>
         */
        mv.visitVarInsn(ILOAD, 4);
        Label doNotRegister = new Label();
        mv.visitJumpInsn(IFEQ, doNotRegister);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, factoryName, "register", "(Ljava/lang/Object;)V", false);
        mv.visitLabel(doNotRegister);
        mv.visitFrame(Opcodes.F_FULL, 5, new Object[]{factoryName, STATIC_SHAPE_INTERNAL_NAME, Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER}, 0, new Object[]{});

        /**
         * Implicit return statement
         */
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 5);
        mv.visitEnd();
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

    private static ArrayList<Object> getFactoryFrameLocals(String factoryName, Class<?>[] constructorParameters) {
        // Expected max length: this + args + primitive + static object
        ArrayList<Object> frameLocals = new ArrayList<>(constructorParameters.length + 3);
        frameLocals.add(factoryName);
        for (Class<?> constructorParameter : constructorParameters) {
            frameLocals.add(getFrameLocal(constructorParameter));
        }
        return frameLocals;
    }

    private static void addFactoryMethods(ClassVisitor cv, Class<?> storageClass, Class<?> storageFactoryInterface, String factoryName) {
        /**
         * Example:
         *
         * Interface provided by the user:
         * 
         * <pre>
         * public interface StaticObjectFactory {
         *     StaticObject create(Klass klass);
         * }
         * </pre>
         *
         * Generated factory method:
         * 
         * <pre>
         * StaticObject create(Klass klass) {
         *     byte[] primitive;
         *     StaticObject obj = new StaticObject(
         *                     klass,
         *                     shape,
         *                     primitive = primitiveArraySize > 0 ? new byte[primitiveArraySize] : null,
         *                     objectArraySize > 0 ? new Object[objectArraySize] : null);
         *     if (doRegister && primitive != null) {
         *         register(primitive);
         *     }
         *     return obj;
         * }
         * </pre>
         */
        for (Method m : storageFactoryInterface.getMethods()) {
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, m.getName(), Type.getMethodDescriptor(m), null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitTypeInsn(NEW, Type.getInternalName(storageClass));
            mv.visitInsn(DUP);
            StringBuilder constructorDescriptor = new StringBuilder();
            constructorDescriptor.append('(');

            Class<?>[] constructorParameters = m.getParameterTypes();
            int maxStack = 2; // DUP
            int maxLocals = 1; // this
            for (Class<?> param : constructorParameters) {
                Type parameterType = Type.getType(param);
                int loadOpcode = parameterType.getOpcode(ILOAD);
                mv.visitVarInsn(loadOpcode, maxLocals);
                int parameterSize = parameterType.getSize();
                maxStack += parameterSize;
                maxLocals += parameterSize;
                constructorDescriptor.append(Type.getDescriptor(param));
            }

            // Prepare arrays of frame locals and stack for jumps
            ArrayList<Object> frameLocals = getFactoryFrameLocals(factoryName, constructorParameters);
            ArrayList<Object> frameStack = new ArrayList<>();
            frameStack.add(label0);
            frameStack.add(label0);
            for (int i = 1; i < frameLocals.size(); i++) {
                frameStack.add(frameLocals.get(i));
            }

            /**
             * <pre>
             *                     shape,
             * </pre>
             */
            constructorDescriptor.append(STATIC_SHAPE_DESCRIPTOR);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "shape", STATIC_SHAPE_DESCRIPTOR);
            frameStack.add(STATIC_SHAPE_INTERNAL_NAME);
            maxStack++;

            /**
             * <pre>
             *                     primitive = primitiveArraySize > 0 ? new byte[primitiveArraySize] : null,
             * </pre>
             */
            constructorDescriptor.append("[B");
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "primitiveArraySize", "I");
            Label nonPositiveArraySize1 = new Label();
            mv.visitJumpInsn(IFLE, nonPositiveArraySize1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "primitiveArraySize", "I");
            mv.visitIntInsn(NEWARRAY, T_BYTE);
            Label done1 = new Label();
            mv.visitJumpInsn(GOTO, done1);
            mv.visitLabel(nonPositiveArraySize1);
            mv.visitFrame(F_FULL, frameLocals.size(), frameLocals.toArray(), frameStack.size(), frameStack.toArray());
            mv.visitInsn(ACONST_NULL);
            mv.visitLabel(done1);
            frameStack.add("[B");
            mv.visitFrame(F_FULL, frameLocals.size(), frameLocals.toArray(), frameStack.size(), frameStack.toArray());
            maxStack++;
            mv.visitInsn(DUP);
            // no need to increment maxStack here since the byte[] is immediately stored, and we are
            // going to push the Object[] to the stack later on
            int primitiveArrayLocal = maxLocals;
            maxLocals += Type.getType(byte[].class).getSize();
            frameLocals.add(getFrameLocal(byte[].class));
            mv.visitVarInsn(ASTORE, primitiveArrayLocal);

            /**
             * <pre>
             *                     objectArraySize > 0 ? new Object[objectArraySize] : null);
             * </pre>
             */
            constructorDescriptor.append("[Ljava/lang/Object;");
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "objectArraySize", "I");
            Label nonPositiveArraySize2 = new Label();
            mv.visitJumpInsn(IFLE, nonPositiveArraySize2);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "objectArraySize", "I");
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            Label done2 = new Label();
            mv.visitJumpInsn(GOTO, done2);
            mv.visitLabel(nonPositiveArraySize2);
            mv.visitFrame(F_FULL, frameLocals.size(), frameLocals.toArray(), frameStack.size(), frameStack.toArray());
            mv.visitInsn(ACONST_NULL);
            mv.visitLabel(done2);
            frameStack.add("[Ljava/lang/Object;");
            mv.visitFrame(F_FULL, frameLocals.size(), frameLocals.toArray(), frameStack.size(), frameStack.toArray());
            maxStack++;

            /**
             * <pre>
             *     StaticObject obj = new StaticObject( ... );
             * </pre>
             */
            constructorDescriptor.append(")V");
            String storageName = Type.getInternalName(storageClass);
            mv.visitMethodInsn(INVOKESPECIAL, storageName, "<init>", constructorDescriptor.toString(), false);
            int staticObjectLocal = maxLocals;
            maxLocals += Type.getType(storageClass).getSize();
            frameLocals.add(getFrameLocal(storageClass));
            mv.visitVarInsn(ASTORE, staticObjectLocal);

            /**
             * <pre>
             * if (doRegister && primitive != null) {
             *     register(primitive);
             * }
             * </pre>
             */
            // no need to increment maxStack here since the previous bytecodes pushed more variables
            // to the stack
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, factoryName, "doRegister", "Z");
            Label doNotRegister = new Label();
            mv.visitJumpInsn(IFEQ, doNotRegister);
            mv.visitVarInsn(ALOAD, primitiveArrayLocal);
            mv.visitJumpInsn(IFNULL, doNotRegister);
            mv.visitVarInsn(ALOAD, primitiveArrayLocal);
            mv.visitMethodInsn(INVOKESTATIC, factoryName, "register", "(Ljava/lang/Object;)V", false);
            mv.visitLabel(doNotRegister);
            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{storageName}, 0, null);

            /**
             * <pre>
             * return obj;
             * </pre>
             */
            mv.visitVarInsn(ALOAD, staticObjectLocal);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(maxStack, maxLocals);
            mv.visitEnd();
        }
    }

    private static void addFactoryHelperMethods(ClassVisitor cv, String factoryName) {
        /**
         * Example:
         *
         * <pre>
         * &#64;TruffleBoundary
         * private static void register(Object obj) {
         *     if (!ImageInfo.inImageBuildtimeCode()) {
         *         throw new RuntimeException("Should not reach here!")
         *     }
         *     replacements.put(obj, obj);
         * }
         * </pre>
         */
        MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "register", "(Ljava/lang/Object;)V", null, null);

        AnnotationVisitor av = mv.visitAnnotation(Type.getDescriptor(TruffleBoundary.class), true);
        av.visitEnd();

        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(ImageInfo.class), "inImageBuildtimeCode", "()Z", false);
        Label inImageBuildTime = new Label();
        mv.visitJumpInsn(IFNE, inImageBuildTime);
        mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Should not reach here!");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitLabel(inImageBuildTime);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitFieldInsn(GETSTATIC, factoryName, "replacements", "Ljava/util/concurrent/ConcurrentHashMap;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ConcurrentHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
    }

    private static Class<?> generateStorage(GeneratorClassLoaders gcls, Class<?> storageSuperClass, String storageClassName) {
        String storageSuperName = Type.getInternalName(storageSuperClass);

        ClassWriter storageWriter = new ClassWriter(0);
        int storageAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC;
        storageWriter.visit(V1_8, storageAccess, storageClassName, null, storageSuperName, null);
        addStorageConstructors(storageWriter, storageClassName, storageSuperClass, storageSuperName);
        addStorageField(storageWriter, "primitive", "[B", true);
        addStorageField(storageWriter, "object", "[Ljava/lang/Object;", true);
        addStorageField(storageWriter, "shape", STATIC_SHAPE_DESCRIPTOR, true);
        if (Cloneable.class.isAssignableFrom(storageSuperClass)) {
            addCloneMethod(storageSuperClass, storageWriter, storageClassName);
        }
        storageWriter.visitEnd();
        return load(gcls, storageClassName, storageWriter.toByteArray(), true);
    }

    private static <T> Class<? extends T> generateFactory(GeneratorClassLoaders gcls, Class<?> storageClass, Class<T> storageFactoryInterface) {
        ClassWriter factoryWriter = new ClassWriter(0);
        int factoryAccess = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        String factoryName = generateFactoryName(storageClass);
        factoryWriter.visit(V1_8, factoryAccess, factoryName, null, Type.getInternalName(Object.class), new String[]{Type.getInternalName(storageFactoryInterface)});
        addFactoryFields(factoryWriter);
        addFactoryConstructor(factoryWriter, factoryName);
        addFactoryMethods(factoryWriter, storageClass, storageFactoryInterface, factoryName);
        addFactoryHelperMethods(factoryWriter, factoryName);
        factoryWriter.visitEnd();
        Class<? extends T> factoryClass = load(gcls, factoryName, factoryWriter.toByteArray(), false);

        ConcurrentHashMap<Object, Object> replacements = ArrayBasedStaticShape.replacements;
        if (replacements != null) {
            try {
                factoryClass.getDeclaredField("replacements").set(null, replacements);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Should not reach here", e);
            }
        }

        return factoryClass;
    }
}
