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
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.mergeSuppressWarnings;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.SuppressFBWarnings;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediateEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
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
import com.oracle.truffle.dsl.processor.java.model.CodeTypeParameterElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

/**
 * Central code generation class for Bytecode DSL root nodes.
 */
public final class BytecodeRootNodeElement extends AbstractElement {

    static final String USER_LOCALS_START_INDEX = "USER_LOCALS_START_INDEX";
    static final String BCI_INDEX = "BCI_INDEX";
    static final String COROUTINE_FRAME_INDEX = "COROUTINE_FRAME_INDEX";
    static final String EMPTY_INT_ARRAY = "EMPTY_INT_ARRAY";

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
    final BuilderElement builder;
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
    final OperationConstantsElement operationsElement = new OperationConstantsElement(this);
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

    private Map<TypeMirror, CodeExecutableElement> expectMethods = new HashMap<>();

    BytecodeRootNodeElement(BytecodeDSLModel model, TypeMirror abstractBuilderType) {
        super(null, Set.of(PUBLIC, FINAL), ElementKind.CLASS, ElementUtils.findPackageElement(model.getTemplateType()), model.getName());
        if (model.hasErrors()) {
            throw new IllegalArgumentException("Models with errors are not supported.");
        }

        this.model = model;
        this.builder = new BuilderElement(this);
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
        addField(this, Set.of(PRIVATE, STATIC, FINAL), types.BytecodeDSLAccess, "SAFE_ACCESS") //
                        .createInitBuilder().tree(createFastAccessFieldInitializer(false));
        addField(this, Set.of(PRIVATE, STATIC, FINAL), types.ByteArraySupport, "SAFE_BYTES") //
                        .createInitBuilder().startCall("SAFE_ACCESS.getByteArraySupport").end();
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

        // Define helper classes containing the constants for instructions and operations.
        instructionsElement.lazyInit();
        this.add(instructionsElement);

        operationsElement.lazyInit();
        this.add(operationsElement);

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

        bytecodeDescriptorElement.lazyInit();

        CodeVariableElement descriptor = new CodeVariableElement(Set.of(PUBLIC, STATIC, FINAL), bytecodeDescriptorElement.asType(), "BYTECODE");
        descriptor.createInitBuilder().startNew(bytecodeDescriptorElement.asType()).end();
        this.getEnclosedElements().add(0, descriptor);

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
        BytecodeDSLNodeGeneratorPlugs plugs = new BytecodeDSLNodeGeneratorPlugs(this, instr);
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
            b.startIf().string("frame.getTag(BCI_INDEX) == ").staticReference(types.FrameSlotKind, "Illegal").string(".tag").end().startBlock();
            b.lineComment("The bci index might be illegal if it was never set due to optimizations.");
            b.statement("return -1");
            b.end();
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
        ex.addParameter(new CodeVariableElement(type(String.class), "message"));
        ex.addParameter(new CodeVariableElement(type(Object.class), "args"));
        ex.setVarArgs(true);

        CodeTreeBuilder b = ex.createBuilder();
        b.startThrow().startNew(type(AssertionError.class));
        b.startCall("message.formatted").string("args").end();
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
            builder.serializationRootNode.add(stub);
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

        if (model.enableUncachedInterpreter) {
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

    static void emitThrowAssertionError(CodeTreeBuilder b, String reason, String... args) {
        b.startThrow().startCall("assertionFailed");
        b.string(reason);
        for (String arg : args) {
            b.string(arg);
        }
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

    static void addJavadoc(CodeElement<?> element, List<String> lines) {
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
        return readImmediateWithOffset(bc, bci, immediate, immediate.offset());
    }

    static CodeTree readImmediateWithOffset(String bc, String bci, InstructionImmediate immediate, int offset) {
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
        b.string(bci);
        if (offset > 0) {
            b.string(" + ").string(offset);
        } else if (offset < 0) {
            b.string(" - ").string(-offset);
        }
        b.string(" ");
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

    static String readConstSafe(String index) {
        return String.format("SAFE_ACCESS.readObject(constants, %s)", index);
    }

    static String readLongSafe(String array, String index) {
        return String.format("SAFE_BYTES.getLong(%s, %s)", array, index);
    }

    static String readIntSafe(String array, String index) {
        return String.format("SAFE_BYTES.getInt(%s, %s)", array, index);
    }

    static String readShortSafe(String array, String index) {
        return String.format("SAFE_BYTES.getShort(%s, %s)", array, index);
    }

    static String readByteSafe(String array, String index) {
        return String.format("SAFE_BYTES.getByte(%s, %s)", array, index);
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
        return switch (instr.kind) {
            case RETURN, YIELD, TAG_ENTER, TAG_LEAVE, TAG_LEAVE_VOID, TAG_RESUME, TAG_YIELD, TAG_YIELD_NULL -> true;
            case CUSTOM -> {
                if (instr.operation.kind == OperationKind.CUSTOM_YIELD) {
                    // custom yield always needs to set the bci
                    yield true;
                }
                CustomOperationModel custom = instr.operation.customModel;
                if (!custom.shouldStoreBytecodeIndex()) {
                    yield false;
                }

                for (SpecializationData s : instr.nodeData.getReachableSpecializations()) {
                    // if we use cached values in guards we need to store before execute regardless
                    // otherwise the bytecode index is not up-to-date in the guard
                    if (custom.shouldStoreBytecodeIndex(s) && s.isAnyGuardBoundWithCache()) {
                        yield true;
                    }
                }

                for (SpecializationData s : instr.nodeData.getReachableSpecializations()) {
                    if (!custom.shouldStoreBytecodeIndex(s)) {
                        yield false;
                    }
                }

                yield true;
            }
            default -> false;
        };
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
}
