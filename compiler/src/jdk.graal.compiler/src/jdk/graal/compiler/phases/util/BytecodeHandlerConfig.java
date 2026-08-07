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
 * expanded fields and the maximum opcode for which a handler exists.
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
    private final List<ArgumentInfo> templateVariableArguments;
    /**
     * Projection of {@link #allArgumentInfos} containing only values passed through the generated
     * stub ABI. When template mode is enabled, template variables are excluded because generated
     * stubs initialize them from their template variant.
     */
    private final List<ArgumentInfo> stubAbiArgumentInfos;
    /**
     * Unmodifiable projection of {@link #stubAbiArgumentInfos} containing only the argument types.
     * This duplicates part of the state in {@code stubAbiArgumentInfos}, so changes to how it is
     * derived must keep {@link #equals(Object)} and {@link #hashCode()} in sync.
     */
    private final List<ResolvedJavaType> stubAbiArgumentTypes;

    private BytecodeHandlerConfig(int maximumOperationCode, int templatesLength, ResolvedJavaType returnType, List<ArgumentInfo> arguments) {
        this.maximumOperationCode = maximumOperationCode;
        this.templatesLength = templatesLength;
        this.returnType = Objects.requireNonNull(returnType, "returnType");
        this.allArgumentInfos = Collections.unmodifiableList(new ArrayList<>(arguments));
        List<ArgumentInfo> templateVariables = new ArrayList<>();
        List<ArgumentInfo> abiArguments = new ArrayList<>(arguments.size());
        for (ArgumentInfo info : arguments) {
            if (info.isTemplateVariable()) {
                templateVariables.add(info);
            }
            if (info.index() >= 0) {
                abiArguments.add(info);
            }
        }
        this.templateVariableArguments = Collections.unmodifiableList(templateVariables);
        this.stubAbiArgumentInfos = Collections.unmodifiableList(abiArguments);
        List<ResolvedJavaType> types = new ArrayList<>(abiArguments.size());
        for (ArgumentInfo info : abiArguments) {
            types.add(info.type());
        }
        this.stubAbiArgumentTypes = Collections.unmodifiableList(types);
        assert verifyStubAbiArguments(this.stubAbiArgumentInfos);
    }

    /**
     * Builds a {@link BytecodeHandlerConfig} by interpreting the bytecode handler annotation located
     * on {@code targetMethod} with template mode disabled.
     */
    public static BytecodeHandlerConfig fromAnnotation(AnnotationValue handlerConfig, ResolvedJavaMethod targetMethod) {
        return fromAnnotation(handlerConfig, targetMethod, false);
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

    /**
     * Derives the {@link BytecodeHandlerConfig} used for stubs targeting {@code targetMethod} when
     * called from {@code enclosingMethod}. The annotation is read from the enclosing interpreter
     * method, not from the handler method itself. Template mode is disabled.
     */
    public static BytecodeHandlerConfig getHandlerConfig(ResolvedJavaMethod enclosingMethod, ResolvedJavaMethod targetMethod) {
        return getHandlerConfig(enclosingMethod, targetMethod, false);
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
                    int abiIndex = templateVariants > 0 ? -1 : nextIndex++;
                    arguments.add(new ArgumentInfo(fieldType, abiIndex, originalIndex, false, false, true, parameterType, javaField, true, javaField.isFinal(),
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

    private static boolean verifyStubAbiArguments(List<ArgumentInfo> arguments) {
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

    public int getTemplatesLength() {
        return templatesLength;
    }

    public ResolvedJavaType getReturnType() {
        return returnType;
    }

    /**
     * Returns the complete handler argument model in Java call-shape order. Use this when
     * reconstructing the original handler arguments. When template mode is enabled, template
     * variables are part of that shape even though they are not passed through the stub ABI.
     */
    public List<ArgumentInfo> getAllArgumentInfos() {
        return allArgumentInfos;
    }

    /**
     * Returns only the template-variable field arguments.
     */
    public List<ArgumentInfo> getTemplateVariableArguments() {
        return templateVariableArguments;
    }

    /**
     * Returns the dense stub ABI projection. Use this for stub parameters, foreign-call
     * signatures, multi-return slots, register assignment, and any lookup by
     * {@link ArgumentInfo#index()}.
     */
    public List<ArgumentInfo> getStubAbiArgumentInfos() {
        return stubAbiArgumentInfos;
    }

    /**
     * Returns the parameter types for the dense stub ABI projection.
     */
    public List<ResolvedJavaType> getStubAbiArgumentTypes() {
        return stubAbiArgumentTypes;
    }

    /**
     * Returns the number of thread-local slots required to preserve ordinary ABI arguments and
     * template variables when a handler chain returns to Java.
     */
    public int getPendingStateSlotCount() {
        return stubAbiArgumentInfos.size() + templateVariableArguments.size();
    }

    /**
     * Returns the thread-local slot assigned to a template variable. Template slots follow the
     * dense stub ABI slots and do not form part of the stub calling convention.
     */
    public int getTemplateVariablePendingStateSlot(int templateVariableIndex) {
        return stubAbiArgumentInfos.size() + templateVariableIndex;
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

    public boolean isStubAbiArgumentImmutable(int index) {
        return getStubAbiArgumentInfos().get(index).isImmutable();
    }

    public boolean hasPendingExceptionState() {
        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
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
        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
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
     * Describes one argument slot in the bytecode-handler stub ABI. Expanded Java parameters can
     * produce multiple {@link ArgumentInfo}s that share the same {@link #originalIndex()} but map to
     * different stub ABI {@link #index()} values. In template mode, template variables have index
     * {@code -1} and are excluded from the stub ABI.
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

        /**
         * Returns {@code true} for values that must be recoverable on a generated stub's exception
         * edge: the {@code copyFromReturn} value and mutable fields of virtual-expanded arguments.
         */
        public boolean needsPendingExceptionState() {
            return copyFromReturn || (isExpanded && isOwnerVirtual && !isImmutable);
        }
    }
}
