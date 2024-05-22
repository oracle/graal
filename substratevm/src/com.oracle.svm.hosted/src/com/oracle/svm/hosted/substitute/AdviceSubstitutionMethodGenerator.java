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
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.UserError;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.collections.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.oracle.svm.core.util.UserError.guarantee;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_NATIVE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ATHROW;
import static jdk.internal.org.objectweb.asm.Opcodes.CHECKCAST;
import static jdk.internal.org.objectweb.asm.Opcodes.GETFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.GOTO;
import static jdk.internal.org.objectweb.asm.Opcodes.IFNONNULL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static jdk.internal.org.objectweb.asm.Opcodes.POP;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;

public class AdviceSubstitutionMethodGenerator {
    private static final String SUBSTITUTION_METHOD_POSTFIX = "_substituteOfOrigin";
    private static final String ALIAS_METHOD_POSTFIX = "_aliasOfOrigin";
    private static final String ALIAS_ANNOTATION_NAME = Type.getDescriptor(Alias.class);
    private static final String TARGET_ELEMENT_ANNOTATION_NAME = Type.getDescriptor(TargetElement.class);
    private static final String AGENT_SUPPORT_ANNOTATION_NAME = Type.getDescriptor(Advice.ForAgentSupport.class);
    private static final String SUBSTITUTE_ANNOTATION_NAME = Type.getDescriptor(Substitute.class);

    private final String newClassClassName;
    private final String annotatedClassName;
    private final ClassWriter classWriter;

    public AdviceSubstitutionMethodGenerator(ClassWriter classWriter, String newClassClassName, String annotatedClassName) {
        this.classWriter = classWriter;
        this.newClassClassName = newClassClassName;
        this.annotatedClassName = annotatedClassName;
    }

    /**
     * Generate a substitution method which has the following basic structure.
     * 
     * <pre>
     *    {@literal @Substitute}
     *    {@literal @TargetElement(name="[originalMethodName]")}
     *     public static OriginalReturn [originalMethodName]_substituteOfOrigin([original parameters]){
     *          BeforeReturn beforeReturn = beforeOriginal([original parameters]);
     *          OriginalReturn originalReturn = null;
     *          try{
     *              originalReturn = original([original parameters])
     *          } catch(Throwable t){
     *              afterOriginal([original parameters],originalReturn, beforeReturn, t)
     *              throw t;
     *          }
     *          afterOriginal([original parameters],originalReturn, beforeReturn, null);
     *          return originalReturn;
     *     }
     * </pre>
     *
     * @param targetEntry
     * @param original
     * @param descriptor
     * @param isConstructor
     * @param aliasOfOriginName
     */
    public void createSubstituteMethod(Map.Entry<Executable, Pair<Method, Method>> targetEntry, Executable original,
                    String descriptor, boolean isConstructor, String aliasOfOriginName) {
        String originalMethodName = original.getName();
        String substituteOfOriginName = originalMethodName + SUBSTITUTION_METHOD_POSTFIX;
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC, substituteOfOriginName, descriptor, null, null);
        AnnotationVisitor av = mv.visitAnnotation(SUBSTITUTE_ANNOTATION_NAME, true);
        av.visitEnd();

        av = mv.visitAnnotation(TARGET_ELEMENT_ANNOTATION_NAME, true);
        av.visit("name", isConstructor ? TargetElement.CONSTRUCTOR_NAME : originalMethodName);
        av.visitEnd();

        av = mv.visitAnnotation(AGENT_SUPPORT_ANNOTATION_NAME, true);
        av.visitEnd();

        mv.visitCode();

        int parameterSize = original.getParameterCount();
        int localVarCounter = parameterSize;
        int beforeReturnSlot = -1;
        int originReturnSlot = -1;
        int exceptionSlot = -1;

        boolean needExceptionHandler = false;
        Class<? extends Throwable> onThrowableType = null;
        Method afterMethod = targetEntry.getValue().getRight();
        if (afterMethod != null) {
            Advice.After afterAnnotation = afterMethod.getAnnotation(Advice.After.class);
            onThrowableType = afterAnnotation.onThrowable();
            if (!Advice.NoException.class.equals(onThrowableType)) {
                needExceptionHandler = true;
            }
        }

        Label startTryLabel = new Label();
        Label endTryLabel = new Label();
        Label startCatchLabel = new Label();
        if (needExceptionHandler) {
            mv.visitTryCatchBlock(startTryLabel, endTryLabel, startCatchLabel, Type.getInternalName(onThrowableType));
        }
        Map<Integer, Integer> originalMethodParamLocalVarMap = new HashMap<>();
        Parameter[] originalParameters = original.getParameters();

        // Call @Before method
        Method beforeMethod = targetEntry.getValue().getLeft();
        String beforeMethodReturnTypeInternalName = null;
        Field resultOfBefore = null;
        if (beforeMethod != null) {
            Map<Integer, Integer> beforeMethodParamLocalVarMap = fillMethodParameterMap(beforeMethod);
            loadParameters(beforeMethod, mv, beforeMethodParamLocalVarMap, false);
            mv.visitMethodInsn(INVOKESTATIC, annotatedClassName, beforeMethod.getName(), Type.getMethodDescriptor(beforeMethod), false);
            Class<?> beforeMethodReturnType = beforeMethod.getReturnType();
            if (!beforeMethodReturnType.equals(Void.TYPE)) {
                beforeReturnSlot = ++localVarCounter;
                mv.visitVarInsn(ByteCodeOper.getStoreOp(beforeMethodReturnType), beforeReturnSlot);
                if (beforeMethodReturnType.getAnnotation(Advice.ResultWrapper.class) != null) {
                    beforeMethodReturnTypeInternalName = Type.getInternalName(beforeMethodReturnType);
                    Parameter[] parameters = beforeMethod.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        if (parameters[i].getAnnotation(Advice.Rewrite.class) != null) {
                            guarantee(i < originalParameters.length && originalParameters[i].getType().equals(parameters[i].getType()),
                                            "The %sth parameter of @Before method %s#%s doesn't match with its corresponding original parameter." +
                                                            "It is supposed to be of %s type, but is %s",
                                            i,
                                            beforeMethod.getDeclaringClass().getName(), beforeMethod.getName(),
                                            originalParameters[i].getType().getName(),
                                            parameters[i].getType().getName());
                            String fieldName = parameters[i].getAnnotation(Advice.Rewrite.class).field();
                            Class<?> parameterType = parameters[i].getType();
                            try {
                                Field f = beforeMethodReturnType.getDeclaredField(fieldName);
                                guarantee(f.getType().equals(Optional.class), "Rewrite parameter's corresponding field must be of %s type, but is %s", Optional.class.getName(), f.getType().getName());
                                mv.visitVarInsn(ALOAD, beforeReturnSlot); // load result returned by
                                                                          // @Before method
                                mv.visitFieldInsn(GETFIELD, beforeMethodReturnTypeInternalName, fieldName, "Ljava/util/Optional;");
                                Label rewriteParaNonNull = new Label();
                                mv.visitJumpInsn(IFNONNULL, rewriteParaNonNull);
                                // rewrite parameter is null, meaning it didn't rewrite, use the
                                // original parameter
                                mv.visitVarInsn(ByteCodeOper.getLoadOp(parameterType), i + 1);
                                Label storeLocalVar = new Label();
                                mv.visitJumpInsn(GOTO, storeLocalVar);
                                // rewrite the parameter value according to the returned value
                                mv.visitLabel(rewriteParaNonNull);
                                mv.visitVarInsn(ALOAD, beforeReturnSlot);
                                mv.visitFieldInsn(GETFIELD, beforeMethodReturnTypeInternalName, fieldName, "Ljava/util/Optional;");
                                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Optional", "get", "()Ljava/lang/Object;", false);
                                JavaKind javaKind = JavaKind.fromJavaClass(parameterType);
                                if (javaKind.isPrimitive()) {
                                    String boxedInternalName = Type.getInternalName(javaKind.toBoxedJavaClass());
                                    mv.visitTypeInsn(CHECKCAST, boxedInternalName);
                                    mv.visitMethodInsn(INVOKEVIRTUAL, boxedInternalName, javaKind.getJavaName() + "Value", "()" + javaKind.getTypeChar(), false);
                                } else {
                                    mv.visitTypeInsn(CHECKCAST, Type.getInternalName(parameterType));
                                }
                                mv.visitLabel(storeLocalVar);
                                mv.visitVarInsn(ByteCodeOper.getStoreOp(parameterType), ++localVarCounter);
                                originalMethodParamLocalVarMap.put(i, localVarCounter);
                            } catch (NoSuchFieldException e) {
                                throw UserError.abort(e, "Could not generate substitution method for %s: Field declared in @%s on %s could not be found in class.",
                                                original.getName(), Advice.Rewrite.class.getSimpleName(), beforeMethod.getName(), beforeMethodReturnType.getName());
                            }
                        }
                    }
                    for (Field declaredField : beforeMethodReturnType.getDeclaredFields()) {
                        if (declaredField.getAnnotation(Advice.BeforeResult.class) != null) {
                            resultOfBefore = declaredField;
                            break;
                        }
                    }
                }
            }
        }
        // Call original method
        if (needExceptionHandler) {
            mv.visitLabel(startTryLabel);
        }
        fillMethodParameterMap(original, originalMethodParamLocalVarMap);
        loadParameters(original, mv, originalMethodParamLocalVarMap, false);
        mv.visitMethodInsn(INVOKEVIRTUAL, newClassClassName, aliasOfOriginName, descriptor, false);
        Class<?> originalReturnType = null;
        if (!isConstructor && !((Method) original).getReturnType().equals(Void.TYPE)) {
            originalReturnType = ((Method) original).getReturnType();
        }

        // Call @After method
        if (afterMethod != null) {
            Map<Integer, Integer> afterMethodParamLocalVarMap = originalMethodParamLocalVarMap;
            Label noExceptionLabel = new Label();
            if (originalReturnType != null) {
                originReturnSlot = ++localVarCounter;
                mv.visitVarInsn(ByteCodeOper.getStoreOp(originalReturnType), originReturnSlot);
                int parameterIndex = lookUpParameterIndex(afterMethod, Advice.Return.class);
                afterMethodParamLocalVarMap.put(parameterIndex, originReturnSlot);
            }
            if (beforeMethodReturnTypeInternalName != null && resultOfBefore != null) {
                // @Before method returns a wrapped result, fetch the corresponding field
                mv.visitVarInsn(ALOAD, beforeReturnSlot);
                Class<?> fieldType = resultOfBefore.getType();
                mv.visitFieldInsn(GETFIELD, beforeMethodReturnTypeInternalName, resultOfBefore.getName(), Type.getDescriptor(fieldType));
                mv.visitVarInsn(ByteCodeOper.getStoreOp(fieldType), ++localVarCounter);
                afterMethodParamLocalVarMap.put(lookUpParameterIndex(afterMethod, Advice.BeforeResult.class), localVarCounter);
            } else {
                afterMethodParamLocalVarMap.put(lookUpParameterIndex(afterMethod, Advice.BeforeResult.class), beforeReturnSlot);
            }
            Class<?> afterMethodReturnType = afterMethod.getReturnType().equals(Void.TYPE) ? null : afterMethod.getReturnType();
            int thrownIndex = lookUpParameterIndex(afterMethod, Advice.Thrown.class);
            if (!needExceptionHandler) {
                guarantee(thrownIndex == -1, "Could not use @Advice.Thrown annotated parameter without declaring onThrowable().\n" +
                                "The @Advice.After annotated method %s#%s was not correctly declared.",
                                afterMethod.getDeclaringClass().getName(),
                                afterMethod.getName());
            } else {
                // In the catch block
                mv.visitLabel(endTryLabel);
                mv.visitJumpInsn(GOTO, noExceptionLabel);
                mv.visitLabel(startCatchLabel);
                exceptionSlot = ++localVarCounter;
                mv.visitVarInsn(ASTORE, exceptionSlot);

                if (thrownIndex != -1) {
                    Class<?> thrownParamType = afterMethod.getParameterTypes()[thrownIndex];
                    guarantee(onThrowableType.equals(thrownParamType), "@Advice.Thrown parameter's type must be the same as onThrowable()'s.\n " +
                                    "But the @Advice.Thrown annotated parameter of method %s#%s is of type %s, while the onThrowable() returns %s.",
                                    afterMethod.getDeclaringClass().getName(),
                                    afterMethod.getName(),
                                    thrownParamType.getName(),
                                    onThrowableType.getName());
                    afterMethodParamLocalVarMap.put(thrownIndex, exceptionSlot);
                }
                // Call @After method to deal with original exception
                loadParameters(afterMethod, mv, afterMethodParamLocalVarMap, true);
                mv.visitMethodInsn(INVOKESTATIC, annotatedClassName, afterMethod.getName(), Type.getMethodDescriptor(afterMethod), false);
                // Discard whatever @After method returns
                if (afterMethodReturnType != null) {
                    mv.visitInsn(POP);
                }
                // Rethrow the exception
                mv.visitVarInsn(ALOAD, exceptionSlot);
                mv.visitInsn(ATHROW);
            }
            // Out the catch block, the normal branch
            mv.visitLabel(noExceptionLabel);
            loadParameters(afterMethod, mv, afterMethodParamLocalVarMap, false);
            mv.visitMethodInsn(INVOKESTATIC, annotatedClassName, afterMethod.getName(), Type.getMethodDescriptor(afterMethod), false);
            // Original method returns something and not override by @After method
            if (afterMethodReturnType == null && originalReturnType != null) {
                mv.visitVarInsn(ByteCodeOper.getLoadOp(originalReturnType), originReturnSlot);
            }
        }

        if (originalReturnType != null) {
            mv.visitInsn(ByteCodeOper.getReturnOp(originalReturnType));
        } else {
            mv.visitInsn(RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static int lookUpParameterIndex(Executable method, Class<? extends Annotation> annotation) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getAnnotation(annotation) != null) {
                return i;
            }
        }
        return -1;
    }

    private static Map<Integer, Integer> fillMethodParameterMap(Executable method) {
        Map<Integer, Integer> paramLocalVarMap = new HashMap<>();
        fillMethodParameterMap(method, paramLocalVarMap);
        return paramLocalVarMap;
    }

    private static void fillMethodParameterMap(Executable method, Map<Integer, Integer> paramLocalVarMap) {
        for (int i = 0; i < method.getParameters().length; i++) {
            paramLocalVarMap.putIfAbsent(i, i + 1);
        }
    }

    private static void loadParameters(Executable method, MethodVisitor methodVisitor, Map<Integer, Integer> localVarMap, boolean inExceptionHandleBlock) {
        Parameter[] parameters = method.getParameters();
        // Load receiver
        if (!Modifier.isStatic(method.getModifiers())) {
            methodVisitor.visitVarInsn(ALOAD, 0);
        }
        // Load original method parameters
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameterType = parameters[i].getType();
            Parameter p = parameters[i];
            if (p.getAnnotation(Advice.This.class) != null) {
                // This ref always uses ALOAD_0
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitTypeInsn(CHECKCAST, Type.getInternalName(p.getType())); // cast
                                                                                           // this
                                                                                           // ref
                                                                                           // parameter
            } else {
                int loadOpCode = ByteCodeOper.getLoadOp(parameterType);
                int localVarIndex = localVarMap.get(i);
                if (p.getAnnotation(Advice.Return.class) != null) {
                    if (localVarIndex == -1) {
                        throw UserError.abort("Could not accept original method's returning value as @After method's parameter, because the original method doesn't return anything." +
                                        " Please make sure the declaration of @After method and original method are persistent.");
                    }
                    // In exception handler block, set default value for original returning.
                    if (inExceptionHandleBlock) {
                        methodVisitor.visitInsn(ByteCodeOper.getDefaultOp(p.getType()));
                    } else {
                        methodVisitor.visitVarInsn(loadOpCode, localVarIndex);
                    }
                } else if (p.getAnnotation(Advice.BeforeResult.class) != null) {
                    if (localVarIndex == -1) {
                        throw UserError.abort("Could not accept @Before method's returning value as @After method's parameter, because the @Before method doesn't return anything." +
                                        " Please make sure the declaration of @After method and @Before method are persistent.");
                    }
                    methodVisitor.visitVarInsn(loadOpCode, localVarIndex);
                } else if (p.getAnnotation(Advice.Thrown.class) != null) {
                    if (inExceptionHandleBlock) {
                        methodVisitor.visitVarInsn(loadOpCode, localVarIndex);
                    } else {
                        // Not in exception handler block, set the parameter to null
                        methodVisitor.visitInsn(ACONST_NULL);
                    }
                } else {
                    methodVisitor.visitVarInsn(loadOpCode, localVarIndex);
                }
            }
        }
    }

    public String createAliasMethod(String originalMethodName, String descriptor, boolean isConstructor) {
        String aliasOfOriginName = originalMethodName + ALIAS_METHOD_POSTFIX;
        MethodVisitor mv = classWriter.visitMethod(ACC_PUBLIC + ACC_NATIVE, aliasOfOriginName, descriptor, null, null);
        AnnotationVisitor aliasV = mv.visitAnnotation(ALIAS_ANNOTATION_NAME, true);
        /** Add {@link Alias#noSubstitution()} */
        aliasV.visit("noSubstitution", true);
        aliasV.visitEnd();
        AnnotationVisitor targetElementV = mv.visitAnnotation(TARGET_ELEMENT_ANNOTATION_NAME, true);
        /** Add {@link TargetElement#name()} */
        targetElementV.visit("name", isConstructor ? TargetElement.CONSTRUCTOR_NAME : originalMethodName);
        targetElementV.visitEnd();
        mv.visitEnd();
        return aliasOfOriginName;
    }
}
