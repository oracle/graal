/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getTypeElement;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.isAssignable;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEqualsAny;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleSuppressedWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.ConstantOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.DynamicOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.ConstantOperandsModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel.Operator;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public final class CustomOperationParser extends AbstractParser<CustomOperationModel> {

    private final ProcessorContext context;
    private final BytecodeDSLModel parent;
    private final DeclaredType annotationType;
    private final boolean forProxyValidation;
    private boolean uncachedProxyValidation;

    private CustomOperationParser(ProcessorContext context, BytecodeDSLModel parent, DeclaredType annotationType, boolean forProxyValidation) {
        this.context = context;
        this.parent = parent;
        this.annotationType = annotationType;
        this.forProxyValidation = forProxyValidation;
    }

    public static CustomOperationParser forProxyValidation() {
        ProcessorContext context = ProcessorContext.getInstance();
        CodeTypeElement dummyBytecodeClass = new CodeTypeElement(Set.of(), ElementKind.CLASS, null, "DummyBytecodeClass");
        dummyBytecodeClass.setSuperClass(context.getTypes().Node);
        dummyBytecodeClass.setEnclosingElement(new GeneratedPackageElement("dummy"));
        return new CustomOperationParser(
                        context,
                        new BytecodeDSLModel(context, dummyBytecodeClass, null, null, null),
                        context.getTypes().OperationProxy_Proxyable,
                        true);
    }

    public static CustomOperationParser forCodeGeneration(BytecodeDSLModel parent, DeclaredType annotationType) {
        ProcessorContext context = parent.getContext();
        if (isHandled(context, annotationType)) {
            return new CustomOperationParser(context, parent, annotationType, false);
        } else {
            throw new IllegalArgumentException(String.format("%s does not handle the %s annotation.", CustomOperationParser.class.getName(), annotationType));
        }
    }

    private static boolean isHandled(ProcessorContext context, TypeMirror annotationType) {
        Types typeUtils = context.getEnvironment().getTypeUtils();
        TruffleTypes truffleTypes = context.getTypes();
        for (DeclaredType handled : new DeclaredType[]{truffleTypes.Operation, truffleTypes.OperationProxy_Proxyable, truffleTypes.ShortCircuitOperation}) {
            if (typeUtils.isSameType(annotationType, handled)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected CustomOperationModel parse(Element element, List<AnnotationMirror> annotationMirrors) {
        /**
         * This entrypoint is only invoked by the TruffleProcessor to validate Proxyable nodes. We
         * directly invoke {@link parseCustomRegularOperation} for code gen use cases.
         */
        if (!ElementUtils.typeEquals(annotationType, context.getTypes().OperationProxy_Proxyable)) {
            throw new AssertionError();
        }

        TypeElement typeElement = (TypeElement) element;
        if (annotationMirrors.size() != 1) {
            throw new IllegalArgumentException(String.format("Expected element %s to have one %s annotation, but %d found.", typeElement.getSimpleName(), annotationType, annotationMirrors.size()));
        }
        AnnotationMirror mirror = annotationMirrors.get(0);
        return parseCustomRegularOperation(mirror, typeElement, null);
    }

    CustomOperationModel parseCustomRegularOperation(AnnotationMirror mirror, TypeElement typeElement, String explicitName) {
        if (forProxyValidation) {
            this.uncachedProxyValidation = ElementUtils.getAnnotationValue(Boolean.class, mirror, "allowUncached");
        }
        if (isShortCircuit()) {
            throw new AssertionError();
        }

        String name = getCustomOperationName(typeElement, explicitName);
        String javadoc = ElementUtils.getAnnotationValue(String.class, mirror, "javadoc");
        boolean isInstrumentation = ElementUtils.typeEquals(mirror.getAnnotationType(), types.Instrumentation);
        OperationKind kind = isInstrumentation ? OperationKind.CUSTOM_INSTRUMENTATION : OperationKind.CUSTOM;
        CustomOperationModel customOperation = parent.customRegularOperation(kind, name, javadoc, typeElement, mirror);
        if (customOperation == null) {
            return null;
        }

        AnnotationValue forceCachedValue = ElementUtils.getAnnotationValue(mirror, "forceCached", false);
        if (forceCachedValue != null) {
            boolean forceCached = ElementUtils.resolveAnnotationValue(Boolean.class, forceCachedValue);
            if (forceCached) {
                if (parent.enableUncachedInterpreter) {
                    customOperation.setForceCached();
                } else {
                    customOperation.getModelForMessages().addError(mirror, forceCachedValue,
                                    "The uncached interpreter is not enabled, so forceCached has no effect. Remove the forceCached attribute to resolve this error.");
                }
            } else {
                customOperation.getModelForMessages().addError(mirror, forceCachedValue,
                                "Setting forceCached to false has no effect. Remove the forceCached attribute or set it to true to resolve this error.");
            }
        }

        OperationModel operation = customOperation.operation;

        validateCustomOperation(customOperation, typeElement, mirror, name);
        ConstantOperandsModel constantOperands = getConstantOperands(customOperation, typeElement, mirror);
        operation.constantOperands = constantOperands;
        if (customOperation.hasErrors()) {
            return customOperation;
        }

        CodeTypeElement generatedNode = createNodeForCustomInstruction(typeElement);
        List<ExecutableElement> specializations = findSpecializations(generatedNode);
        if (specializations.size() == 0) {
            customOperation.addError("Operation class %s contains no specializations.", generatedNode.getSimpleName());
            return customOperation;
        }

        List<SpecializationSignature> signatures = parseSignatures(specializations, customOperation, constantOperands);
        if (customOperation.hasErrors()) {
            return customOperation;
        }

        Signature signature = SpecializationSignatureParser.createPolymorphicSignature(signatures, specializations, customOperation);
        if (customOperation.hasErrors()) {
            return customOperation;
        }
        if (signature == null) {
            throw new AssertionError("Signature could not be computed, but no error was reported");
        }

        produceConstantOperandWarnings(customOperation, signature, mirror);
        List<String> constantOperandBeforeNames = mergeConstantOperandNames(customOperation, constantOperands.before(), signatures, 0);
        List<String> constantOperandAfterNames = mergeConstantOperandNames(customOperation, constantOperands.after(), signatures,
                        signature.constantOperandsBeforeCount + signature.dynamicOperandCount);
        List<List<String>> dynamicOperandNames = collectDynamicOperandNames(signatures, signature);

        if (operation.kind == OperationKind.CUSTOM_INSTRUMENTATION) {
            validateInstrumentationSignature(customOperation, signature);
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.Prolog)) {
            validatePrologSignature(customOperation, signature);
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.EpilogReturn)) {
            validateEpilogReturnSignature(customOperation, signature);
        } else if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.EpilogExceptional)) {
            validateEpilogExceptionalSignature(customOperation, signature, specializations, signatures);
        } else {
            List<TypeMirror> tags = ElementUtils.getAnnotationValueList(TypeMirror.class, mirror, "tags");
            MessageContainer modelForErrors = customOperation.getModelForMessages();
            if (!tags.isEmpty()) {
                AnnotationValue tagsValue = ElementUtils.getAnnotationValue(mirror, "tags");
                customOperation.implicitTags.addAll(tags);
                if (!parent.enableTagInstrumentation) {
                    modelForErrors.addError(mirror, tagsValue,
                                    "Tag instrumentation is not enabled. The tags attribute can only be used if tag instrumentation is enabled for the parent root node. " +
                                                    "Enable tag instrumentation using @%s(... enableTagInstrumentation = true) to resolve this or remove the tags attribute.",
                                    getSimpleName(types.GenerateBytecode));
                } else {
                    for (TypeMirror tag : tags) {
                        if (!customOperation.bytecode.isTagProvided(tag)) {
                            modelForErrors.addError(mirror, tagsValue,
                                            "Invalid tag '%s' specified. The tag is not provided by language '%s'.",
                                            getSimpleName(tag),
                                            ElementUtils.getQualifiedName(parent.languageClass));
                            break;
                        }
                    }
                }
            }

        }

        AnnotationMirror variadicReturn = ElementUtils.findAnnotationMirror(typeElement.getAnnotationMirrors(), types.Variadic);
        if (variadicReturn != null) {
            if (kind != OperationKind.CUSTOM) {
                customOperation.addError(variadicReturn, null,
                                "@%s can only be used on on @%s annotated classes.",
                                getSimpleName(types.Variadic),
                                getSimpleName(types.Operation));
            }
            Integer startOffset = ElementUtils.getAnnotationValue(Integer.class, variadicReturn, "startOffset", false);
            if (startOffset != null) {
                customOperation.addError(variadicReturn, ElementUtils.getAnnotationValue(variadicReturn, "startOffset"),
                                "@%s.startOffset is not supported for variadic return specifications. It is supported for variadic operands only.",
                                getSimpleName(types.Variadic));
            }
            if (!ElementUtils.typeEquals(signature.returnType, context.getType(Object[].class))) {
                customOperation.addError(variadicReturn, null,
                                "@%s annotated operations must return Object[] for all specializations.",
                                getSimpleName(types.Variadic));
            }

            operation.variadicReturn = true;
        }

        if (customOperation.hasErrors()) {
            return customOperation;
        }

        operation.isVariadic = signature.isVariadic || isShortCircuit();
        operation.variadicOffset = signature.variadicOffset;
        operation.isVoid = signature.isVoid;

        DynamicOperandModel[] dynamicOperands = new DynamicOperandModel[signature.dynamicOperandCount];
        for (int i = 0; i < dynamicOperands.length; i++) {
            dynamicOperands[i] = new DynamicOperandModel(dynamicOperandNames.get(i), false, signature.isVariadicParameter(i));
        }
        operation.dynamicOperands = dynamicOperands;
        operation.constantOperandBeforeNames = constantOperandBeforeNames;
        operation.constantOperandAfterNames = constantOperandAfterNames;
        operation.operationBeginArguments = createOperationConstantArguments(constantOperands.before(), constantOperandBeforeNames);
        operation.operationEndArguments = createOperationConstantArguments(constantOperands.after(), constantOperandAfterNames);

        operation.setInstruction(createCustomInstruction(customOperation, generatedNode, signature, name));

        return customOperation;
    }

    private static List<List<String>> collectDynamicOperandNames(List<SpecializationSignature> signatures, Signature signature) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < signature.dynamicOperandCount; i++) {
            result.add(getDynamicOperandNames(signatures, signature.constantOperandsBeforeCount + i));
        }
        return result;
    }

    private static List<String> mergeConstantOperandNames(CustomOperationModel customOperation, List<ConstantOperandModel> constantOperands, List<SpecializationSignature> signatures,
                    int operandOffset) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < constantOperands.size(); i++) {
            ConstantOperandModel constantOperand = constantOperands.get(i);
            List<String> operandNames = getConstantOperandNames(signatures, constantOperand, operandOffset + i);
            if (operandNames.size() > 1) {
                customOperation.addWarning(constantOperand.mirror(), null,
                                "Specializations use multiple different names for this operand (%s). It is recommended to use the same name in each specialization or to explicitly provide a name for the operand.",
                                operandNames);
            }
            // Take the first name.
            result.add(operandNames.getFirst());
        }
        return result;
    }

    private void produceConstantOperandWarnings(CustomOperationModel customOperation, Signature polymorphicSignature, AnnotationMirror mirror) {
        ConstantOperandsModel constantOperands = customOperation.operation.constantOperands;
        for (ConstantOperandModel constantOperand : constantOperands.before()) {
            warnIfSpecifyAtEndUnnecessary(polymorphicSignature, constantOperand, customOperation, mirror);
        }

        for (ConstantOperandModel constantOperand : constantOperands.after()) {
            warnIfSpecifyAtEndUnnecessary(polymorphicSignature, constantOperand, customOperation, mirror);
        }

    }

    private static List<String> getDynamicOperandNames(List<SpecializationSignature> signatures, int operandIndex) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (SpecializationSignature signature : signatures) {
            result.add(signature.operandNames().get(operandIndex));
        }
        return new ArrayList<>(result);
    }

    private static List<String> getConstantOperandNames(List<SpecializationSignature> signatures, ConstantOperandModel constantOperand, int operandIndex) {
        if (!constantOperand.name().isEmpty()) {
            return List.of(constantOperand.name());
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (SpecializationSignature signature : signatures) {
            result.add(signature.operandNames().get(operandIndex));
        }
        return new ArrayList<>(result);
    }

    private void warnIfSpecifyAtEndUnnecessary(Signature polymorphicSignature, ConstantOperandModel constantOperand, CustomOperationModel customOperation, AnnotationMirror mirror) {
        if (ElementUtils.typeEquals(mirror.getAnnotationType(), types.Prolog)) {
            /*
             * Even though the prolog doesn't take dynamic operands, its constants are supplied via
             * beginRoot/endRoot, so the difference is meaningful.
             */
            return;
        }

        if (polymorphicSignature.dynamicOperandCount == 0 && constantOperand.specifyAtEnd() != null) {
            customOperation.addWarning(constantOperand.mirror(),
                            ElementUtils.getAnnotationValue(constantOperand.mirror(), "specifyAtEnd"),
                            "The specifyAtEnd attribute is unnecessary. This operation does not take any dynamic operands, so all operands will be provided to a single emit%s method.",
                            customOperation.operation.name);
        }
    }

    private void validateInstrumentationSignature(CustomOperationModel customOperation, Signature signature) {
        if (signature.isVoid ^ signature.dynamicOperandCount == 0) {
            if (signature.isVoid) {
                customOperation.addError(String.format("An @%s operation cannot be void and also specify a dynamic operand. " +
                                "Instrumentations must have transparent stack effects. " + //
                                "Change the return type or remove the dynamic operand to resolve this.",
                                getSimpleName(types.Instrumentation)));
            } else {
                customOperation.addError(String.format("An @%s operation cannot have a return value without also specifying a single dynamic operand. " +
                                "Instrumentations must have transparent stack effects. " + //
                                "Use void as the return type or specify a single dynamic operand value to resolve this.",
                                getSimpleName(types.Instrumentation)));
            }
        } else if (signature.dynamicOperandCount > 1) {
            customOperation.addError(String.format("An @%s operation cannot have more than one dynamic operand. " +
                            "Instrumentations must have transparent stack effects. " + //
                            "Remove the additional operands to resolve this.",
                            getSimpleName(types.Instrumentation)));
        } else if (signature.isVariadic) {
            customOperation.addError(String.format("An @%s operation cannot use @%s for its dynamic operand. " +
                            "Instrumentations must have transparent stack effects. " + //
                            "Remove the variadic annotation to resolve this.",
                            getSimpleName(types.Instrumentation),
                            getSimpleName(types.Variadic)));
        }
    }

    private void validatePrologSignature(CustomOperationModel customOperation, Signature signature) {
        if (signature.dynamicOperandCount > 0) {
            customOperation.addError(String.format("A @%s operation cannot have any dynamic operands. " +
                            "Remove the operands to resolve this.",
                            getSimpleName(types.Prolog)));
        } else if (!signature.isVoid) {
            customOperation.addError(String.format("A @%s operation cannot have a return value. " +
                            "Use void as the return type.",
                            getSimpleName(types.Prolog)));
        }
    }

    private void validateEpilogReturnSignature(CustomOperationModel customOperation, Signature signature) {
        if (signature.dynamicOperandCount != 1) {
            customOperation.addError(String.format("An @%s operation must have exactly one dynamic operand for the returned value. " +
                            "Update all specializations to take one operand to resolve this.",
                            getSimpleName(types.EpilogReturn)));
        } else if (signature.isVoid) {
            customOperation.addError(String.format("An @%s operation must have a return value. " +
                            "The result is returned from the root node instead of the original return value. " +
                            "Update all specializations to return a value to resolve this.",
                            getSimpleName(types.EpilogReturn)));
        }

    }

    private void validateEpilogExceptionalSignature(CustomOperationModel customOperation, Signature signature, List<ExecutableElement> specializations, List<SpecializationSignature> signatures) {
        if (signature.dynamicOperandCount != 1) {
            customOperation.addError(String.format("An @%s operation must have exactly one dynamic operand for the exception. " +
                            "Update all specializations to take one operand to resolve this.",
                            getSimpleName(types.EpilogExceptional)));
            return;
        }

        for (int i = 0; i < signatures.size(); i++) {
            Signature individualSignature = signatures.get(i).signature();
            TypeMirror argType = individualSignature.operandTypes.get(0);
            if (!isAssignable(argType, types.AbstractTruffleException)) {
                customOperation.addError(String.format("The operand type for %s must be %s or a subclass.",
                                specializations.get(i).getSimpleName(),
                                getSimpleName(types.AbstractTruffleException)));
            }
        }
        if (customOperation.hasErrors()) {
            return;
        }

        if (!signature.isVoid) {
            customOperation.addError(String.format("An @%s operation cannot have a return value. " +
                            "Use void as the return type.",
                            getSimpleName(types.EpilogExceptional)));
        }
    }

    private OperationArgument[] createOperationConstantArguments(List<ConstantOperandModel> operands, List<String> operandNames) {
        assert operands.size() == operandNames.size();
        OperationArgument[] arguments = new OperationArgument[operandNames.size()];
        for (int i = 0; i < operandNames.size(); i++) {
            ConstantOperandModel constantOperand = operands.get(i);
            String argumentName = operandNames.get(i);
            TypeMirror builderType;
            OperationArgument.Encoding encoding;
            // Special cases: local accessors are supplied by BytecodeLocal builder arguments.
            if (ElementUtils.typeEqualsAny(constantOperand.type(), types.LocalAccessor, types.MaterializedLocalAccessor)) {
                builderType = types.BytecodeLocal;
                encoding = OperationArgument.Encoding.LOCAL;
            } else if (ElementUtils.typeEquals(constantOperand.type(), types.LocalRangeAccessor)) {
                builderType = new ArrayCodeTypeMirror(types.BytecodeLocal);
                encoding = OperationArgument.Encoding.LOCAL_ARRAY;
            } else {
                builderType = constantOperand.type();
                encoding = OperationArgument.Encoding.OBJECT;
            }
            arguments[i] = new OperationArgument(builderType, constantOperand.type(), encoding,
                            sanitizeConstantArgumentName(argumentName),
                            constantOperand.doc());
        }
        return arguments;
    }

    private static String sanitizeConstantArgumentName(String name) {
        return name + "Value";
    }

    CustomOperationModel parseCustomShortCircuitOperation(AnnotationMirror mirror, String name, Operator operator, TypeElement booleanConverterTypeElement) {
        String javadoc = ElementUtils.getAnnotationValue(String.class, mirror, "javadoc");
        CustomOperationModel customOperation = parent.customShortCircuitOperation(name, javadoc, mirror);
        if (customOperation == null) {
            return null;
        }

        // All short-circuit operations have the same signature.
        OperationModel operation = customOperation.operation;
        operation.isVariadic = true;
        operation.isVoid = false;
        operation.setDynamicOperands(new DynamicOperandModel(List.of("value"), false, false));

        /*
         * NB: This creates a new operation for the boolean converter (or reuses one if such an
         * operation already exists).
         */
        InstructionModel booleanConverterInstruction = null;
        if (booleanConverterTypeElement != null) {
            booleanConverterInstruction = getOrCreateBooleanConverterInstruction(booleanConverterTypeElement, mirror);
        }
        InstructionModel instruction = parent.shortCircuitInstruction("sc." + name, new ShortCircuitInstructionModel(operator, booleanConverterInstruction));
        operation.setInstruction(instruction);

        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        instruction.addImmediate(ImmediateKind.BRANCH_PROFILE, "branch_profile");

        return customOperation;
    }

    private InstructionModel getOrCreateBooleanConverterInstruction(TypeElement typeElement, AnnotationMirror mirror) {
        CustomOperationModel result = parent.getCustomOperationForType(typeElement);
        if (result == null) {
            result = CustomOperationParser.forCodeGeneration(parent, types.Operation).parseCustomRegularOperation(mirror, typeElement, null);
        }
        if (result == null || result.hasErrors()) {
            parent.addError(mirror, ElementUtils.getAnnotationValue(mirror, "booleanConverter"),
                            "Encountered errors using %s as a boolean converter. These errors must be resolved before the DSL can proceed.", getSimpleName(typeElement));
            return null;
        }

        List<ExecutableElement> specializations = findSpecializations(typeElement);
        assert specializations.size() != 0;

        boolean returnsBoolean = true;
        for (ExecutableElement spec : specializations) {
            if (spec.getReturnType().getKind() != TypeKind.BOOLEAN) {
                returnsBoolean = false;
                break;
            }
        }

        Signature sig = result.operation.instruction.signature;
        if (!returnsBoolean || sig.dynamicOperandCount != 1 || sig.isVariadic) {
            parent.addError(mirror, ElementUtils.getAnnotationValue(mirror, "booleanConverter"),
                            "Specializations for boolean converter %s must only take one dynamic operand and return boolean.", getSimpleName(typeElement));
            return null;
        }

        return result.operation.instruction;
    }

    private String getCustomOperationName(TypeElement typeElement, String explicitName) {
        if (explicitName != null && !explicitName.isEmpty()) {
            return explicitName;
        }
        String name = typeElement.getSimpleName().toString();
        if (isProxy() && name.endsWith("Node")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * Validates the operation specification. Reports any errors on the {@link customOperation}.
     */
    private void validateCustomOperation(CustomOperationModel customOperation, TypeElement typeElement, AnnotationMirror mirror, String name) {
        if (name.contains("_")) {
            customOperation.addError("Operation class name cannot contain underscores.");
        }

        boolean isNode = isAssignable(typeElement.asType(), types.NodeInterface);
        if (isNode) {
            if (isProxy()) {
                AnnotationMirror generateCached = NodeParser.findGenerateAnnotation(typeElement.asType(), types.GenerateCached);
                if (generateCached != null && !ElementUtils.getAnnotationValue(Boolean.class, generateCached, "value")) {
                    customOperation.addError(
                                    "Class %s does not generate a cached node, so it cannot be used as an OperationProxy. Enable cached node generation using @GenerateCached(true) or delegate to this node using a regular Operation.",
                                    typeElement.getQualifiedName());
                    return;
                }
            }
        } else {
            // operation specification
            if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
                customOperation.addError("Operation class must be declared final. Inheritance in operation specifications is not supported.");
            }
            if (typeElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !typeElement.getModifiers().contains(Modifier.STATIC)) {
                customOperation.addError("Operation class must not be an inner class (non-static nested class). Declare the class as static.");
            }
            if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
                customOperation.addError("Operation class must not be declared private. Remove the private modifier to make it visible.");
            }
            if (!ElementUtils.isObject(typeElement.getSuperclass()) || !typeElement.getInterfaces().isEmpty()) {
                customOperation.addError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.");
            }

            // Ensure all non-private methods are static.
            for (Element el : typeElement.getEnclosedElements()) {
                if (el.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }

                if (!el.getModifiers().contains(Modifier.STATIC)) {
                    if (el.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) el).getParameters().size() == 0) {
                        continue; // ignore the default constructor
                    }
                    if (el.getKind() == ElementKind.METHOD && isSpecialization((ExecutableElement) el)) {
                        continue; // non-static specializations get a different message; see below
                    }
                    customOperation.addError(el, "Operation class must not contain non-static members.");
                }
            }
        }

        /**
         * The generated Node for this instruction does not subclass the original class defining the
         * specializations. Thus, each specialization should (1) be declared as static and (2) be
         * visible from the generated Node (i.e., public or package-private and in the same package
         * as the root node). Specialization visibility can be checked easily before we try to
         * generate the node.
         *
         * Similarly, the members (methods and fields) used in guard/cache expressions should (1)
         * not be instance fields/methods of the receiver and (2) be visible from the generated
         * Node. The first condition is "enforced" when we filter non-static members from the Node;
         * the {@link DSLExpressionResolver} should fail to resolve any instance member references.
         * The latter condition is checked during the regular resolution process.
         *
         */
        for (ExecutableElement specialization : findSpecializations(typeElement)) {
            if (!specialization.getModifiers().contains(Modifier.STATIC)) {
                customOperation.addError(specialization,
                                "Operation specializations must be static. Rewrite this specialization as a static method to resolve this error. " +
                                                "A static specialization cannot reference the \"this\" instance or any instance state; instead, use \"@%s Node\" to bind the receiver and define state using @%s parameters.",
                                getSimpleName(types.Bind), getSimpleName(types.Cached));
            }

            if (specialization.getModifiers().contains(Modifier.PRIVATE)) {
                customOperation.addError(specialization, "Operation specialization cannot be private.");
            } else if (!forProxyValidation && !ElementUtils.isVisible(parent.getTemplateType(), specialization)) {
                // We can only perform visibility checks during generation.
                parent.addError(mirror, null, "Operation %s's specialization \"%s\" must be visible from this node.", typeElement.getSimpleName(), specialization.getSimpleName());
            }
        }
    }

    private ConstantOperandsModel getConstantOperands(CustomOperationModel customOperation, TypeElement typeElement, AnnotationMirror mirror) {
        List<AnnotationMirror> constantOperands = ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.ConstantOperand);
        if (constantOperands.isEmpty()) {
            return ConstantOperandsModel.NONE;
        }

        if (ElementUtils.typeEqualsAny(mirror.getAnnotationType(), types.EpilogReturn, types.EpilogExceptional)) {
            customOperation.addError("An @%s operation cannot declare constant operands.", getSimpleName(mirror.getAnnotationType()));
            return null;
        }

        List<ConstantOperandModel> before = new ArrayList<>();
        List<ConstantOperandModel> after = new ArrayList<>();

        for (AnnotationMirror constantOperandMirror : constantOperands) {
            TypeMirror type = parseConstantOperandType(constantOperandMirror);
            String operandName = ElementUtils.getAnnotationValue(String.class, constantOperandMirror, "name");
            String javadoc = ElementUtils.getAnnotationValue(String.class, constantOperandMirror, "javadoc");
            Boolean specifyAtEnd = ElementUtils.getAnnotationValue(Boolean.class, constantOperandMirror, "specifyAtEnd", false);
            int dimensions = ElementUtils.getAnnotationValue(Integer.class, constantOperandMirror, "dimensions");
            ConstantOperandModel constantOperand = new ConstantOperandModel(type, operandName, javadoc, specifyAtEnd, dimensions, constantOperandMirror);

            if (ElementUtils.isAssignable(type, types.Node) && !ElementUtils.isAssignable(type, types.RootNode)) {
                // It is probably a bug if the user tries to define a constant Node. It will not be
                // adopted, and if the root node splits it will not be duplicated.
                customOperation.addError(constantOperandMirror, ElementUtils.getAnnotationValue(constantOperandMirror, "type"),
                                "Nodes cannot be used as constant operands.");
            } else if (ElementUtils.typeEquals(type, types.MaterializedLocalAccessor) && !parent.enableMaterializedLocalAccesses) {
                customOperation.addError(constantOperandMirror, ElementUtils.getAnnotationValue(constantOperandMirror, "type"),
                                "MaterializedLocalAccessor cannot be used because materialized local accesses are disabled. They can be enabled using the enableMaterializedLocalAccesses field of @GenerateBytecode.");
            }

            if (!isValidOperandName(operandName)) {
                customOperation.addError(constantOperandMirror, ElementUtils.getAnnotationValue(constantOperandMirror, "name"),
                                "Invalid constant operand name \"%s\". Operand name must be a valid Java identifier.", operandName);
            }

            if (dimensions != 0) {
                customOperation.addError(constantOperandMirror, ElementUtils.getAnnotationValue(constantOperandMirror, "dimensions"), "Constant operands with non-zero dimensions are not supported.");
            }

            if (specifyAtEnd == null || !specifyAtEnd) {
                before.add(constantOperand);
            } else {
                after.add(constantOperand);
            }
        }
        return new ConstantOperandsModel(before, after);
    }

    /**
     * Extracts the type of a constant operand from its annotation. Converts a raw type to a generic
     * type with wildcards.
     * <p>
     * Specializations may declare operands with generic types (e.g., {@code NodeFactory<?>}), but a
     * ConstantOperand annotation can only encode a raw type (e.g., {@code NodeFactory}). ecj treats
     * the latter as a subclass of the former, but javac does not, leading to problems with node
     * generation. Replacing the raw type with a wildcarded type prevents these errors.
     */
    private TypeMirror parseConstantOperandType(AnnotationMirror constantOperandMirror) {
        TypeMirror result = ElementUtils.getAnnotationValue(TypeMirror.class, constantOperandMirror, "type");
        return ElementUtils.rawTypeToWildcardedType(context, result);
    }

    private static boolean isValidOperandName(String name) {
        if (name.isEmpty()) {
            return true;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /*
     * Creates a placeholder Node from the type element that will be passed to FlatNodeGenFactory.
     * We remove any members that are not needed for code generation.
     */
    private CodeTypeElement createNodeForCustomInstruction(TypeElement typeElement) {
        boolean isNode = isAssignable(typeElement.asType(), types.NodeInterface);
        CodeTypeElement nodeType;
        if (isNode) {
            nodeType = cloneTypeHierarchy(typeElement, ct -> {
                // Remove annotations that will cause {@link FlatNodeGenFactory} to generate
                // unnecessary code. We programmatically add @NodeChildren later, so remove them
                // here.
                ct.getAnnotationMirrors().removeIf(
                                m -> typeEqualsAny(m.getAnnotationType(), types.NodeChild, types.NodeChildren, types.GenerateUncached, types.GenerateCached, types.GenerateInline,
                                                types.GenerateNodeFactory));
                // Remove all non-static or private elements, including all of the execute methods.
                ct.getEnclosedElements().removeIf(e -> !e.getModifiers().contains(Modifier.STATIC) || e.getModifiers().contains(Modifier.PRIVATE));
            });
        } else {
            nodeType = CodeTypeElement.cloneShallow(typeElement);
            nodeType.setSuperClass(types.Node);
        }
        nodeType.getAnnotationMirrors().removeIf(m -> typeEqualsAny(m.getAnnotationType(), types.ExpectErrorTypes));
        return nodeType;
    }

    /**
     * Adds annotations, methods, etc. to the {@link generatedNode} so that the desired code will be
     * generated by {@link FlatNodeGenFactory} during code generation.
     */
    private void addCustomInstructionNodeMembers(CustomOperationModel customOperation, CodeTypeElement generatedNode, Signature signature) {
        if (shouldGenerateUncached(customOperation)) {
            generatedNode.addAnnotationMirror(new CodeAnnotationMirror(types.GenerateUncached));
        }
        generatedNode.addAll(createExecuteMethods(customOperation, signature));

        /*
         * Add @NodeChildren to this node for each argument to the operation. These get used by
         * FlatNodeGenFactory to synthesize specialization logic. Since we directly execute the
         * children, we remove the fields afterwards.
         */
        CodeAnnotationMirror nodeChildrenAnnotation = new CodeAnnotationMirror(types.NodeChildren);
        nodeChildrenAnnotation.setElementValue("value",
                        new CodeAnnotationValue(createNodeChildAnnotations(customOperation, signature).stream().map(CodeAnnotationValue::new).collect(Collectors.toList())));
        generatedNode.addAnnotationMirror(nodeChildrenAnnotation);

        if (parent.enableSpecializationIntrospection) {
            generatedNode.addAnnotationMirror(new CodeAnnotationMirror(types.Introspectable));
        }
    }

    private boolean isShortCircuit() {
        return ElementUtils.typeEquals(annotationType, context.getTypes().ShortCircuitOperation);
    }

    private boolean isProxy() {
        return ElementUtils.typeEquals(annotationType, context.getTypes().OperationProxy_Proxyable);
    }

    private boolean isOperation() {
        return ElementUtils.typeEquals(annotationType, context.getTypes().Operation);
    }

    private List<AnnotationMirror> createNodeChildAnnotations(CustomOperationModel customOperation, Signature signature) {
        List<AnnotationMirror> result = new ArrayList<>();

        OperationModel operation = customOperation.operation;
        ConstantOperandsModel constantOperands = operation.constantOperands;
        for (int i = 0; i < operation.numConstantOperandsBefore(); i++) {
            result.add(createNodeChildAnnotation(operation.getConstantOperandBeforeName(i), constantOperands.before().get(i).type()));
        }
        for (int i = 0; i < signature.dynamicOperandCount; i++) {
            result.add(createNodeChildAnnotation("child" + i, signature.getGenericType(i)));
        }
        for (int i = 0; i < operation.numConstantOperandsAfter(); i++) {
            result.add(createNodeChildAnnotation(operation.getConstantOperandAfterName(i), constantOperands.after().get(i).type()));
        }

        return result;
    }

    private CodeAnnotationMirror createNodeChildAnnotation(String name, TypeMirror regularReturn, TypeMirror... unexpectedReturns) {
        CodeAnnotationMirror mir = new CodeAnnotationMirror(types.NodeChild);
        mir.setElementValue("value", new CodeAnnotationValue(name));
        mir.setElementValue("type", new CodeAnnotationValue(createNodeChildType(regularReturn, unexpectedReturns).asType()));
        return mir;
    }

    private CodeTypeElement createNodeChildType(TypeMirror regularReturn, TypeMirror... unexpectedReturns) {
        CodeTypeElement c = new CodeTypeElement(Set.of(PUBLIC, ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement(""), "C");
        c.setSuperClass(types.Node);

        c.add(createNodeChildExecute("execute", regularReturn, false));
        for (TypeMirror ty : unexpectedReturns) {
            c.add(createNodeChildExecute("execute" + firstLetterUpperCase(getSimpleName(ty)), ty, true));
        }

        return c;
    }

    private CodeExecutableElement createNodeChildExecute(String name, TypeMirror returnType, boolean withUnexpected) {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, ABSTRACT), returnType, name);
        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (withUnexpected) {
            ex.addThrownType(types.UnexpectedResultException);
        }

        return ex;
    }

    private List<CodeExecutableElement> createExecuteMethods(CustomOperationModel customOperation, Signature signature) {
        List<CodeExecutableElement> result = new ArrayList<>();

        result.add(createExecuteMethod(customOperation, signature, "executeObject", signature.returnType, false, false));

        if (parent.enableUncachedInterpreter) {
            result.add(createExecuteMethod(customOperation, signature, "executeUncached", signature.returnType, false, true));
        }

        return result;
    }

    private CodeExecutableElement createExecuteMethod(CustomOperationModel customOperation, Signature signature, String name, TypeMirror type, boolean withUnexpected, boolean uncached) {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, ABSTRACT), type, name);
        if (withUnexpected) {
            ex.addThrownType(types.UnexpectedResultException);
        }

        ex.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (uncached) {
            OperationModel operation = customOperation.operation;
            ConstantOperandsModel constantOperands = operation.constantOperands;
            for (int i = 0; i < operation.numConstantOperandsBefore(); i++) {
                ex.addParameter(new CodeVariableElement(constantOperands.before().get(i).type(), operation.getConstantOperandBeforeName(i)));
            }
            for (int i = 0; i < signature.dynamicOperandCount; i++) {
                ex.addParameter(new CodeVariableElement(signature.getGenericType(i), "child" + i + "Value"));
            }
            for (int i = 0; i < operation.numConstantOperandsAfter(); i++) {
                ex.addParameter(new CodeVariableElement(constantOperands.after().get(i).type(), operation.getConstantOperandAfterName(i)));
            }

        }

        return ex;
    }

    /**
     * Creates and registers a new instruction for a custom operation.
     *
     * This method calls into the Truffle DSL's regular {@link NodeParser Node parsing} logic to
     * generate a {@link NodeData node model} that will later be used by {@link FlatNodeGenFactory
     * code generation} to generate code for the instruction.
     */
    private InstructionModel createCustomInstruction(CustomOperationModel customOperation, CodeTypeElement generatedNode, Signature signature,
                    String operationName) {
        String instructionName = "c." + operationName;
        InstructionModel instr;
        if (customOperation.isEpilogExceptional()) {
            // We don't emit bytecode for this operation. Allocate an InstructionModel but don't
            // register it as an instruction.
            instr = new InstructionModel(InstructionKind.CUSTOM, instructionName, signature, null);
        } else {
            instr = parent.instruction(InstructionKind.CUSTOM, instructionName, signature);
        }
        instr.nodeType = generatedNode;
        instr.nodeData = parseGeneratedNode(customOperation, generatedNode, signature);

        if (customOperation.operation.variadicReturn) {
            instr.nonNull = true;
        }

        OperationModel operation = customOperation.operation;
        for (int i = 0; i < operation.numConstantOperandsBefore(); i++) {
            instr.addImmediate(ImmediateKind.CONSTANT, operation.getConstantOperandBeforeName(i));
        }

        for (int i = 0; i < operation.numConstantOperandsAfter(); i++) {
            instr.addImmediate(ImmediateKind.CONSTANT, operation.getConstantOperandAfterName(i));
        }

        if (!instr.canUseNodeSingleton()) {
            instr.addImmediate(ImmediateKind.NODE_PROFILE, "node");
        }

        return instr;
    }

    /**
     * Use the {@link NodeParser} to parse the generated node specification.
     */
    private NodeData parseGeneratedNode(CustomOperationModel customOperation, CodeTypeElement generatedNode, Signature signature) {
        if (forProxyValidation) {
            /*
             * A proxied node, by virtue of being a {@link Node}, will already be parsed and
             * validated during regular DSL processing. Re-parsing it here would lead to duplicate
             * error messages on the node itself.
             *
             * NB: We cannot check whether a Proxyable node's cache/guard expressions are visible
             * since it is not associated with a bytecode node during validation. This extra check
             * will happen when a bytecode node using this proxied node is generated.
             */
            return null;
        }

        // Add members to the generated node so that the proper node specification is parsed.
        addCustomInstructionNodeMembers(customOperation, generatedNode, signature);

        NodeData result;
        try {
            NodeParser parser = NodeParser.createOperationParser(parent.getTemplateType());
            result = parser.parse(generatedNode, false);
        } catch (Throwable ex) {
            StringWriter wr = new StringWriter();
            ex.printStackTrace(new PrintWriter(wr));
            customOperation.addError("Error generating instruction for Operation node %s: \n%s", parent.getName(), wr.toString());
            return null;
        }

        if (result == null) {
            customOperation.addError("Error generating instruction for Operation node %s. This is likely a bug in the Bytecode DSL.", parent.getName());
            return null;
        }

        if (result.getTypeSystem() == null) {
            customOperation.addError("Error parsing type system for operation. Fix problems in the referenced type system class first.");
            return null;
        }
        checkUnnecessaryForceCached(customOperation, result);

        TypeSystemData parentTypeSystem = parent.typeSystem;
        if (parentTypeSystem != null && !parentTypeSystem.isDefault()) {
            if (result.getTypeSystem().isDefault()) {
                result.setTypeSystem(parentTypeSystem);
            } else {
                if (isOperation() && ElementUtils.typeEquals(result.getTypeSystem().getTemplateType().asType(), parent.typeSystem.getTemplateType().asType())) {
                    customOperation.addSuppressableWarning(TruffleSuppressedWarnings.UNUSED,
                                    "Type system referenced by this operation is the same as the type system referenced by the parent bytecode root node. Remove the operation type system reference to resolve this warning.");
                }
            }
        }

        result.redirectMessagesOnGeneratedElements(parent);

        return result;
    }

    /**
     * Parses each specialization to a signature. Returns the list of signatures, or null if any of
     * them had errors.
     */
    public static List<SpecializationSignature> parseSignatures(List<ExecutableElement> specializations, MessageContainer customOperation, ConstantOperandsModel constantOperands) {
        List<SpecializationSignature> signatures = new ArrayList<>(specializations.size());
        SpecializationSignatureParser parser = new SpecializationSignatureParser(ProcessorContext.getInstance());
        for (ExecutableElement specialization : specializations) {
            signatures.add(parser.parse(specialization, customOperation, constantOperands));
        }
        return signatures;
    }

    static TruffleTypes types() {
        return ProcessorContext.types();
    }

    private List<ExecutableElement> findSpecializations(TypeElement te) {
        if (ElementUtils.isObject(te.asType())) {
            return new ArrayList<>();
        }

        List<ExecutableElement> result = findSpecializations(getTypeElement((DeclaredType) te.getSuperclass()));

        for (ExecutableElement ex : ElementFilter.methodsIn(te.getEnclosedElements())) {
            if (isSpecialization(ex)) {
                result.add(ex);
            }
        }

        return result;
    }

    private boolean isSpecialization(ExecutableElement ex) {
        return ElementUtils.findAnnotationMirror(ex, types.Specialization) != null || ElementUtils.findAnnotationMirror(ex, types.Fallback) != null;
    }

    private boolean shouldGenerateUncached(CustomOperationModel customOperation) {
        if (forProxyValidation) {
            return uncachedProxyValidation;
        } else {
            return parent.enableUncachedInterpreter && !customOperation.forcesCached();
        }
    }

    private void checkUnnecessaryForceCached(CustomOperationModel customOperation, NodeData result) {
        if (!parent.enableUncachedInterpreter || !customOperation.forcesCached()) {
            // Operation does not set forceCached.
            return;
        }
        if (!result.isUncachable()) {
            // Operation is not cachable, so forceCached is necessary.
            return;
        }
        if (isProxy() && !proxyableAllowsUncached(customOperation.getTemplateTypeAnnotation())) {
            // Operation is cachable, but Proxyable disallows uncached, so forceCached is necessary.
            return;
        }
        // Otherwise, forceCached is unnecessary.
        AnnotationMirror mirror = customOperation.getTemplateTypeAnnotation();
        AnnotationValue forceCached = ElementUtils.getAnnotationValue(mirror, "forceCached");
        customOperation.getModelForMessages().addSuppressableWarning(TruffleSuppressedWarnings.FORCE_CACHED, mirror, forceCached,
                        "This operation supports uncached execution, so forcing cached is not necessary. Remove the forceCached attribute to resolve this warning.");
    }

    private boolean proxyableAllowsUncached(AnnotationMirror operationProxyMirror) {
        if (!ElementUtils.typeEquals(operationProxyMirror.getAnnotationType(), types.OperationProxy)) {
            throw new AssertionError();
        }
        AnnotationValue proxiedTypeValue = ElementUtils.getAnnotationValue(operationProxyMirror, "value");
        TypeMirror proxiedType = BytecodeDSLParser.getTypeMirror(context, proxiedTypeValue);
        TypeElement proxiedElement = (TypeElement) ((DeclaredType) proxiedType).asElement();
        AnnotationMirror proxyableMirror = ElementUtils.findAnnotationMirror(proxiedElement, types.OperationProxy_Proxyable);
        return ElementUtils.getAnnotationValue(Boolean.class, proxyableMirror, "allowUncached");
    }

    @Override
    public DeclaredType getAnnotationType() {
        return annotationType;
    }

    private CodeTypeElement cloneTypeHierarchy(TypeElement element, Consumer<CodeTypeElement> mapper) {
        CodeTypeElement result = CodeTypeElement.cloneShallow(element);
        if (!ElementUtils.isObject(element.getSuperclass())) {
            result.setSuperClass(cloneTypeHierarchy(context.getTypeElement((DeclaredType) element.getSuperclass()), mapper).asType());
        }

        mapper.accept(result);

        return result;
    }

}
