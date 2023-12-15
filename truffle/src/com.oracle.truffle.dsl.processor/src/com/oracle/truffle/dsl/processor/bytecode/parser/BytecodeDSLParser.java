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
package com.oracle.truffle.dsl.processor.bytecode.parser;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONException;
import org.graalvm.shadowed.org.json.JSONObject;
import org.graalvm.shadowed.org.json.JSONTokener;

import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModels;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.Signature;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel.CommonInstructionDecision;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel.QuickenDecision;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel.SuperInstructionDecision;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;
import com.oracle.truffle.dsl.processor.parser.TypeSystemParser;

public class BytecodeDSLParser extends AbstractParser<BytecodeDSLModels> {

    private static final EnumSet<TypeKind> BOXABLE_TYPE_KINDS = EnumSet.of(TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.INT, TypeKind.FLOAT, TypeKind.LONG, TypeKind.DOUBLE);

    @SuppressWarnings("unchecked")
    @Override
    protected BytecodeDSLModels parse(Element element, List<AnnotationMirror> mirror) {
        TypeElement typeElement = (TypeElement) element;
        /*
         * In regular usage, a language annotates a RootNode with {@link GenerateBytecode} and the
         * DSL generates a single bytecode interpreter. However, for internal testing purposes, we
         * may use {@link GenerateBytecodeTestVariants} to generate multiple interpreters. In the
         * latter case, we need to parse multiple configurations and ensure they agree.
         */
        AnnotationMirror generateBytecodeTestVariantsMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), types.GenerateBytecodeTestVariants);
        List<BytecodeDSLModel> models;
        AnnotationMirror topLevelAnnotationMirror;
        if (generateBytecodeTestVariantsMirror != null) {
            topLevelAnnotationMirror = generateBytecodeTestVariantsMirror;
            models = parseGenerateBytecodeTestVariants(typeElement, generateBytecodeTestVariantsMirror);
        } else {
            AnnotationMirror generateBytecodeMirror = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), types.GenerateBytecode);
            assert generateBytecodeMirror != null;
            topLevelAnnotationMirror = generateBytecodeMirror;
            models = List.of(new BytecodeDSLModel(context, typeElement, generateBytecodeMirror, "Gen"));
        }

        BytecodeDSLModels modelList = new BytecodeDSLModels(context, typeElement, topLevelAnnotationMirror, models);
        if (modelList.hasErrors()) {
            return modelList;
        }

        for (BytecodeDSLModel model : models) {
            parseBytecodeDSLModel(typeElement, model, model.getTemplateTypeAnnotation());
            if (model.hasErrors()) {
                // we only need one copy of the error messages.
                break;
            }

        }
        return modelList;

    }

    private List<BytecodeDSLModel> parseGenerateBytecodeTestVariants(TypeElement typeElement, AnnotationMirror mirror) {
        List<AnnotationMirror> variants = ElementUtils.getAnnotationValueList(AnnotationMirror.class, mirror, "value");

        boolean first = true;
        Set<String> suffixes = new HashSet<>();
        TypeMirror languageClass = null;
        boolean enableYield = false;

        List<BytecodeDSLModel> result = new ArrayList<>();

        for (AnnotationMirror variant : variants) {
            AnnotationValue suffixValue = ElementUtils.getAnnotationValue(variant, "suffix");
            String suffix = ElementUtils.resolveAnnotationValue(String.class, suffixValue);

            AnnotationValue generateBytecodeMirrorValue = ElementUtils.getAnnotationValue(variant, "configuration");
            AnnotationMirror generateBytecodeMirror = ElementUtils.resolveAnnotationValue(AnnotationMirror.class, generateBytecodeMirrorValue);

            BytecodeDSLModel model = new BytecodeDSLModel(context, typeElement, generateBytecodeMirror, suffix);

            if (!first && suffixes.contains(suffix)) {
                model.addError(variant, suffixValue, "A variant with suffix \"%s\" already exists. Each variant must have a unique suffix.", suffix);
            }
            suffixes.add(suffix);

            AnnotationValue variantLanguageClassValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "languageClass");
            TypeMirror variantLanguageClass = ElementUtils.resolveAnnotationValue(TypeMirror.class, variantLanguageClassValue);
            if (first) {
                languageClass = variantLanguageClass;
            } else if (!languageClass.equals(variantLanguageClass)) {
                model.addError(generateBytecodeMirror, variantLanguageClassValue, "Incompatible variant: all variants must use the same language class.");
            }

            AnnotationValue variantEnableYieldValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "enableYield");
            boolean variantEnableYield = ElementUtils.resolveAnnotationValue(Boolean.class,
                            variantEnableYieldValue);
            if (first) {
                enableYield = variantEnableYield;
            } else if (variantEnableYield != enableYield) {
                model.addError(generateBytecodeMirror, variantEnableYieldValue, "Incompatible variant: all variants must have the same value for enableYield.");
            }

            first = false;
            result.add(model);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void parseBytecodeDSLModel(TypeElement typeElement, BytecodeDSLModel model, AnnotationMirror generateBytecodeMirror) {
        model.languageClass = (DeclaredType) ElementUtils.getAnnotationValue(generateBytecodeMirror, "languageClass").getValue();
        model.storeBciInFrame = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableUncachedInterpreter");
        model.enableUncachedInterpreter = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableUncachedInterpreter");
        model.enableSerialization = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableSerialization");
        model.allowUnsafe = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "allowUnsafe");
        model.enableYield = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableYield");
        model.storeBciInFrame = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "storeBciInFrame");
        model.enableQuickening = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableQuickening");
        model.specializationDebugListener = types.BytecodeDebugListener == null ? false : ElementUtils.isAssignable(typeElement.asType(), types.BytecodeDebugListener);
        model.enableSpecializationIntrospection = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableSpecializationIntrospection");

        model.addDefault();

        // check basic declaration properties
        if (!typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            model.addError(typeElement, "Bytecode DSL class must be declared abstract.");
        }

        if (!ElementUtils.isAssignable(typeElement.asType(), types.RootNode)) {
            model.addError(typeElement, "Bytecode DSL class must directly or indirectly subclass %s.", getSimpleName(types.RootNode));
        }

        if (!ElementUtils.isAssignable(typeElement.asType(), types.BytecodeRootNode)) {
            model.addError(typeElement, "Bytecode DSL class must directly or indirectly implement %s.", getSimpleName(types.BytecodeRootNode));
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
            model.addError(typeElement, "Bytecode DSL class should declare a constructor that has signature (%s, %s) or (%s, %s.%s). The constructor should be visible to subclasses.",
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

        // Extract hook implementations.
        model.executeProlog = ElementUtils.findMethod(typeElement, "executeProlog");
        model.executeEpilog = ElementUtils.findMethod(typeElement, "executeEpilog");
        model.interceptControlFlowException = ElementUtils.findMethod(typeElement, "interceptControlFlowException");
        model.interceptInternalException = ElementUtils.findMethod(typeElement, "interceptInternalException");
        model.interceptTruffleException = ElementUtils.findMethod(typeElement, "interceptTruffleException");

        // Detect method implementations that will be overridden by the generated class.
        List<ExecutableElement> overrides = List.of(
                        ElementUtils.findMethod(types.RootNode, "execute"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "executeOSR"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "getOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "setOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "storeParentFrameInArguments"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "restoreParentFrameFromArguments"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "setUncachedInterpreterThreshold"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "materializeInstrumentTree"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "findSourceSectionAtBci"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "findBciOfOperationNode"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "readBciFromFrame"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getLocalIndex"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getLocal"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getLocals"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getLocalNames"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getLocalInfos"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "copyLocals", 2),
                        ElementUtils.findMethod(types.BytecodeRootNode, "copyLocals", 3));

        for (ExecutableElement override : overrides) {
            ExecutableElement declared = ElementUtils.findMethod(typeElement, override.getSimpleName().toString());
            if (declared == null) {
                continue;
            }

            String executeSuffix = override.getSimpleName().toString().equals("execute") ? " Override executeProlog and executeEpilog to perform actions before and after execution." : "";

            if (declared.getModifiers().contains(Modifier.FINAL)) {
                model.addError(declared,
                                "This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed." +
                                                executeSuffix);
            } else {
                model.addWarning(declared, "This method is overridden by the generated Bytecode DSL class, so this definition is unreachable and can be removed." + executeSuffix);
            }
        }

        if (model.hasErrors()) {
            return;
        }

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
        Set<TypeMirror> beTypes = new LinkedHashSet<>();

        List<AnnotationValue> boxingEliminatedTypes = (List<AnnotationValue>) ElementUtils.getAnnotationValue(generateBytecodeMirror, "boxingEliminationTypes").getValue();
        for (AnnotationValue value : boxingEliminatedTypes) {

            TypeMirror mir = getTypeMirror(value);

            if (BOXABLE_TYPE_KINDS.contains(mir.getKind())) {
                beTypes.add(mir);
            } else {
                model.addError("Cannot perform boxing elimination on %s. Remove this type from the boxing eliminated types list. Only primitive types boolean, byte, int, float, long, and double are supported.",
                                mir);
            }
        }
        model.boxingEliminatedTypes = beTypes;

        // optimization decisions & tracing
        AnnotationValue decisionsFileValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "decisionsFile", false);
        AnnotationValue decisionsOverrideFilesValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "decisionsOverrideFiles", false);
        String[] decisionsOverrideFilesPath = new String[0];

        if (decisionsFileValue != null) {
            model.decisionsFilePath = resolveElementRelativePath(typeElement, (String) decisionsFileValue.getValue());

            if (TruffleProcessorOptions.bytecodeEnableTracing(processingEnv)) {
                model.enableTracing = true;
            } else if ((boolean) ElementUtils.getAnnotationValue(generateBytecodeMirror, "forceTracing", true).getValue()) {
                model.addWarning("Bytecode DSL execution tracing is forced on. Use this only during development.");
                model.enableTracing = true;
            }
        }

        if (decisionsOverrideFilesValue != null) {
            decisionsOverrideFilesPath = ((List<AnnotationValue>) decisionsOverrideFilesValue.getValue()).stream().map(x -> (String) x.getValue()).toArray(String[]::new);
        }

        // error sync
        if (model.hasErrors()) {
            return;
        }

        for (OperationModel operation : model.getOperations()) {
            if (operation.instruction != null) {
                NodeData node = operation.instruction.nodeData;
                if (node != null) {
                    validateUniqueSpecializationNames(node, model);
                }
            }
        }

        // error sync
        if (model.hasErrors()) {
            return;
        }

        // custom operations
        boolean customOperationDeclared = false;
        for (TypeElement te : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            AnnotationMirror mir = ElementUtils.findAnnotationMirror(te, types.Operation);
            if (mir == null) {
                continue;
            }

            customOperationDeclared = true;
            CustomOperationParser.forCodeGeneration(model, types.Operation).parseCustomOperation(te, mir);
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.OperationProxy)) {
            customOperationDeclared = true;
            AnnotationValue mirrorValue = ElementUtils.getAnnotationValue(mir, "value");
            TypeMirror proxiedType = getTypeMirror(mirrorValue);

            if (proxiedType.getKind() != TypeKind.DECLARED) {
                model.addError(mir, mirrorValue, "Could not proxy operation: the proxied type must be a class, not %s.", proxiedType);
                continue;
            }

            TypeElement te = (TypeElement) ((DeclaredType) proxiedType).asElement();
            AnnotationMirror proxyable = ElementUtils.findAnnotationMirror(te.getAnnotationMirrors(), types.OperationProxy_Proxyable);
            if (proxyable == null) {
                model.addError(mir, mirrorValue, "Could not use %s as an operation proxy: the class must be annotated with @%s.%s.", te.getQualifiedName(), getSimpleName(types.OperationProxy),
                                getSimpleName(types.OperationProxy_Proxyable));
            } else if (model.enableUncachedInterpreter && !NodeParser.isGenerateUncached(te)) {
                model.addError(mir, mirrorValue, "Could not use %s as an operation proxy: the class must be annotated with @%s when an uncached interpreter is requested.", te.getQualifiedName(),
                                getSimpleName(types.GenerateUncached));
            }

            CustomOperationModel customOperation = CustomOperationParser.forCodeGeneration(model, types.OperationProxy_Proxyable).parseCustomOperation(te, mir);
            if (customOperation != null && customOperation.hasErrors()) {
                model.addError(mir, mirrorValue, "Encountered errors using %s as an OperationProxy. These errors must be resolved before the DSL can proceed.", te.getQualifiedName());
            }
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.ShortCircuitOperation)) {
            customOperationDeclared = true;
            TypeMirror proxiedType = getTypeMirror(ElementUtils.getAnnotationValue(mir, "booleanConverter"));
            if (proxiedType.getKind() != TypeKind.DECLARED) {
                model.addError("Could not proxy operation: the proxied type must be a class, not %s", proxiedType);
                continue;
            }

            TypeElement te = (TypeElement) ((DeclaredType) proxiedType).asElement();

            CustomOperationParser.forCodeGeneration(model, types.ShortCircuitOperation).parseCustomOperation(te, mir);
        }

        if (!customOperationDeclared) {
            model.addError("At least one operation must be declared using @%s, @%s, or @%s.", getSimpleName(types.Operation), getSimpleName(types.OperationProxy),
                            getSimpleName(types.ShortCircuitOperation));
        }

        // error sync
        if (model.hasErrors()) {
            return;
        }

        // parse force quickenings
        List<QuickenDecision> quickenings = parseForceQuickenings(model);
        boolean enableDecisionsFile = (decisionsFileValue != null || decisionsOverrideFilesValue != null) && !model.enableTracing;

        // apply optimization decisions
        if (enableDecisionsFile) {
            model.optimizationDecisions = parseDecisions(model, model.decisionsFilePath, decisionsOverrideFilesPath);

            for (QuickenDecision decision : model.optimizationDecisions.quickenDecisions) {
                OperationModel operation = null;
                for (OperationModel current : model.getOperations()) {
                    if (current.name.equals(decision.operation())) {
                        operation = current;
                        break;
                    }
                }
                if (operation == null) {
                    model.addError("Error reading optimization decisions: Invalid quickened operation %s.", decision.operation());
                    continue;
                }

                try {
                    operation.instruction.nodeData.findSpecializationsByName(decision.specializations());
                } catch (IllegalArgumentException e) {
                    model.addError("Error parsing optimization decisions: %s.", e.getMessage());
                    continue;
                }
                quickenings.add(decision);
            }

            for (SuperInstructionDecision decision : model.optimizationDecisions.superInstructionDecisions) {
                String resultingInstructionName = "si." + String.join(".", decision.instructions);
                List<InstructionModel> subInstructions = new ArrayList<>();
                InstructionModel lastInstruction = null;
                for (String instrName : decision.instructions) {
                    InstructionModel subInstruction = model.getInstructionByName(instrName);
                    if (subInstruction == null) {
                        model.addError("Error reading optimization decisions: Super-instruction '%s' defines a sub-instruction '%s' which does not exist.", resultingInstructionName, instrName);
                    } else if (subInstruction.kind == InstructionKind.SUPERINSTRUCTION) {
                        model.addError("Error reading optimization decisions: Super-instruction '%s' cannot contain another super-instruction '%s'.", resultingInstructionName, instrName);
                    }
                    subInstructions.add(subInstruction);
                    lastInstruction = subInstruction;
                }

                if (lastInstruction == null) {
                    // invalid super instruction
                    continue;
                }

                InstructionModel instr = model.instruction(InstructionKind.SUPERINSTRUCTION, resultingInstructionName,
                                lastInstruction.signature);
                instr.subInstructions = subInstructions;

            }

        }

        /*
         * If boxing elimination is enabled and the language uses operations with statically known
         * types we generate a quickening decisions for each operation and specialization in order
         * to enable boxing elimination.
         */
        if (model.usesBoxingElimination()) {
            for (OperationModel operation : model.getOperations()) {
                if (operation.kind != OperationKind.CUSTOM_SIMPLE) {
                    continue;
                }

                boolean genericReturnBoxingEliminated = model.isBoxingEliminated(operation.instruction.signature.returnType);
                /*
                 * First we group specializations by boxing eliminated signature. Every
                 * specialization has at most one boxing signature, so at most we will get one
                 * boxing signature for each specialization out of this (assuming no implicit
                 * casts).
                 */
                Map<List<TypeMirror>, List<SpecializationData>> boxingGroups = new LinkedHashMap<>();
                for (SpecializationData specialization : operation.instruction.nodeData.getReachableSpecializations()) {
                    if (specialization.getMethod() == null) {
                        continue;
                    }

                    Iterable<TypeMirror> signatureTypes;
                    if (genericReturnBoxingEliminated) {
                        /*
                         * If the generic instruction already supports boxing elimination with its
                         * return type we do not need to generate boxing elimination signatures for
                         * return types at all.
                         */
                        signatureTypes = specialization.getSignatureParameters().stream().map((p) -> p.getType()).toList();
                    } else {
                        signatureTypes = specialization.getTypeSignature();
                    }

                    List<TypeMirror> signature = new ArrayList<>();
                    for (TypeMirror actualType : signatureTypes) {
                        TypeMirror boxingType;
                        if (model.isBoxingEliminated(actualType)) {
                            boxingType = actualType;
                        } else {
                            boxingType = context.getType(Object.class);
                        }
                        signature.add(boxingType);
                    }

                    if (specialization.hasUnexpectedResultRewrite()) {
                        /*
                         * Unexpected result specializations effectively have an Object return type.
                         */
                        signature.set(0, context.getType(Object.class));
                    }

                    boxingGroups.computeIfAbsent(signature, (s) -> new ArrayList<>()).add(specialization);
                }

                for (List<TypeMirror> boxingGroup : boxingGroups.keySet().stream().//
                                filter((s) -> countBoxingEliminatedTypes(model, s) > 0).//
                                // Sort by number of boxing eliminated types.
                                sorted((s0, s1) -> {
                                    return Long.compare(countBoxingEliminatedTypes(model, s0), countBoxingEliminatedTypes(model, s1));

                                }).toList()) {
                    List<SpecializationData> specializations = boxingGroups.get(boxingGroup);
                    List<String> allIds = specializations.stream().filter((s) -> s.getMethod() != null).map((s) -> s.getMethodName()).toList();
                    quickenings.add(new QuickenDecision(operation.name, new HashSet<>(allIds)));
                }
            }
        }

        Map<String, List<QuickenDecision>> quickeningsByOperation = quickenings.stream().distinct().collect(Collectors.groupingBy(QuickenDecision::operation));
        for (var groupedDecision : quickeningsByOperation.entrySet()) {
            String operationName = groupedDecision.getKey();
            List<QuickenDecision> decisions = groupedDecision.getValue();
            OperationModel operation = model.getOperationByName(operationName);
            List<List<SpecializationData>> resolvedQuickenings = decisions.stream().map((d) -> operation.instruction.nodeData.findSpecializationsByName(d.specializations())).sorted(
                            (s0, s1) -> {
                                if (s0.size() != s1.size()) {
                                    // sort by size we want to check single specializations first
                                    return Integer.compare(s0.size(), s1.size());
                                }
                                return 0;
                            }).toList();

            for (List<SpecializationData> includedSpecializations : resolvedQuickenings) {
                String name;
                if (includedSpecializations.size() == operation.instruction.nodeData.getSpecializations().size()) {
                    // all specializations included
                    name = "#";
                } else {
                    name = String.join("#", includedSpecializations.stream().map((s) -> s.getId()).toList());
                }
                Signature signature = CustomOperationParser.createPolymorphicSignature(includedSpecializations.stream().map(s -> s.getMethod()).toList(), null);
                InstructionModel baseInstruction = operation.instruction;
                InstructionModel quickenedInstruction = model.quickenInstruction(baseInstruction, signature, ElementUtils.firstLetterUpperCase(name));
                quickenedInstruction.filteredSpecializations = includedSpecializations;
            }
        }

        if (model.usesBoxingElimination()) {
            InstructionModel conditional = model.instruction(InstructionKind.MERGE_CONDITIONAL,
                            "merge.conditional", model.signature(Object.class, boolean.class, Object.class));
            model.conditionalOperation.setInstruction(conditional);

            for (InstructionModel instruction : model.getInstructions().toArray(InstructionModel[]::new)) {
                switch (instruction.kind) {
                    case CUSTOM:
                        for (int i = 0; i < instruction.signature.valueCount; i++) {
                            if (instruction.getQuickeningRoot().needsBoxingElimination(model, i)) {
                                instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(i));
                            }
                        }
                        if (model.isBoxingEliminated(instruction.signature.returnType)) {
                            InstructionModel returnTypeQuickening = model.quickenInstruction(instruction,
                                            instruction.signature, "unboxed");
                            returnTypeQuickening.returnTypeQuickening = true;
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        /*
                         * This is currently not supported because short circuits produces values
                         * that can be consumed by instructions. We would need to remember the
                         * branch we entered to properly boxing eliminate it.
                         *
                         * We are not doing this yet, and it might also not be worth it.
                         */
                        break;
                    case LOAD_ARGUMENT:
                    case LOAD_CONSTANT:
                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel returnTypeQuickening = model.quickenInstruction(instruction,
                                            new Signature(boxedType, List.of()),
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            returnTypeQuickening.returnTypeQuickening = true;
                        }
                        break;
                    case BRANCH_FALSE:
                        if (model.isBoxingEliminated(context.getType(boolean.class))) {
                            instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(0));

                            InstructionModel specialization = model.quickenInstruction(instruction,
                                            new Signature(context.getType(void.class), List.of(context.getType(Object.class))),
                                            "Generic");
                            specialization.returnTypeQuickening = false;

                            InstructionModel returnTypeQuickening = model.quickenInstruction(instruction,
                                            new Signature(context.getType(void.class), List.of(context.getType(boolean.class))),
                                            "Boolean");
                            returnTypeQuickening.returnTypeQuickening = true;
                        }
                        break;
                    case MERGE_CONDITIONAL:
                        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(0));
                        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(1));
                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel specializedInstruction = model.quickenInstruction(instruction,
                                            new Signature(context.getType(Object.class), List.of(context.getType(boolean.class), boxedType)),
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            specializedInstruction.returnTypeQuickening = false;
                            specializedInstruction.specializedType = boxedType;

                            Signature newSignature = new Signature(boxedType, specializedInstruction.signature.argumentTypes);
                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            newSignature,
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;
                        }
                        InstructionModel genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;
                        break;
                    case POP:
                        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(0));
                        instruction.specializedType = context.getType(Object.class);

                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel specializedInstruction = model.quickenInstruction(instruction,
                                            new Signature(context.getType(void.class), List.of(boxedType)),
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            specializedInstruction.returnTypeQuickening = false;
                            specializedInstruction.specializedType = boxedType;
                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature, "generic");
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;
                        break;
                    case DUP:
                        break;
                    case LOAD_LOCAL:
                    case LOAD_LOCAL_MATERIALIZED:
                        // needed for boxing elimination
                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel specializedInstruction = model.quickenInstruction(instruction,
                                            instruction.signature,
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            specializedInstruction.returnTypeQuickening = false;
                            specializedInstruction.specializedType = boxedType;

                            Signature newSignature = new Signature(boxedType, instruction.signature.argumentTypes);
                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            newSignature,
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;

                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;

                        break;

                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                        // needed for boxing elimination
                        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(0));

                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel specializedInstruction = model.quickenInstruction(instruction,
                                            instruction.signature,
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            specializedInstruction.returnTypeQuickening = false;
                            specializedInstruction.specializedType = boxedType;

                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            new Signature(context.getType(void.class), List.of(boxedType)),
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;

                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;

                        break;
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

        model.finalizeInstructions();

        return;
    }

    private static String createChildBciName(int i) {
        return "child" + i;
    }

    private static long countBoxingEliminatedTypes(BytecodeDSLModel model, List<TypeMirror> s0) {
        return s0.stream().filter((t) -> model.isBoxingEliminated(t)).count();
    }

    private List<QuickenDecision> parseForceQuickenings(BytecodeDSLModel model) {
        List<QuickenDecision> decisions = new ArrayList<>();

        for (OperationModel operation : model.getOperations()) {
            InstructionModel instruction = operation.instruction;
            if (instruction == null) {
                continue;
            }
            NodeData node = instruction.nodeData;
            if (node == null) {
                continue;
            }
            Set<Element> processedElements = new HashSet<>();
            if (node != null) {
                // order map for determinism
                Map<String, Set<String>> grouping = new LinkedHashMap<>();
                for (SpecializationData specialization : node.getSpecializations()) {
                    if (specialization.getMethod() == null) {
                        continue;
                    }
                    ExecutableElement method = specialization.getMethod();
                    processedElements.add(method);

                    Map<String, List<SpecializationData>> seenNames = new LinkedHashMap<>();
                    for (AnnotationMirror forceQuickening : ElementUtils.getRepeatedAnnotation(method.getAnnotationMirrors(), types.ForceQuickening)) {
                        String name = ElementUtils.getAnnotationValue(String.class, forceQuickening, "value", false);

                        if (!model.enableQuickening) {
                            model.addError(method, "Cannot use @%s if quickening node enabled for @%s. Enable quickening in @%s to resolve this.", ElementUtils.getSimpleName(types.ForceQuickening),
                                            ElementUtils.getSimpleName(types.GenerateBytecode), ElementUtils.getSimpleName(types.ForceQuickening));
                            break;
                        }

                        if (name == null) {
                            name = "";
                        } else if (name.equals("")) {
                            model.addError(method, "Identifier for @%s must not be an empty string.", ElementUtils.getSimpleName(types.ForceQuickening));
                            continue;
                        }

                        seenNames.computeIfAbsent(name, (v) -> new ArrayList<>()).add(specialization);
                        grouping.computeIfAbsent(name, (v) -> new LinkedHashSet<>()).add(specialization.getMethodName());
                    }

                    for (var entry : seenNames.entrySet()) {
                        if (entry.getValue().size() > 1) {
                            model.addError(method, "Multiple @%s with the same value are not allowed for one specialization.", ElementUtils.getSimpleName(types.ForceQuickening));
                            break;
                        }
                    }
                }

                for (var entry : grouping.entrySet()) {
                    if (entry.getKey().equals("")) {
                        for (String specialization : entry.getValue()) {
                            decisions.add(new QuickenDecision(operation.name, Set.of(specialization)));
                        }
                    } else {
                        if (entry.getValue().size() <= 1) {
                            SpecializationData s = node.findSpecializationsByName(entry.getValue()).iterator().next();
                            model.addError(s.getMethod(), "@%s with name '%s' does only match a single quickening, but must match more than one. " +
                                            "Specify additional quickenings with the same name or remove the value from the annotation to resolve this.",
                                            ElementUtils.getSimpleName(types.ForceQuickening),
                                            entry.getKey());
                            continue;
                        }
                        decisions.add(new QuickenDecision(operation.name, entry.getValue()));
                    }
                }
            }

            // make sure force quickening is not used in wrong locations
            for (Element e : ElementUtils.loadFilteredMembers(node.getTemplateType())) {
                if (processedElements.contains(e)) {
                    // already processed
                    continue;
                }

                if (!ElementUtils.getRepeatedAnnotation(e.getAnnotationMirrors(), types.ForceQuickening).isEmpty()) {
                    model.addError(e, "Invalid location of @%s. The annotation can only be used on method annotated with @%s.",
                                    ElementUtils.getSimpleName(types.ForceQuickening),
                                    ElementUtils.getSimpleName(types.Specialization));
                }
            }
        }

        return decisions;
    }

    private static void validateUniqueSpecializationNames(NodeData node, MessageContainer messageTarget) {
        Set<String> seenSpecializationNames = new HashSet<>();
        for (SpecializationData specialization : node.getSpecializations()) {
            if (specialization.getMethod() == null) {
                continue;
            }
            String methodName = specialization.getMethodName();
            if (!seenSpecializationNames.add(methodName)) {
                messageTarget.addError(specialization.getMethod(),
                                "Specialization method name %s is not unique but might be used as an identifier to refer to specializations. " + //
                                                "Use a unqiue specialization method name unique to resolve this." + //
                                                "It is recommended to specialization method names that use a defining characteristic of the specialization, for example 'doBelowZero'.");
            }
        }
    }

    private String errorPrefix() {
        return String.format("Failed to generate code for @%s: ", getSimpleName(types.GenerateBytecode));
    }

    private String resolveElementRelativePath(Element element, String relativePath) {
        File filePath = CompilerFactory.getCompiler(element).getEnclosingSourceFile(processingEnv, element);
        return Path.of(filePath.getPath()).getParent().resolve(relativePath).toAbsolutePath().toString();
    }

    private static OptimizationDecisionsModel parseDecisions(BytecodeDSLModel model, String decisionsFile, String[] decisionOverrideFiles) {
        OptimizationDecisionsModel result = new OptimizationDecisionsModel();
        result.decisionsFilePath = decisionsFile;
        result.decisionsOverrideFilePaths = decisionOverrideFiles;

        if (decisionsFile != null) {
            parseDecisionsFile(model, result, decisionsFile, true);
        }

        return result;
    }

    private static void parseDecisionsFile(BytecodeDSLModel model, OptimizationDecisionsModel result, String filePath, boolean isMain) {
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

    private static void parseDecision(BytecodeDSLModel model, OptimizationDecisionsModel result, JSONObject decision) {
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
                result.quickenDecisions.add(new QuickenDecision(decision.getString("operation"), Set.of(jsonGetStringArray(decision, "specializations"))));
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
        return types.GenerateBytecode;
    }

    @Override
    public DeclaredType getRepeatAnnotationType() {
        /**
         * This annotation is not technically a Repeatable container for {@link @GenerateBytecode},
         * but it is a convenient way to get the processor framework to forward a node with this
         * annotation to the {@link BytecodeDSLParser}.
         */
        return types.GenerateBytecodeTestVariants;
    }
}
