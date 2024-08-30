/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.hosted.substitute;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import org.graalvm.collections.Pair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.oracle.svm.core.util.UserError.guarantee;

public class AdviceAliasClassGenerator {
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("debug.aspect.substitute"));
    private final ImageClassLoader imageClassLoader;

    public static class AliasClassInfo {
        private Class<?> originalClass;
        private Class<?> annotatedClass;
        private boolean isDeclared;

        public AliasClassInfo(Class<?> originalClass, Class<?> annotatedClass, boolean isDeclared) {
            this.originalClass = originalClass;
            this.annotatedClass = annotatedClass;
            this.isDeclared = isDeclared;
        }
    }

    private class AnnotationSuppliers {
        private Annotation annotation;
        private Supplier<String[]> valueSupplier;
        private Supplier<Advice.NotFoundAction> notFoundActionSupplier;
        private Class<?>[] returnTypeCriteria;

        AnnotationSuppliers(Annotation annotation, Supplier<String[]> valueSupplier, Supplier<Advice.NotFoundAction> notFoundActionSupplier,
                        Class<?>[] returnTypeCriteria) {
            this.annotation = annotation;
            this.valueSupplier = valueSupplier;
            this.notFoundActionSupplier = notFoundActionSupplier;
            this.returnTypeCriteria = returnTypeCriteria;
        }
    }

    public AdviceAliasClassGenerator(ImageClassLoader imageClassLoader) {
        this.imageClassLoader = imageClassLoader;
    }

    public List<Class<?>> generateAliasClasses(List<AliasClassInfo> aliasClassInfos) {
        List<Class<?>> ret = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        for (AliasClassInfo aliasClassInfo : aliasClassInfos) {
            try {
                Class<?> c = generateAliasClass(aliasClassInfo.annotatedClass, aliasClassInfo.originalClass, aliasClassInfo.isDeclared);
                if (c != null) {
                    ret.add(c);
                }
            } catch (Throwable e) {
                exceptions.add(e);
            }
        }
        if (exceptions.isEmpty()) {
            return ret;
        } else {
            for (Throwable exception : exceptions) {
                exception.printStackTrace();
            }
            throw VMError.shouldNotReachHere("Error: Can't apply Advice methods.");
        }
    }

    /**
     * Dynamically generate alias class that following the substitution idiom (See
     * {@link TargetClass}).
     *
     * @param annotatedClass
     * @param originalClass
     * @param isDeclared set to true if the original method must be declared in its class. I.e. can
     *            be retrieved via {@link Class#getDeclaredMethod(String, Class[])}. When set to
     *            false, will ignore the {@link NoSuchMethodException} when the declared method is
     *            not found.
     */
    public Class<?> generateAliasClass(Class<?> annotatedClass, Class<?> originalClass, boolean isDeclared) {
        Aspect aspect = annotatedClass.getAnnotation(Aspect.class);
        if (!AnnotationSubstitutionProcessor.isIncluded(aspect.onlyWith(), originalClass, annotatedClass)) {
            return null;
        }
        String annotatedClassQualifiedName = annotatedClass.getName();
        String annotatedClassInternalName = Type.getInternalName(annotatedClass);
        String newNamePostfix = "$$" + originalClass.getName().replace('.', '_').replace('$', '_');
        String substituteClassQualifiedName = annotatedClassQualifiedName + newNamePostfix;
        String substituteClassInternalName = annotatedClassInternalName + newNamePostfix;
        Class<?> ret;
        try {
            ret = Class.forName(substituteClassQualifiedName, false, imageClassLoader.getClassLoader());
        } catch (ClassNotFoundException e) {
            ret = null;
        }
        // Generate class only when it's not existed
        if (ret == null) {
            // 1. Collect Advice methods, find before and after advice methods for each method in
            // original class
            // Key is the original method, value pair is <beforeMethod, afterMethod>.
            Map<Executable, Pair<Method, Method>> originalMethodMap = new HashMap<>();
            for (Method adviceMethod : annotatedClass.getDeclaredMethods()) {
                Advice.Before before = adviceMethod.getAnnotation(Advice.Before.class);
                if (before != null) {
                    registerOriginalAdvice(originalClass, originalMethodMap, adviceMethod,
                                    new AnnotationSuppliers(before, () -> before.value(), () -> before.notFoundAction(), before.onlyWithReturnType()), isDeclared);
                }
                Advice.After after = adviceMethod.getAnnotation(Advice.After.class);
                if (after != null) {
                    registerOriginalAdvice(originalClass, originalMethodMap, adviceMethod,
                                    new AnnotationSuppliers(after, () -> after.value(), () -> after.notFoundAction(), after.onlyWithReturnType()), isDeclared);
                }
            }

            // 2. Generate advice methods
            if (!originalMethodMap.isEmpty()) {
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                classWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, substituteClassInternalName, null, "java/lang/Object", null);
                // Add @TargetClass(className="original") on the class declaration
                AnnotationVisitor av = classWriter.visitAnnotation(Type.getDescriptor(TargetClass.class), true);
                av.visit("className", originalClass.getName());
                av.visitEnd();
                // Generate constructor
                MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                constructor.visitCode();
                constructor.visitVarInsn(Opcodes.ALOAD, 0);
                constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                constructor.visitInsn(Opcodes.RETURN);
                constructor.visitMaxs(1, 1);
                constructor.visitEnd();

                AdviceSubstitutionMethodGenerator adviceSubstitutionMethodGenerator = new AdviceSubstitutionMethodGenerator(classWriter, substituteClassInternalName, annotatedClassInternalName);
                for (Map.Entry<Executable, Pair<Method, Method>> originalAdviceEntry : originalMethodMap.entrySet()) {
                    Executable originalMethod = originalAdviceEntry.getKey();
                    String originalMethodName = originalMethod.getName();
                    String descriptor;
                    boolean isConstructor = false;
                    if (originalMethod instanceof Method) {
                        descriptor = Type.getMethodDescriptor((Method) originalMethod);
                    } else {
                        descriptor = Type.getConstructorDescriptor((Constructor<?>) originalMethod);
                        isConstructor = true;
                    }

                    // Generate an alias method
                    String aliasOfOriginName = adviceSubstitutionMethodGenerator.createAliasMethod(originalMethodName, descriptor, isConstructor);

                    // Generate a substitute method
                    adviceSubstitutionMethodGenerator.createSubstituteMethod(originalAdviceEntry, originalMethod, descriptor, isConstructor, aliasOfOriginName);
                }

                byte[] b = classWriter.toByteArray();
                // For debugging
                if (DEBUG) {
                    try {
                        Path output = Paths.get(System.getProperty("user.dir"), substituteClassInternalName.replace('/', '_') + ".class");
                        try (OutputStream out = new FileOutputStream(output.toFile())) {
                            out.write(b);
                        }
                    } catch (IOException e) {
                        throw UserError.abort("Can't write Aspect class definition.", e);
                    }
                }
                try {
                    ret = NativeImageSystemClassLoader.defineClass(imageClassLoader.getClassLoader(), substituteClassQualifiedName, b, 0, b.length);
                    imageClassLoader.addAsAppClass(ret);
                } catch (VMError.HostedError t) {
                    if (DEBUG) {
                        t.printStackTrace();
                    }
                }
            }
        }
        return ret;
    }

    private static void registerOriginalAdvice(Class<?> originalClass, Map<Executable, Pair<Method, Method>> originalMethodMap,
                    Method adviceMethod, AnnotationSuppliers annotationSuppliers, boolean isDeclared) {
        Class<?> annotationClass = annotationSuppliers.annotation.annotationType();
        String[] annotationValue = annotationSuppliers.valueSupplier.get();
        guarantee(Modifier.isStatic(adviceMethod.getModifiers()), "Provided advice method %s#%s is not static.", adviceMethod.getDeclaringClass().getName(), adviceMethod.getName());
        String[] originalMethodNames;
        if (annotationValue.length > 0) {
            originalMethodNames = annotationValue;
        } else {
            originalMethodNames = new String[]{adviceMethod.getName()};
        }
        for (String originalMethodName : originalMethodNames) {
            Class<?>[] parameterTypes = Arrays.stream(adviceMethod.getParameters()).filter(parameter -> parameter.getDeclaredAnnotation(Advice.Return.class) == null &&
                            parameter.getDeclaredAnnotation(Advice.BeforeResult.class) == null &&
                            parameter.getDeclaredAnnotation(Advice.This.class) == null &&
                            parameter.getDeclaredAnnotation(Advice.Thrown.class) == null).map(Parameter::getType).toArray(Class<?>[]::new);

            try {
                Executable originalMethod;
                Class<?> returnType;
                if (originalMethodName.equals("<init>")) {
                    originalMethod = originalClass.getDeclaredConstructor(parameterTypes);
                    returnType = originalClass;
                } else {
                    originalMethod = originalClass.getDeclaredMethod(originalMethodName, parameterTypes);
                    returnType = ((Method) originalMethod).getReturnType();
                }
                if (AnnotationSubstitutionProcessor.isIncluded(annotationSuppliers.returnTypeCriteria, returnType, annotationClass)) {
                    originalMethodMap.compute(originalMethod, (k, v) -> {
                        if (v == null) {
                            if (annotationClass.equals(Advice.Before.class)) {
                                return Pair.createLeft(adviceMethod);
                            } else {
                                return Pair.createRight(adviceMethod);
                            }
                        } else {
                            if (annotationClass.equals(Advice.Before.class)) {
                                if (v.getLeft() == null) {
                                    return Pair.create(adviceMethod, v.getRight());
                                }
                            } else {
                                if (v.getRight() == null) {
                                    return Pair.create(v.getLeft(), adviceMethod);
                                }
                            }
                            throw UserError.abort("Duplicated @%s is set for the same target method %s", ClassUtil.getUnqualifiedName(annotationClass), originalMethod.getName());
                        }
                    });
                }
            } catch (NoSuchMethodException e) {
                if (isDeclared) {
                    String message = String.format("Could not find target method for @%s annotation." +
                                    " Please check method name and parameter types to make sure the expect method exists.", ClassUtil.getUnqualifiedName(annotationClass));
                    switch (annotationSuppliers.notFoundActionSupplier.get()) {
                        case error:
                            throw UserError.abort(e, message);
                        case info:
                            LogUtils.info(message);
                            break;
                        case ignore:// do nothing
                    }
                }
            }
        }
    }
}
