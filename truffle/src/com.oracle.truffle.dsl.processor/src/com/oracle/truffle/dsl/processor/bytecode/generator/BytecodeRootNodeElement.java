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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.addField;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.createInitializedVariable;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.wildcard;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.addOverride;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
import com.oracle.truffle.dsl.processor.SuppressFBWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.BuilderElement.SerializationRootNodeElement;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.ConstantOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.DynamicOperandModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateWidth;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediateEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
import com.oracle.truffle.dsl.processor.generator.DSLExpressionGenerator;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.GeneratorMode;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.NodeConstants;
import com.oracle.truffle.dsl.processor.generator.StaticConstants;
import com.oracle.truffle.dsl.processor.generator.TypeSystemCodeGenerator;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

/**
 * Central code generation class for Bytecode DSL root nodes.
 */
final class BytecodeRootNodeElement extends AbstractElement {

    static final String USER_LOCALS_START_INDEX = "USER_LOCALS_START_INDEX";
    static final String BCI_INDEX = "BCI_INDEX";
    static final String COROUTINE_FRAME_INDEX = "COROUTINE_FRAME_INDEX";
    private static final String EMPTY_INT_ARRAY = "EMPTY_INT_ARRAY";

    // Bytecode version encoding: [tags][instrumentations][source bit]
    private static final int MAX_TAGS = 32;
    static final int TAG_OFFSET = 32;
    private static final int MAX_INSTRUMENTATIONS = 31;
    static final int INSTRUMENTATION_OFFSET = 1;

    // !Important: Keep these in sync with InstructionBytecodeSizeTest!
    // Estimated number of Java bytecodes per instruction.
    private static final int ESTIMATED_CUSTOM_INSTRUCTION_SIZE = 26;
    private static final int ESTIMATED_EXTRACTED_INSTRUCTION_SIZE = 20;
    // Estimated number of bytecodes needed if they are just part of the switch table.
    private static final int GROUP_DISPATCH_SIZE = 40;
    // Estimated number of java bytecodes needed for a bytecode loop including exception handling
    private static final int ESTIMATED_BYTECODE_FOOTPRINT = 1000;
    // Limit from HotSpot to be classified as a huge method and therefore not be JIT compiled
    static final int JAVA_JIT_BYTECODE_LIMIT = 8000;

    // The abstract builder and descriptor class (different from builderType if
    // GenerateBytecodeTestVariants used)
    final TypeMirror abstractBuilderType;

    final BytecodeDSLModel model;

    /**
     * We generate several CodeTypeElements to implement a Bytecode DSL interpreter. For each type,
     * a corresponding Factory class generates it and its members.
     * <p>
     * When generating code, some factories need to refer to other generated types. We declare those
     * types here as fields to make them accessible (i.e. the fields below are *not* a complete list
     * of the generated types).
     */

    // The builder class invoked by the language parser to generate the bytecode.
    final BuilderElement builder = new BuilderElement();
    final TypeMirror bytecodeBuilderType;
    final TypeMirror parserType;

    // Singleton fields for accessing arrays and the frame.
    private final CodeVariableElement fastAccess;
    private final CodeVariableElement byteArraySupport;
    private final CodeVariableElement frameExtensions;

    // Root node and ContinuationLocation classes to support yield.
    final ContinuationRootNodeImplElement continuationRootNodeImpl;

    // Implementations of public classes that Truffle interpreters interact with.
    final BytecodeRootNodesImplElement bytecodeRootNodesImpl = new BytecodeRootNodesImplElement(this);

    // Helper classes that map instructions/operations/tags to constant integral values.
    final InstructionsElement instructionsElement = new InstructionsElement(this);
    private final BytecodeDescriptorElement bytecodeDescriptorElement = new BytecodeDescriptorElement(this);
    private final OperationConstantsElement operationsElement = new OperationConstantsElement(this);
    final FrameTagConstantsElement frameTagsElement;

    // Helper class that tracks the number of guest-language loop iterations. The count must be
    // wrapped in an object, otherwise the loop unrolling logic of ExplodeLoop.MERGE_EXPLODE
    // will create a new "state" for each count.
    final LoopCounterElement loopCounter = new LoopCounterElement(this);
    final StackPointerElement stackPointerElement = new StackPointerElement(this);

    CodeTypeElement configEncoder;
    AbstractBytecodeNodeElement abstractBytecodeNode;
    TagNodeElement tagNode;
    TagRootNodeElement tagRootNode;
    InstructionTracerAccessImplElement instructionTracerAccessImplElement;
    InstructionDescriptorImplElement instructionDescriptorImpl;
    InstructionDescriptorListElement instructionDescriptorList;
    InstructionImplElement instructionImpl;
    private SerializationRootNodeElement serializationRootNode;

    private Map<TypeMirror, CodeExecutableElement> expectMethods = new HashMap<>();

    BytecodeRootNodeElement(BytecodeDSLModel model, TypeMirror abstractBuilderType) {
        super(null, Set.of(PUBLIC, FINAL), ElementKind.CLASS, ElementUtils.findPackageElement(model.getTemplateType()), model.getName());
        if (model.hasErrors()) {
            throw new IllegalArgumentException("Models with errors are not supported.");
        }

        this.model = model;
        this.abstractBuilderType = abstractBuilderType == null ? types.BytecodeBuilder : abstractBuilderType;
        this.bytecodeBuilderType = builder.asType();

        this.parserType = generic(types.BytecodeParser, abstractBuilderType == null ? bytecodeBuilderType : abstractBuilderType);
        setSuperClass(model.getTemplateType().asType());
        addField(this, Set.of(PRIVATE, STATIC, FINAL), int[].class, EMPTY_INT_ARRAY, "new int[0]");

        addField(this, Set.of(PRIVATE, STATIC, FINAL), Object[].class, "EMPTY_ARRAY", "new Object[0]");
        this.fastAccess = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.BytecodeDSLAccess, "ACCESS");
        this.fastAccess.setInit(createFastAccessFieldInitializer(model.allowUnsafe));
        this.byteArraySupport = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.ByteArraySupport, "BYTES");
        this.byteArraySupport.createInitBuilder().startCall("ACCESS.getByteArraySupport").end();
        this.frameExtensions = addField(this, Set.of(PRIVATE, STATIC, FINAL), types.FrameExtensions, "FRAMES");
        this.frameExtensions.createInitBuilder().startCall("ACCESS.getFrameExtensions").end();

        // Print a summary of the model in a docstring at the start.
        this.createDocBuilder().startDoc().lines(model.pp()).end();
        mergeSuppressWarnings(this, "static-method");

        if (model.hasYieldOperation()) {
            continuationRootNodeImpl = new ContinuationRootNodeImplElement(this);
        } else {
            continuationRootNodeImpl = null;
        }

        // Define constants for accessing the frame.
        this.addAll(createFrameLayoutConstants());

        if (model.usesBoxingElimination()) {
            frameTagsElement = new FrameTagConstantsElement(this);
        } else {
            frameTagsElement = null;
        }

        if (model.enableInstructionTracing) {
            this.instructionTracerAccessImplElement = new InstructionTracerAccessImplElement(this);
        }
        this.instructionDescriptorImpl = new InstructionDescriptorImplElement(this);
        this.instructionDescriptorList = new InstructionDescriptorListElement(this);

        this.instructionImpl = new InstructionImplElement(this);

        if (model.enableTagInstrumentation) {
            this.tagNode = new TagNodeElement(this);
            this.tagRootNode = new TagRootNodeElement(this);
        }

        this.add(bytecodeDescriptorElement);

        this.abstractBytecodeNode = this.add(new AbstractBytecodeNodeElement(this));
        if (model.enableTagInstrumentation) {
            tagNode.lazyInit();
        }

        CodeVariableElement bytecodeNode = new CodeVariableElement(Set.of(PRIVATE, VOLATILE), abstractBytecodeNode.asType(), "bytecode");
        this.add(child(bytecodeNode));
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
        addJavadoc(this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "maxLocals")), "The number of frame slots required for locals.");
        if (model.usesBoxingElimination()) {
            addJavadoc(this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "numLocals")), "The total number of locals created.");
        }
        this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "buildIndex"));
        this.add(createBytecodeUpdater());

        // Define the interpreter implementations.
        BytecodeNodeElement cachedBytecodeNode = this.add(new BytecodeNodeElement(this, InterpreterTier.CACHED));
        abstractBytecodeNode.getPermittedSubclasses().add(cachedBytecodeNode.asType());

        CodeTypeElement initialBytecodeNode;
        if (model.enableUncachedInterpreter) {
            CodeTypeElement uncachedBytecodeNode = this.add(new BytecodeNodeElement(this, InterpreterTier.UNCACHED));
            abstractBytecodeNode.getPermittedSubclasses().add(uncachedBytecodeNode.asType());
            initialBytecodeNode = uncachedBytecodeNode;
        } else {
            CodeTypeElement uninitializedBytecodeNode = this.add(new BytecodeNodeElement(this, InterpreterTier.UNINITIALIZED));
            abstractBytecodeNode.getPermittedSubclasses().add(uninitializedBytecodeNode.asType());
            initialBytecodeNode = uninitializedBytecodeNode;
        }

        // Define the builder class.
        builder.lazyInit();
        this.add(builder);

        if (model.enableInstructionTracing) {
            this.add(instructionTracerAccessImplElement);
            instructionTracerAccessImplElement.lazyInit();
        }
        this.add(instructionDescriptorImpl);
        this.add(instructionDescriptorList);
        this.add(instructionImpl);

        if (model.enableTagInstrumentation) {
            this.add(tagNode);
            this.add(tagRootNode);
        }

        instructionDescriptorImpl.lazyInit();
        instructionImpl.lazyInit();

        configEncoder = this.add(createBytecodeConfigEncoderClass());

        this.add(createNewConfigBuilder());

        // Define implementations for the public classes that Truffle interpreters interact
        // with.
        bytecodeRootNodesImpl.lazyInit();
        this.add(bytecodeRootNodesImpl);

        // Define helper classes containing the constants for instructions and operations.
        instructionsElement.lazyInit();
        this.add(instructionsElement);

        bytecodeDescriptorElement.lazyInit();

        CodeVariableElement descriptor = new CodeVariableElement(Set.of(PUBLIC, STATIC, FINAL), bytecodeDescriptorElement.asType(), "BYTECODE");
        descriptor.createInitBuilder().startNew(bytecodeDescriptorElement.asType()).end();
        this.getEnclosedElements().add(0, descriptor);

        operationsElement.lazyInit();
        this.add(operationsElement);

        if (model.usesBoxingElimination()) {
            this.add(frameTagsElement);
        }

        this.add(new ExceptionHandlerImplElement(this));
        this.add(new ExceptionHandlerListElement(this));
        this.add(new SourceInformationImplElement(this));
        this.add(new SourceInformationListElement(this));
        this.add(new SourceInformationTreeImplElement(this));
        this.add(new LocalVariableImplElement(this));
        this.add(new LocalVariableListElement(this));

        // Define the classes that implement continuations (yield).
        if (model.hasYieldOperation()) {
            continuationRootNodeImpl.lazyInit();
            this.add(continuationRootNodeImpl);
        }

        int numHandlerKinds = 0;
        this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "HANDLER_CUSTOM")).createInitBuilder().string(String.valueOf(numHandlerKinds++));

        if (model.epilogExceptional != null) {
            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "HANDLER_EPILOG_EXCEPTIONAL")).createInitBuilder().string(
                            String.valueOf(numHandlerKinds++));
        }
        if (model.enableTagInstrumentation) {
            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "HANDLER_TAG_EXCEPTIONAL")).createInitBuilder().string(String.valueOf(numHandlerKinds++));
        }

        if (model.defaultLocalValueExpression != null) {
            CodeVariableElement var = this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(Object.class), "DEFAULT_LOCAL_VALUE"));
            var.createInitBuilder().tree(DSLExpressionGenerator.write(model.defaultLocalValueExpression, null, Map.of()));
        }

        if (model.variadicStackLimitExpression != null) {
            CodeVariableElement var = this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "VARIADIC_STACK_LIMIT"));
            CodeTreeBuilder b = var.createInitBuilder();
            boolean needsValidation = model.variadicStackLimitExpression.resolveConstant() == null;
            if (needsValidation) {
                CodeExecutableElement validateVariadicStackLimit = this.add(createValidateVariadicStackLimit());
                b.startCall(validateVariadicStackLimit.getSimpleName().toString());
            }
            b.tree(DSLExpressionGenerator.write(model.variadicStackLimitExpression, null, Map.of()));
            if (needsValidation) {
                b.end();
            }

        }

        // Define the generated node's constructor.
        this.add(createConstructor(initialBytecodeNode));

        // Define the execute method.
        this.add(createExecute());

        // Define a continueAt method.
        // This method delegates to the current tier's continueAt, handling the case where
        // the tier changes.
        this.add(createContinueAt());
        this.add(createTransitionToCached());
        this.add(createUpdateBytecode());

        // Other root node overrides.
        this.add(createIsInstrumentable());
        this.add(createPrepareForCall());
        this.addOptional(createPrepareForInstrumentation());
        this.addOptional(createPrepareForCompilation());

        this.add(createEncodeTags());
        if (model.enableTagInstrumentation) {
            this.add(createFindInstrumentableCallNode());
        }

        // Define a loop counter class to track how many back-edges have been taken.
        this.add(loopCounter);
        if (model.enableStackPointerBoxing) {
            this.add(stackPointerElement);
        }

        // Define the static method to create a root node.
        this.add(createCreate());

        // Define serialization methods and helper fields.
        if (model.enableSerialization) {
            this.add(createSerialize());
            this.add(createDeserialize());
        }

        // Our serialized representation encodes Tags as shorts.
        // Construct mappings to/from these shorts for serialization/deserialization.
        if (!model.getProvidedTags().isEmpty()) {
            CodeVariableElement classToTag = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL),
                            generic(ConcurrentHashMap.class,
                                            type(Integer.class),
                                            arrayOf(generic(context.getDeclaredType(Class.class), wildcard(types.Tag, null)))),
                            "TAG_MASK_TO_TAGS");
            classToTag.createInitBuilder().string("new ConcurrentHashMap<>()");
            this.add(classToTag);
            CodeExecutableElement classToTagMethod = createMapTagMaskToTagsArray();
            this.add(classToTagMethod);

            CodeExecutableElement initializeTagIndexToClass = this.add(createInitializeTagIndexToClass());
            CodeVariableElement tagToClass = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), generic(context.getDeclaredType(ClassValue.class), type(Integer.class)),
                            "CLASS_TO_TAG_MASK");
            tagToClass.createInitBuilder().startStaticCall(initializeTagIndexToClass).end();
            this.add(tagToClass);
        }

        // Define helper methods for throwing exceptions.
        this.add(createSneakyThrow());
        this.add(createAssertionFailed());

        // Define methods for cloning the root node.
        this.addOptional(createIsCloningAllowed());
        this.addOptional(createCloneUninitializedSupported());
        this.add(new CodeVariableElement(Set.of(Modifier.PRIVATE), generic(types.BytecodeSupport_CloneReferenceList, asType()), "clones"));
        this.addOptional(createCloneUninitialized());

        this.add(createFindBytecodeIndex());
        this.add(createIsCaptureFramesForTrace());

        // Define helpers for locals.
        this.add(createGetBytecodeNode());
        this.add(createGetBytecodeNodeImpl());
        this.add(createGetBytecodeRootNode());

        this.add(createInvalidate());

        this.add(createGetRootNodes());
        this.add(createGetSourceSection());
        CodeExecutableElement translateStackTraceElement = this.addOptional(createTranslateStackTraceElement());
        if (translateStackTraceElement != null) {
            abstractBytecodeNode.add(createCreateStackTraceElement());
        }

        this.add(createComputeSize());

        // Define the generated Node classes for custom instructions.
        StaticConstants consts = new StaticConstants();
        for (InstructionModel instr : model.getInstructions()) {
            if (instr.nodeData == null || instr.quickeningBase != null) {
                continue;
            }
            this.add(createCachedDataClass(instr, consts));
        }
        if (model.epilogExceptional != null) {
            this.add(createCachedDataClass(model.epilogExceptional.operation.instruction, consts));
        }
        consts.addElementsTo(this);

        if (model.usesBoxingElimination()) {
            for (TypeMirror boxingEliminatedType : model.boxingEliminatedTypes) {
                this.add(createApplyQuickening(boxingEliminatedType));
                this.add(createIsQuickening(boxingEliminatedType));
            }
            this.add(createUndoQuickening());
            this.add(createCreateCachedTags());

        }

        if (cloneUninitializedNeedsUnquickenedBytecode()) {
            this.add(createUnquickenBytecode());
        }

        if (model.isBytecodeUpdatable()) {
            // we add this last so we do not pick up this field for constructors
            abstractBytecodeNode.add(new CodeVariableElement(Set.of(VOLATILE), arrayOf(type(byte.class)), "oldBytecodes"));
        }

        /*
         * These calls should occur after all methods have been added. They use the generated root
         * node's method set to determine what method delegate/stub methods to generate.
         */
        if (model.hasYieldOperation()) {
            continuationRootNodeImpl.addRootNodeDelegateMethods();
        }
        if (model.enableSerialization) {
            addMethodStubsToSerializationRootNode();
        }
    }

    private CodeExecutableElement createNewConfigBuilder() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), types.BytecodeConfig_Builder, "newConfigBuilder");
        method.createBuilder().startReturn().startStaticCall(types.BytecodeConfig, "newBuilder").staticReference(configEncoder.asType(), "INSTANCE").end().end();
        return method;
    }

    private CodeExecutableElement createValidateVariadicStackLimit() {
        CodeExecutableElement validateVariadicStackLimit = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "validateVariadicStackLimit",
                        new CodeVariableElement(type(int.class), "limit"));
        CodeTreeBuilder b = validateVariadicStackLimit.createBuilder();
        b.startIf().string("limit <= 1").end().startBlock();
        b.startThrow().startNew(type(IllegalStateException.class)).doubleQuote("The variadic stack limit must be greater than 1.").end().end();
        b.end().startElseIf().string("limit % 2 != 0").end().startBlock();
        b.startThrow().startNew(type(IllegalStateException.class)).doubleQuote("The variadic stack limit must be a power of 2.").end().end();
        b.end().startElseIf().string("limit > Short.MAX_VALUE").end().startBlock();
        b.startThrow().startNew(type(IllegalStateException.class)).doubleQuote("The variadic stack limit must be smaller or equal to Short.MAX_VALUE").end().end();
        b.end();
        b.statement("return limit");
        return validateVariadicStackLimit;
    }

    private CodeExecutableElement createConstructor(CodeTypeElement initialBytecodeNode) {
        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(model.languageClass, "language"));
        ctor.addParameter(new CodeVariableElement(types.FrameDescriptor_Builder, "builder"));
        ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
        ctor.addParameter(new CodeVariableElement(type(int.class), "maxLocals"));
        if (model.usesBoxingElimination()) {
            ctor.addParameter(new CodeVariableElement(type(int.class), "numLocals"));
        }
        ctor.addParameter(new CodeVariableElement(type(int.class), "buildIndex"));

        for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
            ctor.addParameter(new CodeVariableElement(var.asType(), var.getSimpleName().toString()));
        }

        CodeTreeBuilder b = ctor.getBuilder();

        // super call
        b.startStatement().startCall("super");
        b.string("language");
        if (model.fdBuilderConstructor != null) {
            b.string("builder");
        } else {
            b.string("builder.build()");
        }
        b.end(2);

        b.statement("this.nodes = nodes");
        b.statement("this.maxLocals = maxLocals");
        if (model.usesBoxingElimination()) {
            b.statement("this.numLocals = numLocals");
        }
        b.statement("this.buildIndex = buildIndex");
        b.startStatement();
        b.string("this.bytecode = ");
        b.startCall("insert");
        b.startNew(initialBytecodeNode.asType());

        for (VariableElement var : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
            b.string(var.getSimpleName().toString());
        }

        b.end(); // new
        b.end(); // insert
        b.end(); // statement

        return ctor;
    }

    private CodeExecutableElement createExecute() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "execute", new String[]{"frame"});

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startCall("continueAt");
        b.string("bytecode");
        b.string("0"); // bci
        b.string("maxLocals"); // sp
        b.startGroup().cast(types.FrameWithoutBoxing).string("frame").end();
        if (model.hasYieldOperation()) {
            b.startGroup().cast(types.FrameWithoutBoxing).string("frame").end();
            b.string("null");
        }

        b.end(2);

        return ex;
    }

    private CodeTypeElement createCachedDataClass(InstructionModel instr, StaticConstants consts) {
        NodeConstants nodeConsts = new NodeConstants();
        BytecodeDSLNodeGeneratorPlugs plugs = new BytecodeDSLNodeGeneratorPlugs(BytecodeRootNodeElement.this, instr);
        FlatNodeGenFactory factory = new FlatNodeGenFactory(context, GeneratorMode.DEFAULT, instr.nodeData, consts, nodeConsts, plugs);

        boolean forceFrame = false;
        if (isStoreBciEnabled(model, InterpreterTier.CACHED)) {
            for (SpecializationData s : instr.nodeData.getReachableSpecializations()) {
                if (isStoreBciBeforeSpecialization(model, InterpreterTier.CACHED, instr, s)) {
                    forceFrame = true;
                }
            }
        }
        factory.setForceFrameInExecuteAndSpecialize(forceFrame);

        String className = cachedDataClassName(instr);
        CodeTypeElement el = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, className);
        el.setSuperClass(types.Node);
        factory.create(el);

        List<ExecutableElement> cachedExecuteMethods = new ArrayList<>();
        cachedExecuteMethods.add(createCachedExecute(plugs, factory, el, instr));
        for (InstructionModel quickening : instr.getFlattenedQuickenedInstructions()) {
            cachedExecuteMethods.add(createCachedExecute(plugs, factory, el, quickening));
        }
        processCachedNode(el);
        el.getEnclosedElements().addAll(0, cachedExecuteMethods);

        CodeExecutableElement quicken = plugs.getQuickenMethod();
        if (quicken != null) {
            el.getEnclosedElements().add(quicken);
        }
        nodeConsts.addToClass(el);

        if (instr.canUseNodeSingleton()) {

            for (VariableElement field : ElementFilter.fieldsIn(el.getEnclosedElements())) {
                if (field.getModifiers().contains(STATIC)) {
                    continue;
                }
                // safety check so we never have inconsistent conditions
                throw new AssertionError("Instruction " + instr + " is used as singleton but has node fields " + field);
            }

            el.addAnnotationMirror(new CodeAnnotationMirror(types.DenyReplace));
            CodeVariableElement singleton = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL),
                            el.asType(), "SINGLETON");
            singleton.createInitBuilder().startNew(className).end();
            el.add(singleton);

            CodeExecutableElement isAdoptable = GeneratorUtils.override(types.Node, "isAdoptable");
            isAdoptable.createBuilder().startReturn().string("false").end();
            el.add(isAdoptable);
        }

        return el;
    }

    /**
     * Encodes the state used to begin execution. This encoding is used on method entry, on OSR
     * transition, and on continuation resumption (if enabled). The encoding is as follows:
     *
     * <pre>
     * 00000000 0000000C SSSSSSSS SSSSSSSS BBBBBBBBB BBBBBBBBB BBBBBBBBB BBBBBBBBB
     * </pre>
     *
     * Where {@code B} represents the bci and {@code S} represents the sp. If continuations are
     * enabled, the {@code C} bit is used by OSR to indicate that the OSR compilation should use a
     * materialized continuation frame for locals (this flag should not be used outside of OSR).
     */
    String encodeState(String bci, String sp, String useContinuationFrame) {
        String result = "";
        if (useContinuationFrame != null) {
            if (!model.hasYieldOperation()) {
                throw new AssertionError();
            }
            result += String.format("((%s ? 1L : 0L) << 48) | ", useContinuationFrame);
        }
        if (sp != null) {
            result += String.format("((%s & 0xFFFFL) << 32) | ", sp);
        }
        result += String.format("(%s & 0xFFFFFFFFL)", bci);
        return result;
    }

    String encodeState(String bci, String sp) {
        return encodeState(bci, sp, null);
    }

    private static final String RETURN_BCI = "0xFFFFFFFF";

    static String encodeReturnState(String sp) {
        return String.format("((%s & 0xFFFFL) << 32) | %sL", sp, RETURN_BCI);
    }

    static String encodeNewBci(String bci, String state) {
        return String.format("(%s & 0xFFFF00000000L) | (%s & 0xFFFFFFFFL)", state, bci);
    }

    static String decodeBci(String state) {
        return String.format("(int) %s", state);
    }

    static String decodeSp(String state) {
        return String.format("(short) (%s >>> 32)", state);
    }

    String decodeUseContinuationFrame(String state) {
        if (!model.hasYieldOperation()) {
            throw new AssertionError();
        }
        return String.format("(%s & (1L << 48)) != 0", state);
    }

    String clearUseContinuationFrame(String target) {
        if (!model.hasYieldOperation()) {
            throw new AssertionError();
        }
        return String.format("(%s & ~(1L << 48))", target);
    }

    private CodeExecutableElement createContinueAt() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(Object.class), "continueAt");
        ex.addParameter(new CodeVariableElement(abstractBytecodeNode.asType(), "bc"));
        ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "sp"));
        ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        if (model.hasYieldOperation()) {
            /**
             * When an {@link BytecodeRootNode} is suspended, its frame gets materialized. Resuming
             * execution with this materialized frame would provide unsatisfactory performance.
             *
             * Instead, on entry, we copy stack state from the materialized frame into the new frame
             * so that stack accesses can be virtualized. We do not copy local state since there can
             * be many temporary locals and they may not be used.
             *
             * In regular calls, localFrame is the same as frame, but when a node is suspended and
             * resumed, it will be the materialized frame used for local accesses.
             */
            ex.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "localFrame"));
            /**
             * When we resume, this parameter is non-null and is included so that the root node can
             * be patched when the interpreter transitions to cached.
             */
            ex.addParameter(new CodeVariableElement(continuationRootNodeImpl.asType(), "continuationRootNode"));
        }

        CodeTreeBuilder b = ex.createBuilder();

        if (model.overridesBytecodeDebugListenerMethod("beforeRootExecute")) {
            b.startStatement();
            b.startCall("beforeRootExecute");
            emitParseInstruction(b, "bc", "bci", CodeTreeBuilder.singleString("bc.readValidBytecode(bc.bytecodes, bci)"));
            b.end();
            b.end();
        }

        b.statement("long state = ", encodeState("bci", "sp"));

        b.startWhile().string("true").end().startBlock();

        b.startAssign("state");
        b.startCall("bc", "continueAt");
        b.string("this");
        b.string("frame");
        if (model.hasYieldOperation()) {
            b.string("localFrame");
        }
        b.string("state");
        b.end();
        b.end();

        b.startIf().string(decodeBci("state"), " == ", RETURN_BCI).end().startBlock();
        b.statement("break");
        b.end().startElseBlock();
        b.lineComment("Bytecode or tier changed");
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

        if (model.isBytecodeUpdatable() || model.hasYieldOperation()) {
            b.declaration(abstractBytecodeNode.asType(), "oldBytecode", "bc");
            b.statement("bc = this.bytecode");

            if (model.isBytecodeUpdatable()) {
                b.startAssign("state");
            } else {
                b.startStatement();
            }
            b.startCall("oldBytecode.transition");
            b.string("bc");
            if (model.isBytecodeUpdatable()) {
                b.string("state");
            }
            if (model.hasYieldOperation()) {
                b.string("continuationRootNode");
            }
            b.end(2);
        } else {
            b.statement("bc = this.bytecode");
        }

        b.end();
        b.end();

        String returnValue = uncheckedGetFrameObject(decodeSp("state"));
        b.startReturn().string(returnValue).end();
        mergeSuppressWarnings(ex, "all");
        return ex;
    }

    private Element createIsCaptureFramesForTrace() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "isCaptureFramesForTrace", new String[]{"compiled"}, new TypeMirror[]{type(boolean.class)});
        CodeTreeBuilder b = ex.createBuilder();
        if (model.captureFramesForTrace) {
            b.lineComment("GenerateBytecode#captureFramesForTrace is true.");
            b.statement("return true");
        } else if (model.storeBciInFrame) {
            b.lineComment("GenerateBytecode#storeBytecodeIndexInFrame is true, so the frame is needed for location computations.");
            b.statement("return true");
        } else if (model.enableUncachedInterpreter) {
            b.lineComment("The uncached interpreter (which is never compiled) needs the frame for location computations.");
            b.lineComment("This may capture the frame in more situations than strictly necessary, but doing so in the interpreter is inexpensive.");
            b.statement("return !compiled");
        } else {
            b.lineComment("GenerateBytecode#captureFramesForTrace is not true, and the interpreter does not need the frame for location lookups.");
            b.statement("return false");
        }
        return ex;
    }

    private Element createFindBytecodeIndex() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "findBytecodeIndex", new String[]{"node", "frame"});
        mergeSuppressWarnings(ex, "hiding");
        CodeTreeBuilder b = ex.createBuilder();
        b.startAssert().string("!(node instanceof ").type(types.BytecodeRootNode).string("): ").doubleQuote("A BytecodeRootNode should not be used as a call location.").end();
        if (model.storeBciInFrame) {
            b.startIf().string("node == null").end().startBlock();
            b.statement("return -1");
            b.end();
            /*- TODO: GR-62198
            b.startAssert();
            b.startStaticCall(types.BytecodeNode, "get").string("node").end().instanceOf(abstractBytecodeNode.asType()).string(" : ").doubleQuote("invalid bytecode node passed");
            b.end();
             */
            b.startReturn();
            b.startCall("frame.getInt").string("BCI_INDEX").end();
            b.end();
        } else {
            b.declaration(abstractBytecodeNode.asType(), "bytecode", "null");
            b.declaration(types.Node, "prev", "node");
            b.declaration(types.Node, "current", "node");
            b.startWhile().string("current != null").end().startBlock();
            b.startIf().string("current").instanceOf(abstractBytecodeNode.asType()).string(" b").end().startBlock();
            b.statement("bytecode = b");
            b.statement("break");
            b.end();
            b.statement("prev = current");
            b.statement("current = prev.getParent()");
            b.end();
            b.startIf().string("bytecode == null").end().startBlock();
            b.statement("return -1");
            b.end();
            b.statement("return bytecode.findBytecodeIndex(frame, prev)");
        }
        return ex;
    }

    private CodeExecutableElement createFindInstrumentableCallNode() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "findInstrumentableCallNode", new String[]{"callNode", "frame", "bytecodeIndex"});
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startDeclaration(types.BytecodeNode, "bc").startStaticCall(types.BytecodeNode, "get").string("callNode").end().end();
        b.startIf().string("bc == null || !(bc instanceof AbstractBytecodeNode bytecodeNode)").end().startBlock();
        ExecutableElement superImpl = ElementUtils.findMethodInClassHierarchy(ElementUtils.findMethod(types.RootNode, "findInstrumentableCallNode"), model.templateType);
        if (superImpl.getModifiers().contains(ABSTRACT)) {
            // edge case: root node could redeclare findInstrumentableCallNode as abstract.
            b.startReturn().string("null").end();
        } else {
            b.startReturn().string("super.findInstrumentableCallNode(callNode, frame, bytecodeIndex)").end();
        }
        b.end();
        b.statement("return bytecodeNode.findInstrumentableCallNode(bytecodeIndex)");
        return ex;
    }

    private CodeVariableElement createBytecodeUpdater() {
        TypeMirror updaterType = generic(type(AtomicReferenceFieldUpdater.class), this.asType(), abstractBytecodeNode.asType());
        CodeVariableElement bytecodeUpdater = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), updaterType, "BYTECODE_UPDATER");
        bytecodeUpdater.createInitBuilder().startStaticCall(type(AtomicReferenceFieldUpdater.class), "newUpdater").typeLiteral(this.asType()).typeLiteral(
                        abstractBytecodeNode.asType()).doubleQuote("bytecode").end();
        return bytecodeUpdater;
    }

    private CodeExecutableElement createCreateCachedTags() {
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE, STATIC),
                        type(byte[].class), "createCachedTags",
                        new CodeVariableElement(type(int.class), "numLocals"));

        CodeTreeBuilder b = executable.createBuilder();
        b.statement("byte[] localTags = new byte[numLocals]");
        b.statement("Arrays.fill(localTags, FrameSlotKind.Illegal.tag)");
        b.statement("return localTags");
        return executable;
    }

    private CodeExecutableElement createUndoQuickening() {
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE, STATIC),
                        type(short.class), "undoQuickening",
                        new CodeVariableElement(type(short.class), "$operand"));

        CodeTreeBuilder b = executable.createBuilder();

        b.startSwitch().string("$operand").end().startBlock();
        for (InstructionModel instruction : model.getInstructions()) {
            if (!instruction.isReturnTypeQuickening()) {
                continue;
            }
            b.startCase().tree(createInstructionConstant(instruction)).end();
            b.startCaseBlock();
            b.startReturn().tree(createInstructionConstant(instruction.quickeningBase)).end();
            b.end();
        }
        b.caseDefault();
        b.startCaseBlock();
        b.statement("return $operand");
        b.end();
        b.end();

        return executable;
    }

    private CodeExecutableElement createApplyQuickening(TypeMirror type) {
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE, STATIC),
                        type(short.class), createApplyQuickeningName(type),
                        new CodeVariableElement(type(short.class), "$operand"));

        CodeTreeBuilder b = executable.createBuilder();
        b.startSwitch().string("$operand").end().startBlock();
        for (InstructionModel instruction : model.getInstructions()) {
            if (!instruction.isReturnTypeQuickening()) {
                continue;
            }

            if (ElementUtils.typeEquals(instruction.signature.returnType, type)) {
                b.startCase().tree(createInstructionConstant(instruction.quickeningBase)).end();
                b.startCase().tree(createInstructionConstant(instruction)).end();
                b.startCaseBlock();
                b.startReturn().tree(createInstructionConstant(instruction)).end();
                b.end();
            }
        }
        b.caseDefault();
        b.startCaseBlock();
        b.statement("return -1");
        b.end();
        b.end();

        return executable;
    }

    private CodeExecutableElement createIsQuickening(TypeMirror type) {
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE, STATIC),
                        type(boolean.class), createIsQuickeningName(type),
                        new CodeVariableElement(type(short.class), "operand"));

        CodeTreeBuilder b = executable.createBuilder();
        List<InstructionModel> returnQuickenings = model.getInstructions().stream().//
                        filter((i) -> i.isReturnTypeQuickening() && ElementUtils.typeEquals(i.signature.returnType, type)).toList();

        if (returnQuickenings.isEmpty()) {
            b.returnFalse();
        } else {
            b.startSwitch().string("operand").end().startBlock();
            for (InstructionModel instruction : returnQuickenings) {
                b.startCase().tree(createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.returnTrue();
            b.end();
            b.caseDefault();
            b.startCaseBlock();
            b.returnFalse();
            b.end();
            b.end();
        }

        return executable;
    }

    private CodeExecutableElement createMapTagMaskToTagsArray() {
        TypeMirror tagClass = generic(context.getDeclaredType(Class.class), wildcard(types.Tag, null));
        CodeExecutableElement classToTagMethod = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(generic(context.getDeclaredType(Class.class), wildcard(types.Tag, null))),
                        "mapTagMaskToTagsArray");
        classToTagMethod.addParameter(new CodeVariableElement(type(int.class), "tagMask"));
        GeneratorUtils.mergeSuppressWarnings(classToTagMethod, "unchecked", "rawtypes");

        CodeTreeBuilder b = classToTagMethod.createBuilder();

        b.startStatement().type(generic(ArrayList.class, tagClass)).string(" tags = ").startNew("ArrayList<>").end().end();
        int index = 0;
        for (TypeMirror tag : model.getProvidedTags()) {
            b.startIf().string("(tagMask & ").string(1 << index).string(") != 0").end().startBlock();
            b.startStatement().startCall("tags", "add").typeLiteral(tag).end().end();
            b.end();
            index++;
        }
        b.statement("return tags.toArray(new Class[tags.size()])");
        return classToTagMethod;
    }

    private List<CodeVariableElement> createFrameLayoutConstants() {
        List<CodeVariableElement> result = new ArrayList<>();
        int reserved = 0;

        if (model.needsBciSlot()) {
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, BCI_INDEX, reserved++ + ""));
        }

        if (model.hasYieldOperation()) {
            result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, COROUTINE_FRAME_INDEX, reserved++ + ""));
        }

        result.add(createInitializedVariable(Set.of(PRIVATE, STATIC, FINAL), int.class, USER_LOCALS_START_INDEX, reserved + ""));

        return result;
    }

    CodeTree createFastAccessFieldInitializer(boolean allowUnsafe) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startStaticCall(types.BytecodeDSLAccess, "lookup").string("BytecodeRootNodesImpl.VISIBLE_TOKEN").string(Boolean.toString(allowUnsafe)).end();
        return b.build();
    }

    private CodeExecutableElement createIsCloningAllowed() {
        ExecutableElement executable = ElementUtils.findOverride(ElementUtils.findMethod(types.RootNode, "isCloningAllowed"), model.templateType);
        if (executable != null) {
            return null;
        }
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloningAllowed");
        ex.createBuilder().returnTrue();
        return ex;
    }

    private CodeExecutableElement createCloneUninitializedSupported() {
        ExecutableElement executable = ElementUtils.findOverride(ElementUtils.findMethod(types.RootNode, "isCloneUninitializedSupported"), model.templateType);
        if (executable != null && executable.getModifiers().contains(Modifier.FINAL)) {
            return null;
        }

        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "isCloneUninitializedSupported");
        ex.createBuilder().returnTrue();
        return ex;
    }

    private CodeExecutableElement createCloneUninitialized() {
        ExecutableElement executable = ElementUtils.findOverride(ElementUtils.findMethod(types.RootNode, "cloneUninitialized"), model.templateType);
        if (executable != null && executable.getModifiers().contains(Modifier.FINAL)) {
            return null;
        }
        CodeExecutableElement ex = GeneratorUtils.override(types.RootNode, "cloneUninitialized");

        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(this.asType(), "clone");
        b.startSynchronized("nodes");
        b.startAssign("clone").cast(this.asType()).string("this.copy()").end();
        b.statement("clone.clones = null");
        // The base copy method performs a shallow copy of all fields.
        // Some fields should be manually reinitialized to default values.
        b.statement("clone.bytecode = insert(this.bytecode.cloneUninitialized())");
        b.declaration(generic(types.BytecodeSupport_CloneReferenceList, this.asType()), "localClones", "this.clones");
        b.startIf().string("localClones == null").end().startBlock();
        b.startStatement().string("this.clones = localClones = ").startNew(generic(types.BytecodeSupport_CloneReferenceList, this.asType())).end().end();
        b.end();

        b.statement("localClones.add(clone)");
        b.end();

        emitFence(b);
        b.startReturn().string("clone").end();

        return ex;
    }

    private CodeExecutableElement createTranslateStackTraceElement() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "translateStackTraceElement", new String[]{"stackTraceElement"});
        if (ex.getModifiers().contains(Modifier.FINAL)) {
            // already overridden by the root node.
            return null;
        }
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startStaticCall(abstractBytecodeNode.asType(), "createStackTraceElement");
        b.string("stackTraceElement");
        b.end();
        b.end();

        return ex;
    }

    private CodeExecutableElement createCreateStackTraceElement() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(Object.class), "createStackTraceElement");
        ex.addParameter(new CodeVariableElement(types.TruffleStackTraceElement, "stackTraceElement"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startCall("createDefaultStackTraceElement");
        b.string("stackTraceElement");
        b.end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("bytecode.getSourceSection()").end();
        return ex;
    }

    private CodeExecutableElement createComputeSize() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "computeSize");

        Collection<InstructionModel> instructions = model.getInstructions();
        int[] lengths = instructions.stream() //
                        .mapToInt(InstructionModel::getInstructionLength) //
                        .sorted() //
                        .toArray();
        int midpoint = lengths.length / 2;

        int median;
        if (lengths.length % 2 == 0) {
            median = (lengths[midpoint - 1] + lengths[midpoint]) / 2;
        } else {
            median = lengths[midpoint];
        }

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.string("bytecode.bytecodes.length / ", Integer.toString(median), " /* median instruction length */");
        b.end();
        return ex;
    }

    private CodeExecutableElement createSneakyThrow() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(RuntimeException.class), "sneakyThrow");

        TypeMirror throwable = type(Throwable.class);
        CodeVariableElement param = new CodeVariableElement(throwable, "e");
        ex.addParameter(param);

        CodeTypeParameterElement tpE = new CodeTypeParameterElement(CodeNames.of("E"), throwable);
        ex.getTypeParameters().add(tpE);
        ex.addThrownType(tpE.asType());

        mergeSuppressWarnings(ex, "unchecked");
        CodeTreeBuilder b = ex.createBuilder();
        b.startThrow();
        b.cast(tpE.asType()).variable(param);
        b.end();

        return ex;
    }

    private CodeExecutableElement createAssertionFailed() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(AssertionError.class), "assertionFailed");
        CodeVariableElement param = new CodeVariableElement(type(String.class), "message");
        ex.addParameter(param);

        CodeTreeBuilder b = ex.createBuilder();
        b.startThrow().startNew(type(AssertionError.class));
        b.string("message");
        b.end(2);

        // AssertionError.<init> is blocklisted from NI code. Create it behind a boundary.
        return withTruffleBoundary(ex);
    }

    CodeExecutableElement withTruffleBoundary(CodeExecutableElement ex) {
        ex.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_TruffleBoundary));
        return ex;
    }

    private CodeExecutableElement createCreate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC, STATIC), generic(types.BytecodeRootNodes, model.templateType.asType()), "create");
        ex.addParameter(new CodeVariableElement(model.languageClass, "language"));
        ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        ex.addParameter(new CodeVariableElement(parserType, "parser"));

        addJavadoc(ex, String.format("""
                        Creates one or more bytecode nodes. This is the entrypoint for creating new {@link %s} instances.

                        @param language the Truffle language instance.
                        @param config indicates whether to parse metadata (e.g., source information).
                        @param parser the parser that invokes a series of builder instructions to generate bytecode.
                        """, model.getName()));

        CodeTreeBuilder b = ex.getBuilder();
        b.statement("return BYTECODE.create(language, config, parser)");
        return ex;
    }

    private CodeExecutableElement createInitializeTagIndexToClass() {
        DeclaredType classValue = context.getDeclaredType(ClassValue.class);
        TypeMirror classValueType = generic(classValue, type(Integer.class));

        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), classValueType,
                        "initializeTagMaskToClass");
        CodeTreeBuilder b = method.createBuilder();

        b.startStatement();
        b.string("return new ClassValue<>()").startBlock();
        b.string("protected Integer computeValue(Class<?> type) ").startBlock();

        boolean elseIf = false;
        int index = 0;
        for (TypeMirror tagClass : model.getProvidedTags()) {
            elseIf = b.startIf(elseIf);
            b.string("type == ").typeLiteral(tagClass);
            b.end().startBlock();
            b.startReturn().string(1 << index).end();
            b.end();
            index++;
        }
        createFailInvalidTag(b, "type");

        b.end();

        b.end();
        b.end();

        return method;
    }

    private void createFailInvalidTag(CodeTreeBuilder b, String tagLocal) {
        b.startThrow().startNew(type(IllegalArgumentException.class)).startCall("String.format").doubleQuote(
                        "Invalid tag specified. Tag '%s' not provided by language '" + ElementUtils.getQualifiedName(model.languageClass) + "'.").string(tagLocal, ".getName()").end().end().end();
    }

    private CodeExecutableElement createSerialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC), type(void.class), "serialize");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parserType, "parser"));
        method.addThrownType(type(IOException.class));

        addJavadoc(method, """
                        Serializes the bytecode nodes parsed by the {@code parser}.
                        All metadata (e.g., source info) is serialized (even if it has not yet been parsed).
                        <p>
                        Unlike the {@link BytecodeRootNodes#serialize} instance method, which replays builder
                        calls that were already validated during the original bytecode parse, this method
                        does <strong>not</strong> validate the builder calls performed by the {@code parser}.
                        Validation will happen (as usual) when the bytes are deserialized.
                        <p>
                        Additionally, this method cannot serialize field values that get set outside of the
                        parser, unlike the {@link BytecodeRootNodes#serialize} instance method, which has
                        access to the instances being serialized.

                        @param buffer the buffer to write the byte output to.
                        @param callback the language-specific serializer for constants in the bytecode.
                        @param parser the parser.
                        """);

        CodeTreeBuilder b = method.createBuilder();
        b.statement("BYTECODE.serialize(buffer, callback, parser)");
        return method;
    }

    private CodeExecutableElement createDeserialize() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PUBLIC, STATIC),
                        generic(types.BytecodeRootNodes, model.getTemplateType().asType()), "deserialize");
        method.addParameter(new CodeVariableElement(model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "input"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addThrownType(type(IOException.class));
        addJavadoc(method,
                        """
                                        Deserializes a byte sequence to bytecode nodes. The bytes must have been produced by a previous call to {@link #serialize}.").newLine()

                                        @param language the language instance.
                                        @param config indicates whether to deserialize metadata (e.g., source information).
                                        @param input A function that supplies the bytes to deserialize. This supplier must produce a new {@link DataInput} each time, since the bytes may be processed multiple times for reparsing.
                                        @param callback The language-specific deserializer for constants in the bytecode. This callback must perform the inverse of the callback that was used to {@link #serialize} the nodes to bytes.
                                        """);

        CodeTreeBuilder b = method.createBuilder();
        b.statement("return BYTECODE.deserialize(language, config, input, callback)");
        return method;
    }

    private CodeExecutableElement createGetBytecodeNode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNode, "getBytecodeNode");

        ex.getModifiers().remove(Modifier.DEFAULT);
        ex.getModifiers().add(Modifier.FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("bytecode").end();

        return ex;
    }

    private CodeExecutableElement createGetBytecodeNodeImpl() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), abstractBytecodeNode.asType(), "getBytecodeNodeImpl");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("bytecode").end();
        return ex;
    }

    private CodeExecutableElement createInvalidate() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "invalidate");
        ex.addParameter(new CodeVariableElement(type(String.class), "reason"));
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("this.reportReplace(this, this, reason)");
        return ex;
    }

    private CodeExecutableElement createGetBytecodeRootNode() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), this.asType(), "getBytecodeRootNodeImpl");
        ex.addParameter(new CodeVariableElement(type(int.class), "index"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().cast(this.asType()).string("this.nodes.getNode(index)").end();
        return ex;
    }

    private CodeExecutableElement createGetRootNodes() {
        CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeRootNode, "getRootNodes");
        ex.setReturnType(generic(types.BytecodeRootNodes, model.templateType.asType()));
        ex.getModifiers().remove(Modifier.DEFAULT);
        ex.getModifiers().add(Modifier.FINAL);

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("this.nodes").end();

        return ex;
    }

    private CodeExecutableElement createTransitionToCached() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "transitionToCached");
        if (model.enableUncachedInterpreter) {
            ex.addParameter(new CodeVariableElement(types.Frame, "frame"));
            ex.addParameter(new CodeVariableElement(type(int.class), "bci"));
        }
        CodeTreeBuilder b = ex.createBuilder();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.declaration(abstractBytecodeNode.asType(), "oldBytecode");
        b.declaration(abstractBytecodeNode.asType(), "newBytecode");
        b.startDoBlock();
        b.statement("oldBytecode = this.bytecode");
        b.startAssign("newBytecode").startCall("insert").startCall("oldBytecode.toCached");
        if (model.usesBoxingElimination()) {
            b.string("this.numLocals");
        }
        b.end(3);

        if (model.enableUncachedInterpreter) {
            b.startIf().string("bci > 0").end().startBlock();
            b.lineComment("initialize local tags");
            b.declaration(type(int.class), "localCount", "newBytecode.getLocalCount(bci)");
            b.startFor().string("int localOffset = 0; localOffset < localCount; localOffset++").end().startBlock();
            b.statement("newBytecode.setLocalValue(bci, frame, localOffset, newBytecode.getLocalValue(bci, frame, localOffset))");
            b.end();
            b.end();
        }

        emitFence(b);
        b.startIf().string("oldBytecode == newBytecode").end().startBlock();
        b.returnStatement();
        b.end();
        b.end().startDoWhile().startCall("!BYTECODE_UPDATER", "compareAndSet").string("this").string("oldBytecode").string("newBytecode").end().end();
        return ex;
    }

    private CodeExecutableElement createUpdateBytecode() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), abstractBytecodeNode.asType(), "updateBytecode");

        for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
            ex.addParameter(new CodeVariableElement(e.asType(), e.getSimpleName().toString() + "_"));
        }
        ex.addParameter(new CodeVariableElement(type(CharSequence.class), "reason"));
        if (model.hasYieldOperation()) {
            ex.addParameter(new CodeVariableElement(type(int[].class), "continuations"));
            ex.addParameter(new CodeVariableElement(type(int.class), "continuationsIndex"));
        }

        CodeTreeBuilder b = ex.createBuilder();
        b.tree(GeneratorUtils.createNeverPartOfCompilation());
        b.declaration(abstractBytecodeNode.asType(), "oldBytecode");
        b.declaration(abstractBytecodeNode.asType(), "newBytecode");
        b.startDoBlock();
        b.statement("oldBytecode = this.bytecode");
        b.startStatement();
        b.string("newBytecode = ").startCall("insert").startCall("oldBytecode", "update");
        for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
            b.string(e.getSimpleName().toString() + "_");
        }
        b.end().end(); // call, call
        b.end(); // statement

        b.startIf().string("bytecodes_ == null").end().startBlock();
        b.lineComment("When bytecode doesn't change, nodes are reused and should be re-adopted.");
        b.statement("newBytecode.adoptNodesAfterUpdate()");
        b.end();
        emitFence(b);
        b.end().startDoWhile().startCall("!BYTECODE_UPDATER", "compareAndSet").string("this").string("oldBytecode").string("newBytecode").end().end();
        b.newLine();

        if (model.isBytecodeUpdatable()) {
            b.startIf().string("bytecodes_ != null").end().startBlock();
            b.startStatement().startCall("oldBytecode.invalidate");
            b.string("newBytecode");
            b.string("reason");
            b.end(2);
            b.end();

            if (model.hasYieldOperation()) {
                if (model.enableInstructionTracing) {
                    b.declaration(type(int.class), "oldConstantOffset", "oldBytecode.isInstructionTracingEnabled() ? 1 : 0");
                    b.declaration(type(int.class), "newConstantOffset", "newBytecode.isInstructionTracingEnabled() ? 1 : 0");
                }

                // We need to patch the BytecodeNodes for continuations.
                b.startFor().string("int i = 0; i < continuationsIndex; i = i + CONTINUATION_LENGTH").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuations[i + CONTINUATION_OFFSET_CPI]");
                b.declaration(type(int.class), "continuationBci", "continuations[i + CONTINUATION_OFFSET_BCI]");

                if (model.enableInstructionTracing) {
                    b.lineComment("The constant offset is 1 with instruction tracing enabled. See INSTRUCTION_TRACER_CONSTANT_INDEX.");
                    b.lineComment("We need to align constant indices for the continuation root node updates.");
                    b.declaration(type(int.class), "oldConstantPoolIndex", "constantPoolIndex - newConstantOffset + oldConstantOffset");
                } else {
                    b.declaration(type(int.class), "oldConstantPoolIndex", "constantPoolIndex");
                }

                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                b.cast(continuationRootNodeImpl.asType());
                b.string("oldBytecode.constants[oldConstantPoolIndex]");
                b.end();

                b.startAssert().string("oldBytecode.constants[oldConstantPoolIndex] == newBytecode.constants[constantPoolIndex]").end();

                b.lineComment("locations may become null if they are no longer reachable.");
                b.declaration(types.BytecodeLocation, "newLocation", "continuationBci == -1 ? null : newBytecode.getBytecodeLocation(continuationBci)");

                b.startStatement().startCall("continuationRootNode", "updateBytecodeLocation");
                b.string("newLocation");
                b.string("oldBytecode");
                b.string("newBytecode");
                b.string("reason");
                b.end(2);
                b.end();

                b.end(); // for
            }
        }

        b.startAssert().startStaticCall(type(Thread.class), "holdsLock").string("this.nodes").end().end();
        b.statement("var cloneReferences = this.clones");
        b.startIf().string("cloneReferences != null").end().startBlock();
        b.startStatement();
        b.string("cloneReferences.forEach((clone) -> ").startBlock();

        b.startStatement();

        b.startCall("clone", "updateBytecode");
        for (VariableElement var : ex.getParameters()) {
            switch (var.getSimpleName().toString()) {
                case "bytecodes_":
                    b.startGroup();
                    b.string("bytecodes_ != null ? ");
                    b.startCall("unquickenBytecode");
                    b.string(var.getSimpleName().toString());
                    b.end();
                    b.string(" : null");
                    b.end();
                    break;
                case "tagRoot_":
                    b.startGroup();
                    b.string("tagRoot_ != null ? ");
                    b.cast(tagRootNode.asType());
                    b.string("tagRoot_.deepCopy() : null");
                    b.end();
                    break;
                default:
                    b.string(var.getSimpleName().toString());
                    break;
            }
        }

        b.end();
        b.end();

        b.end(); // block
        b.string(")");
        b.end(); // statement
        b.end();

        b.startReturn().string("newBytecode").end();

        return ex;
    }

    private CodeTypeElement createBytecodeConfigEncoderClass() {
        CodeTreeBuilder b;
        CodeTypeElement type = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeConfigEncoderImpl");
        type.setSuperClass(types.BytecodeConfigEncoder);

        CodeExecutableElement constructor = type.add(new CodeExecutableElement(Set.of(PRIVATE), null, type.getSimpleName().toString()));
        b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();

        type.add(createEncodeInstrumentation());
        type.add(createDecode1());
        type.add(createDecode2(type));

        CodeExecutableElement encodeTag = GeneratorUtils.override(types.BytecodeConfigEncoder, "encodeTag", new String[]{"c"});
        b = encodeTag.createBuilder();

        if (model.getProvidedTags().isEmpty()) {
            createFailInvalidTag(b, "c");
        } else {
            b.startReturn().string("((long) CLASS_TO_TAG_MASK.get(c)) << " + TAG_OFFSET).end().build();
        }

        type.add(encodeTag);

        CodeVariableElement configEncoderVar = type.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type.asType(), "INSTANCE"));
        configEncoderVar.createInitBuilder().startNew(type.asType()).end();

        return type;
    }

    private CodeExecutableElement createDecode1() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.STATIC), type(long.class), "decode");
        ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startCall("decode").string("getEncoder(config)").string("getEncoding(config)").end();
        b.end();
        return ex;
    }

    @SuppressFBWarnings(value = "BSHIFT_WRONG_ADD_PRIORITY", justification = "the shift priority is expected. FindBugs false positive.")
    private CodeExecutableElement createDecode2(CodeTypeElement type) {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.STATIC), type(long.class), "decode");
        ex.addParameter(new CodeVariableElement(types.BytecodeConfigEncoder, "encoder"));
        ex.addParameter(new CodeVariableElement(type(long.class), "encoding"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("encoder != null && encoder  != ").staticReference(type.asType(), "INSTANCE").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("Encoded config is not compatible with this bytecode node.").end().end();
        b.end();

        long mask = 1L;
        if (model.getInstrumentationsCount() > MAX_INSTRUMENTATIONS) {
            throw new AssertionError("Unsupported instrumentation size.");
        }
        if (model.getProvidedTags().size() > MAX_TAGS) {
            throw new AssertionError("Unsupported instrumentation size.");
        }

        if (model.traceInstructionInstrumentationIndex != -1) {
            mask |= 1L << (INSTRUMENTATION_OFFSET + model.traceInstructionInstrumentationIndex);
        }

        for (int i = 0; i < model.getInstrumentations().size(); i++) {
            mask |= 1L << (INSTRUMENTATION_OFFSET + i);
        }

        for (int i = 0; i < model.getProvidedTags().size(); i++) {
            mask |= 1L << (TAG_OFFSET + i);
        }

        b.startReturn().string("(encoding & 0x" + Long.toHexString(mask) + "L)").end();
        return ex;
    }

    private CodeExecutableElement createEncodeInstrumentation() {
        CodeExecutableElement encodeInstrumentation = GeneratorUtils.override(types.BytecodeConfigEncoder, "encodeInstrumentation", new String[]{"c"});
        CodeTreeBuilder b = encodeInstrumentation.createBuilder();

        if (model.hasInstrumentations()) {
            b.declaration("long", "encoding", "0L");
            boolean elseIf = b.startIf(false);
            b.string("c == ").typeLiteral(types.InstructionTracer);
            b.end().startBlock();
            if (model.enableInstructionTracing) {
                b.statement("encoding |= 0x" + Integer.toHexString(1 << model.traceInstructionInstrumentationIndex));
            } else {
                b.lineComment("Instruction tracing disabled");
            }
            b.end();
            for (CustomOperationModel customOperation : model.getInstrumentations()) {
                elseIf = b.startIf(elseIf);
                b.string("c == ").typeLiteral(customOperation.operation.instruction.nodeType.asType());
                b.end().startBlock();
                b.statement("encoding |= 0x" + Integer.toHexString(1 << customOperation.operation.instrumentationIndex));
                b.end();
            }
            b.startElseBlock();
        }
        b.startThrow().startNew(type(IllegalArgumentException.class)).startCall("String.format").doubleQuote(
                        "Invalid instrumentation specified. Instrumentation '%s' does not exist or is not an instrumentation for '" + ElementUtils.getQualifiedName(model.templateType) + "'. " +
                                        "Instrumentations can be specified using the @Instrumentation annotation.").string("c.getName()").end().end().end();
        if (model.hasInstrumentations()) {
            b.end(); // else
            b.startReturn().string("encoding << 1").end();
        }
        return encodeInstrumentation;
    }

    private CodeExecutableElement createIsInstrumentable() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "isInstrumentable");
        CodeTreeBuilder b = ex.createBuilder();
        if (model.enableTagInstrumentation) {
            b.statement("return true");
        } else {
            b.statement("return false");
        }
        return ex;
    }

    private CodeExecutableElement createPrepareForCall() {
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "prepareForCall");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("!this.nodes.isParsed()").end().startBlock();
        emitThrowIllegalStateException(ex, b, "A call target cannot be created until bytecode parsing completes. Request a call target after the parse is complete instead.");
        b.end();

        b.startStatement();
        b.startCall("BYTECODE.prepareForCall");
        b.startCall("getLanguage").typeLiteral(model.languageClass).end();
        b.string("this");
        b.end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createPrepareForInstrumentation() {
        if (!model.enableTagInstrumentation) {
            return null;
        }
        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "prepareForInstrumentation", new String[]{"materializedTags"});
        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(types.BytecodeConfig_Builder, "b", "newConfigBuilder()");
        b.lineComment("Sources are always needed for instrumentation.");
        b.statement("b.addSource()");

        b.startFor().type(type(Class.class)).string(" tag : materializedTags").end().startBlock();
        b.statement("b.addTag((Class<? extends Tag>) tag)");
        b.end();

        b.statement("getRootNodes().update(b.build())");
        return ex;
    }

    private CodeExecutableElement createPrepareForCompilation() {
        if (!model.enableUncachedInterpreter) {
            return null;
        }

        CodeExecutableElement ex = overrideImplementRootNodeMethod(model, "prepareForCompilation", new String[]{"rootCompilation", "compilationTier", "lastTier"});
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();

        b.startReturn();
        // Disable compilation for the uncached interpreter.
        b.string("bytecode.getTier() != ").staticReference(types.BytecodeTier, "UNCACHED");

        ExecutableElement parentImpl = ElementUtils.findMethodInClassHierarchy(ElementUtils.findMethod(types.RootNode, "prepareForCompilation", 3), model.templateType);
        if (parentImpl != null && !parentImpl.getModifiers().contains(ABSTRACT)) {
            // Delegate to the parent impl.
            b.string(" && ").startCall("super.prepareForCompilation").variables(ex.getParameters()).end();
        }
        b.end();
        return ex;
    }

    private CodeExecutableElement createEncodeTags() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "encodeTags");
        ex.addParameter(new CodeVariableElement(arrayOf(type(Class.class)), "tags"));
        ex.setVarArgs(true);
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("tags == null").end().startBlock();
        b.statement("return 0");
        b.end();

        if (model.getProvidedTags().isEmpty()) {
            b.startIf().string("tags.length != 0").end().startBlock();
            createFailInvalidTag(b, "tags[0]");
            b.end();
            b.startReturn().string("0").end();
        } else {
            b.statement("int tagMask = 0");
            b.startFor().string("Class<?> tag : tags").end().startBlock();
            b.statement("tagMask |= CLASS_TO_TAG_MASK.get(tag)");
            b.end();
            b.startReturn().string("tagMask").end();
        }

        return ex;
    }

    boolean cloneUninitializedNeedsUnquickenedBytecode() {
        // If the node supports BE/quickening, cloneUninitialized should unquicken the bytecode.
        // Uncached nodes don't rewrite bytecode, so we only need to unquicken if cached.
        return (model.usesBoxingElimination() || model.enableQuickening);
    }

    private CodeExecutableElement createUnquickenBytecode() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(type(byte.class)), "unquickenBytecode");
        ex.addParameter(new CodeVariableElement(arrayOf(type(byte.class)), "original"));

        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(arrayOf(type(byte.class)), "copy", "Arrays.copyOf(original, original.length)");

        Map<Boolean, List<InstructionModel>> partitionedByIsQuickening = model.getInstructions().stream() //
                        .sorted((e1, e2) -> e1.name.compareTo(e2.name)).collect(Collectors.partitioningBy(InstructionModel::isQuickening));

        List<Entry<Integer, List<InstructionModel>>> regularGroupedByLength = partitionedByIsQuickening.get(false).stream() //
                        .collect(deterministicGroupingBy(InstructionModel::getInstructionLength)).entrySet() //
                        .stream().sorted(Comparator.comparing(entry -> entry.getKey())) //
                        .toList();

        List<Entry<InstructionModel, List<InstructionModel>>> quickenedGroupedByQuickeningRoot = partitionedByIsQuickening.get(true).stream() //
                        .collect(deterministicGroupingBy(InstructionModel::getQuickeningRoot)).entrySet() //
                        .stream().sorted(Comparator.comparing((Entry<InstructionModel, List<InstructionModel>> entry) -> {
                            InstructionKind kind = entry.getKey().kind;
                            return kind == InstructionKind.CUSTOM || kind == InstructionKind.CUSTOM_SHORT_CIRCUIT;
                        }).thenComparing(entry -> entry.getKey().getInstructionLength())) //
                        .toList();

        b.declaration(type(int.class), "bci", "0");

        b.startWhile().string("bci < copy.length").end().startBlock();
        b.startSwitch().tree(readInstruction("copy", "bci")).end().startBlock();

        for (var quickenedGroup : quickenedGroupedByQuickeningRoot) {
            InstructionModel quickeningRoot = quickenedGroup.getKey();
            List<InstructionModel> instructions = quickenedGroup.getValue();
            int instructionLength = instructions.get(0).getInstructionLength();
            for (InstructionModel instruction : instructions) {
                if (instruction.getInstructionLength() != instructionLength) {
                    throw new AssertionError("quickened group has multiple different instruction lengths");
                }
                b.startCase().tree(createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();

            b.statement(writeInstruction("copy", "bci", createInstructionConstant(quickeningRoot)));
            b.startStatement().string("bci += ").string(instructionLength).end();
            b.statement("break");
            b.end();
        }

        for (var regularGroup : regularGroupedByLength) {
            int instructionLength = regularGroup.getKey();
            List<InstructionModel> instructions = regularGroup.getValue();
            for (InstructionModel instruction : instructions) {
                b.startCase().tree(createInstructionConstant(instruction)).end();
            }
            b.startCaseBlock();
            b.startStatement().string("bci += ").string(instructionLength).end();
            b.statement("break");
            b.end();
        }

        b.end(); // switch
        b.end(); // while

        b.startReturn();
        b.string("copy");
        b.end();
        return ex;
    }

    static String createApplyQuickeningName(TypeMirror type) {
        return "applyQuickening" + ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type));
    }

    static String createIsQuickeningName(TypeMirror type) {
        return "isQuickening" + ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(type));
    }

    BytecodeDSLModel getModel() {
        return model;
    }

    ProcessorContext getContext() {
        return context;
    }

    CodeTypeElement getAbstractBytecodeNode() {
        return abstractBytecodeNode;
    }

    CodeTypeElement getContinuationRootNodeImpl() {
        return continuationRootNodeImpl;
    }

    private ExecutableElement createCachedExecute(BytecodeDSLNodeGeneratorPlugs plugs, FlatNodeGenFactory factory, CodeTypeElement el, InstructionModel instruction) {
        plugs.setInstruction(instruction);
        TypeMirror returnType = instruction.signature.returnType;
        CodeExecutableElement executable = new CodeExecutableElement(Set.of(PRIVATE),
                        returnType, executeMethodName(instruction),
                        new CodeVariableElement(types.VirtualFrame, "frameValue"));

        if (instruction.isEpilogExceptional()) {
            executable.addParameter(new CodeVariableElement(types.AbstractTruffleException, "ex"));
        }

        if (hasUnexpectedExecuteValue(instruction)) {
            executable.getThrownTypes().add(types.UnexpectedResultException);
            lookupExpectMethod(instruction.getQuickeningRoot().signature.returnType, returnType);
        }

        List<SpecializationData> specializations;
        boolean skipStateChecks;
        if (instruction.filteredSpecializations == null) {
            specializations = instruction.nodeData.getReachableSpecializations();
            skipStateChecks = false;
        } else {
            specializations = instruction.filteredSpecializations;
            /*
             * If specializations are filtered we know we know all of them are active at the same
             * time, so we can skip state checks.
             */
            skipStateChecks = specializations.size() == 1;
        }
        CodeExecutableElement element = factory.createExecuteMethod(el, executable, specializations, skipStateChecks && instruction.isQuickening());
        element.findParameter("frameValue").setType(types.FrameWithoutBoxing);
        return element;
    }

    CodeExecutableElement lookupExpectMethod(TypeMirror currentType, TypeMirror targetType) {
        if (ElementUtils.isVoid(targetType) || ElementUtils.isVoid(currentType)) {
            throw new AssertionError("Invalid target type " + targetType);
        }

        CodeExecutableElement expectMethod = expectMethods.get(targetType);
        if (expectMethod == null) {
            expectMethod = TypeSystemCodeGenerator.createExpectMethod(Modifier.PRIVATE, model.typeSystem, currentType, targetType);
            this.add(expectMethod);
            expectMethods.put(targetType, expectMethod);
        }
        return expectMethod;
    }

    static String executeMethodName(InstructionModel instruction) {
        return "execute" + instruction.getQualifiedQuickeningName();
    }

    private void serializationWrapException(CodeTreeBuilder b, Runnable r) {
        b.startTryBlock();
        r.run();
        b.end().startCatchBlock(type(IOException.class), "ex");
        b.startThrow().startNew(type(IOError.class)).string("ex").end(2);
        b.end();
    }

    /**
     * The template class may directly (or indirectly, through a parent) widen the visibility of a
     * RootNode method. Our override must be at least as visible.
     *
     * (BytecodeRootNode methods are not an issue, because interface methods are all public.)
     */

    static CodeExecutableElement overrideImplementRootNodeMethod(BytecodeDSLModel model, String name) {
        return overrideImplementRootNodeMethod(model, name, new String[0]);
    }

    static CodeExecutableElement overrideImplementRootNodeMethod(BytecodeDSLModel model, String name, String[] parameterNames) {
        return overrideImplementRootNodeMethod(model, name, parameterNames, new TypeMirror[parameterNames.length]);
    }

    static CodeExecutableElement overrideImplementRootNodeMethod(BytecodeDSLModel model, String name, String[] parameterNames, TypeMirror[] parameterTypes) {
        TruffleTypes types = model.getContext().getTypes();
        CodeExecutableElement result = GeneratorUtils.override(types.RootNode, name, parameterNames, parameterTypes);

        if (result == null) {
            throw new IllegalArgumentException("Method with name " + name + " and types " + Arrays.toString(parameterTypes) + " not found.");
        }

        // If the RootNode method is already public, nothing to do.
        if (ElementUtils.getVisibility(result.getModifiers()) == Modifier.PUBLIC) {
            return result;
        }

        // Otherwise, in order to override it in user code, it must be protected. The only widening
        // we need to worry about is from protected -> public.
        if (ElementUtils.getVisibility(result.getModifiers()) != Modifier.PROTECTED) {
            throw new AssertionError("Unexpected visibility of root node override.");
        }

        ExecutableElement override = ElementUtils.findInstanceMethod(model.templateType, name, parameterTypes);
        if (override != null && ElementUtils.getVisibility(override.getModifiers()) == Modifier.PUBLIC) {
            result.setVisibility(Modifier.PUBLIC);
            return result;
        }

        for (TypeElement parent : ElementUtils.getSuperTypes(model.templateType)) {
            override = ElementUtils.findInstanceMethod(parent, name, parameterTypes);
            if (override == null) {
                continue;
            }
            if (ElementUtils.getVisibility(override.getModifiers()) == Modifier.PUBLIC) {
                result.setVisibility(Modifier.PUBLIC);
                return result;
            }
        }

        return result;
    }

    /**
     * In order to compile properly, SerializationRootNode must implement any abstract methods of
     * the template class. Assuming the generated root node compiles properly, it must implement
     * these same methods, and we can ensure SerializationRootNode will compile by creating stubs
     * for each of the generated root node's public/protected instance methods.
     *
     * (Typically, the only abstract method is BytecodeRootNode#execute, but the template class
     * *could* declare other abstract methods that are coincidentally implemented by the generated
     * root node, like getSourceSection).
     */
    private void addMethodStubsToSerializationRootNode() {
        for (ExecutableElement method : ElementUtils.getOverridableMethods(this)) {
            CodeExecutableElement stub = CodeExecutableElement.cloneNoAnnotations(method);
            addOverride(stub);
            CodeTreeBuilder b = stub.createBuilder();
            emitThrowIllegalStateException(stub, b, "method should not be called");
            b.end(2);
            serializationRootNode.add(stub);
        }
    }

    static Collection<List<InstructionModel>> groupInstructionsByLength(Collection<InstructionModel> models) {
        return models.stream().sorted(Comparator.comparingInt((i) -> i.getInstructionLength())).collect(deterministicGroupingBy((m) -> m.getInstructionLength())).values();
    }

    void emitQuickening(CodeTreeBuilder b, String node, String bc, String bci, CodeTree oldInstruction, CodeTree newInstruction) {
        boolean overridesOnQuicken = model.overridesBytecodeDebugListenerMethod("onQuicken");
        if (overridesOnQuicken && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            emitParseInstruction(b, node, bci, readInstruction(bc, bci));
            b.end();
        }

        b.statement(writeInstruction(bc, bci, newInstruction));

        if (overridesOnQuicken) {
            b.startStatement();
            b.startCall(node, "getRoot().onQuicken");
            if (oldInstruction == null) {
                b.string("oldInstruction");
            } else {
                emitParseInstruction(b, node, bci, oldInstruction);
            }
            emitParseInstruction(b, node, bci, newInstruction);
            b.end().end();

            if (oldInstruction == null) {
                b.end(); // block
            }
        }

    }

    void emitInvalidateInstruction(CodeTreeBuilder b, String node, String bc, String bci, CodeTree oldInstruction, CodeTree newInstruction) {
        boolean overridesOnInvalidateInstruction = model.overridesBytecodeDebugListenerMethod("onInvalidateInstruction");
        if (overridesOnInvalidateInstruction && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            emitParseInstruction(b, node, bci, readInstruction(bc, bci));
            b.end();
        }

        b.statement(writeInstruction(bc, bci, newInstruction));

        if (overridesOnInvalidateInstruction) {
            b.startStatement();
            b.startCall(node, "getRoot().onInvalidateInstruction");
            if (oldInstruction == null) {
                b.string("oldInstruction");
            } else {
                emitParseInstruction(b, node, bci, oldInstruction);
            }
            emitParseInstruction(b, node, bci, newInstruction);
            b.end().end();

            if (oldInstruction == null) {
                b.end(); // block
            }
        }

    }

    void emitQuickening(CodeTreeBuilder b, String node, String bc, String bci, String oldInstruction, String newInstruction) {
        emitQuickening(b, node, bc, bci, oldInstruction != null ? CodeTreeBuilder.singleString(oldInstruction) : null, CodeTreeBuilder.singleString(newInstruction));
    }

    void emitQuickeningOperand(CodeTreeBuilder b, String node, String bc,
                    String baseBci,
                    String baseInstruction,
                    int operandIndex,
                    String operandBci,
                    String oldInstruction, String newInstruction) {
        boolean overridesOnQuickenOperand = model.overridesBytecodeDebugListenerMethod("onQuickenOperand");
        if (overridesOnQuickenOperand && oldInstruction == null) {
            b.startBlock();
            b.startDeclaration(instructionImpl.asType(), "oldInstruction");
            emitParseInstruction(b, node, operandBci, readInstruction(bc, operandBci));
            b.end();
        }

        b.statement(writeInstruction(bc, operandBci, newInstruction));

        if (overridesOnQuickenOperand) {
            b.startStatement();
            b.startCall(node, "getRoot().onQuickenOperand");
            CodeTree base = baseInstruction == null ? readInstruction(bc, baseBci) : CodeTreeBuilder.singleString(baseInstruction);
            emitParseInstruction(b, node, baseBci, base);
            b.string(operandIndex);
            if (oldInstruction == null) {
                b.string("oldInstruction");
            } else {
                emitParseInstruction(b, node, operandBci, CodeTreeBuilder.singleString(oldInstruction));
            }
            emitParseInstruction(b, node, operandBci, CodeTreeBuilder.singleString(newInstruction));
            b.end().end();

            if (oldInstruction == null) {
                b.end(); // block
            }
        }

    }

    void emitOnSpecialize(CodeTreeBuilder b, String node, String bci, CodeTree operand, String specializationName) {
        if (model.overridesBytecodeDebugListenerMethod("onSpecialize")) {
            b.startStatement().startCall(node, "getRoot().onSpecialize");
            emitParseInstruction(b, node, bci, operand);
            b.doubleQuote(specializationName);
            b.end().end();
        }
    }

    CodeTreeBuilder emitParseInstruction(CodeTreeBuilder b, String node, String bci, CodeTree operand) {
        b.startNew(instructionImpl.asType()).string(node).string(bci).tree(operand).end();
        return b;
    }

    static boolean hasUnexpectedExecuteValue(InstructionModel instr) {
        return ElementUtils.needsCastTo(instr.getQuickeningRoot().signature.returnType, instr.signature.returnType);
    }

    public static <T, K> Collector<T, ?, Map<K, List<T>>> deterministicGroupingBy(Function<? super T, ? extends K> classifier) {
        return Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList());
    }

    /**
     * Custom instructions are generated from Operations and OperationProxies. During parsing we
     * convert these definitions into Nodes for which {@link FlatNodeGenFactory} understands how to
     * generate specialization code. We clean up the result (removing unnecessary fields/methods,
     * fixing up types, etc.) here.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processCachedNode(CodeTypeElement el) {
        // The parser injects @NodeChildren of dummy type "C". We do not directly execute the
        // children (the plugs rewire child executions to stack loads), so we can remove them.
        for (VariableElement fld : ElementFilter.fieldsIn(el.getEnclosedElements())) {
            if (ElementUtils.getQualifiedName(fld.asType()).equals("C")) {
                el.getEnclosedElements().remove(fld);
            }
        }

        for (ExecutableElement ctor : ElementFilter.constructorsIn(el.getEnclosedElements())) {
            el.getEnclosedElements().remove(ctor);
        }

        for (ExecutableElement method : ElementFilter.methodsIn(el.getEnclosedElements())) {
            String name = method.getSimpleName().toString();
            if (name.equals("executeAndSpecialize")) {
                continue;
            }
            if (name.startsWith("execute")) {
                el.getEnclosedElements().remove(method);
            }
        }

        if (BytecodeRootNodeElement.this.model.enableUncachedInterpreter) {
            // We do not need any other execute methods on the Uncached class.
            for (CodeTypeElement type : (List<CodeTypeElement>) (List<?>) ElementFilter.typesIn(el.getEnclosedElements())) {
                if (type.getSimpleName().toString().equals("Uncached")) {
                    type.setSuperClass(types.Node);
                    for (ExecutableElement ctor : ElementFilter.methodsIn(type.getEnclosedElements())) {
                        String name = ctor.getSimpleName().toString();
                        if (name.startsWith("execute") && !name.equals("executeUncached")) {
                            type.getEnclosedElements().remove(ctor);
                        }
                    }
                }
            }
        }
    }

    void emitFence(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(type(VarHandle.class), "storeStoreFence").end(2);
    }

    static void emitThrowAssertionError(CodeTreeBuilder b, String reason) {
        b.startThrow().startCall("assertionFailed").string(reason).end(2);
    }

    void emitThrowEncodingException(CodeTreeBuilder b, String reason) {
        b.startThrow().startStaticCall(types.BytecodeEncodingException, "create");
        b.string(reason);
        b.end(2);
    }

    /**
     * Unfortunately HotSpot does not JIT methods bigger than {@link #JAVA_JIT_BYTECODE_LIMIT}
     * bytecodes. So we need to split up the instructions.
     */
    static List<List<InstructionModel>> partitionInstructions(Collection<InstructionModel> originalInstructions) {
        // filtered instructions
        List<InstructionModel> instructions = originalInstructions.stream().toList();

        int instructionCount = instructions.size();
        int estimatedSize = ESTIMATED_BYTECODE_FOOTPRINT + (instructionCount * ESTIMATED_CUSTOM_INSTRUCTION_SIZE);

        if (estimatedSize > JAVA_JIT_BYTECODE_LIMIT) {
            List<InstructionModel> topLevelInstructions = new ArrayList<>();
            List<InstructionModel> partitionableInstructions = new ArrayList<>();
            for (InstructionModel instruction : instructions) {
                if (instruction.kind != InstructionKind.CUSTOM || instruction.operation.kind == OperationKind.CUSTOM_YIELD) {
                    topLevelInstructions.add(instruction);
                } else {
                    partitionableInstructions.add(instruction);
                }
            }

            int groupCount = (int) partitionableInstructions.stream().map(InstructionGroup::new).distinct().count();

            int instructionsPerPartition = JAVA_JIT_BYTECODE_LIMIT / ESTIMATED_EXTRACTED_INSTRUCTION_SIZE;

            // Estimate the space consumed by built-ins (which always go in the main partition).
            int spaceUsedForBuiltins = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_CUSTOM_INSTRUCTION_SIZE * topLevelInstructions.size());
            int spaceUsedForDispatching = GROUP_DISPATCH_SIZE * groupCount;
            // Any remaining space in the main partition can be used for custom instructions.
            int spaceLeftForCustom = Math.max(0, JAVA_JIT_BYTECODE_LIMIT - spaceUsedForBuiltins - spaceUsedForDispatching);
            int customInstructionsInTopLevelPartition = spaceLeftForCustom / ESTIMATED_CUSTOM_INSTRUCTION_SIZE;

            topLevelInstructions.addAll(partitionableInstructions.subList(0, Math.min(partitionableInstructions.size(), customInstructionsInTopLevelPartition)));
            List<InstructionModel> instructionsToPartition = partitionableInstructions.subList(customInstructionsInTopLevelPartition, partitionableInstructions.size());
            List<List<InstructionModel>> partitions = new ArrayList<>();
            partitions.add(topLevelInstructions);
            for (int i = 0; i < instructionsToPartition.size(); i += instructionsPerPartition) {
                partitions.add(instructionsToPartition.subList(i, Math.min(i + instructionsPerPartition, instructionsToPartition.size())));
            }
            return partitions;
        } else {
            return List.of(instructions);
        }
    }

    record InstructionGroup(int stackEffect, int instructionLength) {
        InstructionGroup(InstructionModel instr) {
            this(instr.getStackEffect(), instr.getInstructionLength());
        }
    }

    static void emitThrowIllegalArgumentException(CodeTreeBuilder b, String reasonString) {
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(ProcessorContext.getInstance().getType(IllegalArgumentException.class));
        if (reasonString != null) {
            b.doubleQuote(reasonString);
        }
        b.end(2);
    }

    static void emitThrowIllegalStateException(CodeExecutableElement method, CodeTreeBuilder b, String reasonString) {
        GeneratorUtils.addBoundaryOrTransferToInterpreter(method, b);
        b.startThrow().startNew(ProcessorContext.getInstance().getType(IllegalStateException.class));
        if (reasonString != null) {
            b.doubleQuote(reasonString);
        }
        b.end(2);
    }

    static void addJavadoc(CodeElement<?> element, String contents) {
        addJavadoc(element, Arrays.asList(contents.split("\n")));
    }

    private static void addJavadoc(CodeElement<?> element, List<String> lines) {
        CodeTreeBuilder b = element.createDocBuilder();
        b.startJavadoc();
        for (String line : lines) {
            if (line.isBlank()) {
                b.string(" "); // inject a space so that empty lines get *'s
            } else {
                b.string(line);
            }
            b.newLine();
        }
        b.end();
    }

    CodeAnnotationMirror createExplodeLoopAnnotation(String kind) {
        CodeAnnotationMirror explodeLoop = new CodeAnnotationMirror(types.ExplodeLoop);
        if (kind != null) {
            TypeElement loopExplosionKind = ElementUtils.castTypeElement(types.ExplodeLoop_LoopExplosionKind);
            Optional<Element> enumValue = ElementUtils.getEnumValues(loopExplosionKind).stream().filter(
                            value -> value.getSimpleName().contentEquals(kind)).findFirst();
            if (enumValue.isEmpty()) {
                throw new IllegalArgumentException(String.format("Unknown enum value for %s: %s", loopExplosionKind.getSimpleName(), kind));
            }
            CodeAnnotationValue value = new CodeAnnotationValue(enumValue.get());
            explodeLoop.setElementValue("kind", value);

        }
        return explodeLoop;
    }

    CodeTree createInstructionConstant(InstructionModel instr) {
        return CodeTreeBuilder.createBuilder().staticReference(instructionsElement.asType(), instr.getConstantName()).build();
    }

    CodeTree createOperationConstant(OperationModel op) {
        return CodeTreeBuilder.createBuilder().staticReference(operationsElement.asType(), op.getConstantName()).build();
    }

    static String safeCastShort(String value) {
        return String.format("safeCastShort(%s)", value);
    }

    String localFrame() {
        return model.hasYieldOperation() ? "localFrame" : "frame";
    }

    // Helpers to generate common strings
    static CodeTree readInstruction(String bc, String bci) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("BYTES", "getShort");
        b.string(bc);
        b.string(bci);
        b.end();
        return b.build();
    }

    static CodeTree writeInstruction(String bc, String bci, String value) {
        return writeInstruction(bc, bci, CodeTreeBuilder.singleString(value));
    }

    static CodeTree writeInstruction(String bc, String bci, CodeTree value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("BYTES", "putShort");
        b.string(bc);
        b.string(bci);
        b.tree(value);
        b.end();
        return b.build();
    }

    static String encodeInlinedConstant(ImmediateKind kind, String value) {
        return switch (kind) {
            case CONSTANT_LONG, CONSTANT_INT, CONSTANT_SHORT -> value;
            case CONSTANT_DOUBLE -> "Double.doubleToRawLongBits(" + value + ")";
            case CONSTANT_FLOAT -> "Float.floatToRawIntBits(" + value + ")";
            case CONSTANT_CHAR -> "(short) (" + value + " - " + (1 << 15) + ")";
            case CONSTANT_BYTE -> value; // byte can implicitly widen to short
            case CONSTANT_BOOL -> "(short) (" + value + " ? 1 : 0)";
            default -> {
                throw new AssertionError("Unexpected inlined constant operand kind " + kind);
            }
        };
    }

    CodeTree decodeInlinedConstant(ImmediateKind kind, CodeTree value) {
        return switch (kind) {
            case CONSTANT_LONG, CONSTANT_INT, CONSTANT_SHORT -> value;
            case CONSTANT_DOUBLE -> CodeTreeBuilder.createBuilder().startCall("Double.longBitsToDouble").tree(value).end().build();
            case CONSTANT_FLOAT -> CodeTreeBuilder.createBuilder().startCall("Float.intBitsToFloat").tree(value).end().build();
            case CONSTANT_CHAR -> CodeTreeBuilder.createBuilder().startGroup().cast(type(char.class)).startParantheses().tree(value).string(" + " + (1 << 15)).end(2).build();
            case CONSTANT_BYTE -> CodeTreeBuilder.createBuilder().startGroup().cast(type(byte.class)).tree(value).end().build();
            case CONSTANT_BOOL -> CodeTreeBuilder.createBuilder().startGroup().tree(value).string(" != 0").end().build();
            default -> {
                throw new AssertionError("Unexpected inlined constant operand kind " + kind);
            }
        };
    }

    static CodeTree readImmediate(String bc, String bci, InstructionImmediate immediate) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        String accessor = switch (immediate.kind().width) {
            case BYTE -> "getByte";
            case SHORT -> "getShort";
            case INT -> "getIntUnaligned";
            case LONG -> "getLongUnaligned";
        };
        b.startCall("BYTES", accessor);
        b.string(bc);
        b.startGroup();
        b.string(bci).string(" + ").string(immediate.offset()).string(" ");
        b.startComment().string(" imm ", immediate.name(), " ").end();
        b.end();
        b.end();
        return b.build();
    }

    CodeTree readConstantImmediate(String bc, String bci, String bytecodeNode, InstructionImmediate imm, TypeMirror immediateType) {
        if (imm.kind() == ImmediateKind.CONSTANT) {
            return readConstFastPath(readImmediate(bc, bci, imm), bytecodeNode + ".constants", immediateType);
        } else {
            return decodeInlinedConstant(imm.kind(), readImmediate(bc, bci, imm));
        }
    }

    static CodeTree writeImmediate(String bc, String bci, String value, InstructionImmediateEncoding immediate) {
        return writeImmediate(bc, bci, CodeTreeBuilder.singleString(value), immediate);
    }

    static CodeTree writeImmediate(String bc, String bci, CodeTree value, InstructionImmediateEncoding immediate) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        String accessor = switch (immediate.width()) {
            case BYTE -> "putByte";
            case SHORT -> "putShort";
            case INT -> "putInt";
            case LONG -> "putLong";
        };
        b.startCall("BYTES", accessor);
        b.string(bc);
        b.startGroup();
        b.string(bci).string(" + ").string(immediate.offset());
        b.end();
        b.tree(value);
        b.end();
        return b.build();
    }

    static String readInt(String array, String index) {
        return String.format("BYTES.getInt(%s, %s)", array, index);
    }

    static String writeInt(String array, String index, String value) {
        return String.format("BYTES.putInt(%s, %s, %s)", array, index, value);
    }

    static String readByte(String array, String index) {
        return String.format("BYTES.getByte(%s, %s)", array, index);
    }

    static String writeByte(String array, String index, String value) {
        return String.format("BYTES.putByte(%s, %s, %s)", array, index, value);
    }

    CodeTree readConstFastPath(CodeTree index, String constants) {
        return readConstFastPath(index, constants, null);
    }

    CodeTree readConstFastPath(CodeTree index, String constants, TypeMirror knownType) {
        return readConst(index, uncheckedCast(type(Object[].class), constants), knownType);
    }

    static CodeTree readConst(String index, String constants) {
        return readConst(CodeTreeBuilder.singleString(index), CodeTreeBuilder.singleString(constants), null);
    }

    static CodeTree readConst(CodeTree index, CodeTree constants, TypeMirror knownType) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.readObject");
        b.tree(constants);
        b.tree(index);
        b.end();
        CodeTree tree = b.build();
        if (knownType != null && !ElementUtils.isObject(knownType)) {
            tree = uncheckedCast(ElementUtils.boxType(knownType), tree);
        }
        return tree;
    }

    static CodeTree readIntArray(String array, String index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.readInt");
        b.string(array);
        b.string(index);
        b.end();
        return b.build();
    }

    static CodeTree writeIntArray(String array, String index, String value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.writeInt");
        b.string(array);
        b.string(index);
        b.string(value);
        b.end();
        return b.build();
    }

    static CodeTree readTagNode(TypeMirror expectedType, CodeTree index) {
        return readTagNode(expectedType, "tagRoot.tagNodes", index);
    }

    static CodeTree uncheckedCast(TypeMirror type, String value) {
        return uncheckedCast(type, CodeTreeBuilder.singleString(value));
    }

    static CodeTree uncheckedCast(TypeMirror type, CodeTree value) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.uncheckedCast");
        b.tree(value);
        b.typeLiteral(type);
        b.end();
        return b.build();
    }

    static CodeTree readTagNode(TypeMirror expectedType, String tagNodes, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.readObject");
        b.string(tagNodes);
        b.tree(index);
        b.end();
        return uncheckedCast(expectedType, b.build());
    }

    static CodeTree readTagNodeSafe(CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.string("tagRoot.tagNodes[" + index + "]");
        b.end();
        return b.build();
    }

    CodeTree readNodeProfile(TypeMirror expectedType, CodeTree index) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startCall("ACCESS.uncheckedCast");
        b.startCall("ACCESS.readObject");
        b.tree(uncheckedCast(arrayOf(types.Node), "this.cachedNodes_"));
        b.tree(index);
        b.end();
        b.typeLiteral(expectedType);
        b.end();
        return b.build();
    }

    static String uncheckedGetFrameObject(String index) {
        return uncheckedGetFrameObject("frame", index);
    }

    static String uncheckedGetFrameObject(String frame, String index) {
        return String.format("FRAMES.uncheckedGetObject(%s, %s)", frame, index);
    }

    static CodeTreeBuilder startRequireFrame(CodeTreeBuilder b, TypeMirror type) {
        String methodName;
        switch (type.getKind()) {
            case BOOLEAN:
                methodName = "requireBoolean";
                break;
            case BYTE:
                methodName = "requireByte";
                break;
            case INT:
                methodName = "requireInt";
                break;
            case LONG:
                methodName = "requireLong";
                break;
            case FLOAT:
                methodName = "requireFloat";
                break;
            case DOUBLE:
                methodName = "requireDouble";
                break;
            default:
                methodName = "requireObject";
                break;
        }
        b.startCall("FRAMES", methodName);
        return b;
    }

    static CodeTreeBuilder startExpectFrameUnsafe(CodeTreeBuilder b, String frame, TypeMirror type) {
        return startExpectFrame(b, frame, type, true);
    }

    static CodeTreeBuilder startExpectFrame(CodeTreeBuilder b, String frame, TypeMirror type, boolean unsafe) {
        String methodName;
        switch (type.getKind()) {
            case BOOLEAN:
                methodName = "expectBoolean";
                break;
            case BYTE:
                methodName = "expectByte";
                break;
            case INT:
                methodName = "expectInt";
                break;
            case LONG:
                methodName = "expectLong";
                break;
            case FLOAT:
                methodName = "expectFloat";
                break;
            case DOUBLE:
                methodName = "expectDouble";
                break;
            default:
                methodName = "expectObject";
                break;
        }
        if (unsafe) {
            b.startCall("FRAMES", methodName);
            b.string(frame);
        } else {
            b.startCall(frame, methodName);
        }
        return b;
    }

    static CodeTreeBuilder startGetFrameUnsafe(CodeTreeBuilder b, String frame, TypeMirror type) {
        return startGetFrame(b, frame, type, true);
    }

    static CodeTreeBuilder startGetFrame(CodeTreeBuilder b, String frame, TypeMirror type, boolean unsafe) {
        String methodName;
        if (type == null) {
            methodName = "getValue";
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    methodName = "getBoolean";
                    break;
                case BYTE:
                    methodName = "getByte";
                    break;
                case INT:
                    methodName = "getInt";
                    break;
                case LONG:
                    methodName = "getLong";
                    break;
                case FLOAT:
                    methodName = "getFloat";
                    break;
                case DOUBLE:
                    methodName = "getDouble";
                    break;
                default:
                    methodName = "getObject";
                    break;
            }
        }
        if (unsafe) {
            b.startCall("FRAMES", methodName);
            b.string(frame);
        } else {
            b.startCall(frame, methodName);
        }
        return b;
    }

    static String getSetMethod(TypeMirror type) {
        if (type == null) {
            return "setValue";
        } else {
            return switch (type.getKind()) {
                case BOOLEAN -> "setBoolean";
                case BYTE -> "setByte";
                case INT -> "setInt";
                case LONG -> "setLong";
                case FLOAT -> "setFloat";
                case DOUBLE -> "setDouble";
                default -> "setObject";
            };
        }
    }

    static CodeTreeBuilder startSetFrame(CodeTreeBuilder b, TypeMirror type) {
        String methodName = getSetMethod(type);
        b.startCall("FRAMES", methodName);
        return b;
    }

    static String setFrameObject(String index, String value) {
        return setFrameObject("frame", index, value);
    }

    static String setFrameObject(String frame, String index, String value) {
        return String.format("FRAMES.setObject(%s, %s, %s)", frame, index, value);
    }

    static String clearFrame(String frame, String index) {
        return String.format("FRAMES.clear(%s, %s)", frame, index);
    }

    static String copyFrameSlot(String src, String dst) {
        return String.format("FRAMES.copy(frame, %s, %s)", src, dst);
    }

    static String copyFrameTo(String srcFrame, String srcOffset, String dstFrame, String dstOffset, String length) {
        return String.format("FRAMES.copyTo(%s, %s, %s, %s, %s)", srcFrame, srcOffset, dstFrame, dstOffset, length);
    }

    static String cachedDataClassName(InstructionModel instr) {
        if (!instr.hasNodeImmediate() && !instr.canUseNodeSingleton()) {
            return null;
        }
        if (instr.quickeningBase != null) {
            return cachedDataClassName(instr.quickeningBase);
        }
        return instr.getInternalName() + "Node";
    }

    static GeneratedTypeMirror getCachedDataClassType(InstructionModel instr) {
        return new GeneratedTypeMirror("", cachedDataClassName(instr));
    }

    private static String childString(int numChildren) {
        return numChildren + ((numChildren == 1) ? " child" : " children");
    }

    void emitValidateInstructionTracer(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(type(Objects.class), "requireNonNull").string("tracer").end().end();
        b.declaration("var", "exclusiveDescriptor", "tracer.getExclusiveBytecodeDescriptor()");
        b.startIf().string("exclusiveDescriptor != null && exclusiveDescriptor != BYTECODE").end().startBlock();
        b.startThrow().startNew(type(IllegalArgumentException.class));
        b.startStaticCall(type(String.class), "format");
        b.doubleQuote("The passed instruction tracer is exclusive to %s, but it was installed for %s.");
        b.string("exclusiveDescriptor").string("BYTECODE");
        b.end(); // static call
        b.end().end(); // throw, new
        b.end(); // if block
    }

    static void emitCallDefault(CodeTreeBuilder b,
                    CodeExecutableElement method) {
        emitCallDefault(b, method, null);
    }

    static void emitCallDefault(CodeTreeBuilder b,
                    CodeExecutableElement method, BiConsumer<String, CodeTreeBuilder> argumentMapping) {
        b.startCall(method.getSimpleName().toString());
        for (VariableElement p : method.getParameters()) {
            String name = p.getSimpleName().toString();
            if (argumentMapping == null) {
                b.string(name);
            } else {
                b.startGroup();
                argumentMapping.accept(name, b);
                b.end();
            }
        }
        b.end(); // call
    }

    /**
     * Whether we store the bci at the beginning of an instruction execute, e.g. if all
     * specializations require it or some other condition requires it.
     */
    public static boolean isStoreBciBeforeExecute(BytecodeDSLModel model, InterpreterTier tier, InstructionModel instr) {
        if (!isStoreBciEnabled(model, tier)) {
            return false;
        }
        if (instr.operation == null) {
            return false;
        }
        switch (instr.operation.kind) {
            case RETURN:
            case YIELD:
            case TAG:
            case CUSTOM_YIELD: // custom yield always needs to set the bci
                return true;
            case CUSTOM:
            case CUSTOM_INSTRUMENTATION:
            case CUSTOM_SHORT_CIRCUIT:
                if (instr.operation.kind == OperationKind.CUSTOM_YIELD) {
                    return true;
                }
                CustomOperationModel custom = instr.operation.customModel;
                if (!custom.shouldStoreBytecodeIndex()) {
                    return false;
                }

                for (SpecializationData s : instr.nodeData.getReachableSpecializations()) {
                    // if we use cached values in guards we need to store before execute regardless
                    // otherwise the bytecode index is not up-to-date in the guard
                    if (custom.shouldStoreBytecodeIndex(s) && s.isAnyGuardBoundWithCache()) {
                        return true;
                    }
                }
                for (SpecializationData s : instr.nodeData.getReachableSpecializations()) {
                    if (!custom.shouldStoreBytecodeIndex(s)) {
                        return false;
                    }
                }

                return true;
            default:
                return false;
        }

    }

    /**
     * Returns <code>true</code> if we should set the bytecode index just before the specialization.
     */
    public static boolean isStoreBciBeforeSpecialization(BytecodeDSLModel model, InterpreterTier tier, InstructionModel instr, SpecializationData specialization) {
        if (!isStoreBciEnabled(model, tier)) {
            return false;
        }
        switch (instr.kind) {
            case CUSTOM:
            case CUSTOM_SHORT_CIRCUIT:
                CustomOperationModel custom = instr.operation.customModel;
                if (!custom.shouldStoreBytecodeIndex(specialization)) {
                    // does never perform any calls
                    return false;
                }
                if (isStoreBciBeforeExecute(model, tier, instr)) {
                    // already stored in execute
                    return false;
                }
                return true;
            default:
                return false;

        }
    }

    /**
     * Returns <code>true</code> if bytecode index storing in the frame is enabled, else
     * <code>false</code>.
     */
    public static boolean isStoreBciEnabled(BytecodeDSLModel model, InterpreterTier tier) {
        return model.storeBciInFrame || tier == InterpreterTier.UNCACHED;
    }

    /**
     * When in the uncached interpreter or an interpreter with storeBciInFrame set to true, we need
     * to store the bci in the frame before escaping operations (e.g., returning, yielding,
     * throwing) or potentially-escaping operations (e.g., a custom operation that could invoke
     * another root node).
     */
    public static void storeBciInFrame(CodeTreeBuilder b, String frame, String bci) {
        b.statement("FRAMES.setInt(" + frame + ", " + BCI_INDEX + ", ", bci, ")");
    }

    final class BuilderElement extends CodeTypeElement {
        private static final String UNINIT = "UNINITIALIZED";

        private final CodeVariableElement uninitialized = add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(byte.class), UNINIT));
        private final RootStackElement rootStackElement = add(new RootStackElement());
        private final OperationStackElement operationStack = add(new OperationStackElement());

        private final BytecodeLocalImplElement bytecodeLocalImpl = add(new BytecodeLocalImplElement());
        private final BytecodeLabelImplElement bytecodeLabelImpl = add(new BytecodeLabelImplElement());

        private SerializationLocalElement serializationLocal;
        private SerializationLabelElement serializationLabel;
        private SerializationStateElement serializationElements;
        private DeserializationStateElement deserializationElement;
        private CodeVariableElement serialization;

        private CodeExecutableElement validateLocalScope;
        private CodeExecutableElement validateMaterializedLocalScope;

        private OperationFields operationFields;

        BuilderElement() {
            super(Set.of(PUBLIC, STATIC, FINAL), ElementKind.CLASS, null, "Builder");
        }

        void lazyInit() {
            addJavadoc(this, """
                            Builder class to generate bytecode. An interpreter can invoke this class with its {@link com.oracle.truffle.api.bytecode.BytecodeParser} to generate bytecode.
                            """);
            this.setSuperClass(abstractBuilderType);
            this.setEnclosingElement(BytecodeRootNodeElement.this);

            this.rootStackElement.lazyInit();

            this.uninitialized.createInitBuilder().string(-1).end();

            this.operationFields = new OperationFields();
            this.operationStack.lazyInit(operationFields);
            this.bytecodeLocalImpl.lazyInit();
            this.bytecodeLabelImpl.lazyInit();

            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), model.languageClass, "language"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), bytecodeRootNodesImpl.asType(), "nodes"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(CharSequence.class), "reparseReason"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "parseBytecodes"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "tags"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "instrumentations"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(boolean.class), "parseSources"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, BytecodeRootNodeElement.this.asType()), "builtNodes"));
            this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, types.Source), "sources"));
            this.add(new CodeVariableElement(Set.of(PRIVATE), rootStackElement.asType(), "state"));
            this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numRoots"));

            if (model.enableSerialization) {
                BytecodeRootNodeElement.this.serializationRootNode = this.add(new SerializationRootNodeElement());
                this.serializationLocal = this.add(new SerializationLocalElement());
                this.serializationLabel = this.add(new SerializationLabelElement());
                this.serializationElements = this.add(new SerializationStateElement());
                this.deserializationElement = this.add(new DeserializationStateElement());
                this.serialization = this.add(new CodeVariableElement(Set.of(PRIVATE), serializationElements.asType(), "serialization"));
            }

            this.add(createParseConstructor());
            this.add(createReparseConstructor());

            this.add(createCreateLocal());
            this.add(createCreateLocalAllParameters());
            this.add(createCreateLabel());
            this.addAll(createSourceSectionUnavailableHelpers());

            for (OperationModel operation : model.getOperations()) {
                if (omitBuilderMethods(operation)) {
                    continue;
                }

                if (operation.hasChildren()) {
                    this.add(createBegin(operation));
                    this.add(createEnd(operation));
                } else {
                    this.add(createEmit(operation));
                }
            }
            this.add(createMarkReachable());
            this.add(createUpdateReachable());

            this.add(createBeginOperation());
            this.add(createEndOperation());
            this.add(createVerifyOperation());
            this.add(createValidateRootOperationBegin());
            this.add(createGetCurrentRootOperationData());
            this.add(createBeforeChild());
            this.add(createAfterChild());
            this.add(createSafeCastShort());
            this.add(createCheckOverflowShort());
            this.add(createCheckOverflowInt());
            this.add(createCheckBci());

            if (model.hasCustomVariadic) {
                this.add(createDoEmitVariadicBeforeChild());
                this.add(createDoEmitVariadicEnd());
                if (model.hasVariadicReturn) {
                    this.add(createIsVariadicReturn());
                }
            }

            if (model.enableTagInstrumentation) {
                this.add(createFindOuterTag());
            }

            this.add(createDoEmitFinallyHandler());

            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "PATCH_CURRENT_SOURCE")).createInitBuilder().string("-2");
            this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "PATCH_NODE_SOURCE")).createInitBuilder().string("-3");

            this.add(createFinish());
            this.add(createBeforeEmitBranch());
            this.add(createBeforeEmitReturn());
            this.add(createDoEmitRootSourceSection());
            if (model.hasYieldOperation()) {
                if (model.enableTagInstrumentation) {
                    this.add(createDoEmitTagYield(model.tagYieldInstruction));
                    this.add(createDoEmitTagResume());

                    if (model.tagYieldNullInstruction != null) {
                        this.add(createDoEmitTagYield(model.tagYieldNullInstruction));
                    }
                }
            }

            if (model.enableBlockScoping) {
                this.add(createGetCurrentScope());
            }
            if (model.enableSerialization) {
                this.add(createSerialize());
                this.add(createSerializeFinallyGenerator());
                this.add(createDeserialize());
            }

            if (model.enableInstructionTracing) {
                this.add(createFindOrCreateInstructionTracer());
                this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "INSTRUCTION_TRACER_CONSTANT_INDEX")).createInitBuilder().string("0");
            }

            this.add(createToString());
            this.add(createFailState());
            this.add(createFailArgument());
            this.add(createDumpAt());

            this.rootStackElement.lateInit();
        }

        private CodeExecutableElement createReparseConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(type(boolean.class), "parseBytecodes"));
            ctor.addParameter(new CodeVariableElement(type(int.class), "tags"));
            ctor.addParameter(new CodeVariableElement(type(int.class), "instrumentations"));
            ctor.addParameter(new CodeVariableElement(type(boolean.class), "parseSources"));
            ctor.addParameter(new CodeVariableElement(type(CharSequence.class), "reparseReason"));

            CodeTreeBuilder javadoc = ctor.createDocBuilder();
            javadoc.startJavadoc();
            javadoc.string("Constructor for reparsing.");
            javadoc.newLine();
            javadoc.end();

            CodeTreeBuilder b = ctor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.statement("this.language = nodes.getLanguage()");
            b.statement("this.nodes = nodes");
            b.statement("this.reparseReason = reparseReason");
            b.statement("this.parseBytecodes = parseBytecodes");
            b.statement("this.tags = tags");
            b.statement("this.instrumentations = instrumentations");
            b.statement("this.parseSources = parseSources");
            b.statement("this.sources = parseSources ? new ArrayList<>(4) : null");
            b.statement("this.builtNodes = new ArrayList<>()");

            b.startAssign("this.state").startStaticCall(rootStackElement.asType(), "acquire").end().end();
            b.statement("this.state.instrumentations = instrumentations");

            return ctor;
        }

        private CodeExecutableElement createParseConstructor() {
            CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, "Builder");
            ctor.addParameter(new CodeVariableElement(model.languageClass, "language"));
            ctor.addParameter(new CodeVariableElement(bytecodeRootNodesImpl.asType(), "nodes"));
            ctor.addParameter(new CodeVariableElement(type(long.class), "configEncoding"));

            CodeTreeBuilder javadoc = ctor.createDocBuilder();
            javadoc.startJavadoc();
            javadoc.string("Constructor for initial parses.");
            javadoc.newLine();
            javadoc.end();

            CodeTreeBuilder b = ctor.createBuilder();
            b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
            b.statement("this.language = language");
            b.statement("this.nodes = nodes");
            b.statement("this.reparseReason = null");
            b.statement("long encoding = configEncoding");
            b.statement("this.tags = (int)((encoding >> " + TAG_OFFSET + ") & 0xFFFF_FFFF)");
            b.statement("this.instrumentations = (int)((encoding >> " + INSTRUMENTATION_OFFSET + ") & 0x7FFF_FFFF)");
            b.statement("this.parseSources = (encoding & 0x1) != 0");
            b.statement("this.parseBytecodes = true");
            b.statement("this.sources = parseSources ? new ArrayList<>(4) : null");
            b.statement("this.builtNodes = new ArrayList<>()");

            b.startAssign("this.state").startStaticCall(rootStackElement.asType(), "acquire").end().end();
            b.statement("this.state.instrumentations = instrumentations");
            return ctor;
        }

        private boolean omitBuilderMethods(OperationModel operation) {
            // These operations are emitted automatically. The builder methods are unnecessary.
            return (model.prolog != null && model.prolog.operation == operation) ||
                            (model.epilogExceptional != null && model.epilogExceptional.operation == operation);
        }

        private CodeExecutableElement createMarkReachable() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            type(void.class), "markReachable");
            method.addParameter(new CodeVariableElement(type(boolean.class), "newReachable"));
            CodeTreeBuilder b = method.createBuilder();
            b.statement("state.reachable = newReachable");
            b.startTryBlock();

            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();
                for (OperationModel op : model.getOperations()) {
                    switch (op.kind) {
                        case ROOT:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.tree(operationStack.write(op, operationFields.reachable, "newReachable"));
                            b.statement("return");
                            b.end();
                            break;
                        case IF_THEN:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if and parent block unreachable.");
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case IF_THEN_ELSE:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.tree(operationStack.write(op, operationFields.elseReachable, "newReachable"));
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.elseReachable, "newReachable"));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case CONDITIONAL:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.tree(operationStack.write(op, operationFields.elseReachable, "newReachable"));
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.thenReachable, "newReachable"));
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.elseReachable, "newReachable"));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.tryReachable, "newReachable"));
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.catchReachable, "newReachable"));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case WHILE:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.bodyReachable, "newReachable"));
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.bodyReachable, "newReachable"));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                        case TRY_FINALLY:
                        case TRY_CATCH_OTHERWISE:
                            b.startCase().tree(createOperationConstant(op)).end().startCaseBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.tree(operationStack.write(op, operationFields.tryReachable, "newReachable"));
                            if (op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                                b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                                b.tree(operationStack.write(op, operationFields.catchReachable, "newReachable"));
                            }
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return");
                            b.end();
                            break;
                    }
                }
                b.end(); // switch
            });

            b.end().startFinallyBlock();
            b.startAssert().string("updateReachable() == state.reachable : ").doubleQuote("Inconsistent reachability detected.").end();
            b.end();

            return method;
        }

        private CodeExecutableElement createUpdateReachable() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            type(boolean.class), "updateReachable");

            CodeTreeBuilder doc = method.createDocBuilder();
            doc.startJavadoc();
            doc.string("Updates the reachable field from the current operation. Typically invoked when the operation ended or the child is changing.");
            doc.newLine();
            doc.end();

            CodeTreeBuilder b = method.createBuilder();
            b.statement("boolean oldReachable = state.reachable");
            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();
                for (OperationModel op : model.getOperations()) {
                    switch (op.kind) {
                        case ROOT:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.reachable));
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case IF_THEN:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.thenReachable));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case IF_THEN_ELSE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.thenReachable));
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.elseReachable));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case CONDITIONAL:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.lineComment("Unreachable condition branch makes the if, then and parent block unreachable.");
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.thenReachable));
                            b.end().startElseIf().string("operation.childCount == 2").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.elseReachable));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case TRY_CATCH:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.tryReachable));
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.catchReachable));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case WHILE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("continue");
                            b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.bodyReachable));
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                        case TRY_FINALLY:
                        case TRY_CATCH_OTHERWISE:
                            b.startCase().tree(createOperationConstant(op)).end().startBlock();
                            b.startIf().string("operation.childCount == 0").end().startBlock();
                            b.statement("state.reachable = " + operationStack.read(op, operationFields.tryReachable));
                            if (op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                                b.end().startElseIf().string("operation.childCount == 1").end().startBlock();
                                b.statement("state.reachable = " + operationStack.read(op, operationFields.catchReachable));
                            }
                            b.end().startElseBlock();
                            b.lineComment("Invalid child index, but we will fail in the end method.");
                            b.end();
                            b.statement("return oldReachable");
                            b.end();
                            break;
                    }
                }

                b.end(); // switch
            });

            b.statement("return oldReachable");
            return method;
        }

        private CodeExecutableElement createSerialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            type(void.class), "serialize");
            method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
            method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));

            // When serializing existing BytecodeRootNodes, we want to use their field values rather
            // than the ones that get stored on the dummy root nodes during the reparse.
            TypeMirror nodeList = generic(List.class, model.getTemplateType().asType());
            CodeVariableElement existingNodes = new CodeVariableElement(nodeList, "existingNodes");
            method.addParameter(existingNodes);

            method.addThrownType(type(IOException.class));
            CodeTreeBuilder b = method.createBuilder();

            b.statement("this.serialization = new SerializationState(buffer, callback)");

            b.startTryBlock();

            if (model.serializedFields.size() == 0) {
                // Simplify generated code: just one call
                b.startStatement().startCall("nodes.getParserImpl()", "parse").string("this").end(2);
                serializationElements.writeShort(b, serializationElements.codeEndSerialize);
            } else {
                b.lineComment("1. Serialize the root nodes and their constants.");
                b.startStatement().startCall("nodes.getParserImpl()", "parse").string("this").end(2);

                b.lineComment("2. Serialize the fields stored on each root node. If existingNodes is provided, serialize those fields instead of the new root nodes' fields.");

                b.declaration(nodeList, "nodesToSerialize", "existingNodes != null ? existingNodes : serialization.builtNodes");

                b.statement("int[][] nodeFields = new int[nodesToSerialize.size()][]");
                b.startFor().string("int i = 0; i < nodeFields.length; i ++").end().startBlock();
                b.declaration(model.getTemplateType().asType(), "node", "nodesToSerialize.get(i)");
                b.statement("int[] fields = nodeFields[i] = new int[" + model.serializedFields.size() + "]");
                for (int i = 0; i < model.serializedFields.size(); i++) {
                    VariableElement var = model.serializedFields.get(i);
                    b.startStatement();
                    b.string("fields[").string(i).string("] = ");
                    b.startCall("serialization.serializeObject");
                    b.startGroup();
                    b.string("node.").string(var.getSimpleName().toString());
                    b.end();
                    b.end();
                    b.end();
                }
                b.end();
                serializationElements.writeShort(b, serializationElements.codeEndSerialize);

                b.lineComment("3. Encode the constant pool indices for each root node's fields.");
                b.startFor().string("int i = 0; i < nodeFields.length; i++").end().startBlock();
                b.statement("int[] fields = nodeFields[i]");

                for (int i = 0; i < model.serializedFields.size(); i++) {
                    serializationElements.writeInt(b, "fields[" + i + "]");
                }
                b.end();
            }

            b.end().startFinallyBlock();
            b.statement("this.serialization = null");
            b.end();

            return method;

        }

        private CodeExecutableElement createSerializeFinallyGenerator() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "serializeFinallyGenerator");
            method.addParameter(new CodeVariableElement(declaredType(Runnable.class), "finallyGenerator"));
            method.addThrownType(declaredType(IOException.class));

            CodeTreeBuilder b = method.getBuilder();

            b.startDeclaration(declaredType(ByteArrayOutputStream.class), "baos");
            b.startNew(declaredType(ByteArrayOutputStream.class)).end();
            b.end();
            b.declaration(serializationElements.asType(), "outerSerialization", "serialization");

            b.startTryBlock();
            b.startAssign("serialization").startNew(serializationElements.asType());
            b.startNew(declaredType(DataOutputStream.class)).string("baos").end();
            b.string("serialization");
            b.end(2);
            b.statement("finallyGenerator.run()");
            serializationElements.writeShort(b, serializationElements.codeEndFinallyGenerator);
            b.end(); // try

            b.startFinallyBlock();
            b.statement("serialization = outerSerialization");
            b.end(); // finally

            b.declaration(arrayOf(type(byte.class)), "bytes", "baos.toByteArray()");
            serializationElements.writeShort(b, serializationElements.codeCreateFinallyGenerator);
            serializationElements.writeInt(b, "bytes.length");
            serializationElements.writeBytes(b, "bytes");
            b.startReturn().string(safeCastShort("serialization.finallyGeneratorCount++")).end();

            return method;
        }

        private CodeExecutableElement createFindOrCreateInstructionTracer() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            instructionTracerAccessImplElement.asType(), "findOrCreateInstructionTracer");
            CodeTreeBuilder b = method.createBuilder();

            b.declaration(instructionTracerAccessImplElement.asType(), "tracer", "null");
            b.startIf().string("reparseReason != null").end().startBlock();
            b.startDeclaration(arrayOf(type(Object.class)), "previousConstants");
            b.string("((").type(abstractBytecodeNode.asType()).string(") nodes.getNode(numRoots - 1).getBytecodeNode()).constants");
            b.end();
            b.startIf().string("INSTRUCTION_TRACER_CONSTANT_INDEX < previousConstants.length && previousConstants[INSTRUCTION_TRACER_CONSTANT_INDEX] instanceof ").type(
                            instructionTracerAccessImplElement.asType()).string(
                                            " t").end().startBlock();
            b.statement("tracer = t");
            b.end(); // if constant
            b.end(); // if reparse

            b.startIf().string("tracer == null").end().startBlock();
            b.startAssign("tracer").startNew(instructionTracerAccessImplElement.asType()).string("language").end().end();
            b.end();

            b.statement("return tracer");
            return method;
        }

        private CodeExecutableElement createDeserialize() {
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            type(void.class), "deserialize");
            mergeSuppressWarnings(method, "hiding");

            method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "bufferSupplier"));
            method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
            method.addParameter(new CodeVariableElement(deserializationElement.asType(), "outerContext"));

            CodeTreeBuilder b = method.createBuilder();

            b.startTryBlock();

            b.startDeclaration(deserializationElement.asType(), "context");
            b.startNew(deserializationElement.asType()).string("outerContext").end();
            b.end();

            b.declaration(type(DataInput.class), "buffer", "bufferSupplier.get()");

            b.startWhile().string("true").end().startBlock();

            b.declaration(type(short.class), "code", "buffer.readShort()");

            b.startSwitch().string("code").end().startBlock();

            b.startCase().staticReference(serializationElements.codeCreateLabel).end().startBlock();
            b.statement("context.labels.add(createLabel())");
            b.statement("break");
            b.end(); // create label

            b.startCase().staticReference(serializationElements.codeCreateLocal).end().startBlock();
            b.statement("int nameId = buffer.readInt()");
            b.statement("Object name = null");
            b.startIf().string("nameId != -1").end().startBlock();
            b.statement("name = context.consts.get(nameId)");
            b.end();
            b.statement("int infoId = buffer.readInt()");
            b.statement("Object info = null");
            b.startIf().string("infoId != -1").end().startBlock();
            b.statement("info = context.consts.get(infoId)");
            b.end();
            b.statement("context.locals.add(createLocal(name, info))");
            b.statement("break");
            b.end(); // create local

            b.startCase().staticReference(serializationElements.codeCreateNull).end().startBlock();
            b.startStatement();
            b.startCall("context.consts.add");
            b.string("null");
            b.end();
            b.end();
            b.statement("break");
            b.end(); // create object

            b.startCase().staticReference(serializationElements.codeCreateObject).end().startBlock();
            b.startStatement();
            b.startCall("context.consts.add");
            b.startStaticCall(type(Objects.class), "requireNonNull");
            b.string("callback.deserialize(context, buffer)");
            b.end();
            b.end();
            b.end();
            b.statement("break");
            b.end(); // create object

            b.startCase().staticReference(serializationElements.codeCreateFinallyGenerator).end().startBlock();
            b.statement("byte[] finallyGeneratorBytes = new byte[buffer.readInt()]");
            b.statement("buffer.readFully(finallyGeneratorBytes)");

            b.startStatement().startCall("context.finallyGenerators.add");
            b.startGroup().string("() -> ").startCall("deserialize");
            b.startGroup().string("() -> ").startStaticCall(types.SerializationUtils, "createDataInput");
            b.startStaticCall(declaredType(ByteBuffer.class), "wrap").string("finallyGeneratorBytes").end();
            b.end(2);
            b.string("callback");
            b.string("context");
            b.end(2); // lambda
            b.end(2);
            b.statement("break");
            b.end(); // create finally generator

            b.startCase().staticReference(serializationElements.codeEndFinallyGenerator).end().startBlock();
            b.statement("return");
            b.end(); // end finally generator

            b.startCase().staticReference(serializationElements.codeEndSerialize).end().startBlock();

            if (model.serializedFields.size() != 0) {
                b.startFor().string("int i = 0; i < this.builtNodes.size(); i++").end().startBlock();
                b.declaration(BytecodeRootNodeElement.this.asType(), "node", "this.builtNodes.get(i)");
                for (int i = 0; i < model.serializedFields.size(); i++) {
                    VariableElement var = model.serializedFields.get(i);
                    b.startStatement();
                    b.string("node.").string(var.getSimpleName().toString());
                    b.string(" = ");
                    if (ElementUtils.needsCastTo(type(Object.class), var.asType())) {
                        b.cast(var.asType());
                    }
                    b.string("context.consts.get(buffer.readInt())");
                    b.end();
                }
                b.end();
            }

            b.returnStatement();
            b.end();

            final boolean hasTags = !model.getProvidedTags().isEmpty();
            for (OperationModel operation : model.getUserOperations()) {
                // create begin/emit code
                b.startCase().staticReference(serializationElements.codeBegin[operation.id]).end().startBlock();

                if (operation.kind == OperationKind.TAG && !hasTags) {
                    b.startThrow().startNew(type(IllegalStateException.class));
                    b.doubleQuote(String.format("Cannot deserialize instrument tag. The language does not specify any tags with a @%s annotation.",
                                    ElementUtils.getSimpleName(types.ProvidedTags)));
                    b.end().end();
                    b.end(); // switch block
                    continue;
                }

                if (operation.kind == OperationKind.ROOT) {
                    b.statement("context.builtNodes.add(null)");
                }

                for (OperationArgument beginArgument : operation.operationBeginArguments) {
                    buildDeserializeOperationArgument(b, beginArgument);
                }

                b.startStatement();
                if (operation.hasChildren()) {
                    b.startCall("begin" + operation.builderName);
                } else {
                    b.startCall("emit" + operation.builderName);
                }

                for (int i = 0; i < operation.operationBeginArguments.length; i++) {
                    b.string(operation.getOperationBeginArgumentName(i));
                }

                b.end(2); // statement, call

                b.statement("break");

                b.end(); // case block

                if (operation.hasChildren()) {
                    b.startCase().staticReference(serializationElements.codeEnd[operation.id]).end().startBlock();

                    for (OperationArgument endArgument : operation.operationEndArguments) {
                        buildDeserializeOperationArgument(b, endArgument);
                    }

                    if (operation.kind == OperationKind.ROOT) {
                        b.startStatement();
                        b.type(BytecodeRootNodeElement.this.asType()).string(" node = ").cast(BytecodeRootNodeElement.this.asType()).string("end" + operation.builderName + "()");
                        b.end();

                        b.declaration(type(int.class), "serializedContextDepth", "buffer.readInt()");
                        b.startIf().string("context.").variable(deserializationElement.depth).string(" != serializedContextDepth").end().startBlock();
                        b.startThrow().startNew(type(AssertionError.class));
                        b.startGroup();
                        b.doubleQuote("Invalid context depth. Expected ").string(" + context.").variable(deserializationElement.depth).string(" + ");
                        b.doubleQuote(" but got ").string(" + serializedContextDepth");
                        b.end(); // group
                        b.end(2); // throw
                        b.end(); // if

                        b.startStatement().startCall("context.builtNodes.set").string("buffer.readInt()").string("node").end().end();
                    } else {
                        b.startStatement().startCall("end" + operation.builderName);
                        for (int i = 0; i < operation.operationEndArguments.length; i++) {
                            b.string(operation.getOperationEndArgumentName(i));
                        }
                        b.end(2);
                    }
                    b.statement("break");

                    b.end();
                }
            }

            b.caseDefault().startBlock();
            b.startThrow().startNew(type(AssertionError.class));
            b.startGroup();
            b.doubleQuote("Unknown operation code ").string(" + code");
            b.end();
            b.end().end();

            b.end(); // switch block
            b.end();

            b.end(); // switch
            b.end(); // while block

            b.end().startCatchBlock(type(IOException.class), "ex");
            b.startThrow().startNew(type(IOError.class)).string("ex").end(2);
            b.end();

            return method;

        }

        private void buildSerializeOperationArgument(CodeTreeBuilder before, CodeTreeBuilder after, OperationArgument argument) {
            String argumentName = argument.name();
            switch (argument.kind()) {
                case LOCAL:
                    String serializationLocalCls = serializationLocal.getSimpleName().toString();
                    serializationElements.writeShort(after, safeCastShort(String.format("((%s) %s).contextDepth", serializationLocalCls, argumentName)));
                    serializationElements.writeShort(after, safeCastShort(String.format("((%s) %s).localIndex", serializationLocalCls, argumentName)));
                    break;
                case LOCAL_ARRAY:
                    serializationElements.writeShort(after, safeCastShort(argumentName + ".length"));
                    // Emit the depth once then assert that all locals have the same depth.
                    String depth = argumentName + "Depth";
                    after.startIf().string(argumentName, ".length > 0").end().startBlock();
                    after.startDeclaration(type(short.class), depth);
                    after.startCall("safeCastShort");
                    after.startGroup();
                    after.startParantheses().cast(serializationLocal.asType()).string(argumentName, "[0]").end();
                    after.string(".contextDepth");
                    after.end(3);

                    serializationElements.writeShort(after, depth);

                    after.startFor().string("int i = 0; i < " + argumentName + ".length; i++").end().startBlock();
                    after.startDeclaration(serializationLocal.asType(), "localImpl");
                    after.cast(serializationLocal.asType()).string(argumentName, "[i]");
                    after.end();

                    after.startAssert().string(depth, " == ", safeCastShort("localImpl.contextDepth")).end();
                    serializationElements.writeShort(after, safeCastShort("localImpl.localIndex"));

                    after.end(); // for
                    after.end(); // if
                    break;
                case LABEL:
                    String serializationLabelCls = serializationLabel.getSimpleName().toString();
                    serializationElements.writeShort(after, safeCastShort(String.format("((%s) %s).contextDepth", serializationLabelCls, argumentName)));
                    serializationElements.writeShort(after, safeCastShort(String.format("((%s) %s).labelIndex", serializationLabelCls, argumentName)));
                    break;
                case TAGS:
                    serializationElements.writeInt(after, "encodedTags");
                    break;
                case SHORT:
                    serializationElements.writeShort(after, argumentName);
                    break;
                case INTEGER:
                    serializationElements.writeInt(after, argumentName);
                    break;
                case CONSTANT: {
                    if (argument.constantOperand().isPresent()) {
                        ConstantOperandModel constantOperand = argument.constantOperand().get();
                        if (constantOperand.kind() != ImmediateKind.CONSTANT) {
                            // Special case: inlined constant operands.
                            buildSerializeInlinedConstant(after, constantOperand.kind(), argument);
                            break;
                        }
                    }
                    String index = argumentName + "_index";
                    before.startDeclaration(type(int.class), index);
                    before.startCall("serialization.serializeObject").string(argumentName).end();
                    before.end();
                    serializationElements.writeInt(after, index);
                    break;
                }
                case FINALLY_GENERATOR: {
                    String index = "finallyGeneratorIndex";
                    before.startDeclaration(type(short.class), index);
                    before.startCall("serializeFinallyGenerator");
                    before.string(argumentName);
                    before.end(2);
                    serializationElements.writeShort(after, "serialization.depth");
                    serializationElements.writeShort(after, index);
                    break;
                }
                default:
                    throw new AssertionError("unexpected argument kind " + argument.kind());
            }
        }

        private void buildSerializeInlinedConstant(CodeTreeBuilder b, ImmediateKind kind, OperationArgument argument) {
            String encoded = encodeInlinedConstant(kind, argument.name());
            switch (kind.width) {
                case LONG -> serializationElements.writeLong(b, encoded);
                case INT -> serializationElements.writeInt(b, encoded);
                case SHORT -> serializationElements.writeShort(b, encoded);
                case BYTE -> serializationElements.writeByte(b, encoded);
            }
        }

        private void buildDeserializeOperationArgument(CodeTreeBuilder b, OperationArgument argument) {
            TypeMirror argType = argument.builderType();
            String argumentName = argument.name();
            switch (argument.kind()) {
                case LOCAL:
                    b.declaration(argType, argumentName, "context.getContext(buffer.readShort()).locals.get(buffer.readShort())");
                    break;
                case LABEL:
                    b.declaration(argType, argumentName, "context.getContext(buffer.readShort()).labels.get(buffer.readShort())");
                    break;
                case TAGS:
                    b.declaration(argType, argumentName, "TAG_MASK_TO_TAGS.computeIfAbsent(buffer.readInt(), (v) -> mapTagMaskToTagsArray(v))");
                    break;
                case SHORT:
                    b.declaration(argType, argumentName, "buffer.readShort()");
                    break;
                case INTEGER:
                    b.declaration(argType, argumentName, "buffer.readInt()");
                    break;
                case LOCAL_ARRAY:
                    b.startDeclaration(argType, argumentName).startNewArray(arrayOf(types.BytecodeLocal), CodeTreeBuilder.singleString("buffer.readShort()")).end().end();
                    b.startIf().string(argumentName, ".length != 0").end().startBlock();
                    b.declaration(deserializationElement.asType(), "setterContext", "context.getContext(buffer.readShort())");
                    b.startFor().string("int i = 0; i < ", argumentName, ".length; i++").end().startBlock();
                    b.statement(argumentName, "[i] = setterContext.locals.get(buffer.readShort())");
                    b.end(); // if
                    b.end();
                    break;
                case CONSTANT:
                    b.startDeclaration(argType, argumentName);
                    if (argument.constantOperand().isPresent() && argument.constantOperand().get().kind() != ImmediateKind.CONSTANT) {
                        // Special case: inlined constant operands.
                        buildDeserializeInlinedConstant(b, argument.constantOperand().get().kind());
                    } else {
                        if (!ElementUtils.isObject(argType)) {
                            b.cast(argType);
                        }
                        b.string("context.consts.get(buffer.readInt())");
                    }
                    b.end(); // declaration
                    break;
                case FINALLY_GENERATOR:
                    b.startDeclaration(argType, argumentName);
                    b.string("context.getContext(buffer.readShort()).finallyGenerators.get(buffer.readShort())");
                    b.end();
                    break;
                default:
                    throw new AssertionError("unexpected argument kind " + argument.kind());
            }
        }

        private void buildDeserializeInlinedConstant(CodeTreeBuilder b, ImmediateKind kind) {
            CodeTree read = CodeTreeBuilder.singleString(switch (kind.width) {
                case BYTE -> "buffer.readByte()";
                case SHORT -> "buffer.readShort()";
                case INT -> "buffer.readInt()";
                case LONG -> "buffer.readLong()";
            });
            b.tree(decodeInlinedConstant(kind, read));
        }

        private CodeExecutableElement createFinish() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "finish");
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("state.operationSp != 0").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("Unexpected parser end - there are still operations on the stack. Did you forget to end them?").end().end();
            b.end();

            b.startIf().string("reparseReason == null").end().startBlock();
            b.startStatement().string("nodes.setNodes(builtNodes.toArray(new ").type(BytecodeRootNodeElement.this.asType()).string("[0]))").end();
            b.end();
            b.statement("assert nodes.validate()");

            b.startStatement().startStaticCall(rootStackElement.asType(), "release").string("state").end().end();
            b.lineComment("make sure its no longer used");
            b.statement("this.state = null");

            return ex;
        }

        private CodeExecutableElement createCreateLocal() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");

            addJavadoc(ex, "Creates a new local. Uses default values for the local's metadata.");

            CodeTreeBuilder b = ex.createBuilder();

            b.startReturn().startCall("createLocal");
            b.string("null"); // name
            b.string("null"); // info
            b.end(2);

            return ex;
        }

        private CodeExecutableElement createCreateLocalAllParameters() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLocal, "createLocal");
            ex.addParameter(new CodeVariableElement(type(Object.class), "name"));
            ex.addParameter(new CodeVariableElement(type(Object.class), "info"));

            addJavadoc(ex, """
                            Creates a new local. Uses the given {@code name} and {@code info} in its local metadata.

                            @param name the name assigned to the local's slot.
                            @param info the info assigned to the local's slot.
                            @see BytecodeNode#getLocalNames
                            @see BytecodeNode#getLocalInfos
                            """);

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    b.declaration(type(int.class), "nameId");
                    b.startIf().string("name != null").end().startBlock();
                    b.statement("nameId = serialization.serializeObject(name)");
                    b.end().startElseBlock();
                    b.statement("nameId = -1");
                    b.end();

                    b.declaration(type(int.class), "infoId");
                    b.startIf().string("info != null").end().startBlock();
                    b.statement("infoId = serialization.serializeObject(info)");
                    b.end().startElseBlock();
                    b.statement("infoId = -1");
                    b.end();

                    serializationElements.writeShort(b, serializationElements.codeCreateLocal);
                    serializationElements.writeInt(b, "nameId");
                    serializationElements.writeInt(b, "infoId");
                });
                b.startReturn().startNew(serializationLocal.asType());
                b.string("serialization.depth");
                b.string("serialization.localCount++");
                b.end(2);
                b.end();
            }

            if (model.enableBlockScoping) {
                b.declaration(operationStack.asType(), "scope", "getCurrentScope()");
                b.declaration(type(short.class), "localIndex", "state.allocateBytecodeLocal() /* unique global index */");
                b.declaration(type(short.class), "frameIndex", safeCastShort("USER_LOCALS_START_INDEX + scope.getFrameOffset() + scope.getNumLocals()") + " /* location in frame */");
                b.declaration(type(int.class), "tableIndex", "state.doEmitLocal(localIndex, frameIndex, name, info) /* index in global table */");
                b.statement("scope.registerLocal(tableIndex)");
            } else {
                b.declaration(type(short.class), "localIndex", "state.allocateBytecodeLocal() /* unique global index */");
                b.declaration(type(short.class), "frameIndex", safeCastShort("USER_LOCALS_START_INDEX + localIndex") + " /* location in frame */");
                b.statement("state.doEmitLocal(name, info)");
            }

            b.startDeclaration(bytecodeLocalImpl.asType(), "local");

            b.startNew(bytecodeLocalImpl.asType()).string("frameIndex");
            b.string("localIndex");
            b.startCall("safeCastShort");
            b.string(operationStack.read(model.rootOperation, "state.operationStack[state.rootOperationSp]", operationFields.index));
            b.end();
            if (model.enableBlockScoping) {
                b.string("scope");
                b.string("scope.sequenceNumber");
            }

            b.end(); // new

            b.end();
            b.startReturn().string("local").end();
            return ex;
        }

        private CodeExecutableElement createGetCurrentScope() {
            TypeMirror scopeType = operationStack.asType();
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE),
                            scopeType, "getCurrentScope");
            CodeTreeBuilder b = method.createBuilder();
            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();
                b.startCase().tree(createOperationConstant(model.rootOperation)).end();
                b.startCase().tree(createOperationConstant(model.blockOperation)).end();
                b.startCaseBlock();
                b.statement("return operation");
                b.end();
                b.end();
            });
            b.startThrow().startCall("failState").doubleQuote("Invalid scope for local variable.").end().end();
            return method;

        }

        private CodeExecutableElement createCreateLabel() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), types.BytecodeLabel, "createLabel");

            addJavadoc(ex, "Creates a new label. The result should be {@link #emitLabel emitted} and can be {@link #emitBranch branched to}.");

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeCreateLabel);
                });

                b.startReturn().startNew(serializationLabel.asType());
                b.string(serialization.getName(), ".", serializationElements.depth.getName());
                b.string(serialization.getName(), ".", serializationElements.labelCount.getName(), "++");
                b.end(2);
                b.end();
            }

            /**
             * To keep control flow reasonable, emitBranch checks that branches target labels
             * defined in the same operation or an enclosing one. The check is thwarted if the user
             * directly defines a label in a branching control structure (e.g.,
             * TryCatch(emitLabel(lbl), branch(lbl)) is unreasonable but passes the check).
             * Requiring labels to be defined in Root or Block operations prevents this edge case.
             */
            b.declaration(operationStack.asType(), "operationStack", "state.peekOperation()");
            b.startIf();
            b.string("operationStack == null || (operationStack.operation != ").tree(createOperationConstant(model.blockOperation));
            b.string(" && operationStack.operation != ").tree(createOperationConstant(model.rootOperation)).string(")");
            b.end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("Labels must be created inside either Block or Root operations.").end().end();
            b.end();

            b.startAssign("BytecodeLabel result").startNew(bytecodeLabelImpl.asType());
            b.string("state.numLabels++");
            b.string(UNINIT);
            b.string("operationStack.sequenceNumber");
            b.end(2);

            b.statement("operationStack.addDeclaredLabel(result)");

            b.startReturn().string("result").end();

            return ex;
        }

        private List<CodeExecutableElement> createSourceSectionUnavailableHelpers() {
            CodeExecutableElement begin = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "beginSourceSectionUnavailable");
            addJavadoc(begin, """
                            Begins a built-in SourceSection operation with an unavailable source section.

                            @see #beginSourceSection(int, int)
                            @see #endSourceSectionUnavailable()
                            """);
            begin.createBuilder().statement("beginSourceSection(-1, -1)");

            CodeExecutableElement end = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "endSourceSectionUnavailable");
            addJavadoc(end, """
                            Ends a built-in SourceSection operation with an unavailable source section.

                            @see #endSourceSection()
                            @see #beginSourceSectionUnavailable()
                            """);
            end.createBuilder().statement("endSourceSection()");

            return List.of(begin, end);
        }

        private static String getBuilderMethodJavadocHeader(String action, OperationModel operation) {
            StringBuilder sb = new StringBuilder(action);

            if (operation.isCustom()) {
                sb.append(" a custom ");
                switch (operation.kind) {
                    case CUSTOM:
                    case CUSTOM_YIELD:
                    case CUSTOM_INSTRUMENTATION:
                        CustomOperationModel customOp = operation.parent.getCustomOperationForOperation(operation);
                        sb.append("{@link ");
                        sb.append(customOp.getTemplateType().getQualifiedName());
                        sb.append(" ");
                        sb.append(operation.name);
                        sb.append("}");
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        // short-circuit ops don't have a defining class
                        sb.append(operation.name);
                        break;
                    default:
                        throw new AssertionError("Unexpected operation kind for operation " + operation);
                }
            } else {
                sb.append(" a built-in ");
                sb.append(operation.name);
            }
            sb.append(" operation.");
            return sb.toString();
        }

        private static String getOperationSignatureJavadoc(OperationModel operation) {
            StringBuilder result = new StringBuilder();
            result.append("Signature: ");
            result.append(operation.name);
            result.append("(");

            boolean first = true;
            for (DynamicOperandModel dynamicOperand : operation.dynamicOperands) {
                if (!first) {
                    result.append(", ");
                }
                first = false;

                boolean firstOperandName = true;
                for (String operandName : dynamicOperand.names()) {
                    if (!firstOperandName) {
                        result.append("|");
                    }
                    firstOperandName = false;
                    result.append(operandName);
                }

                if (dynamicOperand.isVariadic()) {
                    result.append("...");
                }
            }
            result.append(")");

            if (operation.kind == OperationKind.ROOT) {
                // do nothing
            } else if (operation.isTransparent) {
                result.append(" -> void/Object");
            } else if (operation.isVoid || operation.kind == OperationKind.RETURN) {
                result.append(" -> void");
            } else if (operation.isCustom()) {
                result.append(" -> ");
                result.append(ElementUtils.getSimpleName(
                                operation.instruction.signature.returnType));
            } else {
                result.append(" -> Object");
            }

            return result.toString();
        }

        private void addBeginOrEmitOperationDoc(OperationModel operation, CodeExecutableElement ex) {
            List<String> lines = new ArrayList<>(1);

            if (operation.hasChildren()) {
                lines.add(getBuilderMethodJavadocHeader("Begins", operation));
            } else {
                lines.add(getBuilderMethodJavadocHeader("Emits", operation));
            }

            lines.add("<p>");
            lines.add(getOperationSignatureJavadoc(operation));

            if (operation.javadoc != null && !operation.javadoc.isBlank()) {
                lines.add("<p>");
                for (String line : operation.javadoc.strip().split("\n")) {
                    lines.add(line);
                }
            }

            if (operation.hasChildren()) {
                lines.add("<p>");
                lines.add("A corresponding call to {@link #end" + operation.name + "} is required to end the operation.");
            }

            if (operation.operationBeginArguments.length != 0) {
                lines.add(" ");
                for (OperationArgument argument : operation.operationBeginArguments) {
                    lines.add(argument.toJavadocParam());
                }
            }

            addJavadoc(ex, lines);
        }

        private void addEndOperationDoc(OperationModel operation, CodeExecutableElement ex) {
            if (!operation.hasChildren()) {
                throw new AssertionError("tried generating end method for operation with no children");
            }

            List<String> lines = new ArrayList<>(1);
            lines.add(getBuilderMethodJavadocHeader("Ends", operation));
            lines.add("<p>");
            lines.add(getOperationSignatureJavadoc(operation));

            if (operation.kind == OperationKind.TAG) {
                lines.add("<p>");
                lines.add("The tags passed to this method should match the ones used in the corresponding {@link #beginTag} call.");
            }

            lines.add(" ");
            if (operation.operationEndArguments.length != 0) {
                for (OperationArgument argument : operation.operationEndArguments) {
                    lines.add(argument.toJavadocParam());
                }
            }
            lines.add("@see #begin" + operation.name);

            addJavadoc(ex, lines);
        }

        private void addBeginRootOperationDoc(OperationModel rootOperation, CodeExecutableElement ex) {
            if (rootOperation.kind != OperationKind.ROOT) {
                throw new AssertionError("tried generating beginRoot doc for non-root operation");
            }

            List<String> lines = new ArrayList<>(2);
            lines.add("Begins a new root node.");
            lines.add("<p>");
            lines.add(getOperationSignatureJavadoc(rootOperation));
            lines.add("<p>");
            for (String line : rootOperation.javadoc.strip().split("\n")) {
                lines.add(line);
            }

            lines.add(" ");
            for (OperationArgument operationArgument : rootOperation.operationBeginArguments) {
                lines.add(operationArgument.toJavadocParam());
            }
            if (model.prolog != null && model.prolog.operation.operationBeginArguments.length != 0) {
                for (OperationArgument operationArgument : model.prolog.operation.operationBeginArguments) {
                    lines.add(operationArgument.toJavadocParam());
                }
            }

            addJavadoc(ex, lines);
        }

        private CodeExecutableElement createBegin(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                return createBeginRoot(operation);
            }
            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "begin" + operation.builderName);

            for (OperationArgument arg : operation.operationBeginArguments) {
                ex.addParameter(arg.toVariableElement());
            }
            ex.setVarArgs(operation.operationBeginArgumentVarArgs);
            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (operation.kind == OperationKind.TAG) {
                b.startIf().string("newTags.length == 0").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("The tags parameter for beginTag must not be empty. Please specify at least one tag.").end().end();
                b.end();

                b.declaration(type(int.class), "encodedTags", "encodeTags(newTags)");
                b.startIf().string("(encodedTags & this.tags) == 0").end().startBlock();
                b.returnStatement();
                b.end();
            } else if (operation.isSourceOnly()) {
                b.startIf().string("!parseSources").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (model.enableSerialization && !operation.isInternal) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
            }

            if (operation.requiresRootOperation()) {
                b.startStatement().startCall("validateRootOperationBegin").end(2);
            }

            if (operation.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.before()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationBeginArgumentName(index++));
                }
            }

            List<String> constantOperandValues = emitConstantBeginOperands(b, operation);

            if (operation.kind == OperationKind.CUSTOM_INSTRUMENTATION) {
                int mask = 1 << operation.instrumentationIndex;
                b.startIf().string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") == 0").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (operation.isCustom() && !operation.customModel.implicitTags.isEmpty()) {
                VariableElement tagConstants = lookupTagConstant(operation.customModel.implicitTags);
                if (tagConstants != null) {
                    buildBegin(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            switch (operation.kind) {
                case ROOT:
                case BLOCK:
                    if (model.enableBlockScoping) {
                        b.declaration(operationStack.asType(), "parentScope", "getCurrentScope()");
                    }
                    break;
                case STORE_LOCAL: /* LOAD_LOCAL, CLEAR_LOCAL handled by createEmit */
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                    emitValidateLocalScope(b, operation);
                    break;
            }

            if (operation.kind != OperationKind.FINALLY_HANDLER) {
                b.startStatement().startCall("beforeChild").end(2);
            }

            if (operation.kind == OperationKind.TAG) {
                b.declaration(tagNode.asType(), "node", "new TagNode(encodedTags & this.tags, state.bci)");
                b.startIf().string("state.tagNodes == null").end().startBlock();
                b.statement("state.tagNodes = new ArrayList<>()");
                b.end();
                b.declaration(type(int.class), "nodeId", "state.tagNodes.size()");
                b.statement("state.tagNodes.add(node)");
            }

            /*
             * NB: initOperationBeginData is side-effecting: it can emit declarations that are
             * referenced by the returned CodeTree. We have to call it before we start the
             * beginOperation call.
             */
            Map<OperationField, String> initValues = initOperationBeginData(b, operation, constantOperandValues);
            b.startDeclaration(operationStack.asType(), "operation").startCall("beginOperation");
            b.tree(createOperationConstant(operation));
            b.end(2);
            b.tree(operationStack.createInitialize(operation, "operation", initValues));

            switch (operation.kind) {
                case BLOCK:
                    if (model.enableBlockScoping) {
                        b.tree(operationStack.write(operation, operationFields.frameOffset, "parentScope.getFrameOffset() + parentScope.getNumLocals()"));
                    }
                    break;
                case TAG:
                    buildEmitInstruction(b, null, model.tagEnterInstruction, "nodeId");
                    break;
                case WHILE:
                case RETURN:
                case TRY_FINALLY:
                case TRY_CATCH_OTHERWISE:
                    break;
            }

            return ex;
        }

        private CodeExecutableElement getValidateLocalScope(boolean materialized) {
            if (materialized) {
                if (validateMaterializedLocalScope == null) {
                    validateMaterializedLocalScope = createValidateLocalScope(true);
                    this.add(validateMaterializedLocalScope);
                }
                return validateMaterializedLocalScope;
            } else {
                if (validateLocalScope == null) {
                    validateLocalScope = createValidateLocalScope(false);
                    this.add(validateLocalScope);
                }
                return validateLocalScope;
            }
        }

        private CodeExecutableElement createValidateLocalScope(boolean materialized) {
            String name = materialized ? "validateMaterializedLocalScope" : "validateLocalScope";
            CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), name);
            method.addParameter(new CodeVariableElement(types.BytecodeLocal, "local"));

            CodeTreeBuilder b = method.createBuilder();

            b.startDeclaration(bytecodeLocalImpl.asType(), "localImpl");
            b.cast(bytecodeLocalImpl.asType()).string("local");
            b.end();

            if (model.enableBlockScoping) {
                b.startIf().string("localImpl.scope.sequenceNumber != localImpl.scopeSequenceNumber || !localImpl.scope.getValid()").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("Local variable scope of this local is no longer valid.").end().end();
                b.end();
            }

            if (materialized) {
                // Local must belong to the current root node or an outer root node.
                if (model.enableBlockScoping) {
                    // The scope check above suffices to check nesting.
                } else {
                    // Otherwise, ensure the local's root is on the stack.
                    b.declaration(rootStackElement.asType(), "currentState", "this.state");
                    b.startWhile().string("currentState != null").end().startBlock();
                    buildOperationStackWalk(b, "0", () -> {
                        b.startSwitch().string("operation.operation").end().startBlock();
                        b.startCase().tree(createOperationConstant(model.rootOperation)).end();
                        b.startCaseBlock();
                        b.startIf().string(operationStack.read(model.rootOperation, "operation", operationFields.index), " == localImpl.rootIndex").end().startBlock();
                        b.lineComment("root node found");
                        b.statement("return");
                        b.end();
                        b.end(); // case root
                        b.end(); // switch
                    }, "currentState");
                    b.statement("currentState = currentState.parent");
                    b.end();

                    b.startThrow().startCall("failArgument").doubleQuote(
                                    "Local variables used in materialized accesses must belong to the current root node or an outer root node.").end().end();
                }
            } else {
                // Local must belong to the current root node.
                b.declaration(operationStack.asType(), "rootOperation", "getCurrentRootOperationData()");
                b.startIf().string(operationStack.read(model.rootOperation, "rootOperation", operationFields.index), " != localImpl.rootIndex").end().startBlock();

                String materializedAccessAdvice = "Consider using materialized local accesses (i.e., LoadLocalMaterialized/StoreLocalMaterialized or MaterializedLocalAccessor) to access locals from an outer root node.";
                if (!model.enableMaterializedLocalAccesses) {
                    materializedAccessAdvice += " Materialized local accesses are currently disabled and can be enabled using the enableMaterializedLocalAccesses field of @GenerateBytecode.";
                }

                b.startThrow().startCall("failArgument").doubleQuote("Local variable must belong to the current root node. " + materializedAccessAdvice).end().end();
                b.end();
            }

            return method;
        }

        private void emitValidateLocalScope(CodeTreeBuilder b, OperationModel operation) {
            boolean materialized = operation.instruction.kind.isLocalVariableMaterializedAccess();
            emitValidateLocalScope(b, materialized, operation.getOperationBeginArgumentName(0));
        }

        private void emitValidateLocalScope(CodeTreeBuilder b, boolean materialized, String localName) {
            b.startStatement().startCall(getValidateLocalScope(materialized).getSimpleName().toString()).string(localName).end().end();
        }

        private CodeExecutableElement createBeginRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(void.class), "beginRoot");
            if (model.prolog != null && model.prolog.operation.operationBeginArguments.length != 0) {
                for (OperationArgument operationArgument : model.prolog.operation.operationBeginArguments) {
                    ex.addParameter(operationArgument.toVariableElement());
                }
            }
            addBeginRootOperationDoc(rootOperation, ex);

            CodeTreeBuilder b = ex.getBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(rootOperation, b);
                b.statement("return");
                b.end();
            }

            if (model.prolog != null) {
                for (OperationArgument operationArgument : model.prolog.operation.operationBeginArguments) {
                    buildConstantOperandValidation(b, operationArgument.builderType(), operationArgument.name());
                }
            }

            b.startIf().string("state.rootOperationSp != -1").end().startBlock(); // {
            b.statement("state = state.getNext()");
            b.end(); // }
            b.statement("state.rootOperationSp = state.operationSp");
            b.statement("state.instrumentations = instrumentations");

            Map<OperationField, String> initValues = new HashMap<>();
            initValues.put(operationFields.index, safeCastShort("numRoots++"));
            b.startDeclaration(operationStack.asType(), "operation").startCall("beginOperation");
            b.tree(createOperationConstant(rootOperation));
            b.end(2);
            b.tree(operationStack.createInitialize(rootOperation, "operation", initValues));
            b.end();

            if (model.enableInstructionTracing) {
                int mask = 1 << model.traceInstructionInstrumentationIndex;
                b.startIf().string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") != 0").end().startBlock();
                b.statement("int constantIndex = state.addConstant(findOrCreateInstructionTracer())");
                b.statement("assert constantIndex == INSTRUCTION_TRACER_CONSTANT_INDEX");
                b.end();
            }

            b.startIf().string("reparseReason == null").end().startBlock();
            b.statement("builtNodes.add(null)");
            b.startIf().string("builtNodes.size() > Short.MAX_VALUE").end().startBlock();
            emitThrowEncodingException(b, "\"Root node count exceeded maximum value.\"");
            b.end();
            b.end();

            if (model.enableBlockScoping) {
                b.tree(operationStack.write(rootOperation, operationFields.frameOffset, "state.numLocals"));
            }

            if (model.prolog != null || model.epilogExceptional != null || model.epilogReturn != null) {
                if (model.enableRootTagging) {
                    buildBegin(b, model.tagOperation, lookupTagConstant(types.StandardTags_RootTag).getSimpleName().toString());
                }

                // If prolog defined, emit prolog before Root's child.
                if (model.prolog != null) {
                    if (model.prolog.operation.operationEndArguments.length != 0) {
                        // If the prolog has end constants, we'll need to patch them in endRoot.
                        b.tree(operationStack.write(rootOperation, operationFields.prologBci, "state.bci"));
                    }

                    List<String> constantOperandValues = emitConstantOperands(b, model.prolog.operation);
                    buildEmitOperationInstruction(b, model.prolog.operation, constantOperandValues);
                }
                if (model.epilogReturn != null) {
                    buildBegin(b, model.epilogReturn.operation);
                }

                if (model.enableRootBodyTagging) {
                    buildBegin(b, model.tagOperation, lookupTagConstant(types.StandardTags_RootBodyTag).getSimpleName().toString());
                }
            } else {
                VariableElement tagConstants = getAllRootTagConstants();
                if (tagConstants != null) {
                    buildBegin(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            if (needsRootBlock()) {
                buildBegin(b, model.blockOperation);
            }

            return ex;
        }

        private boolean needsRootBlock() {
            return model.enableRootTagging || model.enableRootBodyTagging || model.epilogExceptional != null || model.epilogReturn != null;
        }

        private VariableElement getAllRootTagConstants() {
            if (model.enableRootTagging && model.enableRootBodyTagging) {
                return lookupTagConstant(types.StandardTags_RootTag, types.StandardTags_RootBodyTag);
            } else if (model.enableRootTagging) {
                return lookupTagConstant(types.StandardTags_RootTag);
            } else if (model.enableRootBodyTagging) {
                return lookupTagConstant(types.StandardTags_RootBodyTag);
            } else {
                return null;
            }
        }

        private VariableElement lookupTagConstant(TypeMirror... tags) {
            return lookupTagConstant(List.of(tags));
        }

        private VariableElement lookupTagConstant(List<TypeMirror> tags) {
            String name = "TAGS";
            for (TypeMirror type : tags) {
                name += "_" + ElementUtils.createConstantName(ElementUtils.getSimpleName(type));
            }
            VariableElement existing = this.findField(name);
            if (existing != null) {
                return existing;
            }

            CodeVariableElement newVariable = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), arrayOf(type(Class.class)), name);
            CodeTreeBuilder b = newVariable.createInitBuilder();
            b.string("new Class<?>[]{").startCommaGroup();
            for (TypeMirror type : tags) {
                b.typeLiteral(type);
            }
            b.end().string("}");

            this.add(newVariable);
            return newVariable;

        }

        private void createSerializeBegin(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {

                if (operation.kind == OperationKind.ROOT) {
                    b.startDeclaration(serializationRootNode.asType(), "node");
                    b.startNew(serializationRootNode.asType());
                    b.startStaticCall(types.FrameDescriptor, "newBuilder").end();
                    b.string("serialization.depth");
                    b.startCall("checkOverflowShort").string("serialization.rootCount++").doubleQuote("Root node count").end();
                    b.end();
                    b.end(); // declaration
                    b.statement("serialization.rootStack.push(node)");
                    b.statement("serialization.builtNodes.add(node)");
                }

                CodeTreeBuilder afterOperation = CodeTreeBuilder.createBuilder();
                for (OperationArgument argument : operation.operationBeginArguments) {
                    buildSerializeOperationArgument(b, afterOperation, argument);
                }
                serializationElements.writeShort(b, serializationElements.codeBegin[operation.id]);
                b.tree(afterOperation.build());
            });
        }

        private Map<OperationField, String> initOperationBeginData(CodeTreeBuilder b, OperationModel operation, List<String> constantOperandValues) {
            Map<OperationField, String> values = new HashMap<>();
            switch (operation.kind) {
                case ROOT:
                    throw new AssertionError("should not reach here");
                case STORE_LOCAL:
                case STORE_LOCAL_MATERIALIZED:
                case LOAD_LOCAL_MATERIALIZED:
                case LOAD_LOCAL:
                case CLEAR_LOCAL:
                    values.put(operationFields.local, "(BytecodeLocalImpl)" + operation.getOperationBeginArgumentName(0));
                    break;
                case IF_THEN:
                    values.put(operationFields.thenReachable, "state.reachable");
                    break;
                case IF_THEN_ELSE:
                case CONDITIONAL:
                    values.put(operationFields.thenReachable, "state.reachable");
                    values.put(operationFields.elseReachable, "state.reachable");
                    break;
                case WHILE:
                    values.put(operationFields.whileStartBci, "state.bci");
                    values.put(operationFields.bodyReachable, "state.reachable");
                    break;
                case TRY_CATCH:
                    values.put(operationFields.handlerId, "++state.numHandlers");
                    values.put(operationFields.stackHeight, "state.currentStackHeight");
                    values.put(operationFields.tryStartBci, "state.bci");
                    values.put(operationFields.operationReachable, "state.reachable");
                    values.put(operationFields.tryReachable, "state.reachable");
                    values.put(operationFields.catchReachable, "state.reachable");
                    break;
                case TRY_FINALLY:
                    values.put(operationFields.handlerId, "++state.numHandlers");
                    values.put(operationFields.stackHeight, "state.currentStackHeight");
                    values.put(operationFields.finallyGenerator, operation.getOperationBeginArgumentName(0));
                    values.put(operationFields.tryStartBci, "state.bci");
                    values.put(operationFields.operationReachable, "state.reachable");
                    values.put(operationFields.tryReachable, "state.reachable");
                    values.put(operationFields.catchReachable, "false");
                    break;
                case TRY_CATCH_OTHERWISE:
                    values.put(operationFields.handlerId, "++state.numHandlers");
                    values.put(operationFields.stackHeight, "state.currentStackHeight");
                    values.put(operationFields.finallyGenerator, operation.getOperationBeginArgumentName(0));
                    values.put(operationFields.tryStartBci, "state.bci");
                    values.put(operationFields.operationReachable, "state.reachable");
                    values.put(operationFields.tryReachable, "state.reachable");
                    values.put(operationFields.catchReachable, "state.reachable");
                    break;
                case FINALLY_HANDLER:
                    values.put(operationFields.finallyOperationSp, "finallyOperationSp");
                    break;
                case CUSTOM:
                case CUSTOM_YIELD:
                case CUSTOM_INSTRUMENTATION:
                    if (operation.hasConstantOperands()) {
                        List<OperationField> fields = operationFields.getConstants(operation.constantOperands.before(), false);
                        if (fields.size() != constantOperandValues.size()) {
                            throw new AssertionError("Expected %d constant operands but %d values were provided.".formatted(fields.size(), constantOperandValues.size()));
                        }
                        for (int i = 0; i < fields.size(); i++) {
                            values.put(fields.get(i), constantOperandValues.get(i));
                        }
                    }
                    break;
                case CUSTOM_SHORT_CIRCUIT:
                case RETURN:
                    break;
                case TAG:
                    values.put(operationFields.nodeId, "nodeId");
                    values.put(operationFields.operationReachable, "state.reachable");
                    values.put(operationFields.startStackHeight, "state.currentStackHeight");
                    values.put(operationFields.node, "node");
                    break;
                case BLOCK:
                    values.put(operationFields.startStackHeight, "state.currentStackHeight");
                    break;
                case SOURCE:
                    b.startIf().string(operation.getOperationBeginArgumentName(0) + ".hasBytes()").end().startBlock();
                    b.startThrow().startCall("failArgument").doubleQuote("Byte-based sources are not supported.").end(2);
                    b.end();

                    b.statement("int index = sources.indexOf(" + operation.getOperationBeginArgumentName(0) + ")");
                    b.startIf().string("index == -1").end().startBlock();
                    b.statement("index = sources.size()");
                    b.statement("sources.add(" + operation.getOperationBeginArgumentName(0) + ")");
                    b.end();

                    values.put(operationFields.sourceIndex, "index");
                    break;

                case SOURCE_SECTION:
                    b.declaration(type(int.class), "foundSourceIndex", "-1");
                    b.declaration(rootStackElement.asType(), "currentState", "this.state");
                    b.startWhile().string("currentState != null").end().startBlock();
                    b.string("loop: ");
                    // NB: walk entire operation stack, not just until root operation.
                    buildOperationStackWalk(b, "0", () -> {
                        b.startSwitch().string("currentState.operationStack[i].operation").end().startBlock();

                        b.startCase().tree(createOperationConstant(model.sourceOperation)).end();
                        b.startCaseBlock();
                        emitCastOperationData(b, model.sourceOperation, "i", "sourceData", "currentState", false);
                        b.statement("foundSourceIndex = ", operationStack.read(model.sourceOperation, "sourceData", operationFields.sourceIndex));
                        b.statement("break loop");
                        b.end(); // case epilog
                        b.end(); // switch
                    }, "currentState");
                    b.statement("currentState = currentState.parent");
                    b.end();

                    b.startIf().string("foundSourceIndex == -1").end().startBlock();
                    b.startThrow().startCall("failState").doubleQuote("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.").end().end();
                    b.end();

                    b.declaration(type(int.class), "startBci");
                    b.startIf().string("state.rootOperationSp == -1").end().startBlock();
                    b.lineComment("not in a root yet");
                    b.statement("startBci = 0");
                    b.end().startElseBlock();
                    b.statement("startBci = state.bci");
                    b.end();

                    values.put(operationFields.sourceIndex, "foundSourceIndex");
                    values.put(operationFields.startBci, "startBci");

                    if (operation.operationBeginArguments.length == 0) {
                        values.put(operationFields.start, "-2");
                        values.put(operationFields.length, "-2");
                    } else {
                        String indexName = operation.getOperationBeginArgumentName(0);
                        String lengthName = operation.getOperationBeginArgumentName(1);

                        emitValidateSourceSection(b, indexName, lengthName);

                        values.put(operationFields.start, indexName);
                        values.put(operationFields.length, lengthName);
                    }
                    break;
            }
            return values;
        }

        private CodeExecutableElement createBeginOperation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "beginOperation");
            ex.addParameter(new CodeVariableElement(type(int.class), "id"));
            CodeTreeBuilder b = ex.createBuilder();
            b.statement("return state.pushOperation(id)");
            return ex;
        }

        private CodeExecutableElement createEndOperation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "endOperation");
            ex.addParameter(new CodeVariableElement(type(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();
            b.statement("verifyOperation(id)");
            b.statement("return state.popOperation()");

            return ex;
        }

        private CodeExecutableElement createVerifyOperation() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "verifyOperation");
            ex.addParameter(new CodeVariableElement(type(int.class), "id"));

            CodeTreeBuilder b = ex.createBuilder();

            b.declaration(operationStack.asType(), "entry", "state.peekOperation()");
            b.startIf().string("entry == null").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.doubleQuote("Unexpected operation end - there are no operations on the stack. Did you forget a beginRoot()?");
            b.end(2);
            b.end(); // }

            b.startElseIf().string("entry.operation != id").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.doubleQuote("Unexpected operation end, expected end%s, but got end%s.");
            b.startStaticCall(operationsElement.asType(), "getName").string("entry.operation").end();
            b.startStaticCall(operationsElement.asType(), "getName").string("id").end();
            b.end(2); // throw, call
            b.end(); // }

            b.statement("return entry ");

            return ex;
        }

        private CodeExecutableElement createEnd(OperationModel operation) {
            if (operation.kind == OperationKind.ROOT) {
                // endRoot is handled specially.
                return createEndRoot(operation);
            }

            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "end" + operation.builderName);

            if (operation.kind == OperationKind.TAG) {
                ex.setVarArgs(true);
                ex.addParameter(new CodeVariableElement(arrayOf(context.getDeclaredType(Class.class)), "newTags"));
            } else {
                for (OperationArgument arg : operation.operationEndArguments) {
                    ex.addParameter(arg.toVariableElement());
                }
            }

            addEndOperationDoc(operation, ex);
            CodeTreeBuilder b = ex.createBuilder();

            if (operation.kind == OperationKind.TAG) {
                b.startIf().string("newTags.length == 0").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("The tags parameter for beginTag must not be empty. Please specify at least one tag.").end().end();
                b.end();
                b.declaration(type(int.class), "encodedTags", "encodeTags(newTags)");
                b.startIf().string("(encodedTags & this.tags) == 0").end().startBlock();
                b.returnStatement();
                b.end();
            } else if (operation.isSourceOnly()) {
                b.startIf().string("!parseSources").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (model.enableSerialization && !operation.isInternal) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeEnd(operation, b);
                b.statement("return");
                b.end();
            }

            if (operation.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.after()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationEndArgumentName(index++));
                }
            }

            List<String> constantOperandValues = emitConstantOperands(b, operation);

            if (operation.kind == OperationKind.CUSTOM_INSTRUMENTATION) {
                int mask = 1 << operation.instrumentationIndex;
                b.startIf().string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") == 0").end().startBlock();
                b.returnStatement();
                b.end();
            }

            switch (operation.kind) {
                case FINALLY_HANDLER:
                    b.startStatement().startCall("endOperation");
                    b.tree(createOperationConstant(operation));
                    b.end(2);
                    // FinallyHandler doesn't need to validate its children or call afterChild.
                    return ex;
                case TRY_FINALLY:
                    b.startDeclaration(operationStack.asType(), "operation").startCall("verifyOperation");
                    b.tree(createOperationConstant(operation));
                    b.end(2);
                    break;
                default:
                    b.startDeclaration(operationStack.asType(), "operation").startCall("endOperation");
                    b.tree(createOperationConstant(operation));
                    b.end(2);
                    break;
            }

            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                // Short-circuiting operations should have at least one child.
                b.startIf().string("operation.childCount == 0").end().startBlock();
                b.startThrow().startCall("failState").string("\"Operation " + operation.name + " expected at least " + childString(1) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"").end().end();
                b.end();
            } else if (operation.isVariadic && operation.numDynamicOperands() > 1) {
                // The variadic child is included in numChildren, so the operation requires
                // numChildren - 1 children at minimum.
                b.startIf().string("operation.childCount < " + (operation.numDynamicOperands() - 1)).end().startBlock();
                b.startThrow().startCall("failState").string("\"Operation " + operation.name + " expected at least " + childString(operation.numDynamicOperands() - 1) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"").end().end();
                b.end();
            } else if (!operation.isVariadic) {
                b.startIf().string("operation.childCount != " + operation.numDynamicOperands()).end().startBlock();
                b.startThrow().startCall("failState").string("\"Operation " + operation.name + " expected exactly " + childString(operation.numDynamicOperands()) +
                                ", but \" + operation.childCount + \" provided. This is probably a bug in the parser.\"").end().end();
                b.end();
            }

            switch (operation.kind) {
                case CUSTOM_SHORT_CIRCUIT:
                    InstructionModel shortCircuitInstruction = operation.instruction;
                    if (shortCircuitInstruction.shortCircuitModel.returnConvertedBoolean()) {
                        /*
                         * All operands except the last are automatically converted when testing the
                         * short circuit condition. For the last operand we need to insert a
                         * conversion.
                         */
                        buildEmitBooleanConverterInstruction(b, shortCircuitInstruction);
                    }
                    // Go through the work list and fill in the branch target for each branch.

                    b.declaration(type(int[].class), "branchFixupBcis", operationStack.read(operation, operationFields.branchFixupBcis));
                    b.declaration(type(int.class), "numBranchFixupBcis", operationStack.read(operation, operationFields.numBranchFixupBcis));

                    b.startFor().string("int i = 0; i < numBranchFixupBcis; i++").end().startBlock();
                    b.statement(writeInt("state.bc", "branchFixupBcis[i]", "state.bci"));
                    b.end();

                    break;
                case SOURCE_SECTION:

                    String index;
                    String length;
                    if (operation.operationBeginArguments.length == 0) {
                        index = operation.getOperationEndArgumentName(0);
                        length = operation.getOperationEndArgumentName(1);

                        emitValidateSourceSection(b, index, length);

                        b.startStatement().startCall("state.doPatchSourceInfo");
                        b.string("this.builtNodes");

                        b.string(operationStack.read(operation, operationFields.sourceNodeId));
                        b.string(operationStack.read(operation, operationFields.start));
                        b.string(index);
                        b.string(length);
                        b.end(2);

                        b.startStatement().startCall("state.doEmitSourceInfo");
                        b.string(operationStack.read(operation, operationFields.sourceIndex));
                        b.string(operationStack.read(operation, operationFields.startBci));
                        b.string("state.bci");
                        b.string(index);
                        b.string(length);
                        b.end(2);
                    } else {
                        b.startStatement().startCall("state.doEmitSourceInfo");
                        b.string(operationStack.read(operation, operationFields.sourceIndex));
                        b.string(operationStack.read(operation, operationFields.startBci));
                        b.string("state.bci");
                        b.string(operationStack.read(operation, operationFields.start));
                        b.string(operationStack.read(operation, operationFields.length));
                        b.end(2);
                    }
                    break;
                case SOURCE:
                    break;
                case IF_THEN_ELSE:
                    b.statement("markReachable(", operationStack.read(operation, operationFields.thenReachable), " || ", operationStack.read(operation, operationFields.elseReachable),
                                    ")");
                    break;
                case IF_THEN:
                case WHILE:
                    b.statement("updateReachable()");
                    break;
                case CONDITIONAL:
                    b.statement("markReachable(", operationStack.read(operation, operationFields.thenReachable), " || ", operationStack.read(operation, operationFields.elseReachable),
                                    ")");
                    if (model.usesBoxingElimination()) {
                        buildEmitInstruction(b, null, operation.instruction, emitMergeConditionalArguments(operation.instruction));
                    }
                    break;
                case TRY_CATCH:
                    b.statement("markReachable(", operationStack.read(operation, operationFields.tryReachable), " || ", operationStack.read(operation, operationFields.catchReachable),
                                    ")");
                    break;
                case TRY_FINALLY:
                    emitFinallyHandlersAfterTry(b, operation);
                    emitFixFinallyBranchBci(b, operation);
                    b.statement("state.popOperation()");
                    b.statement("markReachable(", operationStack.read(operation, operationFields.tryReachable), ")");
                    break;
                case TRY_CATCH_OTHERWISE:
                    b.statement("markReachable(", operationStack.read(operation, operationFields.tryReachable), " || ", operationStack.read(operation, operationFields.catchReachable),
                                    ")");
                    break;
                case RETURN:
                    String bci = "-1";
                    if (model.usesBoxingElimination()) {
                        bci = operationStack.read(operation, operationFields.childBci);
                    }
                    b.statement("beforeEmitReturn(", bci, ")");
                    buildEmitOperationInstruction(b, operation, null);
                    b.statement("markReachable(false)");
                    break;
                case TAG:
                    b.declaration(tagNode.asType(), "tagNode", operationStack.read(operation, operationFields.node));

                    b.startIf().string("(encodedTags & this.tags) != tagNode.tags").end().startBlock();
                    emitThrowIllegalArgumentException(b, "The tags provided to endTag do not match the tags provided to the corresponding beginTag call.");
                    b.end();

                    b.lineComment("If this tag operation is nested in another, add it to the outer tag tree. Otherwise, it becomes a tag root.");
                    b.declaration(operationStack.asType(), "outerTag", "findOuterTag()");

                    // Otherwise, this tag is the root of a tag tree.
                    b.startIf().string("outerTag == null").end().startBlock();
                    b.startIf().string("state.tagRoots == null").end().startBlock();
                    b.statement("state.tagRoots = new ArrayList<>(3)");
                    b.end();
                    b.statement("state.tagRoots.add(tagNode)");
                    b.end().startElseBlock(); // if !outerTagFound

                    b.startIf().string(operationStack.read(model.tagOperation, "outerTag", operationFields.tagChildren), " == null").end().startBlock();
                    b.tree(operationStack.write(model.tagOperation, "outerTag", operationFields.tagChildren, "new ArrayList<>(3)"));
                    b.end();
                    b.statement(operationStack.read(model.tagOperation, "outerTag", operationFields.tagChildren), ".add(tagNode)");

                    b.end();

                    b.declaration(arrayOf(tagNode.asType()), "children");
                    b.declaration(generic(type(List.class), tagNode.asType()), "operationChildren", operationStack.read(operation, operationFields.tagChildren));

                    // Set the children array and adopt children.
                    b.startIf().string("operationChildren == null").end().startBlock();
                    b.statement("children = TagNode.EMPTY_ARRAY");
                    b.end().startElseBlock();
                    b.statement("children = new TagNode[operationChildren.size()]");
                    b.startFor().string("int i = 0; i < children.length; i++").end().startBlock();
                    b.statement("children[i] = tagNode.insert(operationChildren.get(i))");
                    b.end();
                    b.end();

                    b.statement("tagNode.children = children");
                    b.statement("tagNode.returnBci = state.bci");

                    b.startIf().string(operationStack.read(operation, operationFields.producedValue)).end().startBlock();
                    String[] args;
                    InstructionImmediate imm = operation.instruction.getImmediate(ImmediateKind.BYTECODE_INDEX);
                    if (imm == null) {
                        args = new String[]{operationStack.read(operation, operationFields.nodeId)};
                    } else {
                        args = new String[]{operationStack.read(operation, operationFields.nodeId), operationStack.read(operation, operationFields.childBci)};
                    }

                    b.startIf().string(operationStack.read(operation, operationFields.operationReachable)).end().startBlock();
                    /*
                     * Leaving the tag leave is always reachable, because probes may decide to
                     * return at any point and we need a point where we can continue.
                     */
                    b.statement("markReachable(true)");
                    buildEmitInstruction(b, null, model.tagLeaveValueInstruction, args);
                    b.statement("state.doCreateExceptionHandler(", operationStack.read(operation, operationFields.handlerStartBci), ", state.bci, HANDLER_TAG_EXCEPTIONAL, ",
                                    operationStack.read(operation, operationFields.nodeId), ", ",
                                    operationStack.read(operation, operationFields.startStackHeight), ")");
                    b.end().startElseBlock();
                    buildEmitInstruction(b, null, model.tagLeaveValueInstruction, args);
                    b.end();

                    emitCallAfterChild(b, operation, "true", "state.bci - " + model.tagLeaveValueInstruction.getInstructionLength());

                    b.end().startElseBlock();

                    b.startIf().string(operationStack.read(operation, operationFields.operationReachable)).end().startBlock();
                    /*
                     * Leaving the tag leave is always reachable, because probes may decide to
                     * return at any point and we need a point where we can continue.
                     */
                    b.statement("markReachable(true)");
                    buildEmitInstruction(b, null, model.tagLeaveVoidInstruction, operationStack.read(operation, operationFields.nodeId));
                    b.statement("state.doCreateExceptionHandler(", operationStack.read(operation, operationFields.handlerStartBci), ", state.bci, HANDLER_TAG_EXCEPTIONAL, ",
                                    operationStack.read(operation, operationFields.nodeId),
                                    ", ", operationStack.read(operation, operationFields.startStackHeight), ")");
                    b.end().startElseBlock();
                    buildEmitInstruction(b, null, model.tagLeaveVoidInstruction, operationStack.read(operation, operationFields.nodeId));
                    b.end();

                    emitCallAfterChild(b, operation, "false", "-1");

                    b.end();

                    break;
                case BLOCK:
                    b.startIf().string("!operation.validateDeclaredLabels()").end().startBlock();
                    b.startThrow().startCall("failState");
                    b.string("\"Operation Block ended without emitting one or more declared labels.\"");
                    b.end(2); // throw, call
                    b.end();

                    if (model.enableBlockScoping) {
                        // local table entries are emitted at the end of the block.
                        emitEndBlockScope(b, operation);
                    }

                    break;
                case YIELD, CUSTOM_YIELD:
                    if (model.enableTagInstrumentation) {
                        b.statement("doEmitTagYield()");
                    }
                    buildEmitOperationInstruction(b, operation, constantOperandValues);

                    if (model.enableTagInstrumentation) {
                        b.declaration(type(int.class), "tagResumeBci", "doEmitTagResume()");
                    }
                    break;
                default:
                    if (operation.instruction != null) {
                        buildEmitOperationInstruction(b, operation, constantOperandValues);
                    }
                    break;
            }

            if (operation.kind == OperationKind.TAG) {
                // handled in tag section
            } else if (operation.isTransparent) {
                // custom transparent operations have the operation cast on the stack
                String bci = null;
                if (model.usesBoxingElimination()) {
                    bci = operationStack.read(operation, operationFields.childBci);
                }
                emitCallAfterChild(b, operation, operationStack.read(operation, operationFields.producedValue), bci);
            } else if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                b.declaration(type(int.class), "nextBci");
                b.startIf().string("operation.childCount <= 1").end().startBlock();
                b.lineComment("Single child -> boxing elimination possible");
                b.startStatement().string("nextBci = ");
                ShortCircuitInstructionModel shortCircuitModel = operation.instruction.shortCircuitModel;
                if (shortCircuitModel.returnConvertedBoolean()) {
                    // We emit a boolean converter instruction above. Compute its bci.
                    b.string("state.bci - " + shortCircuitModel.booleanConverterInstruction().getInstructionLength());
                } else {
                    // The child bci points to the instruction producing this last value.
                    String childBci = "-1";
                    if (model.usesBoxingElimination()) {
                        childBci = operationStack.read(operation, operationFields.childBci);
                    }
                    b.string(childBci);
                }
                b.end();  // statement
                b.end(); // if block

                b.startElseBlock();
                b.lineComment("Multi child -> boxing elimination not possible use short-circuit bci to disable it.");

                String shortCircuitBci = "-1";
                if (model.usesBoxingElimination()) {
                    shortCircuitBci = operationStack.read(operation, operationFields.shortCircuitBci);
                }
                b.statement("nextBci = ", shortCircuitBci);
                b.end();

                emitCallAfterChild(b, operation, "true", "nextBci");
            } else if (model.enableTagInstrumentation && (operation.kind == OperationKind.YIELD || operation.kind == OperationKind.CUSTOM_YIELD)) {
                // The "childBci" can change depending on whether tag.resume was emitted.
                // We don't BE yields/tag.resume but the BE machinery needs a valid bci.
                emitCallAfterChild(b, operation, "true", "tagResumeBci != -1 ? tagResumeBci : state.bci - " + operation.instruction.getInstructionLength());
            } else {
                String nextBci;
                if (operation.instruction != null) {
                    nextBci = "state.bci - " + operation.instruction.getInstructionLength();
                } else {
                    nextBci = "-1";
                }
                emitCallAfterChild(b, operation, String.valueOf(!operation.isVoid), nextBci);
            }

            if (operation.isCustom() && !operation.customModel.implicitTags.isEmpty()) {
                VariableElement tagConstants = lookupTagConstant(operation.customModel.implicitTags);
                if (tagConstants != null) {
                    buildEnd(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            return ex;
        }

        private CodeExecutableElement createFindOuterTag() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "findOuterTag");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(boolean.class), "outerTagFound", "false");
            buildOperationStackWalk(b, () -> {
                b.startIf().string("operation.operation == ").tree(createOperationConstant(model.tagOperation)).end().startBlock();
                b.statement("return operation");
                b.end(); // if tag operation
            });

            b.statement("return null");

            return ex;
        }

        private void emitValidateSourceSection(CodeTreeBuilder b, String index, String length) {
            b.startIf().string(index, " != -1 && ", length, " != -1").end().startBlock();

            b.startIf().string(index, " < 0 ").end().startBlock();
            b.startThrow().startNew(type(IllegalArgumentException.class));
            b.startGroup();
            b.doubleQuote("Invalid " + index + " provided:").string(" + ", index);
            b.end();
            b.end().end();
            b.end(); // block

            b.startIf().string(length, " < 0").end().startBlock();
            b.startThrow().startNew(type(IllegalArgumentException.class));
            b.startGroup();
            b.doubleQuote("Invalid " + length + " provided:").string(" + ", index);
            b.end();
            b.end().end();
            b.end(); // block

            b.end(); // block
        }

        private void emitCallAfterChild(CodeTreeBuilder b, OperationModel op, String producedValue, String childBci) {
            b.startStatement().startCall("afterChild");
            b.tree(createOperationConstant(op));
            b.string(producedValue);
            if (model.usesBoxingElimination()) {
                b.string(childBci);
            }
            b.end(2);
        }

        private void createSerializeEnd(OperationModel operation, CodeTreeBuilder b) {
            serializationWrapException(b, () -> {
                CodeTreeBuilder afterCode = CodeTreeBuilder.createBuilder();
                for (OperationArgument argument : operation.operationEndArguments) {
                    buildSerializeOperationArgument(b, afterCode, argument);
                }
                serializationElements.writeShort(b, serializationElements.codeEnd[operation.id]);
                b.tree(afterCode.build());
            });
        }

        private void emitEndBlockScope(CodeTreeBuilder b, OperationModel operation) {
            b.startIf().string(operationStack.read(operation, operationFields.numLocals), " > 0").end().startBlock();

            b.statement("state.maxLocals = Math.max(state.maxLocals, ", operationStack.read(operation, operationFields.frameOffset), " + ",
                            operationStack.read(operation, operationFields.numLocals), ")");
            b.startFor().string("int index = 0; index < ", operationStack.read(operation, operationFields.numLocals), "; index++").end().startBlock();

            b.statement("state.locals[", operationStack.read(operation, operationFields.locals), "[index] + LOCALS_OFFSET_END_BCI] = state.bci");
            if (operation.kind == OperationKind.BLOCK) {
                buildEmitInstruction(b, null, model.clearLocalInstruction,
                                safeCastShort("state.locals[" + operationStack.read(operation, operationFields.locals) + "[index] + LOCALS_OFFSET_FRAME_INDEX]"));
            }
            b.end(); // for
            b.end(); // block
            b.tree(operationStack.write(operation, operationFields.valid, "false"));
        }

        private CodeExecutableElement createEndRoot(OperationModel rootOperation) {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), model.templateType.asType(), "endRoot");
            String javadoc = """
                            Finishes generating bytecode for the current root node.
                            <p>
                            """;

            javadoc += getOperationSignatureJavadoc(rootOperation) + "\n\n";
            if (model.prolog != null) {
                for (OperationArgument operationArgument : model.prolog.operation.operationEndArguments) {
                    ex.addParameter(operationArgument.toVariableElement());
                    javadoc += operationArgument.toJavadocParam() + "\n";
                }
            }

            javadoc += "@returns the root node with generated bytecode.\n";
            addJavadoc(ex, javadoc);

            CodeTreeBuilder b = ex.getBuilder();

            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                serializationWrapException(b, () -> {
                    serializationElements.writeShort(b, serializationElements.codeEnd[rootOperation.id]);
                    b.startDeclaration(serializationRootNode.asType(), "result");
                    b.string("serialization.", serializationElements.rootStack.getSimpleName().toString(), ".pop()");
                    b.end();
                    serializationElements.writeInt(b, CodeTreeBuilder.singleString("result.contextDepth"));
                    serializationElements.writeInt(b, CodeTreeBuilder.singleString("result.rootIndex"));
                    b.statement("return result");
                });
                b.end();
            }

            if (needsRootBlock()) {
                emitCastOperationData(b, model.blockOperation, "state.operationSp - 1", "blockOperation");
                b.startIf().string("!", operationStack.read(model.blockOperation, "blockOperation", operationFields.producedValue)).end().startBlock();
                buildEmit(b, model.loadNullOperation);
                b.end();
                buildEnd(b, model.blockOperation);
                emitCastOperationData(b, model.rootOperation, "state.rootOperationSp");
            } else {
                emitCastOperationData(b, model.rootOperation, "state.rootOperationSp");
                b.startIf().string("!", operationStack.read(model.blockOperation, operationFields.producedValue)).end().startBlock();
                buildEmit(b, model.loadNullOperation);
                b.end();
            }

            b.startIf().string("!state.operationStack[state.rootOperationSp].validateDeclaredLabels()").end().startBlock();
            b.startThrow().startCall("failState");
            b.string("\"Operation Root ended without emitting one or more declared labels.\"");
            b.end(2); // throw, call
            b.end();

            if (model.prolog != null || model.epilogExceptional != null || model.epilogReturn != null) {
                if (model.prolog != null) {
                    // Patch the end constants.
                    OperationModel prologOperation = model.prolog.operation;
                    List<ConstantOperandModel> after = prologOperation.constantOperands.after();
                    if (prologOperation.operationEndArguments.length != after.size()) {
                        throw new AssertionError("The prolog operation has %d arguments, but there are %d constant operands specified at the end.".formatted(
                                        prologOperation.operationEndArguments.length, after.size()));
                    }
                    for (OperationArgument operationArgument : prologOperation.operationEndArguments) {
                        buildConstantOperandValidation(b, operationArgument.builderType(), operationArgument.name());
                    }
                    for (int i = 0; i < prologOperation.operationEndArguments.length; i++) {
                        String constantOperandValue = emitConstantOperand(b, prologOperation.operationEndArguments[i], prologOperation.constantOperandAfterNames.get(i));
                        InstructionImmediate immediate = prologOperation.instruction.constantOperandImmediates.get(after.get(i));
                        b.statement(writeImmediate("state.bc", operationStack.read(rootOperation, operationFields.prologBci), constantOperandValue, immediate.encoding()));
                    }
                }

                if (model.enableRootBodyTagging) {
                    buildEnd(b, model.tagOperation, lookupTagConstant(types.StandardTags_RootBodyTag).getSimpleName().toString());
                }

                if (model.epilogReturn != null) {
                    buildEnd(b, model.epilogReturn.operation);
                }

                if (model.epilogExceptional != null) {
                    b.lineComment("Emit epilog special exception handler");
                    b.statement("state.doCreateExceptionHandler(0, state.bci, HANDLER_EPILOG_EXCEPTIONAL, -1, -1)");
                }

                if (model.enableRootTagging) {
                    buildEnd(b, model.tagOperation, lookupTagConstant(types.StandardTags_RootTag).getSimpleName().toString());
                }
            } else {
                VariableElement tagConstants = getAllRootTagConstants();
                if (tagConstants != null) {
                    buildEnd(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            buildEmitOperationInstruction(b, model.returnOperation, null);

            b.startStatement().startCall("endOperation");
            b.tree(createOperationConstant(rootOperation));
            b.end(2);

            if (model.enableBlockScoping) {
                emitEndBlockScope(b, rootOperation);
            }

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.defaultDeclaration(e.asType(), e.getSimpleName().toString() + "_");
            }

            b.statement("doEmitRootSourceSection(", operationStack.read(model.rootOperation, operationFields.index), ")");
            b.startIf().string("parseSources").end().startBlock();
            CodeTree copyOf = CodeTreeBuilder.createBuilder().startStaticCall(type(Arrays.class), "copyOf").string("state.sourceInfo").string("state.sourceInfoIndex").end().build();
            b.startAssign("sourceInfo_").tree(copyOf).end();
            b.startAssign("sources_").string("sources").end();
            b.end();

            b.startIf().string("parseBytecodes").end().startBlock();

            b.startAssign("bytecodes_").startStaticCall(type(Arrays.class), "copyOf").string("state.bc").string("state.bci").end().end();
            b.startAssign("constants_").string("state.toConstants()").end();
            b.startAssign("handlers_").startStaticCall(type(Arrays.class), "copyOf").string("state.handlerTable").string("state.handlerTableSize").end().end();
            b.startAssign("sources_").string("sources").end();
            b.startAssign("numNodes_").string("state.numNodes").end();
            b.startAssign("locals_").string("state.locals == null ? " + EMPTY_INT_ARRAY + " : ").startStaticCall(type(Arrays.class), "copyOf").string("state.locals").string(
                            "state.localsTableIndex").end().end();
            b.end().startElseBlock();
            b.statement("state.toConstants()");
            b.end();

            if (model.enableTagInstrumentation) {
                b.startIf().string("tags != 0 && state.tagNodes != null").end().startBlock();
                b.startDeclaration(arrayOf(tagNode.asType()), "tagNodes_").string("state.tagNodes.toArray(TagNode[]::new)").end();

                b.declaration(tagNode.asType(), "tagTree_");

                b.startAssert().string("!state.tagRoots.isEmpty()").end();
                b.startIf().string("state.tagRoots.size() == 1").end().startBlock();
                b.startAssign("tagTree_").string("state.tagRoots.get(0)").end();
                b.end().startElseBlock();
                b.startAssign("tagTree_").startNew(tagNode.asType());
                b.string("0").string("-1");
                b.end().end();
                b.statement("tagTree_.children = tagTree_.insert(state.tagRoots.toArray(TagNode[]::new))");
                b.end();

                b.startAssign("tagRoot_");
                b.startNew(tagRootNode.asType());
                b.string("tagTree_");
                b.string("tagNodes_");
                b.end();
                b.end();

                b.end();
            }

            b.declaration(BytecodeRootNodeElement.this.asType(), "result", (CodeTree) null);

            if (model.hasYieldOperation()) {
                b.declaration(type(int[].class), "continuations", "state.continuations");
                b.declaration(type(int.class), "continuationsIndex", "state.continuationsIndex");
            }

            b.startIf().string("reparseReason != null").end().startBlock(); // {
            b.statement("result = builtNodes.get(", operationStack.read(model.rootOperation, operationFields.index), ")");

            b.startIf().string("parseBytecodes").end().startBlock();
            b.declaration(abstractBytecodeNode.asType(), "oldBytecodeNode", "result.bytecode");
            b.statement("assert result.maxLocals == " + maxLocals());
            b.statement("assert result.nodes == this.nodes");
            b.startAssert();
            b.string("result.getFrameDescriptor().getNumberOfSlots() == ");
            buildFrameSize(b);
            b.end();

            if (model.hasYieldOperation()) {

                if (model.enableInstructionTracing) {
                    b.declaration(type(int.class), "oldConstantOffset", "oldBytecodeNode.isInstructionTracingEnabled() ? 1 : 0");
                    b.startDeclaration(type(int.class), "newConstantOffset");
                    int mask = 1 << model.traceInstructionInstrumentationIndex;
                    b.string("(this.instrumentations & ").string("0x", Integer.toHexString(mask)).string(") != 0 ? 1 : 0");
                    b.end(); // delcaration
                    b.statement("assert constants_.length - newConstantOffset == oldBytecodeNode.constants.length - oldConstantOffset");
                } else {
                    b.statement("assert constants_.length == oldBytecodeNode.constants.length");
                }

                /**
                 * Copy ContinuationRootNodes into new constant array *before* we update the new
                 * bytecode, otherwise a racy thread may read it as null
                 */
                b.startFor().string("int i = 0; i < continuationsIndex; i = i + CONTINUATION_LENGTH").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuations[i + CONTINUATION_OFFSET_CPI]");
                if (model.enableInstructionTracing) {
                    b.lineComment("The constant offset is 1 with instruction tracing enabled. See INSTRUCTION_TRACER_CONSTANT_INDEX.");
                    b.lineComment("We need to align constant indices for the continuation root node updates.");
                    b.declaration(type(int.class), "oldConstantPoolIndex", "constantPoolIndex - newConstantOffset + oldConstantOffset");
                } else {
                    b.declaration(type(int.class), "oldConstantPoolIndex", "constantPoolIndex");
                }
                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode");
                b.cast(continuationRootNodeImpl.asType()).string("oldBytecodeNode.constants[oldConstantPoolIndex]");

                b.end();

                b.startStatement().startCall("ACCESS.writeObject");
                b.string("constants_");
                b.string("constantPoolIndex");
                b.string("continuationRootNode");
                b.end(2);
                b.end();
            }

            b.end();

            if (model.hasYieldOperation()) {
                b.startDeclaration(abstractBytecodeNode.asType(), "bytecodeNode");
            } else {
                b.startStatement();
            }
            b.startCall("result", "updateBytecode");
            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "_");
            }
            b.string("this.reparseReason");
            if (model.hasYieldOperation()) {
                b.string("continuations");
                b.string("continuationsIndex");
            }
            b.end();
            b.end();

            b.startAssert().string("result.buildIndex == ", operationStack.read(model.rootOperation, operationFields.index)).end();

            b.end().startElseBlock(); // } {

            b.startDeclaration(types.FrameDescriptor_Builder, "frameDescriptorBuilder").startStaticCall(types.FrameDescriptor, "newBuilder").end().end();

            if (model.defaultLocalValueExpression != null) {
                b.statement("frameDescriptorBuilder.defaultValue(DEFAULT_LOCAL_VALUE)");
            } else {
                b.statement("frameDescriptorBuilder.defaultValueIllegal()");
            }
            b.statement("frameDescriptorBuilder.useSlotKinds(false)");

            b.startStatement().startCall("frameDescriptorBuilder.addSlots");
            b.startGroup();
            buildFrameSize(b);
            b.end();
            b.end().end(); // call, statement

            b.startAssign("result").startNew(BytecodeRootNodeElement.this.asType());
            b.string("language");
            b.string("frameDescriptorBuilder");
            b.string("nodes"); // BytecodeRootNodesImpl
            b.string(maxLocals());
            if (model.usesBoxingElimination()) {
                b.string("state.numLocals");
            }
            b.string(operationStack.read(model.rootOperation, operationFields.index));

            for (VariableElement e : ElementFilter.fieldsIn(abstractBytecodeNode.getEnclosedElements())) {
                b.string(e.getSimpleName().toString() + "_");
            }
            b.end(2);

            if (model.hasYieldOperation()) {
                b.declaration(types.BytecodeNode, "bytecodeNode", "result.getBytecodeNode()");

                b.startFor().string("int i = 0; i < continuationsIndex; i = i + CONTINUATION_LENGTH").end().startBlock();
                b.declaration(type(int.class), "constantPoolIndex", "continuations[i + CONTINUATION_OFFSET_CPI]");
                b.declaration(type(int.class), "continuationBci", "continuations[i + CONTINUATION_OFFSET_BCI]");
                // Convert the relative sp to an absolute index in the frame.
                b.declaration(type(int.class), "continuationSp", "continuations[i + CONTINUATION_OFFSET_SP] + " + maxLocals());

                b.declaration(types.BytecodeLocation, "location");
                b.startIf().string("continuationBci == -1").end().startBlock();
                b.statement("location = null");
                b.end().startElseBlock();
                b.startAssign("location").string("bytecodeNode.getBytecodeLocation(continuationBci)").end();
                b.end(); // block

                b.startDeclaration(continuationRootNodeImpl.asType(), "continuationRootNode").startNew(continuationRootNodeImpl.asType());
                b.string("language");
                b.string("result.getFrameDescriptor()");
                b.string("result");
                b.string("continuationSp");
                b.string("location");
                b.end(2);

                b.startStatement().startCall("ACCESS.writeObject");
                b.string("constants_");
                b.string("constantPoolIndex");
                b.string("continuationRootNode");
                b.end(2);

                b.end();
            }

            b.startAssert().string(operationStack.read(model.rootOperation, operationFields.index), " <= numRoots").end();
            b.statement("builtNodes.set(", operationStack.read(model.rootOperation, operationFields.index), ", result)");

            b.end(); // }

            b.statement("state.reset()");

            b.startIf().string("state.parent != null").end().startBlock(); // {
            b.statement("state = state.parent");
            b.end();

            b.startReturn().string("result").end();
            return ex;
        }

        private void buildFrameSize(CodeTreeBuilder b) {
            b.string("state.maxStackHeight + ").string(maxLocals());
        }

        private String maxLocals() {
            if (model.enableBlockScoping) {
                return "state.maxLocals + USER_LOCALS_START_INDEX";
            } else {
                return "state.numLocals + USER_LOCALS_START_INDEX";
            }
        }

        private void buildBegin(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("begin" + operation.builderName);
            for (String arg : args) {
                b.string(arg);
            }
            b.end(2);
        }

        private void buildEnd(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("end" + operation.builderName);
            for (String arg : args) {
                b.string(arg);
            }
            b.end(2);
        }

        private void buildEmit(CodeTreeBuilder b, OperationModel operation, String... args) {
            b.startStatement().startCall("emit" + operation.builderName);
            for (String arg : args) {
                b.string(arg);
            }
            b.end(2);
        }

        /**
         * Generates code to walk the "logical" operation stack. If we're currently emitting a
         * finally handler (marked by a FinallyHandler operation), skips past the
         * TryFinally/TryCatchOtherwise operation.
         *
         * The supplied Runnable contains the loop body and can use "i" to reference the current
         * index.
         *
         * Note: lowerLimit is inclusive (iteration will include lowerLimit).
         */
        private void buildOperationStackWalk(CodeTreeBuilder b, String lowerLimit, Runnable r) {
            buildOperationStackWalk(b, lowerLimit, r, "state");
        }

        private void buildOperationStackWalk(CodeTreeBuilder b, String lowerLimit, Runnable r, String state) {
            b.startFor().string("int i = ", state, ".operationSp - 1; i >= ", lowerLimit, "; i--").end().startBlock();
            b.declaration(operationStack.asType(), "operation", state + ".operationStack[i]");
            b.startIf().string("operation.operation == ").tree(createOperationConstant(model.finallyHandlerOperation)).end().startBlock();
            b.startAssign("i").string(operationStack.read(List.of(model.finallyHandlerOperation), operationFields.finallyOperationSp)).end();
            b.statement("continue");
            b.end(); // if

            r.run();

            b.end(); // for
        }

        /**
         * Common case for a operation stack walks; walks until we hit the current root operation.
         */
        private void buildOperationStackWalk(CodeTreeBuilder b, Runnable r) {
            buildOperationStackWalk(b, "state.rootOperationSp", r);
        }

        /**
         * Like {@link #buildOperationStackWalk(CodeTreeBuilder, String, Runnable)}, but walks from
         * the bottom of the operation stack. Uses the {@code finallyHandlerSp} field on
         * {@code TryFinallyData} to skip "try" operations when a finally handler is being emitted
         * in-line.
         *
         * Note: lowerLimit is inclusive (iteration will start from lowerLimit).
         */
        private void buildOperationStackWalkFromBottom(CodeTreeBuilder b, String lowerLimit, Runnable r) {
            b.startFor().string("int i = ", lowerLimit, "; i < state.operationSp; i++").end().startBlock();
            b.declaration(operationStack.asType(), "operation", "state.operationStack[i]");
            b.startSwitch().string("operation.operation").end().startBlock();
            b.startCase().tree(createOperationConstant(model.tryFinallyOperation)).end();
            b.startCase().tree(createOperationConstant(model.tryCatchOtherwiseOperation)).end();
            b.startCaseBlock();
            b.declaration(type(int.class), "finallyHandlerSp", operationStack.read(List.of(model.tryFinallyOperation, model.tryFinallyOperation), operationFields.finallyHandlerSp));
            b.startIf().string("finallyHandlerSp != ", UNINIT).end().startBlock();
            b.statement("i = finallyHandlerSp - 1");
            b.statement("continue");
            b.end(); // if finallyHandlerSp set

            b.statement("break");

            b.end(); // case block
            b.caseDefault().startCaseBlock();
            b.statement("break");
            b.end();

            b.end(); // switch

            r.run();

            b.end(); // for
        }

        private void emitCastOperationData(CodeTreeBuilder b, OperationModel operation, String sp) {
            emitCastOperationData(b, operation, sp, "operation");
        }

        private void emitCastOperationData(CodeTreeBuilder b, OperationModel operation, String sp, String localName) {
            emitCastOperationData(b, operation, sp, localName, "state", true);
        }

        private void emitCastOperationData(CodeTreeBuilder b, OperationModel operation, String sp, String localName, String stateName, boolean checkOperation) {
            b.startDeclaration(operationStack.asType(), localName);
            b.string(stateName, ".operationStack[", sp, "]");
            b.end();
            if (checkOperation) {
                b.startAssert().string(localName, ".operation == ").tree(createOperationConstant(operation)).end();
            }
        }

        /**
         * Produces code to emit finally handler(s) after the try block. Expects the finally
         * operation be on the top of the operation stack (at operationSp - 1).
         *
         * For TryFinally, emits both regular and exceptional handlers; for TryCatchOtherwise, just
         * emits the regular handler.
         *
         * NB: each call to doEmitFinallyHandler must happen regardless of reachability so that the
         * frame and constant pool layouts are consistent across reparses.
         */
        private void emitFinallyHandlersAfterTry(CodeTreeBuilder b, OperationModel op) {
            b.declaration(type(int.class), "handlerSp", "state.currentStackHeight + 1 /* reserve space for the exception */");
            b.statement("state.updateMaxStackHeight(handlerSp)");
            b.declaration(type(int.class), "exHandlerIndex", UNINIT);

            b.startIf().string(operationStack.read(op, operationFields.operationReachable)).end().startBlock();
            b.lineComment("register exception table entry");
            b.statement("exHandlerIndex = state.doCreateExceptionHandler(", operationStack.read(op, operationFields.tryStartBci), ", state.bci, HANDLER_CUSTOM, -",
                            operationStack.read(op, operationFields.handlerId), ", handlerSp)");
            b.end();

            b.lineComment("emit handler for normal completion case");
            b.statement("doEmitFinallyHandler(operation, state.operationSp - 1)");
            b.lineComment("the operation was popped, so manually update reachability. try is reachable if neither it nor the finally handler exited early.");

            b.tree(operationStack.write(op, operationFields.tryReachable, operationStack.read(op, operationFields.tryReachable) + " && state.reachable"));

            b.startIf().string("state.reachable").end().startBlock();
            buildEmitInstruction(b, "branchTargetBci", model.branchInstruction, new String[]{UNINIT});
            b.tree(operationStack.write(op, operationFields.endBranchFixupBci, "branchTargetBci + " + model.branchInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset()));
            b.end();

            b.startIf().string(operationStack.read(op, operationFields.operationReachable)).end().startBlock();
            b.lineComment("update exception table; force handler code to be reachable");
            b.statement("state.reachable = true");

            b.startStatement().startCall("state.patchHandlerTable");
            b.string(operationStack.read(op, operationFields.extraTableEntriesStart));
            b.string(operationStack.read(op, operationFields.extraTableEntriesEnd));
            b.string(operationStack.read(op, operationFields.handlerId));
            b.string("state.bci");
            b.string("handlerSp");
            b.end(2);

            b.startIf().string("exHandlerIndex != ", UNINIT).end().startBlock();
            b.statement("state.handlerTable[exHandlerIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = state.bci");
            b.end();

            b.end(); // if operationReachable

            if (op.kind != OperationKind.TRY_CATCH_OTHERWISE) {
                b.lineComment("emit handler for exceptional case");
                b.statement("state.currentStackHeight = handlerSp");
                b.statement("doEmitFinallyHandler(operation, state.operationSp - 1)");
                buildEmitInstruction(b, null, model.throwInstruction);
            }
        }

        /**
         * Produces code to patch the regular finally handler's branch over the exceptional handler.
         */
        private void emitFixFinallyBranchBci(CodeTreeBuilder b, OperationModel op) {
            // The regular handler branches over the exceptional handler. Patch its bci.
            b.startIf().string(operationStack.read(op, operationFields.endBranchFixupBci), " != ", UNINIT).end().startBlock();
            b.statement(writeInt("state.bc", operationStack.read(op, operationFields.endBranchFixupBci), "state.bci"));
            b.end();
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, List<String> constantOperandValues) {
            buildEmitOperationInstruction(b, operation, null, constantOperandValues);
        }

        private void buildEmitOperationInstruction(CodeTreeBuilder b, OperationModel operation, String customChildBci, List<String> constantOperandValues) {
            String[] args = switch (operation.kind) {
                case LOAD_LOCAL -> {
                    List<String> immediates = new ArrayList<>();
                    immediates.add("((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").frameIndex");
                    if (model.localAccessesNeedLocalIndex()) {
                        immediates.add("((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").localIndex");
                    }
                    yield immediates.toArray(String[]::new);
                }
                case STORE_LOCAL -> {
                    List<String> immediates = new ArrayList<>();
                    immediates.add(operationStack.read(operation, operationFields.local) + ".frameIndex");
                    if (model.localAccessesNeedLocalIndex()) {
                        immediates.add(operationStack.read(operation, operationFields.local) + ".localIndex");
                    }
                    if (model.usesBoxingElimination()) {
                        immediates.add(operationStack.read(operation, operationFields.childBci));
                    }
                    yield immediates.toArray(String[]::new);
                }
                case CLEAR_LOCAL -> new String[]{"((BytecodeLocalImpl) " + operation.getOperationBeginArgumentName(0) + ").frameIndex"};
                case STORE_LOCAL_MATERIALIZED -> {
                    List<String> immediates = new ArrayList<>();
                    immediates.add(operationStack.read(operation, operationFields.local) + ".frameIndex");
                    immediates.add(operationStack.read(operation, operationFields.local) + ".rootIndex");
                    if (model.materializedLocalAccessesNeedLocalIndex()) {
                        immediates.add(operationStack.read(operation, operationFields.local) + ".localIndex");
                    }
                    if (model.usesBoxingElimination()) {
                        immediates.add(operationStack.read(operation, operationFields.childBci));
                    }
                    yield immediates.toArray(String[]::new);
                }
                case LOAD_LOCAL_MATERIALIZED -> {
                    List<String> immediates = new ArrayList<>();
                    immediates.add(operationStack.read(operation, operationFields.local) + ".frameIndex");
                    immediates.add(operationStack.read(operation, operationFields.local) + ".rootIndex");
                    if (model.materializedLocalAccessesNeedLocalIndex()) {
                        immediates.add(operationStack.read(operation, operationFields.local) + ".localIndex");
                    }
                    yield immediates.toArray(String[]::new);
                }
                case RETURN, LOAD_NULL -> new String[]{};
                case LOAD_ARGUMENT -> new String[]{safeCastShort(operation.getOperationBeginArgumentName(0))};
                case LOAD_CONSTANT -> new String[]{"state.addConstant(" + operation.getOperationBeginArgumentName(0) + ")"};
                case YIELD -> {
                    b.declaration(type(short.class), "constantPoolIndex", "state.allocateContinuationConstant()");
                    yield new String[]{"constantPoolIndex"};
                }
                case CUSTOM, CUSTOM_YIELD, CUSTOM_INSTRUMENTATION -> buildCustomInitializer(b, operation, operation.instruction, customChildBci, constantOperandValues);
                case CUSTOM_SHORT_CIRCUIT -> throw new AssertionError("Tried to emit a short circuit instruction directly. These operations should only be emitted implicitly.");
                default -> throw new AssertionError("Reached an operation " + operation.name + " that cannot be initialized. This is a bug in the Bytecode DSL processor.");
            };

            switch (operation.kind) {
                case CUSTOM_YIELD:
                case YIELD:
                    buildEmitInstruction(b, "continuationBci", operation.instruction, args);
                    b.startStatement().startCall("state.doEmitContinuation");
                    b.string("constantPoolIndex").string("continuationBci != -1 ? continuationBci + " + operation.instruction.getInstructionLength() + " : -1");
                    b.string("state.currentStackHeight");
                    b.end();
                    b.end(2); // statement + call
                    b.end();
                    break;
                default:
                    buildEmitInstruction(b, null, operation.instruction, args);
                    break;
            }

        }

        private void buildEmitLabel(CodeTreeBuilder b, OperationModel operation) {
            b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationBeginArgumentName(0)).end();

            b.startIf().string("labelImpl.isDefined()").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("BytecodeLabel already emitted. Each label must be emitted exactly once.").end().end();
            b.end();

            b.startIf().string("labelImpl.declaringOp != state.peekOperation().sequenceNumber").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("BytecodeLabel must be emitted inside the same operation it was created in.").end().end();
            b.end();

            b.declaration(operationStack.asType(), "operation", "state.peekOperation()");

            b.startIf().string("operation.operation == ").tree(createOperationConstant(model.blockOperation)).end().startBlock();
            b.startAssert().string("state.currentStackHeight == ", operationStack.read(model.blockOperation, "operation", operationFields.startStackHeight)).end();
            b.end().startElseBlock();
            b.startAssert().string("operation.operation == ").tree(createOperationConstant(model.rootOperation)).end();
            b.startAssert().string("state.currentStackHeight == 0").end();
            b.end();

            b.startStatement().startCall("state.resolveUnresolvedLabel");
            b.string("labelImpl");
            b.string("state.currentStackHeight");
            b.end(2);
        }

        private void buildEmitBranch(CodeTreeBuilder b, OperationModel operation) {
            b.startAssign("BytecodeLabelImpl labelImpl").string("(BytecodeLabelImpl) " + operation.getOperationBeginArgumentName(0)).end();

            b.declaration(type(int.class), "declaringOperationSp", UNINIT);
            buildOperationStackWalk(b, () -> {
                b.startIf().string("operation.sequenceNumber == labelImpl.declaringOp").end().startBlock();
                b.statement("declaringOperationSp = i");
                b.statement("break");
                b.end();
            });

            /**
             * To keep branches reasonable, require them to target a label defined in the same
             * operation or an enclosing one.
             */
            b.startIf().string("declaringOperationSp == ", UNINIT).end().startBlock();
            b.startThrow().startCall("failState").doubleQuote(
                            "Branch must be targeting a label that is declared in an enclosing operation of the current root. Jumps into other operations are not permitted.").end().end();
            b.end();

            b.startIf().string("labelImpl.isDefined()").end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("Backward branches are unsupported. Use a While operation to model backward control flow.").end().end();
            b.end();

            b.declaration(type(int.class), "targetStackHeight");

            b.declaration(operationStack.asType(), "operation", "state.operationStack[declaringOperationSp]");

            b.startIf().string("operation.operation == ").tree(createOperationConstant(model.blockOperation)).end().startBlock();
            b.startAssign("targetStackHeight").string(operationStack.read(model.blockOperation, "operation", operationFields.startStackHeight)).end();
            b.end().startElseBlock();
            b.startAssert().string("operation.operation == ").tree(createOperationConstant(model.rootOperation)).end();
            b.startAssign("targetStackHeight").string("0").end();
            b.end();

            b.statement("beforeEmitBranch(declaringOperationSp)");

            /**
             * If the label sp doesn't match the current sp, we need to pop before branching.
             */
            b.lineComment("Pop any extra values off the stack before branching.");
            b.declaration(type(int.class), "stackHeightBeforeBranch", "state.currentStackHeight");
            b.startWhile().string("targetStackHeight != state.currentStackHeight").end().startBlock();
            buildEmitInstruction(b, null, model.popInstruction, emitPopArguments("-1"));
            b.end();
            b.lineComment("If the branch is not taken (e.g., control branches over it) the values are still on the stack.");
            b.statement("state.currentStackHeight = stackHeightBeforeBranch");

            b.startIf().string("state.reachable").end().startBlock();

            buildEmitInstruction(b, "branchTargetBci", model.branchInstruction, UNINIT);
            /**
             * Mark the branch target as uninitialized. Add this location to a work list to be
             * processed once the label is defined.
             */
            b.startStatement().startCall("state.registerUnresolvedLabel");
            b.string("labelImpl");
            b.string("branchTargetBci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset());
            b.end(2);
            b.end(); // if reachable
        }

        private void buildEmitLoadException(CodeTreeBuilder b, OperationModel operation) {
            b.declaration(type(int.class), "exceptionStackHeight", UNINIT);
            b.string("loop: ");
            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();
                b.startCase().tree(createOperationConstant(model.tryCatchOperation)).end();
                b.startCaseBlock();
                b.startIf().string("operation.childCount == 1").end().startBlock();
                b.statement("exceptionStackHeight = ", operationStack.read(model.tryCatchOperation, operationFields.stackHeight));
                b.statement("break loop");
                b.end();
                b.statement("break");
                b.end(); // case TryCatch

                b.startCase().tree(createOperationConstant(model.tryCatchOtherwiseOperation)).end();
                b.startCaseBlock();
                b.startIf().string("operation.childCount == 1").end().startBlock();
                b.statement("exceptionStackHeight = ", operationStack.read(model.tryCatchOtherwiseOperation, operationFields.stackHeight));
                b.statement("break loop");
                b.end();
                b.statement("break");
                b.end(); // case TryCatchOtherwise

                b.end(); // switch
            });

            b.startIf().string("exceptionStackHeight == ", UNINIT).end().startBlock();
            b.startThrow().startCall("failState").doubleQuote("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.").end().end();
            b.end();

            buildEmitInstruction(b, null, operation.instruction, "safeCastShort(exceptionStackHeight)");
        }

        private CodeExecutableElement createValidateRootOperationBegin() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "validateRootOperationBegin");

            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("state.rootOperationSp == -1").end().startBlock(); // {
            b.startThrow().startCall("failState");
            b.doubleQuote("Unexpected operation emit - no root operation present. Did you forget a beginRoot()?");
            b.end(2);
            b.end(); // }

            return ex;
        }

        private CodeExecutableElement createGetCurrentRootOperationData() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "getCurrentRootOperationData");
            CodeTreeBuilder b = ex.createBuilder();
            b.startStatement().startCall("validateRootOperationBegin").end(2);
            b.statement("return state.operationStack[state.rootOperationSp]");
            return ex;
        }

        private CodeExecutableElement createEmit(OperationModel operation) {
            Modifier visibility = operation.isInternal ? PRIVATE : PUBLIC;
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(visibility), type(void.class), "emit" + operation.builderName);
            ex.setVarArgs(operation.operationBeginArgumentVarArgs);

            for (OperationArgument arg : operation.operationBeginArguments) {
                ex.addParameter(arg.toVariableElement());
            }
            if (operation.operationEndArguments.length != 0) {
                throw new AssertionError("operation with no children has end arguments. they should all be at the beginning");
            }

            addBeginOrEmitOperationDoc(operation, ex);

            CodeTreeBuilder b = ex.createBuilder();

            if (model.enableSerialization && !operation.isInternal) {
                b.startIf().string("serialization != null").end().startBlock();
                createSerializeBegin(operation, b);
                b.statement("return");
                b.end();
            }

            if (operation.requiresRootOperation()) {
                b.startStatement().startCall("validateRootOperationBegin").end(2);
            }

            if (operation.kind == OperationKind.LOAD_CONSTANT) {
                String constantArgument = operation.operationBeginArguments[0].name();
                b.startIf().string(constantArgument, " == null").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("The " + constantArgument + " parameter must not be null. Use emitLoadNull() instead for null values.").end().end();
                b.end();
                b.startIf();
                b.instanceOf(constantArgument, types.Node).string(" && ");
                b.string("!").startParantheses().instanceOf(constantArgument, types.RootNode).end();
                b.end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("Nodes cannot be used as constants.").end().end();
                b.end();
            }

            if (operation.hasConstantOperands()) {
                int index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.before()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationBeginArgumentName(index++));
                }
                index = 0;
                for (ConstantOperandModel operand : operation.constantOperands.after()) {
                    buildConstantOperandValidation(b, operand.type(), operation.getOperationEndArgumentName(index++));
                }
            }

            List<String> constantOperandValues = emitConstantOperands(b, operation);

            if (operation.kind == OperationKind.CUSTOM_INSTRUMENTATION) {
                int mask = 1 << operation.instrumentationIndex;
                b.startIf().string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") == 0").end().startBlock();
                b.returnStatement();
                b.end();
            }

            if (operation.isCustom() && !operation.customModel.implicitTags.isEmpty()) {
                VariableElement tagConstants = lookupTagConstant(operation.customModel.implicitTags);
                if (tagConstants != null) {
                    buildBegin(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            b.startStatement().startCall("beforeChild").end(2);

            if (operation.kind == OperationKind.LOAD_LOCAL || operation.kind == OperationKind.CLEAR_LOCAL) {
                emitValidateLocalScope(b, operation);
            }

            // emit the instruction
            switch (operation.kind) {
                case LABEL -> buildEmitLabel(b, operation);
                case BRANCH -> buildEmitBranch(b, operation);
                case LOAD_EXCEPTION -> buildEmitLoadException(b, operation);
                case CUSTOM_YIELD -> {
                    if (operation.instruction.signature.dynamicOperandCount != 0) {
                        throw new AssertionError("expected custom yield to have 0 dynamic operands: " + operation.instruction);
                    }

                    if (model.enableTagInstrumentation) {
                        b.statement("doEmitTagYieldNull()");
                    }
                    buildEmitOperationInstruction(b, operation, constantOperandValues);
                    if (model.enableTagInstrumentation) {
                        b.statement("doEmitTagResume()");
                    }
                }
                default -> {
                    if (operation.instruction == null) {
                        throw new AssertionError("operation did not have instruction");
                    }
                    buildEmitOperationInstruction(b, operation, constantOperandValues);
                }
            }

            // update reachability
            switch (operation.kind) {
                case BRANCH:
                    b.statement("markReachable(false)");
                    break;
                case LABEL:
                    b.statement("markReachable(true)");
                    break;
            }

            emitCallAfterChild(b, operation, String.valueOf(!operation.isVoid), operation.instruction != null ? "state.bci - " + operation.instruction.getInstructionLength() : "-1");

            if (operation.isCustom() && !operation.customModel.implicitTags.isEmpty()) {
                VariableElement tagConstants = lookupTagConstant(operation.customModel.implicitTags);
                if (tagConstants != null) {
                    buildEnd(b, model.tagOperation, tagConstants.getSimpleName().toString());
                }
            }

            return ex;
        }

        private void buildConstantOperandValidation(CodeTreeBuilder b, TypeMirror type, String name) {
            if (!ElementUtils.isPrimitive(type)) {
                b.startIf().string(name, " == null").end().startBlock();
                b.startThrow().startCall("failArgument").doubleQuote("The " + name + " parameter must not be null. Constant operands do not permit null values.").end().end();
                b.end();
            }

            if (ElementUtils.typeEquals(type, types.LocalAccessor)) {
                emitValidateLocalScope(b, false, name);
            } else if (ElementUtils.typeEquals(type, types.LocalRangeAccessor)) {
                String element = name + "Element";
                b.startFor().type(types.BytecodeLocal).string(" " + element + " : " + name).end().startBlock();
                emitValidateLocalScope(b, false, element);
                b.end();
            } else if (ElementUtils.typeEquals(type, types.MaterializedLocalAccessor)) {
                emitValidateLocalScope(b, true, name);
            }
        }

        /**
         * Helper to emit declarations for each constant operand inside a begin method.
         *
         * This method should be called before any early exit checks (e.g., checking whether an
         * instrumentation is enabled) so that the constant pool is stable. However, it should be
         * called *after* the serialization check (there is no constant pool for serialization).
         *
         * Returns the names of the declared variables for later use in code gen.
         */
        private List<String> emitConstantBeginOperands(CodeTreeBuilder b, OperationModel operation) {
            InstructionModel instruction = operation.instruction;
            if (instruction == null) {
                return List.of();
            }

            List<ConstantOperandModel> constantOperandsBefore = operation.constantOperands.before();
            if (constantOperandsBefore.isEmpty()) {
                return List.of();
            }

            List<String> result = new ArrayList<>(constantOperandsBefore.size());
            for (int i = 0; i < constantOperandsBefore.size(); i++) {
                result.add(emitConstantOperand(b, operation.operationBeginArguments[i], operation.getConstantOperandBeforeName(i)));
            }
            return result;
        }

        /**
         * Helper to emit declarations for each constant operand inside an emit/end method.
         *
         * This method should be called before any early exit checks (e.g., checking whether an
         * instrumentation is enabled) so that the constant pool is stable. However, it should be
         * called *after* the serialization check (there is no constant pool for serialization).
         *
         * Returns the names of the declared variables for later use in code gen.
         */
        private List<String> emitConstantOperands(CodeTreeBuilder b, OperationModel operation) {
            InstructionModel instruction = operation.instruction;
            if (instruction == null) {
                return List.of();
            }
            List<ConstantOperandModel> before = operation.constantOperands.before();
            List<ConstantOperandModel> after = operation.constantOperands.after();
            if (before.isEmpty() && after.isEmpty()) {
                return List.of();
            }

            boolean inEmit = !operation.hasChildren();
            List<String> result = new ArrayList<>(before.size() + after.size());
            if (inEmit) {
                for (int i = 0; i < before.size(); i++) {
                    result.add(emitConstantOperand(b, operation.operationBeginArguments[i], operation.getConstantOperandBeforeName(i)));
                }
            } else {
                for (var field : operationFields.getConstants(before, false)) {
                    result.add(operationStack.read(operation, field));
                }
            }
            for (int i = 0; i < after.size(); i++) {
                if (model.prolog != null && operation == model.prolog.operation) {
                    /**
                     * Special case: when emitting the prolog in beginRoot, end constants are not
                     * yet known. They will be patched in endRoot.
                     */
                    result.add(UNINIT);
                } else {
                    result.add(emitConstantOperand(b, operation.operationEndArguments[i], operation.getConstantOperandAfterName(i)));
                }

            }
            return result;
        }

        private String[] buildCustomInitializer(CodeTreeBuilder b, OperationModel operation, InstructionModel instruction, String customChildBci, List<String> constantOperandValues) {
            if (operation.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                throw new AssertionError("short circuit operations should not be emitted directly.");
            }

            if (customChildBci != null && operation.numDynamicOperands() > 1) {
                throw new AssertionError("customChildBci can only be used with a single child.");
            }

            if (operation.isVariadic) {
                b.declaration(type(int.class), "variadicCount", "operation.childCount - " + (operation.instruction.signature.dynamicOperandCount - 1));

                b.startIf().string(operationStack.read(operation, operationFields.variadicCountPatchIndex), " != -1").end().startBlock();
                b.statement(writeInt("state.bc", operationStack.read(operation, operationFields.variadicCountPatchIndex), "variadicCount"));
                b.end();

                b.startStatement().startCall("doEmitVariadicEnd");
                if (model.maximumVariadicOffset > 0) {
                    b.string(operation.variadicOffset);
                }
                b.string("variadicCount");
                if (model.hasVariadicReturn) {
                    b.string(operationStack.read(operation, operationFields.variadicReturnIndices));
                    b.string(operationStack.read(operation, operationFields.numVariadicReturnIndices));
                }
                b.end().end();
            }

            List<InstructionImmediate> immediates = instruction.getImmediates();
            String[] args = new String[immediates.size()];

            int childBciIndex = 0;
            int constantIndex = 0;
            for (int i = 0; i < immediates.size(); i++) {
                InstructionImmediate immediate = immediates.get(i);
                if (immediate.dynamic()) {
                    args[i] = ElementUtils.defaultValue(immediate.encoding().width().toType(context));
                    continue;
                }
                args[i] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        if (customChildBci != null) {
                            yield customChildBci;
                        } else {
                            if (operation.isTransparent) {
                                if (childBciIndex != 0) {
                                    throw new AssertionError("Unexpected transparent child.");
                                }
                                childBciIndex++;
                                yield operationStack.read(operation, operationFields.childBci);
                            } else {
                                String childBci = "childBci" + childBciIndex;
                                b.declaration(type(int.class), childBci, operationStack.read(operation, operationFields.getChildBci(childBciIndex, false)));
                                childBciIndex++;
                                yield childBci;
                            }
                        }
                    }
                    case CONSTANT -> {
                        if (constantIndex < constantOperandValues.size()) {
                            yield constantOperandValues.get(constantIndex++);
                        } else if (operation.kind == OperationKind.CUSTOM_YIELD) {
                            // The continuation root is the last constant, after constant operands.
                            b.declaration(type(short.class), "constantPoolIndex", "state.allocateContinuationConstant()");
                            yield "constantPoolIndex";
                        } else {
                            throw new AssertionError("Operation has more constant immediates than constant operands: " + operation);
                        }
                    }
                    case CONSTANT_LONG, CONSTANT_DOUBLE, CONSTANT_INT, CONSTANT_FLOAT, CONSTANT_SHORT, CONSTANT_CHAR, CONSTANT_BOOL, CONSTANT_BYTE -> constantOperandValues.get(constantIndex++);
                    case NODE_PROFILE -> "state.allocateNode()";
                    case TAG_NODE -> "node";
                    case FRAME_INDEX, LOCAL_INDEX, SHORT, STATE_PROFILE, LOCAL_ROOT, INTEGER, BRANCH_PROFILE, STACK_POINTER -> throw new AssertionError("Operation " + operation.name +
                                    " takes an immediate " + immediate.name() + " with unexpected kind " + immediate.kind() + ". This is a bug in the Bytecode DSL processor.");

                };
            }

            return args;
        }

        private String emitConstantOperand(CodeTreeBuilder b, OperationArgument argument, String constantOperandName) {
            ConstantOperandModel constantOperand = argument.constantOperand().orElseThrow(() -> new AssertionError("Operation argument " + argument + " did not have a constant operand."));
            if (constantOperand.kind() == ImmediateKind.CONSTANT) {
                /**
                 * Eagerly allocate space for the constants. Even if the node is not emitted (e.g.,
                 * it's a disabled instrumentation), we need the constant pool to be stable.
                 */
                String constantPoolIndex = constantOperandName + "Index";
                b.startDeclaration(type(int.class), constantPoolIndex);
                b.startCall("state.addConstant");
                if (ElementUtils.typeEquals(argument.builderType(), constantOperand.type())) {
                    b.string(argument.name());
                } else {
                    b.startStaticCall(constantOperand.type(), "constantOf");
                    if (ElementUtils.typeEquals(constantOperand.type(), types.MaterializedLocalAccessor)) {
                        // Materialized accessors also need the root index.
                        b.startGroup();
                        b.startParantheses().cast(bytecodeLocalImpl.asType()).string(argument.name()).end();
                        b.string(".rootIndex");
                        b.end();
                    }
                    b.string(argument.name());
                    b.end();
                }
                b.end();
                b.end();
                return constantPoolIndex;
            } else {
                return encodeInlinedConstant(constantOperand.kind(), argument.name());
            }
        }

        private CodeExecutableElement createBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "beforeChild");
            CodeTreeBuilder p = ex.createBuilder();

            Map<EqualityCodeTree, List<OperationModel>> caseGrouping = EqualityCodeTree.group(p, model.getOperationsWithChildren(), (OperationModel op, CodeTreeBuilder b) -> {
                if (op.isTransparent && (op.isVariadic || op.numDynamicOperands() > 1)) {
                    b.startIf().string(operationStack.read(op, operationFields.producedValue)).end().startBlock();

                    String childBci = "-1";
                    if (model.usesBoxingElimination()) {
                        childBci = operationStack.read(op, operationFields.childBci);
                    }

                    buildEmitInstruction(b, null, model.popInstruction, emitPopArguments(childBci));
                    b.end();
                    b.statement("break");
                } else if (op.kind == OperationKind.CUSTOM_SHORT_CIRCUIT) {
                    ShortCircuitInstructionModel shortCircuitModel = op.instruction.shortCircuitModel;

                    // Only emit the boolean check between consecutive children.
                    b.startIf().string("childIndex != 0").end().startBlock();

                    // If this operation has a converter, convert the value.
                    if (shortCircuitModel.convertsOperands()) {
                        /**
                         * If the operation doesn't produce a boolean, it must DUP the operand so it
                         * can pass it to the converter and also produce it as a result.
                         */
                        if (!shortCircuitModel.producesBoolean()) {
                            buildEmitInstruction(b, null, model.dupInstruction);
                        }
                        buildEmitBooleanConverterInstruction(b, op.instruction);
                    }

                    if (model.usesBoxingElimination()) {
                        b.tree(operationStack.write(op, operationFields.shortCircuitBci, "state.bci"));
                    }

                    // Emit the boolean check.
                    buildEmitInstruction(b, "shortCircuitBci", op.instruction, emitShortCircuitArguments(op.instruction));

                    // Remember the short circuit instruction's bci so we can patch the branch bci.
                    b.startIf().string("shortCircuitBci != -1").end().startBlock();

                    b.declaration(type(int[].class), "branchFixupBcis", operationStack.read(op, operationFields.branchFixupBcis));
                    b.declaration(type(int.class), "numBranchFixupBcis", operationStack.read(op, operationFields.numBranchFixupBcis));
                    b.startIf().string("numBranchFixupBcis >= branchFixupBcis.length").end().startBlock();
                    b.startAssign("branchFixupBcis").startStaticCall(type(Arrays.class), "copyOf").string("branchFixupBcis").string("branchFixupBcis.length * 2").end().end();
                    b.tree(operationStack.write(op, operationFields.branchFixupBcis, "branchFixupBcis"));
                    b.end();
                    b.statement("branchFixupBcis[numBranchFixupBcis] = shortCircuitBci + " + op.instruction.getImmediate("branch_target").offset());
                    b.tree(operationStack.write(op, operationFields.numBranchFixupBcis, "numBranchFixupBcis + 1"));

                    b.end(); // reachable

                    b.end(); // childIndex != 0

                    b.statement("break");
                } else if (op.kind == OperationKind.IF_THEN_ELSE ||
                                op.kind == OperationKind.IF_THEN ||
                                op.kind == OperationKind.CONDITIONAL ||
                                op.kind == OperationKind.TRY_FINALLY) {

                    b.startIf().string("childIndex >= 1").end().startBlock();
                    b.statement("updateReachable()");
                    b.end();
                    b.statement("break");
                } else if (op.kind == OperationKind.TRY_CATCH ||
                                op.kind == OperationKind.TRY_CATCH_OTHERWISE) {
                    b.startIf().string("childIndex == 1").end().startBlock();
                    b.statement("updateReachable()");
                    b.lineComment("The exception dispatch logic pushes the exception onto the stack.");
                    b.statement("state.currentStackHeight = state.currentStackHeight + 1");
                    b.statement("state.updateMaxStackHeight(state.currentStackHeight)");
                    b.end(); // if
                    b.statement("break");
                } else if (op.kind == OperationKind.CUSTOM && op.isVariadic) {
                    // Before emitting a variadic instruction, we need to emit instructions
                    // to merge all
                    // of the operands on the stack into one array.
                    if (op.instruction.signature.dynamicOperandCount == 1) {
                        // only argument is variadic
                        b.startDeclaration(type(int.class), "patchIndex").startCall("doEmitVariadicBeforeChild");
                        if (model.maximumVariadicOffset > 0) {
                            b.string(op.variadicOffset);
                        }
                        b.string("childIndex");
                        b.end().end();
                        b.startIf().string("patchIndex != -1").end().startBlock();

                        b.tree(operationStack.write(op, operationFields.variadicCountPatchIndex, "patchIndex"));
                        b.end();
                    } else {
                        b.startIf().string("childIndex >= " + (op.instruction.signature.dynamicOperandCount - 1)).end().startBlock();
                        b.startDeclaration(type(int.class), "patchIndex").startCall("doEmitVariadicBeforeChild");
                        if (model.maximumVariadicOffset > 0) {
                            b.string(op.variadicOffset);
                        }
                        b.string("childIndex - " + (op.instruction.signature.dynamicOperandCount - 1));
                        b.end().end();
                        b.startIf().string("patchIndex != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.variadicCountPatchIndex, "patchIndex"));
                        b.end();
                        b.end();
                    }

                    b.statement("break");
                } else {
                    b.statement("break");
                }

            });
            CodeTreeBuilder b = p;
            b.startIf().string("state.operationSp == 0").end().startBlock();
            b.statement("return");
            b.end();

            b.declaration(operationStack.asType(), "operation", "state.peekOperation()");
            b.statement("int childIndex = operation.childCount");

            b.startSwitch().string("operation.operation").end().startBlock();
            for (var group : caseGrouping.entrySet()) {
                EqualityCodeTree key = group.getKey();
                for (OperationModel op : group.getValue()) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startBlock();
                b.tree(key.getTree());
                b.end();
            }
            b.caseDefault();
            b.startCaseBlock();
            emitThrowAssertionError(b, "\"beforeChild should not be called on an operation with no children.\"");
            b.end();
            b.end(); // switch

            return ex;
        }

        private void buildEmitBooleanConverterInstruction(CodeTreeBuilder b, InstructionModel shortCircuitInstruction) {
            InstructionModel booleanConverter = shortCircuitInstruction.shortCircuitModel.booleanConverterInstruction();

            List<InstructionImmediate> immediates = booleanConverter.getImmediates();
            String[] args = new String[immediates.size()];
            for (int i = 0; i < args.length; i++) {
                InstructionImmediate immediate = immediates.get(i);
                if (immediate.dynamic()) {
                    args[i] = ElementUtils.defaultValue(immediate.encoding().width().toType(context));
                    continue;
                }
                args[i] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        if (shortCircuitInstruction.shortCircuitModel.producesBoolean()) {
                            b.statement("int childBci = ", operationStack.read(shortCircuitInstruction.operation, operationFields.childBci));
                            b.startAssert();
                            b.string("childBci != " + UNINIT);
                            b.end();
                        } else {
                            b.lineComment("Boxing elimination not supported for converter operations if the value is returned.");
                            b.statement("int childBci = -1");
                        }
                        yield "childBci";
                    }
                    case NODE_PROFILE -> "state.allocateNode()";
                    default -> throw new AssertionError(String.format("Boolean converter instruction had unexpected encoding: %s", immediates));
                };
            }
            buildEmitInstruction(b, null, booleanConverter, args);
        }

        private CodeExecutableElement createAfterChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "afterChild");
            ex.addParameter(new CodeVariableElement(type(int.class), "operationCode"));
            ex.addParameter(new CodeVariableElement(type(boolean.class), "producedValue"));
            if (model.usesBoxingElimination()) {
                ex.addParameter(new CodeVariableElement(type(int.class), "childBci"));
            }
            CodeTreeBuilder p = ex.createBuilder();

            Map<EqualityCodeTree, List<OperationModel>> caseGrouping = EqualityCodeTree.group(p, model.getOperationsWithChildren(), (OperationModel op, CodeTreeBuilder b) -> {
                if (op.isTransparent()) {
                    b.tree(operationStack.write(op, operationFields.producedValue, "producedValue"));
                    if (model.usesBoxingElimination()) {
                        b.tree(operationStack.write(op, operationFields.childBci, "childBci"));
                    }
                    b.statement("break");
                    return;
                }

                if (op.requiresStackBalancing()) {
                    List<Integer> valueChildren = new ArrayList<>();
                    List<Integer> nonValueChildren = new ArrayList<>();
                    for (int i = 0; i < op.dynamicOperands.length; i++) {
                        if (!op.dynamicOperands[i].voidAllowed()) {
                            valueChildren.add(i);
                        } else {
                            nonValueChildren.add(i);
                        }
                    }
                    if (nonValueChildren.isEmpty()) {
                        // Simplification: each child should be value producing.
                        b.startIf().string("!producedValue").end().startBlock();
                        b.startThrow().startCall("failState");
                        b.doubleQuote("Operation %s expected a value-producing child at position %s, but a void one was provided.");
                        b.startStaticCall(operationsElement.asType(), "getName").string("operation.operation").end();
                        b.string("childIndex");
                        b.end().end(); // throw, call
                        b.end(); // if
                    } else if (valueChildren.isEmpty()) {
                        // Simplification: each child should not be value producing.
                        b.startIf().string("producedValue").end().startBlock();
                        buildEmitInstruction(b, null, model.popInstruction, emitPopArguments("childBci"));
                        b.end();
                    } else {
                        // Otherwise, partition by value/not value producing.
                        b.startIf();
                        b.string("(");
                        for (int i = 0; i < valueChildren.size(); i++) {
                            if (i != 0) {
                                b.string(" || ");
                            }
                            String operator = (op.isVariadic && valueChildren.get(i) == op.dynamicOperands.length - 1) ? ">=" : "==";
                            b.string("childIndex " + operator + " " + valueChildren.get(i));
                        }
                        b.string(") && !producedValue");
                        b.end().startBlock();

                        b.startThrow().startCall("failState");
                        b.doubleQuote("Operation %s expected a value-producing child at position %s, but a void one was provided.");
                        b.startStaticCall(operationsElement.asType(), "getName").string("operation.operation").end();
                        b.string("childIndex");
                        b.end().end(); // throw, call
                        b.end(); // block

                        b.startElseIf();
                        b.string("(");
                        for (int i = 0; i < nonValueChildren.size(); i++) {
                            if (i != 0) {
                                b.string(" || ");
                            }
                            String operator;
                            if (op.isVariadic && nonValueChildren.get(i) == op.dynamicOperands.length - 1) {
                                operator = ">=";
                            } else {
                                operator = "==";
                            }
                            b.string("childIndex " + operator + " " + nonValueChildren.get(i));
                        }
                        b.string(") && producedValue");
                        b.end().startBlock();
                        buildEmitInstruction(b, null, model.popInstruction, emitPopArguments("childBci"));
                        b.end();
                    }
                }

                switch (op.kind) {
                    case TAG:
                        b.tree(operationStack.write(op, operationFields.producedValue, "producedValue"));
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.childBci, "childBci"));
                        }
                        break;
                    case RETURN:
                        b.tree(operationStack.write(op, operationFields.producedValue, "producedValue"));
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.childBci, "childBci"));
                        }
                        break;
                    case IF_THEN:
                        b.startIf().string("childIndex == 0").end().startBlock();

                        buildEmitInstruction(b, "branchFalseBci", model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.startIf().string("branchFalseBci != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.falseBranchFixupBci,
                                        "branchFalseBci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset()));
                        b.end();

                        b.end().startElseBlock();
                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.falseBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();
                        b.end();
                        break;
                    case IF_THEN_ELSE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, "branchFalseBci", model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.startIf().string("branchFalseBci != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.falseBranchFixupBci,
                                        "branchFalseBci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset()));
                        b.end();
                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        buildEmitInstruction(b, "branchBci", model.branchInstruction, new String[]{UNINIT});
                        b.startIf().string("branchBci != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.endBranchFixupBci, "branchBci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset()));
                        b.end();
                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.falseBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();
                        b.end().startElseBlock();
                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.endBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();
                        b.end();
                        break;
                    case CONDITIONAL:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            buildEmitInstruction(b, null, model.dupInstruction);
                        }
                        buildEmitInstruction(b, "branchFalseBci", model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.startIf().string("branchFalseBci != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.falseBranchFixupBci,
                                        "branchFalseBci + " + model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target").offset()));
                        b.end();

                        b.end().startElseIf().string("childIndex == 1").end().startBlock();
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.getChildBci(0, false), "childBci"));
                        }
                        b.startIf().string("state.reachable").end().startBlock();
                        buildEmitInstruction(b, "branchBci", model.branchInstruction, new String[]{UNINIT});
                        b.tree(operationStack.write(op, operationFields.endBranchFixupBci, "branchBci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset()));
                        // we have to adjust the stack for the third child
                        b.end();
                        b.statement("state.currentStackHeight -= 1");

                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.falseBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();
                        b.end().startElseBlock();
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.getChildBci(1, false), "childBci"));
                        }
                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.endBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();
                        b.end();
                        break;
                    case WHILE:
                        InstructionImmediate branchTarget = model.branchFalseInstruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
                        b.startIf().string("childIndex == 0").end().startBlock();
                        buildEmitInstruction(b, "branchFalseBci", model.branchFalseInstruction, emitBranchFalseArguments(model.branchFalseInstruction));
                        b.startIf().string("branchFalseBci != -1").end().startBlock();
                        b.tree(operationStack.write(op, operationFields.endBranchFixupBci, "branchFalseBci + " + branchTarget.offset()));
                        b.end();
                        b.end().startElseBlock();
                        b.statement("int toUpdate = ", operationStack.read(op, operationFields.endBranchFixupBci));
                        b.startIf().string("toUpdate != ", UNINIT).end().startBlock();
                        /**
                         * To emit a branch.backward, we need the branch profile from the
                         * branch.false instruction. Since we have the offset of the branch target
                         * (toUpdate) we can obtain the branch profile with a bit of offset math.
                         *
                         * Note that we do not emit branch.backward when branch.false was not
                         * emitted (i.e., when toUpdate == UNINIT). This is OK, because it should be
                         * impossible to reach the end of a loop body if the loop body cannot be
                         * entered.
                         */
                        InstructionImmediate branchProfile = model.branchFalseInstruction.findImmediate(ImmediateKind.BRANCH_PROFILE, "branch_profile");
                        int offset = branchProfile.offset() - branchTarget.offset();
                        if (ImmediateKind.BRANCH_PROFILE.width != ImmediateWidth.INT) {
                            throw new AssertionError("branch profile width changed");
                        }
                        String readBranchProfile = readInt("state.bc", "toUpdate + " + offset + " /* loop branch profile */");
                        buildEmitInstruction(b, null, model.branchBackwardInstruction, new String[]{operationStack.read(op, operationFields.whileStartBci), readBranchProfile});
                        b.statement(writeInt("state.bc", "toUpdate", "state.bci"));
                        b.end();

                        b.end();
                        break;
                    case TRY_CATCH:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        b.startIf().string(operationStack.read(op, operationFields.operationReachable)).end().startBlock();
                        b.declaration(type(int.class), "tryEndBci", "state.bci");

                        b.startIf().string(operationStack.read(op, operationFields.tryReachable)).end().startBlock();
                        buildEmitInstruction(b, "branchBci", model.branchInstruction, new String[]{UNINIT});
                        b.tree(operationStack.write(op, operationFields.endBranchFixupBci, "branchBci + " + model.branchInstruction.getImmediate(ImmediateKind.BYTECODE_INDEX).offset()));
                        b.end(); // if tryReachable

                        b.declaration(type(int.class), "handlerSp", "state.currentStackHeight + 1");
                        b.startStatement().startCall("state.patchHandlerTable");
                        b.string(operationStack.read(op, operationFields.extraTableEntriesStart));
                        b.string(operationStack.read(op, operationFields.extraTableEntriesEnd));
                        b.string(operationStack.read(op, operationFields.handlerId));
                        b.string("state.bci");
                        b.string("handlerSp");
                        b.end(2);
                        b.statement("state.doCreateExceptionHandler(", operationStack.read(op, operationFields.tryStartBci), ", tryEndBci, HANDLER_CUSTOM, state.bci, handlerSp)");

                        b.end(); // if operationReachable
                        b.end();

                        b.startElseIf().string("childIndex == 1").end().startBlock();
                        b.lineComment("pop the exception");
                        buildEmitInstruction(b, null, model.popInstruction, emitPopArguments("-1"));
                        emitFixFinallyBranchBci(b, op);
                        b.end();
                        break;
                    case TRY_CATCH_OTHERWISE:
                        b.startIf().string("childIndex == 0").end().startBlock();
                        emitFinallyHandlersAfterTry(b, op);
                        b.end().startElseBlock();
                        b.lineComment("pop the exception");
                        buildEmitInstruction(b, null, model.popInstruction, emitPopArguments("-1"));
                        emitFixFinallyBranchBci(b, op);
                        b.end();

                        break;
                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.childBci, "childBci"));
                        } else {
                            // no operand to encode
                        }
                        break;
                    case CUSTOM:
                    case CUSTOM_YIELD:
                    case CUSTOM_INSTRUMENTATION:
                        int immediateIndex = 0;
                        boolean elseIf = false;
                        for (int valueIndex = 0; valueIndex < op.instruction.signature.dynamicOperandCount; valueIndex++) {
                            if (op.instruction.needsChildBciForBoxingElimination(model, valueIndex)) {
                                elseIf = b.startIf(elseIf);
                                b.string("childIndex == " + valueIndex).end().startBlock();

                                int index = immediateIndex++;
                                b.tree(operationStack.write(op, operationFields.getChildBci(index, false), "childBci"));
                                b.end();
                            }
                        }

                        if (op.isVariadic && model.hasVariadicReturn) {
                            if (op.instruction.signature.dynamicOperandCount > 1) {
                                b.startIf().string("childIndex > ").string(op.instruction.signature.dynamicOperandCount - 2).end().startBlock();
                            }

                            b.startIf().string("isVariadicReturn(operationCode)").end().startBlock();

                            b.declaration(type(int[].class), "variadicReturnIndices", operationStack.read(op, operationFields.variadicReturnIndices));
                            b.declaration(type(int.class), "numVariadicReturnIndices", operationStack.read(op, operationFields.numVariadicReturnIndices));

                            b.startIf().string("numVariadicReturnIndices >= variadicReturnIndices.length").end().startBlock();
                            b.startAssign("variadicReturnIndices").startStaticCall(type(Arrays.class), "copyOf").string("variadicReturnIndices").string(
                                            "variadicReturnIndices.length * 2").end().end();
                            b.tree(operationStack.write(op, operationFields.variadicReturnIndices, "variadicReturnIndices"));
                            b.end();
                            if (op.instruction.signature.dynamicOperandCount > 1) {
                                b.statement("variadicReturnIndices[numVariadicReturnIndices] = childIndex - " + (op.instruction.signature.dynamicOperandCount - 1));
                            } else {
                                b.statement("variadicReturnIndices[numVariadicReturnIndices] = childIndex");
                            }
                            b.tree(operationStack.write(op, operationFields.numVariadicReturnIndices, "numVariadicReturnIndices + 1"));

                            b.end(); // if isVariadicReturn

                            if (op.instruction.signature.dynamicOperandCount > 1) {
                                b.end();
                            }
                        }

                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        if (model.usesBoxingElimination()) {
                            b.tree(operationStack.write(op, operationFields.childBci, "childBci"));
                        }
                        break;
                }
                b.statement("break");
            });
            CodeTreeBuilder b = p;
            b.startIf().string("state.operationSp == 0").end().startBlock();
            b.statement("return");
            b.end();

            b.declaration(operationStack.asType(), "operation", "state.peekOperation()");
            b.statement("int childIndex = operation.childCount");

            b.startSwitch().string("operation.operation").end().startBlock();
            for (var group : caseGrouping.entrySet()) {
                EqualityCodeTree key = group.getKey();
                for (OperationModel op : group.getValue()) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
                b.startBlock();
                b.tree(key.getTree());
                b.end();
            }
            b.end(); // switch

            b.statement("operation.childCount = childIndex + 1");

            return ex;
        }

        private String[] emitShortCircuitArguments(InstructionModel instruction) {
            List<InstructionImmediate> immedates = instruction.getImmediates();
            String[] branchArguments = new String[immedates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immedates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> UNINIT;
                    case BRANCH_PROFILE -> "state.allocateBranchProfile()";
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitBranchFalseArguments(InstructionModel instruction) {
            List<InstructionImmediate> immediates = instruction.getImmediates();
            String[] branchArguments = new String[immediates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immediates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> (index == 0) ? UNINIT : "childBci";
                    case BRANCH_PROFILE -> "state.allocateBranchProfile()";
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitMergeConditionalArguments(InstructionModel instr) {
            List<InstructionImmediate> immediates = instr.getImmediates();
            String[] branchArguments = new String[immediates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immediates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> {
                        String readChildBci = operationStack.read(instr.operation, operationFields.getChildBci(index, false));
                        if (index == 0) {
                            yield operationStack.read(instr.operation, operationFields.thenReachable) + " ? " + readChildBci + " : -1";
                        } else {
                            yield operationStack.read(instr.operation, operationFields.elseReachable) + " ? " + readChildBci + " : -1";
                        }
                    }
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private String[] emitPopArguments(String childBciName) {
            List<InstructionImmediate> immediates = model.popInstruction.getImmediates();
            String[] branchArguments = new String[immediates.size()];
            for (int index = 0; index < branchArguments.length; index++) {
                InstructionImmediate immediate = immediates.get(index);
                branchArguments[index] = switch (immediate.kind()) {
                    case BYTECODE_INDEX -> childBciName;
                    default -> throw new AssertionError("Unexpected immediate: " + immediate);
                };
            }
            return branchArguments;
        }

        private CodeExecutableElement createSafeCastShort() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(short.class), "safeCastShort");
            ex.addParameter(new CodeVariableElement(type(int.class), "num"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("Short.MIN_VALUE <= num && num <= Short.MAX_VALUE").end().startBlock();
            b.startReturn().string("(short) num").end();
            b.end();
            emitThrowEncodingException(b, "\"Value \" + num + \" cannot be encoded as a short.\"");
            return ex;
        }

        private CodeExecutableElement createCheckOverflowShort() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(short.class), "checkOverflowShort");
            ex.addParameter(new CodeVariableElement(type(short.class), "num"));
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(String.class), "valueName"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("num < 0").end().startBlock();
            emitThrowEncodingException(b, "valueName + \" overflowed.\"");
            b.end();
            b.statement("return num");

            return ex;
        }

        private CodeExecutableElement createCheckOverflowInt() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "checkOverflowInt");
            ex.addParameter(new CodeVariableElement(type(int.class), "num"));
            ex.addParameter(new CodeVariableElement(context.getDeclaredType(String.class), "valueName"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("num < 0").end().startBlock();
            emitThrowEncodingException(b, "valueName + \" overflowed.\"");
            b.end();
            b.statement("return num");

            return ex;
        }

        private CodeExecutableElement createCheckBci() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "checkBci");
            ex.addParameter(new CodeVariableElement(type(int.class), "newBci"));
            CodeTreeBuilder b = ex.createBuilder();
            b.startReturn().startCall("checkOverflowInt");
            b.string("newBci");
            b.doubleQuote("Bytecode index");
            b.end(2);
            return ex;
        }

        private CodeExecutableElement createDoEmitVariadicEnd() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitVariadicEnd");
            if (model.maximumVariadicOffset > 0) {
                ex.addParameter(new CodeVariableElement(type(int.class), "offset"));
            }
            ex.addParameter(new CodeVariableElement(type(int.class), "count"));
            if (model.hasVariadicReturn) {
                ex.addParameter(new CodeVariableElement(type(int[].class), "dynamicArguments"));
                ex.addParameter(new CodeVariableElement(type(int.class), "dynamicArgumentsLength"));
            }

            CodeTreeBuilder b = ex.createBuilder();

            if (model.maximumVariadicOffset == 0) {
                b.declaration(type(int.class), "offset", "0");
            }

            if (model.hasVariadicReturn) {
                b.declaration(type(int.class), "mergeCount", "0");
                b.startIf().string("dynamicArgumentsLength > 0").end().startBlock();
                b.startFor().string("int i = dynamicArgumentsLength - 1; i >= 0; i--").end().startBlock();
                b.startIf().string("mergeCount < count && dynamicArguments[i] == count - 1 - mergeCount").end().startBlock();
                b.statement("mergeCount++");
                b.end().startElseBlock();
                b.statement("break");
                b.end(); // else block
                b.end(); // for
                b.end(); // if dynamicArguments != null
            }

            b.startIf().string("offset == 0 && count == 0").end().startBlock();
            if (model.hasVariadicReturn) {
                b.statement("assert dynamicArgumentsLength == 0");
            }
            buildEmitInstruction(b, null, model.emptyVariadicInstruction);

            b.end().startElseIf().string("count <= VARIADIC_STACK_LIMIT").end().startBlock();

            if (model.hasVariadicReturn) {
                b.startIf().string("offset == 0 && count == 1 && mergeCount == 1").end().startBlock();
                b.lineComment("pass dynamic variadics directly");
                b.returnDefault();
                b.end();
            }

            buildEmitInstructionWithStackEffect(b, null, model.createVariadicInstruction, "-count + 1", createCreateVariadicArguments("offset", "(short)count", "(short)mergeCount"));

            b.end().startElseBlock();

            b.declaration(type(int.class), "stackCount", "count % VARIADIC_STACK_LIMIT");
            b.startIf().string("stackCount == 0").end().startBlock();
            b.statement("stackCount = VARIADIC_STACK_LIMIT");
            b.end();

            if (model.hasVariadicReturn) {
                b.startIf().string("mergeCount > stackCount").end().startBlock();
                b.lineComment("if we can't merge in one go, splat is almost always more efficient");
                b.statement("mergeCount = 0");
                b.end();
            }

            buildEmitInstructionWithStackEffect(b, null, model.loadVariadicInstruction, "-stackCount",
                            createLoadVariadicArguments("offset + count - stackCount", "(short)(stackCount)", "(short)mergeCount"));

            if (model.hasVariadicReturn) {
                b.end();
            }

            b.end();

            if (model.hasVariadicReturn) {
                b.startIf().string("dynamicArgumentsLength > 0").end().startBlock();
                b.declaration(type(int.class), "prev", "-1");
                b.declaration(type(int.class), "length", "0");
                b.startFor().string("int i = dynamicArgumentsLength - 1 - mergeCount; i >= 0; i--").end().startBlock();
                b.declaration(type(int.class), "index", "dynamicArguments[i]");

                b.startIf().string("prev == -1 ").end().startBlock();
                b.lineComment("first element");
                b.statement("length++");
                b.end().startElseIf().string("prev == index + 1").end().startBlock();
                b.lineComment("continuous range");
                b.statement("length++");
                b.end().startElseBlock();
                b.lineComment("range not continuous");
                buildEmitInstruction(b, null, model.splatVariadicInstruction, "offset + prev", "length");
                b.statement("length = 1");
                b.end();
                b.statement("prev = index");

                b.end();
                b.startIf().string("length > 0").end().startBlock();
                b.lineComment("emit last range");
                b.statement("assert prev != -1");
                buildEmitInstruction(b, null, model.splatVariadicInstruction, "offset + prev", "length");
                b.end();

                b.end(); // dynamicArguments != null
            }

            return ex;
        }

        private CodeExecutableElement createDoEmitVariadicBeforeChild() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitVariadicBeforeChild");

            if (model.maximumVariadicOffset > 0) {
                ex.addParameter(new CodeVariableElement(type(int.class), "offset"));
            }
            ex.addParameter(new CodeVariableElement(type(int.class), "count"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("count == 0").end().startBlock();
            b.startReturn().string("-1").end();
            b.end();

            b.declaration(type(int.class), "stackCount", "count % VARIADIC_STACK_LIMIT");
            b.startIf().string("stackCount != 0").end().startBlock();
            b.lineComment("nothing to emit yet");
            b.startReturn().string("-1").end();
            b.end();

            b.startIf().string("count <= VARIADIC_STACK_LIMIT").end().startBlock();
            buildEmitInstructionWithStackEffect(b, "createVariadicOffset", model.createVariadicInstruction, "-VARIADIC_STACK_LIMIT + 1",
                            createCreateVariadicArguments("offset", "VARIADIC_STACK_LIMIT", "(short) 0"));
            b.startReturn().string("createVariadicOffset + " + model.createVariadicInstruction.findImmediate(ImmediateKind.INTEGER, "count").offset()).end();
            b.end().startElseBlock();
            String offset;
            if (model.maximumVariadicOffset > 0) {
                offset = "offset + count - VARIADIC_STACK_LIMIT";
            } else {
                offset = "count - VARIADIC_STACK_LIMIT";
            }
            buildEmitInstructionWithStackEffect(b, null, model.loadVariadicInstruction, "-VARIADIC_STACK_LIMIT", createLoadVariadicArguments(offset, "(short)VARIADIC_STACK_LIMIT", "(short) 0"));
            b.startReturn().string("-1").end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createIsVariadicReturn() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "isVariadicReturn");
            ex.addParameter(new CodeVariableElement(type(int.class), "op"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startSwitch().string("op").end().startBlock();

            for (OperationModel op : model.getOperations()) {
                if (op.variadicReturn) {
                    b.startCase().tree(createOperationConstant(op)).end();
                }
            }
            b.startCaseBlock();
            b.returnTrue();
            b.end();

            b.end(); // switch block
            b.returnFalse();
            return ex;
        }

        private String[] createCreateVariadicArguments(String offset, String count, String mergeCount) throws AssertionError {
            List<InstructionImmediate> immediates = model.createVariadicInstruction.getImmediates();
            String[] createArguments = new String[immediates.size()];
            for (int i = 0; i < immediates.size(); i++) {
                createArguments[i] = switch (immediates.get(i).name()) {
                    case "offset" -> offset;
                    case "count" -> count;
                    case "merge_count" -> mergeCount;
                    default -> {
                        throw new AssertionError("invalid immediate " + immediates.get(i));
                    }
                };
            }
            return createArguments;
        }

        private String[] createLoadVariadicArguments(String offset, String count, String mergeCount) throws AssertionError {
            List<InstructionImmediate> immediates = model.loadVariadicInstruction.getImmediates();
            String[] createArguments = new String[immediates.size()];
            for (int i = 0; i < immediates.size(); i++) {
                createArguments[i] = switch (immediates.get(i).name()) {
                    case "offset" -> offset;
                    case "count" -> count;
                    case "merge_count" -> mergeCount;
                    default -> {
                        throw new AssertionError("invalid immediate " + immediates.get(i));
                    }
                };
            }
            return createArguments;
        }

        private void buildEmitInstruction(CodeTreeBuilder b, String localName, InstructionModel instr, String... arguments) {
            buildEmitInstructionWithStackEffect(b, localName, instr, String.valueOf(instr.getStackEffect()), arguments);
        }

        private void buildEmitInstructionWithStackEffect(CodeTreeBuilder b, String localName, InstructionModel instr, String stackEffect, String... arguments) throws AssertionError {
            CodeExecutableElement doEmitInstruction = rootStackElement.ensureDoEmitInstructionCreated(instr);
            if (localName != null) {
                b.startDeclaration(type(int.class), localName);
            } else {
                b.startStatement();
            }
            b.startCall("state", doEmitInstruction.getSimpleName().toString());
            b.tree(createInstructionConstant(instr));
            b.string(stackEffect);
            int argumentsLength = arguments != null ? arguments.length : 0;
            if (argumentsLength != instr.getImmediates().size()) {
                throw new AssertionError(
                                "Invalid number of immediates for instruction " + instr.name + ". Expected " + instr.getImmediates().size() + " but got " + argumentsLength + ". Immediates: " +
                                                String.join(", ", arguments));
            }

            if (arguments != null) {
                for (String argument : arguments) {
                    b.string(argument);
                }
            }
            b.end(2);
        }

        private CodeExecutableElement createDoEmitFinallyHandler() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitFinallyHandler");
            ex.addParameter(new CodeVariableElement(operationStack.asType(), "tryFinallyData"));
            ex.addParameter(new CodeVariableElement(type(int.class), "finallyOperationSp"));

            CodeTreeBuilder b = ex.createBuilder();

            b.startAssert();
            b.string("state.operationStack[finallyOperationSp].operation", " == ").tree(createOperationConstant(model.tryFinallyOperation));
            b.string(" || state.operationStack[finallyOperationSp].operation", " == ").tree(createOperationConstant(model.tryCatchOtherwiseOperation));
            b.end();
            b.startAssert().string(operationStack.read(model.tryFinallyOperation, "tryFinallyData", operationFields.finallyHandlerSp), " == ", UNINIT).end();
            b.startTryBlock();
            b.tree(operationStack.write(model.tryFinallyOperation, "tryFinallyData", operationFields.finallyHandlerSp, "state.operationSp"));
            buildBegin(b, model.finallyHandlerOperation, safeCastShort("finallyOperationSp"));
            b.statement(operationStack.read(model.tryFinallyOperation, "tryFinallyData", operationFields.finallyGenerator), ".run()");
            buildEnd(b, model.finallyHandlerOperation);
            b.end().startFinallyBlock();
            b.tree(operationStack.write(model.tryFinallyOperation, "tryFinallyData", operationFields.finallyHandlerSp, UNINIT));
            b.end();

            return ex;
        }

        private CodeExecutableElement createFailState() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(RuntimeException.class), "failState");
            ex.addParameter(new CodeVariableElement(type(String.class), "message"));
            ex.addParameter(new CodeVariableElement(type(Object[].class), "args"));
            ex.setVarArgs(true);
            CodeTreeBuilder b = ex.createBuilder();

            b.startThrow().startNew(type(IllegalStateException.class));
            b.startGroup();
            b.doubleQuote("Invalid builder usage: ");
            b.string(" + ");
            b.startStaticCall(type(String.class), "format");
            b.string("message");
            b.string("args");
            b.end();
            b.string(" + ").doubleQuote(" Operation stack: ").string(" + dumpAt()");
            b.end().end().end();

            return ex;
        }

        private CodeExecutableElement createFailArgument() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(RuntimeException.class), "failArgument");
            ex.addParameter(new CodeVariableElement(type(String.class), "message"));

            CodeTreeBuilder b = ex.createBuilder();
            b.startThrow().startNew(type(IllegalArgumentException.class));
            b.startGroup();
            b.doubleQuote("Invalid builder operation argument: ");
            b.string(" + ").string("message").string(" + ").doubleQuote(" Operation stack: ").string(" + dumpAt()");
            b.end().end().end();

            return ex;
        }

        private CodeExecutableElement createDumpAt() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(String.class), "dumpAt");
            CodeTreeBuilder b = ex.createBuilder();

            b.startTryBlock();
            b.statement("return state.toString()");
            b.end().startCatchBlock(type(Exception.class), "e");
            b.startReturn().doubleQuote("<invalid-location>").end();
            b.end();

            return ex;
        }

        private CodeExecutableElement createToString() {
            CodeExecutableElement ex = GeneratorUtils.override(declaredType(Object.class), "toString");
            CodeTreeBuilder b = ex.createBuilder();

            b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
            b.startStatement().startCall("b.append").startGroup().typeLiteral(BytecodeRootNodeElement.this.asType()).string(".getSimpleName()").end().end().end();
            b.statement("b.append('.')");
            b.startStatement().startCall("b.append").startGroup().typeLiteral(this.asType()).string(".getSimpleName()").end().end().end();
            b.startStatement().startCall("b.append").doubleQuote("[").end().end();
            b.startStatement().startCall("b.append").doubleQuote("mode=").end().end();

            boolean elseIf = false;
            if (model.enableSerialization) {
                b.startIf().string("serialization != null").end().startBlock();
                b.startStatement().startCall("b.append").doubleQuote("serializing").end().end();
                b.end();
                elseIf = true;
            }

            elseIf = b.startIf(elseIf);
            b.string("reparseReason != null").end().startBlock();
            b.startStatement().startCall("b.append").doubleQuote("reparsing").end().end();
            b.end();

            b.startElseBlock();
            b.startStatement().startCall("b.append").doubleQuote("default").end().end();
            b.end();

            b.startStatement().startCall("b.append").doubleQuote(", bytecodeIndex=").end().startCall(".append").string("state.bci").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", stackPointer=").end().startCall(".append").string("state.currentStackHeight").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", bytecodes=").end().startCall(".append").string("parseBytecodes").end().end();
            b.startStatement().startCall("b.append").doubleQuote(", sources=").end().startCall(".append").string("parseSources").end().end();

            if (!model.getInstrumentations().isEmpty()) {
                b.startStatement().startCall("b.append").doubleQuote(", instruments=[").end().end();
                b.declaration(type(String.class), "sep", "\"\"");
                for (CustomOperationModel customOp : model.getInstrumentations()) {
                    OperationModel operation = customOp.operation;
                    int mask = 1 << operation.instrumentationIndex;
                    b.startIf();
                    b.string("(instrumentations & ").string("0x", Integer.toHexString(mask)).string(") != 0").end().startBlock();
                    b.startStatement().startCall("b.append").string("sep").end().end();
                    b.startStatement().startCall("b.append").doubleQuote(operation.name).end().end();
                    b.startAssign("sep").doubleQuote(",").end();
                    b.end(); // block
                }
                b.startStatement().startCall("b.append").doubleQuote("]").end().end();
            }

            if (model.enableTagInstrumentation) {
                b.startStatement().startCall("b.append").doubleQuote(", tags=").end().end();
                b.declaration(type(String.class), "sepTag", "\"\"");
                for (TypeMirror tag : model.getProvidedTags()) {
                    b.startIf().string("(tags & CLASS_TO_TAG_MASK.get(").typeLiteral(tag).string(")) != 0").end().startBlock();
                    b.startStatement().startCall("b.append").string("sepTag").end().end();
                    b.startStatement().startCall("b.append").startStaticCall(types.Tag, "getIdentifier").typeLiteral(tag).end().end().end();
                    b.startAssign("sepTag").doubleQuote(",").end();
                    b.end();
                }
            }

            b.startStatement().startCall("b.append").doubleQuote(",").end().end();
            b.startStatement().startCall("b.append").string("System.lineSeparator()").end().end();
            b.startStatement().startCall("b.append").doubleQuote("  current=").end().end();
            b.startStatement().startCall("b.append").string("state.toString()").end().end();
            b.startStatement().startCall("b.append").doubleQuote("]").end().end();

            b.statement("return b.toString()");
            return ex;
        }

        private CodeExecutableElement createDoEmitRootSourceSection() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE),
                            type(void.class), "doEmitRootSourceSection");
            ex.addParameter(new CodeVariableElement(type(int.class), "nodeId"));
            CodeTreeBuilder b = ex.createBuilder();

            b.startIf().string("!parseSources").end().startBlock();
            b.lineComment("Nothing to do here without sources");
            b.statement("return");
            b.end();

            /**
             * Walk the entire operation stack (past any root operations) and find enclosing source
             * sections. The entire root node's bytecode range is covered by the source section.
             */
            b.declaration(rootStackElement.asType(), "currentState", "state");
            b.startWhile().string("currentState != null").end().startBlock();
            buildOperationStackWalk(b, "0", () -> {
                b.startSwitch().string("operation.operation").end().startBlock();

                b.startCase().tree(createOperationConstant(model.sourceSectionPrefixOperation)).end();
                b.startBlock();
                b.startStatement().startCall("state.doEmitSourceInfo");
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.sourceIndex));
                b.string("0");
                b.string("state.bci");
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.start));
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.length));
                b.end(2);
                b.statement("return");
                b.end();

                b.startCase().tree(createOperationConstant(model.sourceSectionSuffixOperation)).end();
                b.startBlock();

                b.tree(operationStack.write(model.sourceSectionSuffixOperation, operationFields.sourceNodeId, "nodeId"));

                b.startAssign("int operationStart").startCall("state.doEmitSourceInfo");
                b.string(operationStack.read(model.sourceSectionSuffixOperation, operationFields.sourceIndex));
                b.string("0");
                b.string("state.bci");
                b.string(operationStack.read(model.sourceSectionSuffixOperation, operationFields.start));
                b.string("PATCH_NODE_SOURCE");
                b.end(2);
                b.tree(operationStack.write(model.sourceSectionSuffixOperation, operationFields.start, "operationStart"));

                b.statement("return");
                b.end();

                b.end(); // switch
            }, "currentState");
            b.statement("currentState = currentState.parent");
            b.end();

            return ex;
        }

        /**
         * Before emitting a branch, we may need to emit instructions to "resolve" pending
         * operations (like finally handlers). We may also need to close and reopen certain bytecode
         * ranges, like exception handlers, which should not apply to those emitted instructions.
         */
        private CodeExecutableElement createBeforeEmitBranch() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "beforeEmitBranch");
            ex.addParameter(new CodeVariableElement(type(int.class), "declaringOperationSp"));
            emitStackWalksBeforeEarlyExit(ex, OperationKind.BRANCH, "branch", "declaringOperationSp + 1");
            return ex;
        }

        /**
         * Before emitting a return, we may need to emit instructions to "resolve" pending
         * operations (like finally handlers). We may also need to close and reopen certain bytecode
         * ranges, like exception handlers, which should not apply to those emitted instructions.
         */
        private CodeExecutableElement createBeforeEmitReturn() {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "beforeEmitReturn");
            ex.addParameter(new CodeVariableElement(type(int.class), "parentBci"));
            emitStackWalksBeforeEarlyExit(ex, OperationKind.RETURN, "return", "state.rootOperationSp + 1");
            return ex;
        }

        /**
         * Before emitting a yield, we may need to emit additional instructions for tag
         * instrumentation.
         */
        private CodeExecutableElement createDoEmitTagYield(InstructionModel instr) {
            if (!model.enableTagInstrumentation || !model.hasYieldOperation()) {
                throw new AssertionError("cannot produce method");
            }

            String methodName = switch (instr.kind) {
                case TAG_YIELD -> "doEmitTagYield";
                case TAG_YIELD_NULL -> "doEmitTagYieldNull";
                default -> throw new AssertionError("Unexpected tag yield instruction " + instr);
            };

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), methodName);

            CodeTreeBuilder b = ex.createBuilder();
            b.startIf().string("tags == 0").end().startBlock();
            b.returnDefault();
            b.end();

            buildOperationStackWalk(b, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();

                OperationModel op = model.findOperation(OperationKind.TAG);
                b.startCase().tree(createOperationConstant(op)).end();
                b.startBlock();
                buildEmitInstruction(b, null, instr, operationStack.read(op, operationFields.nodeId));
                b.statement("break");
                b.end(); // case tag

                b.end(); // switch
            });

            return ex;
        }

        /**
         * Before emitting a yield, we may need to emit additional instructions for tag
         * instrumentation.
         */
        private CodeExecutableElement createDoEmitTagResume() {
            if (!model.enableTagInstrumentation || !model.hasYieldOperation()) {
                throw new AssertionError("cannot produce method");
            }

            CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitTagResume");
            CodeTreeBuilder b = ex.createBuilder();
            b.declaration(type(int.class), "tagResumeBci", "-1");

            b.startIf().string("tags == 0").end().startBlock();
            b.startReturn().string("tagResumeBci").end();
            b.end();

            buildOperationStackWalkFromBottom(b, "state.rootOperationSp", () -> {
                b.startSwitch().string("operation.operation").end().startBlock();
                OperationModel op = model.findOperation(OperationKind.TAG);
                b.startCase().tree(createOperationConstant(op)).end();
                b.startBlock();
                b.startAssign("tagResumeBci").string("state.bci").end();
                buildEmitInstruction(b, null, model.tagResumeInstruction, operationStack.read(op, operationFields.nodeId));
                b.statement("break");
                b.end(); // case tag

                b.end(); // switch
            });
            b.startReturn().string("tagResumeBci").end();

            return ex;
        }

        /**
         * Generates code to walk the operation stack and emit any "exit" instructions before a
         * branch/return. Also closes and reopens bytecode ranges that should not apply to those
         * emitted instructions.
         */
        private void emitStackWalksBeforeEarlyExit(CodeExecutableElement ex, OperationKind operationKind, String friendlyInstructionName, String lowestOperationIndex) {
            addJavadoc(ex, "Walks the operation stack, emitting instructions for any operations that need to complete before the " + friendlyInstructionName +
                            " (and fixing up bytecode ranges to exclude these instructions).");
            CodeTreeBuilder b = ex.createBuilder();

            emitUnwindBeforeEarlyExit(b, operationKind, lowestOperationIndex);
            emitRewindBeforeEarlyExit(b, operationKind, lowestOperationIndex);
        }

        /**
         * Generates code to walk the operation stack and emit exit instructions. Also closes
         * exception ranges for exception handlers where necessary.
         */
        private void emitUnwindBeforeEarlyExit(CodeTreeBuilder b, OperationKind operationKind, String lowestOperationIndex) {
            b.startJavadoc();
            b.string("Emit \"exit\" instructions for any pending operations, and close any bytecode ranges that should not apply to the emitted instructions.").newLine();
            b.end();
            if (operationKind == OperationKind.RETURN) {
                // Remember the bytecode index for boxing elimination.
                b.declaration(type(int.class), "childBci", "parentBci");
            }

            b.declaration(type(boolean.class), "needsRewind", "false");
            buildOperationStackWalk(b, lowestOperationIndex, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();

                if (model.enableTagInstrumentation) {
                    b.startCase().tree(createOperationConstant(model.tagOperation)).end();
                    b.startBlock();
                    b.startIf().string("state.reachable").end().startBlock();
                    if (operationKind == OperationKind.RETURN) {
                        buildEmitInstruction(b, null, model.tagLeaveValueInstruction, buildTagLeaveArguments(model.tagLeaveValueInstruction));
                        b.statement("childBci = state.bci - " + model.tagLeaveValueInstruction.getInstructionLength());
                    } else {
                        if (operationKind != OperationKind.BRANCH) {
                            throw new AssertionError("unexpected operation kind used for unwind code generation.");
                        }
                        buildEmitInstruction(b, null, model.tagLeaveVoidInstruction, operationStack.read(model.tagOperation, operationFields.nodeId));
                    }
                    b.statement("state.doCreateExceptionHandler(",
                                    operationStack.read(model.tagOperation, operationFields.handlerStartBci),
                                    ", state.bci, HANDLER_TAG_EXCEPTIONAL, ",
                                    operationStack.read(model.tagOperation, operationFields.nodeId), ", ",
                                    operationStack.read(model.tagOperation, operationFields.startStackHeight), ")");
                    b.statement("needsRewind = true");
                    b.end(); // reachable
                    b.statement("break");
                    b.end(); // case tag
                }

                if (operationKind == OperationKind.RETURN && model.epilogReturn != null) {
                    b.startCase().tree(createOperationConstant(model.epilogReturn.operation)).end();
                    b.startBlock();
                    buildEmitOperationInstruction(b, model.epilogReturn.operation, "childBci", null);
                    b.statement("childBci = state.bci - " + model.epilogReturn.operation.instruction.getInstructionLength());
                    b.statement("break");
                    b.end(); // case epilog
                }

                for (OperationKind finallyOpKind : List.of(OperationKind.TRY_FINALLY, OperationKind.TRY_CATCH_OTHERWISE)) {
                    OperationModel op = model.findOperation(finallyOpKind);
                    b.startCase().tree(createOperationConstant(op)).end();
                    b.startBlock();
                    b.startIf().string("operation.childCount == 0 /* still in try */").end().startBlock();
                    b.startIf().string("state.reachable").end().startBlock();
                    emitExtraExceptionTableEntry(b, op);
                    b.statement("needsRewind = true");
                    b.end(); // if reachable
                    b.statement("doEmitFinallyHandler(operation, i)");
                    b.end(); // if in try
                    b.statement("break");
                    b.end(); // case finally
                }

                OperationModel tryCatch = model.findOperation(OperationKind.TRY_CATCH);
                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH))).end();
                b.startBlock();
                b.startIf().string("operation.childCount == 0 /* still in try */ && state.reachable").end().startBlock();
                emitExtraExceptionTableEntry(b, tryCatch);
                b.statement("needsRewind = true");
                b.end(); // if in try and reachable
                b.statement("break");
                b.end(); // case trycatch

                b.startCase().tree(createOperationConstant(model.sourceSectionPrefixOperation)).end();
                b.startBlock();
                b.startStatement().startCall("state.doEmitSourceInfo");
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.sourceIndex));
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.startBci));
                b.string("state.bci");
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.start));
                b.string(operationStack.read(model.sourceSectionPrefixOperation, operationFields.length));
                b.end(2);
                b.statement("needsRewind = true");
                b.statement("break");
                b.end(); // case source section

                b.startCase().tree(createOperationConstant(model.sourceSectionSuffixOperation)).end();
                b.startBlock();
                b.startAssign("int operationStart").startCall("state.doEmitSourceInfo");
                b.string(operationStack.read(model.sourceSectionSuffixOperation, operationFields.sourceIndex));
                b.string(operationStack.read(model.sourceSectionSuffixOperation, operationFields.startBci));
                b.string("state.bci");
                b.string(operationStack.read(model.sourceSectionSuffixOperation, operationFields.start));
                b.string("PATCH_CURRENT_SOURCE");
                b.end(2);
                b.tree(operationStack.write(model.sourceSectionSuffixOperation, operationFields.start, "operationStart"));
                b.statement("needsRewind = true");
                b.statement("break");
                b.end();

                if (model.enableBlockScoping) {
                    b.startCase().tree(createOperationConstant(model.blockOperation)).end();
                    b.startBlock();
                    b.startFor().string("int j = 0; j < ", operationStack.read(model.blockOperation, operationFields.numLocals), "; j++").end().startBlock();

                    b.statement("state.locals[", operationStack.read(model.blockOperation, operationFields.locals), "[j] + LOCALS_OFFSET_END_BCI] = state.bci");
                    if (operationKind == OperationKind.BRANCH) {
                        buildEmitInstruction(b, null, model.clearLocalInstruction,
                                        safeCastShort("state.locals[" + operationStack.read(model.blockOperation, operationFields.locals) + "[j] + LOCALS_OFFSET_FRAME_INDEX]"));
                    }
                    b.statement("needsRewind = true");
                    b.end(); // for
                    b.statement("break");
                    b.end(); // case block
                }

                b.end(); // switch
            });
        }

        private void emitExtraExceptionTableEntry(CodeTreeBuilder b, OperationModel op) {
            b.startDeclaration(type(int.class), "handlerTableIndex");
            b.string("state.doCreateExceptionHandler(", operationStack.read(op, operationFields.tryStartBci), ", state.bci, HANDLER_CUSTOM, -",
                            operationStack.read(op, operationFields.handlerId), ", ", UNINIT, " /* stack height */)");
            b.end();
            b.startIf().string("handlerTableIndex != ", UNINIT).end().startBlock();
            b.startIf().string(operationStack.read(op, operationFields.extraTableEntriesStart), " == ", UNINIT).end().startBlock();
            b.tree(operationStack.write(op, operationFields.extraTableEntriesStart, "handlerTableIndex"));
            b.end();
            b.tree(operationStack.write(op, operationFields.extraTableEntriesEnd, "handlerTableIndex + EXCEPTION_HANDLER_LENGTH"));
            b.end();
        }

        /**
         * Generates code to reopen bytecode ranges after "exiting" the parent operations.
         */
        private void emitRewindBeforeEarlyExit(CodeTreeBuilder b, OperationKind operationKind, String lowestOperationIndex) {
            b.startJavadoc();
            b.string("Now that all \"exit\" instructions have been emitted, reopen bytecode ranges.").newLine();
            b.end();
            b.startIf().string("needsRewind").end().startBlock();

            buildOperationStackWalkFromBottom(b, lowestOperationIndex, () -> {
                b.startSwitch().string("operation.operation").end().startBlock();

                if (model.enableTagInstrumentation) {
                    b.startCase().tree(createOperationConstant(model.tagOperation)).end();
                    b.startBlock();
                    b.tree(operationStack.write(model.tagOperation, operationFields.handlerStartBci, "state.bci"));
                    b.statement("break");
                    b.end();
                }

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_FINALLY))).end();
                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH_OTHERWISE))).end();
                b.startCaseBlock();
                b.startIf().string("operation.childCount == 0 /* still in try */").end().startBlock();
                b.tree(operationStack.write(model.findOperation(OperationKind.TRY_FINALLY), operationFields.tryStartBci, "state.bci"));
                b.end(); // if
                b.statement("break");
                b.end(); // case finally

                b.startCase().tree(createOperationConstant(model.findOperation(OperationKind.TRY_CATCH))).end();
                b.startCaseBlock();
                b.startIf().string("operation.childCount == 0 /* still in try */").end().startBlock();
                b.tree(operationStack.write(model.tryCatchOperation, operationFields.tryStartBci, "state.bci"));
                b.end(); // if
                b.statement("break");
                b.end(); // case trycatch

                b.startCase().tree(createOperationConstant(model.sourceSectionPrefixOperation)).end();
                b.startBlock();
                b.tree(operationStack.write(model.sourceSectionPrefixOperation, operationFields.startBci, "state.bci"));
                b.statement("break");
                b.end(); // case source section

                b.startCase().tree(createOperationConstant(model.sourceSectionSuffixOperation)).end();
                b.startBlock();
                b.tree(operationStack.write(model.sourceSectionPrefixOperation, operationFields.startBci, "state.bci"));
                b.statement("break");
                b.end(); // case source section

                if (model.enableBlockScoping) {
                    b.startCase().tree(createOperationConstant(model.blockOperation)).end();
                    b.startBlock();
                    b.startFor().string("int j = 0; j < ", operationStack.read(model.blockOperation, operationFields.numLocals), "; j++").end().startBlock();
                    b.declaration(type(int.class), "prevTableIndex", operationStack.read(model.blockOperation, operationFields.locals) + "[j]");

                    /**
                     * We need to emit multiple local ranges if instructions were emitted after
                     * unwinding the block (i.e., instructions at which the local is not live).
                     * Otherwise, we can reuse the same local table entry. We cannot reuse the entry
                     * after a branch because we emit a clear.local instruction when unwinding.
                     */
                    if (operationKind != OperationKind.BRANCH) {
                        b.declaration(type(int.class), "endBci", "state.locals[prevTableIndex + LOCALS_OFFSET_END_BCI]");
                        b.startIf().string("endBci == state.bci").end().startBlock();
                        b.lineComment("No need to split. Reuse the existing entry.");
                        b.statement("state.locals[prevTableIndex + LOCALS_OFFSET_END_BCI] = ", UNINIT);
                        b.statement("continue");
                        b.end();
                    }

                    b.lineComment("Create a new table entry with a new bytecode range and the same metadata.");
                    b.declaration(type(int.class), "localIndex", "state.locals[prevTableIndex + LOCALS_OFFSET_LOCAL_INDEX]");
                    b.declaration(type(int.class), "frameIndex", "state.locals[prevTableIndex + LOCALS_OFFSET_FRAME_INDEX]");
                    b.declaration(type(int.class), "nameIndex", "state.locals[prevTableIndex + LOCALS_OFFSET_NAME]");
                    b.declaration(type(int.class), "infoIndex", "state.locals[prevTableIndex + LOCALS_OFFSET_INFO]");
                    b.statement(operationStack.read(model.blockOperation, operationFields.locals), "[j] = state.doEmitLocal(localIndex, frameIndex, nameIndex, infoIndex)");
                    b.end(); // for
                    b.end(); // case block
                }

                b.end(); // switch
            });
            b.end(); // if
        }

        private String[] buildTagLeaveArguments(InstructionModel instr) {
            InstructionImmediate operandIndex = instr.getImmediate(ImmediateKind.BYTECODE_INDEX);
            String[] args;
            if (operandIndex == null) {
                args = new String[]{operationStack.read(instr.operation, operationFields.nodeId)};
            } else {
                args = new String[]{operationStack.read(instr.operation, operationFields.nodeId), "childBci"};
            }
            return args;
        }

        final class RootStackElement extends CodeTypeElement {

            final Map<InstructionEncoding, CodeExecutableElement> doEmitInstructionMethods = new TreeMap<>();

            RootStackElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "RootStackElement");

                TypeMirror referenceType = generic(SoftReference.class, this.asType());
                TypeMirror deque = generic(type(ThreadLocal.class), referenceType);
                this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), deque, "THREAD_LOCAL")).createInitBuilder().string("new ThreadLocal<>()");

                this.add(createAcquire());
                this.add(createRelease());
                this.add(createGetNext());
                this.add(createCleanup());
            }

            void lazyInit() {
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), rootStackElement.asType(), "parent"));
                CodeExecutableElement ctor = this.add(createConstructorUsingFields(Set.of(), this, null));
                this.add(new CodeVariableElement(Set.of(PRIVATE), rootStackElement.asType(), "next"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(boolean.class), "needsClean"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), generic(SoftReference.class, this.asType()), "reference")).createInitBuilder().startNew(
                                generic(SoftReference.class, this.asType())).string("this").end();

                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "operationSequenceNumber"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(operationStack.asType()), "operationStack"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "operationSp"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "rootOperationSp"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(boolean.class), "reachable")).createInitBuilder().string("true");
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(byte[].class), "bc"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "bci"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numLocals"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numLabels"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numNodes"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numHandlers"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "numConditionalBranches"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "currentStackHeight"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "maxStackHeight"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int[].class), "sourceInfo"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "sourceInfoIndex"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int[].class), "handlerTable"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "handlerTableSize"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), arrayOf(type(int.class)), "locals"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "localsTableIndex"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), types.BytecodeSupport_ConstantsBuffer, "constants"));
                this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "instrumentations"));

                if (model.hasYieldOperation()) {
                    /**
                     * Invariant: Continuation locations are sorted by bci, which means we can
                     * iterate over the bytecodes and continuation locations in lockstep (i.e., the
                     * i-th yield instruction uses the i-th continuation location).
                     */
                    this.add(new CodeVariableElement(Set.of(PRIVATE), type(int[].class), "continuations"));
                    this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "continuationsIndex"));
                }

                if (model.enableBlockScoping) {
                    this.add(new CodeVariableElement(Set.of(PRIVATE), type(int.class), "maxLocals"));
                }

                if (model.enableTagInstrumentation) {
                    this.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagRoots"));
                    this.add(new CodeVariableElement(Set.of(PRIVATE), generic(type(List.class), tagNode.asType()), "tagNodes"));
                }

                CodeTreeBuilder b = ctor.createBuilder();

                /*
                 * We initialize the fields declared on builderState here when beginRoot is called.
                 */
                b.statement("this.parent = parent");
                b.statement("this.operationSequenceNumber = 0");
                b.statement("this.rootOperationSp = -1");
                b.startAssign("this.operationStack").startNewArray(arrayOf(operationStack.asType()), CodeTreeBuilder.singleString("32")).end().end();
                b.startFor().string("int i = 0; i < this.operationStack.length; i++").end().startBlock();
                b.startAssign("this.operationStack[i]").startNew(operationStack.asType()).end().end();
                b.end();

                b.statement("this.reachable = true");
                if (model.enableTagInstrumentation) {
                    b.statement("this.tagRoots = null");
                    b.statement("this.tagNodes = null");
                }
                b.statement("this.numLocals = 0");
                if (model.enableBlockScoping) {
                    b.statement("this.maxLocals = numLocals");
                }
                b.statement("this.numLabels = 0");
                b.statement("this.numNodes = 0");
                b.statement("this.numHandlers = 0");
                b.statement("this.numConditionalBranches = 0");
                b.startAssign("this.constants").startNew(types.BytecodeSupport_ConstantsBuffer).end().end();
                b.statement("this.bc = new byte[512]");
                b.statement("this.bci = 0");
                b.statement("this.currentStackHeight = 0");
                b.statement("this.maxStackHeight = 0");
                b.statement("this.handlerTable = new int[8 * EXCEPTION_HANDLER_LENGTH]");
                b.statement("this.handlerTableSize = 0");
                b.statement("this.locals = null");
                b.statement("this.localsTableIndex = 0");
                if (model.hasYieldOperation()) {
                    b.statement("this.continuations = new int[4 * CONTINUATION_LENGTH]");
                    b.statement("this.continuationsIndex = 0");
                }
                b.statement("this.sourceInfo = new int[16 * SOURCE_INFO_LENGTH]");
                b.statement("this.sourceInfoIndex = 0");
                b.statement("this.instrumentations = 0");

                this.add(createReset());
                this.add(createPushOperation());
                this.add(createPopOperation());
                this.add(createPeekOperation());
                this.add(createUpdateMaxStackHeight());
                this.add(createEnsureBytecodeCapacity());
                this.add(createAddConstant());
                this.add(createAllocateConstantSlot());
                this.add(createToConstants());
                this.add(createAllocateNode());
                this.add(createAllocateBytecodeLocal());
                this.add(createAllocateBranchProfile());

                if (model.hasYieldOperation()) {
                    this.add(createAllocateContinuationConstant());
                    this.add(createDoEmitContinuation());
                }

                this.add(createDoEmitLocal());
                this.add(createDoEmitLocalConstantIndices());
                this.add(createAllocateLocalsTableEntry());
                this.add(createPatchHandlerTable());
                this.add(createDoCreateExceptionHandler());
                this.add(createDoPatchSourceInfo());
                this.add(createDoEmitSourceInfo());
                this.add(createRegisterUnresolvedLabel());
                this.add(createResolveUnresolvedLabel());

                if (model.enableInstructionTracing) {
                    this.add(createDoEmitTraceInstruction());
                }
            }

            void lateInit() {
                // we do this late to ensure that all instruction methods are known.
                this.addAll(doEmitInstructionMethods.values());
                this.add(createToString());
            }

            private CodeExecutableElement createReset() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "reset");
                CodeTreeBuilder doc = ex.createDocBuilder();
                doc.startJavadoc();
                doc.string("Resets all internal state to be usable for the next root.");
                doc.newLine();
                doc.end();
                CodeTreeBuilder b = ex.createBuilder();

                b.statement("this.rootOperationSp = -1");
                b.statement("this.reachable = true");
                if (model.enableTagInstrumentation) {
                    b.statement("this.tagRoots = null");
                    b.statement("this.tagNodes = null");
                }
                b.statement("this.numLocals = 0");
                if (model.enableBlockScoping) {
                    b.statement("this.maxLocals = 0");
                }
                b.statement("this.numLabels = 0");
                b.statement("this.numNodes = 0");
                b.statement("this.numHandlers = 0");
                b.statement("this.numConditionalBranches = 0");
                b.statement("this.bci = 0");
                b.statement("this.currentStackHeight = 0");
                b.statement("this.maxStackHeight = 0");
                b.statement("this.handlerTableSize = 0");
                b.statement("this.localsTableIndex = 0");
                if (model.hasYieldOperation()) {
                    b.statement("this.continuationsIndex = 0");
                }
                b.statement("this.sourceInfoIndex = 0");
                b.statement("this.instrumentations = 0");
                b.statement("this.needsClean = true");

                return ex;
            }

            private CodeExecutableElement createAcquire() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(STATIC), asType(), "acquire");
                CodeTreeBuilder b = ex.createBuilder();
                TypeMirror referenceType = generic(SoftReference.class, this.asType());
                b.declaration(referenceType, "ref", "THREAD_LOCAL.get()");
                b.startIf().string("ref != null").end().startBlock();
                b.statement("THREAD_LOCAL.set(null)");
                b.declaration(asType(), "obj", "ref.get()");
                b.startIf().string("obj != null").end().startBlock();
                b.statement("return obj");
                b.end();
                b.end();

                b.startReturn().startNew(asType()).string("null").end().end();
                return ex;
            }

            private CodeExecutableElement createRelease() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(STATIC), type(void.class), "release");
                ex.addParameter(new CodeVariableElement(asType(), "obj"));
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("obj.cleanup()");
                b.startStatement().startCall("THREAD_LOCAL.set").string("obj.reference");
                b.end().end();
                return ex;
            }

            private CodeExecutableElement createGetNext() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), asType(), "getNext");
                CodeTreeBuilder b = ex.createBuilder();
                b.startIf().string("this.next == null").end().startBlock();
                b.startStatement().string("this.next = ").startNew(asType()).string("this").end().end();
                b.end();
                b.statement("return this.next");
                return ex;
            }

            private CodeExecutableElement createCleanup() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "cleanup");
                CodeTreeBuilder doc = ex.createDocBuilder();
                doc.startJavadoc();
                doc.string("Cleans up all object references before releasing it to the shared cache.");
                doc.newLine();
                doc.end();

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("!needsClean").end().startBlock();
                b.statement("return");
                b.end();

                b.lineComment("Only reset this for each set of root nodes.");
                b.statement("this.operationSequenceNumber = 0");
                b.lineComment("clear references to potentially expensive references");
                b.statement("this.constants.clear()");

                b.startIf().string("this.next != null").end().startBlock();
                b.statement("this.next.cleanup()");
                b.end();
                b.statement("needsClean = false");

                return ex;
            }

            private CodeExecutableElement createPushOperation() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "pushOperation");
                ex.addParameter(new CodeVariableElement(type(int.class), "operation"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int.class), "sp", "this.operationSp");
                b.declaration(arrayOf(operationStack.asType()), "stack", "this.operationStack");
                b.startIf().string("sp == stack.length").end().startBlock(); // {
                b.startAssign("stack = this.operationStack").startStaticCall(type(Arrays.class), "copyOf");
                b.string("stack");
                b.string("stack.length * 2");
                b.end(2);
                b.startFor().string("int i = sp; i < stack.length; i++").end().startBlock();
                b.startAssign("stack[i]").startNew(operationStack.asType()).end().end();
                b.end();
                b.end();

                b.declaration(operationStack.asType(), "entry", "stack[sp]");
                b.statement("entry.operation = operation");
                b.statement("entry.sequenceNumber = this.operationSequenceNumber++");
                b.statement("entry.childCount = 0");
                b.statement("this.operationSp = sp + 1");

                b.statement("return entry");

                return ex;
            }

            private CodeExecutableElement createPeekOperation() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "peekOperation");
                CodeTreeBuilder b = ex.createBuilder();
                b.startIf().string("this.operationSp <= 0").end().startBlock();
                b.statement("return null");
                b.end();
                b.statement("return this.operationStack[this.operationSp - 1]");
                return ex;
            }

            private CodeExecutableElement createPopOperation() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), operationStack.asType(), "popOperation");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return this.operationStack[--this.operationSp]");
                return ex;
            }

            private CodeExecutableElement createUpdateMaxStackHeight() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "updateMaxStackHeight");
                ex.addParameter(new CodeVariableElement(type(int.class), "stackHeight"));
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("this.maxStackHeight = Math.max(this.maxStackHeight, stackHeight)");

                b.startIf().string("this.maxStackHeight > Short.MAX_VALUE").end().startBlock();
                emitThrowEncodingException(b, "\"Maximum stack height exceeded.\"");
                b.end();
                b.end(2);
                return ex;
            }

            private CodeExecutableElement createEnsureBytecodeCapacity() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "ensureBytecodeCapacity");
                ex.addParameter(new CodeVariableElement(type(int.class), "size"));
                CodeTreeBuilder b = ex.createBuilder();
                b.startIf().string("size > this.bc.length").end().startBlock();
                b.startAssign("this.bc").startStaticCall(type(Arrays.class), "copyOf");
                b.string("this.bc");
                b.string("Math.max(size, this.bc.length * 2)");
                b.end(2); // assign, static call
                b.end(); // if block
                return ex;
            }

            private CodeExecutableElement createAddConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class),
                                "addConstant");
                ex.addParameter(new CodeVariableElement(type(Object.class), "constant"));

                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return constants.add(constant)");

                return ex;
            }

            private CodeExecutableElement createAllocateConstantSlot() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(short.class),
                                "allocateConstantSlot");
                CodeTreeBuilder doc = ex.createDocBuilder();
                doc.startJavadoc();
                doc.string("Allocates a slot for a constant which will be manually added to the constant pool later.");
                doc.newLine();
                doc.end();

                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return safeCastShort(constants.addNull())");
                return ex;
            }

            private CodeExecutableElement createToConstants() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), new ArrayCodeTypeMirror(type(Object.class)), "toConstants");

                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return constants.materialize()");

                return ex;
            }

            private CodeExecutableElement createAllocateNode() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateNode");
                CodeTreeBuilder b = ex.createBuilder();
                b.startIf().string("!this.reachable").end().startBlock();
                b.statement("return -1");
                b.end();

                b.startReturn().startCall("checkOverflowInt");
                b.string("this.numNodes++");
                b.doubleQuote("Node counter");
                b.end(2);

                return ex;
            }

            private CodeExecutableElement createAllocateBytecodeLocal() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "allocateBytecodeLocal");
                CodeTreeBuilder b = ex.createBuilder();

                b.startReturn().startCall("checkOverflowShort");
                b.string("(short) this.numLocals++");
                b.doubleQuote("Number of locals");
                b.end(2);

                return ex;
            }

            private CodeExecutableElement createAllocateBranchProfile() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateBranchProfile");
                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("!this.reachable").end().startBlock();
                b.statement("return -1");
                b.end();

                b.startReturn().startCall("checkOverflowInt");
                b.string("this.numConditionalBranches++");
                b.doubleQuote("Number of branch profiles");
                b.end(2);

                return ex;
            }

            private CodeExecutableElement createAllocateContinuationConstant() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(short.class), "allocateContinuationConstant");
                CodeTreeBuilder b = ex.createBuilder();

                /**
                 * NB: We need to allocate constant pool slots for continuations regardless of
                 * reachability in order to keep the constant pool consistent. In rare scenarios,
                 * reparsing can make a previously unreachable yield reachable (e.g., reparsing with
                 * tags)
                 */
                b.startReturn();
                b.string("allocateConstantSlot()");
                b.end();

                return ex;
            }

            private CodeExecutableElement createDoEmitLocal() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitLocal");
                if (model.enableBlockScoping) {
                    ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
                    ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
                }
                ex.addParameter(new CodeVariableElement(type(Object.class), "name"));
                ex.addParameter(new CodeVariableElement(type(Object.class), "info"));
                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int.class), "nameIndex", "-1");
                b.startIf().string("name != null").end().startBlock();
                b.statement("nameIndex = this.addConstant(name)");
                b.end();

                b.declaration(type(int.class), "infoIndex", "-1");
                b.startIf().string("info != null").end().startBlock();
                b.statement("infoIndex = this.addConstant(info)");
                b.end();

                b.startReturn().startCall("doEmitLocal");
                if (model.enableBlockScoping) {
                    b.string("localIndex");
                    b.string("frameIndex");
                }
                b.string("nameIndex");
                b.string("infoIndex");
                b.end(2);
                return ex;
            }

            private CodeExecutableElement createPatchHandlerTable() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "patchHandlerTable");
                ex.addParameter(new CodeVariableElement(type(int.class), "tableStart"));
                ex.addParameter(new CodeVariableElement(type(int.class), "tableEnd"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerId"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerSp"));

                addJavadoc(ex, """
                                Iterates the handler table, searching for unresolved entries corresponding to the given handlerId.
                                Patches them with the handlerBci and handlerSp now that those values are known.
                                """);

                CodeTreeBuilder b = ex.createBuilder();

                b.startFor().string("int i = tableStart; i < tableEnd; i += EXCEPTION_HANDLER_LENGTH").end().startBlock();

                b.startIf().string("this.handlerTable[i + EXCEPTION_HANDLER_OFFSET_KIND] != HANDLER_CUSTOM").end().startBlock();
                b.statement("continue");
                b.end();
                b.startIf().string("this.handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] != -handlerId").end().startBlock();
                b.statement("continue");
                b.end();

                b.statement("this.handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci");
                b.statement("this.handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp");
                b.end();

                return ex;
            }

            private CodeExecutableElement createDoEmitLocalConstantIndices() {
                if (model.enableBlockScoping) {
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_START_BCI")).createInitBuilder().string("0");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_END_BCI")).createInitBuilder().string("1");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_LOCAL_INDEX")).createInitBuilder().string("2");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_FRAME_INDEX")).createInitBuilder().string("3");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_NAME")).createInitBuilder().string("4");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_INFO")).createInitBuilder().string("5");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_LENGTH")).createInitBuilder().string("6");
                } else {
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_NAME")).createInitBuilder().string("0");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_OFFSET_INFO")).createInitBuilder().string("1");
                    BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "LOCALS_LENGTH")).createInitBuilder().string("2");
                }

                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitLocal");
                if (model.enableBlockScoping) {
                    ex.addParameter(new CodeVariableElement(type(int.class), "localIndex"));
                    ex.addParameter(new CodeVariableElement(type(int.class), "frameIndex"));
                }
                ex.addParameter(new CodeVariableElement(type(int.class), "nameIndex"));
                ex.addParameter(new CodeVariableElement(type(int.class), "infoIndex"));
                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int.class), "tableIndex", "allocateLocalsTableEntry()");

                if (model.enableBlockScoping) {
                    b.statement("assert frameIndex - USER_LOCALS_START_INDEX >= 0");
                    b.statement("this.locals[tableIndex + LOCALS_OFFSET_START_BCI] = this.bci");
                    b.lineComment("will be patched later at the end of the block");
                    b.statement("this.locals[tableIndex + LOCALS_OFFSET_END_BCI] = -1");
                    b.statement("this.locals[tableIndex + LOCALS_OFFSET_LOCAL_INDEX] = localIndex");
                    b.statement("this.locals[tableIndex + LOCALS_OFFSET_FRAME_INDEX] = frameIndex");
                }
                b.statement("this.locals[tableIndex + LOCALS_OFFSET_NAME] = nameIndex");
                b.statement("this.locals[tableIndex + LOCALS_OFFSET_INFO] = infoIndex");

                b.statement("return tableIndex");
                return ex;
            }

            private CodeExecutableElement createAllocateLocalsTableEntry() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "allocateLocalsTableEntry");
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("int result = this.localsTableIndex");
                b.startIf().string("this.locals == null").end().startBlock();
                b.startAssert().string("result == 0").end();
                b.startAssign("this.locals").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("LOCALS_LENGTH * 8")).end().end();
                b.end().startElseIf().string("result + LOCALS_LENGTH > this.locals.length").end().startBlock();
                b.startAssign("this.locals").startStaticCall(type(Arrays.class), "copyOf");
                b.string("this.locals");
                b.string("Math.max(result + LOCALS_LENGTH, this.locals.length * 2)");
                b.end(2); // assign, static call
                b.end(); // if block
                b.statement("this.localsTableIndex += LOCALS_LENGTH");
                b.statement("return result");
                return ex;
            }

            private CodeExecutableElement createDoPatchSourceInfo() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doPatchSourceInfo");
                ex.addParameter(new CodeVariableElement(generic(ArrayList.class, BytecodeRootNodeElement.this.asType()), "nodes"));
                ex.addParameter(new CodeVariableElement(type(int.class), "nodeId"));
                ex.addParameter(new CodeVariableElement(type(int.class), "sourceIndex"));
                ex.addParameter(new CodeVariableElement(type(int.class), "start"));
                ex.addParameter(new CodeVariableElement(type(int.class), "length"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int[].class), "info");
                b.startIf().string("nodeId >= 0").end().startBlock();
                b.statement("info = nodes.get(nodeId).bytecode.sourceInfo");
                b.end().startElseBlock();
                b.statement("info = this.sourceInfo");
                b.end();

                b.declaration(type(int.class), "index", "sourceIndex");
                b.startWhile().string("index >= 0").end().startBlock();

                b.declaration(type(int.class), "oldStart", "info[index + SOURCE_INFO_OFFSET_START]");
                b.declaration(type(int.class), "oldEnd", "info[index + SOURCE_INFO_OFFSET_LENGTH]");
                b.statement("assert nodeId >= 0 ? oldEnd == PATCH_NODE_SOURCE : oldEnd == PATCH_CURRENT_SOURCE");
                b.statement("info[index + SOURCE_INFO_OFFSET_START] = start");
                b.statement("info[index + SOURCE_INFO_OFFSET_LENGTH] = length");
                b.statement("index = oldStart");
                b.end();

                return ex;
            }

            private CodeExecutableElement createDoEmitSourceInfo() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doEmitSourceInfo");
                ex.addParameter(new CodeVariableElement(type(int.class), "sourceIndex"));
                ex.addParameter(new CodeVariableElement(type(int.class), "startBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "endBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "start"));
                ex.addParameter(new CodeVariableElement(type(int.class), "length"));

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("this.rootOperationSp == -1").end().startBlock();
                b.statement("return -1");
                b.end();

                b.declaration(type(int.class), "index", "this.sourceInfoIndex");
                b.declaration(type(int.class), "prevIndex", "index - SOURCE_INFO_LENGTH");

                b.startIf();
                b.string("prevIndex >= 0").newLine().startIndention();
                b.string(" && start >= -1 && length >= -1");
                b.string(" && (this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_SOURCE]) == sourceIndex").newLine();
                b.string(" && (this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START]) == start").newLine();
                b.string(" && (this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_LENGTH]) == length");
                b.end(2).startBlock();

                b.startIf().string("(this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START_BCI]) == startBci").newLine().startIndention();
                b.string(" && (this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == endBci");
                b.end(2).startBlock();
                b.lineComment("duplicate entry");
                b.statement("return prevIndex");
                b.end();

                b.startElseIf().string("(this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == startBci").end().startBlock();
                b.lineComment("contiguous entry");
                b.statement("this.sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI] = endBci");
                b.statement("return prevIndex");
                b.end();

                b.end(); // if source, start, length match

                b.startIf().string("index >= this.sourceInfo.length").end().startBlock();
                b.statement("this.sourceInfo = Arrays.copyOf(this.sourceInfo, this.sourceInfo.length * 2)");
                b.end();

                b.statement("this.sourceInfo[index + SOURCE_INFO_OFFSET_START_BCI] = startBci");
                b.statement("this.sourceInfo[index + SOURCE_INFO_OFFSET_END_BCI] = endBci");
                b.statement("this.sourceInfo[index + SOURCE_INFO_OFFSET_SOURCE] = sourceIndex");
                b.statement("this.sourceInfo[index + SOURCE_INFO_OFFSET_START] = start");
                b.statement("this.sourceInfo[index + SOURCE_INFO_OFFSET_LENGTH] = length");

                b.statement("this.sourceInfoIndex = index + SOURCE_INFO_LENGTH");

                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_START_BCI")).createInitBuilder().string("0");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_END_BCI")).createInitBuilder().string("1");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_SOURCE")).createInitBuilder().string("2");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_START")).createInitBuilder().string("3");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_OFFSET_LENGTH")).createInitBuilder().string("4");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "SOURCE_INFO_LENGTH")).createInitBuilder().string("5");

                b.statement("return index");

                return ex;
            }

            private CodeExecutableElement createDoEmitContinuation() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitContinuation");
                ex.addParameter(new CodeVariableElement(type(int.class), "cpi"));
                ex.addParameter(new CodeVariableElement(type(int.class), "continuationBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "sp"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int[].class), "table", "this.continuations");
                b.declaration(type(int.class), "index", "this.continuationsIndex");
                b.startIf().string("index + CONTINUATION_LENGTH >= table.length").end().startBlock();
                b.statement("table = this.continuations = Arrays.copyOf(this.continuations, this.continuations.length * 2)");
                b.end();

                b.statement("table[index + CONTINUATION_OFFSET_CPI] = cpi");
                b.statement("table[index + CONTINUATION_OFFSET_BCI] = continuationBci");
                b.statement("table[index + CONTINUATION_OFFSET_SP] = sp");

                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "CONTINUATION_OFFSET_CPI")).createInitBuilder().string("0");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "CONTINUATION_OFFSET_BCI")).createInitBuilder().string("1");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "CONTINUATION_OFFSET_SP")).createInitBuilder().string("2");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "CONTINUATION_LENGTH")).createInitBuilder().string("3");

                b.statement("this.continuationsIndex += CONTINUATION_LENGTH");

                return ex;
            }

            private CodeExecutableElement createDoCreateExceptionHandler() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "doCreateExceptionHandler");
                ex.addParameter(new CodeVariableElement(type(int.class), "startBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "endBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerKind"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerBci"));
                ex.addParameter(new CodeVariableElement(type(int.class), "handlerSp"));

                CodeTreeBuilder b = ex.createBuilder();

                b.startAssert().string("startBci <= endBci").end();

                b.lineComment("Don't create empty handler ranges.");
                b.startIf().string("startBci == endBci").end().startBlock();
                b.startReturn().string(UNINIT).end();
                b.end();

                b.lineComment("If the previous entry is for the same handler and the ranges are contiguous, combine them.");
                b.startIf().string("this.handlerTableSize > 0").end().startBlock();
                b.declaration(type(int.class), "previousEntry", "this.handlerTableSize - EXCEPTION_HANDLER_LENGTH");
                b.declaration(type(int.class), "previousEndBci", "this.handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI]");
                b.declaration(type(int.class), "previousKind", "this.handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_KIND]");
                b.declaration(type(int.class), "previousHandlerBci", "this.handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
                b.startIf().string("previousEndBci == startBci && previousKind == handlerKind && previousHandlerBci == handlerBci").end().startBlock();
                b.statement("this.handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci");
                b.startReturn().string(UNINIT).end();
                b.end(); // if same handler and contiguous
                b.end(); // if table non-empty

                b.startIf().string("this.handlerTable.length <= this.handlerTableSize + EXCEPTION_HANDLER_LENGTH").end().startBlock();
                b.statement("this.handlerTable = Arrays.copyOf(this.handlerTable, this.handlerTable.length * 2)");
                b.end();

                b.statement("int result = this.handlerTableSize");
                b.statement("this.handlerTable[result + EXCEPTION_HANDLER_OFFSET_START_BCI] = startBci");
                b.statement("this.handlerTable[result + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci");
                b.statement("this.handlerTable[result + EXCEPTION_HANDLER_OFFSET_KIND] = handlerKind");
                b.statement("this.handlerTable[result + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci");
                b.statement("this.handlerTable[result + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp");
                b.statement("this.handlerTableSize += EXCEPTION_HANDLER_LENGTH");

                b.statement("return result");

                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_START_BCI")).createInitBuilder().string("0");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_END_BCI")).createInitBuilder().string("1");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_KIND")).createInitBuilder().string("2");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_HANDLER_BCI")).createInitBuilder().string("3");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_OFFSET_HANDLER_SP")).createInitBuilder().string("4");
                BytecodeRootNodeElement.this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), type(int.class), "EXCEPTION_HANDLER_LENGTH")).createInitBuilder().string("5");

                return ex;
            }

            private CodeExecutableElement ensureDoEmitInstructionCreated(InstructionModel instruction) {
                return doEmitInstructionMethods.computeIfAbsent(instruction.getInstructionEncoding(), (e) -> createDoEmitInstruction(e));
            }

            private CodeExecutableElement createDoEmitTraceInstruction() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "doEmitTraceInstruction");

                CodeTreeBuilder b = ex.createBuilder();
                b.declaration(type(int.class), "newBci", "checkBci(this.bci + 2)");
                b.startIf().string("newBci > this.bc.length").end().startBlock();
                b.statement("this.ensureBytecodeCapacity(newBci)");
                b.end();

                b.statement(writeInstruction("this.bc", "this.bci", createInstructionConstant(model.traceInstruction)));
                b.statement("this.bci = newBci");

                return ex;

            }

            private CodeExecutableElement createDoEmitInstruction(InstructionEncoding encoding) {
                // Give each method a unique name so that we don't accidentally use the wrong
                // overload.
                StringBuilder methodName = new StringBuilder("doEmitInstruction");
                for (InstructionImmediateEncoding immediate : encoding.immediates()) {
                    methodName.append(immediate.width().toEncodedName());
                }

                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), methodName.toString());
                ex.addParameter(new CodeVariableElement(type(short.class), "instruction"));
                ex.addParameter(new CodeVariableElement(type(int.class), "stackEffect"));

                List<CodeVariableElement> dataParams = new ArrayList<>();
                for (int i = 0; i < encoding.immediates().size(); i++) {
                    InstructionImmediateEncoding immediate = encoding.immediates().get(i);
                    CodeVariableElement param = new CodeVariableElement(immediate.width().toType(context), "data" + i);
                    dataParams.add(param);
                    ex.addParameter(param);
                }

                CodeTreeBuilder b = ex.createBuilder();

                b.startIf().string("stackEffect != 0").end().startBlock();
                b.statement("this.currentStackHeight += stackEffect");
                b.startAssert().string("this.currentStackHeight >= 0").end();
                b.end();

                b.startIf().string("stackEffect > 0").end().startBlock();
                b.statement("this.updateMaxStackHeight(this.currentStackHeight)");
                b.end();

                b.startIf().string("!this.reachable").end().startBlock();
                b.statement("return -1");
                b.end();

                if (model.enableInstructionTracing) {
                    int mask = 1 << model.traceInstructionInstrumentationIndex;
                    b.startIf().string("(this.instrumentations & ").string("0x", Integer.toHexString(mask)).string(") != 0").end().startBlock();
                    b.statement("doEmitTraceInstruction()");
                    b.end();
                }

                b.declaration(type(int.class), "instructionBci", "this.bci");
                b.declaration(type(int.class), "newBci", "checkBci(instructionBci + " + encoding.length() + ")");
                b.startIf().string("newBci > this.bc.length").end().startBlock();
                b.statement("this.ensureBytecodeCapacity(newBci)");
                b.end();

                b.end();

                b.statement(writeInstruction("this.bc", "this.bci + 0", "instruction"));

                for (int i = 0; i < encoding.immediates().size(); i++) {
                    InstructionImmediateEncoding immediateEncoding = encoding.immediates().get(i);
                    CodeVariableElement dataParam = dataParams.get(i);
                    b.statement(writeImmediate("this.bc", "this.bci", dataParam.getName(), immediateEncoding));
                }

                b.statement("this.bci = newBci");
                b.statement("return instructionBci");

                return ex;
            }

            private CodeExecutableElement createRegisterUnresolvedLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "registerUnresolvedLabel");
                ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
                ex.addParameter(new CodeVariableElement(type(int.class), "immediateBci"));

                CodeTreeBuilder b = ex.createBuilder();
                b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");

                b.startIf().string("impl.unresolved0 == -1").end().startBlock();
                b.statement("impl.unresolved0 = immediateBci");
                b.statement("return");
                b.end();

                b.startIf().string("impl.unresolved1 == -1").end().startBlock();
                b.statement("impl.unresolved1 = immediateBci");
                b.statement("return");
                b.end();

                b.declaration(type(int.class), "index", "impl.unresolvedCount++");
                b.declaration(type(int[].class), "array", "impl.unresolvedArray");
                b.startIf().string("array == null").end().startBlock();
                b.statement("array = impl.unresolvedArray = new int[4]");
                b.end().startElseIf().string("index >= array.length").end().startBlock();
                b.statement("array = impl.unresolvedArray = Arrays.copyOf(array, array.length * 2)");
                b.end();
                b.statement("array[index] = immediateBci");
                return ex;
            }

            private CodeExecutableElement createResolveUnresolvedLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "resolveUnresolvedLabel");
                ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));
                ex.addParameter(new CodeVariableElement(type(int.class), "stackHeight"));

                CodeTreeBuilder b = ex.createBuilder();
                b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
                b.statement("assert !impl.isDefined()");
                b.statement("impl.bci = this.bci");

                b.startIf().string("impl.unresolved0 != -1").end().startBlock();
                b.statement(writeInt("this.bc", "impl.unresolved0", "this.bci"));
                b.end();

                b.startIf().string("impl.unresolved1 != -1").end().startBlock();
                b.statement(writeInt("this.bc", "impl.unresolved1", "this.bci"));
                b.end();

                b.startFor().string("int i = 0; i < impl.unresolvedCount; i++").end().startBlock();
                b.statement(writeInt("this.bc", "impl.unresolvedArray[i]", "this.bci"));
                b.end();

                return ex;
            }

            private CodeExecutableElement createToString() {
                CodeExecutableElement ex = GeneratorUtils.override(declaredType(Object.class), "toString");
                CodeTreeBuilder b = ex.createBuilder();

                b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();
                b.startStatement().startCall("b.append").startGroup().typeLiteral(this.asType()).string(".getSimpleName()").end().end().end();
                b.startStatement().startCall("b.append").doubleQuote("[").end().end();
                b.startStatement().startCall("b.append").doubleQuote("bytecodeIndex=").end().startCall(".append").string("this.bci").end().end();
                b.startStatement().startCall("b.append").doubleQuote(", stackPointer=").end().startCall(".append").string("this.currentStackHeight").end().end();
                b.startStatement().startCall("b.append").doubleQuote(", operations=").end().end();
                b.startStatement().startCall("b.append").string("System.lineSeparator()").end().end();

                // for operation stacks
                b.startFor().string("int i = this.operationSp - 1; i >= 0; i--").end().startBlock();
                b.startStatement().startCall("b.append").doubleQuote("    [").end().end();

                b.startStatement().startCall("b.append").string("String.format(\"%03d\", i)").end().end();
                b.startStatement().startCall("b.append").doubleQuote("] ").end().end();
                b.startStatement().startCall("b.append").string("this.operationStack[i].toString()").end().end();
                b.startStatement().startCall("b.append").string("System.lineSeparator()").end().end();
                b.end(); // for

                b.startStatement().startCall("b.append").doubleQuote("    instructions=").end().end();
                b.startStatement().startCall("b.append").string("System.lineSeparator()").end().end();

                b.declaration(type(Object[].class), "tempConstants", "constants.create()");
                b.declaration(type(byte[].class), "tempBytecodes", "Arrays.copyOf(this.bc, this.bci)");
                b.startFor().string("int currentBci = 0; currentBci < bci; ").end().startBlock();
                b.declaration(type(int.class), "opcode", readInstruction("this.bc", "currentBci"));
                b.startStatement().startCall("b.append").doubleQuote("       ").end().end();
                b.startStatement().startCall("b.append").startNew(instructionImpl.asType()).string("null").string("currentBci").string("opcode").string("tempBytecodes").string(
                                "tempConstants").end().end().end();
                b.startStatement().startCall("b.append").string("System.lineSeparator()").end().end();
                b.startAssign("currentBci").string("currentBci + ").tree(instructionsElement.call("getLength", "opcode")).end();

                b.end(); // for

                b.startIf().string("parent != null").end().startBlock();
                b.startStatement().startCall("b.append").doubleQuote("  parent=").end().end();
                b.startStatement().startCall("b.append").string("parent.toString()").end().end();

                b.end();

                b.statement("return b.toString()");
                return ex;
            }
        }

        final class OperationFields {

            private final OperationField index = field(type(int.class), "index").asFinal();
            private final OperationField producedValue = field(type(boolean.class), "producedValue").withInitializer("false");
            private final OperationField childBci = field(type(int.class), "childBci").withInitializer(UNINIT);
            private final OperationField shortCircuitBci = field(type(int.class), "shortCircuitBci").withInitializer(UNINIT);
            private final OperationField reachable = field(type(boolean.class), "reachable").withInitializer("true");
            private final OperationField prologBci = field(type(int.class), "prologBci").withInitializer(UNINIT);
            private final OperationField nodeId = field(type(int.class), "nodeId").asFinal();
            private final OperationField startStackHeight = field(type(int.class), "startStackHeight").asFinal();
            private final OperationField operationReachable = field(type(boolean.class), "operationReachable").asFinal();
            private final OperationField node = tagNode != null ? field(tagNode.asType(), "node").asFinal() : null;
            private final OperationField handlerStartBci = field(type(int.class), "handlerStartBci").withInitializer("node.enterBci");
            private final OperationField tagChildren = tagNode != null ? field(generic(type(List.class), tagNode.asType()), "children").withInitializer("null") : null;

            private final OperationField sourceIndex = field(type(int.class), "sourceIndex").asFinal();
            private final OperationField startBci = field(type(int.class), "startBci");
            private final OperationField start = field(type(int.class), "start");
            private final OperationField length = field(type(int.class), "length");
            private final OperationField sourceNodeId = field(type(int.class), "sourceNodeId").withInitializer("-1");

            private final OperationField local = field(bytecodeLocalImpl.asType(), "local");

            private final OperationField thenReachable = field(type(boolean.class), "thenReachable");
            private final OperationField elseReachable = field(type(boolean.class), "elseReachable");
            private final OperationField falseBranchFixupBci = field(type(int.class), "falseBranchFixupBci").withInitializer(UNINIT);
            private final OperationField endBranchFixupBci = field(type(int.class), "endBranchFixupBci").withInitializer(UNINIT);

            private final OperationField whileStartBci = field(type(int.class), "whileStartBci").asFinal();
            private final OperationField bodyReachable = field(type(boolean.class), "bodyReachable");

            private final OperationField handlerId = field(type(int.class), "handlerId").asFinal();
            private final OperationField stackHeight = field(type(int.class), "stackHeight").asFinal();
            private final OperationField tryStartBci = field(type(int.class), "tryStartBci");
            private final OperationField tryReachable = field(type(boolean.class), "tryReachable");
            private final OperationField catchReachable = field(type(boolean.class), "catchReachable");

            private final OperationField extraTableEntriesStart = field(type(int.class), "extraTableEntriesStart").withInitializer(UNINIT);
            private final OperationField extraTableEntriesEnd = field(type(int.class), "extraTableEntriesEnd").withInitializer(UNINIT);

            private final OperationField finallyGenerator = field(context.getDeclaredType(Runnable.class), "finallyGenerator").asFinal();
            private final OperationField finallyHandlerSp = field(type(int.class), "finallyHandlerSp").withInitializer(UNINIT).withDoc(
                            """
                                            The index of the finally handler operation on the operation stack.
                                            This value is uninitialized unless a finally handler is being emitted, and allows us to
                                            walk the operation stack from bottom to top.
                                            """);
            private final OperationField finallyOperationSp = field(type(int.class), "finallyOperationSp").asFinal().withDoc(
                            """
                                            The index of the corresponding finally operation (TryFinally/TryCatchOtherwise) on the
                                            operation stack. This index can be used to skip over the "try" body operations when walking
                                            the operation stack from top to bottom.
                                            """);

            private final OperationField variadicCountPatchIndex = field(type(int.class), "variadicCountPatchIndex").withInitializer(UNINIT);
            private final OperationField numVariadicReturnIndices = field(type(int.class), "numVariadicReturnIndices").withInitializer("0");
            private final OperationField variadicReturnIndices = field(type(int[].class), "variadicReturnIndices") //
                            .skipInitialization(true).dynamicType(false).withStaticInitializer("new int[16]").lengthField(numVariadicReturnIndices);
            private final OperationField numBranchFixupBcis = field(type(int.class), "numBranchFixupBcis").withInitializer("0");
            private final OperationField branchFixupBcis = field(type(int[].class), "branchFixupBcis").//
                            skipInitialization(true).//
                            dynamicType(false).//
                            withStaticInitializer("new int[16]").//
                            lengthField(numBranchFixupBcis);

            private final OperationField frameOffset = field(type(int.class), "frameOffset").withInitializer("0");
            private final OperationField numLocals = field(type(int.class), "numLocals").withInitializer("0");
            private final OperationField locals = field(type(int[].class), "locals").//
                            skipInitialization(true).//
                            dynamicType(false).//
                            withStaticInitializer("new int[16]").//
                            lengthField(numLocals);
            private final OperationField valid = field(type(boolean.class), "valid").withInitializer("true");
            private final OperationField declaredLabels = field(generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "declaredLabels").withInitializer("null");

            private final List<OperationField> childBcis = new ArrayList<>();
            private final Map<ImmediateWidth, List<OperationField>> constants = new HashMap<>();

            OperationFields() {
            }

            /**
             * This is the core method where fields are assigned to operations. This method
             * determines whether fields are needed at all, as all operations that are part of the
             * model are visited. So if an operation is not in the model it won't get mapped and we
             * won't allocate fields form them.
             *
             * See {@link OperationVariables} for how fields get mapped to instance fields in the
             * operation stack element.
             */
            public List<OperationField> mapFieldsToOperation(OperationModel operation) {
                List<OperationField> fields = new ArrayList<>(5);
                switch (operation.kind) {
                    case ROOT:
                        fields.add(index); // init
                        fields.add(producedValue);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        fields.add(reachable);
                        if (model.prolog != null && model.prolog.operation.operationEndArguments.length != 0) {
                            fields.add(prologBci);
                        }

                        // scope
                        if (model.enableBlockScoping) {
                            fields.add(frameOffset);
                            fields.add(locals);
                            fields.add(numLocals);
                            fields.add(valid);
                        }

                        fields.add(declaredLabels);
                        break;
                    case BLOCK:
                        fields.add(startStackHeight); // init
                        fields.add(producedValue);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }

                        // scope
                        if (model.enableBlockScoping) {
                            fields.add(frameOffset);
                            fields.add(locals);
                            fields.add(numLocals);
                            fields.add(valid);
                        }

                        fields.add(declaredLabels);
                        break;
                    case TAG:
                        fields.add(nodeId); // init
                        fields.add(operationReachable); // init
                        fields.add(startStackHeight); // init
                        fields.add(node); // init
                        fields.add(handlerStartBci);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        fields.add(producedValue);
                        fields.add(tagChildren);
                        break;
                    case SOURCE_SECTION:
                        fields.add(sourceIndex); // init
                        fields.add(startBci); // init
                        fields.add(start); // init
                        fields.add(length); // init
                        fields.add(sourceNodeId);
                        fields.add(producedValue);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        break;
                    case SOURCE:
                        fields.add(sourceIndex); // init
                        fields.add(producedValue);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        break;
                    case RETURN:
                        fields.add(producedValue);
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        break;
                    case STORE_LOCAL:
                    case STORE_LOCAL_MATERIALIZED:
                    case LOAD_LOCAL:
                    case LOAD_LOCAL_MATERIALIZED:
                        fields.add(local); // init
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                        }
                        break;
                    case CLEAR_LOCAL:
                        fields.add(local); // init
                        break;
                    case IF_THEN:
                        fields.add(thenReachable); // init
                        fields.add(falseBranchFixupBci);
                        break;
                    case IF_THEN_ELSE:
                        fields.add(thenReachable); // init
                        fields.add(elseReachable); // init
                        fields.add(falseBranchFixupBci);
                        fields.add(endBranchFixupBci);
                        break;
                    case CONDITIONAL:
                        fields.add(thenReachable); // init
                        fields.add(elseReachable); // init
                        fields.add(falseBranchFixupBci);
                        fields.add(endBranchFixupBci);
                        if (model.usesBoxingElimination()) {
                            fields.add(getChildBci(0, true));
                            fields.add(getChildBci(1, true));
                        }
                        break;
                    case WHILE:
                        fields.add(whileStartBci); // init
                        fields.add(bodyReachable); // init
                        fields.add(endBranchFixupBci);
                        break;
                    case TRY_CATCH:
                        fields.add(handlerId); // init
                        fields.add(stackHeight); // init
                        fields.add(tryStartBci); // init
                        fields.add(operationReachable); // init
                        fields.add(tryReachable); // init
                        fields.add(catchReachable); // init
                        fields.add(endBranchFixupBci);
                        fields.add(extraTableEntriesStart);
                        fields.add(extraTableEntriesEnd);
                        break;
                    case TRY_FINALLY, TRY_CATCH_OTHERWISE:
                        fields.add(handlerId); // init
                        fields.add(stackHeight); // init
                        fields.add(finallyGenerator); // init
                        fields.add(tryStartBci); // init
                        fields.add(operationReachable); // init
                        fields.add(tryReachable); // init
                        fields.add(catchReachable); // init
                        fields.add(endBranchFixupBci);
                        fields.add(extraTableEntriesStart);
                        fields.add(extraTableEntriesEnd);
                        fields.add(finallyHandlerSp);
                        break;
                    case FINALLY_HANDLER:
                        fields.add(finallyOperationSp); // init
                        break;
                    case CUSTOM, CUSTOM_YIELD, CUSTOM_INSTRUMENTATION:
                        if (operation.isTransparent()) {
                            fields.add(producedValue);
                            if (model.usesBoxingElimination()) {
                                fields.add(childBci);
                            }
                        } else {
                            fields.addAll(getConstants(operation.constantOperands.before(), true));
                            int bciFields = operation.numDynamicOperands();
                            if (model.usesBoxingElimination()) {
                                for (int i = 0; i < bciFields; i++) {
                                    fields.add(getChildBci(i, true));
                                }
                            }
                            if (operation.isVariadic) {
                                fields.add(variadicCountPatchIndex);
                                if (model.hasVariadicReturn) {
                                    fields.add(variadicReturnIndices);
                                    fields.add(numVariadicReturnIndices);
                                }
                            }
                        }
                        break;
                    case CUSTOM_SHORT_CIRCUIT:
                        if (model.usesBoxingElimination()) {
                            fields.add(childBci);
                            fields.add(shortCircuitBci);
                        }
                        fields.add(branchFixupBcis);
                        fields.add(numBranchFixupBcis);
                        break;
                    default:
                        if (operation.isTransparent()) {
                            fields.add(producedValue);
                            if (model.usesBoxingElimination()) {
                                fields.add(childBci);
                            }
                        }
                        break;
                }
                return fields;
            }

            OperationField getChildBci(int childIndex, boolean create) {
                // ensure child bcis created
                if (create) {
                    for (int i = childBcis.size(); i < childIndex + 1; i++) {
                        childBcis.add(field(type(int.class), "child" + i + "Bci").withInitializer(UNINIT));
                    }
                }
                return childBcis.get(childIndex);
            }

            List<OperationField> getConstants(List<ConstantOperandModel> operands, boolean create) {
                List<OperationField> result = new ArrayList<>(operands.size());
                // Allocate separate fields for each immediate width.
                Map<ImmediateWidth, Integer> constantCountsByWidth = new HashMap<>();
                for (ConstantOperandModel constantOperand : operands) {
                    ImmediateWidth requestedWidth = constantOperand.kind().width;
                    int fieldIndex = constantCountsByWidth.compute(requestedWidth, (k, v) -> (v == null) ? 0 : v + 1);
                    if (create) {
                        List<OperationField> fields = constants.computeIfAbsent(requestedWidth, e -> new ArrayList<>());
                        for (int i = fields.size(); i < fieldIndex + 1; i++) {
                            fields.add(field(requestedWidth.toType(context), "constant" + requestedWidth.toEncodedName() + i));
                        }
                    }
                    result.add(constants.get(requestedWidth).get(fieldIndex));
                }

                return result;
            }

            private OperationField field(TypeMirror type, String name) {
                return new OperationField(type, name);
            }
        }

        final class OperationStackElement extends CodeTypeElement {

            private OperationVariables variables;
            private final Map<OperationField, GetterAndSetter> gettersSetters = new LinkedHashMap<>();

            OperationStackElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "OperationStackElement");

            }

            private void lazyInit(OperationFields fields) {
                this.variables = new OperationVariables((m) -> fields.mapFieldsToOperation(m));

                CodeExecutableElement constructor = createConstructorUsingFields(Set.of(), this, null);
                CodeTreeBuilder b = constructor.appendBuilder();
                Map<CodeVariableElement, String> staticInitializer = new LinkedHashMap<>();
                for (OperationField field : variables.fieldToVariable.keySet()) {
                    if (field.staticInitializer != null) {
                        CodeVariableElement var = variables.fieldToVariable.get(field);
                        String prev = staticInitializer.put(var, field.staticInitializer);
                        if (prev != null && !prev.equals(field.staticInitializer)) {
                            /*
                             * This is not a hard limitation, but we want all buffers statically
                             * allocated the same way otherwise we can't allocate them to the same
                             * field.
                             */
                            throw new AssertionError("Buffer variables must have the same initializer. Expected " + field.initializer + " but got " + prev);
                        }
                    }
                }
                for (var entry : staticInitializer.entrySet()) {
                    CodeVariableElement variable = entry.getKey();
                    String initializer = entry.getValue();
                    b.startStatement();
                    b.string("this.", variable.getSimpleName().toString());
                    b.string(" = ");
                    b.string(initializer);
                    b.end();
                }

                this.add(constructor);

                this.add(new CodeVariableElement(Set.of(), type(int.class), "operation"));
                this.add(new CodeVariableElement(Set.of(), type(int.class), "sequenceNumber"));
                this.add(new CodeVariableElement(Set.of(), type(int.class), "childCount"));
                this.addAll(this.variables.variables());

                // we collect all operations for each variable per field
                // we need it later to assert the operations when we create the getters and setters
                for (var entry : variables.fieldToOperation.entrySet()) {
                    OperationField field = entry.getKey();
                    Collection<OperationModel> operations = entry.getValue();
                    CodeVariableElement variable = variables.fieldToVariable.get(field);
                    GetterAndSetter methods = new GetterAndSetter(
                                    createGetField(operations, field, variable),
                                    createSetField(operations, field, variable));
                    this.add(methods.getter);
                    this.add(methods.setter);
                    gettersSetters.put(field, methods);
                }

                if (model.additionalAssertions) {
                    this.add(createIsOperation());
                }

                this.add(createValidateDeclaredLabels());
                this.add(createAddDeclaredLabel());

                if (model.enableBlockScoping) {
                    this.add(createRegisterLocal());
                }
                this.add(createToString());
            }

            CodeTree createInitialize(OperationModel operation, String local, Map<OperationField, String> originalValues) {
                CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
                boolean first = true;
                Map<OperationField, String> values = new HashMap<>(originalValues);
                for (OperationField field : variables.operationToField.get(operation)) {
                    if (field.skipInitialization) {
                        continue;
                    }
                    String value;
                    if (field.initializer == null) {
                        // needs init value
                        value = values.remove(field);
                        if (value == null) {
                            throw new AssertionError("Operation init value not specified: " + field.name + " operation " + operation);
                        }
                    } else {
                        value = field.initializer;
                    }
                    if (first) {
                        b.lineComment("Initialize operation " + operation.name);
                        first = false;
                    }
                    b.tree(write(operation, local, field, CodeTreeBuilder.singleString(value), true));
                }

                if (!values.isEmpty()) {
                    throw new AssertionError("Too many operation init fields specified: " + values + " operation " + operation);
                }

                return b.build();
            }

            String read(OperationModel operation, String local, OperationField field) {
                return read(List.of(operation), local, field);
            }

            String read(OperationModel operation, OperationField field) {
                return read(operation, "operation", field);
            }

            String read(Collection<OperationModel> operation, OperationField field) {
                return read(operation, "operation", field);
            }

            String read(Collection<OperationModel> operations, String local, OperationField field) {
                validateField(operations, field);
                ExecutableElement getter = this.gettersSetters.get(field).getter();
                return local + "." + getter.getSimpleName() + "()";

            }

            CodeTree write(OperationModel operation, OperationField field, String value) {
                return write(operation, "operation", field, CodeTreeBuilder.singleString(value), false);
            }

            CodeTree write(Collection<OperationModel> operation, OperationField field, String value) {
                return write(operation, "operation", field, CodeTreeBuilder.singleString(value), false);
            }

            CodeTree write(OperationModel operation, String local, OperationField field, String value) {
                return write(operation, local, field, CodeTreeBuilder.singleString(value), false);
            }

            CodeTree write(OperationModel operation, String local, OperationField field, CodeTree value, boolean init) {
                return write(List.of(operation), local, field, value, init);
            }

            CodeTree write(Collection<OperationModel> operations, String local, OperationField field, CodeTree value, boolean init) {
                validateField(operations, field);
                if (!init && field.isFinal) {
                    throw new AssertionError("Field " + field + " is final and cannot be written after init.");
                }

                ExecutableElement setter = this.gettersSetters.get(field).setter();
                CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
                b.startStatement();
                b.startCall(local, setter.getSimpleName().toString());
                b.tree(value);
                b.end();
                b.end();
                return b.build();
            }

            private void validateField(Collection<OperationModel> operations, OperationField field) throws AssertionError {
                for (OperationModel operation : operations) {
                    if (!variables.operationToField.get(operation).contains(field)) {
                        throw new AssertionError("Invalid field " + field + " used for operation " + operation.name + ". Available fields: " + variables.operationToField.get(operation));
                    }
                }
            }

            private CodeExecutableElement createIsOperation() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "isOperation");
                ex.addParameter(new CodeVariableElement(type(int[].class), "ops"));
                ex.setVarArgs(true);
                CodeTreeBuilder b = ex.createBuilder();
                b.declaration("int", "currentOp", "this.operation");
                b.startFor().string("int op : ops").end().startBlock();
                b.startIf().string("currentOp == op").end().startBlock();
                b.returnTrue();
                b.end();
                b.end();
                b.returnFalse();
                return ex;
            }

            private CodeExecutableElement createSetField(Collection<OperationModel> operations, OperationField f, CodeVariableElement var) {
                String name = "set" + ElementUtils.firstLetterUpperCase(f.name);
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(), type(void.class), name);
                ex.addParameter(new CodeVariableElement(f.type, "value"));
                if (f.doc != null) {
                    addJavadoc(ex, f.doc);
                }
                CodeTreeBuilder b = ex.createBuilder();

                if (model.additionalAssertions) {
                    b.startAssert().startCall("isOperation");
                    for (OperationModel operation : operations) {
                        b.tree(createOperationConstant(operation));
                    }
                    b.end().end();
                }

                b.statement("this.", var.getName(), " = value");
                return ex;
            }

            private CodeExecutableElement createGetField(Collection<OperationModel> operations, OperationField f, CodeVariableElement var) {
                String name = "get" + ElementUtils.firstLetterUpperCase(f.name);
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(), f.type, name);
                if (f.doc != null) {
                    addJavadoc(ex, f.doc);
                }
                CodeTreeBuilder b = ex.createBuilder();

                if (model.additionalAssertions) {
                    b.startAssert().startCall("isOperation");
                    for (OperationModel operation : operations) {
                        b.tree(createOperationConstant(operation));
                    }
                    b.end().end();
                }

                b.startReturn();
                if (f.dynamicType) {
                    TypeElement element = ElementUtils.castTypeElement(f.type);
                    if (element != null && !element.getTypeParameters().isEmpty()) {
                        GeneratorUtils.mergeSuppressWarnings(ex, "unchecked");
                    }
                    b.cast(f.type);
                }
                b.string(var.getName());
                b.end();
                return ex;
            }

            private CodeExecutableElement createValidateDeclaredLabels() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(boolean.class), "validateDeclaredLabels");

                CodeTreeBuilder b = ex.createBuilder();
                b.declaration("var", "labels", "getDeclaredLabels()");
                b.startIf().string("labels != null").end().startBlock();
                b.startFor().string("BytecodeLabel label : labels").end().startBlock();
                b.statement("BytecodeLabelImpl impl = (BytecodeLabelImpl) label");
                b.startIf().string("!impl.isDefined()").end().startBlock();
                b.statement("return false");
                b.end(3);
                b.statement("return true");

                return ex;
            }

            private CodeExecutableElement createRegisterLocal() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(), type(void.class), "registerLocal");
                ex.addParameter(new CodeVariableElement(type(int.class), "tableIndex"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration(type(int.class), "localTableIndex", "getNumLocals()");
                b.statement("setNumLocals(localTableIndex + 1)");

                b.declaration(type(int[].class), "localsTable", "getLocals()");

                b.startIf().string("localsTable == null").end().startBlock();
                b.startAssign("localsTable").startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("8")).end().end();
                b.statement("setLocals(localsTable)");
                b.end();
                b.startElseIf().string("localTableIndex >= localsTable.length").end().startBlock();
                b.startAssign("localsTable").startStaticCall(type(Arrays.class), "copyOf");
                b.string("localsTable");
                b.string("localsTable.length * 2");
                b.end(2); // assign, static call
                b.statement("setLocals(localsTable)");
                b.end(); // if block
                b.statement("localsTable[localTableIndex] = tableIndex");

                return ex;
            }

            private CodeExecutableElement createAddDeclaredLabel() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(FINAL), type(void.class), "addDeclaredLabel");
                ex.addParameter(new CodeVariableElement(types.BytecodeLabel, "label"));

                CodeTreeBuilder b = ex.createBuilder();

                b.declaration("var", "labels", "getDeclaredLabels()");

                b.startIf().string("labels == null").end().startBlock();
                b.statement("labels = new ArrayList<>(8)");
                b.statement("setDeclaredLabels(labels)");
                b.end();

                b.statement("labels.add(label)");

                return ex;
            }

            private CodeExecutableElement createToString() {
                CodeExecutableElement ex = GeneratorUtils.override(declaredType(Object.class), "toString");

                CodeTreeBuilder p = ex.createBuilder();

                Map<EqualityCodeTree, List<OperationModel>> caseGrouping = EqualityCodeTree.group(p, model.getOperationsWithChildren(), (OperationModel op, CodeTreeBuilder b) -> {
                    var iterator = this.variables.operationToField.get(op).stream().filter((f) -> !f.isLengthField).toList().iterator();
                    while (iterator.hasNext()) {
                        OperationField field = iterator.next();

                        b.startStatement();
                        b.startCall("b.append").doubleQuote(field.name + "(").end();
                        b.startCall(".append");
                        if (field.type.getKind() == TypeKind.ARRAY) {
                            b.startStaticCall(type(Arrays.class), "toString");

                            if (field.lengthField != null) {
                                b.startStaticCall(type(Arrays.class), "copyOf");
                                b.string(operationStack.read(op, "this", field));
                                b.string(operationStack.read(op, "this", field.lengthField));
                                b.end();
                            } else {
                                b.string(operationStack.read(op, "this", field));
                            }

                            b.end();
                        } else {
                            b.string(operationStack.read(op, "this", field));
                        }

                        b.end(); // append call
                        b.startCall(".append").doubleQuote(")").end();
                        if (iterator.hasNext()) {
                            b.startCall(".append").doubleQuote(" ").end();
                        }
                        b.end();
                    }
                    b.statement("break");
                });

                CodeTreeBuilder b = p;

                b.startDeclaration(type(StringBuilder.class), "b").startNew(type(StringBuilder.class)).end().end();

                b.startStatement().startCall("b.append");
                b.startStaticCall(type(String.class), "format");
                b.doubleQuote("%-15s");
                b.startStaticCall(operationsElement.asType(), "getName").string("operation").end();
                b.end();
                b.end().end();

                b.startStatement().startCall("b.append").doubleQuote(" ").end().end();

                b.startSwitch().string("operation").end().startBlock();
                for (var group : caseGrouping.entrySet()) {
                    EqualityCodeTree key = group.getKey();
                    for (OperationModel op : group.getValue()) {
                        b.startCase().tree(createOperationConstant(op)).end();
                    }
                    b.startCaseBlock();
                    b.tree(key.getTree());
                    b.end();
                }
                b.end(); // switch

                b.statement("return b.toString()");
                return ex;
            }

            record GetterAndSetter(ExecutableElement getter, ExecutableElement setter) {
            }

        }

        /**
         * Class that manages allocation of operation fields to variables / instance fields in the
         * stack element class. The specification of concrete fields can be found in
         * OperationFields.
         */
        final class OperationVariables {

            final Map<OperationModel, LinkedHashSet<OperationField>> operationToField = new LinkedHashMap<>();
            final Map<TypeMirror, List<CodeVariableElement>> typeToVariables = new LinkedHashMap<>();
            final Map<OperationField, CodeVariableElement> fieldToVariable = new LinkedHashMap<>();
            final Map<OperationField, LinkedHashSet<OperationModel>> fieldToOperation = new LinkedHashMap<>();

            OperationVariables(Function<OperationModel, List<OperationField>> mapping) {
                for (OperationModel operation : model.getOperations()) {
                    List<OperationField> fields = mapping.apply(operation);
                    this.operationToField.put(operation, new LinkedHashSet<>(fields));
                    fields.addAll(fields);
                    for (OperationField field : fields) {
                        fieldToOperation.computeIfAbsent(field, (f) -> new LinkedHashSet<>()).add(operation);
                    }
                }

                Map<String, Set<OperationField>> variableToFields = new LinkedHashMap<>();

                /*
                 * This mapping represents the allocation of operation fields to
                 * OperationStackElement fields ("variables"). Each operation has allocation lists
                 * for each variable type. We represent allocation with a boolean list where a
                 * "true" value at index i means variable i has been allocated.
                 */
                final Map<OperationModel, Map<TypeMirror, List<Boolean>>> operationToFieldAllocation = new LinkedHashMap<>();
                /*
                 * In this allocation strategy we allocate fields with most usages first. This
                 * ensures that the most contested fields are allocated early.
                 */
                for (var entry : fieldToOperation.entrySet().stream().sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size())).toList()) {
                    OperationField field = entry.getKey();
                    Collection<OperationModel> operations = entry.getValue();

                    TypeMirror fieldType;
                    if (field.dynamicType) {
                        fieldType = ProcessorContext.getInstance().getType(Object.class);
                    } else {
                        fieldType = field.type;
                    }

                    // first try to allocate the field for all operations until an index is found
                    // where it's the same for all operations
                    int fieldIndex = 0;
                    restart: while (true) {
                        for (OperationModel operation : operations) {
                            List<Boolean> availableList = operationToFieldAllocation.//
                                            computeIfAbsent(operation, (k) -> new LinkedHashMap<>()).//
                                            computeIfAbsent(fieldType, (k) -> new ArrayList<>());

                            if (fieldIndex >= availableList.size()) {
                                // fits
                                continue;
                            }
                            if (!availableList.get(fieldIndex)) {
                                // fits
                                continue;
                            }

                            // we failed to allocate for this operation
                            // now we find the next best possible index and restart
                            // the entire allocation for this field until
                            // we find an index that fits all operations.
                            for (int i = fieldIndex; i < availableList.size(); i++) {
                                if (!availableList.get(i)) {
                                    // retry with unallocated index
                                    fieldIndex = i;
                                    continue restart;
                                }
                            }
                            // all indices have been allocated. increase the number of variables and
                            // retry
                            fieldIndex = Math.max(fieldIndex, availableList.size());
                            continue restart;
                        }
                        break;
                    }

                    // now we mark the field being used
                    for (OperationModel operation : operations) {
                        List<Boolean> availableList = operationToFieldAllocation.//
                                        computeIfAbsent(operation, (k) -> new LinkedHashMap<>()).//
                                        computeIfAbsent(fieldType, (k) -> new ArrayList<>());
                        while (fieldIndex >= availableList.size()) {
                            availableList.add(Boolean.FALSE);
                        }
                        // lets make sure we actually did properly allocate the index
                        if (availableList.get(fieldIndex)) {
                            throw new AssertionError("Field allocation invariant failed");
                        }
                        availableList.set(fieldIndex, Boolean.TRUE);
                    }

                    List<CodeVariableElement> variables = typeToVariables.computeIfAbsent(fieldType, (k) -> new ArrayList<>());
                    CodeVariableElement var;
                    if (fieldIndex < variables.size()) {
                        var = variables.get(fieldIndex);
                    } else if (fieldIndex == variables.size()) {
                        String prefix = ElementUtils.firstLetterLowerCase(ElementUtils.getTypeSimpleId(fieldType));
                        var = new CodeVariableElement(Set.of(PRIVATE), fieldType, prefix + fieldIndex);
                        variables.add(var);
                    } else {
                        throw new AssertionError("fieldIndex %d exceeds variable size %d".formatted(fieldIndex, variables.size()));
                    }

                    this.fieldToVariable.put(field, var);
                    variableToFields.computeIfAbsent(var.getName(), (k) -> new LinkedHashSet<>()).add(field);
                }

                // generate some useful javadoc comments with the field allocation
                for (var entry : this.typeToVariables.values()) {
                    for (CodeVariableElement variable : entry) {
                        CodeTreeBuilder doc = variable.createDocBuilder();

                        doc.startJavadoc();
                        doc.string("Field mappings:");
                        for (OperationField field : variableToFields.get(variable.getName())) {

                            doc.newLine().string("    ").string(field.name).string(" : ").type(field.type);
                            if (field.doc != null) {
                                for (String line : field.doc.split("\n")) {
                                    doc.newLine();
                                    doc.string("      ");
                                    if (line.isBlank()) {
                                        doc.string(" ");
                                    } else {
                                        doc.string(line);
                                    }
                                }
                            }

                            int count = 0;
                            for (OperationModel operation : fieldToOperation.get(field)) {
                                if (count % 8 == 0) {
                                    doc.newLine().string("        ");
                                }
                                doc.string(operation.getConstantName()).string(", ");
                                count++;
                            }
                        }

                        doc.newLine();
                        doc.end();
                    }
                }
            }

            public List<CodeVariableElement> variables() {
                List<CodeVariableElement> variables = new ArrayList<>();
                /*
                 * We sort variables by size. It is recommended to have bigger types first in a Java
                 * class and smaller primitives last.
                 *
                 * Reference types are always sorted last, so they end up at the end of the class.
                 */
                for (TypeMirror type : typeToVariables.keySet().stream().sorted((e1, e2) -> {
                    return Integer.compare(getFieldSize(e2), getFieldSize(e1));
                }).toList()) {
                    variables.addAll(typeToVariables.get(type));
                }
                return variables;
            }

            private int getFieldSize(TypeMirror e1) {
                switch (e1.getKind()) {
                    case BOOLEAN:
                    case BYTE:
                        return 1;
                    case SHORT:
                    case CHAR:
                        return 2;
                    case INT:
                    case FLOAT:
                        return 4;
                    case LONG:
                    case DOUBLE:
                        return 8;
                    default:
                        // this makes reference fields always
                        // be last, this is good for an
                        // efficient field layout
                        return -1;
                }
            }

        }

        static final class OperationField {
            final TypeMirror type;
            final String name;
            boolean isFinal;
            // If initializer is null, the field value is required as a constructor parameter
            String initializer;
            // Invoked when the operation stack element is instantiated
            String staticInitializer;
            String doc;

            boolean skipInitialization;
            boolean dynamicType;

            OperationField lengthField;
            boolean isLengthField;

            OperationField(TypeMirror type, String name) {
                this.type = type;
                this.name = name;
                this.dynamicType = !ElementUtils.isPrimitive(type);
            }

            public OperationField lengthField(OperationField field) {
                this.lengthField = field;
                field.isLengthField = true;
                return this;
            }

            /**
             * Field cannot be written after initialization.
             */
            OperationField asFinal() {
                this.isFinal = true;
                return this;
            }

            /**
             * Initializer that is invoked when the operation is initialized with
             * OperationStackElement#createInitialize.
             */
            OperationField withInitializer(String newInitializer) {
                this.initializer = newInitializer;
                return this;
            }

            /**
             * Static initializer that is invoked on initialization of the operation data class
             * object.
             */
            OperationField withStaticInitializer(String newInitializer) {
                this.staticInitializer = newInitializer;
                return this;
            }

            OperationField withDoc(String newDoc) {
                this.doc = newDoc;
                return this;
            }

            /**
             * If set to true skips initialization of this field. This is useful for buffers that
             * should not be reset every time an operation is allocated.
             */
            OperationField skipInitialization(boolean value) {
                this.skipInitialization = value;
                return this;
            }

            /**
             * Dynamically typed fields use Object to represent their value. This makes the field
             * better reusable. Setting dynamic type to <code>false</code> makes the type of the
             * field used in the variable directly.
             */
            OperationField dynamicType(boolean value) {
                this.dynamicType = value;
                return this;
            }

            @Override
            public String toString() {
                return "DataClassField[" + name + "]";
            }
        }

        final class BytecodeLocalImplElement extends CodeTypeElement {

            BytecodeLocalImplElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLocalImpl");
            }

            void lazyInit() {
                this.setSuperClass(types.BytecodeLocal);

                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(short.class), "frameIndex"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(short.class), "localIndex"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(short.class), "rootIndex"));

                if (model.enableBlockScoping) {
                    this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), operationStack.asType(), "scope"));
                    this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "scopeSequenceNumber"));
                }

                CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);

                this.add(createGetLocalOffset());
                this.add(createGetLocalIndex());
                this.add(createToString());
            }

            private CodeExecutableElement createGetLocalOffset() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeLocal, "getLocalOffset");
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("frameIndex - USER_LOCALS_START_INDEX").end();
                return ex;
            }

            private CodeExecutableElement createGetLocalIndex() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeLocal, "getLocalIndex");
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("localIndex").end();
                return ex;
            }

            private CodeExecutableElement createToString() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(String.class), "toString");
                CodeTreeBuilder b = ex.createBuilder();

                b.startReturn().doubleQuote("BytecodeLocal[localOffset=");
                b.string(" + this.getLocalOffset() + ");
                b.doubleQuote(", localIndex=");
                b.string(" + this.getLocalIndex() + ");
                b.doubleQuote(", rootIndex=");
                b.string(" + rootIndex + ");
                b.doubleQuote("]");
                b.end();
                return ex;
            }
        }

        final class BytecodeLabelImplElement extends CodeTypeElement {

            BytecodeLabelImplElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeLabelImpl");
            }

            void lazyInit() {
                this.setSuperClass(types.BytecodeLabel);

                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "id"));
                this.add(new CodeVariableElement(type(int.class), "bci"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "declaringOp"));

                CodeExecutableElement constructor = createConstructorUsingFields(Set.of(), this, null);
                this.add(new CodeVariableElement(type(int.class), "unresolved0")).createInitBuilder().string("-1");
                this.add(new CodeVariableElement(type(int.class), "unresolved1")).createInitBuilder().string("-1");
                this.add(new CodeVariableElement(type(int.class), "unresolvedCount"));
                this.add(new CodeVariableElement(type(int[].class), "unresolvedArray"));

                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);
                this.add(constructor);

                this.add(createIsDefined());
                this.add(createEquals());
                this.add(createHashCode());
                this.add(createToString());
            }

            private CodeExecutableElement createIsDefined() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(boolean.class), "isDefined");
                CodeTreeBuilder b = ex.createBuilder();
                b.startReturn().string("bci != -1").end();
                return ex;
            }

            private CodeExecutableElement createEquals() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(boolean.class), "equals");
                ex.addParameter(new CodeVariableElement(type(Object.class), "other"));

                CodeTreeBuilder b = ex.createBuilder();
                b.startIf().string("!(other instanceof BytecodeLabelImpl)").end().startBlock();
                b.returnFalse();
                b.end();

                b.startReturn().string("this.id == ((BytecodeLabelImpl) other).id").end();
                return ex;
            }

            private CodeExecutableElement createHashCode() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(int.class), "hashCode");
                CodeTreeBuilder b = ex.createBuilder();

                b.startReturn().string("this.id").end();
                return ex;
            }

            private CodeExecutableElement createToString() {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(PUBLIC), type(String.class), "toString");
                CodeTreeBuilder b = ex.createBuilder();

                b.startReturn().doubleQuote("BytecodeLabel[id=");
                b.string(" + this.id + ");
                b.string("(!isDefined() ? ");
                b.doubleQuote(", undefined");
                b.string(" : ");
                b.doubleQuote(", bci=");
                b.string(" + this.bci");
                b.string(") + ");
                b.doubleQuote("]");
                b.end();
                return ex;
            }
        }

        final class SerializationRootNodeElement extends CodeTypeElement {

            SerializationRootNodeElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SerializationRootNode");
                this.setSuperClass(model.templateType.asType());

                List<CodeVariableElement> fields = List.of(
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "contextDepth"),
                                new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "rootIndex"));
                this.addAll(fields);
                this.add(createConstructor(this, fields));
            }

            private CodeExecutableElement createConstructor(CodeTypeElement serializationRoot, List<CodeVariableElement> fields) {
                CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, serializationRoot.getSimpleName().toString());
                ctor.addParameter(new CodeVariableElement(types.FrameDescriptor_Builder, "builder"));
                for (CodeVariableElement field : fields) {
                    ctor.addParameter(new CodeVariableElement(field.asType(), field.getName().toString()));
                }
                CodeTreeBuilder b = ctor.getBuilder();

                // super call
                b.startStatement().startCall("super");
                b.string("null"); // language not needed for serialization
                if (model.fdBuilderConstructor != null) {
                    b.string("builder");
                } else {
                    b.string("builder.build()");
                }
                b.end(2);

                for (CodeVariableElement field : fields) {
                    b.startAssign("this", field).variable(field).end();
                }

                return ctor;
            }
        }

        final class SerializationLocalElement extends CodeTypeElement {

            SerializationLocalElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SerializationLocal");
                this.setSuperClass(types.BytecodeLocal);
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "contextDepth"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "localIndex"));

                CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);

                this.add(createGetLocalOffset());
                this.add(createGetLocalIndex());
            }

            private CodeExecutableElement createGetLocalOffset() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeLocal, "getLocalOffset");
                CodeTreeBuilder b = ex.createBuilder();
                emitThrowIllegalStateException(ex, b, null);
                return ex;
            }

            private CodeExecutableElement createGetLocalIndex() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeLocal, "getLocalIndex");
                CodeTreeBuilder b = ex.createBuilder();
                emitThrowIllegalStateException(ex, b, null);
                return ex;
            }
        }

        final class SerializationLabelElement extends CodeTypeElement {
            SerializationLabelElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SerializationLabel");
                this.setSuperClass(types.BytecodeLabel);
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "contextDepth"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "labelIndex"));

                CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
                CodeTree tree = constructor.getBodyTree();
                CodeTreeBuilder b = constructor.createBuilder();
                b.startStatement().startSuperCall().staticReference(bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
                b.tree(tree);
            }
        }

        final class SerializationStateElement extends CodeTypeElement implements ElementHelpers {

            private final CodeVariableElement codeCreateLabel = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LABEL", "-2");
            private final CodeVariableElement codeCreateLocal = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_LOCAL", "-3");
            private final CodeVariableElement codeCreateObject = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_OBJECT", "-4");
            private final CodeVariableElement codeCreateNull = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_NULL", "-5");
            private final CodeVariableElement codeCreateFinallyGenerator = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$CREATE_FINALLY_GENERATOR", "-6");
            private final CodeVariableElement codeEndFinallyGenerator = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END_FINALLY_GENERATOR", "-7");
            private final CodeVariableElement codeEndSerialize = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class, "CODE_$END", "-8");

            private final CodeVariableElement buffer = addField(this, Set.of(PRIVATE, FINAL), DataOutput.class, "buffer");
            private final CodeVariableElement callback = addField(this, Set.of(PRIVATE, FINAL), types.BytecodeSerializer, "callback");
            private final CodeVariableElement outer = addField(this, Set.of(PRIVATE, FINAL), this.asType(), "outer");
            private final CodeVariableElement depth = addField(this, Set.of(PRIVATE, FINAL), type(int.class), "depth");
            private final CodeVariableElement objects = addField(this, Set.of(PRIVATE, FINAL),
                            generic(HashMap.class, Object.class, Integer.class), "objects");
            private final CodeVariableElement builtNodes = addField(this, Set.of(PRIVATE, FINAL), generic(ArrayList.class, model.getTemplateType().asType()), "builtNodes");
            private final CodeVariableElement rootStack = addField(this, Set.of(PRIVATE, FINAL), generic(ArrayDeque.class, serializationRootNode.asType()), "rootStack");
            private final CodeVariableElement labelCount = addField(this, Set.of(PRIVATE), int.class, "labelCount");

            private final CodeVariableElement[] codeBegin;
            private final CodeVariableElement[] codeEnd;

            SerializationStateElement() {
                super(Set.of(PRIVATE, STATIC), ElementKind.CLASS, null, "SerializationState");
                this.getImplements().add(types.BytecodeSerializer_SerializerContext);

                objects.createInitBuilder().startNew("HashMap<>").end();
                builtNodes.createInitBuilder().startNew("ArrayList<>").end();
                rootStack.createInitBuilder().startNew("ArrayDeque<>").end();

                addField(this, Set.of(PRIVATE), int.class, "localCount");
                addField(this, Set.of(PRIVATE), short.class, "rootCount");
                addField(this, Set.of(PRIVATE), int.class, "finallyGeneratorCount");

                codeBegin = new CodeVariableElement[model.getOperations().size() + 1];
                codeEnd = new CodeVariableElement[model.getOperations().size() + 1];

                // Only allocate serialization codes for non-internal operations.
                for (OperationModel o : model.getUserOperations()) {
                    if (o.hasChildren()) {
                        codeBegin[o.id] = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                        "CODE_BEGIN_" + ElementUtils.createConstantName(o.name), String.valueOf(o.id) + " << 1");
                        codeEnd[o.id] = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                        "CODE_END_" + ElementUtils.createConstantName(o.name), "(" + String.valueOf(o.id) + " << 1) | 0b1");
                    } else {
                        codeBegin[o.id] = addField(this, Set.of(PRIVATE, STATIC, FINAL), short.class,
                                        "CODE_EMIT_" + ElementUtils.createConstantName(o.name), String.valueOf(o.id) + " << 1");
                    }
                }

                this.add(createConstructor());
                this.add(createPushConstructor());

                this.add(createSerializeObject());
                this.add(createWriteBytecodeNode());
            }

            private CodeExecutableElement createConstructor() {
                CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
                method.addParameter(new CodeVariableElement(buffer.getType(), buffer.getName()));
                method.addParameter(new CodeVariableElement(callback.getType(), callback.getName()));

                CodeTreeBuilder b = method.createBuilder();

                b.startAssign("this", buffer).variable(buffer).end();
                b.startAssign("this", callback).variable(callback).end();
                b.startAssign("this", outer).string("null").end();
                b.startAssign("this", depth).string("0").end();

                return method;
            }

            private CodeExecutableElement createPushConstructor() {
                CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
                method.addParameter(new CodeVariableElement(buffer.getType(), buffer.getName()));
                method.addParameter(new CodeVariableElement(this.asType(), outer.getName()));

                CodeTreeBuilder b = method.createBuilder();

                b.startAssign("this", buffer).variable(buffer).end();
                b.startAssign("this", callback).field(outer.getName(), callback).end();
                b.startAssign("this", outer).variable(outer).end();
                b.startAssign("this", depth).startCall("safeCastShort").startGroup().field(outer.getName(), depth).string(" + 1").end(3);

                return method;
            }

            private CodeExecutableElement createWriteBytecodeNode() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeSerializer_SerializerContext, "writeBytecodeNode", new String[]{"buffer", "node"});
                mergeSuppressWarnings(ex, "hiding");
                CodeTreeBuilder b = ex.createBuilder();
                b.startDeclaration(serializationRootNode.asType(), "serializationRoot");
                b.cast(serializationRootNode.asType()).string("node");
                b.end();
                b.statement("buffer.writeInt(serializationRoot.contextDepth)");
                b.statement("buffer.writeInt(serializationRoot.rootIndex)");

                return ex;
            }

            private CodeExecutableElement createSerializeObject() {
                CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(int.class), "serializeObject");
                method.addParameter(new CodeVariableElement(type(Object.class), "object"));
                method.addThrownType(type(IOException.class));
                CodeTreeBuilder b = method.createBuilder();

                String argumentName = "object";
                String index = "index";

                b.startDeclaration(declaredType(Integer.class), index).startCall("objects.get").string(argumentName).end(2);
                b.startIf().string(index + " == null").end().startBlock();
                b.startAssign(index).string("objects.size()").end();
                b.startStatement().startCall("objects.put").string(argumentName).string(index).end(2);

                b.startIf().string("object == null").end().startBlock();
                b.startStatement();
                b.string(buffer.getName(), ".").startCall("writeShort").string(codeCreateNull.getName()).end();
                b.end();
                b.end().startElseBlock();

                b.startStatement();
                b.string(buffer.getName(), ".").startCall("writeShort").string(codeCreateObject.getName()).end();
                b.end();
                b.statement("callback.serialize(this, buffer, object)");
                b.end();

                b.end();

                b.statement("return ", index);
                return method;
            }

            void writeByte(CodeTreeBuilder b, String value) {
                writeByte(b, CodeTreeBuilder.singleString(value));
            }

            void writeByte(CodeTreeBuilder b, CodeTree value) {
                b.startStatement();
                b.string("serialization.", buffer.getName(), ".").startCall("writeByte");
                b.tree(value).end();
                b.end();
            }

            void writeShort(CodeTreeBuilder b, CodeVariableElement label) {
                writeShort(b, b.create().staticReference(label).build());
            }

            void writeShort(CodeTreeBuilder b, String value) {
                writeShort(b, CodeTreeBuilder.singleString(value));
            }

            void writeShort(CodeTreeBuilder b, CodeTree value) {
                b.startStatement();
                b.string("serialization.", buffer.getName(), ".").startCall("writeShort");
                b.tree(value).end();
                b.end();
            }

            void writeInt(CodeTreeBuilder b, String value) {
                writeInt(b, CodeTreeBuilder.singleString(value));
            }

            void writeInt(CodeTreeBuilder b, CodeTree value) {
                b.startStatement();
                b.string("serialization.", buffer.getName(), ".").startCall("writeInt");
                b.tree(value).end();
                b.end();
            }

            void writeLong(CodeTreeBuilder b, String value) {
                writeLong(b, CodeTreeBuilder.singleString(value));
            }

            void writeLong(CodeTreeBuilder b, CodeTree value) {
                b.startStatement();
                b.string("serialization.", buffer.getName(), ".").startCall("writeLong");
                b.tree(value).end();
                b.end();
            }

            void writeBytes(CodeTreeBuilder b, String value) {
                writeBytes(b, CodeTreeBuilder.singleString(value));
            }

            void writeBytes(CodeTreeBuilder b, CodeTree value) {
                b.startStatement();
                b.string("serialization.", buffer.getName(), ".").startCall("write");
                b.tree(value).end();
                b.end();
            }

        }

        final class DeserializationStateElement extends CodeTypeElement implements ElementHelpers {

            private final CodeVariableElement depth;

            DeserializationStateElement() {
                super(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "DeserializationState");
                this.setEnclosingElement(BytecodeRootNodeElement.this);
                this.getImplements().add(types.BytecodeDeserializer_DeserializerContext);

                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), this.asType(), "outer"));
                this.depth = this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), type(int.class), "depth"));
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, Object.class), "consts")).//
                                createInitBuilder().startNew("ArrayList<>").end();
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, BytecodeRootNodeElement.this.asType()), "builtNodes")).//
                                createInitBuilder().startNew("ArrayList<>").end();
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLabel), "labels")).//
                                createInitBuilder().startNew("ArrayList<>").end();
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(context.getDeclaredType(ArrayList.class), types.BytecodeLocal), "locals")).//
                                createInitBuilder().startNew("ArrayList<>").end();
                this.add(new CodeVariableElement(Set.of(PRIVATE, FINAL), generic(ArrayList.class, Runnable.class), "finallyGenerators")).//
                                createInitBuilder().startNew("ArrayList<>").end();
                this.add(createConstructor());
                this.add(createReadBytecodeNode());
                this.add(createGetContext());
            }

            private CodeExecutableElement createConstructor() {
                CodeExecutableElement constructor = new CodeExecutableElement(Set.of(PRIVATE), null, this.getSimpleName().toString());
                constructor.addParameter(new CodeVariableElement(this.asType(), "outer"));

                CodeTreeBuilder b = constructor.createBuilder();
                b.statement("this.outer = outer");
                b.statement("this.depth = (outer == null) ? 0 : outer.depth + 1");

                return constructor;
            }

            private CodeExecutableElement createReadBytecodeNode() {
                CodeExecutableElement ex = GeneratorUtils.override(types.BytecodeDeserializer_DeserializerContext, "readBytecodeNode", new String[]{"buffer"});
                CodeTreeBuilder b = ex.createBuilder();
                b.statement("return getContext(buffer.readInt()).builtNodes.get(buffer.readInt())");
                return ex;
            }

            private CodeExecutableElement createGetContext() {
                CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), this.asType(), "getContext");
                method.addParameter(new CodeVariableElement(type(int.class), "targetDepth"));

                CodeTreeBuilder b = method.createBuilder();

                b.startAssert().string("targetDepth >= 0").end();

                b.declaration(this.asType(), "ctx", "this");
                b.startWhile().string("ctx.depth != targetDepth").end().startBlock();
                b.statement("ctx = ctx.outer");
                b.end();

                b.statement("return ctx");

                return method;
            }

        }
    }

}
