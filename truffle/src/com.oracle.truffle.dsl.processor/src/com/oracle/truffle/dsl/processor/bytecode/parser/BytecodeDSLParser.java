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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleProcessorOptions;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeDSLCodeGenerator;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLBuiltins;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModels;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel.QuickenDecision;
import com.oracle.truffle.dsl.processor.bytecode.model.OptimizationDecisionsModel.ResolvedQuickenDecision;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel.Operator;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.expression.DSLExpression;
import com.oracle.truffle.dsl.processor.expression.DSLExpressionResolver;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.library.ExportsData;
import com.oracle.truffle.dsl.processor.library.ExportsLibrary;
import com.oracle.truffle.dsl.processor.library.ExportsParser;
import com.oracle.truffle.dsl.processor.model.ImplicitCastData;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.TypeSystemParser;

public class BytecodeDSLParser extends AbstractParser<BytecodeDSLModels> {

    public static final String SYMBOL_ROOT_NODE = "$rootNode";
    public static final String SYMBOL_BYTECODE_NODE = "$bytecodeNode";
    public static final String SYMBOL_BYTECODE_INDEX = "$bytecodeIndex";

    private static final int MAX_TAGS = 32;
    private static final int MAX_INSTRUMENTATIONS = 31;
    // we reserve 14 bits for future features
    private static final int MAX_TAGS_AND_INSTRUMENTATIONS = 50;

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

            models = List.of(createBytecodeDSLModel(typeElement, generateBytecodeMirror, "Gen", types.BytecodeBuilder));
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
        boolean enableMaterializedLocalAccessors = false;
        boolean enableTagInstrumentation = false;

        TypeMirror abstractBuilderType = BytecodeDSLCodeGenerator.createAbstractBuilderType(typeElement).asType();

        List<BytecodeDSLModel> result = new ArrayList<>();

        for (AnnotationMirror variant : variants) {
            AnnotationValue suffixValue = ElementUtils.getAnnotationValue(variant, "suffix");
            String suffix = ElementUtils.resolveAnnotationValue(String.class, suffixValue);

            AnnotationValue generateBytecodeMirrorValue = ElementUtils.getAnnotationValue(variant, "configuration");
            AnnotationMirror generateBytecodeMirror = ElementUtils.resolveAnnotationValue(AnnotationMirror.class, generateBytecodeMirrorValue);

            BytecodeDSLModel model = createBytecodeDSLModel(typeElement, generateBytecodeMirror, suffix, abstractBuilderType);

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
            boolean variantEnableYield = ElementUtils.resolveAnnotationValue(Boolean.class, variantEnableYieldValue);
            if (first) {
                enableYield = variantEnableYield;
            } else if (variantEnableYield != enableYield) {
                model.addError(generateBytecodeMirror, variantEnableYieldValue, "Incompatible variant: all variants must have the same value for enableYield.");
            }

            AnnotationValue variantEnableMaterializedLocalAccessesValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "enableMaterializedLocalAccesses");
            boolean variantEnableMaterializedLocalAccesses = ElementUtils.resolveAnnotationValue(Boolean.class, variantEnableMaterializedLocalAccessesValue);
            if (first) {
                enableMaterializedLocalAccessors = variantEnableMaterializedLocalAccesses;
            } else if (variantEnableMaterializedLocalAccesses != enableMaterializedLocalAccessors) {
                model.addError(generateBytecodeMirror, variantEnableMaterializedLocalAccessesValue,
                                "Incompatible variant: all variants must have the same value for enableMaterializedLocalAccesses.");
            }

            AnnotationValue variantEnableTagInstrumentationValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "enableTagInstrumentation");
            boolean variantEnableTagInstrumentation = ElementUtils.resolveAnnotationValue(Boolean.class, variantEnableTagInstrumentationValue);
            if (first) {
                enableTagInstrumentation = variantEnableTagInstrumentation;
            } else if (variantEnableTagInstrumentation != enableTagInstrumentation) {
                model.addError(generateBytecodeMirror, variantEnableTagInstrumentationValue, "Incompatible variant: all variants must have the same value for enableTagInstrumentation.");
            }

            first = false;
            result.add(model);
        }

        return result;
    }

    private BytecodeDSLModel createBytecodeDSLModel(TypeElement typeElement, AnnotationMirror generateBytecodeMirror, String suffix, TypeMirror abstractBuilderType) {
        return new BytecodeDSLModel(context, typeElement, generateBytecodeMirror, typeElement.getSimpleName() + suffix, abstractBuilderType);
    }

    @SuppressWarnings("unchecked")
    private void parseBytecodeDSLModel(TypeElement typeElement, BytecodeDSLModel model, AnnotationMirror generateBytecodeMirror) {
        model.languageClass = (DeclaredType) ElementUtils.getAnnotationValue(generateBytecodeMirror, "languageClass").getValue();
        model.enableUncachedInterpreter = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableUncachedInterpreter");
        model.enableSerialization = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableSerialization");
        model.enableSpecializationIntrospection = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableSpecializationIntrospection");
        model.allowUnsafe = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "allowUnsafe");
        model.enableMaterializedLocalAccesses = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableMaterializedLocalAccesses");
        model.enableYield = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableYield");
        model.storeBciInFrame = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "storeBytecodeIndexInFrame");
        model.enableQuickening = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableQuickening");
        model.enableTagInstrumentation = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableTagInstrumentation");
        model.enableRootTagging = model.enableTagInstrumentation && ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableRootTagging");
        model.enableRootBodyTagging = model.enableTagInstrumentation && ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableRootBodyTagging");
        model.enableBlockScoping = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableBlockScoping");
        model.defaultLocalValue = ElementUtils.getAnnotationValue(String.class, generateBytecodeMirror, "defaultLocalValue", false);
        model.variadicStackLimit = ElementUtils.getAnnotationValue(String.class, generateBytecodeMirror, "variadicStackLimit", true);
        boolean enableBytecodeDebugListener = ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "enableBytecodeDebugListener");
        model.bytecodeDebugListener = (!enableBytecodeDebugListener || types.BytecodeDebugListener == null) ? false : ElementUtils.isAssignable(typeElement.asType(), types.BytecodeDebugListener);
        model.additionalAssertions = TruffleProcessorOptions.additionalAssertions(processingEnv) ||
                        ElementUtils.getAnnotationValue(Boolean.class, generateBytecodeMirror, "additionalAssertions", true);

        BytecodeDSLBuiltins.addBuiltins(model, types, context);

        // Check basic declaration properties.
        Set<Modifier> modifiers = typeElement.getModifiers();
        if (!modifiers.contains(Modifier.ABSTRACT)) {
            model.addError(typeElement, "Bytecode DSL class must be declared abstract.");
        }
        if (!ElementUtils.isAssignable(typeElement.asType(), types.RootNode)) {
            model.addError(typeElement, "Bytecode DSL class must directly or indirectly subclass %s.", getSimpleName(types.RootNode));
        }
        if (!ElementUtils.isAssignable(typeElement.asType(), types.BytecodeRootNode)) {
            model.addError(typeElement, "Bytecode DSL class must directly or indirectly implement %s.", getSimpleName(types.BytecodeRootNode));
        }
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
            model.addError(typeElement, "Bytecode DSL class must be public or package-private.");
        }
        if (typeElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !modifiers.contains(Modifier.STATIC)) {
            model.addError(typeElement, "Bytecode DSL class must be static if it is a nested class.");
        }

        List<? extends AnnotationMirror> annotations = typeElement.getAnnotationMirrors();
        checkUnsupportedAnnotation(model, annotations, types.GenerateAOT, null);
        checkUnsupportedAnnotation(model, annotations, types.GenerateInline, null);
        checkUnsupportedAnnotation(model, annotations, types.GenerateCached, "Bytecode DSL always generates a cached interpreter.");
        checkUnsupportedAnnotation(model, annotations, types.GenerateUncached, "Set GenerateBytecode#enableUncachedInterpreter to generate an uncached interpreter.");

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
            if (!ElementUtils.typeEquals(params.get(0).asType(), model.languageClass)) {
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
                            getSimpleName(model.languageClass),
                            getSimpleName(types.FrameDescriptor),
                            getSimpleName(model.languageClass),
                            getSimpleName(types.FrameDescriptor),
                            getSimpleName(types.FrameDescriptor_Builder));
            return;
        }

        // tag instrumentation
        if (model.enableTagInstrumentation) {
            AnnotationValue taginstrumentationValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "enableTagInstrumentation");
            if (model.getProvidedTags().isEmpty()) {
                model.addError(generateBytecodeMirror, taginstrumentationValue,
                                String.format("Tag instrumentation cannot be enabled if the specified language class '%s' does not export any tags using @%s. " +
                                                "Specify at least one provided tag or disable tag instrumentation for this root node.",
                                                getQualifiedName(model.languageClass),
                                                getSimpleName(types.ProvidedTags)));
            } else if (model.enableRootTagging && model.getProvidedRootTag() == null) {
                model.addError(generateBytecodeMirror, taginstrumentationValue,
                                "Tag instrumentation uses implicit root tagging, but the RootTag was not provided by the language class '%s'. " +
                                                "Specify the tag using @%s(%s.class) on the language class or explicitly disable root tagging using @%s(.., enableRootTagging=false) to resolve this.",
                                getQualifiedName(model.languageClass),
                                getSimpleName(types.ProvidedTags),
                                getSimpleName(types.StandardTags_RootTag),
                                getSimpleName(types.GenerateBytecode));
                model.enableRootTagging = false;
            } else if (model.enableRootBodyTagging && model.getProvidedRootBodyTag() == null) {
                model.addError(generateBytecodeMirror, taginstrumentationValue,
                                "Tag instrumentation uses implicit root body tagging, but the RootTag was not provided by the language class '%s'. " +
                                                "Specify the tag using @%s(%s.class) on the language class or explicitly disable root tagging using @%s(.., enableRootBodyTagging=false) to resolve this.",
                                getQualifiedName(model.languageClass),
                                getSimpleName(types.ProvidedTags),
                                getSimpleName(types.StandardTags_RootBodyTag),
                                getSimpleName(types.GenerateBytecode));
                model.enableRootBodyTagging = false;
            }

            if (model.getProvidedTags().size() > MAX_TAGS) {
                model.addError(generateBytecodeMirror, taginstrumentationValue,
                                "Tag instrumentation is currently limited to a maximum of 32 tags. " + //
                                                "The language '%s' provides %s tags. " +
                                                "Reduce the number of tags to resolve this.",
                                getQualifiedName(model.languageClass),
                                model.getProvidedTags().size());
            }

            parseTagTreeNodeLibrary(model, generateBytecodeMirror);

        } else {
            AnnotationValue tagTreeNodeLibraryValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "tagTreeNodeLibrary", false);
            if (tagTreeNodeLibraryValue != null) {
                model.addError(generateBytecodeMirror, tagTreeNodeLibraryValue,
                                "The attribute tagTreeNodeLibrary must not be set if enableTagInstrumentation is not enabled. Enable tag instrumentation or remove this attribute.");
            }
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
        model.interceptControlFlowException = ElementUtils.findMethod(typeElement, "interceptControlFlowException");
        model.interceptInternalException = ElementUtils.findMethod(typeElement, "interceptInternalException");
        model.interceptTruffleException = ElementUtils.findMethod(typeElement, "interceptTruffleException");

        // Detect method implementations that will be overridden by the generated class.
        List<ExecutableElement> overrides = new ArrayList<>(List.of(
                        ElementUtils.findMethod(types.RootNode, "execute"),
                        ElementUtils.findMethod(types.RootNode, "computeSize"),
                        ElementUtils.findMethod(types.RootNode, "findBytecodeIndex"),
                        ElementUtils.findMethod(types.RootNode, "findInstrumentableCallNode"),
                        ElementUtils.findMethod(types.RootNode, "isInstrumentable"),
                        ElementUtils.findMethod(types.RootNode, "isCaptureFramesForTrace"),
                        ElementUtils.findMethod(types.RootNode, "prepareForCall"),
                        ElementUtils.findMethod(types.RootNode, "prepareForCompilation"),
                        ElementUtils.findMethod(types.RootNode, "prepareForInstrumentation"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getBytecodeNode"),
                        ElementUtils.findMethod(types.BytecodeRootNode, "getRootNodes"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "executeOSR"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "getOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "setOSRMetadata"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "storeParentFrameInArguments"),
                        ElementUtils.findMethod(types.BytecodeOSRNode, "restoreParentFrameFromArguments")));

        for (ExecutableElement override : overrides) {
            ExecutableElement declared = ElementUtils.findMethod(typeElement, override.getSimpleName().toString());
            if (declared == null) {
                continue;
            }

            if (declared.getModifiers().contains(Modifier.FINAL)) {
                model.addError(declared,
                                "This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. " +
                                                "You can remove the final modifier to resolve this issue, but since the override will make this method unreachable, it is recommended to simply remove it.");
            } else {
                model.addWarning(declared, "This method is overridden by the generated Bytecode DSL class, so this definition is unreachable and can be removed.");
            }
        }

        if (model.hasErrors()) {
            return;
        }

        List<Element> elementsForDSLExpressions = new ArrayList<>();
        for (VariableElement te : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (!te.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            elementsForDSLExpressions.add(te);
        }
        for (Element te : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (!te.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            elementsForDSLExpressions.add(te);
        }
        DSLExpressionResolver resolver = new DSLExpressionResolver(context, typeElement, elementsForDSLExpressions);

        parseDefaultUncachedThreshold(model, generateBytecodeMirror, resolver);
        if (model.defaultLocalValue != null) {
            model.defaultLocalValueExpression = DSLExpression.parse(model, "defaultLocalValue", model.defaultLocalValue);
            if (model.defaultLocalValueExpression != null) {
                model.defaultLocalValueExpression = DSLExpression.resolve(resolver, model, "defaultLocalValue", model.defaultLocalValueExpression, model.defaultLocalValue);
            }
        }

        model.variadicStackLimitExpression = DSLExpression.parse(model, "variadicStackLimit", model.variadicStackLimit);
        if (model.variadicStackLimitExpression != null) {
            model.variadicStackLimitExpression = DSLExpression.resolve(resolver, model, "variadicStackLimit", model.variadicStackLimitExpression, model.variadicStackLimit);

            if (!model.hasErrors()) {
                AnnotationMirror mirror = model.getMessageAnnotation();
                AnnotationValue value = ElementUtils.getAnnotationValue(mirror, "variadicStackLimit");
                if (!ElementUtils.typeEquals(context.getType(int.class), model.variadicStackLimitExpression.getResolvedType())) {
                    model.addError(mirror, value, "Invalid variadic stack limit specified. Must return 'int' but returned '%s'", getSimpleName(model.variadicStackLimitExpression.getResolvedType()));
                }

                if (!model.hasErrors()) {
                    Object constant = model.variadicStackLimitExpression.resolveConstant();
                    if (constant != null) {
                        if (constant instanceof Integer i) {
                            if (i <= 1) {
                                model.addError(mirror, value, "The variadic stack limit must be greater than 1.");
                            } else if (i % 2 != 0) {
                                model.addError(mirror, value, "The variadic stack limit must be a power of 2.");
                            } else if (i > Short.MAX_VALUE) {
                                model.addError(mirror, value, "The variadic stack limit must be smaller or equal to Short.MAX_VALUE.");
                            }
                        } else {
                            throw new AssertionError("Invalid type should already be handled.");
                        }
                    }
                }
            }
        }

        // find and bind type system
        TypeSystemData typeSystem = parseTypeSystemReference(typeElement);
        if (typeSystem == null) {
            model.addError("The used type system is invalid. Fix errors in the type system first.");
            return;
        }
        model.typeSystem = typeSystem;

        // find and bind boxing elimination types
        Set<TypeMirror> beTypes = new LinkedHashSet<>();

        List<AnnotationValue> boxingEliminatedTypes = (List<AnnotationValue>) ElementUtils.getAnnotationValue(generateBytecodeMirror, "boxingEliminationTypes").getValue();
        for (AnnotationValue value : boxingEliminatedTypes) {

            TypeMirror mir = getTypeMirror(context, value);

            if (BOXABLE_TYPE_KINDS.contains(mir.getKind())) {
                beTypes.add(mir);
            } else {
                model.addError("Cannot perform boxing elimination on %s. Remove this type from the boxing eliminated types list. Only primitive types boolean, byte, int, float, long, and double are supported.",
                                mir);
            }
        }
        model.boxingEliminatedTypes = beTypes;

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
            AnnotationMirror mir = findOperationAnnotation(model, te);
            if (mir == null) {
                continue;
            }
            customOperationDeclared = true;
            CustomOperationParser.forCodeGeneration(model, types.Operation).parseCustomRegularOperation(mir, te, null);
        }

        if (model.getInstrumentations().size() > MAX_INSTRUMENTATIONS) {
            model.addError("Too many @Instrumentation annotated operations specified. The number of instrumentations is " + model.getInstrumentations().size() +
                            ". The maximum number of instrumentations is " + MAX_INSTRUMENTATIONS + ".");
        } else if (model.getInstrumentations().size() + model.getProvidedTags().size() > MAX_TAGS_AND_INSTRUMENTATIONS) {
            model.addError("Too many @Instrumentation and provided tags specified. The number of instrumentrations is " + model.getInstrumentations().size() + " and provided tags is " +
                            model.getProvidedTags().size() +
                            ". The maximum number of instrumentations and provided tags is " + MAX_TAGS_AND_INSTRUMENTATIONS + ".");
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.OperationProxy)) {
            customOperationDeclared = true;
            AnnotationValue mirrorValue = ElementUtils.getAnnotationValue(mir, "value");
            TypeMirror proxiedType = getTypeMirror(context, mirrorValue);

            String name = ElementUtils.getAnnotationValue(String.class, mir, "name");

            if (proxiedType.getKind() != TypeKind.DECLARED) {
                model.addError(mir, mirrorValue, "Could not proxy operation: the proxied type must be a class, not %s.", proxiedType);
                continue;
            }

            TypeElement te = (TypeElement) ((DeclaredType) proxiedType).asElement();
            AnnotationMirror proxyable = ElementUtils.findAnnotationMirror(te.getAnnotationMirrors(), types.OperationProxy_Proxyable);
            if (proxyable == null) {
                model.addError(mir, mirrorValue, "Could not use %s as an operation proxy: the class must be annotated with @%s.%s.", te.getQualifiedName(),
                                getSimpleName(types.OperationProxy),
                                getSimpleName(types.OperationProxy_Proxyable));
            } else if (model.enableUncachedInterpreter) {
                boolean allowUncached = ElementUtils.getAnnotationValue(Boolean.class, proxyable, "allowUncached", true);
                boolean forceCached = ElementUtils.getAnnotationValue(Boolean.class, mir, "forceCached", true);
                if (!allowUncached && !forceCached) {
                    model.addError(mir, mirrorValue,
                                    "Could not use %s as an operation proxy: the class must be annotated with @%s.%s(allowUncached=true) when an uncached interpreter is requested (or the proxy declaration should use @%s(..., forceCached=true)).",
                                    te.getQualifiedName(),
                                    getSimpleName(types.OperationProxy),
                                    getSimpleName(types.OperationProxy_Proxyable),
                                    getSimpleName(types.OperationProxy));
                }
            }

            CustomOperationModel customOperation = CustomOperationParser.forCodeGeneration(model, types.OperationProxy_Proxyable).parseCustomRegularOperation(mir, te, name);
            if (customOperation != null && customOperation.hasErrors()) {
                model.addError(mir, mirrorValue, "Encountered errors using %s as an OperationProxy. These errors must be resolved before the DSL can proceed.", te.getQualifiedName());
            }
        }

        for (AnnotationMirror mir : ElementUtils.getRepeatedAnnotation(typeElement.getAnnotationMirrors(), types.ShortCircuitOperation)) {
            customOperationDeclared = true;

            String name = ElementUtils.getAnnotationValue(String.class, mir, "name");

            AnnotationValue operatorValue = ElementUtils.getAnnotationValue(mir, "operator");
            Operator operator = Operator.valueOf(((VariableElement) operatorValue.getValue()).getSimpleName().toString());

            AnnotationValue booleanConverterValue = ElementUtils.getAnnotationValue(mir, "booleanConverter");
            TypeMirror booleanConverter = getTypeMirror(context, booleanConverterValue);

            TypeElement booleanConverterTypeElement;
            if (booleanConverter.getKind() == TypeKind.DECLARED) {
                booleanConverterTypeElement = (TypeElement) ((DeclaredType) booleanConverter).asElement();
            } else if (booleanConverter.getKind() == TypeKind.VOID) {
                if (operator.returnConvertedBoolean) {
                    Operator alternative = switch (operator) {
                        case AND_RETURN_CONVERTED -> Operator.AND_RETURN_VALUE;
                        case OR_RETURN_CONVERTED -> Operator.OR_RETURN_VALUE;
                        default -> throw new AssertionError();
                    };
                    model.addError(mir, operatorValue, "Short circuit operation uses %s but no boolean converter was declared. Use %s or specify a boolean converter.", operator, alternative);
                    continue;
                }
                booleanConverterTypeElement = null;
            } else {
                model.addError(mir, booleanConverterValue, "Could not use class as boolean converter: the converter type must be a declared type, not %s.", booleanConverter);
                continue;
            }
            CustomOperationParser.forCodeGeneration(model, types.ShortCircuitOperation).parseCustomShortCircuitOperation(mir, name, operator, booleanConverterTypeElement);
        }

        if (!customOperationDeclared) {
            model.addWarning("No custom operations were declared. Custom operations can be declared using @%s, @%s, or @%s.",
                            getSimpleName(types.Operation), getSimpleName(types.OperationProxy), getSimpleName(types.ShortCircuitOperation));
        }

        // error sync
        if (model.hasErrors()) {
            return;
        }

        if (model.localAccessesNeedLocalIndex()) {
            // clearLocal never looks up the tag, so it does not need a local index.
            model.loadLocalOperation.instruction.addImmediate(ImmediateKind.LOCAL_INDEX, "local_index");
            model.storeLocalOperation.instruction.addImmediate(ImmediateKind.LOCAL_INDEX, "local_index");
        }
        if (model.materializedLocalAccessesNeedLocalIndex()) {
            model.loadLocalMaterializedOperation.instruction.addImmediate(ImmediateKind.LOCAL_INDEX, "local_index");
            model.storeLocalMaterializedOperation.instruction.addImmediate(ImmediateKind.LOCAL_INDEX, "local_index");
        }

        /*
         * Compute optimizations flags. Must happen after all custom operations are parsed.
         */
        model.hasCustomVariadic = false;
        model.hasVariadicReturn = false;
        model.maximumVariadicOffset = 0;
        for (OperationModel operation : model.getOperations()) {
            if (operation.kind == OperationKind.CUSTOM && operation.isVariadic) {
                model.hasCustomVariadic = true;
            }
            if (operation.variadicReturn) {
                model.hasVariadicReturn = true;
            }
            model.maximumVariadicOffset = Math.max(operation.variadicOffset, model.maximumVariadicOffset);
        }

        if (model.hasVariadicReturn && !model.hasCustomVariadic) {
            model.addError("An operation with @%s return value was specified but no operation takes @%s operands. Specify at least one operation that allows a @%s number of operands or remove the annotation.",
                            getSimpleName(types.Variadic), getSimpleName(types.Variadic), getSimpleName(types.Variadic));
            return;
        }

        // parse force quickenings
        List<QuickenDecision> manualQuickenings = parseForceQuickenings(model);

        // TODO GR-57220

        resolveBoxingElimination(model, manualQuickenings);

        // Validate fields for serialization.
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

    private void resolveBoxingElimination(BytecodeDSLModel model, List<QuickenDecision> manualQuickenings) {
        /*
         * If boxing elimination is enabled and the language uses operations with statically known
         * types we generate quickening decisions for each operation and specialization in order to
         * enable boxing elimination.
         */
        List<ResolvedQuickenDecision> boxingEliminationQuickenings = new ArrayList<>();
        if (model.usesBoxingElimination()) {

            for (OperationModel operation : model.getOperations()) {
                if (operation.kind != OperationKind.CUSTOM && operation.kind != OperationKind.CUSTOM_INSTRUMENTATION) {
                    continue;
                }

                boolean genericReturnBoxingEliminated = model.isBoxingEliminated(operation.instruction.signature.returnType);
                /*
                 * First we group specializations by boxing eliminated signature. Every
                 * specialization has at most one boxing signature without implicit casts. With
                 * implict casts one specialization can have multiple.
                 */
                Map<List<TypeMirror>, List<SpecializationData>> boxingGroups = new LinkedHashMap<>();
                int signatureCount = 0;
                for (SpecializationData specialization : operation.instruction.nodeData.getReachableSpecializations()) {
                    if (specialization.getMethod() == null) {
                        continue;
                    }

                    List<TypeMirror> baseSignature = operation.getSpecializationSignature(specialization).signature().getDynamicOperandTypes();
                    List<List<TypeMirror>> expandedSignatures = expandBoxingEliminatedImplicitCasts(model, operation.instruction.nodeData.getTypeSystem(), baseSignature);

                    signatureCount += expandedSignatures.size();

                    TypeMirror boxingReturnType;
                    if (specialization.hasUnexpectedResultRewrite()) {
                        /*
                         * Unexpected result specializations effectively have an Object return type.
                         */
                        boxingReturnType = context.getType(Object.class);
                    } else if (genericReturnBoxingEliminated) {
                        /*
                         * If the generic instruction already supports boxing elimination with its
                         * return type we do not need to generate boxing elimination signatures for
                         * return types at all.
                         */
                        boxingReturnType = context.getType(Object.class);
                    } else {
                        boxingReturnType = specialization.getReturnType().getType();
                    }

                    for (List<TypeMirror> signature : expandedSignatures) {
                        signature.add(0, boxingReturnType);
                    }

                    for (List<TypeMirror> sig : expandedSignatures.stream().//
                                    filter((e) -> e.stream().anyMatch(model::isBoxingEliminated)).toList()) {
                        boxingGroups.computeIfAbsent(sig, (s) -> new ArrayList<>()).add(specialization);
                    }

                    /*
                     * With boxing overloads we need to force quicken each specialization to ensure
                     * the boxing overload can be selected.
                     */
                    if (specialization.getBoxingOverloads().size() > 0) {
                        boolean isBoxingEliminatedOverload = false;
                        for (SpecializationData boxingOverload : specialization.getBoxingOverloads()) {
                            if (model.isBoxingEliminated(boxingOverload.getReturnType().getType())) {
                                isBoxingEliminatedOverload = true;
                                break;
                            }
                        }
                        if (isBoxingEliminatedOverload && operation.instruction.nodeData.getReachableSpecializations().size() > 1) {
                            for (List<TypeMirror> signature : expandedSignatures) {
                                List<TypeMirror> parameterTypes = signature.subList(1, signature.size());
                                boxingEliminationQuickenings.add(new ResolvedQuickenDecision(operation, List.of(specialization), parameterTypes));
                            }
                        }
                    }
                }

                if (signatureCount > 32 && operation.customModel != null) {
                    // We should eventually offer a solution for this problem, if it comes more
                    // often in the future.
                    operation.customModel.addWarning(
                                    String.format("This operation expands to '%s' instructions due to boxing elimination.", signatureCount));
                }

                for (List<TypeMirror> boxingGroup : boxingGroups.keySet().stream().//
                                filter((s) -> countBoxingEliminatedTypes(model, s) > 0).//
                                // Sort by number of boxing eliminated types.
                                sorted((s0, s1) -> {
                                    return Long.compare(countBoxingEliminatedTypes(model, s0), countBoxingEliminatedTypes(model, s1));
                                }).toList()) {

                    List<SpecializationData> specializations = boxingGroups.get(boxingGroup);
                    // filter return type
                    List<TypeMirror> parameterTypes = boxingGroup.subList(1, boxingGroup.size());
                    boxingEliminationQuickenings.add(new ResolvedQuickenDecision(operation, specializations, parameterTypes));
                }
            }
        }

        List<ResolvedQuickenDecision> resolvedQuickenings = Stream.concat(boxingEliminationQuickenings.stream(),
                        manualQuickenings.stream().map((e) -> e.resolve(model))).//
                        distinct().//
                        sorted(Comparator.comparingInt(e -> e.specializations().size())).//
                        toList();

        Map<List<SpecializationData>, List<ResolvedQuickenDecision>> decisionsBySpecializations = //
                        resolvedQuickenings.stream().collect(Collectors.groupingBy((e) -> e.specializations(),
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        for (var entry : decisionsBySpecializations.entrySet()) {
            List<SpecializationData> includedSpecializations = entry.getKey();
            List<ResolvedQuickenDecision> decisions = entry.getValue();

            for (ResolvedQuickenDecision quickening : decisions) {
                assert !includedSpecializations.isEmpty();
                NodeData node = quickening.operation().instruction.nodeData;

                String name;
                if (includedSpecializations.size() == quickening.operation().instruction.nodeData.getSpecializations().size()) {
                    // all specializations included
                    name = "#";
                } else {
                    name = String.join("#", includedSpecializations.stream().map((s) -> s.getId()).toList());
                }

                if (decisions.size() > 1) {
                    // more than one decisions for this combination of specializations
                    for (TypeMirror type : quickening.types()) {
                        if (model.isBoxingEliminated(type)) {
                            name += "$" + ElementUtils.getSimpleName(type);
                        } else {
                            name += "$Object";
                        }
                    }
                }

                List<ExecutableElement> includedSpecializationElements = includedSpecializations.stream().map(s -> s.getMethod()).toList();
                List<SpecializationSignature> includedSpecializationSignatures = CustomOperationParser.parseSignatures(includedSpecializationElements, node,
                                quickening.operation().constantOperands);

                Signature signature = SpecializationSignatureParser.createPolymorphicSignature(includedSpecializationSignatures,
                                includedSpecializationElements, node);

                // inject custom signatures.
                for (int i = 0; i < quickening.types().size(); i++) {
                    TypeMirror type = quickening.types().get(i);
                    if (model.isBoxingEliminated(type)) {
                        signature = signature.specializeOperandType(i, type);
                    }
                }

                InstructionModel baseInstruction = quickening.operation().instruction;

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

                        for (int i = 0; i < instruction.signature.dynamicOperandCount; i++) {
                            if (instruction.getQuickeningRoot().needsBoxingElimination(model, i)) {
                                instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(i));
                            }
                        }

                        // handle boxing overloads
                        SpecializationData singleSpecialization = instruction.resolveSingleSpecialization();
                        if (singleSpecialization != null) {
                            Set<TypeMirror> overloadedTypes = new HashSet<>();
                            for (SpecializationData boxingOverload : singleSpecialization.getBoxingOverloads()) {
                                TypeMirror overloadedReturnType = boxingOverload.getReturnType().getType();
                                if (overloadedTypes.add(overloadedReturnType) && model.isBoxingEliminated(overloadedReturnType) &&
                                                !ElementUtils.typeEquals(instruction.signature.returnType, overloadedReturnType)) {
                                    InstructionModel returnTypeQuickening = model.quickenInstruction(instruction,
                                                    instruction.signature.specializeReturnType(overloadedReturnType),
                                                    ElementUtils.getSimpleName(overloadedReturnType));
                                    returnTypeQuickening.specializedType = overloadedReturnType;
                                    returnTypeQuickening.returnTypeQuickening = true;
                                }
                            }
                            if (!overloadedTypes.isEmpty()) {
                                InstructionModel specialization = model.quickenInstruction(instruction, instruction.signature, "Generic");
                                specialization.returnTypeQuickening = false;
                                specialization.generic = true;
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
                            specialization.generic = true;
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

                            Signature newSignature = new Signature(boxedType, specializedInstruction.signature.operandTypes);
                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            newSignature,
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;
                        }
                        InstructionModel genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.generic = true;
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
                        genericQuickening.generic = true;
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;
                        break;
                    case TAG_YIELD:
                        // no boxing elimination needed for yielding
                        // we are always returning and returns do not support boxing elimination.
                        break;
                    case TAG_LEAVE:
                        instruction.addImmediate(ImmediateKind.BYTECODE_INDEX, createChildBciName(0));
                        instruction.specializedType = context.getType(Object.class);

                        for (TypeMirror boxedType : model.boxingEliminatedTypes) {
                            InstructionModel specializedInstruction = model.quickenInstruction(instruction,
                                            new Signature(context.getType(Object.class), List.of(boxedType)),
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            specializedInstruction.returnTypeQuickening = false;
                            specializedInstruction.specializedType = boxedType;

                            Signature newSignature = new Signature(boxedType, instruction.signature.operandTypes);
                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            newSignature,
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;
                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature, "generic");
                        genericQuickening.generic = true;
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

                            Signature newSignature = new Signature(boxedType, instruction.signature.operandTypes);
                            InstructionModel argumentQuickening = model.quickenInstruction(specializedInstruction,
                                            newSignature,
                                            "unboxed");
                            argumentQuickening.returnTypeQuickening = true;
                            argumentQuickening.specializedType = boxedType;

                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.generic = true;
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
                                            instruction.signature.specializeOperandType(0, boxedType),
                                            ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxedType)));
                            argumentQuickening.returnTypeQuickening = false;
                            argumentQuickening.specializedType = boxedType;
                        }

                        genericQuickening = model.quickenInstruction(instruction,
                                        instruction.signature,
                                        "generic");
                        genericQuickening.generic = true;
                        genericQuickening.returnTypeQuickening = false;
                        genericQuickening.specializedType = null;
                        break;
                }
            }

            for (InstructionModel instruction1 : model.getInstructions().toArray(InstructionModel[]::new)) {
                if (instruction1.nodeData != null) {
                    if (instruction1.getQuickeningRoot().hasSpecializedQuickenings()) {
                        instruction1.nodeData.setForceSpecialize(true);
                    }
                }
            }
        }
    }

    private static void parseDefaultUncachedThreshold(BytecodeDSLModel model, AnnotationMirror generateBytecodeMirror, DSLExpressionResolver resolver) {
        AnnotationValue explicitValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "defaultUncachedThreshold", false);
        String defaultUncachedThreshold;
        if (explicitValue != null) {
            if (!model.enableUncachedInterpreter) {
                model.addError(generateBytecodeMirror, explicitValue, "An uncached interpreter is not enabled, so the uncached threshold has no effect.");
                return;
            }
            defaultUncachedThreshold = ElementUtils.resolveAnnotationValue(String.class, explicitValue);
        } else {
            // Extract default value.
            defaultUncachedThreshold = ElementUtils.getAnnotationValue(String.class, generateBytecodeMirror, "defaultUncachedThreshold");
        }

        DSLExpression expression = DSLExpression.parse(model, "defaultUncachedThreshold", defaultUncachedThreshold);
        if (expression == null) {
            return;
        }

        DSLExpression resolvedExpression = DSLExpression.resolve(resolver, model, "defaultUncachedThreshold", expression, defaultUncachedThreshold);
        if (resolvedExpression == null) {
            return;
        }

        if (!ElementUtils.typeEquals(resolvedExpression.getResolvedType(), model.getContext().getType(int.class))) {
            model.addError(generateBytecodeMirror, explicitValue, "Expression has type %s, but type int required. Change the expression to evaluate to an int.", resolvedExpression.getResolvedType());
            return;
        }

        model.defaultUncachedThreshold = defaultUncachedThreshold;
        model.defaultUncachedThresholdExpression = resolvedExpression;
    }

    private List<List<TypeMirror>> expandBoxingEliminatedImplicitCasts(BytecodeDSLModel model, TypeSystemData typeSystem, List<TypeMirror> signatureTypes) {
        List<List<TypeMirror>> expandedSignatures = new ArrayList<>();
        expandedSignatures.add(new ArrayList<>());

        for (TypeMirror actualType : signatureTypes) {
            TypeMirror boxingType;
            if (model.isBoxingEliminated(actualType)) {
                boxingType = actualType;
            } else {
                boxingType = context.getType(Object.class);
            }
            List<ImplicitCastData> implicitCasts = typeSystem.lookupByTargetType(actualType);
            List<List<TypeMirror>> newSignatures = new ArrayList<>();
            for (ImplicitCastData cast : implicitCasts) {
                if (model.isBoxingEliminated(cast.getTargetType())) {
                    for (List<TypeMirror> existingSignature : expandedSignatures) {
                        List<TypeMirror> appended = new ArrayList<>(existingSignature);
                        appended.add(cast.getSourceType());
                        newSignatures.add(appended);
                    }
                }
            }
            for (List<TypeMirror> s : expandedSignatures) {
                List<TypeMirror> appended = new ArrayList<>(s);
                appended.add(boxingType);
                newSignatures.add(appended);
            }
            expandedSignatures = newSignatures;
        }
        return expandedSignatures;
    }

    private TypeSystemData parseTypeSystemReference(TypeElement typeElement) {
        AnnotationMirror typeSystemRefMirror = ElementUtils.findAnnotationMirror(typeElement, types.TypeSystemReference);
        if (typeSystemRefMirror != null) {
            TypeMirror typeSystemType = ElementUtils.getAnnotationValue(TypeMirror.class, typeSystemRefMirror, "value");
            if (typeSystemType instanceof DeclaredType) {
                return context.parseIfAbsent((TypeElement) ((DeclaredType) typeSystemType).asElement(), TypeSystemParser.class, (e) -> {
                    TypeSystemParser parser = new TypeSystemParser();
                    return parser.parse(e, false);
                });
            }
            return null;
        } else {
            return new TypeSystemData(context, typeElement, null, true);
        }
    }

    private void parseTagTreeNodeLibrary(BytecodeDSLModel model, AnnotationMirror generateBytecodeMirror) {
        AnnotationValue tagTreeNodeLibraryValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "tagTreeNodeLibrary");
        TypeMirror tagTreeNodeLibrary = ElementUtils.getAnnotationValue(TypeMirror.class, generateBytecodeMirror, "tagTreeNodeLibrary");
        ExportsParser parser = new ExportsParser();
        TypeElement type = ElementUtils.castTypeElement(tagTreeNodeLibrary);
        if (type == null) {
            model.addError(generateBytecodeMirror, tagTreeNodeLibraryValue,
                            "Invalid type specified for the tag tree node library. Must be a declared class.",
                            getQualifiedName(tagTreeNodeLibrary));
            return;
        }

        ExportsData exports = parser.parse(type, false);
        model.tagTreeNodeLibrary = exports;
        if (exports.hasErrors()) {
            model.addError(generateBytecodeMirror, tagTreeNodeLibraryValue,
                            "The provided tag tree node library '%s' contains errors. Please fix the errors in the class to resolve this problem.",
                            getQualifiedName(tagTreeNodeLibrary));
            return;
        }

        ExportsLibrary nodeLibrary = exports.getExportedLibraries().get(ElementUtils.getTypeSimpleId(types.NodeLibrary));
        if (nodeLibrary == null) {
            model.addError(generateBytecodeMirror, tagTreeNodeLibraryValue,
                            "The provided tag tree node library '%s' must export a library of type '%s' but does not. " +
                                            "Add @%s(value = %s.class, receiverType = %s.class) to the class declaration to resolve this.",
                            getQualifiedName(tagTreeNodeLibrary),
                            getQualifiedName(types.NodeLibrary),
                            getSimpleName(types.ExportLibrary),
                            getSimpleName(types.NodeLibrary),
                            getSimpleName(types.TagTreeNode));
            return;
        }

        if (!ElementUtils.typeEquals(nodeLibrary.getReceiverType(), types.TagTreeNode)) {
            model.addError(generateBytecodeMirror, tagTreeNodeLibraryValue,
                            "The provided tag tree node library '%s' must export the '%s' library with receiver type '%s' but it currently uses '%s'. " +
                                            "Change the receiver type to '%s' to resolve this.",
                            getQualifiedName(tagTreeNodeLibrary),
                            getSimpleName(types.NodeLibrary),
                            getSimpleName(types.TagTreeNode),
                            getSimpleName(nodeLibrary.getReceiverType()),
                            getSimpleName(types.TagTreeNode));
            return;
        }

    }

    private static void checkUnsupportedAnnotation(BytecodeDSLModel model, List<? extends AnnotationMirror> annotations, TypeMirror annotation, String error) {
        AnnotationMirror mirror = ElementUtils.findAnnotationMirror(annotations, annotation);
        if (mirror != null) {
            String errorMessage = (error != null) ? error : String.format("Bytecode DSL interpreters do not support the %s annotation.", ElementUtils.getSimpleName(annotation));
            model.addError(mirror, null, errorMessage);
        }
    }

    private AnnotationMirror findOperationAnnotation(BytecodeDSLModel model, TypeElement typeElement) {
        AnnotationMirror foundMirror = null;
        TypeMirror foundType = null;
        for (TypeMirror annotationType : List.of(types.Operation, types.Instrumentation, types.Prolog, types.EpilogReturn, types.EpilogExceptional)) {
            AnnotationMirror annotationMirror = ElementUtils.findAnnotationMirror(typeElement, annotationType);
            if (annotationMirror == null) {
                continue;
            }
            if (foundMirror == null) {
                foundMirror = annotationMirror;
                foundType = annotationType;
            } else {
                model.addError(typeElement, "@%s and @%s cannot be used at the same time. Remove one of the annotations to resolve this.",
                                getSimpleName(foundType), getSimpleName(annotationType));
                return null;
            }
        }
        return foundMirror;
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
                Map<String, Set<SpecializationData>> grouping = new LinkedHashMap<>();
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
                            model.addError(method, "Cannot use @%s if quickening is not enabled for @%s. Enable quickening in @%s to resolve this.", ElementUtils.getSimpleName(types.ForceQuickening),
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
                        grouping.computeIfAbsent(name, (v) -> new LinkedHashSet<>()).add(specialization);
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
                        for (SpecializationData specialization : entry.getValue()) {
                            decisions.add(new QuickenDecision(operation, Set.of(specialization)));
                        }
                    } else {
                        if (entry.getValue().size() == 1) {
                            SpecializationData s = entry.getValue().iterator().next();
                            model.addError(s.getMethod(), "@%s with name '%s' does only match a single quickening, but must match more than one. " +
                                            "Specify additional quickenings with the same name or remove the value from the annotation to resolve this.",
                                            ElementUtils.getSimpleName(types.ForceQuickening),
                                            entry.getKey());
                            continue;
                        }
                        decisions.add(new QuickenDecision(operation, entry.getValue()));
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
                                                "Use a unique specialization method name to resolve this. " + //
                                                "It is recommended to choose a defining characteristic of a specialization when naming it, for example 'doBelowZero'.");
            }
        }
    }

    private String errorPrefix() {
        return String.format("Failed to generate code for @%s: ", getSimpleName(types.GenerateBytecode));
    }

    public static TypeMirror getTypeMirror(ProcessorContext context, AnnotationValue value) throws AssertionError {
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
