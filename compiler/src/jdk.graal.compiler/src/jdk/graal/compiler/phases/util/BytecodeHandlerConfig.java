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
package jdk.graal.compiler.phases.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Aggregates metadata derived from a {@code BytecodeInterpreterHandlerConfig} annotation which is
 * resolved against a concrete bytecode handler signature. The resulting model describes the stub
 * ABI: argument expansion, non-null guarantees, {@code returnValue}/copy-from-return slots, mutable
 * expanded fields, and the maximum opcode for which a handler exists.
 */
public final class BytecodeHandlerConfig {

    private final int maximumOperationCode;
    private final int templatesLength;
    private final ResolvedJavaType returnType;
    /**
     * Complete handler argument model in Java call-shape order, including template variables.
     */
    private final List<ArgumentInfo> allArgumentInfos;
    /**
     * Projection of {@link #allArgumentInfos} containing only template-variable fields.
     */
    private final List<ArgumentInfo> templateArgumentInfos;
    /**
     * Projection of {@link #allArgumentInfos} containing values threaded between generated stubs.
     * Template variables are excluded because each handler variant initializes them from constants.
     */
    private final List<ArgumentInfo> calleeParameterInfos;
    /**
     * Arguments returned by a terminating stub in multi-return index order. Template arguments
     * occupy the suffix after the actual stub parameters.
     */
    private final List<ArgumentInfo> callerReturnInfos;
    /**
     * Unmodifiable projection of {@link #calleeParameterInfos} containing only the parameter types.
     */
    private final List<ResolvedJavaType> calleeParameterTypes;

    private BytecodeHandlerConfig(int maximumOperationCode, int templatesLength, ResolvedJavaType returnType, List<ArgumentInfo> arguments) {
        this.maximumOperationCode = maximumOperationCode;
        this.templatesLength = templatesLength;
        this.returnType = Objects.requireNonNull(returnType, "returnType");
        this.allArgumentInfos = Collections.unmodifiableList(new ArrayList<>(arguments));
        List<ArgumentInfo> templateArguments = new ArrayList<>();
        List<ArgumentInfo> stubParameters = new ArrayList<>(arguments.size());
        for (ArgumentInfo info : arguments) {
            if (info.isTemplateVariable()) {
                templateArguments.add(info);
            } else {
                stubParameters.add(info);
            }
        }
        this.templateArgumentInfos = Collections.unmodifiableList(templateArguments);
        this.calleeParameterInfos = Collections.unmodifiableList(stubParameters);
        List<ArgumentInfo> terminationReturns = new ArrayList<>(arguments.size());
        terminationReturns.addAll(stubParameters);
        terminationReturns.addAll(templateArguments);
        this.callerReturnInfos = Collections.unmodifiableList(terminationReturns);
        List<ResolvedJavaType> types = new ArrayList<>(stubParameters.size());
        for (ArgumentInfo info : stubParameters) {
            types.add(info.type());
        }
        this.calleeParameterTypes = Collections.unmodifiableList(types);
        assert verifyArgumentIndexes(this.calleeParameterInfos, this.templateArgumentInfos);
    }

    /**
     * Builds a {@link BytecodeHandlerConfig} by interpreting the bytecode handler annotation located
     * on {@code targetMethod}. When {@code templateModeEnabled} is false, {@code templateVariable}
     * metadata is ignored and those fields remain ordinary ABI arguments.
     */
    public static BytecodeHandlerConfig fromAnnotation(AnnotationValue handlerConfig, ResolvedJavaMethod targetMethod, boolean templateModeEnabled) {
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
            GraalError.guarantee(originalIndex < argumentAnnotations.size(), "Missing receiver argument config for %s", targetMethod);
            currentIndex = appendReceiver(arguments, argumentAnnotations.get(originalIndex), declaringClass, originalIndex, currentIndex,
                            templateModeEnabled);
            originalIndex++;
        }

        int parameterCount = signature.getParameterCount(false);
        for (int i = 0; i < parameterCount; i++, originalIndex++) {
            GraalError.guarantee(originalIndex < argumentAnnotations.size(), "Missing argument config for parameter %d of %s", i, targetMethod);
            ResolvedJavaType parameterType = signature.getParameterType(i, declaringClass).resolve(declaringClass);
            currentIndex = appendParameter(arguments, argumentAnnotations.get(originalIndex), parameterType, declaringClass, originalIndex, currentIndex,
                            templateModeEnabled);
        }
        GraalError.guarantee(originalIndex == argumentAnnotations.size(), "Unused argument config for %s", targetMethod);

        assignTemplateArgumentIndexes(arguments, currentIndex);
        int templatesLength = computeTemplatesLength(arguments);

        return new BytecodeHandlerConfig(maximumOperationCode, templatesLength, returnType, arguments);
    }

    private static int computeTemplatesLength(List<ArgumentInfo> arguments) {
        int result = 1;
        for (ArgumentInfo argumentInfo : arguments) {
            int templateVariants = argumentInfo.templateVariants();
            if (templateVariants > 0) {
                GraalError.guarantee(result <= Integer.MAX_VALUE / templateVariants, "Template variant count exceeds int range");
                result *= templateVariants;
            }
        }
        return result;
    }

    private static void assignTemplateArgumentIndexes(List<ArgumentInfo> arguments, int firstTemplateIndex) {
        int nextIndex = firstTemplateIndex;
        for (int i = 0; i < arguments.size(); i++) {
            ArgumentInfo argumentInfo = arguments.get(i);
            if (argumentInfo.isTemplateVariable()) {
                arguments.set(i, argumentInfo.withIndex(nextIndex++));
            }
        }
    }

    /**
     * Derives the {@link BytecodeHandlerConfig} used for stubs targeting {@code targetMethod} when
     * called from {@code enclosingMethod}. If {@code templateModeEnabled} is false,
     * {@code templateVariable} metadata is ignored and those fields remain ordinary ABI arguments.
     */
    public static BytecodeHandlerConfig getHandlerConfig(ResolvedJavaMethod enclosingMethod, ResolvedJavaMethod targetMethod, boolean templateModeEnabled) {
        AnnotationValue configAnnotation = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandlerConfig(enclosingMethod);
        GraalError.guarantee(configAnnotation != null, "Method %s is missing @BytecodeInterpreterHandlerConfig", enclosingMethod.format("%H.%n(%p)"));
        return BytecodeHandlerConfig.fromAnnotation(configAnnotation, targetMethod, templateModeEnabled);
    }

    private static int appendReceiver(List<ArgumentInfo> arguments, AnnotationValue receiverConfig, ResolvedJavaType declaringClass, int originalIndex, int currentIndex,
                    boolean templateModeEnabled) {
        ExpansionKind expansionKind = getExpansionKind(receiverConfig);
        boolean nonNull = receiverConfig.getBoolean("nonNull");
        int nextIndex = currentIndex;

        switch (expansionKind) {
            case NONE -> {
                arguments.add(new ArgumentInfo(declaringClass, nextIndex++, originalIndex, false, false, false, null, null, false, true, nonNull, 0));
            }
            case VIRTUAL -> throw GraalError.shouldNotReachHere("Receiver cannot be VIRTUAL");
            case MATERIALIZED -> {
                arguments.add(new ArgumentInfo(declaringClass, nextIndex++, originalIndex, false, true, false, null, null, false, true, nonNull, 0));
                nextIndex = appendMaterializedFields(arguments, receiverConfig, declaringClass, declaringClass, originalIndex, nextIndex, templateModeEnabled);
            }
            default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + expansionKind);
        }
        return nextIndex;
    }

    private static int appendParameter(List<ArgumentInfo> arguments, AnnotationValue parameterConfig, ResolvedJavaType parameterType, ResolvedJavaType declaringClass, int originalIndex,
                    int currentIndex, boolean templateModeEnabled) {
        ExpansionKind expansionKind = getExpansionKind(parameterConfig);
        boolean copyFromReturn = parameterConfig.getBoolean("returnValue");
        boolean nonNull = !parameterType.isPrimitive() && parameterConfig.getBoolean("nonNull");
        int nextIndex = currentIndex;

        switch (expansionKind) {
            case NONE -> {
                arguments.add(new ArgumentInfo(parameterType, nextIndex++, originalIndex, copyFromReturn, false, false, null, null, false, !copyFromReturn, nonNull, 0));
            }
            case VIRTUAL -> {
                List<AnnotationValue> fields = parameterConfig.getList("fields", AnnotationValue.class);
                for (ResolvedJavaField javaField : parameterType.getInstanceFields(true)) {
                    ResolvedJavaType fieldType = javaField.getType().resolve(declaringClass);
                    boolean fieldNonNull = false;
                    AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
                    int templateVariants = templateModeEnabled ? getTemplateVariants(fieldConfig, javaField) : 0;
                    if (!fieldType.isPrimitive()) {
                        fieldNonNull = fieldConfig != null && fieldConfig.getBoolean("nonNull");
                        GraalError.guarantee(templateVariants == 0, "Field %s is marked as a template variable", javaField.format("%H.%n"));
                    } else if (templateVariants > 0) {
                        GraalError.guarantee(fieldType.getJavaKind() == JavaKind.Int, "Template variable field %s must be int", javaField.format("%H.%n"));
                    }
                    int argumentIndex = templateVariants > 0 ? -1 : nextIndex++;
                    arguments.add(new ArgumentInfo(fieldType, argumentIndex, originalIndex, false, false, true, parameterType, javaField, true, javaField.isFinal(),
                                    fieldNonNull, templateVariants));
                }
            }
            case MATERIALIZED -> {
                arguments.add(new ArgumentInfo(parameterType, nextIndex++, originalIndex, copyFromReturn, true, false, null, null, false, true, nonNull, 0));
                nextIndex = appendMaterializedFields(arguments, parameterConfig, parameterType, declaringClass, originalIndex, nextIndex, templateModeEnabled);
            }
            default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + expansionKind);
        }
        return nextIndex;
    }

    private static int appendMaterializedFields(List<ArgumentInfo> arguments, AnnotationValue materializedConfig, ResolvedJavaType expandedType, ResolvedJavaType declaringClass, int originalIndex,
                    int currentIndex, boolean templateModeEnabled) {
        int nextIndex = currentIndex;
        List<AnnotationValue> fields = materializedConfig.getList("fields", AnnotationValue.class);
        for (ResolvedJavaField javaField : expandedType.getInstanceFields(true)) {
            AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
            if (fieldConfig != null) {
                ResolvedJavaType fieldType = javaField.getType().resolve(declaringClass);
                boolean fieldNonNull = !fieldType.isPrimitive() && fieldConfig.getBoolean("nonNull");
                if (templateModeEnabled) {
                    GraalError.guarantee(getTemplateVariants(fieldConfig, javaField) == 0, "Field %s is marked as a template variable", javaField.format("%H.%n"));
                }
                arguments.add(new ArgumentInfo(fieldType, nextIndex++, originalIndex, false, false, true, declaringClass, javaField, false, javaField.isFinal(),
                                fieldNonNull, 0));
            }
        }
        return nextIndex;
    }

    private static int getTemplateVariants(AnnotationValue fieldConfig, ResolvedJavaField javaField) {
        if (fieldConfig == null) {
            return 0;
        }
        int templateVariants = fieldConfig.getInt("templateVariable");
        GraalError.guarantee(templateVariants >= 0, "Template variable field %s has %d variants", javaField.format("%H.%n"), templateVariants);
        GraalError.guarantee(templateVariants != 1, "Template variable field %s has one variant", javaField.format("%H.%n"));
        return templateVariants;
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

    private static boolean verifyArgumentIndexes(List<ArgumentInfo> stubParameters, List<ArgumentInfo> templateArguments) {
        boolean copyFromReturnSeen = false;
        for (int i = 0; i < stubParameters.size(); i++) {
            ArgumentInfo argumentInfo = stubParameters.get(i);
            assert argumentInfo.index == i : Assertions.errorMessage("Unaligned argument", argumentInfo);
            assert !(argumentInfo.copyFromReturn && copyFromReturnSeen) : Assertions.errorMessage("Multiple arguments with returnValue set to true", argumentInfo);
            copyFromReturnSeen |= argumentInfo.copyFromReturn;
        }
        for (int i = 0; i < templateArguments.size(); i++) {
            ArgumentInfo argumentInfo = templateArguments.get(i);
            assert argumentInfo.index == stubParameters.size() + i : Assertions.errorMessage("Unaligned template argument", argumentInfo);
        }
        return true;
    }

    public int getMaximumOperationCode() {
        return maximumOperationCode;
    }

    public int getTemplatesLength() {
        return templatesLength;
    }

    public ResolvedJavaType getReturnType() {
        return returnType;
    }

    /**
     * Returns the complete handler argument model in Java call-shape order. Use this when
     * reconstructing the original handler arguments. When template mode is enabled, template
     * variables are part of that shape even though they are initialized from the selected variant
     * instead of callee parameters. Callee parameters have prefix indexes and template arguments
     * have suffix indexes, so a template argument's index is not necessarily its position in this
     * list.
     */
    public List<ArgumentInfo> getAllArgumentInfos() {
        return allArgumentInfos;
    }

    /**
     * Returns the implicit template arguments in their logical index order.
     */
    public List<ArgumentInfo> getTemplateArgumentInfos() {
        return templateArgumentInfos;
    }

    /**
     * Returns the arguments passed between threaded stubs in logical index order.
     */
    public List<ArgumentInfo> getCalleeParameterInfos() {
        return calleeParameterInfos;
    }

    /**
     * Returns all argument values produced when a handler chain terminates. Template arguments are
     * trailing return values and are not callee parameters.
     */
    public List<ArgumentInfo> getCallerReturnInfos() {
        return callerReturnInfos;
    }

    /**
     * Returns the types of the actual stub parameters.
     */
    public List<ResolvedJavaType> getCalleeParameterTypes() {
        return calleeParameterTypes;
    }

    /**
     * Returns the number of thread-local slots required to preserve all logical arguments when a
     * handler chain unwinds to Java.
     */
    public int getPendingStateSlotCount() {
        return allArgumentInfos.size();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BytecodeHandlerConfig other)) {
            return false;
        }
        return maximumOperationCode == other.maximumOperationCode && templatesLength == other.templatesLength && returnType.equals(other.returnType) &&
                        allArgumentInfos.equals(other.allArgumentInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumOperationCode, templatesLength, returnType, allArgumentInfos);
    }

    public boolean hasPendingExceptionState() {
        if (!templateArgumentInfos.isEmpty()) {
            return true;
        }
        for (ArgumentInfo argumentInfo : calleeParameterInfos) {
            if (argumentInfo.needsPendingExceptionState()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if one argument receives the handler return value.
     */
    public boolean hasCopyFromReturnArgument() {
        return getCopyFromReturnArgument() != null;
    }

    /**
     * Returns the argument that receives the handler return value, or {@code null} if this handler
     * has no {@code returnValue} argument.
     */
    public ArgumentInfo getCopyFromReturnArgument() {
        for (ArgumentInfo argumentInfo : calleeParameterInfos) {
            if (argumentInfo.copyFromReturn()) {
                return argumentInfo;
            }
        }
        return null;
    }

    private enum ExpansionKind {
        NONE,
        MATERIALIZED,
        VIRTUAL
    }

    /**
     * Describes one logical bytecode-handler argument. Expanded Java parameters can produce
     * multiple {@link ArgumentInfo}s that share the same {@link #originalIndex()}. Callee
     * parameters occupy a dense index prefix; template arguments occupy the caller-return suffix
     * and are initialized from the selected template variant.
     */
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
                    boolean nonNull,
                    int templateVariants) {
        public boolean isTemplateVariable() {
            return templateVariants > 0;
        }

        private ArgumentInfo withIndex(int newIndex) {
            return new ArgumentInfo(type, newIndex, originalIndex, copyFromReturn, isOwner, isExpanded, ownerType, field, isOwnerVirtual, isImmutable,
                            nonNull, templateVariants);
        }

        /**
         * Returns {@code true} for values that must be recoverable on a generated stub's exception
         * edge: the {@code copyFromReturn} value and mutable fields of virtual-expanded arguments.
         */
        public boolean needsPendingExceptionState() {
            return copyFromReturn || (isExpanded && isOwnerVirtual && !isImmutable);
        }
    }
}
