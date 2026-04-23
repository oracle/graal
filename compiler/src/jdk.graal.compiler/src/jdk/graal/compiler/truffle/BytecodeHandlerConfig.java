/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Aggregates metadata derived from a {@code BytecodeInterpreterHandlerConfig} annotation which is
 * resolved against a concrete bytecode handler signature. This includes information about
 * parameters and the maximum opcode for which a handler exists.
 */
public final class BytecodeHandlerConfig {

    private final int maximumOperationCode;
    private final ResolvedJavaType returnType;
    private final List<ArgumentInfo> argumentInfos;
    /**
     * Unmodifiable projection of {@link #argumentInfos} containing only the argument types. This
     * duplicates part of the state in {@code argumentInfos}, so changes to how it is derived must
     * keep {@link #equals(Object)} and {@link #hashCode()} in sync.
     */
    private final List<ResolvedJavaType> argumentTypes;

    private BytecodeHandlerConfig(int maximumOperationCode, ResolvedJavaType returnType, List<ArgumentInfo> arguments) {
        this.maximumOperationCode = maximumOperationCode;
        this.returnType = Objects.requireNonNull(returnType, "returnType");
        this.argumentInfos = Collections.unmodifiableList(new ArrayList<>(arguments));
        List<ResolvedJavaType> types = new ArrayList<>(arguments.size());
        for (ArgumentInfo info : arguments) {
            types.add(info.type());
        }
        this.argumentTypes = Collections.unmodifiableList(types);
        assert verifyArguments(this.argumentInfos);
    }

    /**
     * Builds a {@link BytecodeHandlerConfig} by interpreting the Truffle handler annotation located
     * on {@code targetMethod}.
     */
    public static BytecodeHandlerConfig fromAnnotation(AnnotationValue handlerConfig, ResolvedJavaMethod targetMethod) {
        Objects.requireNonNull(handlerConfig, "handlerConfig");
        Objects.requireNonNull(targetMethod, "targetMethod");

        int maximumOperationCode = handlerConfig.getInt("maximumOperationCode");
        List<AnnotationValue> argumentAnnotations = handlerConfig.getList("arguments", AnnotationValue.class);

        ResolvedJavaType declaringClass = targetMethod.getDeclaringClass();
        Signature signature = targetMethod.getSignature();
        ResolvedJavaType returnType = signature.getReturnType(declaringClass).resolve(declaringClass);

        List<ArgumentInfo> arguments = new ArrayList<>();
        int originalIndex = 0;
        int currentIndex = 0;

        if (!targetMethod.isStatic()) {
            currentIndex = appendReceiver(arguments, argumentAnnotations.get(originalIndex), declaringClass, originalIndex, currentIndex);
            originalIndex++;
        }

        int parameterCount = signature.getParameterCount(false);
        for (int i = 0; i < parameterCount; i++, originalIndex++) {
            ResolvedJavaType parameterType = signature.getParameterType(i, declaringClass).resolve(declaringClass);
            currentIndex = appendParameter(arguments, argumentAnnotations.get(originalIndex), parameterType, declaringClass, originalIndex, currentIndex);
        }

        return new BytecodeHandlerConfig(maximumOperationCode, returnType, arguments);
    }

    /**
     * Derives the {@link BytecodeHandlerConfig} used for stubs targeting {@code targetMethod} when
     * called from {@code enclosingMethod}. The annotation is read from the enclosing interpreter
     * method, not from the handler method itself.
     */
    public static BytecodeHandlerConfig getHandlerConfig(ResolvedJavaMethod enclosingMethod, ResolvedJavaMethod targetMethod, TruffleBytecodeHandlerTypes truffleTypes) {
        AnnotationValue configAnnotation = AnnotationValueSupport.getDeclaredAnnotationValue(truffleTypes.typeBytecodeInterpreterHandlerConfig(), enclosingMethod);
        return BytecodeHandlerConfig.fromAnnotation(configAnnotation, targetMethod);
    }

    private static int appendReceiver(List<ArgumentInfo> arguments, AnnotationValue receiverConfig, ResolvedJavaType declaringClass, int originalIndex, int currentIndex) {
        ExpansionKind expansionKind = getExpansionKind(receiverConfig);
        boolean nonNull = receiverConfig.getBoolean("nonNull");
        int nextIndex = currentIndex;

        switch (expansionKind) {
            case NONE -> {
                arguments.add(new ArgumentInfo(declaringClass, nextIndex++, originalIndex, false, false, false, null, null, false, true, nonNull));
            }
            case VIRTUAL -> throw GraalError.shouldNotReachHere("Receiver cannot be VIRTUAL");
            case MATERIALIZED -> {
                arguments.add(new ArgumentInfo(declaringClass, nextIndex++, originalIndex, false, true, false, null, null, false, true, nonNull));
                List<AnnotationValue> fields = receiverConfig.getList("fields", AnnotationValue.class);
                for (ResolvedJavaField javaField : declaringClass.getInstanceFields(true)) {
                    AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
                    if (fieldConfig != null) {
                        ResolvedJavaType fieldType = javaField.getType().resolve(declaringClass);
                        boolean fieldNonNull = !fieldType.isPrimitive() && fieldConfig.getBoolean("nonNull");
                        arguments.add(new ArgumentInfo(fieldType, nextIndex++, originalIndex, false, false, true, declaringClass, javaField, false, javaField.isFinal(), fieldNonNull));
                    }
                }
            }
            default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + expansionKind);
        }
        return nextIndex;
    }

    private static int appendParameter(List<ArgumentInfo> arguments, AnnotationValue parameterConfig, ResolvedJavaType parameterType, ResolvedJavaType declaringClass, int originalIndex,
                    int currentIndex) {
        ExpansionKind expansionKind = getExpansionKind(parameterConfig);
        boolean copyFromReturn = parameterConfig.getBoolean("returnValue");
        boolean nonNull = !parameterType.isPrimitive() && parameterConfig.getBoolean("nonNull");
        int nextIndex = currentIndex;

        switch (expansionKind) {
            case NONE -> {
                arguments.add(new ArgumentInfo(parameterType, nextIndex++, originalIndex, copyFromReturn, false, false, null, null, false, !copyFromReturn, nonNull));
            }
            case VIRTUAL -> {
                List<AnnotationValue> fields = parameterConfig.getList("fields", AnnotationValue.class);
                for (ResolvedJavaField javaField : parameterType.getInstanceFields(true)) {
                    ResolvedJavaType fieldType = javaField.getType().resolve(declaringClass);
                    boolean fieldNonNull = false;
                    if (!fieldType.isPrimitive()) {
                        AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
                        fieldNonNull = fieldConfig != null && fieldConfig.getBoolean("nonNull");
                    }
                    arguments.add(new ArgumentInfo(fieldType, nextIndex++, originalIndex, false, false, true, parameterType, javaField, true, javaField.isFinal(), fieldNonNull));
                }
            }
            case MATERIALIZED -> {
                arguments.add(new ArgumentInfo(parameterType, nextIndex++, originalIndex, copyFromReturn, true, false, null, null, false, true, nonNull));
                List<AnnotationValue> fields = parameterConfig.getList("fields", AnnotationValue.class);
                for (ResolvedJavaField javaField : parameterType.getInstanceFields(true)) {
                    AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
                    if (fieldConfig != null) {
                        ResolvedJavaType fieldType = javaField.getType().resolve(declaringClass);
                        boolean fieldNonNull = !fieldType.isPrimitive() && fieldConfig.getBoolean("nonNull");
                        arguments.add(new ArgumentInfo(fieldType, nextIndex++, originalIndex, false, false, true, declaringClass, javaField, false, javaField.isFinal(), fieldNonNull));
                    }
                }
            }
            default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + expansionKind);
        }
        return nextIndex;
    }

    private static AnnotationValue findFieldConfig(List<AnnotationValue> fields, String name) {
        for (AnnotationValue fieldConfig : fields) {
            if (fieldConfig.getString("name").equals(name)) {
                return fieldConfig;
            }
        }
        return null;
    }

    private static ExpansionKind getExpansionKind(AnnotationValue argumentConfig) {
        return ExpansionKind.valueOf(argumentConfig.getEnum("expand").name);
    }

    private static boolean verifyArguments(List<ArgumentInfo> arguments) {
        boolean copyFromReturnSeen = false;
        for (int i = 0; i < arguments.size(); i++) {
            ArgumentInfo argumentInfo = arguments.get(i);
            assert argumentInfo.index == i : Assertions.errorMessage("Unaligned argument", argumentInfo);
            assert !(argumentInfo.copyFromReturn && copyFromReturnSeen) : Assertions.errorMessage("Multiple arguments with returnValue set to true", argumentInfo);
            copyFromReturnSeen |= argumentInfo.copyFromReturn;
        }
        return true;
    }

    public int getMaximumOperationCode() {
        return maximumOperationCode;
    }

    public ResolvedJavaType getReturnType() {
        return returnType;
    }

    public List<ArgumentInfo> getArgumentInfos() {
        return argumentInfos;
    }

    public List<ResolvedJavaType> getArgumentTypes() {
        return argumentTypes;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BytecodeHandlerConfig other)) {
            return false;
        }
        return maximumOperationCode == other.maximumOperationCode && returnType.equals(other.returnType) && argumentInfos.equals(other.argumentInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumOperationCode, returnType, argumentInfos);
    }

    public boolean isArgumentImmutable(int index) {
        return getArgumentInfos().get(index).isImmutable();
    }

    private enum ExpansionKind {
        NONE,
        MATERIALIZED,
        VIRTUAL
    }

    public record ArgumentInfo(ResolvedJavaType type,
                    int index,
                    int originalIndex,
                    boolean copyFromReturn,
                    boolean isOwner,
                    boolean isExpanded,
                    ResolvedJavaType ownerType,
                    ResolvedJavaField field,
                    boolean isOwnerVirtual,
                    boolean isImmutable,
                    boolean nonNull) {
    }
}
