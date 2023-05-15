/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations.parser;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getAnnotationValue;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel;
import com.oracle.truffle.dsl.processor.operations.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.operations.model.OperationsModel;
import com.oracle.truffle.dsl.processor.operations.model.OperationsModelList;
import com.oracle.truffle.dsl.processor.operations.model.OptimizationDecisionsModel;
import com.oracle.truffle.dsl.processor.operations.model.OptimizationDecisionsModel.CommonInstructionDecision;
import com.oracle.truffle.dsl.processor.operations.model.OptimizationDecisionsModel.QuickenDecision;
import com.oracle.truffle.dsl.processor.operations.model.OptimizationDecisionsModel.SuperInstructionDecision;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.TypeSystemParser;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;

public class OperationsParser extends AbstractParser<OperationsModelList> {

    private static final EnumSet<TypeKind> BOXABLE_TYPE_KINDS = EnumSet.of(TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.INT, TypeKind.FLOAT, TypeKind.LONG, TypeKind.DOUBLE);

    @SuppressWarnings("unchecked")
    @Override
    protected OperationsModelList parse(Element element, List<AnnotationMirror> mirror) {
        TypeElement typeElement = (TypeElement) element;

        // In regular usage, a language annotates a RootNode with {@link GenerateOperations} and the
        // DSL generates a single bytecode interpreter. However, for internal testing purposes, we
        // may use {@link GenerateOperationsTestVariants} to generate multiple interpreters. In the
        // latter case, we need to parse multiple configurations and ensure they agree.

        AnnotationMirror generateOperationsTestVariantsMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), types.GenerateOperationsTestVariants);
        List<OperationsModel> models;
        AnnotationMirror topLevelAnnotationMirror;
        if (generateOperationsTestVariantsMirror != null) {
            topLevelAnnotationMirror = generateOperationsTestVariantsMirror;
            models = parseGenerateOperationsTestVariants(typeElement, generateOperationsTestVariantsMirror);
        } else {
            AnnotationMirror generateOperationsMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), types.GenerateOperations);
            assert generateOperationsMirror != null;
            topLevelAnnotationMirror = generateOperationsMirror;
            models = List.of(new OperationsModel(context, typeElement, generateOperationsMirror, "Gen"));
        }

        OperationsModelList modelList = new OperationsModelList(context, typeElement, topLevelAnnotationMirror, models);

        for (OperationsModel model : models) {
            parseOperationsModel(typeElement, model, model.getTemplateTypeAnnotation());
            if (model.hasErrors()) {
                // we only need one copy of the error messages.
                break;
            }
        }

        return modelList;
    }

    private List<OperationsModel> parseGenerateOperationsTestVariants(TypeElement typeElement, AnnotationMirror mirror) {
        List<AnnotationMirror> variants = ElementUtils.getAnnotationValueList(AnnotationMirror.class, mirror, "value");

        boolean first = true;
        Set<String> names = new HashSet<>();
        TypeMirror languageClass = null;
        boolean enableYield = false;

        List<OperationsModel> result = new ArrayList<>();

        for (AnnotationMirror variant : variants) {
            AnnotationValue nameValue = ElementUtils.getAnnotationValue(variant, "name");
            String name = ElementUtils.resolveAnnotationValue(String.class, nameValue);

            AnnotationValue generateOperationsMirrorValue = ElementUtils.getAnnotationValue(variant, "configuration");
            AnnotationMirror generateOperationsMirror = ElementUtils.resolveAnnotationValue(AnnotationMirror.class, generateOperationsMirrorValue);

            OperationsModel model = new OperationsModel(context, typeElement, generateOperationsMirror, name);

            if (!first && names.contains(name)) {
                model.addError(variant, nameValue, "A variant with name \"%s\" already exists. Each variant must have a unique name.", name);
            }
            names.add(name);

            AnnotationValue variantLanguageClassValue = ElementUtils.getAnnotationValue(generateOperationsMirror, "languageClass");
            TypeMirror variantLanguageClass = ElementUtils.resolveAnnotationValue(TypeMirror.class, variantLanguageClassValue);
            if (first) {
                languageClass = variantLanguageClass;
            } else if (!languageClass.equals(variantLanguageClass)) {
                model.addError(generateOperationsMirror, variantLanguageClassValue, "Incompatible variant: all variants must use the same language class.");
            }

            AnnotationValue variantEnableYieldValue = ElementUtils.getAnnotationValue(generateOperationsMirror, "enableYield");
            boolean variantEnableYield = ElementUtils.resolveAnnotationValue(Boolean.class,
                            variantEnableYieldValue);
            if (first) {
                enableYield = variantEnableYield;
            } else if (variantEnableYield != enableYield) {
                model.addError(generateOperationsMirror, variantEnableYieldValue, "Incompatible variant: all variants must have the same value for enableYield.");
            }

            first = false;
            result.add(model);
        }

        return result;
    }

    private void parseOperationsModel(TypeElement typeElement, OperationsModel model, AnnotationMirror generateOperationsMirror) {
        model.languageClass = (DeclaredType) ElementUtils.getAnnotationValue(generateOperationsMirror, "languageClass").getValue();
        model.enableYield = (boolean) ElementUtils.getAnnotationValue(generateOperationsMirror, "enableYield", true).getValue();
        model.enableSerialization = (boolean) ElementUtils.getAnnotationValue(generateOperationsMirror, "enableSerialization", true).getValue();
        model.enableBaselineInterpreter = (boolean) ElementUtils.getAnnotationValue(generateOperationsMirror, "enableBaselineInterpreter", true).getValue();
        model.allowUnsafe = (boolean) ElementUtils.getAnnotationValue(generateOperationsMirror, "allowUnsafe", true).getValue();

        model.addDefault();

        // check basic declaration properties
        if (!typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            model.addError(typeElement, "Operations class must be declared abstract.");
        }

        if (!ElementUtils.isAssignable(typeElement.asType(), types.RootNode)) {
            model.addError(typeElement, "Operations class must directly or indirectly subclass %s.", getSimpleName(types.RootNode));
        }

        if (!ElementUtils.isAssignable(typeElement.asType(), types.OperationRootNode)) {
            model.addError(typeElement, "Operations class must directly or indirectly implement %s.", getSimpleName(types.OperationRootNode));
        }

        // Find the appropriate constructor.
        List<ExecutableElement> viableConstructors = ElementFilter.constructorsIn(typeElement.getEnclosedElements()).stream().filter(ctor -> {
            if (!(ctor.getModifiers().contains(Modifier.PUBLIC) || ctor.getModifiers().contains(Modifier.PROTECTED))) {
                // not visible
                return false;
            }
            List<? extends VariableElement> params = ctor.getParameters();
            if (params.size() != 2) {
                // not the right number of params
                return false;
            }
            if (!ElementUtils.isAssignable(params.get(0).asType(), types.TruffleLanguage)) {
                // wrong first parameter type
                return false;
            }
            TypeMirror secondParameterType = ctor.getParameters().get(1).asType();
            boolean isFrameDescriptor = ElementUtils.isAssignable(secondParameterType, types.FrameDescriptor);
            boolean isFrameDescriptorBuilder = ElementUtils.isAssignable(secondParameterType, types.FrameDescriptor_Builder);
            // second parameter type should be FrameDescriptor or FrameDescriptor.Builder
            return isFrameDescriptor || isFrameDescriptorBuilder;
        }).collect(Collectors.toList());

        if (viableConstructors.isEmpty()) {
            model.addError(typeElement, "Operations class should declare a constructor that has signature (%s, %s) or (%s, %s.%s). The constructor should be visible to subclasses.",
                            getSimpleName(types.TruffleLanguage),
                            getSimpleName(types.FrameDescriptor),
                            getSimpleName(types.TruffleLanguage),
                            getSimpleName(types.FrameDescriptor),
                            getSimpleName(types.FrameDescriptor_Builder));
            return;
        }

        Map<String, List<ExecutableElement>> constructorsByFDType = viableConstructors.stream().collect(Collectors.groupingBy(ctor -> {
            TypeMirror secondParameterType = ctor.getParameters().get(1).asType();
            if (ElementUtils.isAssignable(secondParameterType, types.FrameDescriptor)) {
                return TruffleTypes.FrameDescriptor_Name;
            } else {
                return TruffleTypes.FrameDescriptor_Builder_Name;
            }
        }));

        // Prioritize a constructor that takes a FrameDescriptor.Builder.
        if (constructorsByFDType.containsKey(TruffleTypes.FrameDescriptor_Builder_Name)) {
            List<ExecutableElement> ctors = constructorsByFDType.get(TruffleTypes.FrameDescriptor_Builder_Name);
            assert ctors.size() == 1;
            model.fdBuilderConstructor = ctors.get(0);
        } else {
            List<ExecutableElement> ctors = constructorsByFDType.get(TruffleTypes.FrameDescriptor_Name);
            assert ctors.size() == 1;
            model.fdConstructor = ctors.get(0);
        }

        // Detect method implementations that will be overridden by the generated class.
        List<ExecutableElement> overrides = List.of(
                        ElementUtils.findMethod(types.RootNode, "execute"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "executeOSR"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "getOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "setOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "storeParentFrameInArguments"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "restoreParentFrameFromArguments"),
                        ElementUtils.findMethod(types.OperationRootNode, "setBaselineInterpreterThreshold"),
                        ElementUtils.findMethod(types.OperationRootNode, "materializeInstrumentTree"),
                        ElementUtils.findMethod(types.OperationRootNode, "getSourceSectionAtBci"));

        for (ExecutableElement override : overrides) {
            ExecutableElement declared = ElementUtils.findMethod(typeElement, override.getSimpleName().toString());
            if (declared == null) {
                continue;
            }

            String executeSuffix = override.getSimpleName().toString().equals("execute") ? " Override executeProlog and executeEpilog to perform actions before and after execution." : "";

            if (declared.getModifiers().contains(Modifier.FINAL)) {
                model.addError(declared,
                                "This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed." +
                                                executeSuffix);
            } else {
                model.addWarning(declared, "This method is overridden by the generated Operations class, so this definition is unreachable and can be removed." + executeSuffix);
            }
        }

        if (model.hasErrors()) {
            return;
        }

        // TODO: metadata

        // find and bind type system
        AnnotationMirror typeSystemRefMirror = ElementUtils.findAnnotationMirror(typeElement, types.TypeSystemReference);
        if (typeSystemRefMirror != null) {
            TypeMirror typeSystemType = getAnnotationValue(TypeMirror.class, typeSystemRefMirror, "value");

            TypeSystemData typeSystem = null;
            if (typeSystemType instanceof DeclaredType) {
                typeSystem = context.parseIfAbsent((TypeElement) ((DeclaredType) typeSystemType).asElement(), TypeSystemParser.class, (e) -> {
                    TypeSystemParser parser = new TypeSystemParser();
                    return parser.parse(e, false);
                });
            }
            if (typeSystem == null) {
                model.addError("The used type system '%s' is invalid. Fix errors in the type system first.", getQualifiedName(typeSystemType));
                return;
            }

            model.typeSystem = typeSystem;
        } else {
            model.typeSystem = new TypeSystemData(context, typeElement, null, true);
        }

        // find and bind boxing elimination types
        Set<TypeMirror> beTypes = new HashSet<>();

        List<AnnotationValue> boxingEliminatedTypes = (List<AnnotationValue>) ElementUtils.getAnnotationValue(generateOperationsMirror, "boxingEliminationTypes").getValue();
        for (AnnotationValue value : boxingEliminatedTypes) {

            TypeMirror mir = getTypeMirror(value);

            if (BOXABLE_TYPE_KINDS.contains(mir.getKind())) {
                beTypes.add(mir);
            } else {
                model.addError("Cannot perform boxing elimination on %s. Remove this type from the boxing eliminated types list. Only primitive types boolean, byte, int, float, long, and double are supported.",
                                mir);
            }
        }
        // TODO: remove this line when we actually support BE.
        beTypes.clear();

        model.boxingEliminatedTypes = beTypes;

        // optimization decisions & tracing
        AnnotationValue decisionsFileValue = ElementUtils.getAnnotationValue(generateOperationsMirror, "decisionsFile", false);
        AnnotationValue decisionsOverrideFilesValue = ElementUtils.getAnnotationValue(generateOperationsMirror, "decisionsOverrideFiles", false);
        String[] decisionsOverrideFilesPath = new String[0];

        if (decisionsFileValue != null) {
            model.decisionsFilePath = resolveElementRelativePath(typeElement, (String) decisionsFileValue.getValue());

            if (TruffleProcessorOptions.operationsEnableTracing(processingEnv)) {
                model.enableTracing = true;
            } else if ((boolean) ElementUtils.getAnnotationValue(generateOperationsMirror, "forceTracing", true).getValue()) {
                model.addWarning("Operation DSL execution tracing is forced on. Use this only during development.");
                model.enableTracing = true;
            }
        }

        if (decisionsOverrideFilesValue != null) {
            decisionsOverrideFilesPath = ((List<AnnotationValue>) decisionsOverrideFilesValue.getValue()).stream().map(x -> (String) x.getValue()).toArray(String[]::new);
        }

        model.enableOptimizations = (decisionsFileValue != null || decisionsOverrideFilesValue != null) && !model.enableTracing;

        // error sync
        if (model.hasErrors()) {
            return;
        }

        // custom operations
        for (TypeElement te : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            AnnotationMirror op = ElementUtils.findAnnotationMirror(te, types.Operation);
            if (op == null) {
                continue;
            }

            CustomOperationParser.forOperation(model, op).parse(te);
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.OperationProxy)) {
            TypeMirror proxiedType = getTypeMirror(ElementUtils.getAnnotationValue(mir, "value"));

            if (proxiedType.getKind() != TypeKind.DECLARED) {
                model.addError("Could not proxy operation: the proxied type must be a class, not %s.", proxiedType);
                continue;
            }

            TypeElement te = (TypeElement) ((DeclaredType) proxiedType).asElement();

            CustomOperationParser.forOperationProxy(model, mir).parse(te);
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.ShortCircuitOperation)) {
            TypeMirror proxiedType = getTypeMirror(ElementUtils.getAnnotationValue(mir, "booleanConverter"));

            if (proxiedType.getKind() != TypeKind.DECLARED) {
                model.addError("Could not proxy operation: the proxied type must be a class, not %s", proxiedType);
                continue;
            }

            TypeElement te = (TypeElement) ((DeclaredType) proxiedType).asElement();

            CustomOperationParser.forShortCircuitOperation(model, mir).parse(te);
        }

        // error sync
        if (model.hasErrors()) {
            return;
        }

        // apply optimization decisions
        if (model.enableOptimizations) {
            model.optimizationDecisions = parseDecisions(model, model.decisionsFilePath, decisionsOverrideFilesPath);

            for (SuperInstructionDecision decision : model.optimizationDecisions.superInstructionDecisions) {
                String resultingInstructionName = "si." + String.join(".", decision.instructions);
                InstructionModel instr = model.instruction(InstructionKind.SUPERINSTRUCTION, resultingInstructionName);
                instr.subInstructions = new ArrayList<>();

                for (String instrName : decision.instructions) {
                    InstructionModel subInstruction = model.getInstructionByName(instrName);
                    if (subInstruction == null) {
                        model.addError("Error reading optimization decisions: Super-instruction '%s' defines a sub-instruction '%s' which does not exist.", resultingInstructionName, instrName);
                    } else if (subInstruction.kind == InstructionKind.SUPERINSTRUCTION) {
                        model.addError("Error reading optimization decisions: Super-instruction '%s' cannot contain another super-instruction '%s'.", resultingInstructionName, instrName);
                    }
                    instr.subInstructions.add(subInstruction);
                }
            }
        }

        // serialization fields
        if (model.enableSerialization) {
            List<VariableElement> serializedFields = new ArrayList<>();
            TypeElement type = model.getTemplateType();
            while (type != null) {
                if (ElementUtils.typeEquals(types.RootNode, type.asType())) {
                    break;
                }
                for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.TRANSIENT) || field.getModifiers().contains(Modifier.FINAL)) {
                        continue;
                    }

                    boolean inTemplateType = model.getTemplateType() == type;
                    boolean visible = inTemplateType ? !field.getModifiers().contains(Modifier.PRIVATE) : ElementUtils.isVisible(model.getTemplateType(), field);

                    if (!visible) {
                        model.addError(inTemplateType ? field : null, errorPrefix() +
                                        "The field '%s' is not accessible to generated code. The field must be accessible for serialization. Add the transient modifier to the field or make it accessible to resolve this problem.",
                                        ElementUtils.getReadableReference(model.getTemplateType(), field));
                        continue;
                    }

                    serializedFields.add(field);
                }

                type = ElementUtils.castTypeElement(type.getSuperclass());
            }

            model.serializedFields = serializedFields;
        }

        return;
    }

    private String errorPrefix() {
        return String.format("Failed to generate code for @%s: ", getSimpleName(types.GenerateOperations));
    }

    private String resolveElementRelativePath(Element element, String relativePath) {
        File filePath = CompilerFactory.getCompiler(element).getEnclosingSourceFile(processingEnv, element);
        return Path.of(filePath.getPath()).getParent().resolve(relativePath).toAbsolutePath().toString();
    }

    private static OptimizationDecisionsModel parseDecisions(OperationsModel model, String decisionsFile, String[] decisionOverrideFiles) {
        OptimizationDecisionsModel result = new OptimizationDecisionsModel();
        result.decisionsFilePath = decisionsFile;
        result.decisionsOverrideFilePaths = decisionOverrideFiles;

        if (decisionsFile != null) {
            parseDecisionsFile(model, result, decisionsFile, true);
        }

        return result;
    }

    private static void parseDecisionsFile(OperationsModel model, OptimizationDecisionsModel result, String filePath, boolean isMain) {
        try {
            // this parsing is very fragile, and error reporting is very useless
            FileInputStream fi = new FileInputStream(filePath);
            JSONArray o = new JSONArray(new JSONTokener(fi));
            for (int i = 0; i < o.length(); i++) {
                if (o.get(i) instanceof String) {
                    // strings are treated as comments
                    continue;
                } else {
                    parseDecision(model, result, o.getJSONObject(i));
                }
            }
        } catch (FileNotFoundException ex) {
            if (isMain) {
                model.addError("Decisions file '%s' not found. Build & run with tracing enabled to generate it.", filePath);
            } else {
                model.addError("Decisions file '%s' not found. Create it, or remove it from decisionOverrideFiles to resolve this error.", filePath);
            }
        } catch (JSONException ex) {
            model.addError("Decisions file '%s' is invalid: %s", filePath, ex);
        }
    }

    private static void parseDecision(OperationsModel model, OptimizationDecisionsModel result, JSONObject decision) {
        switch (decision.getString("type")) {
            case "SuperInstruction": {
                SuperInstructionDecision m = new SuperInstructionDecision();
                m.id = decision.optString("id");
                m.instructions = jsonGetStringArray(decision, "instructions");
                result.superInstructionDecisions.add(m);
                break;
            }
            case "CommonInstruction": {
                CommonInstructionDecision m = new CommonInstructionDecision();
                m.id = decision.optString("id");
                result.commonInstructionDecisions.add(m);
                break;
            }
            case "Quicken": {
                QuickenDecision m = new QuickenDecision();
                m.id = decision.optString("id");
                m.operation = decision.getString("operation");
                m.specializations = jsonGetStringArray(decision, "specializations");
                result.quickenDecisions.add(m);
                break;
            }
            default:
                model.addError("Unknown optimization decision type: '%s'.", decision.getString("type"));
                break;
        }
    }

    private static String[] jsonGetStringArray(JSONObject obj, String key) {
        return ((List<?>) obj.getJSONArray(key).toList()).toArray(String[]::new);
    }

    private TypeMirror getTypeMirror(AnnotationValue value) throws AssertionError {
        if (value.getValue() instanceof Class<?>) {
            return context.getType((Class<?>) value.getValue());
        } else if (value.getValue() instanceof TypeMirror) {
            return (TypeMirror) value.getValue();
        } else {
            throw new AssertionError();
        }
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

    @Override
    public DeclaredType getRepeatAnnotationType() {
        // This annotation is not technically a Repeatable container for @GenerateOperations, but it
        // is a convenient way to get the processor framework to forward a node with this annotation
        // to the OperationsParser.
        return types.GenerateOperationsTestVariants;
    }
}
